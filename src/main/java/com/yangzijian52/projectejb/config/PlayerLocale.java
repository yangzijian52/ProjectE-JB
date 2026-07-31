package com.yangzijian52.projectejb.config;

import java.util.Locale;

public enum PlayerLocale {
    ZH_CN("zh_cn"),
    EN_US("en_us");

    private final String key;

    PlayerLocale(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static PlayerLocale from(String value, PlayerLocale fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "zh", "zh_cn", "chinese", "简体中文" -> ZH_CN;
            case "en", "en_us", "english" -> EN_US;
            default -> fallback;
        };
    }
}
