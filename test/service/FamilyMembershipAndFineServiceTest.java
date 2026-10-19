package service;

import enums.*;
import model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import paymentStrategy.PaymentStrategy;
import repository.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FamilyMembershipAndFineServiceTest {

    private DataStore dataStore;
    private UserRepository userRepository;
    private MembershipRepository membershipRepository;
    private MembershipPlanRepository membershipPlanRepository;
    private PaymentRepository paymentRepository;
    private FineRepository fineRepository;
    private TransactionRepository transactionRepository;
    private AuditLogRepository auditLogRepository;

    private AuditService auditService;
    private PolicyService policyService;
    private FineService fineService;
    private MembershipPlanService membershipPlanService;
    private MembershipService membershipService;
    private NotificationService notificationService;

    private User member1;
    private User member2;
    private User staffLibrarian;

    private MembershipPlan standardPlan;
    private MembershipPlan premiumPlan;
    private MembershipPlan familyPlan;

    @BeforeEach
    void setUp() {
        dataStore = new DataStore();
        userRepository = new InMemoryUserRepository(dataStore);
        membershipRepository = new InMemoryMembershipRepository(dataStore);
        membershipPlanRepository = new InMemoryMembershipPlanRepository(dataStore);
        paymentRepository = new InMemoryPaymentRepository(dataStore);
        fineRepository = new InMemoryFineRepository(dataStore);
        transactionRepository = new InMemoryTransactionRepository(dataStore);
        auditLogRepository = new InMemoryAuditLogRepository(dataStore);

        auditService = new AuditService(auditLogRepository);
        policyService = new PolicyService(userRepository, membershipRepository, transactionRepository, fineRepository, auditService);
        fineService = new FineService(fineRepository, transactionRepository, policyService, auditService);
        membershipPlanService = new MembershipPlanService(membershipPlanRepository, userRepository);
        notificationService = new NotificationService();

        // Register default plans including Family
        standardPlan = new MembershipPlan(DataStore.getMembershipPlanId(), "Standard Plan", 299.0);
        premiumPlan = new MembershipPlan(DataStore.getMembershipPlanId(), "Premium Plan", 599.0);
        familyPlan = new MembershipPlan(DataStore.getMembershipPlanId(), MembershipPlanService.DEFAULT_FAMILY_PLAN_NAME, MembershipPlanService.DEFAULT_FAMILY_PLAN_PRICE);

        membershipPlanRepository.save(standardPlan);
        membershipPlanRepository.save(premiumPlan);
        membershipPlanRepository.save(familyPlan);

        membershipService = new MembershipService(
                userRepository, membershipRepository, membershipPlanService,
                paymentRepository, new paymentStrategy.Razorpay(), auditService, notificationService
        );

        member1 = new User("Alice", "alice@example.com", "9111111111", "pass1", UserRole.MEMBER, UserStatus.ACTIVE);
        member2 = new User("Bob", "bob@example.com", "9222222222", "pass2", UserRole.MEMBER, UserStatus.ACTIVE);
        staffLibrarian = new User("Librarian", "lib@example.com", "9333333333", "pass3", UserRole.LIBRARIAN, UserStatus.ACTIVE);

        userRepository.save(member1);
        userRepository.save(member2);
        userRepository.save(staffLibrarian);
    }

    // ==========================================
    // FineService Tests
    // ==========================================

    @Test
    @DisplayName("Find an existing fine by ID")
    void testFindExistingFineById() {
        Fine fine = new Fine(101, member1.getPhone(), 1, 50.0, FineType.OVERDUE);
        fineRepository.save(fine);

        Fine found = fineService.findById(101);
        assertNotNull(found);
        assertEquals(101, found.getFineId());
        assertEquals(member1.getPhone(), found.getUserPhone());
        assertEquals(50.0, found.getAmount(), 0.001);
    }

    @Test
    @DisplayName("Query a nonexistent fine ID returns null")
    void testQueryNonExistentFineId() {
        Fine found = fineService.findById(999);
        assertNull(found);
    }

    @Test
    @DisplayName("Retrieve multiple fines belonging to one member")
    void testRetrieveMultipleFinesBelongingToOneMember() {
        Fine fine1 = new Fine(101, member1.getPhone(), 1, 30.0, FineType.OVERDUE);
        Fine fine2 = new Fine(102, member1.getPhone(), 2, 40.0, FineType.OVERDUE);
        Fine fineOther = new Fine(103, member2.getPhone(), 3, 50.0, FineType.OVERDUE);

        fineRepository.save(fine1);
        fineRepository.save(fine2);
        fineRepository.save(fineOther);

        List<Fine> fines = fineService.findByUserPhone(member1.getPhone());
        assertEquals(2, fines.size());

        List<Fine> history = fineService.getFineHistory(member1.getPhone());
        assertEquals(2, history.size());
    }

    @Test
    @DisplayName("Verify that one member cannot access another member's private fine history")
    void testMemberCannotAccessAnotherMemberPrivateFineHistory() {
        Fine fine = new Fine(101, member1.getPhone(), 1, 50.0, FineType.OVERDUE);
        fineRepository.save(fine);

        // member2 attempting to view member1's fine history
        List<Fine> history = fineService.getFineHistory(member1.getPhone(), member2);
        assertTrue(history.isEmpty());

        // member2 attempting to view member1's outstanding fines
        List<Fine> outstanding = fineService.getOutstandingFines(member1.getPhone(), member2);
        assertTrue(outstanding.isEmpty());

        // member2 attempting to find member1's fine by ID
        Fine found = fineService.findById(101, member2);
        assertNull(found);
    }

    @Test
    @DisplayName("Return only legitimately outstanding fines")
    void testReturnOnlyLegitimatelyOutstandingFines() {
        Fine finePending = new Fine(101, member1.getPhone(), 1, 50.0, FineType.OVERDUE);
        Fine finePartial = new Fine(102, member1.getPhone(), 2, 100.0, FineType.LOST_BOOK);
        finePartial.applyPayment(40.0); // 60 remaining, PARTIALLY_PAID

        fineRepository.save(finePending);
        fineRepository.save(finePartial);

        List<Fine> outstanding = fineService.getOutstandingFines(member1.getPhone());
        assertEquals(2, outstanding.size());
    }

    @Test
    @DisplayName("Exclude fully paid and waived fines from outstanding results")
    void testExcludeFullyPaidAndWaivedFinesFromOutstandingResults() {
        Fine finePending = new Fine(101, member1.getPhone(), 1, 50.0, FineType.OVERDUE);
        Fine finePaid = new Fine(102, member1.getPhone(), 2, 100.0, FineType.OVERDUE);
        finePaid.markPaid();

        Fine fineWaived = new Fine(103, member1.getPhone(), 3, 70.0, FineType.DAMAGED_BOOK);
        fineWaived.waive();

        fineRepository.save(finePending);
        fineRepository.save(finePaid);
        fineRepository.save(fineWaived);

        List<Fine> outstanding = fineService.getOutstandingFines(member1.getPhone());
        assertEquals(1, outstanding.size());
        assertEquals(101, outstanding.get(0).getFineId());
    }

    @Test
    @DisplayName("Handle an empty repository safely")
    void testHandleEmptyRepository() {
        assertNull(fineService.findById(1));
        assertTrue(fineService.findByUserPhone("9999999999").isEmpty());
        assertTrue(fineService.findByUserPhone(null).isEmpty());
        assertTrue(fineService.findByUserPhone("   ").isEmpty());
        assertTrue(fineService.getFineHistory("9999999999").isEmpty());
        assertTrue(fineService.getOutstandingFines("9999999999").isEmpty());
    }

    @Test
    @DisplayName("Verify staff authorization remains enforced")
    void testVerifyStaffAuthorizationRemainsEnforced() {
        Fine fine = new Fine(101, member1.getPhone(), 1, 60.0, FineType.OVERDUE);
        fineRepository.save(fine);

        // Staff can view member's fine history and outstanding fines
        List<Fine> history = fineService.getFineHistory(member1.getPhone(), staffLibrarian);
        assertEquals(1, history.size());

        List<Fine> outstanding = fineService.getOutstandingFines(member1.getPhone(), staffLibrarian);
        assertEquals(1, outstanding.size());

        Fine staffLookup = fineService.findById(101, staffLibrarian);
        assertNotNull(staffLookup);

        // Member cannot waive fine
        assertFalse(fineService.waiveFine(101, member1));
        assertFalse(fineService.waiveFine(101));

        // Staff can waive fine
        assertTrue(fineService.waiveFine(101, staffLibrarian));
        assertEquals(FineStatus.WAIVED, fine.getStatus());
    }

    // ==========================================
    // Membership Tests
    // ==========================================

    @Test
    @DisplayName("Existing plans continue to work")
    void testExistingPlansContinueToWork() {
        boolean activated = membershipService.getMembershipByPhone(
                member1.getPhone(), standardPlan.getPlanId(), 1, PaymentMethod.UPI, member1);
        assertTrue(activated);

        Membership active = membershipService.getActiveMembership(member1.getPhone());
        assertNotNull(active);
        assertEquals(standardPlan.getPlanId(), active.getPlanId());
        assertEquals(MembershipStatus.ACTIVE, active.getStatus());
    }

    @Test
    @DisplayName("Family appears in plan selection")
    void testFamilyAppearsInPlanSelection() {
        List<MembershipPlan> allPlans = membershipPlanService.getAllPlans();
        boolean found = false;
        for (MembershipPlan p : allPlans) {
            if ("Family Plan".equalsIgnoreCase(p.getPlanName())) {
                found = true;
                assertEquals(MembershipPlanService.DEFAULT_FAMILY_PLAN_PRICE, p.getAmount(), 0.001);
            }
        }
        assertTrue(found, "Family Plan must appear in plan selection");

        // Can also find by name
        assertNotNull(membershipPlanService.findByName("Family Plan"));
        assertNotNull(membershipPlanService.findByName("Family"));
    }

    @Test
    @DisplayName("Family membership stores the selected duration")
    void testFamilyMembershipStoresSelectedDuration() {
        int durationMonths = 6;
        LocalDate beforePurchase = LocalDate.now();

        boolean activated = membershipService.getMembershipByPhone(
                member1.getPhone(), familyPlan.getPlanId(), durationMonths, PaymentMethod.CARD, member1);
        assertTrue(activated);

        Membership mem = membershipService.getActiveMembership(member1.getPhone());
        assertNotNull(mem);
        assertEquals(familyPlan.getPlanId(), mem.getPlanId());
        assertEquals(beforePurchase.plusMonths(durationMonths), mem.getEndDate());
    }

    @Test
    @DisplayName("Correct total for one month")
    void testCorrectTotalForOneMonth() {
        membershipService.getMembershipByPhone(
                member1.getPhone(), standardPlan.getPlanId(), 1, PaymentMethod.UPI, member1);

        Membership mem = membershipService.getActiveMembership(member1.getPhone());
        assertNotNull(mem);

        List<Payment> payments = paymentRepository.findByMembershipId(mem.getMembershipId());
        assertEquals(1, payments.size());
        assertEquals(299.0, payments.get(0).getAmount(), 0.001);
    }

    @Test
    @DisplayName("Correct total for multiple months")
    void testCorrectTotalForMultipleMonths() {
        int duration = 3;
        membershipService.getMembershipByPhone(
                member1.getPhone(), standardPlan.getPlanId(), duration, PaymentMethod.UPI, member1);

        Membership mem = membershipService.getActiveMembership(member1.getPhone());
        assertNotNull(mem);

        List<Payment> payments = paymentRepository.findByMembershipId(mem.getMembershipId());
        assertEquals(1, payments.size());
        // Monthly 299 * 3 = 897
        assertEquals(299.0 * 3, payments.get(0).getAmount(), 0.001);
    }

    @Test
    @DisplayName("Correct total for each monthly plan")
    void testCorrectTotalForEachMonthlyPlan() {
        // Standard plan: 299 * 2 = 598
        membershipService.getMembershipByPhone(member1.getPhone(), standardPlan.getPlanId(), 2, PaymentMethod.UPI);
        Membership m1 = membershipService.getActiveMembership(member1.getPhone());
        Payment p1 = paymentRepository.findByMembershipId(m1.getMembershipId()).get(0);
        assertEquals(598.0, p1.getAmount(), 0.001);

        // Bob: Premium plan: 599 * 3 = 1797
        membershipService.getMembershipByPhone(member2.getPhone(), premiumPlan.getPlanId(), 3, PaymentMethod.UPI);
        Membership m2 = membershipService.getActiveMembership(member2.getPhone());
        Payment p2 = paymentRepository.findByMembershipId(m2.getMembershipId()).get(0);
        assertEquals(1797.0, p2.getAmount(), 0.001);

        // User 3: Family plan: 899 * 4 = 3596
        User user3 = new User("Charlie", "charlie@example.com", "9444444444", "pass4", UserRole.MEMBER, UserStatus.ACTIVE);
        userRepository.save(user3);
        membershipService.getMembershipByPhone(user3.getPhone(), familyPlan.getPlanId(), 4, PaymentMethod.CARD);
        Membership m3 = membershipService.getActiveMembership(user3.getPhone());
        Payment p3 = paymentRepository.findByMembershipId(m3.getMembershipId()).get(0);
        assertEquals(899.0 * 4, p3.getAmount(), 0.001);
    }

    @Test
    @DisplayName("Invalid zero or negative durations are rejected")
    void testInvalidDurationsRejected() {
        assertFalse(membershipService.getMembershipByPhone(member1.getPhone(), standardPlan.getPlanId(), 0, PaymentMethod.UPI));
        assertFalse(membershipService.getMembershipByPhone(member1.getPhone(), standardPlan.getPlanId(), -1, PaymentMethod.UPI));
        assertFalse(membershipService.getMembershipByPhone(member1.getPhone(), standardPlan.getPlanId(), -12, PaymentMethod.UPI));

        assertNull(membershipRepository.findActiveByUserPhone(member1.getPhone()));
    }

    @Test
    @DisplayName("Payment record contains total amount, not just monthly price")
    void testPaymentRecordContainsTotalAmount() {
        int duration = 5;
        double expectedTotal = familyPlan.getAmount() * duration;

        boolean activated = membershipService.getMembershipByPhone(
                member1.getPhone(), familyPlan.getPlanId(), duration, PaymentMethod.NET_BANKING, member1);
        assertTrue(activated);

        Membership mem = membershipService.getActiveMembership(member1.getPhone());
        List<Payment> memPayments = paymentRepository.findByMembershipId(mem.getMembershipId());
        assertEquals(1, memPayments.size());
        assertEquals(expectedTotal, memPayments.get(0).getAmount(), 0.001);
        assertTrue(memPayments.get(0).isSuccessful());
    }

    @Test
    @DisplayName("Successful payment activates membership")
    void testSuccessfulPaymentActivatesMembership() {
        boolean ok = membershipService.getMembershipByPhone(member1.getPhone(), standardPlan.getPlanId(), 2, PaymentMethod.UPI);
        assertTrue(ok);

        Membership active = membershipService.getActiveMembership(member1.getPhone());
        assertNotNull(active);
        assertEquals(MembershipStatus.ACTIVE, active.getStatus());
    }

    @Test
    @DisplayName("Failed payment does not activate membership")
    void testFailedPaymentDoesNotActivateMembership() {
        // Strategy that fails
        PaymentStrategy failingStrategy = new PaymentStrategy() {
            @Override
            public int pay(double amount) {
                return 0; // indicates failure
            }

            @Override
            public void refund(int gatewayReference) {
            }
        };

        MembershipService failingMemService = new MembershipService(
                userRepository, membershipRepository, membershipPlanService,
                paymentRepository, failingStrategy, auditService, notificationService
        );

        boolean ok = failingMemService.getMembershipByPhone(member1.getPhone(), familyPlan.getPlanId(), 3, PaymentMethod.UPI);
        assertFalse(ok);

        assertNull(membershipRepository.findActiveByUserPhone(member1.getPhone()));

        // Ensure payment record exists and is marked failed
        List<Payment> payments = paymentRepository.findByUserPhone(member1.getPhone());
        assertEquals(1, payments.size());
        assertTrue(payments.get(0).isFailed());
    }

    @Test
    @DisplayName("Renewal calculates the correct total and extends membership")
    void testRenewalCalculatesCorrectTotal() {
        // Activate 1 month first
        membershipService.getMembershipByPhone(member1.getPhone(), standardPlan.getPlanId(), 1, PaymentMethod.UPI);
        Membership original = membershipService.getActiveMembership(member1.getPhone());
        LocalDate originalEnd = original.getEndDate();

        // Renew for 3 months with Family plan
        boolean renewed = membershipService.renewMembership(member1.getPhone(), familyPlan.getPlanId(), 3, PaymentMethod.CARD);
        assertTrue(renewed);

        Membership updated = membershipService.getActiveMembership(member1.getPhone());
        assertEquals(originalEnd.plusMonths(3), updated.getEndDate());

        // Check payments: first payment (299), second payment (899 * 3 = 2697)
        List<Payment> payments = paymentRepository.findByMembershipId(original.getMembershipId());
        assertEquals(2, payments.size());
        assertEquals(299.0, payments.get(0).getAmount(), 0.001);
        assertEquals(899.0 * 3, payments.get(1).getAmount(), 0.001);
    }

    @Test
    @DisplayName("Expiry date reflects calendar-month duration")
    void testExpiryDateReflectsCalendarMonthDuration() {
        LocalDate start = LocalDate.now();
        membershipService.getMembershipByPhone(member1.getPhone(), standardPlan.getPlanId(), 3, PaymentMethod.UPI);

        Membership mem = membershipService.getActiveMembership(member1.getPhone());
        assertEquals(start.plusMonths(3), mem.getEndDate());
    }

    @Test
    @DisplayName("Payment confirmation prints the total amount in member UI flow")
    void testPaymentConfirmationPrintsTotalAmountInMemberUi() throws Exception {
        Class<?> mainClass = Class.forName("ui.Main");
        Field currentUserField = mainClass.getDeclaredField("currentUser");
        currentUserField.setAccessible(true);
        currentUserField.set(null, member1);

        Field membershipPlanServiceField = mainClass.getDeclaredField("membershipPlanService");
        membershipPlanServiceField.setAccessible(true);
        membershipPlanServiceField.set(null, membershipPlanService);

        Field membershipServiceField = mainClass.getDeclaredField("membershipService");
        membershipServiceField.setAccessible(true);
        membershipServiceField.set(null, membershipService);

        // Input: 6 (activate), planId, 3 months, 1 (UPI), 12 (logout)
        String input = "6\n" + familyPlan.getPlanId() + "\n3\n1\n12\n";
        java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(input.getBytes());
        System.setIn(in);

        Field scField = mainClass.getDeclaredField("sc");
        scField.setAccessible(true);
        scField.set(null, new java.util.Scanner(System.in));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream testOut = new PrintStream(out, true, java.nio.charset.StandardCharsets.UTF_8);
        System.setOut(testOut);

        try {
            java.lang.reflect.Method memberMenuMethod = Class.forName("ui.Member").getDeclaredMethod("memberMenu");
            memberMenuMethod.setAccessible(true);
            memberMenuMethod.invoke(null);
        } finally {
            System.setOut(originalOut);
        }

        String output = out.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(output.contains("Selected Plan: Family Plan"), "Must display Selected Plan");
        assertTrue(output.contains("Monthly Price: ₹899.0"), "Must display Monthly Price");
        assertTrue(output.contains("Duration: 3 months"), "Must display Duration");
        assertTrue(output.contains("Total Amount: ₹2697.0"), "Must display Total Amount: ₹2697.0");
        assertTrue(output.contains("Payment Status: SUCCESS"), "Must display Payment Status: SUCCESS");
        assertTrue(output.contains("Membership Status: ACTIVE"), "Must display Membership Status: ACTIVE");
        assertTrue(output.contains("Membership Expiry Date:"), "Must display Expiry Date");
        assertTrue(output.contains("Membership activated successfully"), "Must display activation success message");
    }

    // ==========================================
    // Phase 5 Authorization Gap Tests
    // ==========================================

    @Test
    @DisplayName("Member can retrieve their own fine history and outstanding fines")
    void testMemberCanRetrieveTheirOwnFineHistory() {
        Fine fine1 = new Fine(101, member1.getPhone(), 1, 50.0, FineType.OVERDUE);
        fineRepository.save(fine1);

        List<Fine> history = fineService.getFineHistory(member1.getPhone(), member1);
        assertEquals(1, history.size());
        assertEquals(101, history.get(0).getFineId());

        List<Fine> outstanding = fineService.getOutstandingFines(member1.getPhone(), member1);
        assertEquals(1, outstanding.size());
        assertEquals(101, outstanding.get(0).getFineId());

        Fine own = fineService.findById(101, member1);
        assertNotNull(own);
        assertEquals(101, own.getFineId());
    }

    @Test
    @DisplayName("Null caller is rejected by caller-aware fine methods")
    void testNullCallerIsRejectedByCallerAwareFineMethods() {
        Fine fine1 = new Fine(101, member1.getPhone(), 1, 50.0, FineType.OVERDUE);
        fineRepository.save(fine1);

        assertNull(fineService.findById(101, null));
        assertTrue(fineService.findByUserPhone(member1.getPhone(), null).isEmpty());
        assertTrue(fineService.getFineHistory(member1.getPhone(), null).isEmpty());
        assertTrue(fineService.getOutstandingFines(member1.getPhone(), null).isEmpty());
        assertTrue(fineService.getAllFines(null).isEmpty());
        assertTrue(fineService.getFinesByStatus(FineStatus.PENDING, null).isEmpty());
        assertTrue(fineService.findByTransactionId(1, null).isEmpty());
        assertFalse(fineService.waiveFine(101, null));
    }

    @Test
    @DisplayName("Unauthorized roles cannot retrieve all fines")
    void testUnauthorizedRolesCannotRetrieveAllFines() {
        Fine fine1 = new Fine(101, member1.getPhone(), 1, 50.0, FineType.OVERDUE);
        fineRepository.save(fine1);

        assertTrue(fineService.getAllFines(member1).isEmpty());
        assertTrue(fineService.getFinesByStatus(FineStatus.PENDING, member1).isEmpty());
    }

    @Test
    @DisplayName("Member can retrieve their own membership via caller-aware methods")
    void testMemberCanRetrieveTheirOwnMembership() {
        membershipService.getMembershipByPhone(member1.getPhone(), standardPlan.getPlanId(), 1, PaymentMethod.UPI, member1);

        Membership active = membershipService.getActiveMembership(member1.getPhone(), member1);
        assertNotNull(active);
        assertEquals(standardPlan.getPlanId(), active.getPlanId());

        Membership byPhone = membershipService.getMembershipByPhone(member1.getPhone(), member1);
        assertNotNull(byPhone);
        assertEquals(active.getMembershipId(), byPhone.getMembershipId());
    }

    @Test
    @DisplayName("Member cannot retrieve another member's membership")
    void testMemberCannotRetrieveAnotherMemberMembership() {
        membershipService.getMembershipByPhone(member1.getPhone(), standardPlan.getPlanId(), 1, PaymentMethod.UPI, member1);

        // member2 attempts to retrieve member1's membership
        Membership active = membershipService.getActiveMembership(member1.getPhone(), member2);
        assertNull(active, "Member2 should not be able to retrieve Member1's active membership");

        Membership byPhone = membershipService.getMembershipByPhone(member1.getPhone(), member2);
        assertNull(byPhone, "Member2 should not be able to retrieve Member1's membership by phone");
    }

    @Test
    @DisplayName("Member cannot renew another member's membership")
    void testMemberCannotRenewAnotherMemberMembership() {
        membershipService.getMembershipByPhone(member1.getPhone(), standardPlan.getPlanId(), 1, PaymentMethod.UPI, member1);
        Membership original = membershipService.getActiveMembership(member1.getPhone(), member1);
        LocalDate originalEnd = original.getEndDate();

        // member2 attempts to renew member1's membership
        boolean renewed3Args = membershipService.renewMembership(member1.getPhone(), 3, member2);
        assertFalse(renewed3Args, "Member2 should not be able to renew Member1's membership");

        boolean renewed5Args = membershipService.renewMembership(
                member1.getPhone(), familyPlan.getPlanId(), 3, PaymentMethod.CARD, member2);
        assertFalse(renewed5Args, "Member2 should not be able to renew Member1's membership with 5 args");

        // End date remains unchanged
        Membership afterAttempt = membershipService.getActiveMembership(member1.getPhone(), member1);
        assertEquals(originalEnd, afterAttempt.getEndDate());
    }

    @Test
    @DisplayName("Null callers are rejected by caller-aware membership methods")
    void testNullCallersRejectedByCallerAwareMembershipMethods() {
        membershipService.getMembershipByPhone(member1.getPhone(), standardPlan.getPlanId(), 1, PaymentMethod.UPI, member1);

        assertNull(membershipService.getActiveMembership(member1.getPhone(), null));
        assertNull(membershipService.getMembershipByPhone(member1.getPhone(), null));
        assertFalse(membershipService.getMembershipByPhone(member1.getPhone(), standardPlan.getPlanId(), 1, PaymentMethod.UPI, null));
        assertFalse(membershipService.renewMembership(member1.getPhone(), 3, null));
        assertFalse(membershipService.renewMembership(member1.getPhone(), standardPlan.getPlanId(), 3, PaymentMethod.UPI, null));
        assertFalse(membershipService.suspendMembership(null, member1.getPhone()));
        assertFalse(membershipService.reactivateMembership(null, member1.getPhone()));
    }

    @Test
    @DisplayName("Unauthorized roles cannot suspend or reactivate memberships")
    void testUnauthorizedRolesRejectedForMembershipOperations() {
        membershipService.getMembershipByPhone(member1.getPhone(), standardPlan.getPlanId(), 1, PaymentMethod.UPI, member1);

        assertFalse(membershipService.suspendMembership(member2, member1.getPhone()));
        assertFalse(membershipService.reactivateMembership(member2, member1.getPhone()));
    }

    @Test
    @DisplayName("Authorized staff can perform membership operations for members")
    void testAuthorizedStaffMembershipOperationsWork() {
        membershipService.getMembershipByPhone(member1.getPhone(), standardPlan.getPlanId(), 1, PaymentMethod.UPI, member1);

        // Staff can look up active membership
        Membership staffView = membershipService.getActiveMembership(member1.getPhone(), staffLibrarian);
        assertNotNull(staffView);

        // Staff can look up membership by phone
        Membership staffByPhone = membershipService.getMembershipByPhone(member1.getPhone(), staffLibrarian);
        assertNotNull(staffByPhone);

        // Staff can renew membership for member
        boolean renewedByStaff = membershipService.renewMembership(member1.getPhone(), 2, staffLibrarian);
        assertTrue(renewedByStaff);

        // Staff can suspend and reactivate
        assertTrue(membershipService.suspendMembership(staffLibrarian, member1.getPhone()));
        assertEquals(MembershipStatus.SUSPENDED, membershipRepository.findByUserPhone(member1.getPhone()).get(0).getStatus());

        assertTrue(membershipService.reactivateMembership(staffLibrarian, member1.getPhone()));
        assertEquals(MembershipStatus.ACTIVE, membershipService.getActiveMembership(member1.getPhone(), member1).getStatus());
    }

    @Test
    @DisplayName("Internal trusted workflows function correctly")
    void testInternalTrustedWorkflowsFunctionCorrectly() {
        Fine fine = new Fine(201, member1.getPhone(), 1, 40.0, FineType.OVERDUE);
        fineRepository.save(fine);

        // Internal fine lookups
        assertNotNull(fineService.findByIdInternal(201));
        assertEquals(1, fineService.findByUserPhoneInternal(member1.getPhone()).size());
        assertEquals(1, fineService.getFineHistoryInternal(member1.getPhone()).size());
        assertEquals(1, fineService.getOutstandingFinesInternal(member1.getPhone()).size());

        // Internal membership lookups
        membershipService.getMembershipByPhone(member1.getPhone(), standardPlan.getPlanId(), 1, PaymentMethod.UPI, member1);
        assertNotNull(membershipService.getActiveMembershipInternal(member1.getPhone()));
        assertNotNull(membershipService.getMembershipByPhoneInternal(member1.getPhone()));
        assertTrue(membershipService.renewMembershipInternal(member1.getPhone(), 1));
    }
}
