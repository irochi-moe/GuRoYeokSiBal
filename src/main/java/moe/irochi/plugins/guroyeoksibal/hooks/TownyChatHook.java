package moe.irochi.plugins.guroyeoksibal.hooks;

import com.palmergames.bukkit.TownyChat.Chat;
import com.palmergames.bukkit.TownyChat.channels.Channel;
import org.bukkit.entity.Player;

import java.util.Set;

public class TownyChatHook {

    private final Set<String> filteredChannels;

    public TownyChatHook(Set<String> filteredChannels) {
        this.filteredChannels = filteredChannels;
    }

    public boolean shouldFilter(Player player) {
        if (filteredChannels.contains("*")) return true;

        Channel channel = Chat.getTownyChat().getPlayerChannel(player);
        if (channel == null) return filteredChannels.isEmpty();

        return filteredChannels.contains(channel.getName().toLowerCase());
    }
}
