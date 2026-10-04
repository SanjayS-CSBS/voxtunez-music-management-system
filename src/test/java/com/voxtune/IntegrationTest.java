package com.voxtune;

import com.voxtune.ai.CommandParser;
import com.voxtune.db.Database;
import com.voxtune.db.SongRepository;
import com.voxtune.model.MusicQuery;
import com.voxtune.model.Song;
import com.voxtune.service.MusicLibrary;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration test verifying music scanning, command parsing,
 * and database search against the 56 generated 3-minute tracks in music/.
 */
public class IntegrationTest {
    private static SongRepository repository;
    private static MusicLibrary library;
    private static CommandParser parser;

    @BeforeAll
    public static void setUp() {
        Database.setDatabasePath("voxtune_integration.db");
        repository = new SongRepository();
        library = new MusicLibrary(repository);
        parser = new CommandParser();

        // Synchronize with the real music folder (repairs any stale paths, then rescans)
        int count = library.syncLibrary();
        assertTrue(count >= 50, "Expected at least 50 songs discovered, found: " + count);
    }

    @Test
    public void testTaylorSwiftSongs2000sFlow() {
        // Step 1: User says "Play Taylor Swift songs from the 2000s"
        MusicQuery query = parser.parse("Play Taylor Swift songs from the 2000s");
        assertEquals("Taylor Swift", query.getArtist());
        assertEquals(2000, query.getFromYear());
        assertEquals(2009, query.getToYear());

        // Step 2: Database query
        List<Song> results = repository.search(query);
        assertFalse(results.isEmpty(), "Should find Taylor Swift songs from 2000s");

        // Verify matches (Love Story, You Belong With Me, Fifteen)
        for (Song song : results) {
            assertEquals("Taylor Swift", song.getArtist());
            assertTrue(song.getYear() >= 2000 && song.getYear() <= 2009);
            assertTrue(new File(song.getFilePath()).exists(), "Audio file must exist on disk: " + song.getFilePath());
            assertTrue(song.getDuration() >= 170.0, "Audio file duration must be ~3 minutes");
        }
    }

    @Test
    public void testImagineDragonsSearchFlow() {
        MusicQuery query = parser.parse("Play songs by Imagine Dragons");
        assertEquals("Imagine Dragons", query.getArtist());

        List<Song> results = repository.search(query);
        assertTrue(results.size() >= 5, "Expected Imagine Dragons tracks in library");
        for (Song song : results) {
            assertEquals("Imagine Dragons", song.getArtist());
        }
    }

    @Test
    public void testRockSongsFlow() {
        MusicQuery query = parser.parse("Play rock songs");
        assertEquals("Rock", query.getGenre());

        List<Song> results = repository.search(query);
        assertFalse(results.isEmpty(), "Expected rock songs in library");
    }

    @Test
    public void testNinetiesDecadeFlow() {
        MusicQuery query = parser.parse("Play songs from the 90s");
        assertEquals(1990, query.getFromYear());
        assertEquals(1999, query.getToYear());

        List<Song> results = repository.search(query);
        assertFalse(results.isEmpty(), "Expected 90s songs like Nirvana, Oasis, Radiohead");
        for (Song song : results) {
            assertTrue(song.getYear() >= 1990 && song.getYear() <= 1999);
        }
    }

    @Test
    public void testYearRangeFlow() {
        MusicQuery query = parser.parse("Play songs from 2010 to 2015");
        assertEquals(2010, query.getFromYear());
        assertEquals(2015, query.getToYear());

        List<Song> results = repository.search(query);
        assertFalse(results.isEmpty());
        for (Song song : results) {
            assertTrue(song.getYear() >= 2010 && song.getYear() <= 2015);
        }
    }
}
