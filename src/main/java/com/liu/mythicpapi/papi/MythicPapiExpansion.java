package com.liu.mythicpapi.papi;

import com.liu.mythicpapi.hook.MythicMobsHook;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Resolves spawner timer and level placeholders.
 */
public final class MythicPapiExpansion extends PlaceholderExpansion {

    private static final String COOLDOWN = "cooldown_";
    private static final String WARMUP = "warmup_";
    private static final String LEVEL = "level_";
    private static final String HEALTH = "health_";

    private final MythicMobsHook mythicMobsHook;
    private final String version;

    public MythicPapiExpansion(MythicMobsHook mythicMobsHook, String version) {
        this.mythicMobsHook = mythicMobsHook;
        this.version = version;
    }

    @Override
    public String getIdentifier() {
        return "mythicpapi";
    }

    @Override
    public String getAuthor() {
        return "liu";
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (params == null || params.isBlank()) {
            return null;
        }

        String normalized = params.toLowerCase(Locale.ROOT);
        final String prefix;
        final Attribute attribute;
        if (normalized.startsWith(COOLDOWN)) {
            prefix = COOLDOWN;
            attribute = Attribute.COOLDOWN;
        } else if (normalized.startsWith(WARMUP)) {
            prefix = WARMUP;
            attribute = Attribute.WARMUP;
        } else if (normalized.startsWith(LEVEL)) {
            prefix = LEVEL;
            attribute = Attribute.LEVEL;
        } else if (normalized.startsWith(HEALTH)) {
            prefix = HEALTH;
            attribute = Attribute.HEALTH;
        } else {
            return null;
        }

        String spawnerId = params.substring(prefix.length());
        if (spawnerId.isBlank()) {
            return null;
        }

        return switch (attribute) {
            case COOLDOWN, WARMUP -> mythicMobsHook
                    .getRemainingSeconds(spawnerId, attribute == Attribute.COOLDOWN)
                    .stream().mapToObj(String::valueOf).findFirst().orElse(null);
            case LEVEL -> mythicMobsHook.getCurrentLevel(spawnerId)
                    .stream().mapToObj(MythicPapiExpansion::formatNumber).findFirst().orElse("-");
            case HEALTH -> mythicMobsHook.getCurrentHealth(spawnerId)
                    .stream().mapToObj(MythicPapiExpansion::formatHealth).findFirst().orElse("-");
        };
    }

    private static String formatNumber(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    private static String formatHealth(double health) {
        return Long.toString((long) Math.ceil(health));
    }

    private enum Attribute { COOLDOWN, WARMUP, LEVEL, HEALTH }
}
