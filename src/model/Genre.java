package model;

import java.util.Objects;

public class Genre {
    private final int genreId;
    private String name;
    public Genre(int genreId, String name) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Name cannot be blank");
        this.genreId=genreId;
        this.name=name.trim();
    }

    public int getGenreId() {
        return genreId;
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
        if (!(obj instanceof Genre)) return false;
        Genre other = (Genre) obj;
        return genreId == other.genreId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(genreId);
    }
}
