package fr.zyumie.money.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

/**
 * Envoi de messages en couleurs "legacy" (&8, &c, &e, ...) telles qu'utilisées dans config.yml.
 *
 * Avant : chaque commande (MoneyCommand, PayCommand, EcoCommand, MoneyTopCommand) avait sa
 * propre chaîne de .replace("&X", "<tag>") avant de passer par MiniMessage. Ces listes étaient
 * dupliquées ET incomplètes (MoneyTopCommand n'avait pas &c par exemple) : tout code couleur
 * ajouté dans config.yml mais absent de la liste s'affichait tel quel ("&c") au lieu d'être coloré.
 *
 * Ici, LegacyComponentSerializer gère TOUS les codes standards (&0-&9, &a-&f, &k-&o, &r) de façon
 * fiable et centralisée.
 */
public final class Msg {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private Msg() {}

    public static Component parse(String legacyText) {
        return LEGACY.deserialize(legacyText);
    }

    public static void send(CommandSender sender, String legacyText) {
        sender.sendMessage(parse(legacyText));
    }
}
