package net.crafter.plugin.core.model;

public class PlayerData {
    private String username;
    private String id;
    private Role role;
    private double balance;

    public static class Role {
        private String id;
        private String name;
        private String color;

        public String getId() { return id; }
        public String getName() { return name; }
        public String getColor() { return color; }
    }

    public PlayerData() {}

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }
}
