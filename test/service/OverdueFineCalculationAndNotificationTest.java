package service;

import enums.*;
import model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import repository.*;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OverdueFineCalculationAndNotificationTest {

    private DataStore dataStore;
    private UserRepository userRepository;
    private BookRepository bookRepository;
    private BookCopyRepository bookCopyRepository;
    private TransactionRepository transactionRepository;
    private MembershipRepository membershipRepository;
    private FineRepository fineRepository;
    private PolicyService policyService;
    private FineService fineService;
    private TestNotificationService notificationService;
    private OverdueFineSchedulerService schedulerService;

    private Book testBook;

    @BeforeEach
    void setUp() {
        dataStore = new DataStore();
        userRepository = new InMemoryUserRepository(dataStore);
        bookRepository = new InMemoryBookRepository(dataStore);
        bookCopyRepository = new InMemoryBookCopyRepository(dataStore);
        transactionRepository = new InMemoryTransactionRepository(dataStore);
        membershipRepository = new InMemoryMembershipRepository(dataStore);
        fineRepository = new InMemoryFineRepository(dataStore);

        policyService = new PolicyService(userRepository, membershipRepository, transactionRepository, fineRepository);
        fineService = new FineService(fineRepository, transactionRepository, policyService);
        notificationService = new TestNotificationService(userRepository);

        schedulerService = new OverdueFineSchedulerService(
                transactionRepository,
                bookRepository,
                fineService,
                notificationService
        );

        // Prepopulate a sample user and book
        User member = new User("Alice Member", "alice@example.com", "9876543210", "pass123", UserRole.MEMBER, UserStatus.ACTIVE);
        userRepository.save(member);

        testBook = new Book(1, "978-0132350884", "Clean Code", "Robert C. Martin", 2008, "Craftsmanship");
        bookRepository.save(testBook);
    }

    @AfterEach
    void tearDown() {
        if (schedulerService != null && schedulerService.isRunning()) {
            schedulerService.stopScheduler();
        }
    }

    private Transaction createTestTransaction(int id, String phone, int bookId, int copyId, TransactionStatus status, LocalDate dueDate) {
        BookCopy copy = new BookCopy(copyId, bookId);
        bookCopyRepository.save(copy);

        Transaction t = new Transaction(id, phone, bookId);
        try {
            Field statusField = Transaction.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(t, status);

            Field copyField = Transaction.class.getDeclaredField("bookCopyId");
            copyField.setAccessible(true);
            copyField.set(t, copyId);

            Field dueField = Transaction.class.getDeclaredField("dueDate");
            dueField.setAccessible(true);
            dueField.set(t, dueDate);

            Field issueField = Transaction.class.getDeclaredField("issueDate");
            issueField.setAccessible(true);
            issueField.set(t, LocalDateTime.now().minusDays(15));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        transactionRepository.save(t);
        return t;
    }

    @Test
    @DisplayName("Scenario 1: A transaction that is not overdue does not receive an overdue fine or notification")
    void testNotOverdueTransaction() {
        LocalDate today = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> today);

        // Due date tomorrow
        createTestTransaction(1, "9876543210", testBook.getBookId(), 101, TransactionStatus.ISSUED, today.plusDays(1));

        schedulerService.processOverdueTransactions();

        Fine fine = fineRepository.findByTransactionIdAndType(1, FineType.OVERDUE);
        assertNull(fine, "Non-overdue transaction must not receive an overdue fine");
        assertEquals(0, notificationService.getSent().size(), "Non-overdue transaction must not receive a notification");
    }

    @Test
    @DisplayName("Scenario 2: A transaction one day overdue accrues ₹10 according to established convention")
    void testOneDayOverdueAccrues10() {
        LocalDate today = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> today);

        // Due date yesterday (1 day overdue)
        createTestTransaction(2, "9876543210", testBook.getBookId(), 102, TransactionStatus.ISSUED, today.minusDays(1));

        schedulerService.processOverdueTransactions();

        Fine fine = fineRepository.findByTransactionIdAndType(2, FineType.OVERDUE);
        assertNotNull(fine, "Overdue transaction must receive a fine");
        assertEquals(10.0, fine.getAmount(), 0.001, "1 day overdue fine must be Rs. 10.0");
        assertEquals(10.0, fine.getRemainingAmount(), 0.001);
        assertEquals(FineStatus.PENDING, fine.getStatus());
        assertEquals(1, notificationService.getSent().size());
    }

    @Test
    @DisplayName("Scenario 3: A transaction three days overdue accrues ₹30 according to established convention")
    void testThreeDaysOverdueAccrues30() {
        LocalDate today = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> today);

        // Due date 3 days ago
        createTestTransaction(3, "9876543210", testBook.getBookId(), 103, TransactionStatus.ISSUED, today.minusDays(3));

        schedulerService.processOverdueTransactions();

        Fine fine = fineRepository.findByTransactionIdAndType(3, FineType.OVERDUE);
        assertNotNull(fine);
        assertEquals(30.0, fine.getAmount(), 0.001, "3 days overdue fine must be Rs. 30.0");
        assertEquals(30.0, fine.getRemainingAmount(), 0.001);
    }

    @Test
    @DisplayName("Scenario 4: Repeated scheduler executions do not duplicate fine amounts or charge the same overdue days twice")
    void testRepeatedExecutionsDoNotDuplicateFine() {
        LocalDate today = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> today);

        createTestTransaction(4, "9876543210", testBook.getBookId(), 104, TransactionStatus.ISSUED, today.minusDays(2));

        // First execution
        schedulerService.processOverdueTransactions();
        Fine fineFirst = fineRepository.findByTransactionIdAndType(4, FineType.OVERDUE);
        assertNotNull(fineFirst);
        assertEquals(20.0, fineFirst.getAmount(), 0.001);
        int fineCountFirst = fineRepository.findAll().size();

        // Second execution on same date
        schedulerService.processOverdueTransactions();
        Fine fineSecond = fineRepository.findByTransactionIdAndType(4, FineType.OVERDUE);
        assertEquals(20.0, fineSecond.getAmount(), 0.001);
        assertEquals(fineCountFirst, fineRepository.findAll().size(), "No duplicate fine records must be created");
        assertEquals(fineFirst.getFineId(), fineSecond.getFineId(), "Same fine record must be retained");
    }

    @Test
    @DisplayName("Scenario 5: An existing overdue fine is updated correctly rather than duplicated")
    void testExistingOverdueFineIsUpdatedRatherThanDuplicated() {
        LocalDate day1 = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> day1);

        createTestTransaction(5, "9876543210", testBook.getBookId(), 105, TransactionStatus.ISSUED, day1.minusDays(1));

        // Day 1: 1 day overdue -> Rs. 10
        schedulerService.processOverdueTransactions();
        Fine fineDay1 = fineRepository.findByTransactionIdAndType(5, FineType.OVERDUE);
        assertEquals(10.0, fineDay1.getAmount(), 0.001);
        int fineId = fineDay1.getFineId();

        // Advance date to Day 2: 2 days overdue -> Rs. 20
        LocalDate day2 = day1.plusDays(1);
        schedulerService.setDateSupplier(() -> day2);
        schedulerService.processOverdueTransactions();

        Fine fineDay2 = fineRepository.findByTransactionIdAndType(5, FineType.OVERDUE);
        assertEquals(fineId, fineDay2.getFineId(), "Fine ID must remain identical");
        assertEquals(20.0, fineDay2.getAmount(), 0.001, "Amount must be updated to Rs. 20");
        assertEquals(1, fineRepository.findAll().size(), "Only one fine record must exist");
    }

    @Test
    @DisplayName("Scenario 6: Existing partial payments are preserved, and outstanding balance is calculated correctly")
    void testPartialPaymentsPreserved() {
        LocalDate day1 = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> day1);

        createTestTransaction(6, "9876543210", testBook.getBookId(), 106, TransactionStatus.ISSUED, day1.minusDays(1));
        schedulerService.processOverdueTransactions();

        Fine fine = fineRepository.findByTransactionIdAndType(6, FineType.OVERDUE);
        assertEquals(10.0, fine.getAmount(), 0.001);

        // Member pays Rs. 5 partially
        fine.applyPayment(5.0);
        fineRepository.save(fine);
        assertEquals(FineStatus.PARTIALLY_PAID, fine.getStatus());
        assertEquals(5.0, fine.getRemainingAmount(), 0.001);

        // Day 2 arrives (now 2 days overdue -> total Rs. 20)
        LocalDate day2 = day1.plusDays(1);
        schedulerService.setDateSupplier(() -> day2);
        schedulerService.processOverdueTransactions();

        Fine updatedFine = fineRepository.findByTransactionIdAndType(6, FineType.OVERDUE);
        assertEquals(20.0, updatedFine.getAmount(), 0.001);
        assertEquals(5.0, updatedFine.getAmountPaid(), 0.001, "Payment history must be preserved");
        assertEquals(15.0, updatedFine.getRemainingAmount(), 0.001, "Outstanding balance must be 20 - 5 = 15");
        assertEquals(FineStatus.PARTIALLY_PAID, updatedFine.getStatus());
    }

    @Test
    @DisplayName("Scenario 7: Paid or waived fines are handled consistently with existing business rules")
    void testPaidAndWaivedFinesHandledConsistently() {
        LocalDate day1 = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> day1);

        createTestTransaction(7, "9876543210", testBook.getBookId(), 107, TransactionStatus.ISSUED, day1.minusDays(1));
        schedulerService.processOverdueTransactions();

        Fine fine = fineRepository.findByTransactionIdAndType(7, FineType.OVERDUE);
        fine.markPaid();
        fineRepository.save(fine);
        assertEquals(FineStatus.PAID, fine.getStatus());
        assertEquals(0.0, fine.getRemainingAmount(), 0.001);

        // Day 1 repeated run: remaining is 0, no notification
        int notifCount = notificationService.getSent().size();
        schedulerService.processOverdueTransactions();
        assertEquals(notifCount, notificationService.getSent().size(), "No notification when fine is paid in full on same day");

        // Waived fine test
        createTestTransaction(8, "9876543210", testBook.getBookId(), 108, TransactionStatus.ISSUED, day1.minusDays(2));
        schedulerService.processOverdueTransactions();
        Fine fineWaived = fineRepository.findByTransactionIdAndType(8, FineType.OVERDUE);
        fineWaived.waive();
        fineRepository.save(fineWaived);
        assertEquals(FineStatus.WAIVED, fineWaived.getStatus());
        assertEquals(0.0, fineWaived.getRemainingAmount(), 0.001);

        // Day 2 run: waived fine remains waived and does not accrue
        LocalDate day2 = day1.plusDays(1);
        schedulerService.setDateSupplier(() -> day2);
        schedulerService.processOverdueTransactions();

        Fine fineWaivedAfter = fineRepository.findByTransactionIdAndType(8, FineType.OVERDUE);
        assertEquals(FineStatus.WAIVED, fineWaivedAfter.getStatus(), "Waived status must be preserved");
        assertEquals(0.0, fineWaivedAfter.getRemainingAmount(), 0.001);
    }

    @Test
    @DisplayName("Scenario 8: A returned transaction stops accumulating overdue fines")
    void testReturnedTransactionStopsAccumulatingFines() {
        LocalDate day1 = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> day1);

        Transaction t = createTestTransaction(9, "9876543210", testBook.getBookId(), 109, TransactionStatus.ISSUED, day1.minusDays(2));
        schedulerService.processOverdueTransactions();

        Fine fineDay1 = fineRepository.findByTransactionIdAndType(9, FineType.OVERDUE);
        assertEquals(20.0, fineDay1.getAmount(), 0.001);

        // Book returned on day1
        try {
            Field statusField = Transaction.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(t, TransactionStatus.RETURNED);

            Field returnField = Transaction.class.getDeclaredField("returnDate");
            returnField.setAccessible(true);
            returnField.set(t, day1.atTime(12, 0));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        transactionRepository.save(t);

        // Advance to Day 5
        LocalDate day5 = day1.plusDays(4);
        schedulerService.setDateSupplier(() -> day5);
        schedulerService.processOverdueTransactions();

        Fine fineDay5 = fineRepository.findByTransactionIdAndType(9, FineType.OVERDUE);
        assertEquals(20.0, fineDay5.getAmount(), 0.001, "Returned book must stop accumulating overdue fines");
    }

    @Test
    @DisplayName("Scenario 9: Cancelled, expired, lost, and damaged transactions do not receive new overdue fines or notifications")
    void testInactiveTransactionsExcluded() {
        LocalDate today = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> today);

        createTestTransaction(11, "9876543210", testBook.getBookId(), 111, TransactionStatus.CANCELLED, today.minusDays(5));
        createTestTransaction(12, "9876543210", testBook.getBookId(), 112, TransactionStatus.EXPIRED, today.minusDays(5));
        createTestTransaction(13, "9876543210", testBook.getBookId(), 113, TransactionStatus.LOST, today.minusDays(5));
        createTestTransaction(14, "9876543210", testBook.getBookId(), 114, TransactionStatus.DAMAGED, today.minusDays(5));
        createTestTransaction(15, "9876543210", testBook.getBookId(), 115, TransactionStatus.RETURNED, today.minusDays(5));

        schedulerService.processOverdueTransactions();

        assertNull(fineRepository.findByTransactionIdAndType(11, FineType.OVERDUE));
        assertNull(fineRepository.findByTransactionIdAndType(12, FineType.OVERDUE));
        assertNull(fineRepository.findByTransactionIdAndType(13, FineType.OVERDUE));
        assertNull(fineRepository.findByTransactionIdAndType(14, FineType.OVERDUE));
        assertNull(fineRepository.findByTransactionIdAndType(15, FineType.OVERDUE));
        assertEquals(0, notificationService.getSent().size());
    }

    @Test
    @DisplayName("Scenario 10: The correct member receives a notification with correct book details and outstanding fine amount")
    void testNotificationContentAndRecipient() {
        LocalDate today = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> today);

        createTestTransaction(16, "9876543210", testBook.getBookId(), 116, TransactionStatus.ISSUED, today.minusDays(3));

        schedulerService.processOverdueTransactions();

        List<TestNotificationService.Notification> sent = notificationService.getSent();
        assertEquals(1, sent.size());
        TestNotificationService.Notification notif = sent.get(0);
        assertEquals("9876543210", notif.getRecipient());

        String msg = notif.getMessage();
        assertTrue(msg.contains("Notification for: Alice Member (Phone: 9876543210)"), "Notification must identify recipient");
        assertTrue(msg.contains("Message: Overdue Notice:"), "Notification must contain Message label and notice");
        assertTrue(msg.contains("Clean Code"), "Notification must contain book title");
        assertTrue(msg.contains("2026-10-15"), "Notification must contain due date");
        assertTrue(msg.contains("3 days overdue"), "Notification must contain number of overdue days");
        assertTrue(msg.contains("30.00"), "Notification must contain outstanding fine amount");
        assertTrue(msg.toLowerCase().contains("return"), "Notification must urge member to return the book");
    }

    @Test
    @DisplayName("Scenario 11: Repeated checks send another notification when outstanding fine remains greater than zero")
    void testDuplicateNotificationsPrevented() {
        LocalDate today = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> today);

        createTestTransaction(17, "9876543210", testBook.getBookId(), 117, TransactionStatus.ISSUED, today.minusDays(1));

        // First run: sends 1 notification
        schedulerService.processOverdueTransactions();
        assertEquals(1, notificationService.getSent().size());
        assertTrue(notificationService.getSent().get(0).getMessage().contains("10.00"));

        // Repeated run on same day: fine outstanding > 0 -> sends second notification
        schedulerService.processOverdueTransactions();
        assertEquals(2, notificationService.getSent().size(), "Second notification must be sent on repeat execution");

        // Third run: fine outstanding > 0 -> sends third notification
        schedulerService.processOverdueTransactions();
        assertEquals(3, notificationService.getSent().size(), "Third notification must be sent on repeat execution");
    }

    @Test
    @DisplayName("Scenario 12: An increase in the outstanding fine triggers an updated notification")
    void testFineIncreaseTriggersUpdatedNotification() {
        LocalDate day1 = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> day1);

        createTestTransaction(18, "9876543210", testBook.getBookId(), 118, TransactionStatus.ISSUED, day1.minusDays(1));

        // Day 1: sends notification for Rs. 10
        schedulerService.processOverdueTransactions();
        assertEquals(1, notificationService.getSent().size());
        assertTrue(notificationService.getSent().get(0).getMessage().contains("10.00"));

        // Day 2: overdue fine increases to Rs. 20
        LocalDate day2 = day1.plusDays(1);
        schedulerService.setDateSupplier(() -> day2);
        schedulerService.processOverdueTransactions();

        assertEquals(2, notificationService.getSent().size(), "Updated notification must be sent on fine increase");
        assertTrue(notificationService.getSent().get(1).getMessage().contains("20.00"));
    }

    @Test
    @DisplayName("Scenario 13: A notification failure does not stop fine processing or future scheduled checks")
    void testNotificationFailureResilience() {
        LocalDate today = LocalDate.of(2026, 10, 18);

        // Failing notification service
        NotificationService failingNotifService = new NotificationService() {
            @Override
            public boolean send(String recipient, String message) {
                throw new RuntimeException("Simulated notification service failure");
            }
        };

        OverdueFineSchedulerService serviceWithFailingNotif = new OverdueFineSchedulerService(
                transactionRepository,
                bookRepository,
                fineService,
                failingNotifService
        );
        serviceWithFailingNotif.setDateSupplier(() -> today);

        createTestTransaction(19, "9876543210", testBook.getBookId(), 119, TransactionStatus.ISSUED, today.minusDays(2));

        // Processing must not throw exception
        assertDoesNotThrow(() -> serviceWithFailingNotif.processOverdueTransactions());

        // Fine must still be created successfully
        Fine fine = fineRepository.findByTransactionIdAndType(19, FineType.OVERDUE);
        assertNotNull(fine, "Fine must be created even if notification fails");
        assertEquals(20.0, fine.getAmount(), 0.001);

        // Future scheduled checks must still proceed without exception
        assertDoesNotThrow(() -> serviceWithFailingNotif.processOverdueTransactions());
    }

    @Test
    @DisplayName("Scenario 14: A failure processing one transaction does not prevent other overdue transactions from being processed")
    void testPerTransactionFaultIsolation() {
        LocalDate today = LocalDate.of(2026, 10, 18);

        createTestTransaction(20, "9876543210", testBook.getBookId(), 120, TransactionStatus.ISSUED, today.minusDays(2));
        createTestTransaction(21, "9876543210", testBook.getBookId(), 121, TransactionStatus.ISSUED, today.minusDays(3));

        // FineService that fails specifically for transaction 20
        FineService partiallyFailingFineService = new FineService(fineRepository, transactionRepository, policyService) {
            @Override
            public Fine createOverdueFine(int transactionId, LocalDate asOfDate) {
                if (transactionId == 20) {
                    throw new RuntimeException("Simulated transaction 20 failure");
                }
                return super.createOverdueFine(transactionId, asOfDate);
            }
        };

        OverdueFineSchedulerService resilientService = new OverdueFineSchedulerService(
                transactionRepository,
                bookRepository,
                partiallyFailingFineService,
                notificationService
        );
        resilientService.setDateSupplier(() -> today);

        assertDoesNotThrow(() -> resilientService.processOverdueTransactions());

        // Transaction 20 failed, but Transaction 21 must still be processed
        Fine fine21 = fineRepository.findByTransactionIdAndType(21, FineType.OVERDUE);
        assertNotNull(fine21, "Transaction 21 must be processed successfully despite failure of transaction 20");
        assertEquals(30.0, fine21.getAmount(), 0.001);
    }

    @Test
    @DisplayName("Scenario 15: The scheduler shuts down cleanly without lingering threads")
    void testSchedulerLifecycleAndShutdown() {
        schedulerService.startScheduler();
        assertTrue(schedulerService.isRunning(), "Scheduler must report running after start");

        schedulerService.stopScheduler();
        assertFalse(schedulerService.isRunning(), "Scheduler must report not running after stop");
    }

    @Test
    @DisplayName("Scenario 16: Existing BookTransactionService integrates scheduler and preserves business logic")
    void testBookTransactionServiceIntegration() {
        BookTransactionService bts = new BookTransactionService(
                bookRepository,
                bookCopyRepository,
                transactionRepository,
                policyService,
                null,
                notificationService,
                fineService
        );

        assertNotNull(bts.getOverdueFineSchedulerService(), "BookTransactionService must hold scheduler service");
        assertTrue(bts.getOverdueFineSchedulerService().isRunning(), "Scheduler must start automatically");

        bts.getOverdueFineSchedulerService().stopScheduler();
    }
}
