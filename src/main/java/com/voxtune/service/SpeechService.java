package com.voxtune.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Text-to-Speech service using native Windows speech synthesis.
 * Runs asynchronously on a background worker thread to ensure the JavaFX UI never hangs.
 */
public class SpeechService {
    private final ExecutorService speechExecutor;
    private volatile boolean enabled = true;

    public SpeechService() {
        this.speechExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "VoxTune-SpeechSynthesizer");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Speaks the given text aloud asynchronously in a background thread.
     */
    public void speakAsync(String text) {
        if (!enabled || text == null || text.isBlank()) {
            return;
        }

        speechExecutor.submit(() -> speakSynchronously(text));
    }

    /**
     * Synchronous speech execution via Windows .NET System.Speech / SAPI voice.
     */
    public void speakSynchronously(String text) {
        try {
            // Clean and sanitize text to safe characters for Windows speech
            String sanitized = text.replaceAll("[^a-zA-Z0-9 .,!?-]", " ").replaceAll("\\s+", " ").trim();
            if (sanitized.isEmpty()) return;

            // PowerShell one-liner invoking native Windows Speech Synthesizer
            String psCommand = String.format(
                "Add-Type -AssemblyName System.Speech; $s = New-Object System.Speech.Synthesis.SpeechSynthesizer; $s.Rate = 0; $s.Volume = 100; $s.Speak('%s')",
                sanitized
            );

            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", psCommand);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            process.waitFor();

        } catch (InterruptedException ignored) {
            // Clean thread interruption during shutdown
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Keep silent or minimal log so user experience remains seamless
            if (e.getMessage() != null) {
                System.err.println("Speech notice: " + e.getMessage());
            }
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void shutdown() {
        enabled = false;
        speechExecutor.shutdownNow();
    }
}
