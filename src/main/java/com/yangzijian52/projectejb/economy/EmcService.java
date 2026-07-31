package com.yangzijian52.projectejb.economy;

import com.yangzijian52.projectejb.config.EmcValueRegistry;
import com.yangzijian52.projectejb.data.AccountStore;
import com.yangzijian52.projectejb.data.DataAccessException;
import com.yangzijian52.projectejb.util.TextUtil;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

public final class EmcService {
    private final JavaPlugin plugin;
    private final AccountStore store;
    private final EmcValueRegistry values;
    private final ItemSafety itemSafety;
    private int storageSlots;
    private int maximumBuyAmount;
    private long minimumTransfer;
    private double transferFeePercent;
    private boolean consumeLearningItem;
    private boolean creditLearningEmc;

    public EmcService(JavaPlugin plugin, AccountStore store, EmcValueRegistry values) {
        this.plugin = plugin;
        this.store = store;
        this.values = values;
        this.itemSafety = new ItemSafety(plugin.getConfig().getBoolean("security.clean-vanilla-items-only", true));
        reloadSettings();
    }

    public void reloadSettings() {
        storageSlots = Math.max(1, Math.min(36, plugin.getConfig().getInt("security.bulk-inventory-slots", 36)));
        maximumBuyAmount = Math.max(1, plugin.getConfig().getInt("buy.maximum-amount", 2304));
        minimumTransfer = Math.max(1L, plugin.getConfig().getLong("economy.transfer.minimum", 1L));
        transferFeePercent = Math.max(0.0D, plugin.getConfig().getDouble("economy.transfer.fee-percent", 0.0D));
        consumeLearningItem = plugin.getConfig().getBoolean("learning.consume-item", true);
        creditLearningEmc = plugin.getConfig().getBoolean("learning.credit-emc", true);
    }

    public long balance(Player player) {
        return store.getBalance(player.getUniqueId(), player.getName());
    }

    public Set<Material> learned(Player player) {
        return store.getLearned(player.getUniqueId(), player.getName());
    }

    public OperationResult sellHand(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            return OperationResult.of(OperationResult.Code.EMPTY_HAND);
        }
        OperationResult validation = validate(held);
        if (validation != null) {
            return validation;
        }
        long unit = values.value(held.getType()).orElseThrow();
        long total;
        try {
            total = Math.multiplyExact(unit, held.getAmount());
        } catch (ArithmeticException exception) {
            return databaseFailure("EMC overflow while selling held item", exception);
        }
        ItemStack snapshot = held.clone();
        player.getInventory().setItemInMainHand(null);
        try {
            store.credit(player.getUniqueId(), player.getName(), total, "SELL_HAND", held.getType(), held.getAmount());
            return OperationResult.builder(OperationResult.Code.SOLD)
                    .put("amount", held.getAmount())
                    .put("emc", TextUtil.emc(total))
                    .build();
        } catch (RuntimeException exception) {
            player.getInventory().setItemInMainHand(snapshot);
            return databaseFailure("Failed to sell held item", exception);
        }
    }

    public OperationResult sellInventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        Map<Integer, ItemStack> snapshots = new LinkedHashMap<>();
        long totalEmc = 0L;
        int totalItems = 0;
        try {
            for (int slot = 0; slot < storageSlots; slot++) {
                ItemStack item = inventory.getItem(slot);
                if (item == null || !itemSafety.isEligible(item)) {
                    continue;
                }
                OptionalLong unit = values.value(item.getType());
                if (unit.isEmpty()) {
                    continue;
                }
                snapshots.put(slot, item.clone());
                totalEmc = Math.addExact(totalEmc, Math.multiplyExact(unit.getAsLong(), item.getAmount()));
                totalItems = Math.addExact(totalItems, item.getAmount());
            }
        } catch (ArithmeticException exception) {
            return databaseFailure("EMC overflow while planning bulk sale", exception);
        }
        if (snapshots.isEmpty()) {
            return OperationResult.of(OperationResult.Code.NOTHING_TO_SELL);
        }
        snapshots.keySet().forEach(slot -> inventory.setItem(slot, null));
        try {
            store.credit(player.getUniqueId(), player.getName(), totalEmc, "SELL_INVENTORY", null, totalItems);
            return OperationResult.builder(OperationResult.Code.SOLD)
                    .put("amount", totalItems)
                    .put("emc", TextUtil.emc(totalEmc))
                    .build();
        } catch (RuntimeException exception) {
            snapshots.forEach(inventory::setItem);
            return databaseFailure("Failed to sell inventory", exception);
        }
    }

    public OperationResult learnHand(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            return OperationResult.of(OperationResult.Code.EMPTY_HAND);
        }
        OperationResult validation = validate(held);
        if (validation != null) {
            return validation;
        }
        Material material = held.getType();
        if (store.hasLearned(player.getUniqueId(), player.getName(), material)) {
            return OperationResult.builder(OperationResult.Code.ALREADY_LEARNED)
                    .put("item", TextUtil.material(material))
                    .build();
        }
        long emc = creditLearningEmc ? values.value(material).orElseThrow() : 0L;
        ItemStack snapshot = held.clone();
        if (consumeLearningItem) {
            player.getInventory().setItemInMainHand(held.getAmount() == 1 ? null : held.asQuantity(held.getAmount() - 1));
        }
        try {
            boolean learned = store.learnAndCredit(player.getUniqueId(), player.getName(), material, emc);
            if (!learned) {
                if (consumeLearningItem) {
                    player.getInventory().setItemInMainHand(snapshot);
                }
                return OperationResult.builder(OperationResult.Code.ALREADY_LEARNED)
                        .put("item", TextUtil.material(material))
                        .build();
            }
            return OperationResult.builder(OperationResult.Code.LEARNED)
                    .put("item", TextUtil.material(material))
                    .put("emc", TextUtil.emc(emc))
                    .build();
        } catch (RuntimeException exception) {
            if (consumeLearningItem) {
                player.getInventory().setItemInMainHand(snapshot);
            }
            return databaseFailure("Failed to learn held item", exception);
        }
    }

    public OperationResult learnInventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        Set<Material> alreadyLearned = store.getLearned(player.getUniqueId(), player.getName());
        Map<Material, Long> entries = new LinkedHashMap<>();
        Map<Integer, ItemStack> snapshots = new LinkedHashMap<>();
        long totalEmc = 0L;
        for (int slot = 0; slot < storageSlots; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || !itemSafety.isEligible(item) || alreadyLearned.contains(item.getType()) || entries.containsKey(item.getType())) {
                continue;
            }
            OptionalLong value = values.value(item.getType());
            if (value.isEmpty()) {
                continue;
            }
            long credit = creditLearningEmc ? value.getAsLong() : 0L;
            try {
                totalEmc = Math.addExact(totalEmc, credit);
            } catch (ArithmeticException exception) {
                return databaseFailure("EMC overflow while planning bulk learning", exception);
            }
            entries.put(item.getType(), credit);
            if (consumeLearningItem) {
                snapshots.put(slot, item.clone());
            }
        }
        if (entries.isEmpty()) {
            return OperationResult.of(OperationResult.Code.NOTHING_TO_LEARN);
        }
        if (consumeLearningItem) {
            for (Map.Entry<Integer, ItemStack> entry : snapshots.entrySet()) {
                ItemStack item = entry.getValue();
                inventory.setItem(entry.getKey(), item.getAmount() == 1 ? null : item.asQuantity(item.getAmount() - 1));
            }
        }
        try {
            int learnedTypes = store.learnManyAndCredit(player.getUniqueId(), player.getName(), entries, totalEmc);
            return OperationResult.builder(OperationResult.Code.LEARNED_MANY)
                    .put("types", learnedTypes)
                    .put("items", consumeLearningItem ? learnedTypes : 0)
                    .put("emc", TextUtil.emc(totalEmc))
                    .build();
        } catch (RuntimeException exception) {
            if (consumeLearningItem) {
                snapshots.forEach(inventory::setItem);
            }
            return databaseFailure("Failed to learn inventory", exception);
        }
    }

    public OperationResult buy(Player player, Material material, int amount) {
        if (amount < 1 || amount > maximumBuyAmount) {
            return OperationResult.of(OperationResult.Code.NO_EMC_VALUE);
        }
        OptionalLong unitValue = values.value(material);
        if (unitValue.isEmpty()) {
            return OperationResult.of(OperationResult.Code.NO_EMC_VALUE);
        }
        if (!store.hasLearned(player.getUniqueId(), player.getName(), material)) {
            return OperationResult.builder(OperationResult.Code.NOT_LEARNED)
                    .put("item", TextUtil.material(material))
                    .build();
        }
        long total;
        try {
            total = Math.multiplyExact(unitValue.getAsLong(), amount);
        } catch (ArithmeticException exception) {
            return databaseFailure("EMC overflow while buying", exception);
        }
        long balance = store.getBalance(player.getUniqueId(), player.getName());
        if (balance < total) {
            return OperationResult.builder(OperationResult.Code.INSUFFICIENT_EMC)
                    .put("required", TextUtil.emc(total))
                    .put("balance", TextUtil.emc(balance))
                    .build();
        }
        if (!InventoryTransactions.canFit(player.getInventory(), material, amount, storageSlots)) {
            return OperationResult.of(OperationResult.Code.INVENTORY_FULL);
        }
        ItemStack[] inventorySnapshot = snapshotStorage(player.getInventory());
        try {
            if (!store.tryDebit(player.getUniqueId(), player.getName(), total, "BUY", material, amount)) {
                long current = store.getBalance(player.getUniqueId(), player.getName());
                return OperationResult.builder(OperationResult.Code.INSUFFICIENT_EMC)
                        .put("required", TextUtil.emc(total))
                        .put("balance", TextUtil.emc(current))
                        .build();
            }
            if (!InventoryTransactions.add(player.getInventory(), material, amount, storageSlots)) {
                restoreStorage(player.getInventory(), inventorySnapshot);
                store.credit(player.getUniqueId(), player.getName(), total, "BUY_REFUND", material, amount);
                return OperationResult.of(OperationResult.Code.INVENTORY_FULL);
            }
            return OperationResult.builder(OperationResult.Code.BOUGHT)
                    .put("amount", amount)
                    .put("item", TextUtil.material(material))
                    .put("emc", TextUtil.emc(total))
                    .build();
        } catch (RuntimeException exception) {
            restoreStorage(player.getInventory(), inventorySnapshot);
            try {
                long currentBalance = store.getBalance(player.getUniqueId(), player.getName());
                if (currentBalance < balance) {
                    store.credit(player.getUniqueId(), player.getName(), total, "BUY_ERROR_REFUND", material, amount);
                }
            } catch (RuntimeException refundFailure) {
                exception.addSuppressed(refundFailure);
            }
            return databaseFailure("Failed to buy item", exception);
        }
    }

    public OperationResult pay(Player sender, OfflinePlayer receiver, long amount) {
        if (sender.getUniqueId().equals(receiver.getUniqueId())) {
            return OperationResult.of(OperationResult.Code.CANNOT_PAY_SELF);
        }
        if (amount < minimumTransfer) {
            return OperationResult.builder(OperationResult.Code.TRANSFER_TOO_SMALL)
                    .put("minimum", TextUtil.emc(minimumTransfer))
                    .build();
        }
        long fee;
        try {
            fee = Math.max(0L, Math.round(amount * transferFeePercent / 100.0D));
            Math.addExact(amount, fee);
        } catch (ArithmeticException exception) {
            return databaseFailure("EMC overflow while transferring", exception);
        }
        try {
            AccountStore.TransferResult transfer = store.transfer(
                    sender.getUniqueId(),
                    sender.getName(),
                    receiver.getUniqueId(),
                    receiver.getName(),
                    amount,
                    fee);
            if (!transfer.success()) {
                return OperationResult.builder(OperationResult.Code.INSUFFICIENT_EMC)
                        .put("required", TextUtil.emc(Math.addExact(amount, fee)))
                        .put("balance", TextUtil.emc(transfer.senderBalance()))
                        .build();
            }
            return OperationResult.builder(OperationResult.Code.PAID)
                    .put("player", receiver.getName())
                    .put("amount", TextUtil.emc(amount))
                    .put("fee", TextUtil.emc(fee))
                    .build();
        } catch (RuntimeException exception) {
            return databaseFailure("Failed to transfer EMC", exception);
        }
    }

    public int maximumBuyAmount() {
        return maximumBuyAmount;
    }

    public long minimumTransfer() {
        return minimumTransfer;
    }

    private OperationResult validate(ItemStack item) {
        if (!itemSafety.isEligible(item)) {
            return OperationResult.of(OperationResult.Code.UNSAFE_ITEM);
        }
        if (!values.hasValue(item.getType())) {
            return OperationResult.of(OperationResult.Code.NO_EMC_VALUE);
        }
        return null;
    }

    private OperationResult databaseFailure(String message, Throwable exception) {
        plugin.getLogger().log(Level.SEVERE, message, exception);
        return OperationResult.of(OperationResult.Code.DATABASE_ERROR);
    }

    private ItemStack[] snapshotStorage(PlayerInventory inventory) {
        ItemStack[] snapshot = new ItemStack[storageSlots];
        for (int slot = 0; slot < storageSlots; slot++) {
            ItemStack item = inventory.getItem(slot);
            snapshot[slot] = item == null ? null : item.clone();
        }
        return snapshot;
    }

    private void restoreStorage(PlayerInventory inventory, ItemStack[] snapshot) {
        for (int slot = 0; slot < snapshot.length; slot++) {
            inventory.setItem(slot, snapshot[slot] == null ? null : snapshot[slot].clone());
        }
    }
}
