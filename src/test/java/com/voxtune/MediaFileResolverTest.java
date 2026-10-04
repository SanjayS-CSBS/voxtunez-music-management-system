package com.voxtune;

import com.voxtune.service.MediaFileResolver;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for resolving stored song paths to real files on disk.
 */
public class MediaFileResolverTest {
    private static final String SAMPLE = "Adele - Hello - 2015.wav";

    @Test
    public void resolvesPortableRelativePath() {
        File file = MediaFileResolver.resolve("music/" + SAMPLE);
        assertNotNull(file);
        assertTrue(file.isFile());
    }

    @Test
    public void resolvesStaleAbsolutePathByFileName() {
        // Simulates a database created on a different machine/location
        File file = MediaFileResolver.resolve("C:\\Users\\someone\\old\\VoxTunez\\music\\" + SAMPLE);
        assertNotNull(file, "Stale absolute paths should be relinked by file name");
        assertTrue(file.isFile());
    }

    @Test
    public void returnsNullForMissingFile() {
        assertNull(MediaFileResolver.resolve("music/No Such Track - 1970.wav"));
    }

    @Test
    public void recognizesSupportedAudioFileNames() {
        assertTrue(MediaFileResolver.isAudioFileName("Song.MP3"));
        assertTrue(MediaFileResolver.isAudioFileName("track.wav"));
        assertTrue(MediaFileResolver.isAudioFileName("a.m4a"));
        assertTrue(MediaFileResolver.isAudioFileName("b.aiff"));
        assertFalse(MediaFileResolver.isAudioFileName("notes.txt"));
        assertFalse(MediaFileResolver.isAudioFileName(null));
    }

    @Test
    public void canonicalizesToPortableRelativePath() {
        File file = MediaFileResolver.resolve("music/" + SAMPLE);
        assertNotNull(file);
        assertEquals("music/" + SAMPLE, MediaFileResolver.toStoredPath(file).replace('\\', '/'));
    }
}
