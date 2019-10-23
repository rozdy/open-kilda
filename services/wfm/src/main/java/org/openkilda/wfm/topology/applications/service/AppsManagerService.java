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

package org.openkilda.wfm.topology.applications.service;

import static java.lang.String.format;

import org.openkilda.applications.command.apps.CreateExclusion;
import org.openkilda.applications.command.apps.RemoveExclusion;
import org.openkilda.applications.info.apps.CreateExclusionResult;
import org.openkilda.applications.info.apps.FlowApplicationCreated;
import org.openkilda.applications.info.apps.FlowApplicationRemoved;
import org.openkilda.applications.info.apps.RemoveExclusionResult;
import org.openkilda.applications.model.Exclusion;
import org.openkilda.messaging.command.apps.FlowAddAppRequest;
import org.openkilda.messaging.command.apps.FlowRemoveAppRequest;
import org.openkilda.messaging.command.flow.InstallEgressFlow;
import org.openkilda.messaging.command.flow.InstallIngressFlow;
import org.openkilda.messaging.command.flow.UpdateIngressAndEgressFlows;
import org.openkilda.messaging.command.switches.InstallExclusionRequest;
import org.openkilda.messaging.command.switches.RemoveExclusionRequest;
import org.openkilda.messaging.info.apps.AppsEntry;
import org.openkilda.messaging.info.apps.FlowAppsResponse;
import org.openkilda.model.ApplicationRule;
import org.openkilda.model.Cookie;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowApplication;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.ApplicationRepository;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.wfm.error.ExclusionAlreadyExistException;
import org.openkilda.wfm.error.ExclusionNotFoundException;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.error.SwitchPropertiesNotFoundException;
import org.openkilda.wfm.share.flow.resources.EncapsulationResources;
import org.openkilda.wfm.share.flow.resources.ExclusionIdPool;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.service.FlowCommandFactory;
import org.openkilda.wfm.topology.applications.AppsManagerCarrier;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
public class AppsManagerService {

    private final AppsManagerCarrier carrier;

    private final FlowRepository flowRepository;
    private final FlowPathRepository flowPathRepository;
    private final ApplicationRepository applicationRepository;
    private final SwitchPropertiesRepository switchPropertiesRepository;
    private final TransactionManager transactionManager;

    private final FlowResourcesManager flowResourcesManager;
    private final FlowCommandFactory flowCommandFactory = new FlowCommandFactory();
    private final ExclusionIdPool exclusionIdPool;

    public AppsManagerService(AppsManagerCarrier carrier,
                              PersistenceManager persistenceManager, FlowResourcesConfig flowResourcesConfig) {
        this.carrier = carrier;
        this.flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        this.flowPathRepository = persistenceManager.getRepositoryFactory().createFlowPathRepository();
        this.applicationRepository = persistenceManager.getRepositoryFactory().createApplicationRepository();
        this.switchPropertiesRepository = persistenceManager.getRepositoryFactory().createSwitchPropertiesRepository();
        this.transactionManager = persistenceManager.getTransactionManager();
        this.flowResourcesManager = new FlowResourcesManager(persistenceManager, flowResourcesConfig);
        this.exclusionIdPool = new ExclusionIdPool(persistenceManager);
    }

    /**
     * Process getting applications for flow.
     */
    public void getEnabledFlowApplications(String flowId) throws FlowNotFoundException {
        Flow flow = getFlow(flowId);
        sendResponse(flow);
    }

    /**
     * Process adding application for flow endpoint or endpoints.
     */
    public void addFlowApplication(FlowAddAppRequest payload) throws FlowNotFoundException {
        FlowApplication application = convertApplicationFromString(payload.getApplication());
        Flow flow = getFlow(payload.getFlowId());

        switch (application) {
            case TELESCOPE:
                addTelescopeForFlow(flow);
                break;
            default:
                throw new UnsupportedOperationException(
                        format("%s application adding has not yet been implemented.", application));
        }

        sendResponse(flow);
    }

    private void addTelescopeForFlow(Flow flow) {
        transactionManager.doInTransaction(() -> {
            addAppToFlowPath(flow.getForwardPath(), FlowApplication.TELESCOPE);
            addAppToFlowPath(flow.getReversePath(), FlowApplication.TELESCOPE);
            sendUpdateFlowEndpointRulesCommand(flow);
        });
        sendAppCreateNotification(flow.getFlowId(), FlowApplication.TELESCOPE);
    }

    private void addAppToFlowPath(FlowPath flowPath, FlowApplication application) {
        Set<FlowApplication> applications = Optional.ofNullable(flowPath.getApplications()).orElse(new HashSet<>());
        applications.add(application);
        flowPath.setApplications(applications);
        flowPathRepository.createOrUpdate(flowPath);
    }

    private void sendAppCreateNotification(String flowId, FlowApplication application) {
        carrier.emitNotification(FlowApplicationCreated.builder()
                .flowId(flowId)
                .application(application.toString().toLowerCase())
                .build());
    }

    /**
     * Process removing application for flow endpoint or endpoints.
     */
    public void removeFlowApplication(FlowRemoveAppRequest payload) throws FlowNotFoundException {
        FlowApplication application = convertApplicationFromString(payload.getApplication());
        Flow flow = getFlow(payload.getFlowId());

        switch (application) {
            case TELESCOPE:
                removeTelescopeForFlow(flow);
                break;
            default:
                throw new UnsupportedOperationException(
                        format("%s application removing has not yet been implemented.", application));
        }

        sendResponse(flow);
    }

    private void removeTelescopeForFlow(Flow flow) {
        transactionManager.doInTransaction(() -> {
            sendUpdateFlowEndpointRulesCommand(flow);
            removeAppFromFlowPath(flow.getForwardPath(), FlowApplication.TELESCOPE);
            removeAppFromFlowPath(flow.getReversePath(), FlowApplication.TELESCOPE);
            sendUpdateFlowEndpointRulesCommand(flow);
        });
        sendAppRemoveNotification(flow.getFlowId(), FlowApplication.TELESCOPE);
    }

    private void removeAppFromFlowPath(FlowPath flowPath, FlowApplication application) {
        Set<FlowApplication> applications = Optional.ofNullable(flowPath.getApplications()).orElse(new HashSet<>());
        applications.remove(application);
        flowPath.setApplications(applications);
        flowPathRepository.createOrUpdate(flowPath);
    }

    private void sendAppRemoveNotification(String flowId, FlowApplication application) {
        carrier.emitNotification(FlowApplicationRemoved.builder()
                .flowId(flowId)
                .application(application.toString().toLowerCase())
                .build());
    }

    private void sendResponse(Flow flow) {
        carrier.emitNorthboundResponse(FlowAppsResponse.builder()
                .flowId(flow.getFlowId())
                .srcApps(AppsEntry.builder()
                        .endpointSwitch(flow.getForwardPath().getSrcSwitch().getSwitchId())
                        .applications(Optional
                                .ofNullable(flow.getForwardPath().getApplications()).orElse(new HashSet<>()))
                        .build())
                .dstApps(AppsEntry.builder()
                        .endpointSwitch(flow.getReversePath().getSrcSwitch().getSwitchId())
                        .applications(Optional
                                .ofNullable(flow.getReversePath().getApplications()).orElse(new HashSet<>()))
                        .build())
                .build());
    }

    private Flow getFlow(String flowId) throws FlowNotFoundException {
        return flowRepository.findById(flowId).orElseThrow(() -> new FlowNotFoundException(flowId));
    }

    private FlowApplication convertApplicationFromString(String application) {
        return FlowApplication.valueOf(application.toUpperCase());
    }

    private void sendUpdateFlowEndpointRulesCommand(Flow flow) throws SwitchPropertiesNotFoundException {
        carrier.emitSpeakerCommand(buildUpdateFlowEndpointRulesCommand(flow));
    }

    private UpdateIngressAndEgressFlows buildUpdateFlowEndpointRulesCommand(Flow flow)
            throws SwitchPropertiesNotFoundException {
        SwitchId switchId = flow.getSrcSwitch().getSwitchId();
        SwitchProperties switchProperties = switchPropertiesRepository.findBySwitchId(switchId)
                .orElseThrow(() -> new SwitchPropertiesNotFoundException(switchId));
        Objects.requireNonNull(switchProperties.getTelescopePort(),
                format("Telescope port for switch '%s' is not set", switchId));

        Cookie cookie = flow.getForwardPath().getCookie();
        Cookie telescopeCookie = Cookie.buildTelescopeCookie(cookie.getUnmaskedValue(), cookie.isMaskedAsForward());
        return new UpdateIngressAndEgressFlows(buildIngressRuleCommand(flow, flow.getForwardPath()),
                buildEgressRuleCommand(flow, flow.getReversePath()), switchProperties.getTelescopePort(),
                telescopeCookie.getValue());
    }

    private InstallIngressFlow buildIngressRuleCommand(Flow flow, FlowPath flowPath) {
        List<PathSegment> segments = flowPath.getSegments();
        requireSegments(segments);

        PathSegment ingressSegment = segments.get(0);
        if (!ingressSegment.getSrcSwitch().getSwitchId().equals(flowPath.getSrcSwitch().getSwitchId())) {
            throw new IllegalStateException(
                    format("FlowSegment was not found for ingress flow rule, flowId: %s", flow.getFlowId()));
        }
        EncapsulationResources encapsulationResources = getEncapsulationResources(flow, flowPath);

        return flowCommandFactory.buildInstallIngressFlow(flow, flowPath, ingressSegment.getSrcPort(),
                encapsulationResources);
    }

    private InstallEgressFlow buildEgressRuleCommand(Flow flow, FlowPath flowPath) {
        List<PathSegment> segments = flowPath.getSegments();
        requireSegments(segments);

        PathSegment egressSegment = segments.get(segments.size() - 1);
        if (!egressSegment.getDestSwitch().getSwitchId().equals(flowPath.getDestSwitch().getSwitchId())) {
            throw new IllegalStateException(
                    format("FlowSegment was not found for egress flow rule, flowId: %s", flow.getFlowId()));
        }
        EncapsulationResources encapsulationResources = getEncapsulationResources(flow, flowPath);

        return flowCommandFactory.buildInstallEgressFlow(flowPath, egressSegment.getDestPort(), encapsulationResources);
    }

    private EncapsulationResources getEncapsulationResources(Flow flow, FlowPath flowPath) {
        return flowResourcesManager.getEncapsulationResources(flowPath.getPathId(),
                flow.getOppositePathId(flowPath.getPathId()),
                flow.getEncapsulationType())
                .orElseThrow(() -> new IllegalStateException(
                        format("Encapsulation resources are not found for path %s", flowPath)));
    }

    private void requireSegments(List<PathSegment> segments) {
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("Neither one switch flow nor path segments provided");
        }
    }

    /**
     * Create exclusion for the flow.
     */
    public void processCreateExclusion(CreateExclusion payload)
            throws FlowNotFoundException, ExclusionAlreadyExistException {
        Flow flow = getFlow(payload.getFlowId());

        checkTelescopeAppInstallation(flow);

        Exclusion exclusion = payload.getExclusion();
        Optional<ApplicationRule> ruleOptional = applicationRepository.lookupRuleByMatchAndFlow(
                flow.getSrcSwitch().getSwitchId(), flow.getFlowId(), exclusion.getSrcIp(), exclusion.getSrcPort(),
                exclusion.getDstIp(), exclusion.getDstPort(), exclusion.getProto(), exclusion.getEthType());
        if (ruleOptional.isPresent()) {
            throw new ExclusionAlreadyExistException(exclusion);
        }

        int expirationTimeout = Optional.ofNullable(payload.getExpirationTimeout()).orElse(0);
        Cookie flowCookie = flow.getForwardPath().getCookie();
        Cookie cookie = Cookie.buildExclusionCookie(flowCookie.getUnmaskedValue(),
                exclusionIdPool.allocate(flow.getFlowId()));
        ApplicationRule rule = ApplicationRule.builder()
                .flowId(flow.getFlowId())
                .switchId(flow.getSrcSwitch().getSwitchId())
                .cookie(cookie)
                .srcIp(exclusion.getSrcIp())
                .srcPort(exclusion.getSrcPort())
                .dstIp(exclusion.getDstIp())
                .dstPort(exclusion.getDstPort())
                .proto(exclusion.getProto())
                .ethType(exclusion.getEthType())
                .timeCreate(Instant.now())
                .expirationTimeout(expirationTimeout)
                .build();

        applicationRepository.createOrUpdate(rule);

        EncapsulationResources encapsulationResources = getEncapsulationResources(flow, flow.getForwardPath());
        carrier.emitSpeakerCommand(InstallExclusionRequest.builder()
                .switchId(flow.getSrcSwitch().getSwitchId())
                .cookie(cookie.getValue())
                .tunnelId(encapsulationResources.getTransitEncapsulationId())
                .srcIp(exclusion.getSrcIp())
                .srcPort(exclusion.getSrcPort())
                .dstIp(exclusion.getDstIp())
                .dstPort(exclusion.getDstPort())
                .proto(exclusion.getProto())
                .ethType(exclusion.getEthType())
                .expirationTimeout(expirationTimeout)
                .build());
        carrier.emitNotification(CreateExclusionResult.builder()
                .flowId(payload.getFlowId())
                .application(payload.getApplication())
                .expirationTimeout(expirationTimeout)
                .exclusion(payload.getExclusion())
                .success(true)
                .build());
    }

    /**
     * Remove exclusion for the flow.
     */
    public void processRemoveExclusion(RemoveExclusion payload)
            throws FlowNotFoundException, ExclusionNotFoundException {
        Flow flow = getFlow(payload.getFlowId());

        checkTelescopeAppInstallation(flow);

        Exclusion exclusion = payload.getExclusion();

        Optional<ApplicationRule> ruleOptional = applicationRepository.lookupRuleByMatchAndFlow(
                flow.getSrcSwitch().getSwitchId(), flow.getFlowId(), exclusion.getSrcIp(), exclusion.getSrcPort(),
                exclusion.getDstIp(), exclusion.getDstPort(), exclusion.getProto(), exclusion.getEthType());
        if (!ruleOptional.isPresent()) {
            throw new ExclusionNotFoundException(exclusion);
        }
        ApplicationRule rule = ruleOptional.get();
        applicationRepository.delete(rule);
        exclusionIdPool.deallocate(flow.getFlowId(), rule.getCookie().getTypeMetadata());

        EncapsulationResources encapsulationResources = getEncapsulationResources(flow, flow.getForwardPath());
        carrier.emitSpeakerCommand(RemoveExclusionRequest.builder()
                .switchId(flow.getSrcSwitch().getSwitchId())
                .cookie(rule.getCookie().getValue())
                .tunnelId(encapsulationResources.getTransitEncapsulationId())
                .srcIp(payload.getExclusion().getSrcIp())
                .srcPort(payload.getExclusion().getSrcPort())
                .dstIp(payload.getExclusion().getDstIp())
                .dstPort(payload.getExclusion().getDstPort())
                .proto(payload.getExclusion().getProto())
                .ethType(payload.getExclusion().getEthType())
                .build());
        carrier.emitNotification(RemoveExclusionResult.builder()
                .flowId(payload.getFlowId())
                .application(payload.getApplication())
                .exclusion(payload.getExclusion())
                .success(true)
                .build());
    }

    private void checkTelescopeAppInstallation(Flow flow) {
        checkFlowAppInstallation(flow.getForwardPath(), FlowApplication.TELESCOPE);
        checkFlowAppInstallation(flow.getReversePath(), FlowApplication.TELESCOPE);
    }

    private void checkFlowAppInstallation(FlowPath flowPath, FlowApplication application) {
        if (flowPath.getApplications() == null || !flowPath.getApplications().contains(application)) {
            throw new IllegalArgumentException(format("Flow application \"%s\" is not installed for the flow %s",
                    application, flowPath.getFlow().getFlowId()));
        }
    }
}
