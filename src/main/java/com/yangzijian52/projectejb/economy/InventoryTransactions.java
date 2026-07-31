package com.yangzijian52.projectejb.economy;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class InventoryTransactions {
    private InventoryTransactions() {}

    public static boolean canFit(PlayerInventory inventory, Material material, int amount, int storageSlots) {
        int remaining = amount;
        int maxStack = material.getMaxStackSize();
        ItemStack pristine = new ItemStack(material, 1);
        int slots = Math.min(storageSlots, inventory.getStorageContents().length);
        for (int slot = 0; slot < slots && remaining > 0; slot++) {
            ItemStack current = inventory.getItem(slot);
            if (current == null || current.getType().isAir()) {
                remaining -= maxStack;
            } else if (current.isSimilar(pristine)) {
                remaining -= Math.max(0, Math.min(current.getMaxStackSize(), maxStack) - current.getAmount());
            }
        }
        return remaining <= 0;
    }

    public static boolean add(PlayerInventory inventory, Material material, int amount, int storageSlots) {
        int remaining = amount;
        int maxStack = material.getMaxStackSize();
        ItemStack pristine = new ItemStack(material, 1);
        int slots = Math.min(storageSlots, inventory.getStorageContents().length);

        for (int slot = 0; slot < slots && remaining > 0; slot++) {
            ItemStack current = inventory.getItem(slot);
            if (current != null && current.isSimilar(pristine) && current.getAmount() < maxStack) {
                int add = Math.min(remaining, maxStack - current.getAmount());
                current.setAmount(current.getAmount() + add);
                inventory.setItem(slot, current);
                remaining -= add;
            }
        }
        for (int slot = 0; slot < slots && remaining > 0; slot++) {
            ItemStack current = inventory.getItem(slot);
            if (current == null || current.getType().isAir()) {
                int add = Math.min(remaining, maxStack);
                inventory.setItem(slot, new ItemStack(material, add));
                remaining -= add;
            }
        }
        return remaining == 0;
    }

    public static boolean remove(PlayerInventory inventory, Material material, int amount, int storageSlots) {
        int remaining = amount;
        int slots = Math.min(storageSlots, inventory.getStorageContents().length);
        ItemStack pristine = new ItemStack(material, 1);
        for (int slot = 0; slot < slots && remaining > 0; slot++) {
            ItemStack current = inventory.getItem(slot);
            if (current == null || !current.isSimilar(pristine)) {
                continue;
            }
            int remove = Math.min(remaining, current.getAmount());
            int left = current.getAmount() - remove;
            inventory.setItem(slot, left == 0 ? null : current.asQuantity(left));
            remaining -= remove;
        }
        return remaining == 0;
    }
}
