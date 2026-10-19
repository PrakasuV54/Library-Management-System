package model;

import java.util.Objects;

public class Author {
    private final int authorId;
    private String name;
    public Author(int authorId, String name) {
        if (authorId <= 0)
            throw new IllegalArgumentException("Author ID must be positive");
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Name cannot be blank");
        this.authorId=authorId;
        this.name=name.trim();
    }

    public int getAuthorId() {
        return authorId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name cannot be blank");
        this.name = name.trim();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Author)) return false;
        Author other = (Author) obj;
        return authorId == other.authorId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(authorId);
    }
}
