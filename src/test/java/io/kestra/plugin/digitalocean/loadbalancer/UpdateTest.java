package io.kestra.plugin.digitalocean.loadbalancer;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UpdateTest extends AbstractDigitalOceanTest {

    private static final String LOAD_BALANCER_JSON = """
        {
          "load_balancer": {"id": "lb-1", "name": "web-lb", "ip": "192.0.2.1", "status": "active", "region": {"slug": "nyc3"}}
        }
        """;

    @Test
    void updatesLoadBalancerAndSendsBearerToken(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(put(urlPathEqualTo("/v2/load_balancers/lb-1")).willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(LOAD_BALANCER_JSON)));

        var task = Update.builder()
            .id("update-test")
            .type(Update.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .loadBalancerId(Property.ofValue("lb-1"))
            .name(Property.ofValue("web-lb"))
            .region(Property.ofValue("nyc3"))
            .forwardingRules(Property.ofValue(java.util.List.of(Map.of("entry_protocol", "https", "entry_port", 443, "target_protocol", "http", "target_port", 80))))
            .build();

        var output = task.run(runContext());

        assertThat(output.getId(), is("lb-1"));
        verifyBearer(putRequestedFor(urlPathEqualTo("/v2/load_balancers/lb-1")), "test-token");
    }

    @Test
    void failsWithClearMessageWhenNotFound(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(put(urlPathEqualTo("/v2/load_balancers/missing")).willReturn(aResponse().withStatus(404).withBody("{\"message\":\"not found\"}")));

        var task = Update.builder()
            .id("update-404-test")
            .type(Update.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .loadBalancerId(Property.ofValue("missing"))
            .name(Property.ofValue("web-lb"))
            .region(Property.ofValue("nyc3"))
            .forwardingRules(Property.ofValue(java.util.List.of(Map.of("entry_protocol", "http", "entry_port", 80, "target_protocol", "http", "target_port", 80))))
            .build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("resource not found"));
    }
}
