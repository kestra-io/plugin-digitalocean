package io.kestra.plugin.digitalocean.volume;

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

class DetachTest extends AbstractDigitalOceanTest {

    private static final String ACTION_JSON = """
        {"action": {"id": 101, "status": "in-progress", "type": "detach_volume"}}
        """;

    @Test
    void detachesVolumeAndSendsBearerToken(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubPostJson("/v2/volumes/vol-1/actions", 202, ACTION_JSON);

        var task = Detach.builder()
            .id("detach-test")
            .type(Detach.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .volumeId(Property.ofValue("vol-1"))
            .dropletId(Property.ofValue("3164444"))
            .region(Property.ofValue("nyc3"))
            .build();

        var output = task.run(runContext());

        assertThat(output.getActionId(), is(101L));
        assertThat(output.getType(), is("detach_volume"));
        verifyBearer(postRequestedFor(urlPathEqualTo("/v2/volumes/vol-1/actions")), "test-token");
    }

    @Test
    void failsWithClearMessageWhenNotFound(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubPostJson("/v2/volumes/missing/actions", 404, "{\"message\":\"not found\"}");

        var task = Detach.builder()
            .id("detach-404-test")
            .type(Detach.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .volumeId(Property.ofValue("missing"))
            .dropletId(Property.ofValue("3164444"))
            .build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("resource not found"));
    }

    @Test
    void failsWithClearMessageOnNonNumericDropletId(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = Detach.builder()
            .id("detach-bad-droplet-id-test")
            .type(Detach.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .volumeId(Property.ofValue("vol-1"))
            .dropletId(Property.ofValue("not-a-number"))
            .build();

        var runContext = runContext();
        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("dropletId must be a numeric DigitalOcean droplet id"));
        assertThat(ex.getMessage(), containsString("not-a-number"));
    }
}
