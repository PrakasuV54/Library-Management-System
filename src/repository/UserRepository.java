package repository;

import model.User;
import java.util.List;

public interface UserRepository {
    void save(User user);
    User findById(int userId);
    User findById(String phone);
    User findByEmail(String email);
    User findByPhone(String phone);
    List<User> findAll();
    boolean existsById(String phone);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
    boolean changePhone(String oldPhone, String newPhone);
    boolean hasDependentRecords(String phone);
    void deleteById(String phone);
}
