package io.kestra.plugin.digitalocean.reservedip;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssignTest extends AbstractDigitalOceanTest {

    static final String PATH = "/v2/reserved_ips/45.55.96.47/actions";

    static final String ACTION_JSON = """
        {"action": {"id": 72531856, "status": "in-progress", "type": "assign_ip", "started_at": "2026-10-13T12:00:00Z",
          "resource_id": 758604968, "resource_type": "reserved_ip", "region_slug": "nyc3"}}
        """;

    private Assign task(WireMockRuntimeInfo wireMockRuntimeInfo, Property<String> reservedIp, Property<String> dropletId) {
        return Assign.builder()
            .id(IdUtils.create())
            .type(Assign.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .reservedIp(reservedIp)
            .dropletId(dropletId)
            .build();
    }

    @Test
    void assignsTheReservedIpToTheDroplet(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubPostJson(PATH, 201, ACTION_JSON);

        var output = task(wireMockRuntimeInfo, Property.ofValue("45.55.96.47"), Property.ofValue("3164494")).run(runContext());

        assertThat(output.getActionId(), is(72531856L));
        assertThat(output.getStatus(), is("in-progress"));
        assertThat(output.getType(), is("assign_ip"));
        verifyBearer(postRequestedFor(urlPathEqualTo(PATH)), "test-token");
        verify(postRequestedFor(urlPathEqualTo(PATH)).withRequestBody(equalToJson("""
            {"type": "assign", "droplet_id": 3164494}
            """)));
    }

    @Test
    void rejectsANonNumericDropletIdBeforeCallingTheApi(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = task(wireMockRuntimeInfo, Property.ofValue("45.55.96.47"), Property.ofValue("web-01"));

        var runContext = runContext();
        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("dropletId must be a numeric DigitalOcean droplet id, got 'web-01'"));
        verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void surfacesTheApiReasonWhenTheAssignmentIsRefused(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubPostJson(PATH, 422, "{\"id\": \"unprocessable_entity\", \"message\": \"Droplet is in a different region\"}");

        var task = task(wireMockRuntimeInfo, Property.ofValue("45.55.96.47"), Property.ofValue("3164494"));

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("HTTP 422"));
        assertThat(ex.getMessage(), containsString("Droplet is in a different region"));
    }

    @Test
    void rendersTheReservedIpAndDropletIdFromExpressions(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubPostJson(PATH, 201, ACTION_JSON);

        task(wireMockRuntimeInfo, Property.ofExpression("{{ inputs.ip }}"), Property.ofExpression("{{ inputs.standby }}"))
            .run(runContextFactory.of(Map.of("inputs", Map.of("ip", "45.55.96.47", "standby", "3164494"))));

        verify(postRequestedFor(urlPathEqualTo(PATH)).withRequestBody(equalToJson("""
            {"type": "assign", "droplet_id": 3164494}
            """)));
    }
}
