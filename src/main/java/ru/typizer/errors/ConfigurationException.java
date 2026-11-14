package ru.typizer.errors;

/** The client cannot be built: no API key (argument or {@code TYPIZER_API_KEY}), a bad base URL or proxy. */
public class ConfigurationException extends TypizerException {
    private static final long serialVersionUID = 1L;

    public ConfigurationException(String message) {
        super(message);
    }
}
