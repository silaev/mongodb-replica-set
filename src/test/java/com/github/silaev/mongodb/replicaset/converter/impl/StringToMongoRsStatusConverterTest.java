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
        "rs-status.txt, 5",
        "rs-status-framed.txt, 3",
        "rs-status-plain.txt, 0"
    })
    void shouldConvert(final String fileName, final int membersNumber) {
        // GIVEN
        val rsStatus = resourceService.getString(resourceService.getResourceIO(fileName));

        // THEN
        val mongoRsStatusActual = converter.convert(rsStatus);

        // WHEN
        assertThat(mongoRsStatusActual).isNotNull();
        assertThat(mongoRsStatusActual.getStatus()).isEqualTo(1);
        val members = mongoRsStatusActual.getMembers();
        assertThat(members.size()).isEqualTo(membersNumber);
    }
}
