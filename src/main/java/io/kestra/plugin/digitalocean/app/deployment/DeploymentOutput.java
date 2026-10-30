package io.kestra.plugin.digitalocean.app.deployment;

import io.kestra.core.models.tasks.Output;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static io.kestra.plugin.digitalocean.AbstractDigitalOceanTask.asInteger;
import static io.kestra.plugin.digitalocean.AbstractDigitalOceanTask.asMap;
import static io.kestra.plugin.digitalocean.AbstractDigitalOceanTask.asString;

/** Shared output shape for {@link Create}, {@link Get}, and {@link Cancel}: all three return the same deployment JSON object. */
@Builder
@Getter
public class DeploymentOutput implements Output {

    @Schema(title = "Deployment ID")
    private final String id;

    @Schema(
        title = "Phase",
        description = """
            One of PENDING_BUILD, BUILDING, PENDING_DEPLOY, DEPLOYING, ACTIVE, SUPERSEDED, ERROR, CANCELED, or UNKNOWN. \
            ACTIVE means the deployment is live; ERROR, CANCELED, and SUPERSEDED mean it will not go live."""
    )
    private final String phase;

    @Schema(title = "Cause", description = "What started the deployment, e.g. a manual deployment or a push to the repository.")
    private final String cause;

    @Schema(title = "Successful steps")
    private final Integer successSteps;

    @Schema(title = "Failed steps")
    private final Integer errorSteps;

    @Schema(title = "Total steps")
    private final Integer totalSteps;

    @Schema(title = "Error message", description = "Reason given by the first failed step, if any, e.g. why the build failed.")
    private final String errorMessage;

    @Schema(title = "Creation timestamp")
    private final Instant createdAt;

    @Schema(title = "Last update timestamp")
    private final Instant updatedAt;

    @Schema(title = "Last phase change timestamp")
    private final Instant phaseLastUpdatedAt;

    public static DeploymentOutput from(Map<String, Object> deployment) {
        var progress = asMap(deployment.get("progress"));

        return DeploymentOutput.builder()
            .id(asString(deployment.get("id")))
            .phase(asString(deployment.get("phase")))
            .cause(asString(deployment.get("cause")))
            .successSteps(progress != null ? asInteger(progress.get("success_steps")) : null)
            .errorSteps(progress != null ? asInteger(progress.get("error_steps")) : null)
            .totalSteps(progress != null ? asInteger(progress.get("total_steps")) : null)
            .errorMessage(progress != null ? firstErrorMessage(progress.get("steps")) : null)
            .createdAt(instant(deployment.get("created_at")))
            .updatedAt(instant(deployment.get("updated_at")))
            .phaseLastUpdatedAt(instant(deployment.get("phase_last_updated_at")))
            .build();
    }

    // Steps nest (a build step holds one child per component), and the reason sits on the step that failed.
    static String firstErrorMessage(Object steps) {
        if (!(steps instanceof List<?> list)) {
            return null;
        }
        for (var item : list) {
            var step = asMap(item);
            if (step == null) {
                continue;
            }
            var reason = asMap(step.get("reason"));
            if ("ERROR".equals(asString(step.get("status"))) && reason != null && asString(reason.get("message")) != null) {
                return asString(reason.get("message"));
            }
            var nested = firstErrorMessage(step.get("steps"));
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private static Instant instant(Object value) {
        var text = asString(value);
        return text != null ? Instant.parse(text) : null;
    }
}
