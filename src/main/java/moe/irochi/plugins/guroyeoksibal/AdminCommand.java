package moe.irochi.plugins.guroyeoksibal;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public class AdminCommand implements CommandExecutor, TabCompleter {

    private final GuRoYeokSiBal plugin;

    public AdminCommand(GuRoYeokSiBal plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        LanguageManager lang = plugin.getLanguageManager();
        switch (args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "") {
            case "reload" -> {
                plugin.reloadAll();
                sender.sendMessage(lang.get(sender, "command.reload"));
            }
            case "status" -> {
                String notify = lang.getRaw(sender,
                        plugin.isNotifyStaff() ? "command.status-on" : "command.status-off");
                sender.sendMessage(lang.get(sender, "command.status", Map.of(
                        "words", String.valueOf(plugin.getLoadedWordCount()),
                        "mode", plugin.getActionMode(),
                        "notify", notify)));
            }
            default -> sender.sendMessage(lang.get(sender, "command.usage"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            String typed = args[0].toLowerCase(Locale.ROOT);
            return Stream.of("reload", "status")
                    .filter(option -> option.startsWith(typed))
                    .toList();
        }
        return List.of();
    }
}
