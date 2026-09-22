package com.liu.mythicpapi.hook;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

/** Runtime adapter for MythicMobs so the plugin is not tied to a private MythicMobs Maven artifact. */
public final class MythicMobsHook {

    private static final int MAX_CACHED_SPAWNER_LEVELS = 2_048;

    private final Logger logger;
    private final Map<String, Double> lastKnownLevels = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Double> eldest) {
            return size() > MAX_CACHED_SPAWNER_LEVELS;
        }
    });
    private volatile Accessor accessor;
    private volatile boolean warnedUnavailable;

    public MythicMobsHook(JavaPlugin plugin) {
        this.logger = plugin.getLogger();
        this.accessor = null;
    }

    public OptionalInt getRemainingSeconds(String spawnerId, boolean cooldown) {
        return readSpawnerValue(spawnerId, cooldown ? Value.REMAINING_COOLDOWN : Value.REMAINING_WARMUP);
    }

    /** Returns the configured location of a named MythicMobs spawner. */
    public Optional<Location> getSpawnerLocation(String spawnerId) {
        Plugin mythicMobs = Bukkit.getPluginManager().getPlugin("MythicMobs");
        if (mythicMobs == null || !mythicMobs.isEnabled()) {
            warnUnavailableOnce();
            return Optional.empty();
        }
        Accessor current = accessorFor(mythicMobs);
        if (current == null) {
            return Optional.empty();
        }
        try {
            Object manager = current.spawnerManager.invoke(mythicMobs);
            Object spawner = manager == null ? null : current.getSpawnerByName.invoke(manager, spawnerId);
            if (spawner == null) {
                return Optional.empty();
            }
            Object mythicLocation = current.spawnerLocation.invoke(spawner);
            Object location = mythicLocation == null ? null : current.adaptLocation.invoke(null, mythicLocation);
            return location instanceof Location bukkitLocation ? Optional.of(bukkitLocation.clone()) : Optional.empty();
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            accessor = null;
            warnUnavailableOnce();
            return Optional.empty();
        }
    }

    /** Lists named MythicMobs spawners for command completion. */
    public List<String> getSpawnerIds() {
        Plugin mythicMobs = Bukkit.getPluginManager().getPlugin("MythicMobs");
        if (mythicMobs == null || !mythicMobs.isEnabled()) {
            return List.of();
        }
        Accessor current = accessorFor(mythicMobs);
        if (current == null) {
            return List.of();
        }
        try {
            Object manager = current.spawnerManager.invoke(mythicMobs);
            Object spawners = manager == null ? null : current.getSpawners.invoke(manager);
            if (!(spawners instanceof Collection<?> collection)) {
                return List.of();
            }
            return collection.stream().map(spawner -> {
                try {
                    Object name = current.spawnerName.invoke(spawner);
                    return name instanceof String string ? string : null;
                } catch (IllegalAccessException | InvocationTargetException ignored) {
                    return null;
                }
            }).filter(Objects::nonNull).sorted().toList();
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            accessor = null;
            warnUnavailableOnce();
            return List.of();
        }
    }

    /**
     * Returns the level of the most recently spawned, still-active mob associated with this spawner.
     * This deliberately does not evaluate the spawner's Level expression, which may be random.
     */
    public OptionalDouble getCurrentLevel(String spawnerId) {
        Plugin mythicMobs = Bukkit.getPluginManager().getPlugin("MythicMobs");
        if (mythicMobs == null || !mythicMobs.isEnabled()) {
            warnUnavailableOnce();
            return OptionalDouble.empty();
        }

        Accessor current = accessorFor(mythicMobs);
        if (current == null) {
            return OptionalDouble.empty();
        }

        try {
            Object manager = current.spawnerManager.invoke(mythicMobs);
            Object spawner = manager == null ? null : current.getSpawnerByName.invoke(manager, spawnerId);
            if (spawner == null) {
                return OptionalDouble.empty();
            }
            Object activeMobs = current.associatedMobs.invoke(spawner);
            Object mobManager = current.mobManager.invoke(mythicMobs);
            if (!(activeMobs instanceof Collection<?> mobIds) || mobManager == null) {
                return OptionalDouble.empty();
            }

            MobLevel newest = null;
            for (Object mobId : mobIds) {
                if (!(mobId instanceof UUID uuid)) {
                    continue;
                }
                Object result = current.getActiveMob.invoke(mobManager, uuid);
                if (!(result instanceof Optional<?> optional) || optional.isEmpty()) {
                    continue;
                }
                Object activeMob = optional.get();
                Object rawLevel = current.activeMobLevel.invoke(activeMob);
                if (!(rawLevel instanceof Number number) || !Double.isFinite(number.doubleValue())) {
                    continue;
                }
                Object rawSpawnTime = current.activeMobSpawnTime.invoke(activeMob);
                long spawnTime = rawSpawnTime instanceof Number spawnTimeNumber
                        ? spawnTimeNumber.longValue() : Long.MIN_VALUE;
                MobLevel candidate = new MobLevel(number.doubleValue(), spawnTime);
                if (newest == null || candidate.spawnTime() > newest.spawnTime()) {
                    newest = candidate;
                }
            }
            if (newest != null) {
                lastKnownLevels.put(spawnerId, newest.level());
                return OptionalDouble.of(newest.level());
            }
            Double cachedLevel = lastKnownLevels.get(spawnerId);
            return cachedLevel == null ? OptionalDouble.empty() : OptionalDouble.of(cachedLevel);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            accessor = null;
            warnUnavailableOnce();
            return OptionalDouble.empty();
        }
    }

    /** Returns the current remaining health of the most recently spawned active mob at this spawner. */
    public OptionalDouble getCurrentHealth(String spawnerId) {
        Plugin mythicMobs = Bukkit.getPluginManager().getPlugin("MythicMobs");
        if (mythicMobs == null || !mythicMobs.isEnabled()) {
            warnUnavailableOnce();
            return OptionalDouble.empty();
        }

        Accessor current = accessorFor(mythicMobs);
        if (current == null) {
            return OptionalDouble.empty();
        }

        try {
            Object manager = current.spawnerManager.invoke(mythicMobs);
            Object spawner = manager == null ? null : current.getSpawnerByName.invoke(manager, spawnerId);
            if (spawner == null) {
                return OptionalDouble.empty();
            }
            Object activeMobs = current.associatedMobs.invoke(spawner);
            Object mobManager = current.mobManager.invoke(mythicMobs);
            if (!(activeMobs instanceof Collection<?> mobIds) || mobManager == null) {
                return OptionalDouble.empty();
            }

            MobHealth newest = null;
            for (Object mobId : mobIds) {
                if (!(mobId instanceof UUID uuid)) {
                    continue;
                }
                Object result = current.getActiveMob.invoke(mobManager, uuid);
                if (!(result instanceof Optional<?> optional) || optional.isEmpty()) {
                    continue;
                }
                Object activeMob = optional.get();
                Object entity = current.activeMobEntity.invoke(activeMob);
                if (entity == null || Boolean.TRUE.equals(current.entityDead.invoke(entity))) {
                    continue;
                }
                Object rawHealth = entity == null ? null : current.entityHealth.invoke(entity);
                if (!(rawHealth instanceof Number health) || !Double.isFinite(health.doubleValue())) {
                    continue;
                }
                Object rawSpawnTime = current.activeMobSpawnTime.invoke(activeMob);
                long spawnTime = rawSpawnTime instanceof Number spawnTimeNumber
                        ? spawnTimeNumber.longValue() : Long.MIN_VALUE;
                MobHealth candidate = new MobHealth(Math.max(0.0, health.doubleValue()), spawnTime);
                if (newest == null || candidate.spawnTime() > newest.spawnTime()) {
                    newest = candidate;
                }
            }
            return newest == null ? OptionalDouble.empty() : OptionalDouble.of(newest.health());
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            accessor = null;
            warnUnavailableOnce();
            return OptionalDouble.empty();
        }
    }

    private OptionalInt readSpawnerValue(String spawnerId, Value value) {
        Plugin mythicMobs = Bukkit.getPluginManager().getPlugin("MythicMobs");
        if (mythicMobs == null || !mythicMobs.isEnabled()) {
            warnUnavailableOnce();
            return OptionalInt.empty();
        }

        Accessor current = accessorFor(mythicMobs);
        if (current == null) {
            return OptionalInt.empty();
        }

        try {
            Object manager = current.spawnerManager.invoke(mythicMobs);
            if (manager == null) {
                return OptionalInt.empty();
            }
            Object spawner = current.getSpawnerByName.invoke(manager, spawnerId);
            if (spawner == null) {
                return OptionalInt.empty();
            }
            Object result;
            result = (value == Value.REMAINING_COOLDOWN
                    ? current.remainingCooldown : current.remainingWarmup).invoke(spawner);
            return result instanceof Number number
                    ? OptionalInt.of(Math.max(0, number.intValue()))
                    : OptionalInt.empty();
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            accessor = null;
            warnUnavailableOnce();
            return OptionalInt.empty();
        }
    }

    private Accessor accessorFor(Plugin mythicMobs) {
        Accessor current = accessor;
        if (current == null) {
            current = Accessor.create(mythicMobs.getClass().getClassLoader());
            accessor = current;
        }
        if (current == null) {
            warnUnavailableOnce();
        }
        return current;
    }

    private enum Value { REMAINING_COOLDOWN, REMAINING_WARMUP }

    private record MobLevel(double level, long spawnTime) { }

    private record MobHealth(double health, long spawnTime) { }

    private void warnUnavailableOnce() {
        if (!warnedUnavailable) {
            warnedUnavailable = true;
            logger.warning("MythicMobs spawner API is unavailable; placeholders will return empty values.");
        }
    }

    private static final class Accessor {
        private final Method spawnerManager;
        private final Method getSpawnerByName;
        private final Method getSpawners;
        private final Method remainingCooldown;
        private final Method remainingWarmup;
        private final Method associatedMobs;
        private final Method mobManager;
        private final Method getActiveMob;
        private final Method activeMobLevel;
        private final Method activeMobSpawnTime;
        private final Method activeMobEntity;
        private final Method entityHealth;
        private final Method entityDead;
        private final Method spawnerLocation;
        private final Method spawnerName;
        private final Method adaptLocation;

        private Accessor(Method spawnerManager, Method getSpawnerByName, Method getSpawners,
                         Method remainingCooldown, Method remainingWarmup,
                         Method associatedMobs, Method mobManager, Method getActiveMob,
                         Method activeMobLevel, Method activeMobSpawnTime, Method spawnerLocation,
                         Method spawnerName, Method adaptLocation, Method activeMobEntity,
                         Method entityHealth, Method entityDead) {
            this.spawnerManager = spawnerManager;
            this.getSpawnerByName = getSpawnerByName;
            this.getSpawners = getSpawners;
            this.remainingCooldown = remainingCooldown;
            this.remainingWarmup = remainingWarmup;
            this.associatedMobs = associatedMobs;
            this.mobManager = mobManager;
            this.getActiveMob = getActiveMob;
            this.activeMobLevel = activeMobLevel;
            this.activeMobSpawnTime = activeMobSpawnTime;
            this.activeMobEntity = activeMobEntity;
            this.entityHealth = entityHealth;
            this.entityDead = entityDead;
            this.spawnerLocation = spawnerLocation;
            this.spawnerName = spawnerName;
            this.adaptLocation = adaptLocation;
        }

        private static Accessor create(ClassLoader classLoader) {
            try {
                Class<?> mythicBukkit = Class.forName(
                        "io.lumine.mythic.bukkit.MythicBukkit", true, classLoader);
                Class<?> spawnerManager = Class.forName(
                        "io.lumine.mythic.core.spawning.spawners.SpawnerManager", true, classLoader);
                Class<?> mythicSpawner = Class.forName(
                        "io.lumine.mythic.core.spawning.spawners.MythicSpawner", true, classLoader);
                Class<?> mobExecutor = Class.forName(
                        "io.lumine.mythic.core.mobs.MobExecutor", true, classLoader);
                Class<?> activeMob = Class.forName(
                        "io.lumine.mythic.core.mobs.ActiveMob", true, classLoader);
                Class<?> abstractLocation = Class.forName(
                        "io.lumine.mythic.api.adapters.AbstractLocation", true, classLoader);
                Class<?> abstractEntity = Class.forName(
                        "io.lumine.mythic.api.adapters.AbstractEntity", true, classLoader);
                Class<?> bukkitAdapter = Class.forName(
                        "io.lumine.mythic.bukkit.BukkitAdapter", true, classLoader);
                return new Accessor(
                        mythicBukkit.getMethod("getSpawnerManager"),
                        spawnerManager.getMethod("getSpawnerByName", String.class),
                        spawnerManager.getMethod("getSpawners"),
                        mythicSpawner.getMethod("getRemainingCooldownSeconds"),
                        mythicSpawner.getMethod("getRemainingWarmupSeconds"),
                        mythicSpawner.getMethod("getAssociatedMobs"),
                        mythicBukkit.getMethod("getMobManager"),
                        mobExecutor.getMethod("getActiveMob", UUID.class),
                        activeMob.getMethod("getLevel"),
                        activeMob.getMethod("getSpawnTime"),
                        mythicSpawner.getMethod("getLocation"),
                        mythicSpawner.getMethod("getName"),
                        bukkitAdapter.getMethod("adapt", abstractLocation),
                        activeMob.getMethod("getEntity"),
                        abstractEntity.getMethod("getHealth"),
                        abstractEntity.getMethod("isDead"));
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return null;
            }
        }
    }
}
