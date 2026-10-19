package repository;

import model.*;
import java.util.*;

public class DataStore {
    final Map<Integer, User> userById = new LinkedHashMap<>();
    final Map<String, User> userByPhone = new HashMap<>();
    final Map<String, User> userByEmail = new HashMap<>();
    final Map<Integer, Membership> memberships = new LinkedHashMap<>();
    final Map<Integer, Book> books = new HashMap<>();
    final Map<String, Book> bookByIsbn = new HashMap<>();
    final Map<Integer, BookCopy> bookCopies = new HashMap<>();
    final Map<Integer, Transaction> transactions = new LinkedHashMap<>();
    final Map<Integer, Fine> fines = new LinkedHashMap<>();
    final Map<Integer, Payment> payments = new LinkedHashMap<>();
    final List<Author> authors = new ArrayList<>();
    final List<Category> categories = new ArrayList<>();
    final List<Genre> genres = new ArrayList<>();
    final List<Audit> auditLogs = new ArrayList<>();
    final Map<Integer, MembershipPlan> membershipPlans = new HashMap<>();
    final Map<String, MembershipPlan> membershipPlanByName = new HashMap<>();
    final Map<Integer, Integer> activeTransactionIdByCopyId = new HashMap<>();

    public static int getBookId() { return util.IdGenerator.nextBookId(); }
    public static int getMembershipId() { return util.IdGenerator.nextMembershipId(); }
    public static int getMembershipPlanId() { return util.IdGenerator.nextMembershipPlanId(); }
    public static int getBookCopyId() { return util.IdGenerator.nextBookCopyId(); }
    public static int getAuthorId() { return util.IdGenerator.nextAuthorId(); }
    public static int getCategoryId() { return util.IdGenerator.nextCategoryId(); }
    public static int getGenreId() { return util.IdGenerator.nextGenreId(); }
    public static int getAuditId() { return util.IdGenerator.nextAuditId(); }
    public static int getPaymentId() { return util.IdGenerator.nextPaymentId(); }
    public static int getFineId() { return util.IdGenerator.nextFineId(); }
    public static int getTransactionId() { return util.IdGenerator.nextTransactionId(); }

    void updateUserPhoneReferences(String oldPhone, String newPhone) {
        for (Membership membership : memberships.values()) {
            if (oldPhone.equals(membership.getUserPhone())) membership.setUserPhone(newPhone);
        }
        for (Transaction transaction : transactions.values()) {
            if (oldPhone.equals(transaction.getUserPhone())) transaction.setUserPhone(newPhone);
        }
        for (Fine fine : fines.values()) {
            if (oldPhone.equals(fine.getUserPhone())) fine.setUserPhone(newPhone);
        }
        for (Payment payment : payments.values()) {
            if (oldPhone.equals(payment.getUserPhone())) payment.setUserPhone(newPhone);
        }

    }

    boolean hasUserHistory(String phone) {
        for (Membership membership : memberships.values()) {
            if (phone.equals(membership.getUserPhone())) return true;
        }
        for (Transaction transaction : transactions.values()) {
            if (phone.equals(transaction.getUserPhone())) return true;
        }
        for (Fine fine : fines.values()) {
            if (phone.equals(fine.getUserPhone())) return true;
        }
        for (Payment payment : payments.values()) {
            if (phone.equals(payment.getUserPhone())) return true;
        }
        return false;
    }
}
