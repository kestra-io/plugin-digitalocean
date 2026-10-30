package io.kestra.plugin.digitalocean.app.deployment;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CancelTest extends AbstractDigitalOceanTest {

    private static final String PATH = GetTest.PATH + "/cancel";

    private static final String CANCELED_DEPLOYMENT_JSON = """
        {"deployment": {"id": "3aa4d20e-5527-4c00-b496-601fbd22520a", "cause": "manual", "phase": "CANCELED",
          "created_at": "2026-10-13T12:00:00Z"}}
        """;

    private Cancel task(WireMockRuntimeInfo wireMockRuntimeInfo, Property<String> appId, Property<String> deploymentId) {
        return Cancel.builder()
            .id(IdUtils.create())
            .type(Cancel.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .appId(appId)
            .deploymentId(deploymentId)
            .build();
    }

    @Test
    void cancelsTheDeploymentAndReturnsItsNewPhase(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubPostJson(PATH, 200, CANCELED_DEPLOYMENT_JSON);

        var output = task(wireMockRuntimeInfo, Property.ofValue(GetTest.APP_ID), Property.ofValue(GetTest.DEPLOYMENT_ID)).run(runContext());

        assertThat(output.getPhase(), is("CANCELED"));
        verifyBearer(postRequestedFor(urlPathEqualTo(PATH)), "test-token");
    }

    @Test
    void surfacesTheApiReasonWhenTheDeploymentCannotBeCanceled(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubPostJson(PATH, 400, "{\"id\": \"bad_request\", \"message\": \"deployment is not in progress\"}");

        var task = task(wireMockRuntimeInfo, Property.ofValue(GetTest.APP_ID), Property.ofValue(GetTest.DEPLOYMENT_ID));

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("HTTP 400"));
        assertThat(ex.getMessage(), containsString("deployment is not in progress"));
    }

    @Test
    void rendersTheIdsFromExpressions(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubPostJson(PATH, 200, CANCELED_DEPLOYMENT_JSON);

        task(wireMockRuntimeInfo, Property.ofExpression("{{ inputs.appId }}"), Property.ofExpression("{{ inputs.deploymentId }}"))
            .run(runContextFactory.of(Map.of("inputs", Map.of("appId", GetTest.APP_ID, "deploymentId", GetTest.DEPLOYMENT_ID))));

        verify(postRequestedFor(urlPathEqualTo(PATH)));
    }
}
