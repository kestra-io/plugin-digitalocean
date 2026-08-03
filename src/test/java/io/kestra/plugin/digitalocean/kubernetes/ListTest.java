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

class ListTest extends AbstractDigitalOceanTest {

    private static final String CLUSTERS_JSON = """
        {
          "kubernetes_clusters": [
            {"id": "cluster-1", "name": "prod", "region": "nyc1", "version": "1.30.2-do.0",
             "status": {"state": "running"}, "endpoint": "https://cluster-1.k8s.ondigitalocean.com"}
          ],
          "links": {"pages": {}},
          "meta": {"total": 1}
        }
        """;

    @Test
    void listsClustersAndSendsBearerToken(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/v2/kubernetes/clusters", CLUSTERS_JSON);

        var task = List.builder()
            .id("list-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .build();

        var output = task.run(runContext());

        assertThat(output.getTotal(), is(1L));
        assertThat(output.getRows().getFirst().get("name"), is("prod"));
        verifyBearer(getRequestedFor(urlPathEqualTo("/v2/kubernetes/clusters")), "test-token");
    }

    @Test
    void failsWithClearMessageOnRateLimit(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubStatusWithHeader("/v2/kubernetes/clusters", 429, "{\"message\":\"too many requests\"}", "retry-after", "10");

        var task = List.builder()
            .id("list-429-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("rate limit"));
    }
}
