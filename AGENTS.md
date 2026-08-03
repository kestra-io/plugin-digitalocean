# Kestra DigitalOcean Plugin

## What

- Provides plugin components under `io.kestra.plugin.digitalocean`.
- Manages DigitalOcean cloud resources through the DigitalOcean API v2 (`https://api.digitalocean.com`):
  droplets, Kubernetes clusters, managed databases, load balancers, block storage volumes, domain zones
  and their DNS records, and cloud firewalls.
- Includes a polling trigger (`droplet.Trigger`) that fires when a new droplet appears on the account.

## Why

- What user problem does this solve? Teams running infrastructure on DigitalOcean need to provision,
  inspect, resize, and tear down cloud resources as part of a Kestra flow, without hand-rolling HTTP calls.
- Why would a team adopt this plugin in a workflow? It gives infrastructure and platform teams typed
  tasks for the DigitalOcean API, with pagination, error handling, and fetch semantics already solved.
- What operational/business outcome does it enable? Automated droplet lifecycle management, cluster
  provisioning, database scaling, and DNS/firewall changes driven by Kestra flows instead of manual
  `doctl`/API scripting.

## How

### Architecture

Single-module plugin. All authentication, HTTP, pagination, and error-handling logic lives in
`io.kestra.plugin.digitalocean.AbstractDigitalOceanTask`, shared by every task across the resource
sub-packages below. `droplet.Trigger` extends `AbstractTrigger` (not `Task`), so it reuses the abstract
class's static helpers instead of extending it. Every `List` task extends
`io.kestra.plugin.digitalocean.AbstractDigitalOceanListTask`, which owns `perPage`/`fetchType` and the
`run()` template method; a subclass only supplies the endpoint path, JSON array key, a log label, and
(for `database.List`) a row-sanitizing hook. `requireRendered(runContext, property, type, name)` on
`AbstractDigitalOceanTask` replaces the repeated `render(...).as(...).orElseThrow(...)` pattern for
required properties.

Source packages under `io.kestra.plugin.digitalocean`:

- `droplet`: `List`, `Get`, `Create`, `Delete`, `Resize` (size/disk only), `Action` (power on/off, reboot,
  snapshot), `Trigger`.
- `kubernetes`: `List`, `Get`, `Create`, `Delete`, `GetKubeconfig`.
- `database`: `List`, `Get`, `Create`, `Delete`, `Resize`.
- `loadbalancer`: `List`, `Get`, `Create`, `Update`, `Delete`.
- `volume`: `List`, `Get`, `Create`, `Delete`, `Attach`, `Detach`.
- `domain`: `List`, `Get`, `Create`, `Delete` for domain zones (`/v2/domains`, the zone resource itself).
- `domain.record`: `List`, `Get`, `Create`, `Delete` for DNS records within a zone
  (`/v2/domains/{domain}/records`). A zone must exist (via `domain.Create`) before records can be added.
- `firewall`: `List`, `Get`, `Create`, `Delete`.

Each resource package has a shared `<Resource>Output` class (e.g. `DropletOutput`, `ClusterOutput`) reused
by its `Get` and `Create` (and `Update` where applicable) tasks, since both return the same JSON shape.
`Delete` tasks return `VoidOutput`. `List` tasks share `AbstractDigitalOceanTask.PageOutput`
(`rows`/`row`/`uri`/`size`/`total`, following `FetchType`). Droplet actions and volume attach/detach share
`AbstractDigitalOceanTask.ActionOutput` for DigitalOcean's async action response shape.

Two list-of-object inputs are typed classes instead of raw maps: `kubernetes.NodePool` (`size`, `name`,
`count`, already DigitalOcean's own field names) and `loadbalancer.ForwardingRule` (`entryProtocol`,
`entryPort`, `targetProtocol`, `targetPort`, optional `certificateId`/`tlsPassthrough`, converted to
DigitalOcean's snake_case via `ForwardingRule.toMap()` rather than Jackson annotations, since the same
field also has to deserialize from a plain camelCase flow). Firewall's inbound/outbound rules keep the
generic `Property<List<Map<String, Object>>>` shape since they have four different source/destination
variants.

No JSON/HTTP third-party dependency: all requests go through `io.kestra.core.http.client.HttpClient` and
`io.kestra.core.serializers.JacksonMapper`.

### Key Plugin Classes

- `io.kestra.plugin.digitalocean.AbstractDigitalOceanTask` (auth, pagination with a reused HttpClient per
  call, error rewriting, FetchType helpers)
- `io.kestra.plugin.digitalocean.AbstractDigitalOceanListTask` (shared `perPage`/`fetchType` and `run()` for every `List` task)
- `io.kestra.plugin.digitalocean.droplet.Trigger` (polling trigger, KV-based watermark with a per-id consecutive-miss counter)

### Project Structure

```
plugin-digitalocean/
├── src/main/java/io/kestra/plugin/digitalocean/
│   ├── AbstractDigitalOceanTask.java
│   ├── AbstractDigitalOceanListTask.java
│   ├── droplet/
│   ├── kubernetes/
│   ├── database/
│   ├── loadbalancer/
│   ├── volume/
│   ├── domain/
│   │   └── record/
│   └── firewall/
├── src/test/java/io/kestra/plugin/digitalocean/ (WireMock-based tests, one class per task/trigger)
├── build.gradle
└── README.md
```

## Local rules

- Base the wording on the implemented packages and classes, not on template README text.

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
- https://docs.digitalocean.com/reference/api/
