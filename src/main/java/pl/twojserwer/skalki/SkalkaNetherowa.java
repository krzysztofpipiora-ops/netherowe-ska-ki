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
            p.sendMessage("§aPostawiono skalke!");
        }
        return true;
    }

    private void spawn(Location loc) {
        EnderCrystal crystal = loc.getWorld().spawn(loc, EnderCrystal.class);
        crystal.setShowingBottom(true);
        crystal.setCustomName("§c§lSkalka Netherowa");
        crystal.setCustomNameVisible(true);
        crystal.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        hpMap.put(crystal.getUniqueId(), 4);
        respawnMap.put(crystal.getUniqueId(), loc.clone());
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof EnderCrystal crystal)) return;
        if (!crystal.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) return;
        
        e.setCancelled(true);
        if (!(e.getDamager() instanceof Player p)) return;

        guards.removeIf(id -> Bukkit.getEntity(id) == null || Bukkit.getEntity(id).isDead());
        if (!guards.isEmpty()) {
            p.sendMessage("§cZabij straznikow!");
            return;
        }

        int hp = hpMap.getOrDefault(crystal.getUniqueId(), 4) - 1;
        hpMap.put(crystal.getUniqueId(), hp);

        if (hp <= 0) {
            for (int i = 0; i < 3; i++) {
                Entity m = crystal.getLocation().getWorld().spawnEntity(crystal.getLocation().add(1,0,1), 
                    (i % 2 == 0) ? EntityType.WITHER_SKELETON : EntityType.BLAZE);
                guards.add(m.getUniqueId());
            }
            p.sendMessage("§6Straznicy sie pojawili!");
        } else {
            p.sendMessage("§eHP: " + hp + "/4");
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (guards.contains(e.getEntity().getUniqueId())) {
            guards.remove(e.getEntity().getUniqueId());
            if (guards.isEmpty()) {
                for (Entity ent : e.getEntity().getNearbyEntities(10, 10, 10)) {
                    if (ent instanceof EnderCrystal cr && cr.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                        drop(cr.getLocation());
                        Location loc = respawnMap.get(cr.getUniqueId());
                        cr.remove();
                        Bukkit.broadcastMessage("§eSkalka rozbita! Odnowi sie za 30 min.");
                        Bukkit.getScheduler().runTaskLater(this, () -> spawn(loc), 36000L);
                        break;
                    }
                }
            }
        }
    }

    private void drop(Location l) {
        l.getWorld().dropItemNaturally(l, new ItemStack(Material.DIAMOND, 2));
        l.getWorld().dropItemNaturally(l, new ItemStack(Material.GHAST_TEAR, 1));
        l.getWorld().dropItemNaturally(l, new ItemStack(Material.BLAZE_ROD, 2));
        l.getWorld().dropItemNaturally(l, new ItemStack(Material.GOLD_INGOT, 4));
        if (Math.random() < 0.1) l.getWorld().dropItemNaturally(l, new ItemStack(Material.NETHERITE_SCRAP, 1));
    }
}
