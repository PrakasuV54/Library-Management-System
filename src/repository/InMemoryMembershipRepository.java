package repository;

import model.Membership;
import java.util.*;

public class InMemoryMembershipRepository implements MembershipRepository {
    private final DataStore dataStore;
    public InMemoryMembershipRepository(DataStore dataStore) {
        this.dataStore = Objects.requireNonNull(dataStore);
    }

    @Override
    public void save(Membership membership) {
        if (membership == null) return;
        model.User owner = dataStore.userByPhone.get(membership.getUserPhone());
        if (owner != null) {
            membership.assignUserId(owner.getUserId());
        }
        dataStore.memberships.put(membership.getMembershipId(), membership);
    }

    @Override
    public Membership findById(int id) {
        return dataStore.memberships.get(id);
    }

    @Override
    public List<Membership> findByUserPhone(String phone) {
        List<Membership> result = new ArrayList<>();
        if (phone == null) return result;
        for (Membership membership : dataStore.memberships.values()) {
            if (phone.equals(membership.getUserPhone())) result.add(membership);
        }
        return result;
    }

    @Override
    public Membership findActiveByUserPhone(String phone) {
        for (Membership membership : findByUserPhone(phone)) {
            if (membership.isActive()) {
                return membership;
            }
        }
        return null;
    }

    @Override
    public List<Membership> findAll() {
        return new ArrayList<>(dataStore.memberships.values());
    }

    @Override
    public boolean existsById(int id) {
        return dataStore.memberships.containsKey(id);
    }

    @Override
    public void deleteById(int id) {
        dataStore.memberships.remove(id);
    }

    @Override
    public model.MembershipPlan findPlanById(int planId) {
        return dataStore.membershipPlans.get(planId);
    }
}
