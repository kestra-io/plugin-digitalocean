package io.kestra.plugin.digitalocean.spaces.key;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.LinkedHashMap;
import java.util.Map;

/** A plain bean, not a Property field, so a {@code Property<java.util.List<Grant>>} renders the whole list at once. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class Grant {

    @Schema(title = "Bucket name", description = "Name of the Spaces bucket the permission applies to. Leave empty for FULLACCESS, which covers every bucket.")
    private String bucket;

    @Schema(title = "Permission", description = "READ, READWRITE, or FULLACCESS.")
    @NotNull
    private Permission permission;

    Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("bucket", bucket != null ? bucket : "");
        map.put("permission", permission.apiValue());
        return map;
    }
}
