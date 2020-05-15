package com.github.silaev.mongodb.replicaset.integration.api;

import com.github.silaev.mongodb.replicaset.MongoDbReplicaSet;
import com.github.silaev.mongodb.replicaset.core.IntegrationTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@IntegrationTest
@Slf4j
class MongoDbReplicaSetMultiNodeApiITTest extends
    BaseMongoDbReplicaSetApiITTest {
    private static final int REPLICA_SET_NUMBER = 2;

    private static final MongoDbReplicaSet MONGO_REPLICA_SET = MongoDbReplicaSet.builder()
        .replicaSetNumber(REPLICA_SET_NUMBER)
        .addArbiter(true)
        .build();

    @BeforeAll
    static void setUpAll() {
        MONGO_REPLICA_SET.start();
    }

    @AfterAll
    static void tearDownAll() {
        MONGO_REPLICA_SET.stop();
    }

    @Test
    void shouldTestRsStatus() {
        super.shouldTestRsStatus(MONGO_REPLICA_SET, REPLICA_SET_NUMBER);
    }

    @Test
    void shouldTestVersionAndDockerImageName() {
        super.shouldTestVersionAndDockerImageName(MONGO_REPLICA_SET);
    }

    @Test
    void shouldTestEnabled() {
        super.shouldTestEnabled(MONGO_REPLICA_SET);
    }
}
