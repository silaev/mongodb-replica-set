package com.silaev.mongodb.replicaset.model;

import lombok.Builder;
import lombok.Getter;

/**
 * Immutable class property class to evaluate them from different sources.
 *
 * @author Konstantin Silaev
 */
@Getter
@Builder
public class ApplicationProperties {
    private final int replicaSetNumber;
    private final int awaitNodeInitAttempts;
    private final String mongoDockerImageName;
    private final boolean isEnabled;
    private final boolean addArbiter;
}
