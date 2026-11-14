package ru.typizer;

import java.nio.file.Path;

import ru.typizer.models.Deleted;
import ru.typizer.models.File;

/**
 * Files: {@code client.files()}. Upload a document once and run it by {@code file_id} many times
 * ({@code TypizerFile.fromId}); for a single run, pass the file to {@code extract} directly instead.
 */
public final class FileService {
    private final Core core;

    FileService(Core core) {
        this.core = core;
    }

    /** {@code POST /v1/files}. */
    public File upload(Path path) {
        return upload(TypizerFile.fromPath(path), RequestOptions.NONE);
    }

    public File upload(TypizerFile file) {
        return upload(file, RequestOptions.NONE);
    }

    public File upload(TypizerFile file, RequestOptions options) {
        if (file == null || !file.isUpload())
            throw new IllegalArgumentException("files().upload() takes a local document: TypizerFile.fromPath/fromBytes/fromStream");
        Core.checkSize(file);
        Multipart form = new Multipart().file("file", file);
        return core.json(new Core.Call(Op.createFile).body(form).options(options), File::new);
    }

    /** {@code GET /v1/files/{id}}. */
    public File get(String id) {
        return get(id, RequestOptions.NONE);
    }

    public File get(String id, RequestOptions options) {
        return core.json(new Core.Call(Op.getFile, id).options(options), File::new);
    }

    /** {@code DELETE /v1/files/{id}}. */
    public Deleted delete(String id) {
        return delete(id, RequestOptions.NONE);
    }

    public Deleted delete(String id, RequestOptions options) {
        return core.json(new Core.Call(Op.deleteFile, id).options(options), Deleted::new);
    }

    /** {@code GET /v1/files/{id}/content}: the document itself. */
    public Download content(String id) {
        return content(id, RequestOptions.NONE);
    }

    public Download content(String id, RequestOptions options) {
        return core.download(new Core.Call(Op.getFileContent, id).options(options));
    }
}
