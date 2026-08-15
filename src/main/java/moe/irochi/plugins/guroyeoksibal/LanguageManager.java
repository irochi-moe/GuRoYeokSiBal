package moe.irochi.plugins.guroyeoksibal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class LanguageManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String[] BUNDLED = {"en", "ko", "ja"};

    private final JavaPlugin plugin;

    private volatile Map<String, YamlConfiguration> languages = new HashMap<>();
    private volatile YamlConfiguration fallback;
    private volatile YamlConfiguration bundledEnglish;

    public LanguageManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(String fallbackLanguage) {
        Map<String, YamlConfiguration> loaded = new HashMap<>();

        File langDir = new File(plugin.getDataFolder(), "lang");
        langDir.mkdirs();

        for (String code : BUNDLED) {
            File file = new File(langDir, code + ".yml");
            if (!file.exists()) {
                plugin.saveResource("lang/" + code + ".yml", false);
            }
        }

        File[] files = langDir.listFiles((dir, name) ->
                !name.startsWith(".") && name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                String code = name.substring(0, name.length() - 4).toLowerCase(Locale.ROOT);
                try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                    loaded.put(code, YamlConfiguration.loadConfiguration(reader));
                } catch (IOException e) {
                    plugin.getLogger().severe("언어 파일 로드 실패 " + name + ": " + e.getMessage());
                }
            }
        }

        Map<String, YamlConfiguration> bundled = new HashMap<>();
        for (String code : BUNDLED) {
            try (InputStream in = plugin.getResource("lang/" + code + ".yml")) {
                if (in != null) {
                    bundled.put(code, YamlConfiguration.loadConfiguration(
                            new InputStreamReader(in, StandardCharsets.UTF_8)));
                }
            } catch (IOException ignored) {
            }
        }
        for (Map.Entry<String, YamlConfiguration> entry : loaded.entrySet()) {
            YamlConfiguration defaults = bundled.get(entry.getKey());
            if (defaults != null) {
                entry.getValue().setDefaults(defaults);
            }
        }
        this.bundledEnglish = bundled.get("en");

        String code = (fallbackLanguage == null || fallbackLanguage.isBlank())
                ? "en" : fallbackLanguage.trim().toLowerCase(Locale.ROOT);
        YamlConfiguration fb = loaded.get(code);
        if (fb == null) {
            fb = loaded.get("en");
            code = "en";
        }

        this.languages = loaded;
        this.fallback = fb;

        plugin.getLogger().info("언어 " + loaded.size() + "개 로드, 기본 언어: " + code);
    }

    private YamlConfiguration resolve(CommandSender sender) {
        if (sender instanceof Player player) {
            String lang = player.locale().getLanguage().toLowerCase(Locale.ROOT);
            YamlConfiguration cfg = languages.get(lang);
            if (cfg != null) {
                return cfg;
            }
        }
        return fallback;
    }

    public String getRaw(CommandSender sender, String key) {
        YamlConfiguration cfg = resolve(sender);
        String value = cfg != null ? cfg.getString(key) : null;
        if (value == null && fallback != null) {
            value = fallback.getString(key);
        }
        if (value == null && bundledEnglish != null) {
            value = bundledEnglish.getString(key);
        }
        return value != null ? value : key;
    }

    public Component get(CommandSender sender, String key) {
        return deserialize(getRaw(sender, key));
    }

    public Component get(CommandSender sender, String key, Map<String, String> placeholders) {
        String raw = getRaw(sender, key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return deserialize(raw);
    }

    private Component deserialize(String raw) {
        try {
            return MM.deserialize(raw);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("메시지 형식이 잘못되어 그대로 출력합니다: " + raw + " (" + e.getMessage() + ")");
            return Component.text(raw);
        }
    }
}
