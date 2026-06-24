package de.igelbingo;

import org.junit.jupiter.api.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PluginConfigTest {

    private Path tempDir;
    private final Map<String, String> savedEnv = new HashMap<>();

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("igelbingo-test");
    }

    @AfterEach
    void tearDown() throws IOException {
        restoreEnv();
        Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
    }

    @Test
    void envOverridesFileConfig() throws IOException {
        Path configFile = tempDir.resolve("plugins/igelbingo/config.yml");
        Files.createDirectories(configFile.getParent());
        Files.writeString(configFile, "game-memory: 6G\n");

        setEnv("IGELBINGO_GAME_MEMORY", "10G");

        PluginConfig config = new PluginConfig(tempDir);
        assertEquals("10G", config.gameMemory);
    }

    @Test
    void envListParsesCommaSeparated() throws IOException {
        Path configFile = tempDir.resolve("plugins/igelbingo/config.yml");
        Files.createDirectories(configFile.getParent());
        Files.writeString(configFile, "");

        setEnv("IGELBINGO_ADMINS", "user1, user2, user3");

        PluginConfig config = new PluginConfig(tempDir);
        assertEquals(List.of("user1", "user2", "user3"), config.admins);
    }

    @Test
    void envListEmptyUsesDefault() throws IOException {
        Path configFile = tempDir.resolve("plugins/igelbingo/config.yml");
        Files.createDirectories(configFile.getParent());
        Files.writeString(configFile, "admins:\n- defaultAdmin\n");

        PluginConfig config = new PluginConfig(tempDir);
        assertEquals(List.of("defaultAdmin"), config.admins);
    }

    @SuppressWarnings("unchecked")
    private void setEnv(String key, String value) {
        try {
            Map<String, String> env = System.getenv();
            Field field = env.getClass().getDeclaredField("m");
            field.setAccessible(true);
            Map<String, String> writableEnv = (Map<String, String>) field.get(env);
            if (!savedEnv.containsKey(key)) {
                savedEnv.put(key, env.get(key));
            }
            writableEnv.put(key, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private void restoreEnv() {
        try {
            Map<String, String> env = System.getenv();
            Field field = env.getClass().getDeclaredField("m");
            field.setAccessible(true);
            Map<String, String> writableEnv = (Map<String, String>) field.get(env);
            for (var entry : savedEnv.entrySet()) {
                if (entry.getValue() == null) {
                    writableEnv.remove(entry.getKey());
                } else {
                    writableEnv.put(entry.getKey(), entry.getValue());
                }
            }
            savedEnv.clear();
        } catch (Exception ignored) {
        }
    }
}
