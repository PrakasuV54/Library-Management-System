package repository;

import model.Audit;
import java.util.List;

public interface AuditLogRepository {
    void save(Audit audit);
    List<Audit> findAll();
    List<Audit> findByUserPhone(String userPhone);
    List<Audit> findByEntity(String entityType, String entityId);
}
