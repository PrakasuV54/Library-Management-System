package model;

import java.time.LocalDateTime;

public class Audit {
    private final int auditId;
    private final String userPhone;
    private final String action;
    private final String entityType;
    private final String entityId;
    private final LocalDateTime timestamp;
    private final String details;
    public Audit(int auditId,String userPhone,String action,String entityType,String entityId,String details) {
        if(action==null||action.isBlank()||entityType==null||entityType.isBlank())
            throw new IllegalArgumentException("Audit action and entity type are required");
        this.auditId=auditId;
        this.userPhone=userPhone;
        this.action=action;
        this.entityType=entityType;
        this.entityId=entityId;
        this.timestamp=LocalDateTime.now();
        this.details=details;
    }

    public int getAuditId() {
        return auditId;
    }

    public String getUserPhone() {
        return userPhone;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getDetails() {
        return details;
    }
}
