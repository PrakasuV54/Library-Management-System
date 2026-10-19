package repository;

import model.Genre;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class InMemoryGenreRepository implements GenreRepository {
    private final DataStore dataStore;
    public InMemoryGenreRepository(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    public void save(Genre genre) {
        if (genre == null) throw new IllegalArgumentException("Genre is required");
        Genre existing = findById(genre.getGenreId());
        if (existing != null) {
            if (existing != genre) {
                throw new IllegalArgumentException("Genre ID already exists");
            }
            return;
        }
        dataStore.genres.add(genre);
    }

    public Genre findById(int genreId) {
        for (Genre genre : dataStore.genres) {
            if (genre.getGenreId() == genreId) return genre;
        }
        return null;
    }

    @Override
    public Genre findByName(String name) {
        if (name == null || name.isBlank()) return null;
        String trimmed = name.trim();
        for (Genre genre : dataStore.genres) {
            if (genre.getName().equalsIgnoreCase(trimmed)) return genre;
        }
        return null;
    }

    public List<Genre> findAll() {
        return new ArrayList<>(dataStore.genres);
    }

    public boolean existsById(int genreId) {
        return findById(genreId) != null;
    }

    public void deleteById(int genreId) {
        boolean referenced = false;
        for (model.Book book : dataStore.books.values()) {
            for (model.Genre item : book.getGenres()) {
                if (item.getGenreId() == genreId) {
                    referenced = true;
                    break;
                }
            }
            if (referenced) {
                break;
            }
        }
        if (referenced) throw new IllegalStateException("Cannot delete a genre referenced by a book");
        Iterator<Genre> iterator = dataStore.genres.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getGenreId() == genreId) {
                iterator.remove();
                return;
            }
        }
    }
}
