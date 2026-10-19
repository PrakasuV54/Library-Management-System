package service;

import enums.PaymentMethod;
import model.Fine;
import model.Payment;
import paymentStrategy.PaymentStrategy;
import repository.DataStore;
import repository.*;
import repository.PaymentRepository;

public class PaymentService {
    private final PaymentRepository payments;
    private final FineRepository fines;
    private final PaymentStrategy strategy;
    private final AuditService auditService;
    private final MembershipRepository memberships;
    private final TransactionRepository transactions;

    public PaymentService(PaymentRepository payments,FineRepository fines,PaymentStrategy strategy,AuditService auditService,
    repository.MembershipRepository memberships,repository.TransactionRepository transactions) {
        this.payments=payments;
        this.fines=fines;
        this.strategy=strategy;
        this.auditService=auditService;
        this.memberships=memberships;
        this.transactions=transactions;
    }

    public Payment payFine(String phone,int fineId,double amount,PaymentMethod method) {
        if(phone==null||phone.isBlank()||amount<=0||method==null)
            return null;
        Fine fine=fines.findById(fineId);
        if(fine==null||!fine.getUserPhone().equals(phone)||!fine.isPending()||amount>fine.getRemainingAmount())
            return null;
        Payment payment=new Payment(DataStore.getPaymentId(),fineId,phone,amount,method);
        payments.save(payment);
        String reference = String.valueOf(strategy.pay(amount));
        payment.markSuccess(reference);
        fine.applyPayment(amount);
        payments.save(payment);
        fines.save(fine);
        if (auditService != null) auditService.record(phone, "FINE_PAYMENT_SUCCEEDED", "PAYMENT", String.valueOf(payment.getPaymentId()), "Fine ID " + fineId + ", amount " + amount);
        return payment;
    }

    public boolean refund(int paymentId, model.User staff) {
        if (staff == null || staff.getStatus() != enums.UserStatus.ACTIVE || (staff.getRole() != enums.UserRole.LIBRARIAN && staff.getRole() != enums.UserRole.ADMIN)) {
            return false;
        }
        Payment p=payments.findById(paymentId);
        if(p==null||!p.isSuccessful())
            return false;
        Fine fine=null;
        model.Membership membership=null;
        fine=fines.findById(p.getFineId());
        if(fine==null)
            return false;

        String reference = p.getTransactionReference();
        if (reference == null )
            return false;
        int gatewayReference;
        try {
            gatewayReference = Integer.parseInt(reference);
            strategy.refund(gatewayReference);
        } catch (RuntimeException ex) {
            return false;
        }
        p.refund();
        payments.save(p);
        if (auditService != null) auditService.record(staff.getPhone(), "PAYMENT_REFUNDED", "PAYMENT", String.valueOf(paymentId), "Payment target ID " + (p.getFineId() > 0 ? p.getFineId() : p.getMembershipId()));
        return true;
    }


    public Payment findById(int paymentId) {
        return payments.findById(paymentId);
    }

    public String getPayFineFailureReason(String phone, int fineId, double amount, PaymentMethod method) {
        if (phone == null || phone.isBlank()) {
            return "Phone number is required.";
        }
        if (amount <= 0) {
            return "Payment amount must be greater than zero.";
        }
        if (method == null) {
            return "Payment method is required.";
        }
        Fine fine = fines.findById(fineId);
        if (fine == null) {
            return "Fine not found with ID " + fineId + ".";
        }
        if (!fine.getUserPhone().equals(phone.trim())) {
            return "Unauthorized: Fine does not belong to the specified phone number.";
        }
        if (fine.getStatus() == enums.FineStatus.PAID) {
            return "Fine is already fully paid.";
        }
        if (fine.getStatus() == enums.FineStatus.WAIVED) {
            return "Fine has been waived and cannot be paid.";
        }
        if (amount > fine.getRemainingAmount()) {
            return "Payment amount (Rs." + String.format(java.util.Locale.ROOT, "%.2f", amount)
                    + ") exceeds outstanding balance of Rs."
                    + String.format(java.util.Locale.ROOT, "%.2f", fine.getRemainingAmount()) + ".";
        }
        return null;
    }

    public String getRefundFailureReason(int paymentId, model.User staff) {
        if (staff == null || staff.getStatus() != enums.UserStatus.ACTIVE || (staff.getRole() != enums.UserRole.LIBRARIAN && staff.getRole() != enums.UserRole.ADMIN)) {
            return "Insufficient permissions. Staff authorization required.";
        }
        Payment p = payments.findById(paymentId);
        if (p == null) {
            return "Payment with ID " + paymentId + " not found.";
        }
        if (p.getStatus() == enums.PaymentStatus.REFUNDED) {
            return "Payment has already been refunded.";
        }
        if (!p.isSuccessful()) {
            return "Only successful payments can be refunded (Current status: " + p.getStatus() + ").";
        }
        Fine fine = fines.findById(p.getFineId());
        if (fine == null) {
            return "Associated fine record not found.";
        }
        return null;
    }
}
