package io.kestra.plugin.digitalocean.database;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTest;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetTest extends AbstractDigitalOceanTest {

    private static final String DATABASE_JSON = """
        {
          "database": {"id": "db-1", "name": "prod-pg", "engine": "pg", "region": "nyc1", "status": "online",
             "connection": {"host": "db-1.db.ondigitalocean.com", "port": 25060, "user": "doadmin", "password": "secret"}}
        }
        """;

    @Test
    void fetchesDatabaseAndNeverLeaksCredentials(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/v2/databases/db-1", DATABASE_JSON);

        var task = Get.builder()
            .id("get-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .databaseId(Property.ofValue("db-1"))
            .build();

        var output = task.run(runContext());

        assertThat(output.getId(), is("db-1"));
        assertThat(output.getHost(), is("db-1.db.ondigitalocean.com"));
        assertThat(output.getPort(), is(25060));
        assertThat(output.toString(), not(containsString("secret")));
        verifyBearer(getRequestedFor(urlPathEqualTo("/v2/databases/db-1")), "test-token");
    }

    @Test
    void failsWithClearMessageWhenNotFound(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubStatus("/v2/databases/missing", 404, "{\"message\":\"not found\"}");

        var task = Get.builder()
            .id("get-404-test")
            .type(Get.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .databaseId(Property.ofValue("missing"))
            .build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("resource not found"));
    }
}
