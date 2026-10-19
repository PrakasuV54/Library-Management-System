package authentication;

import model.User;

public interface AuthenticationStrategy {
    boolean authenticate(User user, String credential);
}
