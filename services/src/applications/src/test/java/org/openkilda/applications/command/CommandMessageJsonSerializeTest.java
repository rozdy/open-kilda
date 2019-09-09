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

package org.openkilda.applications.command;

import static org.junit.Assert.assertEquals;

import org.openkilda.applications.command.apps.CreateExclusion;
import org.openkilda.applications.model.Endpoint;
import org.openkilda.applications.model.Exclusion;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

public class CommandMessageJsonSerializeTest {

    @Test
    public void shouldSerializeToJson() throws Exception {
        CreateExclusion createExclusion = CreateExclusion.builder()
                .flowId("flow_id")
                .endpoint(Endpoint.builder()
                        .portNumber(2)
                        .switchId("00:00:00:00:00:00:00:01")
                        .vlanId(2)
                        .build())
                .application("app")
                .exclusion(Exclusion.builder()
                        .srcIp("127.0.0.1")
                        .srcPort(1)
                        .dstIp("127.0.0.2")
                        .dstPort(3)
                        .proto("proto")
                        .ethType("eth_type")
                        .build())
                .build();
        CommandAppMessage commandMessage = CommandAppMessage.builder()
                .payload(createExclusion)
                .correlationId("corr_id")
                .timestamp(System.currentTimeMillis())
                .build();

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(commandMessage);

        CommandAppMessage commandMessageFromJson = mapper.readValue(json, CommandAppMessage.class);

        assertEquals(commandMessage, commandMessageFromJson);
    }
}
