package service;

import model.User;
import repository.UserRepository;

public class NotificationService {
    private UserRepository userRepository;

    public NotificationService() {}

    public NotificationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserRepository getUserRepository() {
        return userRepository;
    }

    public void setUserRepository(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean send(String recipient, String message) {
        if (recipient == null || recipient.isBlank() || message == null || message.isBlank()) {
            return false;
        }

        String trimmedRecipient = recipient.trim();
        String formattedMessage = message;

        if (!message.startsWith("Notification for:")) {
            String recipientName = null;
            String recipientPhone = trimmedRecipient;
            if (userRepository != null) {
                User user = userRepository.findByPhone(trimmedRecipient);
                if (user == null && trimmedRecipient.matches("\\d+")) {
                    try {
                        user = userRepository.findById(Integer.parseInt(trimmedRecipient));
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

        System.out.println(formattedMessage);
        return true;
    }

    public boolean send(User recipient, String message) {
        if (recipient == null || message == null || message.isBlank()) {
            return false;
        }
        return send(recipient.getName(), recipient.getPhone(), message);
    }

    public boolean send(String recipientName, String recipientPhone, String message) {
        if ((recipientPhone == null || recipientPhone.isBlank()) && (recipientName == null || recipientName.isBlank())) {
            return false;
        }
        if (message == null || message.isBlank()) {
            return false;
        }

        String effectivePhone = (recipientPhone != null && !recipientPhone.isBlank()) ? recipientPhone.trim() : null;
        String effectiveName = (recipientName != null && !recipientName.isBlank()) ? recipientName.trim() : null;

        if (userRepository != null) {
            User user = null;
            if (effectivePhone != null) {
                user = userRepository.findByPhone(effectivePhone);
            }
            if (user == null && effectiveName != null && effectiveName.matches("\\d+")) {
                try {
                    user = userRepository.findById(Integer.parseInt(effectiveName));
                } catch (NumberFormatException ignored) {}
            }
            if (user != null) {
                if (user.getName() != null && !user.getName().isBlank()) {
                    effectiveName = user.getName().trim();
                }
                if (effectivePhone == null && user.getPhone() != null && !user.getPhone().isBlank()) {
                    effectivePhone = user.getPhone().trim();
                }
            }
        }

        if (effectiveName != null && effectiveName.matches("\\d+")) {
            effectiveName = "Member (ID: " + effectiveName + ")";
        }

        String recipientId = (effectivePhone != null) ? effectivePhone : effectiveName;
        String formattedMessage = formatNotificationMessage(effectiveName, effectivePhone, message);
        return send(recipientId, formattedMessage);
    }

    public static String formatNotificationMessage(String recipientName, String recipientPhone, String message) {
        if (message == null) {
            return "";
        }
        if (message.startsWith("Notification for:")) {
            return message;
        }

        String name = (recipientName != null && !recipientName.isBlank()) ? recipientName.trim() : null;
        String phone = (recipientPhone != null && !recipientPhone.isBlank()) ? recipientPhone.trim() : null;

        if (name != null && name.matches("\\d+")) {
            name = "Member (ID: " + name + ")";
        }

        String header;
        if (name != null && phone != null && !name.equalsIgnoreCase(phone)) {
            header = "Notification for: " + name + " (Phone: " + phone + ")";
        } else if (phone != null) {
            header = "Notification for: " + phone;
        } else if (name != null) {
            header = "Notification for: " + name;
        } else {
            header = "Notification for: Unknown";
        }

        return header + "\nMessage: " + message;
    }
}
