@PluginSubGroup(
    title = "DigitalOcean",
    description = "Tasks and a trigger for managing DigitalOcean cloud resources (droplets, Kubernetes " +
        "clusters, databases, load balancers, volumes, domains, and firewalls) through the DigitalOcean " +
        "API v2 (https://docs.digitalocean.com/reference/api/).",
    categories = PluginSubGroup.PluginCategory.CLOUD
)
package io.kestra.plugin.digitalocean;

import io.kestra.core.models.annotations.PluginSubGroup;