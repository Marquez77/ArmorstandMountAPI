package me.marquez.asmountapi;

import org.bukkit.entity.Player;

public interface ASMountAPI {
    /**
     * 플레이어에게 새로운 아머스탠드를 추가합니다.
     *
     * @param player 대상 플레이어
     * @param data 아머스탠드 데이터
     */
    void addArmorStandMount(Player player, ASMountData data);

    /**
     * 플레이어로부터 아머스탠드를 제거합니다.
     * 데이터의 이름만을 비교하여 추가되어 있는 데이터를 제거합니다.
     *
     * @param player 대상 플레이어
     * @param data 제거할 아머스탠드 데이터
     */
    void removeArmorStandMount(Player player, ASMountData data);

    /**
     * 플레이어로부터 아머스탠드를 제거합니다.
     *
     * @param player 대상 플레이어
     * @param name 제거할 아머스탠드 이름
     */
    void removeArmorStandMount(Player player, String name);

    /**
     * 플레이어의 아머스탠드를 보이지 않도록 합니다.
     *
     * @param player 대상 플레이어
     * @param viewer 보이지 않게 할 플레이어
     */
    void hideArmorStandMounts(Player player, Player viewer);

    /**
     * 플레이어의 아머스탠드를 보이게 합니다.
     *
     * @param player 대상 플레이어
     * @param viewer 보이게 할 플레이어
     */
    void showArmorStandMounts(Player player, Player viewer);
}

