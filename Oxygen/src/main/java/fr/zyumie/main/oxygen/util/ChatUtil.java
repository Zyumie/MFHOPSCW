package fr.zyumie.main.oxygen.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class ChatUtil {

    private ChatUtil() {}

    public static Component color(String legacy) {
        if (legacy == null) legacy = "";
        String mm = legacy
                .replace("&8", "<dark_gray>")
                .replace("&4", "<dark_red>")
                .replace("&c", "<red>")
                .replace("&a", "<green>")
                .replace("&b", "<aqua>")
                .replace("&7", "<gray>")
                .replace("&f", "<white>")
                .replace("&l", "<bold>");
        return MiniMessage.miniMessage().deserialize(mm);
    }
}
