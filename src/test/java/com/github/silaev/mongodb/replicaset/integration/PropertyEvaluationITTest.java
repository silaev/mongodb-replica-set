package com.github.silaev.mongodb.replicaset.integration;

import com.github.silaev.mongodb.replicaset.MongoDbReplicaSet;
import com.github.silaev.mongodb.replicaset.core.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

@IntegrationTest
class PropertyEvaluationITTest {
    private static final MongoDbReplicaSet MONGO_REPLICA_SET = MongoDbReplicaSet.builder()
        .replicaSetNumber(1)
        .awaitNodeInitAttempts(30)
        .propertyFileName("enabled-false.yml")
        .build();

    @Test
    void shouldGetProperties() {
        // GIVEN
        //MONGO_REPLICA_SET

        // WHEN
        // MONGO_REPLICA_SET is initialized

        // THEN
        assertFalse(MONGO_REPLICA_SET.isEnabled());
    }
}
