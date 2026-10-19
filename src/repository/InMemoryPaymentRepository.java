package repository;

import model.Payment;
import java.util.*;

public class InMemoryPaymentRepository implements PaymentRepository {
    private final DataStore dataStore;

    public InMemoryPaymentRepository(DataStore dataStore) {
        this.dataStore = Objects.requireNonNull(dataStore);
    }

    @Override
    public void save(Payment payment) {
        if (payment == null) return;
        model.User owner = dataStore.userByPhone.get(payment.getUserPhone());
        if (owner != null) {
            payment.assignUserId(owner.getUserId());
        }
        dataStore.payments.put(payment.getPaymentId(), payment);
    }

    @Override
    public Payment findById(int id) {
        return dataStore.payments.get(id);
    }

    @Override
    public List<Payment> findByFineId(int id) {
        List<Payment> result = new ArrayList<>();
        for (Payment payment : dataStore.payments.values()) {
            if (payment.getFineId() == id) result.add(payment);
        }
        return result;
    }

    @Override
    public List<Payment> findByMembershipId(int id) {
        List<Payment> result = new ArrayList<>();
        for (Payment payment : dataStore.payments.values()) {
            if (payment.getMembershipId() == id) result.add(payment);
        }
        return result;
    }

    @Override
    public List<Payment> findByUserPhone(String phone) {
        List<Payment> result = new ArrayList<>();
        if (phone == null) return result;
        for (Payment payment : dataStore.payments.values()) {
            if (phone.equals(payment.getUserPhone())) result.add(payment);
        }
        return result;
    }

    @Override
    public List<Payment> findAll() {
        return new ArrayList<>(dataStore.payments.values());
    }

    @Override
    public boolean existsById(int id) {
        return dataStore.payments.containsKey(id);
    }

    @Override
    public void deleteById(int id) {
        dataStore.payments.remove(id);
    }
}
