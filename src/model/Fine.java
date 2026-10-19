package model;

import enums.FineStatus;
import enums.FineType;
import java.time.LocalDateTime;

public class Fine {
    private final int fineId;
    private int userId;
    private String userPhone;
    private final int transactionId;
    private final FineType type;
    private double amount;
    private double amountPaid;
    private FineStatus status;
    private final LocalDateTime createdAt;
    public Fine(int fineId, String userPhone, int transactionId, double amount) {
        this(fineId, userPhone, transactionId, amount, FineType.OVERDUE);
    }

    public Fine(int fineId, String userPhone, int transactionId, double amount, FineType type) {
        if (userPhone == null || userPhone.isBlank())
            throw new IllegalArgumentException("User phone is required");
        if (amount <= 0)
            throw new IllegalArgumentException("Fine amount must be positive");
        this.fineId=fineId;
        this.userPhone=userPhone;
        this.transactionId=transactionId;
        if (type == null) throw new IllegalArgumentException("Fine type is required");
        this.type = type;
        this.amount=amount;
        this.status=FineStatus.PENDING;
        this.createdAt=LocalDateTime.now();
    }

    public int getFineId() {
        return fineId;
    }

    public int getUserId() {
        return userId;
    }

    /** Set by the repository after resolving the user contact to its stable ID. */
    public void assignUserId(int userId) {
        if (userId <= 0) throw new IllegalArgumentException("User ID must be positive");
        if (this.userId != 0 && this.userId != userId) throw new IllegalStateException("User ID cannot be changed");
        this.userId = userId;
    }

    public String getUserPhone() {
        return userPhone;
    }

    public int getTransactionId() {
        return transactionId;
    }

    public FineType getType() {
        return type;
    }

    public double getAmount() {
        return amount;
    }

    public double getAmountPaid() {
        return amountPaid;
    }

    public double getRemainingAmount() {
        return status == FineStatus.WAIVED ? 0 : Math.max(0, amount-amountPaid);
    }

    public FineStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setUserPhone(String phone) {
        if(phone==null||phone.isBlank())throw new IllegalArgumentException("Phone is required");
        userPhone=phone;
    }

    public void applyPayment(double paymentAmount) {
        if (!isPending()) throw new IllegalStateException("Fine is closed");
        if (paymentAmount <= 0 || paymentAmount > getRemainingAmount()) throw new IllegalArgumentException("Payment exceeds fine balance or is invalid");
        amountPaid += paymentAmount;
        status = amountPaid >= amount ? FineStatus.PAID : FineStatus.PARTIALLY_PAID;
    }

    public void reversePayment(double paymentAmount) {
        if (status == FineStatus.WAIVED || paymentAmount <= 0 || paymentAmount > amountPaid)
        throw new IllegalArgumentException("Cannot reverse this fine payment");
        amountPaid = Math.max(0, amountPaid - paymentAmount);
        status = amountPaid <= 0 ? FineStatus.PENDING : FineStatus.PARTIALLY_PAID;
    }

    public void markPaid() {
        if(!isPending())throw new IllegalStateException("Fine is already closed");
        amountPaid=amount;
        status=FineStatus.PAID;
    }

    public void waive() {
        if(status==FineStatus.PAID)throw new IllegalStateException("Paid fine cannot be waived");
        status=FineStatus.WAIVED;
    }

    public boolean isPending() {
        return status==FineStatus.PENDING||status==FineStatus.PARTIALLY_PAID;
    }

    public void updateOverdueAmount(double newAmount) {
        if (this.status == FineStatus.WAIVED) {
            return;
        }
        if (newAmount <= this.amount) {
            return;
        }
        this.amount = newAmount;
        if (this.amountPaid >= this.amount) {
            this.status = FineStatus.PAID;
        } else if (this.amountPaid > 0) {
            this.status = FineStatus.PARTIALLY_PAID;
        } else {
            this.status = FineStatus.PENDING;
        }
    }
}
