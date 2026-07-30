package io.kestra.plugin.digitalocean.firewall;

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

    private static final String FIREWALL_JSON = """
        {
          "firewall": {"id": "fw-2", "name": "api-firewall", "status": "waiting", "created_at": "2024-01-01T00:00:00Z"}
        }
        """;

    @Test
    void createsFirewallAndSendsBearerToken(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubPostJson("/v2/firewalls", 202, FIREWALL_JSON);

        var task = Create.builder()
            .id("create-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .name(Property.ofValue("api-firewall"))
            .inboundRules(Property.ofValue(java.util.List.of(Map.of("protocol", "tcp", "ports", "443", "sources", Map.of("addresses", java.util.List.of("0.0.0.0/0"))))))
            .outboundRules(Property.ofValue(java.util.List.of(Map.of("protocol", "tcp", "ports", "1-65535", "destinations", Map.of("addresses", java.util.List.of("0.0.0.0/0"))))))
            .build();

        var output = task.run(runContext());

        assertThat(output.getId(), is("fw-2"));
        assertThat(output.getStatus(), is("waiting"));
        verifyBearer(postRequestedFor(urlPathEqualTo("/v2/firewalls")), "test-token");
    }

    @Test
    void failsWithClearMessageOnInvalidToken(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubPostJson("/v2/firewalls", 401, "{\"message\":\"Unable to authenticate you\"}");

        var task = Create.builder()
            .id("create-401-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("bad-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .name(Property.ofValue("api-firewall"))
            .inboundRules(Property.ofValue(java.util.List.of(Map.of("protocol", "tcp", "ports", "443", "sources", Map.of("addresses", java.util.List.of("0.0.0.0/0"))))))
            .outboundRules(Property.ofValue(java.util.List.of()))
            .build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("invalid or missing API token"));
    }
}
