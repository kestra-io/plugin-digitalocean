package io.kestra.plugin.digitalocean.app.deployment;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetTest extends AbstractDigitalOceanTest {

    static final String APP_ID = "4f6c71e2-1e90-4762-9fee-6cc4a0a9f2cf";
    static final String DEPLOYMENT_ID = "3aa4d20e-5527-4c00-b496-601fbd22520a";
    static final String PATH = "/v2/apps/" + APP_ID + "/deployments/" + DEPLOYMENT_ID;

    static final String FAILED_DEPLOYMENT_JSON = """
        {"deployment": {"id": "3aa4d20e-5527-4c00-b496-601fbd22520a", "cause": "manual", "phase": "ERROR",
          "created_at": "2026-10-13T12:00:00Z", "updated_at": "2026-10-13T12:04:00Z", "phase_last_updated_at": "2026-10-13T12:04:00Z",
          "spec": {"name": "web"},
          "progress": {"success_steps": 1, "error_steps": 1, "pending_steps": 0, "running_steps": 0, "total_steps": 3,
            "steps": [
              {"name": "build", "status": "ERROR", "steps": [
                {"name": "initialize", "status": "SUCCESS"},
                {"name": "components", "status": "ERROR", "steps": [
                  {"name": "api", "status": "ERROR", "component_name": "api",
                   "reason": {"code": "BuildJobFailed", "message": "npm run build exited with code 1"}}
                ]}
              ]},
              {"name": "deploy", "status": "PENDING"}
            ]}}}
        """;

    static final String ACTIVE_DEPLOYMENT_JSON = """
        {"deployment": {"id": "3aa4d20e-5527-4c00-b496-601fbd22520a", "cause": "manual", "phase": "ACTIVE",
          "created_at": "2026-10-13T12:00:00Z", "updated_at": "2026-10-13T12:06:00Z", "phase_last_updated_at": "2026-10-13T12:06:00Z",
          "progress": {"success_steps": 3, "error_steps": 0, "pending_steps": 0, "running_steps": 0, "total_steps": 3,
            "steps": [{"name": "build", "status": "SUCCESS"}, {"name": "deploy", "status": "SUCCESS"}]}}}
        """;

    private Get task(WireMockRuntimeInfo wireMockRuntimeInfo, Property<String> appId, Property<String> deploymentId) {
        return Get.builder()
            .id(IdUtils.create())
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .appId(appId)
            .deploymentId(deploymentId)
            .build();
    }

    @Test
    void mapsAFailedDeploymentWithTheReasonOfTheNestedStep(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson(PATH, FAILED_DEPLOYMENT_JSON);

        var output = task(wireMockRuntimeInfo, Property.ofValue(APP_ID), Property.ofValue(DEPLOYMENT_ID)).run(runContext());

        assertThat(output.getId(), is(DEPLOYMENT_ID));
        assertThat(output.getPhase(), is("ERROR"));
        assertThat(output.getCause(), is("manual"));
        assertThat(output.getSuccessSteps(), is(1));
        assertThat(output.getErrorSteps(), is(1));
        assertThat(output.getTotalSteps(), is(3));
        assertThat(output.getErrorMessage(), is("npm run build exited with code 1"));
        assertThat(output.getCreatedAt(), is(Instant.parse("2026-10-13T12:00:00Z")));
        assertThat(output.getPhaseLastUpdatedAt(), is(Instant.parse("2026-10-13T12:04:00Z")));
        verifyBearer(getRequestedFor(urlPathEqualTo(PATH)), "test-token");
    }

    @Test
    void leavesTheErrorMessageEmptyWhenNoStepFailed(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson(PATH, ACTIVE_DEPLOYMENT_JSON);

        var output = task(wireMockRuntimeInfo, Property.ofValue(APP_ID), Property.ofValue(DEPLOYMENT_ID)).run(runContext());

        assertThat(output.getPhase(), is("ACTIVE"));
        assertThat(output.getErrorMessage(), nullValue());
    }

    @Test
    void failsWithClearMessageWhenNotFound(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubStatus("/v2/apps/" + APP_ID + "/deployments/missing", 404, "{\"id\":\"not_found\",\"message\":\"deployment not found\"}");

        var task = task(wireMockRuntimeInfo, Property.ofValue(APP_ID), Property.ofValue("missing"));

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("HTTP 404"));
    }

    @Test
    void rendersTheIdsFromExpressions(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson(PATH, ACTIVE_DEPLOYMENT_JSON);

        task(wireMockRuntimeInfo, Property.ofExpression("{{ inputs.appId }}"), Property.ofExpression("{{ inputs.deploymentId }}"))
            .run(runContextFactory.of(Map.of("inputs", Map.of("appId", APP_ID, "deploymentId", DEPLOYMENT_ID))));

        verify(getRequestedFor(urlPathEqualTo(PATH)));
    }
}
