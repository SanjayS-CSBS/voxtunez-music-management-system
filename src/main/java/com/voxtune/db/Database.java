package com.voxtune.db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Database connection manager and schema initializer for VoxTune.
 * Demonstrates:
 * - JDBC connection management
 * - SQL DDL table creation and indexing
 * - try-with-resources statement
 * - static members and methods
 * - Safe file handling
 */
public class Database {
    private static final String DB_FILE_NAME = "voxtune.db";
    private static String dbUrl = "jdbc:sqlite:" + DB_FILE_NAME;

    static {
        try {
            // Explicitly load SQLite JDBC driver class
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("Failed to load SQLite JDBC driver: " + e.getMessage());
        }
    }

    /**
     * Initializes the database connection and creates tables if they do not exist.
     */
    public static synchronized void initialize() {
        String createTableSql = """
            CREATE TABLE IF NOT EXISTS songs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                album TEXT,
                genre TEXT,
                year INTEGER,
                file_path TEXT UNIQUE NOT NULL,
                duration REAL DEFAULT 0.0,
                favorite INTEGER DEFAULT 0,
                date_added TEXT
            );
            CREATE INDEX IF NOT EXISTS idx_artist ON songs(artist);
            CREATE INDEX IF NOT EXISTS idx_genre ON songs(genre);
            CREATE INDEX IF NOT EXISTS idx_year ON songs(year);
            """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(createTableSql);
        } catch (SQLException e) {
            System.err.println("Error initializing SQLite database: " + e.getMessage());
        }
    }

    /**
     * Obtains a new JDBC Connection.
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl);
    }

    /**
     * Sets a custom database path (useful for testing or dedicated directories).
     */
    public static synchronized void setDatabasePath(String path) {
        dbUrl = "jdbc:sqlite:" + path;
        initialize();
    }
}
