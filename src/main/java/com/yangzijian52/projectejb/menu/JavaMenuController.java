package com.yangzijian52.projectejb.menu;

import com.yangzijian52.projectejb.ProjectEJBPlugin;
import com.yangzijian52.projectejb.economy.OperationResult;
import com.yangzijian52.projectejb.util.TextUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

public final class JavaMenuController implements Listener {
    private static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    private final ProjectEJBPlugin plugin;
    private final Map<UUID, SearchPrompt> searchPrompts = new ConcurrentHashMap<>();
    private List<Integer> buyAmounts = List.of(1, 8, 16, 32, 64);
    private List<Long> transferAmounts = List.of(100L, 1000L, 10000L, 100000L);
    private long searchTimeoutMillis = 30_000L;

    public JavaMenuController(ProjectEJBPlugin plugin) {
        this.plugin = plugin;
        reloadSettings();
    }

    public void reloadSettings() {
        buyAmounts = plugin.getConfig().getIntegerList("menu.buy-amounts").stream()
                .filter(value -> value != null && value > 0 && value <= plugin.emcService().maximumBuyAmount())
                .distinct()
                .sorted()
                .toList();
        if (buyAmounts.isEmpty()) {
            buyAmounts = List.of(1);
        }
        transferAmounts = plugin.getConfig().getLongList("menu.transfer-amounts").stream()
                .filter(value -> value != null && value >= plugin.emcService().minimumTransfer())
                .distinct()
                .sorted()
                .toList();
        if (transferAmounts.isEmpty()) {
            transferAmounts = List.of(plugin.emcService().minimumTransfer());
        }
        searchTimeoutMillis = Math.max(5L, plugin.getConfig().getLong("menu.search-timeout-seconds", 30L)) * 1000L;
    }

    public void openMain(Player player) {
        MenuHolder holder = new MenuHolder(MenuType.MAIN);
        Inventory inventory = Bukkit.createInventory(holder, 27, plugin.messages().component(player, "menu.main-title", Map.of(
                "balance", TextUtil.emc(plugin.emcService().balance(player)))));
        holder.bind(inventory);
        fill(inventory);
        inventory.setItem(10, icon(player, Material.HOPPER, "menu.sell-hand"));
        inventory.setItem(11, icon(player, Material.CHEST, "menu.sell-inventory"));
        inventory.setItem(13, icon(player, Material.EMERALD, "menu.buy"));
        inventory.setItem(15, icon(player, Material.BOOK, "menu.learn-hand"));
        inventory.setItem(16, icon(player, Material.BOOKSHELF, "menu.learn-inventory"));
        inventory.setItem(21, icon(player, Material.PLAYER_HEAD, "menu.pay"));
        inventory.setItem(22, icon(player, Material.SUNFLOWER, "menu.balance", Map.of(
                "balance", TextUtil.emc(plugin.emcService().balance(player)))));
        inventory.setItem(26, icon(player, Material.BARRIER, "menu.close"));
        player.openInventory(inventory);
    }

    public void openBuy(Player player, int requestedPage, String search) {
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<Material> materials = plugin.emcService().learned(player).stream()
                .filter(plugin.emcValues()::hasValue)
                .filter(material -> matches(material, normalizedSearch))
                .sorted(Comparator.comparing(Enum::name))
                .toList();
        int pageCount = Math.max(1, (materials.size() + CONTENT_SLOTS.length - 1) / CONTENT_SLOTS.length);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        MenuHolder holder = new MenuHolder(MenuType.BUY);
        holder.page = page;
        holder.search = normalizedSearch;
        Inventory inventory = Bukkit.createInventory(holder, 54, plugin.messages().component(player, "menu.buy-title", Map.of(
                "page", page + 1)));
        holder.bind(inventory);
        fill(inventory);

        int start = page * CONTENT_SLOTS.length;
        int end = Math.min(start + CONTENT_SLOTS.length, materials.size());
        for (int index = start; index < end; index++) {
            Material material = materials.get(index);
            int slot = CONTENT_SLOTS[index - start];
            holder.materials.put(slot, material);
            long emc = plugin.emcValues().value(material).orElse(0L);
            inventory.setItem(slot, materialIcon(player, material, emc));
        }
        if (materials.isEmpty()) {
            inventory.setItem(22, icon(player, Material.GRAY_DYE, "menu.no-results"));
        }
        inventory.setItem(45, icon(player, Material.BARRIER, "menu.back"));
        inventory.setItem(48, page > 0 ? icon(player, Material.ARROW, "menu.previous") : filler());
        inventory.setItem(49, icon(player, normalizedSearch.isEmpty() ? Material.SPYGLASS : Material.MILK_BUCKET,
                normalizedSearch.isEmpty() ? "menu.search" : "menu.clear-search",
                Map.of("query", normalizedSearch)));
        inventory.setItem(50, page + 1 < pageCount ? icon(player, Material.ARROW, "menu.next") : filler());
        inventory.setItem(53, icon(player, Material.SUNFLOWER, "menu.balance", Map.of(
                "balance", TextUtil.emc(plugin.emcService().balance(player)))));
        player.openInventory(inventory);
    }

    public void openAmount(Player player, Material material, int page, String search) {
        MenuHolder holder = new MenuHolder(MenuType.BUY_AMOUNT);
        holder.selectedMaterial = material;
        holder.page = page;
        holder.search = search;
        Inventory inventory = Bukkit.createInventory(holder, 27, plugin.messages().component(player, "menu.amount-title", Map.of(
                "item", TextUtil.material(material))));
        holder.bind(inventory);
        fill(inventory);
        long unit = plugin.emcValues().value(material).orElse(0L);
        int[] slots = centeredSlots(buyAmounts.size());
        for (int index = 0; index < buyAmounts.size() && index < slots.length; index++) {
            int amount = buyAmounts.get(index);
            int slot = slots[index];
            holder.amounts.put(slot, (long) amount);
            long total = safeMultiply(unit, amount);
            inventory.setItem(slot, icon(player, material, "menu.amount-button", Map.of("amount", amount), List.of(
                    plugin.messages().component(player, "menu.amount-lore", Map.of("emc", TextUtil.emc(total))))));
        }
        inventory.setItem(18, icon(player, Material.BARRIER, "menu.back"));
        inventory.setItem(22, materialIcon(player, material, unit));
        player.openInventory(inventory);
    }

    public void openPayPlayers(Player player, int requestedPage) {
        List<Player> targets = Bukkit.getOnlinePlayers().stream()
                .map(Player.class::cast)
                .filter(target -> !target.getUniqueId().equals(player.getUniqueId()))
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int pageCount = Math.max(1, (targets.size() + CONTENT_SLOTS.length - 1) / CONTENT_SLOTS.length);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        MenuHolder holder = new MenuHolder(MenuType.PAY_PLAYERS);
        holder.page = page;
        Inventory inventory = Bukkit.createInventory(holder, 54, plugin.messages().component(player, "menu.pay-title", Map.of()));
        holder.bind(inventory);
        fill(inventory);
        int start = page * CONTENT_SLOTS.length;
        int end = Math.min(start + CONTENT_SLOTS.length, targets.size());
        for (int index = start; index < end; index++) {
            Player target = targets.get(index);
            int slot = CONTENT_SLOTS[index - start];
            holder.players.put(slot, target.getUniqueId());
            inventory.setItem(slot, playerHead(player, target));
        }
        if (targets.isEmpty()) {
            inventory.setItem(22, icon(player, Material.GRAY_DYE, "menu.no-results"));
        }
        inventory.setItem(45, icon(player, Material.BARRIER, "menu.back"));
        inventory.setItem(48, page > 0 ? icon(player, Material.ARROW, "menu.previous") : filler());
        inventory.setItem(50, page + 1 < pageCount ? icon(player, Material.ARROW, "menu.next") : filler());
        player.openInventory(inventory);
    }

    public void openPayAmount(Player player, Player target, int returnPage) {
        MenuHolder holder = new MenuHolder(MenuType.PAY_AMOUNT);
        holder.selectedPlayer = target.getUniqueId();
        holder.page = returnPage;
        Inventory inventory = Bukkit.createInventory(holder, 27, plugin.messages().component(player, "menu.pay-amount-title", Map.of(
                "player", target.getName())));
        holder.bind(inventory);
        fill(inventory);
        int[] slots = centeredSlots(transferAmounts.size());
        for (int index = 0; index < transferAmounts.size() && index < slots.length; index++) {
            long amount = transferAmounts.get(index);
            int slot = slots[index];
            holder.amounts.put(slot, amount);
            inventory.setItem(slot, icon(player, Material.EMERALD, "menu.pay-amount", Map.of(
                    "amount", TextUtil.emc(amount))));
        }
        inventory.setItem(18, icon(player, Material.BARRIER, "menu.back"));
        inventory.setItem(22, playerHead(player, target));
        player.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof MenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0
                || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        int slot = event.getRawSlot();
        switch (holder.type) {
            case MAIN -> clickMain(player, slot);
            case BUY -> clickBuy(player, holder, slot);
            case BUY_AMOUNT -> clickBuyAmount(player, holder, slot);
            case PAY_PLAYERS -> clickPayPlayers(player, holder, slot);
            case PAY_AMOUNT -> clickPayAmount(player, holder, slot);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSearchChat(AsyncChatEvent event) {
        SearchPrompt prompt = searchPrompts.remove(event.getPlayer().getUniqueId());
        if (prompt == null) {
            return;
        }
        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer();
            if (!player.isOnline()) {
                return;
            }
            if (System.currentTimeMillis() > prompt.expiresAt) {
                plugin.messages().send(player, "errors.search-timeout");
                return;
            }
            if (input.equalsIgnoreCase("cancel") || input.equals("取消")) {
                plugin.messages().send(player, "general.search-cancelled");
                openBuy(player, prompt.page, prompt.previousSearch);
                return;
            }
            openBuy(player, 0, input.length() > 64 ? input.substring(0, 64) : input);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        searchPrompts.remove(event.getPlayer().getUniqueId());
    }

    public void closeAll() {
        searchPrompts.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof MenuHolder) {
                player.closeInventory();
            }
        }
    }

    public List<Integer> buyAmounts() {
        return buyAmounts;
    }

    public List<Long> transferAmounts() {
        return transferAmounts;
    }

    private void clickMain(Player player, int slot) {
        OperationResult result = switch (slot) {
            case 10 -> plugin.emcService().sellHand(player);
            case 11 -> plugin.emcService().sellInventory(player);
            case 15 -> plugin.emcService().learnHand(player);
            case 16 -> plugin.emcService().learnInventory(player);
            default -> null;
        };
        if (result != null) {
            plugin.showResult(player, result);
            openMain(player);
            return;
        }
        switch (slot) {
            case 13 -> openBuy(player, 0, "");
            case 21 -> openPayPlayers(player, 0);
            case 26 -> player.closeInventory();
            default -> {
                // Decorative slot.
            }
        }
    }

    private void clickBuy(Player player, MenuHolder holder, int slot) {
        Material material = holder.materials.get(slot);
        if (material != null) {
            openAmount(player, material, holder.page, holder.search);
            return;
        }
        switch (slot) {
            case 45 -> openMain(player);
            case 48 -> openBuy(player, holder.page - 1, holder.search);
            case 49 -> {
                if (holder.search != null && !holder.search.isEmpty()) {
                    openBuy(player, 0, "");
                } else {
                    searchPrompts.put(player.getUniqueId(), new SearchPrompt(
                            holder.page, holder.search, System.currentTimeMillis() + searchTimeoutMillis));
                    player.closeInventory();
                    plugin.messages().send(player, "general.search-prompt");
                }
            }
            case 50 -> openBuy(player, holder.page + 1, holder.search);
            default -> {
                // Decorative slot.
            }
        }
    }

    private void clickBuyAmount(Player player, MenuHolder holder, int slot) {
        Long amount = holder.amounts.get(slot);
        if (amount != null && holder.selectedMaterial != null) {
            plugin.showResult(player, plugin.emcService().buy(player, holder.selectedMaterial, Math.toIntExact(amount)));
            openAmount(player, holder.selectedMaterial, holder.page, holder.search);
        } else if (slot == 18) {
            openBuy(player, holder.page, holder.search);
        }
    }

    private void clickPayPlayers(Player player, MenuHolder holder, int slot) {
        UUID targetId = holder.players.get(slot);
        if (targetId != null) {
            Player target = Bukkit.getPlayer(targetId);
            if (target == null) {
                plugin.messages().send(player, "errors.player-not-found", Map.of("player", targetId));
                openPayPlayers(player, holder.page);
            } else {
                openPayAmount(player, target, holder.page);
            }
            return;
        }
        switch (slot) {
            case 45 -> openMain(player);
            case 48 -> openPayPlayers(player, holder.page - 1);
            case 50 -> openPayPlayers(player, holder.page + 1);
            default -> {
                // Decorative slot.
            }
        }
    }

    private void clickPayAmount(Player player, MenuHolder holder, int slot) {
        if (slot == 18) {
            openPayPlayers(player, holder.page);
            return;
        }
        Long amount = holder.amounts.get(slot);
        Player target = holder.selectedPlayer == null ? null : Bukkit.getPlayer(holder.selectedPlayer);
        if (amount == null) {
            return;
        }
        if (target == null) {
            plugin.messages().send(player, "errors.player-not-found", Map.of("player", "offline"));
            openPayPlayers(player, holder.page);
            return;
        }
        plugin.showResult(player, plugin.emcService().pay(player, target, amount));
        openPayAmount(player, target, holder.page);
    }

    private ItemStack materialIcon(Player player, Material material, long emc) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.lore(List.of(
                plugin.messages().component(player, "menu.buy-lore-price", Map.of("emc", TextUtil.emc(emc))),
                plugin.messages().component(player, "menu.buy-lore-click", Map.of())));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack playerHead(Player viewer, Player target) {
        ItemStack item = icon(viewer, Material.PLAYER_HEAD, "menu.pay-player", Map.of("player", target.getName()));
        if (item.getItemMeta() instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(target);
            item.setItemMeta(skullMeta);
        }
        return item;
    }

    private ItemStack icon(Player player, Material material, String key) {
        return icon(player, material, key, Map.of());
    }

    private ItemStack icon(Player player, Material material, String key, Map<String, ?> placeholders) {
        return icon(player, material, key, placeholders, List.of());
    }

    private ItemStack icon(
            Player player,
            Material material,
            String key,
            Map<String, ?> placeholders,
            List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.messages().component(player, key, placeholders));
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    private static void fill(Inventory inventory) {
        ItemStack filler = filler();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private static boolean matches(Material material, String search) {
        if (search == null || search.isEmpty()) {
            return true;
        }
        String id = material.name().toLowerCase(Locale.ROOT);
        return id.contains(search.replace(' ', '_')) || TextUtil.material(material).toLowerCase(Locale.ROOT).contains(search);
    }

    private static int[] centeredSlots(int count) {
        int[] candidates = {10, 11, 12, 13, 14, 15, 16};
        int used = Math.min(Math.max(1, count), candidates.length);
        int start = (candidates.length - used) / 2;
        int[] result = new int[used];
        System.arraycopy(candidates, start, result, 0, used);
        return result;
    }

    private static long safeMultiply(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private enum MenuType {
        MAIN,
        BUY,
        BUY_AMOUNT,
        PAY_PLAYERS,
        PAY_AMOUNT
    }

    private static final class MenuHolder implements InventoryHolder {
        private final MenuType type;
        private final Map<Integer, Material> materials = new HashMap<>();
        private final Map<Integer, UUID> players = new HashMap<>();
        private final Map<Integer, Long> amounts = new HashMap<>();
        private Inventory inventory;
        private int page;
        private String search = "";
        private Material selectedMaterial;
        private UUID selectedPlayer;

        private MenuHolder(MenuType type) {
            this.type = type;
        }

        private void bind(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return Objects.requireNonNull(inventory, "Inventory has not been bound yet");
        }
    }

    private record SearchPrompt(int page, String previousSearch, long expiresAt) {}
}
