package io.kestra.plugin.digitalocean.spaces.key;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.EncryptedString;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CreateTest extends AbstractDigitalOceanTest {

    private static final String KEY_JSON = """
        {"key": {"name": "reports-key", "access_key": "DO00ACCESS", "secret_key": "s3cr3t-value",
          "grants": [{"bucket": "reports", "permission": "read"}, {"bucket": "uploads", "permission": "readwrite"}],
          "created_at": "2026-10-06T12:00:00Z"}}
        """;

    private Create task(WireMockRuntimeInfo wireMockRuntimeInfo, List<Grant> grants) {
        return Create.builder()
            .id(IdUtils.create())
            .type(Create.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .name(Property.ofValue("reports-key"))
            .grants(grants == null ? null : Property.ofValue(grants))
            .build();
    }

    @Test
    void createsScopedKeyAndEncryptsTheSecret(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubPostJson("/v2/spaces/keys", 201, KEY_JSON);

        var runContext = runContext();
        var output = task(wireMockRuntimeInfo, List.of(
            new Grant("reports", Permission.READ),
            new Grant("uploads", Permission.READWRITE)
        )).run(runContext);

        assertThat(output.getAccessKey(), is("DO00ACCESS"));
        assertThat(output.getGrants().get(1).get("permission"), is("readwrite"));
        assertThat(output.getSecretKey().getType(), is(EncryptedString.TYPE));
        assertThat(output.getSecretKey().getValue(), not(containsString("s3cr3t-value")));
        assertThat(runContext.decrypt(output.getSecretKey().getValue()), is("s3cr3t-value"));
        verifyBearer(postRequestedFor(urlPathEqualTo("/v2/spaces/keys")), "test-token");
        verify(postRequestedFor(urlPathEqualTo("/v2/spaces/keys")).withRequestBody(equalToJson("""
            {"name": "reports-key", "grants": [{"bucket": "reports", "permission": "read"}, {"bucket": "uploads", "permission": "readwrite"}]}
            """)));
    }

    @Test
    void sendsAnEmptyBucketForFullAccess(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubPostJson("/v2/spaces/keys", 201, KEY_JSON);

        task(wireMockRuntimeInfo, List.of(Grant.builder().permission(Permission.FULLACCESS).build())).run(runContext());

        verify(postRequestedFor(urlPathEqualTo("/v2/spaces/keys")).withRequestBody(equalToJson("""
            {"name": "reports-key", "grants": [{"bucket": "", "permission": "fullaccess"}]}
            """)));
    }

    @Test
    void sendsNoGrantsWhenNoneAreGiven(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubPostJson("/v2/spaces/keys", 201, KEY_JSON);

        task(wireMockRuntimeInfo, null).run(runContext());

        verify(postRequestedFor(urlPathEqualTo("/v2/spaces/keys")).withRequestBody(equalToJson("""
            {"name": "reports-key", "grants": []}
            """)));
    }

    @Test
    void rejectsFullAccessMixedWithScopedGrantsBeforeCallingTheApi(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = task(wireMockRuntimeInfo, List.of(
            new Grant("reports", Permission.READ),
            new Grant(null, Permission.FULLACCESS)
        ));

        var runContext = runContext();
        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("FULLACCESS cannot be combined with bucket-scoped grants"));
        verify(0, postRequestedFor(urlPathEqualTo("/v2/spaces/keys")));
    }

    @Test
    void rejectsAScopedGrantWithoutBucket(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var task = task(wireMockRuntimeInfo, List.of(new Grant(" ", Permission.READWRITE)));

        var runContext = runContext();
        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("READWRITE grant needs a bucket name"));
        verify(0, postRequestedFor(urlPathEqualTo("/v2/spaces/keys")));
    }

    @Test
    void surfacesTheApiReasonOnBadRequest(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubPostJson("/v2/spaces/keys", 400, "{\"id\": \"bad_request\", \"message\": \"bucket does not exist\"}");

        var task = task(wireMockRuntimeInfo, List.of(new Grant("missing", Permission.READ)));

        var runContext = runContext();
        var ex = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("HTTP 400"));
        assertThat(ex.getMessage(), containsString("bucket does not exist"));
    }

    @Test
    void rendersTheNameAndGrantsFromExpressions(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubPostJson("/v2/spaces/keys", 201, KEY_JSON);

        var task = Create.builder()
            .id(IdUtils.create())
            .type(Create.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .baseUrl(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .name(Property.ofExpression("{{ inputs.team }}-reader"))
            .grants(Property.ofExpression("{{ inputs.grants }}"))
            .build();

        task.run(runContextFactory.of(Map.of("inputs", Map.of(
            "team", "reports",
            "grants", List.of(Map.of("bucket", "reports", "permission", "READ"))
        ))));

        verify(postRequestedFor(urlPathEqualTo("/v2/spaces/keys")).withRequestBody(equalToJson("""
            {"name": "reports-reader", "grants": [{"bucket": "reports", "permission": "read"}]}
            """)));
    }
}
