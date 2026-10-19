package repository;

import model.Audit;
import java.util.ArrayList;
import java.util.List;

public class InMemoryAuditLogRepository implements AuditLogRepository {
    private final DataStore dataStore;
    public InMemoryAuditLogRepository(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    public void save(Audit audit) {
        if (audit == null) throw new IllegalArgumentException("Audit is required");
        for (Audit existing : dataStore.auditLogs) {
            if (existing.getAuditId() == audit.getAuditId()) {
                throw new IllegalArgumentException("Audit ID already exists");
            }
        }
        dataStore.auditLogs.add(audit);
    }

    public List<Audit> findAll() {
        return new ArrayList<>(dataStore.auditLogs);
    }

    public List<Audit> findByUserPhone(String userPhone) {
        List<Audit> result = new ArrayList<>();
        for (Audit audit : dataStore.auditLogs) {
            if (java.util.Objects.equals(audit.getUserPhone(), userPhone)) result.add(audit);
        }
        return result;
    }

    public List<Audit> findByEntity(String entityType, String entityId) {
        List<Audit> result = new ArrayList<>();
        for (Audit audit : dataStore.auditLogs) {
            if (java.util.Objects.equals(audit.getEntityType(), entityType) && java.util.Objects.equals(audit.getEntityId(), entityId)) {
                result.add(audit);
            }
        }
        return result;
    }
}
