package moe.irochi.plugins.guroyeoksibal.hooks;

import com.palmergames.bukkit.TownyChat.Chat;
import com.palmergames.bukkit.TownyChat.channels.Channel;
import com.palmergames.bukkit.TownyChat.channels.channelTypes;
import com.palmergames.bukkit.TownyChat.config.ChatSettings;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

public class TownyChatHook {

    public record CooldownInfo(boolean applies, String key) {}

    private final Set<String> filteredChannels;
    private final Set<String> cooldownChannels;
    private final Logger logger;
    private volatile boolean resolveFailureLogged;
    private volatile boolean clearFailureLogged;

    public TownyChatHook(Set<String> filteredChannels, Set<String> cooldownChannels, Logger logger) {
        this.filteredChannels = filteredChannels;
        this.cooldownChannels = cooldownChannels;
        this.logger = logger;
    }

    public boolean shouldFilter(Player player, String message) {
        return matches(getChannelName(player, message), filteredChannels);
    }

    public CooldownInfo evaluateCooldown(Player player, String message) {
        String channelName = getChannelName(player, message);
        boolean applies = matches(channelName, cooldownChannels);
        String key = channelName == null ? "towny:default" : "towny:" + channelName;
        return new CooldownInfo(applies, key);
    }

    // 이벤트 취소 시 TownyChat의 directedChat 제거(ignoreCancelled 핸들러)가 스킵되므로 직접 제거
    public void clearDirectedChat(Player player) {
        try {
            Chat.getTownyChat().getTownyPlayerListener().directedChat.remove(player);
        } catch (RuntimeException | LinkageError e) {
            if (!clearFailureLogged) {
                clearFailureLogged = true;
                logger.warning("TownyChat directedChat 정리 실패, 차단된 메시지의 채널 지정이 남을 수 있습니다: " + e);
            }
        }
    }

    private String getChannelName(Player player, String message) {
        try {
            Chat chat = Chat.getTownyChat();
            Channel channel = resolveDestination(chat, player, message);
            return channel == null ? null : channel.getName().toLowerCase(Locale.ROOT);
        } catch (RuntimeException | LinkageError e) {
            if (!resolveFailureLogged) {
                resolveFailureLogged = true;
                logger.warning("TownyChat 채널 확인 실패, 해당 메시지들은 채널 미지정으로 처리됩니다: " + e);
            }
            return null;
        }
    }

    private Channel resolveDestination(Chat chat, Player player, String message) {
        String directedName = chat.getTownyPlayerListener().directedChat.get(player);
        if (directedName != null) {
            Channel directed = chat.getChannelsHandler().getChannel(directedName);
            if (directed != null) return directed;
        }

        boolean shout = message != null && message.startsWith("!") && ChatSettings.isExclamationPoint();
        Channel focused = chat.getPlayerChannel(player);
        if (!shout && focused != null && focused.hasSpeakPermission(player)) {
            return focused;
        }
        return chat.getChannelsHandler().getActiveChannel(player, channelTypes.GLOBAL, shout);
    }

    private boolean matches(String channelName, Set<String> configured) {
        if (configured.contains("*")) return true;
        if (channelName == null) return false;
        return configured.contains(channelName);
    }
}
