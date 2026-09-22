package com.liu.mythicpapi.hologram;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Creates and refreshes the plugin-owned native TextDisplay holograms. */
public final class HologramManager implements Listener {

    private static final String ROOT = "holograms";

    private final Plugin plugin;
    private final NamespacedKey hologramKey;
    private final Map<String, HologramDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, List<TextDisplay>> displays = new LinkedHashMap<>();

    public HologramManager(Plugin plugin) {
        this.plugin = plugin;
        this.hologramKey = new NamespacedKey(plugin, "hologram-id");
    }

    public void load() {
        removeAll();
        ConfigurationSection holograms = plugin.getConfig().getConfigurationSection(ROOT);
        if (holograms == null) {
            return;
        }

        for (String id : holograms.getKeys(false)) {
            loadDefinition(id, holograms.getConfigurationSection(id));
        }
        definitions.values().forEach(this::ensureHologram);
    }

    public boolean create(String id, String spawnerId, Location location) {
        if (location.getWorld() == null || contains(id)) {
            return false;
        }

        String path = ROOT + "." + id;
        plugin.getConfig().set(path + ".location.world", location.getWorld().getName());
        plugin.getConfig().set(path + ".location.x", location.getX());
        plugin.getConfig().set(path + ".location.y", location.getY());
        plugin.getConfig().set(path + ".location.z", location.getZ());
        plugin.getConfig().set(path + ".location.yaw", location.getYaw());
        plugin.getConfig().set(path + ".location.pitch", location.getPitch());
        plugin.getConfig().set(path + ".scale", plugin.getConfig().getDouble("hologram-scale", 1.5));
        plugin.getConfig().set(path + ".lines", List.of(
                "&6" + spawnerId,
                "&f等级: &e%mythicpapi_level_" + spawnerId + "%",
                "&f血量: &c%mythicpapi_health_" + spawnerId + "%",
                "&f数量: &e%mythicpapi_amount_" + spawnerId + "%",
                "&f冷却: &a%mythicpapi_cooldown_" + spawnerId + "%",
                "&f预热: &b%mythicpapi_warmup_" + spawnerId + "%"));
        plugin.saveConfig();
        loadDefinition(id, plugin.getConfig().getConfigurationSection(path));
        HologramDefinition definition = definitions.get(id);
        if (definition != null) {
            ensureHologram(definition);
        }
        return definition != null;
    }

    public boolean remove(String id) {
        if (!plugin.getConfig().contains(ROOT + "." + id)) {
            return false;
        }
        removeDisplays(id);
        definitions.remove(id);
        plugin.getConfig().set(ROOT + "." + id, null);
        plugin.saveConfig();
        return true;
    }

    public boolean setScale(String id, double scale) {
        if (!plugin.getConfig().contains(ROOT + "." + id)) {
            return false;
        }
        plugin.getConfig().set(ROOT + "." + id + ".scale", scale);
        plugin.saveConfig();
        load();
        refresh();
        return true;
    }

    /** Sets the default scale and applies it to every configured hologram. */
    public int setAllScales(double scale) {
        plugin.getConfig().set("hologram-scale", scale);
        ConfigurationSection holograms = plugin.getConfig().getConfigurationSection(ROOT);
        if (holograms == null) {
            plugin.saveConfig();
            return 0;
        }

        int count = 0;
        for (String id : holograms.getKeys(false)) {
            if (holograms.getConfigurationSection(id) == null) {
                continue;
            }
            plugin.getConfig().set(ROOT + "." + id + ".scale", scale);
            count++;
        }
        plugin.saveConfig();
        load();
        refresh();
        return count;
    }

    public List<String> getHologramIds() {
        ConfigurationSection holograms = plugin.getConfig().getConfigurationSection(ROOT);
        return holograms == null ? List.of() : holograms.getKeys(false).stream().sorted().toList();
    }

    public boolean contains(String id) {
        return plugin.getConfig().contains(ROOT + "." + id);
    }

    public void refresh() {
        boolean placeholderApiEnabled = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        for (HologramDefinition definition : definitions.values()) {
            ensureHologram(definition);
            List<TextDisplay> hologram = displays.get(definition.id());
            if (hologram == null) {
                continue;
            }
            for (TextDisplay display : hologram) {
                String template = display.getPersistentDataContainer().get(hologramKey, PersistentDataType.STRING);
                if (template == null || !display.isValid()) {
                    continue;
                }
                String text = placeholderApiEnabled ? PlaceholderAPI.setPlaceholders((org.bukkit.OfflinePlayer) null, template) : template;
                display.setText(color(text));
            }
        }
    }

    public void removeAll() {
        for (String id : List.copyOf(displays.keySet())) {
            removeDisplays(id);
        }
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (display.getPersistentDataContainer().has(hologramKey, PersistentDataType.STRING)) {
                    display.remove();
                }
            }
        }
        definitions.clear();
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> definitions.values().stream()
                .filter(definition -> isInChunk(definition, event.getWorld(), event.getChunk().getX(), event.getChunk().getZ()))
                .forEach(this::ensureHologram));
    }

    private void loadDefinition(String id, ConfigurationSection section) {
        if (section == null) {
            return;
        }

        Optional<Location> location = readLocation(section.getConfigurationSection("location"));
        List<String> lines = section.getStringList("lines");
        if (location.isEmpty() || lines.isEmpty()) {
            plugin.getLogger().warning("Skipping invalid hologram configuration: " + id);
            return;
        }

        double spacing = section.getDouble("line-spacing", plugin.getConfig().getDouble("hologram-line-spacing", 0.25));
        float viewRange = (float) section.getDouble("view-range", plugin.getConfig().getDouble("hologram-view-range", 32.0));
        float scale = (float) Math.clamp(section.getDouble("scale", plugin.getConfig().getDouble("hologram-scale", 1.5)), 0.1, 10.0);
        definitions.put(id, new HologramDefinition(id, location.get(), List.copyOf(lines), spacing, viewRange, scale));
    }

    private void ensureHologram(HologramDefinition definition) {
        if (isDisplayed(definition)) {
            return;
        }
        removeDisplays(definition.id());
        Location location = definition.location();
        World world = location.getWorld();
        if (world != null && world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            spawnHologram(definition);
        }
    }

    private boolean isDisplayed(HologramDefinition definition) {
        List<TextDisplay> hologram = displays.get(definition.id());
        return hologram != null && hologram.size() == definition.lines().size()
                && hologram.stream().allMatch(TextDisplay::isValid);
    }

    private void spawnHologram(HologramDefinition definition) {
        List<TextDisplay> hologram = new ArrayList<>();
        for (int index = 0; index < definition.lines().size(); index++) {
            Location lineLocation = definition.location().clone().add(0,
                    (definition.lines().size() - index - 1) * definition.spacing() * definition.scale(), 0);
            String template = definition.lines().get(index);
            TextDisplay display = lineLocation.getWorld().spawn(lineLocation, TextDisplay.class, entity -> {
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setGravity(false);
                entity.setInvulnerable(true);
                entity.setPersistent(false);
                entity.setSeeThrough(true);
                entity.setDefaultBackground(false);
                entity.setShadowed(true);
                entity.setViewRange(Math.max(1.0f, definition.viewRange()));
                entity.setTransformation(new Transformation(
                        new Vector3f(), new AxisAngle4f(), new Vector3f(definition.scale()), new AxisAngle4f()));
                entity.getPersistentDataContainer().set(hologramKey, PersistentDataType.STRING, template);
                entity.setText(color(template));
            });
            hologram.add(display);
        }
        displays.put(definition.id(), hologram);
    }

    private static boolean isInChunk(HologramDefinition definition, World world, int chunkX, int chunkZ) {
        Location location = definition.location();
        return location.getWorld().equals(world)
                && (location.getBlockX() >> 4) == chunkX
                && (location.getBlockZ() >> 4) == chunkZ;
    }

    private Optional<Location> readLocation(ConfigurationSection section) {
        if (section == null) {
            return Optional.empty();
        }
        World world = Bukkit.getWorld(section.getString("world", ""));
        if (world == null) {
            return Optional.empty();
        }
        return Optional.of(new Location(world,
                section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch")));
    }

    private void removeDisplays(String id) {
        List<TextDisplay> hologram = displays.remove(id);
        if (hologram != null) {
            hologram.forEach(TextDisplay::remove);
        }
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private record HologramDefinition(String id, Location location, List<String> lines,
                                      double spacing, float viewRange, float scale) { }
}
