package fr.zyumie.profile.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Parsing des couleurs "legacy" (&8, &c, &l, ...) telles qu'utilisées dans config.yml et gui.yml.
 * Partagé entre le rendu chat (ProfileCommand) et le rendu GUI (ProfileGuiManager) pour être sûr
 * que les deux modes interprètent les codes couleur de la même façon — équivalent de
 * fr.zyumie.money.util.Msg côté Money.
 */
public final class Msg {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private Msg() {}

    public static Component parse(String legacyText) {
        return LEGACY.deserialize(legacyText == null ? "" : legacyText);
    }
}
