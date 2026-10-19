package model;

import util.IdGenerator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class Book {
    private final int bookId;
    private final String isbn;
    private String title;
    private String publisher;
    private int publicationYear;
    private String description;
    private final Set<Author> authors = new LinkedHashSet<>();
    private final Set<Category> categories = new LinkedHashSet<>();
    private final Set<Genre> genres = new LinkedHashSet<>();

    public Book(String isbn, String title, String publisher, int publicationYear, String description) {
        this(IdGenerator.nextBookId(), isbn, title, publisher, publicationYear, description);
    }

    public Book(int bookId, String isbn, String title, String publisher, int publicationYear, String description) {
        if (bookId <= 0) throw new IllegalArgumentException("Book ID must be positive");
        if (isbn == null || isbn.isBlank()) throw new IllegalArgumentException("ISBN is required");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Title is required");
        if (publisher == null || publisher.isBlank()) throw new IllegalArgumentException("Publisher is required");
        if (publicationYear < 1 || publicationYear > java.time.Year.now().getValue() + 1) throw new IllegalArgumentException("Publication year is invalid");
        this.bookId = bookId;
        this.isbn = isbn.trim();
        this.title = title.trim();
        this.publisher = publisher.trim();
        this.publicationYear = publicationYear;
        this.description = description != null ? description.trim() : null;
    }

    public Book(int bookId, String isbn, String title, String publisher, int publicationYear, String description,
                Collection<Author> initialAuthors, Collection<Genre> initialGenres) {
        this(bookId, isbn, title, publisher, publicationYear, description);
        if (initialAuthors != null) {
            for (Author a : initialAuthors) {
                if (a != null) addAuthor(a);
            }
        }
        if (initialGenres != null) {
            for (Genre g : initialGenres) {
                if (g != null) addGenre(g);
            }
        }
    }

    public Book(String isbn, String title, String publisher, int publicationYear, String description,
                Collection<Author> initialAuthors, Collection<Genre> initialGenres) {
        this(IdGenerator.nextBookId(), isbn, title, publisher, publicationYear, description, initialAuthors, initialGenres);
    }

    public int getBookId() {
        return bookId;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getTitle() {
        return title;
    }

    public String getPublisher() {
        return publisher;
    }

    public int getPublicationYear() {
        return publicationYear;
    }

    public String getDescription() {
        return description;
    }

    public Set<Author> getAuthors() {
        return java.util.Collections.unmodifiableSet(authors);
    }

    public Set<Category> getCategories() {
        return java.util.Collections.unmodifiableSet(categories);
    }

    public Set<Genre> getGenres() {
        return java.util.Collections.unmodifiableSet(genres);
    }

    public void setTitle(String title) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Title is required");
        this.title = title.trim();
    }

    public void setPublisher(String publisher) {
        if (publisher == null || publisher.isBlank()) throw new IllegalArgumentException("Publisher is required");
        this.publisher = publisher.trim();
    }

    public void setPublicationYear(int year) {
        if (year < 1 || year > java.time.Year.now().getValue() + 1) throw new IllegalArgumentException("Publication year is invalid");
        this.publicationYear = year;
    }

    public void setDescription(String description) {
        this.description = description != null ? description.trim() : null;
    }

    public String getAuthorsString() {
        if (authors.isEmpty()) return "";
        List<String> names = new ArrayList<>();
        for (Author a : authors) {
            if (a != null && a.getName() != null && !a.getName().isBlank()) {
                names.add(a.getName());
            }
        }
        return String.join(", ", names);
    }

    public String getGenresString() {
        if (genres.isEmpty()) return "";
        List<String> names = new ArrayList<>();
        for (Genre g : genres) {
            if (g != null && g.getName() != null && !g.getName().isBlank()) {
                names.add(g.getName());
            }
        }
        return String.join(", ", names);
    }

    public String getAuthor() {
        return getAuthorsString();
    }

    public void setAuthor(String author) {
        if (author == null || author.isBlank()) throw new IllegalArgumentException("Author is required");
        authors.clear();
        authors.add(new Author(IdGenerator.nextAuthorId(), author.trim()));
    }

    public void addAuthor(Author author) {
        if (author == null)
            throw new IllegalArgumentException("Author is required");
        authors.add(author);
    }

    public void removeAuthor(Author author) {
        authors.remove(author);
    }

    public void clearAuthors() {
        authors.clear();
    }

    public void setAuthors(Collection<Author> newAuthors) {
        authors.clear();
        if (newAuthors != null) {
            for (Author a : newAuthors) {
                if (a != null) addAuthor(a);
            }
        }
    }

    public void addCategory(Category category) {
        if (category == null)
            throw new IllegalArgumentException("Category is required");
        categories.add(category);
    }

    public void removeCategory(Category category) {
        categories.remove(category);
    }

    public void addGenre(Genre genre) {
        if (genre == null)
            throw new IllegalArgumentException("Genre is required");
        genres.add(genre);
    }

    public void removeGenre(Genre genre) {
        genres.remove(genre);
    }

    public void clearGenres() {
        genres.clear();
    }

    public void setGenres(Collection<Genre> newGenres) {
        genres.clear();
        if (newGenres != null) {
            for (Genre g : newGenres) {
                if (g != null) addGenre(g);
            }
        }
    }

    @Override
    public String toString() {
        String authorsStr = getAuthorsString();
        String genresStr = getGenresString();
        return "Book ID: " + bookId
                + ", ISBN: " + isbn
                + ", Title: " + title
                + ", Authors: " + (authorsStr.isEmpty() ? "N/A" : authorsStr)
                + ", Genres: " + (genresStr.isEmpty() ? "N/A" : genresStr)
                + ", Publisher: " + (publisher != null && !publisher.isBlank() ? publisher : "N/A")
                + ", Publication Year: " + publicationYear;
    }
}
