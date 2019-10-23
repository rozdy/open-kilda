/* Copyright 2018 Telstra Open Source
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

package org.openkilda.testing.service.lockkeeper;

import org.openkilda.northbound.dto.v1.switches.SwitchDto;
import org.openkilda.testing.model.topology.TopologyDefinition.Switch;
import org.openkilda.testing.service.floodlight.ManagementFloodlightFactory;
import org.openkilda.testing.service.floodlight.MultiFloodlightFactory;
import org.openkilda.testing.service.floodlight.StatsFloodlightFactory;
import org.openkilda.testing.service.lockkeeper.model.ASwitchFlow;
import org.openkilda.testing.service.lockkeeper.model.BlockRequest;
import org.openkilda.testing.service.lockkeeper.model.ContainerName;
import org.openkilda.testing.service.northbound.NorthboundService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This is a helper 'testing' service and should not be considered as part of Kilda.
 * Takes control over the intermediate 'A Switch' in Staging which is meant to allow to
 * disconnect ISLs.
 * Also allows to control floodlight's lifecycle.
 */
@Slf4j
@Service
@Profile("hardware")
public class LockKeeperServiceImpl implements LockKeeperService {

    @Autowired
    private NorthboundService northbound;
    @Autowired
    private ManagementFloodlightFactory mgmtFactory;
    @Autowired
    private StatsFloodlightFactory statsFactory;

    @Value("${kafka.bootstrap.server}")
    private String kafkaBootstrapServer;

    /**
     * Each floodlight host has it's own local lock-keeper service deployed in order to have full access to it.
     */
    @Autowired
    @Qualifier("lockKeeperRestTemplates")
    private Map<String, RestTemplate> restTemplatesPerRegion;

    @Override
    public void addFlows(List<ASwitchFlow> flows) {
        RestTemplate restTemplate = restTemplatesPerRegion.values().iterator().next(); //any template fits
        restTemplate.exchange("/flows", HttpMethod.POST, new HttpEntity<>(flows, buildJsonHeaders()), String.class);
        log.debug("Added flows: {}", flows.stream()
                .map(flow -> String.format("%s->%s", flow.getInPort(), flow.getOutPort()))
                .collect(Collectors.toList()));
    }

    @Override
    public void removeFlows(List<ASwitchFlow> flows) {
        RestTemplate restTemplate = restTemplatesPerRegion.values().iterator().next();
        restTemplate.exchange("/flows", HttpMethod.DELETE, new HttpEntity<>(flows, buildJsonHeaders()), String.class);
        log.debug("Removed flows: {}", flows.stream()
                .map(flow -> String.format("%s->%s", flow.getInPort(), flow.getOutPort()))
                .collect(Collectors.toList()));
    }

    @Override
    public List<ASwitchFlow> getAllFlows() {
        RestTemplate restTemplate = restTemplatesPerRegion.values().iterator().next();
        ASwitchFlow[] flows = restTemplate.exchange("/flows",
                HttpMethod.GET, new HttpEntity(buildJsonHeaders()), ASwitchFlow[].class).getBody();
        return Arrays.asList(flows);
    }

    @Override
    public void portsUp(List<Integer> ports) {
        RestTemplate restTemplate = restTemplatesPerRegion.values().iterator().next();
        restTemplate.exchange("/ports", HttpMethod.POST, new HttpEntity<>(ports, buildJsonHeaders()), String.class);
        log.debug("Brought up ports: {}", ports);
    }

    @Override
    public void portsDown(List<Integer> ports) {
        RestTemplate restTemplate = restTemplatesPerRegion.values().iterator().next();
        restTemplate.exchange("/ports", HttpMethod.DELETE, new HttpEntity<>(ports, buildJsonHeaders()), String.class);
        log.debug("Brought down ports: {}", ports);
    }

    @Override
    public void stopFloodlight(String region) {
        restTemplatesPerRegion.get(region).exchange("/floodlight/stop",
                HttpMethod.POST, new HttpEntity<>(new ContainerName(mgmtFactory.getContainerName(region)),
                        buildJsonHeaders()), String.class);
        log.debug("Stopping Floodlight");
    }

    @Override
    public void startFloodlight(String region) {
        restTemplatesPerRegion.get(region).exchange("/floodlight/start",
                HttpMethod.POST, new HttpEntity<>(new ContainerName(mgmtFactory.getContainerName(region)),
                        buildJsonHeaders()), String.class);
        log.debug("Starting Floodlight");
    }

    @Override
    public void restartFloodlight(String region) {
        restTemplatesPerRegion.get(region).exchange("/floodlight/restart",
                HttpMethod.POST, new HttpEntity<>(new ContainerName(mgmtFactory.getContainerName(region)),
                        buildJsonHeaders()), String.class);
        log.debug("Restarting Floodlight");
    }

    @Override
    public void knockoutSwitch(Switch sw) {
        log.debug("Block Floodlight access to switch '{}' by adding iptables rules", sw.getName());
        SwitchDto swInfo = northbound.getSwitch(sw.getDpId());
        Arrays.asList(mgmtFactory, statsFactory).forEach(floodlight -> {
            String containerName = floodlight.getContainerName(sw.getRegion());
            restTemplatesPerRegion.get(sw.getRegion()).exchange("/floodlight/block-switch", HttpMethod.POST,
                    new HttpEntity<>(new BlockRequest(containerName, swInfo.getAddress(), swInfo.getPort()),
                            buildJsonHeaders()), String.class);
        });
    }

    @Override
    public void knockoutSwitch(Switch sw, MultiFloodlightFactory factory) {
        log.debug("Block Floodlight access to switch '{}' by adding iptables rules", sw.getName());
        SwitchDto swInfo = northbound.getSwitch(sw.getDpId());
        String containerName = factory.getContainerName(sw.getRegion());
        restTemplatesPerRegion.get(sw.getRegion()).exchange("/floodlight/block-switch", HttpMethod.POST,
                new HttpEntity<>(new BlockRequest(containerName, swInfo.getAddress(), swInfo.getPort()),
                        buildJsonHeaders()), String.class);
    }

    @Override
    public void reviveSwitch(Switch sw) {
        log.debug("Unblock Floodlight access to switch '{}' by removing iptables rules", sw.getName());
        SwitchDto swInfo = northbound.getSwitch(sw.getDpId());
        Arrays.asList(mgmtFactory, statsFactory).forEach(floodlight -> {
            String containerName = floodlight.getContainerName(sw.getRegion());
            restTemplatesPerRegion.get(sw.getRegion()).exchange("/floodlight/unblock-switch", HttpMethod.POST,
                    new HttpEntity<>(new BlockRequest(containerName, swInfo.getAddress(), swInfo.getPort()),
                            buildJsonHeaders()), String.class);
        });
    }

    @Override
    public void reviveSwitch(Switch sw, MultiFloodlightFactory factory) {
        log.debug("Unblock Floodlight access to switch '{}' by removing iptables rules", sw.getName());
        SwitchDto swInfo = northbound.getSwitch(sw.getDpId());
        String containerName = factory.getContainerName(sw.getRegion());
        restTemplatesPerRegion.get(sw.getRegion()).exchange("/floodlight/unblock-switch", HttpMethod.POST,
                new HttpEntity<>(new BlockRequest(containerName, swInfo.getAddress(), swInfo.getPort()),
                        buildJsonHeaders()), String.class);
    }

    @Override
    public void setController(Switch sw, String controller) {
        throw new UnsupportedOperationException(
                "setController method is not available on hardware env");
    }

    @Override
    public void blockFloodlightAccess(String region, BlockRequest address) {
        log.debug("Block floodlight access to {} by adding iptables rules", address);
        restTemplatesPerRegion.get(region).exchange("/floodlight/block",
                HttpMethod.POST, new HttpEntity<>(address, buildJsonHeaders()), String.class);
    }

    @Override
    public void unblockFloodlightAccess(String region, BlockRequest address) {
        log.debug("Unblock floodlight access to {} by removing iptables rules", address);
        restTemplatesPerRegion.get(region).exchange("/floodlight/unblock",
                HttpMethod.POST, new HttpEntity<>(address, buildJsonHeaders()), String.class);
    }

    @Override
    public void removeFloodlightAccessRestrictions(String region) {
        log.debug("Allow floodlight access to everything by flushing iptables rules(INPUT/OUTPUT chains)");
        String containerName = mgmtFactory.getContainerName(region);
        restTemplatesPerRegion.get(region).exchange("/floodlight/unblock-all",
                HttpMethod.POST, new HttpEntity<>(new ContainerName(containerName), buildJsonHeaders()), String.class);
    }

    @Override
    public void knockoutFloodlight(String region) {
        log.debug("Knock out Floodlight service");
        String containerName = mgmtFactory.getContainerName(region);
        blockFloodlightAccess(region, new BlockRequest(containerName, getPort(kafkaBootstrapServer)));
    }

    @Override
    public void reviveFloodlight(String region) {
        log.debug("Revive Floodlight service");
        String containerName = mgmtFactory.getContainerName(region);
        unblockFloodlightAccess(region, new BlockRequest(containerName, getPort(kafkaBootstrapServer)));
    }

    HttpHeaders buildJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return headers;
    }

    private Integer getPort(String uri) {
        String[] parts = uri.split(":");
        return Integer.valueOf(parts[parts.length - 1]);
    }
}
