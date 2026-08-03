package io.kestra.plugin.digitalocean.loadbalancer;

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

    private static final String LOAD_BALANCER_JSON = """
        {
          "load_balancer": {"id": "lb-1", "name": "web-lb", "ip": "192.0.2.1", "status": "active", "region": {"slug": "nyc3"}}
        }
        """;

    @Test
    void fetchesLoadBalancerAndSendsBearerToken(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/v2/load_balancers/lb-1", LOAD_BALANCER_JSON);

        var task = Get.builder()
            .id("get-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .loadBalancerId(Property.ofValue("lb-1"))
            .build();

        var output = task.run(runContext());

        assertThat(output.getIp(), is("192.0.2.1"));
        assertThat(output.getStatus(), is("active"));
        verifyBearer(getRequestedFor(urlPathEqualTo("/v2/load_balancers/lb-1")), "test-token");
    }

    @Test
    void failsWithClearMessageWhenNotFound(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubStatus("/v2/load_balancers/missing", 404, "{\"message\":\"not found\"}");

        var task = Get.builder()
            .id("get-404-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .loadBalancerId(Property.ofValue("missing"))
            .build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("resource not found"));
    }
}
