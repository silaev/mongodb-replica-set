package com.silaev.mongodb.replicaset;

import com.silaev.mongodb.replicaset.converter.impl.StringToMongoRsStatusConverter;
import com.silaev.mongodb.replicaset.converter.impl.UserInputToApplicationPropertiesConverter;
import com.silaev.mongodb.replicaset.exception.IncorrectUserInputException;
import com.silaev.mongodb.replicaset.exception.MongoNodeInitializationException;
import com.silaev.mongodb.replicaset.model.ApplicationProperties;
import com.silaev.mongodb.replicaset.model.MongoDbVersion;
import com.silaev.mongodb.replicaset.model.MongoRsStatus;
import com.silaev.mongodb.replicaset.model.MongoSocketAddress;
import com.silaev.mongodb.replicaset.model.UserInputProperties;
import com.silaev.mongodb.replicaset.util.StringUtils;
import lombok.Builder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startable;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
 * import com.silaev.mongodb.replicaset.MongoDbReplicaSet;
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
 * import com.silaev.mongodb.replicaset.MongoDbReplicaSet;
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
    static final int ERROR_CONTAINER_EXIT_CODE = 1;
    static final String STATUS_COMMAND = "rs.status()";
    private static final String CLASS_NAME = MongoDbReplicaSet.class.getCanonicalName();
    private static final String LOCALHOST = "localhost";
    private static final String DOCKER_HOST_WORKAROUND = "dockerhost";
    private static final String DOCKER_HOST_INTERNAL = "host.docker.internal";
    private static final boolean USE_HOST_WORKAROUND = true;
    private static final int MONGO_DB_INTERNAL_PORT = 27017;
    private static final String MONGO_ARBITER_NODE_NAME = "mongo-arbiter";
    private static final String DOCKER_HOST_CONTAINER_NAME = "qoomon/docker-host:2.3.0";
    private static final MongoDbVersion FIRST_SUPPORTED_MONGODB_VERSION =
        MongoDbVersion.of(3, 6, 14);
    private static final boolean MOVE_FORWARD = true;
    private static final boolean STOP_PIPELINE = false;
    private final StringToMongoRsStatusConverter statusConverter;
    private final ApplicationProperties properties;
    /*
     * GenericContainer, not Startable, because there is a need to execute commands after starting
     */
    private final ConcurrentMap<MongoSocketAddress, GenericContainer> workingNodeStore;
    private final ConcurrentMap<String, Startable> supplementaryNodeStore;

    @Builder
    private MongoDbReplicaSet(
        final Integer replicaSetNumber,
        final Integer awaitNodeInitAttempts,
        final String propertyFileName,
        final String mongoDockerImageName,
        final Boolean addArbiter
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
                .build()
        );
        this.statusConverter = new StringToMongoRsStatusConverter();
        this.workingNodeStore = new ConcurrentHashMap<>();
        this.supplementaryNodeStore = new ConcurrentHashMap<>();
    }

    MongoDbReplicaSet(
        final StringToMongoRsStatusConverter statusConverter
    ) {
        val propertyConverter =
            new UserInputToApplicationPropertiesConverter();

        this.properties = propertyConverter.convert(
            UserInputProperties.builder().build()
        );
        this.statusConverter = statusConverter;
        this.workingNodeStore = new ConcurrentHashMap<>();
        this.supplementaryNodeStore = new ConcurrentHashMap<>();
    }

    public String getReplicaSetUrl() {
        if (workingNodeStore.isEmpty()) {
            throw new IllegalStateException(
                String.format("Please, start %s first", CLASS_NAME)
            );
        }
        return buildMongoRsUrl();
    }

    public MongoRsStatus getMongoRsStatus() {
        if (workingNodeStore.isEmpty()) {
            throw new IllegalStateException(
                String.format("Please, start %s first", CLASS_NAME)
            );
        }
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
            supplementaryNodeStore.values().stream(),
            workingNodeStore.values().stream()
        ).forEach(Startable::stop);
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

        val dockerHostName = getDockerHostName();
        val network = Network.newNetwork();
        val replicaSetNumber = properties.getReplicaSetNumber();

        GenericContainer mongoContainer = null;
        for (int i = replicaSetNumber - 1; i >= 0; i--) {
            mongoContainer = getAndStartMongoDbContainer(network);
            val mongoSocketAddress = getMongoSocketAddress(
                mongoContainer.getContainerIpAddress(),
                mongoContainer.getMappedPort(MONGO_DB_INTERNAL_PORT)
            );
            workingNodeStore.putIfAbsent(mongoSocketAddress, mongoContainer);
        }

        if (Objects.isNull(mongoContainer)) {
            throw new IllegalStateException("MongoDb container is not supposed to be null");
        }

        if (LOCALHOST.equals(getHostIpAddress()) && getReplicaSetNumber() > 1) {
            warnAboutTheNeedToModifyHostFile();
            supplementaryNodeStore.putIfAbsent(
                DOCKER_HOST_WORKAROUND,
                getAndRunDockerHostContainer(network, dockerHostName)
            );
        }

        val awaitNodeInitAttempts = properties.getAwaitNodeInitAttempts();

        val masterNode = initMasterNode(mongoContainer, awaitNodeInitAttempts);

        if (properties.isAddArbiter()) {
            addArbiterNode(network, masterNode, awaitNodeInitAttempts);
        }

        log.debug(
            "REPLICA SET STATUS:\n{}",
            execMongoDbCommandInContainer(mongoContainer, STATUS_COMMAND).getStdout()
        );
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
        val execResultWaitForAnyMaster = waitForMongoMasterNodeInit(
            mongoContainer,
            "typeof rs.status().members.find(o => o.state == 1) === 'undefined'",
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
        val execResultWaitForMaster = waitForMongoMasterNodeInit(
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

    private GenericContainer findMasterElected(final GenericContainer mongoContainer) {
        val execResultMasterAddress = execMongoDbCommandInContainer(
            mongoContainer,
            "rs.status().members.find(o => o.state == 1).name"
        );
        checkMongoNodeExitCode(execResultMasterAddress, "finding a master node");
        val mongoSocketAddress =
            Optional.ofNullable(statusConverter.formatToJsonString(execResultMasterAddress.getStdout()))
                .map(StringUtils::getArrayByDelimiter)
                .map(a -> {
                        val port = Integer.parseInt(a[1]);
                        return MongoSocketAddress.of(a[0], port, port);
                    }
                ).orElseThrow(
                () -> new IllegalArgumentException("Cannot find an address in MongoDb reply")
            );
        log.debug("Found the master elected: {}", mongoSocketAddress);

        return Optional.ofNullable(workingNodeStore.get(mongoSocketAddress))
            .orElseThrow(() -> new IllegalStateException(
                    String.format("Cannot find a master node in a local store by %s", mongoSocketAddress)
                )
            );
    }

    private GenericContainer checkAndGetMasterNodeInSingleNodeReplicaSet(
        final GenericContainer mongoContainer,
        final int awaitNodeInitAttempts
    ) {
        log.debug("Awaiting a master node, up to {} attempts", awaitNodeInitAttempts);
        val execResultWaitForMaster = waitForMongoMasterNodeInit(
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
                ? MongoSocketAddress.of(LOCALHOST, MONGO_DB_INTERNAL_PORT, port)
                : MongoSocketAddress.of(getDockerHostName(), port, port);
        } else {
            return MongoSocketAddress.of(containerIpAddress, port, port);
        }
    }

    /**
     * In general, avoid deploying more than one arbiter per replica set.
     */
    private void addArbiterNode(
        final Network network,
        final GenericContainer masterNode,
        final int awaitNodeInitAttempts
    ) {
        log.debug("Awaiting an arbiter node to be available, up to {} attempts", properties.getAwaitNodeInitAttempts());

        val mongoContainerArbiter = getAndStartMongoDbContainer(network);
        supplementaryNodeStore.putIfAbsent(MONGO_ARBITER_NODE_NAME, mongoContainerArbiter);
        val mongoSocketAddress = getMongoSocketAddress(
            mongoContainerArbiter.getContainerIpAddress(),
            mongoContainerArbiter.getMappedPort(MONGO_DB_INTERNAL_PORT)
        );
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

        val execResultWaitArbiter = waitForMongoMasterNodeInit(
            masterNode,
            "typeof rs.status().members.find(o => o.state == 7) === 'undefined'",
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
        if (execResultWaitForMaster.getExitCode() == ERROR_CONTAINER_EXIT_CODE) {
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
        if (execResult.getExitCode() == ERROR_CONTAINER_EXIT_CODE) {
            val errorMessage = String.format(
                "Error occurred while %s: %s",
                commandDescription,
                execResult.getStderr()
            );
            log.error(errorMessage);
            throw new MongoNodeInitializationException(errorMessage);
        }
    }

    private Container.ExecResult waitForMongoMasterNodeInit(
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

    private String buildMongoRsUrl() {
        return workingNodeStore.keySet().stream()
            .map(a -> String.format(
                "%s:%d",
                a.getIp(), a.getMappedPort()
                )
            ).collect(Collectors.joining(
                ",",
                "mongodb://",
                "/test" + (getReplicaSetNumber() == 1 ? "" : "?replicaSet=docker-rs")
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
    private GenericContainer<?> getAndRunDockerHostContainer(
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
    private GenericContainer<?> getAndStartMongoDbContainer(final Network network) {
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
}
