package io.kestra.plugin.digitalocean.droplet;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTest;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListTest extends AbstractDigitalOceanTest {

    private static final String DROPLETS_JSON = """
        {
          "droplets": [
            {"id": 3164444, "name": "web-01", "status": "active", "size_slug": "s-1vcpu-1gb",
             "region": {"slug": "nyc3"},
             "networks": {"v4": [{"ip_address": "104.131.186.241", "type": "public"}]},
             "created_at": "2020-07-21T18:37:44Z"}
          ],
          "links": {"pages": {}},
          "meta": {"total": 1}
        }
        """;

    @Test
    void listsDropletsAndSendsBearerToken(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/v2/droplets", DROPLETS_JSON);

        var task = List.builder()
            .id("list-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .build();

        var output = task.run(runContext());

        assertThat(output.getTotal(), is(1L));
        assertThat(output.getSize(), is(1));
        assertThat(output.getRows().getFirst().get("name"), is("web-01"));
        verifyBearer(getRequestedFor(urlPathEqualTo("/v2/droplets")), "test-token");
    }

    @Test
    void fetchOneReturnsFirstDropletOnly(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/v2/droplets", DROPLETS_JSON);

        var task = List.builder()
            .id("list-fetch-one-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .fetchType(Property.ofValue(FetchType.FETCH_ONE))
            .build();

        var output = task.run(runContext());

        assertThat(output.getTotal(), is(1L));
        assertThat(output.getRows(), is((java.util.List<java.util.Map<String, Object>>) null));
        assertThat(output.getRow().get("id"), is(3164444));
    }

    @Test
    void failsWithClearMessageOnRateLimit(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubStatusWithHeader("/v2/droplets", 429, "{\"message\":\"too many requests\"}", "retry-after", "30");

        var task = List.builder()
            .id("list-429-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("rate limit"));
        assertThat(ex.getMessage(), containsString("30"));
    }

    @Test
    void failsWithClearMessageWhenPerPageExceedsMaximum(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = List.builder()
            .id("list-too-large-page-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .perPage(Property.ofValue(500))
            .build();

        var runContext = runContext();
        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("perPage must be between 1 and 200"));
    }
}
