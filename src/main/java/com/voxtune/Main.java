package com.voxtune;

import com.voxtune.ai.AiCommandService;
import com.voxtune.ai.AiResponseService;
import com.voxtune.db.SongRepository;
import com.voxtune.service.AudioPlayer;
import com.voxtune.service.MusicLibrary;
import com.voxtune.service.SpeechService;
import com.voxtune.ui.MainView;
import com.voxtune.voice.VoiceListener;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

/**
 * Main entry point for the VoxTune JavaFX Desktop Application.
 * Demonstrates:
 * - JavaFX Application lifecycle (init, start, stop)
 * - Dependency injection and layered architecture initialization
 * - Clean multi-threaded resource shutdown hooks
 * - CSS stylesheet loading and stage configuration
 */
public class Main extends Application {
    private SongRepository repository;
    private MusicLibrary library;
    private AudioPlayer player;
    private VoiceListener voiceListener;
    private AiCommandService aiCommandService;
    private AiResponseService aiResponseService;
    private SpeechService speechService;

    @Override
    public void init() throws Exception {
        super.init();
        // 1. Initialize data layer
        this.repository = new SongRepository();

        // 2. Initialize service layer
        this.library = new MusicLibrary(repository);
        this.player = new AudioPlayer();
        this.speechService = new SpeechService();

        // 3. Initialize voice & AI layer
        this.voiceListener = new VoiceListener();
        this.aiCommandService = new AiCommandService();
        this.aiResponseService = new AiResponseService();

        // Reconcile the database with the music folder on every startup: repair stale
        // paths from previous install locations, prune missing files, and pick up new tracks.
        library.syncLibrary();
    }

    @Override
    public void start(Stage primaryStage) {
        MainView mainView = new MainView(
            repository, library, player, voiceListener,
            aiCommandService, aiResponseService, speechService
        );

        Scene scene = new Scene(mainView.getRoot(), 1120, 740);

        // Load the Midnight Glass stylesheet
        URL cssResource = getClass().getResource("/styles.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        }

        primaryStage.setTitle("VoxTune - Desktop Music Player");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(960);
        primaryStage.setMinHeight(640);
        primaryStage.show();

        // Welcome greeting spoken aloud on startup in background
        speechService.speakAsync("Welcome to VoxTune. Voice assistant is ready.");
    }

    @Override
    public void stop() throws Exception {
        // Gracefully shutdown all background threads and audio hardware
        if (player != null) {
            player.stop();
        }
        if (voiceListener != null) {
            voiceListener.shutdown();
        }
        if (speechService != null) {
            speechService.shutdown();
        }
        if (library != null) {
            library.shutdown();
        }
        super.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
