package com.voxtune;

import com.voxtune.ai.AiCommandService;
import com.voxtune.ai.AiResponseService;
import com.voxtune.db.Database;
import com.voxtune.db.SongRepository;
import com.voxtune.model.Song;
import com.voxtune.service.AudioPlayer;
import com.voxtune.service.MusicLibrary;
import com.voxtune.service.SpeechService;
import com.voxtune.ui.MainView;
import com.voxtune.voice.VoiceListener;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the favorite feature end-to-end: a spoken/typed command adds a named
 * track to the Favorites list.
 */
public class FavoriteFeatureTest {
    private static final String TEST_DB = "voxtune_favorite_feature.db";

    private SongRepository repository;
    private MusicLibrary library;
    private AudioPlayer player;
    private SpeechService speech;
    private VoiceListener voice;
    private AiCommandService aiCommand;
    private AiResponseService aiResponse;

    @BeforeEach
    public void setUp() {
        new File(TEST_DB).delete();
        Database.setDatabasePath(TEST_DB);
        repository = new SongRepository();
        library = new MusicLibrary(repository);
        player = new AudioPlayer();
        speech = new SpeechService();
        voice = new VoiceListener();
        aiCommand = new AiCommandService();
        aiResponse = new AiResponseService();

        repository.save(new Song("Hello", "Adele", "Singles", "Pop", 2015, "music/Adele - Hello - 2015.wav", 180.0));
        repository.save(new Song("Bad Guy", "Billie Eilish", "Singles", "Pop", 2019, "music/Billie Eilish - Bad Guy - 2019.wav", 180.0));
    }

    @AfterEach
    public void tearDown() {
        player.stop();
        voice.shutdown();
        speech.shutdown();
        library.shutdown();
        new File(TEST_DB).delete();
    }

    @Test
    public void commandAddsNamedSongToFavorites() throws Exception {
        MainView view = buildView();

        boolean noFavoritesAtStart = repository.findFavorites().isEmpty();
        assertTrue(noFavoritesAtStart, "Should start with no favorites");

        view.handleExecuteCommand("Add Hello to favorites");

        boolean added = waitFor(() -> repository.findFavorites().stream()
                .anyMatch(s -> s.getTitle().equals("Hello")), 8000);
        assertTrue(added, "Typing 'Add Hello to favorites' should favorite the Hello track");
        assertEquals(1, repository.findFavorites().size());
    }

    @Test
    public void commandRemovesNamedSongFromFavorites() throws Exception {
        MainView view = buildView();

        view.handleExecuteCommand("Add Bad Guy to favorites");
        assertTrue(waitFor(() -> repository.findFavorites().stream()
                .anyMatch(s -> s.getTitle().equals("Bad Guy")), 8000), "Bad Guy should be favorited");

        view.handleExecuteCommand("Remove Bad Guy from favorites");
        assertTrue(waitFor(() -> repository.findFavorites().stream()
                .noneMatch(s -> s.getTitle().equals("Bad Guy")), 8000), "Bad Guy should be un-favorited");
    }

    private MainView buildView() throws Exception {
        startToolkit();
        AtomicReference<MainView> ref = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch built = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                ref.set(new MainView(repository, library, player, voice, aiCommand, aiResponse, speech));
            } catch (Throwable t) {
                error.set(t);
            } finally {
                built.countDown();
            }
        });
        assertTrue(built.await(30, TimeUnit.SECONDS), "MainView construction timed out");
        if (error.get() != null) {
            throw new AssertionError("MainView construction failed", error.get());
        }
        return ref.get();
    }

    private boolean waitFor(java.util.function.BooleanSupplier condition, long millis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(75);
        }
        return condition.getAsBoolean();
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
