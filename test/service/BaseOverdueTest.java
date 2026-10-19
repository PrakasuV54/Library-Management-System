package service;

import enums.*;
import model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import repository.*;
import paymentStrategy.Razorpay;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;

public abstract class BaseOverdueTest {
    protected DataStore dataStore;
    protected UserRepository userRepository;
    protected BookRepository bookRepository;
    protected BookCopyRepository bookCopyRepository;
    protected TransactionRepository transactionRepository;
    protected MembershipRepository membershipRepository;
    protected FineRepository fineRepository;
    protected PaymentRepository paymentRepository;
    protected AuditLogRepository auditLogRepository;
    protected AuditService auditService;
    protected PolicyService policyService;
    protected FineService fineService;
    protected TestNotificationService notificationService;
    protected PaymentService paymentService;
    protected OverdueFineSchedulerService schedulerService;

    protected User testMember;
    protected User testStaff;
    protected Book testBook;
    protected Book testBook2;

    @BeforeEach
    void setUpBase() {
        dataStore = new DataStore();
        userRepository = new InMemoryUserRepository(dataStore);
        bookRepository = new InMemoryBookRepository(dataStore);
        bookCopyRepository = new InMemoryBookCopyRepository(dataStore);
        transactionRepository = new InMemoryTransactionRepository(dataStore);
        membershipRepository = new InMemoryMembershipRepository(dataStore);
        fineRepository = new InMemoryFineRepository(dataStore);
        paymentRepository = new InMemoryPaymentRepository(dataStore);
        auditLogRepository = new InMemoryAuditLogRepository(dataStore);

        auditService = new AuditService(auditLogRepository);
        policyService = new PolicyService(userRepository, membershipRepository, transactionRepository, fineRepository, auditService);
        fineService = new FineService(fineRepository, transactionRepository, policyService, auditService);
        notificationService = new TestNotificationService(userRepository);
        paymentService = new PaymentService(paymentRepository, fineRepository, new Razorpay(), auditService, membershipRepository, transactionRepository);

        schedulerService = new OverdueFineSchedulerService(transactionRepository, bookRepository, fineService, notificationService);

        testMember = new User("Alice Member", "alice@example.com", "9876543210", "pass123", UserRole.MEMBER, UserStatus.ACTIVE);
        userRepository.save(testMember);

        testStaff = new User("Staff Librarian", "lib@example.com", "8888888888", "libpass", UserRole.LIBRARIAN, UserStatus.ACTIVE);
        userRepository.save(testStaff);

        testBook = new Book(1, "978-0132350884", "Clean Code", "Robert C. Martin", 2008, "Craftsmanship");
        bookRepository.save(testBook);

        testBook2 = new Book(2, "978-0451524935", "1984", "George Orwell", 1950, "Dystopian");
        bookRepository.save(testBook2);
    }

    @AfterEach
    void tearDownBase() {
        if (schedulerService != null && schedulerService.isRunning()) {
            schedulerService.stopScheduler();
        }
    }

    protected Transaction createTestTransaction(int id, String phone, int bookId, int copyId, TransactionStatus status, LocalDate dueDate) {
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
}
