package ru.typizer;

import java.util.LinkedHashMap;
import java.util.Map;

import ru.typizer.internal.Json;
import ru.typizer.models.Batch;
import ru.typizer.models.Format;

/** Batches — one typizer over many documents: {@code client.batches()}. */
public final class BatchService {
    private final Core core;

    BatchService(Core core) {
        this.core = core;
    }

    /** {@code POST /v1/batches}: local files go in one {@code multipart/form-data} request, file ids as JSON. */
    public Batch create(BatchParams params) {
        return create(params, RequestOptions.NONE);
    }

    public Batch create(BatchParams params, RequestOptions options) {
        Core.Call call = new Core.Call(Op.createBatch).options(options);
        if (!params.files.isEmpty()) {
            Multipart form = new Multipart().field("typizer_id", params.typizerId);
            if (params.format != null) form.field("format", params.format);
            if (params.mode != null) form.field("mode", params.mode);
            if (params.metadata != null) form.field("metadata", Json.write(params.metadata));
            for (TypizerFile f : params.files) {
                Core.checkSize(f);
                form.file("files", f);
            }
            call.body(form);
        } else {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("typizer_id", params.typizerId);
            body.put("file_ids", params.fileIds);
            if (params.format != null) body.put("format", params.format);
            if (params.mode != null) body.put("mode", params.mode);
            if (params.metadata != null) body.put("metadata", params.metadata);
            call.jsonBody(body);
        }
        return core.json(call, Batch::new);
    }

    /** {@code GET /v1/batches/{id}}. */
    public Batch get(String id) {
        return get(id, RequestOptions.NONE);
    }

    public Batch get(String id, RequestOptions options) {
        return core.json(new Core.Call(Op.getBatch, id).options(options), Batch::new);
    }

    /** {@code GET /v1/batches}. */
    public Page<Batch> list() {
        return list(ListParams.none(), RequestOptions.NONE);
    }

    public Page<Batch> list(ListParams params) {
        return list(params, RequestOptions.NONE);
    }

    public Page<Batch> list(ListParams params, RequestOptions options) {
        return RunService.page(core, Op.listBatches, new String[0], TypizerService.listQuery(params), "starting_after",
                options, Batch::new);
    }

    /** {@code GET /v1/batches/{id}/export}: all results in one {@code csv}, {@code xlsx} or {@code zip}. */
    public Download export(String id, Format format) {
        return export(id, ExtractParams.enumValue(format, "format"), RequestOptions.NONE);
    }

    /** {@code format}: {@code csv}, {@code xlsx}, {@code json} or {@code xml} (the last two as a zip). */
    public Download export(String id, String format, RequestOptions options) {
        return core.download(new Core.Call(Op.exportBatch, id).query("format", format).options(options));
    }
}
