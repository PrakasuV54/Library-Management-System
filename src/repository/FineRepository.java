package repository;

import model.Fine;
import enums.FineType;
import java.util.List;

public interface FineRepository {
    void save(Fine fine);
    Fine findById(int fineId);
    List<Fine> findByTransactionId(int transactionId);
    Fine findByTransactionIdAndType(int transactionId, FineType type);
    List<Fine> findByUserPhone(String userPhone);
    List<Fine> findAll();
    boolean existsById(int fineId);
    void deleteById(int fineId);
}
