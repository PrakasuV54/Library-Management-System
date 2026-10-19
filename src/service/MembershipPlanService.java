package service;

import model.MembershipPlan;
import model.User;
import enums.UserRole;
import enums.UserStatus;
import repository.UserRepository;
import repository.DataStore;
import repository.MembershipPlanRepository;
import java.util.List;


public class MembershipPlanService {
    public static final String DEFAULT_STANDARD_PLAN_NAME = "Standard Plan";
    public static final double DEFAULT_STANDARD_PLAN_PRICE = 299.0;
    public static final String DEFAULT_PREMIUM_PLAN_NAME = "Premium Plan";
    public static final double DEFAULT_PREMIUM_PLAN_PRICE = 599.0;
    public static final String DEFAULT_FAMILY_PLAN_NAME = "Family Plan";
    public static final double DEFAULT_FAMILY_PLAN_PRICE = 899.0;
    public static final String DEFAULT_FAMILY_PREMIUM_PLAN_NAME = "Family Premium";
    public static final double DEFAULT_FAMILY_PREMIUM_PLAN_PRICE = 1299.0;

    private final MembershipPlanRepository membershipPlanRepository;
    private final UserRepository users;


    public MembershipPlanService(MembershipPlanRepository membershipPlanRepository, UserRepository users) {
        if (membershipPlanRepository == null) {
            throw new IllegalArgumentException("Membership plan repository is required");
        }
        this.membershipPlanRepository = membershipPlanRepository;
        this.users = users;
    }

    public MembershipPlan createPlan(User admin, String planName, double amount) {
        return createPlan(admin, planName, amount, MembershipPlan.resolveDefaultMaxBooks(planName));
    }

    public MembershipPlan createPlan(User admin, String planName, double amount, int maxBooks) {
        if (!isAdmin(admin) || planName == null || planName.isBlank() || amount <= 0)
            return null;
        int planId = DataStore.getMembershipPlanId();
        MembershipPlan plan = new MembershipPlan(planId, planName, amount, maxBooks);
        membershipPlanRepository.save(plan);
        return plan;
    }

    private boolean isAdmin(User admin) {
        if (users == null || admin == null || admin.getStatus() != UserStatus.ACTIVE
                || admin.getRole() != UserRole.ADMIN) return false;
        User stored = users.findByPhone(admin.getPhone());
        return stored != null && stored.getUserId() == admin.getUserId()
                && stored.getStatus() == UserStatus.ACTIVE && stored.getRole() == UserRole.ADMIN;
    }

    public MembershipPlan findById(int planId) {
        return membershipPlanRepository.findById(planId);
    }

    public MembershipPlan findByName(String planName) {
        return membershipPlanRepository.findByName(planName);
    }

    public List<MembershipPlan> getAllPlans() {
        return membershipPlanRepository.findAll();
    }

    public String getCreatePlanFailureReason(User admin, String planName, double amount) {
        if (!isAdmin(admin)) {
            return "Insufficient permissions. Active administrator privileges required.";
        }
        if (planName == null || planName.isBlank()) {
            return "Plan name cannot be empty.";
        }
        if (amount <= 0) {
            return "Plan amount must be greater than zero.";
        }
        return null;
    }
}
