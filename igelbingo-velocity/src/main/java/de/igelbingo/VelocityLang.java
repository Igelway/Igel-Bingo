package de.igelbingo;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class VelocityLang {

    private final Map<String, String> messages = new HashMap<>();
    private String prefix = "&7[&6Igel-Bingo&7]&r";

    public VelocityLang(String language) {
        String file = "languages/" + language + ".yml";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(file)) {
            if (in == null) {
                try (InputStream fallback = getClass().getClassLoader().getResourceAsStream("languages/en_us.yml")) {
                    loadFromStream(fallback);
                }
            } else {
                loadFromStream(in);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load language file: " + file, e);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadFromStream(InputStream in) {
        Yaml yaml = new Yaml();
        Map<String, Object> data = yaml.load(in);
        prefix = getString(data, "prefix", prefix);
        flatten("", data);
    }

    @SuppressWarnings("unchecked")
    private void flatten(String path, Map<String, Object> data) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
            if (entry.getValue() instanceof Map) {
                flatten(key, (Map<String, Object>) entry.getValue());
            } else if (entry.getValue() instanceof String) {
                messages.put(key, (String) entry.getValue());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private String getString(Map<String, Object> data, String key, String defaultValue) {
        Object value = data.get(key);
        return value instanceof String ? (String) value : defaultValue;
    }

    public String get(String key, String... replacements) {
        String msg = messages.getOrDefault(key, key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            msg = msg.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return msg;
    }

    public String prefixed(String key, String... replacements) {
        return prefix + " " + get(key, replacements);
    }
}
