package service;

import enums.TransactionStatus;
import model.Book;
import model.Fine;
import model.Transaction;
import model.User;
import repository.BookRepository;
import repository.TransactionRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class OverdueFineSchedulerService {

    private final TransactionRepository transactions;
    private final BookRepository books;
    private final FineService fineService;
    private final NotificationService notificationService;
    private final repository.UserRepository userRepository;

    private ScheduledExecutorService scheduler;
    private Thread shutdownHook;
    private Supplier<LocalDate> dateSupplier = LocalDate::now;

    public OverdueFineSchedulerService(TransactionRepository transactions, BookRepository books,
                                       FineService fineService, NotificationService notificationService) {
        this(transactions, books, fineService, notificationService, null);
    }

    public OverdueFineSchedulerService(TransactionRepository transactions, BookRepository books,
                                       FineService fineService, NotificationService notificationService,
                                       repository.UserRepository userRepository) {
        this.transactions = Objects.requireNonNull(transactions, "transactions repository must not be null");
        this.books = books;
        this.fineService = Objects.requireNonNull(fineService, "fineService must not be null");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService must not be null");
        this.userRepository = userRepository != null ? userRepository :
                (fineService != null && fineService.getPolicyService() != null ? fineService.getPolicyService().getUserRepository() : null);
        if (this.notificationService.getUserRepository() == null && this.userRepository != null) {
            this.notificationService.setUserRepository(this.userRepository);
        }
    }

    public synchronized void startScheduler() {
        if (scheduler != null && !scheduler.isShutdown()) {
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "OverdueFineSchedulerThread");
            thread.setDaemon(true);
            return thread;
        });

        if (shutdownHook == null) {
            shutdownHook = new Thread(this::stopScheduler, "OverdueFineSchedulerShutdownHook");
            try {
                Runtime.getRuntime().addShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // JVM is already shutting down
            }
        }

        scheduler.scheduleAtFixedRate(this::processOverdueTransactions, 0, 6, TimeUnit.HOURS);
    }

    public synchronized void stopScheduler() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public synchronized boolean isRunning() {
        return scheduler != null && !scheduler.isShutdown();
    }

    public void setDateSupplier(Supplier<LocalDate> dateSupplier) {
        this.dateSupplier = Objects.requireNonNull(dateSupplier, "dateSupplier must not be null");
    }

    public Supplier<LocalDate> getDateSupplier() {
        return dateSupplier;
    }

    public LocalDate getCurrentDate() {
        return dateSupplier != null ? dateSupplier.get() : LocalDate.now();
    }

    public void processOverdueTransactions() {
        processOverdueTransactions(dateSupplier.get());
    }

    public void processOverdueTransactions(LocalDate asOfDate) {
        if (asOfDate == null) {
            asOfDate = LocalDate.now();
        }

        for (Transaction transaction : transactions.findAll()) {
            if (transaction == null) {
                continue;
            }

            try {
                if (transaction.getStatus() != TransactionStatus.ISSUED) {
                    continue;
                }

                LocalDate dueDate = transaction.getDueDate();
                if (dueDate == null || !asOfDate.isAfter(dueDate)) {
                    continue;
                }

                long overdueDays = ChronoUnit.DAYS.between(dueDate, asOfDate);
                if (overdueDays <= 0) {
                    continue;
                }

                Fine fine = fineService.createOverdueFine(transaction.getTransactionId(), asOfDate);
                if (fine == null) {
                    continue;
                }

                double outstandingAmount = fine.getRemainingAmount();
                if (outstandingAmount <= 0) {
                    continue;
                }

                Book book = books != null ? books.findById(transaction.getBookId()) : null;
                String bookTitle = (book != null && book.getTitle() != null && !book.getTitle().isBlank())
                        ? book.getTitle()
                        : "Book (ID: " + transaction.getBookId() + ")";

                String message = String.format(
                        "Overdue Notice: The book '%s' was due on %s and is %d day%s overdue. " +
                                "Your current outstanding fine is Rs.%.2f. Please return the book as soon as possible.",
                        bookTitle,
                        dueDate,
                        overdueDays,
                        overdueDays == 1 ? "" : "s",
                        outstandingAmount
                );

                try {
                    User recipientUser = null;
                    if (userRepository != null) {
                        recipientUser = userRepository.findByPhone(transaction.getUserPhone());
                        if (recipientUser == null && transaction.getUserId() > 0) {
                            recipientUser = userRepository.findById(transaction.getUserId());
                        }
                    } else if (notificationService.getUserRepository() != null) {
                        recipientUser = notificationService.getUserRepository().findByPhone(transaction.getUserPhone());
                        if (recipientUser == null && transaction.getUserId() > 0) {
                            recipientUser = notificationService.getUserRepository().findById(transaction.getUserId());
                        }
                    }

                    if (recipientUser != null && recipientUser.getName() != null && !recipientUser.getName().isBlank()) {
                        notificationService.send(recipientUser.getName(), transaction.getUserPhone(), message);
                    } else if (transaction.getUserId() > 0) {
                        notificationService.send(String.valueOf(transaction.getUserId()), transaction.getUserPhone(), message);
                    } else {
                        notificationService.send(transaction.getUserPhone(), message);
                    }
                } catch (Exception notifEx) {
                    // Notification failure must not affect other transactions
                }
            } catch (Exception txEx) {
                // Transaction failure must not stop the scheduler or processing other transactions
            }
        }
    }
}
