package com.oldschoolminecraft.blastpass;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import net.minecraft.server.EntityPlayer;
import net.minecraft.server.EntityHuman;
import net.minecraft.server.ItemInWorldManager;
import net.minecraft.server.World;
import net.minecraft.server.WorldServer;

import com.oldschoolminecraft.OSMEss.OSMEss;
import com.oldschoolminecraft.OSMEss.Handlers.PlaytimeHandler;
import org.bukkit.OfflinePlayer;

import java.io.File;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.logging.Logger;

public class BlastPass extends JavaPlugin {
    
    private OSMEss osmEss;
    private long requiredPlaytime = 3600000; // Default: 1 hour in milliseconds
    private Logger log;
    
    @Override
    public void onEnable() {
        log = Logger.getLogger("Minecraft");
        log.info("BlastPass Plugin with OSM-Ess Integration Enabled!");
        
        // Hook into OSM-Ess
        osmEss = (OSMEss) getServer().getPluginManager().getPlugin("OSM-Ess");
        if (osmEss == null) {
            log.severe("OSM-Ess not found! BlastPass requires OSM-Ess to function.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Create config directory if it doesn't exist
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        
        loadConfig();
        
        // Register player join listener to hook ItemInWorldManager
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvent(Event.Type.PLAYER_JOIN, new PlayerJoinListener(), Event.Priority.Normal, this);
        
        // Hook existing players
        for (Player player : getServer().getOnlinePlayers()) {
            hookPlayer(player);
        }
        
        log.info("Required playtime: " + (requiredPlaytime / 60000) + " minutes");
        log.info("Pure MNS ItemInWorldManager hooking active!");
    }
    
    @Override
    public void onDisable() {
        saveConfig();
        log.info("BlastPass Plugin Disabled!");
    }
    
    private void loadConfig() {
        File configFile = new File(getDataFolder(), "config.txt");
        
        if (!configFile.exists()) {
            // Create default config
            log.info("Creating default config file...");
            requiredPlaytime = 3600000; // 1 hour default
            saveConfig();
            return;
        }
        
        try {
            BufferedReader reader = new BufferedReader(new FileReader(configFile));
            String line;
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                
                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                // Parse playtime requirement (in minutes)
                if (line.startsWith("required-playtime-minutes:")) {
                    try {
                        int minutes = Integer.parseInt(line.substring(26).trim());
                        requiredPlaytime = minutes * 60000L;
                        log.info("Loaded required playtime: " + minutes + " minutes");
                    } catch (NumberFormatException e) {
                        log.warning("Invalid playtime value, using default: 60 minutes");
                        requiredPlaytime = 3600000;
                    }
                }
            }
            
            reader.close();
            log.info("Config loaded successfully!");
        } catch (IOException e) {
            log.severe("Failed to load config: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void saveConfig() {
        File configFile = new File(getDataFolder(), "config.txt");
        
        try {
            FileWriter writer = new FileWriter(configFile);
            
            writer.write("# BlastPass Configuration\n");
            writer.write("# Required playtime in minutes before players can use TNT\n");
            writer.write("required-playtime-minutes: " + (requiredPlaytime / 60000) + "\n");
            
            writer.close();
            log.info("Config saved successfully!");
        } catch (IOException e) {
            log.severe("Failed to save config: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void hookPlayer(Player player) {
        try {
            CraftPlayer craftPlayer = (CraftPlayer) player;
            EntityPlayer entityPlayer = craftPlayer.getHandle();
            
            // Replace ItemInWorldManager with our custom one
            CustomItemInWorldManager customManager = new CustomItemInWorldManager(this, (WorldServer) entityPlayer.world);
            customManager.player = entityPlayer;
            entityPlayer.itemInWorldManager = customManager;
            
            log.info("Hooked ItemInWorldManager for player: " + player.getName());
        } catch (Exception e) {
            log.severe("Failed to hook player " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public boolean hasEnoughPlaytime(String playerName) {
        try {
            // Check if player has bypass permission
            Player onlinePlayer = getServer().getPlayer(playerName);
            if (onlinePlayer != null && onlinePlayer.hasPermission("blastpass.bypass")) {
                return true;
            }
            
            OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(playerName);
            if (offlinePlayer == null) {
                return false;
            }
            
            long playtimeMillis = osmEss.playtimeHandler.getTotalPlayTimeInMillis(offlinePlayer);
            
            return playtimeMillis >= requiredPlaytime;
        } catch (Exception e) {
            log.warning("Failed to check playtime for " + playerName + ": " + e.getMessage());
            return false;
        }
    }
    
    public long getPlayerPlaytime(String playerName) {
        try {
            OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(playerName);
            if (offlinePlayer == null) {
                return 0;
            }
            
            return osmEss.playtimeHandler.getTotalPlayTimeInMillis(offlinePlayer);
        } catch (Exception e) {
            log.warning("Failed to get playtime for " + playerName + ": " + e.getMessage());
            return 0;
        }
    }
    
    public long getRequiredPlaytime() {
        return requiredPlaytime;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("blastpass")) {
            if (!sender.hasPermission("blastpass.admin")) {
                sender.sendMessage("§cYou don't have permission to use this command!");
                return true;
            }
            
            if (args.length < 1) {
                sender.sendMessage("§cUsage: /blastpass <settime|check> [minutes|player]");
                return true;
            }
            
            String action = args[0].toLowerCase();
            
            if (action.equals("settime")) {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /blastpass settime <minutes>");
                    return true;
                }
                
                try {
                    int minutes = Integer.parseInt(args[1]);
                    requiredPlaytime = minutes * 60000L;
                    saveConfig();
                    sender.sendMessage("§a[BlastPass] Required playtime set to " + minutes + " minutes");
                    log.info(sender.getName() + " set required playtime to " + minutes + " minutes");
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c[BlastPass] Invalid number!");
                }
                return true;
            }
            
            if (action.equals("check")) {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /blastpass check <player>");
                    return true;
                }
                
                String playerName = args[1];
                long playtime = getPlayerPlaytime(playerName);
                long playtimeMinutes = playtime / 60000;
                long requiredMinutes = requiredPlaytime / 60000;
                boolean hasAccess = playtime >= requiredPlaytime;
                
                sender.sendMessage("§e[BlastPass] Player: " + playerName);
                sender.sendMessage("§e  Playtime: " + playtimeMinutes + " minutes");
                sender.sendMessage("§e  Required: " + requiredMinutes + " minutes");
                sender.sendMessage(hasAccess ? "§a  Status: Can use TNT" : "§c  Status: Cannot use TNT");
                return true;
            }
            
            sender.sendMessage("§cUsage: /blastpass <settime|check> [minutes|player]");
            return true;
        }
        
        return false;
    }
    
    private class PlayerJoinListener extends PlayerListener {
        @Override
        public void onPlayerJoin(PlayerJoinEvent event) {
            // Hook the player's ItemInWorldManager when they join
            final Player player = event.getPlayer();
            getServer().getScheduler().scheduleSyncDelayedTask(BlastPass.this, new Runnable() {
                public void run() {
                    hookPlayer(player);
                }
            }, 1L);
        }
    }
    
    private class CustomItemInWorldManager extends ItemInWorldManager {
        private final BlastPass plugin;
        
        public CustomItemInWorldManager(BlastPass plugin, WorldServer world) {
            super(world);
            this.plugin = plugin;
        }
        
        @Override
        public boolean interact(EntityHuman entityhuman, World world, net.minecraft.server.ItemStack itemstack, int x, int y, int z, int face) {
            // Check if it's TNT (block ID 46)
            if (itemstack != null && itemstack.id == 46) {
                // Check if player has enough playtime
                if (!plugin.hasEnoughPlaytime(entityhuman.name)) {
                    long currentPlaytime = plugin.getPlayerPlaytime(entityhuman.name);
                    long requiredPlaytime = plugin.getRequiredPlaytime();
                    long remainingTime = requiredPlaytime - currentPlaytime;
                    long remainingMinutes = remainingTime / 60000;
                    
                    // BLOCK the placement - return false
                    entityhuman.a("§c[BlastPass] You need " + remainingMinutes + " more minutes of playtime to use TNT");
                    plugin.log.info("BLOCKED " + entityhuman.name + " from placing TNT (insufficient playtime) at " + 
                        x + ", " + y + ", " + z + " via MNS ItemInWorldManager");
                    return false; // Block placement at MNS level
                }
            }
            
            // Allow normal placement
            return super.interact(entityhuman, world, itemstack, x, y, z, face);
        }
    }
}
