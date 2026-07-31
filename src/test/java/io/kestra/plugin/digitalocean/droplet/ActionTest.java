package io.kestra.plugin.digitalocean.droplet;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTest;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActionTest extends AbstractDigitalOceanTest {

    @Test
    void powersOffDropletAndSendsBearerToken(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubPostJson("/v2/droplets/3164444/actions", 201, """
            {"action": {"id": 42, "status": "in-progress", "type": "power_off"}}
            """);

        var task = Action.builder()
            .id("action-power-off-test")
            .type(Action.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .dropletId(Property.ofValue("3164444"))
            .action(Property.ofValue(DropletAction.POWER_OFF))
            .build();

        var output = task.run(runContext());

        assertThat(output.getActionId(), is(42L));
        assertThat(output.getStatus(), is("in-progress"));
        assertThat(output.getType(), is("power_off"));
        verifyBearer(postRequestedFor(urlPathEqualTo("/v2/droplets/3164444/actions")), "test-token");
        verify(postRequestedFor(urlPathEqualTo("/v2/droplets/3164444/actions")).withRequestBody(containing("\"type\":\"power_off\"")));
    }

    @Test
    void takesANamedSnapshot(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubPostJson("/v2/droplets/3164444/actions", 201, """
            {"action": {"id": 43, "status": "in-progress", "type": "snapshot"}}
            """);

        var task = Action.builder()
            .id("action-snapshot-test")
            .type(Action.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .dropletId(Property.ofValue("3164444"))
            .action(Property.ofValue(DropletAction.SNAPSHOT))
            .name(Property.ofValue("pre-migration-snapshot"))
            .build();

        var output = task.run(runContext());

        assertThat(output.getActionId(), is(43L));
        assertThat(output.getType(), is("snapshot"));
        verify(postRequestedFor(urlPathEqualTo("/v2/droplets/3164444/actions"))
            .withRequestBody(containing("\"type\":\"snapshot\""))
            .withRequestBody(containing("\"name\":\"pre-migration-snapshot\"")));
    }

    @Test
    void failsWithClearMessageOnRateLimit(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubPostJson("/v2/droplets/3164444/actions", 429, "{\"message\":\"too many requests\"}");

        var task = Action.builder()
            .id("action-429-test")
            .type(Action.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .dropletId(Property.ofValue("3164444"))
            .action(Property.ofValue(DropletAction.REBOOT))
            .build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("rate limit"));
    }
}
