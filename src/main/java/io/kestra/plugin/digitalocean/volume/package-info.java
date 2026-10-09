@PluginSubGroup(
    title = "Volumes",
    description = "Tasks for managing DigitalOcean block storage volumes: list, read, create, delete, " +
        "attach, detach or resize volumes and follow those actions, and take volume snapshots (https://docs.digitalocean.com/products/volumes/).",
    categories = PluginSubGroup.PluginCategory.CLOUD
)
package io.kestra.plugin.digitalocean.volume;

import io.kestra.core.models.annotations.PluginSubGroup;
