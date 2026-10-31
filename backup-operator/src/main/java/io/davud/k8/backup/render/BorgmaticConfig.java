package io.davud.k8.backup.render;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.davud.k8.backup.api.Backup;
import io.davud.k8.backup.api.BackupJob;
import io.davud.k8.backup.api.BackupRepository;
import io.davud.k8.backup.api.RetentionPolicy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the borgmatic config for one job. Secrets never land in it: borgmatic
 * interpolates the ${...} references from the pod's env at runtime.
 */
public final class BorgmaticConfig {
    public static final String DATA_MOUNT = "/data";
    public static final RetentionPolicy DEFAULT_RETENTION = new RetentionPolicy(7, 4, 6);
    // BackupOSError, BackupPermissionError, BackupIOError, BackupFileNotFoundError
    static final List<Integer> BORG_ERRORS = List.of(104, 105, 106, 107);

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));

    private BorgmaticConfig() {
    }

    public static String render(BackupRepository repo, Backup backup, BackupJob job) {
        var config = new LinkedHashMap<String, Object>();
        config.put("repositories", List.of(ordered(
                "path", Names.repositoryPath(backup, job),
                "label", job.getName())));
        // Pod hostnames are random, so name archives after the job instead.
        config.put("archive_name_format", job.getName() + "-{now:%Y-%m-%dT%H:%M:%S}");

        switch (job.getType()) {
            case POSTGRES_DUMP -> {
                var pg = job.getPostgres();
                var db = new LinkedHashMap<String, Object>();
                db.put("name", pg.getDatabase() != null ? pg.getDatabase() : "${PGDATABASE}");
                db.put("hostname", pg.getCnpgCluster() + "-rw");
                db.put("username", "${PGUSER}");
                db.put("password", "${PGPASSWORD}");
                db.put("format", "custom");
                config.put("postgresql_databases", List.of(db));
            }
            case FILESYSTEM -> {
                var subPath = job.getFilesystem().getSubPath();
                var dir = subPath == null || subPath.isBlank()
                        ? DATA_MOUNT
                        : DATA_MOUNT + "/" + subPath.replaceAll("^/+|/+$", "");
                config.put("source_directories", List.of(dir));
            }
        }

        // borg reports unreadable/missing/IO-failed files as warnings and borgmatic
        // treats warnings as success, so a job that can't read its PVC would
        // "succeed" with an empty archive. Needs BORG_EXIT_CODES=modern (set in the image).
        config.put("borg_exit_codes", BORG_ERRORS.stream()
                .map(code -> ordered("code", code, "treat_as", "error"))
                .toList());

        var retention = retention(repo, job);
        putIfSet(config, "keep_daily", retention.getKeepDaily());
        putIfSet(config, "keep_weekly", retention.getKeepWeekly());
        putIfSet(config, "keep_monthly", retention.getKeepMonthly());

        try {
            return YAML.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("rendering borgmatic config", e);
        }
    }

    static RetentionPolicy retention(BackupRepository repo, BackupJob job) {
        if (job.getRetention() != null) {
            return job.getRetention();
        }
        if (repo.getSpec().getRetention() != null) {
            return repo.getSpec().getRetention();
        }
        return DEFAULT_RETENTION;
    }

    // Map.of iteration order changes between JVM runs, which would rewrite every ConfigMap on restart.
    private static Map<String, Object> ordered(String k1, Object v1, String k2, Object v2) {
        var map = new LinkedHashMap<String, Object>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }

    private static void putIfSet(Map<String, Object> config, String key, Integer value) {
        if (value != null) {
            config.put(key, value);
        }
    }
}
