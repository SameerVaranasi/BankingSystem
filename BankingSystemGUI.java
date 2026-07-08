import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

class ASBankAccount implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final String accountHolderName;
    private final String phoneNumber;
    private final String emailAddress;
    private final String accountNumber;
    private double balance;
    private final List<String> transactionHistory;

    public ASBankAccount(String accountHolderName, String phoneNumber, String emailAddress, String accountNumber, double openingBalance) {
        this.accountHolderName = accountHolderName;
        this.phoneNumber = phoneNumber;
        this.emailAddress = emailAddress;
        this.accountNumber = accountNumber;
        this.balance = openingBalance;
        this.transactionHistory = new ArrayList<>();
        addTransaction("Account created with opening balance: Rs. " + formatAmount(openingBalance));
    }

    public String deposit(double amount) {
        if (amount <= 0) {
            return "Deposit amount must be greater than zero.";
        }

        balance += amount;
        addTransaction("Deposited: Rs. " + formatAmount(amount));
        return "Deposit successful. Current balance: Rs. " + formatAmount(balance);
    }

    public String withdraw(double amount) {
        if (amount <= 0) {
            return "Withdrawal amount must be greater than zero.";
        }

        if (amount > balance) {
            addTransaction("Failed withdrawal attempt: Rs. " + formatAmount(amount));
            return "Insufficient balance.";
        }

        balance -= amount;
        addTransaction("Withdrawn: Rs. " + formatAmount(amount));
        return "Withdrawal successful. Current balance: Rs. " + formatAmount(balance);
    }

    public String getAccountHolderName() {
        return accountHolderName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public double getBalance() {
        return balance;
    }

    public String getTransactionHistory() {
        return String.join("\n", transactionHistory);
    }

    public String formatAmount(double amount) {
        return String.format("%.2f", amount);
    }

    private void addTransaction(String message) {
        String dateTime = LocalDateTime.now().format(FORMATTER);
        transactionHistory.add(dateTime + " - " + message + " | Balance: Rs. " + formatAmount(balance));
    }
}

class ASBankUser implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final int HASH_ITERATIONS = 65536;
    private static final int KEY_LENGTH = 256;

    private final String loginId;
    private final byte[] salt;
    private final byte[] passwordHash;
    private final ASBankAccount account;
    private int failedAttempts;
    private LocalDateTime lockedUntil;

    public ASBankUser(String loginId, char[] password, ASBankAccount account) {
        this.loginId = loginId;
        this.salt = createSalt();
        this.passwordHash = hashPassword(password, salt);
        this.account = account;
        Arrays.fill(password, '\0');
    }

    public String getLoginId() {
        return loginId;
    }

    public ASBankAccount getAccount() {
        return account;
    }

    public int getRemainingAttempts() {
        return Math.max(0, 3 - failedAttempts);
    }

    public boolean isLocked() {
        if (lockedUntil == null) {
            return false;
        }

        if (LocalDateTime.now().isAfter(lockedUntil)) {
            failedAttempts = 0;
            lockedUntil = null;
            return false;
        }

        return true;
    }

    public String getLockMessage() {
        if (lockedUntil == null) {
            return "";
        }

        long minutes = Math.max(1, Duration.between(LocalDateTime.now(), lockedUntil).toMinutes());
        return "This login ID is locked. Try again after about " + minutes + " minute(s).";
    }

    public boolean passwordMatches(char[] password) {
        byte[] enteredHash = hashPassword(password, salt);
        boolean matches = slowEquals(passwordHash, enteredHash);
        Arrays.fill(enteredHash, (byte) 0);
        return matches;
    }

    public void recordFailedAttempt() {
        failedAttempts++;
        if (failedAttempts >= 3) {
            lockedUntil = LocalDateTime.now().plusHours(2);
        }
    }

    public void recordSuccessfulLogin() {
        failedAttempts = 0;
        lockedUntil = null;
    }

    private static byte[] createSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    private static byte[] hashPassword(char[] password, byte[] salt) {
        try {
            KeySpec spec = new PBEKeySpec(password, salt, HASH_ITERATIONS, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException exception) {
            throw new IllegalStateException("Could not protect password.", exception);
        }
    }

    private static boolean slowEquals(byte[] first, byte[] second) {
        int difference = first.length ^ second.length;
        for (int i = 0; i < first.length && i < second.length; i++) {
            difference |= first[i] ^ second[i];
        }
        return difference == 0;
    }
}

public class BankingSystemGUI extends JFrame {
    private static final String WELCOME_SCREEN = "welcome";
    private static final String LOGIN_SCREEN = "login";
    private static final String CREATE_SCREEN = "create";
    private static final String DASHBOARD_SCREEN = "dashboard";
    private static final long ACCOUNT_BLOCK_SIZE = 999_999_999L;
    private static final String DATA_FILE_NAME = "as_bank_data.ser";

    private final Map<String, ASBankUser> users;
    private long nextAccountSerial;
    private ASBankUser currentUser;
    private ASBankAccount currentAccount;
    private boolean sidebarExpanded;

    private final CardLayout cardLayout;
    private final JPanel cards;
    private final JTextField loginIdField;
    private final JPasswordField loginPasswordField;
    private final JTextField createNameField;
    private final JTextField createPhoneField;
    private final JTextField createEmailField;
    private final JTextField createLoginIdField;
    private final JPasswordField createPasswordField;
    private final JPasswordField confirmPasswordField;
    private final JTextField openingBalanceField;
    private final JLabel userInfoLabel;
    private final JLabel balanceLabel;
    private final JTextArea outputArea;
    private JPanel sidebarPanel;
    private JButton sidebarToggleButton;
    private JButton depositButton;
    private JButton withdrawButton;
    private JButton balanceButton;
    private JButton historyButton;

    public BankingSystemGUI() {
        users = new HashMap<>();
        nextAccountSerial = 1;
        loadBankData();
        sidebarExpanded = true;
        cardLayout = new CardLayout();
        cards = new JPanel(cardLayout);
        loginIdField = new JTextField(18);
        loginPasswordField = new JPasswordField(18);
        createNameField = new JTextField(18);
        createPhoneField = new JTextField(18);
        createEmailField = new JTextField(18);
        createLoginIdField = new JTextField(18);
        createPasswordField = new JPasswordField(18);
        confirmPasswordField = new JPasswordField(18);
        openingBalanceField = new JTextField(18);
        userInfoLabel = new JLabel();
        balanceLabel = new JLabel();
        outputArea = new JTextArea();

        setTitle("AS Bank");
        setSize(820, 580);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(760, 540));

        buildScreens();
        add(cards);
        showWelcomeScreen();
    }

    private void loadBankData() {
        File dataFile = new File(DATA_FILE_NAME);
        if (!dataFile.exists()) {
            return;
        }

        try (ObjectInputStream input = new ObjectInputStream(new FileInputStream(dataFile))) {
            BankStorage storage = (BankStorage) input.readObject();
            users.clear();
            users.putAll(storage.users);
            nextAccountSerial = storage.nextAccountSerial;
        } catch (IOException | ClassNotFoundException exception) {
            showMessage("Saved bank data could not be loaded. A new bank file will be created when you save again.");
        }
    }

    private void saveBankData() {
        BankStorage storage = new BankStorage(users, nextAccountSerial);
        try (ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(DATA_FILE_NAME))) {
            output.writeObject(storage);
        } catch (IOException exception) {
            showMessage("Bank data could not be saved: " + exception.getMessage());
        }
    }

    private static class BankStorage implements Serializable {
        private static final long serialVersionUID = 1L;
        private final Map<String, ASBankUser> users;
        private final long nextAccountSerial;

        private BankStorage(Map<String, ASBankUser> users, long nextAccountSerial) {
            this.users = new HashMap<>(users);
            this.nextAccountSerial = nextAccountSerial;
        }
    }
    private void buildScreens() {
        cards.add(buildWelcomePanel(), WELCOME_SCREEN);
        cards.add(buildLoginPanel(), LOGIN_SCREEN);
        cards.add(buildCreatePanel(), CREATE_SCREEN);
        cards.add(buildDashboardPanel(), DASHBOARD_SCREEN);
    }

    private JPanel buildWelcomePanel() {
        JPanel panel = createBasePanel();
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);

        GridBagConstraints gbc = createGbc();
        gbc.gridwidth = 2;
        center.add(createLogoLabel(), gbc);

        JLabel subtitle = new JLabel("Secure Banking Portal", SwingConstants.CENTER);
        subtitle.setFont(new Font("Arial", Font.BOLD, 18));
        subtitle.setForeground(new Color(45, 58, 74));
        gbc.gridy = 1;
        center.add(subtitle, gbc);

        JButton loginButton = createPrimaryButton("Login");
        JButton createButton = createSecondaryButton("Create New Account");
        loginButton.addActionListener(event -> showLoginScreen());
        createButton.addActionListener(event -> showCreateScreen());

        gbc.gridwidth = 1;
        gbc.gridy = 2;
        gbc.gridx = 0;
        center.add(loginButton, gbc);

        gbc.gridx = 1;
        center.add(createButton, gbc);

        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildLoginPanel() {
        JPanel panel = createBasePanel();
        JPanel form = createFormPanel();
        GridBagConstraints gbc = createGbc();

        gbc.gridwidth = 2;
        form.add(createLogoLabel(), gbc);

        gbc.gridy = 1;
        form.add(createSectionTitle("Login"), gbc);

        addFormRow(form, gbc, 2, "Login ID", loginIdField);
        addFormRow(form, gbc, 3, "Password", loginPasswordField);

        JButton loginButton = createPrimaryButton("Login");
        JButton backButton = createSecondaryButton("Back");
        loginButton.addActionListener(event -> login());
        backButton.addActionListener(event -> showWelcomeScreen());

        gbc.gridy = 4;
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        form.add(backButton, gbc);

        gbc.gridx = 1;
        form.add(loginButton, gbc);

        panel.add(form, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildCreatePanel() {
        JPanel panel = createBasePanel();
        JPanel form = createFormPanel();
        GridBagConstraints gbc = createGbc();

        gbc.gridwidth = 2;
        form.add(createSectionTitle("Create AS Bank Account"), gbc);

        addFormRow(form, gbc, 1, "Full Name", createNameField);
        addFormRow(form, gbc, 2, "Phone Number", createPhoneField);
        addFormRow(form, gbc, 3, "Email Address", createEmailField);
        addFormRow(form, gbc, 4, "Unique Login ID", createLoginIdField);
        addFormRow(form, gbc, 5, "Password", createPasswordField);
        addFormRow(form, gbc, 6, "Confirm Password", confirmPasswordField);
        addFormRow(form, gbc, 7, "Opening Balance", openingBalanceField);

        JButton createButton = createPrimaryButton("Create Account");
        JButton backButton = createSecondaryButton("Back");
        createButton.addActionListener(event -> createAccount());
        backButton.addActionListener(event -> showWelcomeScreen());

        gbc.gridy = 8;
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        form.add(backButton, gbc);

        gbc.gridx = 1;
        form.add(createButton, gbc);

        panel.add(form, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildDashboardPanel() {
        JPanel panel = createBasePanel();

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));

        JLabel title = new JLabel("AS Bank");
        title.setFont(new Font("Arial", Font.BOLD, 24));
        title.setForeground(new Color(33, 92, 165));

        JButton logoutButton = createLogoutButton();
        logoutButton.addActionListener(event -> logout());
        header.add(title, BorderLayout.WEST);
        header.add(logoutButton, BorderLayout.EAST);

        JPanel accountPanel = createFormPanel();
        accountPanel.setLayout(new BorderLayout(10, 10));
        userInfoLabel.setFont(new Font("Arial", Font.BOLD, 16));
        userInfoLabel.setForeground(new Color(45, 58, 74));
        balanceLabel.setFont(new Font("Arial", Font.BOLD, 22));
        balanceLabel.setForeground(new Color(20, 117, 75));
        accountPanel.add(userInfoLabel, BorderLayout.NORTH);
        accountPanel.add(balanceLabel, BorderLayout.CENTER);

        sidebarPanel = new JPanel(new GridBagLayout());
        sidebarPanel.setBackground(new Color(35, 48, 64));
        sidebarPanel.setBorder(BorderFactory.createEmptyBorder(12, 10, 12, 10));
        sidebarToggleButton = createSidebarButton("Hide Menu");
        depositButton = createSidebarButton("Deposit");
        withdrawButton = createSidebarButton("Withdraw");
        balanceButton = createSidebarButton("Balance Enquiry");
        historyButton = createSidebarButton("Transaction History");
        sidebarToggleButton.addActionListener(event -> toggleSidebar());
        depositButton.addActionListener(event -> deposit());
        withdrawButton.addActionListener(event -> withdraw());
        balanceButton.addActionListener(event -> showBalance());
        historyButton.addActionListener(event -> showHistory());
        buildSidebar();

        JPanel center = new JPanel(new BorderLayout(16, 16));
        center.setOpaque(false);
        center.add(accountPanel, BorderLayout.NORTH);

        outputArea.setEditable(false);
        outputArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        outputArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane scrollPane = new JScrollPane(outputArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Display"));
        center.add(scrollPane, BorderLayout.CENTER);

        panel.add(header, BorderLayout.NORTH);
        panel.add(sidebarPanel, BorderLayout.WEST);
        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    private void createAccount() {
        String name = createNameField.getText().trim();
        String phoneNumber = createPhoneField.getText().trim();
        String emailAddress = createEmailField.getText().trim();
        String loginId = createLoginIdField.getText().trim();
        char[] password = createPasswordField.getPassword();
        char[] confirmPassword = confirmPasswordField.getPassword();
        String openingBalanceText = openingBalanceField.getText().trim();

        if (name.isEmpty() || phoneNumber.isEmpty() || emailAddress.isEmpty() || loginId.isEmpty() || openingBalanceText.isEmpty()) {
            showMessage("Please fill all account details.");
            clearPasswords(password, confirmPassword);
            return;
        }

        if (!phoneNumber.matches("\\d{10}")) {
            showMessage("Phone number must contain exactly 10 digits.");
            clearPasswords(password, confirmPassword);
            return;
        }

        if (!emailAddress.contains("@") || !emailAddress.contains(".")) {
            showMessage("Please enter a valid email address.");
            clearPasswords(password, confirmPassword);
            return;
        }

        if (users.containsKey(loginId)) {
            showMessage("This login ID already exists. Please choose another one.");
            clearPasswords(password, confirmPassword);
            return;
        }

        if (password.length < 6) {
            showMessage("Password must contain at least 6 characters.");
            clearPasswords(password, confirmPassword);
            return;
        }

        if (!Arrays.equals(password, confirmPassword)) {
            showMessage("Password and confirm password do not match.");
            clearPasswords(password, confirmPassword);
            return;
        }

        double openingBalance = parseAmount(openingBalanceText);
        if (openingBalance < 0) {
            showMessage("Opening balance cannot be negative.");
            clearPasswords(password, confirmPassword);
            return;
        }

        String accountNumber = generateAccountNumber();
        ASBankAccount account = new ASBankAccount(name, phoneNumber, emailAddress, accountNumber, openingBalance);
        ASBankUser user = new ASBankUser(loginId, password, account);
        users.put(loginId, user);
        saveBankData();
        clearPasswords(confirmPassword);
        clearCreateFields();
        showMessage("Account created successfully.\nYour account number is: " + accountNumber + "\nPlease login now.");
        showLoginScreen();
    }

    private void login() {
        String loginId = loginIdField.getText().trim();
        char[] password = loginPasswordField.getPassword();

        if (loginId.isEmpty() || password.length == 0) {
            showMessage("Please enter login ID and password.");
            clearPasswords(password);
            return;
        }

        ASBankUser user = users.get(loginId);
        if (user == null) {
            showMessage("Login ID or password is wrong.");
            clearPasswords(password);
            return;
        }

        if (user.isLocked()) {
            showMessage(user.getLockMessage());
            clearPasswords(password);
            return;
        }

        if (!user.passwordMatches(password)) {
            user.recordFailedAttempt();
            saveBankData();
            clearPasswords(password);
            if (user.isLocked()) {
                showMessage("Wrong password 3 times. This login ID is locked for 2 hours.");
            } else {
                showMessage("Login ID or password is wrong. Attempts left: " + user.getRemainingAttempts());
            }
            return;
        }

        user.recordSuccessfulLogin();
        saveBankData();
        clearPasswords(password);
        currentUser = user;
        currentAccount = user.getAccount();
        clearLoginFields();
        updateDashboard();
        showDashboardScreen();
    }

    private String generateAccountNumber() {
        if (nextAccountSerial > ACCOUNT_BLOCK_SIZE * 10L) {
            throw new IllegalStateException("All supported AS Bank account numbers are used.");
        }

        long serial = nextAccountSerial;
        nextAccountSerial++;

        if (serial <= ACCOUNT_BLOCK_SIZE) {
            return "AS" + String.format("%09d", serial);
        }

        long blockNumber = (serial - 1) / ACCOUNT_BLOCK_SIZE;
        long code = ((serial - 1) % ACCOUNT_BLOCK_SIZE) + 1;
        return "AS" + blockNumber + String.format("%09d", code);
    }

    private void deposit() {
        Double amount = askForAmount("Enter amount to deposit:");
        if (amount == null) {
            return;
        }

        outputArea.setText(currentAccount.deposit(amount));
        saveBankData();
        updateDashboard();
    }

    private void withdraw() {
        Double amount = askForAmount("Enter amount to withdraw:");
        if (amount == null) {
            return;
        }

        outputArea.setText(currentAccount.withdraw(amount));
        saveBankData();
        updateDashboard();
    }

    private void showBalance() {
        updateDashboard();
        outputArea.setText(
                "Account Holder: " + currentAccount.getAccountHolderName()
                        + "\nPhone: " + currentAccount.getPhoneNumber()
                        + "\nEmail: " + currentAccount.getEmailAddress()
                        + "\nAccount Number: " + currentAccount.getAccountNumber()
                        + "\nAvailable balance: Rs. " + currentAccount.formatAmount(currentAccount.getBalance())
        );
    }

    private void showHistory() {
        outputArea.setText(currentAccount.getTransactionHistory());
        outputArea.setCaretPosition(0);
    }

    private void logout() {
        currentUser = null;
        currentAccount = null;
        outputArea.setText("");
        showWelcomeScreen();
    }

    private void updateDashboard() {
        userInfoLabel.setText(
                "<html>"
                        + currentAccount.getAccountHolderName()
                        + " | Account No: "
                        + currentAccount.getAccountNumber()
                        + "<br>Phone: "
                        + currentAccount.getPhoneNumber()
                        + " | Email: "
                        + currentAccount.getEmailAddress()
                        + " | Login ID: "
                        + currentUser.getLoginId()
                        + "</html>"
        );
        balanceLabel.setText("Balance: Rs. " + currentAccount.formatAmount(currentAccount.getBalance()));
    }

    private Double askForAmount(String message) {
        String input = JOptionPane.showInputDialog(this, message);
        if (input == null) {
            return null;
        }

        double amount = parseAmount(input.trim());
        if (amount <= 0) {
            showMessage("Please enter an amount greater than zero.");
            return null;
        }

        return amount;
    }

    private double parseAmount(String text) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException exception) {
            showMessage("Please enter a valid amount.");
            return -1;
        }
    }

    private JPanel createBasePanel() {
        JPanel panel = new JPanel(new BorderLayout(16, 16));
        panel.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        panel.setBackground(new Color(239, 244, 248));
        return panel;
    }

    private JPanel createFormPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(204, 216, 228)),
                BorderFactory.createEmptyBorder(22, 24, 22, 24)
        ));
        return panel;
    }

    private JLabel createLogoLabel() {
        JLabel logo = new JLabel("<html><span style='color:#111111;'>A</span><span style='color:#135db8;'>S</span> Bank</html>", SwingConstants.CENTER);
        logo.setFont(new Font("Arial", Font.BOLD, 54));
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        return logo;
    }

    private JLabel createSectionTitle(String text) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(new Font("Arial", Font.BOLD, 22));
        label.setForeground(new Color(35, 48, 64));
        return label;
    }

    private JButton createPrimaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Arial", Font.BOLD, 15));
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(19, 93, 184));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(190, 42));
        return button;
    }

    private JButton createSecondaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Arial", Font.BOLD, 15));
        button.setForeground(new Color(19, 93, 184));
        button.setBackground(Color.WHITE);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(190, 42));
        return button;
    }

    private JButton createLogoutButton() {
        JButton button = new JButton("Logout");
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(35, 48, 64));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private JButton createSidebarButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(35, 48, 64));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setPreferredSize(new Dimension(180, 42));
        return button;
    }

    private void buildSidebar() {
        sidebarPanel.removeAll();
        sidebarPanel.setPreferredSize(new Dimension(sidebarExpanded ? 210 : 72, 420));

        sidebarToggleButton.setText(sidebarExpanded ? "Hide Menu" : "Menu");
        depositButton.setText(sidebarExpanded ? "Deposit" : "D");
        withdrawButton.setText(sidebarExpanded ? "Withdraw" : "W");
        balanceButton.setText(sidebarExpanded ? "Balance Enquiry" : "B");
        historyButton.setText(sidebarExpanded ? "Transaction History" : "H");

        Dimension buttonSize = new Dimension(sidebarExpanded ? 180 : 48, 42);
        sidebarToggleButton.setPreferredSize(buttonSize);
        depositButton.setPreferredSize(buttonSize);
        withdrawButton.setPreferredSize(buttonSize);
        balanceButton.setPreferredSize(buttonSize);
        historyButton.setPreferredSize(buttonSize);

        addSidebarButton(sidebarToggleButton, 0);
        addSidebarButton(depositButton, 1);
        addSidebarButton(withdrawButton, 2);
        addSidebarButton(balanceButton, 3);
        addSidebarButton(historyButton, 4);
        sidebarPanel.revalidate();
        sidebarPanel.repaint();
    }

    private void addSidebarButton(JButton button, int row) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.insets = new Insets(6, 0, 6, 0);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        sidebarPanel.add(button, gbc);
    }

    private void toggleSidebar() {
        sidebarExpanded = !sidebarExpanded;
        buildSidebar();
    }

    private GridBagConstraints createGbc() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        return gbc;
    }

    private void addFormRow(JPanel panel, GridBagConstraints gbc, int row, String label, JTextField field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        panel.add(new JLabel(label), gbc);

        gbc.gridx = 1;
        panel.add(field, gbc);
    }

    private void showWelcomeScreen() {
        cardLayout.show(cards, WELCOME_SCREEN);
    }

    private void showLoginScreen() {
        cardLayout.show(cards, LOGIN_SCREEN);
    }

    private void showCreateScreen() {
        cardLayout.show(cards, CREATE_SCREEN);
    }

    private void showDashboardScreen() {
        cardLayout.show(cards, DASHBOARD_SCREEN);
    }

    private void clearCreateFields() {
        createNameField.setText("");
        createPhoneField.setText("");
        createEmailField.setText("");
        createLoginIdField.setText("");
        createPasswordField.setText("");
        confirmPasswordField.setText("");
        openingBalanceField.setText("");
    }

    private void clearLoginFields() {
        loginIdField.setText("");
        loginPasswordField.setText("");
    }

    private void clearPasswords(char[]... passwords) {
        for (char[] password : passwords) {
            Arrays.fill(password, '\0');
        }
    }

    private void showMessage(String message) {
        JOptionPane.showMessageDialog(this, message);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            BankingSystemGUI app = new BankingSystemGUI();
            app.setVisible(true);
        });
    }
}


