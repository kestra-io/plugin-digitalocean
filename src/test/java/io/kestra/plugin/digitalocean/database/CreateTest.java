package io.kestra.plugin.digitalocean.database;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTest;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CreateTest extends AbstractDigitalOceanTest {

    private static final String DATABASE_JSON = """
        {
          "database": {"id": "db-2", "name": "prod-mysql", "engine": "mysql", "region": "nyc1", "status": "creating",
             "connection": {"host": "db-2.db.ondigitalocean.com", "port": 25060}}
        }
        """;

    @Test
    void createsDatabaseAndSendsBearerToken(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubPostJson("/v2/databases", 201, DATABASE_JSON);

        var task = Create.builder()
            .id("create-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .name(Property.ofValue("prod-mysql"))
            .engine(Property.ofValue("mysql"))
            .engineVersion(Property.ofValue("8"))
            .region(Property.ofValue("nyc1"))
            .size(Property.ofValue("db-s-1vcpu-1gb"))
            .build();

        var output = task.run(runContext());

        assertThat(output.getId(), is("db-2"));
        assertThat(output.getStatus(), is("creating"));
        verifyBearer(postRequestedFor(urlPathEqualTo("/v2/databases")), "test-token");
    }

    @Test
    void failsWithClearMessageOnInvalidToken(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubPostJson("/v2/databases", 401, "{\"message\":\"Unable to authenticate you\"}");

        var task = Create.builder()
            .id("create-401-test")
            .type(Create.class.getName())
            .apiToken(Property.ofValue("bad-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .name(Property.ofValue("prod-mysql"))
            .engine(Property.ofValue("mysql"))
            .engineVersion(Property.ofValue("8"))
            .region(Property.ofValue("nyc1"))
            .size(Property.ofValue("db-s-1vcpu-1gb"))
            .build();

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("invalid or missing API token"));
    }
}
