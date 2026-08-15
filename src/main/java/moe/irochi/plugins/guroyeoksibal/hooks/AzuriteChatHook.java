package moe.irochi.plugins.guroyeoksibal.hooks;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public class AzuriteChatHook {

    private static final String[] SHORTCUT_CHAT_TYPES = {"TEAM", "ALLY", "OFFICER", "CO_LEADER"};

    public record CooldownInfo(boolean applies, String key) {}

    private final Plugin azuritePlugin;
    private final Set<String> filteredChats;
    private final Set<String> cooldownChats;
    private final Logger logger;
    private final Method getUserManager;
    private final Method getTeamManager;
    private final Method getByUUID;
    private final Method getTeamChatSetting;
    private final Method getByPlayer;
    private final Map<String, String> shortcuts = new HashMap<>();
    private volatile boolean resolveFailureLogged;

    public AzuriteChatHook(Plugin azuritePlugin, Set<String> filteredChats, Set<String> cooldownChats, Logger logger) {
        this.azuritePlugin = azuritePlugin;
        this.filteredChats = filteredChats;
        this.cooldownChats = cooldownChats;
        this.logger = logger;

        try {
            Class<?> pluginClass = azuritePlugin.getClass();
            this.getUserManager = pluginClass.getMethod("getUserManager");
            this.getTeamManager = pluginClass.getMethod("getTeamManager");

            Class<?> userManagerClass = getUserManager.getReturnType();
            this.getByUUID = userManagerClass.getMethod("getByUUID", UUID.class);
            this.getTeamChatSetting = getByUUID.getReturnType().getMethod("getTeamChatSetting");

            Class<?> teamManagerClass = getTeamManager.getReturnType();
            this.getByPlayer = teamManagerClass.getMethod("getByPlayer", UUID.class);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Azurite API를 초기화하지 못했습니다.", e);
        }

        loadShortcuts();
    }

    public boolean shouldFilter(Player player, String message) {
        return matches(resolveChatType(player, message), filteredChats);
    }

    // Azurite는 public 외 채팅의 이벤트 취소를 무시함
    public boolean isCancellable(Player player, String message) {
        String chatType = resolveChatType(player, message);
        return chatType == null || "PUBLIC".equals(chatType);
    }

    public CooldownInfo evaluateCooldown(Player player, String message) {
        String chatType = resolveChatType(player, message);
        boolean applies = matches(chatType, cooldownChats);
        String key = chatType == null ? "azurite:default" : "azurite:" + chatType.toLowerCase(Locale.ROOT);
        return new CooldownInfo(applies, key);
    }

    private boolean matches(String chatType, Set<String> configured) {
        if (configured.contains("*")) return true;
        if (chatType == null) return false;
        return configured.contains(chatType.toLowerCase(Locale.ROOT));
    }

    private String resolveChatType(Player player, String message) {
        try {
            Object userManager = getUserManager.invoke(azuritePlugin);
            Object user = getByUUID.invoke(userManager, player.getUniqueId());
            if (user == null) return null;

            Object chatSetting = getTeamChatSetting.invoke(user);
            String chatType = chatSetting != null ? chatSetting.toString() : null;

            if (message != null && !message.isEmpty()) {
                Object teamManager = getTeamManager.invoke(azuritePlugin);
                Object playerTeam = getByPlayer.invoke(teamManager, player.getUniqueId());
                if (playerTeam != null) {
                    String shortcut = findShortcut(message);
                    if (shortcut != null) {
                        chatType = shortcut;
                    }
                } else if (isTeamOnlyChat(chatType)) {
                    chatType = "PUBLIC";
                }
            }

            return chatType;
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!resolveFailureLogged) {
                resolveFailureLogged = true;
                logger.warning("Azurite 채팅 종류 확인 실패, 해당 메시지들은 채팅 종류 미지정으로 처리됩니다: " + e);
            }
            return null;
        }
    }

    private static boolean isTeamOnlyChat(String chatType) {
        if (chatType == null) return false;
        for (String type : SHORTCUT_CHAT_TYPES) {
            if (type.equals(chatType)) return true;
        }
        return false;
    }

    private String findShortcut(String message) {
        String best = null;
        int bestLen = 0;
        for (Map.Entry<String, String> entry : shortcuts.entrySet()) {
            String key = entry.getKey();
            if (key.length() > bestLen && message.startsWith(key)) {
                best = entry.getValue();
                bestLen = key.length();
            }
        }
        return best;
    }

    private void loadShortcuts() {
        File configFile = new File(azuritePlugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        for (String chatType : SHORTCUT_CHAT_TYPES) {
            String shortcut = config.getString("CHAT_FORMAT." + chatType + "_CHAT.SHORTCUT", "");
            if (!shortcut.isEmpty()) {
                String existing = shortcuts.putIfAbsent(shortcut, chatType);
                if (existing != null) {
                    logger.warning("Azurite SHORTCUT 중복: '" + shortcut + "' (" + existing + ", " + chatType
                            + "), " + existing + "으로 처리합니다.");
                }
            }
        }
    }
}
