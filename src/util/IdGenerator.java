package util;

public class IdGenerator {
    private static int userId = 1;
    private static int bookId = 1;
    private static int membershipId = 1;
    private static int membershipPlanId = 1;
    private static int bookCopyId = 1;
    private static int authorId = 1;
    private static int categoryId = 1;
    private static int genreId = 1;
    private static int auditId = 1;
    private static int paymentId = 1;
    private static int fineId = 1;
    private static int transactionId = 1;

    private IdGenerator() {
    }

    public static int nextUserId() {
        return userId++;
    }

    public static int nextBookId() {
        return bookId++;
    }

    public static int nextMembershipId() {
        return membershipId++;
    }

    public static int nextMembershipPlanId() {
        return membershipPlanId++;
    }

    public static int nextBookCopyId() {
        return bookCopyId++;
    }

    public static int nextAuthorId() {
        return authorId++;
    }

    public static int nextCategoryId() {
        return categoryId++;
    }

    public static int nextGenreId() {
        return genreId++;
    }

    public static int nextAuditId() {
        return auditId++;
    }

    public static int nextPaymentId() {
        return paymentId++;
    }

    public static int nextFineId() {
        return fineId++;
    }

    public static int nextTransactionId() {
        return transactionId++;
    }
}
