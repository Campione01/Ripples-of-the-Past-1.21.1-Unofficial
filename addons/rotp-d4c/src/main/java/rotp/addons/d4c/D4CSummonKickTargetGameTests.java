package rotp.addons.d4c;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.d4c.entity.D4CSummonKickEntity;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.subsystems.target.ActionTarget;

/**
 * 1.16 summon_kick (aa/aa/ae.java) was a StandAction with TargetRequirement.ENTITY. Its target was its client's mouse
 * target (InputHandler.pickMouseTarget :697-711): the vanilla pick, and behind a miss with D4C out D4C's precision ray at
 * the user's reach (EntityStandType.clientHitResult :122-151; the user's own Stand was a miss). PowerBaseImpl.checkTarget
 * :289-313 refused anything but an entity without a message, Action.checkRangeAndTarget :162-204 measured it from the
 * performer (D4C while out, else the user) against the default 8 blocks, 4 out of sight (target_too_far), and the kick's
 * own check (ae.java:56-65) wanted a living entity (summon_kick_need_target). The donor's 16.0 (ae.java:124-126)
 * overrode no core method.
 */
@GameTestHolder(D4CAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class D4CSummonKickTargetGameTests {
    private static final String TOO_FAR = "jojo.message.action_condition.target_too_far";
    private static final String NEED_TARGET = "jojo.message.action_condition.summon_kick_need_target";

    private D4CSummonKickTargetGameTests() {}

    @GameTest(template = "empty", batch = "d4cSummonKickTarget", timeoutTicks = 80 + GameTestChunkTickets.WAIT_TICKS)
    public static void summonKickTakesTheCrosshairTargetWithin116sRange(GameTestHelper helper) {
        GameTestChunkTickets.forTest(helper).coverAround(helper.absolutePos(BlockPos.ZERO), 16)
                .whenTicking(() -> {}, h -> summonKickTargetBody(h));
    }

    private static void summonKickTargetBody(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        ServerLevel level = helper.getLevel();
        List<BlockPos> blocks = new ArrayList<>();
        try (D4CGameTestScene scene = new D4CGameTestScene(helper, "d4c-kick-target")) {
            Ability kick = scene.power.getAbility("summon_kick");
            helper.assertTrue(kick != null, "D4C has no summon_kick");
            // above the barrier walls that close the 8-block template, with room for ten blocks in open air
            Vec3 open = helper.absoluteVec(new Vec3(4.5D, 12.0D, 1.5D));
            scene.owner.moveTo(open.x, open.y, open.z, 0.0F, 0.0F);
            // D4C out: the performer, and its precision ray behind an empty crosshair
            scene.stand.setPos(scene.owner.position().add(-1.0D, 0.0D, 0.0D));

            Cow near = scene.cow(new Vec3(0.0D, 0.0D, 4.0D));
            lookAt(scene, near);
            aim(scene, ActionTarget.EMPTY);
            row(wrong, scene, kick, "D4C out, empty crosshair, cow 4 blocks ahead (precision ray)", true, null);
            BlockPos behind = BlockPos.containing(near.position().add(0.0D, 0.5D, 1.5D));
            level.setBlockAndUpdate(behind, Blocks.STONE.defaultBlockState());
            blocks.add(behind);
            aim(scene, new ActionTarget(behind, Direction.NORTH));
            row(wrong, scene, kick, "D4C out, the crosshair on a block behind the cow", false, null);
            aim(scene, new ActionTarget(scene.stand));
            row(wrong, scene, kick, "D4C out, the crosshair on the user's own D4C, a cow behind it", false, null);
            discard(scene, near);

            Cow far = scene.cow(new Vec3(10.0D, 0.0D, 0.0D));
            lookAt(scene, far);
            aim(scene, ActionTarget.EMPTY);
            row(wrong, scene, kick, "D4C out, empty crosshair, cow 10 blocks ahead", false, null);
            discard(scene, far);

            Cow six = scene.cow(new Vec3(0.0D, 0.0D, 6.0D));
            lookAt(scene, six);
            aim(scene, new ActionTarget(six));
            sight(wrong, scene.owner, six, true);
            scene.stand.setPos(scene.owner.position().add(0.0D, 0.0D, -4.0D));
            row(wrong, scene, kick, "cow 6 blocks from the user, 10 from D4C (the performer)", false, TOO_FAR);
            scene.stand.setPos(scene.owner.position().add(-1.0D, 0.0D, 0.0D));
            row(wrong, scene, kick, "cow 6 blocks from the user, D4C beside the user", true, null);
            discard(scene, six);

            // D4C not out: the user is the performer
            D4CAddon.D4C.get().forceUnsummon(scene.owner, scene.power);
            helper.assertTrue(scene.power.getSummonedStandEntity() == null, "Setup: D4C is still out.");

            Cow ten = scene.cow(new Vec3(0.0D, 0.0D, 10.0D));
            lookAt(scene, ten);
            aim(scene, new ActionTarget(ten));
            sight(wrong, scene.owner, ten, true);
            row(wrong, scene, kick, "cow 10 blocks away in sight", false, TOO_FAR);
            if (D4CPhase2BService.summonKick(level, scene.owner)) {
                wrong.add("the kick was sent at the cow 10 blocks away");
            }
            discard(scene, ten);

            Cow close = scene.cow(new Vec3(0.0D, 0.0D, 2.0D));
            lookAt(scene, close);
            aim(scene, ActionTarget.EMPTY);
            row(wrong, scene, kick, "no D4C, empty crosshair, cow 2 blocks ahead (no ray of the server's own)", false,
                    null);
            discard(scene, close);

            Minecart cart = EntityType.MINECART.create(level);
            helper.assertTrue(cart != null, "fixture: no minecart");
            cart.setNoGravity(true);
            cart.moveTo(scene.owner.position().add(0.0D, 0.0D, 3.0D));
            helper.assertTrue(level.addFreshEntity(cart), "fixture: the minecart could not join the level");
            scene.spawned.add(cart);
            lookAt(scene, cart);
            aim(scene, new ActionTarget(cart));
            row(wrong, scene, kick, "a minecart 3 blocks away", false, NEED_TARGET);
            discard(scene, cart);

            Vec3 eye = scene.owner.getEyePosition();
            for (int dy = -2; dy <= 1; dy++) {
                BlockPos wall = BlockPos.containing(eye.x, eye.y + dy, eye.z + 2.0D);
                level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
                blocks.add(wall);
            }
            Cow hidden = scene.cow(new Vec3(0.0D, 0.0D, 6.0D));
            lookAt(scene, hidden);
            aim(scene, new ActionTarget(hidden));
            sight(wrong, scene.owner, hidden, false);
            row(wrong, scene, kick, "cow 6 blocks away out of sight", false, TOO_FAR);
            discard(scene, hidden);
            Cow hiddenNear = scene.cow(new Vec3(0.0D, 0.0D, 3.0D));
            lookAt(scene, hiddenNear);
            aim(scene, new ActionTarget(hiddenNear));
            sight(wrong, scene.owner, hiddenNear, false);
            row(wrong, scene, kick, "cow 3 blocks away out of sight", true, null);
            discard(scene, hiddenNear);
            for (BlockPos pos : blocks) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            blocks.clear();

            Cow target = scene.cow(new Vec3(0.0D, 0.0D, 6.0D));
            lookAt(scene, target);
            aim(scene, new ActionTarget(target));
            sight(wrong, scene.owner, target, true);
            scene.power.tick();
            BufferingState press = BufferingState.clickOnly();
            AbilityInput.keyPress((short) 240, kick, scene.owner, new FriendlyByteBuf(Unpooled.buffer()),
                    InputMethod.CLICK, 0, press, kick.getAbilityId());
            AbilityInput.keyRelease((short) 240, scene.owner);
            List<D4CSummonKickEntity> kicks = scene.around(D4CSummonKickEntity.class);
            if (kicks.size() != 1) {
                wrong.add("a press at the cow 6 blocks away in sight summoned " + kicks.size() + " kicks");
            }
            kicks.forEach(Entity::discard);
        }
        finally {
            for (BlockPos pos : blocks) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        }
        helper.assertTrue(wrong.isEmpty(), "1.16's summon kick took the crosshair target within its range: " + wrong);
        helper.succeed();
    }

    // The press check a client's packet runs on the server, and the message it sends
    private static void row(List<String> wrong, D4CGameTestScene scene, Ability kick, String setup, boolean admitted,
            String message) {
        scene.power.tick();
        scene.clearMessages();
        boolean got = AbilityInput.withConditionCheck(kick, scene.owner);
        List<String> keys = scene.keys();
        if (got != admitted) {
            wrong.add(setup + ": " + (got ? "admitted" : "refused " + keys));
        }
        else if (!admitted && !(message == null ? keys.isEmpty() : keys.equals(List.of(message)))) {
            wrong.add(setup + ": refused with " + keys + ", 1.16 sent " + (message == null ? "nothing" : message));
        }
    }

    private static void sight(List<String> wrong, LivingEntity from, Entity to, boolean expected) {
        if (from.hasLineOfSight(to) != expected) {
            wrong.add("fixture: the cow at " + to.position() + " is " + (expected ? "out of" : "in") + " sight of "
                    + from.position());
        }
    }

    private static void aim(D4CGameTestScene scene, ActionTarget target) {
        LivingComponentAction.getComponent(scene.owner).entityAim.setTarget(target);
    }

    private static void discard(D4CGameTestScene scene, Entity entity) {
        entity.discard();
        scene.spawned.remove(entity);
    }

    private static void lookAt(D4CGameTestScene scene, Entity target) {
        Vec3 aim = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D).subtract(scene.owner.getEyePosition());
        scene.owner.setYRot((float) Math.toDegrees(Math.atan2(-aim.x, aim.z)));
        scene.owner.setXRot((float) -Math.toDegrees(Math.atan2(aim.y, Math.sqrt(aim.x * aim.x + aim.z * aim.z))));
        scene.owner.setYHeadRot(scene.owner.getYRot());
    }
}
