package service;

import enums.MembershipStatus;
import enums.PaymentMethod;
import enums.UserStatus;
import model.Membership;
import model.MembershipPlan;
import model.Payment;
import model.User;
import paymentStrategy.PaymentStrategy;
import repository.DataStore;
import repository.MembershipRepository;
import repository.PaymentRepository;
import repository.UserRepository;
import java.time.LocalDate;

public class  MembershipService {
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final MembershipPlanService membershipPlanService;
    private final PaymentRepository paymentRepository;
    private final PaymentStrategy paymentStrategy;
    private final AuditService auditService;
    private final NotificationService notificationService;


    public MembershipService(UserRepository users, MembershipRepository memberships, MembershipPlanService plans,
    PaymentRepository payments, PaymentStrategy strategy, AuditService auditService,
    NotificationService notificationService) {
        this.userRepository=users;
        this.membershipRepository=memberships;
        this.membershipPlanService=plans;
        this.paymentRepository=payments;
        this.paymentStrategy=strategy;
        this.auditService=auditService;
        this.notificationService=notificationService;
        if (this.notificationService != null && this.notificationService.getUserRepository() == null && users != null) {
            this.notificationService.setUserRepository(users);
        }
    }

    private void sendUserNotification(String phone, String message) {
        if (notificationService == null || phone == null || phone.isBlank()) return;
        String trimmed = phone.trim();
        User user = (userRepository != null) ? userRepository.findByPhone(trimmed) : null;
        if (user == null && userRepository != null && trimmed.matches("\\d+")) {
            try {
                user = userRepository.findById(Integer.parseInt(trimmed));
            } catch (NumberFormatException ignored) {}
        }
        if (user != null && user.getName() != null && !user.getName().isBlank()) {
            notificationService.send(user.getName(), user.getPhone(), message);
        } else {
            notificationService.send(trimmed, message);
        }
    }

    private void sendUserNotification(Membership membership, String message) {
        if (notificationService == null || membership == null) return;
        User user = null;
        String phone = membership.getUserPhone();
        if (userRepository != null) {
            if (phone != null && !phone.isBlank()) {
                user = userRepository.findByPhone(phone.trim());
            }
            if (user == null && membership.getUserId() > 0) {
                user = userRepository.findById(membership.getUserId());
            }
        }
        if (user != null && user.getName() != null && !user.getName().isBlank()) {
            notificationService.send(user.getName(), user.getPhone(), message);
        } else if (membership.getUserId() > 0 && phone != null && !phone.isBlank()) {
            notificationService.send(String.valueOf(membership.getUserId()), phone, message);
        } else if (phone != null && !phone.isBlank()) {
            notificationService.send(phone, message);
        } else if (membership.getUserId() > 0) {
            notificationService.send(String.valueOf(membership.getUserId()), message);
        }
    }


    public boolean getMembershipByPhone(String phone, int planId, int durationInMonths, PaymentMethod method) {
        return purchaseMembership(phone, planId, durationInMonths, method);
    }

    public boolean getMembershipByPhone(String phone, int planId, int durationInMonths, PaymentMethod method, User caller) {
        if (caller == null || !isOwnerOrStaff(caller, phone)) return false;
        return purchaseMembership(phone, planId, durationInMonths, method);
    }

    public Membership getMembershipByPhone(String phone, User caller) {
        if (caller == null || !isOwnerOrStaff(caller, phone)) {
            return null;
        }
        return membershipRepository.findActiveByUserPhone(phone);
    }

    public Membership getMembershipByPhoneInternal(String phone) {
        if (phone == null || phone.isBlank()) return null;
        return membershipRepository.findActiveByUserPhone(phone.trim());
    }

    public Membership getMembershipByPhone(String phone) {
        return getMembershipByPhoneInternal(phone);
    }

    private boolean purchaseMembership(String phone, int planId, int durationInMonths, PaymentMethod method) {
        if (phone==null||phone.isBlank()||durationInMonths<=0||method==null) return false;
        User userResult=userRepository.findByPhone(phone);
        if(userResult == null||userResult.getStatus()!=UserStatus.ACTIVE) return false;
        MembershipPlan planResult=membershipPlanService.findById(planId);
        if(planResult == null || planResult.getAmount() <= 0) return false;
        for (Membership previous : membershipRepository.findByUserPhone(phone)) {
            if (previous.getStatus() == MembershipStatus.ACTIVE && LocalDate.now().isAfter(previous.getEndDate())) {
                previous.expire();
                membershipRepository.save(previous);
            }
        }
        if (membershipRepository.findActiveByUserPhone(phone) != null) return false;
        int membershipId=DataStore.getMembershipId();
        int paymentId=DataStore.getPaymentId();
        double totalCost = planResult.getAmount() * durationInMonths;
        Payment payment=Payment.forMembership(paymentId,membershipId,phone,totalCost,method);
        paymentRepository.save(payment);
        int gatewayReference;
        try {
            gatewayReference = paymentStrategy.pay(totalCost);
        } catch (RuntimeException ex) {
            payment.markFailed();
            paymentRepository.save(payment);
            return false;
        }
        if (gatewayReference <= 0) {
            payment.markFailed();
            paymentRepository.save(payment);
            return false;
        }

        LocalDate start = LocalDate.now();
        Membership membership = new Membership(membershipId, phone, planId, start,
                start.plusMonths(durationInMonths), MembershipStatus.ACTIVE, paymentId);
        payment.markSuccess("GATEWAY-" + gatewayReference);
        paymentRepository.save(payment);
        membershipRepository.save(membership);
        if (auditService != null) auditService.record(phone, "MEMBERSHIP_PURCHASED", "MEMBERSHIP", String.valueOf(membershipId), "Plan ID " + planId + ", payment ID " + paymentId);
        sendUserNotification(membership, "Membership activated until " + membership.getEndDate());
        return true;
    }

    public boolean renewMembership(String phone, int planId, int durationInMonths, PaymentMethod method) {
        return renewMembershipForPhone(phone, planId, durationInMonths, method);
    }

    public boolean renewMembership(String phone, int planId, int durationInMonths, PaymentMethod method, User caller) {
        if (caller == null || !isOwnerOrStaff(caller, phone)) return false;
        return renewMembershipForPhone(phone, planId, durationInMonths, method);
    }

    public boolean renewMembership(String phone, int durationInMonths, User caller) {
        if (caller == null || !isOwnerOrStaff(caller, phone)) return false;
        if (phone == null || phone.isBlank() || durationInMonths <= 0) return false;
        Membership membership = membershipRepository.findActiveByUserPhone(phone);
        if (membership == null) return false;
        return renewMembershipForPhone(phone, membership.getPlanId(), durationInMonths, PaymentMethod.UPI);
    }

    public boolean renewMembershipInternal(String phone, int durationInMonths) {
        if (phone == null || phone.isBlank() || durationInMonths <= 0) return false;
        Membership membership = membershipRepository.findActiveByUserPhone(phone);
        if (membership == null) return false;
        return renewMembershipForPhone(phone, membership.getPlanId(), durationInMonths, PaymentMethod.UPI);
    }

    private boolean renewMembershipForPhone(String phone, int planId, int durationInMonths, PaymentMethod method) {
        if (phone == null || phone.isBlank() || durationInMonths <= 0 || method == null) return false;
        User user = userRepository.findByPhone(phone);
        Membership membership = membershipRepository.findActiveByUserPhone(phone);
        MembershipPlan plan = membershipPlanService.findById(planId);
        if (user == null || user.getStatus() != UserStatus.ACTIVE || membership == null || plan == null || plan.getAmount() <= 0)
            return false;
        double totalCost = plan.getAmount() * durationInMonths;
        int paymentId = DataStore.getPaymentId();
        Payment payment = Payment.forMembership(paymentId, membership.getMembershipId(), phone, totalCost, method);
        paymentRepository.save(payment);
        int gatewayReference;
        try {
            gatewayReference = paymentStrategy.pay(totalCost);
        } catch (RuntimeException ex) {
            payment.markFailed();
            paymentRepository.save(payment);
            return false;
        }
        if (gatewayReference <= 0) {
            payment.markFailed();
            paymentRepository.save(payment);
            return false;
        }
        LocalDate baseDate = membership.getEndDate().isAfter(LocalDate.now()) ? membership.getEndDate() : LocalDate.now();
        LocalDate newEndDate = baseDate.plusMonths(durationInMonths);
        payment.markSuccess("GATEWAY-" + gatewayReference);
        paymentRepository.save(payment);
        membership.extend(newEndDate);
        membership.setPlanId(planId);
        membershipRepository.save(membership);
        if (auditService != null)
            auditService.record(phone, "MEMBERSHIP_RENEWED", "MEMBERSHIP", String.valueOf(membership.getMembershipId()), "Payment ID " + paymentId + ", new end date " + newEndDate);
        sendUserNotification(membership, "Membership renewed until " + newEndDate);
        return true;
    }

    public boolean suspendMembership(User admin, String phone) {
        if (!isActiveStaff(admin) || phone == null || phone.isBlank())
            return false;
        Membership membership = membershipRepository.findActiveByUserPhone(phone);
        if (membership == null)
            return false;
        membership.suspend();
        membershipRepository.save(membership);
        if (auditService != null)
            auditService.record(admin.getPhone(), "MEMBERSHIP_SUSPENDED", "MEMBERSHIP", String.valueOf(membership.getMembershipId()), "Member phone " + phone);
        return true;
    }

    public boolean reactivateMembership(User admin, String phone) {
        if (!isActiveStaff(admin) || phone == null || phone.isBlank())
            return false;
        Membership membership = null;
        for (Membership candidate : membershipRepository.findByUserPhone(phone)) {
            if (candidate.getStatus() == enums.MembershipStatus.SUSPENDED) {
                membership = candidate;
                break;
            }
        }
        if (membership == null)
            return false;
        if (membership.getEndDate() != null && java.time.LocalDate.now().isAfter(membership.getEndDate()))
            return false;
        membership.activate();
        membershipRepository.save(membership);
        if (auditService != null) auditService.record(admin.getPhone(), "MEMBERSHIP_REACTIVATED", "MEMBERSHIP", String.valueOf(membership.getMembershipId()), "Member phone " + phone);
        return true;
    }

    private boolean isOwnerOrStaff(User caller, String targetPhone) {
        if (caller == null || caller.getPhone() == null || targetPhone == null || caller.getStatus() != UserStatus.ACTIVE) return false;
        User stored = userRepository.findByPhone(caller.getPhone());
        if (stored == null || stored.getUserId() != caller.getUserId() || stored.getStatus() != UserStatus.ACTIVE) return false;
        if (stored.getPhone().equals(targetPhone.trim())) return true;
        return stored.getRole() == enums.UserRole.ADMIN || stored.getRole() == enums.UserRole.LIBRARIAN;
    }

    private boolean isActiveStaff(User staff) {
        if (staff == null || staff.getStatus() != UserStatus.ACTIVE || (staff.getRole() != enums.UserRole.ADMIN && staff.getRole() != enums.UserRole.LIBRARIAN)) return false;
        User stored = userRepository.findByPhone(staff.getPhone());
        return stored != null && stored.getUserId() == staff.getUserId() && stored.getRole() == staff.getRole() && stored.getStatus() == UserStatus.ACTIVE;
    }

    public Membership getActiveMembership(String phone, User caller) {
        if (caller == null || !isOwnerOrStaff(caller, phone)) {
            return null;
        }
        return membershipRepository.findActiveByUserPhone(phone);
    }

    public Membership getActiveMembershipInternal(String phone) {
        if (phone == null || phone.isBlank()) return null;
        return membershipRepository.findActiveByUserPhone(phone.trim());
    }

    public Membership getActiveMembership(String phone) {
        return getActiveMembershipInternal(phone);
    }

    public String getPurchaseFailureReason(String phone, int planId, int durationInMonths, PaymentMethod method, User caller) {
        if (caller == null || !isOwnerOrStaff(caller, phone)) {
            return "Unauthorized: You can only activate memberships for your own account.";
        }
        if (phone == null || phone.isBlank()) {
            return "Phone number is required.";
        }
        User user = userRepository.findByPhone(phone.trim());
        if (user == null) {
            return "User account not found with phone " + phone + ".";
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            return "Account is inactive. Please contact library staff.";
        }
        MembershipPlan plan = membershipPlanService.findById(planId);
        if (plan == null) {
            return "Invalid membership plan ID: " + planId + ".";
        }
        if (durationInMonths <= 0) {
            return "Duration must be greater than zero months.";
        }
        if (method == null) {
            return "Payment method is required.";
        }
        Membership active = membershipRepository.findActiveByUserPhone(phone.trim());
        if (active != null) {
            return "You already have an active membership (Plan ID " + active.getPlanId() + ", expires on " + active.getEndDate() + "). Use Renew Membership to extend.";
        }
        return null;
    }

    public String getRenewFailureReason(String phone, int planId, int durationInMonths, PaymentMethod method, User caller) {
        if (caller == null || !isOwnerOrStaff(caller, phone)) {
            return "Unauthorized: You can only renew memberships for your own account.";
        }
        if (phone == null || phone.isBlank()) {
            return "Phone number is required.";
        }
        User user = userRepository.findByPhone(phone.trim());
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            return "User account is not active.";
        }
        Membership active = membershipRepository.findActiveByUserPhone(phone.trim());
        if (active == null) {
            return "You do not have an active membership to renew. Please use Activate Membership to purchase a plan.";
        }
        MembershipPlan plan = membershipPlanService.findById(planId);
        if (plan == null) {
            return "Invalid membership plan ID: " + planId + ".";
        }
        if (durationInMonths <= 0) {
            return "Duration must be greater than zero months.";
        }
        if (method == null) {
            return "Payment method is required.";
        }
        return null;
    }

    public String getSuspendFailureReason(User admin, String phone) {
        if (!isActiveStaff(admin)) {
            return "Insufficient permissions. Staff authorization required.";
        }
        if (phone == null || phone.isBlank()) {
            return "Member phone number is required.";
        }
        Membership active = membershipRepository.findActiveByUserPhone(phone.trim());
        if (active == null) {
            return "Member does not have an active membership to suspend.";
        }
        return null;
    }

    public String getReactivateFailureReason(User admin, String phone) {
        if (!isActiveStaff(admin)) {
            return "Insufficient permissions. Staff authorization required.";
        }
        if (phone == null || phone.isBlank()) {
            return "Member phone number is required.";
        }
        Membership suspended = null;
        for (Membership candidate : membershipRepository.findByUserPhone(phone.trim())) {
            if (candidate.getStatus() == enums.MembershipStatus.SUSPENDED) {
                suspended = candidate;
                break;
            }
        }
        if (suspended == null) {
            return "No suspended membership found for this member.";
        }
        if (suspended.getEndDate() != null && LocalDate.now().isAfter(suspended.getEndDate())) {
            return "The suspended membership has already expired on " + suspended.getEndDate() + ". The member must purchase a new membership.";
        }
        return null;
    }
}
