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
    // Używamy Stringa jako klucza lokalizacji, aby był trwały
    private final Map<String, Location> respawnLocs = new HashMap<>();
    private NamespacedKey key;

    @Override
    public void onEnable() {
        this.key = new NamespacedKey(this, "skalka");
        if (getCommand("skalka") != null) {
            getCommand("skalka").setExecutor(this);
        }
        getServer().getPluginManager().registerEvents(this, this);
        
        // Tworzenie folderu na config, jeśli nie istnieje
        saveDefaultConfig();
        loadLocationsFromConfig();
        
        getLogger().info("Plugin SkalkaNetherowa zostal wlaczony i wczytal lokacje!");
    }

    private void loadLocationsFromConfig() {
        if (getConfig().getConfigurationSection("locs") == null) return;
        for (String id : getConfig().getConfigurationSection("locs").getKeys(false)) {
            Location loc = getConfig().getLocation("locs." + id);
            if (loc != null) {
                respawnLocs.put(id, loc);
                spawn(loc, id);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] args) {
        if (!(s instanceof Player p) || !p.isOp()) return true;
        if (args.length > 0 && args[0].equalsIgnoreCase("set")) {
            String randomID = UUID.randomUUID().toString().substring(0, 8);
            Location loc = p.getLocation();
            
            respawnLocs.put(randomID, loc);
            getConfig().set("locs." + randomID, loc);
            saveConfig();
            
            spawn(loc, randomID);
            p.sendMessage("§a§l[!] §7Skałka postawiona i zapisana w configu!");
            return true;
        }
        return false;
    }

    private void spawn(Location loc, String locID) {
        World world = loc.getWorld();
        if (world == null) return;

        EnderCrystal crystal = world.spawn(loc, EnderCrystal.class);
        crystal.setShowingBottom(true);
        crystal.setCustomName("§c§lSkałka Netherowa §8[§eFala 1/15§8]");
        crystal.setCustomNameVisible(true);
        
        // Zapisujemy ID lokalizacji w samym krysztale
        crystal.getPersistentDataContainer().set(key, PersistentDataType.STRING, locID);
        
        UUID entityID = crystal.getUniqueId();
        hpMap.put(entityID, 4);
        waveMap.put(entityID, 1);
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof EnderCrystal crystal)) return;
        if (!crystal.getPersistentDataContainer().has(key, PersistentDataType.STRING)) return;
        
        e.setCancelled(true);
        if (!(e.getDamager() instanceof Player p)) return;

        guards.removeIf(id -> Bukkit.getEntity(id) == null || Bukkit.getEntity(id).isDead());
        
        if (!guards.isEmpty()) {
            p.sendMessage("§c§l[!] §7Najpierw zabij strażników!");
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
                ((LivingEntity) m).setCustomName("§4Strażnik Fali " + waveNumber);
            }
            guards.add(m.getUniqueId());
        }
        crystal.setCustomName("§c§lSkałka §8[§eWALKA: FALA " + waveNumber + "§8]");
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (!guards.contains(e.getEntity().getUniqueId())) return;
        guards.remove(e.getEntity().getUniqueId());

        if (guards.isEmpty()) {
            for (Entity ent : e.getEntity().getNearbyEntities(15, 15, 15)) {
                if (ent instanceof EnderCrystal cr && cr.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                    String locID = cr.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                    int currentWave = waveMap.getOrDefault(cr.getUniqueId(), 1);

                    if (currentWave >= 15) {
                        finishSkałka(cr, locID);
                    } else {
                        waveMap.put(cr.getUniqueId(), currentWave + 1);
                        cr.setCustomName("§c§lSkałka §8[§eUderz! Fala " + (currentWave + 1) + "/15§8]");
                    }
                    break;
                }
            }
        }
    }

    private void finishSkałka(EnderCrystal cr, String locID) {
        Location dropLoc = cr.getLocation();
        Location respawnLoc = respawnLocs.get(locID);
        
        dropLoot(dropLoc);
        
        hpMap.remove(cr.getUniqueId());
        waveMap.remove(cr.getUniqueId());
        cr.remove();
        
        Bukkit.broadcastMessage("§6§l[SKAŁKA] §eZniszczona! Odrodzi się za 30 min.");
        
        // Odliczanie (30 minut = 36000 ticków)
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (respawnLoc != null) {
                spawn(respawnLoc, locID);
            }
        }, 36000L);
    }

    private void dropLoot(Location l) {
        World w = l.getWorld();
        if (w == null) return;
        w.dropItemNaturally(l, new ItemStack(Material.NETHERITE_SCRAP, 2));
        w.dropItemNaturally(l, new ItemStack(Material.DIAMOND, 5));
        w.dropItemNaturally(l, new ItemStack(Material.GOLDEN_APPLE, 2));
        w.dropItemNaturally(l, new ItemStack(Material.GHAST_TEAR, 3));
        w.dropItemNaturally(l, new ItemStack(Material.BLAZE_ROD, 6));
        // Głowa witherowego szkieleta została usunięta z tej listy
    }
}
