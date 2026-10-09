package io.kestra.plugin.digitalocean.spaces.key;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanListTask;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List DigitalOcean Spaces access keys",
    description = "Lists Spaces access keys on the account, optionally filtered by name, bucket, or permission, " +
        "following DigitalOcean's page-based pagination automatically. Secret keys are never included."
)
@Plugin(
    examples = {
        @Example(
            title = "List the keys that can read or write a bucket",
            full = true,
            code = """
                id: digitalocean_list_spaces_keys
                namespace: company.team

                tasks:
                  - id: list_keys
                    type: io.kestra.plugin.digitalocean.spaces.key.List
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    bucket: reports
                    permission: READWRITE
                  - id: log_total
                    type: io.kestra.plugin.core.log.Log
                    message: "{{ outputs.list_keys.total }} key(s) can write to the reports bucket"
                """
        )
    }
)
public class List extends AbstractDigitalOceanListTask {

    @Schema(title = "Key name", description = "Only list keys with this name.")
    @PluginProperty(group = "main")
    private Property<String> name;

    @Schema(title = "Bucket name", description = "Only list keys with a grant on this bucket.")
    @PluginProperty(group = "main")
    private Property<String> bucket;

    @Schema(title = "Permission", description = "Only list keys with this permission: READ, READWRITE, or FULLACCESS.")
    @PluginProperty(group = "main")
    private Property<Permission> permission;

    @Override
    protected String path(RunContext runContext) throws Exception {
        var query = new ArrayList<String>();
        runContext.render(name).as(String.class).ifPresent(v -> query.add("name=" + encodePathSegment(v)));
        runContext.render(bucket).as(String.class).ifPresent(v -> query.add("bucket=" + encodePathSegment(v)));
        runContext.render(permission).as(Permission.class).ifPresent(v -> query.add("permission=" + v.apiValue()));
        return query.isEmpty() ? "v2/spaces/keys" : "v2/spaces/keys?" + String.join("&", query);
    }

    @Override
    protected String arrayKey() {
        return "keys";
    }

    @Override
    protected String resourceLabel() {
        return "Spaces access key(s)";
    }
}
