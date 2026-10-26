package com.bcp.workshop.auth;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class LegacyDatabase implements UserRepository {
    private static final String DB_URL = System.getenv().getOrDefault("AUTH_DB_URL", "jdbc:sqlite:./data/workshop.db");

    private final String databaseUrl;
    private final PasswordHashService passwordHashService;

    @Inject
    public LegacyDatabase(PasswordHashService passwordHashService) {
        this(DB_URL, passwordHashService);
    }

    LegacyDatabase(String databaseUrl, PasswordHashService passwordHashService) {
        this.databaseUrl = databaseUrl;
        this.passwordHashService = passwordHashService;
    }

    @PostConstruct
    void initialize() {
        try {
            Files.createDirectories(Path.of("data"));
            try (Connection connection = DriverManager.getConnection(databaseUrl)) {
                connection.setAutoCommit(false);
                try {
                    createSchema(connection);
                    migratePlaintextPasswords(connection);
                    insertSeedUser(connection, "student", "Workshop123!", "Workshop Student", "STUDENT");
                    insertSeedUser(connection, "trainer", "Trainer123!", "Workshop Trainer", "TRAINER");
                    connection.commit();
                } catch (SQLException | IllegalStateException error) {
                    rollback(connection, error);
                    throw error;
                }
            }
        } catch (java.io.IOException | SQLException error) {
            throw new IllegalStateException("Unable to initialize authentication database", error);
        }
    }

    @Override
    public Optional<UserRecord> findByUsername(String username) {
        String sql = "SELECT id, username, display_name, role, password FROM legacy_users WHERE username = ?";
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new UserRecord(
                        result.getLong("id"),
                        result.getString("username"),
                        result.getString("display_name"),
                        result.getString("role"),
                        result.getString("password")));
            }
        } catch (SQLException error) {
            throw new AuthenticationServiceException("Unable to query authentication database", error);
        }
    }

    private void createSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS legacy_users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)");
        }
    }

    private void migratePlaintextPasswords(Connection connection) throws SQLException {
        List<LegacyPassword> legacyPasswords = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT username, password FROM legacy_users")) {
            while (result.next()) {
                String password = result.getString("password");
                if (!passwordHashService.isEncodedHash(password)) {
                    legacyPasswords.add(new LegacyPassword(result.getString("username"), password));
                }
            }
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE legacy_users SET password = ? WHERE username = ?")) {
            for (LegacyPassword legacyPassword : legacyPasswords) {
                statement.setString(1, passwordHashService.hash(legacyPassword.password()));
                statement.setString(2, legacyPassword.username());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertSeedUser(
            Connection connection,
            String username,
            String password,
            String displayName,
            String role) throws SQLException {
        if (userExists(connection, username)) {
            return;
        }

        String sql = "INSERT INTO legacy_users(username,password,display_name,role) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, passwordHashService.hash(password));
            statement.setString(3, displayName);
            statement.setString(4, role);
            statement.executeUpdate();
        }
    }

    private boolean userExists(Connection connection, String username) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM legacy_users WHERE username = ?")) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private void rollback(Connection connection, Exception originalError) {
        try {
            connection.rollback();
        } catch (SQLException rollbackError) {
            originalError.addSuppressed(rollbackError);
        }
    }

    private record LegacyPassword(String username, String password) {}
}
