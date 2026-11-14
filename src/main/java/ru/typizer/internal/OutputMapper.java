package ru.typizer.internal;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code Run.outputAs(Class)}: binds JSON to a user class through the application's own JSON library,
 * found by reflection (Jackson 2, then Gson), so the SDK itself depends on neither.
 */
public final class OutputMapper {
    private static final Map<ClassLoader, Binder> BINDERS = new ConcurrentHashMap<>();

    private OutputMapper() {}

    interface Binder {
        Object read(String json, Class<?> type) throws Exception;
    }

    public static <T> T read(String json, Class<T> type) {
        ClassLoader loader = type.getClassLoader() != null ? type.getClassLoader() : OutputMapper.class.getClassLoader();
        Binder binder = BINDERS.computeIfAbsent(loader, OutputMapper::find);
        if (binder == NONE)
            throw new IllegalStateException("Run.outputAs() needs Jackson (com.fasterxml.jackson.core:jackson-databind) or"
                    + " Gson (com.google.code.gson:gson) on the classpath; or parse run.outputJson() yourself");
        try {
            return type.cast(binder.read(json, type));
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IllegalStateException("The run output does not fit " + type.getName() + ": " + cause.getMessage(), cause);
        } catch (Exception e) {
            throw new IllegalStateException("The run output does not fit " + type.getName() + ": " + e.getMessage(), e);
        }
    }

    private static final Binder NONE = (json, type) -> null;

    private static Binder find(ClassLoader loader) {
        Binder b = jackson(loader);
        if (b == null) b = gson(loader);
        return b != null ? b : NONE;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Binder jackson(ClassLoader loader) {
        try {
            Class<?> mapperClass = Class.forName("com.fasterxml.jackson.databind.ObjectMapper", true, loader);
            Object mapper = mapperClass.getConstructor().newInstance();
            // Modules on the classpath (java.time, Kotlin, parameter names) — as the application has them.
            mapperClass.getMethod("findAndRegisterModules").invoke(mapper);
            Class<Enum> featureClass =
                    (Class<Enum>) Class.forName("com.fasterxml.jackson.databind.DeserializationFeature", true, loader);
            Object feature = Enum.valueOf(featureClass, "FAIL_ON_UNKNOWN_PROPERTIES");
            mapperClass.getMethod("configure", featureClass, boolean.class).invoke(mapper, feature, false);
            Method readValue = mapperClass.getMethod("readValue", String.class, Class.class);
            return (json, type) -> readValue.invoke(mapper, json, type);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Binder gson(ClassLoader loader) {
        try {
            Class<?> gsonClass = Class.forName("com.google.gson.Gson", true, loader);
            Object gson = gsonClass.getConstructor().newInstance();
            Method fromJson = gsonClass.getMethod("fromJson", String.class, Class.class);
            return (json, type) -> fromJson.invoke(gson, json, type);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
