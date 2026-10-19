package model;

import enums.UserRole;
import java.util.Map;

public class AnalyticsDashboard {
    private final int year;
    private final Map<String, Map<String, Integer>> mostBorrowedBooksByMonth;
    private final Map<String, Map<String, Integer>> mostBorrowedBooksByQuarter;
    private final Map<String, Integer> mostBorrowedBooksForYear;
    private final Map<String, Map<UserRole, Integer>> registrationsByMonth;
    private final Map<String, Map<UserRole, Integer>> registrationsByQuarter;
    private final Map<UserRole, Integer> registrationsForYear;

    public AnalyticsDashboard(int year, Map<String, Map<String, Integer>> mostBorrowedBooksByMonth, Map<String, Map<String, Integer>> mostBorrowedBooksByQuarter, Map<String, Integer> mostBorrowedBooksForYear, Map<String, Map<UserRole, Integer>> registrationsByMonth, Map<String, Map<UserRole, Integer>> registrationsByQuarter, Map<UserRole, Integer> registrationsForYear) {
        this.year = year;
        this.mostBorrowedBooksByMonth = mostBorrowedBooksByMonth;
        this.mostBorrowedBooksByQuarter = mostBorrowedBooksByQuarter;
        this.mostBorrowedBooksForYear = mostBorrowedBooksForYear;
        this.registrationsByMonth = registrationsByMonth;
        this.registrationsByQuarter = registrationsByQuarter;
        this.registrationsForYear = registrationsForYear;
    }

    public int getYear() {
        return year;
    }

    public Map<String, Map<String, Integer>> getMostBorrowedBooksByMonth() {
        return mostBorrowedBooksByMonth;
    }

    public Map<String, Map<String, Integer>> getMostBorrowedBooksByQuarter() {
        return mostBorrowedBooksByQuarter;
    }

    public Map<String, Integer> getMostBorrowedBooksForYear() {
        return mostBorrowedBooksForYear;
    }

    public Map<String, Map<UserRole, Integer>> getRegistrationsByMonth() {
        return registrationsByMonth;
    }

    public Map<String, Map<UserRole, Integer>> getRegistrationsByQuarter() {
        return registrationsByQuarter;
    }

    public Map<UserRole, Integer> getRegistrationsForYear() {
        return registrationsForYear;
    }
}
