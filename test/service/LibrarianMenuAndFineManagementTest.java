package service;

import enums.FineStatus;
import enums.FineType;
import enums.PaymentMethod;
import enums.PaymentStatus;
import enums.UserRole;
import enums.UserStatus;
import model.Book;
import model.Fine;
import model.Payment;
import model.Transaction;
import model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import paymentStrategy.Razorpay;
import repository.*;
import ui.Librarian;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

public class LibrarianMenuAndFineManagementTest {

    private DataStore dataStore;
    private FineRepository fineRepository;
    private TransactionRepository transactionRepository;
    private UserRepository userRepository;
    private PaymentRepository paymentRepository;
    private AuditLogRepository auditLogRepository;
    private MembershipRepository membershipRepository;
    private AuditService auditService;
    private PolicyService policyService;
    private FineService fineService;
    private PaymentService paymentService;

    private User staffLibrarian;
    private User regularMember;
    private User otherMember;

    @BeforeEach
    void setUp() {
        dataStore = new DataStore();
        userRepository = new InMemoryUserRepository(dataStore);
        fineRepository = new InMemoryFineRepository(dataStore);
        transactionRepository = new InMemoryTransactionRepository(dataStore);
        paymentRepository = new InMemoryPaymentRepository(dataStore);
        auditLogRepository = new InMemoryAuditLogRepository(dataStore);
        membershipRepository = new InMemoryMembershipRepository(dataStore);

        auditService = new AuditService(auditLogRepository);
        policyService = new PolicyService(userRepository, membershipRepository, transactionRepository, fineRepository, auditService);
        fineService = new FineService(fineRepository, transactionRepository, policyService, auditService);
        paymentService = new PaymentService(paymentRepository, fineRepository, new Razorpay(), auditService, membershipRepository, transactionRepository);

        staffLibrarian = new User("Staff Librarian", "lib@test.com", "9999999999", "libpass", UserRole.LIBRARIAN, UserStatus.ACTIVE);
        userRepository.save(staffLibrarian);

        regularMember = new User("Alice Member", "alice@test.com", "9876543210", "pass123", UserRole.MEMBER, UserStatus.ACTIVE);
        userRepository.save(regularMember);

        otherMember = new User("Bob Member", "bob@test.com", "8888888888", "pass456", UserRole.MEMBER, UserStatus.ACTIVE);
        userRepository.save(otherMember);
    }

    @Test
    @DisplayName("1. Librarian menu has unique options, no duplicate labels, and correct option numbering")
    void testLibrarianMenuHasUniqueLabelsAndNoDuplicates() throws Exception {
        // Set up currentUser in Main via reflection
        Class<?> mainClass = Class.forName("ui.Main");
        Field currentUserField = mainClass.getDeclaredField("currentUser");
        currentUserField.setAccessible(true);
        currentUserField.set(null, staffLibrarian);

        // Feed input "25\n" to select Logout immediately
        ByteArrayInputStream in = new ByteArrayInputStream("25\n".getBytes());
        System.setIn(in);

        // Reset scanner in Main with the new input
        Field scField = mainClass.getDeclaredField("sc");
        scField.setAccessible(true);
        scField.set(null, new java.util.Scanner(System.in));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(out));

        try {
            Librarian.librarian();
        } finally {
            System.setOut(originalOut);
        }

        String output = out.toString();

        // Verify correct numbered menu options
        assertTrue(output.contains("1.View All Books"), "Menu must contain 1.View All Books");
        assertTrue(output.contains("2.Search Books"), "Menu must contain 2.Search Books");
        assertTrue(output.contains("3.Request Book"), "Menu must contain 3.Request Book");
        assertTrue(output.contains("4.Cancel Request"), "Menu must contain 4.Cancel Request");
        assertTrue(output.contains("5.Renew Book"), "Menu must contain 5.Renew Book");
        assertTrue(output.contains("6.Activate Membership"), "Menu must contain 6.Activate Membership");
        assertTrue(output.contains("7.Renew Membership"), "Menu must contain 7.Renew Membership");
        assertTrue(output.contains("8.View Currently Rented Books"), "Menu must contain 8.View Currently Rented Books");
        assertTrue(output.contains("9.View Borrowing History"), "Menu must contain 9.View Borrowing History");
        assertTrue(output.contains("10.Manage Fines"), "Menu must contain 10.Manage Fines");
        assertTrue(output.contains("11.Update Profile"), "Menu must contain 11.Update Profile");
        assertTrue(output.contains("12.Add Book"), "Menu must contain 12.Add Book");
        assertTrue(output.contains("13.Remove Book"), "Menu must contain 13.Remove Book");
        assertTrue(output.contains("14.View Pending Requests"), "Menu must contain 14.View Pending Requests");
        assertTrue(output.contains("15.Mark Ready for Pickup"), "Menu must contain 15.Mark Ready for Pickup");
        assertTrue(output.contains("16.Issue Book"), "Menu must contain 16.Issue Book");
        assertTrue(output.contains("17.Process Return"), "Menu must contain 17.Process Return");
        assertTrue(output.contains("18.Mark Lost"), "Menu must contain 18.Mark Lost");
        assertTrue(output.contains("19.Mark Damaged"), "Menu must contain 19.Mark Damaged");
        assertTrue(output.contains("20.View Defaulters"), "Menu must contain 20.View Defaulters");
        assertTrue(output.contains("21.View Current Fines"), "Menu must contain 21.View Current Fines");
        assertTrue(output.contains("22.Manage Current Fines"), "Menu must contain 22.Manage Current Fines");
        assertTrue(output.contains("23.Manage Membership Status"), "Menu must contain 23.Manage Membership Status");
        assertTrue(output.contains("24.Manage Payments"), "Menu must contain 24.Manage Payments");
        assertTrue(output.contains("25.Logout"), "Menu must contain 25.Logout");

        // Ensure "14.View Pending Requests" appears only once (no duplicate)
        int firstIndex = output.indexOf("14.View Pending Requests");
        int secondIndex = output.indexOf("14.View Pending Requests", firstIndex + 1);
        assertEquals(-1, secondIndex, "Menu must not contain duplicate 14.View Pending Requests");
    }

    @Test
    @DisplayName("2. Viewing all fines and filtering by fine status")
    void testViewAllFinesAndFilterByStatus() {
        Fine fPending = new Fine(1, regularMember.getPhone(), 101, 50.0, FineType.OVERDUE);
        Fine fPartiallyPaid = new Fine(2, regularMember.getPhone(), 102, 100.0, FineType.LOST_BOOK);
        fPartiallyPaid.applyPayment(30.0);
        Fine fPaid = new Fine(3, otherMember.getPhone(), 103, 20.0, FineType.OVERDUE);
        fPaid.markPaid();
        Fine fWaived = new Fine(4, otherMember.getPhone(), 104, 80.0, FineType.DAMAGED_BOOK);
        fWaived.waive();

        fineRepository.save(fPending);
        fineRepository.save(fPartiallyPaid);
        fineRepository.save(fPaid);
        fineRepository.save(fWaived);

        // Staff views all fines
        List<Fine> all = fineService.getAllFines(staffLibrarian);
        assertEquals(4, all.size());

        // Filter PENDING
        List<Fine> pending = fineService.getFinesByStatus(FineStatus.PENDING, staffLibrarian);
        assertEquals(1, pending.size());
        assertEquals(1, pending.get(0).getFineId());

        // Filter PARTIALLY_PAID
        List<Fine> partial = fineService.getFinesByStatus(FineStatus.PARTIALLY_PAID, staffLibrarian);
        assertEquals(1, partial.size());
        assertEquals(2, partial.get(0).getFineId());

        // Filter PAID
        List<Fine> paid = fineService.getFinesByStatus(FineStatus.PAID, staffLibrarian);
        assertEquals(1, paid.size());
        assertEquals(3, paid.get(0).getFineId());

        // Filter WAIVED
        List<Fine> waived = fineService.getFinesByStatus(FineStatus.WAIVED, staffLibrarian);
        assertEquals(1, waived.size());
        assertEquals(4, waived.get(0).getFineId());
    }

    @Test
    @DisplayName("3. Empty fine lists handled safely")
    void testEmptyFineListsHandledSafely() {
        assertTrue(fineService.getAllFines(staffLibrarian).isEmpty());
        assertTrue(fineService.getFinesByStatus(FineStatus.PENDING, staffLibrarian).isEmpty());
        assertTrue(fineService.findByUserPhone("9876543210", staffLibrarian).isEmpty());
        assertTrue(fineService.findByTransactionId(999, staffLibrarian).isEmpty());
        assertNull(fineService.findById(999, staffLibrarian));
    }

    @Test
    @DisplayName("4. Viewing fine outstanding balance calculation")
    void testFineOutstandingBalanceCalculation() {
        Fine fine = new Fine(10, regularMember.getPhone(), 201, 150.0, FineType.OVERDUE);
        assertEquals(150.0, fine.getAmount(), 0.001);
        assertEquals(0.0, fine.getAmountPaid(), 0.001);
        assertEquals(150.0, fine.getRemainingAmount(), 0.001);
        assertEquals(FineStatus.PENDING, fine.getStatus());

        fine.applyPayment(50.0);
        assertEquals(50.0, fine.getAmountPaid(), 0.001);
        assertEquals(100.0, fine.getRemainingAmount(), 0.001);
        assertEquals(FineStatus.PARTIALLY_PAID, fine.getStatus());

        fine.applyPayment(100.0);
        assertEquals(150.0, fine.getAmountPaid(), 0.001);
        assertEquals(0.0, fine.getRemainingAmount(), 0.001);
        assertEquals(FineStatus.PAID, fine.getStatus());
    }

    @Test
    @DisplayName("5. Partial payment and full payment recording")
    void testPartialAndFullPayment() {
        Fine fine = new Fine(20, regularMember.getPhone(), 301, 60.0, FineType.OVERDUE);
        fineRepository.save(fine);

        // Partial payment 20.0
        Payment p1 = paymentService.payFine(regularMember.getPhone(), 20, 20.0, PaymentMethod.UPI);
        assertNotNull(p1);
        assertTrue(p1.isSuccessful());

        Fine updated = fineService.findById(20, staffLibrarian);
        assertEquals(20.0, updated.getAmountPaid(), 0.001);
        assertEquals(40.0, updated.getRemainingAmount(), 0.001);
        assertEquals(FineStatus.PARTIALLY_PAID, updated.getStatus());

        // Full remaining payment 40.0
        Payment p2 = paymentService.payFine(regularMember.getPhone(), 20, 40.0, PaymentMethod.CARD);
        assertNotNull(p2);
        assertTrue(p2.isSuccessful());

        Fine fullyPaid = fineService.findById(20, staffLibrarian);
        assertEquals(60.0, fullyPaid.getAmountPaid(), 0.001);
        assertEquals(0.0, fullyPaid.getRemainingAmount(), 0.001);
        assertEquals(FineStatus.PAID, fullyPaid.getStatus());
    }

    @Test
    @DisplayName("6. Invalid payment amounts rejected")
    void testInvalidPaymentAmountsRejected() {
        Fine fine = new Fine(30, regularMember.getPhone(), 401, 50.0, FineType.OVERDUE);
        fineRepository.save(fine);

        // Zero payment
        Payment pZero = paymentService.payFine(regularMember.getPhone(), 30, 0.0, PaymentMethod.UPI);
        assertNull(pZero);

        // Negative payment
        Payment pNeg = paymentService.payFine(regularMember.getPhone(), 30, -10.0, PaymentMethod.UPI);
        assertNull(pNeg);

        // Payment exceeding remaining balance
        Payment pExcess = paymentService.payFine(regularMember.getPhone(), 30, 50.01, PaymentMethod.UPI);
        assertNull(pExcess);

        Fine unchanged = fineService.findById(30, staffLibrarian);
        assertEquals(0.0, unchanged.getAmountPaid(), 0.001);
        assertEquals(50.0, unchanged.getRemainingAmount(), 0.001);
        assertEquals(FineStatus.PENDING, unchanged.getStatus());
    }

    @Test
    @DisplayName("7. Failed payment does not change fine to PAID")
    void testFailedPaymentDoesNotMarkFinePaid() {
        Fine fine = new Fine(40, regularMember.getPhone(), 501, 50.0, FineType.OVERDUE);
        fineRepository.save(fine);

        // Paying with wrong member phone
        Payment pWrongPhone = paymentService.payFine("1111111111", 40, 50.0, PaymentMethod.UPI);
        assertNull(pWrongPhone);

        Fine unchanged = fineService.findById(40, staffLibrarian);
        assertEquals(FineStatus.PENDING, unchanged.getStatus());
        assertEquals(50.0, unchanged.getRemainingAmount(), 0.001);
    }

    @Test
    @DisplayName("8. Fine waiver behavior")
    void testFineWaiverBehavior() {
        Fine fine = new Fine(50, regularMember.getPhone(), 601, 75.0, FineType.OVERDUE);
        fineRepository.save(fine);

        // Staff waives the fine
        boolean waived = fineService.waiveFine(50, staffLibrarian);
        assertTrue(waived);

        Fine updated = fineService.findById(50, staffLibrarian);
        assertEquals(FineStatus.WAIVED, updated.getStatus());
        assertEquals(0.0, updated.getRemainingAmount(), 0.001);

        // Cannot pay a waived fine
        Payment pWaived = paymentService.payFine(regularMember.getPhone(), 50, 20.0, PaymentMethod.UPI);
        assertNull(pWaived);

        // Cannot waive already waived fine again
        assertFalse(fineService.waiveFine(50, staffLibrarian));

        // Cannot waive already PAID fine
        Fine paidFine = new Fine(51, regularMember.getPhone(), 602, 30.0, FineType.OVERDUE);
        paidFine.markPaid();
        fineRepository.save(paidFine);
        assertFalse(fineService.waiveFine(51, staffLibrarian));
    }

    @Test
    @DisplayName("9. Unauthorized access prevented for non-staff callers")
    void testUnauthorizedAccessPrevented() {
        Fine fineAlice = new Fine(60, regularMember.getPhone(), 701, 40.0, FineType.OVERDUE);
        Fine fineBob = new Fine(61, otherMember.getPhone(), 702, 30.0, FineType.OVERDUE);
        fineRepository.save(fineAlice);
        fineRepository.save(fineBob);

        // Member calling staff-only fine retrieval gets empty list
        assertTrue(fineService.getAllFines(regularMember).isEmpty());
        assertTrue(fineService.getFinesByStatus(FineStatus.PENDING, regularMember).isEmpty());

        // Member cannot waive fines
        assertFalse(fineService.waiveFine(60, regularMember));

        // Member cannot view another member's fine by ID
        assertNull(fineService.findById(61, regularMember));

        // Member can view their own fine by ID
        Fine ownFine = fineService.findById(60, regularMember);
        assertNotNull(ownFine);
        assertEquals(60, ownFine.getFineId());
    }

    @Test
    @DisplayName("10. Filtering fines by member phone and transaction ID")
    void testFilterByMemberAndTransaction() {
        Fine fine1 = new Fine(71, regularMember.getPhone(), 801, 20.0, FineType.OVERDUE);
        Fine fine2 = new Fine(72, regularMember.getPhone(), 802, 30.0, FineType.LOST_BOOK);
        Fine fine3 = new Fine(73, otherMember.getPhone(), 803, 40.0, FineType.OVERDUE);

        fineRepository.save(fine1);
        fineRepository.save(fine2);
        fineRepository.save(fine3);

        // Filter by member phone
        List<Fine> aliceFines = fineService.findByUserPhone(regularMember.getPhone(), staffLibrarian);
        assertEquals(2, aliceFines.size());

        // Filter by transaction ID
        List<Fine> tx802Fines = fineService.findByTransactionId(802, staffLibrarian);
        assertEquals(1, tx802Fines.size());
        assertEquals(72, tx802Fines.get(0).getFineId());
    }

    @Test
    @DisplayName("11. Member fine management only retrieves own fines and prevents cross-member operations")
    void testMemberFineManagementAuthorization() {
        Fine aliceFine = new Fine(81, regularMember.getPhone(), 901, 35.0, FineType.OVERDUE);
        Fine bobFine = new Fine(82, otherMember.getPhone(), 902, 50.0, FineType.OVERDUE);
        fineRepository.save(aliceFine);
        fineRepository.save(bobFine);

        // Alice as caller retrieves only Alice's fines
        List<Fine> aliceHistory = fineService.getFineHistory(regularMember.getPhone(), regularMember);
        assertEquals(1, aliceHistory.size());
        assertEquals(81, aliceHistory.get(0).getFineId());

        List<Fine> aliceOutstanding = fineService.getOutstandingFines(regularMember.getPhone(), regularMember);
        assertEquals(1, aliceOutstanding.size());
        assertEquals(81, aliceOutstanding.get(0).getFineId());

        // Alice cannot retrieve Bob's fine history or outstanding fines
        assertTrue(fineService.getFineHistory(otherMember.getPhone(), regularMember).isEmpty());
        assertTrue(fineService.getOutstandingFines(otherMember.getPhone(), regularMember).isEmpty());

        // Alice cannot view Bob's fine by ID
        assertNull(fineService.findById(82, regularMember));

        // Alice can pay her own fine
        Payment alicePayment = paymentService.payFine(regularMember.getPhone(), 81, 15.0, PaymentMethod.UPI);
        assertNotNull(alicePayment);
        assertTrue(alicePayment.isSuccessful());
        assertEquals(20.0, fineRepository.findById(81).getRemainingAmount(), 0.001);

        // Alice cannot pay for Bob's fine
        Payment crossPayment = paymentService.payFine(regularMember.getPhone(), 82, 10.0, PaymentMethod.UPI);
        assertNull(crossPayment, "Member cannot pay another member's fine");
        assertEquals(50.0, fineRepository.findById(82).getRemainingAmount(), 0.001);
    }

    @Test
    @DisplayName("12. Member console flow displays only own fines and records payment")
    void testMemberFineManagementConsoleFlow() throws Exception {
        Class<?> mainClass = Class.forName("ui.Main");
        Field userRepoField = mainClass.getDeclaredField("userRepository");
        userRepoField.setAccessible(true);
        UserRepository mainUserRepo = (UserRepository) userRepoField.get(null);

        Field bookRepoField = mainClass.getDeclaredField("bookRepository");
        bookRepoField.setAccessible(true);
        BookRepository mainBookRepo = (BookRepository) bookRepoField.get(null);

        Field txRepoField = mainClass.getDeclaredField("transactionRepository");
        txRepoField.setAccessible(true);
        TransactionRepository mainTxRepo = (TransactionRepository) txRepoField.get(null);

        Field fineRepoField = mainClass.getDeclaredField("fineRepository");
        fineRepoField.setAccessible(true);
        FineRepository mainFineRepo = (FineRepository) fineRepoField.get(null);

        // Seed users with valid hashed passwords
        String testPassword = "memberPassword123";
        String hashed = AuthService.hashPassword(testPassword);
        User alice = new User("Alice Member", "alice.flow@test.com", "9123456780", hashed, UserRole.MEMBER, UserStatus.ACTIVE);
        User bob = new User("Bob Member", "bob.flow@test.com", "9123456781", hashed, UserRole.MEMBER, UserStatus.ACTIVE);
        mainUserRepo.save(alice);
        mainUserRepo.save(bob);

        // Seed books and transactions
        Book aliceBook = new Book("978-0134494166", "Clean Architecture", "Robert C. Martin", 2017, "Architecture");
        Book bobBook = new Book("978-0201633610", "Design Patterns", "Gang of Four", 1994, "Patterns");
        mainBookRepo.save(aliceBook);
        mainBookRepo.save(bobBook);

        Transaction aliceTx = new Transaction(9101, alice.getPhone(), aliceBook.getBookId());
        Transaction bobTx = new Transaction(9102, bob.getPhone(), bobBook.getBookId());
        mainTxRepo.save(aliceTx);
        mainTxRepo.save(bobTx);

        Fine aliceFine = new Fine(9101, alice.getPhone(), aliceTx.getTransactionId(), 40.0, FineType.OVERDUE);
        Fine bobFine = new Fine(9102, bob.getPhone(), bobTx.getTransactionId(), 60.0, FineType.OVERDUE);
        mainFineRepo.save(aliceFine);
        mainFineRepo.save(bobFine);

        // Simulate console input:
        // 1: Login -> phone -> password
        // 10: Manage Fines -> ISBN -> Amount -> Payment method (1: UPI)
        // 12: Logout
        String simulatedInput = "1\n" + alice.getPhone() + "\n" + testPassword + "\n10\n" + aliceBook.getIsbn() + "\n25.0\n1\n12\n";
        ByteArrayInputStream in = new ByteArrayInputStream(simulatedInput.getBytes());
        System.setIn(in);

        Field scField = mainClass.getDeclaredField("sc");
        scField.setAccessible(true);
        scField.set(null, new Scanner(System.in));

        Field currentUserField = mainClass.getDeclaredField("currentUser");
        currentUserField.setAccessible(true);
        currentUserField.set(null, null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(out));

        try {
            Method memberMethod = Class.forName("ui.Member").getDeclaredMethod("member");
            memberMethod.setAccessible(true);
            memberMethod.invoke(null);
        } finally {
            System.setOut(originalOut);
        }

        String output = out.toString();

        // Verify login success and own fine displayed
        assertTrue(output.contains("Login Success"), "Output must contain login success");
        assertTrue(output.contains("ISBN: 978-0134494166"), "Output must display Alice's book fine");
        assertTrue(output.contains("Amount: Rs.40.0"), "Output must display Alice's fine amount");

        // Verify Bob's fine is NEVER displayed to Alice
        assertFalse(output.contains("978-0201633610"), "Alice must NOT see Bob's book fine");
        assertFalse(output.contains(bob.getPhone()), "Alice must NOT see Bob's phone number");
        assertFalse(output.contains("Amount: Rs.60.0"), "Alice must NOT see Bob's fine amount");

        // Verify payment success and logout
        assertTrue(output.contains("Fine payment successful"), "Payment must succeed");
        assertTrue(output.contains("Logged out successfully"), "Logout must succeed");

        // Verify fine balances in repository
        Fine updatedAliceFine = mainFineRepo.findById(9101);
        assertEquals(25.0, updatedAliceFine.getAmountPaid(), 0.001);
        assertEquals(15.0, updatedAliceFine.getRemainingAmount(), 0.001);
        assertEquals(FineStatus.PARTIALLY_PAID, updatedAliceFine.getStatus());

        // Bob's fine remains intact
        Fine untouchedBobFine = mainFineRepo.findById(9102);
        assertEquals(0.0, untouchedBobFine.getAmountPaid(), 0.001);
        assertEquals(60.0, untouchedBobFine.getRemainingAmount(), 0.001);
        assertEquals(FineStatus.PENDING, untouchedBobFine.getStatus());

        // Current user must be reset to null on logout
        assertNull(currentUserField.get(null), "currentUser must be null after logout");
    }

    @Test
    @DisplayName("13. Librarian console flow for viewing and managing current fines")
    void testLibrarianFineManagementConsoleFlow() throws Exception {
        Class<?> mainClass = Class.forName("ui.Main");
        Field currentUserField = mainClass.getDeclaredField("currentUser");
        currentUserField.setAccessible(true);
        currentUserField.set(null, staffLibrarian);

        Field userRepoField = mainClass.getDeclaredField("userRepository");
        userRepoField.setAccessible(true);
        UserRepository mainUserRepo = (UserRepository) userRepoField.get(null);
        mainUserRepo.save(staffLibrarian);

        Field fineRepoField = mainClass.getDeclaredField("fineRepository");
        fineRepoField.setAccessible(true);
        FineRepository mainFineRepo = (FineRepository) fineRepoField.get(null);

        Fine testFine = new Fine(9201, regularMember.getPhone(), 9901, 30.0, FineType.OVERDUE);
        mainFineRepo.save(testFine);

        // Input:
        // 21: View Current Fines -> 1: All Fines -> 8: Back
        // 22: Manage Current Fines -> phone -> 1: select fine 1 -> 3: Back -> 0: Back
        // 25: Logout
        String simulatedInput = "21\n1\n8\n22\n" + regularMember.getPhone() + "\n1\n3\n0\n25\n";
        ByteArrayInputStream in = new ByteArrayInputStream(simulatedInput.getBytes());
        System.setIn(in);

        Field scField = mainClass.getDeclaredField("sc");
        scField.setAccessible(true);
        scField.set(null, new Scanner(System.in));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(out));

        try {
            Librarian.librarian();
        } finally {
            System.setOut(originalOut);
        }

        String output = out.toString();

        // Verify options 11 and 12 executed properly
        assertTrue(output.contains("--- View Current Fines ---"), "Output must contain View Current Fines");
        assertTrue(output.contains("--- Manage Current Fines ---"), "Output must contain Manage Current Fines");
        assertTrue(output.contains("--- Fine Details ---"), "Output must contain Fine Details");
        assertTrue(output.contains("Fine ID: 9201"), "Output must display Fine ID 9201");
        assertTrue(output.contains("Logged out"), "Librarian must log out cleanly");

        // Verify currentUser reset
        assertNull(currentUserField.get(null), "currentUser must be null after librarian logout");
    }
}
