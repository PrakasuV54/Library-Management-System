package repository;

import model.MembershipPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class InMemoryMembershipPlanRepository implements MembershipPlanRepository {
    private final DataStore dataStore;

    public InMemoryMembershipPlanRepository(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public void save(MembershipPlan plan) {
        if (plan == null) return;
        String planName = plan.getPlanName().trim().toLowerCase(Locale.ROOT);
        dataStore.membershipPlans.put(plan.getPlanId(), plan);
        dataStore.membershipPlanByName.put(planName, plan);
        if (planName.endsWith(" plan")) {
            dataStore.membershipPlanByName.put(planName.substring(0, planName.length() - 5).trim(), plan);
        } else {
            dataStore.membershipPlanByName.put(planName + " plan", plan);
        }
    }

    @Override
    public MembershipPlan findById(int planId) {
        return dataStore.membershipPlans.get(planId);
    }

    @Override
    public MembershipPlan findByName(String planName) {
        if (planName == null) {
            return null;
        }
        String key = planName.trim().toLowerCase(Locale.ROOT);
        MembershipPlan plan = dataStore.membershipPlanByName.get(key);
        if (plan == null) {
            plan = dataStore.membershipPlanByName.get(key + " plan");
        }
        if (plan == null && key.endsWith(" plan")) {
            plan = dataStore.membershipPlanByName.get(key.substring(0, key.length() - 5).trim());
        }
        return plan;
    }

    @Override
    public List<MembershipPlan> findAll() {
        return new ArrayList<>(dataStore.membershipPlans.values());
    }

    @Override
    public boolean existsById(int planId) {
        return dataStore.membershipPlans.containsKey(planId);
    }
}
