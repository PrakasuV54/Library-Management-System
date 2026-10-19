package repository;

import model.Transaction;
import java.util.List;

public interface TransactionRepository {
    void save(Transaction transaction);
    Transaction findById(int transactionId);
    List<Transaction> findByUserPhone(String userPhone);
    List<Transaction> findByBookId(int bookId);
    List<Transaction> findByIsbn(String isbn);
    Transaction findActiveByCopyId(int bookCopyId);
    List<Transaction> findAll();
    boolean existsById(int transactionId);
    void deleteById(int transactionId);
    default List<Transaction> findQueuedRequestsByBookId(int bookId) {
        List<Transaction> list = new java.util.ArrayList<>();
        for (Transaction t : findByBookId(bookId)) {
            if (t.getStatus() == enums.TransactionStatus.REQUESTED) {
                list.add(t);
            }
        }
        return list;
    }
}
