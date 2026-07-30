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
import static org.hamcrest.Matchers.not;
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

    @Test
    void failsWithClearMessageWhenForbiddenWithoutAssumingBadToken(WireMockRuntimeInfo wireMockRuntimeInfo) {
        // Live QA case: DigitalOcean returns 403 while a load balancer is still processing a previous
        // action, not only when the token is invalid. The rewritten message must not claim the token is bad.
        stubFor(put(urlPathEqualTo("/v2/load_balancers/lb-1")).willReturn(aResponse().withStatus(403)
            .withBody("{\"id\":\"forbidden\",\"message\":\"Load Balancer can't be updated while it processes previous actions\"}")));

        var task = Update.builder()
            .id("update-403-test")
            .type(Update.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .loadBalancerId(Property.ofValue("lb-1"))
            .name(Property.ofValue("web-lb"))
            .region(Property.ofValue("nyc3"))
            .forwardingRules(Property.ofValue(java.util.List.of(Map.of("entry_protocol", "http", "entry_port", 80, "target_protocol", "http", "target_port", 80))))
            .build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("403"));
        assertThat(ex.getMessage(), containsString("Load Balancer can't be updated while it processes previous actions"));
        assertThat(ex.getMessage(), not(containsString("invalid or missing API token")));
    }

    @Test
    void failsWithClearMessageOnInvalidToken(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(put(urlPathEqualTo("/v2/load_balancers/lb-1")).willReturn(aResponse().withStatus(401)
            .withBody("{\"id\":\"unauthorized\",\"message\":\"Unable to authenticate you\"}")));

        var task = Update.builder()
            .id("update-401-test")
            .type(Update.class.getName())
            .apiToken(Property.ofValue("bad-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .loadBalancerId(Property.ofValue("lb-1"))
            .name(Property.ofValue("web-lb"))
            .region(Property.ofValue("nyc3"))
            .forwardingRules(Property.ofValue(java.util.List.of(Map.of("entry_protocol", "http", "entry_port", 80, "target_protocol", "http", "target_port", 80))))
            .build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("invalid or missing API token"));
    }
}
