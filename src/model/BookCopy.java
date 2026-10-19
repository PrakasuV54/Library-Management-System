package model;

import enums.BookCopyStatus;
import java.time.LocalDate;

public class BookCopy {
    private final int bookCopyId;
    private final int bookId;
    private final LocalDate acquiredDate;
    private BookCopyStatus status;
    public BookCopy(int bookCopyId, int bookId) {
        this(bookCopyId, bookId, BookCopyStatus.AVAILABLE, LocalDate.now());
    }

    public BookCopy(int bookCopyId, int bookId, BookCopyStatus status) {
        this(bookCopyId, bookId, status, LocalDate.now());
    }

    public BookCopy(int bookCopyId, int bookId, BookCopyStatus status, LocalDate acquiredDate) {
        this.bookCopyId = bookCopyId;
        this.bookId = bookId;
        if (status == null || acquiredDate == null)
            throw new IllegalArgumentException("Status and acquired date are required");
        this.status = status;
        this.acquiredDate = acquiredDate;
    }

    public int getBookCopyId() {
        return bookCopyId;
    }

    public int getBookId() {
        return bookId;
    }

    public LocalDate getAcquiredDate() {
        return acquiredDate;
    }

    public BookCopyStatus getStatus() {
        return status;
    }

    public void reserve() {
        if (status != BookCopyStatus.AVAILABLE)
            throw new IllegalStateException("Book copy is not available");
        status = BookCopyStatus.RESERVED;
    }

    public void issue() {
        if (status != BookCopyStatus.RESERVED)
            throw new IllegalStateException("Only a reserved book copy can be issued");
        status = BookCopyStatus.ISSUED;
    }

    public void returnCopy() {
        if (status != BookCopyStatus.ISSUED)
            throw new IllegalStateException("Only an issued book copy can be returned");
        status = BookCopyStatus.AVAILABLE;
    }

    public void releaseReservation() {
        if (status != BookCopyStatus.RESERVED)
            throw new IllegalStateException("Only a reserved copy can be released");
        status = BookCopyStatus.AVAILABLE;
    }

    public void markLost() {
        if (status == BookCopyStatus.LOST || status == BookCopyStatus.DAMAGED)
            throw new IllegalStateException("Book copy is already unavailable");
        status = BookCopyStatus.LOST;
    }

    public void markDamaged() {
        if (status == BookCopyStatus.LOST || status == BookCopyStatus.DAMAGED)
            throw new IllegalStateException("Book copy is already unavailable");
        status = BookCopyStatus.DAMAGED;
    }

    public void markAvailable() {
        if (status != BookCopyStatus.DAMAGED && status != BookCopyStatus.LOST)
            throw new IllegalStateException("Only damaged or lost copies can be restored");
        status = BookCopyStatus.AVAILABLE;
    }

    public boolean isAvailable() {
        return status == BookCopyStatus.AVAILABLE;
    }

    public boolean isReserved() {
        return status == BookCopyStatus.RESERVED;
    }

    public boolean isIssued() {
        return status == BookCopyStatus.ISSUED;
    }
}
