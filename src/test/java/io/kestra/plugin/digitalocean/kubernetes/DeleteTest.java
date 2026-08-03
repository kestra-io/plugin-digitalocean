package io.kestra.plugin.digitalocean.kubernetes;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTest;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeleteTest extends AbstractDigitalOceanTest {

    @Test
    void deletesClusterAndSendsBearerToken(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(delete(urlPathEqualTo("/v2/kubernetes/clusters/cluster-1")).willReturn(aResponse().withStatus(204)));

        var task = Delete.builder()
            .id("delete-test")
            .type(Delete.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .clusterId(Property.ofValue("cluster-1"))
            .build();

        task.run(runContext());

        verifyBearer(deleteRequestedFor(urlPathEqualTo("/v2/kubernetes/clusters/cluster-1")), "test-token");
    }

    @Test
    void failsWithClearMessageWhenNotFound(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(delete(urlPathEqualTo("/v2/kubernetes/clusters/missing")).willReturn(aResponse().withStatus(404).withBody("{\"message\":\"not found\"}")));

        var task = Delete.builder()
            .id("delete-404-test")
            .type(Delete.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .clusterId(Property.ofValue("missing"))
            .build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("resource not found"));
    }
}
