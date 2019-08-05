/* Copyright 2019 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.pce;

import org.openkilda.config.provider.ConfigurationProvider;
import org.openkilda.config.provider.PropertiesBasedConfigurationProvider;
import org.openkilda.model.FlowPath;
import org.openkilda.model.Isl;
import org.openkilda.model.IslStatus;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeatures;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.Neo4jConfig;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchFeaturesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.impl.Neo4jSessionFactory;
import org.openkilda.persistence.spi.PersistenceProvider;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.neo4j.ogm.testutil.TestServer;

import java.util.ArrayList;
import java.util.List;

public class NetworkBaseTest {

    protected static TestServer testServer;
    protected static TransactionManager txManager;
    protected static SwitchRepository switchRepository;
    protected static SwitchFeaturesRepository switchFeaturesRepository;
    protected static IslRepository islRepository;
    protected static FlowRepository flowRepository;
    protected static FlowPathRepository flowPathRepository;
    protected static RepositoryFactory repositoryFactory;

    protected static PathComputerConfig config;

    protected static AvailableNetworkFactory availableNetworkFactory;
    protected static PathComputerFactory pathComputerFactory;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setUpOnce() {
        testServer = new TestServer(true, true, 5);

        PersistenceManager persistenceManager = PersistenceProvider.getInstance().createPersistenceManager(
                new ConfigurationProvider() { //NOSONAR
                    @SuppressWarnings("unchecked")
                    @Override
                    public <T> T getConfiguration(Class<T> configurationType) {
                        if (configurationType.equals(Neo4jConfig.class)) {
                            return (T) new Neo4jConfig() {
                                @Override
                                public String getUri() {
                                    return testServer.getUri();
                                }

                                @Override
                                public String getLogin() {
                                    return testServer.getUsername();
                                }

                                @Override
                                public String getPassword() {
                                    return testServer.getPassword();
                                }

                                @Override
                                public int getConnectionPoolSize() {
                                    return 50;
                                }

                                @Override
                                public String getIndexesAuto() {
                                    return "update";
                                }
                            };
                        } else {
                            throw new UnsupportedOperationException("Unsupported configurationType "
                                    + configurationType);
                        }
                    }
                });

        txManager = persistenceManager.getTransactionManager();

        repositoryFactory = persistenceManager.getRepositoryFactory();
        switchRepository = repositoryFactory.createSwitchRepository();
        switchFeaturesRepository = repositoryFactory.createSwitchFeaturesRepository();
        islRepository = repositoryFactory.createIslRepository();
        flowRepository = repositoryFactory.createFlowRepository();
        flowPathRepository = repositoryFactory.createFlowPathRepository();

        config = new PropertiesBasedConfigurationProvider().getConfiguration(PathComputerConfig.class);

        availableNetworkFactory = new AvailableNetworkFactory(config, repositoryFactory);
        pathComputerFactory = new PathComputerFactory(config, availableNetworkFactory);
    }

    @AfterClass
    public static void tearDown() {
        testServer.shutdown();
    }

    @After
    public void cleanUp() {
        ((Neo4jSessionFactory) txManager).getSession().purgeDatabase();
    }

    protected Switch createSwitch(String name) {
        Switch sw = Switch.builder().switchId(new SwitchId(name)).status(SwitchStatus.ACTIVE)
                .build();

        switchRepository.createOrUpdate(sw);
        SwitchFeatures switchFeatures = SwitchFeatures.builder().switchObj(sw)
                .supportedTransitEncapsulation(SwitchFeatures.DEFAULT_FLOW_ENCAPSULATION_TYPES).build();
        switchFeaturesRepository.createOrUpdate(switchFeatures);
        return sw;
    }

    protected Isl createIsl(Switch srcSwitch, Switch dstSwitch, IslStatus status, IslStatus actual,
                            int cost, long bw, int port) {
        Isl isl = new Isl();
        isl.setSrcSwitch(srcSwitch);
        isl.setDestSwitch(dstSwitch);
        isl.setStatus(status);
        isl.setActualStatus(actual);
        isl.setCost(cost);
        isl.setAvailableBandwidth(bw);
        isl.setLatency(5);
        isl.setSrcPort(port);
        isl.setDestPort(port);

        islRepository.createOrUpdate(isl);
        return isl;
    }

    protected FlowPath addPathSegment(FlowPath flowPath, Switch src, Switch dst, int srcPort, int dstPort) {
        PathSegment ps = new PathSegment();
        ps.setSrcSwitch(src);
        ps.setDestSwitch(dst);
        ps.setSrcPort(srcPort);
        ps.setDestPort(dstPort);
        ps.setLatency(null);
        List<PathSegment> segments = new ArrayList<>(flowPath.getSegments());
        segments.add(ps);
        flowPath.setSegments(segments);
        return flowPath;
    }
}
