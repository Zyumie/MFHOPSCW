package fr.zyumie.oxygenArmor;

import fr.zyumie.main.api.MainAPI;
import fr.zyumie.main.module.Module;
import fr.zyumie.main.oxygen.api.OxygenAPI;
import fr.zyumie.oxygenArmor.listener.ArmorEquipListener;
import fr.zyumie.oxygenArmor.listener.CustomCraftListener;
import fr.zyumie.oxygenArmor.task.ArmorDisplayTask;
import fr.zyumie.oxygenArmor.task.ArmorStandChargeTask;
import fr.zyumie.oxygenArmor.util.ArmorItemUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class OxygenArmor extends JavaPlugin implements Module {

    private ArmorManager armorManager;
    private ArmorOxygenSupplier supplier;
    private ArmorDisplayTask displayTask;
    private BukkitTask displayBukkitTask;
    private BukkitTask chargeBukkitTask;

    // ─────────────── JavaPlugin lifecycle ───────────────

    @Override
    public void onEnable() {
        saveDefaultConfig();
        armorManager = new ArmorManager(this);
        getServer().getScheduler().runTaskLater(this, () ->
            MainAPI.get().getModuleManager().register(this), 1L);
    }

    @Override
    public void onDisable() {
        if (armorManager != null) armorManager.saveAll();
    }

    // ─────────────── Module ───────────────

    @Override public String getId()          { return "oxygen-armor"; }
    @Override public String getDisplayName() { return "Armure Oxygène"; }
    @Override public String[] getDependencies() { return new String[]{"oxygen"}; }

    @Override
    public void onModuleEnable() {
        getServer().getPluginManager().registerEvents(new ArmorEquipListener(this, armorManager), this);
        getServer().getPluginManager().registerEvents(new CustomCraftListener(this), this);

        supplier = new ArmorOxygenSupplier(armorManager);
        OxygenAPI.get().registerSupplier(supplier);

        displayTask = new ArmorDisplayTask(armorManager);
        displayBukkitTask = displayTask.runTaskTimer(this, 0L, 4L);

        int chargeInterval = getConfig().getInt("armor.recharge.tick-interval-ticks", 100);
        chargeBukkitTask = new ArmorStandChargeTask(this)
                .runTaskTimer(this, 40L, chargeInterval);

        getLogger().info("OxygenArmor activé.");
    }

    @Override
    public void onModuleDisable() {
        if (supplier != null) OxygenAPI.get().unregisterSupplier(supplier);
        if (displayTask != null) displayTask.cleanup();
        if (displayBukkitTask != null) displayBukkitTask.cancel();
        if (chargeBukkitTask != null) chargeBukkitTask.cancel();
        armorManager.saveAll();
        getLogger().info("OxygenArmor désactivé.");
    }

    // ─────────────── Commande admin ───────────────

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("oxygenarmor")) return false;

        if (!sender.hasPermission("oxygenarmor.admin")) {
            sender.sendMessage(mm(cfg("messages.prefix") + cfg("messages.no-permission")));
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("give")) {
            Player target = args.length >= 2
                    ? Bukkit.getPlayer(args[1])
                    : (sender instanceof Player p ? p : null);

            if (target == null) {
                sender.sendMessage(mm("<red>Joueur introuvable ou non spécifié."));
                return true;
            }
            target.getInventory().addItem(ArmorItemUtil.create(this, 100.0));
            sender.sendMessage(mm(cfg("messages.prefix") +
                    cfg("messages.give-success").replace("{player}", target.getName())));
            return true;
        }

        sender.sendMessage(mm("<gold>/oxygenarmor give [joueur]"));
        return true;
    }

    // ─────────────── Helpers ───────────────

    private String cfg(String path) { return getConfig().getString(path, ""); }

    private net.kyori.adventure.text.Component mm(String legacy) {
        String s = legacy
                .replace("&8","<dark_gray>").replace("&b","<aqua>")
                .replace("&7","<gray>").replace("&a","<green>")
                .replace("&c","<red>").replace("&e","<yellow>")
                .replace("&6","<gold>");
        return MiniMessage.miniMessage().deserialize(s);
    }

    public ArmorManager getArmorManager() { return armorManager; }
}
