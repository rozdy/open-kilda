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

package org.openkilda.testing.service.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration
public class KafkaConfig {
    @Bean(name = "kafkaConsumerProperties")
    public Properties kafkaConsumerProperties(@Value("${kafka.bootstrap.server}") String bootstrapServer) {
        Properties connectDefaults = new Properties();
        connectDefaults.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServer);
        connectDefaults.put(ConsumerConfig.GROUP_ID_CONFIG, "autotest");
        connectDefaults.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        connectDefaults.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        connectDefaults.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return connectDefaults;
    }

    @Bean(name = "kafkaProducerProperties")
    public Properties kafkaProducerProperties(@Value("${kafka.bootstrap.server}") String bootstrapServer) {
        Properties connectDefaults = new Properties();
        connectDefaults.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServer);
        connectDefaults.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        connectDefaults.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        return connectDefaults;
    }
}
