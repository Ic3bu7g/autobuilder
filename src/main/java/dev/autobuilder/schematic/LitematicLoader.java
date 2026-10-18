package dev.autobuilder.schematic;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Reads .litematic (all regions merged). Air is dropped. */
public final class LitematicLoader {
    public static Schematic load(Path file) throws IOException {
        NbtCompound root = NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes());
        NbtCompound regions = root.getCompound("Regions");
        List<Schematic.Entry> out = new ArrayList<>();
        for (String key : regions.getKeys()) {
            NbtCompound r = regions.getCompound(key);
            NbtCompound pos = r.getCompound("Position"), size = r.getCompound("Size");
            int px = pos.getInt("x"), py = pos.getInt("y"), pz = pos.getInt("z");
            int sx = size.getInt("x"), sy = size.getInt("y"), sz = size.getInt("z");
            int ax = Math.abs(sx), ay = Math.abs(sy), az = Math.abs(sz);
            int ox = sx < 0 ? px + sx + 1 : px, oy = sy < 0 ? py + sy + 1 : py, oz = sz < 0 ? pz + sz + 1 : pz;

            NbtList paletteNbt = r.getList("BlockStatePalette", NbtElement.COMPOUND_TYPE);
            BlockState[] palette = new BlockState[paletteNbt.size()];
            for (int i = 0; i < palette.length; i++)
                palette[i] = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), paletteNbt.getCompound(i));

            long[] data = r.getLongArray("BlockStates");
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.length - 1));
            long mask = (1L << bits) - 1;
            long total = (long) ax * ay * az;
            for (int i = 0; i < total; i++) {
                long bitIndex = (long) i * bits;
                int start = (int) (bitIndex >>> 6), end = (int) ((bitIndex + bits - 1) >>> 6), shift = (int) (bitIndex & 63);
                int v = (int) (start == end ? (data[start] >>> shift) & mask
                        : ((data[start] >>> shift) | (data[end] << (64 - shift))) & mask);
                BlockState s = palette[v];
                if (s.isAir()) continue;
                int x = i % ax, z = (i / ax) % az, y = i / (ax * az);
                out.add(new Schematic.Entry(new BlockPos(ox + x, oy + y, oz + z), s));
            }
        }
        int mx = Integer.MAX_VALUE, my = Integer.MAX_VALUE, mz = Integer.MAX_VALUE;
        for (var e : out) { mx = Math.min(mx, e.pos().getX()); my = Math.min(my, e.pos().getY()); mz = Math.min(mz, e.pos().getZ()); }
        List<Schematic.Entry> norm = new ArrayList<>(out.size());
        for (var e : out) norm.add(new Schematic.Entry(e.pos().add(-mx, -my, -mz), e.state()));
        return new Schematic(file.getFileName().toString(), norm);
    }
}
