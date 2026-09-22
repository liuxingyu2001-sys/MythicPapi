# MythicPapi

PlaceholderAPI expansion for MythicMobs named spawners.

MythicMobs 命名刷怪器的 PlaceholderAPI 扩展。

## Features / 功能

MythicPapi provides the following placeholders:

MythicPapi 提供以下占位符：

- `%mythicpapi_cooldown_<spawner-id>%` - Current remaining cooldown in seconds. / 当前剩余冷却时间，单位为秒。
- `%mythicpapi_warmup_<spawner-id>%` - Current remaining warmup time in seconds. / 当前剩余预热时间，单位为秒。
- `%mythicpapi_level_<spawner-id>%` - Level of the most recently spawned mob. / 最近生成的生物等级。
- `%mythicpapi_health_<spawner-id>%` - Current health of the active mob. / 当前生物生命值。
- `%mythicpapi_amount_<spawner-id>%` - Number of alive mobs from this spawner. / 该刷新点当前存活的怪物数量。

The level is read from the most recently spawned active mob at that spawner. Random spawner levels such as `1-50` remain fixed instead of being re-rolled when the placeholder refreshes. The last observed generated level remains visible during cooldown and changes only when a newer mob is generated.

等级取自该刷怪器最近生成且仍处于活动状态的生物。对于 `1-50` 这类随机等级，刷新占位符时不会重新随机；冷却期间会继续显示上一次记录的等级，直到生成新的生物后才更新。

The health value is the current remaining health of the active mob and shows `-` when no mob is alive. The amount value counts only active, non-dead mobs and returns `0` when none are alive. Spawner IDs may contain underscores; the `cooldown_`, `warmup_`, `level_`, `health_`, or `amount_` prefix is used to split the placeholder.

生命值显示当前生物的剩余生命值，没有存活生物时显示 `-`。数量只统计仍处于活动且未死亡的怪物，没有时显示 `0`。刷怪器 ID 可以包含下划线；插件会使用 `cooldown_`、`warmup_`、`level_`、`health_` 或 `amount_` 前缀解析占位符。

## Requirements / 环境要求

- Paper 1.21.x
- MythicMobs 5.x, enabled before MythicPapi / MythicMobs 5.x，且需要在 MythicPapi 之前启用
- PlaceholderAPI 2.11.x or newer / PlaceholderAPI 2.11.x 或更高版本

## Installation / 安装

1. Install Paper 1.21.x, MythicMobs 5.x, and PlaceholderAPI 2.11.x or newer.
2. Copy the MythicPapi jar into the server's `plugins` directory.
3. Start or restart the server and make sure MythicMobs and PlaceholderAPI are enabled.

1. 安装 Paper 1.21.x、MythicMobs 5.x 和 PlaceholderAPI 2.11.x 或更高版本。
2. 将 MythicPapi 的 jar 文件放入服务器的 `plugins` 目录。
3. 启动或重启服务器，并确认 MythicMobs 与 PlaceholderAPI 已成功启用。

## Holograms / 全息显示

MythicPapi uses Paper's built-in `TextDisplay` entities, so no separate hologram plugin is required. Holograms are saved in `plugins/MythicPapi/config.yml`; lines support colour codes and PlaceholderAPI placeholders.

MythicPapi 使用 Paper 内置的 `TextDisplay` 实体，不需要额外安装全息插件。全息配置保存在 `plugins/MythicPapi/config.yml` 中，文本行支持颜色代码和 PlaceholderAPI 占位符。

Create a default hologram at your current location:

在当前位置创建默认全息：

```text
/mmpapi hologram add <hologram-id> <spawner-id>
```

For the usual case, use the shorter form below. It uses the MythicMobs spawner ID as the hologram ID and creates the lowest hologram line two blocks above that spawner's configured location. Change `hologram-height` in `config.yml` to adjust this default.

通常可以使用下面的简写形式。它会使用 MythicMobs 刷怪器 ID 作为全息 ID，并让最底部的全息文本行位于该刷怪器配置点上方 2 格。可在 `config.yml` 中修改 `hologram-height` 调整默认悬浮高度。

```text
/mmpapi hologram add <spawner-id>
```

The default lines show the spawner ID, level, health, amount, cooldown, and warmup. The default display scale is `1.5`; set a hologram's `scale` in `config.yml` to override it. Edit `holograms` to change the position, lines, line spacing, scale, or view range, then apply the changes with:

默认文本行显示刷怪器 ID、等级、生命值、数量、冷却时间和预热时间。默认显示比例为 `1.5`；可以在 `config.yml` 中设置全息的 `scale` 覆盖默认值。编辑 `holograms` 可以修改位置、文本行、行间距、比例或可视距离，然后执行以下命令应用配置：

```text
/mmpapi hologram reload
```

Adjust one hologram in-game with `/mmpapi hologram scale <hologram-id> <multiplier>` (valid range: `0.1` to `10`). Use `/mmpapi hologram scaleall <multiplier>` to update every existing hologram and the default scale used by newly created holograms. Other commands are `/mmpapi hologram remove <hologram-id>` and `/mmpapi hologram list`.

可以使用 `/mmpapi hologram scale <hologram-id> <multiplier>` 在游戏中调整单个全息图显示比例，有效范围为 `0.1` 至 `10`；缩放会由下向上展开各行，不会降低最底部显示位置。使用 `/mmpapi hologram scaleall <multiplier>` 可一键修改所有已有全息图和未来新建全息图的默认比例。`/mmpapi hologram sync` 会扫描所有当前 MythicMobs 刷新点，只为缺失的同名全息图创建默认配置，不会改写已有全息图。其他命令：`/mmpapi hologram remove <hologram-id>` 和 `/mmpapi hologram list`。

## Build / 构建

Java 21 and Maven are required. Run:

需要 Java 21 和 Maven，执行以下命令构建：

```text
mvn clean package
```

The compiled jar is generated in `target/`.

编译后的 jar 文件会生成在 `target/` 目录中。
