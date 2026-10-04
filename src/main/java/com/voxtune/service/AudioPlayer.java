package com.voxtune.service;

import com.voxtune.model.PlaybackState;
import com.voxtune.model.Song;
import javafx.application.Platform;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Service managing real audio playback using JavaFX MediaPlayer.
 * Demonstrates:
 * - Service Layer Pattern
 * - Observer / Listener Pattern
 * - Queue management using Java Collections
 * - Multithreading coordination with Platform.runLater()
 * - Clean resource disposal and error handling
 */
public class AudioPlayer {
    private MediaPlayer mediaPlayer;
    private final List<Song> queue = new ArrayList<>();
    private int currentIndex = -1;
    private PlaybackState currentState = PlaybackState.IDLE;
    private double volume = 0.75; // default 75%

    // Listener callbacks
    private final List<Consumer<PlaybackState>> stateListeners = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<Double, Double>> timeListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<Song>> songChangeListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<String>> errorListeners = new CopyOnWriteArrayList<>();

    public AudioPlayer() {
    }

    public synchronized void addStateListener(Consumer<PlaybackState> listener) {
        stateListeners.add(listener);
        listener.accept(currentState);
    }

    public synchronized void addTimeListener(BiConsumer<Double, Double> listener) {
        timeListeners.add(listener);
    }

    public synchronized void addSongChangeListener(Consumer<Song> listener) {
        songChangeListeners.add(listener);
    }

    /**
     * Registers a callback invoked when a track cannot be located or fails to play.
     */
    public synchronized void addErrorListener(Consumer<String> listener) {
        errorListeners.add(listener);
    }

    /**
     * Plays a single song, replacing the current queue with just this song.
     */
    public void play(Song song) {
        if (song == null) return;
        List<Song> singleList = new ArrayList<>();
        singleList.add(song);
        playQueue(singleList, 0);
    }

    /**
     * Sets a new playback queue and starts playing from the specified index.
     */
    public synchronized void playQueue(List<Song> songs, int startIndex) {
        if (songs == null || songs.isEmpty()) {
            stop();
            return;
        }

        queue.clear();
        queue.addAll(songs);
        currentIndex = Math.max(0, Math.min(startIndex, queue.size() - 1));

        loadAndPlayCurrent();
    }

    private void loadAndPlayCurrent() {
        if (currentIndex < 0 || currentIndex >= queue.size()) {
            stop();
            return;
        }

        Song song = queue.get(currentIndex);
        File file = MediaFileResolver.resolve(song.getFilePath());
        if (file == null) {
            System.err.println("Audio file not found or unreadable: " + song.getFilePath());
            notifyError("Couldn't locate the audio file for \"" + song.getTitle() + "\". Try Scan Music Folder.");
            next();
            return;
        }

        Platform.runLater(() -> {
            try {
                if (mediaPlayer != null) {
                    mediaPlayer.stop();
                    mediaPlayer.dispose();
                    mediaPlayer = null;
                }

                setState(PlaybackState.LOADING);
                notifySongChanged(song);

                Media media = new Media(file.toURI().toString());
                mediaPlayer = new MediaPlayer(media);
                mediaPlayer.setVolume(volume);

                mediaPlayer.setOnReady(() -> {
                    double dur = media.getDuration().toSeconds();
                    if (dur > 0 && song.getDuration() <= 0) {
                        song.setDuration(dur);
                    }
                    mediaPlayer.play();
                    setState(PlaybackState.PLAYING);
                });

                mediaPlayer.setOnPlaying(() -> setState(PlaybackState.PLAYING));
                mediaPlayer.setOnPaused(() -> setState(PlaybackState.PAUSED));
                mediaPlayer.setOnStopped(() -> setState(PlaybackState.STOPPED));

                // Auto-advance to next song on end
                mediaPlayer.setOnEndOfMedia(this::next);

                // Track time progress
                mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                    if (newTime != null && mediaPlayer != null && mediaPlayer.getTotalDuration() != null) {
                        double currentSec = newTime.toSeconds();
                        double totalSec = mediaPlayer.getTotalDuration().toSeconds();
                        notifyTime(currentSec, totalSec);
                    }
                });

                mediaPlayer.setOnError(() -> {
                    String detail = mediaPlayer != null && mediaPlayer.getError() != null
                            ? mediaPlayer.getError().getMessage() : "unknown";
                    System.err.println("MediaPlayer error: " + detail);
                    notifyError("Couldn't play \"" + song.getTitle() + "\".");
                    next();
                });

            } catch (Exception e) {
                System.err.println("Exception initializing MediaPlayer: " + e.getMessage());
                notifyError("Couldn't play \"" + song.getTitle() + "\".");
                next();
            }
        });
    }

    public void pause() {
        Platform.runLater(() -> {
            if (mediaPlayer != null && currentState == PlaybackState.PLAYING) {
                mediaPlayer.pause();
                setState(PlaybackState.PAUSED);
            }
        });
    }

    public void resume() {
        Platform.runLater(() -> {
            if (mediaPlayer != null && currentState == PlaybackState.PAUSED) {
                mediaPlayer.play();
                setState(PlaybackState.PLAYING);
            } else if (currentState == PlaybackState.STOPPED || currentState == PlaybackState.IDLE) {
                if (!queue.isEmpty()) {
                    loadAndPlayCurrent();
                }
            }
        });
    }

    public void togglePlayPause() {
        if (currentState == PlaybackState.PLAYING) {
            pause();
        } else {
            resume();
        }
    }

    public void stop() {
        Platform.runLater(() -> {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.dispose();
                mediaPlayer = null;
            }
            setState(PlaybackState.STOPPED);
            notifyTime(0.0, 0.0);
        });
    }

    public synchronized void next() {
        if (queue.isEmpty()) {
            stop();
            return;
        }

        if (currentIndex + 1 < queue.size()) {
            currentIndex++;
            loadAndPlayCurrent();
        } else {
            // End of queue
            stop();
            setState(PlaybackState.IDLE);
        }
    }

    public synchronized void previous() {
        if (queue.isEmpty()) {
            stop();
            return;
        }

        if (mediaPlayer != null && mediaPlayer.getCurrentTime().toSeconds() > 3.0) {
            // If played more than 3 seconds, seek back to beginning of current track
            seek(0.0);
        } else if (currentIndex > 0) {
            currentIndex--;
            loadAndPlayCurrent();
        } else {
            seek(0.0);
        }
    }

    public void seek(double seconds) {
        Platform.runLater(() -> {
            if (mediaPlayer != null) {
                mediaPlayer.seek(Duration.seconds(seconds));
            }
        });
    }

    public void setVolume(double vol) {
        this.volume = Math.max(0.0, Math.min(1.0, vol));
        Platform.runLater(() -> {
            if (mediaPlayer != null) {
                mediaPlayer.setVolume(this.volume);
            }
        });
    }

    public double getVolume() {
        return volume;
    }

    public synchronized Song getCurrentSong() {
        if (currentIndex >= 0 && currentIndex < queue.size()) {
            return queue.get(currentIndex);
        }
        return null;
    }

    public List<Song> getQueue() {
        return Collections.unmodifiableList(queue);
    }

    public PlaybackState getPlaybackState() {
        return currentState;
    }

    private void setState(PlaybackState state) {
        this.currentState = state;
        for (Consumer<PlaybackState> listener : stateListeners) {
            listener.accept(state);
        }
    }

    private void notifyTime(double currentSec, double totalSec) {
        for (BiConsumer<Double, Double> listener : timeListeners) {
            listener.accept(currentSec, totalSec);
        }
    }

    private void notifySongChanged(Song song) {
        for (Consumer<Song> listener : songChangeListeners) {
            listener.accept(song);
        }
    }

    private void notifyError(String message) {
        for (Consumer<String> listener : errorListeners) {
            listener.accept(message);
        }
    }
}
