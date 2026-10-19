package service;

import enums.*;
import model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import paymentStrategy.Razorpay;
import repository.*;
import ui.Librarian;
import ui.Main;
import ui.Member;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

public class ConsoleManualWorkflowExecutionTest {

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
    private PaymentService paymentService;

    private User librarian;
    private User standardMember;
    private User familyPremiumMember;
    private User premiumMember;
    private User familyMember;

    private MembershipPlan standardPlan;
    private MembershipPlan premiumPlan;
    private MembershipPlan familyPlan;
    private MembershipPlan familyPremiumPlan;

    private Book bookA; // Clean Code
    private Book bookB; // 1984

    private static java.util.Map<String, Object> originalMainFields = new java.util.HashMap<>();

    @org.junit.jupiter.api.BeforeAll
    static void captureOriginals() throws Exception {
        for (Field f : Main.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) && !java.lang.reflect.Modifier.isFinal(f.getModifiers())) {
                f.setAccessible(true);
                originalMainFields.put(f.getName(), f.get(null));
            }
        }
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() throws Exception {
        restoreOriginals();
    }

    @org.junit.jupiter.api.AfterAll
    static void restoreOriginals() throws Exception {
        for (java.util.Map.Entry<String, Object> entry : originalMainFields.entrySet()) {
            try {
                Field f = Main.class.getDeclaredField(entry.getKey());
                f.setAccessible(true);
                f.set(null, entry.getValue());
            } catch (Exception ignored) {}
        }
    }

    @BeforeEach
    void setUp() throws Exception {
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
        paymentService = new PaymentService(paymentRepository, fineRepository, new Razorpay(), auditService, membershipRepository, transactionRepository);

        // Preload plans
        standardPlan = new MembershipPlan(DataStore.getMembershipPlanId(), "Standard Plan", 299.0);
        premiumPlan = new MembershipPlan(DataStore.getMembershipPlanId(), "Premium Plan", 599.0);
        familyPlan = new MembershipPlan(DataStore.getMembershipPlanId(), MembershipPlanService.DEFAULT_FAMILY_PLAN_NAME, MembershipPlanService.DEFAULT_FAMILY_PLAN_PRICE);
        familyPremiumPlan = new MembershipPlan(DataStore.getMembershipPlanId(), MembershipPlanService.DEFAULT_FAMILY_PREMIUM_PLAN_NAME, MembershipPlanService.DEFAULT_FAMILY_PREMIUM_PLAN_PRICE);

        membershipPlanRepository.save(standardPlan);
        membershipPlanRepository.save(premiumPlan);
        membershipPlanRepository.save(familyPlan);
        membershipPlanRepository.save(familyPremiumPlan);

        // Users
        librarian = new User("Head Librarian", "console.lib@test.com", "8888888888", "lib123", UserRole.LIBRARIAN, UserStatus.ACTIVE);
        standardMember = new User("Alice Standard", "alice@test.com", "9111111111", "pass1", UserRole.MEMBER, UserStatus.ACTIVE);
        familyPremiumMember = new User("David FamilyPremium", "david@test.com", "9222222222", "pass2", UserRole.MEMBER, UserStatus.ACTIVE);
        premiumMember = new User("Bob Premium", "bob@test.com", "9333333333", "pass3", UserRole.MEMBER, UserStatus.ACTIVE);
        familyMember = new User("Charlie Family", "charlie@test.com", "9444444444", "pass4", UserRole.MEMBER, UserStatus.ACTIVE);

        userRepository.save(librarian);
        userRepository.save(standardMember);
        userRepository.save(familyPremiumMember);
        userRepository.save(premiumMember);
        userRepository.save(familyMember);

        // Memberships
        membershipService.getMembershipByPhone(standardMember.getPhone(), standardPlan.getPlanId(), 1, PaymentMethod.UPI, standardMember);
        membershipService.getMembershipByPhone(familyPremiumMember.getPhone(), familyPremiumPlan.getPlanId(), 1, PaymentMethod.CARD, familyPremiumMember);
        membershipService.getMembershipByPhone(premiumMember.getPhone(), premiumPlan.getPlanId(), 1, PaymentMethod.UPI, premiumMember);
        membershipService.getMembershipByPhone(familyMember.getPhone(), familyPlan.getPlanId(), 1, PaymentMethod.CARD, familyMember);

        // Books
        bookA = new Book("978-0132350884", "Clean Code", "Prentice Hall", 2008, "Technical");
        bookA.addAuthor(new Author(1, "Robert C. Martin"));
        bookB = new Book("978-0451524935", "1984", "Secker & Warburg", 1950, "Literature");
        bookB.addAuthor(new Author(2, "George Orwell"));
        bookService.addBook(bookA, 2, librarian);
        bookService.addBook(bookB, 2, librarian);

        // Wire Main static fields
        injectMainDependencies();
    }

    private void injectMainDependencies() throws Exception {
        setStaticField(Main.class, "userRepository", userRepository);
        setStaticField(Main.class, "membershipRepository", membershipRepository);
        setStaticField(Main.class, "membershipPlanRepository", membershipPlanRepository);
        setStaticField(Main.class, "paymentRepository", paymentRepository);
        setStaticField(Main.class, "fineRepository", fineRepository);
        setStaticField(Main.class, "transactionRepository", transactionRepository);
        setStaticField(Main.class, "bookRepository", bookRepository);
        setStaticField(Main.class, "bookCopyRepository", bookCopyRepository);
        setStaticField(Main.class, "auditLogRepository", auditLogRepository);
        setStaticField(Main.class, "policyService", policyService);
        setStaticField(Main.class, "fineService", fineService);
        setStaticField(Main.class, "membershipPlanService", membershipPlanService);
        setStaticField(Main.class, "membershipService", membershipService);
        setStaticField(Main.class, "bookService", bookService);
        setStaticField(Main.class, "bookTransactionService", bookTransactionService);
        setStaticField(Main.class, "paymentService", paymentService);
        setStaticField(Main.class, "notificationService", notificationService);
        setStaticField(Main.class, "auditService", auditService);
    }

    private void setStaticField(Class<?> clazz, String fieldName, Object value) throws Exception {
        Field f = clazz.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(null, value);
    }

    private String runConsole(User user, boolean isLibrarian, String simulatedInput) throws Exception {
        setStaticField(Main.class, "currentUser", user);
        ByteArrayInputStream in = new ByteArrayInputStream(simulatedInput.getBytes(StandardCharsets.UTF_8));
        System.setIn(in);
        setStaticField(Main.class, "sc", new Scanner(System.in));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));

        try {
            if (isLibrarian) {
                Librarian.librarian();
            } else {
                java.lang.reflect.Method m = Member.class.getDeclaredMethod("memberMenu");
                m.setAccessible(true);
                m.invoke(null);
            }
        } finally {
            System.setOut(originalOut);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private void setRequestDate(Transaction t, LocalDateTime dt) throws Exception {
        Field f = Transaction.class.getDeclaredField("requestDate");
        f.setAccessible(true);
        f.set(t, dt);
    }

    @Test
    @DisplayName("Manual-Style E2E Verification: Scenarios 1 to 7 with Standard vs Family Premium")
    void testManualE2EWorkflowScenarios1to7() throws Exception {
        // -------------------------------------------------------------
        // Scenario 1: Standard member requests Book A (Clean Code) via Member UI
        // -------------------------------------------------------------
        // Input: 3 (Request Book), ISBN "978-0132350884", 12 (Logout)
        String outputAlice = runConsole(standardMember, false, "3\n978-0132350884\n12\n");
        assertTrue(outputAlice.contains("Book requested successfully"), "Standard member request must succeed in console UI");
        assertTrue(outputAlice.contains("Status: REQUESTED"), "Status must be REQUESTED");

        List<Transaction> pendingAlice = transactionRepository.findByUserPhone(standardMember.getPhone());
        assertEquals(1, pendingAlice.size());
        Transaction txStandard = pendingAlice.get(0);
        assertEquals(TransactionStatus.REQUESTED, txStandard.getStatus());
        // Set older timestamp (e.g. 1 hour ago)
        setRequestDate(txStandard, LocalDateTime.now().minusHours(1));
        transactionRepository.save(txStandard);

        // -------------------------------------------------------------
        // Scenario 2: Family Premium member requests the same book later via Member UI
        // -------------------------------------------------------------
        // Input: 3 (Request Book), ISBN "978-0132350884", 12 (Logout)
        String outputDavid = runConsole(familyPremiumMember, false, "3\n978-0132350884\n12\n");
        assertTrue(outputDavid.contains("Book requested successfully"), "Family Premium member request must succeed in console UI");

        List<Transaction> pendingDavid = transactionRepository.findByUserPhone(familyPremiumMember.getPhone());
        assertEquals(1, pendingDavid.size());
        Transaction txFamilyPrem = pendingDavid.get(0);
        assertEquals(TransactionStatus.REQUESTED, txFamilyPrem.getStatus());
        // Set newer timestamp (e.g. 5 minutes ago)
        setRequestDate(txFamilyPrem, LocalDateTime.now().minusMinutes(5));
        transactionRepository.save(txFamilyPrem);

        // -------------------------------------------------------------
        // Scenario 3: Librarian views pending requests and sees Family Premium prioritized first
        // -------------------------------------------------------------
        // Input: 14 (View Pending Requests), 2 (Back), 25 (Logout)
        String outputView = runConsole(librarian, true, "14\n2\n25\n");
        assertTrue(outputView.contains("David FamilyPremium"), "Pending list must show David FamilyPremium");
        assertTrue(outputView.contains("Alice Standard"), "Pending list must show Alice Standard");

        int indexDavid = outputView.indexOf("David FamilyPremium");
        int indexAlice = outputView.indexOf("Alice Standard");
        assertTrue(indexDavid < indexAlice, "Family Premium request must appear BEFORE older Standard request in pending view!");

        BookCopy availableCopy = bookCopyRepository.findAvailableCopy(bookA.getBookId());
        assertNotNull(availableCopy);
        int copyId = availableCopy.getBookCopyId();

        // -------------------------------------------------------------
        // Scenario 4: Attempting to assign Standard request first is rejected without modifying state
        // -------------------------------------------------------------
        // Input: 14 (View Pending), 1 (Assign copy), reqId = txStandard.id, copyId, 25 (Logout)
        String inputAssignStd = "14\n1\n" + txStandard.getTransactionId() + "\n" + copyId + "\n25\n";
        String outputAssignStd = runConsole(librarian, true, inputAssignStd);

        assertTrue(outputAssignStd.contains("Unable to assign copy"), "Out-of-order assignment must be rejected in console UI");
        assertEquals(TransactionStatus.REQUESTED, txStandard.getStatus(), "Standard transaction must remain REQUESTED");
        assertEquals(TransactionStatus.REQUESTED, txFamilyPrem.getStatus(), "Family Premium transaction must remain REQUESTED");
        assertEquals(BookCopyStatus.AVAILABLE, availableCopy.getStatus(), "Copy must remain AVAILABLE");

        // -------------------------------------------------------------
        // Scenario 5: Assigning Family Premium request succeeds and changes both to RESERVED
        // -------------------------------------------------------------
        // Input: 14 (View Pending), 1 (Assign copy), reqId = txFamilyPrem.id, copyId, 25 (Logout)
        String inputAssignFP = "14\n1\n" + txFamilyPrem.getTransactionId() + "\n" + copyId + "\n25\n";
        String outputAssignFP = runConsole(librarian, true, inputAssignFP);

        assertTrue(outputAssignFP.contains("Copy assigned successfully (Status: RESERVED)"), "Assignment must succeed for Family Premium");
        assertEquals(TransactionStatus.RESERVED, txFamilyPrem.getStatus(), "Family Premium transaction must be RESERVED");
        assertEquals(BookCopyStatus.RESERVED, availableCopy.getStatus(), "Copy must be RESERVED");
        assertEquals(copyId, txFamilyPrem.getBookCopyId());

        // -------------------------------------------------------------
        // Scenario 6: Staff marks READY_FOR_PICKUP and member is notified
        // -------------------------------------------------------------
        // Input: 15 (Mark Ready for Pickup), reqId = txFamilyPrem.id, 25 (Logout)
        String inputReady = "15\n" + txFamilyPrem.getTransactionId() + "\n25\n";
        String outputReady = runConsole(librarian, true, inputReady);

        assertTrue(outputReady.contains("Marked ready"), "Transaction must be marked ready in console UI");
        assertEquals(TransactionStatus.READY_FOR_PICKUP, txFamilyPrem.getStatus());

        // -------------------------------------------------------------
        // Scenario 7: Staff issues book; limit of 15 for Family Premium enforced
        // -------------------------------------------------------------
        assertEquals(15, policyService.getMaxBooksForUser(familyPremiumMember.getPhone()));

        // Input: 16 (Issue Book), reqId = txFamilyPrem.id, 25 (Logout)
        String inputIssue = "16\n" + txFamilyPrem.getTransactionId() + "\n25\n";
        String outputIssue = runConsole(librarian, true, inputIssue);

        assertTrue(outputIssue.contains("Book issued"), "Book must be issued successfully in console UI");
        assertEquals(TransactionStatus.ISSUED, txFamilyPrem.getStatus());
        assertEquals(BookCopyStatus.ISSUED, availableCopy.getStatus());
        assertEquals(1, policyService.countIssuedBooks(familyPremiumMember.getPhone()));
    }

    @Test
    @DisplayName("Manual-Style E2E Verification: Scenario 8 with Premium versus Family")
    void testManualE2EWorkflowScenario8PremiumVsFamily() throws Exception {
        // Family member requests Clean Code first
        String outputFam = runConsole(familyMember, false, "3\n978-0132350884\n12\n");
        assertTrue(outputFam.contains("Book requested successfully"));
        Transaction txFam = transactionRepository.findByUserPhone(familyMember.getPhone()).get(0);
        setRequestDate(txFam, LocalDateTime.now().minusHours(2));
        transactionRepository.save(txFam);

        // Premium member requests Clean Code later
        String outputPrem = runConsole(premiumMember, false, "3\n978-0132350884\n12\n");
        assertTrue(outputPrem.contains("Book requested successfully"));
        Transaction txPrem = transactionRepository.findByUserPhone(premiumMember.getPhone()).get(0);
        setRequestDate(txPrem, LocalDateTime.now().minusMinutes(5));
        transactionRepository.save(txPrem);

        // Librarian views pending requests
        String outputView = runConsole(librarian, true, "14\n2\n25\n");
        int indexPrem = outputView.indexOf("Bob Premium");
        int indexFam = outputView.indexOf("Charlie Family");
        assertTrue(indexPrem < indexFam, "Premium request must appear before older Family request in pending list");

        BookCopy copy = bookCopyRepository.findAvailableCopy(bookA.getBookId());
        assertNotNull(copy);

        // Attempting to assign Family request first fails
        String outputFail = runConsole(librarian, true, "14\n1\n" + txFam.getTransactionId() + "\n" + copy.getBookCopyId() + "\n25\n");
        assertTrue(outputFail.contains("Unable to assign copy"));
        assertEquals(TransactionStatus.REQUESTED, txFam.getStatus());

        // Assigning Premium request succeeds
        String outputSucc = runConsole(librarian, true, "14\n1\n" + txPrem.getTransactionId() + "\n" + copy.getBookCopyId() + "\n25\n");
        assertTrue(outputSucc.contains("Copy assigned successfully (Status: RESERVED)"));
        assertEquals(TransactionStatus.RESERVED, txPrem.getStatus());
    }

    @Test
    @DisplayName("Manual-Style E2E Verification: Scenario 9 Request Queues for Different Books Remain Independent")
    void testManualE2EWorkflowScenario9BookQueuesIndependent() throws Exception {
        // Standard member requests Book A (Clean Code)
        runConsole(standardMember, false, "3\n978-0132350884\n12\n");
        Transaction txA = transactionRepository.findByUserPhone(standardMember.getPhone()).get(0);

        // Premium member requests Book B (1984)
        runConsole(premiumMember, false, "3\n978-0451524935\n12\n");
        Transaction txB = transactionRepository.findByUserPhone(premiumMember.getPhone()).get(0);

        BookCopy copyA = bookCopyRepository.findAvailableCopy(bookA.getBookId());
        BookCopy copyB = bookCopyRepository.findAvailableCopy(bookB.getBookId());
        assertNotNull(copyA);
        assertNotNull(copyB);

        // Assigning copy of Book A to Standard member succeeds even though Premium member has a request for Book B
        String outputAssignA = runConsole(librarian, true, "14\n1\n" + txA.getTransactionId() + "\n" + copyA.getBookCopyId() + "\n25\n");
        assertTrue(outputAssignA.contains("Copy assigned successfully (Status: RESERVED)"),
                "Request for Book A must not be blocked by request for Book B");
        assertEquals(TransactionStatus.RESERVED, txA.getStatus());

        // Assigning copy of Book B to Premium member succeeds independently
        String outputAssignB = runConsole(librarian, true, "14\n1\n" + txB.getTransactionId() + "\n" + copyB.getBookCopyId() + "\n25\n");
        assertTrue(outputAssignB.contains("Copy assigned successfully (Status: RESERVED)"));
        assertEquals(TransactionStatus.RESERVED, txB.getStatus());
    }
}
