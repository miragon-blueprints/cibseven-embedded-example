package io.miragon.blueprint.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Exports the live OpenAPI contract to {@code openapi/openapi.json} at the repo root, so the committed
 * spec can never lie about the code. CI regenerates it and runs {@code git diff --exit-code} — the
 * output must be byte-for-byte deterministic, or that gate would flap.
 *
 * <p>Determinism is bought three ways: {@link SerializationFeature#ORDER_MAP_ENTRIES_BY_KEYS} sorts
 * every object key, a fixed two-space LF indenter keeps it stable across OSes, and a trailing newline
 * keeps POSIX tools happy.
 *
 * <p>This is not really an assertion test — it is a code generator wearing a JUnit costume so it runs
 * inside {@code mvn verify} with a live application context. See ADR-0003.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.web-application-type=servlet")
@ActiveProfiles("test")
class OpenApiSpecExportTest {

    @Value("${local.server.port}")
    private int port;

    private final JsonMapper deterministicMapper = JsonMapper.builder()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .defaultPrettyPrinter(new DefaultPrettyPrinter().withObjectIndenter(new DefaultIndenter("  ", "\n")))
            .build();

    @Test
    void exportsTheOpenApiContractToOpenapiOpenapiJsonAtTheRepoRoot() throws Exception {
        // given: the live spec served by springdoc
        String raw = fetch("http://localhost:" + port + "/v3/api-docs");
        assertThat(raw).isNotBlank();

        // when: it is re-serialised with sorted keys and a fixed indenter
        ObjectNode tree = (ObjectNode) deterministicMapper.readTree(raw);
        // Drop the `servers` block — springdoc fills it with the random test port, which would make
        // the drift gate flap. The frontend client uses the httpClient mutator's baseURL anyway.
        tree.remove("servers");
        // springdoc's component-schema map order is not guaranteed stable across classpaths — adding
        // the CIB seven web client reordered it — so sort the schemas by name to keep the export
        // byte-identical regardless. Paths and property order are already deterministic.
        sortComponentSchemas(tree);
        String pretty = deterministicMapper.writeValueAsString(tree) + "\n";

        // then: the result contains our /api paths and is written to the committed location
        assertThat(pretty).contains("\"/api/bike-leasing\"");
        Path target = repoRoot().resolve("openapi").resolve("openapi.json");
        Files.createDirectories(target.getParent());
        Files.writeString(target, pretty);
    }

    /** Reorders {@code components.schemas} alphabetically by name for a deterministic, stable export. */
    private void sortComponentSchemas(ObjectNode root) {
        if (root.get("components") instanceof ObjectNode components
                && components.get("schemas") instanceof ObjectNode schemas) {
            Map<String, JsonNode> byName = new TreeMap<>();
            schemas.properties().forEach(entry -> byName.put(entry.getKey(), entry.getValue()));
            ObjectNode sorted = deterministicMapper.createObjectNode();
            byName.forEach(sorted::set);
            components.set("schemas", sorted);
        }
    }

    private String fetch(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    /** Walk up from the module working directory until the repo root (the one dir with an {@code openapi/}). */
    private Path repoRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("openapi")) && Files.exists(dir.resolve("pom.xml"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "could not locate the repo root (no openapi/ dir with pom.xml above "
                        + System.getProperty("user.dir") + ")");
    }
}
