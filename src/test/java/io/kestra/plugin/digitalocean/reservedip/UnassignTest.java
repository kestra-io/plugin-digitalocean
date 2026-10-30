package io.kestra.plugin.digitalocean.reservedip;

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

class UnassignTest extends AbstractDigitalOceanTest {

    private Unassign task(WireMockRuntimeInfo wireMockRuntimeInfo, Property<String> reservedIp) {
        return Unassign.builder()
            .id(IdUtils.create())
            .type(Unassign.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .reservedIp(reservedIp)
            .build();
    }

    @Test
    void unassignsTheReservedIp(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubPostJson(AssignTest.PATH, 201, AssignTest.ACTION_JSON.replace("assign_ip", "unassign_ip"));

        var output = task(wireMockRuntimeInfo, Property.ofValue("45.55.96.47")).run(runContext());

        assertThat(output.getType(), is("unassign_ip"));
        verifyBearer(postRequestedFor(urlPathEqualTo(AssignTest.PATH)), "test-token");
        verify(postRequestedFor(urlPathEqualTo(AssignTest.PATH)).withRequestBody(equalToJson("""
            {"type": "unassign"}
            """)));
    }

    @Test
    void failsWithClearMessageWhenTheReservedIpDoesNotExist(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubPostJson("/v2/reserved_ips/10.0.0.1/actions", 404, "{\"id\": \"not_found\", \"message\": \"The resource you were accessing could not be found.\"}");

        var task = task(wireMockRuntimeInfo, Property.ofValue("10.0.0.1"));

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("HTTP 404"));
    }

    @Test
    void rendersTheReservedIpFromAnExpression(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubPostJson(AssignTest.PATH, 201, AssignTest.ACTION_JSON);

        task(wireMockRuntimeInfo, Property.ofExpression("{{ inputs.ip }}"))
            .run(runContextFactory.of(Map.of("inputs", Map.of("ip", "45.55.96.47"))));

        verify(postRequestedFor(urlPathEqualTo(AssignTest.PATH)));
    }
}
