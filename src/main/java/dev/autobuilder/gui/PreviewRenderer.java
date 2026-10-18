package dev.autobuilder.gui;

import dev.autobuilder.build.BuilderEngine;
import dev.autobuilder.config.BuilderConfig;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Colour-coded outlines: green = still to place, red = wrong block present, (correct blocks are hidden). */
public final class PreviewRenderer {
    public static void render(WorldRenderContext ctx) {
        var pl = BuilderEngine.INSTANCE.placement;
        var mc = MinecraftClient.getInstance();
        if (pl == null || mc.world == null || ctx.consumers() == null) return;
        BuilderConfig c = BuilderConfig.get();
        Vec3d cam = ctx.camera().getPos();
        VertexConsumer vc = ctx.consumers().getBuffer(RenderLayer.getLines());
        var m = ctx.matrixStack();
        double max = c.renderDistance * (double) c.renderDistance;
        int drawn = 0;
        for (var e : pl.sorted) {
            if (drawn >= c.maxPreviewBoxes) break;
            var pos = e.pos();
            if (pos.getSquaredDistance(cam) > max) continue;
            var cur = mc.world.getBlockState(pos);
            if (cur.getBlock() == e.state().getBlock()) continue;
            boolean wrong = !cur.isReplaceable();
            Box b = new Box(pos).offset(-cam.x, -cam.y, -cam.z).contract(0.02);
            WorldRenderer.drawBox(m, vc, b, wrong ? 1f : 0.2f, wrong ? 0.2f : 1f, 0.3f, 0.9f);
            drawn++;
        }
    }
}
