package repository;

import model.Genre;
import java.util.List;

public interface GenreRepository {
    void save(Genre genre);
    Genre findById(int genreId);
    Genre findByName(String name);
    List<Genre> findAll();
    boolean existsById(int genreId);
    void deleteById(int genreId);
}
