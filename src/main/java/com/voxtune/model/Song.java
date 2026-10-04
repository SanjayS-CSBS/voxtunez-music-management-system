package com.voxtune.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Domain model representing a musical track in the user's library.
 * Demonstrates:
 * - OOP Encapsulation (private fields, public accessors)
 * - Constructor overloading (multiple constructors)
 * - this keyword
 * - Method overriding (equals, hashCode, toString)
 * - Java 8/17 Date and Time API (LocalDateTime)
 */
public class Song {
    private int id;
    private String title;
    private String artist;
    private String album;
    private String genre;
    private int year;
    private String filePath;
    private double duration; // in seconds
    private boolean favorite;
    private LocalDateTime dateAdded;

    // Default constructor
    public Song() {
        this.title = "Unknown Title";
        this.artist = "Unknown Artist";
        this.album = "Unknown Album";
        this.genre = "General";
        this.year = 0;
        this.filePath = "";
        this.duration = 0.0;
        this.favorite = false;
        this.dateAdded = LocalDateTime.now();
    }

    // Overloaded constructor without ID (used when discovering new files)
    public Song(String title, String artist, String album, String genre, int year, String filePath, double duration) {
        this(0, title, artist, album, genre, year, filePath, duration, false, LocalDateTime.now());
    }

    // Master constructor with all fields
    public Song(int id, String title, String artist, String album, String genre, int year, 
                String filePath, double duration, boolean favorite, LocalDateTime dateAdded) {
        this.id = id;
        this.title = (title != null && !title.isBlank()) ? title.trim() : "Unknown Title";
        this.artist = (artist != null && !artist.isBlank()) ? artist.trim() : "Unknown Artist";
        this.album = (album != null && !album.isBlank()) ? album.trim() : "Singles";
        this.genre = (genre != null && !genre.isBlank()) ? genre.trim() : "General";
        this.year = year;
        this.filePath = filePath;
        this.duration = duration;
        this.favorite = favorite;
        this.dateAdded = (dateAdded != null) ? dateAdded : LocalDateTime.now();
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public double getDuration() {
        return duration;
    }

    public void setDuration(double duration) {
        this.duration = duration;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public LocalDateTime getDateAdded() {
        return dateAdded;
    }

    public void setDateAdded(LocalDateTime dateAdded) {
        this.dateAdded = dateAdded;
    }

    /**
     * Formats duration into MM:SS format.
     */
    public String getFormattedDuration() {
        int totalSecs = (int) Math.round(duration);
        int mins = totalSecs / 60;
        int secs = totalSecs % 60;
        return String.format("%d:%02d", mins, secs);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Song other)) return false;
        return Objects.equals(filePath, other.filePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filePath);
    }

    @Override
    public String toString() {
        return String.format("%s - %s (%d) [%s]", artist, title, year, getFormattedDuration());
    }
}
