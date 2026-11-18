package ru.typizer;

import java.util.List;

import ru.typizer.models.Deleted;
import ru.typizer.models.Validator;
import ru.typizer.models.ValidatorDetail;
import ru.typizer.models.ValidatorSuggestion;
import ru.typizer.models.ValidatorTestResult;
import ru.typizer.models.ValidatorUpdateResult;
import ru.typizer.models.ValidatorVersion;

/**
 * Your own field checks: {@code client.validators()}. A schema field references one as
 * {@code "x-typizer-check": "my/contract-no@1"}; a check written into the schema as an object needs
 * no call here — {@code extract} sends it as is.
 *
 * <pre>{@code
 * ValidatorTestResult r = client.validators().test(Arrays.asList("ДГ-2026/0153", "ДГ-26/153"), "my/contract-no@1");
 * for (ValidatorTestResult.Result x : r.results()) System.out.println(x.pass() + " " + x.message());
 * }</pre>
 */
public final class ValidatorService {
    private final Core core;

    ValidatorService(Core core) {
        this.core = core;
    }

    /** {@code GET /v1/validators}: newest first; {@link Page#autoPager()} goes through every page. */
    public Page<Validator> list() {
        return list(ListParams.none(), RequestOptions.NONE);
    }

    public Page<Validator> list(ListParams params) {
        return list(params, RequestOptions.NONE);
    }

    public Page<Validator> list(ListParams params, RequestOptions options) {
        return RunService.page(core, Op.listValidators, new String[0], TypizerService.listQuery(params), "starting_after",
                options, Validator::new);
    }

    /**
     * A validator with every version and the typizers that use it: {@code my/<slug>} (a version suffix
     * {@code @1} is dropped — the answer has every version) → {@code GET /v1/validators/my/{slug}};
     * {@code val_…} → {@code GET /v1/validators/{id}}.
     */
    public ValidatorDetail get(String ref) {
        return get(ref, RequestOptions.NONE);
    }

    public ValidatorDetail get(String ref, RequestOptions options) {
        return core.json(lookup(ref).options(options), ValidatorDetail::new);
    }

    /** Every version of a validator ({@code val_…} or {@code my/<slug>}), newest first: the {@code versions} of {@link #get}. */
    public List<ValidatorVersion> versions(String ref) {
        return versions(ref, RequestOptions.NONE);
    }

    public List<ValidatorVersion> versions(String ref, RequestOptions options) {
        return get(ref, options).versions();
    }

    /** {@code POST /v1/validators} with a name and a spec (a {@code Map}, JSON text or a tree node); version {@code 1.0.0}. */
    public Validator create(String name, Object spec) {
        return create(ValidatorParams.builder().name(name).spec(spec).build(), RequestOptions.NONE);
    }

    /** {@code POST /v1/validators}. A red test is a {@code BadRequestException} ({@code param} {@code tests/<i>}), a taken slug a {@code ConflictException}. */
    public Validator create(ValidatorParams params) {
        return create(params, RequestOptions.NONE);
    }

    public Validator create(ValidatorParams params, RequestOptions options) {
        if (params == null || !params.body.containsKey("name") || !params.body.containsKey("spec"))
            throw new IllegalArgumentException("A validator needs name and spec");
        return core.json(new Core.Call(Op.createValidator).jsonBody(params.body).options(options), Validator::new);
    }

    /**
     * {@code PATCH /v1/validators/{id}}: changes only the fields set in {@code params}; the answer says
     * which version this made ({@code bump}) and which typizers moved to it.
     */
    public ValidatorUpdateResult update(String id, ValidatorParams params) {
        return update(id, params, RequestOptions.NONE);
    }

    public ValidatorUpdateResult update(String id, ValidatorParams params, RequestOptions options) {
        if (params == null) throw new IllegalArgumentException("params are required");
        if (params.body.containsKey("slug")) throw new IllegalArgumentException("slug cannot be changed");
        return core.json(new Core.Call(Op.updateValidator, requireId(id)).jsonBody(params.body).options(options),
                ValidatorUpdateResult::new);
    }

    /**
     * {@code DELETE /v1/validators/{id}}: moves the validator to the archive; its versions stay with
     * runs and old typizer versions. While a typizer references it — {@code ConflictException} with
     * {@code param} {@code used_by}.
     */
    public Deleted archive(String id) {
        return archive(id, RequestOptions.NONE);
    }

    public Deleted archive(String id, RequestOptions options) {
        return core.json(new Core.Call(Op.deleteValidator, requireId(id)).options(options), Deleted::new);
    }

    /**
     * {@code POST /v1/validators/test}: runs a saved or official check ({@code my/contract-no@1},
     * {@code inn@1}) on 1–100 values — strings or numbers. Nothing is saved or billed.
     */
    public ValidatorTestResult test(List<?> values, String ref) {
        return test(ValidatorTestParams.builder().values(values).ref(ref).build(), RequestOptions.NONE);
    }

    /** {@code POST /v1/validators/test} with a reference or a spec ({@link ValidatorTestParams}). */
    public ValidatorTestResult test(ValidatorTestParams params) {
        return test(params, RequestOptions.NONE);
    }

    public ValidatorTestResult test(ValidatorTestParams params, RequestOptions options) {
        if (params == null) throw new IllegalArgumentException("params are required");
        return core.json(new Core.Call(Op.testValidator).jsonBody(params.body).options(options), ValidatorTestResult::new);
    }

    /**
     * {@code POST /v1/validators/suggest}: a draft validator from a description and examples (not
     * saved). Uses the organization's agent allowance, like {@code typizers().generate(…)}.
     */
    public ValidatorSuggestion suggest(ValidatorSuggestParams params) {
        return suggest(params, RequestOptions.NONE);
    }

    public ValidatorSuggestion suggest(ValidatorSuggestParams params, RequestOptions options) {
        if (params == null) throw new IllegalArgumentException("params are required");
        return core.json(new Core.Call(Op.suggestValidator).jsonBody(params.body).options(options),
                ValidatorSuggestion::new);
    }

    /** {@code my/<slug>[@version]} → by slug; anything else → by id. */
    static Core.Call lookup(String ref) {
        String r = requireId(ref);
        if (r.startsWith("my/")) {
            String slug = r.substring(3);
            int at = slug.indexOf('@');
            if (at >= 0) slug = slug.substring(0, at);
            if (slug.isEmpty() || slug.indexOf('/') >= 0) throw new IllegalArgumentException("Bad validator reference: " + ref);
            return new Core.Call(Op.getValidatorBySlug, slug);
        }
        return new Core.Call(Op.getValidator, r);
    }

    private static String requireId(String id) {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("A validator id or my/<slug> is required");
        return id.trim();
    }
}
