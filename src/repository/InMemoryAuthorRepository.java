package repository;

import model.Author;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class InMemoryAuthorRepository implements AuthorRepository {
    private final DataStore dataStore;
    public InMemoryAuthorRepository(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    public void save(Author author) {
        if (author == null) throw new IllegalArgumentException("Author is required");
        Author existing = findById(author.getAuthorId());
        if (existing != null) {
            if (existing != author) {
                throw new IllegalArgumentException("Author ID already exists");
            }
            return;
        }
        dataStore.authors.add(author);
    }

    public Author findById(int authorId) {
        for (Author author : dataStore.authors) {
            if (author.getAuthorId() == authorId) return author;
        }
        return null;
    }

    @Override
    public Author findByName(String name) {
        if (name == null || name.isBlank()) return null;
        String trimmed = name.trim();
        for (Author author : dataStore.authors) {
            if (author.getName().equalsIgnoreCase(trimmed)) return author;
        }
        return null;
    }

    public List<Author> findAll() {
        return new ArrayList<>(dataStore.authors);
    }

    public boolean existsById(int authorId) {
        return findById(authorId) != null;
    }

    public void deleteById(int authorId) {
        boolean referenced = false;
        for (model.Book book : dataStore.books.values()) {
            for (model.Author item : book.getAuthors()) {
                if (item.getAuthorId() == authorId) {
                    referenced = true;
                    break;
                }
            }
            if (referenced) {
                break;
            }
        }
        if (referenced) throw new IllegalStateException("Cannot delete an author referenced by a book");
        Iterator<Author> iterator = dataStore.authors.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getAuthorId() == authorId) {
                iterator.remove();
                return;
            }
        }
    }
}
