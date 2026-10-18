package dev.autobuilder.build;

import dev.autobuilder.config.BuilderConfig;
import dev.autobuilder.control.CameraController;
import dev.autobuilder.control.HumanDelay;
import dev.autobuilder.path.AStar;
import dev.autobuilder.schematic.Placement;
import dev.autobuilder.schematic.Schematic;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.*;

/** Tick-driven state machine: select task -> (path) -> aim -> act -> verify. */
public final class BuilderEngine {
    public static final BuilderEngine INSTANCE = new BuilderEngine();

    private enum Kind { PLACE, PLACEHOLDER, BREAK, RESTOCK }
    private enum State { SELECT, WALK, AIM, VERIFY, MINE, RESTOCK_WAIT }
    private record Task(Kind kind, BlockPos pos, BlockState state, BlockPos dependent) {}

    public Placement placement;
    public boolean running, paused;
    public String status = "Idle";
    public int layerFloorOffset = 0;

    private State state = State.SELECT;
    private Task task;
    private List<BlockPos> path = List.of();
    private int pathIdx, ticksInState;
    private PlacementValidator.Aim aim;
    private long lookAt, actAt, nextActionAt;
    private boolean armed, keysHeld;
    private final CameraController camera = new CameraController();
    private final Deque<BlockPos> undo = new ArrayDeque<>();
    private final Deque<BlockPos> breakQueue = new ArrayDeque<>();
    private final Map<BlockPos, BlockPos> placeholders = new HashMap<>(); // placeholder -> dependent target
    private final Set<BlockPos> blacklist = new HashSet<>();
    private final Set<Long> badContainers = new HashSet<>();
    private final Set<Block> missing = new LinkedHashSet<>();

    // ---- public controls ----
    public void load(Schematic s, BlockPos origin) { placement = new Placement(s, origin); reset(); }
    public void start() { if (placement != null) { running = true; paused = false; reset(); } }
    public void stop() { running = false; releaseAll(MinecraftClient.getInstance()); status = "Stopped"; }
    public void togglePause() { paused = !paused; if (paused) releaseAll(MinecraftClient.getInstance()); }
    public void skipLayer() { layerFloorOffset++; blacklist.clear(); resetState(); }
    public void undoLast(int n) {
        for (int i = 0; i < n && !undo.isEmpty(); i++) breakQueue.add(undo.pop());
        resetState();
    }
    private void reset() { blacklist.clear(); badContainers.clear(); undo.clear(); breakQueue.clear(); placeholders.clear(); layerFloorOffset = 0; resetState(); }
    private void resetState() { state = State.SELECT; task = null; path = List.of(); aim = null; ticksInState = 0; }

    // ---- main loop ----
    public void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player; ClientWorld w = mc.world;
        if (p == null || w == null || placement == null || !running || paused) { releaseAll(mc); return; }
        if (state == State.RESTOCK_WAIT) { tickRestock(mc, p); return; }
        if (mc.currentScreen != null) { releaseAll(mc); return; }
        camera.tick(p);
        ticksInState++;
        BuilderConfig c = BuilderConfig.get();
        switch (state) {
            case SELECT -> select(mc, p, w, c);
            case WALK -> walk(mc, p, w, c);
            case AIM -> aim(mc, p, w, c);
            case VERIFY -> verify(mc, p, w);
            case MINE -> mine(mc, p, w);
            default -> {}
        }
    }

    private void select(MinecraftClient mc, ClientPlayerEntity p, ClientWorld w, BuilderConfig c) {
        releaseKeys(mc);
        if (System.currentTimeMillis() < nextActionAt) return;
        // placeholders whose dependent block is now in place get queued for removal
        for (var it = placeholders.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            BlockState want = placement.map.get(e.getValue());
            if (want != null && w.getBlockState(e.getValue()).getBlock() == want.getBlock()) { breakQueue.add(e.getKey()); it.remove(); }
        }
        task = nextTask(mc, p, w);
        if (task == null) {
            if (!placeholders.isEmpty()) { breakQueue.addAll(placeholders.keySet()); placeholders.clear(); }
            if (!breakQueue.isEmpty()) task = new Task(Kind.BREAK, breakQueue.poll(), null, null);
        }
        if (task == null) { status = missing.isEmpty() ? "Finished (or nothing reachable)" : "Out of materials: " + missing.iterator().next().getName().getString(); camera.release(); return; }
        status = task.kind + " " + task.pos.toShortString();
        if (task.kind == Kind.RESTOCK) { planStandFor(mc, p, w, c, BlockPos.fromLong(task.pos.asLong())); return; }
        aim = computeAim(p, w, c, p.getEyePos());
        if (aim != null) enterAim(); else planStandFor(mc, p, w, c, task.pos);
    }

    private PlacementValidator.Aim computeAim(ClientPlayerEntity p, ClientWorld w, BuilderConfig c, Vec3d eye) {
        return switch (task.kind) {
            case PLACE, PLACEHOLDER -> PlacementValidator.findPlace(w, eye, task.pos, task.state, c.reach);
            case BREAK -> PlacementValidator.findBreak(w, eye, task.pos, c.reach);
            case RESTOCK -> PlacementValidator.findBreak(w, eye, task.pos, Math.min(c.reach, 4.0));
        };
    }

    private void planStandFor(MinecraftClient mc, ClientPlayerEntity p, ClientWorld w, BuilderConfig c, BlockPos target) {
        int r = (int) Math.ceil(c.reach);
        BlockPos best = null; double bd = 1e18;
        for (BlockPos s : BlockPos.iterate(target.add(-r, -r, -r), target.add(r, r, r))) {
            if (!AStar.standable(w, s) || placement.map.containsKey(s) || placement.map.containsKey(s.up())) continue;
            if (!(task.kind == Kind.PLACE || task.kind == Kind.PLACEHOLDER
                    ? PlacementValidator.findPlace(w, PlacementValidator.eyeAt(s), task.pos, task.state, c.reach) != null
                    : PlacementValidator.findBreak(w, PlacementValidator.eyeAt(s), task.pos, Math.min(c.reach, 4.0)) != null)) continue;
            double d = s.getSquaredDistance(p.getPos());
            if (d < bd) { bd = d; best = s.toImmutable(); }
        }
        List<BlockPos> found = best == null ? null
                : AStar.find(w, p.getBlockPos(), best, b -> placement.map.containsKey(b));
        if (found == null || found.size() > c.maxPathLength) { fail(); return; }
        path = found; pathIdx = 0; state = State.WALK; ticksInState = 0;
    }

    private void walk(MinecraftClient mc, ClientPlayerEntity p, ClientWorld w, BuilderConfig c) {
        if (ticksInState > 20 * 30 || pathIdx >= path.size()) {
            releaseKeys(mc);
            if (pathIdx >= path.size()) { aim = computeAim(p, w, c, p.getEyePos()); if (aim != null) { enterAim(); return; } }
            fail(); return;
        }
        BlockPos wp = path.get(pathIdx);
        Vec3d center = new Vec3d(wp.getX() + 0.5, p.getY(), wp.getZ() + 0.5);
        double dx = center.x - p.getX(), dz = center.z - p.getZ();
        if (Math.hypot(dx, dz) < 0.3 && Math.abs(p.getY() - wp.getY()) < 1.1) { pathIdx++; return; }
        // never walk into a spot that became unsafe (e.g. newly placed block)
        if (!AStar.isPassable(w, wp) || !AStar.isPassable(w, wp.up())) { releaseKeys(mc); fail(); return; }
        float[] a = CameraController.anglesTo(p.getEyePos(), new Vec3d(center.x, p.getEyeY(), center.z));
        camera.setTarget(a[0], 12f);
        float yawErr = Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(a[0] - p.getYaw()));
        mc.options.forwardKey.setPressed(yawErr < 30f);
        mc.options.jumpKey.setPressed(wp.getY() > p.getY() + 0.3 && p.isOnGround());
        keysHeld = true;
    }

    private void enterAim() {
        state = State.AIM; ticksInState = 0; armed = false;
        BuilderConfig c = BuilderConfig.get();
        lookAt = System.currentTimeMillis() + HumanDelay.sample(c.lookDelayMs, c.lookDelayVarianceMs);
    }

    private void aim(MinecraftClient mc, ClientPlayerEntity p, ClientWorld w, BuilderConfig c) {
        releaseKeys(mc);
        aim = computeAim(p, w, c, p.getEyePos());
        if (aim == null) { if (ticksInState > 40) fail(); else if (ticksInState > 5) resetState(); return; }
        long now = System.currentTimeMillis();
        if (now < lookAt) return;
        float[] a = CameraController.anglesTo(p.getEyePos(), aim.point());
        camera.setTarget(a[0], a[1]);
        boolean onTarget = camera.error(p) < c.aimToleranceDeg;
        if (!armed && onTarget) { armed = true; actAt = now + HumanDelay.sample(c.clickDelayMs, c.clickDelayVarianceMs); }
        if (armed && !onTarget) armed = false;
        if (armed && now >= actAt) act(mc, p, w, c);
        if (ticksInState > 20 * 8) fail();
    }

    private void act(MinecraftClient mc, ClientPlayerEntity p, ClientWorld w, BuilderConfig c) {
        switch (task.kind) {
            case PLACE, PLACEHOLDER -> {
                if (!selectItem(mc, p, task.state.getBlock().asItem())) { missing.add(task.state.getBlock()); resetState(); return; }
                BlockHitResult hit = new BlockHitResult(aim.point(), aim.side(), aim.support(), false);
                mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
                p.swingHand(Hand.MAIN_HAND);
                state = State.VERIFY; ticksInState = 0;
            }
            case BREAK -> { mc.interactionManager.attackBlock(task.pos, aim.side()); p.swingHand(Hand.MAIN_HAND); state = State.MINE; ticksInState = 0; }
            case RESTOCK -> {
                BlockHitResult hit = new BlockHitResult(aim.point(), aim.side(), aim.support(), false);
                mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
                state = State.RESTOCK_WAIT; ticksInState = 0;
            }
        }
    }

    private void verify(MinecraftClient mc, ClientPlayerEntity p, ClientWorld w) {
        if (w.getBlockState(task.pos).getBlock() == task.state.getBlock()) {
            if (task.kind == Kind.PLACE) { undo.push(task.pos); while (undo.size() > BuilderConfig.get().undoDepth) undo.removeLast(); }
            else placeholders.put(task.pos, task.dependent);
            finishTask();
        } else if (ticksInState > 12) fail();
    }

    private void mine(MinecraftClient mc, ClientPlayerEntity p, ClientWorld w) {
        if (w.getBlockState(task.pos).isAir()) { finishTask(); return; }
        mc.interactionManager.updateBlockBreakingProgress(task.pos, aim != null ? aim.side() : net.minecraft.util.math.Direction.UP);
        p.swingHand(Hand.MAIN_HAND);
        if (ticksInState > 20 * 10) fail();
    }

    private void finishTask() {
        BuilderConfig c = BuilderConfig.get();
        nextActionAt = System.currentTimeMillis() + HumanDelay.sample(c.betweenBlocksMs, c.betweenBlocksVarianceMs);
        missing.clear(); resetState();
    }
    private void fail() { if (task != null) blacklist.add(task.pos); resetState(); }

    // ---- task selection ----
    private Task nextTask(MinecraftClient mc, ClientPlayerEntity p, ClientWorld w) {
        BuilderConfig c = BuilderConfig.get();
        int floorY = placement.sorted.isEmpty() ? 0 : placement.sorted.get(0).pos().getY() + layerFloorOffset;
        Task best = null; double bd = 1e18; int layer = Integer.MIN_VALUE;
        missing.clear();
        boolean creative = p.getAbilities().creativeMode;
        for (var e : placement.sorted) {
            BlockPos pos = e.pos();
            if (pos.getY() < floorY || blacklist.contains(pos)) continue;
            if (best != null && pos.getY() != layer) break;
            BlockState cur = w.getBlockState(pos);
            if (cur.getBlock() == e.state().getBlock() || !cur.isReplaceable()) continue;
            Item item = e.state().getBlock().asItem();
            if (!creative && count(p, item) == 0) { missing.add(e.state().getBlock()); continue; }
            Task t = null;
            if (PlacementValidator.hasAnySupport(w, pos)) t = new Task(Kind.PLACE, pos.toImmutable(), e.state(), null);
            else {
                Block ph = Registries.BLOCK.get(Identifier.of(c.placeholderBlock));
                if (creative || count(p, ph.asItem()) > 0)
                    for (var d : net.minecraft.util.math.Direction.values()) {
                        BlockPos n = pos.offset(d);
                        if (w.getBlockState(n).isReplaceable() && !placement.map.containsKey(n) && PlacementValidator.hasAnySupport(w, n)) {
                            t = new Task(Kind.PLACEHOLDER, n.toImmutable(), ph.getDefaultState(), pos.toImmutable()); break;
                        }
                    }
            }
            if (t == null) continue;
            double d = pos.getSquaredDistance(p.getPos());
            if (d < bd) { bd = d; best = t; layer = pos.getY(); }
        }
        if (best == null && !missing.isEmpty() && !c.boundContainers.isEmpty()) {
            for (long l : c.boundContainers) if (!badContainers.contains(l)) return new Task(Kind.RESTOCK, BlockPos.fromLong(l), null, null);
        }
        return best;
    }

    // ---- restocking from bound containers ----
    private void tickRestock(MinecraftClient mc, ClientPlayerEntity p) {
        releaseKeys(mc);
        if (!(mc.currentScreen instanceof HandledScreen<?> hs) || hs.getScreenHandler() == p.playerScreenHandler) {
            if (ticksInState++ > 40) { badContainers.add(task.pos.asLong()); resetState(); }
            return;
        }
        if (ticksInState++ < 8 + (int) (HumanDelay.sample(150, 50) / 50)) return;
        var h = hs.getScreenHandler();
        int containerSlots = h.slots.size() - 36;
        Map<Block, Integer> seen = new HashMap<>();
        for (int i = 0; i < containerSlots; i++) {
            ItemStack s = h.getSlot(i).getStack();
            if (s.getItem() instanceof net.minecraft.item.BlockItem bi) seen.merge(bi.getBlock(), s.getCount(), Integer::sum);
        }
        MaterialChecklist.cachedStorage.putAll(seen);
        for (int i = 0; i < containerSlots; i++) {
            ItemStack s = h.getSlot(i).getStack();
            if (s.getItem() instanceof net.minecraft.item.BlockItem bi && missing.contains(bi.getBlock()))
                mc.interactionManager.clickSlot(h.syncId, i, 0, SlotActionType.QUICK_MOVE, p);
        }
        p.closeHandledScreen();
        badContainers.add(task.pos.asLong());   // don't loop on an empty container; cleared on next start()
        finishTask();
    }

    // ---- inventory helpers ----
    private static int count(ClientPlayerEntity p, Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().size(); i++) { ItemStack s = p.getInventory().getStack(i); if (s.getItem() == item) n += s.getCount(); }
        return n;
    }
    private boolean selectItem(MinecraftClient mc, ClientPlayerEntity p, Item item) {
        var inv = p.getInventory();
        for (int i = 0; i < 9; i++) if (inv.getStack(i).getItem() == item) { inv.selectedSlot = i; return true; }
        for (int i = 9; i < 36; i++) if (inv.getStack(i).getItem() == item) {
            mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, i, inv.selectedSlot, SlotActionType.SWAP, p);
            return true;
        }
        if (p.getAbilities().creativeMode) {
            mc.interactionManager.clickCreativeStack(new ItemStack(item), 36 + inv.selectedSlot);
            return true;
        }
        return false;
    }

    private void releaseKeys(MinecraftClient mc) {
        if (!keysHeld) return;
        mc.options.forwardKey.setPressed(false); mc.options.jumpKey.setPressed(false); keysHeld = false;
    }
    private void releaseAll(MinecraftClient mc) { releaseKeys(mc); camera.release(); }
}
