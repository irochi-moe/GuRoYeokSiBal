package moe.irochi.plugins.guroyeoksibal;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

final class ConfigUpdater {

    private ConfigUpdater() {}

    static void sync(JavaPlugin plugin) {
        YamlConfiguration bundled = loadBundled(plugin);
        if (bundled == null) return;

        FileConfiguration config = plugin.getConfig();
        File file = new File(plugin.getDataFolder(), "config.yml");
        // 파싱에 실패한 파일을 기본값으로 덮어쓰지 않도록 보호
        if (config.getKeys(false).isEmpty() && file.length() > 0) return;

        List<String> added = mergeInto(bundled, config);
        try {
            String merged = bundled.saveToString();
            if (file.exists() && merged.equals(Files.readString(file.toPath(), StandardCharsets.UTF_8))) {
                return;
            }
            Files.writeString(file.toPath(), merged, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("config.yml 갱신 실패: " + e.getMessage());
            return;
        }
        plugin.reloadConfig();
        plugin.getLogger().info(added.isEmpty()
                ? "config.yml을 최신 형식으로 정리했습니다."
                : "config.yml 갱신, 새 설정 " + added.size() + "개 추가: " + String.join(", ", added));
    }

    static List<String> mergeInto(YamlConfiguration bundled, FileConfiguration config) {
        List<String> added = new ArrayList<>();
        for (String key : bundled.getKeys(true)) {
            if (bundled.isConfigurationSection(key)) continue;
            if (config.isSet(key)) {
                bundled.set(key, config.get(key));
            } else {
                added.add(key);
            }
        }
        // 내장 config에 없는 사용자 정의 키(커스텀 쿨타임 등급 등) 보존
        for (String key : config.getKeys(true)) {
            if (!config.isConfigurationSection(key) && !bundled.isSet(key)) {
                bundled.set(key, config.get(key));
            }
        }
        return added;
    }

    private static YamlConfiguration loadBundled(JavaPlugin plugin) {
        InputStream in = plugin.getResource("config.yml");
        if (in == null) return null;
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException e) {
            return null;
        }
    }
}
