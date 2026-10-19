package service;

import enums.BookCopyStatus;
import enums.UserRole;
import enums.UserStatus;
import model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import repository.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BookSearchAndSortTest {

    private DataStore dataStore;
    private BookRepository bookRepository;
    private BookCopyRepository bookCopyRepository;
    private TransactionRepository transactionRepository;
    private UserRepository userRepository;
    private AuditLogRepository auditLogRepository;
    private AuditService auditService;
    private BookService bookService;
    private User librarian;

    private Book book1;
    private Book book2;
    private Book book3;

    @BeforeEach
    void setUp() {
        dataStore = new DataStore();
        bookRepository = new InMemoryBookRepository(dataStore);
        bookCopyRepository = new InMemoryBookCopyRepository(dataStore);
        transactionRepository = new InMemoryTransactionRepository(dataStore);
        userRepository = new InMemoryUserRepository(dataStore);
        auditLogRepository = new InMemoryAuditLogRepository(dataStore);
        auditService = new AuditService(auditLogRepository);
        bookService = new BookService(bookRepository, bookCopyRepository, transactionRepository, auditService);

        librarian = new User("Head Librarian", "lib@test.com", "9999999999", "pass", UserRole.LIBRARIAN, UserStatus.ACTIVE);
        userRepository.save(librarian);

        // Book 1: Clean Code
        book1 = new Book("978-0132350884", "Clean Code", "Prentice Hall", 2008, "Handbook of Agile Software Craftsmanship");
        book1.addAuthor(new Author(1, "Robert C. Martin"));
        book1.addCategory(new Category(1, "Programming"));
        book1.addGenre(new Genre(1, "Technical"));
        bookRepository.save(book1);
        bookCopyRepository.save(new BookCopy(101, book1.getBookId(), BookCopyStatus.AVAILABLE));
        bookCopyRepository.save(new BookCopy(102, book1.getBookId(), BookCopyStatus.ISSUED));

        // Book 2: 1984
        book2 = new Book("978-0451524935", "1984", "Secker & Warburg", 1950, "Classic Dystopian Novel");
        book2.addAuthor(new Author(2, "George Orwell"));
        book2.addCategory(new Category(2, "Literature"));
        book2.addGenre(new Genre(2, "Dystopian"));
        bookRepository.save(book2);
        bookCopyRepository.save(new BookCopy(201, book2.getBookId(), BookCopyStatus.AVAILABLE));

        // Book 3: The Great Gatsby
        book3 = new Book("978-0743273565", "The Great Gatsby", "Charles Scribner's Sons", 1925, "Classic 1920s Novel");
        book3.addAuthor(new Author(3, "F. Scott Fitzgerald"));
        book3.addCategory(new Category(2, "Literature"));
        book3.addGenre(new Genre(3, "Classic"));
        bookRepository.save(book3);
        bookCopyRepository.save(new BookCopy(301, book3.getBookId(), BookCopyStatus.RESERVED));
        bookCopyRepository.save(new BookCopy(302, book3.getBookId(), BookCopyStatus.DAMAGED));
    }

    @Test
    @DisplayName("1. Book creation and retrieval use author and publisher fields")
    void testBookCreationAndRetrievalUsesAuthorField() {
        Book book = new Book("978-0201633610", "Design Patterns", "Addison-Wesley", 1994, "Gang of Four Patterns");
        book.addAuthor(new Author(10, "Erich Gamma"));
        assertEquals("Erich Gamma", book.getAuthor());
        assertEquals("Addison-Wesley", book.getPublisher());
        assertTrue(book.toString().contains("Authors: Erich Gamma"));
        assertTrue(book.toString().contains("Publisher: Addison-Wesley"));

        book.setAuthor("GoF Authors");
        assertEquals("GoF Authors", book.getAuthor());

        bookRepository.save(book);
        Book retrieved = bookRepository.findByIsbn("978-0201633610");
        assertNotNull(retrieved);
        assertEquals("GoF Authors", retrieved.getAuthor());
        assertEquals("Addison-Wesley", retrieved.getPublisher());
    }

    @Test
    @DisplayName("2. Publisher is stored and retrieved independently of authors")
    void testPublisherStorageAndRetrievalIndependentOfAuthors() {
        Book book = new Book("978-0134494166", "Clean Architecture", "Prentice Hall", 2017, "Architecture Guide");
        Author martin = new Author(1, "Robert C. Martin");
        book.addAuthor(martin);

        assertEquals("Prentice Hall", book.getPublisher());
        assertEquals("Robert C. Martin", book.getAuthor());
        assertNotEquals(book.getPublisher(), book.getAuthor());

        book.setPublisher("Pearson");
        assertEquals("Pearson", book.getPublisher());
        assertEquals("Robert C. Martin", book.getAuthor());
    }

    @Test
    @DisplayName("3. Exact ISBN and book ID searches work")
    void testExactIsbnAndBookIdSearches() {
        // Search by ID
        List<Book> byId = bookService.searchById(book1.getBookId());
        assertEquals(1, byId.size());
        assertEquals("Clean Code", byId.get(0).getTitle());

        List<Book> byInvalidId = bookService.searchById(999);
        assertTrue(byInvalidId.isEmpty());

        // Search by ISBN
        List<Book> byIsbn = bookService.searchByIsbn("978-0451524935");
        assertEquals(1, byIsbn.size());
        assertEquals("1984", byIsbn.get(0).getTitle());

        List<Book> byInvalidIsbn = bookService.searchByIsbn("000-0000000000");
        assertTrue(byInvalidIsbn.isEmpty());
    }

    @Test
    @DisplayName("4. Partial case-insensitive title and author searches work")
    void testPartialCaseInsensitiveTitleAndAuthorSearches() {
        // Case-insensitive partial title search
        List<Book> titleMatch = bookService.searchByTitle("clean");
        assertEquals(1, titleMatch.size());
        assertEquals("Clean Code", titleMatch.get(0).getTitle());

        List<Book> titleMatchUpper = bookService.searchByTitle("GATSBY");
        assertEquals(1, titleMatchUpper.size());
        assertEquals("The Great Gatsby", titleMatchUpper.get(0).getTitle());

        // Case-insensitive partial author search
        List<Book> authorMatch = bookService.searchByAuthor("orwell");
        assertEquals(1, authorMatch.size());
        assertEquals("1984", authorMatch.get(0).getTitle());

        List<Book> authorMatchUpper = bookService.searchByAuthor("MARTIN");
        assertEquals(1, authorMatchUpper.size());
        assertEquals("Clean Code", authorMatchUpper.get(0).getTitle());
    }

    @Test
    @DisplayName("5. Leading and trailing whitespace is handled safely")
    void testLeadingAndTrailingWhitespaceHandled() {
        // Whitespace in query
        List<Book> titleWhitespace = bookService.searchByField("title", "   Clean Code   ");
        assertEquals(1, titleWhitespace.size());
        assertEquals("Clean Code", titleWhitespace.get(0).getTitle());

        List<Book> authorWhitespace = bookService.searchByField("author", "   George Orwell   ");
        assertEquals(1, authorWhitespace.size());
        assertEquals("1984", authorWhitespace.get(0).getTitle());

        List<Book> isbnWhitespace = bookService.searchByField("isbn", "   978-0743273565   ");
        assertEquals(1, isbnWhitespace.size());
        assertEquals("The Great Gatsby", isbnWhitespace.get(0).getTitle());

        // Blank or whitespace-only queries return empty list safely
        List<Book> blankQuery = bookService.searchByField("title", "     ");
        assertTrue(blankQuery.isEmpty());

        List<Book> nullQuery = bookService.searchByField("title", null);
        assertTrue(nullQuery.isEmpty());
    }

    @Test
    @DisplayName("6. Multiple search results and no-match cases work")
    void testMultipleSearchResultsAndNoMatchCases() {
        // Literature category matches both book2 and book3
        List<Book> categoryMatches = bookService.searchByGenreOrCategory("Literature");
        assertEquals(2, categoryMatches.size());

        // No match query
        List<Book> noMatches = bookService.searchByTitle("Nonexistent Quantum Physics");
        assertTrue(noMatches.isEmpty());
    }

    @Test
    @DisplayName("7. Empty catalogue behavior is handled gracefully")
    void testEmptyCatalogueBehavior() {
        DataStore emptyStore = new DataStore();
        BookRepository emptyRepo = new InMemoryBookRepository(emptyStore);
        BookCopyRepository emptyCopies = new InMemoryBookCopyRepository(emptyStore);
        TransactionRepository emptyTx = new InMemoryTransactionRepository(emptyStore);
        BookService emptyService = new BookService(emptyRepo, emptyCopies, emptyTx);

        assertTrue(emptyService.getAllBooks().isEmpty());
        assertTrue(emptyService.searchBooks("anything").isEmpty());
        assertTrue(emptyService.searchByField("author", "Orwell").isEmpty());
        assertTrue(emptyService.sortBooks(emptyService.getAllBooks(), "title", true).isEmpty());
        assertNull(emptyService.getFormattedBookDetails(1));
    }

    @Test
    @DisplayName("8. Sorting works in ascending and descending order for all fields")
    void testSortingInBothDirectionsForEveryField() {
        List<Book> all = bookService.getAllBooks();

        // 1. Book ID
        List<Book> byIdAsc = bookService.sortBooks(all, "id", true);
        assertEquals(book1.getBookId(), byIdAsc.get(0).getBookId());
        assertEquals(book3.getBookId(), byIdAsc.get(2).getBookId());

        List<Book> byIdDesc = bookService.sortBooks(all, "id", false);
        assertEquals(book3.getBookId(), byIdDesc.get(0).getBookId());
        assertEquals(book1.getBookId(), byIdDesc.get(2).getBookId());

        // 2. ISBN
        List<Book> byIsbnAsc = bookService.sortBooks(all, "isbn", true);
        assertEquals("978-0132350884", byIsbnAsc.get(0).getIsbn());
        List<Book> byIsbnDesc = bookService.sortBooks(all, "isbn", false);
        assertEquals("978-0743273565", byIsbnDesc.get(0).getIsbn());

        // 3. Title
        List<Book> byTitleAsc = bookService.sortBooks(all, "title", true);
        assertEquals("1984", byTitleAsc.get(0).getTitle());
        assertEquals("The Great Gatsby", byTitleAsc.get(2).getTitle());

        List<Book> byTitleDesc = bookService.sortBooks(all, "title", false);
        assertEquals("The Great Gatsby", byTitleDesc.get(0).getTitle());
        assertEquals("1984", byTitleDesc.get(2).getTitle());

        // 4. Author
        List<Book> byAuthorAsc = bookService.sortBooks(all, "author", true);
        assertEquals("F. Scott Fitzgerald", byAuthorAsc.get(0).getAuthor());
        assertEquals("Robert C. Martin", byAuthorAsc.get(2).getAuthor());

        List<Book> byAuthorDesc = bookService.sortBooks(all, "author", false);
        assertEquals("Robert C. Martin", byAuthorDesc.get(0).getAuthor());
        assertEquals("F. Scott Fitzgerald", byAuthorDesc.get(2).getAuthor());

        // 5. Genre / Category
        List<Book> byGenreAsc = bookService.sortBooks(all, "genre", true);
        assertNotNull(byGenreAsc);
        assertEquals(3, byGenreAsc.size());

        // 6. Publication Year
        List<Book> byYearAsc = bookService.sortBooks(all, "year", true);
        assertEquals(1925, byYearAsc.get(0).getPublicationYear());
        assertEquals(2008, byYearAsc.get(2).getPublicationYear());

        List<Book> byYearDesc = bookService.sortBooks(all, "year", false);
        assertEquals(2008, byYearDesc.get(0).getPublicationYear());
        assertEquals(1925, byYearDesc.get(2).getPublicationYear());
    }

    @Test
    @DisplayName("9. Case-insensitive sorting and null handling work safely")
    void testCaseInsensitiveSortingAndNullHandling() {
        Book bA = new Book(10, "111", "apple", "Pub A", 2000, "desc");
        Book bB = new Book(11, "222", "Banana", "Pub B", 2000, "desc");
        Book bC = new Book(12, "333", "cherry", "Pub C", 2000, "desc");

        List<Book> mixedCase = List.of(bB, bA, bC);
        List<Book> sorted = bookService.sortBooks(mixedCase, "title", true);

        assertEquals("apple", sorted.get(0).getTitle());
        assertEquals("Banana", sorted.get(1).getTitle());
        assertEquals("cherry", sorted.get(2).getTitle());

        // Null list handling
        List<Book> nullList = bookService.sortBooks(null, "title", true);
        assertNotNull(nullList);
        assertTrue(nullList.isEmpty());
    }

    @Test
    @DisplayName("10. Equal values are sorted deterministically using bookId tie-breaker")
    void testDeterministicTieBreaking() {
        Book b1 = new Book(10, "111", "Same Title", "Same Publisher", 2020, "desc");
        Book b2 = new Book(20, "222", "Same Title", "Same Publisher", 2020, "desc");
        Book b3 = new Book(30, "333", "Same Title", "Same Publisher", 2020, "desc");

        List<Book> list = List.of(b3, b1, b2);
        List<Book> sortedAsc = bookService.sortBooks(list, "title", true);
        assertEquals(10, sortedAsc.get(0).getBookId());
        assertEquals(20, sortedAsc.get(1).getBookId());
        assertEquals(30, sortedAsc.get(2).getBookId());

        List<Book> sortedDesc = bookService.sortBooks(list, "title", false);
        // Tie-breaker consistently orders by bookId when fields match
        assertEquals(10, sortedDesc.get(0).getBookId());
        assertEquals(20, sortedDesc.get(1).getBookId());
        assertEquals(30, sortedDesc.get(2).getBookId());
    }

    @Test
    @DisplayName("11. Search results can be sorted without mutating original catalogue")
    void testSearchAndSortIntegration() {
        List<Book> searchResults = bookService.searchByGenreOrCategory("Literature");
        assertEquals(2, searchResults.size());

        List<Book> sortedResults = bookService.sortBooks(searchResults, "year", true);
        assertEquals(1925, sortedResults.get(0).getPublicationYear()); // Gatsby
        assertEquals(1950, sortedResults.get(1).getPublicationYear()); // 1984

        // Underlying repository order remains unaffected
        List<Book> catalog = bookService.getAllBooks();
        assertEquals(3, catalog.size());
        assertEquals(book1.getBookId(), catalog.get(0).getBookId());
    }

    @Test
    @DisplayName("12. Book details viewing displays correct author, publisher, and copy breakdown")
    void testBookDetailViewingDisplaysCorrectAuthorAndCopies() {
        String details1 = bookService.getFormattedBookDetails(book1.getBookId());
        assertNotNull(details1);
        assertTrue(details1.contains("Book ID: " + book1.getBookId()));
        assertTrue(details1.contains("ISBN: 978-0132350884"));
        assertTrue(details1.contains("Title: Clean Code"));
        assertTrue(details1.contains("Authors: Robert C. Martin"));
        assertTrue(details1.contains("Publisher: Prentice Hall"));
        assertTrue(details1.contains("Publication Year: 2008"));
        assertTrue(details1.contains("Programming"));
        assertTrue(details1.contains("Technical"));
        assertTrue(details1.contains("Total Copies: 2"));
        assertTrue(details1.contains("- Available: 1"));
        assertTrue(details1.contains("- Issued: 1"));

        String details3 = bookService.getFormattedBookDetails(book3.getBookId());
        assertNotNull(details3);
        assertTrue(details3.contains("Authors: F. Scott Fitzgerald"));
        assertTrue(details3.contains("Publisher: Charles Scribner's Sons"));
        assertTrue(details3.contains("- Reserved: 1"));
        assertTrue(details3.contains("- Damaged: 1"));
    }

    @Test
    @DisplayName("13. Existing book operations (add, update, delete) continue to work")
    void testExistingBookOperationsContinueToWork() {
        Book newBook = new Book("978-0321125217", "Refactoring", "Addison-Wesley", 1999, "Improving the Design of Existing Code");
        newBook.addAuthor(new Author(104, "Martin Fowler"));
        boolean added = bookService.addBook(newBook, 2, librarian);
        assertTrue(added);

        Book retrieved = bookService.getBookByIsbn("978-0321125217");
        assertNotNull(retrieved);
        assertEquals("Martin Fowler", retrieved.getAuthor());
        assertEquals("Addison-Wesley", retrieved.getPublisher());

        boolean updated = bookService.updateBook(retrieved.getBookId(), "Refactoring 2nd Edition", "Addison-Wesley", 2018, "Updated edition", librarian);
        assertTrue(updated);

        Book updatedBook = bookService.getBookById(retrieved.getBookId());
        assertEquals("Refactoring 2nd Edition", updatedBook.getTitle());
        assertEquals(2018, updatedBook.getPublicationYear());
        assertEquals("Addison-Wesley", updatedBook.getPublisher());
    }

    @Test
    @DisplayName("14. Publication year search works correctly")
    void testPublicationYearSearch() {
        List<Book> books1950 = bookService.searchByField("year", "1950");
        assertEquals(1, books1950.size());
        assertEquals("1984", books1950.get(0).getTitle());

        List<Book> invalidYear = bookService.searchByField("year", "not-a-year");
        assertTrue(invalidYear.isEmpty());
    }

    @Test
    @DisplayName("15. General Search matches across all fields including year and id")
    void testGeneralSearchMatchesAcrossAllFields() {
        // Matches title
        List<Book> byTitle = bookService.searchBooks("clean");
        assertEquals(1, byTitle.size());
        assertEquals("Clean Code", byTitle.get(0).getTitle());

        // Matches author
        List<Book> byAuthor = bookService.searchBooks("fitzgerald");
        assertEquals(1, byAuthor.size());
        assertEquals("The Great Gatsby", byAuthor.get(0).getTitle());

        // Matches ISBN
        List<Book> byIsbn = bookService.searchBooks("0451524935");
        assertEquals(1, byIsbn.size());
        assertEquals("1984", byIsbn.get(0).getTitle());

        // Matches publication year
        List<Book> byYear = bookService.searchBooks("1950");
        assertEquals(1, byYear.size());
        assertEquals("1984", byYear.get(0).getTitle());

        // Matches Book ID
        List<Book> byId = bookService.searchBooks(String.valueOf(book1.getBookId()));
        assertFalse(byId.isEmpty());
        assertTrue(byId.stream().anyMatch(b -> b.getBookId() == book1.getBookId()));

        // Matches Genre / Category
        List<Book> byCategory = bookService.searchBooks("Programming");
        assertEquals(1, byCategory.size());
        assertEquals("Clean Code", byCategory.get(0).getTitle());
    }

    @Test
    @DisplayName("16. Field-specific search only matches the designated field")
    void testFieldSpecificSearchIsolation() {
        // Author word in title search must NOT match
        List<Book> titleSearchForAuthor = bookService.searchByField("title", "Robert");
        assertTrue(titleSearchForAuthor.isEmpty(), "Title search should not match author");

        // Title word in author search must NOT match
        List<Book> authorSearchForTitle = bookService.searchByField("author", "Clean");
        assertTrue(authorSearchForTitle.isEmpty(), "Author search should not match title");

        // ISBN search for title must NOT match
        List<Book> isbnSearchForTitle = bookService.searchByField("isbn", "Clean");
        assertTrue(isbnSearchForTitle.isEmpty(), "ISBN search should not match title");

        // Unknown field returns empty list
        List<Book> unknownField = bookService.searchByField("unknown", "Clean");
        assertTrue(unknownField.isEmpty(), "Unknown field should return empty list");
    }
}
