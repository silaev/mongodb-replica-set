package com.github.silaev.mongodb.replicaset;

import com.github.silaev.mongodb.replicaset.converter.impl.MongoNodeToMongoSocketAddressConverter;
import com.github.silaev.mongodb.replicaset.converter.impl.StringToMongoRsStatusConverter;
import com.github.silaev.mongodb.replicaset.converter.impl.UserInputToApplicationPropertiesConverter;
import com.github.silaev.mongodb.replicaset.exception.IncorrectUserInputException;
import com.github.silaev.mongodb.replicaset.exception.MongoNodeInitializationException;
import com.github.silaev.mongodb.replicaset.model.ApplicationProperties;
import com.github.silaev.mongodb.replicaset.model.MongoDbVersion;
import com.github.silaev.mongodb.replicaset.model.MongoNode;
import com.github.silaev.mongodb.replicaset.model.MongoRsStatus;
import com.github.silaev.mongodb.replicaset.model.MongoSocketAddress;
import com.github.silaev.mongodb.replicaset.model.Pair;
import com.github.silaev.mongodb.replicaset.model.ReplicaSetMemberState;
import com.github.silaev.mongodb.replicaset.model.UserInputProperties;
import com.github.silaev.mongodb.replicaset.util.StringUtils;
import lombok.Builder;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startable;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Constructs a full-featured MongoDB cluster for integration testing, reproducing production issues, learning distributed systems by the example of MongoDB.
 * <p>Supports Mongo DB version 3.6.14 and up.
 *
 * <blockquote>
 * <table class="striped">
 * <caption style="display:none">Chart shows pattern local and remote docker support for replicaSetNumber</caption>
 * <thead>
 *     <tr>
 *         <th scope="col" style="text-align:center">replicaSetNumber
 *         <th scope="col" style="text-align:center">local docker host
 *         <th scope="col" style="text-align:center">local docker host running tests from inside a container with mapping the Docker socket
 *         <th scope="col" style="text-align:center">remote docker daemon
 *         <th scope="col" style="text-align:center">availability of an arbiter node
 * </thead>
 * <tbody>
 *     <tr>
 *         <th scope="row" style="text-align:center"><code>1</code>
 *         <td style="text-align:center">+
 *         <td style="text-align:center">+
 *         <td style="text-align:center">+
 *         <td style="text-align:center">-
 *     <tr>
 *     <tr>
 *         <th scope="row" style="text-align:center"><code>from 2 to 7 (including)</code>
 *         <td style="text-align:center">only if adding <b>127.0.0.1 dockerhost</b> to the OS host file
 *         <td style="text-align:center">+
 *         <td style="text-align:center">+
 *         <td style="text-align:center">+
 *     <tr>
 * </tbody>
 * </table>
 * </blockquote>
 * <p>Complete documentation is found at <a href="https://github.com/silaev/mongodb-replica-set">mongodb-replica-set on github</a>
 *
 * <h3>Example usage</h3>
 * <p>The example of a JUnit5 test class:
 * <pre style="code">
 * import com.github.silaev.mongodb.replicaset.MongoDbReplicaSet;
 * import org.junit.jupiter.api.AfterAll;
 * import org.junit.jupiter.api.BeforeAll;
 * import org.junit.jupiter.api.Test;
 *
 * import static org.junit.jupiter.api.Assertions.assertNotNull;
 *
 * class ITTest {
 *     private static final MongoDbReplicaSet MONGO_REPLICA_SET = MongoDbReplicaSet.builder()
 *             //.replicaSetNumber(3)
 *             //.mongoDockerImageName("mongo:4.2.0")
 *             //.addArbiter(true)
 *             //.awaitNodeInitAttempts(30)
 *             .build();
 *
 *     {@literal @}BeforeAll
 *     static void setUpAll() {
 *         MONGO_REPLICA_SET.start();
 *     }
 *
 *     {@literal @}AfterAll
 *     static void tearDownAllAll() {
 *         MONGO_REPLICA_SET.stop();
 *     }
 *
 *     {@literal @}Test
 *     void shouldTestReplicaSetUrlAndStatus() {
 *         assertNotNull(MONGO_REPLICA_SET.getReplicaSetUrl());
 *         assertNotNull(MONGO_REPLICA_SET.getMongoRsStatus());
 *     }
 * }
 * </pre>
 *
 * <p>The example of a SpringBoot + SpringData with JUnit5:
 * <pre style="code">
 * import com.github.silaev.mongodb.replicaset.MongoDbReplicaSet;
 * import org.junit.jupiter.api.AfterAll;
 * import org.junit.jupiter.api.BeforeAll;
 * import org.junit.jupiter.api.Test;
 * import org.springframework.boot.test.context.SpringBootTest;
 * import org.springframework.boot.test.util.TestPropertyValues;
 * import org.springframework.context.ApplicationContextInitializer;
 * import org.springframework.context.ConfigurableApplicationContext;
 * import org.springframework.test.context.ContextConfiguration;
 *
 * import static org.junit.jupiter.api.Assertions.assertNotNull;
 *
 * {@literal @}SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
 * //@DataMongoTest
 * {@literal @}ContextConfiguration(initializers = ITTest.Initializer.class)
 * class ITTest {
 *     private static final MongoDbReplicaSet MONGO_REPLICA_SET = MongoDbReplicaSet.builder()
 *             //.replicaSetNumber(3)
 *             //.mongoDockerImageName("mongo:4.2.0")
 *             //.addArbiter(true)
 *             //.awaitNodeInitAttempts(30)
 *             .build();
 *
 *     {@literal @}BeforeAll
 *     static void setUpAll() {
 *         MONGO_REPLICA_SET.start();
 *     }
 *
 *     {@literal @}AfterAll
 *     static void tearDownAllAll() {
 *         MONGO_REPLICA_SET.stop();
 *     }
 *
 *     {@literal @}Test
 *     void shouldTestReplicaSetUrlAndStatus() {
 *         assertNotNull(MONGO_REPLICA_SET.getReplicaSetUrl());
 *         assertNotNull(MONGO_REPLICA_SET.getMongoRsStatus());
 *     }
 *
 *     static class Initializer implements ApplicationContextInitializer&#60;ConfigurableApplicationContext&#62; {
 *         {@literal @}Override
 *         public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
 *             if (MONGO_REPLICA_SET.isEnabled()) {
 *                 TestPropertyValues.of(
 *                         "spring.data.mongodb.uri: " + MONGO_REPLICA_SET.getReplicaSetUrl()
 *                 ).applyTo(configurableApplicationContext);
 *             }
 *         }
 *     }
 * }
 * </pre>
 *
 * @author Konstantin Silaev
 */
@Slf4j
public class MongoDbReplicaSet implements Startable {
    public static final int MAX_VOTING_MEMBERS = 7;
    static final String STATUS_COMMAND = "rs.status()";
    private static final int CONTAINER_EXIT_CODE_OK = 0;
    private static final String CLASS_NAME = MongoDbReplicaSet.class.getCanonicalName();
    private static final String LOCALHOST = "localhost";
    private static final String DOCKER_HOST_WORKAROUND = "dockerhost";
    private static final String DOCKER_HOST_INTERNAL = "host.docker.internal";
    private static final boolean USE_HOST_WORKAROUND = true;
    private static final int MONGO_DB_INTERNAL_PORT = 27017;
    private static final String MONGO_ARBITER_NODE_NAME = "mongo-arbiter";
    private static final String DOCKER_HOST_CONTAINER_NAME = "qoomon/docker-host:2.3.0";
    private static final String TOXIPROXY_CONTAINER_NAME = "toxiproxy";
    private static final MongoDbVersion FIRST_SUPPORTED_MONGODB_VERSION =
        MongoDbVersion.of(3, 6, 14);
    private static final boolean MOVE_FORWARD = true;
    private static final boolean STOP_PIPELINE = false;
    private static final String READ_PREFERENCE_PRIMARY = "primary";
    private static final String RS_STATUS_MEMBERS_DEFINED_CONDITION = "rs.status().members != undefined && ";
    private final StringToMongoRsStatusConverter statusConverter;
    private final MongoNodeToMongoSocketAddressConverter socketAddressConverter;
    private final ApplicationProperties properties;
    /*
     * GenericContainer, not Startable, because there is a need to execute commands after starting
     */
    private final Map<MongoSocketAddress, GenericContainer> workingNodeStore;
    private final Map<MongoSocketAddress, ToxiproxyContainer.ContainerProxy> toxyNodeStore;
    private final Map<String, Pair<GenericContainer, MongoSocketAddress>> supplementaryNodeStore;
    private final Map<MongoSocketAddress, Pair<Boolean, GenericContainer>> disconnectedNodeStore;
    private final Network network;

    @Builder
    private MongoDbReplicaSet(
        final Integer replicaSetNumber,
        final Integer awaitNodeInitAttempts,
        final String propertyFileName,
        final String mongoDockerImageName,
        final Boolean addArbiter,
        final Boolean addToxiproxy
    ) {
        val propertyConverter =
            new UserInputToApplicationPropertiesConverter();

        this.properties = propertyConverter.convert(
            UserInputProperties.builder()
                .replicaSetNumber(replicaSetNumber)
                .awaitNodeInitAttempts(awaitNodeInitAttempts)
                .propertyFileName(propertyFileName)
                .mongoDockerImageName(mongoDockerImageName)
                .addArbiter(addArbiter)
                .addToxiproxy(addToxiproxy)
                .build()
        );
        this.statusConverter = new StringToMongoRsStatusConverter();
        this.socketAddressConverter = new MongoNodeToMongoSocketAddressConverter();
        this.workingNodeStore = new ConcurrentHashMap<>();
        this.supplementaryNodeStore = new ConcurrentHashMap<>();
        this.disconnectedNodeStore = new ConcurrentHashMap<>();
        this.toxyNodeStore = new ConcurrentHashMap<>();
        this.network = Network.newNetwork();
    }

    /**
     * Constructor for unit tests.
     *
     * @param statusConverter
     * @param workingNodeStore
     * @param supplementaryNodeStore
     * @param disconnectedNodeStore
     * @param toxyNodeStore
     */
    MongoDbReplicaSet(
        final StringToMongoRsStatusConverter statusConverter,
        Map<MongoSocketAddress, GenericContainer> workingNodeStore,
        Map<String, Pair<GenericContainer, MongoSocketAddress>> supplementaryNodeStore,
        Map<MongoSocketAddress, Pair<Boolean, GenericContainer>> disconnectedNodeStore,
        Map<MongoSocketAddress, ToxiproxyContainer.ContainerProxy> toxyNodeStore
    ) {
        val propertyConverter =
            new UserInputToApplicationPropertiesConverter();

        this.properties = propertyConverter.convert(
            UserInputProperties.builder().build()
        );
        this.statusConverter = statusConverter;
        this.socketAddressConverter = new MongoNodeToMongoSocketAddressConverter();
        this.workingNodeStore = workingNodeStore;
        this.supplementaryNodeStore = supplementaryNodeStore;
        this.disconnectedNodeStore = disconnectedNodeStore;
        this.toxyNodeStore = toxyNodeStore;
        this.network = Network.newNetwork();
    }

    public String getReplicaSetUrl() {
        verifyWorkingNodeStoreIsNotEmpty();

        return buildMongoRsUrl(READ_PREFERENCE_PRIMARY);
    }

    public String getReplicaSetUrl(final String readPreference) {
        verifyWorkingNodeStoreIsNotEmpty();

        return buildMongoRsUrl(readPreference);
    }

    public MongoRsStatus getMongoRsStatus() {
        verifyWorkingNodeStoreIsNotEmpty();

        return statusConverter.convert(
            execMongoDbCommandInContainer(
                workingNodeStore.entrySet().iterator().next().getValue(),
                STATUS_COMMAND
            ).getStdout()
        );
    }

    @Override
    public synchronized void stop() {
        Stream.concat(
            disconnectedNodeStore.values().stream().map(Pair::getRight),
            Stream.concat(
                supplementaryNodeStore.values().stream().map(Pair::getLeft),
                workingNodeStore.values().stream()
            )
        ).forEach(Startable::stop);
        network.close();
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public String mongoDockerImageName() {
        return properties.getMongoDockerImageName();
    }

    public int getReplicaSetNumber() {
        return properties.getReplicaSetNumber();
    }

    public int getAwaitNodeInitAttempts() {
        return properties.getAwaitNodeInitAttempts();
    }

    public String getMongoDockerImageName() {
        return properties.getMongoDockerImageName();
    }

    public boolean getAddArbiter() {
        return properties.isAddArbiter();
    }

    public boolean getAddToxiproxy() {
        return properties.isAddToxiproxy();
    }

    /**
     * Switch to host.docker.internal once https://github.com/docker/for-linux/issues/264 is resolved.
     */
    private String getDockerHostName() {
        return USE_HOST_WORKAROUND ? DOCKER_HOST_WORKAROUND : DOCKER_HOST_INTERNAL;
    }

    private String[] buildMongoEvalCommand(final String command) {
        return new String[]{"mongo", "--eval", command};
    }

    @Override
    public synchronized void start() {
        log.debug("currentThread: {}", Thread.currentThread().getName());

        if (!properties.isEnabled()) {
            log.info("{} is disabled", CLASS_NAME);
            return;
        }
        ToxiproxyContainer toxiproxyContainer = null;
        if (getAddToxiproxy()) {
            toxiproxyContainer = getAndStartToxiproxyContainer();
            supplementaryNodeStore.put(TOXIPROXY_CONTAINER_NAME, Pair.of(toxiproxyContainer, null));
        }
        val dockerHostName = getDockerHostName();
        val replicaSetNumber = properties.getReplicaSetNumber();

        GenericContainer mongoContainer = null;
        for (int i = replicaSetNumber - 1; i >= 0; i--) {
            mongoContainer = getAndStartMongoDbContainer(network);

            val pair = getContainerProxyAndPort(mongoContainer, toxiproxyContainer);

            val mongoSocketAddress = getMongoSocketAddress(
                mongoContainer.getContainerIpAddress(),
                pair.getRight()
            );
            workingNodeStore.put(mongoSocketAddress, mongoContainer);

            if (getAddToxiproxy()) {
                toxyNodeStore.put(mongoSocketAddress, pair.getLeft());
            }
        }

        if (Objects.isNull(mongoContainer)) {
            throw new IllegalStateException("MongoDb container is not supposed to be null");
        }

        if (LOCALHOST.equals(getHostIpAddress()) && getReplicaSetNumber() > 1) {
            warnAboutTheNeedToModifyHostFile();
            supplementaryNodeStore.put(
                DOCKER_HOST_WORKAROUND,
                Pair.of(getAndRunDockerHostContainer(network, dockerHostName), null)
            );
        }

        val awaitNodeInitAttempts = properties.getAwaitNodeInitAttempts();

        val masterNode = initMasterNode(mongoContainer, awaitNodeInitAttempts);

        if (properties.isAddArbiter()) {
            addArbiterNode(network, toxiproxyContainer, masterNode, awaitNodeInitAttempts);
        }

        log.debug(
            "REPLICA SET STATUS:\n{}",
            execMongoDbCommandInContainer(mongoContainer, STATUS_COMMAND).getStdout()
        );
    }

    private Pair<ToxiproxyContainer.ContainerProxy, Integer> getContainerProxyAndPort(
        final GenericContainer mongoContainer,
        final ToxiproxyContainer toxiproxyContainer
    ) {
        ToxiproxyContainer.ContainerProxy containerProxy = null;
        int port;
        if (getAddToxiproxy()) {
            Objects.requireNonNull(toxiproxyContainer, "toxiproxyContainer is not supposed to be null");
            containerProxy = toxiproxyContainer.getProxy(mongoContainer, MONGO_DB_INTERNAL_PORT);
            port = containerProxy.getProxyPort();
        } else {
            Objects.requireNonNull(mongoContainer, "mongoContainer is not supposed to be null");
            port = mongoContainer.getMappedPort(MONGO_DB_INTERNAL_PORT);
        }
        return Pair.of(containerProxy, port);
    }

    @SneakyThrows(value = {IOException.class, InterruptedException.class})
    Container.ExecResult execMongoDbCommandInContainer(
        final GenericContainer mongoContainer,
        final String command
    ) {
        return mongoContainer.execInContainer(
            buildMongoEvalCommand(command)
        );
    }

    private void warnAboutTheNeedToModifyHostFile() {
        log.warn(
            "Please, check that the host file of your OS has 127.0.0.1 dockerhost. " +
                "If you don't want to modify it, then consider the following:" +
                "\n1) set replicaSetNumber to 1;" +
                "\n2) use remote docker daemon;" +
                "\n3) use local docker host running tests from inside a container with mapping the Docker socket."
        );
    }

    private String getHostIpAddress() {
        return Optional.ofNullable(DockerClientFactory.instance())
            .map(DockerClientFactory::dockerHostIpAddress)
            .orElseThrow(
                () -> new IllegalStateException(
                    "The instance of the DockerClientFactory is not initialized"
                )
            );
    }

    private GenericContainer initMasterNode(
        final GenericContainer mongoContainer,
        final int awaitNodeInitAttempts
    ) {
        log.debug("Initializing a {} node replica set...", getReplicaSetNumber());
        val execResultInitRs = execMongoDbCommandInContainer(
            mongoContainer,
            getMongoReplicaSetInitializer()
        );
        val stdoutInitRs = execResultInitRs.getStdout();
        log.debug(stdoutInitRs);
        verifyVersion(stdoutInitRs);

        checkMongoNodeExitCode(
            execResultInitRs,
            "initializing a master node"
        );

        return checkAndGetMasterNode(mongoContainer, awaitNodeInitAttempts);
    }

    private GenericContainer checkAndGetMasterNode(
        final GenericContainer mongoContainer,
        final int awaitNodeInitAttempts
    ) {
        return getReplicaSetNumber() == 1
            ? checkAndGetMasterNodeInSingleNodeReplicaSet(mongoContainer, awaitNodeInitAttempts)
            : checkAndGetMasterNodeInMultiNodeReplicaSet(mongoContainer, awaitNodeInitAttempts);
    }

    void verifyVersion(String stdoutInitRs) {
        val inputVersion = statusConverter.convert(stdoutInitRs).getVersion();

        if (checkVersionPart(
            inputVersion.getMajor(),
            FIRST_SUPPORTED_MONGODB_VERSION.getMajor()
        ) &&
            checkVersionPart(
                inputVersion.getMinor(),
                FIRST_SUPPORTED_MONGODB_VERSION.getMinor())
        ) {
            checkVersionPart(
                inputVersion.getPatch(),
                FIRST_SUPPORTED_MONGODB_VERSION.getPatch()
            );
        }
    }

    private boolean checkVersionPart(final int inputVersion, final int supportedVersion) {
        if (inputVersion == supportedVersion) {
            return MOVE_FORWARD;
        } else if (inputVersion > supportedVersion) {
            return STOP_PIPELINE;
        } else {
            throw new IncorrectUserInputException(
                String.format(
                    "Please, use a MongoDB version that is more or equal to: %s",
                    FIRST_SUPPORTED_MONGODB_VERSION
                )
            );
        }
    }

    private GenericContainer checkAndGetMasterNodeInMultiNodeReplicaSet(
        final GenericContainer mongoContainer,
        final int awaitNodeInitAttempts
    ) {
        log.debug("Searching for a master node in a replica set, up to {} attempts", awaitNodeInitAttempts);
        val execResultWaitForAnyMaster = waitForCondition(
            mongoContainer,
            RS_STATUS_MEMBERS_DEFINED_CONDITION +
                "rs.status().members.find(o => o.state == 1) === undefined",
            awaitNodeInitAttempts,
            "Searching for a master node"
        );
        log.debug(execResultWaitForAnyMaster.getStdout());
        checkMongoNodeExitCodeAfterWaiting(
            mongoContainer,
            execResultWaitForAnyMaster,
            "master candidate",
            awaitNodeInitAttempts
        );

        val masterNode = findMasterElected(mongoContainer);

        log.debug("Verifying that a node is a master one, up to {} attempts", awaitNodeInitAttempts);
        val execResultWaitForMaster = waitForCondition(
            masterNode,
            "db.runCommand( { isMaster: 1 } ).ismaster==false",
            awaitNodeInitAttempts, "verifying that a node is a master one"
        );

        checkMongoNodeExitCodeAfterWaiting(
            masterNode,
            execResultWaitForMaster,
            "master",
            awaitNodeInitAttempts
        );
        return masterNode;
    }

    private GenericContainer findMasterElected(
        final GenericContainer mongoContainer
    ) {
        val execResultMasterAddress = execMongoDbCommandInContainer(
            mongoContainer,
            "rs.status().members.find(o => o.state == 1).name"
        );
        checkMongoNodeExitCode(execResultMasterAddress, "finding a master node");
        final String stdout = execResultMasterAddress.getStdout();
        final MongoSocketAddress mongoSocketAddress =
            Optional.ofNullable(statusConverter.formatToJsonString(stdout))
                .map(StringUtils::getArrayByDelimiter)
                .filter(a -> a.length == 2)
                .map(a -> {
                        val port = Integer.parseInt(a[1]);
                        return MongoSocketAddress.builder()
                            .ip(a[0])
                            .replSetPort(port)
                            .mappedPort(port)
                            .build();
                    }
                ).orElseThrow(
                () -> new IllegalArgumentException(
                    String.format(
                        "Cannot find an address in a MongoDb reply:%s %s",
                        System.lineSeparator(),
                        stdout
                    )
                )
            );
        log.debug("Found the master elected: {}", mongoSocketAddress);

        return extractGenericContainer(mongoSocketAddress, workingNodeStore);
    }

    private <T> T extractGenericContainer(
        final MongoSocketAddress mongoSocketAddress,
        final Map<MongoSocketAddress, T> nodeStore
    ) {
        return Optional.ofNullable(nodeStore.get(mongoSocketAddress))
            .orElseThrow(() -> new IllegalStateException(
                    String.format(
                        "Cannot find a node in a node store by %s",
                        mongoSocketAddress
                    )
                )
            );
    }

    /**
     * Extracts working or arbiter GenericContainer.
     *
     * @param mongoSocketAddress
     * @return Pair containing a flag isWorkingNode(not an arbiter) and GenericContainer.
     */
    private Pair<Boolean, GenericContainer> extractWorkingOrArbiterGenericContainer(
        @NotNull final MongoSocketAddress mongoSocketAddress
    ) {
        return Optional.ofNullable(workingNodeStore.get(mongoSocketAddress))
            .map(n -> Pair.of(Boolean.TRUE, n))
            .orElseGet(() ->
                Optional.ofNullable(supplementaryNodeStore.get(MONGO_ARBITER_NODE_NAME))
                    .filter(arbiter -> mongoSocketAddress.equals(arbiter.getRight()))
                    .map(n -> Pair.of(Boolean.FALSE, n.getLeft()))
                    .orElseThrow(() -> new IllegalStateException(
                            String.format(
                                "Cannot find a node in a working and arbiter node store by %s",
                                mongoSocketAddress
                            )
                        )
                    )
            );
    }

    private GenericContainer checkAndGetMasterNodeInSingleNodeReplicaSet(
        final GenericContainer mongoContainer,
        final int awaitNodeInitAttempts
    ) {
        log.debug("Awaiting a master node, up to {} attempts", awaitNodeInitAttempts);
        val execResultWaitForMaster = waitForCondition(
            mongoContainer,
            "db.runCommand( { isMaster: 1 } ).ismaster==false",
            awaitNodeInitAttempts, "awaiting for a node to be a master one"
        );
        log.debug(execResultWaitForMaster.getStdout());

        checkMongoNodeExitCodeAfterWaiting(
            mongoContainer,
            execResultWaitForMaster,
            "master",
            awaitNodeInitAttempts
        );

        return mongoContainer;
    }

    private MongoSocketAddress getMongoSocketAddress(
        final String containerIpAddress,
        final int port
    ) {
        if (LOCALHOST.equals(containerIpAddress)) {
            return (getReplicaSetNumber() == 1)
                ? MongoSocketAddress.builder()
                .ip(LOCALHOST)
                .replSetPort(MONGO_DB_INTERNAL_PORT)
                .mappedPort(port)
                .build()
                : MongoSocketAddress.builder()
                .ip(getDockerHostName())
                .replSetPort(port)
                .mappedPort(port)
                .build();
        } else {
            return MongoSocketAddress.builder()
                .ip(containerIpAddress)
                .replSetPort(port)
                .mappedPort(port)
                .build();
        }
    }

    /**
     * In general, avoid deploying more than one arbiter per replica set.
     */
    private void addArbiterNode(
        final Network network,
        final ToxiproxyContainer toxiproxyContainer,
        final GenericContainer masterNode,
        final int awaitNodeInitAttempts
    ) {
        log.debug("Awaiting an arbiter node to be available, up to {} attempts", properties.getAwaitNodeInitAttempts());

        val mongoContainerArbiter = getAndStartMongoDbContainer(network);
        val pair = getContainerProxyAndPort(mongoContainerArbiter, toxiproxyContainer);
        val mongoSocketAddress = getMongoSocketAddress(
            mongoContainerArbiter.getContainerIpAddress(),
            pair.getRight()
        );
        supplementaryNodeStore.put(MONGO_ARBITER_NODE_NAME, Pair.of(mongoContainerArbiter, mongoSocketAddress));
        if (getAddToxiproxy()) {
            toxyNodeStore.put(mongoSocketAddress, pair.getLeft());
        }
        val execResultAddArbiter = execMongoDbCommandInContainer(
            masterNode,
            String.format(
                "rs.addArb(\"%s:%d\")",
                mongoSocketAddress.getIp(),
                mongoSocketAddress.getReplSetPort()
            )
        );
        log.debug("Add an arbiter node result: {}", execResultAddArbiter.getStdout());
        checkMongoNodeExitCode(
            execResultAddArbiter,
            "initializing an arbiter node"
        );

        val execResultWaitArbiter = waitForCondition(
            masterNode,
            RS_STATUS_MEMBERS_DEFINED_CONDITION +
                "rs.status().members.find(o => o.state == 7) === undefined",
            awaitNodeInitAttempts,
            "awaiting an arbiter node to be up"
        );
        log.debug("Wait for an arbiter node result: {}", execResultWaitArbiter.getStdout());
        checkMongoNodeExitCodeAfterWaiting(
            masterNode, execResultWaitArbiter,
            "arbiter",
            awaitNodeInitAttempts
        );
    }

    void checkMongoNodeExitCodeAfterWaiting(
        final GenericContainer mongoContainer,
        final Container.ExecResult execResultWaitForMaster,
        final String nodeName,
        final int awaitNodeInitAttempts
    ) {
        if (execResultWaitForMaster.getExitCode() != CONTAINER_EXIT_CODE_OK) {
            val errorMessage = String.format(
                "The %s node was not initialized in a set timeout: %d attempts. Replica set status: %s",
                nodeName,
                awaitNodeInitAttempts,
                execMongoDbCommandInContainer(mongoContainer, STATUS_COMMAND).getStdout()
            );

            log.error(errorMessage);
            throw new MongoNodeInitializationException(errorMessage);
        }
    }

    void checkMongoNodeExitCode(
        final Container.ExecResult execResult,
        final String commandDescription
    ) {
        if (execResult.getExitCode() != CONTAINER_EXIT_CODE_OK) {
            val errorMessage = String.format(
                "Error occurred while %s: %s",
                commandDescription,
                execResult.getStdout()
            );
            log.error(errorMessage);
            throw new MongoNodeInitializationException(errorMessage);
        }
    }

    private Container.ExecResult waitForCondition(
        final GenericContainer mongoContainer,
        final String condition,
        final int awaitNodeInitAttempts,
        final String waitingMessage
    ) {
        return execMongoDbCommandInContainer(
            mongoContainer,
            buildMongoWaitCommand(
                condition,
                awaitNodeInitAttempts,
                waitingMessage
            )
        );
    }

    private String buildMongoWaitCommand(
        final String condition,
        final int attempts,
        final String waitingMessage
    ) {
        return String.format(
            "var attempt = 0; " +
                "while" +
                "(%s) " +
                "{ " +
                "if (attempt > %d) {quit(1);} " +
                "print('%s ' + attempt); sleep(1000);  attempt++; " +
                " }",
            condition, attempts, waitingMessage
        );
    }

    private String getMongoReplicaSetInitializer() {
        val addresses = workingNodeStore.keySet().toArray(new MongoSocketAddress[0]);
        val length = addresses.length;
        String replicaSetInitializer = IntStream.range(0, length)
            .mapToObj(i -> {
                    val address = addresses[length - i - 1];
                    return String.format(
                        "        {\"_id\": %d, \"host\": \"%s:%d\"}",
                        i, address.getIp(), address.getReplSetPort()
                    );
                }
            ).collect(Collectors.joining(
                ",\n",
                "rs.initiate({\n" +
                    "    \"_id\": \"docker-rs\",\n" +
                    "    \"members\": [\n",
                "\n    ]\n});"
                )
            );
        log.debug(replicaSetInitializer);
        return replicaSetInitializer;
    }

    private String buildMongoRsUrl(final String readPreference) {
        return workingNodeStore.keySet().stream()
            .map(a -> String.format(
                "%s:%d",
                a.getIp(), a.getMappedPort()
                )
            ).collect(
                Collectors.joining(
                    ",",
                    "mongodb://",
                    String.format("/test%s&readPreference=%s",
                        getReplicaSetNumber() == 1 ? "" : "?replicaSet=docker-rs",
                        readPreference
                    )
                )
            );
    }

    /**
     * Creates a Docker container to forward TCP and UDP traffic to the docker host.
     * <p>Needs to be closed at the end.
     *
     * @param network        a shared network
     * @param dockerHostName a network alias
     * @return a container to forward TCP and UDP traffic to the docker host.
     * @see <a href="https://github.com/qoomon/docker-host">docker-host on github</a>
     */
    private @NonNull GenericContainer<?> getAndRunDockerHostContainer(
        final Network network,
        final String dockerHostName
    ) {
        final GenericContainer<?> dockerHostContainer = new GenericContainer<>(
            DOCKER_HOST_CONTAINER_NAME
        ).withPrivilegedMode(true)
            .withNetwork(network)
            .withNetworkAliases(dockerHostName);
        dockerHostContainer.start();
        return dockerHostContainer;
    }

    /**
     * Creates ans starts a Docker container representing a MongoDB node to participate in a replica set.
     * <p>Needs to be closed at the end.
     *
     * @param network a shared network
     * @return a Docker container representing a MongoDB node
     */
    private @NonNull GenericContainer<?> getAndStartMongoDbContainer(final Network network) {
        final GenericContainer<?> mongoDbContainer = new GenericContainer<>(
            properties.getMongoDockerImageName()
        ).withNetwork(getReplicaSetNumber() == 1 ? null : network)
            .withExposedPorts(MONGO_DB_INTERNAL_PORT)
            .withCommand("--bind_ip", "0.0.0.0", "--replSet", "docker-rs")
            .waitingFor(
                Wait.forLogMessage(".*waiting for connections on port.*", 1)
            );
        mongoDbContainer.start();
        return mongoDbContainer;
    }

    private @NonNull ToxiproxyContainer getAndStartToxiproxyContainer() {
        ToxiproxyContainer toxiproxy = new ToxiproxyContainer()
            .withNetwork(network);
        toxiproxy.start();
        return toxiproxy;
    }

    /**
     * Stops a Mongo node (a Docker container).
     * Does not allow to start or connect a node back.
     *
     * @param mongoNode a node to stop.
     * @see <a href="https://docs.docker.com/engine/reference/commandline/stop/">docker stop</a>
     */
    public void stopNode(
        final MongoNode mongoNode
    ) {
        validateFaultToleranceTestSupportAvailability();

        val mongoSocketAddress = socketAddressConverter.convert(mongoNode);
        val pair = extractWorkingOrArbiterGenericContainer(mongoSocketAddress);
        val isWorkingNode = pair.getLeft();
        val genericContainer = pair.getRight();

        genericContainer.stop();

        removeNodeFromInternalStore(isWorkingNode, mongoSocketAddress);
        if (getAddToxiproxy()) {
            toxyNodeStore.remove(mongoSocketAddress);
        }
    }

    /**
     * Kills a Mongo node (a Docker container).
     *
     * @param mongoNode a node to kill
     * @see <a href="https://docs.docker.com/engine/reference/commandline/kill/">docker kill</a>
     */
    public void killNode(
        final MongoNode mongoNode
    ) {
        validateFaultToleranceTestSupportAvailability();

        val mongoSocketAddress = socketAddressConverter.convert(mongoNode);
        val pair = extractWorkingOrArbiterGenericContainer(mongoSocketAddress);
        val isWorkingNode = pair.getLeft();
        val genericContainer = pair.getRight();

        DockerClientFactory.instance().client()
            .killContainerCmd(genericContainer.getContainerId())
            .exec();

        removeNodeFromInternalStore(isWorkingNode, mongoSocketAddress);
        if (getAddToxiproxy()) {
            toxyNodeStore.remove(mongoSocketAddress);
        }
    }

    /**
     * Disconnects a Mongo node (a Docker container) from its network.
     *
     * @param mongoNode a node to disconnect.
     * @see <a href="https://docs.docker.com/engine/reference/commandline/network_disconnect/">docker network disconnect</a>
     */
    public synchronized void disconnectNodeFromNetwork(
        final MongoNode mongoNode
    ) {
        validateFaultToleranceTestSupportAvailability();

        val mongoSocketAddress = socketAddressConverter.convert(mongoNode);
        val pair = extractWorkingOrArbiterGenericContainer(mongoSocketAddress);
        val isWorkingNode = pair.getLeft();
        val genericContainer = pair.getRight();
        if (getAddToxiproxy()) {
            extractGenericContainer(mongoSocketAddress, toxyNodeStore).setConnectionCut(true);
        } else {
            DockerClientFactory.instance().client().disconnectFromNetworkCmd()
                .withContainerId(genericContainer.getContainerId())
                .withNetworkId(network.getId())
                .exec();
        }

        removeNodeFromInternalStore(isWorkingNode, mongoSocketAddress);

        disconnectedNodeStore.put(
            mongoSocketAddress,
            Pair.of(isWorkingNode, genericContainer)
        );
    }

    /**
     * Connects a Mongo node (a Docker container) back to its network
     * with forcing a cluster reconfiguration (for instance, in case
     * there is no master in a cluster after some network disconnection).
     * Beware that a container port changes after this operation
     * because of a container restart.
     *
     * @param mongoNode a node to connect.
     * @see <a href="https://docs.docker.com/engine/reference/commandline/network_connect/">docker network connect</a>
     */
    public synchronized void connectNodeToNetworkWithReconfiguration(
        final MongoNode mongoNode
    ) {
        if (getAddToxiproxy()) {
            throw new UnsupportedOperationException("Please, use connectNodeToNetwork with Toxiproxy");
        }
        validateFaultToleranceTestSupportAvailability();
        verifyWorkingNodeStoreIsNotEmpty();

        val disconnectedMongoSocketAddress = socketAddressConverter.convert(mongoNode);
        final Pair<Boolean, GenericContainer> pair =
            extractGenericContainer(disconnectedMongoSocketAddress, disconnectedNodeStore);
        val isWorkingNode = pair.getLeft();
        val disconnectedNode = pair.getRight();
        DockerClientFactory.instance().client().connectToNetworkCmd()
            .withContainerId(disconnectedNode.getContainerId())
            .withNetworkId(network.getId())
            .exec();

        restartGenericContainer(disconnectedNode);
        reconfigureReplSet();

        waitForMaster();
        val masterNode = findMasterElected(workingNodeStore.values().iterator().next());

        val newMongoSocketAddress = getMongoSocketAddress(
            mongoNode.getIp(),
            disconnectedNode.getMappedPort(MONGO_DB_INTERNAL_PORT)
        );

        addNodeToReplSetConfig(isWorkingNode, masterNode, newMongoSocketAddress);

        addNodeToInternalStore(isWorkingNode, newMongoSocketAddress, disconnectedNode);

        disconnectedNodeStore.remove(disconnectedMongoSocketAddress);
    }

    private void removeNodeFromInternalStore(Boolean isWorkingNode, MongoSocketAddress disconnectedMongoSocketAddress) {
        if (Boolean.TRUE.equals(isWorkingNode)) {
            workingNodeStore.remove(disconnectedMongoSocketAddress);
        } else {
            supplementaryNodeStore.remove(MONGO_ARBITER_NODE_NAME);
        }
    }

    private void addNodeToInternalStore(
        final Boolean isWorkingNode,
        final MongoSocketAddress disconnectedMongoSocketAddress,
        final GenericContainer disconnectedNode
    ) {
        if (Boolean.TRUE.equals(isWorkingNode)) {
            workingNodeStore.put(disconnectedMongoSocketAddress, disconnectedNode);
        } else {
            supplementaryNodeStore.put(MONGO_ARBITER_NODE_NAME, Pair.of(disconnectedNode, disconnectedMongoSocketAddress));
        }
    }

    /**
     * Connects a Mongo node (a Docker container) back to its network
     * with forcing a cluster reconfiguration (in case there is no master in
     * a cluster after network disconnection).
     * Beware that a container port changes after this operation
     * because of a container restart.
     *
     * @param mongoNode a node to connect.
     * @see <a href="https://docs.docker.com/engine/reference/commandline/network_connect/">docker network connect</a>
     */
    public synchronized void connectNodeToNetwork(
        final MongoNode mongoNode
    ) {
        validateFaultToleranceTestSupportAvailability();
        verifyWorkingNodeStoreIsNotEmpty();

        val disconnectedMongoSocketAddress = socketAddressConverter.convert(mongoNode);
        final Pair<Boolean, GenericContainer> pair =
            extractGenericContainer(disconnectedMongoSocketAddress, disconnectedNodeStore);
        val isWorkingNode = pair.getLeft();
        val disconnectedNode = pair.getRight();

        if (getAddToxiproxy()) {
            extractGenericContainer(disconnectedMongoSocketAddress, toxyNodeStore).setConnectionCut(false);
            addNodeToInternalStore(isWorkingNode, disconnectedMongoSocketAddress, disconnectedNode);
        } else {
            DockerClientFactory.instance().client().connectToNetworkCmd()
                .withContainerId(disconnectedNode.getContainerId())
                .withNetworkId(network.getId())
                .exec();

            restartGenericContainer(disconnectedNode);

            waitForMaster();
            val masterNode = findMasterElected(workingNodeStore.values().iterator().next());

            removeNodeFromReplSetConfig(disconnectedMongoSocketAddress, masterNode);

            val newMongoSocketAddress = getMongoSocketAddress(
                mongoNode.getIp(),
                disconnectedNode.getMappedPort(MONGO_DB_INTERNAL_PORT)
            );

            addNodeToReplSetConfig(isWorkingNode, masterNode, newMongoSocketAddress);
            addNodeToInternalStore(isWorkingNode, newMongoSocketAddress, disconnectedNode);
        }
        disconnectedNodeStore.remove(disconnectedMongoSocketAddress);
    }

    private void addNodeToReplSetConfig(
        final Boolean isWorkingNode,
        final GenericContainer masterNode,
        final MongoSocketAddress mongoSocketAddress
    ) {
        if (Boolean.TRUE.equals(isWorkingNode)) {
            addWorkingNodeToReplSetConfig(masterNode, mongoSocketAddress);
        } else {
            addArbiterNodeToReplSetConfig(masterNode, mongoSocketAddress);
        }
    }

    private void reconfigureReplSet() {
        val members = getMongoRsStatus().getMembers();
        val replicaSetReConfig = getReplicaSetReConfig(members);
        log.debug("Reconfiguring a node replica set as per: {}", replicaSetReConfig);
        val execResult = execMongoDbCommandInContainer(
            workingNodeStore.values().iterator().next(),
            replicaSetReConfig
        );
        log.debug(execResult.getStdout());

        checkMongoNodeExitCode(
            execResult,
            "Reconfiguring a replica set"
        );
    }

    private String getReplicaSetReConfig(List<MongoNode> members) {
        return IntStream.range(0, members.size())
            .filter(i -> {
                val memberState = members.get(i).getState();
                return !(memberState == ReplicaSetMemberState.DOWN || memberState == ReplicaSetMemberState.UNKNOWN);
            })
            .mapToObj(i -> String.format("[cfg.members[%d]]", i))
            .collect(Collectors.joining(
                ";",
                "cfg = rs.conf(); cfg.members = ",
                ";rs.reconfig(cfg, {force : true})")
            );
    }

    private void validateFaultToleranceTestSupportAvailability() {
        if (getReplicaSetNumber() == 1) {
            throw new IllegalStateException(
                "This operation is not supported for a single node replica set. " +
                    "Please, construct at least a Primary with Two Secondary Members(P-S-S) or " +
                    "Primary with a Secondary and an Arbiter (PSA) replica set"
            );
        }
    }

    private void addWorkingNodeToReplSetConfig(
        final GenericContainer masterNode,
        final MongoSocketAddress newMongoSocketAddress
    ) {
        val execResultAddNode = execMongoDbCommandInContainer(
            masterNode,
            String.format(
                "rs.add(\"%s:%d\")",
                newMongoSocketAddress.getIp(),
                newMongoSocketAddress.getMappedPort()
            )
        );
        log.debug("Add a node: {} to a replica set, stdout: {}", newMongoSocketAddress, execResultAddNode.getStdout());
        checkMongoNodeExitCode(
            execResultAddNode,
            "Adding a node"
        );
    }

    private void addArbiterNodeToReplSetConfig(
        final GenericContainer masterNode,
        final MongoSocketAddress newMongoSocketAddress
    ) {
        val execResultAddNode = execMongoDbCommandInContainer(
            masterNode,
            String.format(
                "rs.addArb(\"%s:%d\")",
                newMongoSocketAddress.getIp(),
                newMongoSocketAddress.getMappedPort()
            )
        );
        log.debug("Add a node: {} to a replica set, stdout: {}", newMongoSocketAddress, execResultAddNode.getStdout());
        checkMongoNodeExitCode(
            execResultAddNode,
            "Adding a node"
        );
    }

    /**
     * Removes a node from a replica set configuration.
     *
     * @param mongoNodeToRemove a node to remove from a replica set.
     * @see <a href="https://docs.mongodb.com/manual/reference/method/rs.remove/">mongodb remove from a replica set</a>
     */
    public void removeNodeFromReplSetConfig(final MongoNode mongoNodeToRemove) {
        validateFaultToleranceTestSupportAvailability();
        verifyWorkingNodeStoreIsNotEmpty();

        removeNodeFromReplSetConfig(
            socketAddressConverter.convert(mongoNodeToRemove),
            findMasterElected(workingNodeStore.values().iterator().next())
        );
    }

    @NotNull
    private Container.ExecResult removeNodeFromReplSetConfig(
        final MongoSocketAddress mongoSocketAddressToRemove,
        final GenericContainer masterNode
    ) {
        val execResultRemoveNode = execMongoDbCommandInContainer(
            masterNode,
            String.format(
                "rs.remove(\"%s:%d\")",
                mongoSocketAddressToRemove.getIp(),
                mongoSocketAddressToRemove.getMappedPort()
            )
        );
        log.debug(
            "Remove a node: {} from a replica set, stdout {}",
            mongoSocketAddressToRemove,
            execResultRemoveNode.getStdout()
        );
        checkMongoNodeExitCode(
            execResultRemoveNode,
            "Removing a node"
        );
        return execResultRemoveNode;
    }

    /**
     * Restarts a container.
     *
     * @param genericContainer
     */
    private void restartGenericContainer(final GenericContainer genericContainer) {
        genericContainer.stop();
        genericContainer.start();
    }

    /**
     * Waits for a reelection in a replica set to completion
     * based on the appearance of a master node that is not equal to a
     * provided previousMasterMongoNode.
     *
     * @param previousMasterMongoNode a node that is not supposed to become
     *                                a new master elected.
     */
    public void waitForMasterReelection(
        final MongoNode previousMasterMongoNode
    ) {
        validateFaultToleranceTestSupportAvailability();
        verifyWorkingNodeStoreIsNotEmpty();

        val prevMasterName = String.format(
            "%s:%s",
            previousMasterMongoNode.getIp(),
            previousMasterMongoNode.getPort()
        );
        val reelectionMessage = String.format(
            "Waiting for the reelection of %s",
            prevMasterName
        );
        val execResultMasterReelection = waitForCondition(
            workingNodeStore.values().iterator().next(),
            String.format(
                RS_STATUS_MEMBERS_DEFINED_CONDITION +
                    "rs.status().members.find(o => o.state == 1 && o.name!='%s') === undefined",
                prevMasterName
            ),
            getAwaitNodeInitAttempts(),
            reelectionMessage
        );
        checkMongoNodeExitCode(execResultMasterReelection, reelectionMessage);
    }

    /**
     * Waits for a master node to be present in a cluster.
     */
    private void waitForMaster() {
        validateFaultToleranceTestSupportAvailability();
        verifyWorkingNodeStoreIsNotEmpty();

        val message = "Waiting for a master node to be present in a cluster";
        val execResult = waitForCondition(
            workingNodeStore.values().iterator().next(),
            RS_STATUS_MEMBERS_DEFINED_CONDITION +
                "rs.status().members.find(o => o.state == 1) === undefined",
            getAwaitNodeInitAttempts(),
            message
        );
        checkMongoNodeExitCode(execResult, message);
    }

    /**
     * Waits until a replica set has STARTUP, STARTUP2 nodes.
     */
    public void waitForAllMongoNodesUp() {
        validateFaultToleranceTestSupportAvailability();
        verifyWorkingNodeStoreIsNotEmpty();

        val waitingMessage = "Waiting for all nodes are up and running";
        val execResultWaitForNodesUp = waitForCondition(
            workingNodeStore.values().iterator().next(),
            RS_STATUS_MEMBERS_DEFINED_CONDITION +
                "rs.status().members.find(o => o.state == 0 || o.state == 5 || o.state == 6) != undefined",
            getAwaitNodeInitAttempts(),
            waitingMessage
        );
        checkMongoNodeExitCode(execResultWaitForNodesUp, waitingMessage);
    }

    /**
     * Waits until a replica set has STARTUP, STARTUP2 and no DOWN nodes.
     */
    public void waitForAllMongoNodesUpWithoutAnyDown() {
        validateFaultToleranceTestSupportAvailability();
        verifyWorkingNodeStoreIsNotEmpty();

        val waitingMessage = "Waiting for all nodes are up and running without any in a down state";
        val execResultWaitForNodesUp = waitForCondition(
            workingNodeStore.values().iterator().next(),
            RS_STATUS_MEMBERS_DEFINED_CONDITION +
                "rs.status().members.find(o => o.state == 0 || o.state == 5 || o.state == 6 || o.state == 8) != undefined",
            getAwaitNodeInitAttempts(),
            waitingMessage
        );
        checkMongoNodeExitCode(execResultWaitForNodesUp, waitingMessage);
    }

    /**
     * Waits until a replica set has nodeNumber DOWN nodes.
     *
     * @param nodeNumber a number of nodes to wait for.
     */
    public void waitForMongoNodesDown(int nodeNumber) {
        validateFaultToleranceTestSupportAvailability();
        verifyWorkingNodeStoreIsNotEmpty();

        val waitingMessage = String.format("Waiting for %d node(s) is(are) down", nodeNumber);
        val execResultWaitForNodesUp = waitForCondition(
            workingNodeStore.values().iterator().next(),
            String.format(
                RS_STATUS_MEMBERS_DEFINED_CONDITION +
                    "rs.status().members.find(o => o.state == 8) != undefined && " +
                    "rs.status().members.find(o => o.state == 8).length == %d",
                nodeNumber
            ),
            getAwaitNodeInitAttempts(),
            waitingMessage
        );
        checkMongoNodeExitCode(execResultWaitForNodesUp, waitingMessage);
    }

    private void verifyWorkingNodeStoreIsNotEmpty() {
        if (workingNodeStore.isEmpty()) {
            throw new IllegalStateException(
                "There is no any working Mongo DB node. Please, consider starting one."
            );
        }
    }

    /**
     * Fetches an arbiter node in a list of provided mongo nodes.
     *
     * @param mongoNodes a list in which to search for a master node.
     * @return MongoNode representing an arbiter node in a replica set.
     */
    public MongoNode getArbiterMongoNode(
        final List<MongoNode> mongoNodes
    ) {
        return getMongoNode(mongoNodes, ReplicaSetMemberState.ARBITER);
    }

    /**
     * Fetches a master node in a list of provided mongo nodes.
     *
     * @param mongoNodes a list in which to search for a master node.
     * @return MongoNode representing a master node in a replica set.
     */
    public MongoNode getMasterMongoNode(
        final List<MongoNode> mongoNodes
    ) {
        return getMongoNode(mongoNodes, ReplicaSetMemberState.PRIMARY);
    }

    /**
     * Fetches any secondary node in a list of provided mongo nodes.
     *
     * @param mongoNodes a list in which to search for a master node.
     * @return MongoNode representing a secondary node in a replica set.
     */
    public MongoNode getSecondaryMongoNode(
        final List<MongoNode> mongoNodes
    ) {
        return getMongoNode(mongoNodes, ReplicaSetMemberState.SECONDARY);
    }

    /**
     * Fetches any node in a list of provided mongo nodes via a memberState.
     *
     * @param mongoNodes  a list in which to search for a master node.
     * @param memberState a state to find a node.
     * @return MongoNode representing a master node in a replica set.
     */
    private MongoNode getMongoNode(
        final List<MongoNode> mongoNodes,
        final ReplicaSetMemberState memberState
    ) {
        return mongoNodes(mongoNodes, memberState)
            .findAny()
            .orElseThrow(
                () -> new IllegalStateException(
                    String.format(
                        "Cannot find a node in a cluster via a memberState: %s",
                        memberState
                    )
                )
            );
    }

    private Stream<MongoNode> mongoNodes(
        final List<MongoNode> mongoNodes,
        final ReplicaSetMemberState memberState
    ) {
        validateFaultToleranceTestSupportAvailability();

        Objects.requireNonNull(memberState, "mongoNodes is not supposed to be null");
        Objects.requireNonNull(memberState, "memberState is not supposed to be null");

        return mongoNodes.stream()
            .filter(n -> memberState.equals(n.getState()));
    }

    public List<ReplicaSetMemberState> nodeStates(List<MongoNode> mongoNodes) {
        return mongoNodes.stream()
            .map(MongoNode::getState)
            .collect(Collectors.toList());
    }
}
