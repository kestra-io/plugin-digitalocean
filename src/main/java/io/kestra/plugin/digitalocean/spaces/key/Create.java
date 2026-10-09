package io.kestra.plugin.digitalocean.spaces.key;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.EncryptedString;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create a DigitalOcean Spaces access key",
    description = """
        Creates an S3-compatible access key for Spaces, scoped to specific buckets (READ or READWRITE) or with \
        FULLACCESS to every bucket. DigitalOcean returns the secret key only once, in this response: the task \
        outputs it as an encrypted string, so it is not stored in cleartext in the execution outputs (this \
        requires Kestra's encryption secret key to be configured)."""
)
@Plugin(
    examples = {
        @Example(
            title = "Create a read-only key for one bucket and pass it to a later task",
            full = true,
            code = """
                id: digitalocean_create_spaces_key
                namespace: company.team

                tasks:
                  - id: create_key
                    type: io.kestra.plugin.digitalocean.spaces.key.Create
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    name: reports-reader
                    grants:
                      - bucket: reports
                        permission: READ
                  - id: log_key
                    type: io.kestra.plugin.core.log.Log
                    message: "Created access key {{ outputs.create_key.accessKey }}"
                """
        )
    }
)
public class Create extends AbstractDigitalOceanTask implements RunnableTask<Create.Output> {

    @Schema(title = "Key name", description = "Name of the access key.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> name;

    @Schema(
        title = "Grants",
        description = """
            Bucket permissions of the key. Each grant has a bucket and a permission (READ or READWRITE); \
            use a single grant with permission FULLACCESS and no bucket for a key that can access every \
            bucket. FULLACCESS cannot be combined with bucket-scoped grants. Leave empty for a key with \
            no access yet."""
    )
    @PluginProperty(group = "main")
    private Property<List<Grant>> grants;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rName = requireRendered(runContext, name, String.class, "name");
        @SuppressWarnings("unchecked")
        var rGrants = (List<Grant>) runContext.render(grants).asList(Grant.class);
        var grantMaps = validateGrants(rGrants);
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        var payload = new LinkedHashMap<String, Object>();
        payload.put("name", rName);
        payload.put("grants", grantMaps);

        logger.info("Creating DigitalOcean Spaces access key '{}' with {} grant(s)", rName, grantMaps.size());

        var url = join(rBaseUrl, "v2/spaces/keys");
        var requestBuilder = HttpRequest.builder()
            .uri(URI.create(url))
            .method("POST")
            .body(HttpRequest.JsonRequestBody.of(payload));

        var body = requestJson(runContext, options, rApiToken, requestBuilder);
        var key = unwrap(body, "key");
        var secretKey = asString(key.get("secret_key"));
        if (secretKey == null) {
            logger.warn("DigitalOcean did not return a secret key for access key {}.", asString(key.get("access_key")));
        }
        var created = KeyOutput.from(key);

        return Output.builder()
            .name(created.getName())
            .accessKey(created.getAccessKey())
            .secretKey(secretKey != null ? EncryptedString.from(secretKey, runContext) : null)
            .grants(created.getGrants())
            .createdAt(created.getCreatedAt())
            .build();
    }

    /**
     * Rejects grants DigitalOcean would refuse or silently widen: a scoped grant without a bucket, and
     * FULLACCESS mixed with scoped grants (the API documents that FULLACCESS then takes priority).
     */
    static List<Map<String, Object>> validateGrants(List<Grant> grants) {
        if (grants == null || grants.isEmpty()) {
            return List.of();
        }
        var hasFullAccess = false;
        var hasScoped = false;
        for (var grant : grants) {
            if (grant == null || grant.getPermission() == null) {
                throw new IllegalArgumentException("Each grant needs a permission: READ, READWRITE, or FULLACCESS");
            }
            if (grant.getPermission() == Permission.FULLACCESS) {
                hasFullAccess = true;
            } else {
                hasScoped = true;
                if (grant.getBucket() == null || grant.getBucket().isBlank()) {
                    throw new IllegalArgumentException("A " + grant.getPermission() + " grant needs a bucket name");
                }
            }
        }
        if (hasFullAccess && hasScoped) {
            throw new IllegalArgumentException(
                "FULLACCESS cannot be combined with bucket-scoped grants: use a single FULLACCESS grant, or only READ/READWRITE grants"
            );
        }
        return grants.stream().map(Grant::toMap).toList();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "Key name")
        private final String name;

        @Schema(title = "Access key ID", description = "The access key ID used to access the buckets.")
        private final String accessKey;

        @Schema(
            title = "Secret key",
            description = "The secret key, encrypted in the execution outputs. DigitalOcean returns it only at creation, so store it right away, e.g. in a secret manager."
        )
        private final EncryptedString secretKey;

        @Schema(title = "Grants", description = "Bucket permissions of the key, each with a bucket and a permission (read, readwrite, or fullaccess).")
        private final List<Map<String, String>> grants;

        @Schema(title = "Creation timestamp")
        private final Instant createdAt;
    }
}
