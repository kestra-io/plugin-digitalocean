package io.kestra.plugin.digitalocean.kubernetes;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * A single node pool for {@link Create}, mapping 1:1 to DigitalOcean's node_pools JSON shape: size, name,
 * and count are already the API's own field names. A plain bean (not a Property field) so a
 * {@code Property<java.util.List<NodePool>>} can render the whole list at once.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class NodePool {

    @Schema(title = "Size", description = "Droplet size slug for nodes in this pool, e.g. s-2vcpu-4gb.")
    @NotNull
    private String size;

    @Schema(title = "Name", description = "Name for the node pool.")
    @NotNull
    private String name;

    @Schema(title = "Count", description = "Number of nodes in the pool.")
    @NotNull
    private Integer count;
}
