package com.github.silaev.mongodb.replicaset;

import com.github.silaev.mongodb.replicaset.converter.impl.UserInputToApplicationPropertiesConverter;
import com.github.silaev.mongodb.replicaset.exception.IncorrectUserInputException;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Constructs a MongoReplicaSet via a builder and verifies
 * the result coming from different sources
 * (a system property, a yml file, default value).
 */
class MongoDbReplicaSetBuilderTest {

    private static final String PREFIX = "mongoReplicaSetProperties.";
    private static final String DOCKER_IMAGE_NAME_PROPERTIES = PREFIX + "mongoDockerImageName";
    private static final String ENABLED_PROPERTIES = PREFIX + "enabled";
    private static final String USE_HOST_DOCKER_INTERNAL = PREFIX + "useHostDockerInternal";

    @Test
    void shouldGetDefaultReplicaSetNumber() {
        //GIVEN
        val replicaNumberExpected =
            UserInputToApplicationPropertiesConverter.REPLICA_SET_NUMBER_DEFAULT;

        //WHEN
        val replicaSet = MongoDbReplicaSet.builder().build();

        //THEN
        assertEquals(
            replicaNumberExpected,
            replicaSet.getReplicaSetNumber()
        );
    }

    @ParameterizedTest(name = "{index}: replicaSetNumber: {0}")
    @ValueSource(ints = {0, MongoDbReplicaSet.MAX_VOTING_MEMBERS + 1})
    void shouldThrowExceptionBecauseOfIncorrectReplicaSetNumber(
        final int replicaSetNumber
    ) {
        //GIVEN
        //replicaSetNumber

        //WHEN
        Executable executable =
            () -> MongoDbReplicaSet.builder().replicaSetNumber(replicaSetNumber).build();

        //THEN
        assertThrows(IncorrectUserInputException.class, executable);
    }

    @Test
    void shouldGetDefaultAwaitNodeInitAttempts() {
        //GIVEN
        val awaitExpected =
            UserInputToApplicationPropertiesConverter.AWAIT_NODE_INIT_ATTEMPTS;

        //WHEN
        val replicaSet = MongoDbReplicaSet.builder().build();

        //THEN
        assertEquals(
            awaitExpected,
            replicaSet.getAwaitNodeInitAttempts()
        );
    }

    @Test
    void shouldGetDefaultMongoDockerImageName() {
        //GIVEN
        val mongoDockerImageExpected =
            UserInputToApplicationPropertiesConverter.MONGO_DOCKER_IMAGE_DEFAULT;

        //WHEN
        val replicaSet = MongoDbReplicaSet.builder().build();

        //THEN
        assertEquals(
            mongoDockerImageExpected,
            replicaSet.getMongoDockerImageName()
        );
    }

    @Test
    void shouldGetUseHostDockerInternalFromProperty() {
        //GIVEN
        try {
            System.setProperty(USE_HOST_DOCKER_INTERNAL, "true");

            //WHEN
            val replicaSet = MongoDbReplicaSet.builder().build();

            //THEN
            assertThat(replicaSet.getUseHostDockerInternal()).isTrue();
        } finally {
            System.clearProperty(USE_HOST_DOCKER_INTERNAL);
        }
    }

    @Test
    void shouldGetUseHostDockerInternalFromInput() {
        //GIVEN
        try {
            System.setProperty(USE_HOST_DOCKER_INTERNAL, "false");

            //WHEN
            final MongoDbReplicaSet replicaSet = MongoDbReplicaSet.builder().useHostDockerInternal(true).build();

            //THEN
            assertThat(replicaSet.getUseHostDockerInternal()).isTrue();
        } finally {
            System.clearProperty(USE_HOST_DOCKER_INTERNAL);
        }
    }

    @Test
    void shouldGetUseHostDockerInternalDefault() {
        //GIVEN
        try {
            System.clearProperty(USE_HOST_DOCKER_INTERNAL);

            //WHEN
            val replicaSet = MongoDbReplicaSet.builder().build();

            //THEN
            assertThat(replicaSet.getUseHostDockerInternal()).isEqualTo(
                UserInputToApplicationPropertiesConverter.USE_HOST_DOCKER_INTERNAL_DEFAULT
            );
        } finally {
            System.clearProperty(USE_HOST_DOCKER_INTERNAL);
        }
    }

    @Test
    void shouldGetMongoDockerImageNameFromSystemProperty() {
        //GIVEN
        try {
            val mongoDockerFile = "mongo:4.2.0";
            System.setProperty(DOCKER_IMAGE_NAME_PROPERTIES, mongoDockerFile);

            //WHEN
            val replicaSet = MongoDbReplicaSet.builder().build();

            //THEN
            assertEquals(
                mongoDockerFile,
                replicaSet.getMongoDockerImageName()
            );
        } finally {
            System.clearProperty(DOCKER_IMAGE_NAME_PROPERTIES);
        }
    }

    @Test
    void shouldGetMongoDockerImageNameFromPropertyFile() {
        //GIVEN
        val propertyFileName = "enabled-false.yml";
        val mongoDockerFile = "mongo:4.1.13";

        //WHEN
        val replicaSet = MongoDbReplicaSet.builder()
            .propertyFileName(propertyFileName)
            .build();

        //THEN
        assertEquals(
            mongoDockerFile,
            replicaSet.getMongoDockerImageName()
        );
    }

    @Test
    void shouldGetDefaultIsEnabled() {
        //GIVEN
        val propertyFileName = "enabled-false.yml";

        //WHEN
        val replicaSet = MongoDbReplicaSet.builder()
            .propertyFileName(propertyFileName)
            .build();

        //THEN
        assertThat(replicaSet.isEnabled()).isFalse();
    }

    @Test
    void shouldIsEnabledFromPropertyFile() {
        //GIVEN

        //WHEN
        val replicaSet = MongoDbReplicaSet.builder().build();

        //THEN
        assertThat(replicaSet.isEnabled()).isTrue();
    }

    @Test
    void shouldGetDefaultAddArbiter() {
        //GIVEN

        //WHEN
        val replicaSet = MongoDbReplicaSet.builder().build();

        //THEN
        assertThat(replicaSet.getAddArbiter()).isFalse();
    }

    @Test
    void shouldGetEnabledFromSystemProperty() {
        //GIVEN
        try {
            val enabled = Boolean.FALSE;
            System.setProperty(ENABLED_PROPERTIES, enabled.toString());

            //WHEN
            val replicaSet = MongoDbReplicaSet.builder().build();

            //THEN
            assertEquals(enabled, replicaSet.isEnabled());

        } finally {
            System.clearProperty(ENABLED_PROPERTIES);
        }
    }

    @Test
    void shouldGetEnabledFromPropertyFile() {
        //GIVEN
        val enabled = Boolean.TRUE;
        val propertyFileName = "enabled-true.yml";

        //WHEN
        val replicaSet = MongoDbReplicaSet.builder()
            .propertyFileName(propertyFileName)
            .build();

        //THEN
        assertEquals(enabled, replicaSet.isEnabled());
    }

    @Test
    void shouldGetDefaultEnabled() {
        //GIVEN
        val enabled = Boolean.TRUE;

        //WHEN
        val replicaSet = MongoDbReplicaSet.builder().build();

        //THEN
        assertEquals(enabled, replicaSet.isEnabled());
    }

    @Test
    void shouldThrowExceptionBecauseOfSlaveDelayOnSingleNode() {
        //GIVEN
        //replicaSetNumber

        //WHEN
        Executable executable =
            () -> MongoDbReplicaSet.builder().replicaSetNumber(1).slaveDelayTimeout(60).build();

        //THEN
        assertThrows(IncorrectUserInputException.class, executable);
    }
}
