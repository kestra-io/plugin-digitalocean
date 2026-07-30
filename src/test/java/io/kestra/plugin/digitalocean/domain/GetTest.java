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

    private static final String RECORD_JSON = """
        {
          "domain_record": {"id": 12345, "type": "A", "name": "www", "data": "104.131.186.241", "ttl": 3600}
        }
        """;

    @Test
    void fetchesRecordAndSendsBearerToken(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/v2/domains/example.com/records/12345", RECORD_JSON);

        var task = Get.builder()
            .id("get-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .domain(Property.ofValue("example.com"))
            .recordId(Property.ofValue("12345"))
            .build();

        var output = task.run(runContext());

        assertThat(output.getId(), is(12345L));
        assertThat(output.getData(), is("104.131.186.241"));
        verifyBearer(getRequestedFor(urlPathEqualTo("/v2/domains/example.com/records/12345")), "test-token");
    }

    @Test
    void encodesSpecialCharactersInPathSegments(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        // "abc/def" is a classic path-injection attempt: unencoded, it would insert an extra path
        // segment ("/v2/domains/my domain.com/records/abc/def") instead of being treated as one id.
        stubGetJson("/v2/domains/my%20domain.com/records/abc%2Fdef", RECORD_JSON);

        var task = Get.builder()
            .id("get-encoding-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .domain(Property.ofValue("my domain.com"))
            .recordId(Property.ofValue("abc/def"))
            .build();

        var output = task.run(runContext());

        assertThat(output.getId(), is(12345L));
        verifyBearer(getRequestedFor(urlPathEqualTo("/v2/domains/my%20domain.com/records/abc%2Fdef")), "test-token");
    }

    @Test
    void failsWithClearMessageWhenNotFound(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubStatus("/v2/domains/example.com/records/999", 404, "{\"message\":\"not found\"}");

        var task = Get.builder()
            .id("get-404-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .domain(Property.ofValue("example.com"))
            .recordId(Property.ofValue("999"))
            .build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("resource not found"));
    }
}
