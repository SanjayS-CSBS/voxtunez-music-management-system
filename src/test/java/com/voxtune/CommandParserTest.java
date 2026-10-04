package com.voxtune;

import com.voxtune.ai.CommandParser;
import com.voxtune.model.CommandIntent;
import com.voxtune.model.MusicQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying natural language command parsing logic in CommandParser.
 */
public class CommandParserTest {
    private CommandParser parser;

    @BeforeEach
    public void setUp() {
        parser = new CommandParser();
    }

    @Test
    public void testTaylorSwiftDecadeCommand() {
        MusicQuery query = parser.parse("Play Taylor Swift songs from the 2000s");
        assertEquals(CommandIntent.PLAY, query.getIntent());
        assertEquals("Taylor Swift", query.getArtist());
        assertEquals(2000, query.getFromYear());
        assertEquals(2009, query.getToYear());
    }

    @Test
    public void testYearRangeCommand() {
        MusicQuery query = parser.parse("Play songs from 2010 to 2015");
        assertEquals(CommandIntent.PLAY, query.getIntent());
        assertEquals(2010, query.getFromYear());
        assertEquals(2015, query.getToYear());
    }

    @Test
    public void testGenreCommand() {
        MusicQuery query = parser.parse("Play rock songs");
        assertEquals(CommandIntent.PLAY, query.getIntent());
        assertEquals("Rock", query.getGenre());
    }

    @Test
    public void testPlaybackControls() {
        assertEquals(CommandIntent.PAUSE, parser.parse("Pause the music").getIntent());
        assertEquals(CommandIntent.RESUME, parser.parse("Continue playing").getIntent());
        assertEquals(CommandIntent.STOP, parser.parse("Stop").getIntent());
        assertEquals(CommandIntent.NEXT, parser.parse("Next song").getIntent());
        assertEquals(CommandIntent.PREVIOUS, parser.parse("Previous song").getIntent());
    }

    @Test
    public void testNinetiesDecade() {
        MusicQuery query = parser.parse("Play songs from the 90s");
        assertEquals(1990, query.getFromYear());
        assertEquals(1999, query.getToYear());
    }

    @Test
    public void testMoodCommand() {
        MusicQuery query = parser.parse("Play something relaxing");
        assertEquals("Relaxing", query.getMood());
    }

    @Test
    public void testConversationalQueries() {
        assertEquals(CommandIntent.CONVERSATION, parser.parse("Who are you?").getIntent());
        assertEquals(CommandIntent.CONVERSATION, parser.parse("What can you do?").getIntent());
        assertEquals(CommandIntent.CONVERSATION, parser.parse("Hi").getIntent());
    }

    @Test
    public void testArtistOnlyCommand() {
        MusicQuery query = parser.parse("Play Adele");
        assertEquals(CommandIntent.PLAY, query.getIntent());
        assertEquals("Adele", query.getArtist());
        assertNull(query.getText(), "Artist-only command should not leave stray free text");
    }

    @Test
    public void testExactTitleCommand() {
        MusicQuery query = parser.parse("Play Hello");
        assertEquals("Hello", query.getText());
    }

    @Test
    public void testByArtistFallbackForUnknownArtist() {
        MusicQuery query = parser.parse("Play songs by Foo Fighters");
        assertEquals("Foo Fighters", query.getArtist());
        assertNull(query.getText(), "'by <artist>' should not leave residual free text");
    }

    @Test
    public void testTitleWordIsNotMisreadAsGenre() {
        MusicQuery query = parser.parse("Play Dance The Night");
        assertEquals("Dance The Night", query.getText());
        assertNull(query.getGenre(), "A genre word inside a known title must not set the genre filter");
    }

    @Test
    public void testMoodHasNoResidualText() {
        MusicQuery query = parser.parse("Play something relaxing");
        assertEquals("Relaxing", query.getMood());
        assertNull(query.getText(), "Fillers like 'something' must not become search text");
    }

    @Test
    public void testFavoriteCommands() {
        assertEquals(CommandIntent.FAVORITE, parser.parse("Add this to favorites").getIntent());
        assertEquals(CommandIntent.FAVORITE, parser.parse("favorite this").getIntent());

        MusicQuery named = parser.parse("Add Hello to favorites");
        assertEquals(CommandIntent.FAVORITE, named.getIntent());
        assertEquals("Hello", named.getText());
    }

    @Test
    public void testUnfavoriteCommands() {
        assertEquals(CommandIntent.UNFAVORITE, parser.parse("Remove from favorites").getIntent());
        assertEquals(CommandIntent.UNFAVORITE, parser.parse("unfavorite this").getIntent());
    }

    @Test
    public void testPlayCommandIsNotMistakenForFavorite() {
        assertEquals(CommandIntent.PLAY, parser.parse("Play Taylor Swift").getIntent());
    }

    @Test
    public void testLibraryVocabularyEnablesPreciseMatch() {
        parser.setLibraryVocabulary(List.of("Aurora Falls"), List.of("Paper Boats"));
        MusicQuery query = parser.parse("play paper boats");
        assertEquals("Paper Boats", query.getText());
        assertNull(query.getArtist());
    }
}
