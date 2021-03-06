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

package org.openkilda.wfm.topology.flowhs.fsm.reroute.actions;

import static java.lang.String.format;

import org.openkilda.floodlight.flow.request.InstallIngressRule;
import org.openkilda.floodlight.flow.response.FlowResponse;
import org.openkilda.floodlight.flow.response.FlowRuleResponse;
import org.openkilda.model.Switch;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.validation.rules.IngressRulesValidator;
import org.openkilda.wfm.topology.flowhs.validation.rules.RulesValidator;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class ValidateIngressRulesAction extends RuleProcessingAction {

    private final SwitchRepository switchRepository;

    public ValidateIngressRulesAction(PersistenceManager persistenceManager) {
        this.switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
    }

    @Override
    protected void perform(State from, State to,
                           Event event, FlowRerouteContext context, FlowRerouteFsm stateMachine) {
        FlowResponse response = context.getFlowResponse();
        UUID commandId = response.getCommandId();
        stateMachine.getPendingCommands().remove(commandId);

        InstallIngressRule expected = stateMachine.getIngressCommands().get(commandId);
        if (expected == null) {
            throw new IllegalStateException(format("Failed to find ingress command with id %s", commandId));
        }

        if (response.isSuccess()) {
            Switch switchObj = switchRepository.findById(expected.getSwitchId())
                    .orElseThrow(() -> new IllegalStateException(format("Failed to find switch %s",
                            expected.getSwitchId())));

            RulesValidator validator =
                    new IngressRulesValidator(expected, (FlowRuleResponse) context.getFlowResponse(),
                            switchObj.getFeatures());
            if (validator.validate()) {
                String message = format("Ingress rule %s has been validated successfully on switch %s",
                        expected.getCookie(), expected.getSwitchId());
                log.debug(message);
                sendHistoryUpdate(stateMachine, "Rule is validated", message);
            } else {
                String message = format("Ingress rule %s is missing on switch %s",
                        expected.getCookie(), expected.getSwitchId());
                log.warn(message);
                sendHistoryUpdate(stateMachine, "Rule is missing", message);

                stateMachine.getFailedValidationResponses().put(commandId, response);
            }
        } else {
            String message = format("Failed to validate ingress rule %s on switch %s",
                    expected.getCookie(), expected.getSwitchId());
            log.warn(message);
            sendHistoryUpdate(stateMachine, "Rule validation failed", message);

            stateMachine.getFailedValidationResponses().put(commandId, response);
        }

        if (stateMachine.getPendingCommands().isEmpty()) {
            if (stateMachine.getFailedValidationResponses().isEmpty()) {
                log.debug("Ingress rules have been validated for flow {}", stateMachine.getFlowId());
                stateMachine.fire(Event.RULES_VALIDATED);
            } else {
                log.warn("Found missing rules or received error response(s) on validation commands of the flow {}",
                        stateMachine.getFlowId());
                stateMachine.fire(Event.MISSING_RULE_FOUND);
            }
        }
    }
}
