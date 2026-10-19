package repository;

import model.MembershipPlan;
import java.util.List;

public interface MembershipPlanRepository {
    void save(MembershipPlan plan);
    MembershipPlan findById(int planId);
    MembershipPlan findByName(String planName);
    List<MembershipPlan> findAll();
    boolean existsById(int planId);
}
