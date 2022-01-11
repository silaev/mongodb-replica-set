package com.github.silaev.mongodb.replicaset;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Info;
import com.github.silaev.mongodb.replicaset.converter.impl.MongoNodeToMongoSocketAddressConverter;
import com.github.silaev.mongodb.replicaset.converter.impl.StringToMongoRsStatusConverter;
import com.github.silaev.mongodb.replicaset.converter.impl.UserInputToApplicationPropertiesConverter;
import com.github.silaev.mongodb.replicaset.core.Generated;
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
import eu.rekawek.toxiproxy.model.ToxicDirection;
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
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
 * <p>See examples on <a href="https://github.com/silaev/mongodb-replica-set">mongodb-replica-set on github</a>
 *
 * @author Konstantin Silaev
 */
@Slf4j
public class MongoDbReplicaSet implements Startable, AutoCloseable {
    public static final int MAX_VOTING_MEMBERS = 7;
    public static final Comparator<MongoSocketAddress> COMPARATOR_MAPPED_PORT = Comparator.comparing(MongoSocketAddress::getMappedPort);
    public static final String RECONFIG_RS_MSG = "Reconfiguring a replica set";
    public static final String WAITING_MSG = "Waiting for";
    static final String STATUS_COMMAND = "rs.status()";
    static final int CONTAINER_EXIT_CODE_OK = 0;
    private static final String SHOPIFY_TOXIPROXY_IMAGE = "shopify/toxiproxy:2.1.3";
    private static final String DEAD_LETTER_DB_NAME = "dead_letter";
    private static final String CLASS_NAME = MongoDbReplicaSet.class.getCanonicalName();
    private static final String LOCALHOST = "localhost";
    private static final String DOCKER_HOST_WORKAROUND = "dockerhost";
    private static final String DOCKER_HOST_INTERNAL = "host.docker.internal";
    private static final int MONGO_DB_INTERNAL_PORT = 27017;
    private static final String MONGO_ARBITER_NODE_NAME = "mongo-arbiter";
    private static final String DOCKER_HOST_CONTAINER_NAME = "qoomon/docker-host:2.4.0";
    private static final String TOXIPROXY_CONTAINER_NAME = "toxiproxy";
    private static final MongoDbVersion FIRST_SUPPORTED_MONGODB_VERSION =
        MongoDbVersion.of(3, 6, 14);
    private static final boolean MOVE_FORWARD = true;
    private static final boolean STOP_PIPELINE = false;
    private static final String READ_PREFERENCE_PRIMARY = "primary";
    private static final String RS_STATUS_MEMBERS_DEFINED_CONDITION = "rs.status().ok === 1 && rs.status().members !== undefined && ";
    private static final String RS_EXCEPTION = "throw new Error('Replica set status is not ok, errmsg: ' + rs.status().errmsg +" +
        " ', codeName: ' + rs.status().codeName);";
    private static final String MONGODB_DATABASE_NAME_DEFAULT = "test";
    private static final int RECONFIG_MAX_TIME_MS = 10000;
    private final StringToMongoRsStatusConverter statusConverter;
    private final MongoNodeToMongoSocketAddressConverter socketAddressConverter;
    private final ApplicationProperties properties;
    /*
     * GenericContainer, not Startable, because there is a need to execute commands after starting
     */
    private final NavigableMap<MongoSocketAddress, GenericContainer> workingNodeStore;
    private final Map<MongoSocketAddress, ToxiproxyContainer.ContainerProxy> toxyNodeStore;
    private final Map<String, Pair<GenericContainer, MongoSocketAddress>> supplementaryNodeStore;
    private final Map<MongoSocketAddress, Pair<Boolean, GenericContainer>> disconnectedNodeStore;
    private final Network network;

    @Builder
    @SuppressWarnings("unused")
    private MongoDbReplicaSet(
        final Integer replicaSetNumber,
        final Integer awaitNodeInitAttempts,
        final String propertyFileName,
        final String mongoDockerImageName,
        final Boolean addArbiter,
        final Boolean addToxiproxy,
        final Integer slaveDelayTimeout,
        final Integer slaveDelayNumber,
        final Boolean useHostDockerInternal,
        final List<String> commandLineOptions
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
                .slaveDelayTimeout(slaveDelayTimeout)
                .slaveDelayNumber(slaveDelayNumber)
                .useHostDockerInternal(useHostDockerInternal)
                .commandLineOptions(commandLineOptions)
                .build()
        );
        this.statusConverter = new StringToMongoRsStatusConverter();
        this.socketAddressConverter = new MongoNodeToMongoSocketAddressConverter();
        this.workingNodeStore = new ConcurrentSkipListMap<>(COMPARATOR_MAPPED_PORT);
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
     * @param network
     */
    MongoDbReplicaSet(
        final StringToMongoRsStatusConverter statusConverter,
        NavigableMap<MongoSocketAddress, GenericContainer> workingNodeStore,
        Map<String, Pair<GenericContainer, MongoSocketAddress>> supplementaryNodeStore,
        Map<MongoSocketAddress, Pair<Boolean, GenericContainer>> disconnectedNodeStore,
        Map<MongoSocketAddress, ToxiproxyContainer.ContainerProxy> toxyNodeStore,
        Network network
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
        this.network = network;
    }

    @Override
    public void close() {
        stop();
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
        disconnectedNodeStore.clear();
        supplementaryNodeStore.clear();
        workingNodeStore.clear();
        toxyNodeStore.clear();
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

    public int getSlaveDelayTimeout() {
        return properties.getSlaveDelayTimeout();
    }

    public int getSlaveDelayNumber() {
        return properties.getSlaveDelayNumber();
    }

    public boolean getUseHostDockerInternal() {
        return properties.isUseHostDockerInternal();
    }

    private String getDockerHostName() {
        return getUseHostDockerInternal() ? DOCKER_HOST_INTERNAL : DOCKER_HOST_WORKAROUND;
    }

    private String[] buildMongoEvalCommand(final String command) {
        return new String[]{"mongo", "--eval", command};
    }

    @Override
    public synchronized void start() {
        if (properties.isEnabled()) {
            int attempt = 0;
            Exception lastException = null;
            boolean doContinue = true;
            final int maxAttempts = 3;
            while (doContinue && attempt < maxAttempts) {
                log.debug("Provisioning a replica set, attempt: {} out of {}. Please, wait.", attempt + 1, maxAttempts);
                try {
                    startInternal();
                    doContinue = false;
                } catch (IncorrectUserInputException e) {
                    throw e;
                } catch (Exception e) {
                    stop();
                    lastException = e;
                    attempt++;
                }
            }
            if (doContinue) {
                throw new MongoNodeInitializationException("Retry limit hit with exception", lastException);
            }
        } else {
            log.info("{} is disabled", CLASS_NAME);
        }
    }

    public void startInternal() {
        decideOnDockerHost();
        final boolean addExtraHost = shouldAddExtraHost();

        ToxiproxyContainer toxiproxyContainer = null;
        if (getAddToxiproxy()) {
            toxiproxyContainer = getAndStartToxiproxyContainer();
            supplementaryNodeStore.put(TOXIPROXY_CONTAINER_NAME, Pair.of(toxiproxyContainer, null));
        }
        val replicaSetNumber = properties.getReplicaSetNumber();

        for (int i = 0; i < replicaSetNumber; i++) {
            GenericContainer mongoContainer = getAndStartMongoDbContainer(network, addExtraHost);

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

        final GenericContainer mongoContainer = workingNodeStore.firstEntry().getValue();
        if (Objects.isNull(mongoContainer)) {
            throw new IllegalStateException("MongoDb container is not supposed to be null");
        }

        val awaitNodeInitAttempts = getAwaitNodeInitAttempts();

        val masterNode = initMasterNode(mongoContainer, awaitNodeInitAttempts);

        if (getAddArbiter()) {
            addArbiterNode(network, toxiproxyContainer, masterNode, awaitNodeInitAttempts, addExtraHost);
        }

        log.debug(
            "REPLICA SET STATUS:\n{}",
            execMongoDbCommandInContainer(mongoContainer, STATUS_COMMAND).getStdout()
        );
    }

    private void decideOnDockerHost() {
        if (!getUseHostDockerInternal() && getReplicaSetNumber() > 1 && LOCALHOST.equals(getHostIpAddress())) {
            warnAboutTheNeedToModifyHostFile();
            supplementaryNodeStore.put(
                DOCKER_HOST_WORKAROUND,
                Pair.of(getAndRunDockerHostContainer(network, getDockerHostName()), null)
            );
        }
    }

    private boolean shouldAddExtraHost() {
        boolean addExtraHost = false;
        if (getUseHostDockerInternal() && getReplicaSetNumber() > 1) {
            final Pair<String, String> ipAddressAndOS = getHostIpAddressAndOS();
            final boolean isLinux = !ipAddressAndOS.getRight().toLowerCase(Locale.ENGLISH).contains("docker desktop");
            if (isLinux && LOCALHOST.equals(ipAddressAndOS.getLeft())) {
                addExtraHost = true;
            }
        }
        return addExtraHost;
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
            log.debug(
                "Real port: {}, proxy port: {}",
                mongoContainer.getMappedPort(MONGO_DB_INTERNAL_PORT),
                port
            );
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

    @NonNull
    private Pair<String, String> getHostIpAddressAndOS() {
        try {
            final DockerClientFactory clientFactory = DockerClientFactory.instance();
            final DockerClient client = clientFactory.client();
            final Info dockerInfo = client.infoCmd().exec();
            final String hostIp = clientFactory.dockerHostIpAddress();
            Objects.requireNonNull(hostIp, "DockerClient: dockerHostIpAddress is not supposed to be null");
            final String os = dockerInfo.getOperatingSystem();
            Objects.requireNonNull(os, "DockerClient: operatingSystem is not supposed to be null");
            return Pair.of(hostIp, os);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot getHostIpAddressAndOS", e);
        }
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
        log.debug("initMasterNode => execResultInitRs: {}", stdoutInitRs);
        checkMongoNodeExitCodeAndStatus(
            execResultInitRs,
            "initializing a master node"
        );
        verifyVersion(stdoutInitRs);

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

    /**
     * All the conditions must be true to stop a loop.
     *
     * @param condition a condition
     * @return a combined negated condition for a loop
     */
    private String buildWaitStopCondition(String condition) {
        return "!(" + RS_STATUS_MEMBERS_DEFINED_CONDITION + condition + ")";
    }

    private GenericContainer checkAndGetMasterNodeInMultiNodeReplicaSet(
        final GenericContainer mongoContainer,
        final int awaitNodeInitAttempts
    ) {
        log.debug("Searching for a master node in a replica set, up to {} attempts", awaitNodeInitAttempts);
        val execResultWaitForAnyMaster = waitForCondition(
            mongoContainer,
            buildWaitStopCondition("rs.status().members.filter(o => o.state === 1).length === 1"),
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
        log.debug("Waiting for a single master node up to {} attempts", getAwaitNodeInitAttempts());
        val execResultMasterAddress = execMongoDbCommandInContainer(
            mongoContainer,
            String.format(
                "var attempt = 0; " +
                    "while (attempt <= %d) { " +
                    "print('%s a single master node up to ' + attempt); sleep(1000); attempt++;" +
                    "if (%s rs.status().members.filter(o => o.state === 1).length === 1) " +
                    "{ rs.status().members.find(o => o.state === 1).name; break; }}; " +
                    "if(attempt > %d) {quit(1)};",
                getAwaitNodeInitAttempts(),
                WAITING_MSG,
                RS_STATUS_MEMBERS_DEFINED_CONDITION,
                getAwaitNodeInitAttempts()
            )
        );
        checkMongoNodeExitCode(execResultMasterAddress, "finding a master node");
        val stdout = execResultMasterAddress.getStdout();
        final MongoSocketAddress mongoSocketAddress =
            Optional.ofNullable(statusConverter.extractRawPayloadFromMongoDBShell(stdout))
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
                        String.format("Cannot find an address in a MongoDb reply:%n %s", stdout)
                    )
                );
        log.debug("Found the master elected: {}", mongoSocketAddress);

        return extractGenericContainer(mongoSocketAddress, workingNodeStore);
    }

    /**
     * Generates if-then-else JS clause.
     *
     * @param condition  on if statement
     * @param thenClause on if statement
     * @return generated if-then-else JS clause
     */
    @NotNull
    private String buildJsIfStatement(final String condition, final String thenClause) {
        return String.format("if (%s) {%s} else {%s}", condition, thenClause, RS_EXCEPTION);
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
            awaitNodeInitAttempts, WAITING_MSG + " a node to be a master one"
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
        final int awaitNodeInitAttempts,
        boolean addExtraHost
    ) {
        log.debug("Awaiting an arbiter node to be available, up to {} attempts", properties.getAwaitNodeInitAttempts());

        val mongoContainerArbiter = getAndStartMongoDbContainer(network, addExtraHost);
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
                "%srs.addArb(\"%s:%d\")",
                getDefaultConcernsCommand(),
                mongoSocketAddress.getIp(),
                mongoSocketAddress.getReplSetPort()
            )
        );
        log.debug("Add an arbiter node result: {}", execResultAddArbiter.getStdout());
        checkMongoNodeExitCodeAndStatus(
            execResultAddArbiter,
            "initializing an arbiter node"
        );

        val execResultWaitArbiter = waitForCondition(
            masterNode,
            buildWaitStopCondition("rs.status().members.find(o => o.state === 7) !== undefined"),
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

    @SneakyThrows
    void checkMongoNodeExitCode(
        final Container.ExecResult execResult,
        final String commandDescription
    ) {
        Objects.requireNonNull(execResult);
        val stdout = execResult.getStdout();
        Objects.requireNonNull(stdout);
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

    @SneakyThrows
    void checkMongoNodeExitCodeAndStatus(
        final Container.ExecResult execResult,
        final String commandDescription
    ) {
        Objects.requireNonNull(execResult);
        val stdout = execResult.getStdout();
        Objects.requireNonNull(stdout);
        if (execResult.getExitCode() != CONTAINER_EXIT_CODE_OK || statusConverter.convert(stdout).getStatus() != 1) {
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
                "print('%s ' + attempt); sleep(1000); attempt++; " +
                " }",
            condition, attempts, waitingMessage
        );
    }

    private String getMongoReplicaSetInitializer() {
        val addresses = workingNodeStore.keySet().toArray(new MongoSocketAddress[0]);
        val length = addresses.length;
        val slaveDelayTimeout = getSlaveDelayTimeout();
        val workingNodeNumber = getReplicaSetNumber() + (getAddArbiter() ? 1 : 0) - getSlaveDelayNumber();

        String replicaSetInitializer = IntStream.range(0, length)
            .mapToObj(i -> {
                    val address = addresses[i];
                    if (slaveDelayTimeout > 0 && i > workingNodeNumber - 1) {
                        return String.format(
                            "        {\"_id\": %d, \"host\": \"%s:%d\", \"slaveDelay\":%d, \"priority\": 0, \"hidden\": true}",
                            i, address.getIp(), address.getReplSetPort(), slaveDelayTimeout
                        );
                    } else {
                        return String.format(
                            "        {\"_id\": %d, \"host\": \"%s:%d\"}",
                            i, address.getIp(), address.getReplSetPort()
                        );
                    }

                }
            ).collect(Collectors.joining(
                ",\n",
                "rs.initiate({\n" +
                    "    \"_id\": \"docker-rs\",\n" +
                    "    \"members\": [\n",
                "\n    ]\n});"
                )
            );
        log.debug("replicaSetInitializer: {}", replicaSetInitializer);
        return "cfg = " + replicaSetInitializer + buildJsIfStatement("cfg.ok===1", "cfg");
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
                    String.format("/%s%s&readPreference=%s",
                        MONGODB_DATABASE_NAME_DEFAULT,
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
     * @see <a href="https://docs.docker.com/engine/api/v1.41/#operation/ContainerCreate">Docker API Create a container</a>
     */
    @SuppressWarnings("java:S2095")
    private @NonNull GenericContainer getAndRunDockerHostContainer(
        final Network network,
        final String dockerHostName
    ) {
        final GenericContainer dockerHostContainer = new GenericContainer<>(
            DOCKER_HOST_CONTAINER_NAME
        ).withCreateContainerCmdModifier(
            it -> it.withHostConfig(
                HostConfig.newHostConfig()
                    .withCapAdd(Capability.NET_ADMIN, Capability.NET_RAW)
                    .withNetworkMode(network.getId())
            )
        ).withNetwork(network)
            .withNetworkAliases(dockerHostName)
            .waitingFor(
                Wait.forLogMessage(".*Forwarding ports.*", 1)
            );
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
    @SuppressWarnings("java:S2095")
    private @NonNull GenericContainer getAndStartMongoDbContainer(
        final Network network,
        final boolean addExtraHost
    ) {
        final String[] commands = Stream.concat(
            Stream.of("--bind_ip", "0.0.0.0", "--replSet", "docker-rs"),
            properties.getCommandLineOptions().stream()
        ).toArray(String[]::new);
        final GenericContainer mongoDbContainer = new GenericContainer<>(
            properties.getMongoDockerImageName()
        ).withNetwork(getReplicaSetNumber() == 1 ? null : network)
            .withExposedPorts(MONGO_DB_INTERNAL_PORT)
            .withCommand(commands)
            .waitingFor(
                Wait.forListeningPort()
            ).withStartupTimeout(Duration.ofSeconds(60))
            .withStartupAttempts(3);
        if (addExtraHost) {
            mongoDbContainer.withExtraHost(DOCKER_HOST_INTERNAL, "host-gateway");
        }
        mongoDbContainer.start();
        return mongoDbContainer;
    }

    @SuppressWarnings("java:S2095")
    private @NonNull ToxiproxyContainer getAndStartToxiproxyContainer() {
        final ToxiproxyContainer toxiproxy = new ToxiproxyContainer(SHOPIFY_TOXIPROXY_IMAGE)
            .withNetwork(network)
            .withStartupTimeout(Duration.ofSeconds(60))
            .withStartupAttempts(3);
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
     * Adds latency to the downstream of a node
     *
     * @param mongoNode a node to disconnect
     * @param latency   in milliseconds
     * @see <a href="https://github.com/Shopify/toxiproxy#latency">toxiproxy latency</a>
     */
    @Generated
    void addLatencyToDownstream(
        final MongoNode mongoNode,
        long latency
    ) {
        validateFaultToleranceTestSupportAvailability();

        val mongoSocketAddress = socketAddressConverter.convert(mongoNode);
        try {
            extractGenericContainer(mongoSocketAddress, toxyNodeStore)
                .toxics()
                .latency(
                    String.format("ADD_LATENCY_DOWNSTREAM_%d", mongoSocketAddress.getMappedPort()),
                    ToxicDirection.DOWNSTREAM,
                    latency
                ).setLatency(latency);
        } catch (IOException e) {
            throw new RuntimeException("Could not control proxy", e);
        }
    }

    @Generated
    void removeLatencyFromDownstream(
        final MongoNode mongoNode
    ) {
        validateFaultToleranceTestSupportAvailability();

        val mongoSocketAddress = socketAddressConverter.convert(mongoNode);
        try {
            extractGenericContainer(mongoSocketAddress, toxyNodeStore)
                .toxics()
                .get(String.format("ADD_LATENCY_DOWNSTREAM_%d", mongoSocketAddress.getMappedPort()))
                .remove();
        } catch (IOException e) {
            throw new RuntimeException("Could not control proxy", e);
        }
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
        reconfigureReplSetRemoveDownAndUnknownNodes();

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
     * Connects a Mongo node (a Docker container) back to its network.
     * Generally should be used in case of soft connections (with Toxyproxy).
     *
     * @param mongoNode a node to connect.
     */
    public synchronized void connectNodeToNetwork(
        final MongoNode mongoNode
    ) {
        connectNodeToNetwork(mongoNode, true, false);
    }

    /**
     * Connects a Mongo node (a Docker container) back to its network.
     * Generally should be used in case of hard connections (without Toxyproxy).
     * Beware that a container port changes after this operation because of a container restart.
     * Internally removes a disconnected node via forcing reconfiguration.
     *
     * @param mongoNode a node to connect.
     */
    public synchronized void connectNodeToNetworkWithForceRemoval(
        final MongoNode mongoNode
    ) {
        connectNodeToNetwork(mongoNode, true, true);
    }

    /**
     * Connects a Mongo node (a Docker container) back to its network.
     * Generally should be used in case of hard connections (without Toxyproxy).
     * Beware that a container port changes after this operation because of a container restart.
     * Internally removes a disconnected node via rs.remove.
     *
     * @param mongoNode a node to connect.
     */
    public synchronized void connectNodeToNetworkWithoutRemoval(
        final MongoNode mongoNode
    ) {
        connectNodeToNetwork(mongoNode, false, false);
    }

    /**
     * Connects a Mongo node (a Docker container) back to its network.
     * In case of hard disconnection (without Toxyproxy) beware that a container port
     * changes after this operation because of a container restart.
     * In case of soft one (with Toxyproxy) calls setConnectionCut(false).
     *
     * @param mongoNode a node to connect.
     * @param remove    Only relevant to hard disconnection (without Toxyproxy). Whether to remove a disconnected mongo node.
     * @param force     Only relevant to hard disconnection (without Toxyproxy).
     *                  If true then re-configures with force to remove a node, otherwise calls rs.remove.
     * @see <a href="https://docs.docker.com/engine/reference/commandline/network_connect/">docker network connect</a>
     */
    private synchronized void connectNodeToNetwork(
        final MongoNode mongoNode,
        final boolean remove,
        final boolean force
    ) {
        validateFaultToleranceTestSupportAvailability();
        verifyWorkingNodeStoreIsNotEmpty();

        val disconnectedMongoSocketAddress = socketAddressConverter.convert(mongoNode);
        final Pair<Boolean, GenericContainer> pair =
            extractGenericContainer(disconnectedMongoSocketAddress, disconnectedNodeStore);
        val isWorkingNode = pair.getLeft();
        val disconnectedNode = pair.getRight();

        if (getAddToxiproxy()) {
            if (force) {
                throw new IllegalArgumentException("addToxiproxy does not work with force");
            }
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

            if (remove) {
                if (force) {
                    removeNodeFromReplSetConfigWithForce(disconnectedMongoSocketAddress, masterNode);
                } else {
                    removeNodeFromReplSetConfig(disconnectedMongoSocketAddress, masterNode);
                }
            }

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
        if (getMongoRsStatus().getVersion().getMajor() >= 5 && getAddArbiter()) {
            reconfigureReplSetForPSA(mongoSocketAddress, isWorkingNode);
        } else {
            if (Boolean.TRUE.equals(isWorkingNode)) {
                addWorkingNodeToReplSetConfig(masterNode, mongoSocketAddress);
            } else {
                addArbiterNodeToReplSetConfig(masterNode, mongoSocketAddress);
            }
        }
    }

    public void reconfigureReplSetForPSA(final MongoSocketAddress mongoSocketAddress, Boolean isWorkingNode) {
        val replicaSetReConfig = getConfigForPSA(mongoSocketAddress, isWorkingNode);
        log.debug("Reconfiguring for PSA a node : {}", replicaSetReConfig);
        val execResult = execMongoDbCommandInContainer(
            workingNodeStore.values().iterator().next(),
            replicaSetReConfig
        );
        log.debug(execResult.getStdout());

        checkMongoNodeExitCode(execResult, RECONFIG_RS_MSG);
    }

    private String getConfigForPSA(final MongoSocketAddress mongoSocketAddress, Boolean isWorkingNode) {
        val newNode = String.format(
            "JSON.parse('{\"_id\": '+max+', \"host\": \"%s\", \"arbiterOnly\": %b, \"buildIndexes\": true, \"hidden\": false, \"priority\": 1, \"votes\": 1, \"tags\": {}}')",
            mongoSocketAddress.getIp() + ":" + mongoSocketAddress.getMappedPort(),
            !isWorkingNode
        );
        return String.format("cfg=rs.config();\n" +
            "max=Math.max.apply(Math, cfg[\"members\"].map(function(o) { return o._id; }))+1;\n" +
            "cfg[\"members\"].push(%s); \n" +
            "rs.reconfigForPSASet(cfg[\"members\"].length-1, cfg);", newNode
        );
    }

    /**
     * Removes all nodes in Down and Unknown state.
     */
    public void reconfigureReplSetRemoveDownAndUnknownNodes() {
        val members = getMongoRsStatus().getMembers();
        val replicaSetReConfig = getReplicaSetReConfigRemoveDownAndUnknownNodes(members);
        log.debug("Reconfiguring a node replica set as per: {}", replicaSetReConfig);
        val execResult = execMongoDbCommandInContainer(
            workingNodeStore.values().iterator().next(),
            replicaSetReConfig
        );
        log.debug(execResult.getStdout());

        checkMongoNodeExitCode(execResult, RECONFIG_RS_MSG);
    }

    /**
     * Reconfigures a replica set by setting slaveDelay=0, priority=1 and hidden=false
     * for each node.
     */
    @SneakyThrows
    public void reconfigureReplSetToDefaults() {
        CompletableFuture<Void> cf = CompletableFuture.runAsync(this::reconfigureReplSetToDefaultsInternal);
        try {
            cf.get((long) RECONFIG_MAX_TIME_MS * 2, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new MongoNodeInitializationException("Timeout exceeded for reconfigureReplSetToDefaults", e);
        }
    }

    private void reconfigureReplSetToDefaultsInternal() {

        verifyWorkingNodeStoreIsNotEmpty();
        val replicaSetReConfig = getReplicaSetReConfigUnsetSlaveDelay();
        log.debug("Reconfiguring a replica set as per: {}", replicaSetReConfig);
        val execResult = execMongoDbCommandInContainer(
            findMasterElected(workingNodeStore.values().iterator().next()),
            replicaSetReConfig
        );
        log.debug(execResult.getStdout());

        checkMongoNodeExitCodeAndStatus(execResult, RECONFIG_RS_MSG);
    }

    @Generated
    void dropConnections(List<MongoNode> members) {
        operateOnConnections(members, 1);
    }

    @Generated
    void enableConnections(List<MongoNode> members) {
        operateOnConnections(members, 0);
    }

    @Generated
    private void operateOnConnections(List<MongoNode> members, int drop) {
        verifyWorkingNodeStoreIsNotEmpty();
        val replicaSetReConfig = getDropConnectionsCommand(members, drop);
        log.debug("Dropping connections: {}", replicaSetReConfig);
        val execResult = execMongoDbCommandInContainer(
            workingNodeStore.values().iterator().next(),
            replicaSetReConfig
        );
        log.debug(execResult.getStdout());

        checkMongoNodeExitCodeAndStatus(
            execResult,
            "Dropping connections"
        );
    }

    /**
     * New in version 4.2.
     * <p>
     * The dropConnections command drops the mongod/mongos instance’s outgoing connections to the specified hosts.
     *
     * @param members
     * @param drop
     * @return
     */
    @Generated
    private String getDropConnectionsCommand(
        final List<MongoNode> members,
        final int drop
    ) {
        return members.stream().map(member -> String.format(
            "\"%s:%d\"",
            member.getIp(), member.getPort()
        )).collect(Collectors.joining(
            ",",
            String.format("db.adminCommand({\"dropConnections\" : %d, \"hostAndPort\":[", drop),
            "]});"
            )
        );
    }

    private String getReplicaSetReConfigUnsetSlaveDelay() {
        val workingNodeNumber = getReplicaSetNumber() + (getAddArbiter() ? 1 : 0) - getSlaveDelayNumber();
        return IntStream.rangeClosed(workingNodeNumber + 1, getMongoRsStatus().getMembers().size())
            .mapToObj(
                i -> String.format(
                    "cfg.members[%d].slaveDelay=0;cfg.members[%d].priority=1;cfg.members[%d].hidden=false",
                    i - 1, i - 1, i - 1
                )
            ).collect(Collectors.joining(
                ";\n",
                "cfg = rs.conf();\n",
                String.format(";%nrs.reconfig(cfg, {force : true, maxTimeMS: %d})", RECONFIG_MAX_TIME_MS))
            );
    }

    private String getReplicaSetReConfigRemoveDownAndUnknownNodes(
        final List<MongoNode> members
    ) {
        return IntStream.range(0, members.size())
            .filter(i -> {
                val memberState = members.get(i).getState();
                return !(memberState == ReplicaSetMemberState.DOWN || memberState == ReplicaSetMemberState.UNKNOWN);
            })
            .mapToObj(i -> String.format("cfg.members[%d]", i))
            .collect(Collectors.joining(
                ",",
                "cfg = rs.conf();\ncfg.members = [",
                String.format("];%nrs.reconfig(cfg, {force : true, maxTimeMS: %d})", RECONFIG_MAX_TIME_MS))
            );
    }

    private String getReplicaSetReConfigRemoveNode(final MongoSocketAddress mongoSocketAddress) {
        final List<MongoNode> members = getMongoRsStatus().getMembers();
        return IntStream.range(0, members.size())
            .filter(i -> {
                final MongoNode mongoNode = members.get(i);
                return !(Objects.equals(mongoSocketAddress.getIp(), mongoNode.getIp())
                    && Objects.equals(mongoSocketAddress.getMappedPort(), mongoNode.getPort()));
            })
            .mapToObj(i -> String.format("cfg.members[%d]", i))
            .collect(Collectors.joining(
                ",",
                "cfg = rs.conf();\ncfg.members = [",
                String.format("];%nrs.reconfig(cfg, {force : true, maxTimeMS: %d})", RECONFIG_MAX_TIME_MS))
            );
    }

    /**
     * https://jira.mongodb.org/browse/SERVER-58964?focusedCommentId=3977410&page=com.atlassian.jira.plugin.system.issuetabpanels%3Acomment-tabpanel
     */
    private String getDefaultConcernsCommand() {
        val mongoRsStatus = getMongoRsStatus();
        return mongoRsStatus.getVersion().getMajor() >= 5 && getAddArbiter()
            ? "db.adminCommand({\"setDefaultRWConcern\" : 1, \"defaultWriteConcern\" : { \"w\" : 1 }});"
            : "";
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
        checkMongoNodeExitCodeAndStatus(
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

    public void removeNodeFromReplSetConfigWithForce(final MongoNode mongoNodeToRemove) {
        validateFaultToleranceTestSupportAvailability();
        verifyWorkingNodeStoreIsNotEmpty();

        removeNodeFromReplSetConfigWithForce(
            socketAddressConverter.convert(mongoNodeToRemove),
            findMasterElected(workingNodeStore.values().iterator().next())
        );
    }

    private void removeNodeFromReplSetConfigWithForce(
        final MongoSocketAddress mongoSocketAddressToRemove,
        final GenericContainer masterNode
    ) {
        val replicaSetReConfig = getReplicaSetReConfigRemoveNode(mongoSocketAddressToRemove);
        log.debug("Reconfiguring a node replica set as per: {}", replicaSetReConfig);
        val execResult = execMongoDbCommandInContainer(
            masterNode,
            replicaSetReConfig
        );
        log.debug(execResult.getStdout());

        checkMongoNodeExitCodeAndStatus(execResult, RECONFIG_RS_MSG);
    }

    private void removeNodeFromReplSetConfig(
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
        checkMongoNodeExitCodeAndStatus(
            execResultRemoveNode,
            "Removing a node"
        );
    }


    /**
     * Restarts a container.
     *
     * @param genericContainer to exec stop and then start.
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
            "%s the reelection of %s",
            WAITING_MSG,
            prevMasterName
        );
        val execResultMasterReelection = waitForCondition(
            workingNodeStore.values().iterator().next(),
            String.format(
                buildWaitStopCondition("rs.status().members.filter(o => o.state === 1).length === 1 && " +
                    "rs.status().members.find(o => o.state === 1 && o.name === '%s') === undefined"),
                prevMasterName
            ),
            getAwaitNodeInitAttempts() * 2,
            reelectionMessage
        );
        checkMongoNodeExitCode(execResultMasterReelection, reelectionMessage);
    }

    /**
     * Waits for a master node to be present in a cluster.
     */
    public void waitForMaster() {
        verifyWorkingNodeStoreIsNotEmpty();

        val message = WAITING_MSG + " a master node to be present in a cluster";
        val execResult = waitForCondition(
            workingNodeStore.values().iterator().next(),
            buildWaitStopCondition("rs.status().members.filter(o => o.state === 1).length === 1"),
            getAwaitNodeInitAttempts(),
            message
        );
        checkMongoNodeExitCode(execResult, message);
    }

    /**
     * Waits until a replica set has only PRIMARY, ARBITER or SECONDARY nodes.
     */
    public void waitForAllMongoNodesUp() {
        validateFaultToleranceTestSupportAvailability();
        verifyWorkingNodeStoreIsNotEmpty();

        val waitingMessage = WAITING_MSG + " all nodes are up and running";
        val execResultWaitForNodesUp = waitForCondition(
            workingNodeStore.values().iterator().next(),
            buildWaitStopCondition("rs.status().members.filter(" +
                "o => o.state === 0 || o.state === 3 || o.state === 5 || o.state === 6 || o.state === 8 || o.state === 9" +
                ").length === 0"),
            getAwaitNodeInitAttempts(),
            waitingMessage
        );
        checkMongoNodeExitCode(execResultWaitForNodesUp, waitingMessage);
    }

    /**
     * Waits until a replica set has the nodeNumber of the nodes in the DOWN state.
     *
     * @param nodeNumber a number of nodes in the DOWN state to wait for.
     */
    public void waitForMongoNodesDown(int nodeNumber) {
        validateFaultToleranceTestSupportAvailability();
        verifyWorkingNodeStoreIsNotEmpty();

        val waitingMessage = String.format("%s %d node(s) is(are) down", WAITING_MSG, nodeNumber);
        val execResultWaitForNodesUp = waitForCondition(
            workingNodeStore.values().iterator().next(),
            String.format(
                buildWaitStopCondition("rs.status().members.filter(o => o.state === 8).length === %d"),
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

    public Stream<MongoNode> mongoNodes(
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

    /**
     * Loads rollback files generated on a former master to a DEAD_LETTER_DB_NAME.
     *
     * @param mongoNode          a former master node.
     * @param collectionFullName db.collectionName, for example test.foo.
     * @return
     */
    @SneakyThrows(value = {IOException.class, InterruptedException.class})
    @Generated
    boolean loadCollectionToDeadLetterDb(
        final MongoNode mongoNode,
        final String collectionFullName
    ) {
        validateFaultToleranceTestSupportAvailability();
        verifyWorkingNodeStoreIsNotEmpty();

        val path = "/data/db/rollback/" + collectionFullName;
        val mongoSocketAddress = socketAddressConverter.convert(mongoNode);
        final GenericContainer genericContainer = extractGenericContainer(mongoSocketAddress, workingNodeStore);
        val waitForRollbackFile = genericContainer.execInContainer(
            "sh", "-c",
            String.format(
                "COUNTER=1; " +
                    "while [ $COUNTER != %d ] && [ ! -d %s ]; " +
                    "do sleep 1; " +
                    "COUNTER=$((COUNTER+1)); " +
                    "echo waiting for a rollback directory: $COUNTER up to %d; " +
                    "done",
                getAwaitNodeInitAttempts(),
                path,
                getAwaitNodeInitAttempts()
            )
        );
        log.debug(
            "waitForRollbackFile: stdout: {}, stderr: {}",
            waitForRollbackFile.getStdout(),
            waitForRollbackFile.getStderr()
        );
        final boolean waitFiled = waitForRollbackFile.getStdout().contains(
            String.format("%d up to", getAwaitNodeInitAttempts() - 1)
        );
        if (waitForRollbackFile.getExitCode() != CONTAINER_EXIT_CODE_OK || waitFiled) {
            log.debug("Cannot find any rollback file");
            return false;
        } else {
            val execResultMongorestore = genericContainer.execInContainer(
                "mongorestore",
                "--uri=" + getReplicaSetUrl(),
                "--db", DEAD_LETTER_DB_NAME,
                path
            );
            log.debug(
                "mongorestore: stdout: {}, stderr: {}",
                execResultMongorestore.getStdout(),
                execResultMongorestore.getStderr()
            );
            if (execResultMongorestore.getExitCode() != CONTAINER_EXIT_CODE_OK) {
                throw new IllegalStateException("Cannot execute mongorestore to extract rollback files");
            }
        }

        return true;
    }
}
