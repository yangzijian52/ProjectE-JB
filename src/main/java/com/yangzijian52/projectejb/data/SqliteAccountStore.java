package com.yangzijian52.projectejb.data;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;

public final class SqliteAccountStore implements AccountStore {
    private final File databaseFile;
    private final long startingBalance;
    private final long maximumBalance;
    private final int busyTimeoutMillis;
    private final boolean transactionHistory;
    private Connection connection;

    public SqliteAccountStore(
            File databaseFile,
            long startingBalance,
            long maximumBalance,
            int busyTimeoutMillis,
            boolean transactionHistory) {
        this.databaseFile = databaseFile;
        this.startingBalance = Math.max(0L, startingBalance);
        this.maximumBalance = Math.max(this.startingBalance, maximumBalance);
        this.busyTimeoutMillis = Math.max(1000, busyTimeoutMillis);
        this.transactionHistory = transactionHistory;
    }

    @Override
    public synchronized void initialize() {
        try {
            File parent = databaseFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new SQLException("Could not create database directory: " + parent);
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA foreign_keys=ON");
                statement.execute("PRAGMA synchronous=NORMAL");
                statement.execute("PRAGMA busy_timeout=" + busyTimeoutMillis);
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS players (
                            uuid TEXT PRIMARY KEY,
                            last_name TEXT NOT NULL,
                            emc INTEGER NOT NULL DEFAULT 0 CHECK (emc >= 0),
                            language TEXT,
                            updated_at INTEGER NOT NULL
                        )
                        """);
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS learned_items (
                            uuid TEXT NOT NULL,
                            material TEXT NOT NULL,
                            learned_at INTEGER NOT NULL,
                            PRIMARY KEY (uuid, material),
                            FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE
                        )
                        """);
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS transactions (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            uuid TEXT NOT NULL,
                            type TEXT NOT NULL,
                            material TEXT,
                            item_count INTEGER NOT NULL DEFAULT 0,
                            emc_delta INTEGER NOT NULL,
                            created_at INTEGER NOT NULL
                        )
                        """);
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_transactions_uuid_time ON transactions(uuid, created_at)");
            }
        } catch (SQLException exception) {
            close();
            throw failure("Failed to initialize SQLite", exception);
        }
    }

    @Override
    public synchronized long getBalance(UUID playerId, String playerName) {
        ensurePlayer(playerId, playerName);
        try (PreparedStatement statement = connection.prepareStatement("SELECT emc FROM players WHERE uuid=?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : startingBalance;
            }
        } catch (SQLException exception) {
            throw failure("Failed to read balance", exception);
        }
    }

    @Override
    public synchronized String getLanguage(UUID playerId, String playerName) {
        ensurePlayer(playerId, playerName);
        try (PreparedStatement statement = connection.prepareStatement("SELECT language FROM players WHERE uuid=?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        } catch (SQLException exception) {
            throw failure("Failed to read language", exception);
        }
    }

    @Override
    public synchronized void setLanguage(UUID playerId, String playerName, String language) {
        ensurePlayer(playerId, playerName);
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE players SET language=?, last_name=?, updated_at=? WHERE uuid=?")) {
            statement.setString(1, language);
            statement.setString(2, safeName(playerName));
            statement.setLong(3, now());
            statement.setString(4, playerId.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw failure("Failed to update language", exception);
        }
    }

    @Override
    public synchronized boolean hasLearned(UUID playerId, String playerName, Material material) {
        ensurePlayer(playerId, playerName);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM learned_items WHERE uuid=? AND material=?")) {
            statement.setString(1, playerId.toString());
            statement.setString(2, material.name());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException exception) {
            throw failure("Failed to read learned item", exception);
        }
    }

    @Override
    public synchronized Set<Material> getLearned(UUID playerId, String playerName) {
        ensurePlayer(playerId, playerName);
        EnumSet<Material> learned = EnumSet.noneOf(Material.class);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT material FROM learned_items WHERE uuid=? ORDER BY material")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    try {
                        Material material = Material.valueOf(result.getString(1));
                        learned.add(material);
                    } catch (IllegalArgumentException ignored) {
                        // A material removed by a future Minecraft version is safely ignored.
                    }
                }
            }
            return Collections.unmodifiableSet(learned);
        } catch (SQLException exception) {
            throw failure("Failed to read learned items", exception);
        }
    }

    @Override
    public synchronized boolean learnAndCredit(UUID playerId, String playerName, Material material, long emc) {
        ensurePlayer(playerId, playerName);
        return inTransaction(() -> {
            if (!insertLearned(playerId, material)) {
                return false;
            }
            updateBalance(playerId, playerName, emc);
            logTransaction(playerId, "LEARN", material, 1, emc);
            return true;
        });
    }

    @Override
    public synchronized int learnManyAndCredit(
            UUID playerId, String playerName, Map<Material, Long> entries, long totalEmc) {
        ensurePlayer(playerId, playerName);
        return inTransaction(() -> {
            int inserted = 0;
            for (Material material : entries.keySet()) {
                if (insertLearned(playerId, material)) {
                    inserted++;
                }
            }
            if (inserted != entries.size()) {
                throw new SQLException("Learned item set changed during bulk learning");
            }
            updateBalance(playerId, playerName, totalEmc);
            logTransaction(playerId, "LEARN_BULK", null, entries.size(), totalEmc);
            return inserted;
        });
    }

    @Override
    public synchronized long credit(
            UUID playerId, String playerName, long amount, String type, Material material, int itemCount) {
        requireNonNegative(amount);
        ensurePlayer(playerId, playerName);
        return inTransaction(() -> {
            long balance = updateBalance(playerId, playerName, amount);
            logTransaction(playerId, type, material, itemCount, amount);
            return balance;
        });
    }

    @Override
    public synchronized boolean tryDebit(
            UUID playerId, String playerName, long amount, String type, Material material, int itemCount) {
        requireNonNegative(amount);
        ensurePlayer(playerId, playerName);
        return inTransaction(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE players SET emc=emc-?, last_name=?, updated_at=? WHERE uuid=? AND emc>=?")) {
                statement.setLong(1, amount);
                statement.setString(2, safeName(playerName));
                statement.setLong(3, now());
                statement.setString(4, playerId.toString());
                statement.setLong(5, amount);
                boolean success = statement.executeUpdate() == 1;
                if (success) {
                    logTransaction(playerId, type, material, itemCount, -amount);
                }
                return success;
            }
        });
    }

    @Override
    public synchronized TransferResult transfer(
            UUID senderId,
            String senderName,
            UUID receiverId,
            String receiverName,
            long amount,
            long fee) {
        requireNonNegative(amount);
        requireNonNegative(fee);
        ensurePlayer(senderId, senderName);
        ensurePlayer(receiverId, receiverName);
        return inTransaction(() -> {
            long total = Math.addExact(amount, fee);
            try (PreparedStatement debit = connection.prepareStatement(
                    "UPDATE players SET emc=emc-?, last_name=?, updated_at=? WHERE uuid=? AND emc>=?")) {
                debit.setLong(1, total);
                debit.setString(2, safeName(senderName));
                debit.setLong(3, now());
                debit.setString(4, senderId.toString());
                debit.setLong(5, total);
                if (debit.executeUpdate() != 1) {
                    return new TransferResult(false, getBalanceDirect(senderId), getBalanceDirect(receiverId));
                }
            }
            updateBalance(receiverId, receiverName, amount);
            logTransaction(senderId, "PAY_SENT", null, 0, -Math.addExact(amount, fee));
            logTransaction(receiverId, "PAY_RECEIVED", null, 0, amount);
            return new TransferResult(true, getBalanceDirect(senderId), getBalanceDirect(receiverId));
        });
    }

    @Override
    public synchronized long setBalance(UUID playerId, String playerName, long balance, String type) {
        long safeBalance = Math.max(0L, Math.min(maximumBalance, balance));
        ensurePlayer(playerId, playerName);
        return inTransaction(() -> {
            long before = getBalanceDirect(playerId);
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE players SET emc=?, last_name=?, updated_at=? WHERE uuid=?")) {
                statement.setLong(1, safeBalance);
                statement.setString(2, safeName(playerName));
                statement.setLong(3, now());
                statement.setString(4, playerId.toString());
                statement.executeUpdate();
            }
            logTransaction(playerId, type, null, 0, safeBalance - before);
            return safeBalance;
        });
    }

    @Override
    public synchronized long takeUpTo(UUID playerId, String playerName, long amount, String type) {
        requireNonNegative(amount);
        ensurePlayer(playerId, playerName);
        long balance = getBalance(playerId, playerName);
        return setBalance(playerId, playerName, Math.max(0L, balance - amount), type);
    }

    @Override
    public synchronized boolean isOpen() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException exception) {
            return false;
        }
    }

    @Override
    public String databasePath() {
        return databaseFile.getAbsolutePath();
    }

    @Override
    public synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // Disable must continue even if SQLite reports a close failure.
            } finally {
                connection = null;
            }
        }
    }

    private void ensurePlayer(UUID playerId, String playerName) {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO players(uuid, last_name, emc, updated_at)
                VALUES(?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET last_name=excluded.last_name, updated_at=excluded.updated_at
                """)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, safeName(playerName));
            statement.setLong(3, startingBalance);
            statement.setLong(4, now());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw failure("Failed to ensure player account", exception);
        }
    }

    private boolean insertLearned(UUID playerId, Material material) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR IGNORE INTO learned_items(uuid, material, learned_at) VALUES(?, ?, ?)")) {
            statement.setString(1, playerId.toString());
            statement.setString(2, material.name());
            statement.setLong(3, now());
            return statement.executeUpdate() == 1;
        }
    }

    private long updateBalance(UUID playerId, String playerName, long positiveDelta) throws SQLException {
        requireNonNegative(positiveDelta);
        long balance = getBalanceDirect(playerId);
        long updated = Math.addExact(balance, positiveDelta);
        if (updated > maximumBalance) {
            throw new SQLException("Maximum EMC balance exceeded");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE players SET emc=?, last_name=?, updated_at=? WHERE uuid=?")) {
            statement.setLong(1, updated);
            statement.setString(2, safeName(playerName));
            statement.setLong(3, now());
            statement.setString(4, playerId.toString());
            statement.executeUpdate();
        }
        return updated;
    }

    private long getBalanceDirect(UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT emc FROM players WHERE uuid=?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : startingBalance;
            }
        }
    }

    private void logTransaction(
            UUID playerId, String type, Material material, int itemCount, long emcDelta) throws SQLException {
        if (!transactionHistory) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO transactions(uuid, type, material, item_count, emc_delta, created_at)
                VALUES(?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, type);
            statement.setString(3, material == null ? null : material.name());
            statement.setInt(4, itemCount);
            statement.setLong(5, emcDelta);
            statement.setLong(6, now());
            statement.executeUpdate();
        }
    }

    private <T> T inTransaction(SqlCallable<T> operation) {
        ensureOpen();
        try {
            boolean oldAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = operation.call();
                connection.commit();
                return result;
            } catch (Throwable throwable) {
                connection.rollback();
                if (throwable instanceof SQLException sqlException) {
                    throw sqlException;
                }
                if (throwable instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new SQLException("Transaction failed", throwable);
            } finally {
                connection.setAutoCommit(oldAutoCommit);
            }
        } catch (SQLException exception) {
            throw failure("SQLite transaction failed", exception);
        }
    }

    private void ensureOpen() {
        if (!isOpen()) {
            throw new DataAccessException("SQLite connection is not open", null);
        }
    }

    private static void requireNonNegative(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("EMC amount must not be negative");
        }
    }

    private static String safeName(String name) {
        return name == null || name.isBlank() ? "unknown" : name.substring(0, Math.min(name.length(), 64));
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static DataAccessException failure(String message, Throwable cause) {
        return new DataAccessException(message, cause);
    }

    @FunctionalInterface
    private interface SqlCallable<T> {
        T call() throws Exception;
    }
}
