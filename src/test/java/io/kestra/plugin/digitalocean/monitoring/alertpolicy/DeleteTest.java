package io.kestra.plugin.digitalocean.monitoring.alertpolicy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.models.property.Property;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTest;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DeleteTest extends AbstractDigitalOceanTest {

    @Test
    void deletesAlertPolicyAndSendsBearerToken(WireMockRuntimeInfo wm) {
        stubFor(delete(urlPathEqualTo("/v2/monitoring/alerts/7343e061-e0c1-4b71-a476-c56dfaa3d2ad"))
            .willReturn(aResponse().withStatus(204)));

        var task = Delete.builder()
            .id(IdUtils.create())
            .type(Delete.class.getName())
            .apiToken(Property.ofValue("secret-token"))
            .baseUrl(Property.ofValue(wm.getHttpBaseUrl()))
            .alertUuid(Property.ofValue("7343e061-e0c1-4b71-a476-c56dfaa3d2ad"))
            .build();

        assertDoesNotThrow(() -> task.run(runContext()));
        verifyBearer(deleteRequestedFor(urlPathEqualTo("/v2/monitoring/alerts/7343e061-e0c1-4b71-a476-c56dfaa3d2ad")), "secret-token");
    }
}
