package io.kestra.plugin.digitalocean.monitoring.alertpolicy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.models.property.Property;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTest;
import io.kestra.plugin.digitalocean.monitoring.models.AlertChannels;
import io.kestra.plugin.digitalocean.monitoring.models.SlackAlert;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class CreateTest extends AbstractDigitalOceanTest {

    @Test
    void createsAlertPolicyAndSendsPayload(WireMockRuntimeInfo wm) throws Exception {
        stubPostJson("/v2/monitoring/alerts", 201, """
            {
              "policy": {
                "uuid": "new-policy-uuid",
                "type": "v1/insights/droplet/cpu",
                "description": "High CPU",
                "compare": "greater_than",
                "value": 85.0,
                "window": "5m",
                "alerts": {
                  "email": ["ops@company.com"],
                  "slack": [{"url": "https://slack.com/hook", "channel": "#alerts"}]
                },
                "enabled": true
              }
            }
            """);

        var task = Create.builder()
            .id(IdUtils.create())
            .type(Create.class.getName())
            .apiToken(Property.ofValue("secret-token"))
            .baseUrl(Property.ofValue(wm.getHttpBaseUrl()))
            .policyType(Property.ofValue("v1/insights/droplet/cpu"))
            .policyDescription(Property.ofValue("High CPU"))
            .compare(Property.ofValue("greater_than"))
            .value(Property.ofValue(85.0))
            .window(Property.ofValue("5m"))
            .alerts(Property.ofValue(AlertChannels.builder()
                .email(List.of("ops@company.com"))
                .slack(List.of(SlackAlert.builder().url("https://slack.com/hook").channel("#alerts").build()))
                .build()))
            .build();

        var output = task.run(runContext());

        assertThat(output.getUuid(), is("new-policy-uuid"));
        assertThat(output.getDescription(), is("High CPU"));
        assertThat(output.getValue(), is(85.0));
        verifyBearer(postRequestedFor(urlPathEqualTo("/v2/monitoring/alerts")), "secret-token");
    }
}
