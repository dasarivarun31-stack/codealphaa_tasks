package codealpha_tasks;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ThreadLocalRandom;

public class StockTradingSimulator {
    private static final String DATA_FILE = "stock-trading-data.ser";
    private static final double STARTING_CASH = 10_000.00;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        PlatformState state = loadState();
        if (state.users.isEmpty()) {
            state.users.add(new User("Demo Trader", STARTING_CASH));
            state.users.get(0).recordSnapshot(state.market);
        }

        System.out.println("Stock Trading Simulator");
        System.out.println("All quotes are simulated. No real orders or money are involved.");
        boolean running = true;
        while (running) {
            User user = state.currentUser();
            System.out.printf(Locale.ROOT, "\nAccount: %s | Cash: $%.2f%n", user.name, user.cash);
            System.out.println("1. View market quotes");
            System.out.println("2. Buy shares");
            System.out.println("3. Sell shares");
            System.out.println("4. View portfolio");
            System.out.println("5. Portfolio performance history");
            System.out.println("6. Transaction history");
            System.out.println("7. Advance simulated market");
            System.out.println("8. Switch or create account");
            System.out.println("9. Save and exit");
            System.out.print("Choose an option: ");

            String choice = readLine(scanner);
            switch (choice) {
                case "1":
                    displayMarket(state.market);
                    break;
                case "2":
                    trade(scanner, state, true);
                    break;
                case "3":
                    trade(scanner, state, false);
                    break;
                case "4":
                    displayPortfolio(user, state.market);
                    break;
                case "5":
                    displayPerformance(user);
                    break;
                case "6":
                    displayTransactions(user);
                    break;
                case "7":
                    state.market.advancePrices();
                    user.recordSnapshot(state.market);
                    System.out.println("Simulated prices advanced. Portfolio value snapshot recorded.");
                    break;
                case "8":
                    manageAccounts(scanner, state);
                    break;
                case "9":
                    running = false;
                    break;
                default:
                    System.out.println("Please choose a number from 1 to 9.");
            }
        }

        saveState(state);
        scanner.close();
        System.out.println("Data saved to " + DATA_FILE + ". Goodbye.");
    }

    private static void displayMarket(Market market) {
        System.out.println("\nSimulated Market Quotes");
        System.out.printf(Locale.ROOT, "%-8s %-24s %12s %12s %10s%n",
                "Ticker", "Company", "Price", "Change", "Change %");
        for (Stock stock : market.stocks()) {
            double change = stock.price - stock.referencePrice;
            double changePercent = change / stock.referencePrice * 100;
            System.out.printf(Locale.ROOT, "%-8s %-24s $%11.2f %+11.2f %9.2f%%%n",
                    stock.ticker, stock.company, stock.price, change, changePercent);
        }
    }

    private static void trade(Scanner scanner, PlatformState state, boolean buying) {
        User user = state.currentUser();
        displayMarket(state.market);
        System.out.print("Ticker: ");
        String ticker = readLine(scanner).toUpperCase(Locale.ROOT);
        Stock stock = state.market.findStock(ticker);
        if (stock == null) {
            System.out.println("That ticker is not available in this simulated market.");
            return;
        }

        System.out.print("Whole shares: ");
        int quantity = readPositiveQuantity(scanner);
        if (quantity == 0) {
            System.out.println("Enter a valid whole-share quantity greater than zero.");
            return;
        }

        boolean completed = buying ? user.buy(stock, quantity) : user.sell(stock, quantity);
        if (!completed) {
            System.out.println(buying
                    ? "Not enough cash for this order."
                    : "You do not own enough shares to complete this sale.");
            return;
        }

        double orderTotal = roundMoney(stock.price * quantity);
        System.out.printf(Locale.ROOT, "%s %d %s at $%.2f per share; total $%.2f.%n",
                buying ? "Bought" : "Sold", quantity, stock.ticker, stock.price, orderTotal);
        user.recordSnapshot(state.market);
    }

    private static void displayPortfolio(User user, Market market) {
        System.out.println("\nPortfolio: " + user.name);
        System.out.printf(Locale.ROOT, "Cash: $%.2f%n", user.cash);
        if (user.holdings.isEmpty()) {
            System.out.println("No shares held.");
        } else {
            System.out.printf(Locale.ROOT, "%-8s %10s %14s %14s %14s%n",
                    "Ticker", "Shares", "Avg cost", "Market value", "Unrealized P/L");
            for (Map.Entry<String, Holding> entry : user.holdings.entrySet()) {
                Stock stock = market.findStock(entry.getKey());
                Holding holding = entry.getValue();
                double marketValue = stock.price * holding.quantity;
                double profitLoss = marketValue - holding.averageCost * holding.quantity;
                System.out.printf(Locale.ROOT, "%-8s %10d $%13.2f $%13.2f $%13.2f%n",
                        stock.ticker, holding.quantity, holding.averageCost, marketValue, profitLoss);
            }
        }

        double holdingsValue = user.holdingsValue(market);
        System.out.printf(Locale.ROOT, "Holdings value: $%.2f%n", holdingsValue);
        System.out.printf(Locale.ROOT, "Total portfolio value: $%.2f%n", user.cash + holdingsValue);
    }

    private static void displayPerformance(User user) {
        if (user.snapshots.isEmpty()) {
            System.out.println("No performance history yet. Advance the market or place a trade first.");
            return;
        }

        PortfolioSnapshot first = user.snapshots.get(0);
        PortfolioSnapshot previous = null;
        System.out.println("\nPortfolio Performance History");
        System.out.printf(Locale.ROOT, "%-20s %14s %14s %12s%n",
            "Recorded at", "Total value", "Step change", "Return");
        for (PortfolioSnapshot snapshot : user.snapshots) {
            double change = snapshot.totalValue - first.totalValue;
            double returnPercent = first.totalValue == 0 ? 0 : change / first.totalValue * 100;
            double stepChange = previous == null ? 0 : snapshot.totalValue - previous.totalValue;
            System.out.printf(Locale.ROOT, "%-20s $%13.2f $%13.2f %11.2f%%%n",
                snapshot.recordedAt.format(TIME_FORMAT), snapshot.totalValue, stepChange, returnPercent);
            previous = snapshot;
        }
    }

    private static void displayTransactions(User user) {
        if (user.transactions.isEmpty()) {
            System.out.println("No transactions recorded for this account.");
            return;
        }

        System.out.println("\nTransaction History: " + user.name);
        System.out.printf(Locale.ROOT, "%-20s %-6s %-8s %10s %12s %12s%n",
                "Time", "Side", "Ticker", "Shares", "Price", "Total");
        for (Transaction transaction : user.transactions) {
            System.out.printf(Locale.ROOT, "%-20s %-6s %-8s %10d $%11.2f $%11.2f%n",
                    transaction.recordedAt.format(TIME_FORMAT), transaction.side, transaction.ticker,
                    transaction.quantity, transaction.price, transaction.total);
        }
    }

    private static void manageAccounts(Scanner scanner, PlatformState state) {
        System.out.println("\nAccounts");
        for (int index = 0; index < state.users.size(); index++) {
            User user = state.users.get(index);
            System.out.printf("%d. %s%s%n", index + 1, user.name,
                    index == state.currentUserIndex ? " (current)" : "");
        }
        System.out.println("N. Create an account");
        System.out.print("Choose an account number or N: ");
        String choice = readLine(scanner);
        if (choice.equalsIgnoreCase("n")) {
            System.out.print("New account name: ");
            String name = readLine(scanner);
            if (name.isEmpty()) {
                System.out.println("Account name cannot be blank.");
                return;
            }
            for (User user : state.users) {
                if (user.name.equalsIgnoreCase(name)) {
                    System.out.println("An account with that name already exists.");
                    return;
                }
            }
            User user = new User(name, STARTING_CASH);
            state.users.add(user);
            state.currentUserIndex = state.users.size() - 1;
            user.recordSnapshot(state.market);
            System.out.printf(Locale.ROOT, "%s created with $%.2f simulated cash.%n", name, STARTING_CASH);
            return;
        }

        try {
            int accountNumber = Integer.parseInt(choice);
            if (accountNumber < 1 || accountNumber > state.users.size()) {
                System.out.println("That account number is not available.");
            } else {
                state.currentUserIndex = accountNumber - 1;
                System.out.println("Switched to " + state.currentUser().name + ".");
            }
        } catch (NumberFormatException exception) {
            System.out.println("Enter a listed account number or N.");
        }
    }

    private static int readPositiveQuantity(Scanner scanner) {
        try {
            int quantity = Integer.parseInt(readLine(scanner));
            return quantity > 0 ? quantity : 0;
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static String readLine(Scanner scanner) {
        return scanner.hasNextLine() ? scanner.nextLine().trim() : "";
    }

    private static PlatformState loadState() {
        try (ObjectInputStream input = new ObjectInputStream(new FileInputStream(DATA_FILE))) {
            PlatformState state = (PlatformState) input.readObject();
            if (state.market == null || state.users == null || state.users.isEmpty()) {
                throw new IOException("Saved data is incomplete.");
            }
            if (state.currentUserIndex < 0 || state.currentUserIndex >= state.users.size()) {
                state.currentUserIndex = 0;
            }
            System.out.println("Loaded saved accounts and portfolio history.");
            return state;
        } catch (IOException | ClassNotFoundException | ClassCastException exception) {
            return new PlatformState();
        }
    }

    private static void saveState(PlatformState state) {
        try (ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(DATA_FILE))) {
            output.writeObject(state);
        } catch (IOException exception) {
            System.out.println("Could not save data: " + exception.getMessage());
        }
    }

    private static double roundMoney(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }

    private static final class PlatformState implements Serializable {
        private static final long serialVersionUID = 1L;
        private final Market market = new Market();
        private final List<User> users = new ArrayList<>();
        private int currentUserIndex;

        private User currentUser() {
            return users.get(currentUserIndex);
        }
    }

    private static final class Market implements Serializable {
        private static final long serialVersionUID = 1L;
        private final Map<String, Stock> stocks = new LinkedHashMap<>();

        private Market() {
            addStock("AAPL", "Apple Inc.", 192.53);
            addStock("MSFT", "Microsoft Corp.", 415.32);
            addStock("NVDA", "NVIDIA Corp.", 875.28);
            addStock("AMZN", "Amazon.com Inc.", 178.22);
            addStock("TSLA", "Tesla Inc.", 172.63);
        }

        private void addStock(String ticker, String company, double price) {
            stocks.put(ticker, new Stock(ticker, company, price));
        }

        private Stock findStock(String ticker) {
            return stocks.get(ticker);
        }

        private Iterable<Stock> stocks() {
            return Collections.unmodifiableCollection(stocks.values());
        }

        private void advancePrices() {
            for (Stock stock : stocks.values()) {
                double movement = ThreadLocalRandom.current().nextDouble(-0.05, 0.050001);
                stock.price = Math.max(0.01, roundMoney(stock.price * (1 + movement)));
            }
        }
    }

    private static final class Stock implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String ticker;
        private final String company;
        private final double referencePrice;
        private double price;

        private Stock(String ticker, String company, double price) {
            this.ticker = ticker;
            this.company = company;
            this.price = price;
            this.referencePrice = price;
        }
    }

    private static final class User implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String name;
        private double cash;
        private final Map<String, Holding> holdings = new LinkedHashMap<>();
        private final List<Transaction> transactions = new ArrayList<>();
        private final List<PortfolioSnapshot> snapshots = new ArrayList<>();

        private User(String name, double startingCash) {
            this.name = name;
            this.cash = startingCash;
        }

        private boolean buy(Stock stock, int quantity) {
            double total = roundMoney(stock.price * quantity);
            if (total > cash) {
                return false;
            }

            Holding holding = holdings.get(stock.ticker);
            if (holding == null) {
                holdings.put(stock.ticker, new Holding(quantity, stock.price));
            } else {
                double oldCost = holding.averageCost * holding.quantity;
                holding.quantity += quantity;
                holding.averageCost = (oldCost + stock.price * quantity) / holding.quantity;
            }
            cash = roundMoney(cash - total);
            transactions.add(new Transaction("BUY", stock, quantity, total));
            return true;
        }

        private boolean sell(Stock stock, int quantity) {
            Holding holding = holdings.get(stock.ticker);
            if (holding == null || holding.quantity < quantity) {
                return false;
            }

            double total = roundMoney(stock.price * quantity);
            holding.quantity -= quantity;
            if (holding.quantity == 0) {
                holdings.remove(stock.ticker);
            }
            cash = roundMoney(cash + total);
            transactions.add(new Transaction("SELL", stock, quantity, total));
            return true;
        }

        private double holdingsValue(Market market) {
            double value = 0;
            for (Map.Entry<String, Holding> entry : holdings.entrySet()) {
                value += market.findStock(entry.getKey()).price * entry.getValue().quantity;
            }
            return value;
        }

        private void recordSnapshot(Market market) {
            snapshots.add(new PortfolioSnapshot(cash, holdingsValue(market)));
        }
    }

    private static final class Holding implements Serializable {
        private static final long serialVersionUID = 1L;
        private int quantity;
        private double averageCost;

        private Holding(int quantity, double averageCost) {
            this.quantity = quantity;
            this.averageCost = averageCost;
        }
    }

    private static final class Transaction implements Serializable {
        private static final long serialVersionUID = 1L;
        private final LocalDateTime recordedAt = LocalDateTime.now();
        private final String side;
        private final String ticker;
        private final int quantity;
        private final double price;
        private final double total;

        private Transaction(String side, Stock stock, int quantity, double total) {
            this.side = side;
            this.ticker = stock.ticker;
            this.quantity = quantity;
            this.price = stock.price;
            this.total = total;
        }
    }

    private static final class PortfolioSnapshot implements Serializable {
        private static final long serialVersionUID = 1L;
        private final LocalDateTime recordedAt = LocalDateTime.now();
        private final double cash;
        private final double holdingsValue;
        private final double totalValue;

        private PortfolioSnapshot(double cash, double holdingsValue) {
            this.cash = cash;
            this.holdingsValue = holdingsValue;
            this.totalValue = cash + holdingsValue;
        }
    }
}