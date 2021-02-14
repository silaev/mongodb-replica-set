package com.github.silaev.mongodb.replicaset.converter.impl;

import com.github.silaev.mongodb.replicaset.service.ResourceService;
import com.github.silaev.mongodb.replicaset.service.impl.ResourceServiceImpl;
import lombok.val;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class StringToMongoRsStatusConverterTest {
    private final StringToMongoRsStatusConverter converter = new StringToMongoRsStatusConverter(
        new YmlConverterImpl(),
        new VersionConverter()
    );
    private final ResourceService resourceService = new ResourceServiceImpl();

    @ParameterizedTest(name = "shouldConvert: {index}, fileName: {0}")
    @CsvSource(value = {
        "shell-output/rs-status.txt, 1, 5",
        "shell-output/rs-status-framed.txt, 1, 3",
        "shell-output/rs-status-plain.txt, 1, 0",
        "shell-output/timeout-exceeds.txt, 0, 0"
    })
    void shouldConvert(final String fileName, final int status, final int membersNumber) {
        // GIVEN
        val rsStatus = resourceService.getString(resourceService.getResourceIO(fileName));

        // THEN
        val mongoRsStatusActual = converter.convert(rsStatus);

        // WHEN
        assertThat(mongoRsStatusActual).isNotNull();
        assertThat(mongoRsStatusActual.getStatus()).isEqualTo(status);
        val members = mongoRsStatusActual.getMembers();
        assertThat(members.size()).isEqualTo(membersNumber);
    }
}
