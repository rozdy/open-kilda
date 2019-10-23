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

import org.openkilda.testing.model.topology.TopologyDefinition.Switch;
import org.openkilda.testing.service.floodlight.MultiFloodlightFactory;
import org.openkilda.testing.service.lockkeeper.model.ASwitchFlow;
import org.openkilda.testing.service.lockkeeper.model.BlockRequest;

import java.util.List;

/**
 * This service is meant to give control over some software or hardware parts of the system that are out of Kilda's
 * direct control. E.g. switches that are not connected to controller or lifecycle of system components.
 */
public interface LockKeeperService {
    void addFlows(List<ASwitchFlow> flows);

    void removeFlows(List<ASwitchFlow> flows);

    List<ASwitchFlow> getAllFlows();

    void portsUp(List<Integer> ports);

    void portsDown(List<Integer> ports);

    void stopFloodlight(String region);

    void startFloodlight(String region);

    void restartFloodlight(String region);

    void knockoutSwitch(Switch sw);

    void knockoutSwitch(Switch sw, MultiFloodlightFactory factory);

    void reviveSwitch(Switch sw);

    void reviveSwitch(Switch sw, MultiFloodlightFactory factory);

    void setController(Switch sw, String controller);

    void blockFloodlightAccess(String region, BlockRequest address);

    void unblockFloodlightAccess(String region, BlockRequest address);

    void removeFloodlightAccessRestrictions(String region);

    void knockoutFloodlight(String region);

    void reviveFloodlight(String region);
}
