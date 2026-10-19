package repository;

import enums.FineType;
import model.Fine;
import model.Transaction;
import java.util.*;

public class InMemoryFineRepository implements FineRepository {
    private final DataStore dataStore;

    public InMemoryFineRepository(DataStore dataStore) {
        this.dataStore = Objects.requireNonNull(dataStore);
    }

    @Override
    public void save(Fine fine) {
        if (fine == null) return;
        model.User owner = dataStore.userByPhone.get(fine.getUserPhone());
        if (owner != null) {
            fine.assignUserId(owner.getUserId());
        }
        dataStore.fines.put(fine.getFineId(), fine);
    }

    @Override
    public Fine findById(int id) {
        return dataStore.fines.get(id);
    }

    @Override
    public List<Fine> findByTransactionId(int transactionId) {
        List<Fine> result = new ArrayList<>();
        for (Fine fine : dataStore.fines.values()) {
            if (fine.getTransactionId() == transactionId) result.add(fine);
        }
        return result;
    }

    @Override
    public Fine findByTransactionIdAndType(int transactionId, FineType type) {
        for (Fine fine : dataStore.fines.values()) {
            if (fine.getTransactionId() == transactionId && fine.getType() == type) return fine;
        }
        return null;
    }

    @Override
    public List<Fine> findByUserPhone(String phone) {
        List<Fine> result = new ArrayList<>();
        if (phone == null) return result;
        for (Fine fine : dataStore.fines.values()) {
            if (phone.equals(fine.getUserPhone())) result.add(fine);
        }
        return result;
    }

    @Override
    public List<Fine> findAll() {
        return new ArrayList<>(dataStore.fines.values());
    }

    @Override
    public boolean existsById(int id) {
        return dataStore.fines.containsKey(id);
    }

    @Override
    public void deleteById(int id) {
        dataStore.fines.remove(id);
    }
}
