package fr.zyumie.money;

import fr.zyumie.main.api.MainAPI;
import fr.zyumie.main.module.Module;
import fr.zyumie.money.command.EcoCommand;
import fr.zyumie.money.command.MoneyCommand;
import fr.zyumie.money.command.MoneyTopCommand;
import fr.zyumie.money.command.PayCommand;
import fr.zyumie.money.listener.PlayerListener;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public class Money extends JavaPlugin implements Module {

    private MoneyManager moneyManager;
    private VaultEconomyProvider economyProvider;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        moneyManager = new MoneyManager(this);
        MainAPI.get().getModuleManager().register(this);
    }

    @Override
    public void onDisable() {
        if (moneyManager != null) moneyManager.saveAll();
        // Désenregistre le provider Vault proprement
        getServer().getServicesManager().unregisterAll(this);
    }

    @Override public String getId()          { return "money"; }
    @Override public String getDisplayName() { return "Money"; }

    @Override
    public void onModuleEnable() {
        // Enregistrement Vault — tous les plugins qui utilisent Economy passeront par ici
        economyProvider = new VaultEconomyProvider(this, moneyManager);
        getServer().getServicesManager().register(
                Economy.class, economyProvider, this, ServicePriority.Highest);
        getLogger().info("Economy Vault enregistrée: " + economyProvider.getName());

        // Listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(moneyManager), this);

        // Commandes
        MoneyCommand moneyCm = new MoneyCommand(this, moneyManager);
        getCommand("money").setExecutor(moneyCm);
        getCommand("money").setTabCompleter(moneyCm);

        PayCommand payCm = new PayCommand(this, moneyManager);
        getCommand("pay").setExecutor(payCm);
        getCommand("pay").setTabCompleter(payCm);

        MoneyTopCommand topCm = new MoneyTopCommand(this, moneyManager);
        getCommand("moneytop").setExecutor(topCm);
        getCommand("moneytop").setTabCompleter(topCm);

        EcoCommand ecoCm = new EcoCommand(this, moneyManager);
        getCommand("eco").setExecutor(ecoCm);
        getCommand("eco").setTabCompleter(ecoCm);

        getLogger().info("MoneyModule activé.");
    }

    @Override
    public void onModuleDisable() {
        moneyManager.saveAll();
        getServer().getServicesManager().unregisterAll(this);
        getLogger().info("MoneyModule désactivé.");
    }

    public MoneyManager getMoneyManager() { return moneyManager; }
}
