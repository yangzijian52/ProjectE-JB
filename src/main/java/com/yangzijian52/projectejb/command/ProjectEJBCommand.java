package com.yangzijian52.projectejb.command;

import com.yangzijian52.projectejb.ProjectEJBPlugin;
import com.yangzijian52.projectejb.config.PlayerLocale;
import com.yangzijian52.projectejb.data.DataAccessException;
import com.yangzijian52.projectejb.economy.OperationResult;
import com.yangzijian52.projectejb.util.TextUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ProjectEJBCommand implements CommandExecutor, TabCompleter, Listener {
    private static final Set<String> PLAYER_SUBCOMMANDS = Set.of(
            "menu", "balance", "sell", "learn", "buy", "pay", "language", "help");
    private final ProjectEJBPlugin plugin;

    public ProjectEJBCommand(ProjectEJBPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
        try {
            return switch (sub) {
                case "menu", "open" -> openMenu(sender);
                case "balance", "bal" -> balance(sender, args);
                case "sell" -> sell(sender, args);
                case "learn" -> learn(sender, args);
                case "buy" -> buy(sender, args);
                case "pay" -> pay(sender, args);
                case "language", "lang" -> language(sender, args);
                case "help", "?" -> help(sender);
                case "reload" -> reload(sender);
                case "setemc" -> setEmc(sender, args);
                case "giveemc" -> giveEmc(sender, args);
                case "takeemc" -> takeEmc(sender, args);
                case "resetemc" -> resetEmc(sender, args);
                case "status" -> status(sender);
                default -> {
                    plugin.messages().send(sender, "errors.usage");
                    yield true;
                }
            };
        } catch (DataAccessException exception) {
            plugin.getLogger().log(Level.SEVERE, "Command database operation failed", exception);
            plugin.messages().send(sender, "errors.database");
            return true;
        } catch (ArithmeticException exception) {
            plugin.messages().send(sender, "errors.invalid-number");
            return true;
        }
    }

    private boolean openMenu(CommandSender sender) {
        Player player = requirePlayer(sender, "projectejb.menu");
        if (player != null) {
            plugin.openMenu(player);
        }
        return true;
    }

    private boolean balance(CommandSender sender, String[] args) {
        if (args.length >= 2 && sender.hasPermission("projectejb.admin.balance")) {
            OfflinePlayer target = findKnownPlayer(args[1]);
            if (target == null) {
                plugin.messages().send(sender, "errors.player-not-found", Map.of("player", args[1]));
                return true;
            }
            long balance = plugin.accountStore().getBalance(target.getUniqueId(), safeName(target));
            plugin.messages().send(sender, "admin.balance", Map.of(
                    "player", safeName(target), "balance", TextUtil.emc(balance)));
            return true;
        }
        Player player = requirePlayer(sender, "projectejb.balance");
        if (player != null) {
            plugin.messages().send(player, "general.balance", Map.of(
                    "balance", TextUtil.emc(plugin.emcService().balance(player))));
        }
        return true;
    }

    private boolean sell(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "projectejb.sell");
        if (player == null) {
            return true;
        }
        if (args.length < 2) {
            plugin.messages().send(player, "errors.usage");
            return true;
        }
        OperationResult result = switch (args[1].toLowerCase(Locale.ROOT)) {
            case "hand" -> plugin.emcService().sellHand(player);
            case "inventory", "inv", "all" -> plugin.emcService().sellInventory(player);
            default -> null;
        };
        if (result == null) {
            plugin.messages().send(player, "errors.usage");
        } else {
            plugin.showResult(player, result);
        }
        return true;
    }

    private boolean learn(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "projectejb.learn");
        if (player == null) {
            return true;
        }
        if (args.length < 2) {
            plugin.messages().send(player, "errors.usage");
            return true;
        }
        OperationResult result = switch (args[1].toLowerCase(Locale.ROOT)) {
            case "hand" -> plugin.emcService().learnHand(player);
            case "inventory", "inv", "all" -> plugin.emcService().learnInventory(player);
            default -> null;
        };
        if (result == null) {
            plugin.messages().send(player, "errors.usage");
        } else {
            plugin.showResult(player, result);
        }
        return true;
    }

    private boolean buy(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "projectejb.buy");
        if (player == null) {
            return true;
        }
        if (args.length < 2) {
            plugin.messages().send(player, "errors.usage");
            return true;
        }
        Material material = TextUtil.parseMaterial(args[1]);
        if (material == null) {
            plugin.messages().send(player, "errors.invalid-material", Map.of("material", args[1]));
            return true;
        }
        Integer amount = args.length >= 3 ? parsePositiveInt(args[2]) : 1;
        if (amount == null || amount > plugin.emcService().maximumBuyAmount()) {
            plugin.messages().send(player, "errors.invalid-number");
            return true;
        }
        plugin.showResult(player, plugin.emcService().buy(player, material, amount));
        return true;
    }

    private boolean pay(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "projectejb.pay");
        if (player == null) {
            return true;
        }
        if (args.length < 3) {
            plugin.messages().send(player, "errors.usage");
            return true;
        }
        Player target = findOnlinePlayer(args[1]);
        if (target == null) {
            plugin.messages().send(player, "errors.player-not-found", Map.of("player", args[1]));
            return true;
        }
        Long amount = parsePositiveLong(args[2]);
        if (amount == null) {
            plugin.messages().send(player, "errors.invalid-number");
            return true;
        }
        plugin.showResult(player, plugin.emcService().pay(player, target, amount));
        return true;
    }

    private boolean language(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "projectejb.language");
        if (player == null) {
            return true;
        }
        if (args.length < 2) {
            plugin.messages().send(player, "errors.usage");
            return true;
        }
        PlayerLocale locale = PlayerLocale.from(args[1], null);
        if (locale == null) {
            plugin.messages().send(player, "errors.usage");
            return true;
        }
        plugin.messages().setLocale(player, locale);
        plugin.messages().send(player, "general.language-set");
        return true;
    }

    private boolean help(CommandSender sender) {
        if (sender instanceof Player player) {
            for (Component line : plugin.messages().list(player, "help.lines")) {
                player.sendMessage(line);
            }
        } else {
            sender.sendMessage("ProjectE-JB: /projectejb status|reload|balance <player>|setemc|giveemc|takeemc|resetemc");
        }
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!check(sender, "projectejb.admin.reload")) {
            return true;
        }
        plugin.reloadProject();
        plugin.messages().send(sender, "admin.reloaded");
        return true;
    }

    private boolean setEmc(CommandSender sender, String[] args) {
        if (!check(sender, "projectejb.admin.setemc")) {
            return true;
        }
        if (args.length < 3) {
            plugin.messages().send(sender, "errors.usage");
            return true;
        }
        Material material = TextUtil.parseMaterial(args[1]);
        Long value = parseNonNegativeLong(args[2]);
        if (material == null) {
            plugin.messages().send(sender, "errors.invalid-material", Map.of("material", args[1]));
            return true;
        }
        if (value == null) {
            plugin.messages().send(sender, "errors.invalid-number");
            return true;
        }
        try {
            plugin.emcValues().setValue(material, value);
            plugin.messages().send(sender, value == 0 ? "admin.emc-disabled" : "admin.emc-set", Map.of(
                    "item", TextUtil.material(material), "emc", TextUtil.emc(value)));
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to persist EMC value", exception);
            plugin.messages().send(sender, "errors.database");
        }
        return true;
    }

    private boolean giveEmc(CommandSender sender, String[] args) {
        if (!check(sender, "projectejb.admin.give")) {
            return true;
        }
        if (args.length < 3) {
            plugin.messages().send(sender, "errors.usage");
            return true;
        }
        OfflinePlayer target = findKnownPlayer(args[1]);
        Long amount = parsePositiveLong(args[2]);
        if (target == null) {
            plugin.messages().send(sender, "errors.player-not-found", Map.of("player", args[1]));
        } else if (amount == null) {
            plugin.messages().send(sender, "errors.invalid-number");
        } else {
            plugin.accountStore().credit(target.getUniqueId(), safeName(target), amount, "ADMIN_GIVE", null, 0);
            plugin.messages().send(sender, "admin.given", Map.of(
                    "player", safeName(target), "amount", TextUtil.emc(amount)));
        }
        return true;
    }

    private boolean takeEmc(CommandSender sender, String[] args) {
        if (!check(sender, "projectejb.admin.take")) {
            return true;
        }
        if (args.length < 3) {
            plugin.messages().send(sender, "errors.usage");
            return true;
        }
        OfflinePlayer target = findKnownPlayer(args[1]);
        Long amount = parsePositiveLong(args[2]);
        if (target == null) {
            plugin.messages().send(sender, "errors.player-not-found", Map.of("player", args[1]));
        } else if (amount == null) {
            plugin.messages().send(sender, "errors.invalid-number");
        } else {
            long before = plugin.accountStore().getBalance(target.getUniqueId(), safeName(target));
            long after = plugin.accountStore().takeUpTo(target.getUniqueId(), safeName(target), amount, "ADMIN_TAKE");
            plugin.messages().send(sender, "admin.taken", Map.of(
                    "player", safeName(target), "amount", TextUtil.emc(before - after)));
        }
        return true;
    }

    private boolean resetEmc(CommandSender sender, String[] args) {
        if (!check(sender, "projectejb.admin.reset")) {
            return true;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "errors.usage");
            return true;
        }
        OfflinePlayer target = findKnownPlayer(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "errors.player-not-found", Map.of("player", args[1]));
        } else {
            plugin.accountStore().setBalance(target.getUniqueId(), safeName(target), 0L, "ADMIN_RESET");
            plugin.messages().send(sender, "admin.reset", Map.of("player", safeName(target)));
        }
        return true;
    }

    private boolean status(CommandSender sender) {
        if (!check(sender, "projectejb.admin.status")) {
            return true;
        }
        plugin.messages().send(sender, "admin.status", Map.of(
                "version", plugin.getPluginMeta().getVersion(),
                "paper", Bukkit.getMinecraftVersion(),
                "database", plugin.accountStore().isOpen() ? "OK" : "CLOSED",
                "values", plugin.emcValues().size(),
                "floodgate", plugin.forms().status()));
        return true;
    }

    private Player requirePlayer(CommandSender sender, String permission) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "errors.players-only");
            return null;
        }
        if (!check(player, permission)) {
            return null;
        }
        return player;
    }

    private boolean check(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        plugin.messages().send(sender, "errors.no-permission");
        return false;
    }

    private Player findOnlinePlayer(String name) {
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    private OfflinePlayer findKnownPlayer(String name) {
        Player online = findOnlinePlayer(name);
        if (online != null) {
            return online;
        }
        return Arrays.stream(Bukkit.getOfflinePlayers())
                .filter(player -> player.getName() != null && player.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    private static String safeName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }

    private static Integer parsePositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Long parsePositiveLong(String value) {
        try {
            long parsed = Long.parseLong(value.replace(",", ""));
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Long parseNonNegativeLong(String value) {
        try {
            long parsed = Long.parseLong(value.replace(",", ""));
            return parsed >= 0 ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        if (args.length == 1) {
            List<String> commands = new ArrayList<>(PLAYER_SUBCOMMANDS);
            if (sender.hasPermission("projectejb.admin")) {
                commands.addAll(List.of("reload", "setemc", "giveemc", "takeemc", "resetemc", "status"));
            }
            return filter(commands.stream(), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("sell") || args[0].equalsIgnoreCase("learn"))) {
            return filter(Stream.of("hand", "inventory"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("language")) {
            return filter(Stream.of("zh_cn", "en_us"), args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("buy") || args[0].equalsIgnoreCase("setemc"))) {
            Stream<String> materials = plugin.emcValues().all().keySet().stream()
                    .map(material -> material.name().toLowerCase(Locale.ROOT));
            return filter(materials, args[1]);
        }
        if (args.length == 2 && Stream.of("pay", "balance", "giveemc", "takeemc", "resetemc")
                .anyMatch(value -> value.equalsIgnoreCase(args[0]))) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName), args[1]);
        }
        return List.of();
    }

    private static List<String> filter(Stream<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
                .sorted()
                .limit(100)
                .toList();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.forms().clear(event.getPlayer());
        plugin.messages().clearLocaleCache(event.getPlayer().getUniqueId());
    }
}
