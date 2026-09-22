package com.liu.mythicpapi.command;

import com.liu.mythicpapi.MythicPapiPlugin;
import com.liu.mythicpapi.hologram.HologramManager;
import com.liu.mythicpapi.hook.MythicMobsHook;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** Administrative command for persistent spawner holograms. */
public final class MythicPapiCommand implements CommandExecutor, TabCompleter {

    private final MythicPapiPlugin plugin;
    private final HologramManager holograms;
    private final MythicMobsHook mythicMobs;

    public MythicPapiCommand(MythicPapiPlugin plugin, HologramManager holograms, MythicMobsHook mythicMobs) {
        this.plugin = plugin;
        this.holograms = holograms;
        this.mythicMobs = mythicMobs;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mythicpapi.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有权限执行此命令。");
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("hologram")) {
            sendUsage(sender, label);
            return true;
        }

        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> add(sender, label, args);
            case "remove", "delete" -> remove(sender, label, args);
            case "scale" -> scale(sender, label, args);
            case "sync" -> sync(sender, label, args);
            case "reload" -> reload(sender);
            case "list" -> list(sender);
            default -> {
                sendUsage(sender, label);
                yield true;
            }
        };
    }

    private boolean add(CommandSender sender, String label, String[] args) {
        if (args.length != 3 && args.length != 4) {
            sendAddUsage(sender, label);
            return true;
        }
        boolean atSpawner = args.length == 3;
        String id = args[2];
        String spawnerId = atSpawner ? id : args[3];
        if (!id.matches("[\\p{IsHan}A-Za-z0-9_-]+")) {
            sender.sendMessage(ChatColor.RED + "全息图ID只能包含中文、字母、数字、下划线和连字符。");
            return true;
        }
        var location = atSpawner ? mythicMobs.getSpawnerLocation(spawnerId)
                .map(spawnerLocation -> sender instanceof Player player
                        ? spawnerLocation.add(0.0, player.getEyeHeight(), 0.0)
                        : spawnerLocation)
                : sender instanceof Player player ? java.util.Optional.of(player.getEyeLocation())
                : java.util.Optional.<org.bukkit.Location>empty();
        if (location.isEmpty()) {
            sender.sendMessage(atSpawner
                    ? ChatColor.RED + "未找到 MythicMobs 刷怪点 " + spawnerId + "。"
                    : ChatColor.RED + "指定全息图位置时只能由玩家执行命令。");
            return true;
        }
        if (!holograms.create(id, spawnerId, location.get())) {
            sender.sendMessage(ChatColor.RED + "创建失败：ID已存在或配置无效。");
            return true;
        }
        sender.sendMessage(ChatColor.GREEN + "已创建全息图 " + id + "，配置已写入 config.yml。");
        return true;
    }

    private boolean remove(CommandSender sender, String label, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(ChatColor.YELLOW + "用法: /" + label + " hologram remove <全息图ID>");
            return true;
        }
        sender.sendMessage(holograms.remove(args[2])
                ? ChatColor.GREEN + "已删除全息图 " + args[2] + "。"
                : ChatColor.RED + "未找到全息图 " + args[2] + "。");
        return true;
    }

    private boolean reload(CommandSender sender) {
        plugin.reloadConfig();
        holograms.load();
        holograms.refresh();
        sender.sendMessage(ChatColor.GREEN + "全息图配置已重载。");
        return true;
    }

    private boolean sync(CommandSender sender, String label, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(ChatColor.YELLOW + "用法: /" + label + " hologram sync");
            return true;
        }

        int created = 0;
        int skipped = 0;
        int unavailable = 0;
        for (String spawnerId : mythicMobs.getSpawnerIds()) {
            if (!spawnerId.matches("[\\p{IsHan}A-Za-z0-9_-]+")) {
                unavailable++;
                continue;
            }
            if (holograms.contains(spawnerId)) {
                skipped++;
                continue;
            }
            var location = mythicMobs.getSpawnerLocation(spawnerId);
            if (location.isEmpty() || !holograms.create(spawnerId, spawnerId, location.get())) {
                unavailable++;
                continue;
            }
            created++;
        }
        holograms.refresh();
        sender.sendMessage(ChatColor.GREEN + "全息图同步完成：新增 " + created + " 个，已跳过 " + skipped
                + " 个，未处理 " + unavailable + " 个。");
        return true;
    }

    private boolean scale(CommandSender sender, String label, String[] args) {
        if (args.length != 4) {
            sender.sendMessage(ChatColor.YELLOW + "用法: /" + label + " hologram scale <全息图ID> <倍率>");
            return true;
        }
        final double value;
        try {
            value = Double.parseDouble(args[3]);
        } catch (NumberFormatException ignored) {
            sender.sendMessage(ChatColor.RED + "倍率必须是数字，例如 1.5。");
            return true;
        }
        if (value < 0.1 || value > 10.0) {
            sender.sendMessage(ChatColor.RED + "倍率必须在 0.1 到 10 之间。");
            return true;
        }
        sender.sendMessage(holograms.setScale(args[2], value)
                ? ChatColor.GREEN + "已将全息图 " + args[2] + " 的显示倍率设为 " + value + "。"
                : ChatColor.RED + "未找到全息图 " + args[2] + "。");
        return true;
    }

    private boolean list(CommandSender sender) {
        List<String> ids = holograms.getHologramIds();
        sender.sendMessage(ChatColor.GOLD + "全息图 (" + ids.size() + "): "
                + (ids.isEmpty() ? "无" : String.join(", ", ids)));
        return true;
    }

    private static void sendUsage(CommandSender sender, String label) {
        sendAddUsage(sender, label);
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " hologram remove <全息图ID>");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " hologram scale <全息图ID> <倍率>");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " hologram sync  &7(补齐所有刷新点全息图)");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " hologram reload | list");
    }

    private static void sendAddUsage(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " hologram add <刷怪点ID>  &7(在刷怪点位置创建)");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " hologram add <全息图ID> <刷怪点ID>  &7(在当前位置创建)");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("mythicpapi.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return prefix(List.of("hologram"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("hologram")) {
            return prefix(List.of("add", "remove", "scale", "sync", "reload", "list"), args[1]);
        }
        if (args[0].equalsIgnoreCase("hologram") && args[1].equalsIgnoreCase("add")
                && (args.length == 3 || args.length == 4)) {
            return prefix(mythicMobs.getSpawnerIds(), args[args.length - 1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("hologram")
                && (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("delete")
                || args[1].equalsIgnoreCase("scale"))) {
            return prefix(holograms.getHologramIds(), args[2]);
        }
        return List.of();
    }

    private static List<String> prefix(List<String> options, String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }
}
