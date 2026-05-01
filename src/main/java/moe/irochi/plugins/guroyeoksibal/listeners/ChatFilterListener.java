package moe.irochi.plugins.guroyeoksibal.listeners;

import moe.irochi.plugins.guroyeoksibal.AhoCorasick;
import moe.irochi.plugins.guroyeoksibal.GuRoYeokSiBal;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.text.Normalizer;

@SuppressWarnings("deprecation")
public class ChatFilterListener implements Listener {

    private final GuRoYeokSiBal plugin;

    public ChatFilterListener(GuRoYeokSiBal plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        if (player.hasPermission("irochi.guroyeksibal.bypass")) return;
        if (!plugin.shouldFilter(player)) return;

        AhoCorasick matcher = plugin.getMatcher();
        if (matcher == null) return;

        String original = event.getMessage();
        String normalized = Normalizer.normalize(original, Normalizer.Form.NFC).toLowerCase();
        String stripped = stripFillers(normalized);

        String matchedWord = matcher.findFirstMatch(normalized);
        boolean detectedInRaw = matchedWord != null;

        if (!detectedInRaw && !stripped.equals(normalized)) {
            matchedWord = matcher.findFirstMatch(stripped);
        }

        if (matchedWord == null) return;
        final String effectiveMatch = matchedWord;

        if ("REPLACE".equalsIgnoreCase(plugin.getActionMode()) && detectedInRaw) {
            String replaced = matcher.replaceAll(normalized, plugin.getReplaceChar());
            if (!replaced.equals(normalized)) {
                event.setMessage(replaced);
                notifyStaff(player, original, effectiveMatch);
                return;
            }
        }

        event.setCancelled(true);
        player.sendMessage(plugin.getBlockMessage());
        notifyStaff(player, original, effectiveMatch);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) return;
        if (!plugin.isFilterWhisper()) return;

        Player player = event.getPlayer();
        if (player.hasPermission("irochi.guroyeksibal.bypass")) return;

        String raw = event.getMessage();
        String lower = raw.toLowerCase();

        String whisperMessage = null;
        String commandPrefix = null;

        for (String cmd : plugin.getWhisperCommands()) {
            String prefix = "/" + cmd.toLowerCase() + " ";
            if (lower.startsWith(prefix)) {
                String afterCmd = raw.substring(prefix.length());
                int spaceIdx = afterCmd.indexOf(' ');
                if (spaceIdx >= 0) {
                    commandPrefix = raw.substring(0, prefix.length() + spaceIdx + 1);
                    whisperMessage = afterCmd.substring(spaceIdx + 1);
                }
                break;
            }
        }

        if (whisperMessage == null || whisperMessage.isEmpty()) return;

        AhoCorasick matcher = plugin.getMatcher();
        if (matcher == null) return;

        String normalized = Normalizer.normalize(whisperMessage, Normalizer.Form.NFC).toLowerCase();
        String stripped = stripFillers(normalized);

        String matchedWord = matcher.findFirstMatch(normalized);
        boolean detectedInRaw = matchedWord != null;

        if (!detectedInRaw && !stripped.equals(normalized)) {
            matchedWord = matcher.findFirstMatch(stripped);
        }

        if (matchedWord == null) return;
        final String effectiveMatch = matchedWord;

        if ("REPLACE".equalsIgnoreCase(plugin.getActionMode()) && detectedInRaw) {
            String replaced = matcher.replaceAll(normalized, plugin.getReplaceChar());
            if (!replaced.equals(normalized)) {
                event.setMessage(commandPrefix + replaced);
                notifyStaff(player, whisperMessage, effectiveMatch);
                return;
            }
        }

        event.setCancelled(true);
        player.sendMessage(plugin.getBlockMessage());
        notifyStaff(player, whisperMessage, effectiveMatch);
    }

    private void notifyStaff(Player offender, String originalMessage, String matchedWord) {
        if (!plugin.isNotifyStaff()) return;

        String source = plugin.getWordSource(matchedWord);
        Component notification = plugin.getNotifyMessage(offender.getName(), originalMessage);
        plugin.getLogger().warning("[탐지] " + offender.getName() + ": " + originalMessage
                + " (\"" + matchedWord + "\" — " + source + ")");

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("irochi.guroyeksibal.notify")) {
                staff.sendMessage(notification);
            }
        }
    }

    private static String stripFillers(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isKorean(c) || Character.isLetter(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean isKorean(char c) {
        return (c >= '\uAC00' && c <= '\uD7A3')
            || (c >= '\u3131' && c <= '\u318E')
            || (c >= '\u1100' && c <= '\u11FF');
    }
}
