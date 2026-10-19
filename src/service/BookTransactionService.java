package service;

import enums.BookCopyStatus;
import enums.TransactionStatus;
import model.Book;
import model.BookCopy;
import model.Transaction;
import model.User;
import repository.BookCopyRepository;
import repository.BookRepository;
import repository.DataStore;
import repository.MembershipRepository;
import repository.TransactionRepository;
import java.time.LocalDate;
import java.util.List;

public class BookTransactionService {
    private final BookRepository books;
    private final BookCopyRepository copies;
    private final TransactionRepository transactions;
    private final PolicyService policy;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final FineService fineService;
    private final OverdueFineSchedulerService overdueFineSchedulerService;

    public BookTransactionService(BookRepository books, BookCopyRepository copies, TransactionRepository transactions, PolicyService policy) {
        this(books, copies, transactions, policy, null, null, null);
    }

    public BookTransactionService(BookRepository books, BookCopyRepository copies, TransactionRepository transactions, PolicyService policy,
    AuditService auditService, NotificationService notificationService) {
        this(books, copies, transactions, policy, auditService, notificationService, null);
    }

    public BookTransactionService(BookRepository books, BookCopyRepository copies, TransactionRepository transactions, PolicyService policy,
    AuditService auditService, NotificationService notificationService, FineService fineService) {
        this.books=books;
        this.copies=copies;
        this.transactions=transactions;
        this.policy=policy;
        this.auditService=auditService;
        this.notificationService=notificationService;
        this.fineService=fineService;
        repository.UserRepository userRepo = (policy != null) ? policy.getUserRepository() : null;
        if (this.notificationService != null && this.notificationService.getUserRepository() == null && userRepo != null) {
            this.notificationService.setUserRepository(userRepo);
        }
        if (fineService != null && notificationService != null) {
            this.overdueFineSchedulerService = new OverdueFineSchedulerService(transactions, books, fineService, notificationService, userRepo);
            this.overdueFineSchedulerService.startScheduler();
        } else {
            this.overdueFineSchedulerService = null;
        }
    }

    private void sendUserNotification(String phone, String message) {
        if (notificationService == null || phone == null || phone.isBlank()) return;
        String trimmed = phone.trim();
        User user = (policy != null && policy.getUserRepository() != null) ? policy.getUserRepository().findByPhone(trimmed) : null;
        if (user == null && policy != null && policy.getUserRepository() != null && trimmed.matches("\\d+")) {
            try {
                user = policy.getUserRepository().findById(Integer.parseInt(trimmed));
            } catch (NumberFormatException ignored) {}
        }
        if (user == null && notificationService.getUserRepository() != null) {
            user = notificationService.getUserRepository().findByPhone(trimmed);
            if (user == null && trimmed.matches("\\d+")) {
                try {
                    user = notificationService.getUserRepository().findById(Integer.parseInt(trimmed));
                } catch (NumberFormatException ignored) {}
            }
        }
        if (user != null && user.getName() != null && !user.getName().isBlank()) {
            notificationService.send(user.getName(), user.getPhone(), message);
        } else {
            notificationService.send(trimmed, message);
        }
    }

    public OverdueFineSchedulerService getOverdueFineSchedulerService() {
        return overdueFineSchedulerService;
    }

    public void processOverdueTransactions() {
        if (overdueFineSchedulerService != null) {
            overdueFineSchedulerService.processOverdueTransactions();
        }
    }


    private void record(String phone, String action, Transaction t, String details) {
        if (auditService != null) auditService.record(phone, action, "TRANSACTION", String.valueOf(t.getTransactionId()), details);
    }



    public Transaction requestBook(String phone, int bookId) {
        if (phone == null || phone.isBlank())
            return null;
        if (!policy.canRequestBook(phone) || books.findById(bookId) == null)
            return null;
        Transaction transaction = new Transaction(DataStore.getTransactionId(), phone, bookId);
        transactions.save(transaction);
        record(phone, "BOOK_REQUESTED", transaction, "Book ID " + bookId);
        sendUserNotification(phone, "Your book request has been queued.");
        notifyStaffIfWaitingRequests(bookId);
        return transaction;
    }

    public Transaction requestBookByIsbn(String phone, String isbn) {
        Book book = books.findByIsbn(isbn);
        return book == null ? null : requestBook(phone, book.getBookId());
    }

    public Transaction requestBookByIsbn(String phone, String isbn, User caller) {
        Book book = books.findByIsbn(isbn);
        return book == null ? null : requestBook(phone, book.getBookId());
    }

    public boolean assignCopy(int transactionId, int bookCopyId, User staff) {
        if (!policy.canManageInventory(staff))
            return false;
        Transaction t = transactions.findById(transactionId);
        if (t == null || t.getStatus() != TransactionStatus.REQUESTED)
            return false;
        if (!policy.canRequestBook(t.getUserPhone()))
            return false;
        BookCopy copy = copies.findById(bookCopyId);
        if (copy == null || copy.getBookId() != t.getBookId() || !copy.isAvailable())
            return false;
        if (transactions.findActiveByCopyId(bookCopyId) != null)
            return false;
        Transaction nextEligible = getNextEligibleRequest(t.getBookId());
        if (nextEligible != null && nextEligible.getTransactionId() != transactionId)
            return false;

        copy.reserve();
        t.reserve(copy.getBookCopyId());
        copies.save(copy);
        transactions.save(t);

        record(staff.getPhone(), "BOOK_COPY_ASSIGNED", t, "Assigned Copy ID " + bookCopyId);
        return true;
    }

    public boolean markReadyForPickup(int transactionId, User staff) {
        if (!policy.canManageInventory(staff))
            return false;
        Transaction t = transactions.findById(transactionId);
        if (t == null || t.getStatus() != TransactionStatus.RESERVED)
            return false;
        BookCopy copy = copies.findById(t.getBookCopyId());
        if (copy == null || copy.getStatus() != BookCopyStatus.RESERVED || copy.getBookId() != t.getBookId())
            return false;
        t.markReadyForPickup();
        transactions.save(t);
        record(staff.getPhone(), "BOOK_READY_FOR_PICKUP", t, "Copy ID " + t.getBookCopyId());
        sendUserNotification(t.getUserPhone(), "Your requested book is ready for pickup.");
        return true;
    }


    public boolean issueBook(int transactionId, User librarian) {
        if (!policy.canManageInventory(librarian))
            return false;
        Transaction t = transactions.findById(transactionId);
        if (t == null || t.getStatus() != TransactionStatus.READY_FOR_PICKUP || !policy.canIssueBook(t.getUserPhone()))
            return false;
        BookCopy copy = copies.findById(t.getBookCopyId());
        if (copy == null || copy.getStatus() != BookCopyStatus.RESERVED || copy.getBookId() != t.getBookId())
            return false;
        t.issue(LocalDate.now().plusDays(policy.getLoanPeriodDays()));
        copy.issue();
        copies.save(copy);
        transactions.save(t);
        record(librarian.getPhone(), "BOOK_ISSUED", t, "Copy ID " + copy.getBookCopyId() + ", due " + t.getDueDate());
        sendUserNotification(t.getUserPhone(), "Book issued. Due date: " + t.getDueDate());
        return true;
    }

    public boolean markCopyLost(int bookCopyId, User librarian) {
        return updateCopyCondition(bookCopyId, librarian, true);
    }

    public boolean markCopyDamaged(int bookCopyId, User librarian) {
        return updateCopyCondition(bookCopyId, librarian, false);
    }

    private boolean updateCopyCondition(int bookCopyId, User librarian, boolean lost) {
        if (!policy.canManageInventory(librarian))
            return false;
        String librarianPhone = librarian.getPhone();
        BookCopy copy = copies.findById(bookCopyId);
        if (copy == null || copy.getStatus() == BookCopyStatus.LOST || copy.getStatus() == BookCopyStatus.DAMAGED)
            return false;
        Transaction activeResult = transactions.findActiveByCopyId(bookCopyId);
        Transaction transaction = activeResult;
        boolean wasIssued = transaction != null && transaction.getStatus() == TransactionStatus.ISSUED;
        String action = lost ? "BOOK_COPY_MARKED_LOST" : "BOOK_COPY_MARKED_DAMAGED";

        if (wasIssued && fineService == null)
            return false;
        if ((copy.getStatus() == BookCopyStatus.RESERVED) && transaction == null)
            return false;
        if (transaction != null && transaction.getStatus() == TransactionStatus.ISSUED && copy.getStatus() != BookCopyStatus.ISSUED)
            return false;
        if (transaction != null && (transaction.getStatus() == TransactionStatus.RESERVED || transaction.getStatus() == TransactionStatus.READY_FOR_PICKUP) && copy.getStatus() != BookCopyStatus.RESERVED)
            return false;

        if (transaction != null) {
            if (wasIssued) {
                if (lost) transaction.markLost();
                else transaction.markDamaged();
            } else {
                transaction.cancel();
            }
            transactions.save(transaction);
        }

        if (lost) copy.markLost();
        else copy.markDamaged();
        copies.save(copy);

        if (auditService != null) {
            String details = "Copy marked " + (lost ? "lost" : "damaged") + " by staff";
            if (transaction != null) details += "; transaction ID " + transaction.getTransactionId();
            auditService.record(librarianPhone, action, "BOOK_COPY", String.valueOf(bookCopyId), details);
        }

        if (wasIssued && fineService != null) {
            if (lost) fineService.createLostBookFine(transaction.getTransactionId());
            else fineService.createDamagedBookFine(transaction.getTransactionId());
            sendUserNotification(transaction.getUserPhone(), lost ? "Your issued book has been marked lost. Please check your outstanding fine." : "Your issued book has been marked damaged. Please check your outstanding fine.");
        }

        notifyStaffIfWaitingRequests(copy.getBookId());
        return true;
    }

    public boolean returnBook(int transactionId, User staff) {
        if (!policy.canManageInventory(staff)) return false;
        Transaction t = transactions.findById(transactionId);
        if (t == null || t.getStatus() != TransactionStatus.ISSUED) return false;
        BookCopy copy = copies.findById(t.getBookCopyId());
        if (copy == null || copy.getStatus() != BookCopyStatus.ISSUED || copy.getBookId() != t.getBookId()) return false;

        LocalDate asOfDate = overdueFineSchedulerService != null ? overdueFineSchedulerService.getCurrentDate() : LocalDate.now();
        if (t.getDueDate() != null && asOfDate.isAfter(t.getDueDate())) {
            if (fineService == null) return false;
            fineService.createOverdueFine(t.getTransactionId(), asOfDate);
        }

        t.returnBook();
        copy.returnCopy();
        copies.save(copy);
        transactions.save(t);

        record(staff.getPhone(), "BOOK_RETURNED", t, "Copy ID " + copy.getBookCopyId());
        sendUserNotification(t.getUserPhone(), "Your returned book has been processed successfully.");

        notifyStaffIfWaitingRequests(t.getBookId());
        return true;
    }

    public boolean cancelRequest(String phone, String isbn, User caller) {
        if (phone == null || phone.isBlank() || isbn == null || isbn.isBlank() || caller == null) return false;

        Book book = books.findByIsbn(isbn.trim());
        if (book == null) return false;

        Transaction match = null;
        for (Transaction transaction : transactions.findByUserPhone(phone)) {
            if (transaction.getBookId() != book.getBookId())
                continue;
            TransactionStatus status = transaction.getStatus();
            if (status != TransactionStatus.REQUESTED && status != TransactionStatus.RESERVED && status != TransactionStatus.READY_FOR_PICKUP)
                continue;

            if (match != null)
                return false; // Avoid cancelling the wrong request.
            match = transaction;
        }

        return match != null && cancelRequest(match.getTransactionId(), caller);
    }



    public boolean cancelRequest(int transactionId, User caller) {
        Transaction t = transactions.findById(transactionId);
        if (t == null || (t.getStatus() != TransactionStatus.REQUESTED && t.getStatus() != TransactionStatus.RESERVED && t.getStatus() != TransactionStatus.READY_FOR_PICKUP)) {
            return false;
        }
        if (caller == null || !policy.isOwnerOrStaff(caller, t.getUserPhone())) {
            return false;
        }
        int bookId = t.getBookId();
        int copyId = t.getBookCopyId();
        t.cancel();
        if (copyId > 0) {
            BookCopy copy = copies.findById(copyId);
            if (copy != null && copy.getStatus() == BookCopyStatus.RESERVED) {
                copy.releaseReservation();
                copies.save(copy);
            }
        }
        transactions.save(t);
        record(caller != null ? caller.getPhone() : t.getUserPhone(), "BOOK_REQUEST_CANCELLED", t, "Copy ID " + copyId);
        sendUserNotification(t.getUserPhone(), "Your book request has been cancelled.");
        notifyStaffIfWaitingRequests(bookId);
        return true;
    }



    public boolean renewBook(int transactionId, User caller) {
        Transaction transaction = transactions.findById(transactionId);
        if (transaction == null || transaction.getStatus() != TransactionStatus.ISSUED
                || transaction.getDueDate() == null || transaction.isOverdue()) {
            return false;
        }
        if (caller == null || !policy.isOwnerOrStaff(caller, transaction.getUserPhone())) {
            return false;
        }
        if (!policy.canRequestBook(transaction.getUserPhone())) {
            return false;
        }
        if (fineService != null && fineService.hasPendingFineForUser(transaction.getUserPhone())) {
            return false;
        }
        transaction.renew(transaction.getDueDate().plusDays(policy.getLoanPeriodDays()));
        transactions.save(transaction);
        record(caller != null ? caller.getPhone() : transaction.getUserPhone(), "BOOK_RENEWED", transaction, "New due date " + transaction.getDueDate());
        sendUserNotification(transaction.getUserPhone(), "Book renewed. New due date: " + transaction.getDueDate());
        return true;
    }


    public List<Transaction> getAllPendingRequests(User staff) {
        if (!policy.canManageInventory(staff)) return new java.util.ArrayList<>();
        return findPendingRequests();
    }


    private List<Transaction> findPendingRequests() {
        java.util.List<Transaction> result = new java.util.ArrayList<>();
        java.util.List<Transaction> requested = new java.util.ArrayList<>();
        java.util.List<Transaction> other = new java.util.ArrayList<>();
        for (Transaction transaction : transactions.findAll()) {
            if (transaction.getStatus() == TransactionStatus.REQUESTED) {
                requested.add(transaction);
            } else if (transaction.getStatus() == TransactionStatus.RESERVED
                    || transaction.getStatus() == TransactionStatus.READY_FOR_PICKUP) {
                other.add(transaction);
            }
        }
        requested.sort(getRequestPriorityComparator());
        result.addAll(requested);
        result.addAll(other);
        return result;
    }

    public List<Transaction> getCurrentlyBorrowedBooks(String phone, User caller) {
        if (caller == null || !policy.isOwnerOrStaff(caller, phone)) return new java.util.ArrayList<>();
        return findCurrentlyBorrowedBooks(phone);
    }

//    public List<Transaction> getCurrentlyBorrowedBooks(String phone) {
//
//        return new java.util.ArrayList<>();
//    }

    private java.util.List<Transaction> findCurrentlyBorrowedBooks(String phone) {
        java.util.List<Transaction> result = new java.util.ArrayList<>();
        for (Transaction transaction : transactions.findByUserPhone(phone)) {
            if (transaction.getStatus() == TransactionStatus.ISSUED) result.add(transaction);
        }
        return result;
    }

    public java.util.List<Transaction> getBorrowingHistory(String phone, User caller) {
        if (caller == null || !policy.isOwnerOrStaff(caller, phone))
            return new java.util.ArrayList<>();
        return transactions.findByUserPhone(phone);
    }

//    public java.util.List<Transaction> getBorrowingHistory(String phone) {
//        return new java.util.ArrayList<>();
//    }

    public boolean expireReservation(int transactionId, User staff) {
        if (!policy.canManageInventory(staff)) return false;
        Transaction t = transactions.findById(transactionId);
        if (t == null || (t.getStatus() != TransactionStatus.READY_FOR_PICKUP && t.getStatus() != TransactionStatus.RESERVED)) {
            return false;
        }
        int copyId = t.getBookCopyId();
        int bookId = t.getBookId();
        t.expire();
        if (copyId > 0) {
            BookCopy copy = copies.findById(copyId);
            if (copy != null && copy.getStatus() == BookCopyStatus.RESERVED) {
                copy.releaseReservation();
                copies.save(copy);
            }
        }
        transactions.save(t);
        record(t.getUserPhone(), "TRANSACTION_EXPIRED", t, "Reservation expired for copy ID " + copyId);
        sendUserNotification(t.getUserPhone(), "Your pickup reservation has expired.");
        notifyStaffIfWaitingRequests(bookId);
        return true;
    }


    public int expireStaleReservations(int days, User staff) {
        if (!policy.canManageInventory(staff) || days <= 0) return 0;
        int count = 0;
        java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusDays(days);
        for (Transaction t : transactions.findAll()) {
            if (t.getStatus() == TransactionStatus.READY_FOR_PICKUP && t.getReadyDate() != null && t.getReadyDate().isBefore(cutoff)) {
                if (expireReservation(t.getTransactionId(), staff)) {
                    count++;
                }
            }
        }
        return count;
    }


    public Transaction getNextEligibleRequest(int bookId) {
        java.util.List<Transaction> candidates = new java.util.ArrayList<>();
        for (Transaction t : transactions.findQueuedRequestsByBookId(bookId)) {
            if (t.getStatus() == TransactionStatus.REQUESTED && policy.canRequestBook(t.getUserPhone())) {
                candidates.add(t);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort(getRequestPriorityComparator());
        return candidates.get(0);
    }

    public Transaction getNextEligibleQueuedRequest(int bookId) {
        return getNextEligibleRequest(bookId);
    }

    public Transaction getNextEligibleRequest(int bookId, User staff) {
        if (!policy.canManageInventory(staff)) return null;
        return getNextEligibleRequest(bookId);
    }

    public java.util.List<Transaction> getPrioritizedPendingRequests(int bookId) {
        java.util.List<Transaction> candidates = new java.util.ArrayList<>();
        for (Transaction t : transactions.findQueuedRequestsByBookId(bookId)) {
            if (t.getStatus() == TransactionStatus.REQUESTED && policy.canRequestBook(t.getUserPhone())) {
                candidates.add(t);
            }
        }
        candidates.sort(getRequestPriorityComparator());
        return candidates;
    }

    public boolean assignNextEligibleRequest(int bookCopyId, User staff) {
        if (!policy.canManageInventory(staff)) return false;
        BookCopy copy = copies.findById(bookCopyId);
        if (copy == null || !copy.isAvailable()) return false;
        Transaction next = getNextEligibleRequest(copy.getBookId());
        if (next == null) return false;
        return assignCopy(next.getTransactionId(), bookCopyId, staff);
    }

    private java.util.Comparator<Transaction> getRequestPriorityComparator() {
        return (t1, t2) -> {
            int p1 = policy != null ? policy.getRequestQueuePriority(t1.getUserPhone()) : 2;
            int p2 = policy != null ? policy.getRequestQueuePriority(t2.getUserPhone()) : 2;
            if (p1 != p2) {
                return Integer.compare(p1, p2);
            }
            if (t1.getRequestDate() != null && t2.getRequestDate() != null) {
                int dateCmp = t1.getRequestDate().compareTo(t2.getRequestDate());
                if (dateCmp != 0) {
                    return dateCmp;
                }
            }
            return Integer.compare(t1.getTransactionId(), t2.getTransactionId());
        };
    }

    private void notifyStaffIfWaitingRequests(int bookId) {
        BookCopy available = copies.findAvailableCopy(bookId);
        if (available == null) return;
        boolean hasEligibleRequest = getNextEligibleRequest(bookId) != null;
        if (hasEligibleRequest && notificationService != null) {
            String msg = "Notice: An available copy (Copy ID " + available.getBookCopyId() + ") exists for Book ID " + bookId + " with eligible waiting requests.";
            if (policy != null) {
                for (User staff : policy.getActiveStaff()) {
                    if (staff != null) {
                        notificationService.send(staff.getName(), staff.getPhone(), msg);
                    }
                }
            }
        }
    }

    public String getCopyAssignmentFailureReason(int transactionId, int bookCopyId, User staff) {
        if (!policy.canManageInventory(staff)) {
            return "Insufficient permissions. Staff privileges required.";
        }
        Transaction t = transactions.findById(transactionId);
        if (t == null) {
            return "Request transaction with ID " + transactionId + " not found.";
        }
        if (t.getStatus() != TransactionStatus.REQUESTED) {
            return "Transaction is in " + t.getStatus() + " status, but must be in REQUESTED status.";
        }
        if (!policy.canRequestBook(t.getUserPhone())) {
            List<String> reasons = policy.getBorrowingEligibilityFailureReasons(t.getUserPhone());
            return "Member (" + t.getUserPhone() + ") is no longer eligible: " + String.join("; ", reasons);
        }
        BookCopy copy = copies.findById(bookCopyId);
        if (copy == null) {
            return "Book copy with ID " + bookCopyId + " not found.";
        }
        if (copy.getBookId() != t.getBookId()) {
            return "Copy ID " + bookCopyId + " belongs to Book ID " + copy.getBookId() + ", but request is for Book ID " + t.getBookId() + ".";
        }
        if (!copy.isAvailable()) {
            return "Copy ID " + bookCopyId + " is not available (Current status: " + copy.getStatus() + ").";
        }
        if (transactions.findActiveByCopyId(bookCopyId) != null) {
            return "Copy ID " + bookCopyId + " is already allocated to another active transaction.";
        }
        Transaction nextEligible = getNextEligibleRequest(t.getBookId());
        if (nextEligible != null && nextEligible.getTransactionId() != transactionId) {
            return "Request ID " + transactionId + " is not the next eligible request in the priority queue (Next eligible request ID: " + nextEligible.getTransactionId() + ").";
        }
        return null;
    }

    public String getMarkReadyFailureReason(int transactionId, User staff) {
        if (!policy.canManageInventory(staff)) {
            return "Insufficient permissions. Staff privileges required.";
        }
        Transaction t = transactions.findById(transactionId);
        if (t == null) {
            return "Transaction with ID " + transactionId + " not found.";
        }
        if (t.getStatus() != TransactionStatus.RESERVED) {
            return "Transaction is in " + t.getStatus() + " status. Only RESERVED transactions can be marked ready for pickup.";
        }
        BookCopy copy = copies.findById(t.getBookCopyId());
        if (copy == null) {
            return "Reserved copy ID " + t.getBookCopyId() + " not found.";
        }
        if (copy.getStatus() != BookCopyStatus.RESERVED) {
            return "Associated book copy ID " + t.getBookCopyId() + " is in " + copy.getStatus() + " status, not RESERVED.";
        }
        if (copy.getBookId() != t.getBookId()) {
            return "Reserved copy belongs to Book ID " + copy.getBookId() + ", but request is for Book ID " + t.getBookId() + ".";
        }
        return null;
    }

    public String getIssueFailureReason(int transactionId, User librarian) {
        if (!policy.canManageInventory(librarian)) {
            return "Insufficient permissions. Staff privileges required.";
        }
        Transaction t = transactions.findById(transactionId);
        if (t == null) {
            return "Transaction with ID " + transactionId + " not found.";
        }
        if (t.getStatus() != TransactionStatus.READY_FOR_PICKUP) {
            return "Transaction is in " + t.getStatus() + " status. Book must be in READY_FOR_PICKUP status before issuing.";
        }
        if (!policy.canIssueBook(t.getUserPhone())) {
            List<String> reasons = policy.getIssueEligibilityFailureReasons(t.getUserPhone());
            return "Member eligibility check failed: " + String.join("; ", reasons);
        }
        BookCopy copy = copies.findById(t.getBookCopyId());
        if (copy == null) {
            return "Reserved copy ID " + t.getBookCopyId() + " not found.";
        }
        if (copy.getStatus() != BookCopyStatus.RESERVED) {
            return "Reserved book copy is in " + copy.getStatus() + " status, not RESERVED.";
        }
        if (copy.getBookId() != t.getBookId()) {
            return "Reserved copy belongs to Book ID " + copy.getBookId() + ", but transaction is for Book ID " + t.getBookId() + ".";
        }
        return null;
    }

    public String getReturnFailureReason(int transactionId, User staff) {
        if (!policy.canManageInventory(staff)) {
            return "Insufficient permissions. Staff privileges required.";
        }
        Transaction t = transactions.findById(transactionId);
        if (t == null) {
            return "Transaction with ID " + transactionId + " not found.";
        }
        if (t.getStatus() != TransactionStatus.ISSUED) {
            return "Transaction is in " + t.getStatus() + " status. Only ISSUED loans can be returned.";
        }
        BookCopy copy = copies.findById(t.getBookCopyId());
        if (copy == null) {
            return "Associated book copy ID " + t.getBookCopyId() + " not found.";
        }
        if (copy.getStatus() != BookCopyStatus.ISSUED) {
            return "Associated book copy is in " + copy.getStatus() + " status, not ISSUED.";
        }
        if (copy.getBookId() != t.getBookId()) {
            return "Associated book copy does not match transaction book ID.";
        }
        LocalDate asOfDate = overdueFineSchedulerService != null ? overdueFineSchedulerService.getCurrentDate() : LocalDate.now();
        if (t.getDueDate() != null && asOfDate.isAfter(t.getDueDate()) && fineService == null) {
            return "Fine service is unavailable to compute overdue fines.";
        }
        return null;
    }

    public String getRenewFailureReason(int transactionId, User caller) {
        Transaction transaction = transactions.findById(transactionId);
        if (transaction == null) {
            return "Loan transaction with ID " + transactionId + " not found.";
        }
        if (transaction.getStatus() != TransactionStatus.ISSUED) {
            return "Transaction is in " + transaction.getStatus() + " status. Only active loans in ISSUED status can be renewed.";
        }
        if (transaction.getDueDate() == null || transaction.isOverdue()) {
            LocalDate due = transaction.getDueDate();
            long days = due != null ? java.time.temporal.ChronoUnit.DAYS.between(due, LocalDate.now()) : 0;
            return "The loan is overdue by " + days + " day(s) (Due date was " + due + "). Overdue books cannot be renewed; please return the book and settle fines.";
        }
        if (caller == null || !policy.isOwnerOrStaff(caller, transaction.getUserPhone())) {
            return "Unauthorized: You do not have permission to renew this loan.";
        }
        if (fineService != null && fineService.hasPendingFineForUser(transaction.getUserPhone())) {
            return "Member has outstanding fines. Please settle all outstanding fines before renewing loans.";
        }
        if (!policy.canRequestBook(transaction.getUserPhone())) {
            List<String> reasons = policy.getBorrowingEligibilityFailureReasons(transaction.getUserPhone());
            return "Member eligibility check failed: " + String.join("; ", reasons);
        }
        return null;
    }

    public String getMarkConditionFailureReason(int bookCopyId, User librarian, boolean lost) {
        if (!policy.canManageInventory(librarian)) {
            return "Insufficient permissions. Staff privileges required.";
        }
        BookCopy copy = copies.findById(bookCopyId);
        if (copy == null) {
            return "Book copy with ID " + bookCopyId + " not found.";
        }
        if (copy.getStatus() == BookCopyStatus.LOST) {
            return "Book copy ID " + bookCopyId + " is already marked as LOST.";
        }
        if (copy.getStatus() == BookCopyStatus.DAMAGED) {
            return "Book copy ID " + bookCopyId + " is already marked as DAMAGED.";
        }
        Transaction transaction = transactions.findActiveByCopyId(bookCopyId);
        boolean wasIssued = transaction != null && transaction.getStatus() == TransactionStatus.ISSUED;
        if (wasIssued && fineService == null) {
            return "Fine service is unavailable to compute lost/damaged copy charge.";
        }
        if (copy.getStatus() == BookCopyStatus.RESERVED && transaction == null) {
            return "Book copy ID " + bookCopyId + " is marked RESERVED but has no active reservation transaction.";
        }
        if (transaction != null && transaction.getStatus() == TransactionStatus.ISSUED && copy.getStatus() != BookCopyStatus.ISSUED) {
            return "Transaction is ISSUED but book copy is in " + copy.getStatus() + " status.";
        }
        if (transaction != null && (transaction.getStatus() == TransactionStatus.RESERVED || transaction.getStatus() == TransactionStatus.READY_FOR_PICKUP) && copy.getStatus() != BookCopyStatus.RESERVED) {
            return "Transaction is reserved/ready for pickup but copy is in " + copy.getStatus() + " status.";
        }
        return null;
    }
}
