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
import lombok.var;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Converts a string to an instance of MongoRsStatus.
 *
 * @author Konstantin Silaev
 */
@AllArgsConstructor
@Slf4j
public class StringToMongoRsStatusConverter implements Converter<String, MongoRsStatus> {

    private static final String MONGO_VERSION_MARKER = "MongoDB server version:";
    private static final String OK = "\"ok\" : ";
    private static final Pattern OK_PATTERN = Pattern.compile("(?i).*" + OK);

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
            extractJsonPayloadFromMongoDBShell(source).getBytes(StandardCharsets.UTF_8)
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

    public String extractRawPayloadFromMongoDBShell(final String mongoDbReply) {
        return extractRawPayloadFromMongoDBShell(mongoDbReply, false);
    }

    private String extractJsonPayloadFromMongoDBShell(final String mongoDbReply) {
        return extractRawPayloadFromMongoDBShell(mongoDbReply, true);
    }

    private String extractRawPayloadFromMongoDBShell(final String mongoDbReply, final boolean formatJson) {
        String version = null;
        val lines = mongoDbReply.replace("\t", "").split("\n");
        int idx = 0;
        val length = lines.length;
        var isVersionLineFound = false;
        while (idx < length) {
            String currentLine = lines[idx];
            if (!currentLine.isEmpty() && currentLine.contains(MONGO_VERSION_MARKER)) {
                version = currentLine.substring(currentLine.indexOf(':') + 1).trim();
                idx++;
                isVersionLineFound = true;
                break;
            }
            idx++;
        }

        if (!isVersionLineFound) {
            throw new IllegalArgumentException(
                String.format("Cannot find a substring %s in mongoDbReply: %n%s", MONGO_VERSION_MARKER, mongoDbReply)
            );
        }

        val sb = new StringBuilder();
        for (int i = idx; i < length; i++) {
            sb.append(lines[i].replaceAll("\\s\\s", ""));
        }
        if (formatJson) {
            val matcher = OK_PATTERN.matcher(sb);
            val endIndexOk = matcher.find() ? matcher.end() : 0;
            if (endIndexOk > 0) {
                val status = sb.substring(endIndexOk, endIndexOk + 1);
                sb.delete(endIndexOk - OK.length(), sb.length());
                val strExtra = String.format("\"version\" : \"%s\",", version) + String.format("\"status\" : \"%s\"}", status);
                sb.append(strExtra);
            } else {
                throw new IllegalArgumentException(
                    String.format("Cannot find a pattern %s in mongoDbReply: %n%s", OK_PATTERN.toString(), mongoDbReply)
                );
            }
        }
        return sb.toString();
    }
}
