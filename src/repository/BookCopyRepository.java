package repository;

import model.BookCopy;
import java.util.List;

public interface BookCopyRepository {
    void save(BookCopy copy);
    BookCopy findById(int bookCopyId);
    List<BookCopy> findByBookId(int bookId);
    List<BookCopy> findByIsbn(String isbn);
    BookCopy findAvailableCopy(int bookId);
    BookCopy findAvailableCopy(String isbn);
    BookCopy findAvailableCopyByIsbn(String isbn);
    List<BookCopy> findAll();
    boolean existsById(int bookCopyId);
    void deleteById(int bookCopyId);
}
