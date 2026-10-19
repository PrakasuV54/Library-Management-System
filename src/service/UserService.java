package service;

import enums.UserRole;
import enums.UserStatus;
import model.User;
import repository.UserRepository;

public class UserService {
    private final UserRepository inMemoryUserRepository;
    private final AuditService auditService;


    public UserService(UserRepository inMemoryUserRepository, AuditService auditService) {
        this.inMemoryUserRepository=inMemoryUserRepository;
        this.auditService=auditService;
    }

    private void record(String phone, String action, String details) {
        if (auditService != null) auditService.record(phone, action, "USER", phone, details);
    }


    public User getProfileByPhone(String phone, User caller) {
        return inMemoryUserRepository.findByPhone(phone);
    }


    public boolean createUser(String name, String email, String phone, String password, UserRole role) {
        if (role != UserRole.MEMBER)
            return false;
        if (name == null || name.isBlank() || email == null || email.isBlank() || phone == null || phone.isBlank() || password == null || password.isBlank()) {
            return false;
        }
        if(inMemoryUserRepository.findById(phone) != null) {
            return false;
        }
        if(inMemoryUserRepository.findByEmail(email) != null) {
            return false;
        }
        User user = new User(name, email, phone, AuthService.hashPassword(password), role, UserStatus.ACTIVE);
        inMemoryUserRepository.save(user);
        record(phone, "USER_CREATED", "User account created");
        return true;
    }

    public boolean addLibrarian(User admin, String name, String email, String phone, String password) {
        if (isCurrentAdmin(admin) || !validNewUser(name, email, phone, password))
            return false;
        if (inMemoryUserRepository.findByPhone(phone) != null || inMemoryUserRepository.findByEmail(email) != null)
            return false;
        User librarian = new User(name, email, phone, AuthService.hashPassword(password),  UserRole.LIBRARIAN, UserStatus.ACTIVE);
        inMemoryUserRepository.save(librarian);
        record(admin.getPhone(), "LIBRARIAN_CREATED", "Created librarian account for " + phone);
        return true;
    }

    public boolean addAdmin(User currentAdmin, String name, String email, String phone, String password) {
        if (isCurrentAdmin(currentAdmin) || !validNewUser(name, email, phone, password))
            return false;
        if (inMemoryUserRepository.findByPhone(phone) != null || inMemoryUserRepository.findByEmail(email) != null)
            return false;
        User admin = new User(name, email, phone, AuthService.hashPassword(password),UserRole.ADMIN, UserStatus.ACTIVE);
        inMemoryUserRepository.save(admin);
        record(currentAdmin.getPhone(), "ADMIN_CREATED", "Created admin account for " + phone);
        return true;
    }

    public boolean changeUserRole(User admin, int userId, UserRole newRole) {
        if (isCurrentAdmin(admin) || newRole == null || userId == admin.getUserId())
            return false;
        User target = inMemoryUserRepository.findById(userId);
        if (target == null || target.getRole() == newRole)
            return false;
        target.setRole(newRole);
        inMemoryUserRepository.save(target);
        record(admin.getPhone(), "USER_ROLE_CHANGED", "User ID " + userId + " role changed to " + newRole);
        return true;
    }

    public boolean activateAccount(User admin, int userId) {
        if (isCurrentAdmin(admin))
            return false;
        User target = inMemoryUserRepository.findById(userId);
        if (target == null || target.getStatus() != UserStatus.INACTIVE)
            return false;
        target.setStatus(UserStatus.ACTIVE);
        inMemoryUserRepository.save(target);
        record(admin.getPhone(), "USER_ACTIVATED", "User ID " + userId + " activated");
        return true;
    }

    public boolean deactivateAccount(User admin, int userId) {
        if (isCurrentAdmin(admin)) return false;
        User target = inMemoryUserRepository.findById(userId);
        if (target == null || target.getStatus() != UserStatus.ACTIVE || target.getUserId() == admin.getUserId())
            return false;
        target.setStatus(UserStatus.INACTIVE);
        inMemoryUserRepository.save(target);
        record(admin.getPhone(), "USER_DEACTIVATED", "User ID " + userId + " deactivated");
        return true;
    }

    public java.util.List<User> getAllUsers(User admin) {
        if (isCurrentAdmin(admin)) return java.util.Collections.emptyList();
        return inMemoryUserRepository.findAll();
    }

    private boolean validNewUser(String name, String email, String phone, String password) {
        return name != null && !name.isBlank() && email != null && email.contains("@") && phone != null && !phone.isBlank() && password != null && !password.isBlank();
    }

    private boolean isCurrentAdmin(User admin) {
        if (admin == null || admin.getStatus() != UserStatus.ACTIVE || admin.getRole() != UserRole.ADMIN) return true;
        User stored = inMemoryUserRepository.findByPhone(admin.getPhone());
        return stored == null || stored.getUserId() != admin.getUserId() || stored.getStatus() != UserStatus.ACTIVE || stored.getRole() != UserRole.ADMIN;
    }



    public boolean deleteUser(User admin, int userId) {
        if (isCurrentAdmin(admin) || userId == admin.getUserId()) return false;
        User target = inMemoryUserRepository.findById(userId);
        if (target == null || inMemoryUserRepository.hasDependentRecords(target.getPhone())) return false;
        String phone = target.getPhone();
        inMemoryUserRepository.deleteById(phone);
        record(admin.getPhone(), "USER_DELETED", "User ID " + userId + " deleted");
        return true;
    }

    public boolean updateNameByPhone(String name, String phone, User caller) {
        if (!isAdmin(caller, phone))
            return false;
        return updateNameForPhone(name, phone);
    }

    public boolean updateNameByPhone(String name,String phone) {
        return false;
    }

    private boolean updateNameForPhone(String name, String phone) {
        if (name == null || name.isBlank() || phone == null || phone.isBlank()) return false;
        User result = inMemoryUserRepository.findByPhone(phone);
        if(result != null) {
            User user = result;
            if(!(user.getName().equals(name))) {
                user.setName(name);
                inMemoryUserRepository.save(user);
                record(phone, "USER_NAME_CHANGED", "Name updated");
                return true;
            }
            return false;
        }
        return false;
    }

    public boolean updateNameByEmail(String name,String email) {
        return false;
    }

    public boolean updateNameByEmail(String name, String email, User caller) {
        if (email == null || caller == null) return false;
        User target = inMemoryUserRepository.findByEmail(email);
        if (target == null || !isAdmin(caller, target.getPhone())) return false;
        return updateNameForPhone(name, target.getPhone());
    }

    public boolean changePasswordByPhone(String phone, String oldPassword, String newPassword, User caller) {
        if (!isOwner(caller, phone)) return false;
        return changePasswordForPhone(phone, oldPassword, newPassword);
    }

    public boolean changePasswordByPhone(String phone, String oldPassword, String newPassword) {
        return false;
    }

    private boolean changePasswordForPhone(String phone, String oldPassword, String newPassword) {
        if (phone == null || phone.isBlank() || oldPassword == null || newPassword == null || newPassword.isBlank()) return false;
        User result = inMemoryUserRepository.findByPhone(phone);
        if (result == null) {
            return false;
        }
        User user = result;
        if (!AuthService.verifyPassword(oldPassword, user.getPasswordHash())) {
            return false;
        }
        if (AuthService.verifyPassword(newPassword, user.getPasswordHash())) {
            return false;
        }
        user.setPasswordHash(AuthService.hashPassword(newPassword));
        inMemoryUserRepository.save(user);
        record(phone, "PASSWORD_CHANGED", "Password changed");
        return true;
    }

    public boolean changePasswordByEmail(String email, String oldPassword, String newPassword) {
        if (oldPassword == null || newPassword == null || newPassword.isBlank()) return false;
        User result = inMemoryUserRepository.findByEmail(email);
        if (result == null) {
            return false;
        }
        User user = result;
        if (!AuthService.verifyPassword(oldPassword, user.getPasswordHash())) {
            return false;
        }
        if (AuthService.verifyPassword(newPassword, user.getPasswordHash())) {
            return false;
        }
        user.setPasswordHash(AuthService.hashPassword(newPassword));
        inMemoryUserRepository.save(user);
        record(user.getPhone(), "PASSWORD_CHANGED", "Password changed by email");
        return true;
    }

    public boolean changePhoneByPhone(String oldPhone, String newPhone, User caller) {
        if (!isAdmin(caller, oldPhone)) return false;
        return changePhoneForPhone(oldPhone, newPhone);
    }

    public boolean changePhoneByPhone(String oldPhone, String newPhone) {
        return false;
    }

    private boolean changePhoneForPhone(String oldPhone, String newPhone) {
        if (oldPhone == null || oldPhone.isBlank() || newPhone == null || newPhone.isBlank()) return false;
        boolean changed = inMemoryUserRepository.changePhone(oldPhone, newPhone);
        if (changed) record(oldPhone, "PHONE_CHANGED", "Phone changed to " + newPhone);
        return changed;
    }

    public boolean changePhoneByEmail(String newPhone, String email) {
        return false;
    }

    public boolean changePhoneByEmail(String newPhone, String email, User caller) {
        if (email == null || caller == null) return false;
        User target = inMemoryUserRepository.findByEmail(email);
        if (target == null || !isAdmin(caller, target.getPhone())) return false;
        return changePhoneForPhone(target.getPhone(), newPhone);
    }

    public boolean changeEmailByPhone(String email, String phone, User caller) {
        if (!isAdmin(caller, phone)) return false;
        return changeEmailForPhone(email, phone);
    }

    public boolean changeEmailByPhone(String email,String phone) {
        return false;
    }

    private boolean changeEmailForPhone(String email,String phone) {
        if (email == null || email.isBlank() || !email.contains("@") || phone == null || phone.isBlank()) return false;
        User result = inMemoryUserRepository.findByPhone(phone);
        if(result != null) {
            User user = result;
            if(!inMemoryUserRepository.existsByEmail(email)) {
                user.setEmail(email);
                inMemoryUserRepository.save(user);
                record(phone, "EMAIL_CHANGED", "Email address updated");
                return true;
            }
            return false;
        }
        return false;
    }

    public boolean changeEmailByEmail(String emailOld,String emailNew) {
        return false;
    }

    public boolean changeEmailByEmail(String emailOld, String emailNew, User caller) {
        if (emailOld == null || caller == null) return false;
        User target = inMemoryUserRepository.findByEmail(emailOld);
        if (target == null || !isAdmin(caller, target.getPhone())) return false;
        return changeEmailForPhone(emailNew, target.getPhone());
    }

    private boolean isOwner(User caller, String targetPhone) {
        if (caller == null || targetPhone == null || caller.getPhone() == null
                || caller.getStatus() != UserStatus.ACTIVE) return false;
        User stored = inMemoryUserRepository.findByPhone(caller.getPhone());
        return stored != null && stored.getUserId() == caller.getUserId()
                && stored.getStatus() == UserStatus.ACTIVE
                && stored.getPhone().equals(targetPhone);
    }

    private boolean isAdmin(User caller, String targetPhone) {
        if (isOwner(caller, targetPhone)) return true;
        if (caller == null || caller.getPhone() == null || caller.getStatus() != UserStatus.ACTIVE
                || caller.getRole() != UserRole.ADMIN) return false;
        User stored = inMemoryUserRepository.findByPhone(caller.getPhone());
        return stored != null && stored.getUserId() == caller.getUserId()
                && stored.getStatus() == UserStatus.ACTIVE && stored.getRole() == UserRole.ADMIN;
    }

    public String getRegistrationFailureReason(String name, String email, String phone, String password) {
        if (name == null || name.isBlank()) {
            return "Name cannot be empty.";
        }
        if (phone == null || phone.isBlank()) {
            return "Phone number cannot be empty.";
        }
        if (!phone.matches("\\d{10}")) {
            return "Phone number must be a valid 10-digit number.";
        }
        if (inMemoryUserRepository.findById(phone) != null) {
            return "An account with phone number " + phone + " already exists. Please log in or use a different phone number.";
        }
        if (email == null || email.isBlank() || !email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            return "Invalid email format. Please provide a valid email address (e.g. name@example.com).";
        }
        if (inMemoryUserRepository.findByEmail(email) != null) {
            return "An account with email address " + email + " already exists. Please log in or use a different email address.";
        }
        if (password == null || password.isBlank()) {
            return "Password cannot be blank.";
        }
        if (password.length() < 8) {
            return "Password must be at least 8 characters long.";
        }
        return null;
    }

    public String getAddStaffFailureReason(User admin, String name, String email, String phone, String password, UserRole role) {
        if (isCurrentAdmin(admin)) {
            return "Insufficient permissions. Active administrator privileges required.";
        }
        if (name == null || name.isBlank()) {
            return "Name cannot be empty.";
        }
        if (phone == null || phone.isBlank() || !phone.matches("\\d{10}")) {
            return "Phone number must be a valid 10-digit number.";
        }
        if (inMemoryUserRepository.findByPhone(phone) != null) {
            return "Phone number '" + phone + "' is already registered to another user.";
        }
        if (email == null || email.isBlank() || !email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            return "Invalid email format. Please provide a valid email address.";
        }
        if (inMemoryUserRepository.findByEmail(email) != null) {
            return "Email address '" + email + "' is already registered to another user.";
        }
        if (password == null || password.isBlank()) {
            return "Password cannot be blank.";
        }
        if (password.length() < 8) {
            return "Password must be at least 8 characters long.";
        }
        return null;
    }

    public String getUserUpdateFailureReason(User admin, int userId, int actionChoice, UserRole newRole) {
        if (isCurrentAdmin(admin)) {
            return "Insufficient permissions. Active administrator privileges required.";
        }
        User target = inMemoryUserRepository.findById(userId);
        if (target == null) {
            return "User with ID " + userId + " not found.";
        }
        if (actionChoice == 4) { // activate
            if (target.getStatus() == UserStatus.ACTIVE) {
                return "User account is already active.";
            }
        } else if (actionChoice == 5) { // deactivate
            if (target.getUserId() == admin.getUserId()) {
                return "Administrators cannot deactivate their own account.";
            }
            if (target.getStatus() == UserStatus.INACTIVE) {
                return "User account is already inactive.";
            }
        } else if (actionChoice == 6) { // change role
            if (target.getUserId() == admin.getUserId()) {
                return "Administrators cannot change their own role.";
            }
            if (newRole == null) {
                return "Invalid role specified.";
            }
            if (target.getRole() == newRole) {
                return "User already holds the " + newRole + " role.";
            }
        } else if (actionChoice == 7) { // delete
            if (target.getUserId() == admin.getUserId()) {
                return "Administrators cannot delete their own account.";
            }
            if (inMemoryUserRepository.hasDependentRecords(target.getPhone())) {
                return "Cannot delete user with existing borrowing records, active loans, fines, or memberships.";
            }
        }
        return null;
    }
}
