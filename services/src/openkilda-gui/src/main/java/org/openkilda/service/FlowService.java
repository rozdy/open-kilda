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

package org.openkilda.service;

import org.openkilda.integration.converter.FlowConverter;
import org.openkilda.integration.exception.IntegrationException;
import org.openkilda.integration.model.Flow;
import org.openkilda.integration.model.FlowStatus;
import org.openkilda.integration.model.response.FlowPayload;
import org.openkilda.integration.service.FlowsIntegrationService;
import org.openkilda.integration.service.SwitchIntegrationService;
import org.openkilda.integration.source.store.FlowStoreService;
import org.openkilda.integration.source.store.dto.InventoryFlow;
import org.openkilda.log.ActivityLogger;
import org.openkilda.log.constants.ActivityType;
import org.openkilda.model.FlowCount;
import org.openkilda.model.FlowDiscrepancy;
import org.openkilda.model.FlowInfo;
import org.openkilda.model.FlowPath;
import org.openkilda.store.service.StoreService;
import org.openkilda.utility.CollectionUtil;
import org.openkilda.utility.StringUtil;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.usermanagement.model.UserInfo;
import org.usermanagement.service.UserService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The Class ServiceFlowImpl.
 *
 * @author Gaurav Chugh
 */

@Service
public class FlowService {

    private static final Logger LOGGER = Logger.getLogger(FlowService.class);

    @Autowired
    private FlowsIntegrationService flowsIntegrationService;

    @Autowired
    private SwitchIntegrationService switchIntegrationService;

    @Autowired
    private UserService userService;
    
    @Autowired
    private ActivityLogger activityLogger;
    
    @Autowired
    private FlowStoreService flowStoreService; 
    
    @Autowired
    private StoreService storeService;
    
    @Autowired
    private FlowConverter flowConverter;

    /**
     * get All Flows.
     *
     * @return SwitchRelationData
     */
    public List<FlowInfo> getAllFlows(List<String> statuses) {
        List<FlowInfo> flows = new ArrayList<FlowInfo>();
        if (!CollectionUtil.isEmpty(statuses)) {
            statuses = statuses.stream().map((status) -> status.toLowerCase()).collect(Collectors.toList());
        }
        if (CollectionUtil.isEmpty(statuses) || statuses.contains("active")) {
            flows = flowsIntegrationService.getFlows();
        }

        if (storeService.getLinkStoreConfig().getUrls().size() > 0) {
            List<InventoryFlow> inventoryFlows = new ArrayList<InventoryFlow>();
            if (CollectionUtil.isEmpty(statuses)) {
                inventoryFlows = flowStoreService.getAllFlows();
            } else {
                String status = "";
                for (String statusObj : statuses) {
                    if (StringUtil.isNullOrEmpty(status)) {
                        status += statusObj;
                    } else {
                        status += "," + statusObj;
                    }
                }
                inventoryFlows = flowStoreService.getFlowsWithParams(status);
            }
            processInventoryFlow(flows, inventoryFlows);
        }
        return flows;
    }
    
    /**
     * Gets the flow count.
     *
     * @param flows the flows
     * @return the flow count
     */
    public Collection<FlowCount> getFlowsCount(final List<Flow> flows) {
        LOGGER.info("Inside ServiceFlowImpl method getFlowsCount");
        Map<FlowCount, FlowCount> infoByFlowInfo = new HashMap<>();
        Map<String, String> csNames = switchIntegrationService.getCustomSwitchNameFromFile();

        if (!CollectionUtil.isEmpty(flows)) {
            flows.forEach((flow) -> {
                FlowCount flowInfo = new FlowCount();
                if (flow.getSource() != null) {
                    flowInfo.setSrcSwitch(flow.getSource().getSwitchId());
                    String srcSwitchName = switchIntegrationService.customSwitchName(csNames,
                            flow.getSource().getSwitchId());
                    flowInfo.setSrcSwitchName(srcSwitchName);
                }
                if (flow.getDestination() != null) {
                    flowInfo.setDstSwitch(flow.getDestination().getSwitchId());
                    String dstSwitchName = switchIntegrationService.customSwitchName(csNames,
                            flow.getDestination().getSwitchId());
                    flowInfo.setDstSwitchName(dstSwitchName);
                }
                flowInfo.setFlowCount(1);

                if (infoByFlowInfo.containsKey(flowInfo)) {
                    infoByFlowInfo.get(flowInfo).incrementFlowCount();
                } else {
                    infoByFlowInfo.put(flowInfo, flowInfo);
                }
            });
        }
        LOGGER.info("exit ServiceSwitchImpl method getFlowsCount");
        return infoByFlowInfo.values();
    }

    /**
     * Gets the path link.
     *
     * @param flowId the flow id
     * @return the path link
     */
    public FlowPayload getFlowPath(final String flowId) throws IntegrationException {
        return flowsIntegrationService.getFlowPath(flowId);
    }

    /**
     * Gets the all flows list.
     *
     * @return the all flow list
     */
    public List<Flow> getAllFlowList() {
        return flowsIntegrationService.getAllFlowList();
    }

    /**
     * Re route Flow by flow id.
     *
     * @param flowId the flow id
     * @return flow path
     */
    public FlowPath rerouteFlow(String flowId) {
        return flowsIntegrationService.rerouteFlow(flowId);
    }

    /**
     * Validate Flow.
     *
     * @param flowId the flow id
     * @return the string
     */
    public String validateFlow(String flowId) {
        return flowsIntegrationService.validateFlow(flowId);
    }

    /**
     * Flow by flow id.
     *
     * @param flowId the flow id
     * @return the flow by id
     */
    public FlowInfo getFlowById(String flowId) {
        FlowInfo flowInfo = new FlowInfo();
        Flow flow = flowsIntegrationService.getFlowById(flowId);
        Map<String, String> csNames = switchIntegrationService.getCustomSwitchNameFromFile();
        if (flow != null) {
            flowInfo = flowConverter.toFlowInfo(flow, csNames);
        }
        if (storeService.getLinkStoreConfig().getUrls().size() > 0) {
            InventoryFlow inventoryFlow = flowStoreService.getFlowById(flowId);
            if (flow != null && inventoryFlow != null) {
                flowInfo.setState(inventoryFlow.getState());
                flowInfo.setIgnoreBandwidth(inventoryFlow.getIgnoreBandwidth());
                FlowDiscrepancy discrepancy = new FlowDiscrepancy();
                discrepancy.setControllerDiscrepancy(false);
                if (flowInfo.getMaximumBandwidth() != inventoryFlow.getMaximumBandwidth()) {
                    discrepancy.setBandwidth(true);
                }
                if (("UP".equalsIgnoreCase(flowInfo.getStatus())
                        && !"ACTIVE".equalsIgnoreCase(inventoryFlow.getState()))
                        || ("DOWN".equalsIgnoreCase(flowInfo.getStatus())
                                && "ACTIVE".equalsIgnoreCase(inventoryFlow.getState()))) {
                    discrepancy.setStatus(true);
                }
                flowInfo.setDiscrepancy(discrepancy);
            } else if (inventoryFlow == null && flow != null) {
                FlowDiscrepancy discrepancy = new FlowDiscrepancy();
                discrepancy.setInventoryDiscrepancy(true);
                discrepancy.setControllerDiscrepancy(false);
                discrepancy.setStatus(true);
                discrepancy.setBandwidth(true);
                flowInfo.setDiscrepancy(discrepancy);
            } else {
                flowConverter.toFlowInfo(flowInfo, inventoryFlow, csNames);
            }
        }
        return flowInfo;
    }

    /**
     * Gets the flow status by id.
     *
     * @param flowId the flow id
     * @return the flow status by id
     */
    public FlowStatus getFlowStatusById(String flowId) {
        return flowsIntegrationService.getFlowStatusById(flowId);
    }

    /**
     * Creates the flow.
     *
     * @param flow the flow
     * @return the flow
     */
    public Flow createFlow(Flow flow) {
        flow = flowsIntegrationService.createFlow(flow);
        activityLogger.log(ActivityType.CREATE_FLOW, flow.getId());
        return flow;
    }

    /**
     * Update flow.
     *
     * @param flowId the flow id
     * @param flow the flow
     * @return the flow
     */
    public Flow updateFlow(String flowId, Flow flow) {
        activityLogger.log(ActivityType.UPDATE_FLOW, flow.getId());
        flow = flowsIntegrationService.updateFlow(flowId, flow);
        return flow;
    }

    /**
     * Delete flow.
     *
     * @param flowId the flow id
     * @param userInfo the user info
     * @return the flow
     */
    public Flow deleteFlow(String flowId, UserInfo userInfo) {
        if (userService.validateOtp(userInfo.getUserId(), userInfo.getCode())) {
            Flow flow = flowsIntegrationService.deleteFlow(flowId);
            activityLogger.log(ActivityType.DELETE_FLOW, flow.getId());
            return flow;
        } else {
            return null;
        }
    }
    
    /**
     * Re sync flow.
     * 
     * @param flowId the flow id
     * 
     * @return
     */
    public String resyncFlow(String flowId) {
        activityLogger.log(ActivityType.RESYNC_FLOW, flowId);
        return flowsIntegrationService.resyncFlow(flowId);
    }
    
    /**
     * Process inventory flow.
     *
     * @param flows the flows
     * @param inventoryFlows the inventory flows
     */
    private void processInventoryFlow(final List<FlowInfo> flows, final List<InventoryFlow> inventoryFlows) {
        List<FlowInfo> discrepancyFlow = new ArrayList<FlowInfo>();
        for (InventoryFlow inventoryFlow : inventoryFlows) {
            int index = -1;
            for (FlowInfo flow : flows) {
                if (flow.getFlowid().equals(inventoryFlow.getId())) {
                    index = flows.indexOf(flow);
                    break;
                }
            }
            if (index >= 0) {
                FlowDiscrepancy discrepancy = new FlowDiscrepancy();
                discrepancy.setControllerDiscrepancy(false);
                if (flows.get(index).getMaximumBandwidth() != inventoryFlow.getMaximumBandwidth()) {
                    discrepancy.setBandwidth(true);
                }
                if (("UP".equalsIgnoreCase(flows.get(index).getStatus())
                        && !"ACTIVE".equalsIgnoreCase(inventoryFlow.getState()))
                        || ("DOWN".equalsIgnoreCase(flows.get(index).getStatus())
                                && "ACTIVE".equalsIgnoreCase(inventoryFlow.getState()))) {
                    discrepancy.setStatus(true);
                }
                flows.get(index).setDiscrepancy(discrepancy);
                flows.get(index).setState(inventoryFlow.getState());
                flows.get(index).setIgnoreBandwidth(inventoryFlow.getIgnoreBandwidth());
            } else {
                final Map<String, String> csNames = switchIntegrationService.getCustomSwitchNameFromFile();
                FlowInfo flowObj = new FlowInfo();
                flowConverter.toFlowInfo(flowObj, inventoryFlow, csNames);
                discrepancyFlow.add(flowObj);
            }
        }

        for (FlowInfo flow : flows) {
            boolean flag = false;
            for (InventoryFlow inventoryFlow : inventoryFlows) {
                if (flow.getFlowid().equals(inventoryFlow.getId())) {
                    flag = true;
                    break;
                }
            }
            if (!flag) {
                FlowDiscrepancy discrepancy = new FlowDiscrepancy();
                discrepancy.setInventoryDiscrepancy(true);
                discrepancy.setControllerDiscrepancy(false);
                discrepancy.setStatus(true);
                discrepancy.setBandwidth(true);
                flow.setDiscrepancy(discrepancy);
            }
        }
        flows.addAll(discrepancyFlow);
    }
}
