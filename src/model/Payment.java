package model;

import enums.PaymentMethod;
import enums.PaymentStatus;
import java.time.LocalDateTime;

public class Payment {
    private final int paymentId;
    private final int fineId;
    private final int membershipId;
    private int userId;
    private String userPhone;
    private final double amount;
    private final PaymentMethod paymentMethod;
    private PaymentStatus status;
    private String transactionReference;
    private final LocalDateTime createdAt;
    public Payment(int paymentId, int fineId, String userPhone, double amount, PaymentMethod method) {
        this(paymentId, fineId, 0, userPhone, amount, method);
    }

    public static Payment forMembership(int paymentId, int membershipId, String userPhone, double amount, PaymentMethod method) {
        return new Payment(paymentId, 0, membershipId, userPhone, amount, method);
    }

    private Payment(int paymentId, int fineId, int membershipId, String userPhone, double amount, PaymentMethod method) {
        if (userPhone == null || userPhone.isBlank())
            throw new IllegalArgumentException("User phone is required");
        if (amount <= 0)
            throw new IllegalArgumentException("Payment amount must be positive");
        if (method == null)
            throw new IllegalArgumentException("Payment method is required");
        this.paymentId = paymentId;
        this.fineId = fineId;
        this.membershipId = membershipId;
        this.userPhone = userPhone;
        this.amount = amount;
        this.paymentMethod = method;
        this.status = PaymentStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public int getPaymentId() {
        return paymentId;
    }

    public int getFineId() {
        return fineId;
    }

    public int getMembershipId() {
        return membershipId;
    }

    public boolean isMembershipPayment() {
        return membershipId > 0;
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

    public double getAmount() {
        return amount;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setUserPhone(String phone) {
        if (phone == null || phone.isBlank()) throw new IllegalArgumentException("Phone is required");
        userPhone = phone;
    }

    public void markSuccess(String reference) {
        if (status != PaymentStatus.PENDING) throw new IllegalStateException("Only pending payments can succeed");
        if (reference == null || reference.isBlank()) throw new IllegalArgumentException("Payment reference is required");
        transactionReference = reference;
        status = PaymentStatus.SUCCESS;
    }

    public void markFailed() {
        if (status != PaymentStatus.PENDING) throw new IllegalStateException("Only pending payments can fail");
        status = PaymentStatus.FAILED;
    }

    public void refund() {
        if (status != PaymentStatus.SUCCESS) throw new IllegalStateException("Only successful payments can be refunded");
        status = PaymentStatus.REFUNDED;
    }

    public boolean isSuccessful() {
        return status == PaymentStatus.SUCCESS;
    }

    public boolean isFailed() {
        return status == PaymentStatus.FAILED;
    }
}
