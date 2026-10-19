package repository;

import model.Category;
import java.util.List;

public interface CategoryRepository {
    void save(Category category);
    Category findById(int categoryId);
    List<Category> findAll();
    boolean existsById(int categoryId);
    void deleteById(int categoryId);
}
