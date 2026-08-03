package io.kestra.plugin.digitalocean.volume;

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
}
