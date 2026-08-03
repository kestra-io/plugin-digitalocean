package io.kestra.plugin.digitalocean.domain;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTest;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CreateTest extends AbstractDigitalOceanTest {

    private static final String DOMAIN_JSON = """
        {
          "domain": {"name": "example.com", "ttl": 1800, "zone_file": null}
        }
        """;

    @Test
    void createsDomainAndSendsBearerToken(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubPostJson("/v2/domains", 201, DOMAIN_JSON);

        var task = Create.builder()
            .id("create-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .name(Property.ofValue("example.com"))
            .ipAddress(Property.ofValue("104.131.186.241"))
            .build();

        var output = task.run(runContext());

        assertThat(output.getName(), is("example.com"));
        assertThat(output.getTtl(), is(1800));
        verifyBearer(postRequestedFor(urlPathEqualTo("/v2/domains")), "test-token");
    }

    @Test
    void failsWithClearMessageOnInvalidToken(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubPostJson("/v2/domains", 401, "{\"message\":\"Unable to authenticate you\"}");

        var task = Create.builder()
            .id("create-401-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("bad-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .name(Property.ofValue("example.com"))
            .build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("invalid or missing API token"));
    }
}
