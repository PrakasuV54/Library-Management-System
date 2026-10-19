package service;

import authentication.AuthenticationStrategy;
import enums.UserRole;
import enums.UserStatus;
import model.User;
import org.mindrot.jbcrypt.BCrypt;
import repository.UserRepository;

public class AuthService {
    private final UserService userService;
    private final UserRepository userRepository;
    private final AuthenticationStrategy authenticationStrategy;
    private final AuditService auditService;

    public AuthService(UserService userService, UserRepository userRepository,
                       AuthenticationStrategy authenticationStrategy) {
        this(userService, userRepository, authenticationStrategy, null);
    }

    public AuthService(UserService userService, UserRepository userRepository,
                       AuthenticationStrategy authenticationStrategy, AuditService auditService) {
        if (userService == null || userRepository == null || authenticationStrategy == null) {
            throw new IllegalArgumentException("User service, repository, and authentication strategy are required");
        }
        this.userService = userService;
        this.userRepository = userRepository;
        this.authenticationStrategy = authenticationStrategy;
        this.auditService = auditService;
    }

    public boolean register(String name, String email, String phone, String password) {
        return userService.createUser(name, email, phone, password, UserRole.MEMBER);
    }


    public User login(String identifier, String password) {
        if (identifier == null || identifier.isBlank() || password == null || password.isBlank()) return null;
        User user = userRepository.findByPhone(identifier.trim());
        if (user == null) user = userRepository.findByEmail(identifier.trim());
        if (user == null || user.getStatus() != UserStatus.ACTIVE  || !authenticationStrategy.authenticate(user, password))
            return null;
        record(user.getPhone(), "LOGIN_SUCCESS", "User authenticated");
        return user;
    }

    public boolean hasRole(User authenticatedUser, UserRole requiredRole) {
        if (authenticatedUser == null || requiredRole == null
                || authenticatedUser.getStatus() != UserStatus.ACTIVE) {
            return false;
        }
        User stored = (userRepository != null && authenticatedUser.getPhone() != null)
                ? userRepository.findByPhone(authenticatedUser.getPhone())
                : null;
        if (stored != null) {
            if (stored.getUserId() != authenticatedUser.getUserId()
                    || stored.getStatus() != UserStatus.ACTIVE) {
                return false;
            }
        }
        UserRole actualRole = (stored != null && stored.getRole() != null)
                ? stored.getRole()
                : authenticatedUser.getRole();
        if (actualRole == null) return false;

        switch (requiredRole) {
            case MEMBER:
                return actualRole == UserRole.MEMBER
                        || actualRole == UserRole.LIBRARIAN
                        || actualRole == UserRole.ADMIN;
            case LIBRARIAN:
                return actualRole == UserRole.LIBRARIAN
                        || actualRole == UserRole.ADMIN;
            case ADMIN:
                return actualRole == UserRole.ADMIN;
            default:
                return false;
        }
    }

    private void record(String phone, String action, String details) {
        if (auditService != null) auditService.record(phone, action, "AUTHENTICATION", phone, details);
    }

    public static String hashPassword(String password) {
        if (password == null || password.isBlank()) throw new IllegalArgumentException("Password cannot be blank");
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    public static boolean verifyPassword(String password, String passwordHash) {
        if (password == null || passwordHash == null || passwordHash.isBlank())
            return false;
        try { return BCrypt.checkpw(password, passwordHash); }
        catch (IllegalArgumentException e) {
            return false;
        }
    }
}
