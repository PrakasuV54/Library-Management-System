package service;

import enums.TransactionStatus;
import enums.UserRole;
import enums.UserStatus;
import model.Fine;
import model.MembershipPlan;
import model.Transaction;
import model.User;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import repository.FineRepository;
import repository.MembershipPlanRepository;
import repository.MembershipRepository;
import repository.TransactionRepository;
import repository.UserRepository;

public class PolicyService {
    private static final int DEFAULT_MAX_BOOKS = 3;
    private static final int DEFAULT_LOAN_DAYS = 14;
    private static final double DEFAULT_FINE_PER_DAY = 10.0;
    private static final double DEFAULT_LOST_BOOK_CHARGE = 500.0;
    private static final double DEFAULT_DAMAGED_BOOK_CHARGE = 100.0;

    private final UserRepository users;
    private final MembershipRepository memberships;
    private final TransactionRepository transactions;
    private final FineRepository fines;
    private final AuditService auditService;
    private MembershipPlanRepository planRepository;

    private int maxBooksBorrowed = DEFAULT_MAX_BOOKS;
    private int loanPeriodDays = DEFAULT_LOAN_DAYS;
    private double finePerDay = DEFAULT_FINE_PER_DAY;
    private double lostBookCharge = DEFAULT_LOST_BOOK_CHARGE;
    private double damagedBookCharge = DEFAULT_DAMAGED_BOOK_CHARGE;

    public PolicyService(UserRepository users, MembershipRepository memberships,
                         TransactionRepository transactions, FineRepository fines) {
        this(users, memberships, transactions, fines, null, null);
    }

    public PolicyService(UserRepository users, MembershipRepository memberships,
                         TransactionRepository transactions, FineRepository fines,
                         AuditService auditService) {
        this(users, memberships, transactions, fines, auditService, null);
    }

    public PolicyService(UserRepository users, MembershipRepository memberships,
                         TransactionRepository transactions, FineRepository fines,
                         AuditService auditService, MembershipPlanRepository planRepository) {
        this.users = users;
        this.memberships = memberships;
        this.transactions = transactions;
        this.fines = fines;
        this.auditService = auditService;
        this.planRepository = planRepository;
    }

    public void setMembershipPlanRepository(MembershipPlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    public int getMaxBooksBorrowed() {
        return maxBooksBorrowed;
    }

    public int getLoanPeriodDays() {
        return loanPeriodDays;
    }

    public double getFinePerDay() {
        return finePerDay;
    }

    public double getLostBookCharge() {
        return lostBookCharge;
    }

    public double getDamagedBookCharge() {
        return damagedBookCharge;
    }

    public boolean canManageInventory(User authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.getStatus() != UserStatus.ACTIVE) return false;
        User currentUser = users.findByPhone(authenticatedUser.getPhone());
        return currentUser != null && currentUser.getUserId() == authenticatedUser.getUserId() && currentUser.getStatus() == UserStatus.ACTIVE && (currentUser.getRole() == UserRole.LIBRARIAN || currentUser.getRole() == UserRole.ADMIN);
    }

    public boolean updateLostBookCharge(User admin, double amount) {
        if (!isAdmin(admin) || amount < 0)
            return false;
        double oldAmount = lostBookCharge;
        lostBookCharge = amount;
        recordPolicyChange(admin.getPhone(), "LOST_BOOK_CHARGE_CHANGED", oldAmount + " -> " + amount);
        return true;
    }

    public boolean updateDamagedBookCharge(User admin, double amount) {
        if (!isAdmin(admin) || amount < 0)
            return false;
        double oldAmount = damagedBookCharge;
        damagedBookCharge = amount;
        recordPolicyChange(admin.getPhone(), "DAMAGED_BOOK_CHARGE_CHANGED", oldAmount + " -> " + amount);
        return true;
    }

    public boolean updateMaxBooksBorrowed(User admin, int limit) {
        if (!isAdmin(admin) || limit < 1)
            return false;
        int oldLimit = maxBooksBorrowed;
        maxBooksBorrowed = limit;
        recordPolicyChange(admin.getPhone(), "BORROWING_LIMIT_CHANGED", oldLimit + " -> " + limit);
        return true;
    }

    public boolean updateLoanPeriodDays(User admin, int days) {
        if (!isAdmin(admin) || days < 1)
            return false;
        int oldDays = loanPeriodDays;
        loanPeriodDays = days;
        recordPolicyChange(admin.getPhone(), "LOAN_PERIOD_CHANGED", oldDays + " -> " + days + " days");
        return true;
    }

    public boolean updateFinePerDay(User admin, double amount) {
        if (!isAdmin(admin) || amount < 0)
            return false;
        double oldAmount = finePerDay;
        finePerDay = amount;
        recordPolicyChange(admin.getPhone(), "FINE_RATE_CHANGED", oldAmount + " -> " + amount + " per day");
        return true;
    }

    private boolean isAdmin(User admin) {
        if (admin == null || admin.getStatus() != UserStatus.ACTIVE || admin.getRole() != UserRole.ADMIN) return false;
        User currentUser = users.findByPhone(admin.getPhone());
        return currentUser != null && currentUser.getUserId() == admin.getUserId() && currentUser.getStatus() == UserStatus.ACTIVE && currentUser.getRole() == UserRole.ADMIN;
    }

    private void recordPolicyChange(String adminPhone, String action, String details) {
        if (auditService != null) {
            auditService.record(adminPhone, action, "LIBRARY_POLICY", "1", details);
        }
    }

    public boolean hasActiveMembership(String phone) {
        if (phone == null || phone.isBlank()) return false;
        model.Membership membership = memberships.findActiveByUserPhone(phone);
        return membership != null && membership.getEndDate() != null
                && !LocalDate.now().isAfter(membership.getEndDate());
    }

    public int countIssuedBooks(String phone) {
        int count = 0;
        for (Transaction transaction : transactions.findByUserPhone(phone)) {
            if (transaction.getStatus() == TransactionStatus.ISSUED) {
                count++;
            }
        }
        return count;
    }

    public boolean hasOutstandingFine(String phone) {
        for (Fine fine : fines.findByUserPhone(phone)) {
            if (fine.isPending()) {
                return true;
            }
        }
        return false;
    }

    public boolean isDefaulter(String phone) {
        if (phone == null || phone.isBlank()) return false;

        if (hasOutstandingFine(phone)) return true;

        for (Transaction transaction : transactions.findByUserPhone(phone)) {
            if (transaction.getStatus() == TransactionStatus.ISSUED &&  LocalDate.now().isAfter(transaction.getDueDate())) {
                return true;
            }
        }
        return false;
    }

    public boolean isStaff(User user) {
        return canManageInventory(user);
    }



    public boolean isOwnerOrStaff(User caller, String targetPhone) {
        if (caller == null || caller.getPhone() == null || targetPhone == null
                || caller.getStatus() != UserStatus.ACTIVE) return false;


        User stored = users.findByPhone(caller.getPhone());
        if (stored == null || stored.getUserId() != caller.getUserId()
                || stored.getStatus() != UserStatus.ACTIVE) return false;

        if (stored.getPhone().equals(targetPhone.trim())) return true;
        return isStaff(stored);
    }

    public List<User> getActiveStaff() {
        List<User> result = new ArrayList<>();
        if (users == null) return result;
        for (User u : users.findAll()) {
            if (u != null && u.getStatus() == UserStatus.ACTIVE &&
                    (u.getRole() == UserRole.LIBRARIAN || u.getRole() == UserRole.ADMIN)) {
                result.add(u);
            }
        }
        return result;
    }

    public List<User> getDefaulters(User staff) {
        if (!isStaff(staff)) {
            return new ArrayList<>();
        }
        return findDefaulters();
    }

//    public List<User> getDefaulters() {
//        return new ArrayList<>();
//    }

    private List<User> findDefaulters() {
        List<User> result = new ArrayList<>();
        for (User user : users.findAll()) {
            if (isDefaulter(user.getPhone())) {
                result.add(user);
            }
        }
        return result;
    }

    public MembershipPlan getPlanForUser(String phone) {
        if (phone == null || phone.isBlank() || memberships == null) {
            return null;
        }
        model.Membership membership = memberships.findActiveByUserPhone(phone.trim());
        if (membership == null) {
            return null;
        }
        int planId = membership.getPlanId();
        if (planRepository != null) {
            MembershipPlan p = planRepository.findById(planId);
            if (p != null) return p;
        }
        return memberships.findPlanById(planId);
    }

    public int getMaxBooksForUser(String phone) {
        MembershipPlan plan = getPlanForUser(phone);
        if (plan != null) {
            return plan.getMaxIssuedBooks();
        }
        return maxBooksBorrowed;
    }

    public int getRequestQueuePriority(String phone) {
        MembershipPlan plan = getPlanForUser(phone);
        if (plan == null) {
            return 2; // Normal priority default
        }
        String name = plan.getPlanName().toLowerCase(Locale.ROOT);
        if (name.contains("premium")) {
            return 1; // High priority: Premium, Family Premium
        }
        return 2; // Normal priority: Standard, Family
    }

    public boolean canRequestBook(String phone) {
        if (phone == null || phone.isBlank()) {
            return false;
        }
        User user = users.findByPhone(phone);
        return user != null && user.getStatus() == UserStatus.ACTIVE && hasActiveMembership(phone) && !isDefaulter(phone);
    }

    public boolean canIssueBook(String phone) {
        return canRequestBook(phone) && countIssuedBooks(phone) < getMaxBooksForUser(phone);
    }

    public UserRepository getUserRepository() {
        return users;
    }

    public List<String> getBorrowingEligibilityFailureReasons(String phone) {
        List<String> reasons = new ArrayList<>();
        if (phone == null || phone.isBlank()) {
            reasons.add("User phone number is required.");
            return reasons;
        }
        User user = users != null ? users.findByPhone(phone.trim()) : null;
        if (user == null) {
            reasons.add("User account not found.");
            return reasons;
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            reasons.add("Account is currently inactive or suspended.");
        }

        // Membership validation
        model.Membership activeMembership = (memberships != null) ? memberships.findActiveByUserPhone(phone.trim()) : null;
        if (activeMembership == null) {
            boolean hadMembership = false;
            if (memberships != null) {
                for (model.Membership m : memberships.findByUserPhone(phone.trim())) {
                    if (m.getStatus() == enums.MembershipStatus.EXPIRED || (m.getEndDate() != null && LocalDate.now().isAfter(m.getEndDate()))) {
                        hadMembership = true;
                        break;
                    }
                }
            }
            if (hadMembership) {
                reasons.add("Membership has expired.");
            } else {
                reasons.add("No active membership found.");
            }
        } else if (activeMembership.getStatus() == enums.MembershipStatus.SUSPENDED) {
            reasons.add("Membership is currently suspended.");
        } else if (activeMembership.getEndDate() != null && LocalDate.now().isAfter(activeMembership.getEndDate())) {
            reasons.add("Membership expired on " + activeMembership.getEndDate() + ".");
        }

        // Outstanding fine validation
        if (fines != null) {
            double totalOutstanding = 0;
            for (Fine fine : fines.findByUserPhone(phone.trim())) {
                if (fine.isPending()) {
                    totalOutstanding += fine.getRemainingAmount();
                }
            }
            if (totalOutstanding > 0) {
                reasons.add("Outstanding fine: Rs." + String.format(Locale.ROOT, "%.2f", totalOutstanding) + ".");
            }
        }

        // Overdue books on loan validation
        if (transactions != null) {
            for (Transaction t : transactions.findByUserPhone(phone.trim())) {
                if (t.getStatus() == TransactionStatus.ISSUED && t.getDueDate() != null && LocalDate.now().isAfter(t.getDueDate())) {
                    long overdueDays = java.time.temporal.ChronoUnit.DAYS.between(t.getDueDate(), LocalDate.now());
                    reasons.add("Overdue book on loan (Transaction ID " + t.getTransactionId() + " is " + overdueDays + " day(s) overdue).");
                }
            }
        }

        return reasons;
    }

    public String getBorrowingResolutionAdvice(List<String> failureReasons) {
        if (failureReasons == null || failureReasons.isEmpty()) return "";
        List<String> actions = new ArrayList<>();
        boolean needMembership = false;
        boolean needFine = false;
        boolean needReturn = false;
        boolean needAccount = false;
        for (String r : failureReasons) {
            String lower = r.toLowerCase(Locale.ROOT);
            if (lower.contains("membership")) needMembership = true;
            if (lower.contains("fine")) needFine = true;
            if (lower.contains("overdue")) needReturn = true;
            if (lower.contains("account")) needAccount = true;
        }
        if (needMembership) actions.add("renew your membership");
        if (needFine) actions.add("settle the outstanding fine");
        if (needReturn) actions.add("return overdue books");
        if (needAccount) actions.add("contact library staff regarding your account status");

        if (actions.isEmpty()) return "";
        return "Please " + String.join(" and ", actions) + ".";
    }

    public List<String> getIssueEligibilityFailureReasons(String phone) {
        List<String> reasons = new ArrayList<>(getBorrowingEligibilityFailureReasons(phone));
        if (phone != null && !phone.isBlank()) {
            int currentIssued = countIssuedBooks(phone.trim());
            int maxAllowed = getMaxBooksForUser(phone.trim());
            if (currentIssued >= maxAllowed) {
                reasons.add("Borrowing limit reached: Member has already borrowed " + currentIssued + " of " + maxAllowed + " allowed books.");
            }
        }
        return reasons;
    }
}
