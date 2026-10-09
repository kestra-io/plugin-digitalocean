package io.kestra.plugin.digitalocean.spaces.key;

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
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListTest extends AbstractDigitalOceanTest {

    private static final String KEYS_JSON = """
        {
          "keys": [
            {"name": "reports key", "access_key": "DO00ACCESS", "grants": [{"bucket": "reports", "permission": "readwrite"}], "created_at": "2026-10-06T12:00:00Z"}
          ],
          "links": {},
          "meta": {"total": 1}
        }
        """;

    @Test
    void listsKeysWithFiltersAndSendsBearerToken(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/v2/spaces/keys", KEYS_JSON);

        var task = List.builder()
            .id(IdUtils.create())
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .name(Property.ofValue("reports key"))
            .bucket(Property.ofValue("reports"))
            .permission(Property.ofValue(Permission.READWRITE))
            .build();

        var output = task.run(runContext());

        assertThat(output.getTotal(), is(1L));
        assertThat(output.getRows().getFirst().get("access_key"), is("DO00ACCESS"));
        verify(getRequestedFor(urlPathEqualTo("/v2/spaces/keys"))
            .withQueryParam("name", equalTo("reports key"))
            .withQueryParam("bucket", equalTo("reports"))
            .withQueryParam("permission", equalTo("readwrite")));
        verifyBearer(getRequestedFor(urlPathEqualTo("/v2/spaces/keys")), "test-token");
    }

    @Test
    void failsWithClearMessageOnRateLimit(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubStatus("/v2/spaces/keys", 429, "{\"message\":\"too many requests\"}");

        var task = List.builder()
            .id(IdUtils.create())
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("rate limit"));
    }

    @Test
    void rendersThePermissionFilterFromAnExpression(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/v2/spaces/keys", KEYS_JSON);

        var task = List.builder()
            .id(IdUtils.create())
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .permission(Property.ofExpression("{{ inputs.permission }}"))
            .build();

        task.run(runContextFactory.of(Map.of("inputs", Map.of("permission", "FULLACCESS"))));

        verify(getRequestedFor(urlPathEqualTo("/v2/spaces/keys")).withQueryParam("permission", equalTo("fullaccess")));
    }
}
