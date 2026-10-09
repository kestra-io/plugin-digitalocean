package io.kestra.plugin.digitalocean.monitoring.alertpolicy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetTest extends AbstractDigitalOceanTest {

    private static final String ALERT_JSON = """
        {
          "policy": {
            "uuid": "7343e061-e0c1-4b71-a476-c56dfaa3d2ad",
            "type": "v1/insights/droplet/cpu",
            "description": "High CPU alert",
            "compare": "greater_than",
            "value": 85.0,
            "window": "5m",
            "entities": ["123456"],
            "tags": ["prod"],
            "alerts": {
              "email": ["ops@company.com"],
              "slack": [
                {
                  "url": "https://hooks.slack.com/services/xxx",
                  "channel": "#alerts"
                }
              ]
            },
            "enabled": true
          }
        }
        """;

    @Test
    void fetchesAlertPolicyAndMapsFields(WireMockRuntimeInfo wm) throws Exception {
        stubGetJson("/v2/monitoring/alerts/7343e061-e0c1-4b71-a476-c56dfaa3d2ad", ALERT_JSON);

        var task = Get.builder()
            .id(IdUtils.create())
            .type(Get.class.getName())
            .apiToken(Property.ofValue("secret-token"))
            .baseUrl(Property.ofValue(wm.getHttpBaseUrl()))
            .alertUuid(Property.ofValue("7343e061-e0c1-4b71-a476-c56dfaa3d2ad"))
            .build();

        var output = task.run(runContext());

        assertThat(output.getUuid(), is("7343e061-e0c1-4b71-a476-c56dfaa3d2ad"));
        assertThat(output.getType(), is("v1/insights/droplet/cpu"));
        assertThat(output.getDescription(), is("High CPU alert"));
        assertThat(output.getCompare(), is("greater_than"));
        assertThat(output.getValue(), is(85.0));
        assertThat(output.getWindow(), is("5m"));
        assertThat(output.getEntities(), hasItem("123456"));
        assertThat(output.getTags(), hasItem("prod"));
        assertThat(output.getAlerts().getEmail(), hasItem("ops@company.com"));
        assertThat(output.getAlerts().getSlack().getFirst().getChannel(), is("#alerts"));
        assertThat(output.getEnabled(), is(true));
        verifyBearer(getRequestedFor(urlPathEqualTo("/v2/monitoring/alerts/7343e061-e0c1-4b71-a476-c56dfaa3d2ad")), "secret-token");
    }

    @Test
    void failsWithClearMessageWhenNotFound(WireMockRuntimeInfo wm) {
        stubStatus("/v2/monitoring/alerts/missing", 404, "{\"message\":\"not found\"}");

        var task = Get.builder()
            .id(IdUtils.create())
            .type(Get.class.getName())
            .apiToken(Property.ofValue("secret-token"))
            .baseUrl(Property.ofValue(wm.getHttpBaseUrl()))
            .alertUuid(Property.ofValue("missing"))
            .build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("resource not found"));
    }

    @Test
    void rendersPropertiesFromExpressions(WireMockRuntimeInfo wm) throws Exception {
        stubGetJson("/v2/monitoring/alerts/7343e061-e0c1-4b71-a476-c56dfaa3d2ad", ALERT_JSON);

        var task = Get.builder()
            .id(IdUtils.create())
            .type(Get.class.getName())
            .apiToken(Property.ofExpression("{{ inputs.token }}"))
            .baseUrl(Property.ofValue(wm.getHttpBaseUrl()))
            .alertUuid(Property.ofExpression("{{ inputs.uuid }}"))
            .build();

        var output = task.run(runContextFactory.of(Map.of("inputs", Map.of("token", "rendered-token", "uuid", "7343e061-e0c1-4b71-a476-c56dfaa3d2ad"))));

        assertThat(output.getUuid(), is("7343e061-e0c1-4b71-a476-c56dfaa3d2ad"));
        verifyBearer(getRequestedFor(urlPathEqualTo("/v2/monitoring/alerts/7343e061-e0c1-4b71-a476-c56dfaa3d2ad")), "rendered-token");
    }
}
