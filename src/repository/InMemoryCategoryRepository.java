package repository;

import model.Category;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class InMemoryCategoryRepository implements CategoryRepository {
    private final DataStore dataStore;
    public InMemoryCategoryRepository(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    public void save(Category category) {
        if (category == null) throw new IllegalArgumentException("Category is required");
        Category existing = findById(category.getCategoryId());
        if (existing != null) {
            if (existing != category) {
                throw new IllegalArgumentException("Category ID already exists");
            }
            return;
        }
        dataStore.categories.add(category);
    }

    public Category findById(int categoryId) {
        for (Category category : dataStore.categories) {
            if (category.getCategoryId() == categoryId) return category;
        }
        return null;
    }

    public List<Category> findAll() {
        return new ArrayList<>(dataStore.categories);
    }

    public boolean existsById(int categoryId) {
        return findById(categoryId) != null;
    }

    public void deleteById(int categoryId) {
        boolean referenced = false;
        for (model.Book book : dataStore.books.values()) {
            for (model.Category item : book.getCategories()) {
                if (item.getCategoryId() == categoryId) {
                    referenced = true;
                    break;
                }
            }
            if (referenced) {
                break;
            }
        }
        if (referenced) throw new IllegalStateException("Cannot delete a category referenced by a book");
        Iterator<Category> iterator = dataStore.categories.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getCategoryId() == categoryId) {
                iterator.remove();
                return;
            }
        }
    }
}
