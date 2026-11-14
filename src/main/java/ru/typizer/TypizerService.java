package ru.typizer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ru.typizer.models.CatalogUpdate;
import ru.typizer.models.Deleted;
import ru.typizer.models.Typizer;
import ru.typizer.models.TypizerDraft;
import ru.typizer.models.TypizerVersion;

/** Typizers — schemas saved once and run by id: {@code client.typizers()}. */
public final class TypizerService {
    private final Core core;
    private final Versions versions;
    private final CatalogUpdates catalogUpdate;

    TypizerService(Core core) {
        this.core = core;
        this.versions = new Versions(core);
        this.catalogUpdate = new CatalogUpdates(core);
    }

    /** {@code POST /v1/typizers} with a name and a schema. */
    public Typizer create(String name, Object schema) {
        return create(TypizerParams.builder().name(name).schema(schema).build(), RequestOptions.NONE);
    }

    /** {@code POST /v1/typizers}. */
    public Typizer create(TypizerParams params) {
        return create(params, RequestOptions.NONE);
    }

    public Typizer create(TypizerParams params, RequestOptions options) {
        if (!params.body.containsKey("name") || !params.body.containsKey("schema"))
            throw new IllegalArgumentException("A typizer needs name and schema");
        return core.json(new Core.Call(Op.createTypizer).jsonBody(params.body).options(options), Typizer::new);
    }

    /** {@code GET /v1/typizers/{id}}. */
    public Typizer get(String id) {
        return get(id, RequestOptions.NONE);
    }

    public Typizer get(String id, RequestOptions options) {
        return core.json(new Core.Call(Op.getTypizer, id).options(options), Typizer::new);
    }

    /** {@code PATCH /v1/typizers/{id}}: changes only the fields set in {@code params} (a new version if the schema changes). */
    public Typizer update(String id, TypizerParams params) {
        return update(id, params, RequestOptions.NONE);
    }

    public Typizer update(String id, TypizerParams params, RequestOptions options) {
        return core.json(new Core.Call(Op.updateTypizer, id).jsonBody(params.body).options(options), Typizer::new);
    }

    /** {@code DELETE /v1/typizers/{id}}. */
    public Deleted delete(String id) {
        return delete(id, RequestOptions.NONE);
    }

    public Deleted delete(String id, RequestOptions options) {
        return core.json(new Core.Call(Op.deleteTypizer, id).options(options), Deleted::new);
    }

    /** {@code GET /v1/typizers}. */
    public Page<Typizer> list() {
        return list(ListParams.none(), RequestOptions.NONE);
    }

    public Page<Typizer> list(ListParams params) {
        return list(params, RequestOptions.NONE);
    }

    public Page<Typizer> list(ListParams params, RequestOptions options) {
        return RunService.page(core, Op.listTypizers, new String[0], listQuery(params), "starting_after", options, Typizer::new);
    }

    /** {@code POST /v1/typizers/generate}: a draft schema from a description of the document. Not saved. */
    public TypizerDraft generate(String description) {
        return generate(description, null, RequestOptions.NONE);
    }

    /** A draft schema from a description and/or a sample file ({@code file_…}); either may be null. */
    public TypizerDraft generate(String description, String fileId) {
        return generate(description, fileId, RequestOptions.NONE);
    }

    public TypizerDraft generate(String description, String fileId, RequestOptions options) {
        if (description == null && fileId == null) throw new IllegalArgumentException("Pass a description, a fileId or both");
        Map<String, Object> body = new LinkedHashMap<>();
        if (description != null) body.put("description", description);
        if (fileId != null) body.put("file_id", fileId);
        return core.json(new Core.Call(Op.generateTypizer).jsonBody(body).options(options), TypizerDraft::new);
    }

    /** {@code GET /v1/typizers/{id}/xsd}: XSD of the XML output (for 1С exchange rules). */
    public Download xsd(String id) {
        return xsd(id, RequestOptions.NONE);
    }

    public Download xsd(String id, RequestOptions options) {
        return core.download(new Core.Call(Op.getTypizerXsd, id).options(options));
    }

    /** Saved versions of a typizer's schema. */
    public Versions versions() {
        return versions;
    }

    /** Updates from the catalog for a typizer installed or forked from it. */
    public CatalogUpdates catalogUpdate() {
        return catalogUpdate;
    }

    static Map<String, Object> listQuery(ListParams params) {
        Map<String, Object> q = new LinkedHashMap<>();
        if (params != null) {
            if (params.limit != null) q.put("limit", params.limit);
            if (params.startingAfter != null) q.put("starting_after", params.startingAfter);
        }
        return q;
    }

    /** {@code client.typizers().versions()}. */
    public static final class Versions {
        private final Core core;

        Versions(Core core) {
            this.core = core;
        }

        /** {@code GET /v1/typizers/{id}/versions}. */
        public Page<TypizerVersion> list(String typizerId) {
            return list(typizerId, ListParams.none(), RequestOptions.NONE);
        }

        public Page<TypizerVersion> list(String typizerId, ListParams params, RequestOptions options) {
            return RunService.page(core, Op.listTypizerVersions, new String[] {typizerId}, listQuery(params), "starting_after",
                    options, TypizerVersion::new);
        }

        /** {@code GET /v1/typizers/{id}/versions/{n}}. */
        public TypizerVersion get(String typizerId, int version) {
            return get(typizerId, version, RequestOptions.NONE);
        }

        public TypizerVersion get(String typizerId, int version, RequestOptions options) {
            return core.json(new Core.Call(Op.getTypizerVersion, typizerId, String.valueOf(version)).options(options),
                    TypizerVersion::new);
        }
    }

    /** {@code client.typizers().catalogUpdate()}. */
    public static final class CatalogUpdates {
        private final Core core;

        CatalogUpdates(Core core) {
            this.core = core;
        }

        /** {@code GET /v1/typizers/{id}/catalog_update}: what the latest (or {@code version}) catalog version changes. */
        public CatalogUpdate get(String typizerId) {
            return get(typizerId, null, RequestOptions.NONE);
        }

        public CatalogUpdate get(String typizerId, String version, RequestOptions options) {
            return core.json(new Core.Call(Op.getTypizerCatalogUpdate, typizerId).query("version", version).options(options),
                    CatalogUpdate::new);
        }

        /** {@code POST /v1/typizers/{id}/catalog_update}: moves the typizer to {@code version}. */
        public Typizer apply(String typizerId, String version) {
            return apply(typizerId, version, null, RequestOptions.NONE);
        }

        /** For a forked typizer, {@code paths} picks which changes to take; null — all. */
        public Typizer apply(String typizerId, String version, List<String> paths, RequestOptions options) {
            if (version == null) throw new IllegalArgumentException("version is required");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("version", version);
            if (paths != null) body.put("paths", new ArrayList<>(paths));
            return core.json(new Core.Call(Op.applyTypizerCatalogUpdate, typizerId).jsonBody(body).options(options),
                    Typizer::new);
        }
    }
}
