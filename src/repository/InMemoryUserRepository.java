package repository;

import model.User;
import java.util.ArrayList;
import java.util.List;

public class InMemoryUserRepository implements UserRepository {
    private final DataStore dataStore;
    public InMemoryUserRepository(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    public DataStore getDataStore() {
        return dataStore;
    }

    public void save(User user) {
        if (user == null || user.getPhone() == null || user.getPhone().isBlank()
        || user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalArgumentException("User, phone, and email are required");
        }
        user.setPhone(user.getPhone().trim());
        user.setEmail(user.getEmail());
        User emailOwner = dataStore.userByEmail.get(user.getEmail());
        if (emailOwner != null && !emailOwner.getPhone().equals(user.getPhone())) {
            throw new IllegalArgumentException("Email already exists");
        }
        java.util.Iterator<java.util.Map.Entry<String, User>> iterator = dataStore.userByEmail.entrySet().iterator();
        while (iterator.hasNext()) {
            java.util.Map.Entry<String, User> entry = iterator.next();
            if (entry.getValue().getPhone().equals(user.getPhone())
            && !entry.getKey().equals(user.getEmail())) {
                iterator.remove();
            }
        }
        dataStore.userById.put(user.getUserId(), user);
        dataStore.userByPhone.put(user.getPhone(), user);
        dataStore.userByEmail.put(user.getEmail(), user);
    }

    public User findById(int userId) {
        return dataStore.userById.get(userId);
    }

    /** Legacy phone lookup; use findByPhone when the lookup key is a phone number. */
    public User findById(String phone) {
        if (phone == null) return null;
        String trimmed = phone.trim();
        User user = dataStore.userByPhone.get(trimmed);
        if (user == null && trimmed.matches("\\d+")) {
            try {
                user = dataStore.userById.get(Integer.parseInt(trimmed));
            } catch (NumberFormatException ignored) {}
        }
        return user;
    }

    public User findByEmail(String email) {
        if (email == null) return null;
        return dataStore.userByEmail.get(email.trim().toLowerCase(java.util.Locale.ROOT));
    }

    public User findByPhone(String phone) {
        return findById(phone);
    }

    public List<User> findAll() {
        return new ArrayList<>(dataStore.userByPhone.values());
    }

    public boolean existsById(String phone) {
        if (phone == null) return false;
        String trimmed = phone.trim();
        if (dataStore.userByPhone.containsKey(trimmed)) return true;
        if (trimmed.matches("\\d+")) {
            try {
                return dataStore.userById.containsKey(Integer.parseInt(trimmed));
            } catch (NumberFormatException ignored) {}
        }
        return false;
    }

    public boolean existsByEmail(String email) {
        return email != null && dataStore.userByEmail.containsKey(email.trim().toLowerCase(java.util.Locale.ROOT));
    }

    public boolean existsByPhone(String phone) {
        return phone != null && dataStore.userByPhone.containsKey(phone.trim());
    }

    @Override
    public boolean changePhone(String oldPhone, String newPhone) {
        if (oldPhone == null || oldPhone.isBlank() || newPhone == null || newPhone.isBlank()) {
            return false;
        }

        oldPhone = oldPhone.trim();
        newPhone = newPhone.trim();
        if (oldPhone.equals(newPhone) || dataStore.userByPhone.containsKey(newPhone)) {
            return false;
        }

        User user = dataStore.userByPhone.get(oldPhone);
        if (user == null) {
            return false;
        }

        dataStore.userByPhone.remove(oldPhone);
        user.setPhone(newPhone);
        dataStore.userByPhone.put(newPhone, user);
        dataStore.updateUserPhoneReferences(oldPhone, newPhone);
        return true;
    }

    public boolean hasDependentRecords(String phone) {
        return phone != null && dataStore.hasUserHistory(phone.trim());
    }

    public void deleteById(String phone) {
        if (phone == null) return;
        phone = phone.trim();
        User user = dataStore.userByPhone.remove(phone);
        if (user != null) {
            dataStore.userByEmail.remove(user.getEmail());
            dataStore.userById.remove(user.getUserId());
        }
    }
}
