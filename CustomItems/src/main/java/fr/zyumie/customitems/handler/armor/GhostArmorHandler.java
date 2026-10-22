package fr.zyumie.customitems.handler.armor;

import fr.zyumie.customitems.ItemRegistry;
import fr.zyumie.customitems.util.ItemBuilder;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Armure Fantôme :
 * - Set complet → Invisibilité permanente + nametag caché
 * - Réception d'un coup → visible pendant N secondes puis re-invisible
 */
public class GhostArmorHandler {

    private static final String TEAM_NAME = "ghost_invis";

    private final JavaPlugin plugin;
    // Joueurs actuellement invisibles grâce à l'armure
    private final Set<UUID> invisible = new HashSet<>();
    // Joueurs en phase "révélé" (attendent de redevenir invisibles)
    private final Set<UUID> revealed  = new HashSet<>();

    public GhostArmorHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        ensureTeam();
    }

    /** Appelé à chaque tick de vérification d'armure */
    public void onArmorTick(Player player) {
        if (!plugin.getConfig().getBoolean("armors.ghost.enabled", true)) return;
        boolean wearing = wearingFullSet(player);

        if (wearing && !invisible.contains(player.getUniqueId())
                    && !revealed.contains(player.getUniqueId())) {
            makeInvisible(player);
        } else if (!wearing && invisible.contains(player.getUniqueId())) {
            makeVisible(player, false);
        }
    }

    /** Appelé quand le joueur reçoit des dégâts */
    public void onHit(Player player) {
        if (!invisible.contains(player.getUniqueId())) return;
        int duration = plugin.getConfig().getInt("armors.ghost.reveal-duration-seconds", 5);
        makeVisible(player, true);

        send(player, plugin.getConfig().getString("armors.ghost.messages.revealed", "")
                .replace("{duration}", String.valueOf(duration)));

        // Redevient invisible après N secondes
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            revealed.remove(player.getUniqueId());
            if (wearingFullSet(player)) makeInvisible(player);
        }, duration * 20L);
    }

    public void onUnequip(Player player) {
        if (invisible.contains(player.getUniqueId()) || revealed.contains(player.getUniqueId())) {
            makeVisible(player, false);
        }
    }

    public boolean isInvisible(Player player) {
        return invisible.contains(player.getUniqueId());
    }

    // ── Internes ──────────────────────────────────────────────────────

    private void makeInvisible(Player player) {
        invisible.add(player.getUniqueId());
        // Invisibilité niveau 2 (amp 1) — tente de masquer l'armure aussi
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                Integer.MAX_VALUE, 1, false, false, false));
        hideNameTag(player);
        send(player, plugin.getConfig().getString("armors.ghost.messages.invisible", ""));
    }

    private void makeVisible(Player player, boolean isRevealed) {
        invisible.remove(player.getUniqueId());
        if (isRevealed) revealed.add(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        showNameTag(player);
        if (!isRevealed)
            send(player, plugin.getConfig().getString("armors.ghost.messages.restored", ""));
    }

    private void hideNameTag(Player player) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = sb.getTeam(TEAM_NAME);
        if (team == null) team = sb.registerNewTeam(TEAM_NAME);
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        team.addPlayer(player);
    }

    private void showNameTag(Player player) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = sb.getTeam(TEAM_NAME);
        if (team != null) team.removePlayer(player);
    }

    private void ensureTeam() {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        if (sb.getTeam(TEAM_NAME) == null) {
            Team t = sb.registerNewTeam(TEAM_NAME);
            t.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }
    }

    private boolean wearingFullSet(Player player) {
        int c = 0;
        for (ItemStack p : player.getInventory().getArmorContents())
            if (ItemBuilder.isCustomItem(plugin, p, ItemRegistry.GHOST_ARMOR)) c++;
        return c == 4;
    }

    private void send(Player p, String message) {
        if (message == null || message.isEmpty()) return;
        p.sendMessage(MiniMessage.miniMessage().deserialize(message));
    }

    public void cleanup(Player player) {
        makeVisible(player, false);
        invisible.remove(player.getUniqueId());
        revealed.remove(player.getUniqueId());
    }
}
