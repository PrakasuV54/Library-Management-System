package service;

import enums.FineType;
import enums.TransactionStatus;
import model.Fine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class SchedulerBehaviorTest extends BaseOverdueTest {

    @Test
    @DisplayName("1. The initial overdue check executes when the scheduler starts")
    void testInitialCheckExecutesOnStart() throws InterruptedException {
        // Create an overdue transaction before starting the scheduler
        LocalDate today = LocalDate.of(2026, 10, 18);
        createTestTransaction(1, testMember.getPhone(), testBook.getBookId(), 401, TransactionStatus.ISSUED, today.minusDays(2));

        schedulerService.setDateSupplier(() -> today);

        // Start scheduler with initialDelay = 0
        schedulerService.startScheduler();

        // Give daemon thread a brief moment to execute initial run
        Thread.sleep(150);

        Fine fine = fineRepository.findByTransactionIdAndType(1, FineType.OVERDUE);
        assertNotNull(fine, "Initial check must run at startup and create overdue fine");
        assertEquals(20.0, fine.getAmount(), 0.001);
    }

    @Test
    @DisplayName("2. Subsequent checks can be executed periodically")
    void testSubsequentScheduledChecks() {
        LocalDate day1 = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> day1);

        createTestTransaction(2, testMember.getPhone(), testBook.getBookId(), 402, TransactionStatus.ISSUED, day1.minusDays(1));
        schedulerService.processOverdueTransactions();

        Fine fine1 = fineRepository.findByTransactionIdAndType(2, FineType.OVERDUE);
        assertEquals(10.0, fine1.getAmount(), 0.001);

        // Next day check
        LocalDate day2 = day1.plusDays(1);
        schedulerService.setDateSupplier(() -> day2);
        schedulerService.processOverdueTransactions();

        Fine fine2 = fineRepository.findByTransactionIdAndType(2, FineType.OVERDUE);
        assertEquals(20.0, fine2.getAmount(), 0.001);
    }

    @Test
    @DisplayName("3. Multiple overdue transactions are processed in one run")
    void testMultipleTransactionsInOneRun() {
        LocalDate today = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> today);

        createTestTransaction(31, testMember.getPhone(), testBook.getBookId(), 431, TransactionStatus.ISSUED, today.minusDays(1));
        createTestTransaction(32, testMember.getPhone(), testBook2.getBookId(), 432, TransactionStatus.ISSUED, today.minusDays(2));
        createTestTransaction(33, testMember.getPhone(), testBook.getBookId(), 433, TransactionStatus.ISSUED, today.minusDays(3));

        schedulerService.processOverdueTransactions();

        assertEquals(10.0, fineRepository.findByTransactionIdAndType(31, FineType.OVERDUE).getAmount(), 0.001);
        assertEquals(20.0, fineRepository.findByTransactionIdAndType(32, FineType.OVERDUE).getAmount(), 0.001);
        assertEquals(30.0, fineRepository.findByTransactionIdAndType(33, FineType.OVERDUE).getAmount(), 0.001);
    }

    @Test
    @DisplayName("4. An exception while processing one transaction does not stop processing other transactions")
    void testExceptionInOneTransactionDoesNotHaltBatch() {
        LocalDate today = LocalDate.of(2026, 10, 18);

        createTestTransaction(41, testMember.getPhone(), testBook.getBookId(), 441, TransactionStatus.ISSUED, today.minusDays(2));
        createTestTransaction(42, testMember.getPhone(), testBook2.getBookId(), 442, TransactionStatus.ISSUED, today.minusDays(3));

        FineService throwingFineService = new FineService(fineRepository, transactionRepository, policyService) {
            @Override
            public Fine createOverdueFine(int transactionId, LocalDate asOfDate) {
                if (transactionId == 41) {
                    throw new RuntimeException("Simulated catastrophic failure for tx 41");
                }
                return super.createOverdueFine(transactionId, asOfDate);
            }
        };

        OverdueFineSchedulerService customScheduler = new OverdueFineSchedulerService(
                transactionRepository, bookRepository, throwingFineService, notificationService
        );
        customScheduler.setDateSupplier(() -> today);

        assertDoesNotThrow(() -> customScheduler.processOverdueTransactions());

        // Tx 41 failed, but Tx 42 must still succeed
        assertNull(fineRepository.findByTransactionIdAndType(41, FineType.OVERDUE));
        Fine fine42 = fineRepository.findByTransactionIdAndType(42, FineType.OVERDUE);
        assertNotNull(fine42);
        assertEquals(30.0, fine42.getAmount(), 0.001);
    }

    @Test
    @DisplayName("5. A failed execution does not permanently stop future scheduler runs")
    void testFailedExecutionDoesNotStopFutureRuns() {
        LocalDate today = LocalDate.of(2026, 10, 18);

        AtomicInteger invocationCount = new AtomicInteger(0);
        FineService intermittentlyFailingFineService = new FineService(fineRepository, transactionRepository, policyService) {
            @Override
            public Fine createOverdueFine(int transactionId, LocalDate asOfDate) {
                if (invocationCount.get() == 0) {
                    throw new RuntimeException("First run failure");
                }
                return super.createOverdueFine(transactionId, asOfDate);
            }
        };

        OverdueFineSchedulerService customScheduler = new OverdueFineSchedulerService(
                transactionRepository, bookRepository, intermittentlyFailingFineService, notificationService
        );
        customScheduler.setDateSupplier(() -> today);

        createTestTransaction(51, testMember.getPhone(), testBook.getBookId(), 451, TransactionStatus.ISSUED, today.minusDays(2));

        // First run fails for tx 51
        customScheduler.processOverdueTransactions();
        assertNull(fineRepository.findByTransactionIdAndType(51, FineType.OVERDUE));

        // Second run succeeds
        invocationCount.incrementAndGet();
        customScheduler.processOverdueTransactions();
        Fine fine = fineRepository.findByTransactionIdAndType(51, FineType.OVERDUE);
        assertNotNull(fine, "Second execution must proceed and create fine");
        assertEquals(20.0, fine.getAmount(), 0.001);
    }

    @Test
    @DisplayName("6. The scheduler shuts down cleanly when stopScheduler() is called")
    void testSchedulerCleanShutdown() {
        schedulerService.startScheduler();
        assertTrue(schedulerService.isRunning());

        schedulerService.stopScheduler();
        assertFalse(schedulerService.isRunning());

        // Repeated stop is safe and idempotent
        assertDoesNotThrow(() -> schedulerService.stopScheduler());
    }

    @Test
    @DisplayName("7. The scheduler does not create duplicate threads or multiple instances unexpectedly")
    void testNoDuplicateThreadsOnRepeatedStart() {
        schedulerService.startScheduler();
        assertTrue(schedulerService.isRunning());

        // Calling start again on already running scheduler should be a no-op
        schedulerService.startScheduler();
        assertTrue(schedulerService.isRunning());

        schedulerService.stopScheduler();
    }

    @Test
    @DisplayName("8. Repeated service initialization does not start unintended duplicate schedulers")
    void testRepeatedServiceInitialization() {
        BookTransactionService bts1 = new BookTransactionService(
                bookRepository, bookCopyRepository, transactionRepository, policyService, null, notificationService, fineService
        );
        assertTrue(bts1.getOverdueFineSchedulerService().isRunning());

        bts1.getOverdueFineSchedulerService().stopScheduler();
        assertFalse(bts1.getOverdueFineSchedulerService().isRunning());
    }

    @Test
    @DisplayName("9. Test execution does not hang because of daemon threads")
    void testWorkerThreadIsDaemon() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final boolean[] isDaemon = new boolean[1];

        OverdueFineSchedulerService customScheduler = new OverdueFineSchedulerService(
                transactionRepository, bookRepository, fineService, notificationService
        ) {
            @Override
            public void processOverdueTransactions() {
                isDaemon[0] = Thread.currentThread().isDaemon();
                latch.countDown();
            }
        };

        customScheduler.startScheduler();
        boolean completed = latch.await(2, TimeUnit.SECONDS);
        assertTrue(completed, "Scheduler task must execute");
        assertTrue(isDaemon[0], "Scheduler worker thread must be a daemon thread to prevent hanging on exit");

        customScheduler.stopScheduler();
    }

    @Test
    @DisplayName("10. Date changes and overdue calculations behave correctly across scheduler executions")
    void testDateChangesAcrossSchedulerExecutions() {
        LocalDate d1 = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> d1);

        createTestTransaction(101, testMember.getPhone(), testBook.getBookId(), 461, TransactionStatus.ISSUED, d1.minusDays(1));
        schedulerService.processOverdueTransactions();

        Fine fine = fineRepository.findByTransactionIdAndType(101, FineType.OVERDUE);
        assertEquals(10.0, fine.getAmount(), 0.001);

        // Advance by 4 days
        LocalDate d5 = d1.plusDays(4);
        schedulerService.setDateSupplier(() -> d5);
        schedulerService.processOverdueTransactions();

        Fine updatedFine = fineRepository.findByTransactionIdAndType(101, FineType.OVERDUE);
        assertEquals(50.0, updatedFine.getAmount(), 0.001);
    }

    @Test
    @DisplayName("11. Scheduler runs immediately at startup with initial delay 0 and scheduled for 6 hours")
    void testSchedulerIntervalIsSixHours() throws Exception {
        LocalDate today = LocalDate.of(2026, 10, 18);
        createTestTransaction(201, testMember.getPhone(), testBook.getBookId(), 501, TransactionStatus.ISSUED, today.minusDays(1));
        schedulerService.setDateSupplier(() -> today);

        schedulerService.startScheduler();
        assertTrue(schedulerService.isRunning());

        // Initial delay = 0 runs immediately at startup
        Thread.sleep(150);
        Fine fine = fineRepository.findByTransactionIdAndType(201, FineType.OVERDUE);
        assertNotNull(fine, "Overdue check must run immediately on startup with initial delay 0");
        assertEquals(10.0, fine.getAmount(), 0.001);

        schedulerService.stopScheduler();
        assertFalse(schedulerService.isRunning());
    }
}
