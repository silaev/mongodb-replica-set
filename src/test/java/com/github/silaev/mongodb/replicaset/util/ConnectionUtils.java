package com.github.silaev.mongodb.replicaset.util;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.WriteConcern;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

/**
 * @author Konstantin Silaev on 3/19/2020
 */
public class ConnectionUtils {
    private ConnectionUtils() {
    }

    /**
     * Used for setting timeouts to fail-fast behaviour.
     *
     * @param mongoRsUrlPrimary a connection string
     * @return MongoClientSettings with timeouts set
     */
    @NotNull
    public static MongoClientSettings getMongoClientSettingsWithTimeout(
        final String mongoRsUrlPrimary,
        final WriteConcern writeConcern,
        final int timeout
    ) {
        final ConnectionString connectionString = new ConnectionString(mongoRsUrlPrimary);
        return MongoClientSettings.builder()
            .writeConcern(writeConcern.withWTimeout(5, TimeUnit.SECONDS))
            //.retryWrites(false)
            //.retryReads(false)
            .applyToClusterSettings(c -> c.serverSelectionTimeout(timeout, TimeUnit.SECONDS))
            .applyConnectionString(connectionString)
            .applyToSocketSettings(
                b -> b
                    .readTimeout(timeout, TimeUnit.SECONDS)
                    .connectTimeout(timeout, TimeUnit.SECONDS)

            ).build();
    }

    @NotNull
    public static MongoClientSettings getMongoClientSettingsWithTimeout(
        final String mongoRsUrlPrimary
    ) {
        return getMongoClientSettingsWithTimeout(mongoRsUrlPrimary, WriteConcern.W1, 5);
    }
}
