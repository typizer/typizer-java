package ru.typizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ru.typizer.models.CatalogEntry;
import ru.typizer.models.CatalogEntryBrief;
import ru.typizer.models.CatalogFavorite;
import ru.typizer.models.CatalogPublisher;
import ru.typizer.models.CatalogReport;
import ru.typizer.models.CatalogVersionBrief;
import ru.typizer.models.Typizer;

/**
 * The schema catalog: {@code client.catalog()}. A reference ({@code ref}) is a slug of an official
 * schema ({@code schet-faktura}), {@code publisher/slug} of a community schema
 * ({@code acme/akt-sverki}) or an entry id ({@code cat_…}), optionally with a version
 * ({@code schet-faktura@1}, {@code acme/akt-sverki@1.2.0}).
 */
public final class CatalogService {
    private final Core core;
    private final Publishers publishers;

    CatalogService(Core core) {
        this.core = core;
        this.publishers = new Publishers(core);
    }

    /** {@code GET /v1/catalog}. */
    public Page<CatalogEntryBrief> list() {
        return list(null, RequestOptions.NONE);
    }

    public Page<CatalogEntryBrief> list(CatalogListParams params) {
        return list(params, RequestOptions.NONE);
    }

    public Page<CatalogEntryBrief> list(CatalogListParams params, RequestOptions options) {
        Map<String, Object> q = params != null ? params.query : new LinkedHashMap<String, Object>();
        return RunService.page(core, Op.listCatalog, new String[0], q, "cursor", options, CatalogEntryBrief::new);
    }

    /**
     * An entry with its schema: {@code GET /v1/catalog/{entry}}, or
     * {@code GET /v1/catalog/{publisher}/{slug}} for a community reference. A version in the
     * reference ({@code @1}) goes to {@code ?version=}.
     */
    public CatalogEntry get(String ref) {
        return get(ref, null, RequestOptions.NONE);
    }

    public CatalogEntry get(String ref, String version) {
        return get(ref, version, RequestOptions.NONE);
    }

    public CatalogEntry get(String ref, String version, RequestOptions options) {
        Ref r = Ref.parse(ref, version);
        Core.Call call = r.publisher != null
                ? new Core.Call(Op.getCommunityCatalogEntry, r.publisher, r.slug)
                : new Core.Call(Op.getCatalogEntry, r.slug);
        return core.json(call.query("version", r.version).options(options), CatalogEntry::new);
    }

    /** {@code GET /v1/catalog/{entry}/versions}: every version, newest first (one list, no pages). */
    public List<CatalogVersionBrief> versions(String ref) {
        return versions(ref, RequestOptions.NONE);
    }

    @SuppressWarnings("unchecked")
    public List<CatalogVersionBrief> versions(String ref, RequestOptions options) {
        Map<String, Object> json = core.json(new Core.Call(Op.listCatalogVersions, entry(ref, options)).options(options));
        List<CatalogVersionBrief> out = new ArrayList<>();
        Object data = json.get("data");
        if (data instanceof List) {
            for (Object item : (List<?>) data) if (item instanceof Map) out.add(new CatalogVersionBrief((Map<String, Object>) item));
        }
        return Collections.unmodifiableList(out);
    }

    /** {@code POST /v1/catalog/{entry}/install}: a typizer that follows the catalog schema (and its updates). */
    public Typizer install(String ref) {
        return install(ref, null, null, RequestOptions.NONE);
    }

    /** {@code version} ({@code "1"}, {@code "1.2.0"}) and {@code name} may be null; a version in {@code ref} also works. */
    public Typizer install(String ref, String version, String name, RequestOptions options) {
        return copy(Op.installCatalogEntry, ref, version, name, options);
    }

    /** {@code POST /v1/catalog/{entry}/fork}: an independent copy of the schema to edit. */
    public Typizer fork(String ref) {
        return fork(ref, null, null, RequestOptions.NONE);
    }

    public Typizer fork(String ref, String version, String name, RequestOptions options) {
        return copy(Op.forkCatalogEntry, ref, version, name, options);
    }

    private Typizer copy(Op op, String ref, String version, String name, RequestOptions options) {
        Ref r = Ref.parse(ref, version);
        Map<String, Object> body = new LinkedHashMap<>();
        if (r.version != null) body.put("version", r.version);
        if (name != null) body.put("name", name);
        return core.json(new Core.Call(op, entry(ref, options)).jsonBody(body).options(options), Typizer::new);
    }

    /** {@code PUT /v1/catalog/{entry}/favorite}. */
    public CatalogFavorite favorite(String ref) {
        return favorite(ref, RequestOptions.NONE);
    }

    public CatalogFavorite favorite(String ref, RequestOptions options) {
        return core.json(new Core.Call(Op.favoriteCatalogEntry, entry(ref, options)).options(options), CatalogFavorite::new);
    }

    /** {@code DELETE /v1/catalog/{entry}/favorite}. */
    public CatalogFavorite unfavorite(String ref) {
        return unfavorite(ref, RequestOptions.NONE);
    }

    public CatalogFavorite unfavorite(String ref, RequestOptions options) {
        return core.json(new Core.Call(Op.unfavoriteCatalogEntry, entry(ref, options)).options(options), CatalogFavorite::new);
    }

    /**
     * {@code POST /v1/catalog/{entry}/reports}: report an entry — {@code reason} is {@code pii},
     * {@code plagiarism}, {@code broken}, {@code spam} or {@code other}.
     */
    public CatalogReport report(String ref, String reason) {
        return report(ref, reason, null, RequestOptions.NONE);
    }

    public CatalogReport report(String ref, String reason, String details, RequestOptions options) {
        if (reason == null) throw new IllegalArgumentException("reason is required");
        Ref r = Ref.parse(ref, null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", reason);
        if (details != null) body.put("details", details);
        if (r.version != null) body.put("version", r.version);
        return core.json(new Core.Call(Op.reportCatalogEntry, entry(ref, options)).jsonBody(body).options(options),
                CatalogReport::new);
    }

    /** Catalog publishers. */
    public Publishers publishers() {
        return publishers;
    }

    /**
     * The {@code {entry}} path parameter: an official slug or {@code cat_…} as is; a community
     * {@code publisher/slug} is looked up first (one {@code GET}) for its {@code cat_…} id.
     */
    private String entry(String ref, RequestOptions options) {
        Ref r = Ref.parse(ref, null);
        if (r.publisher == null) return r.slug;
        return core.json(new Core.Call(Op.getCommunityCatalogEntry, r.publisher, r.slug).options(options), CatalogEntry::new).id();
    }

    /** {@code [publisher/]slug[@version]}. */
    static final class Ref {
        final String publisher;
        final String slug;
        final String version;

        private Ref(String publisher, String slug, String version) {
            this.publisher = publisher;
            this.slug = slug;
            this.version = version;
        }

        static Ref parse(String ref, String version) {
            if (ref == null || ref.trim().isEmpty()) throw new IllegalArgumentException("ref must not be empty");
            String base = ref.trim();
            String v = null;
            int at = base.lastIndexOf('@');
            if (at >= 0) {
                v = base.substring(at + 1);
                base = base.substring(0, at);
                if (v.isEmpty() || base.isEmpty()) throw new IllegalArgumentException("Bad catalog reference: " + ref);
            }
            if (version != null && v != null && !version.equals(v))
                throw new IllegalArgumentException("Two versions: " + ref + " and " + version);
            if (version != null) v = version;
            int slash = base.indexOf('/');
            if (slash < 0) return new Ref(null, base, v);
            if (slash == 0 || slash == base.length() - 1 || base.indexOf('/', slash + 1) >= 0)
                throw new IllegalArgumentException("Bad catalog reference: " + ref + " (expected publisher/slug)");
            return new Ref(base.substring(0, slash), base.substring(slash + 1), v);
        }
    }

    /** {@code client.catalog().publishers()}. */
    public static final class Publishers {
        private final Core core;

        Publishers(Core core) {
            this.core = core;
        }

        /** {@code GET /v1/catalog/publishers/{slug}}. */
        public CatalogPublisher get(String slug) {
            return get(slug, RequestOptions.NONE);
        }

        public CatalogPublisher get(String slug, RequestOptions options) {
            return core.json(new Core.Call(Op.getCatalogPublisher, slug).options(options), CatalogPublisher::new);
        }
    }
}
