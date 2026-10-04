package com.voxtune.db;

import com.voxtune.model.MusicQuery;
import com.voxtune.model.Song;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository data access object handling SQLite queries for songs.
 * Demonstrates:
 * - Data Access Object / Repository Pattern
 * - JDBC PreparedStatements with parameterized queries
 * - try-with-resources for automatic resource cleanup
 * - Java Collections (List, ArrayList)
 * - Java 8 Optional
 * - Dynamic SQL generation with strict parameter binding
 */
public class SongRepository {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public SongRepository() {
        Database.initialize();
    }

    /**
     * Inserts or updates a song by its unique file_path.
     */
    public boolean save(Song song) {
        String sql = """
            INSERT INTO songs (title, artist, album, genre, year, file_path, duration, favorite, date_added)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(file_path) DO UPDATE SET
                title = excluded.title,
                artist = excluded.artist,
                album = excluded.album,
                genre = excluded.genre,
                year = excluded.year,
                duration = excluded.duration;
            """;

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, song.getTitle());
            ps.setString(2, song.getArtist());
            ps.setString(3, song.getAlbum());
            ps.setString(4, song.getGenre());
            ps.setInt(5, song.getYear());
            ps.setString(6, song.getFilePath());
            ps.setDouble(7, song.getDuration());
            ps.setInt(8, song.isFavorite() ? 1 : 0);
            ps.setString(9, song.getDateAdded() != null ? song.getDateAdded().format(DATE_FORMATTER) : LocalDateTime.now().format(DATE_FORMATTER));

            int affected = ps.executeUpdate();
            if (affected > 0 && song.getId() <= 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        song.setId(rs.getInt(1));
                    }
                }
            }
            return affected > 0;
        } catch (SQLException e) {
            System.err.println("Error saving song [" + song.getTitle() + "]: " + e.getMessage());
            return false;
        }
    }

    /**
     * Batch inserts a list of songs inside a single transaction.
     */
    public int saveAll(List<Song> songs) {
        String sql = """
            INSERT INTO songs (title, artist, album, genre, year, file_path, duration, favorite, date_added)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(file_path) DO UPDATE SET
                title = excluded.title,
                artist = excluded.artist,
                album = excluded.album,
                genre = excluded.genre,
                year = excluded.year,
                duration = excluded.duration;
            """;

        int count = 0;
        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Song song : songs) {
                    ps.setString(1, song.getTitle());
                    ps.setString(2, song.getArtist());
                    ps.setString(3, song.getAlbum());
                    ps.setString(4, song.getGenre());
                    ps.setInt(5, song.getYear());
                    ps.setString(6, song.getFilePath());
                    ps.setDouble(7, song.getDuration());
                    ps.setInt(8, song.isFavorite() ? 1 : 0);
                    ps.setString(9, song.getDateAdded() != null ? song.getDateAdded().format(DATE_FORMATTER) : LocalDateTime.now().format(DATE_FORMATTER));
                    ps.addBatch();
                    count++;
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("Error in saveAll batch: " + e.getMessage());
        }
        return count;
    }

    /**
     * Finds all songs sorted by artist and title.
     */
    public List<Song> findAll() {
        String sql = "SELECT * FROM songs ORDER BY artist COLLATE NOCASE ASC, title COLLATE NOCASE ASC";
        return executeQuery(sql);
    }

    /**
     * Finds a song by ID.
     */
    public Optional<Song> findById(int id) {
        String sql = "SELECT * FROM songs WHERE id = ?";
        List<Song> results = executeQuery(sql, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Finds songs by exact or partial artist name.
     */
    public List<Song> findByArtist(String artist) {
        String sql = "SELECT * FROM songs WHERE artist LIKE ? COLLATE NOCASE ORDER BY year ASC, title ASC";
        return executeQuery(sql, "%" + artist.trim() + "%");
    }

    /**
     * Finds songs by genre.
     */
    public List<Song> findByGenre(String genre) {
        String sql = "SELECT * FROM songs WHERE genre LIKE ? COLLATE NOCASE ORDER BY artist ASC, title ASC";
        return executeQuery(sql, "%" + genre.trim() + "%");
    }

    /**
     * Finds songs by year range (e.g. 2000 to 2009).
     */
    public List<Song> findByYearRange(int fromYear, int toYear) {
        String sql = "SELECT * FROM songs WHERE year BETWEEN ? AND ? ORDER BY year ASC, artist ASC";
        return executeQuery(sql, fromYear, toYear);
    }

    /**
     * Search songs by free text matching title, artist, or album.
     */
    public List<Song> searchByText(String text) {
        if (text == null || text.isBlank()) {
            return findAll();
        }
        String pattern = "%" + text.trim() + "%";
        String sql = """
            SELECT * FROM songs 
            WHERE title LIKE ? COLLATE NOCASE 
               OR artist LIKE ? COLLATE NOCASE 
               OR album LIKE ? COLLATE NOCASE 
               OR genre LIKE ? COLLATE NOCASE
            ORDER BY artist ASC, title ASC
            """;
        return executeQuery(sql, pattern, pattern, pattern, pattern);
    }

    /**
     * Dynamic search combining criteria from a structured MusicQuery.
     * Supports combining artist, genre, year range, mood, and limit safely.
     */
    public List<Song> search(MusicQuery query) {
        if (query == null) {
            return findAll();
        }

        StringBuilder sql = new StringBuilder("SELECT * FROM songs WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (query.hasArtist()) {
            sql.append(" AND artist LIKE ? COLLATE NOCASE");
            params.add("%" + query.getArtist().trim() + "%");
        }

        if (query.hasGenre()) {
            sql.append(" AND genre LIKE ? COLLATE NOCASE");
            params.add("%" + query.getGenre().trim() + "%");
        }

        if (query.getFromYear() != null && query.getToYear() != null) {
            sql.append(" AND year BETWEEN ? AND ?");
            params.add(query.getFromYear());
            params.add(query.getToYear());
        } else if (query.getFromYear() != null) {
            sql.append(" AND year >= ?");
            params.add(query.getFromYear());
        } else if (query.getToYear() != null) {
            sql.append(" AND year <= ?");
            params.add(query.getToYear());
        }

        if (query.hasMood()) {
            // Map mood keywords to relevant genres or matching titles
            String moodStr = query.getMood().toLowerCase();
            if (moodStr.contains("relax") || moodStr.contains("chill") || moodStr.contains("calm")) {
                sql.append(" AND (genre LIKE '%pop%' OR genre LIKE '%folk%' OR genre LIKE '%soul%' OR genre LIKE '%indie%')");
            } else if (moodStr.contains("energetic") || moodStr.contains("workout") || moodStr.contains("party")) {
                sql.append(" AND (genre LIKE '%rock%' OR genre LIKE '%dance%' OR genre LIKE '%disco%' OR genre LIKE '%electro%')");
            }
        }

        if (query.hasText()) {
            sql.append(" AND (title LIKE ? COLLATE NOCASE OR artist LIKE ? COLLATE NOCASE OR album LIKE ? COLLATE NOCASE)");
            String textPattern = "%" + query.getText().trim() + "%";
            params.add(textPattern);
            params.add(textPattern);
            params.add(textPattern);
        }

        sql.append(" ORDER BY year ASC, title ASC");

        if (query.getLimit() != null && query.getLimit() > 0) {
            sql.append(" LIMIT ?");
            params.add(query.getLimit());
        }

        return executeQuery(sql.toString(), params.toArray());
    }

    /**
     * Toggles the favorite status of a song.
     */
    public boolean toggleFavorite(int songId) {
        String sql = "UPDATE songs SET favorite = CASE WHEN favorite = 1 THEN 0 ELSE 1 END WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, songId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error toggling favorite: " + e.getMessage());
            return false;
        }
    }

    /**
     * Retrieves all songs marked as favorite.
     */
    public List<Song> findFavorites() {
        String sql = "SELECT * FROM songs WHERE favorite = 1 ORDER BY artist ASC, title ASC";
        return executeQuery(sql);
    }

    /**
     * Retrieves the most recently added songs, newest first.
     */
    public List<Song> findRecentlyAdded(int limit) {
        if (limit <= 0) {
            limit = 20;
        }
        String sql = "SELECT * FROM songs ORDER BY date_added DESC, id DESC LIMIT ?";
        return executeQuery(sql, limit);
    }

    /**
     * Updates the stored file path of an existing song (used when relinking stale paths).
     */
    public boolean updateFilePath(int songId, String filePath) {
        String sql = "UPDATE songs SET file_path = ? WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, filePath);
            ps.setInt(2, songId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating file path for song " + songId + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Deletes a song row by ID (used to prune entries whose files no longer exist).
     */
    public boolean deleteById(int songId) {
        String sql = "DELETE FROM songs WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, songId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting song " + songId + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Returns total count of songs stored in the database.
     */
    public int count() {
        String sql = "SELECT COUNT(*) FROM songs";
        try (Connection conn = Database.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Error counting songs: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Helper method to execute a query with variadic parameters and map result rows to Song objects.
     */
    private List<Song> executeQuery(String sql, Object... params) {
        List<Song> list = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToSong(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Database query error: " + e.getMessage() + " | SQL: " + sql);
        }
        return list;
    }

    private Song mapRowToSong(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String title = rs.getString("title");
        String artist = rs.getString("artist");
        String album = rs.getString("album");
        String genre = rs.getString("genre");
        int year = rs.getInt("year");
        String filePath = rs.getString("file_path");
        double duration = rs.getDouble("duration");
        boolean favorite = rs.getInt("favorite") == 1;
        String dateAddedStr = rs.getString("date_added");

        LocalDateTime dateAdded = LocalDateTime.now();
        if (dateAddedStr != null && !dateAddedStr.isBlank()) {
            try {
                dateAdded = LocalDateTime.parse(dateAddedStr, DATE_FORMATTER);
            } catch (Exception ignored) {
            }
        }

        return new Song(id, title, artist, album, genre, year, filePath, duration, favorite, dateAdded);
    }
}
