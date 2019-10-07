package com.silaev.mongodb.replicaset.integration;

import com.silaev.mongodb.replicaset.MongoDbReplicaSet;
import com.silaev.mongodb.replicaset.core.IntegrationTest;
import com.silaev.mongodb.replicaset.exception.IncorrectUserInputException;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Konstantin Silaev on 10/4/2019
 */
@IntegrationTest
class VersionSupportTest {
    @Test
    void shouldNotValidateVersion() {
        //GIVEN
        val replicaSet = MongoDbReplicaSet.builder()
            .mongoDockerImageName("mongo:3.4.22")
            .build();

        //WHEN
        Executable executable = replicaSet::start;

        //THEN
        assertThrows(IncorrectUserInputException.class, executable);
    }
}
