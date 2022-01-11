package com.github.silaev.mongodb.replicaset.integration.transaction;

import com.github.silaev.mongodb.replicaset.MongoDbReplicaSet;
import com.github.silaev.mongodb.replicaset.core.EnabledIfSystemPropertyEnabledByDefault;
import com.github.silaev.mongodb.replicaset.core.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@IntegrationTest
@EnabledIfSystemPropertyEnabledByDefault(
    named = "mongoReplicaSetProperties.mongoDockerImageName",
    matches = "^mongo:4.*|^mongo:5.*"
)
class MongoDbReplicaSetTransactionMultiNodeITTest extends
    BaseMongoDbReplicaSetTransactionITTest {
    private static final int REPLICA_SET_NUMBER = 3;

    private static final MongoDbReplicaSet MONGO_REPLICA_SET = MongoDbReplicaSet.builder()
        .replicaSetNumber(REPLICA_SET_NUMBER)
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
    void shouldExecuteTransactions() {
        super.shouldExecuteTransactions(MONGO_REPLICA_SET);
    }
}
