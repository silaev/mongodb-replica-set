package com.silaev.mongodb.replicaset.model;

import lombok.Builder;
import lombok.Getter;

/**
 * Basic input properties coming from MongoReplicaSet's builder.
 *
 * @author Konstantin Silaev
 */
@Builder
@Getter
public class UserInputProperties {
    private final Integer replicaSetNumber;
    private final Integer awaitNodeInitAttempts;
    private final String mongoDockerImageName;
    private final Boolean addArbiter;
    private final String propertyFileName;
}
