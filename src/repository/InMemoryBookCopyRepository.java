package repository;

import model.Book;
import model.BookCopy;
import enums.BookCopyStatus;
import java.util.ArrayList;
import java.util.List;

public class InMemoryBookCopyRepository implements BookCopyRepository {
    private final DataStore dataStore;

    public InMemoryBookCopyRepository(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public void save(BookCopy copy) {
        if (copy == null) return;
        dataStore.bookCopies.put(copy.getBookCopyId(), copy);
    }

    @Override
    public BookCopy findById(int bookCopyId) {
        return dataStore.bookCopies.get(bookCopyId);
    }

    @Override
    public List<BookCopy> findByBookId(int bookId) {
        List<BookCopy> result = new ArrayList<>();
        for (BookCopy copy : dataStore.bookCopies.values()) {
            if (copy.getBookId() == bookId) result.add(copy);
        }
        return result;
    }

    @Override
    public List<BookCopy> findByIsbn(String isbn) {
        if (isbn == null) return new ArrayList<>();
        Book book = dataStore.bookByIsbn.get(isbn.trim());
        return book == null ? new ArrayList<>() : findByBookId(book.getBookId());
    }

    @Override
    public BookCopy findAvailableCopy(int bookId) {
        for (BookCopy copy : dataStore.bookCopies.values()) {
            if (copy.getBookId() == bookId && copy.isAvailable()) return copy;
        }
        return null;
    }

    @Override
    public BookCopy findAvailableCopy(String isbn) {
        return findAvailableCopyByIsbn(isbn);
    }

    @Override
    public BookCopy findAvailableCopyByIsbn(String isbn) {
        if (isbn == null) return null;
        Book book = dataStore.bookByIsbn.get(isbn.trim());
        return book == null ? null : findAvailableCopy(book.getBookId());
    }

    @Override
    public List<BookCopy> findAll() {
        return new ArrayList<>(dataStore.bookCopies.values());
    }

    @Override
    public boolean existsById(int bookCopyId) {
        return dataStore.bookCopies.containsKey(bookCopyId);
    }

    @Override
    public void deleteById(int bookCopyId) {
        dataStore.bookCopies.remove(bookCopyId);
    }
}
