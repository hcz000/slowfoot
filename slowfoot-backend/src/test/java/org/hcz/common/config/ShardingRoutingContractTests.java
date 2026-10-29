package org.hcz.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShardingRoutingContractTests {

    private static final String LOGICAL_JDBC_URL = "jdbc:shardingsphere:classpath:sharding-test.yml";
    private static final String PHYSICAL_JDBC_URL =
            "jdbc:h2:mem:sharding_contract;MODE=MYSQL;IGNORECASE=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1";

    private static final List<ShardRule> SHARD_RULES = List.of(
            new ShardRule("work_like", "work_id"),
            new ShardRule("work_share", "work_id"),
            new ShardRule("favorites_work", "user_id"),
            new ShardRule("user_notification", "user_id"),
            new ShardRule("work_view_history", "user_id"),
            new ShardRule("user_search_history", "user_id")
    );

    @Test
    void routesRowsToFourPhysicalTablesByModulo() throws Exception {
        Class.forName("org.apache.shardingsphere.driver.ShardingSphereDriver");
        try (Connection connection = DriverManager.getConnection(LOGICAL_JDBC_URL);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO test_event (id, user_id, payload) VALUES (?, ?, ?)")) {
            for (long userId = 0; userId < 8; userId++) {
                statement.setLong(1, userId + 100);
                statement.setLong(2, userId);
                statement.setString(3, "event-" + userId);
                assertEquals(1, statement.executeUpdate());
            }

            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT payload FROM test_event WHERE user_id = ?")) {
                query.setLong(1, 6L);
                try (ResultSet resultSet = query.executeQuery()) {
                    assertTrue(resultSet.next());
                    assertEquals("event-6", resultSet.getString("payload"));
                    assertFalse(resultSet.next());
                }
            }

            assertThrows(SQLException.class, () -> {
                try (Statement missingKey = connection.createStatement()) {
                    missingKey.executeUpdate("INSERT INTO test_event (id, payload) VALUES (999, 'invalid')");
                }
            });
        }

        try (Connection connection = DriverManager.getConnection(PHYSICAL_JDBC_URL, "sa", "")) {
            for (int shard = 0; shard < 4; shard++) {
                assertEquals(List.of((long) shard, (long) shard + 4), userIds(connection, shard));
            }
        }
    }

    @Test
    void productionConfigurationAndSchemaStayOnFourShards() throws IOException {
        String config = resource("/sharding.yml");
        String profile = resource("/application-sharding.yml");
        String schema = resource("/db/schema.sql");

        assertTrue(profile.contains("jdbc:shardingsphere:classpath:sharding.yml?placeholder-type=environment"));
        assertTrue(config.contains("type: DML_SHARDING_CONDITIONS"));
        assertFalse(config.contains("% 16"));

        for (ShardRule rule : SHARD_RULES) {
            assertTrue(config.contains("actualDataNodes: ds_0." + rule.table() + "_$->{0..3}"));
            assertTrue(config.contains("algorithm-expression: " + rule.table()
                    + "_$->{" + rule.shardingColumn() + " % 4}"));

            Pattern physicalTables = Pattern.compile(
                    "(?m)^CREATE TABLE IF NOT EXISTS `" + Pattern.quote(rule.table()) + "_[0-3]`");
            assertEquals(4, physicalTables.matcher(schema).results().count());

            Pattern legacyTable = Pattern.compile(
                    "(?m)^CREATE TABLE IF NOT EXISTS `" + Pattern.quote(rule.table()) + "`");
            assertFalse(legacyTable.matcher(schema).find());
        }
    }

    private List<Long> userIds(Connection connection, int shard) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT user_id FROM test_event_" + shard + " ORDER BY user_id")) {
            var result = new java.util.ArrayList<Long>();
            while (resultSet.next()) {
                result.add(resultSet.getLong(1));
            }
            return result;
        }
    }

    private String resource(String path) throws IOException {
        try (InputStream input = ShardingRoutingContractTests.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing classpath resource: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private record ShardRule(String table, String shardingColumn) {
    }
}
