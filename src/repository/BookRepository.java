package repository;

import model.Book;
import java.util.List;

public interface BookRepository {
    void save(Book book);
    Book findById(int bookId);
    Book findByIsbn(String isbn);
    List<Book> findAll();
    List<Book> search(String query);
    List<Book> findByTitle(String title);
    List<Book> findByAuthor(String author);
    List<Book> findByGenre(String genre);
    List<Book> findByGenreOrCategory(String genreOrCategory);
    List<Book> findByPublisher(String publisher);
    List<Book> findByDescription(String description);
    List<Book> findByPublicationYear(int year);
    boolean existsById(int bookId);
    boolean existsByIsbn(String isbn);
    void deleteById(int bookId);
    void deleteByIsbn(String isbn);
}
