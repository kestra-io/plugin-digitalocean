package io.kestra.plugin.digitalocean.monitoring.metrics;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.models.property.Property;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTest;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class DropletCpuTest extends AbstractDigitalOceanTest {

    @Test
    void fetchesDropletCpuMetrics(WireMockRuntimeInfo wm) throws Exception {
        stubGetJson("/v2/monitoring/metrics/droplet/cpu", """
            {
              "status": "success",
              "data": {
                "resultType": "matrix",
                "result": [
                  {
                    "metric": {
                      "host_id": "123456",
                      "mode": "idle"
                    },
                    "values": [
                      [1728000000, "95.5"],
                      [1728000060, "94.2"]
                    ]
                  }
                ]
              }
            }
            """);

        var task = DropletCpu.builder()
            .id(IdUtils.create())
            .type(DropletCpu.class.getName())
            .apiToken(Property.ofValue("secret-token"))
            .baseUrl(Property.ofValue(wm.getHttpBaseUrl()))
            .hostId(Property.ofValue("123456"))
            .start(Property.ofValue("1728000000"))
            .end(Property.ofValue("1728003600"))
            .build();

        var output = task.run(runContext());

        assertThat(output.getStatus(), is("success"));
        assertThat(output.getResultType(), is("matrix"));
        assertThat(output.getData(), hasSize(1));
        assertThat(output.getData().getFirst().getMetric().get("mode"), is("idle"));
        assertThat(output.getData().getFirst().getValues(), hasSize(2));
        assertThat(output.getData().getFirst().getValues().getFirst().getValue(), is(95.5));
        verifyBearer(getRequestedFor(urlEqualTo("/v2/monitoring/metrics/droplet/cpu?host_id=123456&start=1728000000&end=1728003600")), "secret-token");
    }
}
