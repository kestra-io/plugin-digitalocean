package io.kestra.plugin.digitalocean.domain.record;

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

class CreateTest extends AbstractDigitalOceanTest {

    private static final String RECORD_JSON = """
        {
          "domain_record": {"id": 12346, "type": "A", "name": "api", "data": "104.131.186.242", "ttl": 1800}
        }
        """;

    @Test
    void createsRecordAndSendsBearerToken(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubPostJson("/v2/domains/example.com/records", 201, RECORD_JSON);

        var task = Create.builder()
            .id("create-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .domain(Property.ofValue("example.com"))
            .recordType(Property.ofValue(RecordType.A))
            .name(Property.ofValue("api"))
            .data(Property.ofValue("104.131.186.242"))
            .build();

        var output = task.run(runContext());

        assertThat(output.getId(), is(12346L));
        assertThat(output.getName(), is("api"));
        verifyBearer(postRequestedFor(urlPathEqualTo("/v2/domains/example.com/records")), "test-token");
    }

    @Test
    void mapsAPlainStringRecordTypeLikeInAFlowToTheEnum(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        // new Property<>(String) mirrors how a plain YAML scalar (recordType: A) is stored before
        // rendering, proving the string-to-enum mapping Kestra does for a flow written without any
        // Java builder (Property.ofValue(RecordType.A), used in the other tests, skips that mapping).
        stubPostJson("/v2/domains/example.com/records", 201, RECORD_JSON);

        var task = Create.builder()
            .id("create-string-type-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .domain(Property.ofValue("example.com"))
            .recordType(new Property<>("A"))
            .name(Property.ofValue("api"))
            .data(Property.ofValue("104.131.186.242"))
            .build();

        task.run(runContext());

        verify(postRequestedFor(urlPathEqualTo("/v2/domains/example.com/records"))
            .withRequestBody(containing("\"type\":\"A\"")));
    }

    @Test
    void failsWithClearMessageOnInvalidToken(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubPostJson("/v2/domains/example.com/records", 401, "{\"message\":\"Unable to authenticate you\"}");

        var task = Create.builder()
            .id("create-401-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("bad-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .domain(Property.ofValue("example.com"))
            .recordType(Property.ofValue(RecordType.A))
            .name(Property.ofValue("api"))
            .data(Property.ofValue("104.131.186.242"))
            .build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("invalid or missing API token"));
    }
}
