package dev.oakheart.oaktags.data;

import dev.oakheart.oaktags.model.FilterMode;
import dev.oakheart.oaktags.model.SortMode;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class SQLiteDataStore implements DataStore {
    private final Logger logger;
    private final File dbFile;
    private Connection connection;
    private boolean operational;

    // Prepared statements
    private PreparedStatement loadPlayerTagsStmt;
    private PreparedStatement grantTagStmt;
    private PreparedStatement revokeTagStmt;
    private PreparedStatement loadPlayerSettingsStmt;
    private PreparedStatement savePlayerSettingsStmt;
    private PreparedStatement loadClaimCountsStmt;

    public SQLiteDataStore(Logger logger, File dataFolder) {
        this.logger = logger;
        this.dbFile = new File(dataFolder, "oaktags.db");
        this.operational = false;
    }

    @Override
    public void initialize() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA synchronous=NORMAL");
            }

            createTables();
            prepareStatements();
            operational = true;
            logger.info("SQLite database initialized.");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize SQLite database", e);
            operational = false;
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_tags (
                    uuid TEXT NOT NULL,
                    tag_id TEXT NOT NULL,
                    granted_at INTEGER NOT NULL DEFAULT (strftime('%s','now')),
                    granted_by TEXT,
                    PRIMARY KEY (uuid, tag_id)
                )
                """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_settings (
                    uuid TEXT PRIMARY KEY,
                    active_tag TEXT,
                    sort_mode TEXT NOT NULL DEFAULT 'UNLOCKED_FIRST',
                    sort_reversed INTEGER NOT NULL DEFAULT 0,
                    filter_mode TEXT NOT NULL DEFAULT 'all'
                )
                """);

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_tags_uuid ON player_tags(uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_tags_tag ON player_tags(tag_id)");

            // Migration: add favorites column if missing
            try {
                stmt.execute("ALTER TABLE player_settings ADD COLUMN favorites TEXT NOT NULL DEFAULT ''");
            } catch (SQLException ignored) {
                // Column already exists
            }
        }
    }

    private void prepareStatements() throws SQLException {
        loadPlayerTagsStmt = connection.prepareStatement(
                "SELECT tag_id FROM player_tags WHERE uuid = ?");
        grantTagStmt = connection.prepareStatement(
                "INSERT OR IGNORE INTO player_tags (uuid, tag_id, granted_by) VALUES (?, ?, ?)");
        revokeTagStmt = connection.prepareStatement(
                "DELETE FROM player_tags WHERE uuid = ? AND tag_id = ?");
        loadPlayerSettingsStmt = connection.prepareStatement(
                "SELECT active_tag, sort_mode, sort_reversed, filter_mode, favorites FROM player_settings WHERE uuid = ?");
        savePlayerSettingsStmt = connection.prepareStatement(
                "INSERT OR REPLACE INTO player_settings (uuid, active_tag, sort_mode, sort_reversed, filter_mode, favorites) VALUES (?, ?, ?, ?, ?, ?)");
        loadClaimCountsStmt = connection.prepareStatement(
                "SELECT tag_id, COUNT(*) as cnt FROM player_tags GROUP BY tag_id");
    }

    @Override
    public synchronized Set<String> loadPlayerTags(UUID uuid) {
        Set<String> tags = new HashSet<>();
        if (!operational) return tags;
        try {
            loadPlayerTagsStmt.setString(1, uuid.toString());
            try (ResultSet rs = loadPlayerTagsStmt.executeQuery()) {
                while (rs.next()) {
                    tags.add(rs.getString("tag_id"));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load player tags for " + uuid, e);
        }
        return tags;
    }

    @Override
    public synchronized void grantTag(UUID uuid, String tagId, String grantedBy) {
        if (!operational) return;
        try {
            grantTagStmt.setString(1, uuid.toString());
            grantTagStmt.setString(2, tagId);
            grantTagStmt.setString(3, grantedBy);
            grantTagStmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to grant tag " + tagId + " to " + uuid, e);
        }
    }

    @Override
    public synchronized void revokeTag(UUID uuid, String tagId) {
        if (!operational) return;
        try {
            revokeTagStmt.setString(1, uuid.toString());
            revokeTagStmt.setString(2, tagId);
            revokeTagStmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to revoke tag " + tagId + " from " + uuid, e);
        }
    }

    @Override
    public synchronized PlayerSettings loadPlayerSettings(UUID uuid) {
        if (!operational) return PlayerSettings.defaults();
        try {
            loadPlayerSettingsStmt.setString(1, uuid.toString());
            try (ResultSet rs = loadPlayerSettingsStmt.executeQuery()) {
                if (rs.next()) {
                    return new PlayerSettings(
                            rs.getString("active_tag"),
                            SortMode.fromString(rs.getString("sort_mode")),
                            rs.getInt("sort_reversed") == 1,
                            FilterMode.fromString(rs.getString("filter_mode")),
                            parseFavorites(rs.getString("favorites"))
                    );
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load player settings for " + uuid, e);
        }
        return PlayerSettings.defaults();
    }

    @Override
    public synchronized void savePlayerSettings(UUID uuid, String activeTag, SortMode sortMode,
                                                boolean sortReversed, FilterMode filterMode,
                                                Set<String> favorites) {
        if (!operational) return;
        try {
            savePlayerSettingsStmt.setString(1, uuid.toString());
            savePlayerSettingsStmt.setString(2, activeTag);
            savePlayerSettingsStmt.setString(3, sortMode.name());
            savePlayerSettingsStmt.setInt(4, sortReversed ? 1 : 0);
            savePlayerSettingsStmt.setString(5, filterMode.getValue());
            savePlayerSettingsStmt.setString(6, serializeFavorites(favorites));
            savePlayerSettingsStmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to save player settings for " + uuid, e);
        }
    }

    @Override
    public synchronized void bulkSavePlayerSettings(Map<UUID, PlayerSettings> settings) {
        if (!operational || settings.isEmpty()) return;
        try {
            connection.setAutoCommit(false);
            for (var entry : settings.entrySet()) {
                PlayerSettings s = entry.getValue();
                savePlayerSettingsStmt.setString(1, entry.getKey().toString());
                savePlayerSettingsStmt.setString(2, s.activeTag());
                savePlayerSettingsStmt.setString(3, s.sortMode().name());
                savePlayerSettingsStmt.setInt(4, s.sortReversed() ? 1 : 0);
                savePlayerSettingsStmt.setString(5, s.filterMode().getValue());
                savePlayerSettingsStmt.setString(6, serializeFavorites(s.favorites()));
                savePlayerSettingsStmt.executeUpdate();
            }
            connection.commit();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to batch save player settings", e);
            try {
                connection.rollback();
            } catch (SQLException re) {
                logger.log(Level.SEVERE, "Failed to rollback batch save transaction", re);
            }
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to restore auto-commit", e);
            }
        }
    }

    @Override
    public synchronized Map<String, Integer> loadClaimCounts() {
        Map<String, Integer> counts = new HashMap<>();
        if (!operational) return counts;
        try (ResultSet rs = loadClaimCountsStmt.executeQuery()) {
            while (rs.next()) {
                counts.put(rs.getString("tag_id"), rs.getInt("cnt"));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load claim counts", e);
        }
        return counts;
    }

    @Override
    public boolean isOperational() {
        return operational && connection != null;
    }

    @Override
    public synchronized void close() {
        operational = false;

        closeStatement(loadPlayerTagsStmt);
        closeStatement(grantTagStmt);
        closeStatement(revokeTagStmt);
        closeStatement(loadPlayerSettingsStmt);
        closeStatement(savePlayerSettingsStmt);
        closeStatement(loadClaimCountsStmt);

        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to close database connection", e);
            }
        }
    }

    private void closeStatement(PreparedStatement stmt) {
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to close prepared statement", e);
            }
        }
    }

    private Set<String> parseFavorites(String raw) {
        if (raw == null || raw.isEmpty()) return new HashSet<>();
        return Arrays.stream(raw.split(","))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(HashSet::new));
    }

    private String serializeFavorites(Set<String> favorites) {
        if (favorites == null || favorites.isEmpty()) return "";
        return String.join(",", favorites);
    }
}
