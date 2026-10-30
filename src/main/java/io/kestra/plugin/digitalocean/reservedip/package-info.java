@PluginSubGroup(
    title = "Reserved IPs",
    description = "Tasks for moving DigitalOcean reserved IPs between Droplets: assign a reserved IP to a Droplet, " +
        "unassign it, and follow the action until it completes (https://docs.digitalocean.com/products/networking/reserved-ips/).",
    categories = PluginSubGroup.PluginCategory.CLOUD
)
package io.kestra.plugin.digitalocean.reservedip;

import io.kestra.core.models.annotations.PluginSubGroup;
