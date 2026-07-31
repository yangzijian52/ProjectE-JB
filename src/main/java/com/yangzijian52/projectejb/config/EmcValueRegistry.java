package com.yangzijian52.projectejb.config;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class EmcValueRegistry {
    private final JavaPlugin plugin;
    private final File file;
    private final Map<Material, Long> values = new EnumMap<>(Material.class);
    private YamlConfiguration configuration;

    public EmcValueRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "emc-values.yml");
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            plugin.saveResource("emc-values.yml", false);
        }
        configuration = YamlConfiguration.loadConfiguration(file);
        values.clear();
        ConfigurationSection section = configuration.getConfigurationSection("values");
        if (section == null) {
            plugin.getLogger().severe("emc-values.yml does not contain a values section.");
            return;
        }
        int invalid = 0;
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            long value = section.getLong(key, 0L);
            if (material == null || !material.isItem()) {
                invalid++;
                plugin.getLogger().warning("Ignoring unknown/non-item EMC material: " + key);
                continue;
            }
            if (value > 0) {
                values.put(material, value);
            }
        }
        plugin.getLogger().info("Loaded " + values.size() + " EMC values" + (invalid == 0 ? "." : " (" + invalid + " invalid entries ignored)."));
    }

    public OptionalLong value(Material material) {
        Long value = values.get(material);
        return value == null ? OptionalLong.empty() : OptionalLong.of(value);
    }

    public boolean hasValue(Material material) {
        return values.containsKey(material);
    }

    public int size() {
        return values.size();
    }

    public Map<Material, Long> all() {
        return Collections.unmodifiableMap(values);
    }

    public synchronized void setValue(Material material, long value) throws IOException {
        String key = material.name().toLowerCase(Locale.ROOT);
        configuration.set("values." + key, Math.max(0L, value));
        configuration.save(file);
        if (value > 0) {
            values.put(material, value);
        } else {
            values.remove(material);
        }
    }
}
