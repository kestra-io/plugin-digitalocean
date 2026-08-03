package io.kestra.plugin.digitalocean.kubernetes;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTest;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetTest extends AbstractDigitalOceanTest {

    private static final String CLUSTER_JSON = """
        {
          "kubernetes_cluster": {"id": "cluster-1", "name": "prod", "region": "nyc1", "version": "1.30.2-do.0",
             "status": {"state": "running"}, "endpoint": "https://cluster-1.k8s.ondigitalocean.com",
             "created_at": "2024-01-01T00:00:00Z"}
        }
        """;

    @Test
    void fetchesClusterAndSendsBearerToken(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/v2/kubernetes/clusters/cluster-1", CLUSTER_JSON);

        var task = Get.builder()
            .id("get-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .clusterId(Property.ofValue("cluster-1"))
            .build();

        var output = task.run(runContext());

        assertThat(output.getId(), is("cluster-1"));
        assertThat(output.getStatus(), is("running"));
        assertThat(output.getEndpoint(), is("https://cluster-1.k8s.ondigitalocean.com"));
        verifyBearer(getRequestedFor(urlPathEqualTo("/v2/kubernetes/clusters/cluster-1")), "test-token");
    }

    @Test
    void failsWithClearMessageWhenNotFound(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubStatus("/v2/kubernetes/clusters/missing", 404, "{\"message\":\"not found\"}");

        var task = Get.builder()
            .id("get-404-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .clusterId(Property.ofValue("missing"))
            .build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("resource not found"));
    }
}
