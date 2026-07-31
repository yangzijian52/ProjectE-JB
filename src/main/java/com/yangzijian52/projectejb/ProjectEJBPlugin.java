package com.yangzijian52.projectejb;

import com.yangzijian52.projectejb.command.ProjectEJBCommand;
import com.yangzijian52.projectejb.config.EmcValueRegistry;
import com.yangzijian52.projectejb.config.MessageService;
import com.yangzijian52.projectejb.data.AccountStore;
import com.yangzijian52.projectejb.data.SqliteAccountStore;
import com.yangzijian52.projectejb.economy.EmcService;
import com.yangzijian52.projectejb.economy.OperationResult;
import com.yangzijian52.projectejb.form.FloodgateFormBridge;
import com.yangzijian52.projectejb.form.FormBridge;
import com.yangzijian52.projectejb.form.UnavailableFormBridge;
import com.yangzijian52.projectejb.menu.JavaMenuController;
import java.io.File;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProjectEJBPlugin extends JavaPlugin {
    private AccountStore accountStore;
    private EmcValueRegistry emcValues;
    private MessageService messages;
    private EmcService emcService;
    private JavaMenuController menus;
    private FormBridge forms;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            initializeServices();
            registerEntryPoints();
            getLogger().info("ProjectE-JB 1.0.0 enabled for Paper " + Bukkit.getMinecraftVersion()
                    + "; Floodgate=" + forms.status() + "; EMC values=" + emcValues.size());
        } catch (Throwable throwable) {
            getLogger().log(Level.SEVERE, "ProjectE-JB failed to enable safely.", throwable);
            if (accountStore != null) {
                accountStore.close();
            }
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (menus != null) {
            menus.closeAll();
        }
        if (accountStore != null) {
            accountStore.close();
        }
    }

    private void initializeServices() {
        String databaseName = getConfig().getString("database.file", "data.db");
        File databaseFile = new File(getDataFolder(), databaseName);
        accountStore = new SqliteAccountStore(
                databaseFile,
                getConfig().getLong("economy.starting-emc", 0L),
                getConfig().getLong("economy.maximum-balance", 9_000_000_000_000_000_000L),
                getConfig().getInt("database.busy-timeout-millis", 5000),
                getConfig().getBoolean("logging.transaction-history", true));
        accountStore.initialize();
        emcValues = new EmcValueRegistry(this);
        messages = new MessageService(this, accountStore);
        emcService = new EmcService(this, accountStore, emcValues);
        menus = new JavaMenuController(this);

        boolean enabled = getConfig().getBoolean("floodgate.enabled", true);
        if (!enabled) {
            forms = new UnavailableFormBridge("disabled");
        } else if (!Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            forms = new UnavailableFormBridge("not-installed");
        } else {
            try {
                forms = new FloodgateFormBridge(this);
            } catch (Throwable throwable) {
                getLogger().log(Level.WARNING, "Floodgate API could not be initialized; Java menus remain available.", throwable);
                forms = new UnavailableFormBridge("api-error");
            }
        }
    }

    private void registerEntryPoints() {
        ProjectEJBCommand command = new ProjectEJBCommand(this);
        Objects.requireNonNull(getCommand("projectejb"), "projectejb command missing from plugin.yml")
                .setExecutor(command);
        Objects.requireNonNull(getCommand("projectejb")).setTabCompleter(command);
        Bukkit.getPluginManager().registerEvents(menus, this);
        Bukkit.getPluginManager().registerEvents(command, this);
    }

    public void openMenu(Player player) {
        if (forms.isBedrock(player) && forms.openMain(player)) {
            return;
        }
        menus.openMain(player);
    }

    public void showResult(Player player, OperationResult result) {
        messages.send(player, result.code().messageKey(), result.values());
        if (result.code() == OperationResult.Code.PAID) {
            String targetName = String.valueOf(result.values().get("player"));
            Player target = Bukkit.getPlayerExact(targetName);
            if (target != null && target.isOnline()) {
                messages.send(target, "general.received", Map.of(
                        "player", player.getName(),
                        "amount", result.values().getOrDefault("amount", "0")));
            }
        }
    }

    public void reloadProject() {
        reloadConfig();
        emcValues.reload();
        messages.reload();
        emcService.reloadSettings();
        menus.reloadSettings();
    }

    public AccountStore accountStore() {
        return accountStore;
    }

    public EmcValueRegistry emcValues() {
        return emcValues;
    }

    public MessageService messages() {
        return messages;
    }

    public EmcService emcService() {
        return emcService;
    }

    public JavaMenuController menus() {
        return menus;
    }

    public FormBridge forms() {
        return forms;
    }
}
