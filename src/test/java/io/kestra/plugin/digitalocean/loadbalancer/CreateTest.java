package io.kestra.plugin.digitalocean.loadbalancer;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CreateTest extends AbstractDigitalOceanTest {

    private static final String LOAD_BALANCER_JSON = """
        {
          "load_balancer": {"id": "lb-2", "name": "api-lb", "ip": "192.0.2.2", "status": "new", "region": {"slug": "nyc3"}}
        }
        """;

    @Test
    void createsLoadBalancerAndSendsBearerToken(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubPostJson("/v2/load_balancers", 202, LOAD_BALANCER_JSON);

        var task = Create.builder()
            .id("create-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .name(Property.ofValue("api-lb"))
            .region(Property.ofValue("nyc3"))
            .forwardingRules(Property.ofValue(java.util.List.of(Map.of("entry_protocol", "http", "entry_port", 80, "target_protocol", "http", "target_port", 80))))
            .build();

        var output = task.run(runContext());

        assertThat(output.getId(), is("lb-2"));
        assertThat(output.getStatus(), is("new"));
        verifyBearer(postRequestedFor(urlPathEqualTo("/v2/load_balancers")), "test-token");
    }

    @Test
    void requiresAtLeastOneForwardingRule(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = Create.builder()
            .id("create-no-rules-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .name(Property.ofValue("api-lb"))
            .region(Property.ofValue("nyc3"))
            .forwardingRules(Property.ofValue(java.util.List.of()))
            .build();

        var runContext = runContext();
        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("forwardingRules"));
    }

    @Test
    void failsWithClearMessageOnInvalidToken(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubPostJson("/v2/load_balancers", 401, "{\"message\":\"Unable to authenticate you\"}");

        var task = Create.builder()
            .id("create-401-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("bad-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .name(Property.ofValue("api-lb"))
            .region(Property.ofValue("nyc3"))
            .forwardingRules(Property.ofValue(java.util.List.of(Map.of("entry_protocol", "http", "entry_port", 80, "target_protocol", "http", "target_port", 80))))
            .build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("invalid or missing API token"));
    }
}
