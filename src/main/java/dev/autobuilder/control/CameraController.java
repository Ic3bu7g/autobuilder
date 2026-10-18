package dev.autobuilder.control;

import dev.autobuilder.config.BuilderConfig;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import java.util.concurrent.ThreadLocalRandom;

/** Critically damped spring on yaw/pitch with a speed cap and low-pass micro-tremor. */
public final class CameraController {
    private float ty, tp, vy, vp, ny, np;
    private boolean active;

    public void setTarget(float yaw, float pitch) { ty = yaw; tp = MathHelper.clamp(pitch, -90f, 90f); active = true; }
    public void release() { active = false; vy = vp = 0; }

    public static float[] anglesTo(Vec3d eye, Vec3d p) {
        double dx = p.x - eye.x, dy = p.y - eye.y, dz = p.z - eye.z;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        return new float[]{yaw, pitch};
    }

    public float error(ClientPlayerEntity p) {
        float dy = MathHelper.wrapDegrees(ty - p.getYaw()), dp = tp - p.getPitch();
        return (float) Math.sqrt(dy * dy + dp * dp);
    }

    public void tick(ClientPlayerEntity p) {
        if (!active) return;
        BuilderConfig c = BuilderConfig.get();
        float w = (float) c.cameraStiffness, cap = (float) c.cameraMaxDegPerTick;
        float dy = MathHelper.wrapDegrees(ty - p.getYaw()), dp = tp - p.getPitch();
        vy += w * w * dy - 2 * w * vy;
        vp += w * w * dp - 2 * w * vp;
        vy = MathHelper.clamp(vy, -cap, cap);
        vp = MathHelper.clamp(vp, -cap, cap);
        var r = ThreadLocalRandom.current();
        ny += (float) ((r.nextGaussian() * c.cameraJitterDeg - ny) * 0.1);
        np += (float) ((r.nextGaussian() * c.cameraJitterDeg - np) * 0.1);
        p.setYaw(p.getYaw() + vy + ny);
        p.setPitch(MathHelper.clamp(p.getPitch() + vp + np, -90f, 90f));
    }
}
