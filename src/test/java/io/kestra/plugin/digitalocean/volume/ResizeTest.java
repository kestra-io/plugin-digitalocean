package io.kestra.plugin.digitalocean.volume;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResizeTest extends AbstractDigitalOceanTest {

    private static final String ACTION_JSON = """
        {"action": {"id": 72531856, "status": "in-progress", "type": "resize_volume", "region_slug": "nyc3"}}
        """;

    private Resize.ResizeBuilder<?, ?> task(WireMockRuntimeInfo wireMockRuntimeInfo, int sizeGigabytes) {
        return Resize.builder()
            .id(IdUtils.create())
            .type(Resize.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .volumeId(Property.ofValue("vol-1"))
            .sizeGigabytes(Property.ofValue(sizeGigabytes))
            .region(Property.ofValue("nyc3"));
    }

    @Test
    void resizesVolumeAndSendsBearerToken(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubPostJson("/v2/volumes/vol-1/actions", 202, ACTION_JSON);

        var output = task(wireMockRuntimeInfo, 200).build().run(runContext());

        assertThat(output.getActionId(), is(72531856L));
        assertThat(output.getStatus(), is("in-progress"));
        verifyBearer(postRequestedFor(urlPathEqualTo("/v2/volumes/vol-1/actions")), "test-token");
        verify(postRequestedFor(urlPathEqualTo("/v2/volumes/vol-1/actions"))
            .withRequestBody(equalToJson("{\"type\": \"resize\", \"size_gigabytes\": 200, \"region\": \"nyc3\"}")));
    }

    @Test
    void rejectsSizeOutsideTheApiRangeBeforeCallingIt(WireMockRuntimeInfo wireMockRuntimeInfo) {
        for (var sizeGigabytes : new int[]{0, 16385}) {
            var task = task(wireMockRuntimeInfo, sizeGigabytes).build();

            var runContext = runContext();
            var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
            assertThat(ex.getMessage(), containsString("sizeGigabytes must be between 1 and 16384, got " + sizeGigabytes));
        }
        verify(0, postRequestedFor(urlPathEqualTo("/v2/volumes/vol-1/actions")));
    }

    @Test
    void surfacesTheApiReasonWhenShrinking(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubPostJson("/v2/volumes/vol-1/actions", 422,
            "{\"id\": \"unprocessable_entity\", \"message\": \"new size must be larger than the current size\"}");

        var task = task(wireMockRuntimeInfo, 50).build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("HTTP 422"));
        assertThat(ex.getMessage(), containsString("new size must be larger than the current size"));
    }

    @Test
    void failsWithClearMessageOnRateLimit(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubPostJson("/v2/volumes/vol-1/actions", 429, "{\"message\":\"too many requests\"}");

        var task = task(wireMockRuntimeInfo, 200).build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("rate limit"));
    }

    @Test
    void rendersTheSizeFromAnExpression(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubPostJson("/v2/volumes/vol-1/actions", 202, ACTION_JSON);

        var task = Resize.builder()
            .id(IdUtils.create())
            .type(Resize.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .volumeId(Property.ofExpression("{{ inputs.volumeId }}"))
            .sizeGigabytes(Property.ofExpression("{{ inputs.size * 2 }}"))
            .region(Property.ofExpression("{{ inputs.region }}"))
            .build();

        task.run(runContextFactory.of(Map.of("inputs", Map.of("volumeId", "vol-1", "size", 150, "region", "nyc3"))));

        verify(postRequestedFor(urlPathEqualTo("/v2/volumes/vol-1/actions"))
            .withRequestBody(equalToJson("{\"type\": \"resize\", \"size_gigabytes\": 300, \"region\": \"nyc3\"}")));
    }
}
