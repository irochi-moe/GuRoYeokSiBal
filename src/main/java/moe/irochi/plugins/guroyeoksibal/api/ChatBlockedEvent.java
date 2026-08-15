package moe.irochi.plugins.guroyeoksibal.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ChatBlockedEvent extends Event {

    public enum Reason { COOLDOWN, PROFANITY, ALL_CAPS }

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String message;
    private final Reason reason;

    public ChatBlockedEvent(boolean async, Player player, String message, Reason reason) {
        super(async);
        this.player = player;
        this.message = message;
        this.reason = reason;
    }

    public Player getPlayer() {
        return player;
    }

    public String getMessage() {
        return message;
    }

    public Reason getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
