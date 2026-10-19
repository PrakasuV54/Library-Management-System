package service;

import enums.*;
import model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import repository.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class ComprehensiveSyntheticDataTest {

    private DataStore dataStore;
    private UserRepository userRepository;
    private MembershipPlanRepository membershipPlanRepository;
    private MembershipRepository membershipRepository;
    private AuthorRepository authorRepository;
    private GenreRepository genreRepository;
    private CategoryRepository categoryRepository;
    private BookRepository bookRepository;
    private BookCopyRepository bookCopyRepository;
    private TransactionRepository transactionRepository;
    private FineRepository fineRepository;
    private PaymentRepository paymentRepository;
    private AuditLogRepository auditLogRepository;
    private BookService bookService;
    private SyntheticData syntheticData;

    @BeforeEach
    void setUp() {
        dataStore = new DataStore();
        userRepository = new InMemoryUserRepository(dataStore);
        membershipPlanRepository = new InMemoryMembershipPlanRepository(dataStore);
        membershipRepository = new InMemoryMembershipRepository(dataStore);
        authorRepository = new InMemoryAuthorRepository(dataStore);
        genreRepository = new InMemoryGenreRepository(dataStore);
        categoryRepository = new InMemoryCategoryRepository(dataStore);
        bookRepository = new InMemoryBookRepository(dataStore);
        bookCopyRepository = new InMemoryBookCopyRepository(dataStore);
        transactionRepository = new InMemoryTransactionRepository(dataStore);
        fineRepository = new InMemoryFineRepository(dataStore);
        paymentRepository = new InMemoryPaymentRepository(dataStore);
        auditLogRepository = new InMemoryAuditLogRepository(dataStore);

        AuditService auditService = new AuditService(auditLogRepository);
        bookService = new BookService(bookRepository, bookCopyRepository, transactionRepository,
                auditService, authorRepository, genreRepository);

        syntheticData = new SyntheticData(userRepository, membershipPlanRepository, bookService,
                authorRepository, genreRepository, membershipRepository, transactionRepository,
                fineRepository, paymentRepository, auditLogRepository, categoryRepository,
                bookCopyRepository);
    }

    @Test
    @DisplayName("Verify at least 50 records generated for every applicable entity")
    void testAtLeastFiftyRecordsPerEntity() {
        syntheticData.syntheticData();

        List<User> users = userRepository.findAll();
        List<Membership> memberships = membershipRepository.findAll();
        List<MembershipPlan> plans = membershipPlanRepository.findAll();
        List<Book> books = bookRepository.findAll();
        List<BookCopy> copies = bookCopyRepository.findAll();
        List<Author> authors = authorRepository.findAll();
        List<Genre> genres = genreRepository.findAll();
        List<Category> categories = categoryRepository.findAll();
        List<Transaction> transactions = transactionRepository.findAll();
        List<Fine> fines = fineRepository.findAll();
        List<Payment> payments = paymentRepository.findAll();
        List<Audit> auditLogs = auditLogRepository.findAll();

        // Count distinct publishers across books
        Set<String> distinctPublishers = new HashSet<>();
        for (Book b : books) {
            if (b.getPublisher() != null && !b.getPublisher().isBlank()) {
                distinctPublishers.add(b.getPublisher().trim());
            }
        }

        assertTrue(users.size() >= 50, "Users count should be at least 50, got: " + users.size());
        assertTrue(memberships.size() >= 50, "Memberships count should be at least 50, got: " + memberships.size());
        assertTrue(plans.size() >= 50, "Membership plans count should be at least 50, got: " + plans.size());
        assertTrue(books.size() >= 50, "Books count should be at least 50, got: " + books.size());
        assertTrue(copies.size() >= 50, "Book copies count should be at least 50, got: " + copies.size());
        assertTrue(authors.size() >= 50, "Authors count should be at least 50, got: " + authors.size());
        assertTrue(genres.size() >= 50, "Genres count should be at least 50, got: " + genres.size());
        assertTrue(categories.size() >= 50, "Categories count should be at least 50, got: " + categories.size());
        assertTrue(distinctPublishers.size() >= 50, "Distinct publishers should be at least 50, got: " + distinctPublishers.size());
        assertTrue(transactions.size() >= 50, "Transactions count should be at least 50, got: " + transactions.size());
        assertTrue(fines.size() >= 50, "Fines count should be at least 50, got: " + fines.size());
        assertTrue(payments.size() >= 50, "Payments count should be at least 50, got: " + payments.size());
        assertTrue(auditLogs.size() >= 50, "Audit logs count should be at least 50, got: " + auditLogs.size());
    }

    @Test
    @DisplayName("Verify unique IDs and uniqueness constraints across all generated entities")
    void testUniqueIdentities() {
        syntheticData.syntheticData();

        // 1. Users
        Set<Integer> userIds = new HashSet<>();
        Set<String> userPhones = new HashSet<>();
        Set<String> userEmails = new HashSet<>();
        for (User u : userRepository.findAll()) {
            assertTrue(userIds.add(u.getUserId()), "Duplicate userId: " + u.getUserId());
            assertTrue(userPhones.add(u.getPhone()), "Duplicate phone: " + u.getPhone());
            assertTrue(userEmails.add(u.getEmail().toLowerCase()), "Duplicate email: " + u.getEmail());
        }

        // 2. Books
        Set<Integer> bookIds = new HashSet<>();
        Set<String> isbns = new HashSet<>();
        for (Book b : bookRepository.findAll()) {
            assertTrue(bookIds.add(b.getBookId()), "Duplicate bookId: " + b.getBookId());
            assertTrue(isbns.add(b.getIsbn()), "Duplicate ISBN: " + b.getIsbn());
        }

        // 3. Book copies
        Set<Integer> copyIds = new HashSet<>();
        for (BookCopy c : bookCopyRepository.findAll()) {
            assertTrue(copyIds.add(c.getBookCopyId()), "Duplicate bookCopyId: " + c.getBookCopyId());
        }

        // 4. Authors
        Set<Integer> authorIds = new HashSet<>();
        Set<String> authorNames = new HashSet<>();
        for (Author a : authorRepository.findAll()) {
            assertTrue(authorIds.add(a.getAuthorId()), "Duplicate authorId: " + a.getAuthorId());
            assertTrue(authorNames.add(a.getName().toLowerCase()), "Duplicate author name: " + a.getName());
        }

        // 5. Genres
        Set<Integer> genreIds = new HashSet<>();
        Set<String> genreNames = new HashSet<>();
        for (Genre g : genreRepository.findAll()) {
            assertTrue(genreIds.add(g.getGenreId()), "Duplicate genreId: " + g.getGenreId());
            assertTrue(genreNames.add(g.getName().toLowerCase()), "Duplicate genre name: " + g.getName());
        }

        // 6. Categories
        Set<Integer> categoryIds = new HashSet<>();
        Set<String> categoryNames = new HashSet<>();
        for (Category cat : categoryRepository.findAll()) {
            assertTrue(categoryIds.add(cat.getCategoryId()), "Duplicate categoryId: " + cat.getCategoryId());
            assertTrue(categoryNames.add(cat.getName().toLowerCase()), "Duplicate category name: " + cat.getName());
        }

        // 7. Membership plans
        Set<Integer> planIds = new HashSet<>();
        Set<String> planNames = new HashSet<>();
        for (MembershipPlan p : membershipPlanRepository.findAll()) {
            assertTrue(planIds.add(p.getPlanId()), "Duplicate planId: " + p.getPlanId());
            assertTrue(planNames.add(p.getPlanName().toLowerCase()), "Duplicate plan name: " + p.getPlanName());
        }

        // 8. Transactions
        Set<Integer> txIds = new HashSet<>();
        for (Transaction t : transactionRepository.findAll()) {
            assertTrue(txIds.add(t.getTransactionId()), "Duplicate transactionId: " + t.getTransactionId());
        }

        // 9. Fines
        Set<Integer> fineIds = new HashSet<>();
        for (Fine f : fineRepository.findAll()) {
            assertTrue(fineIds.add(f.getFineId()), "Duplicate fineId: " + f.getFineId());
        }

        // 10. Payments
        Set<Integer> paymentIds = new HashSet<>();
        for (Payment p : paymentRepository.findAll()) {
            assertTrue(paymentIds.add(p.getPaymentId()), "Duplicate paymentId: " + p.getPaymentId());
        }

        // 11. Audit logs
        Set<Integer> auditIds = new HashSet<>();
        for (Audit a : auditLogRepository.findAll()) {
            assertTrue(auditIds.add(a.getAuditId()), "Duplicate auditId: " + a.getAuditId());
        }
    }

    @Test
    @DisplayName("Verify relational integrity (foreign keys point to valid entities)")
    void testRelationalIntegrity() {
        syntheticData.syntheticData();

        // 1. Memberships
        for (Membership m : membershipRepository.findAll()) {
            assertNotNull(userRepository.findByPhone(m.getUserPhone()),
                    "Membership userPhone must reference existing user: " + m.getUserPhone());
            assertNotNull(membershipPlanRepository.findById(m.getPlanId()),
                    "Membership planId must reference existing plan: " + m.getPlanId());
        }

        // 2. Books and copies
        for (BookCopy c : bookCopyRepository.findAll()) {
            assertNotNull(bookRepository.findById(c.getBookId()),
                    "BookCopy bookId must reference existing book: " + c.getBookId());
        }

        // 3. Transactions
        for (Transaction t : transactionRepository.findAll()) {
            assertNotNull(userRepository.findByPhone(t.getUserPhone()),
                    "Transaction userPhone must reference existing user: " + t.getUserPhone());
            assertNotNull(bookRepository.findById(t.getBookId()),
                    "Transaction bookId must reference existing book: " + t.getBookId());
            if (t.getBookCopyId() > 0) {
                assertNotNull(bookCopyRepository.findById(t.getBookCopyId()),
                        "Transaction bookCopyId must reference existing copy: " + t.getBookCopyId());
            }
        }

        // 4. Fines
        for (Fine f : fineRepository.findAll()) {
            assertNotNull(userRepository.findByPhone(f.getUserPhone()),
                    "Fine userPhone must reference existing user: " + f.getUserPhone());
            assertNotNull(transactionRepository.findById(f.getTransactionId()),
                    "Fine transactionId must reference existing transaction: " + f.getTransactionId());
        }

        // 5. Payments
        for (Payment p : paymentRepository.findAll()) {
            assertNotNull(userRepository.findByPhone(p.getUserPhone()),
                    "Payment userPhone must reference existing user: " + p.getUserPhone());
            if (p.getFineId() > 0) {
                assertNotNull(fineRepository.findById(p.getFineId()),
                        "Payment fineId must reference existing fine: " + p.getFineId());
            }
            if (p.getMembershipId() > 0) {
                assertNotNull(membershipRepository.findById(p.getMembershipId()),
                        "Payment membershipId must reference existing membership: " + p.getMembershipId());
            }
        }

        // 6. Audit logs
        for (Audit a : auditLogRepository.findAll()) {
            if (a.getUserPhone() != null && !a.getUserPhone().isBlank() && !a.getUserPhone().equals("SYSTEM")) {
                assertNotNull(userRepository.findByPhone(a.getUserPhone()),
                        "Audit log userPhone must reference existing user: " + a.getUserPhone());
            }
        }
    }

    @Test
    @DisplayName("Verify canonicalization of authors and genres on books")
    void testCanonicalAuthorAndGenreAssociations() {
        syntheticData.syntheticData();

        for (Book b : bookRepository.findAll()) {
            for (Author a : b.getAuthors()) {
                Author canonical = authorRepository.findById(a.getAuthorId());
                assertNotNull(canonical, "Book author must exist in repository: " + a.getName());
                assertSame(canonical, a, "Book author instance must be canonical instance from repository: " + a.getName());
            }
            for (Genre g : b.getGenres()) {
                Genre canonical = genreRepository.findById(g.getGenreId());
                assertNotNull(canonical, "Book genre must exist in repository: " + g.getName());
                assertSame(canonical, g, "Book genre instance must be canonical instance from repository: " + g.getName());
            }
        }
    }

    @Test
    @DisplayName("Verify consistency between transactions and book-copy statuses")
    void testTransactionAndCopyStatusConsistency() {
        syntheticData.syntheticData();

        Map<Integer, Integer> activeIssuedCopies = new HashMap<>();
        Map<Integer, Integer> activeReservedCopies = new HashMap<>();

        for (Transaction t : transactionRepository.findAll()) {
            if (t.getStatus() == TransactionStatus.ISSUED) {
                assertTrue(t.getBookCopyId() > 0, "Issued transaction must have a copy ID");
                BookCopy copy = bookCopyRepository.findById(t.getBookCopyId());
                assertNotNull(copy);
                assertEquals(BookCopyStatus.ISSUED, copy.getStatus(),
                        "Copy linked to ISSUED transaction must have ISSUED status");
                assertFalse(activeIssuedCopies.containsKey(t.getBookCopyId()),
                        "Copy cannot be issued to multiple active transactions: Copy ID " + t.getBookCopyId());
                activeIssuedCopies.put(t.getBookCopyId(), t.getTransactionId());
            } else if (t.getStatus() == TransactionStatus.RESERVED || t.getStatus() == TransactionStatus.READY_FOR_PICKUP) {
                assertTrue(t.getBookCopyId() > 0, "Reserved transaction must have a copy ID");
                BookCopy copy = bookCopyRepository.findById(t.getBookCopyId());
                assertNotNull(copy);
                assertEquals(BookCopyStatus.RESERVED, copy.getStatus(),
                        "Copy linked to RESERVED/READY transaction must have RESERVED status");
                assertFalse(activeReservedCopies.containsKey(t.getBookCopyId()),
                        "Copy cannot be reserved for multiple active transactions: Copy ID " + t.getBookCopyId());
                activeReservedCopies.put(t.getBookCopyId(), t.getTransactionId());
            } else if (t.getStatus() == TransactionStatus.LOST) {
                if (t.getBookCopyId() > 0) {
                    BookCopy copy = bookCopyRepository.findById(t.getBookCopyId());
                    assertNotNull(copy);
                    assertEquals(BookCopyStatus.LOST, copy.getStatus());
                }
            } else if (t.getStatus() == TransactionStatus.DAMAGED) {
                if (t.getBookCopyId() > 0) {
                    BookCopy copy = bookCopyRepository.findById(t.getBookCopyId());
                    assertNotNull(copy);
                    assertEquals(BookCopyStatus.DAMAGED, copy.getStatus());
                }
            }
        }
    }

    @Test
    @DisplayName("Verify mathematical and status consistency of fines and payments")
    void testFineAndPaymentConsistency() {
        syntheticData.syntheticData();

        for (Fine f : fineRepository.findAll()) {
            double fineAmount = f.getAmount();
            double paidAmount = f.getAmountPaid();
            double remainingAmount = f.getRemainingAmount();

            if (f.getStatus() == FineStatus.WAIVED) {
                assertEquals(0.0, remainingAmount, 0.01, "WAIVED fine must have zero remaining balance");
                assertEquals(FineStatus.WAIVED, f.getStatus(), "WAIVED fine must have WAIVED status");
            } else {
                assertEquals(fineAmount, paidAmount + remainingAmount, 0.01,
                        "Fine amount must equal paid amount + remaining amount for fine ID " + f.getFineId());
            }

            if (f.getStatus() == FineStatus.PAID) {
                assertTrue(remainingAmount <= 0.01, "PAID fine must have zero remaining balance");
                assertTrue(paidAmount >= fineAmount - 0.01, "PAID fine must have paid amount equal to fine amount");
            } else if (f.getStatus() == FineStatus.PENDING) {
                assertTrue(paidAmount <= 0.01, "PENDING fine must have zero paid amount");
                assertTrue(remainingAmount >= fineAmount - 0.01, "PENDING fine must have full remaining amount");
            } else if (f.getStatus() == FineStatus.PARTIALLY_PAID) {
                assertTrue(paidAmount > 0.01, "PARTIALLY_PAID fine must have non-zero paid amount");
                assertTrue(remainingAmount > 0.01, "PARTIALLY_PAID fine must have non-zero remaining amount");
            }
        }

        for (Payment p : paymentRepository.findAll()) {
            assertEquals(PaymentStatus.SUCCESS, p.getStatus(), "Payment status should be SUCCESS");
            assertTrue(p.getAmount() > 0, "Payment amount must be positive");
            assertNotNull(p.getPaymentMethod(), "Payment method must not be null");
        }
    }

    @Test
    @DisplayName("Verify preservation of pre-existing seed data")
    void testPreservationOfExistingSeedData() {
        syntheticData.syntheticData();

        // 1. Core Users
        User admin = userRepository.findByPhone("9999999999");
        assertNotNull(admin, "Seed admin must be preserved");
        assertEquals(UserRole.ADMIN, admin.getRole());

        User librarian = userRepository.findByPhone("8888888888");
        assertNotNull(librarian, "Seed librarian must be preserved");
        assertEquals(UserRole.LIBRARIAN, librarian.getRole());

        // 2. Core Membership Plans (by name)
        assertNotNull(membershipPlanRepository.findByName("Standard Plan"), "Seed Standard Plan must exist");
        assertNotNull(membershipPlanRepository.findByName("Premium Plan"), "Seed Premium Plan must exist");
        assertNotNull(membershipPlanRepository.findByName("Family Plan"), "Seed Family Plan must exist");
        assertNotNull(membershipPlanRepository.findByName("Family Premium Plan"), "Seed Family Premium Plan must exist");

        // 3. Core Authors
        assertNotNull(authorRepository.findByName("Robert C. Martin"), "Seed author Robert C. Martin must exist");
        assertNotNull(authorRepository.findByName("Martin Fowler"), "Seed author Martin Fowler must exist");

        // 4. Core Books (by ISBN)
        assertNotNull(bookRepository.findByIsbn("978-0132350884"), "Seed Clean Code must exist");
        assertNotNull(bookRepository.findByIsbn("978-0134494166"), "Seed Clean Architecture must exist");
        assertNotNull(bookRepository.findByIsbn("978-0201633610"), "Seed Design Patterns must exist");
    }

    @Test
    @DisplayName("Verify idempotency: running synthetic initialization twice produces no duplicate records")
    void testIdempotency() {
        syntheticData.syntheticData();

        int usersCount1 = userRepository.findAll().size();
        int membershipsCount1 = membershipRepository.findAll().size();
        int plansCount1 = membershipPlanRepository.findAll().size();
        int booksCount1 = bookRepository.findAll().size();
        int copiesCount1 = bookCopyRepository.findAll().size();
        int authorsCount1 = authorRepository.findAll().size();
        int genresCount1 = genreRepository.findAll().size();
        int categoriesCount1 = categoryRepository.findAll().size();
        int transactionsCount1 = transactionRepository.findAll().size();
        int finesCount1 = fineRepository.findAll().size();
        int paymentsCount1 = paymentRepository.findAll().size();
        int auditLogsCount1 = auditLogRepository.findAll().size();

        // Invoke second time
        syntheticData.syntheticData();

        assertEquals(usersCount1, userRepository.findAll().size(), "Users count must not increase on second run");
        assertEquals(membershipsCount1, membershipRepository.findAll().size(), "Memberships count must not increase on second run");
        assertEquals(plansCount1, membershipPlanRepository.findAll().size(), "Plans count must not increase on second run");
        assertEquals(booksCount1, bookRepository.findAll().size(), "Books count must not increase on second run");
        assertEquals(copiesCount1, bookCopyRepository.findAll().size(), "Copies count must not increase on second run");
        assertEquals(authorsCount1, authorRepository.findAll().size(), "Authors count must not increase on second run");
        assertEquals(genresCount1, genreRepository.findAll().size(), "Genres count must not increase on second run");
        assertEquals(categoriesCount1, categoryRepository.findAll().size(), "Categories count must not increase on second run");
        assertEquals(transactionsCount1, transactionRepository.findAll().size(), "Transactions count must not increase on second run");
        assertEquals(finesCount1, fineRepository.findAll().size(), "Fines count must not increase on second run");
        assertEquals(paymentsCount1, paymentRepository.findAll().size(), "Payments count must not increase on second run");
        assertEquals(auditLogsCount1, auditLogRepository.findAll().size(), "Audit logs count must not increase on second run");
    }

    @Test
    @DisplayName("Verify summary output format is printed concisely")
    void testPrintSummaryFormat() {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(outContent));
            syntheticData.syntheticData();
            String output = outContent.toString();
            assertTrue(output.contains("=== Synthetic Data Summary ==="), "Output must contain header");
            assertTrue(output.contains("Users:"), "Output must contain Users count");
            assertTrue(output.contains("Memberships:"), "Output must contain Memberships count");
            assertTrue(output.contains("Membership Plans:"), "Output must contain Membership Plans count");
            assertTrue(output.contains("Books:"), "Output must contain Books count");
            assertTrue(output.contains("Book Copies:"), "Output must contain Book Copies count");
            assertTrue(output.contains("Authors:"), "Output must contain Authors count");
            assertTrue(output.contains("Genres:"), "Output must contain Genres count");
            assertTrue(output.contains("Categories:"), "Output must contain Categories count");
            assertTrue(output.contains("Publishers:"), "Output must contain Publishers count");
            assertTrue(output.contains("Transactions:"), "Output must contain Transactions count");
            assertTrue(output.contains("Fines:"), "Output must contain Fines count");
            assertTrue(output.contains("Payments:"), "Output must contain Payments count");
            assertTrue(output.contains("Audit Logs:"), "Output must contain Audit Logs count");
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    @DisplayName("Verify existing application services continue to function normally with synthetic data")
    void testExistingServiceOperationsOnSyntheticData() {
        syntheticData.syntheticData();

        // 1. Search books by keyword
        List<Book> searchResults = bookService.searchBooks("Clean");
        assertFalse(searchResults.isEmpty(), "Searching 'Clean' should find books");

        // 2. Can find available copy for synthetic book
        List<Book> allBooks = bookRepository.findAll();
        assertTrue(allBooks.size() >= 50, "At least 50 books should exist");
        Book bookWithAvailableCopy = null;
        BookCopy availableCopy = null;
        for (Book b : allBooks) {
            BookCopy c = bookCopyRepository.findAvailableCopy(b.getBookId());
            if (c != null) {
                bookWithAvailableCopy = b;
                availableCopy = c;
                break;
            }
        }
        assertNotNull(bookWithAvailableCopy, "There should be at least one book with an available copy");
        assertNotNull(availableCopy, "Available copy should exist");
        assertTrue(availableCopy.isAvailable(), "Available copy must be in AVAILABLE status");

        // 3. User borrowing capability with synthetic member
        User member = userRepository.findByPhone("9870000001");
        assertNotNull(member, "Synthetic member should exist");
        assertEquals(UserRole.MEMBER, member.getRole());
        assertEquals(UserStatus.ACTIVE, member.getStatus());
    }
}
