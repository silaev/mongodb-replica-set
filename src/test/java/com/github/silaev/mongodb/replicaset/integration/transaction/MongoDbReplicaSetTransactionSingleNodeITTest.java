package com.github.silaev.mongodb.replicaset.integration.transaction;

import com.github.silaev.mongodb.replicaset.MongoDbReplicaSet;
import com.github.silaev.mongodb.replicaset.core.EnabledIfSystemPropertyExistsAndMatches;
import com.github.silaev.mongodb.replicaset.core.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@IntegrationTest
@Execution(ExecutionMode.CONCURRENT)
@EnabledIfSystemPropertyExistsAndMatches(
    named = "mongoReplicaSetProperties.mongoDockerImageName",
    matches = "^mongo:4.*"
)
class MongoDbReplicaSetTransactionSingleNodeITTest extends
    BaseMongoDbReplicaSetTransactionITTest {
    private static final int REPLICA_SET_NUMBER = 1;

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
