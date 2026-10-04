package com.voxtune.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.vosk.Model;
import org.vosk.Recognizer;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Offline voice listener capturing live microphone input and transcribing speech via Vosk.
 * Demonstrates:
 * - Java Sound API (TargetDataLine, AudioFormat, AudioSystem)
 * - Vosk offline Speech-to-Text neural model integration
 * - Multithreaded daemon audio capture loop
 * - Jackson JSON tree parsing for partial and final transcription
 * - Functional callbacks (Consumer) and thread-safe flags (AtomicBoolean)
 */
public class VoiceListener {
    private static final String DEFAULT_MODEL_DIR = "models/vosk-model-en-us-0.22-lgraph";
    private static final float SAMPLE_RATE = 16000.0f;

    private final ObjectMapper mapper = new ObjectMapper();
    private Model model;
    private Recognizer recognizer;
    private TargetDataLine microphone;

    private final AtomicBoolean isListening = new AtomicBoolean(false);
    private Thread captureThread;

    private Consumer<String> onPartialResultCallback;
    private Consumer<String> onFinalResultCallback;
    private Consumer<String> onStatusCallback;
    private Consumer<String> onErrorCallback;

    public VoiceListener() {
    }

    public void setOnPartialResult(Consumer<String> callback) {
        this.onPartialResultCallback = callback;
    }

    public void setOnFinalResult(Consumer<String> callback) {
        this.onFinalResultCallback = callback;
    }

    public void setOnStatus(Consumer<String> callback) {
        this.onStatusCallback = callback;
    }

    public void setOnError(Consumer<String> callback) {
        this.onErrorCallback = callback;
    }

    /**
     * Initializes the Vosk model from disk.
     */
    public synchronized boolean initModel() {
        if (model != null) {
            return true;
        }

        String modelPath = System.getenv("VOSK_MODEL_PATH");
        if (modelPath == null || modelPath.isBlank()) {
            modelPath = DEFAULT_MODEL_DIR;
        }

        File modelDir = new File(modelPath);
        if (!modelDir.exists() || !modelDir.isDirectory()) {
            // Check relative to current working directory
            File altDir = new File("models/vosk-model-en-us-0.22-lgraph");
            if (altDir.exists()) {
                modelDir = altDir;
            } else {
                notifyError("Vosk model not found. Check models/vosk-model-en-us-0.22-lgraph.");
                return false;
            }
        }

        try {
            this.model = new Model(modelDir.getAbsolutePath());
            return true;
        } catch (Exception e) {
            notifyError("Failed to initialize Vosk model: " + e.getMessage());
            return false;
        }
    }

    /**
     * Starts listening to the microphone in a background thread.
     */
    public synchronized boolean startListening() {
        if (isListening.get()) {
            return true;
        }

        if (model == null && !initModel()) {
            return false;
        }

        try {
            recognizer = new Recognizer(model, SAMPLE_RATE);
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

            if (!AudioSystem.isLineSupported(info)) {
                notifyError("Microphone unavailable.");
                return false;
            }

            microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphone.open(format);
            microphone.start();

            isListening.set(true);
            notifyStatus("Listening - speak now");

            captureThread = new Thread(this::captureLoop, "VoxTune-VoiceCapture");
            captureThread.setDaemon(true);
            captureThread.start();

            return true;
        } catch (LineUnavailableException e) {
            notifyError("Microphone unavailable.");
            return false;
        } catch (Exception e) {
            notifyError("Voice recognition error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Background audio buffer reading and streaming loop.
     */
    private void captureLoop() {
        byte[] buffer = new byte[4096];

        try {
            while (isListening.get() && microphone != null && microphone.isOpen()) {
                int bytesRead = microphone.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                        String json = recognizer.getResult();
                        String text = extractText(json, "text");
                        if (text != null && !text.isBlank()) {
                            if (onFinalResultCallback != null) {
                                onFinalResultCallback.accept(text.trim());
                            }
                        }
                    } else {
                        String json = recognizer.getPartialResult();
                        String partial = extractText(json, "partial");
                        if (partial != null && !partial.isBlank()) {
                            if (onPartialResultCallback != null) {
                                onPartialResultCallback.accept(partial.trim());
                            }
                        }
                    }
                }
            }

            // Flush final recognized speech on stop
            if (recognizer != null) {
                String json = recognizer.getFinalResult();
                String text = extractText(json, "text");
                if (text != null && !text.isBlank()) {
                    if (onFinalResultCallback != null) {
                        onFinalResultCallback.accept(text.trim());
                    }
                }
            }

        } catch (Exception e) {
            if (isListening.get()) {
                notifyError("Audio streaming error: " + e.getMessage());
            }
        }
    }

    /**
     * Stops listening and frees audio hardware.
     */
    public synchronized void stopListening() {
        isListening.set(false);
        notifyStatus("Voice assistant ready");

        try {
            if (microphone != null) {
                microphone.stop();
                microphone.close();
                microphone = null;
            }
            if (captureThread != null) {
                captureThread.interrupt();
                captureThread = null;
            }
        } catch (Exception ignored) {
        }
    }

    public boolean isListening() {
        return isListening.get();
    }

    private String extractText(String json, String field) {
        try {
            JsonNode node = mapper.readTree(json);
            JsonNode fieldNode = node.get(field);
            if (fieldNode != null) {
                return fieldNode.asText();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void notifyStatus(String msg) {
        if (onStatusCallback != null) {
            onStatusCallback.accept(msg);
        }
    }

    private void notifyError(String err) {
        if (onErrorCallback != null) {
            onErrorCallback.accept(err);
        }
    }

    public void shutdown() {
        stopListening();
        if (recognizer != null) {
            recognizer.close();
        }
        if (model != null) {
            model.close();
        }
    }
}
