package ru.typizer;

import java.util.Locale;
import java.util.logging.Logger;

/**
 * Where the SDK writes its few log lines. It never logs the API key, request or response bodies,
 * {@code output}, {@code fields}, file names, metadata or webhook secrets — only: at {@code DEBUG} one
 * line per HTTP attempt (method, path without query, status, request id, attempt, duration), at
 * {@code INFO} each retry with its reason and pause, at {@code WARN} a {@code Typizer-Maintenance}
 * notice (once per client and event).
 *
 * <p>The default is {@link #jul()}: {@code java.util.logging} logger {@code ru.typizer}, silent below
 * {@code WARNING} unless you configure it (or set {@code TYPIZER_LOG=debug|info|warn} to print to
 * stderr). For SLF4J/Logback/Log4j pass a lambda:
 *
 * <pre>{@code
 * org.slf4j.Logger log = LoggerFactory.getLogger("ru.typizer");
 * TypizerClient.builder().logger((level, message) -> log.info(message))
 * }</pre>
 */
@FunctionalInterface
public interface TypizerLogger {
    enum Level {
        DEBUG,
        INFO,
        WARN
    }

    /** Writes one line. */
    void log(Level level, String message);

    /** Whether {@code level} is written at all (lets the SDK skip building the message). */
    default boolean isEnabled(Level level) {
        return true;
    }

    /** {@code java.util.logging} logger {@code ru.typizer}: DEBUG → FINE, INFO → INFO, WARN → WARNING. */
    static TypizerLogger jul() {
        return JulLogger.INSTANCE;
    }

    /** Lines at {@code min} and above to {@code System.err}. */
    static TypizerLogger stderr(Level min) {
        return new TypizerLogger() {
            @Override
            public void log(Level level, String message) {
                if (isEnabled(level)) System.err.println(message);
            }

            @Override
            public boolean isEnabled(Level level) {
                return level.ordinal() >= min.ordinal();
            }
        };
    }

    /** Nothing at all. */
    static TypizerLogger none() {
        return new TypizerLogger() {
            @Override
            public void log(Level level, String message) {}

            @Override
            public boolean isEnabled(Level level) {
                return false;
            }
        };
    }

    /** {@code TYPIZER_LOG=debug|info|warn} → {@link #stderr}, otherwise {@link #jul()}. */
    static TypizerLogger fromEnvironment() {
        String v = System.getenv("TYPIZER_LOG");
        if (v == null || v.trim().isEmpty()) return jul();
        switch (v.trim().toLowerCase(Locale.ROOT)) {
            case "debug": return stderr(Level.DEBUG);
            case "info": return stderr(Level.INFO);
            case "warn":
            case "warning": return stderr(Level.WARN);
            default: return jul();
        }
    }

    /** The {@code java.util.logging} adapter. */
    final class JulLogger implements TypizerLogger {
        static final JulLogger INSTANCE = new JulLogger();
        private final Logger logger = Logger.getLogger("ru.typizer");

        private JulLogger() {
            // Silent by default: WARNING and above, unless logging.properties already set a level.
            if (logger.getLevel() == null) logger.setLevel(java.util.logging.Level.WARNING);
        }

        private static java.util.logging.Level map(Level level) {
            switch (level) {
                case DEBUG: return java.util.logging.Level.FINE;
                case INFO: return java.util.logging.Level.INFO;
                default: return java.util.logging.Level.WARNING;
            }
        }

        @Override
        public void log(Level level, String message) {
            logger.log(map(level), message);
        }

        @Override
        public boolean isEnabled(Level level) {
            return logger.isLoggable(map(level));
        }
    }
}
