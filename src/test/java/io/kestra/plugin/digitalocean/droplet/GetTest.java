package io.kestra.plugin.digitalocean.droplet;

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

    private static final String DROPLET_JSON = """
        {
          "droplet": {"id": 3164444, "name": "web-01", "status": "active", "size_slug": "s-1vcpu-1gb",
             "region": {"slug": "nyc3"},
             "networks": {"v4": [{"ip_address": "104.131.186.241", "type": "public"}]},
             "created_at": "2020-07-21T18:37:44Z"}
        }
        """;

    @Test
    void fetchesDropletAndSendsBearerToken(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/v2/droplets/3164444", DROPLET_JSON);

        var task = Get.builder()
            .id("get-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .dropletId(Property.ofValue("3164444"))
            .build();

        var output = task.run(runContext());

        assertThat(output.getId(), is(3164444L));
        assertThat(output.getName(), is("web-01"));
        assertThat(output.getStatus(), is("active"));
        assertThat(output.getRegion(), is("nyc3"));
        assertThat(output.getIp(), is("104.131.186.241"));
        verifyBearer(getRequestedFor(urlPathEqualTo("/v2/droplets/3164444")), "test-token");
    }

    @Test
    void failsWithClearMessageWhenNotFound(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubStatus("/v2/droplets/999", 404, "{\"message\":\"not found\"}");

        var task = Get.builder()
            .id("get-404-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .dropletId(Property.ofValue("999"))
            .build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("resource not found"));
    }
}
