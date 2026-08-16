package moe.irochi.plugins.guroyeoksibal.hooks;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Set;

public class EssentialsChatHook implements ChatHook {

    private final Set<String> filteredChats;
    private final Set<String> cooldownChats;
    private final int radius;
    private final boolean questionEnabled;

    public EssentialsChatHook(Plugin essentials, Set<String> filteredChats, Set<String> cooldownChats) {
        this.filteredChats = filteredChats;
        this.cooldownChats = cooldownChats;
        File configFile = new File(essentials.getDataFolder(), "config.yml");
        YamlConfiguration config = configFile.exists()
                ? YamlConfiguration.loadConfiguration(configFile) : new YamlConfiguration();
        this.radius = config.getInt("chat.radius", config.getInt("chat-radius", 0));
        this.questionEnabled = config.getBoolean("chat.question-enabled", true);
    }

    @Override
    public Decision evaluate(Player player, String message) {
        String chatType = resolveChatType(message);
        return new Decision(
                matches(chatType, filteredChats),
                true,
                matches(chatType, cooldownChats),
                "essentials:" + chatType);
    }

    public int getRadius() {
        return radius;
    }

    // EssentialsChat AbstractChatHandler.getChatType()과 동일한 판정
    // (권한 없는 !는 shout로 분류 후 Essentials가 차단, /shout 토글 사용자는 local로 근사)
    private String resolveChatType(String message) {
        if (radius < 1) return "global";
        if (message == null || message.length() < 2) return "local";
        char first = message.charAt(0);
        if (first == '!') return "shout";
        if (first == '?' && questionEnabled) return "question";
        return "local";
    }

    private boolean matches(String chatType, Set<String> configured) {
        return configured.contains("*") || configured.contains(chatType);
    }
}
