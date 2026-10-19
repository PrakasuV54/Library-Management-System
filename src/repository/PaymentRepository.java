package repository;

import model.Payment;
import java.util.List;

public interface PaymentRepository {
    void save(Payment payment);
    Payment findById(int paymentId);
    List<Payment> findByFineId(int fineId);
    List<Payment> findByMembershipId(int membershipId);
    List<Payment> findByUserPhone(String userPhone);
    List<Payment> findAll();
    boolean existsById(int paymentId);
    void deleteById(int paymentId);
}
