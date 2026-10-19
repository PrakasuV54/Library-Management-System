package service;

import enums.*;
import model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ApplicationIntegrationTest extends BaseOverdueTest {

    @Test
    @DisplayName("1. The scheduler starts through existing application initialization path")
    void testSchedulerInitializationThroughExistingPath() {
        BookTransactionService bts = new BookTransactionService(
                bookRepository, bookCopyRepository, transactionRepository, policyService, auditService, notificationService, fineService
        );

        assertNotNull(bts.getOverdueFineSchedulerService());
        assertTrue(bts.getOverdueFineSchedulerService().isRunning());

        bts.getOverdueFineSchedulerService().stopScheduler();
    }

    @Test
    @DisplayName("2. Existing constructors of BookTransactionService continue to work safely")
    void testLegacyConstructorsCompatibility() {
        // 4-argument constructor
        BookTransactionService bts4 = new BookTransactionService(
                bookRepository, bookCopyRepository, transactionRepository, policyService
        );
        assertNull(bts4.getOverdueFineSchedulerService());
        assertDoesNotThrow(bts4::processOverdueTransactions);

        // 6-argument constructor
        BookTransactionService bts6 = new BookTransactionService(
                bookRepository, bookCopyRepository, transactionRepository, policyService, auditService, notificationService
        );
        assertNull(bts6.getOverdueFineSchedulerService());
        assertDoesNotThrow(bts6::processOverdueTransactions);
    }

    @Test
    @DisplayName("3. Existing fine retrieval and payment workflows function properly with updated fines")
    void testFineRetrievalAndPaymentWorkflows() {
        LocalDate day1 = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> day1);

        createTestTransaction(3, testMember.getPhone(), testBook.getBookId(), 603, TransactionStatus.ISSUED, day1.minusDays(2));
        schedulerService.processOverdueTransactions();

        // Check getOutstandingFines via fineService
        List<Fine> outstanding = fineService.getOutstandingFines(testMember.getPhone(), testMember);
        assertEquals(1, outstanding.size());
        assertEquals(20.0, outstanding.get(0).getRemainingAmount(), 0.001);

        // Check getFineHistory via fineService
        List<Fine> history = fineService.getFineHistory(testMember.getPhone(), testMember);
        assertEquals(1, history.size());
        assertEquals(20.0, history.get(0).getAmount(), 0.001);

        // Pay fine via paymentService
        Payment payment = paymentService.payFine(testMember.getPhone(), outstanding.get(0).getFineId(), 20.0, PaymentMethod.UPI);
        assertNotNull(payment);
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());

        // Now outstanding should be empty
        List<Fine> outstandingAfterPay = fineService.getOutstandingFines(testMember.getPhone(), testMember);
        assertEquals(0, outstandingAfterPay.size());
    }

    @Test
    @DisplayName("4. End-to-end lifecycle: issue, overdue, fine accrual, payment, and return")
    void testEndToEndLifecycle() {
        BookTransactionService bts = new BookTransactionService(
                bookRepository, bookCopyRepository, transactionRepository, policyService, auditService, notificationService, fineService
        );

        LocalDate issueDate = LocalDate.of(2026, 10, 1);
        LocalDate dueDate = issueDate.plusDays(14); // Due Oct 15

        BookCopy copy = new BookCopy(604, testBook.getBookId(), BookCopyStatus.ISSUED);
        bookCopyRepository.save(copy);

        Transaction t = new Transaction(604, testMember.getPhone(), testBook.getBookId());
        try {
            java.lang.reflect.Field statusF = Transaction.class.getDeclaredField("status");
            statusF.setAccessible(true);
            statusF.set(t, TransactionStatus.ISSUED);
            java.lang.reflect.Field copyF = Transaction.class.getDeclaredField("bookCopyId");
            copyF.setAccessible(true);
            copyF.set(t, 604);
            java.lang.reflect.Field dueF = Transaction.class.getDeclaredField("dueDate");
            dueF.setAccessible(true);
            dueF.set(t, dueDate);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        transactionRepository.save(t);

        // Date is Oct 18 (3 days overdue -> Rs. 30)
        LocalDate oct18 = LocalDate.of(2026, 10, 18);
        bts.getOverdueFineSchedulerService().setDateSupplier(() -> oct18);
        bts.processOverdueTransactions();

        Fine fine = fineRepository.findByTransactionIdAndType(604, FineType.OVERDUE);
        assertNotNull(fine);
        assertEquals(30.0, fine.getAmount(), 0.001);

        // Member returns the book on Oct 18
        boolean returned = bts.returnBook(t.getTransactionId(), testStaff);
        assertTrue(returned);
        assertEquals(TransactionStatus.RETURNED, transactionRepository.findById(604).getStatus());

        // Date advances to Oct 25 (book was returned on Oct 18 so fine must not exceed 30)
        LocalDate oct25 = LocalDate.of(2026, 10, 25);
        bts.getOverdueFineSchedulerService().setDateSupplier(() -> oct25);
        bts.processOverdueTransactions();

        Fine fineAfterReturn = fineRepository.findByTransactionIdAndType(604, FineType.OVERDUE);
        assertEquals(30.0, fineAfterReturn.getAmount(), 0.001, "Fine must not accrue after book return");

        bts.getOverdueFineSchedulerService().stopScheduler();
    }

    @Test
    @DisplayName("5. Confirm Main.java structure and service dependencies are intact")
    void testMainJavaRemainsUnmodified() throws Exception {
        File mainFile = new File("src/ui/Main.java");
        assertTrue(mainFile.exists());
        String content = Files.readString(mainFile.toPath());

        // Verify key service instantiation and login routing structure are intact
        assertTrue(content.contains("static BookTransactionService bookTransactionService = new BookTransactionService(bookRepository, bookCopyRepository, transactionRepository, policyService, auditService, notificationService, fineService);"));
        assertTrue(content.contains("staffLogin(UserRole requiredRole)"));
    }
}
