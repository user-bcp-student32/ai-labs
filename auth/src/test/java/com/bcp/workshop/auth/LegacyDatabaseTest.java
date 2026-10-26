package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyDatabaseTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void canBeConstructedByTheContainerWithSharedPasswordHasher() {
        assertNotNull(new LegacyDatabase(new PasswordHashService()));
    }

    @Test
    void migratesLegacyPlaintextPasswordAndKeepsLoginWorking() throws Exception {
        String databaseUrl = "jdbc:sqlite:" + temporaryDirectory.resolve("legacy.db").toAbsolutePath();
        String username = "legacy-student";
        String password = "legacy-test-password";
        createLegacyDatabase(databaseUrl, username, password);

        PasswordHashService passwordHashService = new PasswordHashService();
        LegacyDatabase database = new LegacyDatabase(databaseUrl, passwordHashService);
        database.initialize();
        database.initialize();

        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT password FROM legacy_users WHERE username = ?")) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                String storedHash = result.getString("password");
                assertTrue(passwordHashService.isEncodedHash(storedHash));
                assertTrue(passwordHashService.matches(password, storedHash));
            }
        }

        UserRecord user = database.findByUsername(username).orElseThrow();
        assertEquals(username, user.username());
        assertTrue(passwordHashService.matches(password, user.passwordHash()));
        assertTrue(database.findByUsername("missing-user").isEmpty());
    }

    @Test
    void reportsDatabaseErrorsInsteadOfReturningNoUser() throws Exception {
        Class.forName("org.sqlite.JDBC");
        String databaseUrl = "jdbc:sqlite:" + temporaryDirectory.resolve("broken.db").toAbsolutePath();
        PasswordHashService passwordHashService = new PasswordHashService();
        LegacyDatabase database = new LegacyDatabase(databaseUrl, passwordHashService);
        database.initialize();

        try (Connection connection = DriverManager.getConnection(databaseUrl);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE legacy_users");
        }

        assertThrows(AuthenticationServiceException.class, () -> database.findByUsername("student"));
    }

    private void createLegacyDatabase(String databaseUrl, String username, String password)
            throws ClassNotFoundException, java.sql.SQLException {
        Class.forName("org.sqlite.JDBC");
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement statement = connection.prepareStatement(
                     "CREATE TABLE legacy_users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)")) {
            statement.execute();
        }
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO legacy_users(username,password,display_name,role) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, username);
            statement.setString(2, password);
            statement.setString(3, "Legacy Student");
            statement.setString(4, "STUDENT");
            statement.executeUpdate();
        }
    }
}
