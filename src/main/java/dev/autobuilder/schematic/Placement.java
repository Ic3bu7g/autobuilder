package dev.autobuilder.schematic;

import net.minecraft.block.BlockState;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** A schematic positioned in the world, with rotation/mirror applied. */
public final class Placement {
    public final Schematic schematic;
    public BlockPos origin;
    public int rotation;         // 0-3 quarter turns clockwise
    public boolean mirror;       // mirror along X before rotating
    public List<Schematic.Entry> sorted = List.of();   // world coords, ordered by y then x,z
    public Map<BlockPos, BlockState> map = new HashMap<>();

    public Placement(Schematic s, BlockPos origin) { this.schematic = s; this.origin = origin; rebuild(); }

    public void rebuild() {
        BlockRotation rot = BlockRotation.values()[rotation & 3];
        List<Schematic.Entry> list = new ArrayList<>(schematic.blocks().size());
        for (var e : schematic.blocks()) {
            int x = e.pos().getX(), y = e.pos().getY(), z = e.pos().getZ();
            BlockState st = e.state();
            if (mirror) { x = -x; st = st.mirror(BlockMirror.FRONT_BACK); }
            for (int i = 0; i < (rotation & 3); i++) { int nx = -z; z = x; x = nx; }
            st = st.rotate(rot);
            list.add(new Schematic.Entry(origin.add(x, y, z), st));
        }
        list.sort(Comparator.comparingInt((Schematic.Entry e) -> e.pos().getY())
                .thenComparingInt(e -> e.pos().getX()).thenComparingInt(e -> e.pos().getZ()));
        sorted = list;
        Map<BlockPos, BlockState> m = new HashMap<>();
        for (var e : list) m.put(e.pos(), e.state());
        map = m;
    }
}
