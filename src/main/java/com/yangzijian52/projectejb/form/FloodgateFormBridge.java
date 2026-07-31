package com.yangzijian52.projectejb.form;

import com.yangzijian52.projectejb.ProjectEJBPlugin;
import com.yangzijian52.projectejb.config.PlayerLocale;
import com.yangzijian52.projectejb.economy.OperationResult;
import com.yangzijian52.projectejb.util.TextUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.Form;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

public final class FloodgateFormBridge implements FormBridge {
    private static final int FORM_PAGE_SIZE = 15;
    private final ProjectEJBPlugin plugin;
    private final FloodgateApi api;
    private final Map<UUID, UUID> sessions = new ConcurrentHashMap<>();

    public FloodgateFormBridge(ProjectEJBPlugin plugin) {
        this.plugin = plugin;
        this.api = FloodgateApi.getInstance();
        if (api == null) {
            throw new IllegalStateException("FloodgateApi.getInstance() returned null");
        }
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public boolean isBedrock(Player player) {
        try {
            return player != null && api.isFloodgatePlayer(player.getUniqueId());
        } catch (Throwable throwable) {
            return false;
        }
    }

    @Override
    public boolean openMain(Player player) {
        if (!isBedrock(player)) {
            return false;
        }
        UUID token = newSession(player);
        List<Consumer<Player>> actions = new ArrayList<>();
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(text(player, "forms.main-title", Map.of()))
                .content(text(player, "forms.main-content", Map.of(
                        "balance", TextUtil.emc(plugin.emcService().balance(player)))));
        button(builder, actions, text(player, "forms.sell-hand", Map.of()), target -> operate(target, plugin.emcService().sellHand(target)));
        button(builder, actions, text(player, "forms.sell-inventory", Map.of()), target -> operate(target, plugin.emcService().sellInventory(target)));
        button(builder, actions, text(player, "forms.learn-hand", Map.of()), target -> operate(target, plugin.emcService().learnHand(target)));
        button(builder, actions, text(player, "forms.learn-inventory", Map.of()), target -> operate(target, plugin.emcService().learnInventory(target)));
        button(builder, actions, text(player, "forms.buy", Map.of()), target -> openBuy(target, 0, ""));
        button(builder, actions, text(player, "forms.pay", Map.of()), target -> openPayPlayers(target, 0));
        button(builder, actions, text(player, "forms.close", Map.of()), target -> clear(target));
        builder.validResultHandler(response -> dispatch(player, token, () -> {
            int index = response.clickedButtonId();
            if (index >= 0 && index < actions.size()) {
                actions.get(index).accept(player);
            }
        }));
        return send(player, builder.build());
    }

    @Override
    public String status() {
        var floodgate = Bukkit.getPluginManager().getPlugin("floodgate");
        return floodgate == null ? "api-ready" : "ready-" + floodgate.getPluginMeta().getVersion();
    }

    @Override
    public void clear(Player player) {
        if (player != null) {
            sessions.remove(player.getUniqueId());
        }
    }

    private void openBuy(Player player, int requestedPage, String search) {
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<Material> materials = plugin.emcService().learned(player).stream()
                .filter(plugin.emcValues()::hasValue)
                .filter(material -> matches(material, normalizedSearch))
                .sorted(Comparator.comparing(Enum::name))
                .toList();
        int pageCount = Math.max(1, (materials.size() + FORM_PAGE_SIZE - 1) / FORM_PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        UUID token = newSession(player);
        List<Consumer<Player>> actions = new ArrayList<>();
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(text(player, "forms.buy-title", Map.of("page", page + 1)))
                .content(text(player, "forms.buy-content", Map.of(
                        "balance", TextUtil.emc(plugin.emcService().balance(player)),
                        "query", normalizedSearch.isEmpty() ? "-" : normalizedSearch)));

        int start = page * FORM_PAGE_SIZE;
        int end = Math.min(start + FORM_PAGE_SIZE, materials.size());
        for (int index = start; index < end; index++) {
            Material material = materials.get(index);
            long emc = plugin.emcValues().value(material).orElse(0L);
            button(builder, actions, TextUtil.material(material) + "\n" + TextUtil.emc(emc) + " EMC",
                    target -> openBuyAmount(target, material, page, normalizedSearch));
        }
        button(builder, actions, text(player, "forms.search", Map.of()),
                target -> openSearch(target, page, normalizedSearch));
        if (!normalizedSearch.isEmpty()) {
            button(builder, actions, text(player, "forms.clear-search", Map.of("query", normalizedSearch)),
                    target -> openBuy(target, 0, ""));
        }
        if (page > 0) {
            button(builder, actions, text(player, "forms.previous", Map.of()),
                    target -> openBuy(target, page - 1, normalizedSearch));
        }
        if (page + 1 < pageCount) {
            button(builder, actions, text(player, "forms.next", Map.of()),
                    target -> openBuy(target, page + 1, normalizedSearch));
        }
        button(builder, actions, text(player, "forms.back", Map.of()), this::openMain);
        builder.validResultHandler(response -> dispatch(player, token, () -> {
            int index = response.clickedButtonId();
            if (index >= 0 && index < actions.size()) {
                actions.get(index).accept(player);
            }
        }));
        send(player, builder.build());
    }

    private void openSearch(Player player, int returnPage, String previousSearch) {
        UUID token = newSession(player);
        CustomForm form = CustomForm.builder()
                .title(text(player, "forms.search-title", Map.of()))
                .label(text(player, "forms.search-label", Map.of()))
                .input(text(player, "forms.search-label", Map.of()), "minecraft:diamond", previousSearch)
                .validResultHandler(response -> {
                    String query = response.asInput(1);
                    dispatch(player, token, () -> openBuy(player, 0, truncate(query == null ? "" : query.trim(), 64)));
                })
                .closedOrInvalidResultHandler(() -> dispatch(player, token,
                        () -> openBuy(player, returnPage, previousSearch)))
                .build();
        send(player, form);
    }

    private void openBuyAmount(Player player, Material material, int returnPage, String search) {
        UUID token = newSession(player);
        long unit = plugin.emcValues().value(material).orElse(0L);
        List<Integer> amounts = plugin.menus().buyAmounts();
        List<Consumer<Player>> actions = new ArrayList<>();
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(text(player, "forms.amount-title", Map.of("item", TextUtil.material(material))))
                .content(text(player, "forms.amount-content", Map.of(
                        "emc", TextUtil.emc(unit),
                        "balance", TextUtil.emc(plugin.emcService().balance(player)))));
        for (int amount : amounts) {
            long total = safeMultiply(unit, amount);
            button(builder, actions, amount + " × " + TextUtil.material(material) + "\n" + TextUtil.emc(total) + " EMC",
                    target -> {
                        plugin.showResult(target, plugin.emcService().buy(target, material, amount));
                        openBuyAmount(target, material, returnPage, search);
                    });
        }
        button(builder, actions, text(player, "forms.back", Map.of()),
                target -> openBuy(target, returnPage, search));
        builder.validResultHandler(response -> dispatch(player, token, () -> {
            int index = response.clickedButtonId();
            if (index >= 0 && index < actions.size()) {
                actions.get(index).accept(player);
            }
        }));
        send(player, builder.build());
    }

    private void openPayPlayers(Player player, int requestedPage) {
        List<Player> targets = Bukkit.getOnlinePlayers().stream()
                .map(Player.class::cast)
                .filter(target -> !target.getUniqueId().equals(player.getUniqueId()))
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int pageCount = Math.max(1, (targets.size() + FORM_PAGE_SIZE - 1) / FORM_PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        UUID token = newSession(player);
        List<Consumer<Player>> actions = new ArrayList<>();
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(text(player, "forms.pay-title", Map.of()))
                .content(text(player, "forms.pay-content", Map.of(
                        "balance", TextUtil.emc(plugin.emcService().balance(player)))));
        int start = page * FORM_PAGE_SIZE;
        int end = Math.min(start + FORM_PAGE_SIZE, targets.size());
        for (int index = start; index < end; index++) {
            Player target = targets.get(index);
            button(builder, actions, target.getName(), viewer -> {
                Player current = Bukkit.getPlayer(target.getUniqueId());
                if (current == null) {
                    plugin.messages().send(viewer, "errors.player-not-found", Map.of("player", target.getName()));
                    openPayPlayers(viewer, page);
                } else {
                    openPayAmount(viewer, current, page);
                }
            });
        }
        if (page > 0) {
            button(builder, actions, text(player, "forms.previous", Map.of()), target -> openPayPlayers(target, page - 1));
        }
        if (page + 1 < pageCount) {
            button(builder, actions, text(player, "forms.next", Map.of()), target -> openPayPlayers(target, page + 1));
        }
        button(builder, actions, text(player, "forms.back", Map.of()), this::openMain);
        builder.validResultHandler(response -> dispatch(player, token, () -> {
            int index = response.clickedButtonId();
            if (index >= 0 && index < actions.size()) {
                actions.get(index).accept(player);
            }
        }));
        send(player, builder.build());
    }

    private void openPayAmount(Player player, Player target, int returnPage) {
        UUID token = newSession(player);
        List<Long> amounts = plugin.menus().transferAmounts();
        List<Consumer<Player>> actions = new ArrayList<>();
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(text(player, "forms.pay-amount-title", Map.of("player", target.getName())))
                .content(text(player, "forms.pay-amount-content", Map.of()));
        for (long amount : amounts) {
            button(builder, actions, TextUtil.emc(amount) + " EMC", sender -> {
                Player current = Bukkit.getPlayer(target.getUniqueId());
                if (current == null) {
                    plugin.messages().send(sender, "errors.player-not-found", Map.of("player", target.getName()));
                    openPayPlayers(sender, returnPage);
                    return;
                }
                plugin.showResult(sender, plugin.emcService().pay(sender, current, amount));
                openPayAmount(sender, current, returnPage);
            });
        }
        button(builder, actions, text(player, "forms.back", Map.of()), sender -> openPayPlayers(sender, returnPage));
        builder.validResultHandler(response -> dispatch(player, token, () -> {
            int index = response.clickedButtonId();
            if (index >= 0 && index < actions.size()) {
                actions.get(index).accept(player);
            }
        }));
        send(player, builder.build());
    }

    private void operate(Player player, OperationResult result) {
        plugin.showResult(player, result);
        openMain(player);
    }

    private boolean send(Player player, Form form) {
        try {
            FloodgatePlayer floodgatePlayer = api.getPlayer(player.getUniqueId());
            if (floodgatePlayer == null) {
                return false;
            }
            floodgatePlayer.sendForm(form);
            return true;
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "Failed to send Floodgate form to " + player.getName(), throwable);
            return false;
        }
    }

    private void dispatch(Player player, UUID token, Runnable action) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !token.equals(sessions.get(player.getUniqueId()))) {
                return;
            }
            action.run();
        });
    }

    private UUID newSession(Player player) {
        UUID token = UUID.randomUUID();
        sessions.put(player.getUniqueId(), token);
        return token;
    }

    private String text(Player player, String key, Map<String, ?> placeholders) {
        return plugin.messages().plain(player, key, placeholders);
    }

    private static void button(
            SimpleForm.Builder builder,
            List<Consumer<Player>> actions,
            String text,
            Consumer<Player> action) {
        builder.button(text);
        actions.add(action);
    }

    private static boolean matches(Material material, String search) {
        if (search == null || search.isEmpty()) {
            return true;
        }
        return material.name().toLowerCase(Locale.ROOT).contains(search.replace(' ', '_'))
                || TextUtil.material(material).toLowerCase(Locale.ROOT).contains(search);
    }

    private static String truncate(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length);
    }

    private static long safeMultiply(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }
}
