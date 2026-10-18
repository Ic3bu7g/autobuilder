package dev.autobuilder.build;

import dev.autobuilder.schematic.Placement;
import net.minecraft.block.Block;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import java.util.*;

public final class MaterialChecklist {
    public record Row(Block block, int needed, int have) {}
    /** Counts seen the last time a bound container was opened. */
    public static final Map<Block, Integer> cachedStorage = new HashMap<>();

    public static List<Row> compute(Placement pl, ClientWorld w, PlayerEntity p) {
        Map<Block, Integer> need = new HashMap<>();
        for (var e : pl.sorted)
            if (w.getBlockState(e.pos()).getBlock() != e.state().getBlock()) need.merge(e.state().getBlock(), 1, Integer::sum);
        List<Row> rows = new ArrayList<>();
        for (var e : need.entrySet()) {
            int have = 0;
            for (int i = 0; i < p.getInventory().size(); i++) {
                ItemStack s = p.getInventory().getStack(i);
                if (s.getItem() == e.getKey().asItem()) have += s.getCount();
            }
            rows.add(new Row(e.getKey(), e.getValue(), have + cachedStorage.getOrDefault(e.getKey(), 0)));
        }
        rows.sort(Comparator.comparingInt((Row r) -> r.have() - r.needed()));
        return rows;
    }
}
