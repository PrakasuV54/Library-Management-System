package service;

import enums.UserRole;
import enums.UserStatus;
import model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import repository.*;
import ui.Main;
import ui.Member;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

public class RegistrationAndAuditRegressionTest {

    private DataStore dataStore;
    private UserRepository userRepository;
    private MembershipPlanRepository membershipPlanRepository;
    private BookRepository bookRepository;
    private BookCopyRepository bookCopyRepository;
    private TransactionRepository transactionRepository;
    private AuditLogRepository auditLogRepository;
    private AuditService auditService;
    private UserService userService;
    private AuthService authService;
    private BookService bookService;
    private SyntheticData syntheticData;

    @BeforeEach
    void setUp() throws Exception {
        dataStore = new DataStore();
        userRepository = new InMemoryUserRepository(dataStore);
        membershipPlanRepository = new InMemoryMembershipPlanRepository(dataStore);
        bookRepository = new InMemoryBookRepository(dataStore);
        bookCopyRepository = new InMemoryBookCopyRepository(dataStore);
        transactionRepository = new InMemoryTransactionRepository(dataStore);
        auditLogRepository = new InMemoryAuditLogRepository(dataStore);

        auditService = new AuditService(auditLogRepository);
        userService = new UserService(userRepository, auditService);
        authService = new AuthService(userService, userRepository, new authentication.PasswordAuthentication(), auditService);
        AuthorRepository authorRepository = new InMemoryAuthorRepository(dataStore);
        GenreRepository genreRepository = new InMemoryGenreRepository(dataStore);
        bookService = new BookService(bookRepository, bookCopyRepository, transactionRepository, auditService, authorRepository, genreRepository);

        syntheticData = new SyntheticData(userRepository, membershipPlanRepository, bookService);
        syntheticData.syntheticData();

        // Wire into Main static fields so Member UI uses our initialized repositories/services
        setStaticField(Main.class, "dataStore", dataStore);
        setStaticField(Main.class, "userRepository", userRepository);
        setStaticField(Main.class, "membershipPlanRepository", membershipPlanRepository);
        setStaticField(Main.class, "bookRepository", bookRepository);
        setStaticField(Main.class, "bookCopyRepository", bookCopyRepository);
        setStaticField(Main.class, "transactionRepository", transactionRepository);
        setStaticField(Main.class, "auditLogRepository", auditLogRepository);
        setStaticField(Main.class, "auditService", auditService);
        setStaticField(Main.class, "userService", userService);
        setStaticField(Main.class, "authService", authService);
        setStaticField(Main.class, "bookService", bookService);
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

    @Test
    @DisplayName("Registration prompts for name, phone, email, password and registers successfully")
    void testRegistrationPromptsAndSavesEmail() throws Exception {
        // Choice 2: Register
        // Name: David Miller
        // Phone: 9988771122
        // Email: david.miller@example.com
        // Password: Password123!
        // Confirm Password: Password123!
        String input = "2\nDavid Miller\n9988771122\ndavid.miller@example.com\nPassword123!\nPassword123!\n";
        String output = runMemberConsole(input);

        // Verify prompts appear in the exact required sequence
        int nameIndex = output.indexOf("Enter your name");
        int phoneIndex = output.indexOf("Enter your phone number");
        int emailIndex = output.indexOf("Enter your email");
        int passwordIndex = output.indexOf("Enter your password");
        int confirmIndex = output.indexOf("Enter your password again");
        int successIndex = output.indexOf("Registered successfully");

        assertTrue(nameIndex >= 0, "Must prompt for name");
        assertTrue(phoneIndex > nameIndex, "Must prompt for phone after name");
        assertTrue(emailIndex > phoneIndex, "Must prompt for email after phone");
        assertTrue(passwordIndex > emailIndex, "Must prompt for password after email");
        assertTrue(confirmIndex > passwordIndex, "Must prompt for confirm password after password");
        assertTrue(successIndex > confirmIndex, "Must display success message after confirmation");

        // Verify user was persisted with email in repository
        User registered = userRepository.findByPhone("9988771122");
        assertNotNull(registered, "Registered user must exist in repository");
        assertEquals("David Miller", registered.getName());
        assertEquals("david.miller@example.com", registered.getEmail());
        assertEquals("9988771122", registered.getPhone());
        assertEquals(UserRole.MEMBER, registered.getRole());
        assertEquals(UserStatus.ACTIVE, registered.getStatus());

        // Verify lookup by email works
        User byEmail = userRepository.findByEmail("david.miller@example.com");
        assertNotNull(byEmail, "User must be retrievable by email");
        assertEquals(registered.getUserId(), byEmail.getUserId());

        // Verify user can authenticate using both phone and email
        User authByPhone = authService.login("9988771122", "Password123!");
        assertNotNull(authByPhone, "User must be able to log in with phone");
        assertEquals(registered.getUserId(), authByPhone.getUserId());

        User authByEmail = authService.login("david.miller@example.com", "Password123!");
        assertNotNull(authByEmail, "User must be able to log in with email");
        assertEquals(registered.getUserId(), authByEmail.getUserId());
    }

    @Test
    @DisplayName("Registration rejects invalid email format")
    void testRegistrationRejectsInvalidEmail() throws Exception {
        String input = "2\nInvalid Email User\n9988771133\nnot-an-email\n";
        String output = runMemberConsole(input);

        assertTrue(output.contains("Enter your email"), "Must prompt for email");
        assertTrue(output.contains("Registration failed: Invalid email format"), "Must display invalid email error");

        assertNull(userRepository.findByPhone("9988771133"), "User with invalid email must not be saved");
    }

    @Test
    @DisplayName("Registration rejects duplicate email address")
    void testRegistrationRejectsDuplicateEmail() throws Exception {
        // First register David
        String input1 = "2\nDavid First\n9988771144\ndavid.unique@example.com\nPassword123!\nPassword123!\n";
        runMemberConsole(input1);
        assertNotNull(userRepository.findByPhone("9988771144"));

        // Attempt to register another user with different phone but same email
        String input2 = "2\nDavid Second\n9988771155\ndavid.unique@example.com\n";
        String output2 = runMemberConsole(input2);

        assertTrue(output2.contains("Enter your email"), "Must prompt for email");
        assertTrue(output2.contains("Registration failed: An account with email address david.unique@example.com already exists"),
                "Must display duplicate email error");

        assertNull(userRepository.findByPhone("9988771155"), "User with duplicate email must not be saved");
    }

    @Test
    @DisplayName("Registration rejects empty name")
    void testRegistrationRejectsEmptyName() throws Exception {
        String input = "2\n   \n";
        String output = runMemberConsole(input);

        assertTrue(output.contains("Registration failed: Name cannot be empty"), "Must reject empty name");
    }

    @Test
    @DisplayName("Registration rejects invalid phone number")
    void testRegistrationRejectsInvalidPhone() throws Exception {
        String input = "2\nValid Name\n12345\n";
        String output = runMemberConsole(input);

        assertTrue(output.contains("Registration failed: Phone number must be a valid 10-digit number"),
                "Must reject invalid phone number");
        assertNull(userRepository.findByPhone("12345"));
    }

    @Test
    @DisplayName("Registration rejects duplicate phone number")
    void testRegistrationRejectsDuplicatePhone() throws Exception {
        // Admin phone 9999999999 exists
        String input = "2\nAdmin Clone\n9999999999\n";
        String output = runMemberConsole(input);

        assertTrue(output.contains("Registration failed: An account with phone number 9999999999 already exists"),
                "Must reject duplicate phone number");
    }

    @Test
    @DisplayName("Registration rejects short password")
    void testRegistrationRejectsShortPassword() throws Exception {
        String input = "2\nShort Pass User\n9988771166\nshort.pass@example.com\nshort\n";
        String output = runMemberConsole(input);

        assertTrue(output.contains("Registration failed: Password must contain at least 8 characters"),
                "Must reject passwords with fewer than 8 characters");
        assertNull(userRepository.findByPhone("9988771166"));
    }

    @Test
    @DisplayName("Registration rejects password mismatch")
    void testRegistrationRejectsPasswordMismatch() throws Exception {
        String input = "2\nMismatch User\n9988771177\nmismatch@example.com\nPassword123!\nDifferent123!\n";
        String output = runMemberConsole(input);

        assertTrue(output.contains("Registration failed: Passwords do not match"),
                "Must reject mismatched passwords");
        assertNull(userRepository.findByPhone("9988771177"));
    }

    @Test
    @DisplayName("Registration preserves existing seed accounts and synthetic records")
    void testRegistrationPreservesExistingDataset() throws Exception {
        int initialCount = userRepository.findAll().size();
        assertTrue(initialCount >= 50, "Should have at least 50 users from synthetic data");

        String input = "2\nNew User\n9988771188\nnew.user@example.com\nPassword123!\nPassword123!\n";
        runMemberConsole(input);

        assertEquals(initialCount + 1, userRepository.findAll().size(), "User count should increment by 1");

        // Seed accounts still exist and can authenticate
        User admin = authService.login("9999999999", "admin123");
        assertNotNull(admin, "Seed admin must still be able to log in");
        assertEquals(UserRole.ADMIN, admin.getRole());

        User librarian = authService.login("8888888888", "lib123");
        assertNotNull(librarian, "Seed librarian must still be able to log in");
        assertEquals(UserRole.LIBRARIAN, librarian.getRole());
    }
}
