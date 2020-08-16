package com.github.silaev.mongodb.replicaset.converter.impl;

import com.github.silaev.mongodb.replicaset.converter.YmlConverter;
import com.github.silaev.mongodb.replicaset.exception.IncorrectUserInputException;
import com.github.silaev.mongodb.replicaset.model.MongoReplicaSetProperties;
import com.github.silaev.mongodb.replicaset.model.PropertyContainer;
import com.github.silaev.mongodb.replicaset.model.UserInputProperties;
import com.github.silaev.mongodb.replicaset.service.ResourceService;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Konstantin Silaev
 */
@ExtendWith(MockitoExtension.class)
class UserInputToApplicationPropertiesConverterTest {
    @Mock
    private YmlConverter ymlConverter;

    @Mock
    private ResourceService resourceService;

    @InjectMocks
    private UserInputToApplicationPropertiesConverter converter;

    static Stream<String> blankOrNullStrings() {
        return Stream.of("", "   ", null);
    }

    @Test
    void shouldGetFileProperties() {
        //GIVEN
        val propertyFileName = "propertyFileName.yml";
        val io = mock(InputStream.class);
        when(resourceService.getResourceIO(propertyFileName))
            .thenReturn(io);
        val propertyContainer = mock(PropertyContainer.class);
        when(ymlConverter.unmarshal(PropertyContainer.class, io))
            .thenReturn(propertyContainer);
        val mongoReplicaFileProperties = mock(MongoReplicaSetProperties.class);
        when(propertyContainer.getMongoReplicaSetProperties())
            .thenReturn(mongoReplicaFileProperties);
        when(mongoReplicaFileProperties.getEnabled()).thenReturn(Boolean.FALSE);

        //WHEN
        val mongoReplicaFilePropertiesActual =
            converter.getFileProperties(propertyFileName);

        //THEN
        assertFalse(mongoReplicaFilePropertiesActual.getEnabled());
    }

    @ParameterizedTest(name = "{index}: filePath: {0}")
    @MethodSource("blankOrNullStrings")
    void shouldGetDefaultsBecauseOfNullOrEmptyFile(String propertyFileName) {
        //GIVEN
        //propertyFileName

        //WHEN

        val mongoReplicaFilePropertiesActual =
            converter.getFileProperties(propertyFileName);

        //THEN
        assertNotNull(mongoReplicaFilePropertiesActual);
        assertNull(mongoReplicaFilePropertiesActual.getEnabled());
    }

    @Test
    void shouldNotEvaluateEnabledBecauseOfFileFormat() {
        //GIVEN
        val propertyFileName = "propertyFileName";

        //WHEN
        Executable executable =
            () -> converter.getFileProperties(propertyFileName);

        //THEN
        assertThrows(IllegalArgumentException.class, executable);
    }

    @Test
    void shouldNotConvertBecauseOfArbiterAndSingleNode() {
        //GIVEN
        val inputProperties = UserInputProperties.builder()
            .addArbiter(true)
            .replicaSetNumber(1)
            .build();

        //WHEN
        Executable executable = () -> converter.convert(inputProperties);

        //THEN
        assertThrows(IncorrectUserInputException.class, executable);
    }

    @Test
    void shouldNotConvertBecauseSlaveDelayNumberIsMoreThanReplicaSetNumber() {
        //GIVEN
        val inputProperties = UserInputProperties.builder()
            .slaveDelayNumber(6)
            .replicaSetNumber(4)
            .slaveDelayTimeout(5000)
            .build();

        //WHEN
        Executable executable = () -> converter.convert(inputProperties);

        //THEN
        assertThrows(IncorrectUserInputException.class, executable);
    }

    @Test
    void shouldNotConvertBecauseSlaveDelayTimeoutIsNotSet() {
        //GIVEN
        val inputProperties = UserInputProperties.builder()
            .slaveDelayNumber(6)
            .replicaSetNumber(4)
            .build();

        //WHEN
        Executable executable = () -> converter.convert(inputProperties);

        //THEN
        assertThrows(IncorrectUserInputException.class, executable);
    }
}
