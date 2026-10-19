package service;

import enums.*;
import model.Fine;
import model.Payment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class PaymentAndFineStatusConsistencyTest extends BaseOverdueTest {

    @Test
    @DisplayName("1. PENDING fine status is maintained when no payment has been made")
    void testPendingFineStatus() {
        LocalDate today = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> today);

        createTestTransaction(1, testMember.getPhone(), testBook.getBookId(), 301, TransactionStatus.ISSUED, today.minusDays(1));
        schedulerService.processOverdueTransactions();

        Fine fine = fineRepository.findByTransactionIdAndType(1, FineType.OVERDUE);
        assertNotNull(fine);
        assertEquals(FineStatus.PENDING, fine.getStatus());
        assertEquals(0.0, fine.getAmountPaid(), 0.001);
        assertEquals(10.0, fine.getRemainingAmount(), 0.001);
        assertTrue(fine.isPending());
    }

    @Test
    @DisplayName("2. PARTIALLY_PAID fine status when partially paid")
    void testPartiallyPaidFineStatus() {
        LocalDate today = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> today);

        createTestTransaction(2, testMember.getPhone(), testBook.getBookId(), 302, TransactionStatus.ISSUED, today.minusDays(2));
        schedulerService.processOverdueTransactions();

        Fine fine = fineRepository.findByTransactionIdAndType(2, FineType.OVERDUE);
        assertEquals(20.0, fine.getAmount(), 0.001);

        Payment payment = paymentService.payFine(testMember.getPhone(), fine.getFineId(), 8.0, PaymentMethod.UPI);
        assertNotNull(payment);
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());

        Fine updatedFine = fineRepository.findById(fine.getFineId());
        assertEquals(FineStatus.PARTIALLY_PAID, updatedFine.getStatus());
        assertEquals(8.0, updatedFine.getAmountPaid(), 0.001);
        assertEquals(12.0, updatedFine.getRemainingAmount(), 0.001);
        assertTrue(updatedFine.isPending());
    }

    @Test
    @DisplayName("3. PAID fine status when paid in full")
    void testPaidFineStatus() {
        LocalDate today = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> today);

        createTestTransaction(3, testMember.getPhone(), testBook.getBookId(), 303, TransactionStatus.ISSUED, today.minusDays(1));
        schedulerService.processOverdueTransactions();

        Fine fine = fineRepository.findByTransactionIdAndType(3, FineType.OVERDUE);
        Payment payment = paymentService.payFine(testMember.getPhone(), fine.getFineId(), 10.0, PaymentMethod.CARD);
        assertNotNull(payment);

        Fine updatedFine = fineRepository.findById(fine.getFineId());
        assertEquals(FineStatus.PAID, updatedFine.getStatus());
        assertEquals(10.0, updatedFine.getAmountPaid(), 0.001);
        assertEquals(0.0, updatedFine.getRemainingAmount(), 0.001);
        assertFalse(updatedFine.isPending());
    }

    @Test
    @DisplayName("4. WAIVED fines are preserved and not silently un-waived by scheduler")
    void testWaivedFineStatusPreserved() {
        LocalDate day1 = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> day1);

        createTestTransaction(4, testMember.getPhone(), testBook.getBookId(), 304, TransactionStatus.ISSUED, day1.minusDays(2));
        schedulerService.processOverdueTransactions();

        Fine fine = fineRepository.findByTransactionIdAndType(4, FineType.OVERDUE);
        boolean waived = fineService.waiveFine(fine.getFineId(), testStaff);
        assertTrue(waived);

        Fine waivedFine = fineRepository.findById(fine.getFineId());
        assertEquals(FineStatus.WAIVED, waivedFine.getStatus());
        assertEquals(0.0, waivedFine.getRemainingAmount(), 0.001);

        // Advance 3 days: scheduler runs again
        LocalDate day4 = day1.plusDays(3);
        schedulerService.setDateSupplier(() -> day4);
        schedulerService.processOverdueTransactions();

        Fine afterSchedulerFine = fineRepository.findById(fine.getFineId());
        assertEquals(FineStatus.WAIVED, afterSchedulerFine.getStatus(), "Waived fine must not be un-waived");
        assertEquals(0.0, afterSchedulerFine.getRemainingAmount(), 0.001, "Waived remaining amount must remain 0");
    }

    @Test
    @DisplayName("5. A partially paid overdue fine whose total amount increases preserves payments and adjusts balance")
    void testPartiallyPaidFineAccrual() {
        LocalDate day1 = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> day1);

        createTestTransaction(5, testMember.getPhone(), testBook.getBookId(), 305, TransactionStatus.ISSUED, day1.minusDays(2));
        schedulerService.processOverdueTransactions();

        Fine fine = fineRepository.findByTransactionIdAndType(5, FineType.OVERDUE);
        assertEquals(20.0, fine.getAmount(), 0.001);

        // Member pays Rs. 10
        paymentService.payFine(testMember.getPhone(), fine.getFineId(), 10.0, PaymentMethod.UPI);
        Fine afterPayment = fineRepository.findById(fine.getFineId());
        assertEquals(10.0, afterPayment.getAmountPaid(), 0.001);
        assertEquals(10.0, afterPayment.getRemainingAmount(), 0.001);
        assertEquals(FineStatus.PARTIALLY_PAID, afterPayment.getStatus());

        // Next day arrives (now 3 days overdue, total Rs. 30)
        LocalDate day2 = day1.plusDays(1);
        schedulerService.setDateSupplier(() -> day2);
        schedulerService.processOverdueTransactions();

        Fine updatedFine = fineRepository.findById(fine.getFineId());
        assertEquals(30.0, updatedFine.getAmount(), 0.001);
        assertEquals(10.0, updatedFine.getAmountPaid(), 0.001, "Original payment must be preserved");
        assertEquals(20.0, updatedFine.getRemainingAmount(), 0.001, "Remaining balance must be 30 - 10 = 20");
        assertEquals(FineStatus.PARTIALLY_PAID, updatedFine.getStatus());
    }

    @Test
    @DisplayName("6. A fully paid overdue fine that accrues another overdue day transitions back to PARTIALLY_PAID with correct balance")
    void testFullyPaidFineAccruingNewOverdueDay() {
        LocalDate day1 = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> day1);

        createTestTransaction(6, testMember.getPhone(), testBook.getBookId(), 306, TransactionStatus.ISSUED, day1.minusDays(1));
        schedulerService.processOverdueTransactions();

        Fine fine = fineRepository.findByTransactionIdAndType(6, FineType.OVERDUE);
        assertEquals(10.0, fine.getAmount(), 0.001);

        // Member pays in full on Day 1
        paymentService.payFine(testMember.getPhone(), fine.getFineId(), 10.0, PaymentMethod.NET_BANKING);
        Fine paidFine = fineRepository.findById(fine.getFineId());
        assertEquals(FineStatus.PAID, paidFine.getStatus());
        assertEquals(0.0, paidFine.getRemainingAmount(), 0.001);

        // Day 2 arrives, book still not returned (now 2 days overdue -> Rs. 20)
        LocalDate day2 = day1.plusDays(1);
        schedulerService.setDateSupplier(() -> day2);
        schedulerService.processOverdueTransactions();

        Fine reaccruedFine = fineRepository.findById(fine.getFineId());
        assertEquals(20.0, reaccruedFine.getAmount(), 0.001);
        assertEquals(10.0, reaccruedFine.getAmountPaid(), 0.001, "Previously paid amount must not be lost");
        assertEquals(10.0, reaccruedFine.getRemainingAmount(), 0.001, "Member now owes remaining Rs. 10");
        assertEquals(FineStatus.PARTIALLY_PAID, reaccruedFine.getStatus(), "Status must become PARTIALLY_PAID");
    }

    @Test
    @DisplayName("7. Payment processing immediately before and after fine accrual")
    void testPaymentBeforeAndAfterAccrual() {
        LocalDate day1 = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> day1);

        createTestTransaction(7, testMember.getPhone(), testBook.getBookId(), 307, TransactionStatus.ISSUED, day1.minusDays(1));
        schedulerService.processOverdueTransactions();

        Fine fine = fineRepository.findByTransactionIdAndType(7, FineType.OVERDUE);

        // Pay Rs. 5 before accrual
        Payment p1 = paymentService.payFine(testMember.getPhone(), fine.getFineId(), 5.0, PaymentMethod.UPI);
        assertNotNull(p1);
        assertEquals(5.0, fineRepository.findById(fine.getFineId()).getRemainingAmount(), 0.001);

        // Accrual happens (day 2: total fine becomes 20)
        LocalDate day2 = day1.plusDays(1);
        schedulerService.setDateSupplier(() -> day2);
        schedulerService.processOverdueTransactions();

        Fine afterAccrual = fineRepository.findById(fine.getFineId());
        assertEquals(20.0, afterAccrual.getAmount(), 0.001);
        assertEquals(15.0, afterAccrual.getRemainingAmount(), 0.001);

        // Pay Rs. 15 after accrual to clear fine
        Payment p2 = paymentService.payFine(testMember.getPhone(), fine.getFineId(), 15.0, PaymentMethod.CARD);
        assertNotNull(p2);

        Fine finalFine = fineRepository.findById(fine.getFineId());
        assertEquals(FineStatus.PAID, finalFine.getStatus());
        assertEquals(20.0, finalFine.getAmountPaid(), 0.001);
        assertEquals(0.0, finalFine.getRemainingAmount(), 0.001);
    }

    @Test
    @DisplayName("8. Overpayment rejection, negative balance prevention, and payment history integrity")
    void testOverpaymentRejectionAndIntegrity() {
        LocalDate today = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> today);

        createTestTransaction(8, testMember.getPhone(), testBook.getBookId(), 308, TransactionStatus.ISSUED, today.minusDays(1));
        schedulerService.processOverdueTransactions();

        Fine fine = fineRepository.findByTransactionIdAndType(8, FineType.OVERDUE);
        assertEquals(10.0, fine.getRemainingAmount(), 0.001);

        // Attempt to pay Rs. 15 on a Rs. 10 balance
        Payment overpayment = paymentService.payFine(testMember.getPhone(), fine.getFineId(), 15.0, PaymentMethod.UPI);
        assertNull(overpayment, "Overpayment attempt must be rejected");

        Fine unchangedFine = fineRepository.findById(fine.getFineId());
        assertEquals(0.0, unchangedFine.getAmountPaid(), 0.001);
        assertEquals(10.0, unchangedFine.getRemainingAmount(), 0.001);

        // Negative payment rejection
        Payment negPayment = paymentService.payFine(testMember.getPhone(), fine.getFineId(), -5.0, PaymentMethod.UPI);
        assertNull(negPayment, "Negative payment must be rejected");
        assertEquals(10.0, unchangedFine.getRemainingAmount(), 0.001);
    }
}
