package net.crafter.plugin.core.model;

import java.util.ArrayList;
import java.util.List;

public class StatisticsData {
    private LatestStatistics latest = new LatestStatistics();
    private List<TopLoader> topCreditLoaders = new ArrayList<>();
    private int totalUsers;

    public LatestStatistics getLatest() { return latest; }
    public void setLatest(LatestStatistics latest) { this.latest = latest; }

    public List<TopLoader> getTopCreditLoaders() { return topCreditLoaders; }
    public void setTopCreditLoaders(List<TopLoader> topCreditLoaders) { this.topCreditLoaders = topCreditLoaders; }

    public int getTotalUsers() { return totalUsers; }
    public void setTotalUsers(int totalUsers) { this.totalUsers = totalUsers; }

    public static class LatestStatistics {
        private List<Purchase> purchases = new ArrayList<>();
        private List<Payment> payments = new ArrayList<>();
        private List<Signup> signups = new ArrayList<>();

        public List<Purchase> getPurchases() { return purchases; }
        public void setPurchases(List<Purchase> purchases) { this.purchases = purchases; }

        public List<Payment> getPayments() { return payments; }
        public void setPayments(List<Payment> payments) { this.payments = payments; }

        public List<Signup> getSignups() { return signups; }
        public void setSignups(List<Signup> signups) { this.signups = signups; }
    }

    public static class Purchase {
        private String username;
        private String productName;
        private double amount;
        private String timestamp;

        public String getUsername() { return username; }
        public String getProductName() { return productName; }
        public double getAmount() { return amount; }
        public String getTimestamp() { return timestamp; }
    }

    public static class Payment {
        private String username;
        private double amount;
        private String paymentMethod;
        private String timestamp;

        public String getUsername() { return username; }
        public double getAmount() { return amount; }
        public String getPaymentMethod() { return paymentMethod; }
        public String getTimestamp() { return timestamp; }
    }

    public static class Signup {
        private String username;
        private String timestamp;

        public String getUsername() { return username; }
        public String getTimestamp() { return timestamp; }
    }

    public static class TopLoader {
        private String username;
        private double totalAmount;

        public String getUsername() { return username; }
        public double getTotalAmount() { return totalAmount; }
    }
}
