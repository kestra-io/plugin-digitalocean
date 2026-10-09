package io.kestra.plugin.digitalocean.spaces.key;

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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetTest extends AbstractDigitalOceanTest {

    private Get task(WireMockRuntimeInfo wireMockRuntimeInfo) {
        return Get.builder()
            .id(IdUtils.create())
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .accessKey(Property.ofValue("DO00ACCESS"))
            .build();
    }

    @Test
    void readsKeyAndSendsBearerToken(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/v2/spaces/keys/DO00ACCESS", """
            {"key": {"name": "full-key", "access_key": "DO00ACCESS",
              "grants": [{"bucket": "", "permission": "fullaccess"}], "created_at": "2026-10-06T12:00:00Z"}}
            """);

        var output = task(wireMockRuntimeInfo).run(runContext());

        assertThat(output.getName(), is("full-key"));
        assertThat(output.getAccessKey(), is("DO00ACCESS"));
        assertThat(output.getGrants().getFirst(), is(Map.of("bucket", "", "permission", "fullaccess")));
        assertThat(output.getCreatedAt(), is(Instant.parse("2026-10-06T12:00:00Z")));
        verifyBearer(getRequestedFor(urlPathEqualTo("/v2/spaces/keys/DO00ACCESS")), "test-token");
    }

    @Test
    void failsWithClearMessageWhenNotFound(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubStatus("/v2/spaces/keys/DO00ACCESS", 404, "{\"id\":\"not_found\",\"message\":\"key not found\"}");

        var task = task(wireMockRuntimeInfo);

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("HTTP 404"));
    }

    @Test
    void rendersTheAccessKeyFromAnExpression(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/v2/spaces/keys/DO00ACCESS", """
            {"key": {"name": "full-key", "access_key": "DO00ACCESS", "grants": [], "created_at": "2026-10-06T12:00:00Z"}}
            """);

        var task = Get.builder()
            .id(IdUtils.create())
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .accessKey(Property.ofExpression("{{ outputs.create_key.accessKey }}"))
            .build();

        var output = task.run(runContextFactory.of(Map.of("outputs", Map.of("create_key", Map.of("accessKey", "DO00ACCESS")))));

        assertThat(output.getGrants(), is(empty()));
    }
}
