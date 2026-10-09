package io.kestra.plugin.digitalocean.monitoring.alertpolicy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.models.property.Property;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTest;
import io.kestra.plugin.digitalocean.monitoring.models.AlertChannels;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class UpdateTest extends AbstractDigitalOceanTest {

    @Test
    void updatesAlertPolicyAndSendsPayload(WireMockRuntimeInfo wm) throws Exception {
        stubFor(put(urlPathEqualTo("/v2/monitoring/alerts/7343e061-e0c1-4b71-a476-c56dfaa3d2ad"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "policy": {
                        "uuid": "7343e061-e0c1-4b71-a476-c56dfaa3d2ad",
                        "type": "v1/insights/droplet/cpu",
                        "description": "Updated alert",
                        "compare": "greater_than",
                        "value": 90.0,
                        "window": "10m",
                        "alerts": {
                          "email": ["ops@company.com"]
                        },
                        "enabled": true
                      }
                    }
                    """)));

        var task = Update.builder()
            .id(IdUtils.create())
            .type(Update.class.getName())
            .apiToken(Property.ofValue("secret-token"))
            .baseUrl(Property.ofValue(wm.getHttpBaseUrl()))
            .alertUuid(Property.ofValue("7343e061-e0c1-4b71-a476-c56dfaa3d2ad"))
            .policyType(Property.ofValue("v1/insights/droplet/cpu"))
            .policyDescription(Property.ofValue("Updated alert"))
            .compare(Property.ofValue("greater_than"))
            .value(Property.ofValue(90.0))
            .window(Property.ofValue("10m"))
            .alerts(Property.ofValue(AlertChannels.builder()
                .email(List.of("ops@company.com"))
                .build()))
            .build();

        var output = task.run(runContext());

        assertThat(output.getUuid(), is("7343e061-e0c1-4b71-a476-c56dfaa3d2ad"));
        assertThat(output.getDescription(), is("Updated alert"));
        assertThat(output.getValue(), is(90.0));
        verifyBearer(putRequestedFor(urlPathEqualTo("/v2/monitoring/alerts/7343e061-e0c1-4b71-a476-c56dfaa3d2ad")), "secret-token");
    }
}
