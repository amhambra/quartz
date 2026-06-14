package org.quartz.examples.example13;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

public class DatabaseIdentifier {

    public enum DatabaseType {
        EMPTY,
        NONE,
        UNKNOWN,
        ORACLE,
        PG,
        MSSQL,
        MARIADB
    }

    public static DatabaseType identifyDatabase(Connection connection) {
        DatabaseType result = DatabaseType.NONE;

        if (connection == null) {
            return result;
        }

        try {
            // Retrieve the metadata from the active connection
            DatabaseMetaData metaData = connection.getMetaData();

            // Get the official product name (e.g., "PostgreSQL", "Oracle", etc.)
            String dbProductName = metaData.getDatabaseProductName();
            String dbVersion = metaData.getDatabaseProductVersion();

            System.out.println("Connected to: " + dbProductName);
            System.out.println("Version: " + dbVersion);

            // Normalize the string to make branching easy
            String dbType = dbProductName.toLowerCase();

            if (dbType.contains("microsoft sql server")) {
                result = DatabaseType.MSSQL;

                System.out.println("-> Applying SQL Server configuration logic.");
                // e.g., execute "SET LOCK_TIMEOUT 2000"

            } else if (dbType.contains("postgresql")) {
                result = DatabaseType.PG;

                System.out.println("-> Applying PostgreSQL configuration logic.");
                // e.g., execute "SET lock_timeout = 2000"

            } else if (dbType.contains("oracle")) {
                result = DatabaseType.ORACLE;

                System.out.println("-> Applying Oracle configuration logic.");

            } else if (dbType.contains("mariadb") || dbType.contains("mysql")) {
                result = DatabaseType.MARIADB;

                System.out.println("-> Applying MariaDB/MySQL configuration logic.");
                // e.g., execute "SET session innodb_lock_wait_timeout = 2"

            } else {
                result = DatabaseType.UNKNOWN;

                System.out.println("-> Unknown DBMS: " + dbProductName);
            }

        } catch (SQLException e) {
            System.err.println("Failed to retrieve database metadata: " + e.getMessage());
        }

        return result;
    }
}