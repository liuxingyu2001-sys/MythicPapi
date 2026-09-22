# MythicPapi

PlaceholderAPI expansion for MythicMobs named spawners.

Placeholders:

- `%mythicpapi_cooldown_<spawner-id>%`
- `%mythicpapi_warmup_<spawner-id>%`
- `%mythicpapi_level_<spawner-id>%`
- `%mythicpapi_health_<spawner-id>%`

The cooldown and warmup values are the current remaining time in seconds. The level value is read from the most recently spawned active mob at that spawner, so random spawner levels such as `1-50` remain fixed rather than being re-rolled by placeholder refreshes. The last observed generated level remains visible during cooldown and changes only when a newer mob is generated. The health value is the current remaining health of that active mob and shows `-` when no mob is alive. Spawner IDs may contain underscores; the `cooldown_`, `warmup_`, `level_`, or `health_` prefix is used to split the placeholder.

Requirements:

- Paper 1.21.x
- MythicMobs 5.x (enabled before this plugin)
- PlaceholderAPI 2.11.x or newer

## Holograms

MythicPapi uses Paper's built-in `TextDisplay` entities, so no separate hologram plugin is needed. Holograms are saved in `plugins/MythicPapi/config.yml`; lines support colour codes and PlaceholderAPI placeholders.

Create a default hologram at your current location:

```
/mmpapi hologram add <hologram-id> <spawner-id>
```

For the usual case, use the shorter form below. It uses the MythicMobs spawner ID as the hologram ID and creates the hologram at that spawner's configured location. When a player runs it, the lowest hologram line is raised to the player's eye height:

```
/mmpapi hologram add <spawner-id>
```

The default lines show the spawner ID, level, cooldown, and warmup. The default display scale is `1.5`; set a hologram's `scale` in `config.yml` to override it. Edit `holograms` to change the position, lines, line spacing, scale, or view range, then apply it with:

```
/mmpapi hologram reload
```

Adjust scale in-game with `/mmpapi hologram scale <hologram-id> <multiplier>` (valid range: `0.1` to `10`). Other commands: `/mmpapi hologram remove <hologram-id>` and `/mmpapi hologram list`.
