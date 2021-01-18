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
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Comparator;
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
@Slf4j
public class StringToMongoRsStatusConverter
    implements Converter<String, MongoRsStatus> {

    private static final String MONGO_VERSION_MARKER = "MongoDB server version:";
    public static final String OK = "\"ok\" : ";

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
        MongoRsStatusMutable mongoRsStatusMutable;
        try {
            mongoRsStatusMutable = yamlConverter.unmarshal(MongoRsStatusMutable.class, io);
        } catch (Exception e) {
            log.error("Cannot convert to yaml format: \n{}", source);
            throw e;
        }
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
                .sorted(Comparator.comparing(MongoNode::getPort))
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
        String[] lines = mongoDbReply.replaceAll("\t", "").split("\n");
        int idx = 0;
        final int length = lines.length;
        while (idx < length) {
            String currentLine = lines[idx];
            if (!currentLine.isEmpty() && currentLine.contains(MONGO_VERSION_MARKER)) {
                version = currentLine.substring(currentLine.indexOf(':') + 1).trim();
                idx++;
                break;
            }
            idx++;
        }
        val sb = new StringBuilder();
        for (int i = idx; i < length; i++) {
            sb.append(lines[i].replaceAll("\\s\\s", ""));
        }
        final int indexOfOk = sb.indexOf(OK);
        if (indexOfOk > 0) {
            val start = indexOfOk + OK.length();
            String status = sb.substring(start, start + 1);
            sb.delete(indexOfOk, sb.length());
            val strExtra = String.format("\"version\" : \"%s\",", version) + String.format("\"status\" : \"%s\"}", status);
            sb.append(strExtra);
        }
        return sb.toString();
    }
}
