# VoxTune - Desktop Music Player & Voice Commander

VoxTune is an offline-first Java 17 desktop music application built with JavaFX, SQLite (JDBC), Vosk speech recognition, and native Windows speech synthesis. It features natural language and voice music control, a studio desktop aesthetic, and includes 56 real, playable 3-minute tracks.

---

## Features

- **Offline Voice Control**: Microphone capture using Java Sound API and offline speech-to-text with the Vosk acoustic model (`vosk-model-en-us-0.22-lgraph`).
- **Natural Language Parsing**: Offline rule-based NLP parser extracting playback intents, artists, song titles, decades (e.g. `2000s` -> 2000-2009, `90s` -> 1990-1999), genres, moods, and year ranges.
- **Spoken Voice Feedback**: Spoken feedback using native Windows text-to-speech in a background thread without freezing the UI.
- **Optional Cloud AI Integration**: Pluggable OpenAI-compatible API endpoint via environment variables, automatically falling back to the local parser when offline.
- **Local Music Management**: Directory scanning with Java NIO (`Files.walk`), automatic metadata extraction (`Artist - Title - Year.wav`), and SQLite JDBC persistence with search and favorites support.
- **Real Audio Playback**: Built with JavaFX MediaPlayer, featuring dynamic queue management, auto-advance, track scrubbing, seeking, and volume control.
- **Clean Studio Desktop Aesthetic**: Clean, professional palette with warm parchment background, clear typography, and responsive controls without decorative emojis.

---

## Technology Stack

- **Language**: Java 17 (OpenJDK 17 Temurin)
- **Build System**: Apache Maven 3.9+
- **UI Framework**: JavaFX 17 (`javafx-controls`, `javafx-media`, `javafx-graphics`)
- **Database**: SQLite 3 via `sqlite-jdbc`
- **Speech Recognition**: Vosk `0.3.45` (`com.alphacephei:vosk`)
- **Speech Synthesis**: Windows System.Speech Synthesizer via background PowerShell process
- **JSON Processing**: Jackson `2.16.1` (`jackson-databind`)
- **Testing**: JUnit 5 Jupiter (`junit-jupiter:5.10.2`)

---

## Project Structure

```
VoxTunez/
├── pom.xml
├── README.md
├── generate_music.py                     # 3-minute track synthesizer
├── music/                                # 56 real 3-minute .wav tracks
│   ├── Taylor Swift - Love Story - 2008.wav
│   ├── Taylor Swift - You Belong With Me - 2008.wav
│   ├── Imagine Dragons - Radioactive - 2012.wav
│   └── ...
├── models/
│   └── vosk-model-en-us-0.22-lgraph/     # Offline speech recognition model
├── src/
│   ├── main/
│   │   ├── java/com/voxtune/
│   │   │   ├── Main.java                 # JavaFX entry point & lifecycle
│   │   │   ├── ai/
│   │   │   │   ├── AiCommandService.java  # Cloud AI parser + offline fallback
│   │   │   │   ├── AiResponseService.java # Natural response generator
│   │   │   │   └── CommandParser.java    # Offline regex & NLP parser
│   │   │   ├── db/
│   │   │   │   ├── Database.java         # SQLite connection & schema init
│   │   │   │   └── SongRepository.java   # JDBC PreparedStatements & search
│   │   │   ├── exception/
│   │   │   │   └── SongNotFoundException.java # Custom checked exception
│   │   │   ├── model/
│   │   │   │   ├── CommandIntent.java    # Enum of playback intents
│   │   │   │   ├── MusicQuery.java       # Structured query model
│   │   │   │   ├── PlaybackState.java    # Enum: PLAYING, PAUSED, IDLE, etc.
│   │   │   │   └── Song.java             # Song domain model
│   │   │   ├── service/
│   │   │   │   ├── AudioPlayer.java      # JavaFX MediaPlayer queue engine
│   │   │   │   ├── MusicLibrary.java     # NIO directory scanner & sync
│   │   │   │   └── SpeechService.java    # Windows SAPI background TTS
│   │   │   ├── ui/
│   │   │   │   └── MainView.java         # Studio UI dashboard
│   │   │   └── voice/
│   │   │       └── VoiceListener.java    # Java Sound + Vosk microphone loop
│   │   └── resources/
│   │       └── styles.css                # Professional stylesheet
│   └── test/
│       └── java/com/voxtune/
│           ├── CommandParserTest.java    # Parser unit tests
│           ├── IntegrationTest.java      # 56-track end-to-end integration test
│           ├── SongRepositoryTest.java   # Database CRUD & search tests
│           └── SpeechServiceTest.java    # Speech synthesizer tests
└── voxtune.db                            # SQLite persistent database
```

---

## How to Build and Run

### 1. Build and Compile
```bash
mvn clean compile
```

### 2. Run All Automated Tests
```bash
mvn test
```

### 3. Launch VoxTune
```bash
mvn clean javafx:run
```

---

## Voice and Natural Language Commands

Click the **Voice** button or type into the command field:

| Command | Action |
| :--- | :--- |
| `"Play Taylor Swift songs from the 2000s"` | Searches Taylor Swift tracks from 2000-2009, queues Love Story, You Belong With Me, Fifteen, starts audio playback, speaks confirmation |
| `"Play Love Story"` | Matches title Love Story directly and begins playing |
| `"Play songs by Imagine Dragons"` | Queues all Imagine Dragons tracks (Radioactive, Demons, Believer, etc.) |
| `"Play rock songs"` | Filters rock/grunge tracks (Queen, Nirvana, Oasis, Coldplay) |
| `"Play songs from the 90s"` | Filters tracks from 1990 to 1999 (Nirvana, Oasis, Radiohead) |
| `"Play songs from 2010 to 2015"` | Plays tracks released between 2010 and 2015 |
| `"Pause"` / `"Pause the music"` | Pauses current audio playback |
| `"Resume"` / `"Continue playing"` | Resumes playback |
| `"Next song"` / `"Previous song"` | Moves backward or forward in the queue |
| `"Stop"` | Stops playback and resets progress |
| `"Volume up"` / `"Volume down"` | Adjusts playback volume by 15% |
| `"Who are you?"` / `"What can you do?"` | Assistant answers and speaks aloud |

---

## Environment Variables (Optional)

VoxTune operates 100% offline. Cloud AI is optional:

| Variable | Description | Default |
| :--- | :--- | :--- |
| `VOXTUNE_AI_KEY` | OpenAI API Key for cloud command & conversational parsing | *(None, uses local NLP)* |
| `VOXTUNE_AI_URL` | OpenAI-compatible endpoint URL | `https://api.openai.com/v1/chat/completions` |
| `VOXTUNE_AI_MODEL` | AI model name | `gpt-4o-mini` |
| `VOSK_MODEL_PATH` | Custom path to Vosk offline speech model | `models/vosk-model-en-us-0.22-lgraph` |

---

## Core Java Concepts (Viva Reference)

1. **OOP and Encapsulation**: Domain models `Song` and `MusicQuery` with private fields and public accessors.
2. **Inheritance and Polymorphism**: `SongNotFoundException` extends standard `Exception`; `Main` extends `javafx.application.Application`.
3. **Constructors and Overloading**: `Song` has overloaded constructors for default initialization, new file discovery, and database loading.
4. **Method Overriding**: `Song` overrides `equals()`, `hashCode()`, and `toString()`.
5. **Language Keywords**: `this` in constructors and setters, `super` in exceptions and application lifecycle, `static` in database connection factory, `final` for thread safety.
6. **Enums**: `PlaybackState` and `CommandIntent` with state query methods.
7. **Collections and Generics**: `List<Song>`, `ArrayList<Song>`, `CopyOnWriteArrayList` for thread-safe event listeners.
8. **Java NIO and Streams**: `MusicLibrary` scans the file system using `Files.walk(Path)` and stream filters without blocking the UI.
9. **Multithreading and Concurrency**: Background tasks run via `ExecutorService` and `CompletableFuture`; UI events dispatched through `Platform.runLater()`.
10. **JDBC and SQL**: SQLite database with parameterized `PreparedStatement` queries and `try-with-resources`.
11. **Regular Expressions**: `CommandParser` parses natural language commands, decades, year ranges, and titles using `Pattern` and `Matcher`.
12. **Design Patterns**: Repository pattern (`SongRepository`), Observer pattern (playback event listeners), Service layer separation.
