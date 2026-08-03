@PluginSubGroup(
    title = "Droplets",
    description = "Tasks and a trigger for managing DigitalOcean Droplets (virtual machines): list, read, " +
        "create, resize or run power actions on, and delete droplets, plus a polling trigger that fires " +
        "when a new droplet appears on the account " +
        "(https://docs.digitalocean.com/products/droplets/).",
    categories = PluginSubGroup.PluginCategory.CLOUD
)
package io.kestra.plugin.digitalocean.droplet;

import io.kestra.core.models.annotations.PluginSubGroup;
