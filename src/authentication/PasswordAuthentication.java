package authentication;

import model.User;
import service.AuthService;

public class PasswordAuthentication implements AuthenticationStrategy {
    @Override
    public boolean authenticate(User user, String password) {
        if (user == null || password == null || password.isBlank()) {
            return false;
        }

        return AuthService.verifyPassword(password, user.getPasswordHash());
    }
}
