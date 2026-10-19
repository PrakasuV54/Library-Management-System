package service;

import enums.UserRole;
import model.AnalyticsDashboard;
import model.Book;
import model.Transaction;
import model.User;
import repository.BookRepository;
import repository.TransactionRepository;
import repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

public class AnalyticsService {
    private final BookRepository books;
    private final TransactionRepository transactions;
    private final UserRepository users;

    public AnalyticsService(BookRepository books, TransactionRepository transactions, UserRepository users) {
        this.books = books;
        this.transactions = transactions;
        this.users = users;
    }

    public Map<String, Integer> getMostBorrowedBooks(LocalDate startDate, LocalDate endDate) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            return result;
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();
        for (Transaction transaction : transactions.findAll()) {
            LocalDateTime issueDate = transaction.getIssueDate();
            if (issueDate == null || issueDate.isBefore(start) || !issueDate.isBefore(end)) {
                continue;
            }

            Book book = books.findById(transaction.getBookId());
            if (book != null) {
                result.put(book.getTitle(), result.getOrDefault(book.getTitle(), 0) + 1);
            }
        }
        return sortByBorrowCount(result);
    }

    public Map<String, Integer> getMostBorrowedBooksForMonth(YearMonth month) {
        if (month == null) return new LinkedHashMap<>();
        return getMostBorrowedBooks(month.atDay(1), month.atEndOfMonth());
    }

    public Map<String, Integer> getMostBorrowedBooksForQuarter(int year, int quarter) {
        if (quarter < 1 || quarter > 4) return new LinkedHashMap<>();
        int firstMonth = (quarter - 1) * 3 + 1;
        LocalDate start = LocalDate.of(year, firstMonth, 1);
        return getMostBorrowedBooks(start, start.plusMonths(3).minusDays(1));
    }

    public Map<String, Integer> getMostBorrowedBooksForYear(int year) {
        return getMostBorrowedBooks(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    public Map<UserRole, Integer> getUserRegistrationsByRole(LocalDate startDate, LocalDate endDate) {
        Map<UserRole, Integer> result = new LinkedHashMap<>();
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            return result;
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();
        for (User user : users.findAll()) {
            LocalDateTime createdAt = user.getCreatedAt();
            if (!createdAt.isBefore(start) && createdAt.isBefore(end)) {
                result.put(user.getRole(), result.getOrDefault(user.getRole(), 0) + 1);
            }
        }
        return result;
    }

    public Map<UserRole, Integer> getUserRegistrationsForMonth(YearMonth month) {
        if (month == null) return new LinkedHashMap<>();
        return getUserRegistrationsByRole(month.atDay(1), month.atEndOfMonth());
    }

    public Map<UserRole, Integer> getUserRegistrationsForQuarter(int year, int quarter) {
        if (quarter < 1 || quarter > 4) return new LinkedHashMap<>();
        int firstMonth = (quarter - 1) * 3 + 1;
        LocalDate start = LocalDate.of(year, firstMonth, 1);
        return getUserRegistrationsByRole(start, start.plusMonths(3).minusDays(1));
    }

    public Map<UserRole, Integer> getUserRegistrationsForYear(int year) {
        return getUserRegistrationsByRole(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }


    public AnalyticsDashboard getDashboardForYear(int year) {
        Map<String, Map<String, Integer>> booksByMonth = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> booksByQuarter = new LinkedHashMap<>();
        Map<String, Map<UserRole, Integer>> registrationsByMonth = new LinkedHashMap<>();
        Map<String, Map<UserRole, Integer>> registrationsByQuarter = new LinkedHashMap<>();

        for (int month = 1; month <= 12; month++) {
            YearMonth period = YearMonth.of(year, month);
            String monthName = period.getMonth().toString();
            booksByMonth.put(monthName, getMostBorrowedBooksForMonth(period));
            registrationsByMonth.put(monthName, getUserRegistrationsForMonth(period));
        }

        for (int quarter = 1; quarter <= 4; quarter++) {
            String quarterName = "Q" + quarter;
            booksByQuarter.put(quarterName, getMostBorrowedBooksForQuarter(year, quarter));
            registrationsByQuarter.put(quarterName, getUserRegistrationsForQuarter(year, quarter));
        }

        return new AnalyticsDashboard(year, booksByMonth, booksByQuarter, getMostBorrowedBooksForYear(year), registrationsByMonth, registrationsByQuarter, getUserRegistrationsForYear(year));
    }

    private Map<String, Integer> sortByBorrowCount(Map<String, Integer> counts) {
        Map<String, Integer> sorted = new LinkedHashMap<>();
        counts.entrySet().stream().sorted((first, second) -> second.getValue().compareTo(first.getValue())).forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return sorted;
    }
}
