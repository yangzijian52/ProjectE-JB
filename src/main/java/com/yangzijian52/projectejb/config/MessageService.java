package com.yangzijian52.projectejb.config;

import com.yangzijian52.projectejb.data.AccountStore;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class MessageService {
    private final JavaPlugin plugin;
    private final AccountStore store;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<PlayerLocale, YamlConfiguration> languages = new HashMap<>();
    private final Map<UUID, PlayerLocale> localeCache = new ConcurrentHashMap<>();
    private PlayerLocale defaultLocale;
    private PlayerLocale fallbackLocale;

    public MessageService(JavaPlugin plugin, AccountStore store) {
        this.plugin = plugin;
        this.store = store;
        reload();
    }

    public void reload() {
        defaultLocale = PlayerLocale.from(plugin.getConfig().getString("language.default"), PlayerLocale.ZH_CN);
        fallbackLocale = PlayerLocale.from(plugin.getConfig().getString("language.fallback"), PlayerLocale.EN_US);
        languages.clear();
        localeCache.clear();
        for (PlayerLocale locale : PlayerLocale.values()) {
            String resource = "lang/" + locale.key() + ".yml";
            File file = new File(plugin.getDataFolder(), resource);
            if (!file.exists()) {
                plugin.saveResource(resource, false);
            }
            languages.put(locale, YamlConfiguration.loadConfiguration(file));
        }
    }

    public PlayerLocale locale(Player player) {
        return localeCache.computeIfAbsent(player.getUniqueId(), ignored -> {
            String stored = store.getLanguage(player.getUniqueId(), player.getName());
            return PlayerLocale.from(stored, defaultLocale);
        });
    }

    public PlayerLocale locale(UUID playerId, String playerName) {
        return localeCache.computeIfAbsent(playerId,
                ignored -> PlayerLocale.from(store.getLanguage(playerId, playerName), defaultLocale));
    }

    public PlayerLocale defaultLocale() {
        return defaultLocale;
    }

    public Component component(Player player, String key, Map<String, ?> placeholders) {
        return component(locale(player), key, placeholders);
    }

    public Component component(PlayerLocale locale, String key, Map<String, ?> placeholders) {
        return miniMessage.deserialize(replace(raw(locale, key), placeholders));
    }

    public String plain(Player player, String key, Map<String, ?> placeholders) {
        return PlainTextComponentSerializer.plainText().serialize(component(player, key, placeholders));
    }

    public String plain(PlayerLocale locale, String key, Map<String, ?> placeholders) {
        return PlainTextComponentSerializer.plainText().serialize(component(locale, key, placeholders));
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, ?> placeholders) {
        PlayerLocale locale = sender instanceof Player player ? locale(player) : defaultLocale;
        Component prefix = component(locale, "prefix", Map.of());
        sender.sendMessage(prefix.append(component(locale, key, placeholders)));
    }

    public void sendRaw(CommandSender sender, Component component) {
        PlayerLocale locale = sender instanceof Player player ? locale(player) : defaultLocale;
        sender.sendMessage(component(locale, "prefix", Map.of()).append(component));
    }

    public List<Component> list(Player player, String key) {
        PlayerLocale locale = locale(player);
        List<String> entries = language(locale).getStringList(key);
        if (entries.isEmpty() && locale != fallbackLocale) {
            entries = language(fallbackLocale).getStringList(key);
        }
        return entries.stream().map(miniMessage::deserialize).toList();
    }

    public void setLocale(Player player, PlayerLocale locale) {
        store.setLanguage(player.getUniqueId(), player.getName(), locale.key());
        localeCache.put(player.getUniqueId(), locale);
    }

    public void clearLocaleCache(UUID playerId) {
        localeCache.remove(playerId);
    }

    private String raw(PlayerLocale locale, String key) {
        String value = language(locale).getString(key);
        if (value == null && locale != fallbackLocale) {
            value = language(fallbackLocale).getString(key);
        }
        return value == null ? "<red>Missing language key: " + key + "</red>" : value;
    }

    private YamlConfiguration language(PlayerLocale locale) {
        return languages.getOrDefault(locale, languages.get(fallbackLocale));
    }

    private static String replace(String input, Map<String, ?> placeholders) {
        String output = input;
        for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
            String safeValue = MiniMessage.miniMessage().escapeTags(String.valueOf(entry.getValue()));
            output = output.replace("{" + entry.getKey() + "}", safeValue);
        }
        return output;
    }
}
