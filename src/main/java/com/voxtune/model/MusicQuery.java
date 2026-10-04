package com.voxtune.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Model representing a parsed structured music query or playback request.
 * Demonstrates:
 * - Complex model encapsulation
 * - Jackson JSON mapping annotations
 * - Builder pattern / convenience constructors
 * - Safe null-check utility methods
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MusicQuery {
    @JsonProperty("intent")
    private CommandIntent intent = CommandIntent.PLAY;

    @JsonProperty("artist")
    private String artist;

    @JsonProperty("genre")
    private String genre;

    @JsonProperty("fromYear")
    private Integer fromYear;

    @JsonProperty("toYear")
    private Integer toYear;

    @JsonProperty("mood")
    private String mood;

    @JsonProperty("text")
    private String text;

    @JsonProperty("limit")
    private Integer limit;

    public MusicQuery() {
    }

    public MusicQuery(CommandIntent intent) {
        this.intent = intent;
    }

    public MusicQuery(CommandIntent intent, String text) {
        this.intent = intent;
        this.text = text;
    }

    public MusicQuery(CommandIntent intent, String artist, String genre, Integer fromYear, Integer toYear, String mood, String text, Integer limit) {
        this.intent = (intent != null) ? intent : CommandIntent.PLAY;
        this.artist = artist;
        this.genre = genre;
        this.fromYear = fromYear;
        this.toYear = toYear;
        this.mood = mood;
        this.text = text;
        this.limit = limit;
    }

    // Getters and Setters
    public CommandIntent getIntent() {
        return intent;
    }

    public void setIntent(CommandIntent intent) {
        this.intent = intent;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public Integer getFromYear() {
        return fromYear;
    }

    public void setFromYear(Integer fromYear) {
        this.fromYear = fromYear;
    }

    public Integer getToYear() {
        return toYear;
    }

    public void setToYear(Integer toYear) {
        this.toYear = toYear;
    }

    public String getMood() {
        return mood;
    }

    public void setMood(String mood) {
        this.mood = mood;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    // Query helper methods
    public boolean hasArtist() {
        return artist != null && !artist.isBlank();
    }

    public boolean hasGenre() {
        return genre != null && !genre.isBlank();
    }

    public boolean hasYearFilter() {
        return fromYear != null || toYear != null;
    }

    public boolean hasMood() {
        return mood != null && !mood.isBlank();
    }

    public boolean hasText() {
        return text != null && !text.isBlank();
    }

    @Override
    public String toString() {
        return "MusicQuery{" +
                "intent=" + intent +
                ", artist='" + artist + '\'' +
                ", genre='" + genre + '\'' +
                ", fromYear=" + fromYear +
                ", toYear=" + toYear +
                ", mood='" + mood + '\'' +
                ", text='" + text + '\'' +
                ", limit=" + limit +
                '}';
    }
}
