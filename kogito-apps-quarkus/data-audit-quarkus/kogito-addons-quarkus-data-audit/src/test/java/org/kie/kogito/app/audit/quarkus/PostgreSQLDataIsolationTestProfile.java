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

import java.util.Map;
import java.util.Set;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Quarkus test profile that activates both data-isolation and the PostgreSQL
 * datasource configuration (via the "test-postgresql" config profile).
 *
 * It re-uses {@link DataIsolationTestProfile.MockProcessesBean} so the same
 * mock Processes bean allows processId1 / version 1.0.
 */
public class PostgreSQLDataIsolationTestProfile implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
        return "test-postgresql";
    }

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("kogito.persistence.data-isolation.enabled", "true");
    }

    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return Set.of(DataIsolationTestProfile.MockProcessesBean.class);
    }
}
