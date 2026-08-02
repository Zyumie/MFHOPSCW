package fr.zyumie.oxygenArmor;

import fr.zyumie.main.oxygen.api.OxygenSupplier;
import org.bukkit.entity.Player;

public class ArmorOxygenSupplier implements OxygenSupplier {

    private final ArmorManager armorManager;

    public ArmorOxygenSupplier(ArmorManager armorManager) {
        this.armorManager = armorManager;
    }

    @Override
    public int priority() { return 10; }

    @Override
    public boolean supplyTick(Player player, double percentNeeded) {
        if (!armorManager.isWearingArmor(player)) return false;
        if (armorManager.getReserve(player) <= 0.0) return false;
        armorManager.modifyReserve(player, -percentNeeded);
        return true;
    }
}
