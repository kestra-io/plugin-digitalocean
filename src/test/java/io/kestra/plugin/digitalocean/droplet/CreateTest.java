package io.kestra.plugin.digitalocean.droplet;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateTest extends AbstractDigitalOceanTest {

    private static final String DROPLET_JSON = """
        {
          "droplet": {"id": 3164445, "name": "web-02", "status": "new", "size_slug": "s-1vcpu-1gb",
             "region": {"slug": "nyc3"}, "networks": {"v4": []}, "created_at": "2024-01-01T00:00:00Z"}
        }
        """;

    private static final String ACTIVE_DROPLET_JSON = """
        {
          "droplet": {"id": 3164445, "name": "web-02", "status": "active", "size_slug": "s-1vcpu-1gb",
             "region": {"slug": "nyc3"},
             "networks": {"v4": [{"ip_address": "203.0.113.10", "type": "public"}]},
             "created_at": "2024-01-01T00:00:00Z"}
        }
        """;

    private Create.CreateBuilder<?, ?> baseTask(WireMockRuntimeInfo wireMockRuntimeInfo) {
        return Create.builder()
            .id("create-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .name(Property.ofValue("web-02"))
            .region(Property.ofValue("nyc3"))
            .size(Property.ofValue("s-1vcpu-1gb"))
            .image(Property.ofValue("ubuntu-22-04-x64"))
            .tags(Property.ofValue(List.of("web")));
    }

    @Test
    void createsDropletAndSendsBearerToken(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubPostJson("/v2/droplets", 202, DROPLET_JSON);
        stubFor(get(urlPathEqualTo("/v2/droplets/3164445"))
            .inScenario("droplet-activation")
            .whenScenarioStateIs(Scenario.STARTED)
            .willSetStateTo("active")
            .willReturn(okJson(DROPLET_JSON)));
        stubFor(get(urlPathEqualTo("/v2/droplets/3164445"))
            .inScenario("droplet-activation")
            .whenScenarioStateIs("active")
            .willReturn(okJson(ACTIVE_DROPLET_JSON)));

        var task = baseTask(wireMockRuntimeInfo).build();

        var output = task.run(runContext());

        assertThat(output.getId(), is(3164445L));
        assertThat(output.getName(), is("web-02"));
        assertThat(output.getStatus(), is("active"));
        assertThat(output.getIp(), is("203.0.113.10"));
        verifyBearer(postRequestedFor(urlPathEqualTo("/v2/droplets")), "test-token");
        verify(2, getRequestedFor(urlPathEqualTo("/v2/droplets/3164445")));
    }

    @Test
    void skipsWaitWhenDisabled(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubPostJson("/v2/droplets", 202, DROPLET_JSON);

        var task = baseTask(wireMockRuntimeInfo)
            .wait(Property.ofValue(false))
            .build();

        var output = task.run(runContext());

        assertThat(output.getStatus(), is("new"));
        assertThat(output.getIp(), nullValue());
        verify(0, getRequestedFor(urlPathEqualTo("/v2/droplets/3164445")));
    }

    @Test
    void failsWithActionableMessageOnTimeout(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubPostJson("/v2/droplets", 202, DROPLET_JSON);
        stubFor(get(urlPathEqualTo("/v2/droplets/3164445")).willReturn(okJson(DROPLET_JSON)));

        var task = baseTask(wireMockRuntimeInfo)
            .waitTimeout(Property.ofValue(Duration.ofSeconds(1)))
            .build();

        var runContext = runContext();
        var start = System.nanoTime();
        var ex = assertThrows(IllegalStateException.class, () -> task.run(runContext));
        var elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(ex.getMessage(), containsString("did not become active within"));
        assertThat(ex.getMessage(), containsString("Increase waitTimeout, or set wait: false"));
        // The poll loop must not sleep past the 1s deadline before re-checking it: with the fixed 5s
        // poll interval capped to the remaining time, this fails close to 1s instead of always rounding
        // up to the next 5s boundary.
        assertTrue(elapsed.toMillis() < 3000, "expected timeout close to the configured 1s, took " + elapsed);
    }

    @Test
    void rejectsOutOfRangeWaitTimeout(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = baseTask(wireMockRuntimeInfo)
            .waitTimeout(Property.ofValue(Duration.ofHours(2)))
            .build();

        var runContext = runContext();
        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("waitTimeout must be between 1 and 3600"));
    }

    @Test
    void skipsPollingWhenAlreadyActive(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubPostJson("/v2/droplets", 202, ACTIVE_DROPLET_JSON);

        var task = baseTask(wireMockRuntimeInfo).build();

        var output = task.run(runContext());

        assertThat(output.getStatus(), is("active"));
        assertThat(output.getIp(), is("203.0.113.10"));
        verify(0, getRequestedFor(urlPathEqualTo("/v2/droplets/3164445")));
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
