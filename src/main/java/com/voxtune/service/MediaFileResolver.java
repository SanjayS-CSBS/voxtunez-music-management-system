package com.voxtune.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Resolves stored song file paths to real files on disk and canonicalizes real
 * files back into portable, machine-independent paths.
 *
 * <p>The library stores paths relative to the project directory (e.g.
 * {@code music/Adele - Hello - 2015.wav}) so that moving or copying the project
 * never breaks playback. Legacy/absolute paths from other machines are still
 * resolved by falling back to matching the file name inside the {@code music/}
 * folder.</p>
 */
public final class MediaFileResolver {
    public static final String MUSIC_DIR = "music";

    private MediaFileResolver() {
    }

    /**
     * Returns true when a file name has a supported audio extension.
     */
    public static boolean isAudioFileName(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase();
        return lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".m4a")
                || lower.endsWith(".aiff") || lower.endsWith(".aif");
    }

    /**
     * Resolves a stored path to an existing readable audio file, or null if it cannot be found.
     */
    public static File resolve(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return null;
        }

        // 1. Try the stored path as-is (absolute or relative to the working directory).
        File direct = new File(storedPath);
        if (direct.isFile() && direct.canRead()) {
            return direct;
        }

        String fileName = new File(storedPath).getName();
        if (fileName.isBlank()) {
            return null;
        }

        // 2. Try "music/<fileName>" relative to the working directory and every ancestor.
        Path base = Paths.get("").toAbsolutePath().normalize();
        for (Path dir = base; dir != null; dir = dir.getParent()) {
            File candidate = dir.resolve(MUSIC_DIR).resolve(fileName).toFile();
            if (candidate.isFile() && candidate.canRead()) {
                return candidate;
            }
        }

        // 3. Last resort: case-insensitive match of the file name inside the music folder.
        return findByFileName(base.resolve(MUSIC_DIR), fileName);
    }

    private static File findByFileName(Path musicDir, String fileName) {
        if (!Files.isDirectory(musicDir)) {
            return null;
        }
        try (Stream<Path> stream = Files.list(musicDir)) {
            Optional<Path> match = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(fileName))
                    .findFirst();
            return match.map(p -> p.toFile()).orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Canonicalizes a real file into the portable form stored in the database.
     * Files under the working directory become relative paths (e.g. "music/song.wav");
     * anything else keeps its absolute path.
     */
    public static String toStoredPath(File file) {
        Path absolute = file.toPath().toAbsolutePath().normalize();
        Path base = Paths.get("").toAbsolutePath().normalize();
        if (absolute.startsWith(base)) {
            return base.relativize(absolute).toString().replace('\\', '/');
        }
        return absolute.toString();
    }
}
