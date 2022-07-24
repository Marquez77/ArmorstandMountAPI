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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class ASMountAPI extends JavaPlugin {

    protected final Map<Player, Map<String, ASMountData>> playerMountData = new HashMap<>();
    protected final Set<Pair<Player, Player>> excludeView = new HashSet<>();
    protected final Map<Player, List<Player>> aroundPlayers = new HashMap<>();

    private double entityRange = 30;

    @Getter
    private static ASMountAPI instance;

    protected List<Player> getAroundPlayers(Player player) {
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

    protected void sendPacketsToPlayers(List<Player> players, Packet<?>... packets) {
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
        getServer().getPluginManager().registerEvents(new ASMountListener(this), this);
        getServer().getScheduler().scheduleAsyncRepeatingTask(this, () -> playerMountData.forEach((player, mountData) -> {
            if(!player.isDead()) {
                try {
                    List<Player> prevAroundPlayers = aroundPlayers.getOrDefault(player, null);
                    List<Player> players = getAroundPlayers(player);
                    aroundPlayers.put(player, new ArrayList<>(players));
                    if(prevAroundPlayers != null) {
                        List<Player> temp = new ArrayList<>(prevAroundPlayers);
                        players.forEach(temp::remove);
                        if(!temp.isEmpty()) despawnArmorStands(temp, mountData.values().toArray(new ASMountData[0]));
                        prevAroundPlayers.forEach(players::remove);
                    }
                    if(!players.isEmpty()) spawnArmorStands(player, players, mountData.values().toArray(new ASMountData[0]));
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

    protected void spawnArmorStands(Player player, List<Player> players, ASMountData... mountData) {
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

    protected void despawnArmorStands(List<Player> players, ASMountData... mountData) {
        if(players == null || players.isEmpty()) return;
        int[] ids = new int[mountData.length];
        for(int i = 0; i < mountData.length; i++) ids[i] = mountData[i].getArmorStand().getId();
        ClientboundRemoveEntitiesPacket removeEntitiesPacket = new ClientboundRemoveEntitiesPacket(ids);
        sendPacketsToPlayers(players, removeEntitiesPacket);
    }

    /**
     * 플레이어에게 새로운 아머스탠드를 추가합니다.
     *
     * @param player 대상 플레이어
     * @param data 아머스탠드 데이터
     */
    public void addArmorStandMount(Player player, ASMountData data) {
        Location location = player.getLocation();
        ArmorStand armorStand = new ArmorStand(((CraftWorld)location.getWorld()).getHandle(), location.getX(), location.getY(), location.getZ());
        armorStand.setInvisible(true);
        armorStand.setMarker(true);
        armorStand.setSmall(data.isSmall());
        data.setArmorStand(armorStand);
        Map<String, ASMountData> mountData = playerMountData.getOrDefault(player, new HashMap<>());
        if(mountData.containsKey(data.getName())) removeArmorStandMount(player, data.getName());
        mountData.put(data.getName(), data);
        playerMountData.put(player, mountData);
        List<Player> players = getAroundPlayers(player);
        aroundPlayers.put(player, players);
        spawnArmorStands(player, players, data);
    }

    /**
     * 플레이어로부터 아머스탠드를 제거합니다.
     * 데이터의 이름만을 비교하여 추가되어 있는 데이터를 제거합니다.
     *
     * @param player 대상 플레이어
     * @param data 제거할 아머스탠드 데이터
     */
    public void removeArmorStandMount(Player player, ASMountData data) {
        removeArmorStandMount(player, data.getName());
    }

    /**
     * 플레이어로부터 아머스탠드를 제거합니다.
     *
     * @param player 대상 플레이어
     * @param name 제거할 아머스탠드 이름
     */
    public void removeArmorStandMount(Player player, String name) {
        if(playerMountData.containsKey(player)) {
            Map<String, ASMountData> mountData = playerMountData.get(player);
            ASMountData data = mountData.remove(name);
            if(data != null) {
                despawnArmorStands(getAroundPlayers(player), data);
            }
        }
    }

    /**
     * 플레이어의 아머스탠드를 보이지 않도록 합니다.
     *
     * @param player 대상 플레이어
     * @param viewer 보이지 않게 할 플레이어
     */
    public void hideArmorStandMounts(Player player, Player viewer) {
        excludeView.add(Pair.of(player, viewer));
        if(playerMountData.containsKey(player)) {
            despawnArmorStands(Collections.singletonList(viewer), playerMountData.get(player).values().toArray(new ASMountData[0]));
        }
    }

    /**
     * 플레이어의 아머스탠드를 보이게 합니다.
     *
     * @param player 대상 플레이어
     * @param viewer 보이게 할 플레이어
     */
    public void showArmorStandMounts(Player player, Player viewer) {
        excludeView.remove(Pair.of(player, viewer));
        if(playerMountData.containsKey(player)) {
            spawnArmorStands(player, Collections.singletonList(viewer), playerMountData.get(player).values().toArray(new ASMountData[0]));
        }
    }

    protected List<Packet<?>> getRefreshPackets(Player player) {
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
}
