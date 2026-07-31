package com.yangzijian52.projectejb.form;

import org.bukkit.entity.Player;

public interface FormBridge {
    boolean available();

    boolean isBedrock(Player player);

    boolean openMain(Player player);

    String status();

    void clear(Player player);
}
