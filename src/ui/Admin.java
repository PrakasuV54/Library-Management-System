package ui;

import enums.*;
import model.*;

public class Admin extends Main {

    public static void admin() {
        if (currentUser == null || currentUser.getStatus() != UserStatus.ACTIVE || !authService.hasRole(currentUser, UserRole.ADMIN)) {
            System.out.println("Please log in as Admin"); return;
        }
        while (true) {
            System.out.println("\nAdmin Menu");
            System.out.println("1.View All Books");
            System.out.println("2.Search Books");
            System.out.println("3.Request Book");
            System.out.println("4.Cancel Request");
            System.out.println("5.Renew Book");
            System.out.println("6.Activate Membership");
            System.out.println("7.Renew Membership");
            System.out.println("8.View Currently Rented Books");
            System.out.println("9.View Borrowing History");
            System.out.println("10.Manage Fines");
            System.out.println("11.Update Profile");
            System.out.println("12.Add Book");
            System.out.println("13.Remove Book");
            System.out.println("14.View Pending Requests");
            System.out.println("15.Mark Ready for Pickup");
            System.out.println("16.Issue Book");
            System.out.println("17.Process Return");
            System.out.println("18.Mark Lost");
            System.out.println("19.Mark Damaged");
            System.out.println("20.View Defaulters");
            System.out.println("21.View Current Fines");
            System.out.println("22.Manage Current Fines");
            System.out.println("23.Manage Membership Status");
            System.out.println("24.Manage Payments");
            System.out.println("25.Dashboard");
            System.out.println("26.Manage Users");
            System.out.println("27.Manage Policies");
            System.out.println("28.Manage Membership Plans");
            System.out.println("29.Logout");
            System.out.println("Enter choice");
            int choice = readInt();
            switch (choice) {
                case 1: Member.handleViewAllBooks(); break;
                case 2: Member.handleSearchBooks(); break;
                case 3: Member.handleRequestBook(); break;
                case 4: Member.handleCancelRequest(); break;
                case 5: Member.handleRenewBook(); break;
                case 6: Member.handleActivateMembership(); break;
                case 7: Member.handleRenewMembership(); break;
                case 8: Member.handleViewCurrentlyRentedBooks(); break;
                case 9: Member.handleViewBorrowingHistory(); break;
                case 10: Member.handleManageFines(); break;
                case 11: Member.handleUpdateProfile(); break;
                case 12: Librarian.handleAddBook(); break;
                case 13: Librarian.handleRemoveBook(); break;
                case 14: Librarian.handleViewPendingRequests(); break;
                case 15: Librarian.handleMarkReadyForPickup(); break;
                case 16: Librarian.handleIssueBook(); break;
                case 17: Librarian.handleProcessReturn(); break;
                case 18: Librarian.handleMarkLost(); break;
                case 19: Librarian.handleMarkDamaged(); break;
                case 20: Librarian.handleViewDefaulters(); break;
                case 21: Librarian.viewCurrentFines(); break;
                case 22: Librarian.manageCurrentFines(); break;
                case 23: Librarian.handleManageMembershipStatus(); break;
                case 24: Librarian.handleManagePayments(); break;
                case 25: handleDashboard(); break;
                case 26: handleManageUsers(); break;
                case 27: handleManagePolicies(); break;
                case 28: handleManageMembershipPlans(); break;
                case 29:
                    currentUser = null;
                    System.out.println("Logged out");
                    return;
                default:
                    System.out.println("Invalid choice");
            }
        }
    }

    public static void handleDashboard() {
        System.out.println("Enter year for dashboard"); int year = readInt();
        AnalyticsDashboard dashboard = analyticsService.getDashboardForYear(year);
        System.out.println("Registrations by year: " + dashboard.getRegistrationsForYear());
        System.out.println("Registrations by month: " + dashboard.getRegistrationsByMonth());
        System.out.println("Registrations by quarter: " + dashboard.getRegistrationsByQuarter());
        System.out.println("Most borrowed books by year: " + dashboard.getMostBorrowedBooksForYear());
        System.out.println("Most borrowed books by month: " + dashboard.getMostBorrowedBooksByMonth());
        System.out.println("Most borrowed books by quarter: " + dashboard.getMostBorrowedBooksForYear());
        System.out.println("Total users: " + userRepository.findAll().size());
        double membershipEarnings = 0, finePayments = 0;
        for (Payment payment : paymentRepository.findAll()) if (payment.getStatus() == PaymentStatus.SUCCESS) {
            if (payment.isMembershipPayment()) membershipEarnings += payment.getAmount(); else finePayments += payment.getAmount();
        }
        System.out.println("Membership earnings: Rs." + membershipEarnings);
        System.out.println("Fine payment earnings: Rs." + finePayments);
    }

    public static void handleManageUsers() {
        System.out.println("1.View users  2.Add librarian  3.Add admin  4.Activate account  5.Deactivate account  6.Change role  7.Delete user");
        int userChoice = readInt();
        if (userChoice == 1) {
            for (User user : userService.getAllUsers(currentUser)) System.out.println(user);
        } else if (userChoice == 2 || userChoice == 3) {
            System.out.println("Enter name"); String name = sc.nextLine().trim();
            System.out.println("Enter email"); String email = sc.nextLine().trim();
            System.out.println("Enter phone"); String phone = sc.nextLine().trim();
            System.out.println("Enter password"); String password = sc.nextLine();
            UserRole role = userChoice == 2 ? UserRole.LIBRARIAN : UserRole.ADMIN;
            String reason = userService.getAddStaffFailureReason(currentUser, name, email, phone, password, role);
            if (reason != null) {
                System.out.println("Unable to create user: " + reason);
            } else {
                boolean success = userChoice == 2 ? userService.addLibrarian(currentUser, name, email, phone, password)
                        : userService.addAdmin(currentUser, name, email, phone, password);
                System.out.println(success ? "User created" : "Unable to create user: Failed to save user account.");
            }
        } else if (userChoice >= 4 && userChoice <= 7) {
            System.out.println("Enter user ID"); int userId = readInt();
            UserRole targetRole = null;
            if (userChoice == 6) {
                System.out.println("1.Member 2.Librarian 3.Admin"); int roleChoice = readInt();
                targetRole = roleChoice == 1 ? UserRole.MEMBER : roleChoice == 2 ? UserRole.LIBRARIAN : roleChoice == 3 ? UserRole.ADMIN : null;
            }
            String updateReason = userService.getUserUpdateFailureReason(currentUser, userId, userChoice, targetRole);
            if (updateReason != null) {
                System.out.println("Unable to update user: " + updateReason);
            } else {
                boolean success = false;
                if (userChoice == 4) success = userService.activateAccount(currentUser, userId);
                if (userChoice == 5) success = userService.deactivateAccount(currentUser, userId);
                if (userChoice == 6 && targetRole != null) success = userService.changeUserRole(currentUser, userId, targetRole);
                if (userChoice == 7) success = userService.deleteUser(currentUser, userId);
                System.out.println(success ? "User updated" : "Unable to update user: The requested account change could not be applied.");
            }
        } else System.out.println("Invalid choice");
    }

    public static void handleManagePolicies() {
        System.out.println("1.Maximum books  2.Loan days  3.Fine per day  4.Lost book charge  5.Damaged book charge");
        int policyChoice = readInt();
        if (policyChoice == 1) {
            System.out.println("Enter maximum books"); int value = readInt();
            if (value < 1) {
                System.out.println("Policy update failed: Maximum books limit must be at least 1.");
            } else if (policyService.updateMaxBooksBorrowed(currentUser, value)) {
                System.out.println("Policy updated");
            } else {
                System.out.println("Policy update failed: Insufficient permissions.");
            }
        } else if (policyChoice == 2) {
            System.out.println("Enter loan days"); int value = readInt();
            if (value < 1) {
                System.out.println("Policy update failed: Loan period must be at least 1 day.");
            } else if (policyService.updateLoanPeriodDays(currentUser, value)) {
                System.out.println("Policy updated");
            } else {
                System.out.println("Policy update failed: Insufficient permissions.");
            }
        } else if (policyChoice >= 3 && policyChoice <= 5) {
            System.out.println("Enter amount"); double value = readDouble();
            if (value < 0) {
                System.out.println("Policy update failed: Amount cannot be negative.");
            } else {
                boolean updated = false;
                if (policyChoice == 3) updated = policyService.updateFinePerDay(currentUser, value);
                if (policyChoice == 4) updated = policyService.updateLostBookCharge(currentUser, value);
                if (policyChoice == 5) updated = policyService.updateDamagedBookCharge(currentUser, value);
                System.out.println(updated ? "Policy updated" : "Policy update failed: Insufficient permissions.");
            }
        } else {
            System.out.println("Invalid choice");
        }
    }

    public static void handleManageMembershipPlans() {
        System.out.println("1.View All Plans  2.Create Plan");
        int planChoice = readInt();
        if (planChoice == 1) {
            for (MembershipPlan p : membershipPlanService.getAllPlans()) {
                System.out.println("Plan ID: " + p.getPlanId() + ", Name: " + p.getPlanName() + ", Amount: Rs." + p.getAmount());
            }
        } else if (planChoice == 2) {
            System.out.println("Enter plan name");
            String planName = sc.nextLine().trim();
            System.out.println("Enter plan amount");
            double planAmount = readDouble();
            String planReason = membershipPlanService.getCreatePlanFailureReason(currentUser, planName, planAmount);
            if (planReason != null) {
                System.out.println("Unable to create plan: " + planReason);
            } else {
                MembershipPlan created = membershipPlanService.createPlan(currentUser, planName, planAmount);
                System.out.println(created != null ? "Plan created successfully" : "Unable to create plan: Could not save membership plan.");
            }
        } else {
            System.out.println("Invalid choice");
        }
    }
}
