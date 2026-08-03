package io.kestra.plugin.digitalocean.kubernetes;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTest;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetKubeconfigTest extends AbstractDigitalOceanTest {

    private static final String KUBECONFIG_YAML = "apiVersion: v1\nkind: Config\nclusters: []\n";

    @Test
    void downloadsKubeconfigAndSendsBearerToken(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlPathEqualTo("/v2/kubernetes/clusters/cluster-1/kubeconfig"))
            .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "text/yaml").withBody(KUBECONFIG_YAML)));

        var task = GetKubeconfig.builder()
            .id("get-kubeconfig-test")
            .type(GetKubeconfig.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .clusterId(Property.ofValue("cluster-1"))
            .build();

        var runContext = runContext();
        var output = task.run(runContext);

        assertThat(output.getUri(), notNullValue());
        try (InputStream is = runContext.storage().getFile(output.getUri())) {
            var content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(content, containsString("kind: Config"));
        }
        verifyBearer(getRequestedFor(urlPathEqualTo("/v2/kubernetes/clusters/cluster-1/kubeconfig")), "test-token");
        verify(getRequestedFor(urlPathEqualTo("/v2/kubernetes/clusters/cluster-1/kubeconfig"))
            .withHeader("Accept", containing("yaml")));
    }

    @Test
    void failsWithClearMessageWhenNotFound(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubStatus("/v2/kubernetes/clusters/missing/kubeconfig", 404, "{\"message\":\"not found\"}");

        var task = GetKubeconfig.builder()
            .id("get-kubeconfig-404-test")
            .type(GetKubeconfig.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .clusterId(Property.ofValue("missing"))
            .build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("resource not found"));
    }
}
