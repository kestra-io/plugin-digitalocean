package io.kestra.plugin.digitalocean.volume;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetActionTest extends AbstractDigitalOceanTest {

    private GetAction task(WireMockRuntimeInfo wireMockRuntimeInfo) {
        return GetAction.builder()
            .id(IdUtils.create())
            .type(GetAction.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .volumeId(Property.ofValue("vol-1"))
            .actionId(Property.ofValue("72531856"))
            .build();
    }

    @Test
    void readsCompletedActionAndSendsBearerToken(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/v2/volumes/vol-1/actions/72531856", """
            {"action": {"id": 72531856, "status": "completed", "type": "attach_volume",
              "started_at": "2020-11-21T21:51:09Z", "completed_at": "2020-11-21T21:51:12Z",
              "resource_id": null, "resource_type": "volume", "region": {"slug": "nyc1"}, "region_slug": "nyc3"}}
            """);

        var output = task(wireMockRuntimeInfo).run(runContext());

        assertThat(output.getActionId(), is(72531856L));
        assertThat(output.getStatus(), is("completed"));
        assertThat(output.getType(), is("attach_volume"));
        assertThat(output.getStartedAt(), is(Instant.parse("2020-11-21T21:51:09Z")));
        assertThat(output.getCompletedAt(), is(Instant.parse("2020-11-21T21:51:12Z")));
        assertThat(output.getRegion(), is("nyc3"));
        verifyBearer(getRequestedFor(urlPathEqualTo("/v2/volumes/vol-1/actions/72531856")), "test-token");
    }

    @Test
    void readsInProgressActionWithoutRegionSlug(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/v2/volumes/vol-1/actions/72531856", """
            {"action": {"id": 72531856, "status": "in-progress", "type": "resize_volume",
              "started_at": "2020-11-21T21:51:09Z", "completed_at": null, "region": {"slug": "nyc1"}, "region_slug": null}}
            """);

        var output = task(wireMockRuntimeInfo).run(runContext());

        assertThat(output.getStatus(), is("in-progress"));
        assertThat(output.getCompletedAt(), is(nullValue()));
        assertThat(output.getRegion(), is("nyc1"));
    }

    @Test
    void failsWithClearMessageOnNotFound(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubStatus("/v2/volumes/vol-1/actions/72531856", 404, "{\"id\":\"not_found\",\"message\":\"The resource you were accessing could not be found.\"}");

        var task = task(wireMockRuntimeInfo);

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("HTTP 404"));
    }

    @Test
    void rendersANumericActionIdFromAnExpression(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/v2/volumes/vol-1/actions/72531856", """
            {"action": {"id": 72531856, "status": "completed", "type": "resize_volume", "region_slug": "nyc3"}}
            """);

        var task = GetAction.builder()
            .id(IdUtils.create())
            .type(GetAction.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .volumeId(Property.ofValue("vol-1"))
            .actionId(Property.ofExpression("{{ outputs.resize_volume.actionId }}"))
            .build();

        var output = task.run(runContextFactory.of(Map.of("outputs", Map.of("resize_volume", Map.of("actionId", 72531856L)))));

        assertThat(output.getStatus(), is("completed"));
    }
}
