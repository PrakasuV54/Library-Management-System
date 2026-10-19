package repository;

import model.Author;
import java.util.List;

public interface AuthorRepository {
    void save(Author author);
    Author findById(int authorId);
    Author findByName(String name);
    List<Author> findAll();
    boolean existsById(int authorId);
    void deleteById(int authorId);
}
