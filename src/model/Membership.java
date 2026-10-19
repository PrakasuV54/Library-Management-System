package model;

import enums.MembershipStatus;
import java.time.LocalDate;

public class Membership {
    private final int membershipId;
    private int userId;
    private String userPhone;
    private int planId;
    private final LocalDate startDate;
    private LocalDate endDate;
    private MembershipStatus status;
    private final int paymentId;
    public Membership(int membershipId, String userPhone, int planId, LocalDate startDate, LocalDate endDate,
    MembershipStatus status, int paymentId) {
        if (userPhone == null || userPhone.isBlank())
            throw new IllegalArgumentException("User phone is required");
        if (startDate == null || endDate == null || endDate.isBefore(startDate))
            throw new IllegalArgumentException("Membership dates are invalid");
        if (status == null)
            throw new IllegalArgumentException("Membership status is required");
        this.membershipId = membershipId;
        this.userPhone = userPhone;
        this.planId = planId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        this.paymentId = paymentId;
    }

    public int getMembershipId() {
        return membershipId;
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

    public int getPlanId() {
        return planId;
    }

    public void setPlanId(int planId) {
        this.planId = planId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public MembershipStatus getStatus() {
        return status;
    }

    public int getPaymentId() {
        return paymentId;
    }

    public void setUserPhone(String phone) {
        if (phone == null || phone.isBlank()) throw new IllegalArgumentException("Phone is required");
        userPhone = phone;
    }

    public void extend(LocalDate newEndDate) {
        if (newEndDate == null || !newEndDate.isAfter(endDate)) {
            throw new IllegalArgumentException("New end date must be after the current end date");
        }
        if (status != MembershipStatus.ACTIVE) {
            throw new IllegalStateException("Only an active membership can be renewed");
        }
        endDate = newEndDate;
    }

    public void suspend() {
        if (status != MembershipStatus.ACTIVE) throw new IllegalStateException("Only an active membership can be suspended");
        status = MembershipStatus.SUSPENDED;
    }

    public void activate() {
        if (status != MembershipStatus.SUSPENDED) throw new IllegalStateException("Only a suspended membership can be reactivated");
        if (LocalDate.now().isAfter(endDate)) throw new IllegalStateException("Expired membership cannot be activated");
        status = MembershipStatus.ACTIVE;
    }

    public void expire() {
        if (status == MembershipStatus.CANCELLED) throw new IllegalStateException("Cancelled membership cannot be expired");
        status = MembershipStatus.EXPIRED;
    }

    public void cancel() {
        if (status != MembershipStatus.ACTIVE && status != MembershipStatus.SUSPENDED) throw new IllegalStateException("Membership cannot be cancelled from its current status");
        status = MembershipStatus.CANCELLED;
    }

    public boolean isActive() {
        LocalDate today = LocalDate.now();
        return status == MembershipStatus.ACTIVE && !today.isBefore(startDate) && !today.isAfter(endDate);
    }
}
