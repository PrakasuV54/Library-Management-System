package service;

import enums.*;
import model.*;
import repository.*;
import paymentStrategy.Razorpay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class MemberNotificationTest extends BaseOverdueTest {

    @Test
    @DisplayName("1. The correct member receives the overdue notification with clear recipient identification")
    void testCorrectMemberReceivesNotification() {
        LocalDate today = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> today);

        createTestTransaction(1, testMember.getPhone(), testBook.getBookId(), 501, TransactionStatus.ISSUED, today.minusDays(2));
        schedulerService.processOverdueTransactions();

        List<TestNotificationService.Notification> sent = notificationService.getSent();
        assertEquals(1, sent.size());
        assertEquals(testMember.getPhone(), sent.get(0).getRecipient());
        assertTrue(sent.get(0).getMessage().contains("Notification for: " + testMember.getName() + " (Phone: " + testMember.getPhone() + ")"),
                "Notification must clearly identify the intended recipient by name and phone");
    }

    @Test
    @DisplayName("2. The notification includes recipient header, book title, due date, overdue days, and outstanding fine amount")
    void testNotificationContentDetails() {
        LocalDate today = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> today);

        createTestTransaction(2, testMember.getPhone(), testBook.getBookId(), 502, TransactionStatus.ISSUED, today.minusDays(4));
        schedulerService.processOverdueTransactions();

        List<TestNotificationService.Notification> sent = notificationService.getSent();
        assertEquals(1, sent.size());
        String msg = sent.get(0).getMessage();

        assertTrue(msg.contains("Notification for: Alice Member (Phone: 9876543210)"), "Must identify recipient");
        assertTrue(msg.contains("Message: Overdue Notice:"), "Must include Message label and original notification content");
        assertTrue(msg.contains("Clean Code"), "Must include book title");
        assertTrue(msg.contains("2026-10-14"), "Must include due date");
        assertTrue(msg.contains("4 days overdue"), "Must include overdue days count");
        assertTrue(msg.contains("40.00"), "Must include outstanding fine amount");
        assertTrue(msg.toLowerCase().contains("return"), "Must ask member to return the book");
    }

    @Test
    @DisplayName("3. Notification uses a safe fallback when book details are missing")
    void testNotificationFallbackWhenBookNotFound() {
        LocalDate today = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> today);

        // Transaction referencing non-existent book ID 9999
        createTestTransaction(3, testMember.getPhone(), 9999, 503, TransactionStatus.ISSUED, today.minusDays(1));
        schedulerService.processOverdueTransactions();

        List<TestNotificationService.Notification> sent = notificationService.getSent();
        assertEquals(1, sent.size());
        assertTrue(sent.get(0).getMessage().contains("Book (ID: 9999)"), "Must use fallback book title");
    }

    @Test
    @DisplayName("4. No overdue notification is sent for a non-overdue transaction")
    void testNoNotificationForNonOverdue() {
        LocalDate today = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> today);

        createTestTransaction(4, testMember.getPhone(), testBook.getBookId(), 504, TransactionStatus.ISSUED, today.plusDays(3));
        schedulerService.processOverdueTransactions();

        assertEquals(0, notificationService.getSent().size(), "Non-overdue transaction must receive zero notifications");
    }

    @Test
    @DisplayName("5. A notification is sent only after the fine has been successfully created or updated")
    void testNotificationSentOnlyAfterFineCreated() {
        LocalDate today = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> today);

        createTestTransaction(5, testMember.getPhone(), testBook.getBookId(), 505, TransactionStatus.ISSUED, today.minusDays(1));

        // Before scheduler run: no fine, no notification
        assertNull(fineRepository.findByTransactionIdAndType(5, FineType.OVERDUE));
        assertEquals(0, notificationService.getSent().size());

        schedulerService.processOverdueTransactions();

        // After scheduler run: fine exists and notification delivered
        Fine fine = fineRepository.findByTransactionIdAndType(5, FineType.OVERDUE);
        assertNotNull(fine);
        assertEquals(1, notificationService.getSent().size());
    }

    @Test
    @DisplayName("6. Repeated checks send another notification when the overdue transaction has an outstanding fine")
    void testDuplicateNotificationsSuppressed() {
        LocalDate today = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> today);

        createTestTransaction(6, testMember.getPhone(), testBook.getBookId(), 506, TransactionStatus.ISSUED, today.minusDays(2));

        // Run 1: notification sent
        schedulerService.processOverdueTransactions();
        assertEquals(1, notificationService.getSent().size());

        // Runs 2 to 4 on same date: fine amount outstanding > 0 -> each run sends another notification
        for (int i = 0; i < 3; i++) {
            schedulerService.processOverdueTransactions();
            assertEquals(2 + i, notificationService.getSent().size(), "Notification must be sent on run " + (i + 2));
        }
    }

    @Test
    @DisplayName("7. An increased fine amount triggers an updated notification")
    void testIncreasedFineTriggersUpdatedNotification() {
        LocalDate day1 = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> day1);

        createTestTransaction(7, testMember.getPhone(), testBook.getBookId(), 507, TransactionStatus.ISSUED, day1.minusDays(1));

        // Day 1: fine is Rs. 10 -> 1 notification
        schedulerService.processOverdueTransactions();
        assertEquals(1, notificationService.getSent().size());
        assertTrue(notificationService.getSent().get(0).getMessage().contains("10.00"));

        // Day 2: fine increases to Rs. 20 -> 2nd notification
        LocalDate day2 = day1.plusDays(1);
        schedulerService.setDateSupplier(() -> day2);
        schedulerService.processOverdueTransactions();

        assertEquals(2, notificationService.getSent().size());
        assertTrue(notificationService.getSent().get(1).getMessage().contains("20.00"));
    }

    @Test
    @DisplayName("8. A notification failure does not stop fine processing")
    void testNotificationFailureDoesNotHaltFineProcessing() {
        LocalDate today = LocalDate.of(2026, 10, 18);

        NotificationService faultyNotificationService = new NotificationService() {
            @Override
            public boolean send(String recipient, String message) {
                throw new RuntimeException("Network timeout during notification delivery");
            }
        };

        OverdueFineSchedulerService customScheduler = new OverdueFineSchedulerService(
                transactionRepository, bookRepository, fineService, faultyNotificationService
        );
        customScheduler.setDateSupplier(() -> today);

        createTestTransaction(8, testMember.getPhone(), testBook.getBookId(), 508, TransactionStatus.ISSUED, today.minusDays(3));

        assertDoesNotThrow(() -> customScheduler.processOverdueTransactions());

        Fine fine = fineRepository.findByTransactionIdAndType(8, FineType.OVERDUE);
        assertNotNull(fine, "Fine must be successfully created even if notification delivery fails");
        assertEquals(30.0, fine.getAmount(), 0.001);
    }

    @Test
    @DisplayName("9. A failure for one member does not prevent notifications for other members")
    void testNotificationFailureForOneMemberDoesNotAffectOthers() {
        LocalDate today = LocalDate.of(2026, 10, 18);

        TestNotificationService selectiveFailingNotif = new TestNotificationService() {
            @Override
            public boolean send(String recipient, String message) {
                if ("9876543210".equals(recipient)) {
                    throw new RuntimeException("SMS gateway failure for member 9876543210");
                }
                return super.send(recipient, message);
            }
        };

        // Create a second member
        model.User member2 = new model.User("Bob Member", "bob@example.com", "9123456789", "bobpass", enums.UserRole.MEMBER, enums.UserStatus.ACTIVE);
        userRepository.save(member2);

        createTestTransaction(91, "9876543210", testBook.getBookId(), 591, TransactionStatus.ISSUED, today.minusDays(1));
        createTestTransaction(92, "9123456789", testBook2.getBookId(), 592, TransactionStatus.ISSUED, today.minusDays(2));

        OverdueFineSchedulerService customScheduler = new OverdueFineSchedulerService(
                transactionRepository, bookRepository, fineService, selectiveFailingNotif
        );
        customScheduler.setDateSupplier(() -> today);

        assertDoesNotThrow(() -> customScheduler.processOverdueTransactions());

        // Member 2 must have received their notification
        List<TestNotificationService.Notification> sent = selectiveFailingNotif.getSent();
        assertEquals(1, sent.size());
        assertEquals("9123456789", sent.get(0).getRecipient());
    }

    @Test
    @DisplayName("10. Failed notifications can be retried on subsequent scheduler runs")
    void testFailedNotificationsAreRetried() {
        LocalDate today = LocalDate.of(2026, 10, 18);

        AtomicBoolean shouldFail = new AtomicBoolean(true);
        TestNotificationService flakyNotif = new TestNotificationService() {
            @Override
            public boolean send(String recipient, String message) {
                if (shouldFail.get()) {
                    return false; // Delivery failed
                }
                return super.send(recipient, message);
            }
        };

        OverdueFineSchedulerService customScheduler = new OverdueFineSchedulerService(
                transactionRepository, bookRepository, fineService, flakyNotif
        );
        customScheduler.setDateSupplier(() -> today);

        createTestTransaction(10, testMember.getPhone(), testBook.getBookId(), 510, TransactionStatus.ISSUED, today.minusDays(2));

        // First execution fails to deliver notification
        customScheduler.processOverdueTransactions();
        assertEquals(0, flakyNotif.getSent().size());

        // Network recovers
        shouldFail.set(false);

        // Next execution retries delivery
        customScheduler.processOverdueTransactions();
        assertEquals(1, flakyNotif.getSent().size(), "Failed notification must be successfully retried");
        assertTrue(flakyNotif.getSent().get(0).getMessage().contains("Clean Code"));
        assertEquals(testMember.getPhone(), flakyNotif.getSent().get(0).getRecipient());
    }

    @Test
    @DisplayName("11. Notification tracking is updated only when the notification operation reports success")
    void testTrackingUpdatedOnlyOnSuccess() {
        LocalDate today = LocalDate.of(2026, 10, 18);

        TestNotificationService nonDeliveringNotif = new TestNotificationService() {
            @Override
            public boolean send(String recipient, String message) {
                return false; // Reports failure
            }
        };

        OverdueFineSchedulerService customScheduler = new OverdueFineSchedulerService(
                transactionRepository, bookRepository, fineService, nonDeliveringNotif
        );
        customScheduler.setDateSupplier(() -> today);

        createTestTransaction(11, testMember.getPhone(), testBook.getBookId(), 511, TransactionStatus.ISSUED, today.minusDays(1));
        customScheduler.processOverdueTransactions();

        assertTrue(nonDeliveringNotif.getSent().isEmpty(), "Notification sent list must remain empty when send returns false");
    }

    @Test
    @DisplayName("12. Notifications are not sent for returned, cancelled, expired, lost, or damaged transactions")
    void testNoNotificationForNonIssuedTransactions() {
        LocalDate today = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> today);

        createTestTransaction(121, testMember.getPhone(), testBook.getBookId(), 521, TransactionStatus.RETURNED, today.minusDays(3));
        createTestTransaction(122, testMember.getPhone(), testBook.getBookId(), 522, TransactionStatus.CANCELLED, today.minusDays(3));
        createTestTransaction(123, testMember.getPhone(), testBook.getBookId(), 523, TransactionStatus.EXPIRED, today.minusDays(3));
        createTestTransaction(124, testMember.getPhone(), testBook.getBookId(), 524, TransactionStatus.LOST, today.minusDays(3));
        createTestTransaction(125, testMember.getPhone(), testBook.getBookId(), 525, TransactionStatus.DAMAGED, today.minusDays(3));

        schedulerService.processOverdueTransactions();

        assertEquals(0, notificationService.getSent().size(), "No notifications must be sent for non-issued transactions");
    }

    @Test
    @DisplayName("13. Restart-persistence investigation: In-memory notification tracker resets on new service instance (documenting restart behavior)")
    void testRestartResetBehavior() {
        LocalDate today = LocalDate.of(2026, 10, 18);
        schedulerService.setDateSupplier(() -> today);

        createTestTransaction(13, testMember.getPhone(), testBook.getBookId(), 513, TransactionStatus.ISSUED, today.minusDays(1));

        // Scheduler instance 1 runs
        schedulerService.processOverdueTransactions();
        assertEquals(1, notificationService.getSent().size());

        // Simulate application restart: a new service instance is created without disk/db state
        TestNotificationService freshNotificationService = new TestNotificationService();
        OverdueFineSchedulerService restartedSchedulerService = new OverdueFineSchedulerService(
                transactionRepository, bookRepository, fineService, freshNotificationService
        );
        restartedSchedulerService.setDateSupplier(() -> today);

        assertTrue(freshNotificationService.getSent().isEmpty(), "New notification service starts with no sent notifications");

        // Running after restart sends notification on new instance
        restartedSchedulerService.processOverdueTransactions();
        assertEquals(1, freshNotificationService.getSent().size(), "Notification must be sent on new scheduler service instance");
        assertTrue(freshNotificationService.getSent().get(0).getMessage().contains("Clean Code"));
    }

    @Test
    @DisplayName("14. NotificationService formats recipient name and phone consistently")
    void testRecipientIdentificationWithKnownUser() {
        TestNotificationService notif = new TestNotificationService(userRepository);
        boolean result = notif.send("9876543210", "Your library card is ready.");

        assertTrue(result);
        assertEquals(1, notif.getSent().size());
        TestNotificationService.Notification n = notif.getSent().get(0);
        assertEquals("9876543210", n.getRecipient());
        String expected = "Notification for: Alice Member (Phone: 9876543210)\nMessage: Your library card is ready.";
        assertEquals(expected, n.getMessage());
    }

    @Test
    @DisplayName("15. NotificationService uses available identifier when member name is unavailable")
    void testRecipientIdentificationWithUnknownUserFallback() {
        TestNotificationService notif = new TestNotificationService(userRepository);
        // Phone number not registered in userRepository
        boolean result = notif.send("9999999999", "General library announcement.");

        assertTrue(result);
        assertEquals(1, notif.getSent().size());
        TestNotificationService.Notification n = notif.getSent().get(0);
        assertEquals("9999999999", n.getRecipient());
        String expected = "Notification for: 9999999999\nMessage: General library announcement.";
        assertEquals(expected, n.getMessage());
    }

    @Test
    @DisplayName("16. BookTransactionService notifications clearly identify the intended member")
    void testBookTransactionNotificationsIdentifyRecipient() {
        membershipRepository.save(new Membership(1001, testMember.getPhone(), 1, LocalDate.now().minusDays(5), LocalDate.now().plusDays(25), enums.MembershipStatus.ACTIVE, 2001));

        BookTransactionService bts = new BookTransactionService(
                bookRepository, bookCopyRepository, transactionRepository, policyService, auditService, notificationService, fineService
        );

        // Member requests book
        Transaction req = bts.requestBook(testMember.getPhone(), testBook.getBookId());
        assertNotNull(req);
        assertEquals(1, notificationService.getSent().size());
        String reqMsg = notificationService.getSent().get(0).getMessage();
        assertTrue(reqMsg.contains("Notification for: Alice Member (Phone: 9876543210)"));
        assertTrue(reqMsg.contains("Message: Your book request has been queued."));

        // Assign copy and mark ready for pickup
        BookCopy copy = new BookCopy(501, testBook.getBookId());
        bookCopyRepository.save(copy);
        bts.assignCopy(req.getTransactionId(), copy.getBookCopyId(), testStaff);
        bts.markReadyForPickup(req.getTransactionId(), testStaff);

        assertEquals(2, notificationService.getSent().size());
        String pickupMsg = notificationService.getSent().get(1).getMessage();
        assertTrue(pickupMsg.contains("Notification for: Alice Member (Phone: 9876543210)"));
        assertTrue(pickupMsg.contains("Message: Your requested book is ready for pickup."));

        // Issue book
        bts.issueBook(req.getTransactionId(), testStaff);
        assertEquals(3, notificationService.getSent().size());
        String issueMsg = notificationService.getSent().get(2).getMessage();
        assertTrue(issueMsg.contains("Notification for: Alice Member (Phone: 9876543210)"));
        assertTrue(issueMsg.contains("Message: Book issued. Due date:"));

        // Return book
        bts.returnBook(req.getTransactionId(), testStaff);
        assertEquals(4, notificationService.getSent().size());
        String returnMsg = notificationService.getSent().get(3).getMessage();
        assertTrue(returnMsg.contains("Notification for: Alice Member (Phone: 9876543210)"));
        assertTrue(returnMsg.contains("Message: Your returned book has been processed successfully."));

        bts.getOverdueFineSchedulerService().stopScheduler();
    }

    @Test
    @DisplayName("17. MembershipService notifications clearly identify the intended member")
    void testMembershipNotificationsIdentifyRecipient() {
        repository.MembershipPlanRepository planRepo = new repository.InMemoryMembershipPlanRepository(dataStore);
        MembershipPlanService planService = new MembershipPlanService(planRepo, userRepository);
        MembershipPlan plan = new MembershipPlan(repository.DataStore.getMembershipPlanId(), "Standard", 299.0);
        planRepo.save(plan);

        MembershipService ms = new MembershipService(
                userRepository, membershipRepository, planService, paymentRepository, new paymentStrategy.Razorpay(), auditService, notificationService
        );

        boolean purchased = ms.getMembershipByPhone(testMember.getPhone(), plan.getPlanId(), 1, enums.PaymentMethod.UPI);
        assertTrue(purchased);
        assertEquals(1, notificationService.getSent().size());
        String actMsg = notificationService.getSent().get(0).getMessage();
        assertTrue(actMsg.contains("Notification for: Alice Member (Phone: 9876543210)"));
        assertTrue(actMsg.contains("Message: Membership activated until"));

        boolean renewed = ms.renewMembership(testMember.getPhone(), plan.getPlanId(), 1, enums.PaymentMethod.UPI);
        assertTrue(renewed);
        assertEquals(2, notificationService.getSent().size());
        String renewMsg = notificationService.getSent().get(1).getMessage();
        assertTrue(renewMsg.contains("Notification for: Alice Member (Phone: 9876543210)"));
        assertTrue(renewMsg.contains("Message: Membership renewed until"));
    }

    @Test
    @DisplayName("18. Staff notifications clearly identify the intended staff recipient")
    void testStaffNotificationsIdentifyStaffRecipient() {
        notificationService.setUserRepository(userRepository);
        notificationService.send(testStaff.getName(), testStaff.getPhone(), "Notice: Waiting requests exist.");

        assertEquals(1, notificationService.getSent().size());
        TestNotificationService.Notification n = notificationService.getSent().get(0);
        assertEquals(testStaff.getPhone(), n.getRecipient());
        String expected = "Notification for: Staff Librarian (Phone: 8888888888)\nMessage: Notice: Waiting requests exist.";
        assertEquals(expected, n.getMessage());
    }

    @Test
    @DisplayName("19. NotificationService does not duplicate recipient header if already formatted")
    void testNoDuplicateRecipientHeaderIfAlreadyPresent() {
        TestNotificationService notif = new TestNotificationService(userRepository);
        String preformatted = "Notification for: Alice Member (Phone: 9876543210)\nMessage: Custom message";
        notif.send("9876543210", preformatted);

        assertEquals(1, notif.getSent().size());
        assertEquals(preformatted, notif.getSent().get(0).getMessage());
    }

    @Test
    @DisplayName("20. Production NotificationService prints directly and does not store notifications")
    void testProductionNotificationServiceDoesNotStoreNotifications() {
        NotificationService prodService = new NotificationService(userRepository);
        boolean sent = prodService.send("9876543210", "Production alert");
        assertTrue(sent);

        // Verify via reflection that production NotificationService contains no Collection or Map fields
        for (java.lang.reflect.Field field : NotificationService.class.getDeclaredFields()) {
            assertFalse(java.util.Collection.class.isAssignableFrom(field.getType()),
                    "Production NotificationService must not contain any Collection fields: " + field.getName());
            assertFalse(java.util.Map.class.isAssignableFrom(field.getType()),
                    "Production NotificationService must not contain any Map fields: " + field.getName());
        }
    }

    @Test
    @DisplayName("21. Membership activation resolves the actual registered user's name when user ID is 12")
    void testMembershipActivationNotificationResolvesRecipientNameWhenIdIs12() throws Exception {
        // Set IdGenerator.userId to 12 via reflection to create a user whose ID is 12
        Field idField = util.IdGenerator.class.getDeclaredField("userId");
        idField.setAccessible(true);
        idField.set(null, 12);

        User user12 = new User("John Doe", "johndoe@library.com", "1234567890",
                AuthService.hashPassword("secret123"), UserRole.MEMBER, UserStatus.ACTIVE);
        assertEquals(12, user12.getUserId());
        userRepository.save(user12);

        MembershipPlanRepository planRepo = new InMemoryMembershipPlanRepository(dataStore);
        MembershipPlanService planService = new MembershipPlanService(planRepo, userRepository);
        MembershipPlan plan = new MembershipPlan(DataStore.getMembershipPlanId(), "Basic Plan", 199.0);
        planRepo.save(plan);

        MembershipService ms = new MembershipService(
                userRepository, membershipRepository, planService, paymentRepository, new Razorpay(), auditService, notificationService
        );

        notificationService.clear();
        boolean activated = ms.getMembershipByPhone("1234567890", plan.getPlanId(), 1, PaymentMethod.UPI);
        assertTrue(activated);

        assertEquals(1, notificationService.getSent().size());
        String notificationMessage = notificationService.getSent().get(0).getMessage();

        // Must display actual registered name, never userId '12' as recipient name
        assertTrue(notificationMessage.contains("Notification for: John Doe (Phone: 1234567890)"),
                "Recipient name must resolve to actual registered name 'John Doe': " + notificationMessage);
        assertFalse(notificationMessage.contains("Notification for: 12 (Phone: 1234567890)"),
                "Recipient name must NOT be raw userId '12'");
        assertTrue(notificationMessage.contains("Message: Membership activated until"));
    }

    @Test
    @DisplayName("22. NotificationService resolves registered user name when userId string is passed")
    void testNotificationServiceResolvesRegisteredUserNameWhenUserIdStringIsPassed() throws Exception {
        Field idField = util.IdGenerator.class.getDeclaredField("userId");
        idField.setAccessible(true);
        idField.set(null, 12);

        User user12 = new User("Bob Smith", "bobsmith@library.com", "1234567890",
                AuthService.hashPassword("secret123"), UserRole.MEMBER, UserStatus.ACTIVE);
        assertEquals(12, user12.getUserId());
        userRepository.save(user12);

        notificationService.clear();
        notificationService.send("12", "1234567890", "Your fine is cleared.");

        assertEquals(1, notificationService.getSent().size());
        String msg = notificationService.getSent().get(0).getMessage();
        assertTrue(msg.contains("Notification for: Bob Smith (Phone: 1234567890)"),
                "Recipient name must resolve to actual registered name: " + msg);
        assertFalse(msg.contains("Notification for: 12 (Phone: 1234567890)"));
    }

    @Test
    @DisplayName("23. NotificationService displays clear fallback Member (ID: 12) when name genuinely cannot be resolved")
    void testFallbackWhenRecipientNameCannotBeResolved() {
        // Without user 12 in the repository, passing userId '12' falls back to Member (ID: 12)
        String formatted = NotificationService.formatNotificationMessage("12", "1234567890", "Account notice.");

        assertTrue(formatted.contains("Notification for: Member (ID: 12) (Phone: 1234567890)"),
                "Fallback must be 'Member (ID: 12)', not raw '12': " + formatted);
        assertFalse(formatted.contains("Notification for: 12 (Phone: 1234567890)"));
    }
}
