# How to use the DigitalOcean plugin

This plugin manages DigitalOcean cloud resources (droplets, Kubernetes clusters, databases, load balancers,
volumes, domains, and firewalls) through the [DigitalOcean API v2](https://docs.digitalocean.com/reference/api/).

## Authentication

Every task and the droplet trigger require `apiToken`, a personal access token sent as
`Authorization: Bearer <token>`. Create one in the DigitalOcean control panel under API > Tokens; it is
prefixed `dop_v1_`. Store it as a [Kestra secret](https://kestra.io/docs/concepts/secret) and reference it
with `{{ secret('DIGITALOCEAN_TOKEN') }}`, or set it once as a
[plugin default](https://kestra.io/docs/workflow-components/plugin-defaults) so every task in a namespace
picks it up automatically.

`baseUrl` defaults to `https://api.digitalocean.com` and rarely needs to change.

## Rate limits

DigitalOcean returns HTTP 429 once the account's rate limit is hit. This plugin surfaces the response's
`retry-after` header value in the error message so a flow's retry configuration can back off accordingly.

## Tasks

Tasks are grouped by resource, one package per DigitalOcean resource type. Class names are bare actions
(`List`, `Get`, `Create`, `Delete`, ...), always used with their full package, for example
`io.kestra.plugin.digitalocean.droplet.Create`.

List tasks share the same shape: `perPage` (defaults to 200, the API maximum) and `fetchType`
(`FETCH`, `FETCH_ONE`, `STORE`, or `NONE`, defaults to `FETCH`), following DigitalOcean's page-based
pagination automatically and reporting the API's `total` count regardless of `fetchType`.

- **`droplet`**: `List`, `Get`, `Create`, `Delete`, and `Resize` (which despite its name also runs power
  actions: `POWER_ON`, `POWER_OFF`, `REBOOT`, `SNAPSHOT`, in addition to the default `RESIZE`).
- **`kubernetes`**: `List`, `Get`, `Create`, `Delete`, and `GetKubeconfig`. The kubeconfig contains a client
  certificate and key, so it is only ever written to Kestra internal storage, never returned as a string
  output.
- **`database`**: `List`, `Get`, `Create`, `Delete`, and `Resize`. Outputs never include the connection
  user or password, only host and port, to avoid leaking credentials into execution outputs.
- **`loadbalancer`**: `List`, `Get`, `Create`, `Update`, and `Delete`. DigitalOcean's update endpoint
  replaces the full configuration, not a partial patch: `Update` always requires `name`, `region`, and
  `forwardingRules` again, even to change a single field.
- **`volume`**: `List`, `Get`, `Create`, `Delete`, `Attach`, and `Detach`.
- **`domain`**: `List`, `Get`, `Create`, and `Delete` for DNS records under a domain already managed by
  DigitalOcean (`/v2/domains/{domain}/records`). `domain` is a required property on every task in this
  package.
- **`firewall`**: `List`, `Get`, `Create`, and `Delete`.

Nested inputs that are inherently a list of objects (Kubernetes node pools, load balancer forwarding
rules, firewall inbound/outbound rules) are modeled as `Property<List<Map<String, Object>>>`, following
DigitalOcean's own JSON shape for each field, rather than a dedicated type per rule variant.

## Triggers

- **`droplet.Trigger`**: polls the account's droplet list at `interval` (defaults to `PT5M`) and fires an
  execution when a new droplet appears. The first evaluation only establishes the baseline of existing
  droplet ids and never fires. Only one new droplet is reported per poll; if several droplets appear
  between polls, the rest are reported on the following polls. Outputs: `id`, `name`, `region`, `status`,
  `createdAt`.
