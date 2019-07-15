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

import org.openkilda.floodlight.api.request.IngressFlowSegmentInstallRequest;
import org.openkilda.floodlight.api.request.IngressFlowSegmentRemoveRequest;
import org.openkilda.floodlight.api.request.IngressFlowSegmentRequest;
import org.openkilda.floodlight.api.request.IngressFlowSegmentVerifyRequest;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.SwitchId;

import lombok.Builder;

import java.util.UUID;

public class IngressFlowSegmentRequestFactory extends FlowSegmentRequestFactory<IngressFlowSegmentRequest> {
    @Builder
    public IngressFlowSegmentRequestFactory(
            MessageContext messageContext, UUID commandId, FlowSegmentMetadata metadata,
            FlowEndpoint endpoint, MeterConfig meterConfig, SwitchId egressSwitchId, Integer islPort,
            FlowTransitEncapsulation encapsulation) {
        super(new RequestBlank(
                messageContext, commandId, metadata, endpoint, meterConfig, egressSwitchId, islPort, encapsulation));
    }

    @Override
    public IngressFlowSegmentRequest makeInstallRequest() {
        return new IngressFlowSegmentInstallRequest(requestBlank);
    }

    @Override
    public IngressFlowSegmentRequest makeRemoveRequest() {
        return new IngressFlowSegmentRemoveRequest(requestBlank);
    }

    @Override
    public IngressFlowSegmentRequest makeVerifyRequest() {
        return new IngressFlowSegmentVerifyRequest(requestBlank);
    }

    @Override
    public FlowSegmentRequestProxiedFactory makeProxyFactory() {
        return new FlowSegmentRequestProxiedFactory(this);
    }

    private static class RequestBlank extends IngressFlowSegmentRequest {
        RequestBlank(
                MessageContext context, UUID commandId, FlowSegmentMetadata metadata,
                FlowEndpoint endpoint, MeterConfig meterConfig, SwitchId egressSwitchId, Integer islPort,
                FlowTransitEncapsulation encapsulation) {
            super(context, commandId, metadata, endpoint, meterConfig, egressSwitchId, islPort, encapsulation);
        }
    }
}
