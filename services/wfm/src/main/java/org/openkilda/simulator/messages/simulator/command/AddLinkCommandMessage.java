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

package org.openkilda.simulator.messages.simulator.command;

import org.openkilda.messaging.model.SwitchId;
import org.openkilda.simulator.messages.LinkMessage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(value = {
        "dpid",
        "link"})

public class AddLinkCommandMessage extends SimulatorCommandMessage {
    @JsonProperty("dpid")
    private SwitchId dpid;

    @JsonProperty("link")
    private LinkMessage link;

    public AddLinkCommandMessage() {

    }

    public AddLinkCommandMessage(@JsonProperty("dpid") SwitchId dpid,
                                 @JsonProperty("link") LinkMessage link) {
        this.dpid = dpid;
        this.link = link;
    }

    @Override
    public SwitchId getDpid() {
        return dpid;
    }

    @Override
    public void setDpid(SwitchId dpid) {
        this.dpid = dpid;
    }

    public LinkMessage getLink() {
        return link;
    }

    public void setLink(LinkMessage link) {
        this.link = link;
    }
}
