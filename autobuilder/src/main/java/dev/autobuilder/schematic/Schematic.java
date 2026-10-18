package dev.autobuilder.schematic;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import java.util.List;

public record Schematic(String name, List<Entry> blocks) {
    public record Entry(BlockPos pos, BlockState state) {}
}
