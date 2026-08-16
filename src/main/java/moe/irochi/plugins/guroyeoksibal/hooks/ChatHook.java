package moe.irochi.plugins.guroyeoksibal.hooks;

import org.bukkit.entity.Player;

public interface ChatHook {

    // cancellable=false: 연동 플러그인이 이벤트 취소를 무시해 REPLACE 검열만 가능
    record Decision(boolean filter, boolean cancellable, boolean cooldownApplies, String cooldownKey) {}

    // 메시지 1건당 1회 호출
    Decision evaluate(Player player, String message);
}
