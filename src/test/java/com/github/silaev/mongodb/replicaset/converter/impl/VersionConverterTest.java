package com.github.silaev.mongodb.replicaset.converter.impl;


import lombok.val;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Konstantin Silaev
 */
class VersionConverterTest {
    private final VersionConverter versionConverter = new VersionConverter();

    private static Stream<String> incorrectVersions() {
        return Stream.of("3", "", null);
    }

    @ParameterizedTest(name = "{index}: version: {0}")
    @ValueSource(strings = {"3.6.14", "3.6"})
    void shouldConvert(String version) {
        //GIVEN
        String[] strings = version.split("\\.");
        val major = Integer.parseInt(strings[0]);
        val minor = Integer.parseInt(strings[1]);
        val patch = strings.length == 3 ? Integer.parseInt(strings[2]) : 0;

        //WHEN
        val mongoDbVersion = versionConverter.convert(version);

        //THEN
        assertEquals(major, mongoDbVersion.getMajor());
        assertEquals(minor, mongoDbVersion.getMinor());
        assertEquals(patch, mongoDbVersion.getPatch());
    }

    @ParameterizedTest(name = "{index}: version: {0}")
    @MethodSource("incorrectVersions")
    void shouldNotConvert(String version) {
        //GIVEN
        //version

        //WHEN
        Executable executable = () -> versionConverter.convert(version);

        //THEN
        assertThrows(IllegalArgumentException.class, executable);
    }
}
