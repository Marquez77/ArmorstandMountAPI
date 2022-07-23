package me.marquez.asmountapi;

import net.minecraft.world.entity.decoration.ArmorStand;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class PacketSystemArmorStand {
	
//	private final Map<EnumItemSlot, ItemStack> equipments = new HashMap<>();
	private ArmorStand armorStand;
	
	public PacketSystemArmorStand(ArmorStand armorStand) {
		this.armorStand = armorStand;
	}
	
	public ArmorStand getArmorStand() {
		return armorStand;
	}
	
	public void setArmorStand(ArmorStand armorStand) {
		this.armorStand = armorStand;
	}

//	public ItemStack getEquipment(final EnumItemSlot enumitemslot) {
//		return equipments.getOrDefault(enumitemslot, null);
//	}
//
//	public void setEquipment(final EnumItemSlot enumitemslot, final ItemStack itemstack) {
//		equipments.put(enumitemslot, itemstack);
//    }
    

}
