/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.kie.kogito.app.audit.quarkus;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.kie.kogito.process.Process;
import org.kie.kogito.process.Processes;

import io.quarkus.test.junit.QuarkusTestProfile;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

public class DataIsolationTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("kogito.persistence.data-isolation.enabled", "true");
    }

    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return Set.of(MockProcessesBean.class);
    }

    @ApplicationScoped
    @Alternative
    public static class MockProcessesBean implements Processes {

        @SuppressWarnings("unchecked")
        @Override
        public Collection<Process<?>> processes() {
            Process<?> p = new Process<>() {
                @Override
                public String id() {
                    return "processId1";
                }

                @Override
                public String version() {
                    return "1.0";
                }

                @Override
                public org.kie.kogito.process.ProcessInstance<Object> createInstance(Object workItem) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public org.kie.kogito.process.ProcessInstance<Object> createInstance(String businessKey, Object workItem) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public org.kie.kogito.process.ProcessInstance<Object> createInstance(org.kie.kogito.Model model) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public org.kie.kogito.process.ProcessInstance<Object> createReadOnlyInstance(
                        org.kie.kogito.process.MutableProcessInstances<Object> instances) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public org.kie.kogito.process.ProcessInstances<Object> instances() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public org.kie.kogito.process.ProcessConfig configure() {
                    throw new UnsupportedOperationException();
                }
            };
            return List.of(p);
        }

        @Override
        public Process<?> processById(String processId) {
            return processes().stream()
                    .filter(p -> p.id().equals(processId))
                    .findFirst()
                    .orElse(null);
        }
    }
}
