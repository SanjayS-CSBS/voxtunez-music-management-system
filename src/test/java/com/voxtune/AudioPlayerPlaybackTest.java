package com.voxtune;

import com.voxtune.model.PlaybackState;
import com.voxtune.model.Song;
import com.voxtune.service.AudioPlayer;
import javafx.application.Platform;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test proving a real library track actually plays through AudioPlayer,
 * including when the stored path is stale (from another machine).
 */
public class AudioPlayerPlaybackTest {

    @Test
    void playsRealTrackFromPortablePath() throws Exception {
        runPlaybackTest("music/Adele - Hello - 2015.wav");
    }

    @Test
    void playsRealTrackEvenWhenStoredPathIsStale() throws Exception {
        runPlaybackTest("C:\\Users\\someone\\old\\VoxTunez\\music\\Adele - Hello - 2015.wav");
    }

    private void runPlaybackTest(String storedPath) throws Exception {
        startToolkit();

        AudioPlayer player = new AudioPlayer();
        AtomicReference<String> error = new AtomicReference<>();
        AtomicReference<PlaybackState> playingState = new AtomicReference<>();
        CountDownLatch playing = new CountDownLatch(1);

        player.addErrorListener(msg -> error.set(msg));
        player.addStateListener(state -> {
            if (state == PlaybackState.PLAYING) {
                playingState.set(state);
                playing.countDown();
            }
        });

        Song song = new Song("Hello", "Adele", "Singles", "Pop", 2015, storedPath, 180.0);
        Platform.runLater(() -> player.play(song));

        boolean reachedPlaying = playing.await(25, TimeUnit.SECONDS);

        player.stop();

        assertNull(error.get(), "Playback should not report an error for a real track");
        assertTrue(reachedPlaying, "AudioPlayer should reach PLAYING state for " + storedPath);
        assertEquals(PlaybackState.PLAYING, playingState.get());
    }

    private static void startToolkit() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException already) {
            latch.countDown();
        }
        latch.await(15, TimeUnit.SECONDS);
        Platform.setImplicitExit(false);
    }
}
