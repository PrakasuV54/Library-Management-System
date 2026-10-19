package ui;

import enums.PaymentMethod;
import enums.PaymentStatus;
import enums.TransactionStatus;
import enums.UserRole;
import enums.UserStatus;
import model.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import service.AuthService;

public class Member extends Main {

    public static void member() {
        System.out.println("1.Login or 2.Register or 3.Exit");
        int loginChoice = readInt();

        switch (loginChoice) {
            case 1:
                System.out.println("Enter Mobile or Email");
                String identifier = sc.nextLine().trim();
                System.out.println("Enter Password");
                String loginPassword = sc.nextLine();

                if (identifier.isEmpty() || loginPassword.isEmpty()) {
                    System.out.println("Login Failed: Phone/Email and password cannot be empty. Please enter your credentials.");
                    break;
                }

                User user = authService.login(identifier, loginPassword);
                if (user != null) {
                    if (user.getRole() != UserRole.MEMBER) {
                        currentUser = null;
                        System.out.println("Login Failed: This account is registered as " + user.getRole() + ". Please log in via the Librarian or Admin menu.");
                        break;
                    }
                    if (user.getStatus() != UserStatus.ACTIVE) {
                        currentUser = null;
                        System.out.println("Login Failed: Your account has been deactivated. Please contact the librarian for reactivation.");
                        break;
                    }
                    currentUser = user;
                    System.out.println("Login Success");
                    memberMenu();
                } else {
                    // Check if account exists but is deactivated, without exposing password correctness
                    User existing = userRepository.findByPhone(identifier);
                    if (existing == null) existing = userRepository.findByEmail(identifier);
                    if (existing != null && existing.getStatus() == UserStatus.INACTIVE) {
                        System.out.println("Login Failed: Your account has been deactivated. Please contact the librarian for reactivation.");
                    } else {
                        System.out.println("Login Failed: Invalid credentials. Please check your phone/email and password, or register for a new account.");
                    }
                }
                break;

            case 2:
                System.out.println("Enter your name");
                String name = sc.nextLine().trim();
                if (name.isEmpty()) {
                    System.out.println("Registration failed: Name cannot be empty.");
                    break;
                }

                System.out.println("Enter your phone number");
                String phone = sc.nextLine().trim();
                if (!phone.matches("\\d{10}")) {
                    System.out.println("Registration failed: Phone number must be a valid 10-digit number.");
                    break;
                }
                if (userRepository.findByPhone(phone) != null) {
                    System.out.println("Registration failed: An account with phone number " + phone + " already exists. Please log in or use a different phone number.");
                    break;
                }

                System.out.println("Enter your email");
                String email = sc.nextLine().trim();
                if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
                    System.out.println("Registration failed: Invalid email format. Please provide a valid email address (e.g. user@example.com).");
                    break;
                }
                if (userRepository.findByEmail(email) != null) {
                    System.out.println("Registration failed: An account with email address " + email + " already exists. Please log in or use a different email address.");
                    break;
                }

                System.out.println("Enter your password");
                String registerPassword = sc.nextLine();
                if (registerPassword.length() < 8) {
                    System.out.println("Registration failed: Password must contain at least 8 characters.");
                    break;
                }

                System.out.println("Enter your password again");
                String confirmPassword = sc.nextLine();
                if (!registerPassword.equals(confirmPassword)) {
                    System.out.println("Registration failed: Passwords do not match.");
                    break;
                }

                if (authService.register(name, email, phone, confirmPassword)) {
                    System.out.println("Registered successfully");
                } else {
                    String regReason = userService.getRegistrationFailureReason(name, email, phone, confirmPassword);
                    System.out.println("Registration failed: " + (regReason != null ? regReason : "Unable to create user account. Please check your details."));
                }
                break;

            case 3:
                return;

            default:
                System.out.println("Invalid Input");
        }
    }

    public static void memberMenu() {
        while (true) {
            System.out.println("\n1.View All Books");
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
            System.out.println("12.Logout");
            System.out.println("Enter Your Choice");

            int choice = readInt();

            switch (choice) {
                case 1:
                    handleViewAllBooks();
                    break;
                case 2:
                    handleSearchBooks();
                    break;
                case 3:
                    handleRequestBook();
                    break;
                case 4:
                    handleCancelRequest();
                    break;
                case 5:
                    handleRenewBook();
                    break;
                case 6:
                    handleActivateMembership();
                    break;
                case 7:
                    handleRenewMembership();
                    break;
                case 8:
                    handleViewCurrentlyRentedBooks();
                    break;
                case 9:
                    handleViewBorrowingHistory();
                    break;
                case 10:
                    handleManageFines();
                    break;
                case 11:
                    handleUpdateProfile();
                    break;
                case 12:
                    currentUser = null;
                    System.out.println("Logged out successfully");
                    return;
                default:
                    System.out.println("Invalid Choice");
            }
        }
    }

    public static void handleViewAllBooks() {
        List<Book> allBooks = bookService.getAllBooks();
        if (allBooks.isEmpty()) {
            System.out.println("No Books Found");
            return;
        }
        UiHelpers.handleBookListInteraction(allBooks);
    }

    public static void handleSearchBooks() {
        UiHelpers.searchBooks();
    }

    public static void handleRequestBook() {
        System.out.println("Enter ISBN to request");
        String requestIsbn = sc.nextLine().trim();
        if (requestIsbn.isEmpty()) {
            System.out.println("ISBN cannot be empty");
            return;
        }

        Book targetBook = bookService.getBookByIsbn(requestIsbn);
        if (targetBook == null) {
            System.out.println("Request failed: Book with ISBN '" + requestIsbn + "' not found in the catalogue.");
            return;
        }

        List<String> borrowingBlockers = policyService.getBorrowingEligibilityFailureReasons(currentUser.getPhone());
        if (!borrowingBlockers.isEmpty()) {
            System.out.println("Borrowing failed:");
            for (String blocker : borrowingBlockers) {
                System.out.println("- " + blocker);
            }
            String advice = policyService.getBorrowingResolutionAdvice(borrowingBlockers);
            if (!advice.isEmpty()) {
                System.out.println("\n" + advice);
            }
            return;
        }

        Transaction requested = bookTransactionService.requestBookByIsbn(
                currentUser.getPhone(), requestIsbn, currentUser);
        if (requested == null) {
            System.out.println("Request failed: Unable to queue request for this book. Please contact the librarian.");
        } else {
            System.out.println("Book requested successfully");
            System.out.println("Status: " + requested.getStatus());
        }
    }

    public static void handleCancelRequest() {
        System.out.println("Enter ISBN to cancel request");
        String cancelIsbn = sc.nextLine().trim();
        if (cancelIsbn.isEmpty()) {
            System.out.println("ISBN cannot be empty");
            return;
        }

        Book cancelBook = bookService.getBookByIsbn(cancelIsbn);
        if (cancelBook == null) {
            System.out.println("Cancellation failed: Book with ISBN '" + cancelIsbn + "' not found in the catalogue.");
            return;
        }

        List<Transaction> userTxForBook = new ArrayList<>();
        for (Transaction t : transactionRepository.findByUserPhone(currentUser.getPhone())) {
            if (t.getBookId() == cancelBook.getBookId()) {
                userTxForBook.add(t);
            }
        }

        List<Transaction> cancellableRequests = new ArrayList<>();
        for (Transaction t : userTxForBook) {
            TransactionStatus status = t.getStatus();
            if (status == TransactionStatus.REQUESTED || status == TransactionStatus.RESERVED || status == TransactionStatus.READY_FOR_PICKUP) {
                cancellableRequests.add(t);
            }
        }

        if (cancellableRequests.isEmpty()) {
            if (userTxForBook.isEmpty()) {
                System.out.println("Cancellation failed: You have no requests or loans for '" + cancelBook.getTitle() + "'.");
            } else {
                Transaction lastTx = userTxForBook.get(userTxForBook.size() - 1);
                if (lastTx.getStatus() == TransactionStatus.ISSUED) {
                    System.out.println("Cancellation failed: Book '" + cancelBook.getTitle() + "' is already issued to you and cannot be cancelled. Please return the book instead.");
                } else if (lastTx.getStatus() == TransactionStatus.RETURNED) {
                    System.out.println("Cancellation failed: The loan for '" + cancelBook.getTitle() + "' has already been returned.");
                } else if (lastTx.getStatus() == TransactionStatus.CANCELLED) {
                    System.out.println("Cancellation failed: The request for '" + cancelBook.getTitle() + "' has already been cancelled.");
                } else if (lastTx.getStatus() == TransactionStatus.EXPIRED) {
                    System.out.println("Cancellation failed: The request for '" + cancelBook.getTitle() + "' has already expired.");
                } else {
                    System.out.println("Cancellation failed: No cancellable request found for this ISBN.");
                }
            }
            return;
        }

        if (cancellableRequests.size() > 1) {
            System.out.println("Cancellation failed: Multiple active requests found for this ISBN. Please contact the librarian to cancel a specific request.");
            return;
        }

        if (bookTransactionService.cancelRequest(currentUser.getPhone(), cancelIsbn, currentUser)) {
            System.out.println("Request cancelled successfully");
        } else {
            System.out.println("Cancellation failed: Unable to cancel the request. Please try again.");
        }
    }

    public static void handleRenewBook() {
        System.out.println("Enter ISBN to renew");
        String renewIsbn = sc.nextLine().trim();
        if (renewIsbn.isEmpty()) {
            System.out.println("ISBN cannot be empty");
            return;
        }

        Book renewBook = bookService.getBookByIsbn(renewIsbn);
        if (renewBook == null) {
            System.out.println("Renewal failed: Book with ISBN '" + renewIsbn + "' not found in the catalogue.");
            return;
        }

        List<Transaction> issuedTransactions =
                bookTransactionService.getCurrentlyBorrowedBooks(currentUser.getPhone(), currentUser);
        Transaction loanToRenew = null;

        for (Transaction t : issuedTransactions) {
            if (t.getBookId() == renewBook.getBookId()
                    && t.getStatus() == TransactionStatus.ISSUED) {
                if (loanToRenew != null) {
                    loanToRenew = null;
                    System.out.println("Renewal failed: Multiple active loans found for this ISBN. Contact the librarian.");
                    break;
                }
                loanToRenew = t;
            }
        }

        if (loanToRenew == null) {
            if (issuedTransactions.isEmpty()) {
                System.out.println("Renewal failed: You currently have no issued books.");
            } else {
                System.out.println("Renewal failed: No active issued loan found for '" + renewBook.getTitle() + "'.");
            }
            return;
        }

        String renewFailure = bookTransactionService.getRenewFailureReason(loanToRenew.getTransactionId(), currentUser);
        if (renewFailure != null) {
            System.out.println("Renewal failed: " + renewFailure);
            return;
        }

        if (bookTransactionService.renewBook(loanToRenew.getTransactionId(), currentUser)) {
            System.out.println("Book renewed successfully");
        } else {
            System.out.println("Renewal failed: Unable to renew loan. Please contact library staff.");
        }
    }

    public static void handleActivateMembership() {
        Membership existingActive = membershipService.getActiveMembership(currentUser.getPhone(), currentUser);
        if (existingActive != null) {
            System.out.println("You already have an active membership (Plan ID " + existingActive.getPlanId()
                    + ", expires on " + existingActive.getEndDate() + "). Please use Renew Membership to extend or change plans.");
            return;
        }

        List<MembershipPlan> plans = membershipPlanService.getAllPlans();
        if (plans.isEmpty()) {
            System.out.println("No membership plans available");
            return;
        }

        for (MembershipPlan plan : plans) {
            System.out.println(plan.getPlanId() + ". " + plan.getPlanName()
                    + " - Rs." + plan.getAmount());
        }

        System.out.println("Enter Plan ID");
        int planId = readInt();
        MembershipPlan selectedPlan = membershipPlanService.findById(planId);
        if (selectedPlan == null) {
            System.out.println("Invalid plan ID. Please select a valid plan from the list above.");
            return;
        }

        System.out.println("Enter duration in months");
        int duration = readInt();
        if (duration <= 0) {
            System.out.println("Duration must be greater than zero months.");
            return;
        }

        double totalCost = selectedPlan.getAmount() * duration;
        System.out.println("Selected Plan: " + selectedPlan.getPlanName());
        System.out.println("Monthly Price: ₹" + selectedPlan.getAmount());
        System.out.println("Duration: " + duration + " months");
        System.out.println("Total Amount: ₹" + totalCost);

        PaymentMethod membershipMethod = UiHelpers.choosePaymentMethod();
        if (membershipMethod == null) {
            return;
        }

        if (membershipService.getMembershipByPhone(
                currentUser.getPhone(), planId, duration, membershipMethod, currentUser)) {
            Membership activeMem = membershipService.getActiveMembership(currentUser.getPhone(), currentUser);
            System.out.println("Payment Status: SUCCESS");
            System.out.println("Membership Status: " + (activeMem != null ? activeMem.getStatus() : "ACTIVE"));
            if (activeMem != null && activeMem.getEndDate() != null) {
                System.out.println("Membership Expiry Date: " + activeMem.getEndDate());
            }
            System.out.println("Membership activated successfully");
        } else {
            System.out.println("Payment Status: FAILED");
            System.out.println("Membership activation failed: Payment processing failed. Please check your payment details or try another payment method.");
        }
    }

    public static void handleRenewMembership() {
        if (membershipService.getActiveMembership(currentUser.getPhone(), currentUser) == null) {
            System.out.println("You do not have an active membership to renew. Please use Activate Membership to purchase a plan.");
            return;
        }

        List<MembershipPlan> renewalPlans = membershipPlanService.getAllPlans();
        if (renewalPlans.isEmpty()) {
            System.out.println("No membership plans available");
            return;
        }

        for (MembershipPlan plan : renewalPlans) {
            System.out.println(plan.getPlanId() + ". " + plan.getPlanName()
                    + " - Rs." + plan.getAmount());
        }

        System.out.println("Enter Plan ID");
        int renewalPlanId = readInt();
        MembershipPlan selectedRenewalPlan = membershipPlanService.findById(renewalPlanId);
        if (selectedRenewalPlan == null) {
            System.out.println("Invalid plan ID. Please select a valid plan from the list above.");
            return;
        }

        System.out.println("Enter duration in months");
        int renewalDuration = readInt();
        if (renewalDuration <= 0) {
            System.out.println("Duration must be greater than zero months.");
            return;
        }

        double renewalTotalCost = selectedRenewalPlan.getAmount() * renewalDuration;
        System.out.println("Selected Plan: " + selectedRenewalPlan.getPlanName());
        System.out.println("Monthly Price: ₹" + selectedRenewalPlan.getAmount());
        System.out.println("Duration: " + renewalDuration + " months");
        System.out.println("Total Amount: ₹" + renewalTotalCost);

        PaymentMethod renewalMethod = UiHelpers.choosePaymentMethod();
        if (renewalMethod == null) {
            return;
        }

        if (membershipService.renewMembership(currentUser.getPhone(),
                renewalPlanId, renewalDuration, renewalMethod, currentUser)) {
            Membership activeMem = membershipService.getActiveMembership(currentUser.getPhone(), currentUser);
            System.out.println("Payment Status: SUCCESS");
            System.out.println("Membership Status: " + (activeMem != null ? activeMem.getStatus() : "ACTIVE"));
            if (activeMem != null && activeMem.getEndDate() != null) {
                System.out.println("Membership Expiry Date: " + activeMem.getEndDate());
            }
            System.out.println("Membership renewed successfully");
        } else {
            System.out.println("Payment Status: FAILED");
            System.out.println("Membership renewal failed: Payment processing failed. Please check your payment details or try another payment method.");
        }
    }

    public static void handleViewCurrentlyRentedBooks() {
        List<Transaction> borrowed =
                bookTransactionService.getCurrentlyBorrowedBooks(currentUser.getPhone(), currentUser);
        if (borrowed.isEmpty()) {
            System.out.println("You have no currently issued books");
            return;
        }

        for (Transaction t : borrowed) {
            Book borrowedBook = bookService.getBookById(t.getBookId());
            if (borrowedBook != null) {
                System.out.println("ISBN: " + borrowedBook.getIsbn());
                System.out.println("Title: " + borrowedBook.getTitle());
                System.out.println("Status: " + t.getStatus());
                System.out.println("Due Date: " + t.getDueDate());
                System.out.println("--------------------");
            }
        }
    }

    public static void handleViewBorrowingHistory() {
        List<Transaction> history =
                bookTransactionService.getBorrowingHistory(currentUser.getPhone(), currentUser);
        if (history.isEmpty()) {
            System.out.println("No borrowing history found");
            return;
        }

        for (Transaction t : history) {
            Book historyBook = bookService.getBookById(t.getBookId());
            if (historyBook != null) {
                System.out.println("ISBN: " + historyBook.getIsbn());
                System.out.println("Title: " + historyBook.getTitle());
                System.out.println("Status: " + t.getStatus());
                System.out.println("Request Date: " + t.getRequestDate());
                System.out.println("Due Date: " + t.getDueDate());
                System.out.println("--------------------");
            }
        }
    }

    public static void handleManageFines() {
        List<Fine> fineHistory = fineService.getFineHistory(currentUser.getPhone(), currentUser);
        if (fineHistory.isEmpty()) {
            System.out.println("No fines found");
            return;
        }

        List<Fine> outstandingFines = fineService.getOutstandingFines(currentUser.getPhone(), currentUser);
        for (int i = 0; i < fineHistory.size(); i++) {
            Fine fine = fineHistory.get(i);
            Transaction fineTransaction = transactionRepository.findById(fine.getTransactionId());
            Book fineBook = fineTransaction == null ? null
                    : bookService.getBookById(fineTransaction.getBookId());

            System.out.println((i + 1) + ". ISBN: "
                    + (fineBook == null ? "Unavailable" : fineBook.getIsbn())
                    + ", Type: " + fine.getType()
                    + ", Amount: Rs." + fine.getAmount()
                    + ", Paid: Rs." + fine.getAmountPaid()
                    + ", Remaining: Rs." + fine.getRemainingAmount()
                    + ", Status: " + fine.getStatus());
        }

        if (outstandingFines.isEmpty()) {
            System.out.println("No outstanding fines to pay");
            return;
        }

        System.out.println("Enter the ISBN for the fine you want to pay");
        String fineIsbn = sc.nextLine().trim();
        if (fineIsbn.isEmpty()) {
            System.out.println("ISBN cannot be empty");
            return;
        }

        List<Fine> matchingFines = new ArrayList<>();
        for (Fine fine : outstandingFines) {
            Transaction t = transactionRepository.findById(fine.getTransactionId());
            Book fineBook = t == null ? null : bookService.getBookById(t.getBookId());
            if (fineBook != null && fineBook.getIsbn().equalsIgnoreCase(fineIsbn)) {
                matchingFines.add(fine);
            }
        }

        if (matchingFines.isEmpty()) {
            System.out.println("No outstanding fine found for ISBN: " + fineIsbn);
            return;
        }
        if (matchingFines.size() > 1) {
            System.out.println("More than one outstanding fine exists for this ISBN. Contact the librarian to avoid paying the wrong fine");
            return;
        }

        Fine selectedFine = matchingFines.get(0);
        System.out.println("Remaining amount: Rs." + selectedFine.getRemainingAmount());
        System.out.println("Enter amount to pay");
        double amount = sc.nextDouble();
        sc.nextLine();
        if (amount <= 0) {
            System.out.println("Invalid payment amount: Amount must be greater than zero.");
            return;
        }
        if (amount > selectedFine.getRemainingAmount()) {
            System.out.println("Invalid payment amount: Rs." + amount + " exceeds the remaining fine balance of Rs." + selectedFine.getRemainingAmount() + ".");
            return;
        }

        PaymentMethod fineMethod = UiHelpers.choosePaymentMethod();
        if (fineMethod == null) {
            return;
        }

        Payment payment = paymentService.payFine(currentUser.getPhone(),
                selectedFine.getFineId(), amount, fineMethod);
        if (payment != null && payment.getStatus() == PaymentStatus.SUCCESS) {
            System.out.println("Fine payment successful");
        } else {
            System.out.println("Fine payment failed: Payment gateway was unable to process the transaction. Please try again.");
        }
    }

    public static void handleUpdateProfile() {
        System.out.println("1.Update Name");
        System.out.println("2.Update Email");
        System.out.println("3.Update Phone");
        System.out.println("4.Change Password");
        System.out.println("5.Back");
        System.out.println("Enter choice");
        int profileChoice = readInt();

        switch (profileChoice) {
            case 1:
                System.out.println("Enter new name");
                String newName = sc.nextLine().trim();
                if (newName.isEmpty()) {
                    System.out.println("Name cannot be empty");
                } else if (newName.equals(currentUser.getName())) {
                    System.out.println("Unable to update name: New name is identical to your current name.");
                } else if (userService.updateNameByPhone(newName, currentUser.getPhone(), currentUser)) {
                    currentUser = userRepository.findByPhone(currentUser.getPhone());
                    System.out.println("Name updated successfully");
                } else {
                    System.out.println("Unable to update name: Account session is inactive or unauthorized.");
                }
                break;

            case 2:
                System.out.println("Enter new email");
                String newEmail = sc.nextLine().trim();
                if (!newEmail.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
                    System.out.println("Invalid email format. Please enter a valid email address (e.g., user@example.com).");
                } else if (newEmail.equalsIgnoreCase(currentUser.getEmail())) {
                    System.out.println("Unable to update email: New email is identical to your current email.");
                } else if (userRepository.findByEmail(newEmail) != null) {
                    System.out.println("Unable to update email: The email address '" + newEmail + "' is already registered to another account.");
                } else if (userService.changeEmailByPhone(newEmail, currentUser.getPhone(), currentUser)) {
                    currentUser = userRepository.findByPhone(currentUser.getPhone());
                    System.out.println("Email updated successfully");
                } else {
                    System.out.println("Unable to update email: Account session is inactive or unauthorized.");
                }
                break;

            case 3:
                System.out.println("Enter new phone number");
                String newPhone = sc.nextLine().trim();
                if (!newPhone.matches("\\d{10}")) {
                    System.out.println("Enter a valid 10-digit phone number");
                } else if (newPhone.equals(currentUser.getPhone())) {
                    System.out.println("Unable to update phone: New phone number is identical to your current phone number.");
                } else if (userRepository.findByPhone(newPhone) != null) {
                    System.out.println("Unable to update phone: The phone number '" + newPhone + "' is already registered to another account.");
                } else if (userService.changePhoneByPhone(currentUser.getPhone(), newPhone, currentUser)) {
                    currentUser = userRepository.findByPhone(newPhone);
                    System.out.println("Phone updated successfully");
                } else {
                    System.out.println("Unable to update phone: Account session is inactive or unauthorized.");
                }
                break;

            case 4:
                System.out.println("Enter current password");
                String oldPassword = sc.nextLine();
                System.out.println("Enter new password");
                String newPassword = sc.nextLine();
                if (newPassword.length() < 8) {
                    System.out.println("Password must contain at least 8 characters");
                } else if (!AuthService.verifyPassword(oldPassword, currentUser.getPasswordHash())) {
                    System.out.println("Password change failed: Current password is incorrect.");
                } else if (AuthService.verifyPassword(newPassword, currentUser.getPasswordHash())) {
                    System.out.println("Password change failed: New password cannot be the same as your current password.");
                } else if (userService.changePasswordByPhone(
                        currentUser.getPhone(), oldPassword, newPassword, currentUser)) {
                    currentUser = userRepository.findByPhone(currentUser.getPhone());
                    System.out.println("Password changed successfully");
                } else {
                    System.out.println("Password change failed: Account session is inactive or unauthorized.");
                }
                break;

            case 5:
                break;

            default:
                System.out.println("Invalid choice");
        }
    }
}
