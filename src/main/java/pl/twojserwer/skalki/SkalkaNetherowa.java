package pl.twojserwer.skalki;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class SkalkaNetherowa extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<UUID, Integer> hpMap = new HashMap<>();
    private final Map<UUID, Integer> waveMap = new HashMap<>(); // Przechowuje numer fali
    private final Set<UUID> guards = new HashSet<>();
    private final Map<UUID, Location> respawnMap = new HashMap<>();
    private NamespacedKey key;

    @Override
    public void onEnable() {
        this.key = new NamespacedKey(this, "skalka");
        getCommand("skalka").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] args) {
        if (!(s instanceof Player p) || !p.isOp()) return true;
        if (args.length > 0 && args[0].equalsIgnoreCase("set")) {
            spawn(p.getLocation());
            p.sendMessage("§a§l[!] §7Postawiono epicką skałkę (15 fal)!");
        }
        return true;
    }

    private void spawn(Location loc) {
        EnderCrystal crystal = loc.getWorld().spawn(loc, EnderCrystal.class);
        crystal.setShowingBottom(true);
        crystal.setCustomName("§c§lSkałka Netherowa §8[§eFala 1/15§8]");
        crystal.setCustomNameVisible(true);
        crystal.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        
        UUID id = crystal.getUniqueId();
        hpMap.put(id, 4);
        waveMap.put(id, 1);
        respawnMap.put(id, loc.clone());
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof EnderCrystal crystal)) return;
        if (!crystal.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) return;
        
        e.setCancelled(true);
        if (!(e.getDamager() instanceof Player p)) return;

        // Czyścimy listę strażników z nieistniejących mobów
        guards.removeIf(id -> Bukkit.getEntity(id) == null || Bukkit.getEntity(id).isDead());
        
        if (!guards.isEmpty()) {
            p.sendMessage("§c§l[!] §7Musisz pokonać strażników fali §e" + waveMap.get(crystal.getUniqueId()) + "§c!");
            return;
        }

        UUID id = crystal.getUniqueId();
        int hp = hpMap.getOrDefault(id, 4) - 1;
        hpMap.put(id, hp);

        if (hp <= 0) {
            int currentWave = waveMap.getOrDefault(id,
