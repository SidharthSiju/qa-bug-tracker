package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Replaces the inline single-column UNIQUE constraint on change_request.cr_number
 * (created unnamed in V1) with a composite UNIQUE constraint on (cr_number, cr_name).
 * Dialect-agnostic so it works on both H2 (dev) and PostgreSQL (prod), which share db/migration.
 */
public class V8__ChangeCrUniqueConstraint extends BaseJavaMigration {

    private static final String TABLE = "change_request";
    private static final String COMPOSITE_CONSTRAINT = "uq_change_request_cr_number_name";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        String schema = schemaOf(connection);

        String legacy = findSingleColumnUnique(connection, schema, "cr_number");
        if (legacy != null && !legacy.equalsIgnoreCase(COMPOSITE_CONSTRAINT)) {
            execute(connection, "ALTER TABLE " + qualified(schema) + " DROP CONSTRAINT " + quote(legacy));
        }

        if (!uniqueConstraintExists(connection, schema, COMPOSITE_CONSTRAINT)) {
            execute(connection, "ALTER TABLE " + qualified(schema)
                    + " ADD CONSTRAINT " + COMPOSITE_CONSTRAINT + " UNIQUE (cr_number, cr_name)");
        }
    }

    private String schemaOf(Connection connection) {
        try {
            String schema = connection.getSchema();
            return schema == null || schema.isBlank() ? "crqa" : schema;
        } catch (SQLException ex) {
            return "crqa";
        }
    }

    private String qualified(String schema) {
        return schema == null || schema.isBlank() ? TABLE : schema + "." + TABLE;
    }

    private String findSingleColumnUnique(Connection connection, String schema, String column) throws SQLException {
        String sql = "SELECT tc.constraint_name "
                + "FROM information_schema.table_constraints tc "
                + "JOIN information_schema.key_column_usage kcu "
                + "  ON kcu.constraint_name = tc.constraint_name "
                + " AND kcu.table_schema = tc.table_schema "
                + " AND kcu.table_name = tc.table_name "
                + "WHERE tc.constraint_type = 'UNIQUE' AND lower(tc.table_name) = lower(?) "
                + "  AND lower(tc.table_schema) = lower(?) "
                + "GROUP BY tc.constraint_name "
                + "HAVING count(*) = 1 AND max(lower(kcu.column_name)) = lower(?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, TABLE);
            statement.setString(2, schema);
            statement.setString(3, column);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private boolean uniqueConstraintExists(Connection connection, String schema, String name) throws SQLException {
        String sql = "SELECT count(*) FROM information_schema.table_constraints "
                + "WHERE constraint_type = 'UNIQUE' AND lower(table_name) = lower(?) "
                + "AND lower(table_schema) = lower(?) AND lower(constraint_name) = lower(?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, TABLE);
            statement.setString(2, schema);
            statement.setString(3, name);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
