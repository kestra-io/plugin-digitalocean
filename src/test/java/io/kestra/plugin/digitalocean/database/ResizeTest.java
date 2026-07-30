package io.kestra.plugin.digitalocean.database;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTest;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResizeTest extends AbstractDigitalOceanTest {

    @Test
    void resizesDatabaseAndSendsBearerToken(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(put(urlPathEqualTo("/v2/databases/db-1/resize")).willReturn(aResponse().withStatus(202)));

        var task = Resize.builder()
            .id("resize-test")
            .type(Resize.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .databaseId(Property.ofValue("db-1"))
            .size(Property.ofValue("db-s-2vcpu-4gb"))
            .numNodes(Property.ofValue(2))
            .build();

        task.run(runContext());

        verifyBearer(putRequestedFor(urlPathEqualTo("/v2/databases/db-1/resize")), "test-token");
    }

    @Test
    void failsWithClearMessageOnRateLimit(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(put(urlPathEqualTo("/v2/databases/db-1/resize")).willReturn(aResponse().withStatus(429).withBody("{\"message\":\"too many requests\"}")));

        var task = Resize.builder()
            .id("resize-429-test")
            .type(Resize.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .databaseId(Property.ofValue("db-1"))
            .size(Property.ofValue("db-s-2vcpu-4gb"))
            .build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("rate limit"));
    }
}
