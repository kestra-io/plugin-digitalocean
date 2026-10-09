package io.kestra.plugin.digitalocean.spaces.key;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeleteTest extends AbstractDigitalOceanTest {

    private Delete task(WireMockRuntimeInfo wireMockRuntimeInfo, String accessKey) {
        return Delete.builder()
            .id(IdUtils.create())
            .type(Delete.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .accessKey(Property.ofValue(accessKey))
            .build();
    }

    @Test
    void deletesKeyAndSendsBearerToken(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(delete(urlPathEqualTo("/v2/spaces/keys/DO00ACCESS")).willReturn(aResponse().withStatus(204)));

        task(wireMockRuntimeInfo, "DO00ACCESS").run(runContext());

        verifyBearer(deleteRequestedFor(urlPathEqualTo("/v2/spaces/keys/DO00ACCESS")), "test-token");
    }

    @Test
    void failsWithClearMessageWhenNotFound(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(delete(urlPathEqualTo("/v2/spaces/keys/missing")).willReturn(aResponse().withStatus(404).withBody("{\"message\":\"not found\"}")));

        var task = task(wireMockRuntimeInfo, "missing");

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("HTTP 404"));
    }

    @Test
    void rendersTheAccessKeyFromAnExpression(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(delete(urlPathEqualTo("/v2/spaces/keys/DO00ACCESS")).willReturn(aResponse().withStatus(204)));

        var task = Delete.builder()
            .id(IdUtils.create())
            .type(Delete.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .accessKey(Property.ofExpression("{{ inputs.accessKey }}"))
            .build();

        task.run(runContextFactory.of(Map.of("inputs", Map.of("accessKey", "DO00ACCESS"))));

        verify(deleteRequestedFor(urlPathEqualTo("/v2/spaces/keys/DO00ACCESS")));
    }
}
