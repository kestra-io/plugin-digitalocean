package io.kestra.plugin.digitalocean.droplet;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CreateTest extends AbstractDigitalOceanTest {

    private static final String DROPLET_JSON = """
        {
          "droplet": {"id": 3164445, "name": "web-02", "status": "new", "size_slug": "s-1vcpu-1gb",
             "region": {"slug": "nyc3"}, "networks": {"v4": []}, "created_at": "2024-01-01T00:00:00Z"}
        }
        """;

    @Test
    void createsDropletAndSendsBearerToken(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubPostJson("/v2/droplets", 202, DROPLET_JSON);

        var task = Create.builder()
            .id("create-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .name(Property.ofValue("web-02"))
            .region(Property.ofValue("nyc3"))
            .size(Property.ofValue("s-1vcpu-1gb"))
            .image(Property.ofValue("ubuntu-22-04-x64"))
            .tags(Property.ofValue(List.of("web")))
            .build();

        var output = task.run(runContext());

        assertThat(output.getId(), is(3164445L));
        assertThat(output.getName(), is("web-02"));
        assertThat(output.getStatus(), is("new"));
        verifyBearer(postRequestedFor(urlPathEqualTo("/v2/droplets")), "test-token");
    }

    @Test
    void failsWithClearMessageOnInvalidToken(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubPostJson("/v2/droplets", 401, "{\"message\":\"Unable to authenticate you\"}");

        var task = Create.builder()
            .id("create-401-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("bad-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .name(Property.ofValue("web-02"))
            .region(Property.ofValue("nyc3"))
            .size(Property.ofValue("s-1vcpu-1gb"))
            .image(Property.ofValue("ubuntu-22-04-x64"))
            .build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("invalid or missing API token"));
    }
}
