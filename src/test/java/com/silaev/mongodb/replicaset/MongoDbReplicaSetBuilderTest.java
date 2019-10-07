package com.silaev.mongodb.replicaset;

import com.silaev.mongodb.replicaset.converter.impl.UserInputToApplicationPropertiesConverter;
import com.silaev.mongodb.replicaset.exception.IncorrectUserInputException;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Constructs a MongoReplicaSet via a builder and verifies
 * the result coming from different sources
 * (a system property, a yml file, default value).
 */
class MongoDbReplicaSetBuilderTest {

    private static final String DOCKER_IMAGE_NAME_PROPERTIES =
        "mongoReplicaSetProperties.mongoDockerImageName";
    private static final String ENABLED_PROPERTIES = "mongoReplicaSetProperties.enabled";

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
        System.clearProperty(DOCKER_IMAGE_NAME_PROPERTIES);
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
    void shouldGetMongoDockerImageNameFromSystemProperty() {
        //GIVEN
        val mongoDockerFile = "mongo:4.2.0";
        val mongoDockerImageNameProperty = DOCKER_IMAGE_NAME_PROPERTIES;
        System.setProperty(mongoDockerImageNameProperty, mongoDockerFile);

        //WHEN
        val replicaSet = MongoDbReplicaSet.builder().build();

        //THEN
        assertEquals(
            mongoDockerFile,
            replicaSet.getMongoDockerImageName()
        );

        //CLEAN UP
        System.clearProperty(mongoDockerImageNameProperty);
    }

    @Test
    void shouldGetMongoDockerImageNameFromPropertyFile() {
        //GIVEN
        System.clearProperty(DOCKER_IMAGE_NAME_PROPERTIES);
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
        Boolean isEnabledExpected = Boolean.FALSE;

        //WHEN
        val replicaSet = MongoDbReplicaSet.builder()
            .propertyFileName(propertyFileName)
            .build();

        //THEN
        assertEquals(
            isEnabledExpected,
            replicaSet.isEnabled()
        );
    }

    @Test
    void shouldIsEnabledFromPropertyFile() {
        //GIVEN
        val isEnabled = Boolean.TRUE;

        //WHEN
        val replicaSet = MongoDbReplicaSet.builder().build();

        //THEN
        assertEquals(isEnabled, replicaSet.isEnabled());
    }

    @Test
    void shouldGetDefaultAddArbiter() {
        //GIVEN
        val addArbiterExpected = Boolean.FALSE;

        //WHEN
        val replicaSet = MongoDbReplicaSet.builder().build();

        //THEN
        assertEquals(addArbiterExpected, replicaSet.getAddArbiter());
    }

    @Test
    void shouldGetEnabledFromSystemProperty() {
        //GIVEN
        val enabled = Boolean.FALSE;
        val enabledProperty = ENABLED_PROPERTIES;
        System.setProperty(enabledProperty, enabled.toString());

        //WHEN
        val replicaSet = MongoDbReplicaSet.builder().build();

        //THEN
        assertEquals(enabled, replicaSet.isEnabled());

        //CLEAN UP
        System.clearProperty(enabledProperty);
    }

    @Test
    void shouldGetEnabledFromPropertyFile() {
        //GIVEN
        System.clearProperty(ENABLED_PROPERTIES);
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
        System.clearProperty(ENABLED_PROPERTIES);
        val enabled = Boolean.TRUE;

        //WHEN
        val replicaSet = MongoDbReplicaSet.builder().build();

        //THEN
        assertEquals(enabled, replicaSet.isEnabled());
    }
}
