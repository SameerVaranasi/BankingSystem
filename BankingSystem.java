import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

class BankAccount {
    private final String accountHolderName;
    private final String accountNumber;
    private double balance;
    private final List<String> transactionHistory;
    private final DateTimeFormatter formatter;

    public BankAccount(String accountHolderName, String accountNumber, double openingBalance) {
        this.accountHolderName = accountHolderName;
        this.accountNumber = accountNumber;
        this.balance = openingBalance;
        this.transactionHistory = new ArrayList<>();
        this.formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        addTransaction("Account created with opening balance: Rs. " + formatAmount(openingBalance));
    }

    public void deposit(double amount) {
        if (amount <= 0) {
            System.out.println("Deposit amount must be greater than zero.");
            return;
        }

        balance += amount;
        addTransaction("Deposited: Rs. " + formatAmount(amount));
        System.out.println("Deposit successful.");
        System.out.println("Current balance: Rs. " + formatAmount(balance));
    }

    public void withdraw(double amount) {
        if (amount <= 0) {
            System.out.println("Withdrawal amount must be greater than zero.");
            return;
        }

        if (amount > balance) {
            System.out.println("Insufficient balance.");
            addTransaction("Failed withdrawal attempt: Rs. " + formatAmount(amount));
            return;
        }

        balance -= amount;
        addTransaction("Withdrawn: Rs. " + formatAmount(amount));
        System.out.println("Withdrawal successful.");
        System.out.println("Current balance: Rs. " + formatAmount(balance));
    }

    public void showBalance() {
        System.out.println("\nAccount holder: " + accountHolderName);
        System.out.println("Account number: " + accountNumber);
        System.out.println("Available balance: Rs. " + formatAmount(balance));
    }

    public void showTransactionHistory() {
        System.out.println("\nTransaction History");
        System.out.println("-------------------");

        if (transactionHistory.isEmpty()) {
            System.out.println("No transactions found.");
            return;
        }

        for (String transaction : transactionHistory) {
            System.out.println(transaction);
        }
    }

    private void addTransaction(String message) {
        String dateTime = LocalDateTime.now().format(formatter);
        transactionHistory.add(dateTime + " - " + message + " | Balance: Rs. " + formatAmount(balance));
    }

    private String formatAmount(double amount) {
        return String.format("%.2f", amount);
    }
}

public class BankingSystem {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Simple Banking System");
        System.out.println("=====================");

        System.out.print("Enter account holder name: ");
        String name = scanner.nextLine();

        System.out.print("Enter account number: ");
        String accountNumber = scanner.nextLine();

        double openingBalance = readAmount(scanner, "Enter opening balance: ");
        BankAccount account = new BankAccount(name, accountNumber, openingBalance);

        int choice;
        do {
            showMenu();
            choice = readChoice(scanner);

            switch (choice) {
                case 1:
                    double depositAmount = readAmount(scanner, "Enter amount to deposit: ");
                    account.deposit(depositAmount);
                    break;
                case 2:
                    double withdrawAmount = readAmount(scanner, "Enter amount to withdraw: ");
                    account.withdraw(withdrawAmount);
                    break;
                case 3:
                    account.showBalance();
                    break;
                case 4:
                    account.showTransactionHistory();
                    break;
                case 5:
                    System.out.println("Thank you for using the banking system.");
                    break;
                default:
                    System.out.println("Invalid choice. Please select 1 to 5.");
            }
        } while (choice != 5);

        scanner.close();
    }

    private static void showMenu() {
        System.out.println("\nMenu");
        System.out.println("1. Deposit");
        System.out.println("2. Withdraw");
        System.out.println("3. Balance Enquiry");
        System.out.println("4. Transaction History");
        System.out.println("5. Exit");
        System.out.print("Enter your choice: ");
    }

    private static int readChoice(Scanner scanner) {
        while (!scanner.hasNextInt()) {
            System.out.print("Please enter a valid number: ");
            scanner.next();
        }

        int choice = scanner.nextInt();
        scanner.nextLine();
        return choice;
    }

    private static double readAmount(Scanner scanner, String prompt) {
        System.out.print(prompt);

        while (!scanner.hasNextDouble()) {
            System.out.print("Please enter a valid amount: ");
            scanner.next();
        }

        double amount = scanner.nextDouble();
        scanner.nextLine();
        return amount;
    }
}
