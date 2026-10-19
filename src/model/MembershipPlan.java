package model;

import java.util.Locale;

public class MembershipPlan {
    private final int planId;
    private final String planName;
    private final double amount;
    private final int maxBooks;

    public MembershipPlan(int planId, String planName, double amount) {
        this(planId, planName, amount, resolveDefaultMaxBooks(planName));
    }

    public MembershipPlan(int planId, String planName, double amount, int maxBooks) {
        if (planName == null || planName.isBlank())
            throw new IllegalArgumentException("Plan name cannot be blank");
        if (amount <= 0)
            throw new IllegalArgumentException("Plan amount must be positive");
        this.planId = planId;
        this.planName = planName.trim();
        this.amount = amount;
        this.maxBooks = maxBooks > 0 ? maxBooks : resolveDefaultMaxBooks(planName);
    }

    public static int resolveDefaultMaxBooks(String planName) {
        if (planName == null) return 3;
        String lower = planName.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("family") && lower.contains("premium")) {
            return 15;
        } else if (lower.contains("family")) {
            return 12;
        } else if (lower.contains("premium")) {
            return 5;
        } else {
            return 3;
        }
    }

    public int getPlanId() {
        return planId;
    }

    public String getPlanName() {
        return planName;
    }

    public double getAmount() {
        return amount;
    }

    public int getMaxBooks() {
        return maxBooks;
    }

    public int getMaxIssuedBooks() {
        return maxBooks;
    }
}
