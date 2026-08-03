@PluginSubGroup(
    title = "DigitalOcean Domain Records",
    description = "Tasks for managing DNS records within a DigitalOcean domain zone: list, read, create, " +
        "and delete records under an existing zone. See io.kestra.plugin.digitalocean.domain for creating " +
        "the zone itself (https://docs.digitalocean.com/products/networking/dns/).",
    categories = PluginSubGroup.PluginCategory.CLOUD
)
package io.kestra.plugin.digitalocean.domain.record;

import io.kestra.core.models.annotations.PluginSubGroup;
