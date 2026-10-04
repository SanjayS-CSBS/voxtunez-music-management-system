package com.voxtune.service;

import com.voxtune.db.SongRepository;
import com.voxtune.model.Song;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Service managing local directory scanning, file parsing, and music metadata synchronization.
 * Demonstrates:
 * - Java NIO (Path, Files, Files.walk, Files.isRegularFile)
 * - Java Streams API
 * - Regular expressions with Pattern and Matcher
 * - Multithreading using ExecutorService and CompletableFuture
 * - Asynchronous background processing preventing UI freezes
 */
public class MusicLibrary {
    private static final String DEFAULT_MUSIC_DIR = "music";
    // Regex for parsing: Artist - Title - Year.ext
    private static final Pattern FILENAME_PATTERN = Pattern.compile("^(.+?)\\s*-\\s*(.+?)\\s*-\\s*(\\d{4})\\.(mp3|wav|m4a|aiff)$", Pattern.CASE_INSENSITIVE);
    // Secondary fallback regex: Artist - Title.ext
    private static final Pattern SIMPLE_PATTERN = Pattern.compile("^(.+?)\\s*-\\s*(.+?)\\.(mp3|wav|m4a|aiff)$", Pattern.CASE_INSENSITIVE);

    private final SongRepository repository;
    private final ExecutorService executor;

    public MusicLibrary(SongRepository repository) {
        this.repository = repository;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "VoxTune-MusicScanner");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Synchronizes the database with the music folder: repairs stale file paths left over
     * from a previous install location, prunes entries whose files no longer exist, then
     * rescans and upserts every track. Safe to call on every startup.
     *
     * @return the number of tracks discovered on disk
     */
    public int syncLibrary() {
        repairExistingPaths();
        return scanDirectory(Paths.get(DEFAULT_MUSIC_DIR));
    }

    /**
     * Relinks database rows to real files and removes orphans. Rows are never pruned when the
     * music folder itself is missing, so an accidental empty scan cannot wipe the library.
     */
    public void repairExistingPaths() {
        boolean musicDirExists = Files.isDirectory(Paths.get(DEFAULT_MUSIC_DIR));
        Set<String> claimedPaths = new HashSet<>();

        for (Song song : repository.findAll()) {
            File resolved = MediaFileResolver.resolve(song.getFilePath());
            if (resolved == null) {
                // File is gone everywhere: prune it (but never when the music folder itself is missing).
                if (musicDirExists) {
                    repository.deleteById(song.getId());
                }
                continue;
            }

            String canonical = MediaFileResolver.toStoredPath(resolved);

            // Another row already owns this file (duplicate from an earlier scan): drop this one.
            if (!claimedPaths.add(canonical)) {
                repository.deleteById(song.getId());
                continue;
            }

            if (!canonical.equals(song.getFilePath()) && !repository.updateFilePath(song.getId(), canonical)) {
                // Could not relink (would violate uniqueness): the row that already owns it wins.
                repository.deleteById(song.getId());
            }
        }
    }

    /**
     * Asynchronously scans the default music folder and stores discovered tracks in SQLite.
     */
    public CompletableFuture<Integer> scanAsync() {
        return scanAsync(Paths.get(DEFAULT_MUSIC_DIR));
    }

    /**
     * Asynchronously scans the specified folder.
     */
    public CompletableFuture<Integer> scanAsync(Path directoryPath) {
        return CompletableFuture.supplyAsync(() -> scanDirectory(directoryPath), executor);
    }

    /**
     * Synchronous scanning logic using Java NIO Files.walk.
     */
    public int scanDirectory(Path directoryPath) {
        if (!Files.exists(directoryPath) || !Files.isDirectory(directoryPath)) {
            try {
                Files.createDirectories(directoryPath);
            } catch (IOException e) {
                System.err.println("Could not create music directory: " + e.getMessage());
                return 0;
            }
        }

        List<Song> discoveredSongs = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(directoryPath)) {
            stream.filter(Files::isRegularFile)
                  .filter(this::isSupportedAudioFile)
                  .forEach(path -> {
                      Song song = parseSongFromPath(path);
                      if (song != null) {
                          discoveredSongs.add(song);
                      }
                  });
        } catch (IOException e) {
            System.err.println("Error scanning music folder: " + e.getMessage());
        }

        if (!discoveredSongs.isEmpty()) {
            return repository.saveAll(discoveredSongs);
        }
        return 0;
    }

    private boolean isSupportedAudioFile(Path path) {
        return MediaFileResolver.isAudioFileName(path.getFileName().toString());
    }

    /**
     * Imports external audio files into the library folder asynchronously.
     */
    public CompletableFuture<Integer> importAsync(List<File> sourceFiles) {
        return CompletableFuture.supplyAsync(() -> importFiles(sourceFiles), executor);
    }

    /**
     * Copies the given files into the default music folder and records them in the database.
     *
     * @return the number of songs successfully imported
     */
    public int importFiles(List<File> sourceFiles) {
        return importFiles(sourceFiles, Paths.get(DEFAULT_MUSIC_DIR));
    }

    /**
     * Copies the given files into {@code musicDir} and records them in the database. Files that
     * are already inside the music folder are imported in place without being copied. Name
     * collisions are resolved by appending " (1)", " (2)", etc. Non-audio files are ignored.
     *
     * @return the number of songs successfully imported
     */
    public int importFiles(List<File> sourceFiles, Path musicDir) {
        if (sourceFiles == null || sourceFiles.isEmpty()) {
            return 0;
        }

        try {
            Files.createDirectories(musicDir);
        } catch (IOException e) {
            System.err.println("Could not create music directory: " + e.getMessage());
            return 0;
        }

        Path musicDirAbs = musicDir.toAbsolutePath().normalize();
        List<Song> imported = new ArrayList<>();

        for (File source : sourceFiles) {
            if (source == null || !source.isFile() || !isSupportedAudioFile(source.toPath())) {
                continue;
            }
            try {
                Path target = source.toPath().toAbsolutePath().normalize();
                if (!target.startsWith(musicDirAbs)) {
                    Path destination = uniqueDestination(musicDirAbs, source.getName());
                    Files.copy(source.toPath(), destination, StandardCopyOption.COPY_ATTRIBUTES);
                    target = destination;
                }
                Song song = parseSongFromPath(target);
                if (song != null) {
                    imported.add(song);
                }
            } catch (IOException e) {
                System.err.println("Could not import \"" + source.getName() + "\": " + e.getMessage());
            }
        }

        if (!imported.isEmpty()) {
            repository.saveAll(imported);
        }
        return imported.size();
    }

    private Path uniqueDestination(Path directory, String fileName) {
        Path candidate = directory.resolve(fileName);
        if (!Files.exists(candidate)) {
            return candidate;
        }
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String extension = dot > 0 ? fileName.substring(dot) : "";
        int counter = 1;
        while (Files.exists(candidate)) {
            candidate = directory.resolve(base + " (" + counter + ")" + extension);
            counter++;
        }
        return candidate;
    }

    /**
     * Extracts song metadata from file name and path.
     */
    public Song parseSongFromPath(Path path) {
        String filename = path.getFileName().toString();
        // Store a portable path relative to the project directory, not a machine-specific absolute path.
        String storedPath = MediaFileResolver.toStoredPath(path.toFile());

        Matcher matcher = FILENAME_PATTERN.matcher(filename);
        if (matcher.matches()) {
            String artist = matcher.group(1).trim();
            String title = matcher.group(2).trim();
            int year = Integer.parseInt(matcher.group(3));
            String genre = inferGenre(artist, title);
            double duration = 180.0; // Standard ~3 min fallback duration for generated tracks

            return new Song(title, artist, "Singles", genre, year, storedPath, duration);
        }

        Matcher simpleMatcher = SIMPLE_PATTERN.matcher(filename);
        if (simpleMatcher.matches()) {
            String artist = simpleMatcher.group(1).trim();
            String title = simpleMatcher.group(2).trim();
            String genre = inferGenre(artist, title);

            return new Song(title, artist, "Singles", genre, 0, storedPath, 180.0);
        }

        // Generic fallback for plain filenames
        String baseName = filename.replaceFirst("[.][^.]+$", "");
        return new Song(baseName, "Various Artists", "Local Files", "General", 0, storedPath, 180.0);
    }

    /**
     * Simple genre inference based on known artist styles or keywords.
     */
    private String inferGenre(String artist, String title) {
        String art = artist.toLowerCase();
        if (art.contains("swift") || art.contains("sheeran") || art.contains("lipa") || art.contains("mars")) {
            return "Pop";
        } else if (art.contains("dragons") || art.contains("coldplay") || art.contains("nirvana") || 
                   art.contains("queen") || art.contains("linkin") || art.contains("green day") || art.contains("radiohead")) {
            return "Rock";
        } else if (art.contains("adele")) {
            return "Soul Pop";
        } else if (art.contains("weeknd")) {
            return "R&B / Synthpop";
        } else if (art.contains("billie eilish")) {
            return "Alternative / Indie";
        }
        return "General";
    }

    public void shutdown() {
        executor.shutdown();
    }
}
