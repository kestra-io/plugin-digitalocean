package io.kestra.plugin.digitalocean.volume;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListActionsTest extends AbstractDigitalOceanTest {

    private static final String ACTIONS_JSON = """
        {
          "actions": [
            {"id": 72531856, "status": "completed", "type": "attach_volume", "region_slug": "nyc3"},
            {"id": 72531900, "status": "in-progress", "type": "resize_volume", "region_slug": "nyc3"}
          ],
          "links": {"pages": {}},
          "meta": {"total": 2}
        }
        """;

    private ListActions task(WireMockRuntimeInfo wireMockRuntimeInfo) {
        return ListActions.builder()
            .id(IdUtils.create())
            .type(ListActions.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .volumeId(Property.ofValue("vol-1"))
            .build();
    }

    @Test
    void listsVolumeActionsAndSendsBearerToken(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/v2/volumes/vol-1/actions", ACTIONS_JSON);

        var output = task(wireMockRuntimeInfo).run(runContext());

        assertThat(output.getTotal(), is(2L));
        assertThat(output.getRows().get(1).get("type"), is("resize_volume"));
        verifyBearer(getRequestedFor(urlPathEqualTo("/v2/volumes/vol-1/actions")), "test-token");
    }

    @Test
    void failsWithClearMessageOnRateLimit(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubStatusWithHeader("/v2/volumes/vol-1/actions", 429, "{\"message\":\"too many requests\"}", "retry-after", "20");

        var task = task(wireMockRuntimeInfo);

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("rate limit"));
    }

    @Test
    void rendersTheVolumeIdFromAnExpression(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/v2/volumes/vol-1/actions", ACTIONS_JSON);

        var task = ListActions.builder()
            .id(IdUtils.create())
            .type(ListActions.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .volumeId(Property.ofExpression("{{ inputs.volumeId }}"))
            .build();

        var output = task.run(runContextFactory.of(Map.of("inputs", Map.of("volumeId", "vol-1"))));

        assertThat(output.getTotal(), is(2L));
    }
}
