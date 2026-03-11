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
    private final Map<UUID, Integer> waveMap = new HashMap<>();
    private final Set<UUID> guards = new HashSet<>();
    private final Map<UUID, Location> respawnMap = new HashMap<>();
    private NamespacedKey key;

    @Override
    public void onEnable() {
        this.key = new NamespacedKey(this, "skalka");
        if (getCommand("skalka") != null) {
            getCommand("skalka").setExecutor(this);
        }
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Plugin SkalkaNetherowa zostal wlaczony!");
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] args) {
        if (!(s instanceof Player p) || !p.isOp()) return true;
        if (args.length > 0 && args[0].equalsIgnoreCase("set")) {
            spawn(p.getLocation());
            p.sendMessage("§a§l[!] §7Postawiono skalke (15 fal)!");
        }
        return true;
    }

    private void spawn(Location loc) {
        EnderCrystal crystal = loc.getWorld().spawn(loc, EnderCrystal.class);
        crystal.setShowingBottom(true);
        crystal.setCustomName("§c§lSkalka Netherowa §8[§eFala 1/15§8]");
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

        guards.removeIf(id -> Bukkit.getEntity(id) == null || Bukkit.getEntity(id).isDead());
        
        if (!guards.isEmpty()) {
            p.sendMessage("§c§l[!] §7Najpierw zabij straznikow fali §e" + waveMap.get(crystal.getUniqueId()) + "§c!");
            return;
        }

        UUID id = crystal.getUniqueId();
        int hp = hpMap.getOrDefault(id, 4) - 1;
        hpMap.put(id, hp);

        if (hp <= 0) {
            int currentWave = waveMap.getOrDefault(id, 1);
            spawnWave(crystal, currentWave);
            p.sendMessage("§6§l[!] §eNadchodzi FALA " + currentWave + "!");
            hpMap.put(id, 4);
        } else {
            p.sendMessage("§e§l[!] §7HP: §6" + hp + "/4 §7(Fala: §e" + waveMap.get(id) + "§7)");
        }
    }

    private void spawnWave(EnderCrystal crystal, int waveNumber) {
        Location loc = crystal.getLocation();
        int mobCount = 3 + (waveNumber / 2); 
        
        for (int i = 0; i < mobCount; i++) {
            EntityType type = (Math.random() < 0.6) ? EntityType.WITHER_SKELETON : EntityType.BLAZE;
            Entity m = loc.getWorld().spawnEntity(loc.clone().add(Math.random()*2-1, 0, Math.random()*2-1), type);
            if (m instanceof LivingEntity) {
                ((LivingEntity) m).setCustomName("§4Straznik Fali " + waveNumber);
            }
            guards.add(m.getUniqueId());
        }
        crystal.setCustomName("§c§lSkalka §8[§eWALKA: FALA " + waveNumber + "§8]");
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (!guards.contains(e.getEntity().getUniqueId())) return;
        guards.remove(e.getEntity().getUniqueId());

        if (guards.isEmpty()) {
            for (Entity ent : e.getEntity().getNearbyEntities(15, 15, 15)) {
                if (ent instanceof EnderCrystal cr && cr.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                    UUID id = cr.getUniqueId();
                    int currentWave = waveMap.getOrDefault(id, 1);
                    if (currentWave >= 15) {
                        completeSkałka(cr);
                    } else {
                        waveMap.put(id, currentWave + 1);
                        cr.setCustomName("§c§lSkalka §8[§eUderz! Fala " + (currentWave + 1) + "/15§8]");
                    }
                    break;
                }
            }
        }
    }

    private void completeSkałka(EnderCrystal cr) {
        Location loc = cr.getLocation();
        Location rLoc = respawnMap.get(cr.getUniqueId());
        dropLoot(loc);
        cr.remove();
        hpMap.remove(cr.getUniqueId());
        waveMap.remove(cr.getUniqueId());
        Bukkit.broadcastMessage("§6§l[SKAŁKA] §eZniszczona! Odrodzi sie za 30 min.");
        Bukkit.getScheduler().runTaskLater(this, () -> spawn(rLoc), 36000L);
    }

    private void dropLoot(Location l) {
        l.getWorld().dropItemNaturally(l, new ItemStack(Material.NETHERITE_SCRAP, 2));
        l.getWorld().dropItemNaturally(l, new ItemStack(Material.DIAMOND, 5));
        l.getWorld().dropItemNaturally(l, new ItemStack(Material.GOLDEN_APPLE, 2));
        l.getWorld().dropItemNaturally(l, new ItemStack(Material.GHAST_TEAR, 3));
        l.getWorld().dropItemNaturally(l, new ItemStack(Material.WITHER_SKELETON_SKULL, 1));
    }
}
