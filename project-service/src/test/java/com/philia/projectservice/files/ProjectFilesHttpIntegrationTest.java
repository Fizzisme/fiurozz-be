package com.philia.projectservice.files;

import com.philia.projectservice.ProjectServiceApplication;
import com.philia.projectservice.files.api.*;
import com.philia.projectservice.files.internal.application.*;
import com.philia.projectservice.files.internal.application.port.out.*;
import com.philia.projectservice.files.internal.domain.FilePolicy;
import com.philia.projectservice.files.internal.domain.exception.FilesException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real HTTP, PostgreSQL row/advisory locks and MinIO objects; never targets a developer's DB. */
@Testcontainers
@SpringBootTest(classes = ProjectServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.docker.compose.enabled=false", "spring.rabbitmq.listener.simple.auto-startup=false",
                "storage.files.gc-enabled=false", "storage.files.lease-sweep-ms=3600000"})
class ProjectFilesHttpIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(DockerImageName.parse(
            "quay.io/minio/minio@sha256:14cea493d9a34af32f524e538b8346cf79f3321eff8e708c1e2960462bd8936e"))
            .withEnv("MINIO_ROOT_USER", "files_test")
            .withEnv("MINIO_ROOT_PASSWORD", "files_test_password")
            .withCommand("server", "/data").withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("storage.files.endpoint", () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        registry.add("storage.files.access-key", () -> "files_test");
        registry.add("storage.files.secret-key", () -> "files_test_password");
        registry.add("storage.files.bucket", () -> "project-files-test");
    }

    @Value("${local.server.port}") int port;
    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired FileWorkspaceStore store;
    @Autowired FileBlobGarbageCollector gc;
    @MockitoSpyBean ProjectFilesHandler handler;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean Clock clock;
    @MockitoSpyBean FileBlobStorage blobs;
    private final HttpClient http = HttpClient.newHttpClient();
    private UUID projectId, ownerId, categoryId, subCategoryId;
    private Instant now;

    @BeforeEach
    void seed() {
        now = Instant.now();
        when(clock.instant()).thenReturn(now);
        projectId = UUID.randomUUID(); ownerId = UUID.randomUUID();
        categoryId = UUID.randomUUID(); subCategoryId = UUID.randomUUID();
        jdbc.sql("insert into project_categories(id,key,slug,title) values (:id,:key,:key,'Tests')")
                .param("id", categoryId).param("key", "test-" + categoryId).update();
        jdbc.sql("insert into project_sub_categories(id,category_id,key,slug,title) values (:id,:category,:key,:key,'Tests')")
                .param("id", subCategoryId).param("category", categoryId).param("key", "test-" + subCategoryId).update();
        jdbc.sql("""
                insert into projects(id,owner_id,owner_display_name,sub_category_id,title,slug,short_description,description)
                values (:id,:owner,'Owner',:subcategory,'Contract Test',:slug,'Contract test','Complete contract test description')
                """).param("id", projectId).param("owner", ownerId).param("subcategory", subCategoryId)
                .param("slug", "test-" + projectId).update();
    }

    @AfterEach
    void cleanup() {
        // Only UUID fixtures created by this test are removed; Testcontainers owns the entire DB.
        jdbc.sql("delete from projects where id=:id").param("id", projectId).update();
        jdbc.sql("delete from project_sub_categories where id=:id").param("id", subCategoryId).update();
        jdbc.sql("delete from project_categories where id=:id").param("id", categoryId).update();
    }

    @Test
    void persistsReadsHistoryAndRevertsThroughRealHttp() throws Exception {
        var empty = call("GET", "/files", null, null, null, ownerId);
        assertThat(empty.statusCode()).isEqualTo(200);
        assertThat(empty.headers().firstValue("ETag")).contains("\"0\"");
        assertThat(data(empty).get("files").size()).isZero();
        var content = "export default function Home(){return 'Tiếng Việt " + projectId + "'}";
        var saved = save(0, "app/(marketing)/[slug]/page.tsx", content, null);
        assertThat(saved.statusCode()).isEqualTo(200);
        assertThat(saved.headers().firstValue("ETag")).contains("\"1\"");
        assertThat(data(saved).get("files").get(0).has("content")).isFalse();
        var tree = data(call("GET", "/files?include=content", null, null, null, ownerId));
        assertThat(tree.get("files").get(0).get("content").asString()).isEqualTo(content);
        assertThat(tree.get("files").get(0).get("sha256").asString()).isEqualTo(FilePolicy.sha256(FilePolicy.text(content)));
        var history = data(call("GET", "/files/revisions", null, null, null, ownerId));
        assertThat(history.get("items").get(0).get("revision").asLong()).isEqualTo(1);
        assertThat(history.get("totalElements").asLong()).isEqualTo(2);
        var reverted = call("POST", "/files/revert", Map.of("toRevision", 0), 1L, null, ownerId);
        assertThat(reverted.statusCode()).isEqualTo(200);
        assertThat(data(reverted).get("revision").asLong()).isEqualTo(2);
        assertThat(data(call("GET", "/files", null, null, null, ownerId)).get("files").size()).isZero();
        assertThat(data(call("GET", "/files?revision=1&include=content", null, null, null, ownerId))
                .get("files").get(0).get("content").asString()).isEqualTo(content);
        assertThat(jdbc.sql("select row_version from projects where id=:id").param("id", projectId).query(Long.class).single()).isZero();
    }

    @Test
    void badBatchCannotPartiallyApply() throws Exception {
        assertThat(save(0, "a.ts", "original-" + projectId, null).statusCode()).isEqualTo(200);
        var response = call("POST", "/files/changes", Map.of("changes", List.of(
                Map.of("op", "PUT", "path", "a.ts", "content", "replacement"),
                Map.of("op", "DELETE", "path", "missing.ts")), "source", Map.of("kind", "USER")), 1L, null, ownerId);
        assertThat(response.statusCode()).isEqualTo(404);
        var tree = data(call("GET", "/files?include=content", null, null, null, ownerId));
        assertThat(tree.get("revision").asLong()).isEqualTo(1);
        assertThat(tree.get("files").get(0).get("content").asString()).startsWith("original-");
    }

    @Test
    void storageFailureAfterFirstUploadRollsBackManifestAndCounter() throws Exception {
        var firstContent = "first-" + projectId;
        var secondContent = "second-" + projectId;
        var hashes = List.of(FilePolicy.sha256(FilePolicy.text(firstContent)), FilePolicy.sha256(FilePolicy.text(secondContent)))
                .stream().sorted().toList();
        doThrow(new FilesException(503, "FILES_STORAGE_UNAVAILABLE", "Injected storage failure"))
                .when(blobs).putIfAbsent(eq(hashes.get(1)), any());
        var response = call("POST", "/files/changes", Map.of("changes", List.of(
                Map.of("op", "PUT", "path", "a.ts", "content", firstContent),
                Map.of("op", "PUT", "path", "b.ts", "content", secondContent)),
                "source", Map.of("kind", "USER")), 0L, null, ownerId);
        assertThat(response.statusCode()).isEqualTo(503);
        var tree = data(call("GET", "/files", null, null, null, ownerId));
        assertThat(tree.get("revision").asLong()).isZero();
        assertThat(tree.get("files").size()).isZero();
        assertThat(jdbc.sql("select count(*) from project_file_revisions where project_id=:id")
                .param("id", projectId).query(Long.class).single()).isZero();
        assertThat(blobs.read(hashes.get(0))).isNotEmpty();
        assertThat(gc.collect(new FileBlobStorage.Blob(hashes.get(0), now), Duration.ofHours(24))).isFalse();
        assertThat(gc.collect(new FileBlobStorage.Blob(hashes.get(0), now.minus(Duration.ofHours(25))),
                Duration.ofHours(24))).isTrue();
    }

    @Test
    void onlyOneConcurrentBatchWithTheSameRevisionSucceeds() throws Exception {
        var responses = race(
                () -> save(0, "a.ts", "first-" + projectId, null),
                () -> save(0, "a.ts", "second-" + projectId, null));
        assertThat(responses.stream().map(HttpResponse::statusCode)).containsExactlyInAnyOrder(200, 412);
        var stale = responses.stream().filter(response -> response.statusCode() == 412).findFirst().orElseThrow();
        assertThat(stale.headers().firstValue("ETag")).contains("\"1\"");
        assertThat(mapper.readTree(stale.body()).get("code").asString()).isEqualTo("FILES_STALE_REVISION");
        assertThat(store.load(projectId).revision).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from project_file_revisions where project_id=:id and revision=1")
                .param("id", projectId).query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void onlyOneConcurrentLeaseAcquisitionSucceeds() throws Exception {
        var responses = race(() -> acquire("run-a"), () -> acquire("run-b"));
        assertThat(responses.stream().map(HttpResponse::statusCode)).containsExactlyInAnyOrder(201, 423);
        var blocked = responses.stream().filter(response -> response.statusCode() == 423).findFirst().orElseThrow();
        assertThat(data(blocked).has("leaseId")).isFalse();
        assertThat(jdbc.sql("select count(*) from project_file_run_checkpoints where project_id=:id")
                .param("id", projectId).query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void leaseRenewalReleaseAndRestorePointAreDurable() throws Exception {
        var acquired = acquire("run-" + projectId);
        assertThat(acquired.statusCode()).isEqualTo(201);
        var leaseId = UUID.fromString(data(acquired).get("leaseId").asString());
        assertThat(data(acquired).get("baseRevision").asLong()).isZero();
        assertThat(data(call("GET", "/lease", null, null, null, ownerId)).has("leaseId")).isFalse();
        assertThat(save(0, "a.ts", "owned-" + projectId, null).statusCode()).isEqualTo(423);
        assertThat(save(0, "a.ts", "owned-" + projectId, leaseId).statusCode()).isEqualTo(200);
        when(clock.instant()).thenReturn(now.plusSeconds(40));
        var renewed = call("PUT", "/lease/" + leaseId, null, null, null, ownerId);
        assertThat(renewed.statusCode()).isEqualTo(200);
        assertThat(Instant.parse(data(renewed).get("expiresAt").asString())).isEqualTo(now.plusSeconds(160));
        assertThat(call("DELETE", "/lease/" + UUID.randomUUID(), null, null, null, ownerId).statusCode()).isEqualTo(204);
        assertThat(data(call("GET", "/lease", null, null, null, ownerId)).isNull()).isFalse();
        assertThat(call("DELETE", "/lease/" + leaseId, null, null, null, ownerId).statusCode()).isEqualTo(204);
        assertThat(call("DELETE", "/lease/" + leaseId, null, null, null, ownerId).statusCode()).isEqualTo(204);
        assertThat(call("PUT", "/lease/" + leaseId, null, null, null, ownerId).statusCode()).isEqualTo(404);
        assertThat(jdbc.sql("select end_revision from project_file_run_checkpoints where project_id=:id")
                .param("id", projectId).query(Long.class).single()).isEqualTo(1);
        // Same run ID cannot move its original undo point forward.
        assertThat(data(acquire("run-" + projectId)).get("baseRevision").asLong()).isZero();
    }

    @Test
    void expiredLeaseRecordsEndBeforeTheNextUnleasedWrite() throws Exception {
        var acquired = acquire("crashed-run");
        var leaseId = UUID.fromString(data(acquired).get("leaseId").asString());
        assertThat(save(0, "a.ts", "before-expiry-" + projectId, leaseId).statusCode()).isEqualTo(200);
        when(clock.instant()).thenReturn(now.plusSeconds(121));
        assertThat(save(1, "a.ts", "after-expiry-" + projectId, null).statusCode()).isEqualTo(200);
        assertThat(data(call("GET", "/lease", null, null, null, ownerId)).isNull()).isTrue();
        assertThat(jdbc.sql("select end_revision from project_file_run_checkpoints where project_id=:id")
                .param("id", projectId).query(Long.class).single()).isEqualTo(1);
        assertThat(call("PUT", "/lease/" + leaseId, null, null, null, ownerId).statusCode()).isEqualTo(404);
    }

    @Test
    void scheduledExpiryCanCommitWithoutUserAuthentication() throws Exception {
        acquire("background-expiry");
        when(clock.instant()).thenReturn(now.plusSeconds(121));
        assertThat(store.expiredLeaseProjects(clock.instant())).contains(projectId);
        handler.expireLease(projectId);
        assertThat(store.load(projectId).leaseId).isNull();
        assertThat(jdbc.sql("select end_revision from project_file_run_checkpoints where project_id=:id")
                .param("id", projectId).query(Long.class).single()).isZero();
    }

    @Test
    void publishingPinsSourceAndLaterEditsDoNotLeakToPublicReaders() throws Exception {
        jdbc.sql("update projects set source_visibility='PUBLIC' where id=:id").param("id", projectId).update();
        var viewer = UUID.randomUUID();
        assertThat(save(0, "a.ts", "published-" + projectId, null).statusCode()).isEqualTo(200);
        assertThat(call("POST", "/publish", Map.of("visibility", "PUBLIC"), 0L, null, ownerId).statusCode()).isEqualTo(200);
        assertThat(store.load(projectId).publishedRevision).isEqualTo(1);
        assertThat(save(1, "a.ts", "working-copy-" + projectId, null).statusCode()).isEqualTo(200);
        var publicTree = data(call("GET", "/files?include=content", null, null, null, viewer));
        assertThat(publicTree.get("revision").asLong()).isEqualTo(1);
        assertThat(publicTree.get("files").get(0).get("content").asString()).startsWith("published-");
        assertThat(call("GET", "/files?revision=2", null, null, null, viewer).statusCode()).isEqualTo(404);
        assertThat(call("GET", "/files/revisions", null, null, null, viewer).statusCode()).isEqualTo(403);
        assertThat(call("POST", "/publish", null, 1L, null, ownerId).statusCode()).isEqualTo(200);
        assertThat(store.load(projectId).publishedRevision).isEqualTo(2);
        assertThat(data(call("GET", "/files", null, null, null, viewer)).get("revision").asLong()).isEqualTo(2);
    }

    @Test
    void publishedWithoutPinDoesNotImplicitlyPublishCurrentSource() throws Exception {
        jdbc.sql("update projects set status='PUBLISHED', visibility='PUBLIC', source_visibility='PUBLIC', published_at=now() where id=:id")
                .param("id", projectId).update();
        save(0, "a.ts", "unpublished-" + projectId, null);
        assertThat(store.load(projectId).publishedRevision).isNull();
        assertThat(call("GET", "/files", null, null, null, UUID.randomUUID()).statusCode()).isEqualTo(404);
    }

    @Test
    void pinFailureRollsBackCatalogPublication() throws Exception {
        doThrow(new FilesException(503, "FILES_STORAGE_UNAVAILABLE", "Injected pin failure"))
                .when(handler).pinPublished(any());
        assertThat(call("POST", "/publish", Map.of("visibility", "PUBLIC"), 0L, null, ownerId).statusCode()).isEqualTo(503);
        assertThat(jdbc.sql("select status from projects where id=:id").param("id", projectId).query(String.class).single())
                .isEqualTo("DRAFT");
        assertThat(jdbc.sql("select row_version from projects where id=:id").param("id", projectId).query(Long.class).single())
                .isZero();
        assertThat(store.load(projectId).publishedRevision).isNull();
    }

    @Test
    void hiddenSourceRemainsHiddenAndUnlistedMatchesCatalogDirectLookupRules() throws Exception {
        save(0, "a.ts", "private-source-" + projectId, null);
        call("POST", "/publish", Map.of("visibility", "UNLISTED"), 0L, null, ownerId);
        var viewer = UUID.randomUUID();
        assertThat(call("GET", "/files", null, null, null, viewer).statusCode()).isEqualTo(404);
        jdbc.sql("update projects set source_visibility='PUBLIC' where id=:id").param("id", projectId).update();
        assertThat(call("GET", "/files", null, null, null, viewer).statusCode()).isEqualTo(200);
        assertThat(call("POST", "/lease", Map.of("holder", "ai-service", "runId", "other", "ttlSeconds", 120),
                null, null, viewer).statusCode()).isEqualTo(403);
    }

    @Test
    void softDeletedProjectCannotBeAccessedButItsExpiredRunIsFinalized() throws Exception {
        acquire("deleted-project-run");
        jdbc.sql("update projects set deleted_at=now() where id=:id").param("id", projectId).update();
        assertThat(call("GET", "/files", null, null, null, ownerId).statusCode()).isEqualTo(404);
        when(clock.instant()).thenReturn(now.plusSeconds(121));
        handler.expireLease(projectId);
        assertThat(store.load(projectId).leaseId).isNull();
        assertThat(jdbc.sql("select end_revision from project_file_run_checkpoints where project_id=:id")
                .param("id", projectId).query(Long.class).single()).isZero();
    }

    @Test
    void enforcesIdentityOwnershipStateAndValidation() throws Exception {
        assertThat(call("GET", "/files", null, null, null, null).statusCode()).isEqualTo(401);
        assertThat(call("GET", "/files", null, null, null, UUID.randomUUID()).statusCode()).isEqualTo(404);
        assertThat(save(0, "../a.ts", "unsafe", null).statusCode()).isEqualTo(400);
        assertThat(save(0, "a.ts", "x".repeat(FilePolicy.MAX_FILE_BYTES + 1), null).statusCode()).isEqualTo(413);
        assertThat(call("POST", "/files/revert", Map.of(), 0L, null, ownerId).statusCode()).isEqualTo(400);
        assertThat(call("POST", "/files/changes", Map.of("changes", List.of(), "source", Map.of("kind", "USER")),
                null, null, ownerId).statusCode()).isEqualTo(400);
        jdbc.sql("update projects set status='ARCHIVED' where id=:id").param("id", projectId).update();
        assertThat(save(0, "a.ts", "archived", null).statusCode()).isEqualTo(409);
        assertThat(acquire("archived").statusCode()).isEqualTo(409);
    }

    @Test
    void gcProtectsHistoricalSnapshotsEvenAfterAFileIsDeleted() throws Exception {
        var content = "retained-history-" + projectId;
        var hash = FilePolicy.sha256(FilePolicy.text(content));
        save(0, "a.ts", content, null);
        assertThat(call("POST", "/files/changes", Map.of("changes", List.of(Map.of("op", "DELETE", "path", "a.ts")),
                "source", Map.of("kind", "USER")), 1L, null, ownerId).statusCode()).isEqualTo(200);
        assertThat(gc.collect(new FileBlobStorage.Blob(hash, now.minus(Duration.ofDays(2))), Duration.ofHours(24))).isFalse();
        assertThat(blobs.read(hash)).isEqualTo(FilePolicy.text(content));
    }

    @Test
    void gcCannotDeleteABlobWhileAWriterHoldsItsTransactionLock() throws Exception {
        var content = "orphan-" + projectId;
        var hash = FilePolicy.sha256(FilePolicy.text(content));
        blobs.putIfAbsent(hash, FilePolicy.text(content));
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var writer = pool.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                store.lockBlobs(List.of(hash));
                locked.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for test."); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
                return null;
            }));
            try {
                assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(gc.collect(new FileBlobStorage.Blob(hash, now.minus(Duration.ofDays(2))), Duration.ofHours(24))).isFalse();
            } finally { release.countDown(); }
            writer.get(10, TimeUnit.SECONDS);
        }
        assertThat(gc.collect(new FileBlobStorage.Blob(hash, now.minus(Duration.ofDays(2))), Duration.ofHours(24))).isTrue();
    }

    @Test
    void sourceBucketCannotBeReadAnonymously() throws Exception {
        var content = "private-" + projectId;
        var hash = FilePolicy.sha256(FilePolicy.text(content));
        save(0, "a.ts", content, null);
        var uri = URI.create("http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000)
                + "/project-files-test/blobs/sha256/" + hash);
        assertThat(http.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
    }

    @Test
    void documentsAllEightOperationsAndLeaseSuccessStatuses() throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        var paths = mapper.readTree(response.body()).get("paths");
        assertThat(paths.get("/{projectId}/files").has("get")).isTrue();
        assertThat(paths.get("/{projectId}/files/changes").has("post")).isTrue();
        assertThat(paths.get("/{projectId}/files/revisions").has("get")).isTrue();
        assertThat(paths.get("/{projectId}/files/revert").has("post")).isTrue();
        assertThat(paths.get("/{projectId}/lease").has("get")).isTrue();
        assertThat(paths.get("/{projectId}/lease").get("post").get("responses").has("201")).isTrue();
        assertThat(paths.get("/{projectId}/lease/{leaseId}").has("put")).isTrue();
        assertThat(paths.get("/{projectId}/lease/{leaseId}").get("delete").get("responses").has("204")).isTrue();
    }

    private HttpResponse<String> save(long revision, String path, String content, UUID lease) throws Exception {
        return call("POST", "/files/changes", Map.of("changes", List.of(Map.of("op", "PUT", "path", path, "content", content)),
                "source", Map.of("kind", "USER")), revision, lease, ownerId);
    }
    private HttpResponse<String> acquire(String runId) throws Exception {
        return call("POST", "/lease", Map.of("holder", "ai-service", "runId", runId, "ttlSeconds", 120),
                null, null, ownerId);
    }
    private HttpResponse<String> call(String method, String suffix, Object body, Long revision, UUID lease, UUID user) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/" + projectId + suffix))
                .timeout(Duration.ofSeconds(20)).header("Accept", "application/json");
        if (user != null) builder.header("X-User-ID", user.toString());
        if (revision != null) builder.header("If-Match", "\"" + revision + "\"");
        if (lease != null) builder.header("Lease-Id", lease.toString());
        if (body != null) builder.header("Content-Type", "application/json");
        return http.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode data(HttpResponse<String> response) throws Exception { return mapper.readTree(response.body()).get("data"); }
    private List<HttpResponse<String>> race(Callable<HttpResponse<String>> first, Callable<HttpResponse<String>> second) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var futures = List.of(first, second).stream().map(action -> pool.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Race barrier timed out.");
                return action.call();
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(futures.get(0).get(25, TimeUnit.SECONDS), futures.get(1).get(25, TimeUnit.SECONDS));
        }
    }
}
