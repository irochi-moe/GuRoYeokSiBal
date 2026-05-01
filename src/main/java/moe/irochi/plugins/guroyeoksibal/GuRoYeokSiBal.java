package moe.irochi.plugins.guroyeoksibal;

import moe.irochi.plugins.guroyeoksibal.commands.GuRoYeokSiBalCommand;
import moe.irochi.plugins.guroyeoksibal.hooks.TownyChatHook;
import moe.irochi.plugins.guroyeoksibal.listeners.ChatFilterListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public final class GuRoYeokSiBal extends JavaPlugin {

    private volatile AhoCorasick matcher;
    private volatile int loadedWordCount = 0;
    private volatile Map<String, String> wordToFile = new HashMap<>();
    private TownyChatHook townyChatHook;
    private Logger logger;

    @Override
    public void onEnable() {
        logger = getLogger();
        saveDefaultConfig();
        loadBannedWords(false);

        if (getServer().getPluginManager().isPluginEnabled("TownyChat")) {
            townyChatHook = new TownyChatHook(getTownyFilteredChannels());
            List<String> channels = getConfig().getStringList("towny-filtered-channels");
            String channelDesc = channels.contains("*") ? "전체 채널" : String.join(", ", channels);
            logger.info("TownyChat 감지 — 필터링 채널: " + channelDesc);
        }

        getServer().getPluginManager().registerEvents(new ChatFilterListener(this), this);

        PluginCommand command = getCommand("guroyeoksibal");
        if (command != null) {
            GuRoYeokSiBalCommand commandHandler = new GuRoYeokSiBalCommand(this);
            command.setExecutor(commandHandler);
            command.setTabCompleter(commandHandler);
        } else {
            logger.severe("/guroyeksibal 명령어 등록 실패 — plugin.yml을 확인하세요.");
        }
    }

    public boolean shouldFilter(Player player) {
        if (townyChatHook == null) return true;
        return townyChatHook.shouldFilter(player);
    }

    private Set<String> getTownyFilteredChannels() {
        List<String> list = getConfig().getStringList("towny-filtered-channels");
        Set<String> result = new HashSet<>();
        for (String entry : list) {
            result.add(entry.trim().toLowerCase());
        }
        return result;
    }

    private void loadBannedWords(boolean isReload) {
        File dataFolder = getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File defaultCsv = new File(dataFolder, "default.csv");
        if (!defaultCsv.exists()) {
            saveResource("default.csv", false);
        }

        File[] csvFiles = dataFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".csv"));

        if (csvFiles == null || csvFiles.length == 0) {
            logger.warning("CSV 파일을 찾을 수 없습니다: " + dataFolder.getPath());
            matcher = AhoCorasick.build(Set.of());
            loadedWordCount = 0;
            return;
        }

        Set<String> words = new HashSet<>();
        Map<String, String> newWordToFile = new HashMap<>();

        for (File csvFile : csvFiles) {
            int before = words.size();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;

                    for (String word : line.split(",")) {
                        String trimmed = word.trim().toLowerCase();
                        if (!trimmed.isEmpty()) {
                            words.add(trimmed);
                            newWordToFile.putIfAbsent(trimmed, csvFile.getName());
                        }
                    }
                }
            } catch (IOException e) {
                logger.severe(csvFile.getName() + " 로드 실패: " + e.getMessage());
            }
            logger.info(csvFile.getName() + "에서 " + (words.size() - before) + "개 단어 로드");
        }

        matcher = AhoCorasick.build(words);
        loadedWordCount = words.size();
        wordToFile = newWordToFile;
        logger.info((isReload ? "리로드 완료" : "준비 완료") + " — 단어 " + loadedWordCount + "개");
    }

    public void reloadBannedWords() {
        reloadConfig();
        loadBannedWords(true);
        if (townyChatHook != null) {
            townyChatHook = new TownyChatHook(getTownyFilteredChannels());
            List<String> channels = getConfig().getStringList("towny-filtered-channels");
            String channelDesc = channels.contains("*") ? "전체 채널" : String.join(", ", channels);
            logger.info("TownyChat 필터링 채널 갱신: " + channelDesc);
        }
    }

    public AhoCorasick getMatcher() {
        return matcher;
    }

    public int getLoadedWordCount() {
        return loadedWordCount;
    }

    public String getWordSource(String word) {
        return wordToFile.getOrDefault(word, "unknown");
    }

    public String getActionMode() {
        return getConfig().getString("action", "BLOCK");
    }

    public char getReplaceChar() {
        String s = getConfig().getString("replace-char", "*");
        return (s == null || s.isEmpty()) ? '*' : s.charAt(0);
    }

    public Component getBlockMessage() {
        String msg = getConfig().getString("block-message", "<red>욕설이 포함된 채팅은 전송할 수 없습니다.");
        return MiniMessage.miniMessage().deserialize(msg);
    }

    public boolean isFilterWhisper() {
        return getConfig().getBoolean("filter-whisper", true);
    }

    public List<String> getWhisperCommands() {
        return getConfig().getStringList("whisper-commands");
    }

    public boolean isNotifyStaff() {
        return getConfig().getBoolean("notify-staff", true);
    }

    public Component getNotifyMessage(String playerName, String message) {
        String format = getConfig().getString(
                "notify-format", "<yellow>[GuRoYeokSiBal] <red>{player}<yellow>: <gray>{message}");
        String sanitizedMessage = message.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
        String formatted = format
                .replace("{player}", MiniMessage.miniMessage().escapeTags(playerName))
                .replace("{message}", MiniMessage.miniMessage().escapeTags(sanitizedMessage));
        return MiniMessage.miniMessage().deserialize(formatted);
    }

    @Override
    public void onDisable() {
        logger.info("플러그인이 비활성화되었습니다.");
    }
}
