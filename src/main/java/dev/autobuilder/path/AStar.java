package dev.autobuilder.path;

import dev.autobuilder.config.BuilderConfig;
import net.minecraft.block.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import java.util.*;
import java.util.function.Predicate;

/** 3D A* over feet positions. Cardinals + checked diagonals, 1-block step-up, capped safe drops. */
public final class AStar {
    private record Node(long pos, double g, double f, Node parent) {}

    public static boolean isPassable(ClientWorld w, BlockPos p) {
        BlockState s = w.getBlockState(p);
        if (!s.getCollisionShape(w, p).isEmpty()) return false;
        if (!w.getFluidState(p).isEmpty()) return false;
        Block b = s.getBlock();
        return !(b instanceof FireBlock || b instanceof CactusBlock || b instanceof SweetBerryBushBlock
                || b instanceof WitherRoseBlock || b instanceof CampfireBlock || b instanceof CobwebBlock
                || b instanceof PowderSnowBlock);
    }

    /** Body + head space free (no suffocation), solid non-harmful floor. */
    public static boolean standable(ClientWorld w, BlockPos feet) {
        if (!isPassable(w, feet) || !isPassable(w, feet.up())) return false;
        BlockPos floor = feet.down();
        BlockState fs = w.getBlockState(floor);
        if (!w.getFluidState(floor).isEmpty()) return false;
        Block b = fs.getBlock();
        if (b instanceof MagmaBlock || b instanceof CactusBlock || b instanceof CampfireBlock) return false;
        return fs.isSideSolidFullSquare(w, floor, Direction.UP);
    }

    public static List<BlockPos> find(ClientWorld w, BlockPos start, BlockPos goal, Predicate<BlockPos> forbidden) {
        BuilderConfig c = BuilderConfig.get();
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::f));
        Map<Long, Double> best = new HashMap<>();
        open.add(new Node(start.asLong(), 0, h(start, goal), null));
        best.put(start.asLong(), 0.0);
        int expanded = 0;
        while (!open.isEmpty() && expanded++ < c.maxPathNodes) {
            Node n = open.poll();
            BlockPos cur = BlockPos.fromLong(n.pos);
            if (cur.equals(goal)) return rebuild(n);
            if (n.g > best.getOrDefault(n.pos, 1e18)) continue;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                boolean diag = dx != 0 && dz != 0;
                if (diag && !(isPassable(w, cur.add(dx, 0, 0)) && isPassable(w, cur.add(0, 0, dz))
                        && isPassable(w, cur.add(dx, 1, 0)) && isPassable(w, cur.add(0, 1, dz)))) continue;
                BlockPos flat = cur.add(dx, 0, dz);
                BlockPos next = null; double extra = 0;
                if (standable(w, flat)) next = flat;
                else if (isPassable(w, cur.up(2)) && standable(w, flat.up())) { next = flat.up(); extra = 0.6; }
                else if (isPassable(w, flat) && isPassable(w, flat.up())) {
                    for (int d = 1; d <= c.maxDrop; d++) {
                        BlockPos cand = flat.down(d);
                        if (standable(w, cand)) { next = cand; extra = 0.4 * d; break; }
                        if (!isPassable(w, cand)) break;
                    }
                }
                if (next == null || forbidden.test(next) || forbidden.test(next.up())) continue;
                double g = n.g + (diag ? 1.414 : 1.0) + extra;
                long key = next.asLong();
                if (g < best.getOrDefault(key, 1e18)) {
                    best.put(key, g);
                    open.add(new Node(key, g, g + h(next, goal), n));
                }
            }
        }
        return null;
    }

    private static double h(BlockPos a, BlockPos b) {
        double dx = Math.abs(a.getX() - b.getX()), dz = Math.abs(a.getZ() - b.getZ()), dy = Math.abs(a.getY() - b.getY());
        return Math.max(dx, dz) + 0.414 * Math.min(dx, dz) + dy * 0.6;
    }
    private static List<BlockPos> rebuild(Node n) {
        LinkedList<BlockPos> out = new LinkedList<>();
        for (Node c = n; c != null; c = c.parent) out.addFirst(BlockPos.fromLong(c.pos));
        return out;
    }
}
