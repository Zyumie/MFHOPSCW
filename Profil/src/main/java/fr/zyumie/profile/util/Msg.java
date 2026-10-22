package fr.zyumie.profile.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Parsing des messages au format MiniMessage (<dark_gray>, <red>, <bold>, ...) tels qu'utilisés dans
 * config.yml et gui.yml. Partagé entre le rendu chat (ProfileCommand) et le rendu GUI (ProfileGuiManager)
 * pour être sûr que les deux modes interprètent les messages de la même façon — équivalent de
 * fr.zyumie.money.util.Msg côté Money.
 */
public final class Msg {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Msg() {}

    public static Component parse(String text, TagResolver... resolvers) {
        return MM.deserialize(text == null ? "" : text, resolvers);
    }

    /** Neutralise les balises MiniMessage d'une valeur externe (nom de faction, ...) avant insertion. */
    public static String escape(String text) {
        return text == null ? "" : MM.escapeTags(text);
    }
}
