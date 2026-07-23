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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.kie.kogito.app.audit.api.SubsystemConstants;
import org.kie.kogito.event.EventPublisher;
import org.kie.kogito.jobs.service.model.JobStatus;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;

import jakarta.inject.Inject;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.app.audit.quarkus.DataAuditTestUtils.newJobEvent;
import static org.kie.kogito.app.audit.quarkus.DataAuditTestUtils.wrapQuery;

@QuarkusTest
@TestProfile(DataIsolationTestProfile.class)
@TestInstance(Lifecycle.PER_CLASS)
public class QuarkusAuditJobIsolationTest {

    @Inject
    EventPublisher publisher;

    @BeforeAll
    public void init() throws Exception {
        // job1 belongs to processId1 — the process declared by DataIsolationTestProfile
        publisher.publish(newJobEvent(
                "iso-job1", "node1", 1,
                "processId1", "piId1", 100L, 10,
                null, null,
                JobStatus.SCHEDULED, 0));

        // job2 belongs to processId2 — a different service; must be invisible
        publisher.publish(newJobEvent(
                "iso-job2", "node1", 1,
                "processId2", "piId2", 100L, 10,
                null, null,
                JobStatus.SCHEDULED, 0));

        // job3: child process whose ROOT is processId1 — must be visible via root_process_id
        publisher.publish(newJobEvent(
                "iso-job3", "node1", 1,
                "child-process", "piId3", 100L, 10,
                "processId1", "piId1",
                JobStatus.SCHEDULED, 0));
    }

    @Test
    public void testGetAllScheduledJobs_isolationFiltersToOwnProcess() {
        List<Map<String, Object>> data = queryJobs("GetAllScheduledJobs");

        assertThat(data)
                .extracting(e -> e.get("jobId"))
                .as("GetAllScheduledJobs must only return jobs belonging to processId1")
                .containsExactlyInAnyOrder("iso-job1", "iso-job3")
                .doesNotContain("iso-job2");
    }

    @Test
    public void testGetAllJobs_isolationFiltersToOwnProcess() {
        List<Map<String, Object>> data = queryJobs("GetAllJobs");

        assertThat(data)
                .extracting(e -> e.get("jobId"))
                .as("GetAllJobs must only return jobs belonging to processId1 (or its children)")
                .containsExactlyInAnyOrder("iso-job1", "iso-job3")
                .doesNotContain("iso-job2");
    }

    @Test
    public void testGetAllPendingJobs_isolationFiltersToOwnProcess() {
        List<Map<String, Object>> data = queryJobs("GetAllPendingJobs");

        assertThat(data)
                .extracting(e -> e.get("jobId"))
                .doesNotContain("iso-job2");
    }

    @Test
    public void testSubProcess_visibleViaRootProcessId() {
        List<Map<String, Object>> data = queryJobs("GetAllJobs");

        assertThat(data)
                .extracting(e -> e.get("jobId"))
                .as("Sub-process job whose root belongs to this service must be visible")
                .contains("iso-job3");
    }

    private List<Map<String, Object>> queryJobs(String queryName) {
        String query = wrapQuery("{ " + queryName + " { jobId, status, processInstanceId } }");
        return given()
                .contentType(ContentType.JSON)
                .body(query)
                .when()
                .post(SubsystemConstants.DATA_AUDIT_QUERY_PATH)
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .path("data." + queryName);
    }
}
