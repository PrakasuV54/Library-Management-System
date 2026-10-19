package ui;

import enums.*;
import model.*;

import java.util.List;

public class Librarian extends Main {

    public static void librarian() {
        if (currentUser == null || currentUser.getStatus() != UserStatus.ACTIVE
                || !authService.hasRole(currentUser, UserRole.LIBRARIAN)) {
            System.out.println("Please log in as Librarian or Admin");
            return;
        }
        while (true) {
            System.out.println("\nLibrarian Menu");
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
            System.out.println("25.Logout");
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
                case 12: handleAddBook(); break;
                case 13: handleRemoveBook(); break;
                case 14: handleViewPendingRequests(); break;
                case 15: handleMarkReadyForPickup(); break;
                case 16: handleIssueBook(); break;
                case 17: handleProcessReturn(); break;
                case 18: handleMarkLost(); break;
                case 19: handleMarkDamaged(); break;
                case 20: handleViewDefaulters(); break;
                case 21: viewCurrentFines(); break;
                case 22: manageCurrentFines(); break;
                case 23: handleManageMembershipStatus(); break;
                case 24: handleManagePayments(); break;
                case 25:
                    currentUser = null;
                    System.out.println("Logged out");
                    return;
                default:
                    System.out.println("Invalid choice");
            }
        }
    }

    public static void handleAddBook() {
        System.out.println("Enter ISBN"); String isbn = sc.nextLine().trim();
        System.out.println("Enter title"); String title = sc.nextLine().trim();
        System.out.println("Enter author(s) (comma-separated if multiple)"); String authorInput = sc.nextLine().trim();
        System.out.println("Enter genre(s) (comma-separated if multiple)"); String genreInput = sc.nextLine().trim();
        System.out.println("Enter publisher"); String publisher = sc.nextLine().trim();
        System.out.println("Enter publication year"); int year = readInt();
        System.out.println("Enter description"); String description = sc.nextLine().trim();
        System.out.println("Enter number of copies"); int copies = readInt();
        if (isbn.isEmpty()) {
            System.out.println("Invalid book details: ISBN cannot be empty."); return;
        }
        if (title.isEmpty()) {
            System.out.println("Invalid book details: Title cannot be empty."); return;
        }
        if (authorInput.isEmpty()) {
            System.out.println("Invalid book details: Author cannot be empty."); return;
        }
        if (publisher.isEmpty()) {
            System.out.println("Invalid book details: Publisher cannot be empty."); return;
        }
        if (copies < 1) {
            System.out.println("Invalid book details: Number of copies must be at least 1."); return;
        }
        Book newBook = new Book(isbn, title, publisher, year, description);
        for (String aName : authorInput.split(",")) {
            String aTrimmed = aName.trim();
            if (!aTrimmed.isEmpty()) {
                Author a = bookService.getOrCreateAuthor(aTrimmed);
                if (a != null) newBook.addAuthor(a);
            }
        }
        if (!genreInput.isEmpty()) {
            for (String gName : genreInput.split(",")) {
                String gTrimmed = gName.trim();
                if (!gTrimmed.isEmpty()) {
                    Genre g = bookService.getOrCreateGenre(gTrimmed);
                    if (g != null) newBook.addGenre(g);
                }
            }
        }
        String addReason = bookService.getAddBookFailureReason(newBook, copies, currentUser);
        if (addReason != null) {
            System.out.println("Unable to add book: " + addReason);
        } else if (bookService.addBook(newBook, copies, currentUser)) {
            System.out.println("Book added successfully");
        } else {
            System.out.println("Unable to add book: Operation failed.");
        }
    }

    public static void handleRemoveBook() {
        System.out.println("Enter ISBN to remove"); String removeIsbn = sc.nextLine().trim();
        if (removeIsbn.isEmpty()) {
            System.out.println("ISBN cannot be empty"); return;
        }
        Book removeBook = bookService.getBookByIsbn(removeIsbn);
        if (removeBook == null) {
            System.out.println("Book not found with ISBN: " + removeIsbn);
        } else {
            String deleteReason = bookService.getDeleteBookFailureReason(removeBook.getBookId(), currentUser);
            if (deleteReason != null) {
                System.out.println("Unable to remove book: " + deleteReason);
            } else if (bookService.deleteBook(removeBook.getBookId(), currentUser)) {
                System.out.println("Book removed");
            } else {
                System.out.println("Unable to remove book: Operation failed.");
            }
        }
    }

    public static void handleViewPendingRequests() {
        List<Transaction> pending = bookTransactionService.getAllPendingRequests(currentUser);
        if (pending.isEmpty()) System.out.println("No pending requests");
        for (Transaction t : pending) {
            Book requestedBook = bookService.getBookById(t.getBookId());
            User member = userRepository.findByPhone(t.getUserPhone());
            System.out.println("Request ID: " + t.getTransactionId() + ", Member: "
                    + (member == null ? t.getUserPhone() : member.getName()) + ", ISBN: "
                    + (requestedBook == null ? "Unknown" : requestedBook.getIsbn()) + ", Status: " + t.getStatus()
                    + (t.getBookCopyId() > 0 ? (", Copy ID: " + t.getBookCopyId()) : ""));
        }
        if (!pending.isEmpty()) {
            System.out.println("1.Assign copy to a waiting request  2.Back");
            int assignChoice = readInt();
            if (assignChoice == 1) {
                System.out.println("Enter request ID to assign copy:");
                int reqId = readInt();
                System.out.println("Enter copy ID to assign:");
                int copyId = readInt();
                String assignReason = bookTransactionService.getCopyAssignmentFailureReason(reqId, copyId, currentUser);
                if (assignReason != null) {
                    System.out.println("Unable to assign copy: " + assignReason);
                } else if (bookTransactionService.assignCopy(reqId, copyId, currentUser)) {
                    System.out.println("Copy assigned successfully (Status: RESERVED)");
                } else {
                    System.out.println("Unable to assign copy: Copy could not be assigned to this request.");
                }
            }
        }
    }

    public static void handleMarkReadyForPickup() {
        System.out.println("Enter request ID to mark ready for pickup"); int readyId = readInt();
        String readyReason = bookTransactionService.getMarkReadyFailureReason(readyId, currentUser);
        if (readyReason != null) {
            System.out.println("Unable to mark ready: " + readyReason);
        } else if (bookTransactionService.markReadyForPickup(readyId, currentUser)) {
            System.out.println("Marked ready");
        } else {
            System.out.println("Unable to mark ready: Request could not transition to ready state.");
        }
    }

    public static void handleIssueBook() {
        System.out.println("Enter request ID to issue"); int issueId = readInt();
        String issueReason = bookTransactionService.getIssueFailureReason(issueId, currentUser);
        if (issueReason != null) {
            System.out.println("Unable to issue book: " + issueReason);
        } else if (bookTransactionService.issueBook(issueId, currentUser)) {
            System.out.println("Book issued");
        } else {
            System.out.println("Unable to issue book: Book loan could not be issued.");
        }
    }

    public static void handleProcessReturn() {
        System.out.println("Enter transaction ID for return"); int returnId = readInt();
        String returnReason = bookTransactionService.getReturnFailureReason(returnId, currentUser);
        if (returnReason != null) {
            System.out.println("Unable to process return: " + returnReason);
        } else if (bookTransactionService.returnBook(returnId, currentUser)) {
            System.out.println("Return processed");
        } else {
            System.out.println("Unable to process return: Return transaction could not be recorded.");
        }
    }

    public static void handleMarkLost() {
        System.out.println("Enter copy ID to mark lost"); int lostId = readInt();
        String lostReason = bookTransactionService.getMarkConditionFailureReason(lostId, currentUser, true);
        if (lostReason != null) {
            System.out.println("Unable to mark copy lost: " + lostReason);
        } else if (bookTransactionService.markCopyLost(lostId, currentUser)) {
            System.out.println("Copy marked lost");
        } else {
            System.out.println("Unable to mark copy lost: Copy cannot be marked lost.");
        }
    }

    public static void handleMarkDamaged() {
        System.out.println("Enter copy ID to mark damaged"); int damagedId = readInt();
        String damagedReason = bookTransactionService.getMarkConditionFailureReason(damagedId, currentUser, false);
        if (damagedReason != null) {
            System.out.println("Unable to mark copy damaged: " + damagedReason);
        } else if (bookTransactionService.markCopyDamaged(damagedId, currentUser)) {
            System.out.println("Copy marked damaged");
        } else {
            System.out.println("Unable to mark copy damaged: Copy cannot be marked damaged.");
        }
    }

    public static void handleViewDefaulters() {
        List<User> defaulters = policyService.getDefaulters(currentUser);
        if (defaulters.isEmpty()) System.out.println("No defaulters found");
        for (User user : defaulters) System.out.println(user.getName() + " | Phone: " + user.getPhone() + " | Email: " + user.getEmail());
    }

    public static void handleManageMembershipStatus() {
        System.out.println("Enter member phone number"); String memberPhone = sc.nextLine().trim();
        User member = userRepository.findByPhone(memberPhone);
        if (member == null || member.getRole() != UserRole.MEMBER) { System.out.println("Member not found"); return; }
        System.out.println("1.Suspend membership  2.Reactivate membership");
        int membershipChoice = readInt();
        if (membershipChoice == 1) {
            String suspendReason = membershipService.getSuspendFailureReason(currentUser, memberPhone);
            if (suspendReason != null) {
                System.out.println("Unable to update membership status: " + suspendReason);
            } else if (membershipService.suspendMembership(currentUser, memberPhone)) {
                System.out.println("Membership status updated");
            } else {
                System.out.println("Unable to update membership status: Could not suspend active membership.");
            }
        } else if (membershipChoice == 2) {
            String reactivateReason = membershipService.getReactivateFailureReason(currentUser, memberPhone);
            if (reactivateReason != null) {
                System.out.println("Unable to update membership status: " + reactivateReason);
            } else if (membershipService.reactivateMembership(currentUser, memberPhone)) {
                System.out.println("Membership status updated");
            } else {
                System.out.println("Unable to update membership status: Could not reactivate suspended membership.");
            }
        } else {
            System.out.println("Invalid choice");
        }
    }

    public static void handleManagePayments() {
        for (Payment payment : paymentRepository.findAll()) {
            System.out.println("Payment ID: " + payment.getPaymentId() + ", Phone: " + payment.getUserPhone()
                    + ", Amount: Rs." + payment.getAmount() + ", Status: " + payment.getStatus());
        }
        System.out.println("Enter payment ID to refund, or 0 to return"); int paymentId = readInt();
        if (paymentId != 0) {
            String refundReason = paymentService.getRefundFailureReason(paymentId, currentUser);
            if (refundReason != null) {
                System.out.println("Refund failed: " + refundReason);
            } else if (paymentService.refund(paymentId, currentUser)) {
                System.out.println("Refund processed");
            } else {
                System.out.println("Refund failed: Payment gateway error during refund.");
            }
        }
    }

    public static void viewCurrentFines() {
        while (true) {
            System.out.println("\n--- View Current Fines ---");
            System.out.println("1.All Fines");
            System.out.println("2.Pending Fines");
            System.out.println("3.Partially Paid Fines");
            System.out.println("4.Paid Fines");
            System.out.println("5.Waived Fines");
            System.out.println("6.Filter by Member Phone");
            System.out.println("7.Filter by Transaction ID");
            System.out.println("8.Back");
            System.out.println("Enter choice:");

            int filterChoice = readInt();
            if (filterChoice == 8) {
                return;
            }

            List<Fine> finesToDisplay;
            switch (filterChoice) {
                case 1:
                    finesToDisplay = fineService.getAllFines(currentUser);
                    break;
                case 2:
                    finesToDisplay = fineService.getFinesByStatus(FineStatus.PENDING, currentUser);
                    break;
                case 3:
                    finesToDisplay = fineService.getFinesByStatus(FineStatus.PARTIALLY_PAID, currentUser);
                    break;
                case 4:
                    finesToDisplay = fineService.getFinesByStatus(FineStatus.PAID, currentUser);
                    break;
                case 5:
                    finesToDisplay = fineService.getFinesByStatus(FineStatus.WAIVED, currentUser);
                    break;
                case 6:
                    System.out.println("Enter member phone number:");
                    String phone = sc.nextLine().trim();
                    finesToDisplay = fineService.findByUserPhone(phone, currentUser);
                    break;
                case 7:
                    System.out.println("Enter transaction ID:");
                    int txId = readInt();
                    finesToDisplay = fineService.findByTransactionId(txId, currentUser);
                    break;
                default:
                    System.out.println("Invalid choice");
                    continue;
            }

            if (finesToDisplay.isEmpty()) {
                System.out.println("No fines found");
            } else {
                System.out.println("\n--- Fines List (" + finesToDisplay.size() + ") ---");
                for (Fine fine : finesToDisplay) {
                    printFineSummary(fine);
                }
            }
        }
    }

    static void printFineSummary(Fine fine) {
        User member = userRepository.findByPhone(fine.getUserPhone());
        int memberId = fine.getUserId() > 0 ? fine.getUserId() : (member != null ? member.getUserId() : 0);
        String memberIdDisplay = memberId > 0 ? String.valueOf(memberId) : "N/A";
        System.out.println("Fine ID: " + fine.getFineId()
                + " | Member ID: " + memberIdDisplay
                + " | Phone: " + fine.getUserPhone()
                + " | Transaction ID: " + fine.getTransactionId()
                + " | Type: " + fine.getType()
                + " | Original Amount: Rs." + String.format("%.2f", fine.getAmount())
                + " | Paid: Rs." + String.format("%.2f", fine.getAmountPaid())
                + " | Outstanding Balance: Rs." + String.format("%.2f", fine.getRemainingAmount())
                + " | Status: " + fine.getStatus());
    }

    public static void manageCurrentFines() {
        System.out.println("\n--- Manage Current Fines ---");
        System.out.println("Enter member phone number:");
        String phone = sc.nextLine().trim();
        if (phone.isEmpty()) {
            System.out.println("Phone number cannot be empty.");
            return;
        }

        User member = userRepository.findByPhone(phone);
        if (member == null) {
            System.out.println("Member not found with phone: " + phone);
            return;
        }

        while (true) {
            List<Fine> fines = fineService.findByUserPhone(phone, currentUser);
            if (fines == null || fines.isEmpty()) {
                System.out.println("No fines found for member: " + phone);
                return;
            }

            System.out.println("\n--- Fines for Member " + phone + " ---");
            for (int i = 0; i < fines.size(); i++) {
                Fine f = fines.get(i);
                System.out.println((i + 1) + ". Fine ID: " + f.getFineId()
                        + " | Transaction ID: " + f.getTransactionId()
                        + " | Fine Amount: Rs." + String.format(java.util.Locale.ROOT, "%.2f", f.getAmount())
                        + " | Amount Paid: Rs." + String.format(java.util.Locale.ROOT, "%.2f", f.getAmountPaid())
                        + " | Remaining Amount: Rs." + String.format(java.util.Locale.ROOT, "%.2f", f.getRemainingAmount())
                        + " | Status: " + f.getStatus());
            }

            System.out.println("Enter the fine number to manage, or 0 to go back:");
            int selection = readInt();
            if (selection == 0) {
                return;
            }
            if (selection < 1 || selection > fines.size()) {
                System.out.println("Invalid selection. Selection outside the displayed list.");
                continue;
            }

            Fine selectedFine = fines.get(selection - 1);
            int fineId = selectedFine.getFineId();

            manageSelectedFine(fineId);
        }
    }

    private static void manageSelectedFine(int fineId) {
        while (true) {
            Fine fine = fineService.findById(fineId, currentUser);
            if (fine == null) {
                System.out.println("Fine not found with ID: " + fineId);
                return;
            }

            System.out.println("\n--- Fine Details ---");
            System.out.println("Fine ID: " + fine.getFineId()
                    + " | Transaction ID: " + fine.getTransactionId()
                    + " | Fine Amount: Rs." + String.format(java.util.Locale.ROOT, "%.2f", fine.getAmount())
                    + " | Amount Paid: Rs." + String.format(java.util.Locale.ROOT, "%.2f", fine.getAmountPaid())
                    + " | Remaining Amount: Rs." + String.format(java.util.Locale.ROOT, "%.2f", fine.getRemainingAmount())
                    + " | Status: " + fine.getStatus());

            System.out.println("\nActions for Fine ID: " + fineId);
            System.out.println("1.Pay Fine");
            System.out.println("2.Waive Fine");
            System.out.println("3.Back");
            System.out.println("Enter choice:");

            int action = readInt();
            switch (action) {
                case 1:
                    paySelectedFine(fineId);
                    return;
                case 2:
                    waiveSelectedFine(fineId);
                    return;
                case 3:
                case 0:
                    return;
                default:
                    System.out.println("Invalid choice. Please select 1, 2, or 3.");
            }
        }
    }

    private static void paySelectedFine(int fineId) {
        Fine fine = fineService.findById(fineId, currentUser);
        if (fine == null) {
            System.out.println("Fine not found with ID: " + fineId);
            return;
        }

        if (fine.getStatus() == FineStatus.PAID) {
            System.out.println("Fine is already fully paid");
            return;
        }
        if (fine.getStatus() == FineStatus.WAIVED) {
            System.out.println("Fine has been waived and cannot be paid");
            return;
        }

        System.out.println("Remaining Balance: Rs." + String.format(java.util.Locale.ROOT, "%.2f", fine.getRemainingAmount()));
        System.out.println("Enter payment amount:");
        double amount = readDouble();
        if (amount <= 0) {
            System.out.println("Invalid payment amount: Amount must be greater than zero.");
            return;
        }
        if (amount > fine.getRemainingAmount()) {
            System.out.println("Invalid payment amount: Rs." + String.format(java.util.Locale.ROOT, "%.2f", amount)
                    + " exceeds outstanding balance of Rs."
                    + String.format(java.util.Locale.ROOT, "%.2f", fine.getRemainingAmount()) + ".");
            return;
        }

        PaymentMethod method = UiHelpers.choosePaymentMethod();
        if (method == null) {
            System.out.println("Payment cancelled");
            return;
        }

        Payment payment = paymentService.payFine(fine.getUserPhone(), fineId, amount, method);
        if (payment != null && payment.isSuccessful()) {
            System.out.println("Payment recorded successfully (Payment ID: " + payment.getPaymentId()
                    + ", Reference: " + payment.getTransactionReference() + ")");
            Fine updatedFine = fineService.findById(fineId, currentUser);
            if (updatedFine != null) {
                System.out.println("\n--- Updated Fine Details ---");
                System.out.println("Fine ID: " + updatedFine.getFineId()
                        + " | Transaction ID: " + updatedFine.getTransactionId()
                        + " | Fine Amount: Rs." + String.format(java.util.Locale.ROOT, "%.2f", updatedFine.getAmount())
                        + " | Amount Paid: Rs." + String.format(java.util.Locale.ROOT, "%.2f", updatedFine.getAmountPaid())
                        + " | Remaining Amount: Rs." + String.format(java.util.Locale.ROOT, "%.2f", updatedFine.getRemainingAmount())
                        + " | Status: " + updatedFine.getStatus());
            }
        } else {
            String payReason = paymentService.getPayFineFailureReason(fine.getUserPhone(), fineId, amount, method);
            System.out.println("Payment failed: " + (payReason != null ? payReason : "Payment gateway was unable to process the transaction."));
        }
    }

    private static void waiveSelectedFine(int fineId) {
        Fine fine = fineService.findById(fineId, currentUser);
        if (fine == null) {
            System.out.println("Fine not found with ID: " + fineId);
            return;
        }
        if (fine.getStatus() == FineStatus.PAID) {
            System.out.println("Paid fine cannot be waived");
            return;
        }
        if (fine.getStatus() == FineStatus.WAIVED) {
            System.out.println("Fine is already waived");
            return;
        }

        if (fineService.waiveFine(fineId, currentUser)) {
            System.out.println("Fine waived successfully");
            Fine updated = fineService.findById(fineId, currentUser);
            if (updated != null) {
                System.out.println("\n--- Updated Fine Details ---");
                System.out.println("Fine ID: " + updated.getFineId()
                        + " | Transaction ID: " + updated.getTransactionId()
                        + " | Fine Amount: Rs." + String.format(java.util.Locale.ROOT, "%.2f", updated.getAmount())
                        + " | Amount Paid: Rs." + String.format(java.util.Locale.ROOT, "%.2f", updated.getAmountPaid())
                        + " | Remaining Amount: Rs." + String.format(java.util.Locale.ROOT, "%.2f", updated.getRemainingAmount())
                        + " | Status: " + updated.getStatus());
            }
        } else {
            String waiveReason = fineService.getWaiveFineFailureReason(fineId, currentUser);
            System.out.println("Unable to waive fine: " + (waiveReason != null ? waiveReason : "Fine status could not be changed to waived."));
        }
    }

    static void viewSingleFineDetails() {
        System.out.println("Enter Fine ID:");
        int fineId = readInt();
        Fine fine = fineService.findById(fineId, currentUser);
        if (fine == null) {
            System.out.println("Fine not found");
            return;
        }

        User member = userRepository.findByPhone(fine.getUserPhone());
        Transaction transaction = transactionRepository.findById(fine.getTransactionId());
        Book book = transaction != null ? bookService.getBookById(transaction.getBookId()) : null;

        System.out.println("\n--- Fine Details ---");
        System.out.println("Fine ID: " + fine.getFineId());
        System.out.println("Member: " + (member != null ? member.getName() : "Unknown")
                + " (Phone: " + fine.getUserPhone()
                + ", Member ID: " + (fine.getUserId() > 0 ? fine.getUserId() : (member != null ? member.getUserId() : "N/A")) + ")");
        System.out.println("Transaction ID: " + fine.getTransactionId()
                + " (Status: " + (transaction != null ? transaction.getStatus() : "Unknown") + ")");
        if (book != null) {
            System.out.println("Book: " + book.getTitle() + " (ISBN: " + book.getIsbn() + ")");
        }
        System.out.println("Fine Type: " + fine.getType());
        System.out.println("Created At: " + fine.getCreatedAt());
        System.out.println("Original Amount: Rs." + String.format("%.2f", fine.getAmount()));
        System.out.println("Amount Paid: Rs." + String.format("%.2f", fine.getAmountPaid()));
        System.out.println("Outstanding Balance: Rs." + String.format("%.2f", fine.getRemainingAmount()));
        System.out.println("Status: " + fine.getStatus());
    }

    static void recordFinePayment() {
        System.out.println("Enter Fine ID to record payment:");
        int fineId = readInt();
        Fine fine = fineService.findById(fineId, currentUser);
        if (fine == null) {
            System.out.println("Fine not found with ID: " + fineId);
            return;
        }

        if (fine.getStatus() == FineStatus.PAID) {
            System.out.println("Fine is already fully paid");
            return;
        }
        if (fine.getStatus() == FineStatus.WAIVED) {
            System.out.println("Fine has been waived and cannot be paid");
            return;
        }

        System.out.println("Fine ID: " + fine.getFineId()
                + ", Member Phone: " + fine.getUserPhone()
                + ", Total Amount: Rs." + String.format("%.2f", fine.getAmount())
                + ", Already Paid: Rs." + String.format("%.2f", fine.getAmountPaid())
                + ", Outstanding Balance: Rs." + String.format("%.2f", fine.getRemainingAmount()));

        System.out.println("Enter payment amount:");
        double amount = readDouble();
        if (amount <= 0) {
            System.out.println("Invalid payment amount: Amount must be greater than zero.");
            return;
        }
        if (amount > fine.getRemainingAmount()) {
            System.out.println("Invalid payment amount: Rs." + amount + " exceeds outstanding balance of Rs."
                    + String.format("%.2f", fine.getRemainingAmount()) + ".");
            return;
        }

        PaymentMethod method = UiHelpers.choosePaymentMethod();
        if (method == null) {
            System.out.println("Payment cancelled");
            return;
        }

        Payment payment = paymentService.payFine(fine.getUserPhone(), fineId, amount, method);
        if (payment != null && payment.isSuccessful()) {
            System.out.println("Payment recorded successfully (Payment ID: " + payment.getPaymentId()
                    + ", Reference: " + payment.getTransactionReference() + ")");
            Fine updatedFine = fineService.findById(fineId, currentUser);
            if (updatedFine != null) {
                System.out.println("Updated Paid Amount: Rs." + String.format("%.2f", updatedFine.getAmountPaid())
                        + " | Outstanding Balance: Rs." + String.format("%.2f", updatedFine.getRemainingAmount())
                        + " | Status: " + updatedFine.getStatus());
            }
        } else {
            String payReason = paymentService.getPayFineFailureReason(fine.getUserPhone(), fineId, amount, method);
            System.out.println("Payment failed: " + (payReason != null ? payReason : "Payment gateway was unable to process the transaction."));
        }
    }

    static void waiveFineAction() {
        System.out.println("Enter Fine ID to waive:");
        int fineId = readInt();
        Fine fine = fineService.findById(fineId, currentUser);
        if (fine == null) {
            System.out.println("Fine not found with ID: " + fineId);
            return;
        }
        if (fine.getStatus() == FineStatus.PAID) {
            System.out.println("Paid fine cannot be waived");
            return;
        }
        if (fine.getStatus() == FineStatus.WAIVED) {
            System.out.println("Fine is already waived");
            return;
        }

        if (fineService.waiveFine(fineId, currentUser)) {
            System.out.println("Fine waived successfully");
            Fine updated = fineService.findById(fineId, currentUser);
            if (updated != null) {
                System.out.println("Updated Outstanding Balance: Rs." + String.format("%.2f", updated.getRemainingAmount())
                        + " | Status: " + updated.getStatus());
            }
        } else {
            String waiveReason = fineService.getWaiveFineFailureReason(fineId, currentUser);
            System.out.println("Unable to waive fine: " + (waiveReason != null ? waiveReason : "Fine status could not be changed to waived."));
        }
    }

    public static void memberOperations() {
        while (true) {
            System.out.println("\n--- Member Operations ---");
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
            System.out.println("12.Lookup Member Account");
            System.out.println("13.Back");
            System.out.println("Enter choice:");

            int choice = readInt();
            switch (choice) {
                case 1:
                    Member.handleViewAllBooks();
                    break;
                case 2:
                    Member.handleSearchBooks();
                    break;
                case 3:
                    Member.handleRequestBook();
                    break;
                case 4:
                    Member.handleCancelRequest();
                    break;
                case 5:
                    Member.handleRenewBook();
                    break;
                case 6:
                    Member.handleActivateMembership();
                    break;
                case 7:
                    Member.handleRenewMembership();
                    break;
                case 8:
                    Member.handleViewCurrentlyRentedBooks();
                    break;
                case 9:
                    Member.handleViewBorrowingHistory();
                    break;
                case 10:
                    Member.handleManageFines();
                    break;
                case 11:
                    Member.handleUpdateProfile();
                    break;
                case 12:
                    lookupMemberAccount();
                    break;
                case 13:
                    return;
                default:
                    System.out.println("Invalid choice");
            }
        }
    }

    static void lookupMemberAccount() {
        System.out.println("Enter member phone number");
        String lookupPhone = sc.nextLine().trim();
        User lookupMember = userRepository.findByPhone(lookupPhone);
        if (lookupMember == null) {
            System.out.println("Member not found");
            return;
        }
        System.out.println("Name: " + lookupMember.getName());
        System.out.println("Phone: " + lookupMember.getPhone());
        System.out.println("Email: " + lookupMember.getEmail());
        System.out.println("Account status: " + lookupMember.getStatus());
        Membership memberMembership = membershipService.getActiveMembership(lookupPhone, currentUser);
        System.out.println("Active membership: " + (memberMembership == null ? "None" : memberMembership.getStatus() + ", ends " + memberMembership.getEndDate()));
        System.out.println("Borrowing history:");
        List<Transaction> memberHistory = bookTransactionService.getBorrowingHistory(lookupPhone, currentUser);
        if (memberHistory.isEmpty()) System.out.println("No borrowing history");
        for (Transaction t : memberHistory) {
            Book memberBook = bookService.getBookById(t.getBookId());
            System.out.println("ISBN: " + (memberBook == null ? "Unknown" : memberBook.getIsbn()) + ", Status: " + t.getStatus() + ", Due: " + t.getDueDate());
        }
        System.out.println("Outstanding fines:");
        for (Fine fine : fineService.getOutstandingFines(lookupPhone, currentUser)) {
            System.out.println("Fine ID: " + fine.getFineId() + ", Remaining: Rs." + fine.getRemainingAmount() + ", Status: " + fine.getStatus());
        }
    }
}
