package dev.autobuilder;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.autobuilder.build.BuilderEngine;
import dev.autobuilder.config.BuilderConfig;
import dev.autobuilder.gui.BuilderScreen;
import dev.autobuilder.gui.PreviewRenderer;
import dev.autobuilder.schematic.LitematicLoader;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Path;

public final class BuilderMod implements ClientModInitializer {
    private static KeyBinding key(String n, int k) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.autobuilder." + n, InputUtil.Type.KEYSYM, k, "category.autobuilder"));
    }

    @Override public void onInitializeClient() {
        BuilderConfig.load();
        KeyBinding toggle = key("toggle", GLFW.GLFW_KEY_KP_ENTER), pause = key("pause", GLFW.GLFW_KEY_KP_0),
                undo = key("undo", GLFW.GLFW_KEY_KP_DECIMAL), skip = key("skip", GLFW.GLFW_KEY_KP_ADD), menu = key("menu", GLFW.GLFW_KEY_RIGHT_SHIFT);
        var eng = BuilderEngine.INSTANCE;

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (toggle.wasPressed()) { if (eng.running) eng.stop(); else eng.start(); }
            while (pause.wasPressed()) eng.togglePause();
            while (undo.wasPressed()) eng.undoLast(5);
            while (skip.wasPressed()) eng.skipLayer();
            while (menu.wasPressed()) mc.setScreen(new BuilderScreen());
            eng.tick(mc);
        });
        WorldRenderEvents.LAST.register(PreviewRenderer::render);

        ClientCommandRegistrationCallback.EVENT.register((d, reg) -> d.register(ClientCommandManager.literal("autobuilder")
            .then(ClientCommandManager.literal("load").then(ClientCommandManager.argument("file", StringArgumentType.greedyString()).executes(ctx -> {
                var mc = MinecraftClient.getInstance();
                Path p = FabricLoader.getInstance().getGameDir().resolve("schematics").resolve(StringArgumentType.getString(ctx, "file"));
                try { eng.load(LitematicLoader.load(p), mc.player.getBlockPos()); say(mc, "Loaded " + p.getFileName()); }
                catch (Exception e) { say(mc, "Load failed: " + e); }
                return 1; })))
            .then(ClientCommandManager.literal("reach").then(ClientCommandManager.argument("blocks", IntegerArgumentType.integer(1, 6)).executes(ctx -> {
                BuilderConfig.get().reach = IntegerArgumentType.getInteger(ctx, "blocks"); BuilderConfig.save(); return 1; })))
            .then(ClientCommandManager.literal("menu").executes(ctx -> { MinecraftClient.getInstance().send(() -> MinecraftClient.getInstance().setScreen(new BuilderScreen())); return 1; }))));
    }
    private static void say(MinecraftClient mc, String s) { mc.inGameHud.getChatHud().addMessage(Text.literal("[AutoBuilder] " + s)); }
}
