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

import static org.junit.Assert.assertEquals;
import static org.openkilda.model.IslStatus.ACTIVE;

import org.openkilda.model.Flow;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.impl.AvailableNetwork;
import org.openkilda.pce.model.Edge;

import com.google.common.collect.Sets;
import org.junit.Test;

import java.util.ArrayList;

public class AvailableNetworkFactoryTest extends NetworkBaseTest {

    public static final SwitchId SWITCH_A_ID = new SwitchId("01");
    public static final SwitchId SWITCH_B_ID = new SwitchId("02");
    public static final SwitchId SWITCH_C_ID = new SwitchId("03");

    @Test
    public void networkWithNegativeCostTest() throws RecoverableException {
        createLinear(2, -1);
        checkLinearNetwork();
    }

    @Test
    public void networkWithPositiveCostTest() throws RecoverableException {
        createLinear(2, 2);
        checkLinearNetwork();
    }

    private void checkLinearNetwork() throws RecoverableException {
        AvailableNetworkFactory factory = new AvailableNetworkFactory(config, repositoryFactory);
        Flow flow = Flow.builder()
                .flowId("flow")
                .srcSwitch(getSwitch(SWITCH_A_ID))
                .destSwitch(getSwitch(SWITCH_C_ID))
                .ignoreBandwidth(true)
                .build();
        AvailableNetwork network = factory.getAvailableNetwork(flow, new ArrayList<>());
        assertEquals(1, network.getSwitch(SWITCH_A_ID).getOutgoingLinks().size());
        assertEquals(1, network.getSwitch(SWITCH_A_ID).getIncomingLinks().size());
        assertEquals(2, network.getSwitch(SWITCH_B_ID).getOutgoingLinks().size());
        assertEquals(2, network.getSwitch(SWITCH_B_ID).getIncomingLinks().size());
        assertEquals(1, network.getSwitch(SWITCH_C_ID).getOutgoingLinks().size());
        assertEquals(1, network.getSwitch(SWITCH_C_ID).getIncomingLinks().size());

        Edge abEdge = buildEdge(network, SWITCH_A_ID, 1, SWITCH_B_ID, 1);
        Edge baEdge = buildEdge(network, SWITCH_B_ID, 1, SWITCH_A_ID, 1);

        assertEquals(Sets.newHashSet(abEdge), network.getSwitch(SWITCH_A_ID).getOutgoingLinks());
        assertEquals(Sets.newHashSet(baEdge), network.getSwitch(SWITCH_A_ID).getIncomingLinks());

        Edge bcEdge = buildEdge(network, SWITCH_B_ID, 2, SWITCH_C_ID, 2);
        Edge cbEdge = buildEdge(network, SWITCH_C_ID, 2, SWITCH_B_ID, 2);

        assertEquals(Sets.newHashSet(baEdge, bcEdge), network.getSwitch(SWITCH_B_ID).getOutgoingLinks());
        assertEquals(Sets.newHashSet(abEdge, cbEdge), network.getSwitch(SWITCH_B_ID).getIncomingLinks());
        assertEquals(Sets.newHashSet(cbEdge), network.getSwitch(SWITCH_C_ID).getOutgoingLinks());
        assertEquals(Sets.newHashSet(bcEdge), network.getSwitch(SWITCH_C_ID).getIncomingLinks());
    }

    private void createLinear(int abCost, int bcCost) {
        // A - B - C
        int index = 1;

        Switch nodeA = createSwitch(SWITCH_A_ID.toString());
        Switch nodeB = createSwitch(SWITCH_B_ID.toString());
        Switch nodeC = createSwitch(SWITCH_C_ID.toString());

        createIsl(nodeA, nodeB, ACTIVE, ACTIVE, abCost, 1000, 1);
        createIsl(nodeB, nodeA, ACTIVE, ACTIVE, abCost, 1000, 1);
        createIsl(nodeB, nodeC, ACTIVE, ACTIVE, bcCost, 1000, 2);
        createIsl(nodeC, nodeB, ACTIVE, ACTIVE, bcCost, 1000, 2);

    }

    private Switch getSwitch(SwitchId switchId) {
        return switchRepository.findById(switchId).get();
    }

    private Edge buildEdge(
            AvailableNetwork network, SwitchId srcSwitchId, int srcPort, SwitchId dstSwitchId, int dstPort) {

        return Edge.builder()
                .srcSwitch(network.getSwitch(srcSwitchId))
                .srcPort(srcPort)
                .destSwitch(network.getSwitch(dstSwitchId))
                .destPort(dstPort).build();
    }
}
