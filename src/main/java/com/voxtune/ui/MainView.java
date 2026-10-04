package com.voxtune.ui;

import com.voxtune.ai.AiCommandService;
import com.voxtune.ai.AiResponseService;
import com.voxtune.db.SongRepository;
import com.voxtune.model.CommandIntent;
import com.voxtune.model.MusicQuery;
import com.voxtune.model.PlaybackState;
import com.voxtune.model.Song;
import com.voxtune.service.AudioPlayer;
import com.voxtune.service.MediaFileResolver;
import com.voxtune.service.MusicLibrary;
import com.voxtune.service.SpeechService;
import com.voxtune.voice.VoiceListener;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.EnumMap;
import java.util.List;

/**
 * Main desktop user interface for VoxTune.
 * Designed with a "Midnight Glass" aesthetic: frosted translucent panels
 * floating over an ambient gradient backdrop with champagne-gold accents.
 * All operations run on background threads without freezing the JavaFX thread.
 */
public class MainView {
    private final BorderPane root;

    // Services
    private final SongRepository repository;
    private final MusicLibrary library;
    private final AudioPlayer player;
    private final VoiceListener voiceListener;
    private final AiCommandService aiCommandService;
    private final AiResponseService aiResponseService;
    private final SpeechService speechService;

    // UI State & Components
    private final ObservableList<Song> songList = FXCollections.observableArrayList();
    private TableView<Song> songTable;
    private TextField commandField;
    private TextField searchField;
    private Button voiceButton;
    private Label voiceStatusLabel;
    private Label aiResponseLabel;
    private Label nowPlayingTitle;
    private Label nowPlayingArtist;
    private Button playPauseButton;
    private Slider progressSlider;
    private Label timeLabel;
    private Slider volumeSlider;
    private Label headingLabel;
    private Button playerFavoriteButton;
    private boolean isUserDraggingSlider = false;

    // Active navigation mode
    private enum NavMode { HOME, LIBRARY, FAVORITES, ASK }
    private NavMode currentMode = NavMode.HOME;
    private final EnumMap<NavMode, Button> navButtons = new EnumMap<>(NavMode.class);
    private boolean suppressSearchFilter = false;

    public MainView(SongRepository repository, MusicLibrary library, AudioPlayer player,
                    VoiceListener voiceListener, AiCommandService aiCommandService,
                    AiResponseService aiResponseService, SpeechService speechService) {
        this.repository = repository;
        this.library = library;
        this.player = player;
        this.voiceListener = voiceListener;
        this.aiCommandService = aiCommandService;
        this.aiResponseService = aiResponseService;
        this.speechService = speechService;

        this.root = new BorderPane();
        buildUI();
        initListeners();
        aiCommandService.refreshLibraryVocabulary(repository);
        selectNav(NavMode.HOME);
    }

    public BorderPane getRoot() {
        return root;
    }

    private void buildUI() {
        root.setLeft(buildSidebar());
        root.setCenter(buildCenterContent());
        root.setBottom(buildBottomPlayerBar());
    }

    /**
     * Left Sidebar: Brand and clean navigation items.
     */
    private VBox buildSidebar() {
        VBox sidebar = new VBox(6);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(224);

        Label emblem = new Label("\u266A");
        emblem.getStyleClass().add("brand-emblem");

        Label brand = new Label("VoxTune");
        brand.getStyleClass().add("brand-title");

        Label subtitle = new Label("Desktop Music Player");
        subtitle.getStyleClass().add("brand-subtitle");

        VBox brandText = new VBox(1, brand, subtitle);
        brandText.setAlignment(Pos.CENTER_LEFT);

        HBox brandRow = new HBox(12, emblem, brandText);
        brandRow.setAlignment(Pos.CENTER_LEFT);
        brandRow.getStyleClass().add("brand-row");

        Label navLabel = new Label("NAVIGATION");
        navLabel.getStyleClass().add("nav-section-label");

        Button homeBtn = createNavButton("\u2302", "Home", NavMode.HOME);
        Button libraryBtn = createNavButton("\u266B", "Library", NavMode.LIBRARY);
        Button favoritesBtn = createNavButton("\u2605", "Favorites", NavMode.FAVORITES);
        Button voiceCmdBtn = createNavButton("\u25C9", "Voice Command", NavMode.ASK);

        homeBtn.getStyleClass().add("active");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Label offlineBadge = new Label("\u25CF  Offline Mode");
        offlineBadge.getStyleClass().add("status-pill");

        sidebar.getChildren().addAll(brandRow, navLabel, homeBtn, libraryBtn, favoritesBtn, voiceCmdBtn, spacer, offlineBadge);
        return sidebar;
    }

    private Button createNavButton(String glyph, String text, NavMode mode) {
        Button btn = new Button(glyph + "    " + text);
        btn.getStyleClass().add("nav-button");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setOnAction(e -> selectNav(mode));
        navButtons.put(mode, btn);
        return btn;
    }

    /**
     * Switches the active sidebar tab: moves the highlight, updates the heading,
     * resets any active search, and loads the content for the selected view.
     */
    private void selectNav(NavMode mode) {
        this.currentMode = mode;

        for (Button btn : navButtons.values()) {
            btn.getStyleClass().remove("active");
        }
        Button activeBtn = navButtons.get(mode);
        if (activeBtn != null && !activeBtn.getStyleClass().contains("active")) {
            activeBtn.getStyleClass().add("active");
        }

        // Clear the search box so the tab's own listing is shown (suppress the reload it would trigger)
        if (searchField != null && !searchField.getText().isEmpty()) {
            suppressSearchFilter = true;
            searchField.clear();
            suppressSearchFilter = false;
        }

        switch (mode) {
            case FAVORITES -> {
                if (headingLabel != null) headingLabel.setText("Favorites");
                loadFavorites();
            }
            case ASK -> {
                if (headingLabel != null) headingLabel.setText("Voice Command");
                if (commandField != null) {
                    commandField.requestFocus();
                }
            }
            case LIBRARY -> {
                if (headingLabel != null) headingLabel.setText("Your Library");
                loadSongs();
            }
            default -> {
                if (headingLabel != null) headingLabel.setText("Music Dashboard");
                loadRecentlyAdded();
            }
        }
    }

    /**
     * Center Content: Top Bar, Command/Voice Control Station, and Songs Table.
     */
    private VBox buildCenterContent() {
        VBox center = new VBox(14);
        center.getStyleClass().add("main-content");

        // 1. Top Bar with Search & Scan
        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("top-bar");

        headingLabel = new Label("Music Dashboard");
        headingLabel.getStyleClass().add("dashboard-heading");

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);

        searchField = new TextField();
        searchField.setPromptText("Search artist, title, album...");
        searchField.setPrefWidth(240);
        searchField.getStyleClass().add("command-input");
        searchField.textProperty().addListener((obs, oldV, newV) -> {
            if (!suppressSearchFilter) {
                filterSongsBySearch(newV);
            }
        });

        Button addBtn = new Button("+  Add Songs");
        addBtn.getStyleClass().add("add-button");
        addBtn.setOnAction(e -> handleAddSongs());

        Button scanBtn = new Button("Scan Music Folder");
        scanBtn.getStyleClass().add("ghost-button");
        scanBtn.setOnAction(e -> handleScanMusic());

        topBar.getChildren().addAll(headingLabel, topSpacer, searchField, addBtn, scanBtn);

        // 2. Command & Voice Station
        VBox commanderCard = new VBox(12);
        commanderCard.getStyleClass().add("commander-card");

        Label cardTitle = new Label("Command & Voice Control");
        cardTitle.getStyleClass().add("card-heading");

        Label cardSub = new Label("Type a command or activate the microphone to discover and play songs.");
        cardSub.getStyleClass().add("card-subtext");

        HBox commandRow = new HBox(10);
        commandRow.setAlignment(Pos.CENTER_LEFT);

        commandField = new TextField();
        commandField.setPromptText("e.g. Play Taylor Swift songs from the 2000s, Play rock songs, Pause, Next");
        commandField.getStyleClass().add("command-input");
        HBox.setHgrow(commandField, Priority.ALWAYS);
        commandField.setOnAction(e -> handleExecuteCommand(commandField.getText()));

        Button executeBtn = new Button("Execute");
        executeBtn.getStyleClass().add("execute-button");
        executeBtn.setOnAction(e -> handleExecuteCommand(commandField.getText()));

        voiceButton = new Button("Voice");
        voiceButton.getStyleClass().add("voice-button");
        voiceButton.setOnAction(e -> toggleVoiceListening());

        commandRow.getChildren().addAll(commandField, executeBtn, voiceButton);

        voiceStatusLabel = new Label("Microphone ready");
        voiceStatusLabel.getStyleClass().add("status-label");

        // Assistant Feedback Box
        VBox assistantBox = new VBox(4);
        assistantBox.getStyleClass().add("assistant-card");

        Label assistantHeader = new Label("VOICE ASSISTANT");
        assistantHeader.getStyleClass().add("assistant-header");

        aiResponseLabel = new Label("Ready. Say a command or select a song to begin.");
        aiResponseLabel.getStyleClass().add("assistant-text");
        aiResponseLabel.setWrapText(true);

        assistantBox.getChildren().addAll(assistantHeader, aiResponseLabel);

        commanderCard.getChildren().addAll(cardTitle, cardSub, commandRow, voiceStatusLabel, assistantBox);

        // 3. Songs Table Section
        VBox tableCard = new VBox(10);
        tableCard.getStyleClass().add("table-card");
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        Label musicTableTitle = new Label("Your Music Library");
        musicTableTitle.getStyleClass().add("section-title");

        songTable = createSongTable();
        VBox.setVgrow(songTable, Priority.ALWAYS);

        tableCard.getChildren().addAll(musicTableTitle, songTable);

        center.getChildren().addAll(topBar, commanderCard, tableCard);
        return center;
    }

    private TableView<Song> createSongTable() {
        TableView<Song> table = new TableView<>(songList);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No songs yet. Click \"Add Songs\" to import audio files, or drop them here."));

        // Column 1: Play action
        TableColumn<Song, String> playCol = new TableColumn<>("Action");
        playCol.setPrefWidth(65);
        playCol.setMaxWidth(65);
        playCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Play");
            {
                btn.getStyleClass().add("cell-play-btn");
                btn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size()) {
                        playSong(getTableView().getItems().get(idx));
                    }
                });
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        // Column 2: Title
        TableColumn<Song, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTitle()));
        titleCol.setPrefWidth(200);

        // Column 3: Artist
        TableColumn<Song, String> artistCol = new TableColumn<>("Artist");
        artistCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getArtist()));
        artistCol.setPrefWidth(160);

        // Column 4: Album
        TableColumn<Song, String> albumCol = new TableColumn<>("Album");
        albumCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getAlbum()));
        albumCol.setPrefWidth(120);

        // Column 5: Year
        TableColumn<Song, String> yearCol = new TableColumn<>("Year");
        yearCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getYear() > 0 ? String.valueOf(data.getValue().getYear()) : "-"));
        yearCol.setPrefWidth(60);

        // Column 6: Genre
        TableColumn<Song, String> genreCol = new TableColumn<>("Genre");
        genreCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getGenre()));
        genreCol.setPrefWidth(100);

        // Column 7: Duration
        TableColumn<Song, String> durCol = new TableColumn<>("Duration");
        durCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFormattedDuration()));
        durCol.setPrefWidth(70);

        // Column 8: Favorite toggle (star)
        TableColumn<Song, String> favCol = new TableColumn<>("Favorite");
        favCol.setPrefWidth(80);
        favCol.setMaxWidth(80);
        favCol.setSortable(false);
        favCol.setCellFactory(col -> new TableCell<>() {
            private final Button favBtn = new Button("\u2606");
            {
                favBtn.getStyleClass().add("cell-fav-btn");
                favBtn.setTooltip(new Tooltip("Add or remove this song from Favorites"));
                favBtn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size()) {
                        Song song = getTableView().getItems().get(idx);
                        toggleFavorite(song);
                        applyFavoriteStyle(favBtn, song);
                    }
                });
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    int idx = getIndex();
                    Song song = (idx >= 0 && idx < getTableView().getItems().size())
                            ? getTableView().getItems().get(idx) : null;
                    applyFavoriteStyle(favBtn, song);
                    setGraphic(favBtn);
                }
            }
        });

        table.getColumns().addAll(playCol, titleCol, artistCol, albumCol, yearCol, genreCol, durCol, favCol);

        // Double click to play, right-click for quick favorite/play actions
        table.setRowFactory(tv -> {
            TableRow<Song> row = new TableRow<>();

            ContextMenu menu = new ContextMenu();
            MenuItem playItem = new MenuItem("Play");
            MenuItem favItem = new MenuItem("Add to Favorites");
            playItem.setOnAction(e -> playSong(row.getItem()));
            favItem.setOnAction(e -> toggleFavorite(row.getItem()));
            menu.getItems().addAll(playItem, favItem);
            menu.setOnShowing(e -> {
                Song s = row.getItem();
                favItem.setText(s != null && s.isFavorite() ? "Remove from Favorites" : "Add to Favorites");
            });
            row.contextMenuProperty().bind(javafx.beans.binding.Bindings
                    .when(row.emptyProperty()).then((ContextMenu) null).otherwise(menu));

            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    playSong(row.getItem());
                }
            });
            return row;
        });

        // Drag & drop audio files anywhere onto the table to add them
        table.setOnDragOver(event -> {
            if (event.getGestureSource() != table && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        table.setOnDragDropped(event -> {
            Dragboard dragboard = event.getDragboard();
            boolean completed = false;
            if (dragboard.hasFiles()) {
                List<File> audioFiles = dragboard.getFiles().stream()
                        .filter(f -> MediaFileResolver.isAudioFileName(f.getName()))
                        .toList();
                if (!audioFiles.isEmpty()) {
                    importSongs(audioFiles);
                    completed = true;
                } else {
                    aiResponseLabel.setText("Those files aren't supported. Use MP3, WAV, M4A, or AIFF.");
                }
            }
            event.setDropCompleted(completed);
            event.consume();
        });

        return table;
    }

    /**
     * Bottom Player Bar: Track info, real media controls, progress bar, and volume.
     */
    private HBox buildBottomPlayerBar() {
        HBox bar = new HBox(16);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("player-bar");

        // Album art placeholder (decorative)
        Region albumArt = new Region();
        albumArt.getStyleClass().add("album-art");
        albumArt.setMinSize(48, 48);
        albumArt.setPrefSize(48, 48);
        albumArt.setMaxSize(48, 48);

        // Track info
        VBox trackInfo = new VBox(2);
        trackInfo.setPrefWidth(200);
        nowPlayingTitle = new Label("No track playing");
        nowPlayingTitle.getStyleClass().add("now-playing-title");
        nowPlayingArtist = new Label("Select a track or use voice");
        nowPlayingArtist.getStyleClass().add("now-playing-artist");
        trackInfo.getChildren().addAll(nowPlayingTitle, nowPlayingArtist);

        // Controls and progress
        VBox centerControls = new VBox(6);
        centerControls.setAlignment(Pos.CENTER);
        HBox.setHgrow(centerControls, Priority.ALWAYS);

        HBox btnRow = new HBox(10);
        btnRow.setAlignment(Pos.CENTER);

        Button prevBtn = new Button("Prev");
        prevBtn.getStyleClass().add("player-btn");
        prevBtn.setOnAction(e -> {
            player.previous();
            speechService.speakAsync("Previous song");
        });

        playPauseButton = new Button("Play");
        playPauseButton.getStyleClass().add("player-btn-main");
        playPauseButton.setOnAction(e -> player.togglePlayPause());

        Button nextBtn = new Button("Next");
        nextBtn.getStyleClass().add("player-btn");
        nextBtn.setOnAction(e -> {
            player.next();
            speechService.speakAsync("Next song");
        });

        Button stopBtn = new Button("Stop");
        stopBtn.getStyleClass().add("player-btn");
        stopBtn.setOnAction(e -> player.stop());

        playerFavoriteButton = new Button("\u2606");
        playerFavoriteButton.getStyleClass().add("fav-star");
        playerFavoriteButton.setTooltip(new Tooltip("Add or remove the current track from Favorites"));
        playerFavoriteButton.setDisable(true);
        playerFavoriteButton.setOnAction(e -> toggleFavorite(player.getCurrentSong()));

        btnRow.getChildren().addAll(prevBtn, playPauseButton, nextBtn, stopBtn, playerFavoriteButton);

        // Progress bar with time labels
        HBox progressRow = new HBox(10);
        progressRow.setAlignment(Pos.CENTER);

        timeLabel = new Label("0:00 / 0:00");
        timeLabel.getStyleClass().add("time-label");

        progressSlider = new Slider(0, 100, 0);
        HBox.setHgrow(progressSlider, Priority.ALWAYS);

        progressSlider.setOnMousePressed(e -> isUserDraggingSlider = true);
        progressSlider.setOnMouseReleased(e -> {
            isUserDraggingSlider = false;
            double targetSeconds = progressSlider.getValue();
            player.seek(targetSeconds);
        });

        progressRow.getChildren().addAll(timeLabel, progressSlider);
        centerControls.getChildren().addAll(btnRow, progressRow);

        // Volume control
        HBox volBox = new HBox(8);
        volBox.setAlignment(Pos.CENTER_RIGHT);
        volBox.setPrefWidth(180);

        Label volLabel = new Label("Volume");
        volLabel.getStyleClass().add("field-label");

        volumeSlider = new Slider(0, 100, 75);
        volumeSlider.setPrefWidth(100);
        volumeSlider.valueProperty().addListener((obs, oldV, newV) -> {
            player.setVolume(newV.doubleValue() / 100.0);
        });

        volBox.getChildren().addAll(volLabel, volumeSlider);

        bar.getChildren().addAll(albumArt, trackInfo, centerControls, volBox);
        return bar;
    }

    /**
     * Wire callbacks from services to UI elements.
     */
    private void initListeners() {
        // Player state listener
        player.addStateListener(state -> Platform.runLater(() -> {
            if (state == PlaybackState.PLAYING) {
                playPauseButton.setText("Pause");
            } else {
                playPauseButton.setText("Play");
            }
        }));

        // Player current song listener
        player.addSongChangeListener(song -> Platform.runLater(() -> {
            if (song != null) {
                nowPlayingTitle.setText(song.getTitle());
                nowPlayingArtist.setText(song.getArtist() + (song.getYear() > 0 ? " (" + song.getYear() + ")" : ""));
            } else {
                nowPlayingTitle.setText("No track playing");
                nowPlayingArtist.setText("Select a track or use voice");
            }
            updatePlayerFavoriteButton();
        }));

        // Player time progress listener
        player.addTimeListener((currentSec, totalSec) -> Platform.runLater(() -> {
            if (totalSec > 0 && !isUserDraggingSlider) {
                progressSlider.setMax(totalSec);
                progressSlider.setValue(currentSec);

                int cMin = (int) (currentSec / 60);
                int cSec = (int) (currentSec % 60);
                int tMin = (int) (totalSec / 60);
                int tSec = (int) (totalSec % 60);

                timeLabel.setText(String.format("%d:%02d / %d:%02d", cMin, cSec, tMin, tSec));
            }
        }));

        // Playback error surfacing so skipped/unplayable tracks are visible, not silent
        player.addErrorListener(msg -> Platform.runLater(() -> {
            aiResponseLabel.setText(msg);
            voiceStatusLabel.setText("Playback issue");
        }));

        // Voice listener callbacks
        voiceListener.setOnStatus(status -> Platform.runLater(() -> voiceStatusLabel.setText(status)));
        voiceListener.setOnError(err -> Platform.runLater(() -> {
            voiceStatusLabel.setText("Notice: " + err);
            voiceButton.setText("Voice");
            voiceButton.getStyleClass().remove("listening");
        }));

        voiceListener.setOnPartialResult(partial -> Platform.runLater(() -> {
            voiceStatusLabel.setText("Hearing: " + partial + "...");
        }));

        voiceListener.setOnFinalResult(finalText -> Platform.runLater(() -> {
            voiceStatusLabel.setText("Heard: " + finalText);
            commandField.setText(finalText);
            toggleVoiceListening(); // Turn off microphone after final speech recognized
            handleExecuteCommand(finalText);
        }));
    }

    /**
     * Toggles offline voice microphone listening.
     */
    private void toggleVoiceListening() {
        if (voiceListener.isListening()) {
            voiceListener.stopListening();
            voiceButton.setText("Voice");
            voiceButton.getStyleClass().remove("listening");
        } else {
            boolean started = voiceListener.startListening();
            if (started) {
                voiceButton.setText("Listening...");
                if (!voiceButton.getStyleClass().contains("listening")) {
                    voiceButton.getStyleClass().add("listening");
                }
            }
        }
    }

    /**
     * Executes user typed or spoken natural language command.
     */
    public void handleExecuteCommand(String rawInput) {
        if (rawInput == null || rawInput.trim().isEmpty()) {
            return;
        }

        String input = rawInput.trim();
        aiResponseLabel.setText("Processing: \"" + input + "\"...");

        aiCommandService.parseCommandAsync(input).thenAccept(query -> Platform.runLater(() -> {
            processParsedQuery(input, query);
        }));
    }

    private void processParsedQuery(String originalInput, MusicQuery query) {
        CommandIntent intent = query.getIntent();

        // 1. Playback controls
        if (intent.isPlaybackControl()) {
            handlePlaybackControlIntent(intent);
            return;
        }

        // 2. Conversational queries
        if (intent == CommandIntent.CONVERSATION) {
            aiResponseService.getConversationalResponseAsync(originalInput).thenAccept(response -> Platform.runLater(() -> {
                aiResponseLabel.setText(response);
                speechService.speakAsync(response);
            }));
            return;
        }

        // 3. Favorites
        if (intent == CommandIntent.FAVORITE || intent == CommandIntent.UNFAVORITE) {
            handleFavoriteIntent(intent, query);
            return;
        }

        // 4. Music Search or Play Query
        List<Song> results = repository.search(query);

        if (results.isEmpty()) {
            String fallbackMsg = "No matching songs found in your library for: " + originalInput;
            aiResponseLabel.setText(fallbackMsg);
            speechService.speakAsync(fallbackMsg);
            return;
        }

        // Generate natural conversational feedback
        String responseText = aiResponseService.getPlaybackResponse(query, results);
        aiResponseLabel.setText(responseText);
        speechService.speakAsync(responseText);

        if (intent == CommandIntent.PLAY) {
            // Update table list with matching tracks & start audio playback
            songList.setAll(results);
            player.playQueue(results, 0);
        } else {
            // SEARCH: show matching tracks in table
            songList.setAll(results);
        }
    }

    /**
     * Applies an "add/remove favorite" command to a named track, or to whatever is currently playing.
     */
    private void handleFavoriteIntent(CommandIntent intent, MusicQuery query) {
        boolean favorite = intent == CommandIntent.FAVORITE;

        Song target = null;
        if (query.hasText() || query.hasArtist() || query.hasGenre()) {
            List<Song> matches = repository.search(query);
            if (!matches.isEmpty()) {
                target = matches.get(0);
            }
        } else {
            target = player.getCurrentSong();
        }

        if (target == null) {
            String msg = favorite
                    ? "Nothing is playing to favorite. Play a song, or name one like \"Add Hello to favorites\"."
                    : "Nothing is playing to un-favorite. Play a song first.";
            aiResponseLabel.setText(msg);
            speechService.speakAsync(msg);
            return;
        }

        setFavorite(target, favorite);
        speechService.speakAsync(favorite
                ? "Added " + target.getTitle() + " to Favorites"
                : "Removed " + target.getTitle() + " from Favorites");
    }

    private void handlePlaybackControlIntent(CommandIntent intent) {
        switch (intent) {
            case PAUSE -> {
                player.pause();
                aiResponseLabel.setText("Paused playback.");
                speechService.speakAsync("Paused");
            }
            case RESUME -> {
                player.resume();
                aiResponseLabel.setText("Resumed playback.");
                speechService.speakAsync("Resuming");
            }
            case STOP -> {
                player.stop();
                aiResponseLabel.setText("Stopped music.");
                speechService.speakAsync("Stopped");
            }
            case NEXT -> {
                player.next();
                aiResponseLabel.setText("Skipped to next song.");
                speechService.speakAsync("Next song");
            }
            case PREVIOUS -> {
                player.previous();
                aiResponseLabel.setText("Going to previous song.");
                speechService.speakAsync("Previous song");
            }
            case VOLUME_UP -> {
                double newVol = Math.min(100.0, volumeSlider.getValue() + 15.0);
                volumeSlider.setValue(newVol);
                aiResponseLabel.setText("Turned volume up.");
                speechService.speakAsync("Volume up");
            }
            case VOLUME_DOWN -> {
                double newVol = Math.max(0.0, volumeSlider.getValue() - 15.0);
                volumeSlider.setValue(newVol);
                aiResponseLabel.setText("Turned volume down.");
                speechService.speakAsync("Volume down");
            }
            default -> {}
        }
    }

    /**
     * Scans local music folder asynchronously without freezing JavaFX thread.
     */
    private void handleScanMusic() {
        aiResponseLabel.setText("Scanning local music folder...");
        library.scanAsync().thenAccept(count -> Platform.runLater(() -> {
            loadSongs();
            String msg = count > 0 
                ? "Scan complete. Discovered " + count + " songs in music library."
                : "Scan complete. Library is up to date (" + repository.count() + " songs).";
            aiResponseLabel.setText(msg);
            speechService.speakAsync("Discovered " + repository.count() + " songs in library.");
        }));
    }

    /**
     * Opens a file picker so the user can add audio files to the library.
     */
    private void handleAddSongs() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Add Songs to VoxTune");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Audio Files", "*.mp3", "*.wav", "*.m4a", "*.aiff", "*.aif"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All Files", "*.*"));

        File musicFolder = new File(MediaFileResolver.MUSIC_DIR);
        if (musicFolder.isDirectory()) {
            chooser.setInitialDirectory(musicFolder);
        }

        javafx.stage.Window owner = root.getScene() != null ? root.getScene().getWindow() : null;
        List<File> selected = chooser.showOpenMultipleDialog(owner);
        if (selected != null && !selected.isEmpty()) {
            importSongs(selected);
        }
    }

    /**
     * Copies the chosen files into the library folder, records them, and refreshes the active view.
     */
    private void importSongs(List<File> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        aiResponseLabel.setText("Adding " + files.size() + " file(s) to your library...");
        library.importAsync(files).thenAccept(count -> Platform.runLater(() -> {
            aiCommandService.refreshLibraryVocabulary(repository);
            selectNav(currentMode);
            String msg = count > 0
                    ? "Added " + count + " new song(s) to your library."
                    : "No new songs were added. Supported formats: MP3, WAV, M4A, AIFF.";
            aiResponseLabel.setText(msg);
            speechService.speakAsync(msg);
        }));
    }

    private void loadSongs() {
        List<Song> all = repository.findAll();
        songList.setAll(all);
    }

    /**
     * Home dashboard: the most recently added tracks plus a quick library summary.
     */
    private void loadRecentlyAdded() {
        List<Song> recent = repository.findRecentlyAdded(15);
        songList.setAll(recent);
        int total = repository.count();
        int favorites = repository.findFavorites().size();
        aiResponseLabel.setText("Welcome back. " + total + " songs in your library, " + favorites
                + " favorites. Showing your latest additions.");
    }

    /**
     * Plays a specific song from the currently displayed list.
     */
    private void playSong(Song song) {
        if (song == null) {
            return;
        }
        int index = songList.indexOf(song);
        player.playQueue(songList, Math.max(0, index));
        String msg = "Playing " + song.getTitle() + " by " + song.getArtist();
        aiResponseLabel.setText(msg);
        speechService.speakAsync(msg);
    }

    /**
     * Adds or removes a song from Favorites. When the Favorites tab is open and a song is
     * un-favorited, it is removed from view immediately.
     */
    private void setFavorite(Song song, boolean favorite) {
        if (song == null) {
            return;
        }
        if (song.getId() > 0 && song.isFavorite() != favorite) {
            repository.toggleFavorite(song.getId());
            song.setFavorite(favorite);
        }
        if (aiResponseLabel != null) {
            aiResponseLabel.setText(favorite
                    ? "Added \"" + song.getTitle() + "\" to Favorites."
                    : "Removed \"" + song.getTitle() + "\" from Favorites.");
        }
        if (currentMode == NavMode.FAVORITES && !favorite) {
            songList.remove(song);
        }
        if (songTable != null) {
            songTable.refresh();
        }
        updatePlayerFavoriteButton();
    }

    private void toggleFavorite(Song song) {
        if (song != null) {
            setFavorite(song, !song.isFavorite());
        }
    }

    private void applyFavoriteStyle(Button button, Song song) {
        if (button == null) {
            return;
        }
        boolean favorite = song != null && song.isFavorite();
        button.setText(favorite ? "\u2605" : "\u2606");
        if (favorite) {
            if (!button.getStyleClass().contains("favorited")) {
                button.getStyleClass().add("favorited");
            }
        } else {
            button.getStyleClass().remove("favorited");
        }
    }

    private void updatePlayerFavoriteButton() {
        if (playerFavoriteButton == null) {
            return;
        }
        Song current = player.getCurrentSong();
        playerFavoriteButton.setDisable(current == null);
        applyFavoriteStyle(playerFavoriteButton, current);
    }

    private void loadFavorites() {
        List<Song> favs = repository.findFavorites();
        songList.setAll(favs);
        if (favs.isEmpty()) {
            aiResponseLabel.setText("No favorites yet. Play a song, then tap the star in the Favorite column.");
        } else {
            aiResponseLabel.setText("Displaying " + favs.size() + " favorite songs.");
        }
    }

    private void filterSongsBySearch(String query) {
        if (query == null || query.isBlank()) {
            loadSongs();
        } else {
            songList.setAll(repository.searchByText(query));
        }
    }
}
