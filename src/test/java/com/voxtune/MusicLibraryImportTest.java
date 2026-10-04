package com.voxtune;

import com.voxtune.db.Database;
import com.voxtune.db.SongRepository;
import com.voxtune.model.Song;
import com.voxtune.service.MusicLibrary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the "Add Songs" import path: external audio files are copied into the
 * library folder and recorded in the database.
 */
public class MusicLibraryImportTest {
    private static final String TEST_DB = "voxtune_import_test.db";
    private static final Path SAMPLE = Paths.get("music", "Adele - Hello - 2015.wav");

    private SongRepository repository;
    private MusicLibrary library;
    private Path tempDir;

    @BeforeEach
    public void setUp() throws Exception {
        new File(TEST_DB).delete();
        Database.setDatabasePath(TEST_DB);
        repository = new SongRepository();
        library = new MusicLibrary(repository);
        tempDir = Files.createTempDirectory("voxtune-import-test");
    }

    @AfterEach
    public void tearDown() throws Exception {
        library.shutdown();
        new File(TEST_DB).delete();
        if (tempDir != null && Files.exists(tempDir)) {
            try (var walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                        // best-effort cleanup
                    }
                });
            }
        }
    }

    @Test
    public void importCopiesFileIntoLibraryAndDatabase() throws Exception {
        File source = tempDir.resolve("New Artist - New Song - 2020.wav").toFile();
        Files.copy(SAMPLE, source.toPath(), StandardCopyOption.REPLACE_EXISTING);

        Path musicDir = tempDir.resolve("library");

        int count = library.importFiles(List.of(source), musicDir);
        assertEquals(1, count);

        Path copied = musicDir.resolve("New Artist - New Song - 2020.wav");
        assertTrue(Files.exists(copied), "File should be copied into the library folder");

        Song song = repository.findAll().stream()
                .filter(s -> s.getTitle().equals("New Song"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Imported song was not recorded in the database"));
        assertEquals("New Artist", song.getArtist());
        assertEquals(2020, song.getYear());
        assertTrue(new File(song.getFilePath()).exists(), "Stored path must point at the imported file");
    }

    @Test
    public void importIgnoresUnsupportedFiles() throws Exception {
        Path text = tempDir.resolve("notes.txt");
        Files.writeString(text, "not audio");
        Path musicDir = tempDir.resolve("library2");

        int count = library.importFiles(List.of(text.toFile()), musicDir);

        assertEquals(0, count);
        assertFalse(Files.exists(musicDir.resolve("notes.txt")), "Non-audio files must not be copied");
        assertTrue(repository.findAll().isEmpty(), "Non-audio files must not be recorded");
    }

    @Test
    public void importDoesNotRecopyFilesAlreadyInsideLibraryFolder() throws Exception {
        Path musicDir = tempDir.resolve("library3");
        Files.createDirectories(musicDir);
        Path existing = musicDir.resolve("Dua Lipa - Levitating - 2020.wav");
        Files.copy(SAMPLE, existing, StandardCopyOption.REPLACE_EXISTING);

        int count = library.importFiles(List.of(existing.toFile()), musicDir);

        assertEquals(1, count);
        try (var list = Files.list(musicDir)) {
            assertEquals(1, list.count(), "A file already inside the library must not be copied again");
        }
    }

    @Test
    public void importResolvesNameCollisions() throws Exception {
        File first = tempDir.resolve("Duplicate - Song - 2021.wav").toFile();
        File secondDir = Files.createDirectories(tempDir.resolve("other")).resolve("Duplicate - Song - 2021.wav").toFile();
        Files.copy(SAMPLE, first.toPath(), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(SAMPLE, secondDir.toPath(), StandardCopyOption.REPLACE_EXISTING);

        Path musicDir = tempDir.resolve("library4");
        library.importFiles(List.of(first), musicDir);
        library.importFiles(List.of(secondDir), musicDir);

        try (var list = Files.list(musicDir)) {
            assertEquals(2, list.count(), "Colliding file names should both be imported under unique names");
        }
    }
}
