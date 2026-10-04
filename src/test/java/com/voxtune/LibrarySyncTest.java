package com.voxtune;

import com.voxtune.db.Database;
import com.voxtune.db.SongRepository;
import com.voxtune.model.Song;
import com.voxtune.service.MusicLibrary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that MusicLibrary.syncLibrary() heals a database whose stored paths
 * no longer match the files on disk.
 */
public class LibrarySyncTest {
    private static final String TEST_DB = "voxtune_sync_test.db";

    private SongRepository repository;
    private MusicLibrary library;

    @BeforeEach
    public void setUp() {
        new File(TEST_DB).delete();
        Database.setDatabasePath(TEST_DB);
        repository = new SongRepository();
        library = new MusicLibrary(repository);
    }

    @AfterEach
    public void tearDown() {
        library.shutdown();
        new File(TEST_DB).delete();
    }

    @Test
    public void syncRepairsStalePathAndPreservesFavorite() {
        Song stale = new Song("Hello", "Adele", "Singles", "Pop", 2015,
                "C:\\Users\\someone\\old\\VoxTunez\\music\\Adele - Hello - 2015.wav", 180.0);
        stale.setFavorite(true);
        repository.save(stale);

        library.syncLibrary();

        Song hello = repository.findAll().stream()
                .filter(s -> s.getTitle().equals("Hello"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Relinked 'Hello' row is missing"));

        assertEquals("music/Adele - Hello - 2015.wav", hello.getFilePath().replace('\\', '/'));
        assertTrue(new File(hello.getFilePath()).exists(), "Relinked path must point to a real file");

        boolean favoritePreserved = repository.findFavorites().stream()
                .anyMatch(s -> s.getTitle().equals("Hello"));
        assertTrue(favoritePreserved, "Favorite flag must survive path repair");

        assertTrue(repository.findAll().stream()
                        .noneMatch(s -> s.getFilePath().contains("C:\\Users")),
                "No stale absolute rows should remain");
    }

    @Test
    public void syncDeduplicatesRowsPointingAtSameFile() {
        // Simulates a database that accumulated a portable row and a stale absolute row for the same track
        repository.save(new Song("Hello", "Adele", "Singles", "Pop", 2015,
                "music/Adele - Hello - 2015.wav", 180.0));
        repository.save(new Song("Hello", "Adele", "Singles", "Pop", 2015,
                "C:\\old\\VoxTunez\\music\\Adele - Hello - 2015.wav", 180.0));

        library.syncLibrary();

        long helloCount = repository.findAll().stream()
                .filter(s -> s.getTitle().equals("Hello"))
                .count();
        assertEquals(1, helloCount, "Duplicate rows for the same file should collapse to one");
    }

    @Test
    public void syncPrunesOrphansWhoseFilesAreMissing() {
        repository.save(new Song("Ghost Track", "Nobody", "Singles", "General", 0,
                "music/No Such Track - 1999.wav", 100.0));

        library.syncLibrary();

        List<Song> all = repository.findAll();
        assertTrue(all.stream().noneMatch(s -> s.getTitle().equals("Ghost Track")),
                "Rows whose files cannot be found should be pruned");
    }
}
