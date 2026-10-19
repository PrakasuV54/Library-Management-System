package repository;

import model.Membership;
import java.util.List;

public interface MembershipRepository {
    void save(Membership membership);
    Membership findById(int membershipId);
    List<Membership> findByUserPhone(String userPhone);
    Membership findActiveByUserPhone(String userPhone);
    List<Membership> findAll();
    boolean existsById(int membershipId);
    void deleteById(int membershipId);
    default model.MembershipPlan findPlanById(int planId) {
        return null;
    }
}
