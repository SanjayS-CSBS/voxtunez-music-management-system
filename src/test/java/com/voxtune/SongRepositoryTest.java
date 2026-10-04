package com.voxtune;

import com.voxtune.db.Database;
import com.voxtune.db.SongRepository;
import com.voxtune.model.CommandIntent;
import com.voxtune.model.MusicQuery;
import com.voxtune.model.Song;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying database CRUD and dynamic search queries in SongRepository.
 */
public class SongRepositoryTest {
    private static final String TEST_DB = "voxtune_repo_test.db";
    private static SongRepository repository;

    @BeforeAll
    public static void setUp() {
        // Ensure clean test database state
        new File(TEST_DB).delete();
        Database.setDatabasePath(TEST_DB);
        repository = new SongRepository();

        // Seed test songs
        repository.save(new Song("Love Story", "Taylor Swift", "Fearless", "Country Pop", 2008, "music/repo_test1.wav", 180.0));
        repository.save(new Song("Blank Space", "Taylor Swift", "1989", "Pop", 2014, "music/repo_test2.wav", 180.0));
        repository.save(new Song("Radioactive", "Imagine Dragons", "Night Visions", "Rock", 2012, "music/repo_test3.wav", 180.0));
        repository.save(new Song("Smells Like Teen Spirit", "Nirvana", "Nevermind", "Grunge", 1991, "music/repo_test4.wav", 180.0));
    }

    @AfterAll
    public static void tearDown() {
        new File(TEST_DB).delete();
    }

    @Test
    public void testFindAll() {
        List<Song> all = repository.findAll();
        assertTrue(all.size() >= 4);
    }

    @Test
    public void testFindByArtist() {
        List<Song> taylors = repository.findByArtist("Taylor Swift");
        assertEquals(2, taylors.size());
    }

    @Test
    public void testSearchCombinedArtistAndDecade() {
        MusicQuery query = new MusicQuery(CommandIntent.PLAY, "Taylor Swift", null, 2000, 2009, null, null, null);
        List<Song> results = repository.search(query);
        assertEquals(1, results.size());
        assertEquals("Love Story", results.get(0).getTitle());
    }

    @Test
    public void testToggleFavorite() {
        List<Song> all = repository.findAll();
        assertFalse(all.isEmpty());
        Song song = all.get(0);

        // First toggle: from 0 (not favorite) to 1 (favorite)
        boolean toggled = repository.toggleFavorite(song.getId());
        assertTrue(toggled);

        List<Song> favs = repository.findFavorites();
        assertTrue(favs.stream().anyMatch(s -> s.getId() == song.getId()), "Song should now be in favorites");

        // Second toggle: from 1 back to 0
        repository.toggleFavorite(song.getId());
        List<Song> favsAfter = repository.findFavorites();
        assertFalse(favsAfter.stream().anyMatch(s -> s.getId() == song.getId()), "Song should no longer be in favorites");
    }

    @Test
    public void testFindRecentlyAdded() {
        List<Song> recent = repository.findRecentlyAdded(2);
        assertEquals(2, recent.size());
    }
}
