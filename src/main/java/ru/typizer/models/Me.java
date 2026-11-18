package ru.typizer.models;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Who the API key belongs to: organization, key and its scopes. */
public final class Me extends TypizerObject {
    static final Set<String> FIELDS = fields("object", "auth", "organization", "user", "role", "api_key", "organizations", "features");

    public Me(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** {@code auth}. */
    public String auth() {
        return str("auth");
    }

    /** {@code organization}. */
    public OrganizationRef organization() {
        return object("organization", OrganizationRef::new);
    }

    /** {@code user}. */
    public User user() {
        return object("user", User::new);
    }

    /** {@code role}. */
    public String role() {
        return str("role");
    }

    /** {@code api_key}. */
    public ApiKey apiKey() {
        return object("api_key", ApiKey::new);
    }

    /** {@code organizations}. */
    public List<Membership> organizations() {
        return objects("organizations", Membership::new);
    }

    /** {@code features}. */
    public Features features() {
        return object("features", Features::new);
    }

    /** The organization of the key. */
    public static final class OrganizationRef extends TypizerObject {
        static final Set<String> FIELDS = fields("object", "id", "name");

        public OrganizationRef(Map<String, Object> data) {
            super(data, FIELDS);
        }

        /** {@code id}. */
        public String id() {
            return str("id");
        }

        /** {@code name}. */
        public String name() {
            return str("name");
        }
    }

    /** The user (null for an API key). */
    public static final class User extends TypizerObject {
        static final Set<String> FIELDS = fields("object", "id", "email", "name", "two_factor_enabled");

        public User(Map<String, Object> data) {
            super(data, FIELDS);
        }

        /** {@code id}. */
        public String id() {
            return str("id");
        }

        /** {@code email}. */
        public String email() {
            return str("email");
        }

        /** {@code name}. */
        public String name() {
            return str("name");
        }

        /** {@code two_factor_enabled}. */
        public boolean twoFactorEnabled() {
            return flag("two_factor_enabled");
        }
    }

    /** The API key of the call. */
    public static final class ApiKey extends TypizerObject {
        static final Set<String> FIELDS = fields("object", "id", "name", "scopes");

        public ApiKey(Map<String, Object> data) {
            super(data, FIELDS);
        }

        /** {@code id}. */
        public String id() {
            return str("id");
        }

        /** {@code name}. */
        public String name() {
            return str("name");
        }

        /** {@code scopes}. */
        public List<String> scopes() {
            return strings("scopes");
        }
    }

    /** An organization the user belongs to. */
    public static final class Membership extends TypizerObject {
        static final Set<String> FIELDS = fields("object", "id", "name", "role");

        public Membership(Map<String, Object> data) {
            super(data, FIELDS);
        }

        /** {@code id}. */
        public String id() {
            return str("id");
        }

        /** {@code name}. */
        public String name() {
            return str("name");
        }

        /** {@code role}. */
        public String role() {
            return str("role");
        }
    }

    /** Features enabled for the organization. */
    public static final class Features extends TypizerObject {
        static final Set<String> FIELDS = fields("object", "billing_enabled", "edo_diadoc", "catalog_community", "inbound_mail",
                "validators_suggest");

        public Features(Map<String, Object> data) {
            super(data, FIELDS);
        }

        /** {@code billing_enabled}. */
        public boolean billingEnabled() {
            return flag("billing_enabled");
        }

        /** {@code edo_diadoc}. */
        public boolean edoDiadoc() {
            return flag("edo_diadoc");
        }

        /** {@code catalog_community}. */
        public boolean catalogCommunity() {
            return flag("catalog_community");
        }

        /** {@code inbound_mail}: приём документов по почте. */
        public boolean inboundMail() {
            return flag("inbound_mail");
        }

        /** {@code validators_suggest}: {@code validators().suggest(…)} is available. */
        public boolean validatorsSuggest() {
            return flag("validators_suggest");
        }
    }
}
