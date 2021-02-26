package com.github.silaev.mongodb.replicaset.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Basic input properties coming from MongoReplicaSet's builder.
 *
 * @author Konstantin Silaev
 */
@Builder
@Getter
public final class UserInputProperties {
    private final Integer replicaSetNumber;
    private final Integer awaitNodeInitAttempts;
    private final String mongoDockerImageName;
    private final Boolean addArbiter;
    private final Boolean addToxiproxy;
    private final Integer slaveDelayTimeout;
    private final String propertyFileName;
    private final Integer slaveDelayNumber;
    private final Boolean useHostDockerInternal;
    private final List<String> commandLineOptions;
}
