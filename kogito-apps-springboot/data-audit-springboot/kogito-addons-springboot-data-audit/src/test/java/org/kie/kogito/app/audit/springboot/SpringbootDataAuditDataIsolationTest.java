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
package org.kie.kogito.app.audit.springboot;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.kie.kogito.Model;
import org.kie.kogito.app.audit.api.SubsystemConstants;
import org.kie.kogito.event.EventPublisher;
import org.kie.kogito.event.process.ProcessInstanceStateEventBody;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.Processes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import io.restassured.http.ContentType;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.app.audit.quarkus.DataAuditTestUtils.newProcessInstanceStateEvent;
import static org.kie.kogito.app.audit.quarkus.DataAuditTestUtils.newProcessInstanceStateEventWithVersion;
import static org.kie.kogito.app.audit.quarkus.DataAuditTestUtils.wrapQuery;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = { "server_port=0", "kogito.persistence.data-isolation.enabled=true" })
@TestInstance(Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class SpringbootDataAuditDataIsolationTest {

    private static final String ALLOWED_PROCESS_ID = "sbAllowedProcess";
    private static final String ALLOWED_PROCESS_VERSION = "1.0";
    private static final String OTHER_PROCESS_ID = "sbOtherProcess";
    private static final String WRONG_PROCESS_VERSION = "2.0";

    @LocalServerPort
    private Integer port;

    @Autowired
    EventPublisher publisher;

    @MockitoBean
    Processes processes;

    private final AtomicBoolean dataPublished = new AtomicBoolean(false);

    @BeforeEach
    public void init() throws Exception {
        Process<? extends Model> allowedProcess = mock(Process.class);
        when(allowedProcess.id()).thenReturn(ALLOWED_PROCESS_ID);
        when(allowedProcess.version()).thenReturn(ALLOWED_PROCESS_VERSION);
        when(processes.processes()).thenReturn(Collections.singletonList(allowedProcess));
        when(processes.processIds()).thenReturn(Set.of(ALLOWED_PROCESS_ID));

        if (dataPublished.compareAndSet(false, true)) {
            publisher.publish(newProcessInstanceStateEvent(
                    ALLOWED_PROCESS_ID, "sb-pi-1",
                    ProcessInstance.STATE_ACTIVE,
                    null, null, null, "testUser",
                    ProcessInstanceStateEventBody.EVENT_TYPE_STARTED));

            publisher.publish(newProcessInstanceStateEvent(
                    OTHER_PROCESS_ID, "sb-pi-2",
                    ProcessInstance.STATE_ACTIVE,
                    null, null, null, "testUser",
                    ProcessInstanceStateEventBody.EVENT_TYPE_STARTED));

            publisher.publish(newProcessInstanceStateEventWithVersion(
                    ALLOWED_PROCESS_ID, ALLOWED_PROCESS_VERSION, "sb-pi-3",
                    ProcessInstance.STATE_ACTIVE,
                    null, null, null, null, "testUser",
                    ProcessInstanceStateEventBody.EVENT_TYPE_STARTED));

            publisher.publish(newProcessInstanceStateEventWithVersion(
                    ALLOWED_PROCESS_ID, WRONG_PROCESS_VERSION, "sb-pi-4",
                    ProcessInstance.STATE_ACTIVE,
                    null, null, null, null, "testUser",
                    ProcessInstanceStateEventBody.EVENT_TYPE_STARTED));
        }
    }

    @Test
    public void testGetAllProcessInstancesState_foreignProcessIdInvisible() {
        List<Map<String, Object>> result = given()
                .port(port)
                .contentType(ContentType.JSON)
                .body(wrapQuery("{ GetAllProcessInstancesState { processId processInstanceId } }"))
                .when()
                .post(SubsystemConstants.DATA_AUDIT_QUERY_PATH)
                .then()
                .statusCode(200)
                .extract()
                .<List<Map<String, Object>>> path("data.GetAllProcessInstancesState");

        assertThat(result)
                .extracting(r -> r.get("processInstanceId"))
                .doesNotContain("sb-pi-2");
    }

    @Test
    public void testGetAllProcessInstancesState_allowedProcessIdVisible() {
        List<Map<String, Object>> result = given()
                .port(port)
                .contentType(ContentType.JSON)
                .body(wrapQuery("{ GetAllProcessInstancesState { processId processInstanceId } }"))
                .when()
                .post(SubsystemConstants.DATA_AUDIT_QUERY_PATH)
                .then()
                .statusCode(200)
                .extract()
                .<List<Map<String, Object>>> path("data.GetAllProcessInstancesState");

        assertThat(result)
                .extracting(r -> r.get("processInstanceId"))
                .contains("sb-pi-1");
    }

    @Test
    public void testGetAllProcessInstancesState_wrongVersionInvisible() {
        List<Map<String, Object>> result = given()
                .port(port)
                .contentType(ContentType.JSON)
                .body(wrapQuery("{ GetAllProcessInstancesState { processId processInstanceId } }"))
                .when()
                .post(SubsystemConstants.DATA_AUDIT_QUERY_PATH)
                .then()
                .statusCode(200)
                .extract()
                .<List<Map<String, Object>>> path("data.GetAllProcessInstancesState");

        assertThat(result)
                .extracting(r -> r.get("processInstanceId"))
                .doesNotContain("sb-pi-4");
    }

    @Test
    public void testGetAllProcessInstancesState_correctVersionVisible() {
        List<Map<String, Object>> result = given()
                .port(port)
                .contentType(ContentType.JSON)
                .body(wrapQuery("{ GetAllProcessInstancesState { processId processInstanceId } }"))
                .when()
                .post(SubsystemConstants.DATA_AUDIT_QUERY_PATH)
                .then()
                .statusCode(200)
                .extract()
                .<List<Map<String, Object>>> path("data.GetAllProcessInstancesState");

        assertThat(result)
                .extracting(r -> r.get("processInstanceId"))
                .contains("sb-pi-3");
    }

    @Test
    public void testGetAllProcessInstancesStateByProcessId_foreignProcessIdFiltered() {
        List<Map<String, Object>> result = given()
                .port(port)
                .contentType(ContentType.JSON)
                .body(wrapQuery("{ GetAllProcessInstancesStateByProcessId(processId: \\\"" + OTHER_PROCESS_ID + "\\\") { processId processInstanceId } }"))
                .when()
                .post(SubsystemConstants.DATA_AUDIT_QUERY_PATH)
                .then()
                .statusCode(200)
                .extract()
                .<List<Map<String, Object>>> path("data.GetAllProcessInstancesStateByProcessId");

        assertThat(result).isEmpty();
    }

    @Test
    public void testGetAllProcessInstancesStateByProcessId_wrongVersionExcluded() {
        List<Map<String, Object>> result = given()
                .port(port)
                .contentType(ContentType.JSON)
                .body(wrapQuery("{ GetAllProcessInstancesStateByProcessId(processId: \\\"" + ALLOWED_PROCESS_ID + "\\\") { processId processInstanceId } }"))
                .when()
                .post(SubsystemConstants.DATA_AUDIT_QUERY_PATH)
                .then()
                .statusCode(200)
                .extract()
                .<List<Map<String, Object>>> path("data.GetAllProcessInstancesStateByProcessId");

        assertThat(result)
                .extracting(r -> r.get("processInstanceId"))
                .doesNotContain("sb-pi-4");
    }
}
