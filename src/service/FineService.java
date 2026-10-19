package service;

import model.Fine;
import enums.FineStatus;
import enums.FineType;
import enums.TransactionStatus;
import model.Transaction;
import repository.DataStore;
import repository.FineRepository;
import repository.TransactionRepository;
import java.time.temporal.ChronoUnit;
import java.time.LocalDate;
import java.util.List;

public class FineService {
    private final FineRepository fines;
    private final TransactionRepository transactions;
    private final PolicyService policyService;
    private final AuditService auditService;

    public FineService(FineRepository fines, TransactionRepository transactions,
                       PolicyService policyService) {
        this(fines, transactions, policyService, null);
    }

    public FineService(FineRepository fines, TransactionRepository transactions,
                       PolicyService policyService, AuditService auditService) {
        this.fines = fines;
        this.transactions = transactions;
        this.policyService = policyService;
        this.auditService = auditService;
    }

    public Fine createOverdueFine(int transactionId) {
        return createOverdueFine(transactionId, LocalDate.now());
    }

    public Fine createOverdueFine(int transactionId, LocalDate asOfDate) {
        Transaction transaction = transactions.findById(transactionId);
        if (transaction == null) {
            return null;
        }

        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();
        boolean isOverdue = false;
        if (transaction.getStatus() == TransactionStatus.ISSUED && transaction.getDueDate() != null) {
            isOverdue = effectiveDate.isAfter(transaction.getDueDate());
        } else if (transaction.getStatus() == TransactionStatus.RETURNED && transaction.getDueDate() != null) {
            effectiveDate = transaction.getReturnDate() != null ? transaction.getReturnDate().toLocalDate() : effectiveDate;
            if (effectiveDate.isAfter(transaction.getDueDate())) {
                isOverdue = true;
            }
        }

        if (!isOverdue || transaction.getDueDate() == null) {
            return null;
        }

        long overdueDays = ChronoUnit.DAYS.between(transaction.getDueDate(), effectiveDate);
        double amount = overdueDays * policyService.getFinePerDay();
        if (amount <= 0) {
            return null;
        }

        Fine existing = fines.findByTransactionIdAndType(transactionId, FineType.OVERDUE);
        if (existing != null) {
            if (amount > existing.getAmount() && existing.getStatus() != FineStatus.WAIVED) {
                existing.updateOverdueAmount(amount);
                fines.save(existing);
                if (auditService != null) {
                    auditService.record("SYSTEM", "FINE_UPDATED", "FINE",
                            String.valueOf(existing.getFineId()), "Updated overdue fine for member "
                                    + transaction.getUserPhone() + ", transaction " + transactionId
                                    + " to Rs." + amount);
                }
            }
            return existing;
        }

        Fine fine = new Fine(DataStore.getFineId(), transaction.getUserPhone(), transactionId, amount, FineType.OVERDUE);
        fines.save(fine);
        if (auditService != null) {
            auditService.record("SYSTEM", "FINE_CREATED", "FINE",
                    String.valueOf(fine.getFineId()), "Overdue fine for member " + transaction.getUserPhone()
                            + ", transaction " + transactionId);
        }
        return fine;
    }

    public Fine findOverdueFineByTransactionId(int transactionId) {
        return fines.findByTransactionIdAndType(transactionId, FineType.OVERDUE);
    }

    public Fine createLostBookFine(int transactionId) {
        return createIncidentFine(transactionId, FineType.LOST_BOOK, policyService.getLostBookCharge());
    }

    public Fine createDamagedBookFine(int transactionId) {
        return createIncidentFine(transactionId, FineType.DAMAGED_BOOK, policyService.getDamagedBookCharge());
    }

    private Fine createIncidentFine(int transactionId, FineType type, double amount) {
        Transaction transaction = transactions.findById(transactionId);
        if (transaction == null) return null;
        if (type == FineType.LOST_BOOK && transaction.getStatus() != enums.TransactionStatus.LOST) return null;
        if (type == FineType.DAMAGED_BOOK && transaction.getStatus() != enums.TransactionStatus.DAMAGED) return null;
        Fine existing = fines.findByTransactionIdAndType(transactionId, type);
        if (existing != null) return existing;
        if (amount <= 0) return null;

        Fine fine = new Fine(DataStore.getFineId(), transaction.getUserPhone(), transactionId, amount, type);
        fines.save(fine);
        if (auditService != null) {
            auditService.record("SYSTEM", "FINE_CREATED", "FINE",
                    String.valueOf(fine.getFineId()), type + " for member " + transaction.getUserPhone()
                            + ", transaction " + transactionId);
        }
        return fine;
    }

    public boolean waiveFine(int fineId, model.User staff) {
        if (staff == null || policyService == null || !policyService.isStaff(staff)) {
            return false;
        }
        Fine fine = fines.findById(fineId);
        if (fine == null) {
            return false;
        }
        if (fine.getStatus() == enums.FineStatus.PAID || fine.getStatus() == enums.FineStatus.WAIVED) {
            return false;
        }
        fine.waive();
        fines.save(fine);
        if (auditService != null) {
            auditService.record(staff.getPhone(), "FINE_WAIVED", "FINE",
                    String.valueOf(fineId), "Fine waived by staff");
        }
        return true;
    }

    public boolean waiveFine(int fineId) {
        return false;
    }

    public Fine findByIdInternal(int fineId) {
        return fines.findById(fineId);
    }

    public Fine findById(int fineId) {
        return findByIdInternal(fineId);
    }

    public Fine findById(int fineId, model.User caller) {
        if (caller == null || policyService == null) return null;
        Fine fine = fines.findById(fineId);
        if (fine == null || !policyService.isOwnerOrStaff(caller, fine.getUserPhone())) return null;
        return fine;
    }

    public List<Fine> getAllFines(model.User staff) {
        if (staff == null || policyService == null || !policyService.isStaff(staff)) {
            return new java.util.ArrayList<>();
        }
        return fines.findAll();
    }

    public List<Fine> getFinesByStatus(FineStatus status, model.User staff) {
        if (staff == null || policyService == null || !policyService.isStaff(staff) || status == null) {
            return new java.util.ArrayList<>();
        }
        List<Fine> result = new java.util.ArrayList<>();
        for (Fine fine : fines.findAll()) {
            if (fine.getStatus() == status) {
                result.add(fine);
            }
        }
        return result;
    }

    public List<Fine> findByTransactionId(int transactionId, model.User caller) {
        if (caller == null || policyService == null) {
            return new java.util.ArrayList<>();
        }
        List<Fine> list = fines.findByTransactionId(transactionId);
        if (list.isEmpty()) {
            return list;
        }
        if (!policyService.isStaff(caller)) {
            List<Fine> result = new java.util.ArrayList<>();
            for (Fine f : list) {
                if (policyService.isOwnerOrStaff(caller, f.getUserPhone())) {
                    result.add(f);
                }
            }
            return result;
        }
        return list;
    }

    public List<Fine> findByUserPhone(String phone, model.User caller) {
        if (caller == null || policyService == null || !policyService.isOwnerOrStaff(caller, phone)) {
            return new java.util.ArrayList<>();
        }
        return fines.findByUserPhone(phone);
    }

    // Internal policy helper; does not expose the fine records to callers.
    public boolean hasPendingFineForUser(String phone) {
        for (Fine fine : fines.findByUserPhone(phone)) {
            if (fine.isPending()) return true;
        }
        return false;
    }

    public List<Fine> findByUserPhoneInternal(String phone) {
        if (phone == null || phone.isBlank()) {
            return new java.util.ArrayList<>();
        }
        return fines.findByUserPhone(phone.trim());
    }

    public List<Fine> findByUserPhone(String phone) {
        return findByUserPhoneInternal(phone);
    }

    public List<Fine> getFineHistory(String phone, model.User caller) {
        if (caller == null || policyService == null || !policyService.isOwnerOrStaff(caller, phone)) {
            return new java.util.ArrayList<>();
        }
        return fines.findByUserPhone(phone);
    }

    public List<Fine> getFineHistoryInternal(String phone) {
        return findByUserPhoneInternal(phone);
    }

    public List<Fine> getFineHistory(String phone) {
        return getFineHistoryInternal(phone);
    }

    public List<Fine> getOutstandingFines(String phone, model.User caller) {
        if (caller == null || policyService == null || !policyService.isOwnerOrStaff(caller, phone)) {
            return new java.util.ArrayList<>();
        }
        return findOutstandingFines(phone);
    }

    public List<Fine> getOutstandingFinesInternal(String phone) {
        return findOutstandingFines(phone);
    }

    public List<Fine> getOutstandingFines(String phone) {
        return getOutstandingFinesInternal(phone);
    }

    private List<Fine> findOutstandingFines(String phone) {
        List<Fine> result = new java.util.ArrayList<>();
        if (phone == null || phone.isBlank()) {
            return result;
        }
        for (Fine fine : fines.findByUserPhone(phone.trim())) {
            if (fine.isPending() && fine.getRemainingAmount() > 0) result.add(fine);
        }
        return result;
    }

    public PolicyService getPolicyService() {
        return policyService;
    }

    public String getWaiveFineFailureReason(int fineId, model.User staff) {
        if (staff == null || policyService == null || !policyService.isStaff(staff)) {
            return "Insufficient permissions. Staff authorization required.";
        }
        Fine fine = fines.findById(fineId);
        if (fine == null) {
            return "Fine not found with ID " + fineId + ".";
        }
        if (fine.getStatus() == enums.FineStatus.PAID) {
            return "Paid fine cannot be waived.";
        }
        if (fine.getStatus() == enums.FineStatus.WAIVED) {
            return "Fine is already waived.";
        }
        return null;
    }
}
