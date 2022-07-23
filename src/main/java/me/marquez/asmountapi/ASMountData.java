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
    private boolean isSmall;

    private ArmorStand armorStand;

}
