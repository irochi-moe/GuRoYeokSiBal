package moe.irochi.plugins.guroyeoksibal.hooks;

import com.Zrips.CMI.CMI;
import com.Zrips.CMI.Modules.ChatFormat.CMIChatRoom;
import com.Zrips.CMI.Modules.ChatFormat.ChatFormatManager;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public class CMIChatHook implements ChatHook {

    private final Set<String> filteredChats;
    private final Set<String> cooldownChats;
    private final Logger logger;
    private volatile boolean resolveFailureLogged;

    public CMIChatHook(Set<String> filteredChats, Set<String> cooldownChats, Logger logger) {
        this.filteredChats = filteredChats;
        this.cooldownChats = cooldownChats;
        this.logger = logger;
    }

    @Override
    public Decision evaluate(Player player, String message) {
        // 예: "staff", "global", "chatroom:trade"
        String chatType = resolveChatType(player, message);
        String baseType = chatType == null ? null
                : chatType.indexOf(':') < 0 ? chatType : chatType.substring(0, chatType.indexOf(':'));
        return new Decision(
                matches(baseType, chatType, filteredChats),
                true,
                matches(baseType, chatType, cooldownChats),
                chatType == null ? "cmi:default" : "cmi:" + chatType);
    }

    // CMI 내부 컬렉션은 스레드 안전하지 않지만 CMI 자신도 비동기 채팅 스레드에서 같은 방식으로 읽음
    private String resolveChatType(Player player, String message) {
        try {
            ChatFormatManager chat = CMI.getInstance().getChatFormatManager();
            UUID uuid = player.getUniqueId();
            if (chat.getStaffChats().contains(uuid)) return "staff";

            CMIChatRoom room = chat.getChatRoom(uuid);
            if (room != null && !startsWith(message, ChatFormatManager.ChatRoomShout)) {
                String name = room.getChatName();
                return name == null ? "chatroom" : "chatroom:" + name.toLowerCase(Locale.ROOT);
            }
            if (startsWith(message, "!") && chat.getChatShoutRange() >= 0) return "global";
            return chat.getChatGeneralRange(player.getWorld()) > 0 ? "local" : "global";
        } catch (RuntimeException | LinkageError e) {
            if (!resolveFailureLogged) {
                resolveFailureLogged = true;
                logger.warning("CMI 채팅 종류 확인 실패, 해당 메시지들은 채팅 종류 미지정으로 처리됩니다: " + e);
            }
            return null;
        }
    }

    private static boolean startsWith(String message, String prefix) {
        return message != null && prefix != null && !prefix.isEmpty() && message.startsWith(prefix);
    }

    private boolean matches(String baseType, String chatType, Set<String> configured) {
        if (configured.contains("*")) return true;
        if (baseType == null) return false;
        return configured.contains(baseType) || configured.contains(chatType);
    }
}
