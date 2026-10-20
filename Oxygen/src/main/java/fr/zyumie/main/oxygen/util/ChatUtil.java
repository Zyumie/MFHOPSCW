package fr.zyumie.main.oxygen.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class ChatUtil {

    private ChatUtil() {}

    public static Component color(String message) {
        if (message == null) message = "";
        return MiniMessage.miniMessage().deserialize(message);
    }
}
