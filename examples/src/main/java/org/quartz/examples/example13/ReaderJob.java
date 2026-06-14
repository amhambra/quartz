package org.quartz.examples.example13;

import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ReaderJob implements Job {

    private static Logger _log  = LoggerFactory.getLogger(SimpleRecoveryJob.class);

    private static final String COUNT = "count";

    private static final String JDBC_URL = "jdbc:sqlserver://n01.harryboing.de:1433;databaseName=quartz252;encrypt=true;trustServerCertificate=true;user=SA;password=Coconet123;";
    private DatabaseIdentifier.DatabaseType JdbcDatabaseType = DatabaseIdentifier.DatabaseType.EMPTY;

    private String sqlQuery = null;

    private String sqlLockTimeout;

    private int intLockTimeoutSeconds = 1;

    private boolean initDatabaseSql;

    @Override
    public String toString() {
        return "ReaderJob{" +
                "JdbcDatabaseType=" + JdbcDatabaseType +
                ", sqlQuery='" + sqlQuery + '\'' +
                ", sqlLockTimeout='" + sqlLockTimeout + '\'' +
                ", intLockTimeoutSeconds=" + intLockTimeoutSeconds +
                ", initDatabaseSql=" + initDatabaseSql +
                '}';
    }

    public ReaderJob() {
        initDatabaseSql = false;
    }

    private boolean initDatabaseSql(Connection conn) {
        boolean result = false;
        if (conn == null) {
            return result;
        }

        JdbcDatabaseType = DatabaseIdentifier.identifyDatabase(conn);

        if (JdbcDatabaseType == null || JdbcDatabaseType == DatabaseIdentifier.DatabaseType.EMPTY || JdbcDatabaseType == DatabaseIdentifier.DatabaseType.NONE || JdbcDatabaseType == DatabaseIdentifier.DatabaseType.UNKNOWN) {
            throw new RuntimeException("databaseType not detectable! [JdbcDatabaseType:" + JdbcDatabaseType + "]");
        }

        String valueLockTimeoutSeconds = "";
        String valueLockTimeoutMSeconds = "";

        if (intLockTimeoutSeconds > 0) {
            valueLockTimeoutSeconds = String.valueOf(intLockTimeoutSeconds);
            valueLockTimeoutMSeconds = String.valueOf(intLockTimeoutSeconds * 1000);
        }

        sqlQuery = "SELECT Quantity FROM DemoInventory WHERE ItemId = 1";

        if (JdbcDatabaseType == DatabaseIdentifier.DatabaseType.MSSQL) {
            sqlLockTimeout = "SET LOCK_TIMEOUT " + valueLockTimeoutMSeconds;
        } else if (JdbcDatabaseType == DatabaseIdentifier.DatabaseType.PG) {
            sqlLockTimeout = "SET LOCK_TIMEOUT=" + valueLockTimeoutMSeconds;
        } else if (JdbcDatabaseType == DatabaseIdentifier.DatabaseType.MARIADB) {
            sqlLockTimeout = "SET session innodb_lock_wait_timeout = " + valueLockTimeoutSeconds;
        } else if (JdbcDatabaseType == DatabaseIdentifier.DatabaseType.ORACLE) {
            // Oracle Architecture: Readers do not block Writers, and Writers do not block Readers.
            // Oracle uses multi-version concurrency control (MVCC). If Session A is updating a row,
            // Session B running a plain SELECT will simply read the old version from the Undo logs.
            // It never gets blocked.
            //
            // To mimic BLOCKING select: Even if you don't actually plan to issue an UPDATE statement afterward
            //
            // Appending " FOR UPDATE WAIT 2" is Oracle's syntax to say:
            //  "Check if this row is currently locked by a writer. If it is, wait 2 seconds for them to commit. If they don't, throw an ORA-03006 error immediately."
            // If you use this method, just make sure to finish your transaction with a COMMIT or ROLLBACK right after the SELECT to release the temporary lock you just acquired.

            sqlLockTimeout = null;
            sqlQuery += " FOR UPDATE WAIT " + valueLockTimeoutSeconds;
        }

        result = true;

        return result;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        int intQuantity = -1;

        JobKey jobKey = context.getJobDetail().getKey();

        if (context.isRecovering()) {
            _log.info("ReaderJob(): {} RECOVERING [{}]", jobKey, this);
        } else {
            _log.info("ReaderJob(): {} STARTING [{}]", jobKey, this);
        }

        try (Connection conn = DriverManager.getConnection(JDBC_URL)) {
            initDatabaseSql = initDatabaseSql(conn);

            Statement stmt = conn.createStatement();

            // Set lock timeout to 2000 milliseconds (2 seconds)
            _log.info("ReaderJob(): {} DO [{}] [{}]", jobKey, sqlLockTimeout, this);
            if (sqlLockTimeout != null && !sqlLockTimeout.isEmpty()) {
                stmt.execute(sqlLockTimeout);
            }

            _log.info("ReaderJob(): {} DO [{}] [{}] [{}]", jobKey, sqlQuery, "read row...", this);
            try (ResultSet rs = stmt.executeQuery(sqlQuery)) {
                if (rs.next()) {
                    intQuantity = rs.getInt("Quantity");
                    _log.info("ReaderJob(): {} OK [Quantity:{}] [{}]", jobKey, intQuantity, "read row...");
                }
            } catch (SQLException ex) {
                // 1222 is the SQL Server error code for Lock Request Timeout
                if (ex.getErrorCode() == 1222) {
                    _log.warn("ReaderJob(): {} ER [Quantity:{}] [{}]", jobKey, intQuantity, "read row... EXCEPTION! BLOCK=Lock Request Timeout!", ex);
                } else {
                    _log.error("ReaderJob(): {} ER [Quantity:{}] [{}]", jobKey, intQuantity, "read row... EXCEPTION!", ex);
                }
            } catch (Exception e) {
                _log.error("ReaderJob(): {} ER [Quantity:{}] [{}]", jobKey, intQuantity, "read row... EXCEPTION!", e);

                throw new JobExecutionException("Reader Job failed", e);
            }
        } catch (Exception e) {
            _log.error("ReaderJob(): {} ER [Quantity:{}] [{}]", jobKey, intQuantity, "read row... EXCEPTION!", e);

            throw new JobExecutionException("Reader Job failed", e);
        }

        JobDataMap data = context.getJobDetail().getJobDataMap();
        int count;
        if (data.containsKey(COUNT)) {
            count = data.getInt(COUNT);
        } else {
            count = 0;
        }
        count++;
        data.put(COUNT, count);

        _log.info("ReaderJob() {} DONE [Execution #{}]", jobKey, count);
    }
}