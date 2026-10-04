package com.voxtune.model;

/**
 * Enum representing the current state of audio playback in VoxTune.
 * Demonstrates Java enums, custom constructor, encapsulation, and instance methods.
 */
public enum PlaybackState {
    IDLE("Idle"),
    LOADING("Loading audio..."),
    PLAYING("Playing"),
    PAUSED("Paused"),
    STOPPED("Stopped");

    private final String displayName;

    PlaybackState(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isPlaying() {
        return this == PLAYING;
    }

    public boolean isPaused() {
        return this == PAUSED;
    }

    public boolean canResume() {
        return this == PAUSED;
    }
}
