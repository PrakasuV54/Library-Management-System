package service;

import enums.FineStatus;
import enums.FineType;
import enums.TransactionStatus;
import model.Fine;
import model.Transaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class OverdueFineCalculationTest extends BaseOverdueTest {

    @Test
    @DisplayName("1. A book that is not overdue does not receive an overdue fine")
    void testNotOverdueDoesNotReceiveFine() {
        LocalDate today = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> today);

        createTestTransaction(1, testMember.getPhone(), testBook.getBookId(), 101, TransactionStatus.ISSUED, today.plusDays(5));

        schedulerService.processOverdueTransactions();

        assertNull(fineRepository.findByTransactionIdAndType(1, FineType.OVERDUE));
    }

    @Test
    @DisplayName("2. A book one day overdue accrues ₹10")
    void testOneDayOverdueAccrues10() {
        LocalDate today = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> today);

        createTestTransaction(2, testMember.getPhone(), testBook.getBookId(), 102, TransactionStatus.ISSUED, today.minusDays(1));

        schedulerService.processOverdueTransactions();

        Fine fine = fineRepository.findByTransactionIdAndType(2, FineType.OVERDUE);
        assertNotNull(fine);
        assertEquals(10.0, fine.getAmount(), 0.001);
        assertEquals(10.0, fine.getRemainingAmount(), 0.001);
        assertEquals(FineStatus.PENDING, fine.getStatus());
    }

    @Test
    @DisplayName("3. A book three days overdue accrues ₹30")
    void testThreeDaysOverdueAccrues30() {
        LocalDate today = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> today);

        createTestTransaction(3, testMember.getPhone(), testBook.getBookId(), 103, TransactionStatus.ISSUED, today.minusDays(3));

        schedulerService.processOverdueTransactions();

        Fine fine = fineRepository.findByTransactionIdAndType(3, FineType.OVERDUE);
        assertNotNull(fine);
        assertEquals(30.0, fine.getAmount(), 0.001);
        assertEquals(30.0, fine.getRemainingAmount(), 0.001);
    }

    @Test
    @DisplayName("4. A book ten days overdue accrues ₹100")
    void testTenDaysOverdueAccrues100() {
        LocalDate today = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> today);

        createTestTransaction(4, testMember.getPhone(), testBook.getBookId(), 104, TransactionStatus.ISSUED, today.minusDays(10));

        schedulerService.processOverdueTransactions();

        Fine fine = fineRepository.findByTransactionIdAndType(4, FineType.OVERDUE);
        assertNotNull(fine);
        assertEquals(100.0, fine.getAmount(), 0.001);
        assertEquals(100.0, fine.getRemainingAmount(), 0.001);
    }

    @Test
    @DisplayName("5. Advancing the test date increases the fine correctly")
    void testAdvancingDateIncreasesFineCorrectly() {
        LocalDate day1 = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> day1);

        createTestTransaction(5, testMember.getPhone(), testBook.getBookId(), 105, TransactionStatus.ISSUED, day1.minusDays(1));

        schedulerService.processOverdueTransactions();
        Fine fine1 = fineRepository.findByTransactionIdAndType(5, FineType.OVERDUE);
        assertEquals(10.0, fine1.getAmount(), 0.001);

        // Advance 2 more days -> now 3 days overdue
        LocalDate day3 = day1.plusDays(2);
        schedulerService.setDateSupplier(() -> day3);
        schedulerService.processOverdueTransactions();

        Fine fine3 = fineRepository.findByTransactionIdAndType(5, FineType.OVERDUE);
        assertEquals(30.0, fine3.getAmount(), 0.001);

        // Advance 4 more days -> now 7 days overdue
        LocalDate day7 = day3.plusDays(4);
        schedulerService.setDateSupplier(() -> day7);
        schedulerService.processOverdueTransactions();

        Fine fine7 = fineRepository.findByTransactionIdAndType(5, FineType.OVERDUE);
        assertEquals(70.0, fine7.getAmount(), 0.001);
    }

    @Test
    @DisplayName("6. Running the scheduler repeatedly on the same date does not increase the fine again")
    void testRepeatedRunsOnSameDateDoNotIncreaseFine() {
        LocalDate today = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> today);

        createTestTransaction(6, testMember.getPhone(), testBook.getBookId(), 106, TransactionStatus.ISSUED, today.minusDays(2));

        schedulerService.processOverdueTransactions();
        Fine fineInitial = fineRepository.findByTransactionIdAndType(6, FineType.OVERDUE);
        assertEquals(20.0, fineInitial.getAmount(), 0.001);
        int initialFineCount = fineRepository.findAll().size();

        // Run 5 more times on same date
        for (int i = 0; i < 5; i++) {
            schedulerService.processOverdueTransactions();
        }

        Fine fineFinal = fineRepository.findByTransactionIdAndType(6, FineType.OVERDUE);
        assertEquals(20.0, fineFinal.getAmount(), 0.001);
        assertEquals(initialFineCount, fineRepository.findAll().size());
    }

    @Test
    @DisplayName("7. An existing overdue fine is updated instead of creating a duplicate record")
    void testExistingOverdueFineIsUpdatedNotDuplicated() {
        LocalDate day1 = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> day1);

        createTestTransaction(7, testMember.getPhone(), testBook.getBookId(), 107, TransactionStatus.ISSUED, day1.minusDays(1));
        schedulerService.processOverdueTransactions();

        Fine initialFine = fineRepository.findByTransactionIdAndType(7, FineType.OVERDUE);
        int originalFineId = initialFine.getFineId();
        assertEquals(10.0, initialFine.getAmount(), 0.001);

        LocalDate day2 = day1.plusDays(1);
        schedulerService.setDateSupplier(() -> day2);
        schedulerService.processOverdueTransactions();

        Fine updatedFine = fineRepository.findByTransactionIdAndType(7, FineType.OVERDUE);
        assertEquals(originalFineId, updatedFine.getFineId(), "Fine ID must not change");
        assertEquals(20.0, updatedFine.getAmount(), 0.001);
        assertEquals(1, fineRepository.findAll().size(), "Must not create duplicate fine records");
    }

    @Test
    @DisplayName("8. Different overdue transactions receive separate, correctly calculated fines")
    void testDifferentTransactionsReceiveSeparateFines() {
        LocalDate today = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> today);

        createTestTransaction(81, testMember.getPhone(), testBook.getBookId(), 181, TransactionStatus.ISSUED, today.minusDays(1));
        createTestTransaction(82, testMember.getPhone(), testBook2.getBookId(), 182, TransactionStatus.ISSUED, today.minusDays(4));

        schedulerService.processOverdueTransactions();

        Fine fine1 = fineRepository.findByTransactionIdAndType(81, FineType.OVERDUE);
        Fine fine2 = fineRepository.findByTransactionIdAndType(82, FineType.OVERDUE);

        assertNotNull(fine1);
        assertNotNull(fine2);
        assertNotEquals(fine1.getFineId(), fine2.getFineId());
        assertEquals(10.0, fine1.getAmount(), 0.001);
        assertEquals(40.0, fine2.getAmount(), 0.001);
    }

    @Test
    @DisplayName("9. Transactions with missing due dates are handled safely")
    void testMissingDueDateHandledSafely() {
        LocalDate today = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> today);

        createTestTransaction(9, testMember.getPhone(), testBook.getBookId(), 109, TransactionStatus.ISSUED, null);

        assertDoesNotThrow(() -> schedulerService.processOverdueTransactions());
        assertNull(fineRepository.findByTransactionIdAndType(9, FineType.OVERDUE));
    }

    @Test
    @DisplayName("10. Returned, cancelled, expired, lost, and damaged transactions do not continue accruing overdue fines")
    void testNonIssuedTransactionsDoNotAccrueFines() {
        LocalDate day1 = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> day1);

        Transaction returnedTx = createTestTransaction(101, testMember.getPhone(), testBook.getBookId(), 201, TransactionStatus.ISSUED, day1.minusDays(2));
        schedulerService.processOverdueTransactions();
        Fine fineOnDay1 = fineRepository.findByTransactionIdAndType(101, FineType.OVERDUE);
        assertEquals(20.0, fineOnDay1.getAmount(), 0.001);

        // Mark returned on day1
        try {
            Field statusField = Transaction.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(returnedTx, TransactionStatus.RETURNED);
            Field returnDateField = Transaction.class.getDeclaredField("returnDate");
            returnDateField.setAccessible(true);
            returnDateField.set(returnedTx, day1.atTime(10, 0));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        transactionRepository.save(returnedTx);

        // Also create cancelled, expired, lost, damaged transactions
        createTestTransaction(102, testMember.getPhone(), testBook.getBookId(), 202, TransactionStatus.CANCELLED, day1.minusDays(5));
        createTestTransaction(103, testMember.getPhone(), testBook.getBookId(), 203, TransactionStatus.EXPIRED, day1.minusDays(5));
        createTestTransaction(104, testMember.getPhone(), testBook.getBookId(), 204, TransactionStatus.LOST, day1.minusDays(5));
        createTestTransaction(105, testMember.getPhone(), testBook.getBookId(), 205, TransactionStatus.DAMAGED, day1.minusDays(5));

        // Advance 10 days
        LocalDate day11 = day1.plusDays(10);
        schedulerService.setDateSupplier(() -> day11);
        schedulerService.processOverdueTransactions();

        // Returned transaction should NOT have accrued beyond 20
        Fine fineOnDay11 = fineRepository.findByTransactionIdAndType(101, FineType.OVERDUE);
        assertEquals(20.0, fineOnDay11.getAmount(), 0.001);

        // None of the others should have received an overdue fine
        assertNull(fineRepository.findByTransactionIdAndType(102, FineType.OVERDUE));
        assertNull(fineRepository.findByTransactionIdAndType(103, FineType.OVERDUE));
        assertNull(fineRepository.findByTransactionIdAndType(104, FineType.OVERDUE));
        assertNull(fineRepository.findByTransactionIdAndType(105, FineType.OVERDUE));
    }

    @Test
    @DisplayName("11. Fine calculation uses existing configured daily rate rather than hardcoding ₹10")
    void testConfiguredDailyRateIsUsed() {
        LocalDate today = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> today);

        // Update fine per day to Rs. 25.0 via PolicyService with admin
        model.User admin = new model.User("Admin", "admin@library.com", "9999999999", "admin123", enums.UserRole.ADMIN, enums.UserStatus.ACTIVE);
        userRepository.save(admin);
        boolean rateUpdated = policyService.updateFinePerDay(admin, 25.0);
        assertTrue(rateUpdated);
        assertEquals(25.0, policyService.getFinePerDay(), 0.001);

        createTestTransaction(111, testMember.getPhone(), testBook.getBookId(), 211, TransactionStatus.ISSUED, today.minusDays(2));

        schedulerService.processOverdueTransactions();

        Fine fine = fineRepository.findByTransactionIdAndType(111, FineType.OVERDUE);
        assertNotNull(fine);
        // 2 days * 25.0 = 50.0
        assertEquals(50.0, fine.getAmount(), 0.001, "Overdue calculation must use configured rate of Rs. 25/day");
    }

    @Test
    @DisplayName("12. Due-date boundary is correct: book due today is not overdue; book due yesterday is one day overdue")
    void testDueDateBoundaryCondition() {
        LocalDate today = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> today);

        // Book due today
        createTestTransaction(121, testMember.getPhone(), testBook.getBookId(), 221, TransactionStatus.ISSUED, today);
        // Book due yesterday
        createTestTransaction(122, testMember.getPhone(), testBook2.getBookId(), 222, TransactionStatus.ISSUED, today.minusDays(1));

        schedulerService.processOverdueTransactions();

        // Due today -> not overdue
        assertNull(fineRepository.findByTransactionIdAndType(121, FineType.OVERDUE), "Book due today must not be overdue");

        // Due yesterday -> exactly 1 day overdue (Rs. 10)
        Fine fineDueYesterday = fineRepository.findByTransactionIdAndType(122, FineType.OVERDUE);
        assertNotNull(fineDueYesterday, "Book due yesterday must be overdue");
        assertEquals(10.0, fineDueYesterday.getAmount(), 0.001);
    }
}
