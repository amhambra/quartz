package org.quartz.examples.example13;

import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class WriterJob implements Job {

    private static Logger _log  = LoggerFactory.getLogger(SimpleRecoveryJob.class);

    private static final String COUNT = "count";

    private static final String JDBC_URL = "jdbc:sqlserver://n01.harryboing.de:1433;databaseName=quartz252;encrypt=true;trustServerCertificate=true;user=SA;password=Coconet123;";

    private static final String DB = "MSSQL";

    private String sqlLockTimeout;

    public WriterJob() {
        /*
        if (DB.equals("MSSQL")) {
            sqlLockTimeout = "SET LOCK_TIMEOUT 2000";
        } else if (DB.equals("PG")) {
            sqlLockTimeout = "SET LOCK_TIMEOUT=2000";
        }
        */
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String sql = "UPDATE DemoInventory SET Quantity = 99 WHERE ItemId = 1";

        JobKey jobKey = context.getJobDetail().getKey();

        if (context.isRecovering()) {
            _log.info("WriterJob(): {} RECOVERING", jobKey);
        } else {
            _log.info("WriterJob(): {} STARTING", jobKey);
        }

        try (Connection conn = DriverManager.getConnection(JDBC_URL)) {
            // start transaction
            conn.setAutoCommit(false);

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.executeUpdate();

                _log.info("WriterJob(): {} DO [{}]", jobKey, "Row updated, holding transaction for 10 seconds...");

                // Sleep to hold the lock, giving the Reader Job time to fire and get blocked
                Thread.sleep(10000);

                conn.commit();

                _log.info("WriterJob(): {} DA [{}]", jobKey, "Transaction committed");
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            _log.error("WriterJob(): {} ER [{}]", jobKey, e, e);

            throw new JobExecutionException("Writer Job failed", e);
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

        _log.info("WriterJob() {} DONE [Execution #{}]", jobKey, count);
    }
}