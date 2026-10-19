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
import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

public class CumulativeRoleBasedMenuAndPermissionsTest {

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
    private User librarianUser;
    private User adminUser;
    private MembershipPlan standardPlan;
    private Book sampleBook;

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

        standardPlan = new MembershipPlan(101, "Standard Plan", 299.0);
        membershipPlanRepository.save(standardPlan);

        memberUser = new User("Alice Member", "alice.perm@test.com", "9111111111", AuthService.hashPassword("MemberPass123"), UserRole.MEMBER, UserStatus.ACTIVE);
        librarianUser = new User("Leo Librarian", "leo.perm@test.com", "9222222222", AuthService.hashPassword("LibPass123"), UserRole.LIBRARIAN, UserStatus.ACTIVE);
        adminUser = new User("Ada Admin", "ada.perm@test.com", "9333333333", AuthService.hashPassword("AdminPass123"), UserRole.ADMIN, UserStatus.ACTIVE);

        userRepository.save(memberUser);
        userRepository.save(librarianUser);
        userRepository.save(adminUser);

        sampleBook = new Book("978-0132350884", "Clean Code", "Prentice Hall", 2008, "Technical");
        sampleBook.addAuthor(new Author(1, "Robert C. Martin"));
        sampleBook.addGenre(new Genre(1, "Software Engineering"));
        bookService.addBook(sampleBook, 2, adminUser);

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

    private Object getStaticField(Class<?> clazz, String fieldName) throws Exception {
        Field f = clazz.getDeclaredField(fieldName);
        f.setAccessible(true);
        return f.get(null);
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
    @DisplayName("1. Cumulative role permissions in AuthService: Member <= Librarian <= Admin")
    void testCumulativeRolePermissionsInAuthService() {
        // Member has only MEMBER permissions
        assertTrue(authService.hasRole(memberUser, UserRole.MEMBER));
        assertFalse(authService.hasRole(memberUser, UserRole.LIBRARIAN));
        assertFalse(authService.hasRole(memberUser, UserRole.ADMIN));

        // Librarian has MEMBER and LIBRARIAN permissions, but not ADMIN
        assertTrue(authService.hasRole(librarianUser, UserRole.MEMBER));
        assertTrue(authService.hasRole(librarianUser, UserRole.LIBRARIAN));
        assertFalse(authService.hasRole(librarianUser, UserRole.ADMIN));

        // Admin has all permissions: MEMBER, LIBRARIAN, and ADMIN
        assertTrue(authService.hasRole(adminUser, UserRole.MEMBER));
        assertTrue(authService.hasRole(adminUser, UserRole.LIBRARIAN));
        assertTrue(authService.hasRole(adminUser, UserRole.ADMIN));
    }

    @Test
    @DisplayName("2. Negative test: MEMBER cannot enter Librarian or Admin console menus")
    void testMemberCannotAccessLibrarianOrAdminConsoleMenus() throws Exception {
        String libOutput = runConsoleAction(Librarian::librarian, memberUser, "25\n");
        assertTrue(libOutput.contains("Please log in as Librarian or Admin"), "Member must be denied entry to Librarian menu");

        String adminOutput = runConsoleAction(Admin::admin, memberUser, "29\n");
        assertTrue(adminOutput.contains("Please log in as Admin"), "Member must be denied entry to Admin menu");
    }

    @Test
    @DisplayName("3. Negative test: LIBRARIAN cannot enter Admin console menu")
    void testLibrarianCannotAccessAdminConsoleMenu() throws Exception {
        String adminOutput = runConsoleAction(Admin::admin, librarianUser, "29\n");
        assertTrue(adminOutput.contains("Please log in as Admin"), "Librarian must be denied entry to Admin menu");
    }

    @Test
    @DisplayName("4. Negative test: MEMBER cannot execute Librarian or Admin service operations")
    void testMemberServiceLevelAuthorizationRestricted() {
        // Librarian operations
        Book newBook = new Book("978-0000000001", "Unauthorized Book", "Press", 2020, "Fiction");
        assertFalse(bookService.addBook(newBook, 1, memberUser));
        assertFalse(bookService.deleteBook(sampleBook.getBookId(), memberUser));
        assertFalse(bookTransactionService.assignCopy(1, 1, memberUser));
        assertFalse(bookTransactionService.markReadyForPickup(1, memberUser));
        assertFalse(bookTransactionService.issueBook(1, memberUser));
        assertFalse(bookTransactionService.returnBook(1, memberUser));
        assertFalse(bookTransactionService.markCopyLost(1, memberUser));
        assertFalse(bookTransactionService.markCopyDamaged(1, memberUser));
        assertFalse(fineService.waiveFine(1, memberUser));
        assertTrue(fineService.getAllFines(memberUser).isEmpty());
        assertTrue(fineService.getFinesByStatus(FineStatus.PENDING, memberUser).isEmpty());
        assertFalse(paymentService.refund(1, memberUser));
        assertFalse(membershipService.suspendMembership(memberUser, "9111111111"));
        assertFalse(membershipService.reactivateMembership(memberUser, "9111111111"));
        assertTrue(policyService.getDefaulters(memberUser).isEmpty());

        // Admin operations
        assertFalse(userService.addLibrarian(memberUser, "Test", "t@test.com", "9888888888", "Pass1234"));
        assertFalse(userService.addAdmin(memberUser, "Test", "t@test.com", "9888888888", "Pass1234"));
        assertFalse(userService.changeUserRole(memberUser, librarianUser.getUserId(), UserRole.ADMIN));
        assertFalse(userService.activateAccount(memberUser, memberUser.getUserId()));
        assertFalse(userService.deactivateAccount(memberUser, librarianUser.getUserId()));
        assertFalse(userService.deleteUser(memberUser, librarianUser.getUserId()));
        assertTrue(userService.getAllUsers(memberUser).isEmpty());
        assertNull(membershipPlanService.createPlan(memberUser, "Gold Plan", 999.0));
        assertFalse(policyService.updateMaxBooksBorrowed(memberUser, 10));
        assertFalse(policyService.updateLoanPeriodDays(memberUser, 20));
        assertFalse(policyService.updateFinePerDay(memberUser, 15.0));
        assertFalse(policyService.updateLostBookCharge(memberUser, 500.0));
        assertFalse(policyService.updateDamagedBookCharge(memberUser, 250.0));
    }

    @Test
    @DisplayName("5. Negative test: LIBRARIAN cannot execute Admin service operations")
    void testLibrarianServiceLevelAdminAuthorizationRestricted() {
        assertFalse(userService.addLibrarian(librarianUser, "Staff2", "s2@test.com", "9777777777", "Pass1234"));
        assertFalse(userService.addAdmin(librarianUser, "Staff3", "s3@test.com", "9777777778", "Pass1234"));
        assertFalse(userService.changeUserRole(librarianUser, memberUser.getUserId(), UserRole.LIBRARIAN));
        assertFalse(userService.activateAccount(librarianUser, memberUser.getUserId()));
        assertFalse(userService.deactivateAccount(librarianUser, memberUser.getUserId()));
        assertFalse(userService.deleteUser(librarianUser, memberUser.getUserId()));
        assertTrue(userService.getAllUsers(librarianUser).isEmpty());
        assertNull(membershipPlanService.createPlan(librarianUser, "Platinum Plan", 1999.0));
        assertFalse(policyService.updateMaxBooksBorrowed(librarianUser, 10));
        assertFalse(policyService.updateLoanPeriodDays(librarianUser, 20));
        assertFalse(policyService.updateFinePerDay(librarianUser, 15.0));
        assertFalse(policyService.updateLostBookCharge(librarianUser, 500.0));
        assertFalse(policyService.updateDamagedBookCharge(librarianUser, 250.0));
    }

    @Test
    @DisplayName("6. Positive test: LIBRARIAN can use Member functionality under own account")
    void testLibrarianCanExecuteAllMemberOperations() {
        // Librarian activates membership
        boolean memSuccess = membershipService.getMembershipByPhone(librarianUser.getPhone(), standardPlan.getPlanId(), 1, PaymentMethod.UPI, librarianUser);
        assertTrue(memSuccess, "Librarian must be able to purchase membership");
        assertNotNull(membershipService.getActiveMembership(librarianUser.getPhone(), librarianUser));

        // Librarian requests book
        Transaction req = bookTransactionService.requestBookByIsbn(librarianUser.getPhone(), sampleBook.getIsbn(), librarianUser);
        assertNotNull(req, "Librarian must be able to request book");
        assertEquals(TransactionStatus.REQUESTED, req.getStatus());

        // Librarian cancels request
        boolean cancelSuccess = bookTransactionService.cancelRequest(librarianUser.getPhone(), sampleBook.getIsbn(), librarianUser);
        assertTrue(cancelSuccess, "Librarian must be able to cancel own book request");

        // Librarian requests again and is issued a book copy
        Transaction req2 = bookTransactionService.requestBookByIsbn(librarianUser.getPhone(), sampleBook.getIsbn(), librarianUser);
        assertNotNull(req2);
        List<BookCopy> copies = bookCopyRepository.findByBookId(sampleBook.getBookId());
        assertFalse(copies.isEmpty());
        int copyId = copies.get(0).getBookCopyId();

        assertTrue(bookTransactionService.assignCopy(req2.getTransactionId(), copyId, adminUser));
        assertTrue(bookTransactionService.markReadyForPickup(req2.getTransactionId(), adminUser));
        assertTrue(bookTransactionService.issueBook(req2.getTransactionId(), adminUser));

        // Librarian views currently borrowed books and borrowing history
        List<Transaction> borrowed = bookTransactionService.getCurrentlyBorrowedBooks(librarianUser.getPhone(), librarianUser);
        assertEquals(1, borrowed.size(), "Librarian must see own borrowed books");
        List<Transaction> history = bookTransactionService.getBorrowingHistory(librarianUser.getPhone(), librarianUser);
        assertFalse(history.isEmpty(), "Librarian must see own borrowing history");

        // Librarian renews own book
        assertTrue(bookTransactionService.renewBook(req2.getTransactionId(), librarianUser), "Librarian must be able to renew own book loan");

        // Librarian profile update
        assertTrue(userService.updateNameByPhone("Leo Updated", librarianUser.getPhone(), librarianUser));
        assertEquals("Leo Updated", userRepository.findByPhone(librarianUser.getPhone()).getName());
    }

    @Test
    @DisplayName("7. Positive test: ADMIN can execute Admin, Librarian, and Member operations")
    void testAdminCanExecuteAllLibrarianAndMemberOperations() {
        // Admin operations
        assertTrue(userService.addLibrarian(adminUser, "New Lib", "newlib@test.com", "9666666666", "SecurePass123"));
        assertNotNull(membershipPlanService.createPlan(adminUser, "VIP Plan", 499.0));
        assertTrue(policyService.updateMaxBooksBorrowed(adminUser, 7));

        // Librarian operations executed by Admin
        Book adminBook = new Book("978-0201633610", "Design Patterns", "Addison-Wesley", 1994, "Technical");
        adminBook.addAuthor(new Author(2, "Erich Gamma"));
        assertTrue(bookService.addBook(adminBook, 1, adminUser), "Admin must be able to add books");
        assertNotNull(policyService.getDefaulters(adminUser), "Admin must be able to view defaulters");

        // Member operations executed by Admin under own account
        assertTrue(membershipService.getMembershipByPhone(adminUser.getPhone(), standardPlan.getPlanId(), 1, PaymentMethod.CARD, adminUser));
        Transaction adminReq = bookTransactionService.requestBookByIsbn(adminUser.getPhone(), adminBook.getIsbn(), adminUser);
        assertNotNull(adminReq, "Admin must be able to request books as a member");
        assertTrue(bookTransactionService.cancelRequest(adminUser.getPhone(), adminBook.getIsbn(), adminUser));
        assertTrue(userService.updateNameByPhone("Ada Updated", adminUser.getPhone(), adminUser));
        assertEquals("Ada Updated", userRepository.findByPhone(adminUser.getPhone()).getName());
    }

    @Test
    @DisplayName("8. Console verification: Flat Librarian menu has all 25 numbered options and no submenus")
    void testLibrarianConsoleMenuIsFlatAndContainsAll25Options() throws Exception {
        String input = "25\n";
        String output = runConsoleAction(Librarian::librarian, librarianUser, input);

        // Member functions (1-11)
        assertTrue(output.contains("1.View All Books"));
        assertTrue(output.contains("2.Search Books"));
        assertTrue(output.contains("3.Request Book"));
        assertTrue(output.contains("4.Cancel Request"));
        assertTrue(output.contains("5.Renew Book"));
        assertTrue(output.contains("6.Activate Membership"));
        assertTrue(output.contains("7.Renew Membership"));
        assertTrue(output.contains("8.View Currently Rented Books"));
        assertTrue(output.contains("9.View Borrowing History"));
        assertTrue(output.contains("10.Manage Fines"));
        assertTrue(output.contains("11.Update Profile"));

        // Librarian-specific functions (12-24)
        assertTrue(output.contains("12.Add Book"));
        assertTrue(output.contains("13.Remove Book"));
        assertTrue(output.contains("14.View Pending Requests"));
        assertTrue(output.contains("15.Mark Ready for Pickup"));
        assertTrue(output.contains("16.Issue Book"));
        assertTrue(output.contains("17.Process Return"));
        assertTrue(output.contains("18.Mark Lost"));
        assertTrue(output.contains("19.Mark Damaged"));
        assertTrue(output.contains("20.View Defaulters"));
        assertTrue(output.contains("21.View Current Fines"));
        assertTrue(output.contains("22.Manage Current Fines"));
        assertTrue(output.contains("23.Manage Membership Status"));
        assertTrue(output.contains("24.Manage Payments"));

        // Logout (25)
        assertTrue(output.contains("25.Logout"));

        // Confirm NO submenus
        assertFalse(output.contains("Member Operations"), "Librarian menu must not contain Member Operations submenu");
        assertFalse(output.contains("Librarian Operations"), "Librarian menu must not contain Librarian Operations submenu");
        assertTrue(output.contains("Logged out"), "Librarian must log out cleanly on option 25");
    }

    @Test
    @DisplayName("9. Console verification: Flat Admin menu has all 29 numbered options and no submenus")
    void testAdminConsoleMenuIsFlatAndContainsAll29Options() throws Exception {
        String input = "29\n";
        String output = runConsoleAction(Admin::admin, adminUser, input);

        // Member functions (1-11)
        assertTrue(output.contains("1.View All Books"));
        assertTrue(output.contains("2.Search Books"));
        assertTrue(output.contains("3.Request Book"));
        assertTrue(output.contains("4.Cancel Request"));
        assertTrue(output.contains("5.Renew Book"));
        assertTrue(output.contains("6.Activate Membership"));
        assertTrue(output.contains("7.Renew Membership"));
        assertTrue(output.contains("8.View Currently Rented Books"));
        assertTrue(output.contains("9.View Borrowing History"));
        assertTrue(output.contains("10.Manage Fines"));
        assertTrue(output.contains("11.Update Profile"));

        // Librarian functions (12-24)
        assertTrue(output.contains("12.Add Book"));
        assertTrue(output.contains("13.Remove Book"));
        assertTrue(output.contains("14.View Pending Requests"));
        assertTrue(output.contains("15.Mark Ready for Pickup"));
        assertTrue(output.contains("16.Issue Book"));
        assertTrue(output.contains("17.Process Return"));
        assertTrue(output.contains("18.Mark Lost"));
        assertTrue(output.contains("19.Mark Damaged"));
        assertTrue(output.contains("20.View Defaulters"));
        assertTrue(output.contains("21.View Current Fines"));
        assertTrue(output.contains("22.Manage Current Fines"));
        assertTrue(output.contains("23.Manage Membership Status"));
        assertTrue(output.contains("24.Manage Payments"));

        // Admin functions (25-28)
        assertTrue(output.contains("25.Dashboard"));
        assertTrue(output.contains("26.Manage Users"));
        assertTrue(output.contains("27.Manage Policies"));
        assertTrue(output.contains("28.Manage Membership Plans"));

        // Logout (29)
        assertTrue(output.contains("29.Logout"));

        // Confirm NO submenus
        assertFalse(output.contains("Member Operations"), "Admin menu must not contain Member Operations submenu");
        assertFalse(output.contains("Librarian Operations"), "Admin menu must not contain Librarian Operations submenu");
        assertTrue(output.contains("Logged out"), "Admin must log out cleanly on option 29");
    }

    @Test
    @DisplayName("10. Console verification: Member menu has 12 options and executes cleanly")
    void testMemberConsoleMenuContainsAll12Options() throws Exception {
        String input = "1\n6\n12\n";
        String output = runConsoleAction(Member::memberMenu, memberUser, input);

        assertTrue(output.contains("1.View All Books"));
        assertTrue(output.contains("2.Search Books"));
        assertTrue(output.contains("3.Request Book"));
        assertTrue(output.contains("4.Cancel Request"));
        assertTrue(output.contains("5.Renew Book"));
        assertTrue(output.contains("6.Activate Membership"));
        assertTrue(output.contains("7.Renew Membership"));
        assertTrue(output.contains("8.View Currently Rented Books"));
        assertTrue(output.contains("9.View Borrowing History"));
        assertTrue(output.contains("10.Manage Fines"));
        assertTrue(output.contains("11.Update Profile"));
        assertTrue(output.contains("12.Logout"));
        assertTrue(output.contains("Clean Code"));
        assertTrue(output.contains("Logged out successfully"));
    }

    @Test
    @DisplayName("11. Self-service under Member, Librarian, and Admin uses currentUser without asking for phone")
    void testSelfServiceUsesCurrentUserWithoutPromptingForPhone() throws Exception {
        // Under Member: option 8 (View Currently Rented Books) -> returns without asking for phone
        String memberOut = runConsoleAction(Member::memberMenu, memberUser, "8\n12\n");
        assertFalse(memberOut.contains("Enter member phone"), "Self-service must not ask for phone");
        assertTrue(memberOut.contains("You have no currently issued books") || memberOut.contains("ISBN:"));

        // Under Librarian: option 8 (View Currently Rented Books) -> uses librarian's account
        String libOut = runConsoleAction(Librarian::librarian, librarianUser, "8\n25\n");
        assertFalse(libOut.contains("Enter member phone"), "Librarian self-service must not ask for phone");
        assertTrue(libOut.contains("You have no currently issued books") || libOut.contains("ISBN:"));

        // Under Admin: option 8 (View Currently Rented Books) -> uses admin's account
        String adminOut = runConsoleAction(Admin::admin, adminUser, "8\n29\n");
        assertFalse(adminOut.contains("Enter member phone"), "Admin self-service must not ask for phone");
        assertTrue(adminOut.contains("You have no currently issued books") || adminOut.contains("ISBN:"));
    }

    @Test
    @DisplayName("12. Staff operations on another member prompt for target identifiers")
    void testStaffOperationsOnAnotherMemberPromptForTargetIdentifiers() throws Exception {
        // Option 20 (View Defaulters) and Option 23 (Manage Membership Status prompts for phone)
        // We supply target member's phone "9111111111", then 1 (Suspend), then 25 (Logout)
        String input = "23\n9111111111\n1\n25\n";
        String output = runConsoleAction(Librarian::librarian, librarianUser, input);

        assertTrue(output.contains("Enter member phone number"), "Staff operation must prompt for target member phone");
        assertTrue(output.contains("Unable to update membership status") || output.contains("Membership status updated"));
    }

    @Test
    @DisplayName("13. Invalid identifiers in staff operations are handled safely")
    void testInvalidIdentifiersInStaffOperationsHandledSafely() throws Exception {
        // Option 23 with non-existent phone "0000000000", then 25 (Logout)
        String input = "23\n0000000000\n25\n";
        String output = runConsoleAction(Librarian::librarian, librarianUser, input);

        assertTrue(output.contains("Member not found"), "Must inform when target member is not found");
        assertTrue(output.contains("Logged out"));
    }

    @Test
    @DisplayName("14. Session retention across operations until explicit Logout")
    void testSessionRetentionAcrossOperationsUntilExplicitLogout() throws Exception {
        // Under Librarian: execute View Books (1, then 6 to exit book view), View Defaulters (20), then Logout (25)
        String input = "1\n6\n20\n25\n";
        runConsoleAction(Librarian::librarian, librarianUser, input);
        assertNull(getStaticField(Main.class, "currentUser"), "currentUser should be cleared only after explicit Logout");

        // Under Admin: execute View Books (1, then 6), Dashboard (25, then year 2026), then Logout (29)
        String adminInput = "1\n6\n25\n2026\n29\n";
        runConsoleAction(Admin::admin, adminUser, adminInput);
        assertNull(getStaticField(Main.class, "currentUser"), "currentUser should be cleared only after explicit Logout");
    }

    private String runMain(String input) throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        System.setIn(in);
        setStaticField(Main.class, "sc", new Scanner(System.in));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));

        try {
            Main.main(new String[0]);
        } finally {
            System.setOut(originalOut);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("15. Main login routes Member to Member menu and terminates session on explicit Logout")
    void testMainLoginRoutesMemberToMemberMenu() throws Exception {
        // Option 1 (Member) -> 1 (Login) -> phone -> pass -> 12 (Logout) -> 4 (Exit)
        String input = "1\n1\n9111111111\nMemberPass123\n12\n4\n";
        String out = runMain(input);

        assertTrue(out.contains("Select User"));
        assertTrue(out.contains("1.Member"));
        assertTrue(out.contains("Login Success"));
        assertTrue(out.contains("1.View All Books"));
        assertTrue(out.contains("12.Logout"));
        assertTrue(out.contains("Logged out successfully"));
        assertTrue(out.contains("Exiting Library Management System"));
        assertNull(getStaticField(Main.class, "currentUser"));
    }

    @Test
    @DisplayName("16. Main login routes Librarian to Librarian menu and terminates session on explicit Logout")
    void testMainLoginRoutesLibrarianToLibrarianMenu() throws Exception {
        // Option 2 (Librarian) -> phone -> pass -> 25 (Logout) -> 4 (Exit)
        String input = "2\n9222222222\nLibPass123\n25\n4\n";
        String out = runMain(input);

        assertTrue(out.contains("Login successful"));
        assertTrue(out.contains("Librarian Menu"));
        assertTrue(out.contains("25.Logout"));
        assertTrue(out.contains("Logged out"));
        assertTrue(out.contains("Exiting Library Management System"));
        assertNull(getStaticField(Main.class, "currentUser"));
    }

    @Test
    @DisplayName("17. Main login routes Admin logging in under Librarian option directly to Admin menu")
    void testMainLoginRoutesAdminUnderLibrarianOptionToAdminMenu() throws Exception {
        // Option 2 (Librarian) with Admin credentials -> routes to Admin menu -> 29 (Logout) -> 4 (Exit)
        String input = "2\n9333333333\nAdminPass123\n29\n4\n";
        String out = runMain(input);

        assertTrue(out.contains("Login successful"));
        assertTrue(out.contains("Admin Menu"));
        assertTrue(out.contains("29.Logout"));
        assertTrue(out.contains("Logged out"));
        assertTrue(out.contains("Exiting Library Management System"));
        assertNull(getStaticField(Main.class, "currentUser"));
    }

    @Test
    @DisplayName("18. Main login routes Admin to Admin menu and terminates session on explicit Logout")
    void testMainLoginRoutesAdminToAdminMenu() throws Exception {
        // Option 3 (Admin) with Admin credentials -> 29 (Logout) -> 4 (Exit)
        String input = "3\n9333333333\nAdminPass123\n29\n4\n";
        String out = runMain(input);

        assertTrue(out.contains("Login successful"));
        assertTrue(out.contains("Admin Menu"));
        assertTrue(out.contains("29.Logout"));
        assertTrue(out.contains("Logged out"));
        assertTrue(out.contains("Exiting Library Management System"));
        assertNull(getStaticField(Main.class, "currentUser"));
    }

    @Test
    @DisplayName("19. Main login rejects Member attempting staff login with specific guidance")
    void testMainLoginRejectsMemberAttemptingStaffLogin() throws Exception {
        // Option 2 with Member credentials -> rejected -> 4 (Exit)
        String input = "2\n9111111111\nMemberPass123\n4\n";
        String out = runMain(input);

        assertTrue(out.contains("Login Failed: This account is registered as MEMBER. Please log in via the Member menu."));
        assertNull(getStaticField(Main.class, "currentUser"));
    }

    @Test
    @DisplayName("20. Main login rejects Librarian attempting Admin login with administrator privilege requirement")
    void testMainLoginRejectsLibrarianAttemptingAdminLogin() throws Exception {
        // Option 3 with Librarian credentials -> rejected -> 4 (Exit)
        String input = "3\n9222222222\nLibPass123\n4\n";
        String out = runMain(input);

        assertTrue(out.contains("Login Failed: This account is registered as LIBRARIAN. Administrator privileges required."));
        assertNull(getStaticField(Main.class, "currentUser"));
    }

    @Test
    @DisplayName("21. Main login rejects deactivated staff accounts with clear deactivation message")
    void testMainLoginRejectsDeactivatedStaffAccounts() throws Exception {
        User inactiveStaff = new User("Inactive Staff", "inactive@library.com", "9999888877",
                AuthService.hashPassword("InactivePass123"), UserRole.LIBRARIAN, UserStatus.INACTIVE);
        userRepository.save(inactiveStaff);

        // Option 2 with inactive staff credentials -> rejected -> 4 (Exit)
        String input = "2\n9999888877\nInactivePass123\n4\n";
        String out = runMain(input);

        assertTrue(out.contains("Login Failed: Your account has been deactivated. Please contact the administrator."));
        assertNull(getStaticField(Main.class, "currentUser"));
    }
}
