package pl.twojserwer.skalki;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

public class SkalkaNetherowa extends JavaPlugin implements Listener, CommandExecutor {
    private final Map<UUID, Integer> hpMap = new HashMap<>();
    private final Map<UUID, Integer> waveMap = new HashMap<>();
    private final Set<UUID> guards = new HashSet<>();
    private final Map<String, Location> respawnLocs = new HashMap<>();
    private NamespacedKey key;

    @Override
    public void onEnable() {
        this.key = new NamespacedKey(this, "skalka_system");
        
        // Rejestracja komendy - jesli tu jest blad, plugin sie nie wylaczy
        try {
            PluginCommand cmd = getCommand("skalka");
            if (cmd != null) cmd.setExecutor(this);
        } catch (Exception e) {
            getLogger().warning("Nie udalo sie zarejestrowac komendy w systemie.");
        }

        getServer().getPluginManager().registerEvents(this, this);
        
        // Bezpieczne ladowanie configu
        try {
            saveDefaultConfig();
            if (getConfig().getConfigurationSection("locs") != null) {
                for (String id : getConfig().getConfigurationSection("locs").getKeys(false)) {
                    Location l = getConfig().getLocation("locs." + id);
                    if (l != null && l.getWorld() != null) {
                        respawnLocs.put(id, l);
                        spawnCrystal(l, id);
                    }
                }
            }
        } catch (Exception e) {
            getLogger().warning("Config jest pusty lub uszkodzony - pomijam ladowanie skale.");
        }
        
        getLogger().info("PLUGIN SKALKA URUCHOMIONY!");
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String lb, String[] args) {
        if (!(s instanceof Player p) || !p.isOp()) return true;

        if (args.length > 0 && args[0].equalsIgnoreCase("set")) {
            String id = UUID.randomUUID().toString().substring(0, 8);
            Location loc = p.getLocation();
            
            respawnLocs.put(id, loc);
            getConfig().set("locs." + id, loc);
            saveConfig();
            
            spawnCrystal(loc, id);
            p.sendMessage("§a§l[!] §7Skalka ustawiona!");
            return true;
        }
        return true;
    }

    private void spawnCrystal(Location loc, String id) {
        if (loc == null || loc.getWorld() == null) return;
        EnderCrystal crystal = (EnderCrystal) loc.getWorld().spawnEntity(loc, EntityType.ENDER_CRYSTAL);
        crystal.setShowingBottom(true);
        crystal.setCustomName("§c§lSkalka §8[§eFala 1/15§8]");
        crystal.setCustomNameVisible(true);
        crystal.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
        hpMap.put(crystal.getUniqueId(), 4);
        waveMap.put(crystal.getUniqueId(), 1);
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof EnderCrystal crystal)) return;
        if (!crystal.getPersistentDataContainer().has(key, PersistentDataType.STRING)) return;
        
        e.setCancelled(true);
        if (!(e.getDamager() instanceof Player p)) return;

        guards.removeIf(uuid -> Bukkit.getEntity(uuid) == null || Bukkit.getEntity(uuid).isDead());
        if (!guards.isEmpty()) {
            p.sendMessage("§c§l[!] §7Pokonaj straznikow!");
            return;
        }

        UUID uid = crystal.getUniqueId();
        int hp = hpMap.getOrDefault(uid, 4) - 1;
        hpMap.put(uid, hp);

        if (hp <= 0) {
            int wave = waveMap.getOrDefault(uid, 1);
            spawnWave(crystal, wave);
            p.sendMessage("§6§l[!] §eFALA " + wave + "!");
            hpMap.put(uid, 4);
        } else {
            p.sendMessage("§e§l[!] §7Moc: §6" + hp + "/4 §7(Fala: " + waveMap.get(uid) + "/15)");
        }
    }

    private void spawnWave(EnderCrystal crystal, int wave) {
        Location l = crystal.getLocation();
        int count = 3 + (wave / 2);
        for (int i = 0; i < count; i++) {
            EntityType t = (Math.random() < 0.6) ? EntityType.WITHER_SKELETON : EntityType.BLAZE;
            Entity m = l.getWorld().spawnEntity(l.clone().add(Math.random()*2-1, 0, Math.random()*2-1), t);
            if (m instanceof LivingEntity le) le.setCustomName("§4Straznik " + wave);
            guards.add(m.getUniqueId());
        }
        crystal.setCustomName("§c§lSkalka §8[§eWALKA: FALA " + wave + "§8]");
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (!guards.contains(e.getEntity().getUniqueId())) return;
        guards.remove(e.getEntity().getUniqueId());
        if (guards.isEmpty()) {
            for (Entity ent : e.getEntity().getNearbyEntities(15, 15, 15)) {
                if (ent instanceof EnderCrystal cr && cr.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                    String lid = cr.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                    int wave = waveMap.getOrDefault(cr.getUniqueId(), 1);
                    if (wave >= 15) {
                        finish(cr, lid);
                    } else {
                        waveMap.put(cr.getUniqueId(), wave + 1);
                        cr.setCustomName("§c§lSkalka §8[§eUderz! Fala " + (wave+1) + "/15§8]");
                    }
                    break;
                }
            }
        }
    }

    private void finish(EnderCrystal cr, String lid) {
        Location loc = respawnLocs.get(lid);
        Location dropLoc = cr.getLocation();
        dropLoot(dropLoc);
        cr.remove();
        hpMap.remove(cr.getUniqueId());
        waveMap.remove(cr.getUniqueId());
        Bukkit.broadcastMessage("§6§l[SKAŁKA] §eZniszczona! Odrodzi sie za 30 min.");
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (loc != null) spawnCrystal(loc, lid);
        }, 36000L);
    }

    private void dropLoot(Location l) {
        l.getWorld().dropItemNaturally(l, new ItemStack(Material.NETHERITE_SCRAP, 2));
        l.getWorld().dropItemNaturally(l, new ItemStack(Material.DIAMOND, 5));
        l.getWorld().dropItemNaturally(l, new ItemStack(Material.GOLDEN_APPLE, 2));
        l.getWorld().dropItemNaturally(l, new ItemStack(Material.GHAST_TEAR, 3));
        l.getWorld().dropItemNaturally(l, new ItemStack(Material.BLAZE_ROD, 6));
    }
}
