package model;

import enums.TransactionStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Transaction {
    private final int transactionId;
    private int userId;
    private String userPhone;
    private final int bookId;
    private int bookCopyId;
    private TransactionStatus status;
    private final LocalDateTime requestDate;
    private LocalDateTime reservedDate;
    private LocalDateTime readyDate;
    private LocalDateTime issueDate;
    private LocalDate dueDate;
    private LocalDateTime returnDate;
    public Transaction(int transactionId, String userPhone, int bookId) {
        if (userPhone == null || userPhone.isBlank())
            throw new IllegalArgumentException("User phone is required");
        this.transactionId = transactionId;
        this.userPhone = userPhone;
        if (bookId <= 0) throw new IllegalArgumentException("Book ID must be positive");
        this.bookId = bookId;
        this.status = TransactionStatus.REQUESTED;
        this.requestDate = LocalDateTime.now();
    }

    public Transaction(int transactionId, String userPhone, int bookId, int bookCopyId,
                       TransactionStatus status, LocalDateTime requestDate, LocalDateTime issueDate,
                       LocalDate dueDate, LocalDateTime returnDate) {
        if (userPhone == null || userPhone.isBlank())
            throw new IllegalArgumentException("User phone is required");
        if (bookId <= 0) throw new IllegalArgumentException("Book ID must be positive");
        this.transactionId = transactionId;
        this.userPhone = userPhone.trim();
        this.bookId = bookId;
        this.bookCopyId = bookCopyId;
        this.status = status != null ? status : TransactionStatus.REQUESTED;
        this.requestDate = requestDate != null ? requestDate : LocalDateTime.now();
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.returnDate = returnDate;
    }

    public int getTransactionId() {
        return transactionId;
    }

    public int getUserId() {
        return userId;
    }

    /** Set by the repository after resolving the user contact to its stable ID. */
    public void assignUserId(int userId) {
        if (userId <= 0)
            throw new IllegalArgumentException("User ID must be positive");
        if (this.userId != 0 && this.userId != userId)
            throw new IllegalStateException("User ID cannot be changed");
        this.userId = userId;
    }

    public String getUserPhone() {
        return userPhone;
    }

    public int getBookId() {
        return bookId;
    }

    public int getBookCopyId() {
        return bookCopyId;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public LocalDateTime getRequestDate() {
        return requestDate;
    }

    public LocalDateTime getReservedDate() {
        return reservedDate;
    }

    public LocalDateTime getReadyDate() {
        return readyDate;
    }

    public LocalDateTime getIssueDate() {
        return issueDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LocalDateTime getReturnDate() {
        return returnDate;
    }

    public void setUserPhone(String userPhone) {
        if (userPhone == null || userPhone.isBlank()) throw new IllegalArgumentException("Phone is required");
        this.userPhone = userPhone;
    }

    public void reserve(int bookCopyId) {
        if (status != TransactionStatus.REQUESTED)
            throw new IllegalStateException("Only requested transactions can be reserved");
        if (bookCopyId <= 0)
            throw new IllegalArgumentException("Book copy ID must be positive");
        this.bookCopyId = bookCopyId;
        status = TransactionStatus.RESERVED;
        reservedDate = LocalDateTime.now();
    }

    public void markReadyForPickup() {
        if (status != TransactionStatus.RESERVED) throw new IllegalStateException("Transaction is not reserved");
        status = TransactionStatus.READY_FOR_PICKUP;
        readyDate = LocalDateTime.now();
    }

    public void issue(LocalDate dueDate) {
        if (status != TransactionStatus.READY_FOR_PICKUP)
            throw new IllegalStateException("Transaction is not ready for issue");
        if (dueDate == null || !dueDate.isAfter(LocalDate.now()))
            throw new IllegalArgumentException("Due date must be in the future");
        status = TransactionStatus.ISSUED;
        issueDate = LocalDateTime.now();
        this.dueDate = dueDate;
    }

    public void renew(LocalDate newDueDate) {
        if (status != TransactionStatus.ISSUED)
            throw new IllegalStateException("Only issued books can be renewed");
        if (newDueDate == null || dueDate == null || !newDueDate.isAfter(dueDate)) {
            throw new IllegalArgumentException("New due date must be after the current due date");
        }
        dueDate = newDueDate;
    }

    public void returnBook() {
        if (status != TransactionStatus.ISSUED)
            throw new IllegalStateException("Only issued books can be returned");
        status = TransactionStatus.RETURNED;
        returnDate = LocalDateTime.now();
    }

    public void markLost() {
        if (status != TransactionStatus.ISSUED)
            throw new IllegalStateException("Only issued books can be marked lost");
        status = TransactionStatus.LOST;
    }

    public void markDamaged() {
        if (status != TransactionStatus.ISSUED)
            throw new IllegalStateException("Only issued books can be marked damaged");
        status = TransactionStatus.DAMAGED;
    }

    public void cancel() {
        if (status != TransactionStatus.REQUESTED && status != TransactionStatus.RESERVED && status != TransactionStatus.READY_FOR_PICKUP)
            throw new IllegalStateException("Transaction cannot be cancelled");
        status = TransactionStatus.CANCELLED;
    }

    public void expire() {
        if (status != TransactionStatus.REQUESTED && status != TransactionStatus.RESERVED && status != TransactionStatus.READY_FOR_PICKUP)
            throw new IllegalStateException("Transaction cannot expire");
        status = TransactionStatus.EXPIRED;
    }

    public boolean isActive() {
        return status == TransactionStatus.REQUESTED || status == TransactionStatus.RESERVED || status == TransactionStatus.READY_FOR_PICKUP || status == TransactionStatus.ISSUED;
    }

    public boolean isOverdue() {
        return status == TransactionStatus.ISSUED && dueDate != null && LocalDate.now().isAfter(dueDate);
    }
}
