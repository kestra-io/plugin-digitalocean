package io.kestra.plugin.digitalocean.database;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTest;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListTest extends AbstractDigitalOceanTest {

    private static final String DATABASES_JSON = """
        {
          "databases": [
            {"id": "db-1", "name": "prod-pg", "engine": "pg", "region": "nyc1", "status": "online",
             "connection": {"host": "db-1.db.ondigitalocean.com", "port": 25060}}
          ],
          "links": {"pages": {}},
          "meta": {"total": 1}
        }
        """;

    @Test
    void listsDatabasesAndSendsBearerToken(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/v2/databases", DATABASES_JSON);

        var task = List.builder()
            .id("list-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .build();

        var output = task.run(runContext());

        assertThat(output.getTotal(), is(1L));
        assertThat(output.getRows().getFirst().get("name"), is("prod-pg"));
        verifyBearer(getRequestedFor(urlPathEqualTo("/v2/databases")), "test-token");
    }

    @Test
    void totalNeverUnderreportsTheFetchedRowsWhenMetaTotalIsZero(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        // DigitalOcean's /v2/databases endpoint has been observed returning the cluster in the array while
        // leaving meta.total at 0 (or omitting meta entirely). size and total must never disagree.
        stubGetJson("/v2/databases", """
            {
              "databases": [
                {"id": "db-1", "name": "prod-pg", "engine": "pg", "region": "nyc1", "status": "online",
                 "connection": {"host": "db-1.db.ondigitalocean.com", "port": 25060}}
              ],
              "links": {"pages": {}},
              "meta": {"total": 0}
            }
            """);

        var task = List.builder()
            .id("list-zero-meta-total-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .build();

        var output = task.run(runContext());

        assertThat(output.getTotal(), is(1L));
        assertThat(output.getSize(), is(1));
        assertThat(output.getRows(), hasSize(1));
    }

    @Test
    void totalNeverUnderreportsTheFetchedRowsWhenMetaIsAbsent(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubGetJson("/v2/databases", """
            {
              "databases": [
                {"id": "db-1", "name": "prod-pg", "engine": "pg", "region": "nyc1", "status": "online"},
                {"id": "db-2", "name": "prod-mysql", "engine": "mysql", "region": "nyc1", "status": "online"}
              ],
              "links": {"pages": {}}
            }
            """);

        var task = List.builder()
            .id("list-no-meta-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .build();

        var output = task.run(runContext());

        assertThat(output.getTotal(), is(2L));
        assertThat(output.getSize(), is(2));
        assertThat(output.getRows(), hasSize(2));
    }

    @Test
    void neverLeaksCredentialsFromDatabaseListRows(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        var secretPassword = "s3cr3t-db-password-do-not-leak";
        stubGetJson("/v2/databases", """
            {
              "databases": [
                {"id": "db-1", "name": "prod-pg", "engine": "pg", "region": "nyc1", "status": "online",
                 "num_nodes": 1, "size": "db-s-1vcpu-1gb", "created_at": "2024-01-01T00:00:00Z", "tags": ["prod"],
                 "connection": {"host": "db-1.db.ondigitalocean.com", "port": 25060, "user": "doadmin",
                   "password": "%s", "uri": "postgresql://doadmin:%s@db-1.db.ondigitalocean.com:25060/defaultdb"},
                 "private_connection": {"host": "private-db-1.db.ondigitalocean.com", "port": 25060,
                   "user": "doadmin", "password": "%s"},
                 "users": [{"name": "doadmin", "role": "primary", "password": "%s"}]}
              ],
              "links": {"pages": {}},
              "meta": {"total": 1}
            }
            """.formatted(secretPassword, secretPassword, secretPassword, secretPassword));

        var task = List.builder()
            .id("list-credential-leak-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .build();

        var output = task.run(runContext());

        var row = output.getRows().getFirst();
        assertThat(row.containsKey("connection"), is(false));
        assertThat(row.containsKey("private_connection"), is(false));
        assertThat(row.containsKey("users"), is(false));
        assertThat(row.get("name"), is("prod-pg"));

        var serializedRows = JacksonMapper.ofJson().writeValueAsString(output.getRows());
        assertThat(serializedRows, not(containsString(secretPassword)));
        assertThat(serializedRows, not(containsString("password")));
    }

    @Test
    void failsWithClearMessageOnRateLimit(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubStatusWithHeader("/v2/databases", 429, "{\"message\":\"too many requests\"}", "retry-after", "5");

        var task = List.builder()
            .id("list-429-test")
            .type(List.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("rate limit"));
    }
}
