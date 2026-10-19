package service;

import authentication.PasswordAuthentication;
import enums.*;
import model.*;
import org.junit.jupiter.api.*;
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
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

public class StaffManageCurrentFinesWorkflowTest {

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

    private User memberUser;
    private User secondMember;
    private User librarianUser;
    private User adminUser;

    private static java.util.Map<String, Object> originalMainFields = new java.util.HashMap<>();

    @BeforeAll
    static void captureOriginals() throws Exception {
        for (Field f : Main.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) && !java.lang.reflect.Modifier.isFinal(f.getModifiers())) {
                f.setAccessible(true);
                originalMainFields.put(f.getName(), f.get(null));
            }
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        restoreOriginals();
    }

    @AfterAll
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
        userService = new UserService(userRepository, auditService);
        authService = new AuthService(userService, userRepository, new PasswordAuthentication(), auditService);
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

        memberUser = new User("Alice Member", "alice.fines@test.com", "9111111111", AuthService.hashPassword("MemberPass123"), UserRole.MEMBER, UserStatus.ACTIVE);
        secondMember = new User("Bob Member", "bob.fines@test.com", "9222222222", AuthService.hashPassword("MemberPass123"), UserRole.MEMBER, UserStatus.ACTIVE);
        librarianUser = new User("Leo Librarian", "leo.fines@test.com", "9333333333", AuthService.hashPassword("LibPass123"), UserRole.LIBRARIAN, UserStatus.ACTIVE);
        adminUser = new User("Ada Admin", "ada.fines@test.com", "9444444444", AuthService.hashPassword("AdminPass123"), UserRole.ADMIN, UserStatus.ACTIVE);

        userRepository.save(memberUser);
        userRepository.save(secondMember);
        userRepository.save(librarianUser);
        userRepository.save(adminUser);

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
        setStaticField(Main.class, "authService", authService);
        setStaticField(Main.class, "userService", userService);
    }

    private void setStaticField(Class<?> clazz, String fieldName, Object value) throws Exception {
        Field f = clazz.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(null, value);
    }

    private String runConsoleAction(Runnable action, User user, String input) throws Exception {
        setStaticField(Main.class, "currentUser", user);
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        System.setIn(in);
        setStaticField(Main.class, "sc", new Scanner(System.in));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));

        try {
            action.run();
        } finally {
            System.setOut(originalOut);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("1. Selecting one fine among multiple fines and paying the selected fine only")
    void testSelectOneFineAmongMultipleAndPaySelectedFineOnly() throws Exception {
        // Setup 3 fines for Alice
        Fine fine1 = new Fine(1001, memberUser.getPhone(), 501, 50.0, FineType.OVERDUE);
        Fine fine2 = new Fine(1002, memberUser.getPhone(), 502, 30.0, FineType.LOST_BOOK);
        Fine fine3 = new Fine(1003, memberUser.getPhone(), 503, 40.0, FineType.DAMAGED_BOOK);
        fineRepository.save(fine1);
        fineRepository.save(fine2);
        fineRepository.save(fine3);

        // Input flow:
        // Option 22 (Manage Current Fines) -> Enter member phone (9111111111)
        // -> Select fine 2 (Fine ID 1002) -> 1 (Pay Fine) -> amount 20.0 -> method 1 (UPI)
        // -> 0 (Back from fine list) -> 25 (Logout)
        String input = "22\n9111111111\n2\n1\n20.0\n1\n0\n25\n";
        String output = runConsoleAction(Librarian::librarian, librarianUser, input);

        // Verify prompt and list formatting
        assertTrue(output.contains("Enter member phone number:"));
        assertTrue(output.contains("1. Fine ID: 1001"));
        assertTrue(output.contains("2. Fine ID: 1002"));
        assertTrue(output.contains("3. Fine ID: 1003"));
        assertTrue(output.contains("Enter the fine number to manage, or 0 to go back:"));
        assertTrue(output.contains("Actions for Fine ID: 1002"));
        assertTrue(output.contains("Payment recorded successfully"));
        assertTrue(output.contains("Updated Fine Details"));

        // Verify repository state: selected fine ONLY was updated
        Fine updatedFine2 = fineRepository.findById(1002);
        assertEquals(20.0, updatedFine2.getAmountPaid(), 0.001);
        assertEquals(10.0, updatedFine2.getRemainingAmount(), 0.001);
        assertEquals(FineStatus.PARTIALLY_PAID, updatedFine2.getStatus());

        // Verify other fines remain completely untouched
        Fine untouchedFine1 = fineRepository.findById(1001);
        assertEquals(0.0, untouchedFine1.getAmountPaid(), 0.001);
        assertEquals(50.0, untouchedFine1.getRemainingAmount(), 0.001);
        assertEquals(FineStatus.PENDING, untouchedFine1.getStatus());

        Fine untouchedFine3 = fineRepository.findById(1003);
        assertEquals(0.0, untouchedFine3.getAmountPaid(), 0.001);
        assertEquals(40.0, untouchedFine3.getRemainingAmount(), 0.001);
        assertEquals(FineStatus.PENDING, untouchedFine3.getStatus());
    }

    @Test
    @DisplayName("2. Selecting one fine among multiple fines and waiving the selected fine only")
    void testSelectOneFineAmongMultipleAndWaiveSelectedFineOnly() throws Exception {
        Fine fine1 = new Fine(1001, memberUser.getPhone(), 501, 50.0, FineType.OVERDUE);
        Fine fine2 = new Fine(1002, memberUser.getPhone(), 502, 30.0, FineType.LOST_BOOK);
        fineRepository.save(fine1);
        fineRepository.save(fine2);

        // Input flow:
        // Option 22 -> Enter phone -> Select fine 1 -> 2 (Waive Fine) -> 0 (Back) -> 25 (Logout)
        String input = "22\n9111111111\n1\n2\n0\n25\n";
        String output = runConsoleAction(Librarian::librarian, librarianUser, input);

        assertTrue(output.contains("Fine waived successfully"));
        assertTrue(output.contains("--- Updated Fine Details ---"));
        assertTrue(output.contains("Status: WAIVED"));

        // Verify fine1 is WAIVED and fine2 is untouched
        Fine updatedFine1 = fineRepository.findById(1001);
        assertEquals(FineStatus.WAIVED, updatedFine1.getStatus());
        assertEquals(0.0, updatedFine1.getRemainingAmount(), 0.001);

        Fine untouchedFine2 = fineRepository.findById(1002);
        assertEquals(FineStatus.PENDING, untouchedFine2.getStatus());
        assertEquals(30.0, untouchedFine2.getRemainingAmount(), 0.001);
        assertEquals(0.0, untouchedFine2.getAmountPaid(), 0.001);
    }

    @Test
    @DisplayName("3. Invalid selections outside displayed list are rejected without modifying any fine")
    void testInvalidSelectionOutsideDisplayedListRejectsWithoutModifyingAnyFine() throws Exception {
        Fine fine1 = new Fine(1001, memberUser.getPhone(), 501, 50.0, FineType.OVERDUE);
        fineRepository.save(fine1);

        // Input flow:
        // Option 22 -> phone -> invalid 99 -> invalid -1 -> 0 (Back) -> 25 (Logout)
        String input = "22\n9111111111\n99\n-1\n0\n25\n";
        String output = runConsoleAction(Librarian::librarian, librarianUser, input);

        assertTrue(output.contains("Invalid selection. Selection outside the displayed list."));

        // Fine remains untouched
        Fine untouched = fineRepository.findById(1001);
        assertEquals(0.0, untouched.getAmountPaid(), 0.001);
        assertEquals(50.0, untouched.getRemainingAmount(), 0.001);
        assertEquals(FineStatus.PENDING, untouched.getStatus());
    }

    @Test
    @DisplayName("4. Going back from selection prompt and actions menu works cleanly")
    void testGoBackFromSelectionAndActionsMenu() throws Exception {
        Fine fine1 = new Fine(1001, memberUser.getPhone(), 501, 50.0, FineType.OVERDUE);
        fineRepository.save(fine1);

        // Input flow:
        // Option 22 -> phone -> select 1 -> 3 (Back from actions) -> 0 (Back from fine list) -> 25 (Logout)
        String input = "22\n9111111111\n1\n3\n0\n25\n";
        String output = runConsoleAction(Librarian::librarian, librarianUser, input);

        assertTrue(output.contains("Actions for Fine ID: 1001"));
        assertTrue(output.contains("Logged out"));

        // Fine remains untouched
        Fine untouched = fineRepository.findById(1001);
        assertEquals(FineStatus.PENDING, untouched.getStatus());
    }

    @Test
    @DisplayName("5. Member with no fines displays clear message and returns to menu")
    void testMemberWithNoFinesDisplaysClearMessageAndReturns() throws Exception {
        // secondMember (9222222222) has no fines
        String input = "22\n9222222222\n25\n";
        String output = runConsoleAction(Librarian::librarian, librarianUser, input);

        assertTrue(output.contains("No fines found for member: 9222222222"));
        assertTrue(output.contains("Logged out"));
    }

    @Test
    @DisplayName("6. Validation of non-existent or empty member phone numbers")
    void testMemberPhoneValidation() throws Exception {
        // Non-existent phone
        String inputNonExistent = "22\n0000000000\n25\n";
        String out1 = runConsoleAction(Librarian::librarian, librarianUser, inputNonExistent);
        assertTrue(out1.contains("Member not found with phone: 0000000000"));

        // Empty phone
        String inputEmpty = "22\n \n25\n";
        String out2 = runConsoleAction(Librarian::librarian, librarianUser, inputEmpty);
        assertTrue(out2.contains("Phone number cannot be empty."));
    }

    @Test
    @DisplayName("7. Admin follows the same workflow and can manage and waive target member's fines")
    void testAdminManageCurrentFinesWorkflow() throws Exception {
        Fine fine1 = new Fine(1001, memberUser.getPhone(), 501, 50.0, FineType.OVERDUE);
        fineRepository.save(fine1);

        // Under Admin: option 22 -> phone -> select 1 -> 2 (Waive) -> 0 (Back) -> 29 (Logout)
        String input = "22\n9111111111\n1\n2\n0\n29\n";
        String output = runConsoleAction(Admin::admin, adminUser, input);

        assertTrue(output.contains("Fine waived successfully"));
        assertTrue(output.contains("Status: WAIVED"));
        assertTrue(output.contains("Logged out"));

        Fine updated = fineRepository.findById(1001);
        assertEquals(FineStatus.WAIVED, updated.getStatus());
    }

    @Test
    @DisplayName("8. Member self-service Manage Fines behavior is preserved")
    void testMemberSelfServiceManageFinesPreserved() throws Exception {
        // Member Alice uses option 10 (Manage Fines). If no fines, displays "No fines found".
        String inputNoFines = "10\n12\n";
        String outNoFines = runConsoleAction(Member::memberMenu, memberUser, inputNoFines);
        assertTrue(outNoFines.contains("No fines found"));
        assertTrue(outNoFines.contains("Logged out successfully"));
    }
}
