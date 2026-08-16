package moe.irochi.plugins.guroyeoksibal.hooks;

import org.bukkit.entity.Player;

public interface ChatHook {

    /**
     * @param filter         이 훅 기준으로 욕설 필터 대상인지
     * @param cancellable    연동 플러그인이 이벤트 취소를 존중하는지 (false면 REPLACE 검열만 가능)
     * @param cooldownApplies 이 훅 기준으로 쿨타임 대상인지
     * @param cooldownKey    쿨타임 버킷 키 (항상 non-null)
     */
    record Decision(boolean filter, boolean cancellable, boolean cooldownApplies, String cooldownKey) {}

    /** 메시지 1건당 1회 호출, 채널/채팅 종류 확인은 이 안에서 한 번만 수행 */
    Decision evaluate(Player player, String message);
}
