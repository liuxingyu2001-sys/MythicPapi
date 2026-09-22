package com.liu.mythicpapi;

import com.liu.mythicpapi.command.MythicPapiCommand;
import com.liu.mythicpapi.hologram.HologramManager;
import com.liu.mythicpapi.hook.MythicMobsHook;
import com.liu.mythicpapi.papi.MythicPapiExpansion;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Bridges MythicMobs spawner timers to PlaceholderAPI. */
public final class MythicPapiPlugin extends JavaPlugin {

    private MythicMobsHook mythicMobsHook;
    private MythicPapiExpansion expansion;
    private HologramManager holograms;
    private BukkitTask hologramRefreshTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        mythicMobsHook = new MythicMobsHook(this);
        holograms = new HologramManager(this);
        Bukkit.getPluginManager().registerEvents(holograms, this);

        if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            getLogger().warning("MythicMobs is not enabled; spawner placeholders will return empty values.");
        }

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            expansion = new MythicPapiExpansion(mythicMobsHook, getDescription().getVersion());
            expansion.register();
            getLogger().info("Registered PlaceholderAPI expansion: mythicpapi");
        } else {
            getLogger().warning("PlaceholderAPI is not enabled; no placeholders were registered.");
        }

        MythicPapiCommand command = new MythicPapiCommand(this, holograms, mythicMobsHook);
        getCommand("mythicpapi").setExecutor(command);
        getCommand("mythicpapi").setTabCompleter(command);
        holograms.load();
        holograms.refresh();
        hologramRefreshTask = Bukkit.getScheduler().runTaskTimer(this, holograms::refresh, 20L, 20L);
    }

    @Override
    public void onDisable() {
        if (hologramRefreshTask != null) {
            hologramRefreshTask.cancel();
            hologramRefreshTask = null;
        }
        if (holograms != null) {
            holograms.removeAll();
            holograms = null;
        }
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
        mythicMobsHook = null;
    }
}
