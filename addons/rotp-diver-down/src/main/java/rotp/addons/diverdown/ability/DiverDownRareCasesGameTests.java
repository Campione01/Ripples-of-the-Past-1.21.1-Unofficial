package rotp.addons.diverdown.ability;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.diverdown.DiverDownAddon;
import rotp.addons.diverdown.entity.BoneShardEntity;
import rotp.addons.diverdown.entity.DiverDownStandEntity;
import rotp.addons.diverdown.entity.KineticTrapEntity;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;

/**
 * Three rarer 1.16 rules of Diver Down (049 RotP Diver Down 1.1.2.1.0): the kinetic trap's choice of target, a bone
 * shard that breaks a block, and the deform dash on an entity that is not living.
 */
@GameTestHolder(DiverDownAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DiverDownRareCasesGameTests {
    private static final String TEMPLATES = "rotp_dd_review_fixes";
    private static final int OWN_TICKS = 60;

    private DiverDownRareCasesGameTests() {}

    // 1.16 KineticTrapEntity.tick (049 :143-166): the trap took the first living entity within 1.1 blocks, its owner too,
    // turned to it and fired only if canDamage allowed that one, so an owner standing on the trap kept it from firing at
    // a mob beside them.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_rare_trap_order",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void kineticTrapHoldsFireWhileItsOwnerIsTheFirstNearItAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            DiverDownWallsGameTests.Scene scene = new DiverDownWallsGameTests.Scene(helper, placement);
            try {
                BlockPos floor = scene.block(2.0D, -1, Blocks.STONE.defaultBlockState());
                KineticTrapEntity trap = new KineticTrapEntity(scene.owner, scene.stand, helper.getLevel());
                Vec3 center = Vec3.atCenterOf(floor);
                trap.moveTo(center.x, center.y, center.z, 0.0F, 0.0F);
                helper.assertTrue(helper.getLevel().addFreshEntity(trap), "Could not add a kinetic trap");
                scene.extra.add(trap);
                // Diver Down, a living entity too, would stand by the trap
                scene.unsummon();
                scene.owner.moveTo(center.x, floor.getY() + 1.0D, center.z, 0.0F, 0.0F);
                Cow cow = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(cow != null, "Could not create a cow");
                scene.prepareMob(cow, new Vec3(center.x, floor.getY() + 1.0D, center.z));
                List<LivingEntity> near = helper.getLevel().getEntitiesOfClass(LivingEntity.class,
                        trap.getBoundingBox().inflate(KineticTrapEntity.TRIGGER_RANGE));
                helper.assertTrue(!near.isEmpty() && near.get(0) == scene.owner && near.contains(cow),
                        "Fixture: the owner is not the first living entity near the trap: " + near);
                trap.tick();
                helper.assertTrue(!trap.isTriggered() && cow.getHealth() == 500.0F,
                        "1.16 turned the trap to the first living entity near it, its owner standing on it, and did not "
                                + "fire; the port fired at the cow beside: triggered " + trap.isTriggered()
                                + ", cow health " + cow.getHealth());
                scene.owner.moveTo(scene.origin.x, scene.origin.y, scene.origin.z, 0.0F, 0.0F);
                trap.tick();
                helper.assertTrue(trap.isTriggered() && cow.getHealth() < 500.0F,
                        "Fixture: the trap did not fire at the cow once its owner stepped off; triggered "
                                + trap.isTriggered() + ", cow health " + cow.getHealth());
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 BoneShardEntity.afterBlockHit (049 :74-86): a shard that broke the block it hit (hardness 0) was slowed by a
    // tenth and checkHit struck what lay on the rest of its way that tick before the shard went; here a cow just behind a
    // slime block's face (slime breaks at hardness 0 and, unlike grass, has a collision box the projectile ray meets).
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_rare_shard_beyond",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void boneShardStrikesWhatLiesBeyondABlockItBreaksAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            DiverDownWallsGameTests.Scene scene = new DiverDownWallsGameTests.Scene(helper, placement);
            try {
                Cow origin = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(origin != null, "Could not create a cow");
                scene.prepareMob(origin, scene.origin.add(2.0D, 0.0D, 0.0D));
                BlockPos slime = scene.block(2.0D, 1, Blocks.SLIME_BLOCK.defaultBlockState());
                Cow behind = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(behind != null, "Could not create a cow");
                // its box, grown by the projectile ray's 0.3, begins 0.3 past the slime's face: beyond the block the
                // shard meets first, within the 0.72 left of its move
                scene.prepareMob(behind, new Vec3(slime.getX() + 0.5D, slime.getY(), slime.getZ() + 0.85D));
                BoneShardEntity shard = new BoneShardEntity(origin, scene.owner, scene.stand, helper.getLevel());
                shard.setPos(slime.getX() + 0.5D, slime.getY() + 0.5D, slime.getZ() - 0.2D);
                shard.setDeltaMovement(0.0D, 0.0D, 0.8D);
                helper.assertTrue(helper.getLevel().addFreshEntity(shard), "Could not add a bone shard");
                scene.extra.add(shard);
                scene.poll(0, () -> {
                    helper.assertTrue(helper.getLevel().getBlockState(slime).isAir(),
                            "Fixture: the shard has not broken the slime block yet");
                    helper.assertTrue(behind.getHealth() < 500.0F,
                            "1.16 let a shard that broke the block it hit strike the cow on the rest of its way that "
                                    + "tick; cow health " + behind.getHealth() + ", shard removed " + shard.isRemoved());
                });
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 StandEntityPunch.knockbackTarget: the deform dash's HeavyPunchInstance set an entity that is not living (a
    // boat) moving away from the Stand at half the heavy knockback, 0.5 + strength / (8 - finisher x 4).
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_rare_dash_boat",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void deformDashKnocksABoatBackAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 3, placement -> {
            DiverDownWallsGameTests.Scene scene = new DiverDownWallsGameTests.Scene(helper, placement);
            try {
                DiverDownStandEntity stand = scene.stand;
                LivingEntity user = scene.owner;
                stand.setManuallyControlled(true);
                stand.setNoGravity(true);
                // nothing to strike as the perform begins: the Stand looks straight up
                stand.moveTo(scene.origin.x, scene.origin.y, scene.origin.z, 0.0F, -90.0F);
                Ability dash = scene.power.getAbility(DiverDownAddon.DEFORM_DASH_NAME);
                EntityActionInstance action = ((EntityActionType) dash).initActionOnAbilityUse(
                        helper.getLevel(), user, stand, null);
                action.setStartingPhase();
                LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
                helper.assertTrue(stand.getCurStandAction() == action && action.getPhase() == ActionPhase.PERFORM,
                        "Fixture: the deform dash is not Diver Down's action in its perform");
                scene.standFacing(scene.origin, 0.0F);
                Vec3 eyes = stand.getEyePosition();
                Boat boat = EntityType.BOAT.create(helper.getLevel());
                helper.assertTrue(boat != null, "Could not create a boat");
                boat.setNoGravity(true);
                boat.moveTo(eyes.x + 0.2D, eyes.y - 0.3D, eyes.z + 1.6D, 0.0F, 0.0F);
                helper.assertTrue(helper.getLevel().addFreshEntity(boat), "Could not add a boat");
                scene.extra.add(boat);
                // the hit breaks the boat into its item
                scene.cleanup.add(() -> helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                        boat.getBoundingBox().inflate(3.0D)).forEach(ItemEntity::discard));
                double reach = stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
                List<ActionTarget> onRay = HitResultUtil.clipMultipleTargets(stand, reach,
                        entity -> StandEntityPunchAbility.canStandHit(stand, entity), 0.25D, stand.getPrecision());
                helper.assertTrue(onRay.size() == 1 && onRay.get(0).getEntity() == boat,
                        "Fixture: the dash's ray does not meet the boat alone: " + onRay);
                Vec3 toBoat = boat.position().subtract(stand.position());
                double speed = StandStatFormulas.getHeavyAttackKnockback(stand.getAttackDamage(),
                        stand.getLastHeavyFinisherValue()) * 0.5D;
                Vec3 expected = toBoat.multiply(1.0D, 0.0D, 1.0D).normalize().scale(speed);
                action.actionTick();
                helper.assertTrue(boat.getDeltaMovement().distanceTo(expected) < 1.0E-3D,
                        "1.16 set the boat the deform dash struck moving away from Diver Down at half the heavy "
                                + "knockback, " + expected + "; it moves " + boat.getDeltaMovement());
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }
}
