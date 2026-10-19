package service;

import model.Audit;
import repository.AuditLogRepository;
import repository.DataStore;
import java.util.List;

public class AuditService {
    private final AuditLogRepository repository;
    public AuditService(AuditLogRepository repository) {
        this.repository=repository;
    }

    public Audit record(String actorPhone,String action,String entityType,String entityId,String details) {
        Audit audit=new Audit(DataStore.getAuditId(),actorPhone,action,entityType,entityId,details);
        repository.save(audit);
        return audit;
    }

    public List<Audit> findAll() {
        return repository.findAll();
    }

    public List<Audit> findByUserPhone(String phone) {
        return repository.findByUserPhone(phone);
    }
}
