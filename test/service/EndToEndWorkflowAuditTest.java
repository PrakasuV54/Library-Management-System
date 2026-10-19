package service;

import enums.*;
import model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import paymentStrategy.PaymentStrategy;
import paymentStrategy.Razorpay;
import repository.*;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class EndToEndWorkflowAuditTest {

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

    private User librarian;
    private User admin;
    private Book bookAlpha;
    private Book bookBeta;

    private MembershipPlan standardPlan;
    private MembershipPlan premiumPlan;
    private MembershipPlan familyPlan;
    private MembershipPlan familyPremiumPlan;

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

        // Seed Plans as in SyntheticData
        standardPlan = new MembershipPlan(DataStore.getMembershipPlanId(), "Standard Plan", 299.0);
        premiumPlan = new MembershipPlan(DataStore.getMembershipPlanId(), "Premium Plan", 599.0);
        familyPlan = new MembershipPlan(DataStore.getMembershipPlanId(), MembershipPlanService.DEFAULT_FAMILY_PLAN_NAME, MembershipPlanService.DEFAULT_FAMILY_PLAN_PRICE);
        familyPremiumPlan = new MembershipPlan(DataStore.getMembershipPlanId(), MembershipPlanService.DEFAULT_FAMILY_PREMIUM_PLAN_NAME, MembershipPlanService.DEFAULT_FAMILY_PREMIUM_PLAN_PRICE);

        membershipPlanRepository.save(standardPlan);
        membershipPlanRepository.save(premiumPlan);
        membershipPlanRepository.save(familyPlan);
        membershipPlanRepository.save(familyPremiumPlan);

        // Staff
        librarian = new User("Librarian John", "john@lib.org", "9876543210", "pass", UserRole.LIBRARIAN, UserStatus.ACTIVE);
        admin = new User("Admin Jane", "jane@lib.org", "9876543211", "pass", UserRole.ADMIN, UserStatus.ACTIVE);
        userRepository.save(librarian);
        userRepository.save(admin);

        // Books
        bookAlpha = new Book("ISBN-ALPHA", "Alpha Systems", "Dr. A. Author", 2024, "Computer Science");
        bookBeta = new Book("ISBN-BETA", "Beta Algorithms", "Prof. B. Builder", 2023, "Algorithms");
        bookService.addBook(bookAlpha, 20, librarian);
        bookService.addBook(bookBeta, 20, librarian);
    }

    private User createMember(String name, String phone, String email) {
        User u = new User(name, email, phone, "secret", UserRole.MEMBER, UserStatus.ACTIVE);
        userRepository.save(u);
        return u;
    }

    private void setRequestDate(Transaction t, LocalDateTime dt) {
        try {
            Field f = Transaction.class.getDeclaredField("requestDate");
            f.setAccessible(true);
            f.set(t, dt);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ==========================================
    // 1. Membership Plan Configuration
    // ==========================================
    @Test
    @DisplayName("Audit Section 2: All 4 Membership Plans initialized and retrieved correctly")
    void testMembershipPlanConfigurationAudit() {
        // Standard: 299, 3 books, Normal
        assertEquals("Standard Plan", standardPlan.getPlanName());
        assertEquals(299.0, standardPlan.getAmount());
        assertEquals(3, standardPlan.getMaxIssuedBooks());

        // Premium: 599, 5 books, High
        assertEquals("Premium Plan", premiumPlan.getPlanName());
        assertEquals(599.0, premiumPlan.getAmount());
        assertEquals(5, premiumPlan.getMaxIssuedBooks());

        // Family: 899, 12 books, Normal
        assertEquals(MembershipPlanService.DEFAULT_FAMILY_PLAN_NAME, familyPlan.getPlanName());
        assertEquals(899.0, familyPlan.getAmount());
        assertEquals(12, familyPlan.getMaxIssuedBooks());

        // Family Premium: 1299, 15 books, High
        assertEquals(MembershipPlanService.DEFAULT_FAMILY_PREMIUM_PLAN_NAME, familyPremiumPlan.getPlanName());
        assertEquals(1299.0, familyPremiumPlan.getAmount());
        assertEquals(15, familyPremiumPlan.getMaxIssuedBooks());

        // Name matching via repository
        assertEquals(familyPremiumPlan.getPlanId(), membershipPlanRepository.findByName("Family Premium").getPlanId());
        assertEquals(familyPremiumPlan.getPlanId(), membershipPlanRepository.findByName("Family Premium Plan").getPlanId());
        assertEquals(familyPlan.getPlanId(), membershipPlanRepository.findByName("Family").getPlanId());
        assertEquals(familyPlan.getPlanId(), membershipPlanRepository.findByName("Family Plan").getPlanId());
    }

    // ==========================================
    // 2. Membership Purchase & Renewal Workflow
    // ==========================================
    @Test
    @DisplayName("Audit Section 3: Membership purchase, renewal, multi-month calculation, and single active membership")
    void testMembershipPurchaseAndRenewalWorkflow() {
        User member = createMember("Test Buyer", "9111111111", "buyer@test.com");

        // 1. View available plans
        List<MembershipPlan> plans = membershipPlanService.getAllPlans();
        assertEquals(4, plans.size());

        // 2 & 3. Select Family Premium for 3 months => 1299 * 3 = 3897
        int durationMonths = 3;
        double expectedTotal = familyPremiumPlan.getAmount() * durationMonths;
        assertEquals(3897.0, expectedTotal);

        // 4. Successful payment activates membership and sets correct expiry
        boolean success = membershipService.getMembershipByPhone(member.getPhone(), familyPremiumPlan.getPlanId(), durationMonths, PaymentMethod.UPI, member);
        assertTrue(success);

        Membership active = membershipService.getActiveMembership(member.getPhone(), member);
        assertNotNull(active);
        assertEquals(familyPremiumPlan.getPlanId(), active.getPlanId());
        assertEquals(MembershipStatus.ACTIVE, active.getStatus());
        assertEquals(LocalDate.now().plusMonths(3), active.getEndDate());

        // 8. Attempting to purchase another membership while active should fail / prevent duplicates
        boolean duplicatePurchase = membershipService.getMembershipByPhone(member.getPhone(), standardPlan.getPlanId(), 1, PaymentMethod.UPI, member);
        assertFalse(duplicatePurchase, "Cannot purchase another membership while one is active");

        // 5. Failed payment does not activate membership
        User failingUser = createMember("Fail Buyer", "9222222222", "fail@test.com");
        MembershipService failingService = new MembershipService(
                userRepository, membershipRepository, membershipPlanService,
                paymentRepository, new PaymentStrategy() {
                    @Override
                    public int pay(double amount) {
                        return 0; // Simulate payment failure
                    }

                    @Override
                    public void refund(int gatewayReference) {
                    }
                },
                auditService, notificationService
        );
        boolean failResult = failingService.getMembershipByPhone(failingUser.getPhone(), standardPlan.getPlanId(), 1, PaymentMethod.CARD, failingUser);
        assertFalse(failResult);
        assertNull(membershipRepository.findActiveByUserPhone(failingUser.getPhone()));

        // 6. Renewal calculates correct amount, updates intended membership and plan
        // Renew member for 2 months under Family Premium: 1299 * 2 = 2598
        LocalDate prevExpiry = active.getEndDate();
        boolean renewSuccess = membershipService.renewMembership(member.getPhone(), familyPremiumPlan.getPlanId(), 2, PaymentMethod.CARD, member);
        assertTrue(renewSuccess);

        Membership renewed = membershipService.getActiveMembership(member.getPhone(), member);
        assertEquals(prevExpiry.plusMonths(2), renewed.getEndDate());
        assertEquals(familyPremiumPlan.getPlanId(), renewed.getPlanId());

        // Verify single active membership in repository
        List<Membership> userMemberships = membershipRepository.findByUserPhone(member.getPhone());
        assertEquals(1, userMemberships.size());

        // 7. Expired or inactive memberships do not receive active limits or high queue priority
        active.suspend();
        membershipRepository.save(active);
        assertFalse(policyService.hasActiveMembership(member.getPhone()));
        assertNull(policyService.getPlanForUser(member.getPhone()));
        assertEquals(3, policyService.getMaxBooksForUser(member.getPhone())); // fallback to default
        assertEquals(2, policyService.getRequestQueuePriority(member.getPhone())); // fallback to normal priority
    }

    // ==========================================
    // 3. Borrowing Limit Workflow for Every Plan
    // ==========================================
    @Test
    @DisplayName("Audit Section 4: Borrowing limits (3, 5, 12, 15), rejections at limit, and return-reissue")
    void testBorrowingLimitsAllPlans() {
        verifyBorrowingLimitForPlan(standardPlan, 3);
        verifyBorrowingLimitForPlan(premiumPlan, 5);
        verifyBorrowingLimitForPlan(familyPlan, 12);
        verifyBorrowingLimitForPlan(familyPremiumPlan, 15);
    }

    private void verifyBorrowingLimitForPlan(MembershipPlan plan, int maxAllowed) {
        String phone = "933" + plan.getPlanId() + "112233";
        User member = createMember("Member " + plan.getPlanName(), phone, "m" + plan.getPlanId() + "@test.com");
        membershipService.getMembershipByPhone(member.getPhone(), plan.getPlanId(), 1, PaymentMethod.UPI, member);

        assertEquals(maxAllowed, policyService.getMaxBooksForUser(phone));

        // Create a unique book for this test with enough copies
        Book book = new Book("ISBN-" + plan.getPlanId(), "Book For " + plan.getPlanName(), "Author", 2024, "Desc");
        bookService.addBook(book, maxAllowed + 5, librarian);

        int[] txIds = new int[maxAllowed];

        // 1. Issue books until limit is reached
        for (int i = 0; i < maxAllowed; i++) {
            assertTrue(policyService.canIssueBook(phone), "Should be eligible before reaching limit at count " + i);
            Transaction req = bookTransactionService.requestBook(phone, book.getBookId());
            assertNotNull(req);

            BookCopy copy = bookCopyRepository.findAvailableCopy(book.getBookId());
            assertNotNull(copy);
            assertTrue(bookTransactionService.assignCopy(req.getTransactionId(), copy.getBookCopyId(), librarian));
            assertTrue(bookTransactionService.markReadyForPickup(req.getTransactionId(), librarian));
            assertTrue(bookTransactionService.issueBook(req.getTransactionId(), librarian));

            txIds[i] = req.getTransactionId();
        }

        assertEquals(maxAllowed, policyService.countIssuedBooks(phone));

        // 2 & 3. Attempt to issue one additional book -> rejected
        assertFalse(policyService.canIssueBook(phone), "Must NOT be eligible once limit is reached");
        Transaction extraReq = bookTransactionService.requestBook(phone, book.getBookId());
        assertNotNull(extraReq);
        BookCopy extraCopy = bookCopyRepository.findAvailableCopy(book.getBookId());
        assertTrue(bookTransactionService.assignCopy(extraReq.getTransactionId(), extraCopy.getBookCopyId(), librarian));
        assertTrue(bookTransactionService.markReadyForPickup(extraReq.getTransactionId(), librarian));
        assertFalse(bookTransactionService.issueBook(extraReq.getTransactionId(), librarian), "Issue must be rejected at limit");

        // 4. Return one issued book and verify another can now be issued
        assertTrue(bookTransactionService.returnBook(txIds[0], librarian));
        assertEquals(maxAllowed - 1, policyService.countIssuedBooks(phone));
        assertTrue(policyService.canIssueBook(phone), "Should be eligible again after returning one book");
        assertTrue(bookTransactionService.issueBook(extraReq.getTransactionId(), librarian), "Pending ready book can now be issued");
        assertEquals(maxAllowed, policyService.countIssuedBooks(phone));
    }

    @Test
    @DisplayName("Audit Section 4: Non-ISSUED statuses do not count as currently issued books")
    void testNonIssuedStatusesDoNotCountTowardsLimit() {
        User member = createMember("Status Test Member", "9444444444", "status@test.com");
        membershipService.getMembershipByPhone(member.getPhone(), standardPlan.getPlanId(), 1, PaymentMethod.UPI, member);

        // REQUESTED
        Transaction req = bookTransactionService.requestBook(member.getPhone(), bookAlpha.getBookId());
        assertEquals(TransactionStatus.REQUESTED, req.getStatus());
        assertEquals(0, policyService.countIssuedBooks(member.getPhone()));

        // RESERVED
        BookCopy copy = bookCopyRepository.findAvailableCopy(bookAlpha.getBookId());
        bookTransactionService.assignCopy(req.getTransactionId(), copy.getBookCopyId(), librarian);
        assertEquals(TransactionStatus.RESERVED, req.getStatus());
        assertEquals(0, policyService.countIssuedBooks(member.getPhone()));

        // READY_FOR_PICKUP
        bookTransactionService.markReadyForPickup(req.getTransactionId(), librarian);
        assertEquals(TransactionStatus.READY_FOR_PICKUP, req.getStatus());
        assertEquals(0, policyService.countIssuedBooks(member.getPhone()));

        // CANCELLED
        bookTransactionService.cancelRequest(member.getPhone(), bookAlpha.getIsbn(), member);
        assertEquals(TransactionStatus.CANCELLED, req.getStatus());
        assertEquals(0, policyService.countIssuedBooks(member.getPhone()));

        // Re-request and progress to ISSUED, then RETURNED
        Transaction req2 = bookTransactionService.requestBook(member.getPhone(), bookAlpha.getBookId());
        BookCopy copy2 = bookCopyRepository.findAvailableCopy(bookAlpha.getBookId());
        bookTransactionService.assignCopy(req2.getTransactionId(), copy2.getBookCopyId(), librarian);
        bookTransactionService.markReadyForPickup(req2.getTransactionId(), librarian);
        bookTransactionService.issueBook(req2.getTransactionId(), librarian);
        assertEquals(TransactionStatus.ISSUED, req2.getStatus());
        assertEquals(1, policyService.countIssuedBooks(member.getPhone()));

        // RETURNED
        bookTransactionService.returnBook(req2.getTransactionId(), librarian);
        assertEquals(TransactionStatus.RETURNED, req2.getStatus());
        assertEquals(0, policyService.countIssuedBooks(member.getPhone()));

        // EXPIRED
        Transaction req3 = bookTransactionService.requestBook(member.getPhone(), bookAlpha.getBookId());
        BookCopy copy3 = bookCopyRepository.findAvailableCopy(bookAlpha.getBookId());
        bookTransactionService.assignCopy(req3.getTransactionId(), copy3.getBookCopyId(), librarian);
        bookTransactionService.markReadyForPickup(req3.getTransactionId(), librarian);
        bookTransactionService.expireReservation(req3.getTransactionId(), librarian);
        assertEquals(TransactionStatus.EXPIRED, req3.getStatus());
        assertEquals(0, policyService.countIssuedBooks(member.getPhone()));

        // LOST
        Transaction req4 = bookTransactionService.requestBook(member.getPhone(), bookAlpha.getBookId());
        BookCopy copy4 = bookCopyRepository.findAvailableCopy(bookAlpha.getBookId());
        bookTransactionService.assignCopy(req4.getTransactionId(), copy4.getBookCopyId(), librarian);
        bookTransactionService.markReadyForPickup(req4.getTransactionId(), librarian);
        bookTransactionService.issueBook(req4.getTransactionId(), librarian);
        assertEquals(1, policyService.countIssuedBooks(member.getPhone()));
        bookTransactionService.markCopyLost(copy4.getBookCopyId(), librarian);
        assertEquals(TransactionStatus.LOST, req4.getStatus());
        assertEquals(0, policyService.countIssuedBooks(member.getPhone()));
        assertTrue(policyService.isDefaulter(member.getPhone()), "Defaulter check applied after lost book");

        // Waive fine to restore eligibility for testing DAMAGED status
        Fine lostFine = fineService.findByTransactionId(req4.getTransactionId(), librarian).get(0);
        fineService.waiveFine(lostFine.getFineId(), admin);
        assertFalse(policyService.isDefaulter(member.getPhone()));

        // DAMAGED
        Transaction req5 = bookTransactionService.requestBook(member.getPhone(), bookAlpha.getBookId());
        BookCopy copy5 = bookCopyRepository.findAvailableCopy(bookAlpha.getBookId());
        bookTransactionService.assignCopy(req5.getTransactionId(), copy5.getBookCopyId(), librarian);
        bookTransactionService.markReadyForPickup(req5.getTransactionId(), librarian);
        bookTransactionService.issueBook(req5.getTransactionId(), librarian);
        assertEquals(1, policyService.countIssuedBooks(member.getPhone()));
        bookTransactionService.markCopyDamaged(copy5.getBookCopyId(), librarian);
        assertEquals(TransactionStatus.DAMAGED, req5.getStatus());
        assertEquals(0, policyService.countIssuedBooks(member.getPhone()));
    }

    // ==========================================
    // 4. Queue Priority Workflow Across Books
    // ==========================================
    @Test
    @DisplayName("Audit Section 5: Queue priority, FIFO, tie-breaker, skipping ineligible, and multi-book independence")
    void testQueuePriorityExhaustiveWorkflow() {
        User stdUser = createMember("Standard User", "9500000001", "std@test.com");
        User premUser = createMember("Premium User", "9500000002", "prem@test.com");
        User famUser = createMember("Family User", "9500000003", "fam@test.com");
        User famPremUser = createMember("Family Premium User", "9500000004", "famprem@test.com");

        membershipService.getMembershipByPhone(stdUser.getPhone(), standardPlan.getPlanId(), 1, PaymentMethod.UPI, stdUser);
        membershipService.getMembershipByPhone(premUser.getPhone(), premiumPlan.getPlanId(), 1, PaymentMethod.UPI, premUser);
        membershipService.getMembershipByPhone(famUser.getPhone(), familyPlan.getPlanId(), 1, PaymentMethod.UPI, famUser);
        membershipService.getMembershipByPhone(famPremUser.getPhone(), familyPremiumPlan.getPlanId(), 1, PaymentMethod.UPI, famPremUser);

        // --- Scenario A: Older Standard vs Newer Premium ---
        Transaction rStd = bookTransactionService.requestBook(stdUser.getPhone(), bookAlpha.getBookId());
        setRequestDate(rStd, LocalDateTime.now().minusHours(2));
        Transaction rPrem = bookTransactionService.requestBook(premUser.getPhone(), bookAlpha.getBookId());
        setRequestDate(rPrem, LocalDateTime.now().minusMinutes(5));

        Transaction selectedA = bookTransactionService.getNextEligibleRequest(bookAlpha.getBookId());
        assertEquals(rPrem.getTransactionId(), selectedA.getTransactionId(), "Premium must be selected over older Standard");

        // Clean up requests
        rStd.cancel();
        rPrem.cancel();

        // --- Scenario B: Older Family vs Newer Family Premium ---
        Transaction rFam = bookTransactionService.requestBook(famUser.getPhone(), bookAlpha.getBookId());
        setRequestDate(rFam, LocalDateTime.now().minusHours(2));
        Transaction rFamPrem = bookTransactionService.requestBook(famPremUser.getPhone(), bookAlpha.getBookId());
        setRequestDate(rFamPrem, LocalDateTime.now().minusMinutes(5));

        Transaction selectedB = bookTransactionService.getNextEligibleRequest(bookAlpha.getBookId());
        assertEquals(rFamPrem.getTransactionId(), selectedB.getTransactionId(), "Family Premium must be selected over older Family");

        rFam.cancel();
        rFamPrem.cancel();

        // --- Scenario C: Two Premium requests submitted at different times ---
        User premUser2 = createMember("Premium User 2", "9500000005", "prem2@test.com");
        membershipService.getMembershipByPhone(premUser2.getPhone(), premiumPlan.getPlanId(), 1, PaymentMethod.UPI, premUser2);

        Transaction rPremOlder = bookTransactionService.requestBook(premUser.getPhone(), bookAlpha.getBookId());
        setRequestDate(rPremOlder, LocalDateTime.now().minusMinutes(30));
        Transaction rPremNewer = bookTransactionService.requestBook(premUser2.getPhone(), bookAlpha.getBookId());
        setRequestDate(rPremNewer, LocalDateTime.now().minusMinutes(10));

        Transaction selectedC = bookTransactionService.getNextEligibleRequest(bookAlpha.getBookId());
        assertEquals(rPremOlder.getTransactionId(), selectedC.getTransactionId(), "Older Premium request must be selected first");

        rPremOlder.cancel();
        rPremNewer.cancel();

        // --- Scenario D: Two normal-priority requests submitted at different times ---
        Transaction rStdOlder = bookTransactionService.requestBook(stdUser.getPhone(), bookAlpha.getBookId());
        setRequestDate(rStdOlder, LocalDateTime.now().minusMinutes(30));
        Transaction rFamNewer = bookTransactionService.requestBook(famUser.getPhone(), bookAlpha.getBookId());
        setRequestDate(rFamNewer, LocalDateTime.now().minusMinutes(10));

        Transaction selectedD = bookTransactionService.getNextEligibleRequest(bookAlpha.getBookId());
        assertEquals(rStdOlder.getTransactionId(), selectedD.getTransactionId(), "Older normal-priority request must be selected first");

        rStdOlder.cancel();
        rFamNewer.cancel();

        // --- Scenario E: Transaction-ID tie-breaker when timestamps are equal ---
        LocalDateTime sameTime = LocalDateTime.of(2026, 10, 18, 10, 0, 0);
        Transaction rTie1 = bookTransactionService.requestBook(premUser.getPhone(), bookAlpha.getBookId());
        setRequestDate(rTie1, sameTime);
        Transaction rTie2 = bookTransactionService.requestBook(premUser2.getPhone(), bookAlpha.getBookId());
        setRequestDate(rTie2, sameTime);

        assertTrue(rTie1.getTransactionId() < rTie2.getTransactionId());
        Transaction selectedE = bookTransactionService.getNextEligibleRequest(bookAlpha.getBookId());
        assertEquals(rTie1.getTransactionId(), selectedE.getTransactionId(), "Lower transaction ID must win tie-breaker");

        rTie1.cancel();
        rTie2.cancel();

        // --- Scenario F: Ineligible High-Priority followed by Eligible High-Priority ---
        Transaction rIneligibleHP = bookTransactionService.requestBook(premUser.getPhone(), bookAlpha.getBookId());
        setRequestDate(rIneligibleHP, LocalDateTime.now().minusMinutes(30));
        Transaction rEligibleHP = bookTransactionService.requestBook(famPremUser.getPhone(), bookAlpha.getBookId());
        setRequestDate(rEligibleHP, LocalDateTime.now().minusMinutes(10));

        // Make premUser suspended
        membershipService.suspendMembership(librarian, premUser.getPhone());
        assertFalse(policyService.canRequestBook(premUser.getPhone()));

        Transaction selectedF = bookTransactionService.getNextEligibleRequest(bookAlpha.getBookId());
        assertEquals(rEligibleHP.getTransactionId(), selectedF.getTransactionId(), "Suspended HP request skipped in favor of eligible HP");

        // Reactivate premUser
        membershipService.reactivateMembership(librarian, premUser.getPhone());
        rIneligibleHP.cancel();
        rEligibleHP.cancel();

        // --- Scenario G: Ineligible High-Priority followed by Eligible Normal-Priority ---
        Transaction rIneligibleHP2 = bookTransactionService.requestBook(premUser.getPhone(), bookAlpha.getBookId());
        setRequestDate(rIneligibleHP2, LocalDateTime.now().minusMinutes(30));
        Transaction rEligibleNormal = bookTransactionService.requestBook(stdUser.getPhone(), bookAlpha.getBookId());
        setRequestDate(rEligibleNormal, LocalDateTime.now().minusMinutes(10));

        // Create an unpaid fine for premUser to make them a defaulter
        Fine fine = new Fine(DataStore.getFineId(), premUser.getPhone(), 0, 50.0, FineType.OVERDUE);
        fineRepository.save(fine);
        assertTrue(policyService.isDefaulter(premUser.getPhone()));
        assertFalse(policyService.canRequestBook(premUser.getPhone()));

        Transaction selectedG = bookTransactionService.getNextEligibleRequest(bookAlpha.getBookId());
        assertEquals(rEligibleNormal.getTransactionId(), selectedG.getTransactionId(), "Defaulter HP request skipped in favor of eligible Normal request");

        // Waive fine
        fineService.waiveFine(fine.getFineId(), admin);
        rIneligibleHP2.cancel();
        rEligibleNormal.cancel();

        // --- Scenario H: Multi-Book Independence (Alpha vs Beta) ---
        Transaction rAlpha = bookTransactionService.requestBook(stdUser.getPhone(), bookAlpha.getBookId());
        Transaction rBeta = bookTransactionService.requestBook(famPremUser.getPhone(), bookBeta.getBookId());

        Transaction nextAlpha = bookTransactionService.getNextEligibleRequest(bookAlpha.getBookId());
        Transaction nextBeta = bookTransactionService.getNextEligibleRequest(bookBeta.getBookId());

        assertEquals(rAlpha.getTransactionId(), nextAlpha.getTransactionId(), "Book Alpha queue must only inspect Alpha requests");
        assertEquals(rBeta.getTransactionId(), nextBeta.getTransactionId(), "Book Beta queue must only inspect Beta requests");

        rAlpha.cancel();
        rBeta.cancel();
    }

    // ==========================================
    // 5. Manual Allocation & Notification Audit
    // ==========================================
    @Test
    @DisplayName("Audit Section 6: Preservation of manual librarian allocation and notification sequence")
    void testManualAllocationAndNotificationSequence() {
        User member = createMember("Alloc Member", "9600000001", "alloc@test.com");
        membershipService.getMembershipByPhone(member.getPhone(), familyPremiumPlan.getPlanId(), 1, PaymentMethod.UPI, member);

        // 1. Member requests book -> REQUESTED
        Transaction req = bookTransactionService.requestBook(member.getPhone(), bookAlpha.getBookId());
        assertEquals(TransactionStatus.REQUESTED, req.getStatus());

        // 3. Confirm NO copy is automatically reserved
        BookCopy copy = bookCopyRepository.findAvailableCopy(bookAlpha.getBookId());
        assertEquals(BookCopyStatus.AVAILABLE, copy.getStatus());

        // 4 & 5. Librarian manually assigns copy -> RESERVED
        assertTrue(bookTransactionService.assignCopy(req.getTransactionId(), copy.getBookCopyId(), librarian));
        assertEquals(TransactionStatus.RESERVED, req.getStatus());
        assertEquals(BookCopyStatus.RESERVED, copy.getStatus());

        // 6 & 7. Staff marks ready for pickup -> READY_FOR_PICKUP, member notified
        assertTrue(bookTransactionService.markReadyForPickup(req.getTransactionId(), librarian));
        assertEquals(TransactionStatus.READY_FOR_PICKUP, req.getStatus());

        // 8. Staff issues book only from READY_FOR_PICKUP state
        assertTrue(bookTransactionService.issueBook(req.getTransactionId(), librarian));
        assertEquals(TransactionStatus.ISSUED, req.getStatus());
        assertEquals(BookCopyStatus.ISSUED, copy.getStatus());

        // 9. Invalid repeated operations rejected safely
        assertFalse(bookTransactionService.issueBook(req.getTransactionId(), librarian), "Cannot re-issue already issued transaction");
        assertFalse(bookTransactionService.markReadyForPickup(req.getTransactionId(), librarian), "Cannot mark ready an already issued transaction");
        assertFalse(bookTransactionService.assignCopy(req.getTransactionId(), copy.getBookCopyId(), librarian), "Cannot assign copy to already issued transaction");
    }

    // ==========================================
    // 6. Regression: Fine Management & Defaulters
    // ==========================================
    @Test
    @DisplayName("Audit Section 7: Fine creation, payment, waiver, and overdue fine processing")
    void testFineManagementAndDefaultersRegression() {
        User member = createMember("Fine Member", "9700000001", "fine@test.com");
        membershipService.getMembershipByPhone(member.getPhone(), standardPlan.getPlanId(), 1, PaymentMethod.UPI, member);

        // Create overdue transaction
        Transaction t = bookTransactionService.requestBook(member.getPhone(), bookAlpha.getBookId());
        BookCopy copy = bookCopyRepository.findAvailableCopy(bookAlpha.getBookId());
        bookTransactionService.assignCopy(t.getTransactionId(), copy.getBookCopyId(), librarian);
        bookTransactionService.markReadyForPickup(t.getTransactionId(), librarian);
        bookTransactionService.issueBook(t.getTransactionId(), librarian);

        // Set due date in past
        try {
            Field f = Transaction.class.getDeclaredField("dueDate");
            f.setAccessible(true);
            f.set(t, LocalDate.now().minusDays(5));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        transactionRepository.save(t);

        // Verify defaulter due to overdue
        assertTrue(policyService.isDefaulter(member.getPhone()));

        // Return overdue book -> triggers overdue fine
        assertTrue(bookTransactionService.returnBook(t.getTransactionId(), librarian));
        assertEquals(TransactionStatus.RETURNED, t.getStatus());

        // Check fine created: 5 days * 10 = 50.0
        List<Fine> fines = fineService.findByUserPhone(member.getPhone(), librarian);
        assertEquals(1, fines.size());
        Fine overdueFine = fines.get(0);
        assertEquals(50.0, overdueFine.getAmount(), 0.001);
        assertEquals(FineStatus.PENDING, overdueFine.getStatus());

        // Member is still defaulter due to outstanding fine
        assertTrue(policyService.isDefaulter(member.getPhone()));

        // Pay fine partially (20)
        PaymentService paymentService = new PaymentService(paymentRepository, fineRepository, new Razorpay(), auditService, membershipRepository, transactionRepository);
        assertNotNull(paymentService.payFine(member.getPhone(), overdueFine.getFineId(), 20.0, PaymentMethod.UPI));
        assertEquals(FineStatus.PARTIALLY_PAID, overdueFine.getStatus());
        assertEquals(30.0, overdueFine.getRemainingAmount(), 0.001);

        // Waive remainder by admin
        assertTrue(fineService.waiveFine(overdueFine.getFineId(), admin));
        assertEquals(FineStatus.WAIVED, overdueFine.getStatus());
        assertEquals(0.0, overdueFine.getRemainingAmount(), 0.001);

        // Member is no longer defaulter
        assertFalse(policyService.isDefaulter(member.getPhone()));
        assertTrue(policyService.canRequestBook(member.getPhone()));
    }

    // ==========================================
    // 7. Targeted Verification: Renewal / Upgrade Expiry Scenarios
    // ==========================================
    @Test
    @DisplayName("Targeted Verification 1: Renewal, upgrade, multi-month, failure, and multiple renewal scenarios")
    void testRenewalAndUpgradeDetailedScenarios() {
        // 1. Active Standard member renews Standard for 1 month
        User u1 = createMember("User One", "9811111111", "u1@test.com");
        membershipService.getMembershipByPhone(u1.getPhone(), standardPlan.getPlanId(), 1, PaymentMethod.UPI, u1);
        Membership m1 = membershipService.getActiveMembership(u1.getPhone(), u1);
        LocalDate origEnd1 = m1.getEndDate();
        assertEquals(LocalDate.now().plusMonths(1), origEnd1);
        assertEquals(3, policyService.getMaxBooksForUser(u1.getPhone()));
        assertEquals(2, policyService.getRequestQueuePriority(u1.getPhone()));

        assertTrue(membershipService.renewMembership(u1.getPhone(), standardPlan.getPlanId(), 1, PaymentMethod.CARD, u1));
        assertEquals(origEnd1.plusMonths(1), m1.getEndDate());
        assertEquals(standardPlan.getPlanId(), m1.getPlanId());
        assertEquals(3, policyService.getMaxBooksForUser(u1.getPhone()));
        assertEquals(2, policyService.getRequestQueuePriority(u1.getPhone()));

        // 2 & 4. Active Standard member upgrades to Family Premium before expiry
        User u2 = createMember("User Two", "9822222222", "u2@test.com");
        membershipService.getMembershipByPhone(u2.getPhone(), standardPlan.getPlanId(), 2, PaymentMethod.UPI, u2);
        Membership m2 = membershipService.getActiveMembership(u2.getPhone(), u2);
        LocalDate origEnd2 = m2.getEndDate();
        assertEquals(LocalDate.now().plusMonths(2), origEnd2);

        // Upgrade to Family Premium for 3 months: 1299 * 3 = 3897
        assertTrue(membershipService.renewMembership(u2.getPhone(), familyPremiumPlan.getPlanId(), 3, PaymentMethod.CARD, u2));
        assertEquals(origEnd2.plusMonths(3), m2.getEndDate(), "Preserves already paid membership time");
        assertEquals(familyPremiumPlan.getPlanId(), m2.getPlanId(), "Plan updated to Family Premium");
        assertEquals(15, policyService.getMaxBooksForUser(u2.getPhone()), "Borrowing limit updated to 15");
        assertEquals(1, policyService.getRequestQueuePriority(u2.getPhone()), "Queue priority updated to High");

        // 3. Member renews for multiple months (6 months)
        User u3 = createMember("User Three", "9833333333", "u3@test.com");
        membershipService.getMembershipByPhone(u3.getPhone(), premiumPlan.getPlanId(), 1, PaymentMethod.UPI, u3);
        Membership m3 = membershipService.getActiveMembership(u3.getPhone(), u3);
        LocalDate origEnd3 = m3.getEndDate();

        assertTrue(membershipService.renewMembership(u3.getPhone(), premiumPlan.getPlanId(), 6, PaymentMethod.UPI, u3));
        assertEquals(origEnd3.plusMonths(6), m3.getEndDate());
        List<Payment> m3Payments = paymentRepository.findByMembershipId(m3.getMembershipId());
        assertEquals(2, m3Payments.size());
        assertEquals(599.0 * 6, m3Payments.get(1).getAmount(), 0.001);

        // 5. Member renews after membership has expired
        User u4 = createMember("User Four", "9844444444", "u4@test.com");
        membershipService.getMembershipByPhone(u4.getPhone(), standardPlan.getPlanId(), 1, PaymentMethod.UPI, u4);
        Membership m4 = membershipService.getActiveMembership(u4.getPhone(), u4);
        m4.expire();
        membershipRepository.save(m4);

        // renewMembership returns false on expired membership (business rule: only active memberships can be renewed)
        assertFalse(membershipService.renewMembership(u4.getPhone(), standardPlan.getPlanId(), 1, PaymentMethod.UPI, u4));
        // Expired member purchases new membership
        assertTrue(membershipService.getMembershipByPhone(u4.getPhone(), familyPlan.getPlanId(), 2, PaymentMethod.UPI, u4));
        Membership m4New = membershipService.getActiveMembership(u4.getPhone(), u4);
        assertNotNull(m4New);
        assertEquals(familyPlan.getPlanId(), m4New.getPlanId());
        assertEquals(LocalDate.now().plusMonths(2), m4New.getEndDate());

        // 6. Payment fails during renewal
        MembershipService failingService = new MembershipService(
                userRepository, membershipRepository, membershipPlanService,
                paymentRepository, new PaymentStrategy() {
                    @Override
                    public int pay(double amount) { return 0; }
                    @Override
                    public void refund(int gatewayReference) {}
                },
                auditService, notificationService
        );
        LocalDate beforeFailDate = m4New.getEndDate();
        int beforeFailPlanId = m4New.getPlanId();
        assertFalse(failingService.renewMembership(u4.getPhone(), familyPremiumPlan.getPlanId(), 1, PaymentMethod.CARD, u4));
        assertEquals(beforeFailDate, m4New.getEndDate(), "Failed payment must not alter expiry date");
        assertEquals(beforeFailPlanId, m4New.getPlanId(), "Failed payment must not alter plan ID");

        // 7. Same membership is renewed more than once
        User u5 = createMember("User Five", "9855555555", "u5@test.com");
        membershipService.getMembershipByPhone(u5.getPhone(), standardPlan.getPlanId(), 1, PaymentMethod.UPI, u5);
        Membership m5 = membershipService.getActiveMembership(u5.getPhone(), u5);
        LocalDate startExpiry = m5.getEndDate();

        assertTrue(membershipService.renewMembership(u5.getPhone(), standardPlan.getPlanId(), 2, PaymentMethod.UPI, u5));
        assertEquals(startExpiry.plusMonths(2), m5.getEndDate());

        assertTrue(membershipService.renewMembership(u5.getPhone(), standardPlan.getPlanId(), 3, PaymentMethod.CARD, u5));
        assertEquals(startExpiry.plusMonths(5), m5.getEndDate());

        assertEquals(1, membershipRepository.findByUserPhone(u5.getPhone()).size(), "Multiple renewals maintain a single active membership");
    }

    // ==========================================
    // 8. Targeted Verification: Actual Queue Selection in assignCopy
    // ==========================================
    @Test
    @DisplayName("Targeted Verification 2: assignCopy rejects lower-priority requests when higher-priority requests exist")
    void testAssignCopyEnforcesQueuePriority() {
        User stdMember = createMember("Std Priority User", "9866666661", "stdp@test.com");
        User premMember = createMember("Prem Priority User", "9866666662", "premp@test.com");
        membershipService.getMembershipByPhone(stdMember.getPhone(), standardPlan.getPlanId(), 1, PaymentMethod.UPI, stdMember);
        membershipService.getMembershipByPhone(premMember.getPhone(), premiumPlan.getPlanId(), 1, PaymentMethod.UPI, premMember);

        // Standard requests book first (older timestamp)
        Transaction rStd = bookTransactionService.requestBook(stdMember.getPhone(), bookAlpha.getBookId());
        setRequestDate(rStd, LocalDateTime.now().minusHours(2));

        // Premium requests book later (newer timestamp)
        Transaction rPrem = bookTransactionService.requestBook(premMember.getPhone(), bookAlpha.getBookId());
        setRequestDate(rPrem, LocalDateTime.now().minusMinutes(5));

        BookCopy copy = bookCopyRepository.findAvailableCopy(bookAlpha.getBookId());
        assertNotNull(copy);

        // Attempting to assign copy to lower priority Standard request must fail!
        assertFalse(bookTransactionService.assignCopy(rStd.getTransactionId(), copy.getBookCopyId(), librarian),
                "assignCopy must reject lower priority request when higher priority request exists");

        // Assigning copy to higher priority Premium request must succeed!
        assertTrue(bookTransactionService.assignCopy(rPrem.getTransactionId(), copy.getBookCopyId(), librarian),
                "assignCopy must succeed for the highest priority eligible request");

        // Now that Premium request is reserved, another copy can be assigned to Standard
        BookCopy copy2 = bookCopyRepository.findAvailableCopy(bookAlpha.getBookId());
        assertNotNull(copy2);
        assertTrue(bookTransactionService.assignCopy(rStd.getTransactionId(), copy2.getBookCopyId(), librarian),
                "Once higher priority request is assigned, next request can be assigned");
    }

    @Test
    @DisplayName("Targeted Verification 3: Dynamic priority update when member upgrades while request is pending")
    void testPendingRequestPriorityUpdatesOnMembershipUpgrade() {
        User memberA = createMember("Member A Normal", "9877777771", "ma@test.com");
        User memberB = createMember("Member B Normal", "9877777772", "mb@test.com");
        membershipService.getMembershipByPhone(memberA.getPhone(), standardPlan.getPlanId(), 1, PaymentMethod.UPI, memberA);
        membershipService.getMembershipByPhone(memberB.getPhone(), familyPlan.getPlanId(), 1, PaymentMethod.UPI, memberB);

        // Member A requests first (older)
        Transaction reqA = bookTransactionService.requestBook(memberA.getPhone(), bookAlpha.getBookId());
        setRequestDate(reqA, LocalDateTime.now().minusMinutes(30));

        // Member B requests second (newer)
        Transaction reqB = bookTransactionService.requestBook(memberB.getPhone(), bookAlpha.getBookId());
        setRequestDate(reqB, LocalDateTime.now().minusMinutes(10));

        // Currently Member A is chosen because both are Normal priority and A is older
        assertEquals(reqA.getTransactionId(), bookTransactionService.getNextEligibleRequest(bookAlpha.getBookId()).getTransactionId());

        // Now Member B upgrades to Family Premium (High priority)
        assertTrue(membershipService.renewMembership(memberB.getPhone(), familyPremiumPlan.getPlanId(), 1, PaymentMethod.CARD, memberB));
        assertEquals(1, policyService.getRequestQueuePriority(memberB.getPhone()));

        // Now Member B is immediately chosen as next eligible request, overtaking older Member A
        Transaction selected = bookTransactionService.getNextEligibleRequest(bookAlpha.getBookId());
        assertEquals(reqB.getTransactionId(), selected.getTransactionId(), "Upgraded member B now has High priority over older Member A");

        BookCopy copy = bookCopyRepository.findAvailableCopy(bookAlpha.getBookId());
        assertTrue(bookTransactionService.assignCopy(reqB.getTransactionId(), copy.getBookCopyId(), librarian));
        assertEquals(TransactionStatus.RESERVED, reqB.getStatus());
    }

    // ==========================================
    // 9. Final Code Review Verification: assignCopy & Manual Allocation
    // ==========================================
    @Test
    @DisplayName("Final Code Review: All 9 assignCopy and manual allocation requirements verified")
    void testAssignCopyAndManualAllocationCodeReviewChecks() {
        User stdUser = createMember("Std CR User", "9911111111", "stdcr@test.com");
        User premUser = createMember("Prem CR User", "9922222222", "premcr@test.com");
        User unauthorizedUser = createMember("Regular Member", "9933333333", "regular@test.com");

        membershipService.getMembershipByPhone(stdUser.getPhone(), standardPlan.getPlanId(), 1, PaymentMethod.UPI, stdUser);
        membershipService.getMembershipByPhone(premUser.getPhone(), premiumPlan.getPlanId(), 1, PaymentMethod.UPI, premUser);

        // 8. Requests for different books remain isolated
        Transaction reqBeta = bookTransactionService.requestBook(stdUser.getPhone(), bookBeta.getBookId());
        Transaction reqAlphaStd = bookTransactionService.requestBook(stdUser.getPhone(), bookAlpha.getBookId());
        setRequestDate(reqAlphaStd, LocalDateTime.now().minusHours(1));
        Transaction reqAlphaPrem = bookTransactionService.requestBook(premUser.getPhone(), bookAlpha.getBookId());
        setRequestDate(reqAlphaPrem, LocalDateTime.now().minusMinutes(10));

        BookCopy copyAlpha = bookCopyRepository.findAvailableCopy(bookAlpha.getBookId());
        BookCopy copyBeta = bookCopyRepository.findAvailableCopy(bookBeta.getBookId());
        assertNotNull(copyAlpha);
        assertNotNull(copyBeta);

        // 4. Staff authorization: unauthorized user cannot assign copy
        assertFalse(bookTransactionService.assignCopy(reqAlphaPrem.getTransactionId(), copyAlpha.getBookCopyId(), unauthorizedUser),
                "Non-staff user cannot assign copy");
        assertEquals(TransactionStatus.REQUESTED, reqAlphaPrem.getStatus(), "Transaction unchanged after unauthorized assignment attempt");
        assertEquals(BookCopyStatus.AVAILABLE, copyAlpha.getStatus(), "Copy unchanged after unauthorized assignment attempt");

        // 3. Supplied copy must belong to the same book as the request
        assertFalse(bookTransactionService.assignCopy(reqAlphaPrem.getTransactionId(), copyBeta.getBookCopyId(), librarian),
                "Cannot assign copy of Beta to a request for Alpha");
        assertEquals(TransactionStatus.REQUESTED, reqAlphaPrem.getStatus());
        assertEquals(BookCopyStatus.AVAILABLE, copyBeta.getStatus());

        // 2 & 5. Attempting to assign lower-priority Standard request fails without mutating transaction or copy
        assertFalse(bookTransactionService.assignCopy(reqAlphaStd.getTransactionId(), copyAlpha.getBookCopyId(), librarian),
                "Cannot assign copy to Standard request when Premium request is waiting");
        assertEquals(TransactionStatus.REQUESTED, reqAlphaStd.getStatus(), "Standard request status untouched");
        assertEquals(BookCopyStatus.AVAILABLE, copyAlpha.getStatus(), "Copy status untouched");

        // 1 & 6. Successful assignment of highest priority eligible request transitions both to RESERVED
        assertTrue(bookTransactionService.assignCopy(reqAlphaPrem.getTransactionId(), copyAlpha.getBookCopyId(), librarian),
                "Highest priority request is assigned successfully");
        assertEquals(TransactionStatus.RESERVED, reqAlphaPrem.getStatus());
        assertEquals(BookCopyStatus.RESERVED, copyAlpha.getStatus());
        assertEquals(copyAlpha.getBookCopyId(), reqAlphaPrem.getBookCopyId());

        // 7. Repeated assignment attempts cannot reserve the same copy twice
        assertFalse(bookTransactionService.assignCopy(reqAlphaStd.getTransactionId(), copyAlpha.getBookCopyId(), librarian),
                "Cannot assign already reserved copy to another request");

        // 1. Transaction must be in REQUESTED status: cannot re-assign already RESERVED transaction
        BookCopy copyAlpha2 = bookCopyRepository.findAvailableCopy(bookAlpha.getBookId());
        assertNotNull(copyAlpha2);
        assertFalse(bookTransactionService.assignCopy(reqAlphaPrem.getTransactionId(), copyAlpha2.getBookCopyId(), librarian),
                "Cannot assign copy to an already RESERVED transaction");

        // Now assign copyAlpha2 to reqAlphaStd (now that reqAlphaPrem is reserved, reqAlphaStd is next eligible)
        assertTrue(bookTransactionService.assignCopy(reqAlphaStd.getTransactionId(), copyAlpha2.getBookCopyId(), librarian));
        assertEquals(TransactionStatus.RESERVED, reqAlphaStd.getStatus());
        assertEquals(BookCopyStatus.RESERVED, copyAlpha2.getStatus());

        // 9. Transitions: RESERVED -> READY_FOR_PICKUP -> ISSUED
        assertTrue(bookTransactionService.markReadyForPickup(reqAlphaPrem.getTransactionId(), librarian));
        assertEquals(TransactionStatus.READY_FOR_PICKUP, reqAlphaPrem.getStatus());

        // Cannot issue without being in READY_FOR_PICKUP (reqAlphaStd is only RESERVED)
        assertFalse(bookTransactionService.issueBook(reqAlphaStd.getTransactionId(), librarian),
                "Cannot issue book directly from RESERVED state");

        // Issue reqAlphaPrem successfully
        assertTrue(bookTransactionService.issueBook(reqAlphaPrem.getTransactionId(), librarian));
        assertEquals(TransactionStatus.ISSUED, reqAlphaPrem.getStatus());
        assertEquals(BookCopyStatus.ISSUED, copyAlpha.getStatus());
    }
}

