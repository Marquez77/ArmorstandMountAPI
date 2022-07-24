package me.marquez.asmountapi;

import com.mojang.datafixers.util.Pair;
import net.minecraft.network.protocol.Packet;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

public class ASMountListener implements Listener {

    private final ASMountAPI api;

    protected ASMountListener(ASMountAPI api) {
        this.api = api;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player player = e.getPlayer();
        if(api.playerMountData.containsKey(player)) {
            List<Packet<?>> packets = api.getRefreshPackets(player);
            api.sendPacketsToPlayers(api.getAroundPlayers(player), packets.toArray(new Packet[0]));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        if(api.playerMountData.containsKey(player)) {
            api.despawnArmorStands(api.aroundPlayers.get(player), api.playerMountData.get(player).values().toArray(new ASMountData[0]));
            api.playerMountData.remove(player);
            api.aroundPlayers.remove(player);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player player = e.getEntity();
        if(api.playerMountData.containsKey(player)) {
            api.despawnArmorStands(api.aroundPlayers.get(player), api.playerMountData.get(player).values().toArray(new ASMountData[0]));
            api.aroundPlayers.remove(player);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player player = e.getPlayer();
        if(api.playerMountData.containsKey(player) && !api.excludeView.contains(Pair.of(player, player))) {
            api.spawnArmorStands(player, List.of(player), api.playerMountData.get(player).values().toArray(new ASMountData[0]));
        }
    }

}
