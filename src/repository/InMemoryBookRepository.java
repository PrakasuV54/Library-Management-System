package repository;

import model.Book;
import model.Author;
import model.Category;
import model.Genre;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class InMemoryBookRepository implements BookRepository {
    private final DataStore dataStore;
    private final AuthorRepository authorRepository;
    private final GenreRepository genreRepository;

    public InMemoryBookRepository(DataStore dataStore) {
        this.dataStore = dataStore;
        this.authorRepository = new InMemoryAuthorRepository(dataStore);
        this.genreRepository = new InMemoryGenreRepository(dataStore);
    }

    public AuthorRepository getAuthorRepository() {
        return authorRepository;
    }

    public GenreRepository getGenreRepository() {
        return genreRepository;
    }

    @Override
    public void save(Book book) {
        if (book == null) throw new IllegalArgumentException("Book is required");
        Book isbnOwner = dataStore.bookByIsbn.get(book.getIsbn());
        if (isbnOwner != null && isbnOwner.getBookId() != book.getBookId()) {
            throw new IllegalArgumentException("ISBN already exists");
        }
        Book old = dataStore.books.get(book.getBookId());
        if (old != null && old != book) throw new IllegalArgumentException("Book ID already exists; update the stored book instance");
        dataStore.books.put(book.getBookId(), book);
        if (old != null && !old.getIsbn().equals(book.getIsbn())) {
            dataStore.bookByIsbn.remove(old.getIsbn());
        }
        dataStore.bookByIsbn.put(book.getIsbn(), book);
    }

    @Override
    public Book findById(int bookId) {
        return dataStore.books.get(bookId);
    }

    @Override
    public Book findByIsbn(String isbn) {
        if (isbn == null) return null;
        return dataStore.bookByIsbn.get(isbn.trim());
    }

    @Override
    public List<Book> findAll() {
        return new ArrayList<>(dataStore.books.values());
    }

    @Override
    public List<Book> search(String query) {
        List<Book> result = new ArrayList<>();
        if (query == null || query.isBlank()) return result;
        String text = query.trim().toLowerCase(Locale.ROOT);
        for (Book book : dataStore.books.values()) {
            if (contains(book.getIsbn(), text) || contains(book.getTitle(), text)
            || contains(book.getPublisher(), text) || contains(book.getDescription(), text)
            || String.valueOf(book.getPublicationYear()).equals(text)
            || String.valueOf(book.getBookId()).equals(text)
            || matchesAuthor(book, text) || matchesCategory(book, text) || matchesGenre(book, text)) {
                result.add(book);
            }
        }
        return result;
    }

    @Override
    public List<Book> findByTitle(String title) {
        List<Book> result = new ArrayList<>();
        if (title == null || title.isBlank()) return result;
        String text = title.trim().toLowerCase(Locale.ROOT);
        for (Book book : dataStore.books.values()) {
            if (contains(book.getTitle(), text)) {
                result.add(book);
            }
        }
        return result;
    }

    @Override
    public List<Book> findByAuthor(String author) {
        List<Book> result = new ArrayList<>();
        if (author == null || author.isBlank()) return result;
        String text = author.trim().toLowerCase(Locale.ROOT);
        for (Book book : dataStore.books.values()) {
            if (matchesAuthor(book, text)) {
                result.add(book);
            }
        }
        return result;
    }

    @Override
    public List<Book> findByGenre(String genre) {
        List<Book> result = new ArrayList<>();
        if (genre == null || genre.isBlank()) return result;
        String text = genre.trim().toLowerCase(Locale.ROOT);
        for (Book book : dataStore.books.values()) {
            if (matchesGenre(book, text)) {
                result.add(book);
            }
        }
        return result;
    }

    @Override
    public List<Book> findByGenreOrCategory(String genreOrCategory) {
        List<Book> result = new ArrayList<>();
        if (genreOrCategory == null || genreOrCategory.isBlank()) return result;
        String text = genreOrCategory.trim().toLowerCase(Locale.ROOT);
        for (Book book : dataStore.books.values()) {
            if (matchesGenre(book, text) || matchesCategory(book, text)) {
                result.add(book);
            }
        }
        return result;
    }

    @Override
    public List<Book> findByPublisher(String publisher) {
        List<Book> result = new ArrayList<>();
        if (publisher == null || publisher.isBlank()) return result;
        String text = publisher.trim().toLowerCase(Locale.ROOT);
        for (Book book : dataStore.books.values()) {
            if (contains(book.getPublisher(), text)) {
                result.add(book);
            }
        }
        return result;
    }

    @Override
    public List<Book> findByDescription(String description) {
        List<Book> result = new ArrayList<>();
        if (description == null || description.isBlank()) return result;
        String text = description.trim().toLowerCase(Locale.ROOT);
        for (Book book : dataStore.books.values()) {
            if (contains(book.getDescription(), text)) {
                result.add(book);
            }
        }
        return result;
    }

    @Override
    public List<Book> findByPublicationYear(int year) {
        List<Book> result = new ArrayList<>();
        if (year <= 0) return result;
        for (Book book : dataStore.books.values()) {
            if (book.getPublicationYear() == year) {
                result.add(book);
            }
        }
        return result;
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private boolean matchesAuthor(Book book, String query) {
        if (book == null || query == null) return false;
        for (Author a : book.getAuthors()) {
            if (a != null && contains(a.getName(), query)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesCategory(Book book, String query) {
        if (book == null || query == null) return false;
        for (Category c : book.getCategories()) {
            if (c != null && contains(c.getName(), query)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesGenre(Book book, String query) {
        if (book == null || query == null) return false;
        for (Genre g : book.getGenres()) {
            if (g != null && contains(g.getName(), query)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean existsById(int bookId) {
        return dataStore.books.containsKey(bookId);
    }

    @Override
    public boolean existsByIsbn(String isbn) {
        return dataStore.bookByIsbn.containsKey(isbn);
    }

    @Override
    public void deleteById(int bookId) {
        Book book = dataStore.books.remove(bookId);
        if (book != null) {
            dataStore.bookByIsbn.remove(book.getIsbn());
        }
    }

    @Override
    public void deleteByIsbn(String isbn) {
        Book book = dataStore.bookByIsbn.get(isbn);
        if (book != null) deleteById(book.getBookId());
    }
}
