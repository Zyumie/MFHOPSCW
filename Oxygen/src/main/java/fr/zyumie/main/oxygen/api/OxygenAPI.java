package fr.zyumie.main.oxygen.api;

import fr.zyumie.main.oxygen.OxygenManager;
import fr.zyumie.main.oxygen.OxygenModule;
import fr.zyumie.main.oxygen.Oxygen;
import org.bukkit.entity.Player;

/**
 * Point d'accès statique pour les plugins externes (ex: OxygenArmorPlugin).
 *
 * Usage depuis OxygenArmorPlugin:
 *
 *   // Enregistrer la réserve de l'armure comme source d'oxygène
 *   OxygenAPI.get().registerSupplier(new ArmorOxygenSupplier(...));
 *
 *   // Lire le % de poumons d'un joueur
 *   double pct = OxygenAPI.get().getPercent(player);
 *
 * Le plugin appelant doit avoir "depend: [OxygenPlugin]" dans son plugin.yml.
 */
public final class OxygenAPI {

    private static OxygenAPI instance;

    private final Oxygen plugin;

    private OxygenAPI(Oxygen plugin) {
        this.plugin = plugin;
    }

    public static void init(Oxygen plugin) {
        instance = new OxygenAPI(plugin);
    }

    public static OxygenAPI get() {
        if (instance == null) {
            throw new IllegalStateException(
                "OxygenAPI non initialisée. Vérifie que OxygenPlugin est chargé " +
                "et que ton plugin a 'depend: [OxygenPlugin]' dans son plugin.yml."
            );
        }
        return instance;
    }

    // ---------- Jauge poumons ----------

    public double getPercent(Player player) {
        return plugin.getOxygenManager().getPercent(player);
    }

    public void setPercent(Player player, double value) {
        plugin.getOxygenManager().setPercent(player, value);
    }

    public void modify(Player player, double delta) {
        plugin.getOxygenManager().modify(player, delta);
    }

    public boolean isSurface(Player player) {
        return plugin.getOxygenManager().isSurface(player);
    }

    // ---------- Suppliers ----------

    public void registerSupplier(OxygenSupplier supplier) {
        plugin.getOxygenModule().registerSupplier(supplier);
    }

    public void unregisterSupplier(OxygenSupplier supplier) {
        plugin.getOxygenModule().unregisterSupplier(supplier);
    }

    // ---------- Accès direct ----------

    public OxygenManager getManager() {
        return plugin.getOxygenManager();
    }

    public OxygenModule getModule() {
        return plugin.getOxygenModule();
    }
}
