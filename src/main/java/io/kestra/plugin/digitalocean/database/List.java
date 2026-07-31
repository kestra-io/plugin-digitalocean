package io.kestra.plugin.digitalocean.database;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanListTask;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List DigitalOcean database clusters",
    description = "Lists managed database clusters on the account, following DigitalOcean's page-based " +
        "pagination automatically. Each row has its connection, private_connection, and users fields " +
        "stripped before being returned: DigitalOcean's list endpoint embeds credentials (a connection " +
        "user/password, a uri with the password inlined, and a users array of passwords) in every item, " +
        "and this task never surfaces them in outputs or internal storage."
)
@Plugin(
    examples = {
        @Example(
            title = "List all database clusters and log how many exist",
            full = true,
            code = """
                id: digitalocean_list_databases
                namespace: company.team

                tasks:
                  - id: list_databases
                    type: io.kestra.plugin.digitalocean.database.List
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                  - id: log_total
                    type: io.kestra.plugin.core.log.Log
                    message: "Found {{ outputs.list_databases.total }} database cluster(s)"
                """
        )
    }
)
public class List extends AbstractDigitalOceanListTask {

    @Override
    protected String path(RunContext runContext) {
        return "v2/databases";
    }

    @Override
    protected String arrayKey() {
        return "databases";
    }

    @Override
    protected String resourceLabel() {
        return "database cluster(s)";
    }

    /**
     * Strips the credential-bearing fields DigitalOcean embeds in every database list item: connection
     * and private_connection each carry a user/password and a uri with the password inlined, and users
     * carries a password per database user. Dropping only "password" would still leak it through uri.
     */
    @Override
    protected java.util.List<Map<String, Object>> transformRows(RunContext runContext, java.util.List<Map<String, Object>> rows) {
        return rows.stream().map(database -> {
            var sanitized = new LinkedHashMap<>(database);
            sanitized.remove("connection");
            sanitized.remove("private_connection");
            sanitized.remove("users");
            return (Map<String, Object>) sanitized;
        }).toList();
    }
}
