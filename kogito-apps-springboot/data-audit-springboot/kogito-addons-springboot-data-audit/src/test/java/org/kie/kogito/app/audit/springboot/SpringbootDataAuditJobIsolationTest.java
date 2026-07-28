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
import org.kie.kogito.jobs.service.model.JobStatus;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.Processes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import io.restassured.http.ContentType;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.app.audit.quarkus.DataAuditTestUtils.newJobEvent;
import static org.kie.kogito.app.audit.quarkus.DataAuditTestUtils.newJobEventWithVersion;
import static org.kie.kogito.app.audit.quarkus.DataAuditTestUtils.wrapQuery;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = { "server_port=0", "kogito.persistence.data-isolation.enabled=true" })
@TestInstance(Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class SpringbootDataAuditJobIsolationTest {

    private static final String ALLOWED_PROCESS_ID = "sbJobProcess";
    private static final String ALLOWED_PROCESS_VERSION = "1.0";

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
            // own process job — must be visible
            publisher.publish(newJobEvent(
                    "sb-job1", "node1", 1,
                    ALLOWED_PROCESS_ID, "sb-pi1", 100L, 10,
                    null, null,
                    JobStatus.SCHEDULED, 0));

            // foreign process job — must be invisible
            publisher.publish(newJobEvent(
                    "sb-job2", "node1", 1,
                    "foreignProcess", "sb-pi2", 100L, 10,
                    null, null,
                    JobStatus.SCHEDULED, 0));

            // sub-process job rooted at own process — must be visible
            publisher.publish(newJobEvent(
                    "sb-job3", "node1", 1,
                    "child-process", "sb-pi3", 100L, 10,
                    ALLOWED_PROCESS_ID, "sb-pi1",
                    JobStatus.SCHEDULED, 0));

            // correct version — must be visible
            publisher.publish(newJobEventWithVersion(
                    "sb-job4", "node1", 1,
                    ALLOWED_PROCESS_ID, ALLOWED_PROCESS_VERSION, "sb-pi4", 100L, 10,
                    null, null, null,
                    JobStatus.SCHEDULED, 0));

            // wrong version — must be invisible
            publisher.publish(newJobEventWithVersion(
                    "sb-job5", "node1", 1,
                    ALLOWED_PROCESS_ID, "2.0", "sb-pi5", 100L, 10,
                    null, null, null,
                    JobStatus.SCHEDULED, 0));
        }
    }

    @Test
    public void testGetAllScheduledJobs_foreignProcessInvisible() {
        assertThat(queryJobs("GetAllScheduledJobs"))
                .extracting(e -> e.get("jobId"))
                .doesNotContain("sb-job2");
    }

    @Test
    public void testGetAllJobs_foreignProcessInvisible() {
        assertThat(queryJobs("GetAllJobs"))
                .extracting(e -> e.get("jobId"))
                .doesNotContain("sb-job2");
    }

    @Test
    public void testGetAllPendingJobs_foreignProcessInvisible() {
        assertThat(queryJobs("GetAllPendingJobs"))
                .extracting(e -> e.get("jobId"))
                .doesNotContain("sb-job2");
    }

    @Test
    public void testGetAllScheduledJobs_ownAndSubProcessVisible() {
        assertThat(queryJobs("GetAllScheduledJobs"))
                .extracting(e -> e.get("jobId"))
                .contains("sb-job1", "sb-job3");
    }

    @Test
    public void testGetAllJobs_subProcessVisibleViaRootProcessId() {
        assertThat(queryJobs("GetAllJobs"))
                .extracting(e -> e.get("jobId"))
                .contains("sb-job3");
    }

    @Test
    public void testGetAllJobs_correctVersionVisible() {
        assertThat(queryJobs("GetAllJobs"))
                .extracting(e -> e.get("jobId"))
                .contains("sb-job4");
    }

    @Test
    public void testGetAllJobs_wrongVersionInvisible() {
        assertThat(queryJobs("GetAllJobs"))
                .extracting(e -> e.get("jobId"))
                .doesNotContain("sb-job5");
    }

    @Test
    public void testGetAllScheduledJobs_wrongVersionInvisible() {
        assertThat(queryJobs("GetAllScheduledJobs"))
                .extracting(e -> e.get("jobId"))
                .doesNotContain("sb-job5");
    }

    private List<Map<String, Object>> queryJobs(String queryName) {
        return given()
                .port(port)
                .contentType(ContentType.JSON)
                .body(wrapQuery("{ " + queryName + " { jobId, status, processInstanceId } }"))
                .when()
                .post(SubsystemConstants.DATA_AUDIT_QUERY_PATH)
                .then()
                .statusCode(200)
                .extract()
                .path("data." + queryName);
    }
}
