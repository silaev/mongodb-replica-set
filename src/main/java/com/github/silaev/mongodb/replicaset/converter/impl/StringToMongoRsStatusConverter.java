package com.github.silaev.mongodb.replicaset.converter.impl;

import com.github.silaev.mongodb.replicaset.converter.Converter;
import com.github.silaev.mongodb.replicaset.converter.YmlConverter;
import com.github.silaev.mongodb.replicaset.model.MongoNode;
import com.github.silaev.mongodb.replicaset.model.MongoNodeMutable;
import com.github.silaev.mongodb.replicaset.model.MongoRsStatus;
import com.github.silaev.mongodb.replicaset.model.MongoRsStatusMutable;
import com.github.silaev.mongodb.replicaset.model.ReplicaSetMemberState;
import com.github.silaev.mongodb.replicaset.util.StringUtils;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Converts a string to an instance of MongoRsStatus.
 *
 * @author Konstantin Silaev
 */
@AllArgsConstructor
public class StringToMongoRsStatusConverter
    implements Converter<String, MongoRsStatus> {

    private static final String MONGO_VERSION_MARKER = "MongoDB server version:";

    private final YmlConverter yamlConverter;
    private final VersionConverter versionConverter;

    public StringToMongoRsStatusConverter() {
        this.yamlConverter = new YmlConverterImpl();
        this.versionConverter = new VersionConverter();
    }

    @SneakyThrows
    @Override
    public MongoRsStatus convert(String source) {
        val io = new ByteArrayInputStream(
            formatToJsonString(source).getBytes(StandardCharsets.UTF_8)
        );
        val mongoRsStatusMutable = yamlConverter.unmarshal(MongoRsStatusMutable.class, io);
        if (Objects.isNull(mongoRsStatusMutable)) {
            return MongoRsStatus.of(
                0,
                null,
                Collections.emptyList()
            );
        } else {
            return MongoRsStatus.of(
                mongoRsStatusMutable.getStatus(),
                versionConverter.convert(mongoRsStatusMutable.getVersion()),
                getImmutableMembers(mongoRsStatusMutable)
            );
        }
    }

    private List<MongoNode> getImmutableMembers(final MongoRsStatusMutable mongoRsStatusMutable) {
        return Optional.ofNullable(mongoRsStatusMutable.getMembers())
            .map(m -> mongoRsStatusMutable.getMembers().stream()
                .map(this::mongoNodeMapping)
                .collect(
                    Collectors.collectingAndThen(
                        Collectors.toList(),
                        Collections::unmodifiableList
                    )
                )
            ).orElseGet(Collections::emptyList);
    }

    private MongoNode mongoNodeMapping(final MongoNodeMutable node) {
        val address = StringUtils.getArrayByDelimiter(node.getName());
        return MongoNode.of(
            address[0],
            Integer.parseInt(address[1]),
            node.getHealth(),
            ReplicaSetMemberState.getByValue(node.getState())
        );
    }

    public String formatToJsonString(final String mongoDbReply) {
        String version = null;
        val sb = new StringBuilder();
        boolean isMongoVersionMarker = false;
        for (String s : mongoDbReply.replaceAll("\t", "").split("\n")) {
            if (!s.isEmpty()) {
                if ((!isMongoVersionMarker) && (s.contains(MONGO_VERSION_MARKER))) {
                    isMongoVersionMarker = true;
                    version = s.substring(s.indexOf(':') + 1).trim();
                } else if (isMongoVersionMarker) {
                    if (s.contains("\"ok\" :")) {
                        String statusString = s.replace("ok", "status");
                        if (statusString.endsWith("}")) {
                            statusString = s.replace("}", "");
                        }
                        sb.append(statusString);
                        if (!s.contains(",")) {
                            sb.append(",");
                        }
                        sb.append(
                            String.format("\"version\" : \"%s\"}", version)
                        );
                        break;
                    }
                    sb.append(s);
                }
            }
        }
        return sb.toString();
    }
}
