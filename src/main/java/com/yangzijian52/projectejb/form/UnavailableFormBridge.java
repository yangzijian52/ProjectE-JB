package com.yangzijian52.projectejb.form;

import org.bukkit.entity.Player;

public final class UnavailableFormBridge implements FormBridge {
    private final String reason;

    public UnavailableFormBridge(String reason) {
        this.reason = reason;
    }

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public boolean isBedrock(Player player) {
        return false;
    }

    @Override
    public boolean openMain(Player player) {
        return false;
    }

    @Override
    public String status() {
        return reason;
    }

    @Override
    public void clear(Player player) {
        // No state when Floodgate is unavailable.
    }
}
