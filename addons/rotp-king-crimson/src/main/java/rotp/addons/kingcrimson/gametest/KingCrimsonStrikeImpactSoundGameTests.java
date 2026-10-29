package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.core.core.JojoMod;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.subsystems.target.ActionTarget;

/**
 * 1.16 StandEntity.attackTarget played a strike's impact sound after doHit, and only for a hit that landed: a
 * refused hurt left the strike silent. On a block it came after the block broke, judged by the hardness of the
 * block struck (StandBlockPunch.playImpactSound read the state from before the hit).
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonStrikeImpactSoundGameTests {
    private static final String HURT = "hurt", SOUND = "impact-sound";
    private KingCrimsonStrikeImpactSoundGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_strike_impact_sound")
    public static void chopImpactSoundFollowsOnlyALandedHit(GameTestHelper helper) {
        new Run(helper).start();
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_strike_block_sound")
    public static void chopImpactSoundOnABlockFollowsItsBreak(GameTestHelper helper) {
        new BlockRun(helper).start();
    }

    private static final class BlockRun implements GameTestListener {
        final GameTestHelper helper;
        final List<BlockPos> wall = new ArrayList<>();
        final List<String> heard = new ArrayList<>();
        KingCrimsonGroundAdmissionFixture scene;
        EntityActionInstance action;
        BlockPos aimed;
        int ticks;
        boolean closed;

        BlockRun(GameTestHelper helper) { this.helper = helper; }
        void start() { helper.testInfo.addListener(this); helper.onEachTick(this::tick); }

        void tick() {
            if (closed) return;
            try {
                if (scene == null) { prepare(); return; }
                helper.assertTrue(++ticks < 60, "KC-IMPACT-SOUND block the Chop never struck");
                if (action.punchedTarget == null) return;
                ActionTarget struck = action.punchedTarget;
                scene.premise(struck.getType() == ActionTarget.TargetType.BLOCK && struck.getBlockPos().equals(aimed),
                        "the Chop struck the glass it was pressed at: " + struck);
                boolean broken = scene.level.getBlockState(aimed).isAir();
                log("block heard=" + heard + " broken=" + broken);
                helper.assertTrue(broken, "KC-IMPACT-SOUND fixture: the Chop did not break the glass");
                helper.assertTrue(heard.equals(List.of("air")),
                        "KC-IMPACT-SOUND 1.16 played the Chop's impact sound once, after the glass broke, though the"
                                + " block was air by then; heard with the block as " + heard);
                close();
                helper.succeed();
            } catch (RuntimeException | Error error) { close(); throw error; }
        }

        void prepare() {
            scene = new KingCrimsonGroundAdmissionFixture(helper, "KC-IMPACT-SOUND/block", 1);
            var caster = scene.actor(scene.origin, scene.origin, 0, 1, 28);
            BlockPos base = BlockPos.containing(scene.origin).south(2);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 2; dy++) {
                    BlockPos pos = base.offset(dx, dy, 0);
                    scene.level.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState());
                    wall.add(pos);
                }
            }
            aimed = BlockPos.containing(caster.stand().getEyePosition()).south(2);
            scene.premise(wall.contains(aimed), "the Stand's eyes face the glass wall: " + aimed);
            ActionTarget glass = new ActionTarget(aimed, Direction.NORTH);
            LivingComponentAction.getComponent(caster.user()).entityAim.setTarget(glass);
            LivingComponentAction.getComponent(caster.stand()).entityAim.setTarget(glass);
            scene.listen(PlayLevelSoundEvent.AtPosition.class, event -> {
                if (event.getLevel() == scene.level && event.getSound() != null
                        && event.getSound().value() == KingCrimsonAddon.KINGCRIMSON_CHOP.get()) {
                    BlockState state = scene.level.getBlockState(BlockPos.containing(event.getPosition()));
                    heard.add(state.isAir() ? "air" : state.getBlock().toString());
                }
            });
            caster.stand().setFinisherMeter(0.75F);
            action = scene.press(caster, null, "kingcrimson_chop", "kingcrimson_chop",
                    InputMethod.CLICK, KingCrimsonGroundAdmissionFixture.ATTACK);
            scene.premise(action != null && action.ability == caster.power().getAbility("kingcrimson_chop"),
                    "the Chop action is installed");
        }

        void close() {
            if (closed) return;
            closed = true;
            try { if (scene != null) scene.close(); }
            finally { wall.forEach(pos -> scene.level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState())); }
        }
        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
    }

    private static final class Run implements GameTestListener {
        final GameTestHelper helper;
        final List<String> failures = new ArrayList<>();
        KingCrimsonGroundAdmissionFixture scene;
        List<String> snapshot;
        int index, ticks, checks, attempts;
        boolean refused, hitCow, closed;
        float health;

        Run(GameTestHelper helper) { this.helper = helper; }
        void start() { helper.testInfo.addListener(this); helper.onEachTick(this::tick); }

        void tick() {
            if (closed) return;
            try {
                if (snapshot != null) { verdict(); scene.close(); scene = null; snapshot = null; }
                if (scene == null) {
                    if (index == 2) {
                        close();
                        log("SUMMARY scenes=2 checks=" + checks + " failures=" + failures.size() + " details=" + failures);
                        helper.assertTrue(failures.isEmpty(), "KC-IMPACT-SOUND failures=" + failures);
                        helper.succeed();
                        return;
                    }
                    prepare(index++ == 0);
                    ticks = 0;
                }
                helper.assertTrue(++ticks < 60, "KC-IMPACT-SOUND " + scene.id + " the Chop never hit");
            } catch (RuntimeException | Error error) { close(); throw error; }
        }

        void prepare(boolean refuse) {
            refused = refuse; attempts = 0; hitCow = false;
            scene = new KingCrimsonGroundAdmissionFixture(helper, "KC-IMPACT-SOUND/" + (refuse ? "refused" : "landed"), 1);
            var caster = scene.actor(scene.origin, scene.origin, 0, 1, 28);
            var cow = scene.cow(scene.origin.add(0, 0, 1.5));
            List<String> order = new ArrayList<>();
            // A cancelled LivingIncomingDamageEvent is a hit that did not land.
            scene.listen(LivingIncomingDamageEvent.class, event -> {
                if (event.getEntity() == cow && snapshot == null) {
                    attempts++;
                    if (refuse) event.setCanceled(true);
                }
            });
            scene.listen(LivingDamageEvent.Post.class, event -> { if (event.getEntity() == cow) order.add(HURT); });
            scene.listen(PlayLevelSoundEvent.AtPosition.class, event -> {
                if (event.getLevel() == scene.level && event.getSound() != null
                        && event.getSound().value() == KingCrimsonAddon.KINGCRIMSON_CHOP.get()) order.add(SOUND);
            });
            caster.stand().setFinisherMeter(0.75F);
            EntityActionInstance action = scene.press(caster, cow, "kingcrimson_chop", "kingcrimson_chop",
                    InputMethod.CLICK, KingCrimsonGroundAdmissionFixture.ATTACK);
            scene.premise(action != null && action.ability == caster.power().getAbility("kingcrimson_chop"),
                    "the Chop action is installed");
            // Read right after the Stand's tick that struck, before the cow's own tick can bleed.
            scene.listen(EntityTickEvent.Post.class, event -> {
                if (event.getEntity() == caster.stand() && snapshot == null && action.punchedTarget != null) {
                    hitCow = action.punchedTarget.getMainEntity() == cow;
                    health = cow.getHealth();
                    snapshot = List.copyOf(order);
                }
            });
        }

        void verdict() {
            scene.premise(hitCow && attempts == 1, "the Chop struck the cow once: attempts=" + attempts);
            log(scene.id + " order=" + snapshot + " health=" + health);
            if (refused) {
                check(health == 200 && !snapshot.contains(HURT) && !snapshot.contains(SOUND),
                        scene.id + " 1.16 played no impact sound for a hit that did not land: " + snapshot);
            } else {
                check(snapshot.equals(List.of(HURT, SOUND)),
                        scene.id + " 1.16 played one impact sound, after the hurt: " + snapshot);
            }
        }

        void check(boolean passed, String detail) {
            checks++;
            log((passed ? "PASS " : "FAIL ") + detail);
            if (!passed) failures.add(detail);
        }

        void close() { if (closed) return; closed = true; if (scene != null) scene.close(); }
        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
    }

    static void log(String message) { JojoMod.getLogger().info("KC-IMPACT-SOUND " + message); }
}
