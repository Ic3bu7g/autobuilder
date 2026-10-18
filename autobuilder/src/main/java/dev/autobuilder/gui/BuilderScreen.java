package dev.autobuilder.gui;

import dev.autobuilder.build.BuilderEngine;
import dev.autobuilder.build.MaterialChecklist;
import dev.autobuilder.config.BuilderConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

public final class BuilderScreen extends Screen {
    public BuilderScreen() { super(Text.literal("Auto Builder")); }

    @Override protected void init() {
        var e = BuilderEngine.INSTANCE;
        int x = 10, y = 20, w = 120, h = 20, g = 24;
        btn("Rotate 90°", x, y, w, h, () -> { if (e.placement != null) { e.placement.rotation = (e.placement.rotation + 1) & 3; e.placement.rebuild(); } });
        btn("Mirror", x, y += g, w, h, () -> { if (e.placement != null) { e.placement.mirror = !e.placement.mirror; e.placement.rebuild(); } });
        btn("Move here", x, y += g, w, h, () -> { if (e.placement != null && client.player != null) { e.placement.origin = client.player.getBlockPos(); e.placement.rebuild(); } });
        String[] ax = {"X", "Y", "Z"};
        for (int i = 0; i < 3; i++) { final int a = i;
            btn(ax[i] + " -", x, y += g, 58, h, () -> nudge(a, -1)); btn(ax[i] + " +", x + 62, y, 58, h, () -> nudge(a, 1)); }
        btn("Placeholder = main hand", x, y += g, w + 40, h, () -> {
            if (client.player != null) { BuilderConfig.get().placeholderBlock = Registries.ITEM.getId(client.player.getMainHandStack().getItem()).toString(); BuilderConfig.save(); } });
        btn("Bind container (crosshair)", x, y += g, w + 40, h, () -> {
            if (client.crosshairTarget instanceof BlockHitResult r && r.getType() == HitResult.Type.BLOCK) {
                var l = BuilderConfig.get().boundContainers; long k = r.getBlockPos().asLong(); if (!l.contains(k)) l.add(k); BuilderConfig.save(); } });
        btn("Clear bindings", x, y += g, w + 40, h, () -> { BuilderConfig.get().boundContainers.clear(); BuilderConfig.save(); });
        btn(e.running ? "Stop" : "Start", x, y += g, w, h, () -> { if (e.running) e.stop(); else e.start(); close(); });
    }
    private void nudge(int axis, int d) {
        var p = BuilderEngine.INSTANCE.placement; if (p == null) return;
        p.origin = p.origin.add(axis == 0 ? d : 0, axis == 1 ? d : 0, axis == 2 ? d : 0); p.rebuild();
    }
    private void btn(String t, int x, int y, int w, int h, Runnable r) { addDrawableChild(ButtonWidget.builder(Text.literal(t), b -> r.run()).dimensions(x, y, w, h).build()); }

    @Override public void render(DrawContext ctx, int mx, int my, float dt) {
        super.render(ctx, mx, my, dt);
        var e = BuilderEngine.INSTANCE;
        ctx.drawTextWithShadow(textRenderer, "Status: " + e.status, 10, 6, 0xFFFFFF);
        if (e.placement == null || client.world == null) return;
        int x = width - 220, y = 20;
        ctx.drawTextWithShadow(textRenderer, "Materials (need / have)", x, 6, 0xFFFF55);
        for (var r : MaterialChecklist.compute(e.placement, client.world, client.player)) {
            if (y > height - 12) break;
            ctx.drawTextWithShadow(textRenderer, r.block().getName().getString() + "  " + r.needed() + " / " + r.have(), x, y, r.have() >= r.needed() ? 0x55FF55 : 0xFF5555);
            y += 10;
        }
    }
}
