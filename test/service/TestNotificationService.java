package service;

import model.User;
import repository.UserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TestNotificationService extends NotificationService {

    public static class Notification {
        private final String recipient;
        private final String message;
        private final LocalDateTime createdAt = LocalDateTime.now();

        public Notification(String recipient, String message) {
            this.recipient = recipient;
            this.message = message;
        }

        public String getRecipient() {
            return recipient;
        }

        public String getMessage() {
            return message;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }
    }

    private final List<Notification> sent = new ArrayList<>();

    public TestNotificationService() {
        super();
    }

    public TestNotificationService(UserRepository userRepository) {
        super(userRepository);
    }

    @Override
    public boolean send(String recipient, String message) {
        if (recipient == null || recipient.isBlank() || message == null || message.isBlank()) {
            return false;
        }

        String trimmedRecipient = recipient.trim();
        String formattedMessage = message;

        if (!message.startsWith("Notification for:")) {
            String recipientName = null;
            String recipientPhone = trimmedRecipient;
            if (getUserRepository() != null) {
                User user = getUserRepository().findByPhone(trimmedRecipient);
                if (user == null && trimmedRecipient.matches("\\d+")) {
                    try {
                        user = getUserRepository().findById(Integer.parseInt(trimmedRecipient));
                    } catch (NumberFormatException ignored) {}
                }
                if (user != null) {
                    if (user.getName() != null && !user.getName().isBlank()) {
                        recipientName = user.getName().trim();
                    }
                    if (user.getPhone() != null && !user.getPhone().isBlank()) {
                        recipientPhone = user.getPhone().trim();
                    }
                }
            }
            if (recipientName == null && trimmedRecipient.matches("\\d+") && trimmedRecipient.length() < 7) {
                recipientName = "Member (ID: " + trimmedRecipient + ")";
                recipientPhone = null;
            }
            formattedMessage = formatNotificationMessage(recipientName, recipientPhone, message);
        }

        sent.add(new Notification(trimmedRecipient, formattedMessage));
        return super.send(recipient, message);
    }

    public List<Notification> getSent() {
        return new ArrayList<>(sent);
    }

    public void clear() {
        sent.clear();
    }
}
