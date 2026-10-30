@PluginSubGroup(
    title = "App Platform Deployments",
    description = "Tasks for DigitalOcean App Platform deployments: start a deployment of an app, follow its phase until " +
        "it is live or failed, list past deployments, and cancel one in progress " +
        "(https://docs.digitalocean.com/products/app-platform/how-to/manage-deployments/).",
    categories = PluginSubGroup.PluginCategory.CLOUD
)
package io.kestra.plugin.digitalocean.app.deployment;

import io.kestra.core.models.annotations.PluginSubGroup;
