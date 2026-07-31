package io.kestra.plugin.digitalocean.kubernetes;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTest;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CreateTest extends AbstractDigitalOceanTest {

    private static final String CLUSTER_JSON = """
        {
          "kubernetes_cluster": {"id": "cluster-2", "name": "staging", "region": "nyc1", "version": "1.30.2-do.0",
             "status": {"state": "provisioning"}, "endpoint": ""}
        }
        """;

    private static NodePool workerPool() {
        return NodePool.builder().name("worker-pool").size("s-2vcpu-4gb").count(3).build();
    }

    @Test
    void createsClusterAndSendsBearerToken(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubPostJson("/v2/kubernetes/clusters", 201, CLUSTER_JSON);

        var task = Create.builder()
            .id("create-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .name(Property.ofValue("staging"))
            .region(Property.ofValue("nyc1"))
            .kubernetesVersion(Property.ofValue("1.30.2-do.0"))
            .nodePools(Property.ofValue(java.util.List.of(workerPool())))
            .build();

        var output = task.run(runContext());

        assertThat(output.getId(), is("cluster-2"));
        assertThat(output.getStatus(), is("provisioning"));
        verifyBearer(postRequestedFor(urlPathEqualTo("/v2/kubernetes/clusters")), "test-token");
        verify(postRequestedFor(urlPathEqualTo("/v2/kubernetes/clusters"))
            .withRequestBody(containing("\"size\":\"s-2vcpu-4gb\""))
            .withRequestBody(containing("\"name\":\"worker-pool\""))
            .withRequestBody(containing("\"count\":3")));
    }

    @Test
    void requiresAtLeastOneNodePool(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = Create.builder()
            .id("create-no-pools-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .name(Property.ofValue("staging"))
            .region(Property.ofValue("nyc1"))
            .kubernetesVersion(Property.ofValue("1.30.2-do.0"))
            .nodePools(Property.ofValue(java.util.List.of()))
            .build();

        var runContext = runContext();
        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("nodePools"));
    }

    @Test
    void failsWithClearMessageOnInvalidToken(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubPostJson("/v2/kubernetes/clusters", 401, "{\"message\":\"Unable to authenticate you\"}");

        var task = Create.builder()
            .id("create-401-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("bad-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .name(Property.ofValue("staging"))
            .region(Property.ofValue("nyc1"))
            .kubernetesVersion(Property.ofValue("1.30.2-do.0"))
            .nodePools(Property.ofValue(java.util.List.of(workerPool())))
            .build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("invalid or missing API token"));
    }
}
