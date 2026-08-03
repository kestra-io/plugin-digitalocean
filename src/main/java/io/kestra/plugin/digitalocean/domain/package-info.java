@PluginSubGroup(
    title = "Domains",
    description = "Tasks for managing DigitalOcean domain zones (the /v2/domains resource itself): list, " +
        "read, create, and delete zones. DNS records within a zone are managed by the child package " +
        "io.kestra.plugin.digitalocean.domain.record (https://docs.digitalocean.com/products/networking/dns/).",
    categories = PluginSubGroup.PluginCategory.CLOUD
)
package io.kestra.plugin.digitalocean.domain;

import io.kestra.core.models.annotations.PluginSubGroup;
