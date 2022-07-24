package me.marquez.asmountapi;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.bukkit.inventory.ItemStack;

@Getter
@Setter
@RequiredArgsConstructor
public class ASMountData {

    @NonNull
    private String name;
    @NonNull
    private ItemStack item;
    @NonNull
    private boolean small;

    private ArmorStand armorStand;

    /**
     * 데이터 객체를 생성합니다.
     *
     * @param name 아머스탠드 이름
     * @param item 아머스탠드 머리에 적용되는 아이템
     */
    public ASMountData(String name, ItemStack item) {
        this(name, item, false);
    }

}
