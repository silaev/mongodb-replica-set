package com.github.silaev.mongodb.replicaset.converter.impl;

import com.github.silaev.mongodb.replicaset.MongoDbReplicaSet;
import com.github.silaev.mongodb.replicaset.converter.Converter;
import com.github.silaev.mongodb.replicaset.converter.YmlConverter;
import com.github.silaev.mongodb.replicaset.exception.IncorrectUserInputException;
import com.github.silaev.mongodb.replicaset.model.ApplicationProperties;
import com.github.silaev.mongodb.replicaset.model.MongoReplicaSetProperties;
import com.github.silaev.mongodb.replicaset.model.PropertyContainer;
import com.github.silaev.mongodb.replicaset.model.UserInputProperties;
import com.github.silaev.mongodb.replicaset.service.ResourceService;
import com.github.silaev.mongodb.replicaset.service.impl.ResourceServiceImpl;
import com.github.silaev.mongodb.replicaset.util.StringUtils;
import lombok.AllArgsConstructor;
import lombok.val;

import java.util.Objects;
import java.util.Optional;

/**
 * Converts MongoReplicaSetInputProperties to ApplicationProperties
 * by evaluating properties located in different sources
 * (a yml file, a system property or a default value).
 *
 * @author Konstantin Silaev
 */
@AllArgsConstructor
public class UserInputToApplicationPropertiesConverter
    implements Converter<UserInputProperties, ApplicationProperties> {
    public static final int REPLICA_SET_NUMBER_DEFAULT = 1;
    public static final int AWAIT_NODE_INIT_ATTEMPTS = 29;
    public static final String MONGO_DOCKER_IMAGE_DEFAULT = "mongo:4.0.10";
    private static final Boolean ADD_ARBITER_DEFAULT = Boolean.FALSE;
    private static final boolean ENABLED_DEFAULT = true;
    private static final String YML_FORMAT = "yml";

    private final YmlConverter ymlConverter;
    private final ResourceService resourceService;

    public UserInputToApplicationPropertiesConverter() {
        this.ymlConverter = new YmlConverterImpl();
        this.resourceService = new ResourceServiceImpl();
    }

    /**
     * Constructs a new MongoReplicaSetProperties from a provided yml file.
     *
     * @param propertyFileName a yml file
     * @return a instance of MongoReplicaSetProperties
     */
    MongoReplicaSetProperties getFileProperties(
        final String propertyFileName
    ) {
        if ((Objects.isNull(propertyFileName)) || StringUtils.isBlank(propertyFileName)) {
            return new MongoReplicaSetProperties();
        }

        if (!YML_FORMAT.equals(
            propertyFileName.substring(propertyFileName.lastIndexOf('.') + 1))
        ) {
            throw new IllegalArgumentException(
                String.format("Incorrect file format: %s is not a %s file.", propertyFileName, YML_FORMAT)
            );
        }

        return ymlConverter.unmarshal(
            PropertyContainer.class,
            resourceService.getResourceIO(propertyFileName)
        ).getMongoReplicaSetProperties();
    }

    private String getMongoDockerImageName(
        final String mongoDockerImageNameInput,
        final String dockerImageNameFileProperty
    ) {

        return Optional.ofNullable(mongoDockerImageNameInput)
            .orElseGet(
                () -> Optional.ofNullable(System.getProperty("mongoReplicaSetProperties.mongoDockerImageName"))
                    .orElseGet(
                        () -> Optional.ofNullable(dockerImageNameFileProperty)
                            .orElse(MONGO_DOCKER_IMAGE_DEFAULT)
                    )
            );
    }

    private Boolean getEnabled(Boolean fileProperties) {
        return Optional.ofNullable(System.getProperty("mongoReplicaSetProperties.enabled"))
            .map(Boolean::valueOf)
            .orElseGet(
                () -> Optional.ofNullable(fileProperties)
                    .orElse(ENABLED_DEFAULT)
            );
    }

    public ApplicationProperties convert(
        final UserInputProperties inputProperties
    ) {
        validateInputProperties(inputProperties);

        val propertyFileName = inputProperties.getPropertyFileName();
        val fileProperties = getFileProperties(propertyFileName);

        val replicaSetNumber = Optional.ofNullable(inputProperties.getReplicaSetNumber())
            .orElse(UserInputToApplicationPropertiesConverter.REPLICA_SET_NUMBER_DEFAULT);
        val awaitNodeInitAttempts = Optional.ofNullable(inputProperties.getAwaitNodeInitAttempts())
            .orElse(UserInputToApplicationPropertiesConverter.AWAIT_NODE_INIT_ATTEMPTS);
        val isEnabled = getEnabled(fileProperties.getEnabled());
        val mongoDockerImageName = getMongoDockerImageName(
            inputProperties.getMongoDockerImageName(),
            fileProperties.getMongoDockerImageName()
        );
        val addArbiter = Optional.ofNullable(inputProperties.getAddArbiter())
            .orElse(UserInputToApplicationPropertiesConverter.ADD_ARBITER_DEFAULT);

        return ApplicationProperties.builder()
            .replicaSetNumber(replicaSetNumber)
            .addArbiter(addArbiter)
            .awaitNodeInitAttempts(awaitNodeInitAttempts)
            .mongoDockerImageName(mongoDockerImageName)
            .isEnabled(isEnabled)
            .build();
    }

    private void validateInputProperties(final UserInputProperties inputProperties) {
        Optional.ofNullable(inputProperties.getReplicaSetNumber())
            .ifPresent(n -> {
                    if (n < 1 || n > MongoDbReplicaSet.MAX_VOTING_MEMBERS) {
                        throw new IncorrectUserInputException(
                            String.format(
                                "Please, set replicaSetNumber more than 0 and less than %d",
                                MongoDbReplicaSet.MAX_VOTING_MEMBERS
                            )
                        );
                    }
                }
            );

        if (Objects.nonNull(inputProperties.getAddArbiter()) && Objects.nonNull(inputProperties.getReplicaSetNumber()) &&
            (inputProperties.getAddArbiter() && inputProperties.getReplicaSetNumber() == 1)) {
            throw new IncorrectUserInputException(
                "Adding an arbiter node is not supported for a single node replica set"
            );
        }
    }
}
