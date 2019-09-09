/* Copyright 2017 Telstra Open Source
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

package org.openkilda.applications.info;

import org.openkilda.applications.AppMessage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class InfoAppMessage extends AppMessage {
    private static final long serialVersionUID = 2246740754710222690L;

    @JsonProperty("payload")
    private InfoAppData payload;

    @Builder
    @JsonCreator
    public InfoAppMessage(@JsonProperty("timestamp") long timestamp,
                          @JsonProperty("correlation_id") String correlationId,
                          @JsonProperty("payload") InfoAppData payload) {
        super(timestamp, correlationId);
        this.payload = payload;
    }
}
