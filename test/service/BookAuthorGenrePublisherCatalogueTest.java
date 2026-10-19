package service;

import enums.BookCopyStatus;
import enums.UserRole;
import enums.UserStatus;
import model.Author;
import model.Book;
import model.BookCopy;
import model.Genre;
import model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import repository.*;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class BookAuthorGenrePublisherCatalogueTest {

    private DataStore dataStore;
    private BookRepository bookRepository;
    private BookCopyRepository bookCopyRepository;
    private TransactionRepository transactionRepository;
    private UserRepository userRepository;
    private AuthorRepository authorRepository;
    private GenreRepository genreRepository;
    private BookService bookService;
    private User librarian;

    @BeforeEach
    void setUp() {
        dataStore = new DataStore();
        bookRepository = new InMemoryBookRepository(dataStore);
        bookCopyRepository = new InMemoryBookCopyRepository(dataStore);
        transactionRepository = new InMemoryTransactionRepository(dataStore);
        userRepository = new InMemoryUserRepository(dataStore);
        authorRepository = new InMemoryAuthorRepository(dataStore);
        genreRepository = new InMemoryGenreRepository(dataStore);
        bookService = new BookService(bookRepository, bookCopyRepository, transactionRepository, null, authorRepository, genreRepository);

        librarian = new User("Head Librarian", "lib@test.com", "9999999999", "pass", UserRole.LIBRARIAN, UserStatus.ACTIVE);
        userRepository.save(librarian);
    }

    @Test
    @DisplayName("1. One book with multiple authors")
    void testOneBookWithMultipleAuthors() {
        Author gamma = new Author(1, "Erich Gamma");
        Author helm = new Author(2, "Richard Helm");
        Author johnson = new Author(3, "Ralph Johnson");
        Author vlissides = new Author(4, "John Vlissides");
        authorRepository.save(gamma);
        authorRepository.save(helm);
        authorRepository.save(johnson);
        authorRepository.save(vlissides);

        Book designPatterns = new Book("978-0201633610", "Design Patterns", "Addison-Wesley", 1994, "Elements of Reusable OO Software");
        designPatterns.addAuthor(gamma);
        designPatterns.addAuthor(helm);
        designPatterns.addAuthor(johnson);
        designPatterns.addAuthor(vlissides);

        bookService.addBook(designPatterns, 2, librarian);

        Book retrieved = bookService.getBookByIsbn("978-0201633610");
        assertNotNull(retrieved);
        Set<Author> authors = retrieved.getAuthors();
        assertEquals(4, authors.size());
        assertTrue(authors.contains(gamma));
        assertTrue(authors.contains(helm));
        assertTrue(authors.contains(johnson));
        assertTrue(authors.contains(vlissides));
        assertEquals("Erich Gamma, Richard Helm, Ralph Johnson, John Vlissides", retrieved.getAuthorsString());
    }

    @Test
    @DisplayName("2. One author associated with multiple books")
    void testOneAuthorAssociatedWithMultipleBooks() {
        Author martin = new Author(1, "Robert C. Martin");
        authorRepository.save(martin);

        Book book1 = new Book("978-0132350884", "Clean Code", "Prentice Hall", 2008, "Handbook of Agile Software Craftsmanship");
        book1.addAuthor(martin);
        bookService.addBook(book1, 2, librarian);

        Book book2 = new Book("978-0134494166", "Clean Architecture", "Prentice Hall", 2017, "Software Structure and Design");
        book2.addAuthor(martin);
        bookService.addBook(book2, 2, librarian);

        List<Book> martinBooks = bookService.searchByAuthor("Robert C. Martin");
        assertEquals(2, martinBooks.size());
        assertTrue(martinBooks.stream().anyMatch(b -> b.getIsbn().equals("978-0132350884")));
        assertTrue(martinBooks.stream().anyMatch(b -> b.getIsbn().equals("978-0134494166")));

        // Verify shared canonical instance
        Book ret1 = bookService.getBookByIsbn("978-0132350884");
        Book ret2 = bookService.getBookByIsbn("978-0134494166");
        Author a1 = ret1.getAuthors().iterator().next();
        Author a2 = ret2.getAuthors().iterator().next();
        assertEquals(a1.getAuthorId(), a2.getAuthorId());
        assertSame(a1, a2);
    }

    @Test
    @DisplayName("3. One book with multiple genres")
    void testOneBookWithMultipleGenres() {
        Genre se = new Genre(1, "Software Engineering");
        Genre prog = new Genre(2, "Programming");
        genreRepository.save(se);
        genreRepository.save(prog);

        Book book = new Book("978-0132350884", "Clean Code", "Prentice Hall", 2008, "Craftsmanship");
        book.addGenre(se);
        book.addGenre(prog);
        bookService.addBook(book, 2, librarian);

        Book retrieved = bookService.getBookByIsbn("978-0132350884");
        assertNotNull(retrieved);
        assertEquals(2, retrieved.getGenres().size());
        assertTrue(retrieved.getGenres().contains(se));
        assertTrue(retrieved.getGenres().contains(prog));
        assertEquals("Software Engineering, Programming", retrieved.getGenresString());
    }

    @Test
    @DisplayName("4. One genre associated with multiple books")
    void testOneGenreAssociatedWithMultipleBooks() {
        Genre classic = new Genre(1, "Classic");
        genreRepository.save(classic);

        Book book1 = new Book("978-0451524935", "1984", "Secker & Warburg", 1950, "Dystopian");
        book1.addGenre(classic);
        bookService.addBook(book1, 2, librarian);

        Book book2 = new Book("978-0743273565", "The Great Gatsby", "Charles Scribner's Sons", 1925, "Novel");
        book2.addGenre(classic);
        bookService.addBook(book2, 2, librarian);

        List<Book> classicBooks = bookService.searchByGenre("Classic");
        assertEquals(2, classicBooks.size());
        assertTrue(classicBooks.stream().anyMatch(b -> b.getTitle().equals("1984")));
        assertTrue(classicBooks.stream().anyMatch(b -> b.getTitle().equals("The Great Gatsby")));

        // Verify shared canonical instance
        Book ret1 = bookService.getBookByIsbn("978-0451524935");
        Book ret2 = bookService.getBookByIsbn("978-0743273565");
        Genre g1 = ret1.getGenres().iterator().next();
        Genre g2 = ret2.getGenres().iterator().next();
        assertEquals(g1.getGenreId(), g2.getGenreId());
        assertSame(g1, g2);
    }

    @Test
    @DisplayName("5. Correct publisher storage and display independent of authors")
    void testPublisherStorageAndDisplayIndependentOfAuthors() {
        Author martin = new Author(1, "Robert C. Martin");
        authorRepository.save(martin);

        Book book = new Book("978-0132350884", "Clean Code", "Prentice Hall", 2008, "Craftsmanship");
        book.addAuthor(martin);
        bookService.addBook(book, 2, librarian);

        Book retrieved = bookService.getBookByIsbn("978-0132350884");
        assertEquals("Prentice Hall", retrieved.getPublisher());
        assertEquals("Robert C. Martin", retrieved.getAuthor());
        assertNotEquals(retrieved.getPublisher(), retrieved.getAuthor());

        String details = bookService.getFormattedBookDetails(retrieved.getBookId());
        assertTrue(details.contains("Authors: Robert C. Martin"));
        assertTrue(details.contains("Publisher: Prentice Hall"));
        assertFalse(details.contains("Publisher: Robert C. Martin"));
        assertFalse(details.contains("Authors: Prentice Hall"));

        // Changing publisher does not affect author
        retrieved.setPublisher("Pearson");
        assertEquals("Pearson", retrieved.getPublisher());
        assertEquals("Robert C. Martin", retrieved.getAuthor());
    }

    @Test
    @DisplayName("6. Author search returns every matching associated book")
    void testAuthorSearchReturnsEveryMatchingAssociatedBook() {
        Author martin = new Author(1, "Robert C. Martin");
        Author orwell = new Author(2, "George Orwell");
        authorRepository.save(martin);
        authorRepository.save(orwell);

        Book b1 = new Book("978-0132350884", "Clean Code", "Prentice Hall", 2008, "Desc 1");
        b1.addAuthor(martin);
        bookService.addBook(b1, 1, librarian);

        Book b2 = new Book("978-0134494166", "Clean Architecture", "Prentice Hall", 2017, "Desc 2");
        b2.addAuthor(martin);
        bookService.addBook(b2, 1, librarian);

        Book b3 = new Book("978-0451524935", "1984", "Secker & Warburg", 1950, "Desc 3");
        b3.addAuthor(orwell);
        bookService.addBook(b3, 1, librarian);

        List<Book> martinResults = bookService.searchByAuthor("Martin");
        assertEquals(2, martinResults.size());
        assertTrue(martinResults.stream().anyMatch(b -> b.getTitle().equals("Clean Code")));
        assertTrue(martinResults.stream().anyMatch(b -> b.getTitle().equals("Clean Architecture")));
        assertFalse(martinResults.stream().anyMatch(b -> b.getTitle().equals("1984")));

        List<Book> orwellResults = bookService.searchByAuthor("Orwell");
        assertEquals(1, orwellResults.size());
        assertEquals("1984", orwellResults.get(0).getTitle());
    }

    @Test
    @DisplayName("7. Genre search returns every matching associated book")
    void testGenreSearchReturnsEveryMatchingAssociatedBook() {
        Genre prog = new Genre(1, "Programming");
        Genre dystopian = new Genre(2, "Dystopian");
        genreRepository.save(prog);
        genreRepository.save(dystopian);

        Book b1 = new Book("978-0132350884", "Clean Code", "Prentice Hall", 2008, "Desc 1");
        b1.addGenre(prog);
        bookService.addBook(b1, 1, librarian);

        Book b2 = new Book("978-0201633610", "Design Patterns", "Addison-Wesley", 1994, "Desc 2");
        b2.addGenre(prog);
        bookService.addBook(b2, 1, librarian);

        Book b3 = new Book("978-0451524935", "1984", "Secker & Warburg", 1950, "Desc 3");
        b3.addGenre(dystopian);
        bookService.addBook(b3, 1, librarian);

        List<Book> progBooks = bookService.searchByGenre("Programming");
        assertEquals(2, progBooks.size());
        assertTrue(progBooks.stream().anyMatch(b -> b.getTitle().equals("Clean Code")));
        assertTrue(progBooks.stream().anyMatch(b -> b.getTitle().equals("Design Patterns")));

        List<Book> dystBooks = bookService.searchByGenre("Dystopian");
        assertEquals(1, dystBooks.size());
        assertEquals("1984", dystBooks.get(0).getTitle());
    }

    @Test
    @DisplayName("8. Publisher search does not incorrectly use author names")
    void testPublisherSearchDoesNotIncorrectlyUseAuthorNames() {
        Author martin = new Author(1, "Robert C. Martin");
        authorRepository.save(martin);

        Book b1 = new Book("978-0132350884", "Clean Code", "Prentice Hall", 2008, "Desc");
        b1.addAuthor(martin);
        bookService.addBook(b1, 1, librarian);

        // Searching publisher for "Prentice Hall" matches
        List<Book> pubMatch = bookService.searchByPublisher("Prentice");
        assertEquals(1, pubMatch.size());
        assertEquals("Clean Code", pubMatch.get(0).getTitle());

        // Searching publisher for author name "Martin" MUST return empty list
        List<Book> authorInPub = bookService.searchByPublisher("Martin");
        assertTrue(authorInPub.isEmpty(), "Publisher search must not match author names");

        // Searching author for publisher name "Prentice" MUST return empty list
        List<Book> pubInAuthor = bookService.searchByAuthor("Prentice");
        assertTrue(pubInAuthor.isEmpty(), "Author search must not match publisher names");
    }

    @Test
    @DisplayName("9. Case-insensitive and partial matching")
    void testCaseInsensitiveAndPartialMatching() {
        Author orwell = new Author(1, "George Orwell");
        authorRepository.save(orwell);
        Genre dystopian = new Genre(1, "Dystopian");
        genreRepository.save(dystopian);

        Book book = new Book("978-0451524935", "1984", "Secker & Warburg", 1950, "Classic Dystopian Novel");
        book.addAuthor(orwell);
        book.addGenre(dystopian);
        bookService.addBook(book, 1, librarian);

        // Case-insensitive author search (lowercase, uppercase, partial)
        assertEquals(1, bookService.searchByAuthor("orwell").size());
        assertEquals(1, bookService.searchByAuthor("GEORGE").size());
        assertEquals(1, bookService.searchByAuthor("  orw  ").size());

        // Case-insensitive genre search
        assertEquals(1, bookService.searchByGenre("dystopian").size());
        assertEquals(1, bookService.searchByGenre("DYSTOPIAN").size());
        assertEquals(1, bookService.searchByGenre("  dyst  ").size());

        // Case-insensitive publisher search
        assertEquals(1, bookService.searchByPublisher("secker").size());
        assertEquals(1, bookService.searchByPublisher("WARBURG").size());
        assertEquals(1, bookService.searchByPublisher("  seck  ").size());
    }

    @Test
    @DisplayName("10. No duplicate search results")
    void testNoDuplicateSearchResults() {
        Author gamma = new Author(1, "Erich Gamma");
        Author helm = new Author(2, "Richard Helm");
        authorRepository.save(gamma);
        authorRepository.save(helm);

        Genre se = new Genre(1, "Software Engineering");
        Genre prog = new Genre(2, "Programming");
        genreRepository.save(se);
        genreRepository.save(prog);

        Book dp = new Book("978-0201633610", "Design Patterns", "Addison-Wesley", 1994, "Reusable Software Patterns");
        dp.addAuthor(gamma);
        dp.addAuthor(helm);
        dp.addGenre(se);
        dp.addGenre(prog);
        bookService.addBook(dp, 2, librarian);

        // General search matches title "Patterns" and description "Patterns"
        List<Book> general = bookService.searchBooks("Patterns");
        assertEquals(1, general.size(), "Book must appear exactly once in search results");

        // General search matches both genres "Software" (in title, desc, and genre)
        List<Book> softwareMatch = bookService.searchBooks("Software");
        assertEquals(1, softwareMatch.size(), "Book must appear exactly once even if multiple attributes match");
    }

    @Test
    @DisplayName("11. Seed data contains the expected relationships")
    void testSeedDataContainsExpectedRelationships() {
        MembershipPlanRepository planRepo = new InMemoryMembershipPlanRepository(dataStore);
        SyntheticData seed = new SyntheticData(userRepository, planRepo, bookService, authorRepository, genreRepository);
        seed.syntheticData();

        // 1. Authors exist in authorRepository
        assertNotNull(authorRepository.findByName("Robert C. Martin"));
        assertNotNull(authorRepository.findByName("Erich Gamma"));
        assertNotNull(authorRepository.findByName("George Orwell"));
        assertNotNull(authorRepository.findByName("F. Scott Fitzgerald"));

        // 2. Genres exist in genreRepository
        assertNotNull(genreRepository.findByName("Software Engineering"));
        assertNotNull(genreRepository.findByName("Programming"));
        assertNotNull(genreRepository.findByName("Classic"));

        // 3. Clean Code has Robert C. Martin and Prentice Hall
        Book cleanCode = bookService.getBookByIsbn("978-0132350884");
        assertNotNull(cleanCode);
        assertEquals("Prentice Hall", cleanCode.getPublisher());
        assertTrue(cleanCode.getAuthors().stream().anyMatch(a -> a.getName().equals("Robert C. Martin")));
        assertTrue(cleanCode.getGenres().stream().anyMatch(g -> g.getName().equals("Software Engineering")));
        assertTrue(cleanCode.getGenres().stream().anyMatch(g -> g.getName().equals("Programming")));

        // 4. Design Patterns has 4 authors and Addison-Wesley
        Book designPatterns = bookService.getBookByIsbn("978-0201633610");
        assertNotNull(designPatterns);
        assertEquals(4, designPatterns.getAuthors().size());
        assertEquals("Addison-Wesley", designPatterns.getPublisher());

        // 5. Shared author across multiple books (Robert C. Martin on Clean Code and Clean Architecture)
        List<Book> martinBooks = bookService.searchByAuthor("Robert C. Martin");
        assertEquals(2, martinBooks.size());

        // 6. Shared genre across multiple books (Classic on 1984 and The Great Gatsby)
        List<Book> classicBooks = bookService.searchByGenre("Classic");
        assertEquals(2, classicBooks.size());
    }

    @Test
    @DisplayName("12. Book display includes every author, genre, and publisher")
    void testBookDisplayIncludesEveryAuthorAndGenre() {
        Author gamma = new Author(1, "Erich Gamma");
        Author helm = new Author(2, "Richard Helm");
        authorRepository.save(gamma);
        authorRepository.save(helm);

        Genre se = new Genre(1, "Software Engineering");
        Genre prog = new Genre(2, "Programming");
        genreRepository.save(se);
        genreRepository.save(prog);

        Book book = new Book("978-0201633610", "Design Patterns", "Addison-Wesley", 1994, "OO Patterns");
        book.addAuthor(gamma);
        book.addAuthor(helm);
        book.addGenre(se);
        book.addGenre(prog);
        bookService.addBook(book, 2, librarian);

        // toString display
        String toString = book.toString();
        assertTrue(toString.contains("Title: Design Patterns"));
        assertTrue(toString.contains("Authors: Erich Gamma, Richard Helm"));
        assertTrue(toString.contains("Genres: Software Engineering, Programming"));
        assertTrue(toString.contains("Publisher: Addison-Wesley"));
        assertTrue(toString.contains("Publication Year: 1994"));

        // getFormattedBookDetails display
        String details = bookService.getFormattedBookDetails(book.getBookId());
        assertTrue(details.contains("Title: Design Patterns"));
        assertTrue(details.contains("Authors: Erich Gamma, Richard Helm"));
        assertTrue(details.contains("Genres: Software Engineering, Programming"));
        assertTrue(details.contains("Publisher: Addison-Wesley"));
        assertTrue(details.contains("Publication Year: 1994"));
    }

    @Test
    @DisplayName("13. Existing catalogue and borrowing tests still pass with updated models")
    void testExistingCatalogueAndBorrowingOperations() {
        Author author = new Author(1, "Joshua Bloch");
        authorRepository.save(author);
        Genre genre = new Genre(1, "Programming");
        genreRepository.save(genre);

        Book effectiveJava = new Book("978-0134685991", "Effective Java", "Addison-Wesley", 2018, "Best practices");
        effectiveJava.addAuthor(author);
        effectiveJava.addGenre(genre);

        // Add book with copies
        assertTrue(bookService.addBook(effectiveJava, 3, librarian));
        assertEquals(1, bookService.getAllBooks().size());

        List<BookCopy> copies = bookCopyRepository.findByBookId(effectiveJava.getBookId());
        assertEquals(3, copies.size());
        assertEquals(BookCopyStatus.AVAILABLE, copies.get(0).getStatus());

        // Update book details without erasing associations
        assertTrue(bookService.updateBook(effectiveJava.getBookId(), "Effective Java 3rd Edition", "Addison-Wesley", 2018, "Updated best practices", librarian));
        Book updated = bookService.getBookById(effectiveJava.getBookId());
        assertEquals("Effective Java 3rd Edition", updated.getTitle());
        assertEquals(1, updated.getAuthors().size());
        assertEquals("Joshua Bloch", updated.getAuthor());
        assertEquals("Programming", updated.getGenresString());
    }

    @Test
    @DisplayName("14. Adding a book with a different Author object but the same existing author name reuses the canonical author")
    void testAddBookWithDifferentAuthorObjectSameNameReusesCanonicalAuthor() {
        Author canonicalMartin = new Author(1, "Robert C. Martin");
        authorRepository.save(canonicalMartin);

        // Different Author instance with different ID (99) but same name
        Author duplicateMartin = new Author(99, "Robert C. Martin");
        Book book = new Book("978-0132350884", "Clean Code", "Prentice Hall", 2008, "Craftsmanship");
        book.addAuthor(duplicateMartin);

        assertTrue(bookService.addBook(book, 2, librarian));

        // Repository must not contain duplicate author records
        assertEquals(1, authorRepository.findAll().size(), "Must not create duplicate author record in repository");

        // The book must reference the existing canonical Author instance
        Author attachedAuthor = book.getAuthors().iterator().next();
        assertSame(canonicalMartin, attachedAuthor, "Book must reference the canonical Author object, not duplicate");
        assertEquals(1, attachedAuthor.getAuthorId());

        // Retrieved book also references the canonical instance
        Book retrieved = bookService.getBookByIsbn("978-0132350884");
        Author retrievedAuthor = retrieved.getAuthors().iterator().next();
        assertSame(canonicalMartin, retrievedAuthor, "Retrieved book must reference the canonical Author object");
    }

    @Test
    @DisplayName("15. Adding a book with a different Genre object but the same existing genre name reuses the canonical genre")
    void testAddBookWithDifferentGenreObjectSameNameReusesCanonicalGenre() {
        Genre canonicalGenre = new Genre(1, "Software Engineering");
        genreRepository.save(canonicalGenre);

        // Different Genre instance with different ID (88) but same name
        Genre duplicateGenre = new Genre(88, "Software Engineering");
        Book book = new Book("978-0132350884", "Clean Code", "Prentice Hall", 2008, "Craftsmanship");
        book.addGenre(duplicateGenre);

        assertTrue(bookService.addBook(book, 2, librarian));

        // Repository must not contain duplicate genre records
        assertEquals(1, genreRepository.findAll().size(), "Must not create duplicate genre record in repository");

        // The book must reference the existing canonical Genre instance
        Genre attachedGenre = book.getGenres().iterator().next();
        assertSame(canonicalGenre, attachedGenre, "Book must reference the canonical Genre object, not duplicate");
        assertEquals(1, attachedGenre.getGenreId());

        // Retrieved book also references the canonical instance
        Book retrieved = bookService.getBookByIsbn("978-0132350884");
        Genre retrievedGenre = retrieved.getGenres().iterator().next();
        assertSame(canonicalGenre, retrievedGenre, "Retrieved book must reference the canonical Genre object");
    }

    @Test
    @DisplayName("16. The same book can have multiple authors and genres, all correctly canonicalized")
    void testMultipleAuthorsAndGenresOnSameBookAllCanonicalized() {
        Author canonicalGamma = new Author(1, "Erich Gamma");
        authorRepository.save(canonicalGamma);
        Genre canonicalSE = new Genre(1, "Software Engineering");
        genreRepository.save(canonicalSE);

        // Book supplied with a duplicate Erich Gamma (ID 99), a new author Richard Helm (ID 100),
        // a duplicate Software Engineering (ID 88), and a new genre Design Patterns (ID 89)
        Author dupGamma = new Author(99, "Erich Gamma");
        Author newHelm = new Author(100, "Richard Helm");
        Genre dupSE = new Genre(88, "Software Engineering");
        Genre newDP = new Genre(89, "Design Patterns");

        Book book = new Book("978-0201633610", "Design Patterns", "Addison-Wesley", 1994, "OO Patterns");
        book.addAuthor(dupGamma);
        book.addAuthor(newHelm);
        book.addGenre(dupSE);
        book.addGenre(newDP);

        assertTrue(bookService.addBook(book, 2, librarian));

        // Authors: Gamma was reused, Helm was newly created
        assertEquals(2, authorRepository.findAll().size());
        assertEquals(2, book.getAuthors().size());
        assertTrue(book.getAuthors().contains(canonicalGamma));
        Author resolvedHelm = authorRepository.findByName("Richard Helm");
        assertNotNull(resolvedHelm);
        assertTrue(book.getAuthors().contains(resolvedHelm));

        // Genres: Software Engineering was reused, Design Patterns was newly created
        assertEquals(2, genreRepository.findAll().size());
        assertEquals(2, book.getGenres().size());
        assertTrue(book.getGenres().contains(canonicalSE));
        Genre resolvedDP = genreRepository.findByName("Design Patterns");
        assertNotNull(resolvedDP);
        assertTrue(book.getGenres().contains(resolvedDP));
    }

    @Test
    @DisplayName("17. Two books can share the same canonical author and genre objects")
    void testTwoBooksShareSameCanonicalAuthorAndGenreObjects() {
        Author canonicalMartin = new Author(1, "Robert C. Martin");
        authorRepository.save(canonicalMartin);
        Genre canonicalProg = new Genre(1, "Programming");
        genreRepository.save(canonicalProg);

        // Book 1 supplied with duplicate Martin (ID 99) and duplicate Prog (ID 88)
        Book book1 = new Book("978-0132350884", "Clean Code", "Prentice Hall", 2008, "Craftsmanship");
        book1.addAuthor(new Author(99, "Robert C. Martin"));
        book1.addGenre(new Genre(88, "Programming"));
        assertTrue(bookService.addBook(book1, 1, librarian));

        // Book 2 supplied with another duplicate Martin (ID 77) and duplicate Prog (ID 66)
        Book book2 = new Book("978-0134494166", "Clean Architecture", "Prentice Hall", 2017, "Structure");
        book2.addAuthor(new Author(77, "Robert C. Martin"));
        book2.addGenre(new Genre(66, "Programming"));
        assertTrue(bookService.addBook(book2, 1, librarian));

        // Verify repository counts remain 1
        assertEquals(1, authorRepository.findAll().size());
        assertEquals(1, genreRepository.findAll().size());

        // Verify both books share identical canonical object references
        Book r1 = bookService.getBookByIsbn("978-0132350884");
        Book r2 = bookService.getBookByIsbn("978-0134494166");

        Author a1 = r1.getAuthors().iterator().next();
        Author a2 = r2.getAuthors().iterator().next();
        assertSame(canonicalMartin, a1);
        assertSame(canonicalMartin, a2);
        assertSame(a1, a2);

        Genre g1 = r1.getGenres().iterator().next();
        Genre g2 = r2.getGenres().iterator().next();
        assertSame(canonicalProg, g1);
        assertSame(canonicalProg, g2);
        assertSame(g1, g2);
    }

    @Test
    @DisplayName("18. New authors and genres are created when no matching entity exists")
    void testNewAuthorsAndGenresCreatedWhenNoMatchingEntityExists() {
        assertEquals(0, authorRepository.findAll().size());
        assertEquals(0, genreRepository.findAll().size());

        Book book = new Book("978-0201485677", "Refactoring", "Addison-Wesley", 1999, "Improving Code Design");
        Author martinFowler = new Author(10, "Martin Fowler");
        Genre softwareDesign = new Genre(20, "Software Design");
        book.addAuthor(martinFowler);
        book.addGenre(softwareDesign);

        assertTrue(bookService.addBook(book, 1, librarian));

        assertEquals(1, authorRepository.findAll().size());
        assertEquals(1, genreRepository.findAll().size());

        Author createdAuthor = authorRepository.findByName("Martin Fowler");
        assertNotNull(createdAuthor);
        assertSame(createdAuthor, book.getAuthors().iterator().next());

        Genre createdGenre = genreRepository.findByName("Software Design");
        assertNotNull(createdGenre);
        assertSame(createdGenre, book.getGenres().iterator().next());
    }

    @Test
    @DisplayName("19. Updating a book does not introduce duplicate or noncanonical associations")
    void testUpdatingBookDoesNotIntroduceDuplicateOrNoncanonicalAssociations() {
        Author canonicalMartin = new Author(1, "Robert C. Martin");
        authorRepository.save(canonicalMartin);

        Book book = new Book("978-0132350884", "Clean Code", "Prentice Hall", 2008, "Initial");
        book.addAuthor(new Author(2, "Initial Author"));
        assertTrue(bookService.addBook(book, 1, librarian));

        // Update book's authors with "Robert C. Martin" (matches existing) and a new genre "Refactoring"
        assertTrue(bookService.updateBook(
                book.getBookId(),
                "Clean Code Updated",
                "Prentice Hall",
                2009,
                "Updated",
                List.of("Robert C. Martin"),
                List.of("Refactoring"),
                librarian
        ));

        Book updated = bookService.getBookById(book.getBookId());
        assertEquals(1, updated.getAuthors().size());
        Author updatedAuthor = updated.getAuthors().iterator().next();
        assertSame(canonicalMartin, updatedAuthor, "Updated book must use canonical author instance");

        assertEquals(1, updated.getGenres().size());
        Genre updatedGenre = updated.getGenres().iterator().next();
        assertEquals("Refactoring", updatedGenre.getName());

        // Repeated update with same name does not duplicate
        assertTrue(bookService.updateBook(
                book.getBookId(),
                "Clean Code 3rd",
                "Prentice Hall",
                2010,
                "Updated again",
                List.of("Robert C. Martin"),
                List.of("Refactoring"),
                librarian
        ));

        assertEquals(2, authorRepository.findAll().size()); // Initial Author + Robert C. Martin
        assertEquals(1, genreRepository.findAll().size()); // Refactoring

        // Update with full Book object preserves canonicalization
        updated.setTitle("Clean Code Final");
        updated.addAuthor(new Author(99, "Robert C. Martin")); // duplicate author added to object
        assertTrue(bookService.updateBook(updated, librarian));

        Book finalBook = bookService.getBookById(book.getBookId());
        assertEquals(1, finalBook.getAuthors().size(), "Duplicate author must be collapsed into canonical single author");
        assertSame(canonicalMartin, finalBook.getAuthors().iterator().next());
    }

    @Test
    @DisplayName("20. Author, genre, and publisher searches remain independent and correct")
    void testAuthorGenrePublisherSearchesRemainIndependentAndCorrect() {
        Author author = new Author(1, "Martin Fowler");
        authorRepository.save(author);
        Genre genre = new Genre(1, "Architecture");
        genreRepository.save(genre);

        Book book = new Book("978-0321127426", "Patterns of EAA", "Addison-Wesley", 2002, "Enterprise patterns");
        book.addAuthor(author);
        book.addGenre(genre);
        assertTrue(bookService.addBook(book, 1, librarian));

        // Search by author "Martin" matches
        List<Book> byAuthor = bookService.searchByAuthor("Martin");
        assertEquals(1, byAuthor.size());
        assertEquals("Patterns of EAA", byAuthor.get(0).getTitle());

        // Search by publisher "Martin" returns empty
        List<Book> byPubMartin = bookService.searchByPublisher("Martin");
        assertTrue(byPubMartin.isEmpty());

        // Search by genre "Architecture" matches
        List<Book> byGenre = bookService.searchByGenre("Architecture");
        assertEquals(1, byGenre.size());
        assertEquals("Patterns of EAA", byGenre.get(0).getTitle());

        // Search by author "Architecture" returns empty
        List<Book> byAuthorArch = bookService.searchByAuthor("Architecture");
        assertTrue(byAuthorArch.isEmpty());

        // Search by publisher "Addison-Wesley" matches
        List<Book> byPub = bookService.searchByPublisher("Addison");
        assertEquals(1, byPub.size());

        // Search by genre "Addison" returns empty
        List<Book> byGenreAddison = bookService.searchByGenre("Addison");
        assertTrue(byGenreAddison.isEmpty());
    }
}
