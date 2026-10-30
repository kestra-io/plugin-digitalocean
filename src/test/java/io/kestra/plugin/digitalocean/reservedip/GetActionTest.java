package io.kestra.plugin.digitalocean.reservedip;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetActionTest extends AbstractDigitalOceanTest {

    private static final String PATH = AssignTest.PATH + "/72531856";

    private GetAction task(WireMockRuntimeInfo wireMockRuntimeInfo, Property<String> reservedIp, Property<String> actionId) {
        return GetAction.builder()
            .id(IdUtils.create())
            .type(GetAction.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .reservedIp(reservedIp)
            .actionId(actionId)
            .build();
    }

    @Test
    void readsTheActionStatus(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson(PATH, AssignTest.ACTION_JSON.replace("in-progress", "completed"));

        var output = task(wireMockRuntimeInfo, Property.ofValue("45.55.96.47"), Property.ofValue("72531856")).run(runContext());

        assertThat(output.getActionId(), is(72531856L));
        assertThat(output.getStatus(), is("completed"));
        verifyBearer(getRequestedFor(urlPathEqualTo(PATH)), "test-token");
    }

    @Test
    void failsWithClearMessageWhenTheActionDoesNotExist(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubStatus(AssignTest.PATH + "/1", 404, "{\"id\": \"not_found\", \"message\": \"The resource you were accessing could not be found.\"}");

        var task = task(wireMockRuntimeInfo, Property.ofValue("45.55.96.47"), Property.ofValue("1"));

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("HTTP 404"));
    }

    @Test
    void rendersTheReservedIpAndActionIdFromExpressions(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson(PATH, AssignTest.ACTION_JSON);

        task(wireMockRuntimeInfo, Property.ofExpression("{{ inputs.ip }}"), Property.ofExpression("{{ inputs.action }}"))
            .run(runContextFactory.of(Map.of("inputs", Map.of("ip", "45.55.96.47", "action", "72531856"))));

        verify(getRequestedFor(urlPathEqualTo(PATH)));
    }
}
