package dev.autobuilder.config;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Every tunable lives here and is persisted to config/autobuilder.json. */
public final class BuilderConfig {
    public double reach = 4.5;                       // vanilla survival block reach
    public int lookDelayMs = 180, lookDelayVarianceMs = 60;
    public int clickDelayMs = 120, clickDelayVarianceMs = 40;
    public int betweenBlocksMs = 90, betweenBlocksVarianceMs = 40;
    public double cameraStiffness = 0.28;            // spring omega per tick (critically damped)
    public double cameraMaxDegPerTick = 14;
    public double cameraJitterDeg = 0.05;
    public double aimToleranceDeg = 1.2;
    public int maxPathNodes = 6000, maxPathLength = 200, maxDrop = 3, undoDepth = 32;
    public int renderDistance = 48, maxPreviewBoxes = 4000;
    public String placeholderBlock = "minecraft:dirt";
    public List<Long> boundContainers = new ArrayList<>(); // BlockPos.asLong()

    private static BuilderConfig inst = new BuilderConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("autobuilder.json"); }
    public static BuilderConfig get() { return inst; }

    public static void load() {
        try {
            if (Files.exists(file())) inst = GSON.fromJson(Files.readString(file()), BuilderConfig.class);
            if (inst == null) inst = new BuilderConfig();
        } catch (Exception e) { inst = new BuilderConfig(); }
        save();
    }
    public static void save() {
        try { Files.writeString(file(), GSON.toJson(inst)); } catch (IOException ignored) {}
    }
}
