package model;

import enums.UserRole;
import enums.UserStatus;
import java.time.LocalDateTime;
import java.util.Locale;

public class User {
    private final int userId;
    private String phone;
    private String name;
    private String email;
    private String passwordHash;
    private UserRole role;
    private UserStatus status;
    private final LocalDateTime createdAt;
    public User(String name,String email,String phone,String passwordHash,UserRole role,UserStatus status) {
        if(name==null||name.isBlank())
            throw new IllegalArgumentException("Name is required");
        if(email==null||email.isBlank()||!email.contains("@"))
            throw new IllegalArgumentException("Valid email is required");
        if(phone==null||phone.isBlank())
            throw new IllegalArgumentException("Phone is required");
        if(passwordHash==null||passwordHash.isBlank())
            throw new IllegalArgumentException("Password hash is required");
        if(role==null||status==null)
            throw new IllegalArgumentException("Role and status are required");
        this.userId = util.IdGenerator.nextUserId();
        this.name=name.trim();
        this.email=email.trim().toLowerCase(Locale.ROOT);
        this.phone=phone.trim();
        this.passwordHash=passwordHash;
        this.role=role;
        this.status=status;
        this.createdAt=LocalDateTime.now();
    }

    public int getUserId() {
        return userId;
    }

    public String getPhone() {
        return phone;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setPhone(String phone) {
        if(phone==null||phone.isBlank())
            throw new IllegalArgumentException("Phone is required");
        this.phone=phone.trim();
    }

    public void setName(String name) {
        if(name==null||name.isBlank())
            throw new IllegalArgumentException("Name is required");
        this.name=name.trim();
    }

    public void setEmail(String email) {
        if(email==null||email.isBlank()||!email.contains("@"))
            throw new IllegalArgumentException("Valid email is required");
        this.email=email.trim().toLowerCase(Locale.ROOT);
    }

    public void setPasswordHash(String hash) {
        if(hash==null||hash.isBlank())
            throw new IllegalArgumentException("Password hash is required");
        passwordHash=hash;
    }

    public void setRole(UserRole role) {
        if(role==null)
            throw new IllegalArgumentException("Role is required");
        this.role=role;
    }

    public void setStatus(UserStatus status) {
        if(status==null)
            throw new IllegalArgumentException("Status is required");
        this.status=status;
    }
}
