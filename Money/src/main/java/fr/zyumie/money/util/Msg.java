package fr.zyumie.money.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

/**
 * Envoi de messages au format MiniMessage (<red>, <gray>, <bold>, ...) tels qu'utilisés dans config.yml.
 *
 * Centralisé ici pour que toutes les commandes (MoneyCommand, PayCommand, EcoCommand, MoneyTopCommand)
 * interprètent les messages de la même façon.
 */
public final class Msg {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Msg() {}

    public static Component parse(String text) {
        return MM.deserialize(text == null ? "" : text);
    }

    public static void send(CommandSender sender, String text) {
        sender.sendMessage(parse(text));
    }
}
