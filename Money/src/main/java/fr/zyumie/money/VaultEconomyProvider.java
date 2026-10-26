package fr.zyumie.money;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Locale;

/**
 * Implémente l'interface Vault Economy et s'enregistre comme provider.
 * Tous les plugins qui utilisent Vault (FactionPlugin, ProfilePlugin, WeeklyQuestPlugin)
 * passeront automatiquement par cette classe.
 */
public class VaultEconomyProvider implements Economy {

    private final Money plugin;
    private final MoneyManager manager;

    public VaultEconomyProvider(Money plugin, MoneyManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override public boolean isEnabled()     { return plugin.isEnabled(); }
    @Override public String getName()        { return "MoneyPlugin"; }
    @Override public boolean hasBankSupport() { return false; }
    @Override public int fractionalDigits()  { return 2; }

    @Override
    public String format(double amount) {
        return String.format(Locale.ROOT, "%.2f %s", amount, currencyNamePlural());
    }

    @Override
    public String currencyNamePlural()   { return plugin.getConfig().getString("economy.currency-plural",   "Crédits"); }
    @Override
    public String currencyNameSingular() { return plugin.getConfig().getString("economy.currency-singular", "Crédit");  }

    // ── Balance ──────────────────────────────────────────────────────────

    @Override public double getBalance(OfflinePlayer player) { return manager.getBalance(player.getUniqueId()); }
    @Override public double getBalance(String playerName)    { return getBalance(resolve(playerName)); }
    @Override public double getBalance(OfflinePlayer player, String world) { return getBalance(player); }
    @Override public double getBalance(String name, String world)          { return getBalance(name); }

    @Override public boolean has(OfflinePlayer player, double amount) { return manager.has(player.getUniqueId(), amount); }
    @Override public boolean has(String name, double amount)          { return has(resolve(name), amount); }
    @Override public boolean has(OfflinePlayer player, String world, double amount) { return has(player, amount); }
    @Override public boolean has(String name, String world, double amount)          { return has(name, amount); }

    // ── Deposit ──────────────────────────────────────────────────────────

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (!MoneyManager.isValidAmount(amount)) return fail(amount, player, "Montant invalide");
        boolean ok = manager.deposit(player.getUniqueId(), amount);
        if (!ok) return fail(amount, player, "Dépôt dépasserait le solde maximum");
        return ok(amount, player);
    }

    @Override public EconomyResponse depositPlayer(String name, double amount) { return depositPlayer(resolve(name), amount); }
    @Override public EconomyResponse depositPlayer(OfflinePlayer p, String w, double a) { return depositPlayer(p, a); }
    @Override public EconomyResponse depositPlayer(String n, String w, double a)        { return depositPlayer(n, a); }

    // ── Withdraw ─────────────────────────────────────────────────────────

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (!MoneyManager.isValidAmount(amount)) return fail(amount, player, "Montant invalide");
        boolean ok = manager.withdraw(player.getUniqueId(), amount);
        if (!ok) return fail(amount, player, "Solde insuffisant");
        return ok(amount, player);
    }

    @Override public EconomyResponse withdrawPlayer(String name, double amount) { return withdrawPlayer(resolve(name), amount); }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer p, String w, double a) { return withdrawPlayer(p, a); }
    @Override public EconomyResponse withdrawPlayer(String n, String w, double a)        { return withdrawPlayer(n, a); }

    // ── Account (non utilisé mais requis par l'interface) ────────────────

    @Override public boolean hasAccount(OfflinePlayer player)              { return true; }
    @Override public boolean hasAccount(String name)                       { return true; }
    @Override public boolean hasAccount(OfflinePlayer p, String w)        { return true; }
    @Override public boolean hasAccount(String n, String w)               { return true; }
    @Override public boolean createPlayerAccount(OfflinePlayer player)     { manager.loadPlayer(player.getUniqueId()); return true; }
    @Override public boolean createPlayerAccount(String name)             { return createPlayerAccount(resolve(name)); }
    @Override public boolean createPlayerAccount(OfflinePlayer p, String w) { return createPlayerAccount(p); }
    @Override public boolean createPlayerAccount(String n, String w)      { return createPlayerAccount(n); }

    // ── Bank (non supporté) ──────────────────────────────────────────────

    @Override public EconomyResponse createBank(String name, OfflinePlayer player) { return notSupported(); }
    @Override public EconomyResponse createBank(String name, String player)        { return notSupported(); }
    @Override public EconomyResponse deleteBank(String name)                       { return notSupported(); }
    @Override public EconomyResponse bankBalance(String name)                      { return notSupported(); }
    @Override public EconomyResponse bankHas(String name, double amount)           { return notSupported(); }
    @Override public EconomyResponse bankWithdraw(String name, double amount)      { return notSupported(); }
    @Override public EconomyResponse bankDeposit(String name, double amount)       { return notSupported(); }
    @Override public EconomyResponse isBankOwner(String name, OfflinePlayer p)     { return notSupported(); }
    @Override public EconomyResponse isBankOwner(String name, String p)            { return notSupported(); }
    @Override public EconomyResponse isBankMember(String name, OfflinePlayer p)    { return notSupported(); }
    @Override public EconomyResponse isBankMember(String name, String p)           { return notSupported(); }
    @Override public List<String> getBanks()                                        { return List.of(); }

    // ── Helpers ──────────────────────────────────────────────────────────

    private OfflinePlayer resolve(String name) {
        OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayerIfCached(name);
        return op != null ? op : org.bukkit.Bukkit.getOfflinePlayer(name);
    }

    private EconomyResponse ok(double amount, OfflinePlayer player) {
        return new EconomyResponse(amount, manager.getBalance(player.getUniqueId()),
                EconomyResponse.ResponseType.SUCCESS, null);
    }

    private EconomyResponse fail(double amount, OfflinePlayer player, String msg) {
        return new EconomyResponse(amount, manager.getBalance(player.getUniqueId()),
                EconomyResponse.ResponseType.FAILURE, msg);
    }

    private EconomyResponse notSupported() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Non supporté");
    }
}
