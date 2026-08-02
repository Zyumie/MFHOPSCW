package fr.zyumie.customitems.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Random;

/**
 * Trouve une position de téléportation aléatoire et sécurisée (pas dans un bloc,
 * pas dans le vide, sol solide sous les pieds) dans un rayon donné.
 */
public final class SafeTeleport {

    private static final Random RANDOM = new Random();

    private SafeTeleport() {}

    /**
     * Cherche jusqu'à {@code maxAttempts} positions dans un cercle de {@code radius} blocs.
     * @return une location sûre, ou null si aucune trouvée après maxAttempts
     */
    public static Location findSafe(Location origin, double radius, int maxAttempts) {
        World world = origin.getWorld();
        if (world == null) return null;

        for (int i = 0; i < maxAttempts; i++) {
            // Position aléatoire dans un cercle
            double angle = RANDOM.nextDouble() * 2 * Math.PI;
            double dist  = (RANDOM.nextDouble() * 0.7 + 0.3) * radius; // entre 30% et 100% du rayon
            double dx = Math.cos(angle) * dist;
            double dz = Math.sin(angle) * dist;

            int x = origin.getBlockX() + (int) dx;
            int z = origin.getBlockZ() + (int) dz;

            // Trouve le sol depuis Y de l'origine vers le bas
            int startY = Math.min(origin.getBlockY() + 10, world.getMaxHeight() - 2);
            Location safe = findGround(world, x, startY, z);
            if (safe != null) return safe;
        }
        return null;
    }

    /** Descend depuis startY jusqu'à Y=-64 pour trouver un sol solide avec 2 blocs d'air au-dessus */
    private static Location findGround(World world, int x, int startY, int z) {
        for (int y = startY; y > world.getMinHeight() + 1; y--) {
            Block floor = world.getBlockAt(x, y, z);
            Block head  = world.getBlockAt(x, y + 1, z);
            Block feet  = world.getBlockAt(x, y + 2, z);

            if (isSolidFloor(floor) && isPassable(head) && isPassable(feet)) {
                return new Location(world, x + 0.5, y + 1, z + 0.5);
            }
        }
        return null;
    }

    private static boolean isSolidFloor(Block block) {
        return block.getType().isSolid()
            && !block.isLiquid()
            && block.getType() != Material.LAVA
            && block.getType() != Material.FIRE
            && block.getType() != Material.CAMPFIRE;
    }

    private static boolean isPassable(Block block) {
        return block.isPassable() || block.getType() == Material.AIR
            || block.getType() == Material.CAVE_AIR;
    }
}
