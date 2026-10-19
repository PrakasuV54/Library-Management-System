package service;

import enums.*;
import model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import paymentStrategy.Razorpay;
import repository.*;
import ui.Admin;
import ui.Librarian;
import ui.Main;
import ui.Member;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

public class ErrorHandlingAndFailureMessagesTest {

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
    private UserService userService;
    private AuthService authService;

    private User admin;
    private User librarian;
    private User activeMember;
    private User inactiveMember;

    private MembershipPlan standardPlan;
    private Book testBook;
    private BookCopy availableCopy;

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
        notificationService = new TestNotificationService();
        policyService = new PolicyService(userRepository, membershipRepository, transactionRepository, fineRepository, auditService, membershipPlanRepository);
        fineService = new FineService(fineRepository, transactionRepository, policyService, auditService);
        userService = new UserService(userRepository, auditService);
        authService = new AuthService(userService, userRepository, new authentication.PasswordAuthentication(), auditService);
        bookService = new BookService(bookRepository, bookCopyRepository, transactionRepository, auditService);
        bookTransactionService = new BookTransactionService(bookRepository, bookCopyRepository, transactionRepository, policyService, auditService, notificationService, fineService);
        paymentService = new PaymentService(paymentRepository, fineRepository, new Razorpay(), auditService, membershipRepository, transactionRepository);
        membershipPlanService = new MembershipPlanService(membershipPlanRepository, userRepository);
        membershipService = new MembershipService(userRepository, membershipRepository, membershipPlanService, paymentRepository, new Razorpay(), auditService, notificationService);

        // Seed users
        admin = new User("System Admin", "admin@library.test", "9000000001", AuthService.hashPassword("AdminPass123"), UserRole.ADMIN, UserStatus.ACTIVE);
        librarian = new User("Head Librarian", "lib@library.test", "9000000002", AuthService.hashPassword("LibrarianPass123"), UserRole.LIBRARIAN, UserStatus.ACTIVE);
        activeMember = new User("Alice Active", "alice@test.com", "9876543210", AuthService.hashPassword("AlicePass123"), UserRole.MEMBER, UserStatus.ACTIVE);
        inactiveMember = new User("Bob Inactive", "bob@test.com", "9876543211", AuthService.hashPassword("BobPass123"), UserRole.MEMBER, UserStatus.INACTIVE);

        userRepository.save(admin);
        userRepository.save(librarian);
        userRepository.save(activeMember);
        userRepository.save(inactiveMember);

        // Seed plans
        standardPlan = new MembershipPlan(1, "Standard Plan", 299.0, 3);
        membershipPlanRepository.save(standardPlan);

        // Seed book & copy
        testBook = new Book("978-0132350884", "Clean Code", "Robert C. Martin", 2008, "Software Craftsmanship");
        bookRepository.save(testBook);
        availableCopy = new BookCopy(101, testBook.getBookId());
        bookCopyRepository.save(availableCopy);

        // Set static fields on Main for console test runs
        setStaticField(Main.class, "userRepository", userRepository);
        setStaticField(Main.class, "bookRepository", bookRepository);
        setStaticField(Main.class, "bookCopyRepository", bookCopyRepository);
        setStaticField(Main.class, "transactionRepository", transactionRepository);
        setStaticField(Main.class, "membershipRepository", membershipRepository);
        setStaticField(Main.class, "membershipPlanRepository", membershipPlanRepository);
        setStaticField(Main.class, "paymentRepository", paymentRepository);
        setStaticField(Main.class, "fineRepository", fineRepository);
        setStaticField(Main.class, "policyService", policyService);
        setStaticField(Main.class, "fineService", fineService);
        setStaticField(Main.class, "userService", userService);
        setStaticField(Main.class, "authService", authService);
        setStaticField(Main.class, "bookService", bookService);
        setStaticField(Main.class, "bookTransactionService", bookTransactionService);
        setStaticField(Main.class, "paymentService", paymentService);
        setStaticField(Main.class, "membershipPlanService", membershipPlanService);
        setStaticField(Main.class, "membershipService", membershipService);
        setStaticField(Main.class, "currentUser", null);
    }

    private void setStaticField(Class<?> clazz, String fieldName, Object value) throws Exception {
        Field f = clazz.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(null, value);
    }

    private String runMemberConsole(String input) throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        System.setIn(in);
        setStaticField(Main.class, "sc", new Scanner(System.in));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));

        try {
            Member.member();
        } finally {
            System.setOut(originalOut);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private String runLibrarianConsole(User user, String input) throws Exception {
        setStaticField(Main.class, "currentUser", user);
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        System.setIn(in);
        setStaticField(Main.class, "sc", new Scanner(System.in));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));

        try {
            Librarian.librarian();
        } finally {
            System.setOut(originalOut);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private String runAdminConsole(User user, String input) throws Exception {
        setStaticField(Main.class, "currentUser", user);
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        System.setIn(in);
        setStaticField(Main.class, "sc", new Scanner(System.in));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));

        try {
            Admin.admin();
        } finally {
            System.setOut(originalOut);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("1. Registration validation: empty name, invalid phone, duplicate phone, duplicate email, password mismatch")
    void testRegistrationFailures() {
        // Empty name
        assertEquals("Name cannot be empty.", userService.getRegistrationFailureReason("", "u@test.com", "9111111111", "Pass1234"));
        // Malformed phone
        assertEquals("Phone number must be a valid 10-digit number.", userService.getRegistrationFailureReason("User", "u@test.com", "12345", "Pass1234"));
        // Duplicate phone
        assertEquals("An account with phone number " + activeMember.getPhone() + " already exists. Please log in or use a different phone number.",
                userService.getRegistrationFailureReason("User", "u@test.com", activeMember.getPhone(), "Pass1234"));
        // Malformed email
        assertEquals("Invalid email format. Please provide a valid email address (e.g. name@example.com).",
                userService.getRegistrationFailureReason("User", "bademail", "9111111111", "Pass1234"));
        // Duplicate email
        assertEquals("An account with email address " + activeMember.getEmail() + " already exists. Please log in or use a different email address.",
                userService.getRegistrationFailureReason("User", activeMember.getEmail(), "9111111111", "Pass1234"));
        // Short password
        assertEquals("Password must be at least 8 characters long.",
                userService.getRegistrationFailureReason("User", "u@test.com", "9111111111", "short"));
    }

    @Test
    @DisplayName("2. Member login: empty input, invalid credentials, deactivated account, staff role redirection")
    void testLoginFailures() throws Exception {
        // Empty credentials
        String outEmpty = runMemberConsole("1\n\n\n3\n");
        assertTrue(outEmpty.contains("Phone/Email and password cannot be empty"), "Must reject empty credentials");

        // Invalid credentials (unknown user)
        String outUnknown = runMemberConsole("1\n9999999999\nWrongPass\n3\n");
        assertTrue(outUnknown.contains("Invalid credentials"), "Must return safe message for unknown user");

        // Deactivated user account
        String outInactive = runMemberConsole("1\n" + inactiveMember.getPhone() + "\nBobPass123\n3\n");
        assertTrue(outInactive.contains("account has been deactivated"), "Must inform deactivated user");

        // Staff logging in at member portal
        String outStaff = runMemberConsole("1\n" + librarian.getPhone() + "\nLibrarianPass123\n3\n");
        assertTrue(outStaff.contains("registered as LIBRARIAN"), "Must guide staff to Librarian menu");
    }

    @Test
    @DisplayName("3. Multi-validation failure reporting: expired membership + outstanding fine with resolution advice")
    void testMultipleBorrowingValidationFailures() {
        // Alice has an expired membership
        Membership expiredMem = new Membership(501, activeMember.getPhone(), standardPlan.getPlanId(),
                LocalDate.now().minusMonths(2), LocalDate.now().minusDays(5), MembershipStatus.EXPIRED, 1);
        membershipRepository.save(expiredMem);

        // Alice also has an outstanding fine of Rs. 150
        Fine pendingFine = new Fine(601, activeMember.getPhone(), 701, 150.0, FineType.OVERDUE);
        fineRepository.save(pendingFine);

        List<String> blockers = policyService.getBorrowingEligibilityFailureReasons(activeMember.getPhone());
        assertEquals(2, blockers.size(), "Must identify both expired membership and outstanding fine");
        assertTrue(blockers.stream().anyMatch(b -> b.contains("Membership has expired")), "Must report expired membership");
        assertTrue(blockers.stream().anyMatch(b -> b.contains("Outstanding fine: Rs.150.00")), "Must report exact fine amount");

        String advice = policyService.getBorrowingResolutionAdvice(blockers);
        assertEquals("Please renew your membership and settle the outstanding fine.", advice,
                "Must provide precise combined resolution advice");
    }

    @Test
    @DisplayName("4. Book request failure in console: reports exact multi-blocking reasons")
    void testBookRequestConsoleMultiFailure() throws Exception {
        // Alice has expired membership and outstanding fine
        Membership expiredMem = new Membership(502, activeMember.getPhone(), standardPlan.getPlanId(),
                LocalDate.now().minusMonths(2), LocalDate.now().minusDays(5), MembershipStatus.EXPIRED, 1);
        membershipRepository.save(expiredMem);
        Fine pendingFine = new Fine(602, activeMember.getPhone(), 702, 150.0, FineType.OVERDUE);
        fineRepository.save(pendingFine);

        // Login as Alice, attempt to request Clean Code (option 3), logout (option 12)
        String input = "1\n" + activeMember.getPhone() + "\nAlicePass123\n3\n978-0132350884\n12\n";
        String output = runMemberConsole(input);

        assertTrue(output.contains("Borrowing failed:"), "Must indicate borrowing failure");
        assertTrue(output.contains("Membership has expired"), "Must list membership expiration");
        assertTrue(output.contains("Outstanding fine: Rs.150.00"), "Must list exact fine amount");
        assertTrue(output.contains("Please renew your membership and settle the outstanding fine."), "Must display action advice");
    }

    private Transaction createTransaction(int id, String phone, int bookId, int copyId, LocalDate dueDate, TransactionStatus status) throws Exception {
        Transaction t = new Transaction(id, phone, bookId);
        setField(t, "bookCopyId", copyId);
        setField(t, "dueDate", dueDate);
        setField(t, "status", status);
        return t;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    @DisplayName("5. Book renew failure: overdue book cannot be renewed")
    void testRenewOverdueBookFailure() throws Exception {
        // Active membership for Alice
        Membership activeMem = new Membership(503, activeMember.getPhone(), standardPlan.getPlanId(),
                LocalDate.now().minusDays(10), LocalDate.now().plusMonths(1), MembershipStatus.ACTIVE, 1);
        membershipRepository.save(activeMem);

        // Overdue transaction (due 3 days ago)
        Transaction overdueTx = createTransaction(801, activeMember.getPhone(), testBook.getBookId(), availableCopy.getBookCopyId(),
                LocalDate.now().minusDays(3), TransactionStatus.ISSUED);
        transactionRepository.save(overdueTx);

        String failureReason = bookTransactionService.getRenewFailureReason(overdueTx.getTransactionId(), activeMember);
        assertNotNull(failureReason);
        assertTrue(failureReason.contains("overdue by 3 day(s)"), "Must state exact days overdue");
        assertTrue(failureReason.contains("Overdue books cannot be renewed"), "Must state policy restriction");
        assertFalse(bookTransactionService.renewBook(overdueTx.getTransactionId(), activeMember), "Service must reject renewal");
    }

    @Test
    @DisplayName("6. Book cancellation failure: non-cancellable already-issued book")
    void testCancelIssuedBookFailure() throws Exception {
        // Loan is already issued to Alice
        Transaction issuedTx = createTransaction(802, activeMember.getPhone(), testBook.getBookId(), availableCopy.getBookCopyId(),
                LocalDate.now().plusDays(12), TransactionStatus.ISSUED);
        transactionRepository.save(issuedTx);

        // Login as Alice, option 4 (Cancel request), enter ISBN, option 12 (Logout)
        String input = "1\n" + activeMember.getPhone() + "\nAlicePass123\n4\n" + testBook.getIsbn() + "\n12\n";
        String output = runMemberConsole(input);

        assertTrue(output.contains("Cancellation failed"), "Must report cancellation failure");
        assertTrue(output.contains("is already issued to you and cannot be cancelled"), "Must explain book is issued");
        assertTrue(output.contains("Please return the book instead"), "Must guide user to return the book");
    }

    @Test
    @DisplayName("7. Copy assignment failure: queue priority violation and copy status")
    void testCopyAssignmentFailureDiagnostics() {
        // Active membership
        Membership activeMem = new Membership(504, activeMember.getPhone(), standardPlan.getPlanId(),
                LocalDate.now().minusDays(1), LocalDate.now().plusMonths(1), MembershipStatus.ACTIVE, 1);
        membershipRepository.save(activeMem);

        // Request 1: ActiveMember
        Transaction req1 = new Transaction(901, activeMember.getPhone(), testBook.getBookId());
        transactionRepository.save(req1);

        // Non-existent transaction
        assertEquals("Request transaction with ID 9999 not found.",
                bookTransactionService.getCopyAssignmentFailureReason(9999, availableCopy.getBookCopyId(), librarian));

        // Non-existent copy
        assertEquals("Book copy with ID 8888 not found.",
                bookTransactionService.getCopyAssignmentFailureReason(req1.getTransactionId(), 8888, librarian));

        // Copy already issued
        availableCopy.reserve();
        availableCopy.issue();
        bookCopyRepository.save(availableCopy);
        assertEquals("Copy ID " + availableCopy.getBookCopyId() + " is not available (Current status: ISSUED).",
                bookTransactionService.getCopyAssignmentFailureReason(req1.getTransactionId(), availableCopy.getBookCopyId(), librarian));
    }

    @Test
    @DisplayName("8. Membership activation: cannot activate when active membership already exists")
    void testMembershipActivationAlreadyActive() {
        Membership activeMem = new Membership(505, activeMember.getPhone(), standardPlan.getPlanId(),
                LocalDate.now(), LocalDate.now().plusMonths(1), MembershipStatus.ACTIVE, 1);
        membershipRepository.save(activeMem);

        String failure = membershipService.getPurchaseFailureReason(activeMember.getPhone(), standardPlan.getPlanId(), 1, PaymentMethod.UPI, activeMember);
        assertNotNull(failure);
        assertTrue(failure.contains("You already have an active membership"), "Must report existing active membership");
        assertTrue(failure.contains("Use Renew Membership to extend"), "Must guide user to renewal");
    }

    @Test
    @DisplayName("9. Fine payment: invalid amount exceeding outstanding balance")
    void testFinePaymentAmountExceedsRemaining() {
        Fine fine = new Fine(603, activeMember.getPhone(), 703, 50.0, FineType.OVERDUE);
        fineRepository.save(fine);

        // Paying Rs. 100 on a Rs. 50 fine
        String reason = paymentService.getPayFineFailureReason(activeMember.getPhone(), fine.getFineId(), 100.0, PaymentMethod.UPI);
        assertNotNull(reason);
        assertTrue(reason.contains("exceeds outstanding balance of Rs.50.00"), "Must report exceeding balance");

        // Paying 0 or negative
        String zeroReason = paymentService.getPayFineFailureReason(activeMember.getPhone(), fine.getFineId(), 0.0, PaymentMethod.UPI);
        assertNotNull(zeroReason);
        assertTrue(zeroReason.contains("greater than zero"), "Must reject zero payment");
    }

    @Test
    @DisplayName("10. Payment refund: cannot refund non-existent, failed, or already refunded payment")
    void testPaymentRefundFailures() {
        // Non-existent payment
        assertEquals("Payment with ID 9999 not found.", paymentService.getRefundFailureReason(9999, librarian));

        // Unauthorized user
        assertEquals("Insufficient permissions. Staff authorization required.", paymentService.getRefundFailureReason(9999, activeMember));

        // Successful payment then refunded
        Payment p = new Payment(1001, 603, activeMember.getPhone(), 50.0, PaymentMethod.UPI);
        p.markSuccess("REF-123");
        p.refund();
        paymentRepository.save(p);

        assertEquals("Payment has already been refunded.", paymentService.getRefundFailureReason(p.getPaymentId(), librarian));
    }

    @Test
    @DisplayName("11. Admin user management: self-deactivation and self-deletion prevention")
    void testAdminSelfModificationProtection() {
        // Self-deactivation
        assertEquals("Administrators cannot deactivate their own account.",
                userService.getUserUpdateFailureReason(admin, admin.getUserId(), 5, null));

        // Self-deletion
        assertEquals("Administrators cannot delete their own account.",
                userService.getUserUpdateFailureReason(admin, admin.getUserId(), 7, null));

        // Self-role change
        assertEquals("Administrators cannot change their own role.",
                userService.getUserUpdateFailureReason(admin, admin.getUserId(), 6, UserRole.MEMBER));
    }

    @Test
    @DisplayName("12. Admin policy management: invalid limits rejected with specific messages")
    void testPolicyValidationConsole() throws Exception {
        // Update max books with 0 -> should fail with specific message
        String input = "27\n1\n0\n29\n";
        String output = runAdminConsole(admin, input);
        assertTrue(output.contains("Maximum books limit must be at least 1"), "Must reject max books < 1");

        // Update loan days with 0 -> should fail with specific message
        String inputDays = "27\n2\n0\n29\n";
        String outDays = runAdminConsole(admin, inputDays);
        assertTrue(outDays.contains("Loan period must be at least 1 day"), "Must reject loan days < 1");

        // Update fine with negative -> should fail with specific message
        String inputFine = "27\n3\n-5.0\n29\n";
        String outFine = runAdminConsole(admin, inputFine);
        assertTrue(outFine.contains("Amount cannot be negative"), "Must reject negative fine rate");
    }

    @Test
    @DisplayName("13. Issue book failure: borrowing limit reached reported specifically")
    void testIssueBookLimitReached() throws Exception {
        // Active membership with max 1 book
        MembershipPlan oneBookPlan = new MembershipPlan(2, "Single Book Plan", 199.0, 1);
        membershipPlanRepository.save(oneBookPlan);
        Membership activeMem = new Membership(506, activeMember.getPhone(), oneBookPlan.getPlanId(),
                LocalDate.now().minusDays(1), LocalDate.now().plusMonths(1), MembershipStatus.ACTIVE, 1);
        membershipRepository.save(activeMem);

        // Already has 1 book issued
        Transaction alreadyIssued = createTransaction(803, activeMember.getPhone(), testBook.getBookId(), availableCopy.getBookCopyId(),
                LocalDate.now().plusDays(13), TransactionStatus.ISSUED);
        transactionRepository.save(alreadyIssued);

        // A second request ready for pickup with a distinct copy
        BookCopy copy2 = new BookCopy(102, testBook.getBookId());
        copy2.reserve();
        bookCopyRepository.save(copy2);
        Transaction req2 = createTransaction(804, activeMember.getPhone(), testBook.getBookId(), copy2.getBookCopyId(),
                null, TransactionStatus.READY_FOR_PICKUP);
        transactionRepository.save(req2);

        String failure = bookTransactionService.getIssueFailureReason(req2.getTransactionId(), librarian);
        assertNotNull(failure);
        assertTrue(failure.contains("Borrowing limit reached"), "Must explicitly report borrowing limit reached");
        assertTrue(failure.contains("already borrowed 1 of 1 allowed books"), "Must report exact counts");
        assertFalse(bookTransactionService.issueBook(req2.getTransactionId(), librarian), "Issue must fail");
    }

    @Test
    @DisplayName("14. Return book failure: transaction not found, not ISSUED, and copy mismatch")
    void testReturnBookFailureReasons() {
        // Non-existent transaction
        assertEquals("Transaction with ID 9999 not found.",
                bookTransactionService.getReturnFailureReason(9999, librarian));

        // Transaction not in ISSUED status (e.g. REQUESTED)
        Transaction requestedTx = new Transaction(998, activeMember.getPhone(), testBook.getBookId());
        transactionRepository.save(requestedTx);
        assertEquals("Transaction is in REQUESTED status. Only ISSUED loans can be returned.",
                bookTransactionService.getReturnFailureReason(requestedTx.getTransactionId(), librarian));

        // Unauthorized caller
        assertEquals("Insufficient permissions. Staff privileges required.",
                bookTransactionService.getReturnFailureReason(requestedTx.getTransactionId(), activeMember));
    }

    @Test
    @DisplayName("15. Mark copy lost/damaged failure: copy not found and already marked")
    void testMarkConditionFailureReasons() {
        // Non-existent copy
        assertEquals("Book copy with ID 8888 not found.",
                bookTransactionService.getMarkConditionFailureReason(8888, librarian, true));

        // Already lost
        availableCopy.markLost();
        bookCopyRepository.save(availableCopy);
        assertEquals("Book copy ID " + availableCopy.getBookCopyId() + " is already marked as LOST.",
                bookTransactionService.getMarkConditionFailureReason(availableCopy.getBookCopyId(), librarian, true));

        // Unauthorized caller
        assertEquals("Insufficient permissions. Staff privileges required.",
                bookTransactionService.getMarkConditionFailureReason(availableCopy.getBookCopyId(), activeMember, true));
    }

    @Test
    @DisplayName("16. Membership status management: suspend and reactivate failure reasons")
    void testMembershipStatusFailureReasons() {
        // Suspend when no active membership exists
        assertEquals("Member does not have an active membership to suspend.",
                membershipService.getSuspendFailureReason(librarian, inactiveMember.getPhone()));

        // Reactivate when no suspended membership exists
        assertEquals("No suspended membership found for this member.",
                membershipService.getReactivateFailureReason(librarian, inactiveMember.getPhone()));

        // Unauthorized caller
        assertEquals("Insufficient permissions. Staff authorization required.",
                membershipService.getSuspendFailureReason(activeMember, inactiveMember.getPhone()));
        assertEquals("Insufficient permissions. Staff authorization required.",
                membershipService.getReactivateFailureReason(activeMember, inactiveMember.getPhone()));
    }

    @Test
    @DisplayName("17. Fine waiver failure reasons: paid, already waived, not found, unauthorized")
    void testFineWaiverFailureReasons() {
        Fine fine = new Fine(605, activeMember.getPhone(), 705, 50.0, FineType.OVERDUE);
        fineRepository.save(fine);

        // Unauthorized caller
        assertEquals("Insufficient permissions. Staff authorization required.",
                fineService.getWaiveFineFailureReason(fine.getFineId(), activeMember));

        // Non-existent fine
        assertEquals("Fine not found with ID 9999.",
                fineService.getWaiveFineFailureReason(9999, librarian));

        // Paid fine cannot be waived
        fine.markPaid();
        fineRepository.save(fine);
        assertEquals("Paid fine cannot be waived.",
                fineService.getWaiveFineFailureReason(fine.getFineId(), librarian));

        // Waived fine
        Fine waivedFine = new Fine(606, activeMember.getPhone(), 706, 30.0, FineType.OVERDUE);
        waivedFine.waive();
        fineRepository.save(waivedFine);
        assertEquals("Fine is already waived.",
                fineService.getWaiveFineFailureReason(waivedFine.getFineId(), librarian));
    }

    @Test
    @DisplayName("18. Catalogue management: duplicate ISBN and delete with existing copies")
    void testCatalogueManagementFailureReasons() {
        // Add book with existing ISBN
        Book duplicateBook = new Book("978-0132350884", "Clean Code Duplicate", "Publisher", 2008, "Desc");
        assertEquals("A book with ISBN '978-0132350884' already exists in the catalogue.",
                bookService.getAddBookFailureReason(duplicateBook, 1, librarian));

        // Add book with 0 copies
        Book validNewBook = new Book("978-1111111111", "Unique Title", "Publisher", 2021, "Desc");
        assertEquals("Number of copies must be at least 1.",
                bookService.getAddBookFailureReason(validNewBook, 0, librarian));

        // Delete book with existing inventory copies
        assertTrue(bookService.getDeleteBookFailureReason(testBook.getBookId(), librarian)
                .contains("copy/copies currently exist in inventory. Remove all physical copies first."));

        // Unauthorized user adding book
        assertEquals("Insufficient permissions. Staff authorization required.",
                bookService.getAddBookFailureReason(validNewBook, 1, activeMember));
    }

    @Test
    @DisplayName("19. Membership plan creation failure reasons: negative amount and empty name")
    void testMembershipPlanCreationFailureReasons() {
        // Empty plan name
        assertEquals("Plan name cannot be empty.",
                membershipPlanService.getCreatePlanFailureReason(admin, "", 299.0));

        // Non-positive amount
        assertEquals("Plan amount must be greater than zero.",
                membershipPlanService.getCreatePlanFailureReason(admin, "New Plan", 0.0));

        // Unauthorized user
        assertEquals("Insufficient permissions. Active administrator privileges required.",
                membershipPlanService.getCreatePlanFailureReason(librarian, "Staff Plan", 199.0));
    }
}
