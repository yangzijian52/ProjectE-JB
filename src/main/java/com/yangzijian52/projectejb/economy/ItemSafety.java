package com.yangzijian52.projectejb.economy;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class ItemSafety {
    private final boolean cleanVanillaOnly;

    public ItemSafety(boolean cleanVanillaOnly) {
        this.cleanVanillaOnly = cleanVanillaOnly;
    }

    public boolean isEligible(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0 || !item.getType().isItem()) {
            return false;
        }
        if (!cleanVanillaOnly) {
            return true;
        }
        ItemStack pristine = new ItemStack(item.getType(), 1);
        ItemStack candidate = item.clone();
        candidate.setAmount(1);
        return candidate.isSimilar(pristine);
    }
}
