package com.voxtune.model;

/**
 * Enumeration of supported user command intents.
 * Demonstrates enum usage with categorization helper methods.
 */
public enum CommandIntent {
    PLAY,
    PAUSE,
    RESUME,
    STOP,
    NEXT,
    PREVIOUS,
    VOLUME_UP,
    VOLUME_DOWN,
    FAVORITE,
    UNFAVORITE,
    SEARCH,
    CONVERSATION,
    UNKNOWN;

    public boolean isPlaybackControl() {
        return this == PAUSE || this == RESUME || this == STOP ||
               this == NEXT || this == PREVIOUS || this == VOLUME_UP || this == VOLUME_DOWN;
    }

    public boolean isSearchOrPlay() {
        return this == PLAY || this == SEARCH;
    }
}
