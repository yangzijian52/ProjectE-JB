package com.yangzijian52.projectejb.util;

import java.text.NumberFormat;
import java.util.Locale;
import org.bukkit.Material;

public final class TextUtil {
    private TextUtil() {}

    public static String emc(long value) {
        return NumberFormat.getIntegerInstance(Locale.US).format(value);
    }

    public static String material(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    public static Material parseMaterial(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("minecraft:")) {
            normalized = normalized.substring("minecraft:".length());
        }
        Material material = Material.matchMaterial(normalized);
        return material != null && material.isItem() ? material : null;
    }
}
