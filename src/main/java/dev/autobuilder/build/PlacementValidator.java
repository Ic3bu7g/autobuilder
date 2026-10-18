package dev.autobuilder.build;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

/** Only returns aims a vanilla server would also accept: in reach, face toward eye, unobstructed ray. */
public final class PlacementValidator {
    public record Aim(Vec3d point, BlockPos support, Direction side) {}

    public static boolean solidEnough(BlockState s) { return !s.isAir() && !s.isReplaceable() && s.getFluidState().isEmpty(); }

    public static boolean hasAnySupport(ClientWorld w, BlockPos target) {
        for (Direction d : Direction.values()) if (solidEnough(w.getBlockState(target.offset(d)))) return true;
        return false;
    }

    public static Aim findPlace(ClientWorld w, Vec3d eye, BlockPos target, BlockState want, double reach) {
        if (!w.getBlockState(target).isReplaceable()) return null;
        if (!w.canPlace(want, target, ShapeContext.absent())) return null;
        Aim best = null; double bd = 1e18;
        for (Direction d : Direction.values()) {
            BlockPos n = target.offset(d);
            if (!solidEnough(w.getBlockState(n))) continue;
            Direction side = d.getOpposite();
            Vec3d hit = Vec3d.ofCenter(n).add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
            if (eye.squaredDistanceTo(hit) > reach * reach) continue;
            Vec3d toEye = eye.subtract(hit);
            if (toEye.x * side.getOffsetX() + toEye.y * side.getOffsetY() + toEye.z * side.getOffsetZ() <= 0) continue;
            Vec3d end = hit.subtract(side.getOffsetX() * 0.05, side.getOffsetY() * 0.05, side.getOffsetZ() * 0.05);
            BlockHitResult r = w.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE, ShapeContext.absent()));
            if (r.getType() != HitResult.Type.BLOCK || !r.getBlockPos().equals(n) || r.getSide() != side) continue;
            double dist = eye.squaredDistanceTo(hit);
            if (dist < bd) { bd = dist; best = new Aim(r.getPos(), n, side); }
        }
        return best;
    }

    public static Aim findBreak(ClientWorld w, Vec3d eye, BlockPos pos, double reach) {
        Vec3d c = Vec3d.ofCenter(pos);
        if (eye.squaredDistanceTo(c) > reach * reach) return null;
        BlockHitResult r = w.raycast(new RaycastContext(eye, c, RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE, ShapeContext.absent()));
        if (r.getType() != HitResult.Type.BLOCK || !r.getBlockPos().equals(pos)) return null;
        return new Aim(r.getPos(), pos, r.getSide());
    }

    public static Vec3d eyeAt(BlockPos feet) { return new Vec3d(feet.getX() + 0.5, feet.getY() + 1.62, feet.getZ() + 0.5); }
}
