package io.kestra.plugin.digitalocean.app.deployment;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CreateTest extends AbstractDigitalOceanTest {

    private static final String PATH = "/v2/apps/" + GetTest.APP_ID + "/deployments";

    private static final String PENDING_DEPLOYMENT_JSON = """
        {"deployment": {"id": "3aa4d20e-5527-4c00-b496-601fbd22520a", "cause": "manual", "phase": "PENDING_BUILD",
          "created_at": "2026-10-13T12:00:00Z", "progress": {"success_steps": 0, "error_steps": 0, "total_steps": 3}}}
        """;

    private Create.CreateBuilder<?, ?> builder(WireMockRuntimeInfo wireMockRuntimeInfo) {
        return Create.builder()
            .id(IdUtils.create())
            .type(Create.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .appId(Property.ofValue(GetTest.APP_ID));
    }

    @Test
    void startsADeploymentWithoutForcingABuildByDefault(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubPostJson(PATH, 200, PENDING_DEPLOYMENT_JSON);

        var output = builder(wireMockRuntimeInfo).build().run(runContext());

        assertThat(output.getId(), is("3aa4d20e-5527-4c00-b496-601fbd22520a"));
        assertThat(output.getPhase(), is("PENDING_BUILD"));
        verifyBearer(postRequestedFor(urlPathEqualTo(PATH)), "test-token");
        verify(postRequestedFor(urlPathEqualTo(PATH)).withRequestBody(equalToJson("""
            {"force_build": false}
            """)));
    }

    @Test
    void forcesABuildWhenAsked(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubPostJson(PATH, 200, PENDING_DEPLOYMENT_JSON);

        builder(wireMockRuntimeInfo).forceBuild(Property.ofValue(true)).build().run(runContext());

        verify(postRequestedFor(urlPathEqualTo(PATH)).withRequestBody(equalToJson("""
            {"force_build": true}
            """)));
    }

    @Test
    void failsWithClearMessageWhenTheAppDoesNotExist(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubPostJson("/v2/apps/missing/deployments", 404, "{\"id\": \"not_found\", \"message\": \"app not found\"}");

        var task = builder(wireMockRuntimeInfo).appId(Property.ofValue("missing")).build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("HTTP 404"));
        assertThat(ex.getMessage(), containsString("app not found"));
    }

    @Test
    void rendersTheAppIdAndForceBuildFromExpressions(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubPostJson(PATH, 200, PENDING_DEPLOYMENT_JSON);

        builder(wireMockRuntimeInfo)
            .appId(Property.ofExpression("{{ inputs.appId }}"))
            .forceBuild(Property.ofExpression("{{ inputs.forceBuild }}"))
            .build()
            .run(runContextFactory.of(Map.of("inputs", Map.of("appId", GetTest.APP_ID, "forceBuild", true))));

        verify(postRequestedFor(urlPathEqualTo(PATH)).withRequestBody(equalToJson("""
            {"force_build": true}
            """)));
    }
}
