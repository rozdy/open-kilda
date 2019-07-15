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

package org.openkilda.floodlight.api.request.factory;

import org.openkilda.floodlight.api.request.EgressFlowSegmentInstallRequest;
import org.openkilda.floodlight.api.request.EgressFlowSegmentRemoveRequest;
import org.openkilda.floodlight.api.request.EgressFlowSegmentRequest;
import org.openkilda.floodlight.api.request.EgressFlowSegmentVerifyRequest;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowTransitEncapsulation;

import lombok.Builder;

import java.util.UUID;

public class EgressFlowSegmentRequestFactory extends FlowSegmentRequestFactory<EgressFlowSegmentRequest> {
    @Builder
    public EgressFlowSegmentRequestFactory(
            MessageContext messageContext, UUID commandId, FlowSegmentMetadata metadata, FlowEndpoint endpoint,
            FlowEndpoint ingressEndpoint, Integer islPort, FlowTransitEncapsulation encapsulation) {
        super(new RequestBlank(messageContext, commandId, metadata, endpoint, ingressEndpoint, islPort, encapsulation));
    }

    @Override
    public EgressFlowSegmentRequest makeInstallRequest() {
        return new EgressFlowSegmentInstallRequest(requestBlank);
    }

    @Override
    public EgressFlowSegmentRequest makeRemoveRequest() {
        return new EgressFlowSegmentRemoveRequest(requestBlank);
    }

    @Override
    public EgressFlowSegmentRequest makeVerifyRequest() {
        return new EgressFlowSegmentVerifyRequest(requestBlank);
    }

    @Override
    public FlowSegmentRequestProxiedFactory makeProxyFactory() {
        return new FlowSegmentRequestProxiedFactory(this);
    }

    private static class RequestBlank extends EgressFlowSegmentRequest {
        RequestBlank(
                MessageContext messageContext, UUID commandId, FlowSegmentMetadata metadata, FlowEndpoint endpoint,
                FlowEndpoint ingressEndpoint, Integer islPort, FlowTransitEncapsulation encapsulation) {
            super(messageContext, commandId, metadata, endpoint, ingressEndpoint, islPort, encapsulation);
        }
    }
}
