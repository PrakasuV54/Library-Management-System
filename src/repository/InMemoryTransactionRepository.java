package repository;

import model.BookCopy;
import model.Transaction;
import enums.BookCopyStatus;
import enums.TransactionStatus;
import java.util.*;

public class InMemoryTransactionRepository implements TransactionRepository {
    private final DataStore dataStore;

    public InMemoryTransactionRepository(DataStore dataStore) {
        this.dataStore = Objects.requireNonNull(dataStore);
    }

    @Override
    public void save(Transaction t) {
        if (t == null) return;

        // Reject a second active transaction for a copy before changing either repository index.
        if (t.isActive() && t.getBookCopyId() > 0) {
            Integer existingId = dataStore.activeTransactionIdByCopyId.get(t.getBookCopyId());
            if (existingId != null && existingId != t.getTransactionId()) {
                Transaction existing = dataStore.transactions.get(existingId);
                if (existing != null && existing.isActive()
                        && existing.getBookCopyId() == t.getBookCopyId()) {
                    throw new IllegalStateException("Copy " + t.getBookCopyId()
                            + " is already linked to active transaction " + existingId);
                }
                dataStore.activeTransactionIdByCopyId.remove(t.getBookCopyId());
            }
        }

        model.User owner = dataStore.userByPhone.get(t.getUserPhone());
        if (owner != null) {
            t.assignUserId(owner.getUserId());
        }
        Transaction old = dataStore.transactions.get(t.getTransactionId());
        if (old != null) removeFromIndexes(old);
        dataStore.transactions.put(t.getTransactionId(), t);
        if (t.isActive() && t.getBookCopyId() > 0) {
            dataStore.activeTransactionIdByCopyId.put(t.getBookCopyId(), t.getTransactionId());
        }
    }

    private void removeFromIndexes(Transaction t) {
        Integer id = dataStore.activeTransactionIdByCopyId.get(t.getBookCopyId());
        if (t.getBookCopyId() > 0 && id != null && id == t.getTransactionId()) {
            dataStore.activeTransactionIdByCopyId.remove(t.getBookCopyId());
        }
    }



    @Override
    public Transaction findById(int id) {
        return dataStore.transactions.get(id);
    }

    @Override
    public List<Transaction> findByUserPhone(String phone) {
        List<Transaction> result = new ArrayList<>();
        if (phone == null) return result;
        for (Transaction transaction : dataStore.transactions.values()) {
            if (phone.equals(transaction.getUserPhone())) result.add(transaction);
        }
        return result;
    }

    @Override
    public List<Transaction> findByBookId(int bookId) {
        List<Transaction> result = new ArrayList<>();
        for (Transaction transaction : dataStore.transactions.values()) {
            if (transaction.getBookId() == bookId) result.add(transaction);
        }
        return result;
    }

    @Override
    public List<Transaction> findByIsbn(String isbn) {
        model.Book book = isbn == null ? null : dataStore.bookByIsbn.get(isbn.trim());
        return book == null ? new ArrayList<>() : findByBookId(book.getBookId());
    }

    @Override
    public Transaction findActiveByCopyId(int copyId) {
        Integer id = dataStore.activeTransactionIdByCopyId.get(copyId);
        return id == null ? null : findById(id);
    }

    @Override
    public List<Transaction> findAll() {
        return new ArrayList<>(dataStore.transactions.values());
    }

    @Override
    public boolean existsById(int id) {
        return dataStore.transactions.containsKey(id);
    }

    @Override
    public void deleteById(int id) {
        Transaction transaction = dataStore.transactions.remove(id);
        if (transaction != null) {
            removeFromIndexes(transaction);
        }
    }

    @Override
    public List<Transaction> findQueuedRequestsByBookId(int bookId) {
        List<Transaction> result = new ArrayList<>();
        for (Transaction transaction : dataStore.transactions.values()) {
            if (transaction.getBookId() == bookId && transaction.getStatus() == TransactionStatus.REQUESTED) {
                result.add(transaction);
            }
        }
        return result;
    }
}
