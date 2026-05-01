package moe.irochi.plugins.guroyeoksibal.commands;

import moe.irochi.plugins.guroyeoksibal.GuRoYeokSiBal;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class GuRoYeokSiBalCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final GuRoYeokSiBal plugin;

    public GuRoYeokSiBalCommand(GuRoYeokSiBal plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            switch (args[0].toLowerCase()) {
                case "reload" -> {
                    if (!sender.hasPermission("irochi.guroyeoksibal.reload")) {
                        sender.sendMessage(MM.deserialize("<red>권한이 없습니다."));
                        return true;
                    }
                    plugin.reloadBannedWords();
                    sender.sendMessage(MM.deserialize("<green>GuRoYeokSiBal: 설정과 욕설 목록을 다시 불러왔습니다."));
                }
                case "status" -> {
                    if (!sender.hasPermission("irochi.guroyeoksibal.reload")) {
                        sender.sendMessage(MM.deserialize("<red>권한이 없습니다."));
                        return true;
                    }
                    int words = plugin.getLoadedWordCount();
                    String mode = plugin.getActionMode();
                    String notify = plugin.isNotifyStaff() ? "<green>켜짐" : "<red>꺼짐";
                    sender.sendMessage(MM.deserialize("<yellow>[GuRoYeokSiBal] <white>키워드 <green>" + words
                            + "<white>개 | 모드 <green>" + mode
                            + "<white> | 알림 수신 <aqua>" + notify));
                }
                default -> sender.sendMessage(MM.deserialize("<yellow>사용법: /guroyeoksibal \\<reload|status>"));
            }
            return true;
        }

        sender.sendMessage(MM.deserialize("<yellow>사용법: /guroyeoksibal \\<reload|status>"));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("reload", "status");
        }
        return List.of();
    }
}
