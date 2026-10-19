package service;

import enums.*;
import model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import paymentStrategy.Razorpay;
import repository.*;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FamilyPremiumAndBorrowingPriorityTest {

    private DataStore dataStore;
    private UserRepository userRepository;
    private MembershipRepository membershipRepository;
    private MembershipPlanRepository membershipPlanRepository;
    private PaymentRepository paymentRepository;
    private FineRepository fineRepository;
    private TransactionRepository transactionRepository;
    private BookRepository bookRepository;
    private BookCopyRepository bookCopyRepository;
    private AuditLogRepository auditLogRepository;

    private AuditService auditService;
    private NotificationService notificationService;
    private PolicyService policyService;
    private FineService fineService;
    private MembershipPlanService membershipPlanService;
    private MembershipService membershipService;
    private BookService bookService;
    private BookTransactionService bookTransactionService;

    private User staffLibrarian;
    private User standardMember;
    private User premiumMember;
    private User familyMember;
    private User familyPremiumMember;

    private MembershipPlan standardPlan;
    private MembershipPlan premiumPlan;
    private MembershipPlan familyPlan;
    private MembershipPlan familyPremiumPlan;

    private Book testBook;

    @BeforeEach
    void setUp() {
        dataStore = new DataStore();
        userRepository = new InMemoryUserRepository(dataStore);
        membershipRepository = new InMemoryMembershipRepository(dataStore);
        membershipPlanRepository = new InMemoryMembershipPlanRepository(dataStore);
        paymentRepository = new InMemoryPaymentRepository(dataStore);
        fineRepository = new InMemoryFineRepository(dataStore);
        transactionRepository = new InMemoryTransactionRepository(dataStore);
        bookRepository = new InMemoryBookRepository(dataStore);
        bookCopyRepository = new InMemoryBookCopyRepository(dataStore);
        auditLogRepository = new InMemoryAuditLogRepository(dataStore);

        auditService = new AuditService(auditLogRepository);
        notificationService = new NotificationService();
        policyService = new PolicyService(userRepository, membershipRepository, transactionRepository, fineRepository, auditService, membershipPlanRepository);
        fineService = new FineService(fineRepository, transactionRepository, policyService, auditService);
        membershipPlanService = new MembershipPlanService(membershipPlanRepository, userRepository);
        membershipService = new MembershipService(
                userRepository, membershipRepository, membershipPlanService,
                paymentRepository, new Razorpay(), auditService, notificationService
        );
        bookService = new BookService(bookRepository, bookCopyRepository, transactionRepository, auditService);
        bookTransactionService = new BookTransactionService(
                bookRepository, bookCopyRepository, transactionRepository,
                policyService, auditService, notificationService, fineService
        );

        // 1. Initialize all 4 plans
        standardPlan = new MembershipPlan(DataStore.getMembershipPlanId(), "Standard Plan", 299.0);
        premiumPlan = new MembershipPlan(DataStore.getMembershipPlanId(), "Premium Plan", 599.0);
        familyPlan = new MembershipPlan(DataStore.getMembershipPlanId(), MembershipPlanService.DEFAULT_FAMILY_PLAN_NAME, MembershipPlanService.DEFAULT_FAMILY_PLAN_PRICE);
        familyPremiumPlan = new MembershipPlan(DataStore.getMembershipPlanId(), MembershipPlanService.DEFAULT_FAMILY_PREMIUM_PLAN_NAME, MembershipPlanService.DEFAULT_FAMILY_PREMIUM_PLAN_PRICE);

        membershipPlanRepository.save(standardPlan);
        membershipPlanRepository.save(premiumPlan);
        membershipPlanRepository.save(familyPlan);
        membershipPlanRepository.save(familyPremiumPlan);

        // 2. Create users
        staffLibrarian = new User("Librarian", "lib@test.com", "9000000001", "pass", UserRole.LIBRARIAN, UserStatus.ACTIVE);
        standardMember = new User("Alice Standard", "alice@test.com", "9000000002", "pass", UserRole.MEMBER, UserStatus.ACTIVE);
        premiumMember = new User("Bob Premium", "bob@test.com", "9000000003", "pass", UserRole.MEMBER, UserStatus.ACTIVE);
        familyMember = new User("Charlie Family", "charlie@test.com", "9000000004", "pass", UserRole.MEMBER, UserStatus.ACTIVE);
        familyPremiumMember = new User("David FamilyPremium", "david@test.com", "9000000005", "pass", UserRole.MEMBER, UserStatus.ACTIVE);

        userRepository.save(staffLibrarian);
        userRepository.save(standardMember);
        userRepository.save(premiumMember);
        userRepository.save(familyMember);
        userRepository.save(familyPremiumMember);

        // 3. Activate memberships for 1 month
        membershipService.getMembershipByPhone(standardMember.getPhone(), standardPlan.getPlanId(), 1, PaymentMethod.UPI, standardMember);
        membershipService.getMembershipByPhone(premiumMember.getPhone(), premiumPlan.getPlanId(), 1, PaymentMethod.UPI, premiumMember);
        membershipService.getMembershipByPhone(familyMember.getPhone(), familyPlan.getPlanId(), 1, PaymentMethod.UPI, familyMember);
        membershipService.getMembershipByPhone(familyPremiumMember.getPhone(), familyPremiumPlan.getPlanId(), 1, PaymentMethod.UPI, familyPremiumMember);

        // 4. Create a test book with multiple copies
        testBook = new Book("978-0134494166", "Clean Architecture", "Robert C. Martin", 2017, "Architecture");
        bookService.addBook(testBook, 20, staffLibrarian);
    }

    @Test
    @DisplayName("1. All four plans initialized with correct prices and borrowing limits")
    void testAllFourPlansConfiguration() {
        assertEquals(299.0, standardPlan.getAmount(), 0.001);
        assertEquals(3, standardPlan.getMaxIssuedBooks());
        assertEquals(3, standardPlan.getMaxBooks());

        assertEquals(599.0, premiumPlan.getAmount(), 0.001);
        assertEquals(5, premiumPlan.getMaxIssuedBooks());
        assertEquals(5, premiumPlan.getMaxBooks());

        assertEquals(899.0, familyPlan.getAmount(), 0.001);
        assertEquals(12, familyPlan.getMaxIssuedBooks());
        assertEquals(12, familyPlan.getMaxBooks());

        assertEquals(1299.0, familyPremiumPlan.getAmount(), 0.001);
        assertEquals(15, familyPremiumPlan.getMaxIssuedBooks());
        assertEquals(15, familyPremiumPlan.getMaxBooks());

        // Name lookup with and without 'Plan' suffix
        assertNotNull(membershipPlanService.findByName("Family Premium"));
        assertNotNull(membershipPlanService.findByName("Family Premium Plan"));
        assertNotNull(membershipPlanService.findByName("Standard"));
        assertNotNull(membershipPlanService.findByName("Standard Plan"));
        assertNotNull(membershipPlanService.findByName("Premium"));
        assertNotNull(membershipPlanService.findByName("Premium Plan"));
        assertNotNull(membershipPlanService.findByName("Family"));
        assertNotNull(membershipPlanService.findByName("Family Plan"));
    }

    @Test
    @DisplayName("2. Borrowing limits of 3, 5, 12, and 15 resolved for active members")
    void testBorrowingLimitsPerTier() {
        assertEquals(3, policyService.getMaxBooksForUser(standardMember.getPhone()));
        assertEquals(5, policyService.getMaxBooksForUser(premiumMember.getPhone()));
        assertEquals(12, policyService.getMaxBooksForUser(familyMember.getPhone()));
        assertEquals(15, policyService.getMaxBooksForUser(familyPremiumMember.getPhone()));

        // User without active membership falls back to default limit
        assertEquals(3, policyService.getMaxBooksForUser("9999999999"));
    }

    @Test
    @DisplayName("3. Reject book issue when member reaches their applicable borrowing limit")
    void testRejectIssueAtBorrowingLimit() {
        // Standard Member (Limit: 3)
        // Issue 3 books
        for (int i = 0; i < 3; i++) {
            Transaction req = bookTransactionService.requestBook(standardMember.getPhone(), testBook.getBookId());
            assertNotNull(req);
            BookCopy copy = bookCopyRepository.findAvailableCopy(testBook.getBookId());
            assertTrue(bookTransactionService.assignCopy(req.getTransactionId(), copy.getBookCopyId(), staffLibrarian));
            assertTrue(bookTransactionService.markReadyForPickup(req.getTransactionId(), staffLibrarian));
            assertTrue(bookTransactionService.issueBook(req.getTransactionId(), staffLibrarian));
        }
        assertEquals(3, policyService.countIssuedBooks(standardMember.getPhone()));

        // 4th book issue attempt must be rejected
        Transaction req4 = bookTransactionService.requestBook(standardMember.getPhone(), testBook.getBookId());
        assertNotNull(req4);
        BookCopy copy4 = bookCopyRepository.findAvailableCopy(testBook.getBookId());
        assertTrue(bookTransactionService.assignCopy(req4.getTransactionId(), copy4.getBookCopyId(), staffLibrarian));
        assertTrue(bookTransactionService.markReadyForPickup(req4.getTransactionId(), staffLibrarian));
        assertFalse(policyService.canIssueBook(standardMember.getPhone()), "canIssueBook must return false when limit is reached");
        assertFalse(bookTransactionService.issueBook(req4.getTransactionId(), staffLibrarian), "issueBook must reject when limit is reached");

        // Non-issued transactions do not count towards the limit
        // req4 is READY_FOR_PICKUP, not ISSUED
        assertEquals(3, policyService.countIssuedBooks(standardMember.getPhone()));
    }

    @Test
    @DisplayName("4. Premium request selected ahead of older Standard request")
    void testPremiumSelectedAheadOfOlderStandard() throws Exception {
        // 1. Standard request created earlier
        Transaction stdReq = bookTransactionService.requestBook(standardMember.getPhone(), testBook.getBookId());
        assertNotNull(stdReq);
        setRequestDate(stdReq, LocalDateTime.now().minusMinutes(30));

        // 2. Premium request created later
        Transaction premReq = bookTransactionService.requestBook(premiumMember.getPhone(), testBook.getBookId());
        assertNotNull(premReq);
        setRequestDate(premReq, LocalDateTime.now().minusMinutes(10));

        // Selection should prioritize Premium even though Standard is older
        Transaction selected = bookTransactionService.getNextEligibleRequest(testBook.getBookId());
        assertNotNull(selected);
        assertEquals(premReq.getTransactionId(), selected.getTransactionId(), "Premium request must be selected before older Standard request");
    }

    @Test
    @DisplayName("5. Family Premium request selected ahead of older Family request")
    void testFamilyPremiumSelectedAheadOfOlderFamily() throws Exception {
        // 1. Family request created earlier
        Transaction famReq = bookTransactionService.requestBook(familyMember.getPhone(), testBook.getBookId());
        assertNotNull(famReq);
        setRequestDate(famReq, LocalDateTime.now().minusMinutes(45));

        // 2. Family Premium request created later
        Transaction famPremReq = bookTransactionService.requestBook(familyPremiumMember.getPhone(), testBook.getBookId());
        assertNotNull(famPremReq);
        setRequestDate(famPremReq, LocalDateTime.now().minusMinutes(15));

        // Selection should prioritize Family Premium over older Family
        Transaction selected = bookTransactionService.getNextEligibleRequest(testBook.getBookId());
        assertNotNull(selected);
        assertEquals(famPremReq.getTransactionId(), selected.getTransactionId(), "Family Premium request must be selected before older Family request");
    }

    @Test
    @DisplayName("6. FIFO ordering within the high-priority group")
    void testFifoWithinHighPriorityGroup() throws Exception {
        // Two high-priority requests: Premium and Family Premium
        Transaction premReq = bookTransactionService.requestBook(premiumMember.getPhone(), testBook.getBookId());
        setRequestDate(premReq, LocalDateTime.now().minusMinutes(20));

        Transaction famPremReq = bookTransactionService.requestBook(familyPremiumMember.getPhone(), testBook.getBookId());
        setRequestDate(famPremReq, LocalDateTime.now().minusMinutes(10));

        // premReq is older, so it should be selected first
        Transaction selected1 = bookTransactionService.getNextEligibleRequest(testBook.getBookId());
        assertEquals(premReq.getTransactionId(), selected1.getTransactionId(), "Older Premium request must be selected first");

        // When premReq is assigned, famPremReq becomes next
        BookCopy copy1 = bookCopyRepository.findAvailableCopy(testBook.getBookId());
        bookTransactionService.assignCopy(premReq.getTransactionId(), copy1.getBookCopyId(), staffLibrarian);

        Transaction selected2 = bookTransactionService.getNextEligibleRequest(testBook.getBookId());
        assertEquals(famPremReq.getTransactionId(), selected2.getTransactionId(), "Second high-priority request must be selected next");
    }

    @Test
    @DisplayName("7. FIFO ordering within the normal-priority group")
    void testFifoWithinNormalPriorityGroup() throws Exception {
        // Two normal-priority requests: Standard and Family
        Transaction stdReq = bookTransactionService.requestBook(standardMember.getPhone(), testBook.getBookId());
        setRequestDate(stdReq, LocalDateTime.now().minusMinutes(40));

        Transaction famReq = bookTransactionService.requestBook(familyMember.getPhone(), testBook.getBookId());
        setRequestDate(famReq, LocalDateTime.now().minusMinutes(20));

        Transaction selected = bookTransactionService.getNextEligibleRequest(testBook.getBookId());
        assertEquals(stdReq.getTransactionId(), selected.getTransactionId(), "Older normal-priority request must be selected first");
    }

    @Test
    @DisplayName("8. Skipping ineligible requests in priority queue")
    void testSkippingIneligibleRequests() throws Exception {
        // High priority request by Premium member
        Transaction premReq = bookTransactionService.requestBook(premiumMember.getPhone(), testBook.getBookId());
        setRequestDate(premReq, LocalDateTime.now().minusMinutes(30));

        // Normal priority request by Standard member
        Transaction stdReq = bookTransactionService.requestBook(standardMember.getPhone(), testBook.getBookId());
        setRequestDate(stdReq, LocalDateTime.now().minusMinutes(10));

        // Premium member's membership gets suspended (making the request ineligible)
        membershipService.suspendMembership(staffLibrarian, premiumMember.getPhone());
        assertFalse(policyService.canRequestBook(premiumMember.getPhone()));

        // Next eligible request must skip the suspended Premium member and select Standard
        Transaction selected = bookTransactionService.getNextEligibleRequest(testBook.getBookId());
        assertNotNull(selected);
        assertEquals(stdReq.getTransactionId(), selected.getTransactionId(), "Ineligible Premium request must be skipped");
    }

    @Test
    @DisplayName("9. Multi-month payment totals for Family Premium purchase and renewal")
    void testFamilyPremiumMultiMonthPaymentTotals() {
        User newFamilyUser = new User("New FP", "newfp@test.com", "9555555555", "pass", UserRole.MEMBER, UserStatus.ACTIVE);
        userRepository.save(newFamilyUser);

        // 3-month purchase: 1299 * 3 = 3897
        boolean purchased = membershipService.getMembershipByPhone(newFamilyUser.getPhone(), familyPremiumPlan.getPlanId(), 3, PaymentMethod.UPI, newFamilyUser);
        assertTrue(purchased);

        Membership mem = membershipService.getActiveMembership(newFamilyUser.getPhone(), newFamilyUser);
        assertNotNull(mem);
        assertEquals(familyPremiumPlan.getPlanId(), mem.getPlanId());
        assertEquals(LocalDate.now().plusMonths(3), mem.getEndDate());

        Payment purchasePayment = paymentRepository.findById(mem.getPaymentId());
        assertNotNull(purchasePayment);
        assertEquals(3897.0, purchasePayment.getAmount(), 0.001);

        // 6-month renewal: 1299 * 6 = 7794
        boolean renewed = membershipService.renewMembership(newFamilyUser.getPhone(), familyPremiumPlan.getPlanId(), 6, PaymentMethod.CARD, newFamilyUser);
        assertTrue(renewed);

        Membership renewedMem = membershipService.getActiveMembership(newFamilyUser.getPhone(), newFamilyUser);
        assertEquals(LocalDate.now().plusMonths(9), renewedMem.getEndDate());
    }

    @Test
    @DisplayName("10. Full manual librarian allocation lifecycle preserved")
    void testManualLibrarianAllocationWorkflowPreserved() {
        // Member requests
        Transaction req = bookTransactionService.requestBook(familyPremiumMember.getPhone(), testBook.getBookId());
        assertEquals(TransactionStatus.REQUESTED, req.getStatus());

        // Copy is not automatically assigned or reserved
        BookCopy copy = bookCopyRepository.findAvailableCopy(testBook.getBookId());
        assertEquals(BookCopyStatus.AVAILABLE, copy.getStatus());

        // Librarian manually assigns copy
        assertTrue(bookTransactionService.assignCopy(req.getTransactionId(), copy.getBookCopyId(), staffLibrarian));
        assertEquals(TransactionStatus.RESERVED, req.getStatus());
        assertEquals(BookCopyStatus.RESERVED, copy.getStatus());

        // Librarian marks ready for pickup
        assertTrue(bookTransactionService.markReadyForPickup(req.getTransactionId(), staffLibrarian));
        assertEquals(TransactionStatus.READY_FOR_PICKUP, req.getStatus());

        // Librarian issues book
        assertTrue(bookTransactionService.issueBook(req.getTransactionId(), staffLibrarian));
        assertEquals(TransactionStatus.ISSUED, req.getStatus());
        assertEquals(BookCopyStatus.ISSUED, copy.getStatus());
        assertEquals(1, policyService.countIssuedBooks(familyPremiumMember.getPhone()));
    }

    private void setRequestDate(Transaction t, LocalDateTime dt) throws Exception {
        Field f = Transaction.class.getDeclaredField("requestDate");
        f.setAccessible(true);
        f.set(t, dt);
    }
}
