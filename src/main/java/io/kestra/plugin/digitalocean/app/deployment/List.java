package io.kestra.plugin.digitalocean.app.deployment;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanListTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List the deployments of a DigitalOcean App Platform app",
    description = """
        Lists an app's deployments, following DigitalOcean's page-based pagination automatically. \
        Each row keeps the deployment's ID, phase, cause, timestamps, and step counts; the app spec and the \
        per-component details are left out, so read one deployment with `app.deployment.Get`."""
)
@Plugin(
    examples = {
        @Example(
            title = "List the deployments of an app and log how many there are",
            full = true,
            code = """
                id: digitalocean_list_app_deployments
                namespace: company.team

                tasks:
                  - id: list_deployments
                    type: io.kestra.plugin.digitalocean.app.deployment.List
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    appId: 4f6c71e2-1e90-4762-9fee-6cc4a0a9f2cf
                  - id: log_total
                    type: io.kestra.plugin.core.log.Log
                    message: "The app has {{ outputs.list_deployments.total }} deployment(s)"
                """
        )
    }
)
public class List extends AbstractDigitalOceanListTask {

    private static final Set<String> KEPT_FIELDS = Set.of(
        "id", "cause", "phase", "phase_last_updated_at", "created_at", "updated_at", "cloned_from", "tier_slug"
    );

    private static final Set<String> KEPT_PROGRESS_FIELDS = Set.of(
        "success_steps", "error_steps", "pending_steps", "running_steps", "total_steps"
    );

    @Schema(title = "App ID", description = "ID of the App Platform app.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> appId;

    @Override
    protected String path(RunContext runContext) throws Exception {
        var rAppId = requireRendered(runContext, appId, String.class, "appId");
        return "v2/apps/" + encodePathSegment(rAppId) + "/deployments";
    }

    @Override
    protected String arrayKey() {
        return "deployments";
    }

    @Override
    protected String resourceLabel() {
        return "App Platform deployments";
    }

    // A deployment carries the whole app spec and one entry per component; kept in every row they would make
    // the output grow with the number of deployments times the size of the app.
    @Override
    protected java.util.List<Map<String, Object>> transformRows(RunContext runContext, java.util.List<Map<String, Object>> rows) {
        return rows.stream().map(List::summary).toList();
    }

    static Map<String, Object> summary(Map<String, Object> deployment) {
        var row = new LinkedHashMap<String, Object>();
        deployment.forEach((key, value) -> {
            if (KEPT_FIELDS.contains(key)) {
                row.put(key, value);
            }
        });
        if (deployment.get("progress") instanceof Map<?, ?> progress) {
            var counts = new LinkedHashMap<String, Object>();
            progress.forEach((key, value) -> {
                if (KEPT_PROGRESS_FIELDS.contains(String.valueOf(key))) {
                    counts.put(String.valueOf(key), value);
                }
            });
            row.put("progress", counts);
        }
        return row;
    }
}
