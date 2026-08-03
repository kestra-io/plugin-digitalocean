@PluginSubGroup(
    title = "Kubernetes",
    description = "Tasks for managing DigitalOcean Kubernetes (DOKS) clusters: list, read, create, and " +
        "delete clusters, and download a cluster's kubeconfig " +
        "(https://docs.digitalocean.com/products/kubernetes/).",
    categories = PluginSubGroup.PluginCategory.CLOUD
)
package io.kestra.plugin.digitalocean.kubernetes;

import io.kestra.core.models.annotations.PluginSubGroup;
