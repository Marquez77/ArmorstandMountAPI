package me.marquez.asmountapi;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import lombok.Getter;
import lombok.NonNull;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_19_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_19_R1.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class ASMountAPI extends JavaPlugin implements Listener {

    private final Map<Player, Map<String, ASMountData>> playerMountData = new HashMap<>();
    private final Set<Pair<Player, Player>> excludeView = new HashSet<>();
    private final Map<Player, List<Player>> aroundPlayers = new HashMap<>();

    private double entityRange = 30;

    @Getter
    private static ASMountAPI instance;

    private List<Player> getAroundPlayers(Player player) {
        List<Player> players = new ArrayList<>();
        players.add(player);
        for(org.bukkit.entity.Entity entity : player.getNearbyEntities(entityRange, entityRange, entityRange)) {
            if(entity instanceof Player p) {
                if(excludeView.contains(Pair.of(player, p))) continue;
                players.add(p);
            }
        }
        return players;
    }

    private void sendPacketsToPlayers(List<Player> players, Packet<?>... packets) {
        players.forEach(player -> sendPacketsToPlayer(player, packets));
    }

    private void sendPacketsToPlayer(Player player, Packet<?>... packets) {
        Connection connection = ((CraftPlayer)player).getHandle().connection.getConnection();
        Arrays.stream(packets).forEach(connection::send);
    }

    @Override
    public void onEnable() {
        instance = this;
        getCommand("asmapireload").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> playerMountData.forEach((player, mountData) -> {
            if(!player.isDead()) {
                try {
                    List<Player> prevAroundPlayers = aroundPlayers.getOrDefault(player, null);
                    List<Player> players = getAroundPlayers(player);
                    aroundPlayers.put(player, new ArrayList<>(players));
                    if(prevAroundPlayers != null) {
                        List<Player> temp = new ArrayList<>(prevAroundPlayers);
                        players.forEach(temp::remove);
                        if(!temp.isEmpty()) despawnArtifact(temp, mountData.values().toArray(new ASMountData[0]));
                        prevAroundPlayers.forEach(players::remove);
                    }
                    if(!players.isEmpty()) spawnArtifact(player, players, mountData.values().toArray(new ASMountData[0]));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }), 0L, 5L);
        reload(getServer().getConsoleSender());
    }

    private void reload(CommandSender sender) {
        reloadConfig();
        entityRange = getConfig().getDouble("entity-distance");
        sender.sendMessage("§7- §eArmorStand Entity view range: §f§l" + entityRange);
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        if(sender.hasPermission("asmapireload.*")) {
            reload(sender);
            return true;
        }
        return false;
    }

    private void spawnArtifact(Player player, List<Player> players, ASMountData... mountData) {
        if(players == null || players.isEmpty()) return;
        List<Packet<?>> packets = new ArrayList<>();
        ServerLevel level = ((CraftWorld)player.getWorld()).getHandle();
        double x = player.getLocation().getX();
        double y = player.getLocation().getY();
        double z = player.getLocation().getZ();
        for (ASMountData data : mountData) {
            ArmorStand as = data.getArmorStand();
            as.level = level;
            as.setPos(x, y, z);
            int id = as.getId();
            ClientboundAddEntityPacket addEntityPacket = new ClientboundAddEntityPacket(as);
            ClientboundSetEntityDataPacket setEntityDataPacket = new ClientboundSetEntityDataPacket(id, as.getEntityData(), true);
            ClientboundSetEquipmentPacket setEquipmentPacket = new ClientboundSetEquipmentPacket(id, List.of(Pair.of(EquipmentSlot.HEAD, CraftItemStack.asNMSCopy(data.getItem()))));
            packets.add(addEntityPacket);
            packets.add(setEntityDataPacket);
            packets.add(setEquipmentPacket);
        }
        packets.addAll(getRefreshPackets(player));
        sendPacketsToPlayers(players, packets.toArray(new Packet[0]));
    }

    private void despawnArtifact(List<Player> players, ASMountData... mountData) {
        if(players == null || players.isEmpty()) return;
        int[] ids = new int[mountData.length];
        for(int i = 0; i < mountData.length; i++) ids[i] = mountData[i].getArmorStand().getId();
        ClientboundRemoveEntitiesPacket removeEntitiesPacket = new ClientboundRemoveEntitiesPacket(ids);
        sendPacketsToPlayers(players, removeEntitiesPacket);
    }

    public void addArmorStandMount(Player player, ASMountData data) {
        Location location = player.getLocation();
        ArmorStand armorStand = new ArmorStand(((CraftWorld)location.getWorld()).getHandle(), location.getX(), location.getY(), location.getZ());
        armorStand.setInvisible(true);
        armorStand.setMarker(true);
        armorStand.setSmall(data.isSmall());
        data.setArmorStand(armorStand);
        Map<String, ASMountData> mountData = playerMountData.getOrDefault(player, new HashMap<>());
        mountData.put(data.getName(), data);
        playerMountData.put(player, mountData);
        List<Player> players = getAroundPlayers(player);
        aroundPlayers.put(player, players);
        spawnArtifact(player, players, data);
    }

    public void removeArmorStandMount(Player player, ASMountData data) {
        removeArmorStandMount(player, data.getName());
    }

    public void removeArmorStandMount(Player player, String name) {
        if(playerMountData.containsKey(player)) {
            Map<String, ASMountData> mountData = playerMountData.get(player);
            ASMountData data = mountData.remove(name);
            if(data != null) {
                despawnArtifact(getAroundPlayers(player), data);
            }
        }
    }

    public void hideArmorStandMounts(Player player, Player viewer) {
        excludeView.add(Pair.of(player, viewer));
        if(playerMountData.containsKey(player)) {
            despawnArtifact(Collections.singletonList(viewer), playerMountData.get(player).values().toArray(new ASMountData[0]));
        }
    }

    public void showArtifact(Player player, Player viewer) {
        excludeView.remove(Pair.of(player, viewer));
        if(playerMountData.containsKey(player)) {
            spawnArtifact(player, Collections.singletonList(viewer), playerMountData.get(player).values().toArray(new ASMountData[0]));
        }
    }

    private List<Packet<?>> getRefreshPackets(Player player) {
        Location loc = player.getLocation();
        byte yaw = (byte)(loc.getYaw() * 256.0f / 360.0f);
        List<ASMountData> mountData = new ArrayList<>(playerMountData.get(player).values());
        List<Packet<?>> packets = new ArrayList<>();
        List<Entity> mounts = new ArrayList<>();
        for (ASMountData data : mountData) {
            ArmorStand armorStand = data.getArmorStand();
            mounts.add(armorStand);
            ClientboundRotateHeadPacket rotateHeadPacket = new ClientboundRotateHeadPacket(armorStand, yaw);
            packets.add(rotateHeadPacket);
        }
        ServerPlayer serverPlayer = ((CraftPlayer)player).getHandle();
        serverPlayer.passengers = ImmutableList.copyOf(mounts);
        ClientboundSetPassengersPacket setPassengersPacket = new ClientboundSetPassengersPacket(serverPlayer);
        packets.add(setPassengersPacket);
        return packets;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player player = e.getPlayer();
        if(playerMountData.containsKey(player)) {
            List<Packet<?>> packets = getRefreshPackets(player);
            sendPacketsToPlayers(getAroundPlayers(player), packets.toArray(new Packet[0]));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        if(playerMountData.containsKey(player)) {
            despawnArtifact(aroundPlayers.get(player), playerMountData.get(player).values().toArray(new ASMountData[0]));
            playerMountData.remove(player);
            aroundPlayers.remove(player);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player player = e.getEntity();
        if(playerMountData.containsKey(player)) {
            despawnArtifact(aroundPlayers.get(player), playerMountData.get(player).values().toArray(new ASMountData[0]));
            aroundPlayers.remove(player);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player player = e.getPlayer();
        if(playerMountData.containsKey(player) && !excludeView.contains(Pair.of(player, player))) {
            spawnArtifact(player, List.of(player), playerMountData.get(player).values().toArray(new ASMountData[0]));
        }
    }
}
