package ui;
import model.*;
import enums.*;
import service.AnalyticsService;
import service.AuthService;
import service.BookService;
import service.BookTransactionService;

import java.util.*;

import repository.*;
import service.*;
import authentication.*;
import paymentStrategy.*;

import  repository.SyntheticData.*;

public class Main {

    static Scanner sc = new Scanner(System.in);
    static User currentUser = null;

    public static int readInt() {
        while (true) {
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;
            try {
                return Integer.parseInt(line);
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a valid number:");
            }
        }
    }

    public static double readDouble() {
        while (true) {
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;
            try {
                return Double.parseDouble(line);
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a valid amount:");
            }
        }
    }



    public static void main(String[] args) {
        SyntheticData data = new SyntheticData(userRepository,membershipPlanRepository,bookService);
        data.syntheticData();
        while (true) {
            System.out.println("\nSelect User");
            System.out.println("1.Member");
            System.out.println("2.Librarian");
            System.out.println("3.Admin");
            System.out.println("4.Exit");
            System.out.println();
            int userChoice = readInt();
            switch (userChoice) {
                case 1:
                    Member.member();
                    break;
                case 2:
                    if (staffLogin(UserRole.LIBRARIAN)) {
                        if (currentUser != null && currentUser.getRole() == UserRole.ADMIN) {
                            Admin.admin();
                        } else {
                            Librarian.librarian();
                        }
                    }
                    break;
                case 3:
                    if (staffLogin(UserRole.ADMIN)) {
                        Admin.admin();
                    }
                    break;
                case 4:
                    System.out.println("Exiting Library Management System. Goodbye!");
                    return;
                default:
                    System.out.println("Invalid Choice");
            }
        }
    }

    private static boolean staffLogin(UserRole requiredRole) {
        System.out.println("Enter phone or email");
        String identifier = sc.nextLine().trim();
        System.out.println("Enter password");
        String password = sc.nextLine();

        if (identifier.isEmpty() || password.isEmpty()) {
            System.out.println("Login Failed: Phone/Email and password cannot be empty. Please enter your credentials.");
            return false;
        }

        User user = authService.login(identifier, password);
        if (user == null) {
            User existing = userRepository.findByPhone(identifier);
            if (existing == null) existing = userRepository.findByEmail(identifier);
            if (existing != null && existing.getStatus() == UserStatus.INACTIVE) {
                System.out.println("Login Failed: Your account has been deactivated. Please contact the administrator.");
            } else {
                System.out.println("Login Failed: Invalid credentials. Please check your phone/email and password.");
            }
            return false;
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            currentUser = null;
            System.out.println("Login Failed: Your account has been deactivated. Please contact the administrator.");
            return false;
        }

        if (user.getRole() == UserRole.MEMBER) {
            currentUser = null;
            System.out.println("Login Failed: This account is registered as MEMBER. Please log in via the Member menu.");
            return false;
        }

        if (requiredRole == UserRole.ADMIN && user.getRole() != UserRole.ADMIN) {
            currentUser = null;
            System.out.println("Login Failed: This account is registered as " + user.getRole() + ". Administrator privileges required.");
            return false;
        }

        currentUser = user;
        System.out.println("Login successful");
        return true;
    }



    




    static DataStore dataStore = new DataStore();
    static UserRepository userRepository = new InMemoryUserRepository(dataStore);
    static BookRepository bookRepository = new InMemoryBookRepository(dataStore);
    static BookCopyRepository bookCopyRepository = new InMemoryBookCopyRepository(dataStore);
    static TransactionRepository transactionRepository = new InMemoryTransactionRepository(dataStore);
    static MembershipRepository membershipRepository = new InMemoryMembershipRepository(dataStore);
    static FineRepository fineRepository = new InMemoryFineRepository(dataStore);
    static PaymentRepository paymentRepository = new InMemoryPaymentRepository(dataStore);
    static MembershipPlanRepository membershipPlanRepository = new InMemoryMembershipPlanRepository(dataStore);
    static AuditLogRepository auditLogRepository = new InMemoryAuditLogRepository(dataStore);
    static AuditService auditService = new AuditService(auditLogRepository);
    static NotificationService notificationService = new NotificationService();
    static PolicyService policyService = new PolicyService(userRepository, membershipRepository, transactionRepository, fineRepository, auditService);
    static FineService fineService = new FineService(fineRepository, transactionRepository, policyService, auditService);
    static UserService userService = new UserService(userRepository, auditService);
    static AuthService authService = new AuthService(userService, userRepository, new PasswordAuthentication(), auditService);
    static BookService bookService = new BookService(bookRepository, bookCopyRepository, transactionRepository, auditService);
    static BookTransactionService bookTransactionService = new BookTransactionService(bookRepository, bookCopyRepository, transactionRepository, policyService, auditService, notificationService, fineService);
    static PaymentService paymentService = new PaymentService(paymentRepository, fineRepository, new Razorpay(), auditService, membershipRepository, transactionRepository);
    static MembershipPlanService membershipPlanService = new MembershipPlanService(membershipPlanRepository, userRepository);
    static MembershipService membershipService = new MembershipService(userRepository, membershipRepository, membershipPlanService, paymentRepository, new Razorpay(), auditService, notificationService);
    static AnalyticsService analyticsService = new AnalyticsService(bookRepository, transactionRepository, userRepository);

}
