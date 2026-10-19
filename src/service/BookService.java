package service;

import model.Book;
import model.BookCopy;
import model.User;
import model.Author;
import model.Genre;
import enums.BookCopyStatus;
import repository.AuthorRepository;
import repository.BookCopyRepository;
import repository.BookRepository;
import repository.DataStore;
import repository.GenreRepository;
import repository.InMemoryBookRepository;
import repository.TransactionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class BookService {
    private final BookRepository books;
    private final BookCopyRepository copies;
    private final TransactionRepository transactions;
    private final AuditService auditService;
    private final AuthorRepository authorRepository;
    private final GenreRepository genreRepository;

    public BookService(BookRepository books, BookCopyRepository copies, TransactionRepository transactions) {
        this(books, copies, transactions, null, null, null);
    }

    public BookService(BookRepository books, BookCopyRepository copies, TransactionRepository transactions,
                       AuditService auditService) {
        this(books, copies, transactions, auditService, null, null);
    }

    public BookService(BookRepository books, BookCopyRepository copies, TransactionRepository transactions,
                       AuditService auditService, AuthorRepository authorRepository, GenreRepository genreRepository) {
        this.books = books;
        this.copies = copies;
        this.transactions = transactions;
        this.auditService = auditService;
        this.authorRepository = authorRepository;
        this.genreRepository = genreRepository;
    }

    public AuthorRepository getAuthorRepository() {
        if (authorRepository != null) return authorRepository;
        if (books instanceof InMemoryBookRepository) {
            return ((InMemoryBookRepository) books).getAuthorRepository();
        }
        return null;
    }

    public GenreRepository getGenreRepository() {
        if (genreRepository != null) return genreRepository;
        if (books instanceof InMemoryBookRepository) {
            return ((InMemoryBookRepository) books).getGenreRepository();
        }
        return null;
    }

    public Author getOrCreateAuthor(String authorName) {
        if (authorName == null || authorName.isBlank()) return null;
        String name = authorName.trim();
        AuthorRepository repo = getAuthorRepository();
        if (repo != null) {
            Author existing = repo.findByName(name);
            if (existing != null) return existing;
            Author created = new Author(DataStore.getAuthorId(), name);
            repo.save(created);
            return created;
        }
        return new Author(DataStore.getAuthorId(), name);
    }

    public Genre getOrCreateGenre(String genreName) {
        if (genreName == null || genreName.isBlank()) return null;
        String name = genreName.trim();
        GenreRepository repo = getGenreRepository();
        if (repo != null) {
            Genre existing = repo.findByName(name);
            if (existing != null) return existing;
            Genre created = new Genre(DataStore.getGenreId(), name);
            repo.save(created);
            return created;
        }
        return new Genre(DataStore.getGenreId(), name);
    }

    public List<Book> getAllBooks() {
        return books.findAll();
    }

    public Book getBookById(int bookId) {
        return books.findById(bookId);
    }

    public Book getBookByIsbn(String isbn) {
        return books.findByIsbn(isbn);
    }

    public List<Book> searchBooks(String query) {
        return books.search(query);
    }

    public List<Book> searchById(int bookId) {
        Book book = books.findById(bookId);
        List<Book> result = new ArrayList<>();
        if (book != null) {
            result.add(book);
        }
        return result;
    }

    public List<Book> searchByIsbn(String isbn) {
        if (isbn == null || isbn.isBlank()) return new ArrayList<>();
        Book book = books.findByIsbn(isbn.trim());
        List<Book> result = new ArrayList<>();
        if (book != null) {
            result.add(book);
        }
        return result;
    }

    public List<Book> searchByTitle(String title) {
        return books.findByTitle(title);
    }

    public List<Book> searchByAuthor(String author) {
        return books.findByAuthor(author);
    }

    public List<Book> searchByGenre(String genre) {
        return books.findByGenre(genre);
    }

    public List<Book> searchByGenreOrCategory(String genreOrCategory) {
        return books.findByGenreOrCategory(genreOrCategory);
    }

    public List<Book> searchByPublisher(String publisher) {
        return books.findByPublisher(publisher);
    }

    public List<Book> searchByDescription(String description) {
        return books.findByDescription(description);
    }

    public List<Book> searchByPublicationYear(int year) {
        return books.findByPublicationYear(year);
    }

    public List<Book> searchByField(String field, String query) {
        if (query == null || field == null) return new ArrayList<>();
        String q = query.trim();
        if (q.isEmpty()) return new ArrayList<>();
        String f = field.trim().toLowerCase(Locale.ROOT);
        if (f.equals("id") || f.equals("bookid") || f.equals("book id")) {
            try {
                int id = Integer.parseInt(q);
                return searchById(id);
            } catch (NumberFormatException e) {
                return new ArrayList<>();
            }
        }
        if (f.equals("isbn")) {
            return searchByIsbn(q);
        }
        if (f.equals("title")) {
            return searchByTitle(q);
        }
        if (f.equals("author") || f.equals("authors")) {
            return searchByAuthor(q);
        }
        if (f.equals("genre") || f.equals("genres")) {
            return searchByGenre(q);
        }
        if (f.equals("category") || f.equals("genre/category") || f.equals("genre / category")) {
            return searchByGenreOrCategory(q);
        }
        if (f.equals("publisher")) {
            return searchByPublisher(q);
        }
        if (f.equals("description")) {
            return searchByDescription(q);
        }
        if (f.equals("year") || f.equals("publicationyear") || f.equals("publication year")) {
            try {
                int year = Integer.parseInt(q);
                return searchByPublicationYear(year);
            } catch (NumberFormatException e) {
                return new ArrayList<>();
            }
        }
        return new ArrayList<>();
    }

    public List<Book> sortBooks(List<Book> bookList, String field, boolean ascending) {
        if (bookList == null || bookList.isEmpty()) {
            return new ArrayList<>();
        }
        List<Book> sorted = new ArrayList<>(bookList);
        String f = field != null ? field.trim().toLowerCase(Locale.ROOT) : "id";
        sorted.sort((b1, b2) -> {
            if (b1 == null && b2 == null) return 0;
            if (b1 == null) return -1;
            if (b2 == null) return 1;
            int cmp = 0;
            if (f.equals("id") || f.equals("bookid") || f.equals("book id")) {
                cmp = Integer.compare(b1.getBookId(), b2.getBookId());
            } else if (f.equals("isbn")) {
                cmp = compareStrings(b1.getIsbn(), b2.getIsbn());
            } else if (f.equals("title")) {
                cmp = compareStrings(b1.getTitle(), b2.getTitle());
            } else if (f.equals("author") || f.equals("authors")) {
                cmp = compareStrings(b1.getAuthorsString(), b2.getAuthorsString());
            } else if (f.equals("genre") || f.equals("genres")) {
                cmp = compareStrings(b1.getGenresString(), b2.getGenresString());
            } else if (f.equals("category") || f.equals("genre/category") || f.equals("genre / category")) {
                cmp = compareStrings(getGenreCategoryString(b1), getGenreCategoryString(b2));
            } else if (f.equals("publisher")) {
                cmp = compareStrings(b1.getPublisher(), b2.getPublisher());
            } else if (f.equals("year") || f.equals("publicationyear") || f.equals("publication year")) {
                cmp = Integer.compare(b1.getPublicationYear(), b2.getPublicationYear());
            } else {
                cmp = Integer.compare(b1.getBookId(), b2.getBookId());
            }
            if (cmp != 0) {
                return ascending ? cmp : -cmp;
            }
            return Integer.compare(b1.getBookId(), b2.getBookId());
        });
        return sorted;
    }

    private int compareStrings(String s1, String s2) {
        if (s1 == null && s2 == null) return 0;
        if (s1 == null) return -1;
        if (s2 == null) return 1;
        return s1.compareToIgnoreCase(s2);
    }

    private String getGenreCategoryString(Book book) {
        if (book == null) return "";
        List<String> names = new ArrayList<>();
        for (model.Category c : book.getCategories()) {
            if (c != null && c.getName() != null) {
                names.add(c.getName());
            }
        }
        for (model.Genre g : book.getGenres()) {
            if (g != null && g.getName() != null) {
                names.add(g.getName());
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return String.join(", ", names);
    }

    public String getFormattedBookDetails(int bookId) {
        Book book = books.findById(bookId);
        if (book == null) return null;

        StringBuilder sb = new StringBuilder();
        sb.append("----------------------------------------\n");
        sb.append("Book Details\n");
        sb.append("----------------------------------------\n");
        sb.append("Book ID: ").append(book.getBookId()).append("\n");
        sb.append("ISBN: ").append(book.getIsbn()).append("\n");
        sb.append("Title: ").append(book.getTitle()).append("\n");
        String authorsStr = book.getAuthorsString();
        sb.append("Authors: ").append(authorsStr.isEmpty() ? "N/A" : authorsStr).append("\n");
        String genresStr = book.getGenresString();
        sb.append("Genres: ").append(genresStr.isEmpty() ? "N/A" : genresStr).append("\n");
        if (!book.getCategories().isEmpty()) {
            List<String> catNames = new ArrayList<>();
            for (model.Category c : book.getCategories()) {
                if (c != null && c.getName() != null) catNames.add(c.getName());
            }
            sb.append("Categories: ").append(String.join(", ", catNames)).append("\n");
        }
        sb.append("Publisher: ").append(book.getPublisher() != null && !book.getPublisher().isBlank() ? book.getPublisher() : "N/A").append("\n");
        sb.append("Publication Year: ").append(book.getPublicationYear()).append("\n");
        if (book.getDescription() != null && !book.getDescription().isBlank()) {
            sb.append("Description: ").append(book.getDescription()).append("\n");
        }

        List<BookCopy> copyList = copies.findByBookId(bookId);
        sb.append("\nPhysical Copy Availability:\n");
        sb.append("Total Copies: ").append(copyList.size()).append("\n");
        int available = 0, reserved = 0, issued = 0, lost = 0, damaged = 0;
        for (BookCopy c : copyList) {
            if (c.getStatus() == BookCopyStatus.AVAILABLE) available++;
            else if (c.getStatus() == BookCopyStatus.RESERVED) reserved++;
            else if (c.getStatus() == BookCopyStatus.ISSUED) issued++;
            else if (c.getStatus() == BookCopyStatus.LOST) lost++;
            else if (c.getStatus() == BookCopyStatus.DAMAGED) damaged++;
        }
        sb.append("  - Available: ").append(available).append("\n");
        sb.append("  - Reserved: ").append(reserved).append("\n");
        sb.append("  - Issued: ").append(issued).append("\n");
        sb.append("  - Lost: ").append(lost).append("\n");
        sb.append("  - Damaged: ").append(damaged).append("\n");
        sb.append("----------------------------------------");
        return sb.toString();
    }

    public Author canonicalizeAuthor(Author a) {
        if (a == null || a.getName() == null || a.getName().isBlank()) return null;
        String name = a.getName().trim();
        AuthorRepository repo = getAuthorRepository();
        if (repo == null) return a;

        // 1. Look up existing author by name (case-insensitive)
        Author existing = repo.findByName(name);
        if (existing != null) {
            return existing;
        }

        // 2. Look up existing author by ID if valid
        if (a.getAuthorId() > 0) {
            existing = repo.findById(a.getAuthorId());
            if (existing != null) {
                return existing;
            }
        }

        // 3. Create and save new canonical entity
        Author canonical;
        if (a.getAuthorId() > 0 && !repo.existsById(a.getAuthorId())) {
            canonical = a;
        } else {
            canonical = new Author(DataStore.getAuthorId(), name);
        }
        repo.save(canonical);
        return canonical;
    }

    public Genre canonicalizeGenre(Genre g) {
        if (g == null || g.getName() == null || g.getName().isBlank()) return null;
        String name = g.getName().trim();
        GenreRepository repo = getGenreRepository();
        if (repo == null) return g;

        // 1. Look up existing genre by name (case-insensitive)
        Genre existing = repo.findByName(name);
        if (existing != null) {
            return existing;
        }

        // 2. Look up existing genre by ID if valid
        if (g.getGenreId() > 0) {
            existing = repo.findById(g.getGenreId());
            if (existing != null) {
                return existing;
            }
        }

        // 3. Create and save new canonical entity
        Genre canonical;
        if (g.getGenreId() > 0 && !repo.existsById(g.getGenreId())) {
            canonical = g;
        } else {
            canonical = new Genre(DataStore.getGenreId(), name);
        }
        repo.save(canonical);
        return canonical;
    }

    public void canonicalizeBookAuthors(Book book) {
        if (book == null) return;
        Set<Author> currentAuthors = book.getAuthors();
        if (!currentAuthors.isEmpty()) {
            List<Author> resolvedAuthors = new ArrayList<>();
            for (Author a : currentAuthors) {
                Author canonical = canonicalizeAuthor(a);
                if (canonical != null && !resolvedAuthors.contains(canonical)) {
                    resolvedAuthors.add(canonical);
                }
            }
            book.setAuthors(resolvedAuthors);
        }
    }

    public void canonicalizeBookGenres(Book book) {
        if (book == null) return;
        Set<Genre> currentGenres = book.getGenres();
        if (!currentGenres.isEmpty()) {
            List<Genre> resolvedGenres = new ArrayList<>();
            for (Genre g : currentGenres) {
                Genre canonical = canonicalizeGenre(g);
                if (canonical != null && !resolvedGenres.contains(canonical)) {
                    resolvedGenres.add(canonical);
                }
            }
            book.setGenres(resolvedGenres);
        }
    }

    public void canonicalizeBookAssociations(Book book) {
        canonicalizeBookAuthors(book);
        canonicalizeBookGenres(book);
    }

    public Author getOrCreateAuthor(Author author) {
        return canonicalizeAuthor(author);
    }

    public Genre getOrCreateGenre(Genre genre) {
        return canonicalizeGenre(genre);
    }

    public boolean addBook(Book book, int numberOfCopies, User librarian) {
        if (!canManageCatalogue(librarian)) return false;
        String librarianPhone = librarian.getPhone();
        if (book == null || numberOfCopies < 0 || books.existsByIsbn(book.getIsbn())) return false;

        canonicalizeBookAssociations(book);

        books.save(book);
        for (int i = 0; i < numberOfCopies; i++) {
            copies.save(new BookCopy(DataStore.getBookCopyId(), book.getBookId()));
        }
        record(librarianPhone, "BOOK_ADDED", book, "Copies added: " + numberOfCopies);
        return true;
    }

    public boolean updateBook(int bookId, String title, String publisher, int publicationYear, String description, User librarian) {
        return updateBook(bookId, title, publisher, publicationYear, description, null, null, librarian);
    }

    public boolean updateBook(int bookId, String title, String publisher, int publicationYear, String description,
                              List<String> authorNames, List<String> genreNames, User librarian) {
        if (!canManageCatalogue(librarian)) return false;
        String librarianPhone = librarian.getPhone();
        Book book = books.findById(bookId);
        if (book == null || title == null || title.isBlank() || publisher == null || publisher.isBlank()) return false;
        book.setTitle(title);
        book.setPublisher(publisher);
        book.setPublicationYear(publicationYear);
        book.setDescription(description);

        if (authorNames != null && !authorNames.isEmpty()) {
            List<Author> resolvedAuthors = new ArrayList<>();
            for (String aName : authorNames) {
                Author a = getOrCreateAuthor(aName);
                if (a != null && !resolvedAuthors.contains(a)) {
                    resolvedAuthors.add(a);
                }
            }
            book.setAuthors(resolvedAuthors);
        } else {
            canonicalizeBookAuthors(book);
        }

        if (genreNames != null && !genreNames.isEmpty()) {
            List<Genre> resolvedGenres = new ArrayList<>();
            for (String gName : genreNames) {
                Genre g = getOrCreateGenre(gName);
                if (g != null && !resolvedGenres.contains(g)) {
                    resolvedGenres.add(g);
                }
            }
            book.setGenres(resolvedGenres);
        } else {
            canonicalizeBookGenres(book);
        }

        books.save(book);
        record(librarianPhone, "BOOK_UPDATED", book, "Book details updated");
        return true;
    }

    public boolean updateBook(Book book, User librarian) {
        if (!canManageCatalogue(librarian) || book == null) return false;
        Book existing = books.findById(book.getBookId());
        if (existing == null) return false;
        canonicalizeBookAssociations(book);
        books.save(book);
        record(librarian.getPhone(), "BOOK_UPDATED", book, "Book details updated");
        return true;
    }

    public boolean updateBookAuthorsAndGenres(int bookId, java.util.Collection<Author> authors, java.util.Collection<Genre> genres, User librarian) {
        if (!canManageCatalogue(librarian)) return false;
        Book book = books.findById(bookId);
        if (book == null) return false;

        if (authors != null) {
            List<Author> resolvedAuthors = new ArrayList<>();
            for (Author a : authors) {
                Author canonical = canonicalizeAuthor(a);
                if (canonical != null && !resolvedAuthors.contains(canonical)) {
                    resolvedAuthors.add(canonical);
                }
            }
            book.setAuthors(resolvedAuthors);
        }

        if (genres != null) {
            List<Genre> resolvedGenres = new ArrayList<>();
            for (Genre g : genres) {
                Genre canonical = canonicalizeGenre(g);
                if (canonical != null && !resolvedGenres.contains(canonical)) {
                    resolvedGenres.add(canonical);
                }
            }
            book.setGenres(resolvedGenres);
        }

        books.save(book);
        record(librarian.getPhone(), "BOOK_UPDATED", book, "Book details updated");
        return true;
    }

    public boolean deleteBook(int bookId, User librarian) {
        if (!canManageCatalogue(librarian)) return false;
        String librarianPhone = librarian.getPhone();
        Book book = books.findById(bookId);
        if (book == null) return false;
        if (!copies.findByBookId(bookId).isEmpty() || !transactions.findByBookId(bookId).isEmpty()) return false;
        books.deleteById(bookId);
        record(librarianPhone, "BOOK_DELETED", book, "Book removed from catalogue");
        return true;
    }

    private boolean canManageCatalogue(User librarian) {
        return librarian != null && librarian.getStatus() == enums.UserStatus.ACTIVE
                && (librarian.getRole() == enums.UserRole.LIBRARIAN
                    || librarian.getRole() == enums.UserRole.ADMIN);
    }

    private void record(String phone, String action, Book book, String details) {
        if (auditService != null) {
            auditService.record(phone, action, "BOOK", String.valueOf(book.getBookId()), details);
        }
    }

    public String getAddBookFailureReason(Book book, int numberOfCopies, User librarian) {
        if (!canManageCatalogue(librarian)) {
            return "Insufficient permissions. Staff authorization required.";
        }
        if (book == null) {
            return "Book details are required.";
        }
        if (book.getIsbn() == null || book.getIsbn().isBlank()) {
            return "ISBN cannot be empty.";
        }
        if (books.existsByIsbn(book.getIsbn())) {
            return "A book with ISBN '" + book.getIsbn() + "' already exists in the catalogue.";
        }
        if (book.getTitle() == null || book.getTitle().isBlank()) {
            return "Title cannot be empty.";
        }
        if (book.getPublisher() == null || book.getPublisher().isBlank()) {
            return "Publisher cannot be empty.";
        }
        if (numberOfCopies < 1) {
            return "Number of copies must be at least 1.";
        }
        return null;
    }

    public String getDeleteBookFailureReason(int bookId, User librarian) {
        if (!canManageCatalogue(librarian)) {
            return "Insufficient permissions. Staff authorization required.";
        }
        Book book = books.findById(bookId);
        if (book == null) {
            return "Book with ID " + bookId + " was not found.";
        }
        if (!copies.findByBookId(bookId).isEmpty()) {
            return "Cannot delete book: " + copies.findByBookId(bookId).size() + " copy/copies currently exist in inventory. Remove all physical copies first.";
        }
        if (!transactions.findByBookId(bookId).isEmpty()) {
            return "Cannot delete book: Active or historical transaction records exist for this book.";
        }
        return null;
    }
}
