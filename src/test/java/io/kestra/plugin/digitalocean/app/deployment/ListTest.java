package io.kestra.plugin.digitalocean.app.deployment;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListTest extends AbstractDigitalOceanTest {

    private static final String PATH = "/v2/apps/" + GetTest.APP_ID + "/deployments";

    private static final String DEPLOYMENTS_JSON = """
        {
          "deployments": [
            {"id": "3aa4d20e-5527-4c00-b496-601fbd22520a", "cause": "manual", "phase": "ACTIVE",
             "created_at": "2026-10-13T12:00:00Z", "spec": {"name": "web", "services": [{"name": "api"}]},
             "services": [{"name": "api", "source_commit_hash": "9a4df0b"}],
             "progress": {"success_steps": 3, "error_steps": 0, "total_steps": 3, "steps": [{"name": "build", "status": "SUCCESS"}]}}
          ],
          "links": {},
          "meta": {"total": 1}
        }
        """;

    private List.ListBuilder<?, ?> builder(WireMockRuntimeInfo wireMockRuntimeInfo) {
        return List.builder()
            .id(IdUtils.create())
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .appId(Property.ofValue(GetTest.APP_ID));
    }

    @Test
    void listsDeploymentsWithoutTheAppSpecOrTheSteps(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson(PATH, DEPLOYMENTS_JSON);

        var output = builder(wireMockRuntimeInfo).build().run(runContext());

        assertThat(output.getTotal(), is(1L));
        var row = output.getRows().getFirst();
        assertThat(row.get("phase"), is("ACTIVE"));
        assertThat(row, not(hasKey("spec")));
        assertThat(row, not(hasKey("services")));
        @SuppressWarnings("unchecked")
        var progress = (Map<String, Object>) row.get("progress");
        assertThat(progress.get("success_steps"), is(3));
        assertThat(progress, not(hasKey("steps")));
        verify(getRequestedFor(urlPathEqualTo(PATH)).withQueryParam("per_page", equalTo("200")));
        verifyBearer(getRequestedFor(urlPathEqualTo(PATH)), "test-token");
    }

    @Test
    void failsWithClearMessageOnRateLimit(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubStatus(PATH, 429, "{\"message\":\"too many requests\"}");

        var task = builder(wireMockRuntimeInfo).build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("rate limit"));
    }

    @Test
    void rendersTheAppIdFromAnExpression(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson(PATH, DEPLOYMENTS_JSON);

        builder(wireMockRuntimeInfo)
            .appId(Property.ofExpression("{{ inputs.appId }}"))
            .build()
            .run(runContextFactory.of(Map.of("inputs", Map.of("appId", GetTest.APP_ID))));

        verify(getRequestedFor(urlPathEqualTo(PATH)));
    }
}
