package io.kestra.plugin.digitalocean.domain;

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

    private static final String DOMAIN_JSON = """
        {
          "domain": {"name": "example.com", "ttl": 1800, "zone_file": "$ORIGIN example.com.\\n"}
        }
        """;

    @Test
    void fetchesDomainAndSendsBearerToken(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/v2/domains/example.com", DOMAIN_JSON);

        var task = Get.builder()
            .id("get-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .name(Property.ofValue("example.com"))
            .build();

        var output = task.run(runContext());

        assertThat(output.getName(), is("example.com"));
        assertThat(output.getTtl(), is(1800));
        verifyBearer(getRequestedFor(urlPathEqualTo("/v2/domains/example.com")), "test-token");
    }

    @Test
    void failsWithClearMessageWhenNotFound(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubStatus("/v2/domains/missing.com", 404, "{\"message\":\"not found\"}");

        var task = Get.builder()
            .id("get-404-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .name(Property.ofValue("missing.com"))
            .build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("resource not found"));
    }
}
