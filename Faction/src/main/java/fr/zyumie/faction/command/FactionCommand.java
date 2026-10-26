package fr.zyumie.faction.command;

import fr.zyumie.faction.listener.FactionManager;
import fr.zyumie.faction.Faction;
import fr.zyumie.faction.GradeHelper;
import fr.zyumie.faction.model.FactionGrade;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FactionCommand implements CommandExecutor, TabCompleter {

    private final Faction plugin;
    private final FactionManager manager;
    private final GradeHelper gradeHelper;

    private final Map<UUID, Long> deleteConfirm = new HashMap<>();
    private static final long CONFIRM_WINDOW_MS = 10_000;

    public FactionCommand(Faction plugin, FactionManager manager, GradeHelper gradeHelper) {
        this.plugin      = plugin;
        this.manager     = manager;
        this.gradeHelper = gradeHelper;
    }

    private Economy economy() {
        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        return rsp != null ? rsp.getProvider() : null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Commande réservée aux joueurs."); return true; }
        if (args.length == 0) { sendUsage(player); return true; }
        switch (args[0].toLowerCase()) {
            case "create"   -> cmdCreate(player, args);
            case "delete"   -> cmdDelete(player);
            case "invite"   -> cmdInvite(player, args);
            case "join"     -> cmdJoin(player, args);
            case "leave"    -> cmdLeave(player);
            case "kick"     -> cmdKick(player, args);
            case "promote"  -> cmdPromote(player, args);
            case "demote"   -> cmdDemote(player, args);
            case "info"     -> cmdInfo(player, args);
            case "list"     -> cmdList(player);
            case "deposit"  -> cmdDeposit(player, args);
            case "withdraw" -> cmdWithdraw(player, args);
            case "bank"     -> cmdBank(player);
            case "pvp"      -> cmdPvp(player, args);
            default         -> sendUsage(player);
        }
        return true;
    }

    private void cmdCreate(Player player, String[] args) {
        if (args.length < 2) { send(player, "<red>Usage: /f create <nom>"); return; }
        if (manager.isInFaction(player.getUniqueId())) { sendMsg(player, "messages.already-in-faction"); return; }
        String name = args[1];
        int max = plugin.getConfig().getInt("faction.max-name-length", 16);
        if (name.length() > max || name.contains(" ")) { sendMsg(player, "messages.create-name-invalid", "{max}", String.valueOf(max)); return; }
        if (manager.exists(name)) { sendMsg(player, "messages.create-already-exists", "{name}", name); return; }
        manager.create(name, player.getUniqueId());
        sendMsg(player, "messages.create-success", "{name}", name);
    }

    private void cmdDelete(Player player) {
        fr.zyumie.faction.model.Faction f = requireFaction(player); if (f == null) return;
        if (!f.isChef(player.getUniqueId())) { sendMsg(player, "messages.no-permission"); return; }
        long now = System.currentTimeMillis();
        Long prev = deleteConfirm.get(player.getUniqueId());
        if (prev == null || now - prev > CONFIRM_WINDOW_MS) {
            deleteConfirm.put(player.getUniqueId(), now);
            sendMsg(player, "messages.delete-confirm"); return;
        }
        deleteConfirm.remove(player.getUniqueId());
        String name = f.getName();
        f.getMembers().keySet().forEach(uuid -> {
            Player m = Bukkit.getPlayer(uuid);
            if (m != null) m.sendMessage(mm(prefix() + "<red>La faction <yellow>" + name + "<red> a été dissoute."));
        });
        manager.disband(f);
        sendMsg(player, "messages.delete-success", "{name}", name);
    }

    private void cmdInvite(Player player, String[] args) {
        fr.zyumie.faction.model.Faction f = requireFaction(player); if (f == null) return;
        if (!gradeHelper.hasPerm(f.getGrade(player.getUniqueId()), "invite")) { sendMsg(player, "messages.no-permission"); return; }
        if (args.length < 2) { send(player, "<red>Usage: /f invite <joueur>"); return; }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { sendMsg(player, "messages.player-not-found", "{name}", args[1]); return; }
        if (manager.isInFaction(target.getUniqueId())) { sendMsg(player, "messages.already-in-faction"); return; }
        f.addInvite(target.getUniqueId());
        sendMsg(player, "messages.invite-sent", "{player}", target.getName());
        target.sendMessage(mm(prefix() + plugin.getConfig().getString("messages.invite-received", "")
                .replace("{faction}", f.getName())));
    }

    private void cmdJoin(Player player, String[] args) {
        if (manager.isInFaction(player.getUniqueId())) { sendMsg(player, "messages.already-in-faction"); return; }
        if (args.length < 2) { send(player, "<red>Usage: /f join <nom_faction>"); return; }
        fr.zyumie.faction.model.Faction f = manager.getFaction(args[1]);
        if (f == null) { sendMsg(player, "messages.faction-not-found", "{name}", args[1]); return; }
        if (!f.isInvited(player.getUniqueId())) { sendMsg(player, "messages.invite-none"); return; }
        f.removeInvite(player.getUniqueId());
        manager.addMember(f, player.getUniqueId(), FactionGrade.INVITE);
        sendMsg(player, "messages.join-success", "{faction}", f.getName());
        broadcastFaction(f, player.getUniqueId(),
                plugin.getConfig().getString("messages.join-broadcast", "").replace("{player}", player.getName()));
    }

    private void cmdLeave(Player player) {
        fr.zyumie.faction.model.Faction f = requireFaction(player); if (f == null) return;
        if (f.isChef(player.getUniqueId())) { sendMsg(player, "messages.leave-chef"); return; }
        String name = f.getName();
        manager.removeMember(f, player.getUniqueId());
        sendMsg(player, "messages.leave-success", "{faction}", name);
        broadcastFaction(f, null, plugin.getConfig().getString("messages.leave-broadcast", "")
                .replace("{player}", player.getName()));
    }

    private void cmdKick(Player player, String[] args) {
        fr.zyumie.faction.model.Faction f = requireFaction(player); if (f == null) return;
        FactionGrade myGrade = f.getGrade(player.getUniqueId());
        if (!gradeHelper.hasPerm(myGrade, "kick")) { sendMsg(player, "messages.no-permission"); return; }
        if (args.length < 2) { send(player, "<red>Usage: /f kick <joueur>"); return; }
        Player target = Bukkit.getPlayer(args[1]);
        UUID tUuid = target != null ? target.getUniqueId() : resolveOffline(args[1]);
        if (tUuid == null || !f.isMember(tUuid)) { sendMsg(player, "messages.player-not-in-faction"); return; }
        if (tUuid.equals(player.getUniqueId())) { sendMsg(player, "messages.kick-self"); return; }
        if (!myGrade.outranks(f.getGrade(tUuid))) { sendMsg(player, "messages.kick-outranks"); return; }
        manager.removeMember(f, tUuid);
        String tName = target != null ? target.getName() : args[1];
        sendMsg(player, "messages.kick-success", "{player}", tName);
        broadcastFaction(f, null, plugin.getConfig().getString("messages.kick-broadcast", "")
                .replace("{player}", tName).replace("{by}", player.getName()));
        if (target != null) target.sendMessage(mm(prefix() + "<red>Tu as été exclu de la faction."));
    }

    private void cmdPromote(Player p, String[] a) { changeGrade(p, a, true); }
    private void cmdDemote(Player p, String[] a)  { changeGrade(p, a, false); }

    private void changeGrade(Player player, String[] args, boolean promote) {
        fr.zyumie.faction.model.Faction f = requireFaction(player); if (f == null) return;
        FactionGrade myGrade = f.getGrade(player.getUniqueId());
        if (!gradeHelper.hasPerm(myGrade, promote ? "promote" : "demote")) { sendMsg(player, "messages.no-permission"); return; }
        if (args.length < 2) { send(player, "<red>Usage: /f " + (promote ? "promote" : "demote") + " <joueur>"); return; }
        Player target = Bukkit.getPlayer(args[1]);
        UUID tUuid = target != null ? target.getUniqueId() : resolveOffline(args[1]);
        if (tUuid == null || !f.isMember(tUuid)) { sendMsg(player, "messages.player-not-in-faction"); return; }
        FactionGrade cur = f.getGrade(tUuid);
        FactionGrade next = promote ? cur.next() : cur.previous();
        if (promote && cur.isMax()) { sendMsg(player, "messages.promote-max", "{player}", args[1]); return; }
        if (!promote && cur.isMin()) { sendMsg(player, "messages.demote-min", "{player}", args[1]); return; }
        if (promote && !myGrade.outranks(next)) { sendMsg(player, "messages.promote-outranks"); return; }
        manager.setGrade(f, tUuid, next);
        sendMsg(player, promote ? "messages.promote-success" : "messages.demote-success",
                "{player}", args[1], "{grade}", gradeHelper.coloredDisplay(next));
    }

    private void cmdInfo(Player player, String[] args) {
        fr.zyumie.faction.model.Faction f = args.length >= 2 ? manager.getFaction(args[1]) : manager.getFactionOf(player.getUniqueId());
        if (f == null) {
            sendMsg(player, args.length >= 2 ? "messages.faction-not-found" : "messages.not-in-faction",
                    "{name}", args.length >= 2 ? args[1] : ""); return;
        }
        send(player, "<dark_gray>─── <gold><bold>" + f.getName() + "</bold> <dark_gray>───");
        send(player, "<gray>Membres: <white>" + f.getMemberCount() + "  <gray>Bank: <yellow>" + fmt(f.getBankBalance()) + "  <gray>PvP: " + (f.isPvpEnabled() ? "<green>Activé" : "<red>Désactivé"));
        f.getMembers().forEach((uuid, grade) -> {
            String n = Bukkit.getOfflinePlayer(uuid).getName();
            if (n == null) n = uuid.toString().substring(0, 8);
            String online = Bukkit.getPlayer(uuid) != null ? "<green>●" : "<gray>○";
            send(player, "  " + online + " <white>" + n + " <gray>(" + gradeHelper.coloredDisplay(grade) + "<gray>)");
        });
    }

    private void cmdList(Player player) {
        send(player, "<dark_gray>─── <gold>Factions <dark_gray>───");
        if (manager.getAllFactions().isEmpty()) { send(player, "<gray>Aucune faction créée."); return; }
        manager.getAllFactions().forEach(f ->
                send(player, "  <yellow>" + f.getName() + " <gray>(" + f.getMemberCount() + " membres)"));
    }

    private void cmdDeposit(Player player, String[] args) {
        Economy eco = economy();
        if (eco == null) { send(player, "<red>Aucun plugin d'économie actif sur le serveur."); return; }
        fr.zyumie.faction.model.Faction f = requireFaction(player); if (f == null) return;
        if (!gradeHelper.hasPerm(f.getGrade(player.getUniqueId()), "deposit")) { sendMsg(player, "messages.no-permission"); return; }
        if (args.length < 2) { send(player, "<red>Usage: /f deposit <montant>"); return; }
        double amount = parseAmount(args[1]); if (amount <= 0) { send(player, "<red>Montant invalide."); return; }
        if (!eco.has(player, amount)) {
            sendMsg(player, "messages.deposit-insufficient"); return;
        }
        eco.withdrawPlayer(player, amount);
        f.depositBank(amount);
        manager.saveFaction(f);
        sendMsg(player, "messages.deposit-success", "{amount}", fmt(amount),
                "{currency}", eco.currencyNamePlural());
    }

    private void cmdWithdraw(Player player, String[] args) {
        Economy eco = economy();
        if (eco == null) { send(player, "<red>Aucun plugin d'économie actif sur le serveur."); return; }
        fr.zyumie.faction.model.Faction f = requireFaction(player); if (f == null) return;
        if (!gradeHelper.hasPerm(f.getGrade(player.getUniqueId()), "withdraw")) { sendMsg(player, "messages.no-permission"); return; }
        if (args.length < 2) { send(player, "<red>Usage: /f withdraw <montant>"); return; }
        double amount = parseAmount(args[1]); if (amount <= 0) { send(player, "<red>Montant invalide."); return; }
        if (!f.withdrawBank(amount)) { sendMsg(player, "messages.withdraw-insufficient"); return; }
        eco.depositPlayer(player, amount);
        manager.saveFaction(f);
        sendMsg(player, "messages.withdraw-success", "{amount}", fmt(amount),
                "{currency}", eco.currencyNamePlural());
    }

    private void cmdBank(Player player) {
        fr.zyumie.faction.model.Faction f = requireFaction(player); if (f == null) return;
        Economy eco = economy();
        String unit = eco != null ? eco.currencyNamePlural() : "Crédits";
        send(player, plugin.getConfig().getString("messages.bank-header", "").replace("{faction}", f.getName()));
        send(player, plugin.getConfig().getString("messages.bank-balance", "")
                .replace("{amount}", fmt(f.getBankBalance()) + " " + unit));
    }

    private void cmdPvp(Player player, String[] args) {
        fr.zyumie.faction.model.Faction f = requireFaction(player); if (f == null) return;
        FactionGrade myGrade = f.getGrade(player.getUniqueId());
        if (!gradeHelper.hasPerm(myGrade, "pvp")) { sendMsg(player, "messages.no-permission"); return; }

        boolean newState;
        if (args.length >= 2) {
            String sub = args[1].toLowerCase();
            if (sub.equals("on") || sub.equals("true") || sub.equals("activer")) {
                newState = true;
            } else if (sub.equals("off") || sub.equals("false") || sub.equals("desactiver")) {
                newState = false;
            } else {
                send(player, "<red>Usage: /f pvp <on/off>");
                return;
            }
        } else {
            newState = !f.isPvpEnabled();
        }

        manager.setPvp(f, newState);
        String statusText = newState ? "<green>activé" : "<red>désactivé";
        sendMsg(player, "messages.pvp-success", "{status}", statusText);
        broadcastFaction(f, player.getUniqueId(), plugin.getConfig().getString("messages.pvp-broadcast", "")
                .replace("{status}", statusText).replace("{player}", player.getName()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1)
            return List.of("create","delete","invite","join","leave","kick",
                    "promote","demote","info","list","deposit","withdraw","bank","pvp");
        if (args.length == 2)
            return switch (args[0].toLowerCase()) {
                case "join","info" -> manager.getAllFactions().stream().map(fr.zyumie.faction.model.Faction::getName).toList();
                case "invite","kick","promote","demote" -> Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName).toList();
                case "pvp" -> List.of("on", "off");
                default -> List.of();
            };
        return List.of();
    }

    private fr.zyumie.faction.model.Faction requireFaction(Player p) {
        fr.zyumie.faction.model.Faction f = manager.getFactionOf(p.getUniqueId());
        if (f == null) sendMsg(p, "messages.not-in-faction");
        return f;
    }

    private void broadcastFaction(fr.zyumie.faction.model.Faction f, UUID exclude, String msg) {
        if (msg == null || msg.isEmpty()) return;
        f.getMembers().keySet().stream()
                .filter(u -> !u.equals(exclude))
                .map(Bukkit::getPlayer).filter(p -> p != null)
                .forEach(p -> p.sendMessage(mm(prefix() + msg)));
    }

    private UUID resolveOffline(String name) {
        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
        return op != null ? op.getUniqueId() : null;
    }

    private double parseAmount(String s) { try { double v = Double.parseDouble(s); return Double.isFinite(v) ? v : -1; } catch (NumberFormatException e) { return -1; } }
    private String fmt(double v)   { return String.format("%.2f", v); }
    private String prefix()        { return plugin.getConfig().getString("messages.prefix", ""); }

    private void sendMsg(Player p, String path, String... rep) {
        String msg = plugin.getConfig().getString(path, "");
        for (int i = 0; i + 1 < rep.length; i += 2) msg = msg.replace(rep[i], rep[i+1]);
        send(p, prefix() + msg);
    }

    private void send(Player p, String text) {
        p.sendMessage(mm(text));
    }

    private Component mm(String s) {
        if (s == null) {
            return Component.empty();
        }
        return MiniMessage.miniMessage().deserialize(s);
    }

    private void sendUsage(Player p) {
        send(p, "<gold>/f <gray>create|delete|invite|join|leave|kick|promote|demote|info|list|deposit|withdraw|bank|pvp");
    }
}