package io.kestra.plugin.digitalocean.volume;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTest;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetTest extends AbstractDigitalOceanTest {

    private static final String VOLUME_JSON = """
        {
          "volume": {"id": "vol-1", "name": "data-volume", "region": {"slug": "nyc3"}, "size_gigabytes": 100, "filesystem_type": "ext4"}
        }
        """;

    @Test
    void fetchesVolumeAndSendsBearerToken(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/v2/volumes/vol-1", VOLUME_JSON);

        var task = Get.builder()
            .id("get-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .volumeId(Property.ofValue("vol-1"))
            .build();

        var output = task.run(runContext());

        assertThat(output.getSizeGigabytes(), is(100L));
        assertThat(output.getFilesystemType(), is("ext4"));
        verifyBearer(getRequestedFor(urlPathEqualTo("/v2/volumes/vol-1")), "test-token");
    }

    @Test
    void sendsBearerTokenWhenOptionsAreSet(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/v2/volumes/vol-1", VOLUME_JSON);

        var task = Get.builder()
            .id(IdUtils.create())
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .volumeId(Property.ofValue("vol-1"))
            .options(readIdleTimeout(Duration.ofSeconds(5)))
            .build();

        var output = task.run(runContext());

        assertThat(output.getSizeGigabytes(), is(100L));
        var sent = findAll(getRequestedFor(urlPathEqualTo("/v2/volumes/vol-1")));
        assertThat(sent, hasSize(1));
        assertOnlyBearer(sent.getFirst(), "test-token");
    }

    @Test
    void appliesReadIdleTimeoutFromOptions(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/v2/volumes/vol-1"))
            .willReturn(okJson(VOLUME_JSON).withFixedDelay(3_000)));

        var task = Get.builder()
            .id(IdUtils.create())
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .volumeId(Property.ofValue("vol-1"))
            .options(readIdleTimeout(Duration.ofMillis(500)))
            .build();

        var runContext = runContext();
        var ex = assertThrows(RuntimeException.class, () -> task.run(runContext));
        assertThat(hasCause(ex, SocketTimeoutException.class), is(true));

        var sent = findAll(getRequestedFor(urlPathEqualTo("/v2/volumes/vol-1")));
        assertThat(sent, hasSize(1));
        assertOnlyBearer(sent.getFirst(), "test-token");
    }

    @Test
    void failsWithClearMessageWhenNotFound(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubStatus("/v2/volumes/missing", 404, "{\"message\":\"not found\"}");

        var task = Get.builder()
            .id("get-404-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .volumeId(Property.ofValue("missing"))
            .build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("resource not found"));
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        var current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
