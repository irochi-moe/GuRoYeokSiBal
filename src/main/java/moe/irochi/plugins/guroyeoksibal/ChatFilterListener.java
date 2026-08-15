package moe.irochi.plugins.guroyeoksibal;

import moe.irochi.plugins.guroyeoksibal.api.ChatBlockedEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;
import java.util.function.Consumer;

public class ChatFilterListener implements Listener {

    private final GuRoYeokSiBal plugin;

    public ChatFilterListener(GuRoYeokSiBal plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    @SuppressWarnings("deprecation")
    public void onChat(AsyncPlayerChatEvent event) {
        if (plugin.isAzuriteHooked()) return;
        if (processChat(event.getPlayer(), event.getMessage(), event::setMessage)) {
            event.setCancelled(true);
        }
    }

    // Azurite 훅 경로는 Azurite가 MONITOR에서 취소 후 재발송하므로 취소돼도 커밋
    @EventHandler(priority = EventPriority.MONITOR)
    @SuppressWarnings("deprecation")
    public void onChatMonitor(AsyncPlayerChatEvent event) {
        if (!plugin.isAzuriteHooked() && event.isCancelled()) return;
        plugin.commitCooldown(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    @SuppressWarnings("deprecation")
    public void onLegacyChat(AsyncPlayerChatEvent event) {
        if (!plugin.isAzuriteHooked()) return;
        if (processChat(event.getPlayer(), event.getMessage(), event::setMessage)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.abortCooldown(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.isFilterCommands()) return;

        Player player = event.getPlayer();
        if (player.hasPermission(GuRoYeokSiBal.PERM_BYPASS)) return;

        ParsedCommand parsed = parseCommand(event.getMessage());
        if (parsed == null || parsed.message().isEmpty()) return;

        String message = parsed.message();
        ProfanityResult verdict = evaluate(player, message, false);
        if (verdict.isBlocked()) {
            event.setCancelled(true);
        } else if (verdict.isReplaced()) {
            event.setMessage(parsed.prefix() + verdict.message());
        }
    }

    private boolean processChat(Player player, String original, Consumer<String> replacer) {
        // Azurite가 취소를 무시하는 채팅은 차단 대신 REPLACE 검열만 적용 (config.yml 참고)
        boolean cancellable = plugin.isChatCancellable(player, original);
        long remaining = plugin.checkCooldown(player, original);
        if (remaining > 0 && cancellable) {
            player.sendMessage(plugin.getCooldownMessage(player, remaining));
            fireBlocked(player, original, ChatBlockedEvent.Reason.COOLDOWN);
            plugin.clearTownyDirectedChat(player);
            return true;
        }

        ProfanityResult profanity = handleProfanity(player, original, !cancellable);
        if (profanity.isBlocked()) {
            plugin.abortCooldown(player);
            plugin.clearTownyDirectedChat(player);
            return true;
        }
        if (profanity.isReplaced()) replacer.accept(profanity.message());
        return false;
    }

    private ProfanityResult handleProfanity(Player player, String original, boolean replaceOnly) {
        if (player.hasPermission(GuRoYeokSiBal.PERM_BYPASS)) {
            return ProfanityResult.PASS;
        }
        if (!plugin.shouldFilter(player, original)) {
            return ProfanityResult.PASS;
        }
        return evaluate(player, original, replaceOnly);
    }

    private ProfanityResult evaluate(Player player, String message, boolean replaceOnly) {
        MessageFilter.Result result = runFilter(message, replaceOnly);

        if (result != null && result.replacement() == null) {
            player.sendMessage(plugin.getBlockMessage(player));
            notifyProfanity(player, message, result.matchedPattern());
            fireBlocked(player, message, ChatBlockedEvent.Reason.PROFANITY);
            return ProfanityResult.BLOCKED;
        }

        if (!replaceOnly && isAllCaps(message)) {
            player.sendMessage(plugin.getCapsBlockMessage(player));
            notifyStaff(player, message, "ALL CAPS");
            fireBlocked(player, message, ChatBlockedEvent.Reason.ALL_CAPS);
            return ProfanityResult.BLOCKED;
        }

        if (result != null) {
            notifyProfanity(player, message, result.matchedPattern());
            return ProfanityResult.replaced(result.replacement());
        }

        return ProfanityResult.PASS;
    }

    private MessageFilter.Result runFilter(String message, boolean replaceOnly) {
        AhoCorasick matcher = plugin.getActiveMatcher();
        if (matcher == null) return null;
        boolean replaceMode = replaceOnly || "REPLACE".equals(plugin.getActionMode());
        return MessageFilter.filter(message, matcher, replaceMode, plugin.getReplaceChar());
    }

    private ParsedCommand parseCommand(String raw) {
        int offset = 0;
        String matchRaw = raw;
        int firstSpace = raw.indexOf(' ');
        int colon = raw.indexOf(':');
        if (colon > 1 && (firstSpace < 0 || colon < firstSpace)) {
            matchRaw = "/" + raw.substring(colon + 1);
            offset = colon;
        }
        String lower = matchRaw.toLowerCase(Locale.ROOT);

        for (String prefix : plugin.getTargetCommandPrefixes()) {
            if (!lower.startsWith(prefix)) continue;

            int targetStart = skipSpaces(matchRaw, prefix.length());
            int spaceIdx = matchRaw.indexOf(' ', targetStart);
            if (spaceIdx < 0) break;
            int msgStart = skipSpaces(matchRaw, spaceIdx + 1);
            if (msgStart >= matchRaw.length()) break;
            return new ParsedCommand(raw.substring(0, msgStart + offset), raw.substring(msgStart + offset));
        }

        for (String prefix : plugin.getFullCommandPrefixes()) {
            if (lower.startsWith(prefix)) {
                int msgStart = skipSpaces(matchRaw, prefix.length());
                if (msgStart >= matchRaw.length()) {
                    return null;
                }
                return new ParsedCommand(raw.substring(0, msgStart + offset), raw.substring(msgStart + offset));
            }
        }

        return null;
    }

    private static int skipSpaces(String s, int from) {
        int i = from;
        while (i < s.length() && s.charAt(i) == ' ') i++;
        return i;
    }

    private void fireBlocked(Player player, String message, ChatBlockedEvent.Reason reason) {
        plugin.getServer().getPluginManager().callEvent(
                new ChatBlockedEvent(!Bukkit.isPrimaryThread(), player, message, reason));
    }

    private void notifyProfanity(Player offender, String originalMessage, String matchedPattern) {
        notifyStaff(offender, originalMessage, plugin.describePattern(matchedPattern));
    }

    private void notifyStaff(Player offender, String originalMessage, String detail) {
        if (!plugin.isNotifyStaff()) return;

        plugin.getLogger().warning("[탐지] " + offender.getName() + ": " + originalMessage
                + " (" + detail + ")");

        String offenderName = offender.getName();
        plugin.getServer().getGlobalRegionScheduler().run(plugin, task -> {
            for (Player staff : plugin.getServer().getOnlinePlayers()) {
                if (staff.hasPermission(GuRoYeokSiBal.PERM_NOTIFY)) {
                    staff.sendMessage(plugin.getNotifyMessage(staff, offenderName, originalMessage));
                }
            }
        });
    }

    private boolean isAllCaps(String message) {
        if (!plugin.isBlockAllCaps()) return false;

        int minLetters = plugin.getBlockAllCapsMinLetters();
        int upperCount = 0;
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (c == '§' && i + 1 < message.length() && MessageFilter.isLegacyCode(message.charAt(i + 1))) {
                i++;
                continue;
            }
            if (c >= 'A' && c <= 'Z') {
                upperCount++;
            } else if (Character.isLetter(c)) {
                return false;
            }
        }
        return upperCount >= minLetters;
    }

    private enum Verdict { PASS, BLOCKED, REPLACED }

    private record ProfanityResult(Verdict verdict, String message) {
        static final ProfanityResult PASS = new ProfanityResult(Verdict.PASS, null);
        static final ProfanityResult BLOCKED = new ProfanityResult(Verdict.BLOCKED, null);

        static ProfanityResult replaced(String message) {
            return new ProfanityResult(Verdict.REPLACED, message);
        }

        boolean isBlocked() {
            return verdict == Verdict.BLOCKED;
        }

        boolean isReplaced() {
            return verdict == Verdict.REPLACED;
        }
    }

    private record ParsedCommand(String prefix, String message) {}
}
