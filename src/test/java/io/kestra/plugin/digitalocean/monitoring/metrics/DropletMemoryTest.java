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

class DropletMemoryTest extends AbstractDigitalOceanTest {

    @Test
    void fetchesDropletMemoryMetrics(WireMockRuntimeInfo wm) throws Exception {
        stubGetJson("/v2/monitoring/metrics/droplet/memory_available", """
            {
              "status": "success",
              "data": {
                "resultType": "matrix",
                "result": [
                  {
                    "metric": {
                      "host_id": "123456"
                    },
                    "values": [
                      [1728000000, "1048576000"]
                    ]
                  }
                ]
              }
            }
            """);

        var task = DropletMemory.builder()
            .id(IdUtils.create())
            .type(DropletMemory.class.getName())
            .apiToken(Property.ofValue("secret-token"))
            .baseUrl(Property.ofValue(wm.getHttpBaseUrl()))
            .hostId(Property.ofValue("123456"))
            .metricType(Property.ofValue("memory_available"))
            .start(Property.ofValue("1728000000"))
            .end(Property.ofValue("1728003600"))
            .build();

        var output = task.run(runContext());

        assertThat(output.getStatus(), is("success"));
        assertThat(output.getData(), hasSize(1));
        assertThat(output.getData().getFirst().getValues().getFirst().getValue(), is(1048576000.0));
        verifyBearer(getRequestedFor(urlEqualTo("/v2/monitoring/metrics/droplet/memory_available?host_id=123456&start=1728000000&end=1728003600")), "secret-token");
    }
}
