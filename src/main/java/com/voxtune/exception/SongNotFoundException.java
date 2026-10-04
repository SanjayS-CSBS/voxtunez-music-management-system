package com.voxtune.exception;

/**
 * Custom checked exception thrown when a requested song or query yields no playable matches.
 * Demonstrates:
 * - Custom exception handling
 * - Exception class inheritance
 * - super constructor calls
 */
public class SongNotFoundException extends Exception {

    public SongNotFoundException(String message) {
        super(message);
    }

    public SongNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
