package io.kestra.plugin.digitalocean.droplet;

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

class ResizeTest extends AbstractDigitalOceanTest {

    private static final String ACTION_JSON = """
        {"action": {"id": 42, "status": "in-progress", "type": "resize"}}
        """;

    @Test
    void resizesDropletAndSendsBearerToken(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubPostJson("/v2/droplets/3164444/actions", 201, ACTION_JSON);

        var task = Resize.builder()
            .id("resize-test")
            .type(Resize.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .dropletId(Property.ofValue("3164444"))
            .action(Property.ofValue(DropletAction.RESIZE))
            .size(Property.ofValue("s-2vcpu-2gb"))
            .build();

        var output = task.run(runContext());

        assertThat(output.getActionId(), is(42L));
        assertThat(output.getStatus(), is("in-progress"));
        assertThat(output.getType(), is("resize"));
        verifyBearer(postRequestedFor(urlPathEqualTo("/v2/droplets/3164444/actions")), "test-token");
    }

    @Test
    void requiresSizeWhenResizing(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = Resize.builder()
            .id("resize-no-size-test")
            .type(Resize.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .dropletId(Property.ofValue("3164444"))
            .action(Property.ofValue(DropletAction.RESIZE))
            .build();

        var runContext = runContext();
        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("size is required"));
    }

    @Test
    void failsWithClearMessageOnRateLimit(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubPostJson("/v2/droplets/3164444/actions", 429, "{\"message\":\"too many requests\"}");

        var task = Resize.builder()
            .id("resize-429-test")
            .type(Resize.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .dropletId(Property.ofValue("3164444"))
            .action(Property.ofValue(DropletAction.POWER_OFF))
            .build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("rate limit"));
    }
}
