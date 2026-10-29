package rotp.addons.diverdown.ability;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import rotp.addons.diverdown.DiverDownAddon;
import rotp.addons.diverdown.DiverDownDamageTypes;
import rotp.addons.diverdown.DiverDownGameEvents;
import rotp.addons.diverdown.entity.DiverDownStandEntity;
import rotp.addons.diverdown.entity.KineticTrapEntity;
import rotp.addons.diverdown.state.DiverDownAuthority;
import rotp.core.JojoModConfig;
import rotp.core.api.stand.StandSkillWalls;
import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility.StandEntityPunch;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.target.ActionTarget;

/**
 * Diver Down phases through solids (owner, 2026-10-15: walls follow the manga; Stone Ocean ch. 74, 84, 106). Its dive
 * into a target and its deform dash aim at a mob behind a wall, and its attacks follow the aim: a mob the aim meets
 * behind a wall is hit through it, a wall with nothing behind it is broken.
 */
@GameTestHolder(DiverDownAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DiverDownWallsGameTests {
    private static final String TEMPLATES = "rotp_dd_review_fixes";
    private static final int OWN_TICKS = 60;
    private static final int LONG_TICKS = 120;

    private DiverDownWallsGameTests() {}

    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_walls_phasing",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void entityPhasingDivesIntoAMobBehindAWall(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement);
            try {
                scene.look(0.0F);
                scene.wall(1.0D, 0);
                scene.wall(1.0D, 1);
                Cow mob = scene.mob(3.0D, 0.0D);
                Ability phasing = scene.power.getAbility(DiverDownAddon.ENTITY_PHASING_NAME);
                helper.assertTrue(phasing != null && StandSkillWalls.isMarked(phasing),
                        "Diver Down's entity phasing is not marked through walls");
                helper.assertTrue(phasing.checkConditions(scene.power).isPositive(),
                        "Fixture: entity phasing is not available");
                phasing.onClick(helper.getLevel(), scene.owner, null);
                helper.assertTrue(scene.stand.isEmbedded() && scene.stand.getEmbeddedTarget() == mob,
                        "Diver Down did not dive into the cow behind the wall; embedded in "
                                + scene.stand.getEmbeddedTarget());
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // The Stand's synced aim stops at the wall, as an idle client sends it; the marked dash looks behind it.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_walls_dash",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void deformDashAimsAtTheMobBehindAWall(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement);
            try {
                DiverDownStandEntity stand = scene.stand;
                stand.setManuallyControlled(true);
                stand.setNoGravity(true);
                // the Stand's eyes level with the middle of the wall block 1 block ahead
                BlockPos wall = BlockPos.containing(scene.origin.x, scene.origin.y + 1.0D, scene.origin.z + 1.0D);
                double eyeY = wall.getY() + 0.5D;
                stand.moveTo(scene.origin.x, eyeY - stand.getEyeHeight(), scene.origin.z, 0.0F, 0.0F);
                stand.setYHeadRot(0.0F);
                stand.setDeltaMovement(Vec3.ZERO);
                scene.place(wall);
                scene.place(wall.above());
                scene.place(wall.below());
                Cow mob = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(mob != null, "Could not create a cow");
                scene.prepareMob(mob, new Vec3(scene.origin.x, eyeY - 0.7D, scene.origin.z + 2.35D));
                Vec3 eyes = stand.getEyePosition();
                helper.assertTrue(mob.getBoundingBox().clip(eyes, eyes.add(stand.getLookAngle().scale(2.5D)))
                                .isPresent(),
                        "Fixture: the cow is not on the Stand's look within its reach");
                LivingComponentAction.getComponent(stand).entityAim.setTarget(
                        new ActionTarget(wall, Direction.NORTH));
                Ability dash = scene.power.getAbility(DiverDownAddon.DEFORM_DASH_NAME);
                helper.assertTrue(dash != null && StandSkillWalls.isMarked(dash),
                        "Diver Down's deform dash is not marked through walls");
                BufferingState buffering = BufferingState.clickOnly();
                dash.onKeyPress(helper.getLevel(), scene.owner, null, InputMethod.CLICK, 0.0F, buffering);
                EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
                helper.assertTrue(buffering.isActionSuccess && action instanceof DeformDashAbility.DeformDashAction,
                        "Fixture: the deform dash did not start");
                DeformDashAbility.DeformDashAction dashAction = (DeformDashAbility.DeformDashAction) action;
                scene.poll(0, () -> {
                    helper.assertTrue(action.getPhase() != ActionPhase.WINDUP || action.isOver(),
                            "The deform dash has not performed yet");
                    ActionTarget target = dashAction.dashTarget();
                    helper.assertTrue(target.getType() == ActionTarget.TargetType.ENTITY
                                    && target.getMainEntity() == mob,
                            "The deform dash aimed at " + target + " instead of the cow behind the wall");
                });
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // The everyday dash: the user aims, and the synced aims a client sends while no action runs met the wall first.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_walls_dash_user_aim",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void deformDashFromTheUsersAimTakesTheMobBehindAWall(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 3, placement -> {
            Scene scene = new Scene(helper, placement);
            try {
                double ahead = 2.2D;
                Vec3 eyes = scene.owner.getEyePosition();
                scene.look((float) Math.toDegrees(Math.atan2(eyes.y - (scene.origin.y + 0.7D), ahead)));
                BlockPos wall = scene.wall(1.0D, 1);
                Cow mob = scene.mob(ahead, 0.0D);
                helper.assertTrue(!scene.stand.isManuallyControlled() && !scene.stand.aimsWithoutUser(),
                        "Fixture: the user must aim for its Stand");
                ActionTarget wallAim = new ActionTarget(wall, Direction.NORTH);
                LivingComponentAction.getComponent(scene.owner).entityAim.setTarget(wallAim);
                LivingComponentAction.getComponent(scene.stand).entityAim.setTarget(wallAim);
                Ability dash = scene.power.getAbility(DiverDownAddon.DEFORM_DASH_NAME);
                BufferingState buffering = BufferingState.clickOnly();
                dash.onKeyPress(helper.getLevel(), scene.owner, null, InputMethod.CLICK, 0.0F, buffering);
                EntityActionInstance action = LivingComponentAction.getCurEntityAction(scene.stand);
                helper.assertTrue(buffering.isActionSuccess && action instanceof DeformDashAbility.DeformDashAction,
                        "Fixture: the deform dash did not start");
                DeformDashAbility.DeformDashAction dashAction = (DeformDashAbility.DeformDashAction) action;
                scene.poll(0, () -> {
                    helper.assertTrue(action.getPhase() != ActionPhase.WINDUP || action.isOver(),
                            "The deform dash has not performed yet");
                    ActionTarget target = dashAction.dashTarget();
                    helper.assertTrue(target.getType() == ActionTarget.TargetType.ENTITY
                                    && target.getMainEntity() == mob,
                            "The deform dash from the user's aim took " + target + " instead of the cow behind the wall");
                });
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 DiverDownEntity.shouldHaveNoPhysics was always true: a manually controlled Diver Down moves into stone.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_body_controlled",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void controlledDiverDownPassesIntoAWall(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement);
            try {
                DiverDownStandEntity stand = scene.stand;
                stand.setManuallyControlled(true);
                stand.setNoGravity(true);
                stand.updateNoPhysics();
                helper.assertTrue(stand.noPhysics, "A manually controlled Diver Down must have no physics");
                double wallZ = scene.stoneColumnAhead(2.0D, Blocks.STONE.defaultBlockState());
                AABB box = scene.moveStandForward();
                helper.assertTrue(box.maxZ > wallZ + 0.5D,
                        "The manually controlled Diver Down stopped at the stone: " + box + ", stone from z " + wallZ);
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // While it acts: 1.16 collideNextPos returned the position it was given, and the Stand still phased.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_body_acting",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void actingDiverDownPassesIntoAWall(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement);
            try {
                DiverDownStandEntity stand = scene.stand;
                stand.setManuallyControlled(true);
                stand.setNoGravity(true);
                double wallZ = scene.stoneColumnAhead(2.0D, Blocks.BEDROCK.defaultBlockState());
                Ability barrageAbility = scene.power.getAbility(DiverDownAddon.BARRAGE);
                helper.assertTrue(barrageAbility instanceof EntityActionType, "Fixture: Diver Down has no barrage");
                EntityActionInstance barrage = ((EntityActionType) barrageAbility).initActionOnAbilityUse(
                        helper.getLevel(), scene.owner, stand, null);
                LivingComponentAction.getComponent(stand).setAction(barrage, scene.owner, SyncType.NO_SYNC);
                helper.assertTrue(stand.getCurStandAction() == barrage, "Fixture: the barrage did not start");
                Vec3 inWall = new Vec3(scene.origin.x, scene.origin.y, wallZ + 0.8D);
                Vec3 placed = stand.collideNextPos(inWall);
                helper.assertTrue(placed.equals(inWall),
                        "An acting Diver Down was kept out of the stone on its way to " + inWall + ": " + placed);
                scene.poll(0, () -> {
                    helper.assertTrue(stand.getCurStandAction() == barrage, "Fixture: the barrage ended");
                    helper.assertTrue(stand.noPhysics, "An acting Diver Down must have no physics");
                    AABB box = scene.moveStandForward();
                    helper.assertTrue(box.maxZ > wallZ + 0.5D,
                            "The acting Diver Down stopped at the wall: " + box + ", wall from z " + wallZ);
                });
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 DiverDownKineticTrap aimed at 5 blocks; the dive and the steal took a target within the user's reach of 5.
    // One test each, so that each check is seen failing on its own. The dive's user is a player: 1.16 reached its
    // target with the player's pick and, past it, the Stand's precision ray at the player's reach (a mob user had no
    // reach attribute, so the Stand's own).
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_reach_phasing",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void entityPhasingReachesFiveBlocks(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 8, placement -> {
            Scene scene = new Scene(helper, placement, true);
            try {
                scene.look(0.0F);
                Ability phasing = scene.power.getAbility(DiverDownAddon.ENTITY_PHASING_NAME);
                Cow far = scene.mob(6.5D, 0.5D);
                phasing.onClick(helper.getLevel(), scene.owner, null);
                helper.assertTrue(!scene.stand.isEmbedded(),
                        "Diver Down dived into a cow 6.5 blocks away: 1.16 reached 5");
                far.moveTo(scene.origin.x, scene.origin.y + 0.5D, scene.origin.z + 4.0D, 0.0F, 0.0F);
                phasing.onClick(helper.getLevel(), scene.owner, null);
                helper.assertTrue(scene.stand.getEmbeddedTarget() == far,
                        "Fixture: Diver Down did not dive into a cow 4 blocks away");
                scene.stand.clearEmbeddedTarget();
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_reach_trap",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void kineticTrapReachesFiveBlocks(GameTestHelper helper) {
        reachCase(helper, DiverDownAddon.KINETIC_TRAP_NAME, "The kinetic trap took a block");
    }

    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_reach_steal",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void stealItemReachesFiveBlocks(GameTestHelper helper) {
        reachCase(helper, DiverDownAddon.STEAL_ITEM_NAME, "Steal Item took a chest");
    }

    private static void reachCase(GameTestHelper helper, String abilityName, String took) {
        GameTestPlacement.whenTicking(helper, 8, placement -> {
            Scene scene = new Scene(helper, placement);
            try {
                scene.look(0.0F);
                Ability ability = scene.power.getAbility(abilityName);
                BlockPos farChest = scene.block(6.5D, 1, Blocks.CHEST.defaultBlockState());
                helper.assertFalse(ability.checkConditions(scene.power).isPositive(),
                        took + " 6.5 blocks away: 1.16 reached 5");
                scene.block(farChest, Blocks.AIR.defaultBlockState());
                scene.block(4.0D, 1, Blocks.CHEST.defaultBlockState());
                helper.assertTrue(ability.checkConditions(scene.power).isPositive(),
                        "Fixture: " + abilityName + " refused a chest 3.5 blocks away");
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 DiverDownKineticTrap 54-57: the block's centre when its top face is sturdy, else its bottom centre minus 0.5.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_trap_point",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void kineticTrapSitsWhere116PutIt(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement);
            try {
                scene.look(0.0F);
                Ability trap = scene.power.getAbility(DiverDownAddon.KINETIC_TRAP_NAME);
                BlockPos stone = scene.block(2.5D, 1, Blocks.STONE.defaultBlockState());
                KineticTrapEntity onStone = scene.setTrap(trap);
                helper.assertTrue(onStone.position().equals(Vec3.atCenterOf(stone)),
                        "The kinetic trap on stone is at " + onStone.position() + ", 1.16 put it at "
                                + Vec3.atCenterOf(stone));
                scene.block(stone, Blocks.STONE_SLAB.defaultBlockState());
                KineticTrapEntity onSlab = scene.setTrap(trap);
                Vec3 below = Vec3.atBottomCenterOf(stone).add(0.0D, -0.5D, 0.0D);
                helper.assertTrue(onSlab.position().equals(below),
                        "The kinetic trap on a bottom slab is at " + onSlab.position() + ", 1.16 put it at " + below);
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 DiverDownKineticTrap set a trap every time with no cap on how many an owner had (owner, 2026-10-15).
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_trap_no_cap",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void kineticTrapsAreNotCapped(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement);
            try {
                scene.look(0.0F);
                Ability trap = scene.power.getAbility(DiverDownAddon.KINETIC_TRAP_NAME);
                scene.block(2.5D, 1, Blocks.STONE.defaultBlockState());
                for (int set = 0; set < 10; set++) {
                    helper.assertTrue(trap.checkConditions(scene.power).isPositive(),
                            "The kinetic trap was refused with " + set + " traps set: 1.16 had no cap");
                    scene.setTrap(trap);
                    helper.assertTrue(scene.traps().size() == set + 1,
                            "Fixture: " + scene.traps().size() + " traps stand after setting " + (set + 1));
                }
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 KineticTrapEntity.tick 146: a trap whose own block turned to air was removed; one in stone stays.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_trap_block_air",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void kineticTrapGoesWithItsBlock(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement);
            try {
                scene.look(0.0F);
                Ability trap = scene.power.getAbility(DiverDownAddon.KINETIC_TRAP_NAME);
                scene.block(2.5D, 1, Blocks.STONE.defaultBlockState());
                KineticTrapEntity kept = scene.setTrap(trap);
                BlockPos front = scene.block(1.5D, 1, Blocks.STONE.defaultBlockState());
                KineticTrapEntity broken = scene.setTrap(trap);
                helper.assertTrue(broken.blockPosition().equals(front), "Fixture: the second trap is not in the front stone");
                scene.block(front, Blocks.AIR.defaultBlockState());
                int[] ticks = {0};
                scene.poll(0, () -> {
                    ticks[0]++;
                    helper.assertTrue(!kept.isRemoved(), "Fixture: the trap in the stone that stayed was removed");
                    helper.assertTrue(broken.isRemoved(), "The kinetic trap stayed after its block was broken");
                    helper.assertTrue(ticks[0] >= 3, "Watching the trap in the stone that stayed");
                });
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 InitEntities sized the trap 0.3 by 0.3, and its trigger box grew that by 1.1: a cow whose side is 1.35
    // blocks off the trap's centre is out of it, one 1.15 off is in.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_trap_size",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void kineticTrapIsSizedAs116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement);
            try {
                scene.look(0.0F);
                Ability trap = scene.power.getAbility(DiverDownAddon.KINETIC_TRAP_NAME);
                scene.block(2.5D, 1, Blocks.STONE.defaultBlockState());
                KineticTrapEntity set = scene.setTrap(trap);
                Vec3 centre = set.position();
                Cow cow = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(cow != null, "Could not create a cow");
                scene.prepareMob(cow, centre.add(1.8D, -0.5D, 0.0D));
                float health = cow.getHealth();
                int[] ticks = {0};
                boolean[] firedEarly = {false};
                scene.poll(0, () -> {
                    ticks[0]++;
                    firedEarly[0] |= ticks[0] <= 3 && (set.isTriggered() || cow.getHealth() < health);
                    helper.assertFalse(firedEarly[0],
                            "The kinetic trap fired at a cow whose side is 1.35 blocks off its centre: 1.16 reached 1.25");
                    if (ticks[0] == 3) {
                        cow.moveTo(centre.x + 1.6D, centre.y - 0.5D, centre.z, 0.0F, 0.0F);
                    }
                    helper.assertTrue(ticks[0] > 3 && set.isTriggered() && cow.getHealth() < health,
                            "Fixture: the kinetic trap did not fire at a cow whose side is 1.15 blocks off its centre");
                    helper.assertTrue(set.getBbWidth() == 0.3F && set.getBbHeight() == 0.3F,
                            "The kinetic trap is " + set.getBbWidth() + " by " + set.getBbHeight()
                                    + ", 1.16 sized it 0.3 by 0.3");
                });
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 DiverDownKineticTrap.getTargetRequirement BLOCK: a crosshair that meets an entity before the block refuses.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_trap_entity_first",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void kineticTrapRefusesAnEntityBeforeTheBlock(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement);
            try {
                scene.look(0.0F);
                Ability trap = scene.power.getAbility(DiverDownAddon.KINETIC_TRAP_NAME);
                scene.block(3.0D, 1, Blocks.STONE.defaultBlockState());
                Cow cow = scene.mob(1.8D, 0.0D);
                helper.assertFalse(trap.checkConditions(scene.power).isPositive(),
                        "The kinetic trap was set on a block behind a cow; 1.16 required a block target");
                List<KineticTrapEntity> before = scene.traps();
                scene.performTrap(trap);
                helper.assertTrue(scene.traps().size() == before.size(),
                        "The kinetic trap was set on a block behind a cow by the server");
                cow.discard();
                helper.assertTrue(trap.checkConditions(scene.power).isPositive(),
                        "Fixture: the kinetic trap refused the bare block");
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 KineticTrapEntity.tick turned the trap to its victim's eyes (lookAt EYES) when it fired; the arm model
    // points the way the trap faces.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_trap_arm_aim",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void firingKineticTrapTurnsToItsVictim(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement);
            try {
                scene.look(0.0F);
                Ability trap = scene.power.getAbility(DiverDownAddon.KINETIC_TRAP_NAME);
                scene.block(2.5D, 1, Blocks.STONE.defaultBlockState());
                KineticTrapEntity set = scene.setTrap(trap);
                Cow cow = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(cow != null, "Could not create a cow");
                scene.prepareMob(cow, set.position().add(1.0D, -0.8D, 0.6D));
                scene.poll(0, () -> {
                    helper.assertTrue(set.isTriggered(), "Fixture: the kinetic trap did not fire at the cow");
                    Vec3 toEyes = cow.getEyePosition().subtract(set.getEyePosition());
                    float yaw = Mth.wrapDegrees((float) (Mth.atan2(toEyes.z, toEyes.x) * 180.0D / Math.PI) - 90.0F);
                    float pitch = Mth.wrapDegrees((float) -(Mth.atan2(toEyes.y, toEyes.horizontalDistance())
                            * 180.0D / Math.PI));
                    helper.assertTrue(Math.abs(set.getYRot() - yaw) < 1.0E-3F
                                    && Math.abs(set.getXRot() - pitch) < 1.0E-3F,
                            "The firing kinetic trap faces yaw " + set.getYRot() + ", pitch " + set.getXRot()
                                    + "; 1.16 turned it to the cow's eyes, yaw " + yaw + ", pitch " + pitch);
                    helper.assertTrue(set.armTicks() >= 2, "Watching the arm count its ticks");
                });
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 KineticTrapEntity.activate played DIVER_DOWN_PUNCH_LIGHT at volume 1, pitch 1 with Entity.playSound when the
    // trap fired: the server broadcasts it from the trap, once, to every player near it.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_trap_sound",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void firingKineticTrapPlaysThePunchSound(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement);
            try {
                List<PlayLevelSoundEvent.AtPosition> heard = new ArrayList<>();
                Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
                    if (event.getLevel() == helper.getLevel() && event.getSound() != null
                            && event.getSound().value() == DiverDownAddon.DIVER_DOWN_PUNCH_LIGHT.get()) {
                        heard.add(event);
                    }
                };
                NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, PlayLevelSoundEvent.AtPosition.class,
                        listener);
                scene.cleanup.add(() -> NeoForge.EVENT_BUS.unregister(listener));
                scene.look(0.0F);
                Ability trap = scene.power.getAbility(DiverDownAddon.KINETIC_TRAP_NAME);
                scene.block(2.5D, 1, Blocks.STONE.defaultBlockState());
                KineticTrapEntity set = scene.setTrap(trap);
                helper.assertTrue(heard.isEmpty(), "Fixture: the punch sound played before the kinetic trap fired");
                Cow cow = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(cow != null, "Could not create a cow");
                scene.prepareMob(cow, set.position().add(1.0D, -0.8D, 0.6D));
                int[] firedTicks = {0};
                scene.poll(0, () -> {
                    helper.assertTrue(set.isTriggered(), "Fixture: the kinetic trap did not fire at the cow");
                    firedTicks[0]++;
                    helper.assertTrue(firedTicks[0] >= 4, "Watching the trap for a repeated sound");
                    helper.assertTrue(heard.size() == 1,
                            "The kinetic trap fired and " + heard.size() + " punch sounds played; 1.16 played one");
                    PlayLevelSoundEvent.AtPosition sound = heard.get(0);
                    helper.assertTrue(sound.getNewVolume() == 1.0F && sound.getNewPitch() == 1.0F
                                    && sound.getSource() == set.getSoundSource()
                                    && sound.getPosition().distanceToSqr(set.position()) < 1.0E-6D,
                            "The kinetic trap's punch sound was " + sound.getNewVolume() + "/" + sound.getNewPitch()
                                    + " in " + sound.getSource() + " at " + sound.getPosition()
                                    + "; 1.16: volume 1, pitch 1 from the trap at " + set.position());
                });
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 KineticTrapEntity.updateAttackTick, once a tick while the trap fired: the arm goes out 1.5 a tick until 8
    // or past it, back 1.2 a tick until 0 or past it, and out again, for the 15 ticks the trap lives.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_trap_arm_thrust",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void kineticTrapArmThrustsAs116(GameTestHelper helper) {
        float[] expected = {0.0F, 1.5F, 3.0F, 4.5F, 6.0F, 7.5F, 9.0F, 7.8F, 6.6F, 5.4F, 4.2F, 3.0F, 1.8F, 0.6F,
                -0.6F, 0.9F};
        for (int tick = 0; tick < expected.length; tick++) {
            float thrust = KineticTrapEntity.armThrust(tick);
            helper.assertTrue(Math.abs(thrust - expected[tick]) < 1.0E-4F,
                    "The kinetic trap's arm is out " + thrust + " pixels " + tick + " ticks after it fired; 1.16: "
                            + expected[tick]);
        }
        helper.succeed();
    }

    // 1.16 KineticTrapEntity kept no Stand: a trap set before Diver Down is unsummoned still fires with only its owner
    // about. activate hit through the hurt cooldown, for 7 times the Stand damage multiplier, on the owner's behalf,
    // and played the punch sound once.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_trap_unsummoned",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + LONG_TICKS + OWN_TICKS)
    public static void kineticTrapFiresWithDiverDownUnsummoned(GameTestHelper helper) {
        unsummonedTrapFires(helper, false);
    }

    // The same with a player for owner: the player tick, death, clone, respawn, dimension change and logout routes of
    // Diver Down's lifecycle cleanup reach a player only. 1.16 had none of them for the trap, and the owner stood for it.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_trap_unsummoned_player",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + LONG_TICKS + OWN_TICKS)
    public static void kineticTrapOfAPlayerFiresWithDiverDownUnsummoned(GameTestHelper helper) {
        unsummonedTrapFires(helper, true);
    }

    private static void unsummonedTrapFires(GameTestHelper helper, boolean playerOwner) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement, playerOwner);
            try {
                scene.damageMultiplier(2.0D);
                List<PlayLevelSoundEvent.AtPosition> heard = scene.punchSounds();
                scene.look(0.0F);
                Ability trap = scene.power.getAbility(DiverDownAddon.KINETIC_TRAP_NAME);
                scene.block(2.5D, 1, Blocks.STONE.defaultBlockState());
                KineticTrapEntity set = scene.setTrap(trap);
                DiverDownStandEntity stand = scene.stand;
                if (playerOwner) {
                    // what the tick of a player whose Stand is away cleans up after
                    DiverDownAuthority.markOwner(scene.owner);
                    helper.assertTrue(DiverDownAuthority.ownerHasAuthority(scene.owner),
                            "Fixture: the player owner is not marked");
                }
                // not unsummon(): its retraction to the user did not finish in about one run in ten at the random
                // test origin; the Stand leaves the level the same way
                scene.power.getPowerType().forceUnsummon(scene.owner, scene.power);
                Cow cow = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(cow != null, "Could not create a cow");
                int[] stage = {0};
                int[] ticks = {0};
                float[] health = {0.0F};
                boolean[] lostEarly = {false};
                scene.poll(0, LONG_TICKS, () -> {
                    if (scene.owner instanceof ServerPlayer player) {
                        // a real server ticks the player through its connection, which the mock one does not
                        player.doTick();
                    }
                    lostEarly[0] |= set.isRemoved() && !set.isTriggered();
                    helper.assertFalse(lostEarly[0],
                            "The kinetic trap went with Diver Down's unsummon; 1.16 kept it");
                    if (stage[0] == 0) {
                        if (!stand.isRemoved() || DiverDownAddon.activeStand(scene.owner) != null) {
                            helper.fail("Fixture: Diver Down is still out");
                        }
                        stage[0] = 1;
                    }
                    if (stage[0] == 1) {
                        helper.assertFalse(set.isTriggered(), "Fixture: the kinetic trap fired with nothing near it");
                        if (++ticks[0] < 5) {
                            helper.fail("Watching the trap with Diver Down unsummoned");
                        }
                        if (scene.owner instanceof ServerPlayer player) {
                            helper.assertFalse(DiverDownAuthority.ownerHasAuthority(player),
                                    "Fixture: the player tick did not run the cleanup for a player without a Stand");
                            helper.assertTrue(player.level() == helper.getLevel() && !set.isRemoved(),
                                    "The kinetic trap went with the player tick cleanup; 1.16 kept it");
                            ResourceKey<Level> from = helper.getLevel().dimension();
                            ResourceKey<Level> to = from == Level.NETHER ? Level.OVERWORLD : Level.NETHER;
                            DiverDownGameEvents.onLivingDeath(new LivingDeathEvent(player,
                                    helper.getLevel().damageSources().generic()));
                            DiverDownGameEvents.onPlayerClone(new PlayerEvent.Clone(player, player, true));
                            DiverDownGameEvents.onPlayerRespawn(new PlayerEvent.PlayerRespawnEvent(player, false));
                            DiverDownGameEvents.onPlayerChangedDimension(
                                    new PlayerEvent.PlayerChangedDimensionEvent(player, from, to));
                            DiverDownGameEvents.onPlayerLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
                            helper.assertTrue(!set.isRemoved(),
                                    "The kinetic trap went with its owner's death, clone, respawn, dimension change"
                                            + " or logout; 1.16 kept it");
                        }
                        scene.prepareMob(cow, set.position().add(1.0D, -0.8D, 0.6D));
                        // a hit bigger than the trap's leaves the cow in its hurt cooldown
                        helper.assertTrue(cow.hurt(helper.getLevel().damageSources().generic(), 20.0F),
                                "Fixture: the cow took no first hit");
                        health[0] = cow.getHealth();
                        stage[0] = 2;
                        ticks[0] = 0;
                        helper.fail("The cow has just come to the trap");
                    }
                    helper.assertTrue(set.isTriggered(),
                            "The kinetic trap did not fire at a cow with Diver Down unsummoned; 1.16 needed only "
                                    + "its owner");
                    float expected = KineticTrapEntity.DAMAGE * 2.0F;
                    helper.assertTrue(Math.abs(health[0] - cow.getHealth() - expected) < 1.0E-4F,
                            "The unsummoned trap took " + (health[0] - cow.getHealth()) + " from a cow in its hurt"
                                    + " cooldown; 1.16 hit through it for " + expected
                                    + " (7 times the Stand damage multiplier 2)");
                    DamageSource source = cow.getLastDamageSource();
                    helper.assertTrue(source != null && source.is(DiverDownDamageTypes.KINETIC_TRAP)
                                    && source.getEntity() == scene.owner && source.getDirectEntity() == set
                                    && cow.getLastHurtByMob() == scene.owner,
                            "The unsummoned trap's hit is " + source + ", the cow blames "
                                    + cow.getLastHurtByMob() + "; the trap hits on its owner's behalf");
                    helper.assertTrue(++ticks[0] >= 4, "Watching the trap for a repeated sound");
                    helper.assertTrue(heard.size() == 1,
                            "The unsummoned trap fired and " + heard.size() + " punch sounds played; 1.16 played one");
                });
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 KineticTrapEntity.tick: an idle trap went when tickCount passed 1200, and the world loop and tick() each
    // raised tickCount once a game tick: it lived 600 game ticks, Stand or no Stand.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_trap_idle_expiry",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + 600 + LONG_TICKS)
    public static void idleKineticTrapExpiresAfter600Ticks(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement);
            try {
                scene.look(0.0F);
                Ability trap = scene.power.getAbility(DiverDownAddon.KINETIC_TRAP_NAME);
                scene.block(2.5D, 1, Blocks.STONE.defaultBlockState());
                KineticTrapEntity set = scene.setTrap(trap);
                long setAt = helper.getLevel().getGameTime();
                long expiresAt = setAt + 600L;
                helper.assertTrue(set.expiresAt() == expiresAt,
                        "The idle kinetic trap ends at " + set.expiresAt() + ", 1.16 kept it 600 game ticks, to "
                                + expiresAt);
                scene.power.getPowerType().forceUnsummon(scene.owner, scene.power);
                long[] lostAt = {-1L};
                scene.poll(0, 600 + LONG_TICKS, () -> {
                    long now = helper.getLevel().getGameTime();
                    if (lostAt[0] < 0L && set.isRemoved()) {
                        lostAt[0] = now;
                    }
                    helper.assertTrue(lostAt[0] < 0L || lostAt[0] >= expiresAt,
                            "The idle kinetic trap was gone " + (lostAt[0] - setAt)
                                    + " ticks after it was set; 1.16 kept it 600 game ticks");
                    helper.assertFalse(set.isTriggered(), "Fixture: the idle kinetic trap fired");
                    helper.assertTrue(lostAt[0] >= 0L,
                            "The idle kinetic trap is still there " + (now - setAt) + " ticks after it was set");
                    helper.assertTrue(lostAt[0] == expiresAt,
                            "The idle kinetic trap was gone " + (lostAt[0] - setAt)
                                    + " ticks after it was set; 1.16 kept it 600 game ticks");
                });
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 saved the trap's owner only, and tickCount began at 0 again when its chunk loaded it: a reloaded trap is
    // idle with a whole life of 600 game ticks ahead, whatever it was saved as.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_trap_reload",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void reloadedKineticTrapGetsAFreshLife(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement);
            try {
                scene.look(0.0F);
                Ability trap = scene.power.getAbility(DiverDownAddon.KINETIC_TRAP_NAME);
                scene.block(2.5D, 1, Blocks.STONE.defaultBlockState());
                KineticTrapEntity set = scene.setTrap(trap);
                Cow cow = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(cow != null, "Could not create a cow");
                scene.prepareMob(cow, set.position().add(1.0D, -0.8D, 0.6D));
                scene.poll(0, () -> {
                    helper.assertTrue(set.isTriggered(), "Fixture: the kinetic trap did not fire at the cow");
                    long now = helper.getLevel().getGameTime();
                    CompoundTag saved = set.saveWithoutId(new CompoundTag());
                    // the tags an earlier port saved, with an end time long past
                    saved.putBoolean("Triggered", true);
                    saved.putLong("ExpiresAt", now - 1000L);
                    KineticTrapEntity loaded = new KineticTrapEntity(DiverDownAddon.KINETIC_TRAP_ENTITY.get(),
                            helper.getLevel());
                    loaded.load(saved);
                    helper.assertTrue(scene.owner.getUUID().equals(loaded.getOwnerUuid()),
                            "The reloaded kinetic trap lost its owner: " + loaded.getOwnerUuid());
                    helper.assertFalse(loaded.isTriggered(),
                            "The reloaded kinetic trap came back fired; 1.16 kept no more than the owner");
                    helper.assertTrue(loaded.expiresAt() == now + 600L,
                            "The reloaded kinetic trap ends at " + loaded.expiresAt() + ", " + (loaded.expiresAt() - now)
                                    + " ticks from now; 1.16 gave it a whole life of 600 game ticks");
                });
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 KineticTrapEntity.getOwner looked the owner up by UUID each tick: with the owner gone from the level (a
    // logout, another dimension) the trap stays and waits, and fires once the owner is back.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_trap_owner_away",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + LONG_TICKS + OWN_TICKS)
    public static void kineticTrapWaitsForItsOwner(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement);
            try {
                scene.look(0.0F);
                Ability trap = scene.power.getAbility(DiverDownAddon.KINETIC_TRAP_NAME);
                scene.block(2.5D, 1, Blocks.STONE.defaultBlockState());
                KineticTrapEntity set = scene.setTrap(trap);
                DiverDownStandEntity stand = scene.stand;
                UUID ownerUuid = scene.owner.getUUID();
                scene.owner.discard();
                Cow cow = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(cow != null, "Could not create a cow");
                scene.prepareMob(cow, set.position().add(1.0D, -0.8D, 0.6D));
                float health = cow.getHealth();
                Cow back = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(back != null, "Could not create the returning owner");
                int[] ticks = {0};
                boolean[] returned = {false};
                boolean[] firedAlone = {false};
                scene.poll(0, LONG_TICKS, () -> {
                    if (returned[0]) {
                        helper.assertTrue(set.isTriggered() && cow.getHealth() < health,
                                "The kinetic trap did not fire once its owner was back");
                        helper.assertTrue(cow.getLastHurtByMob() == back,
                                "The cow blames " + cow.getLastHurtByMob() + ", not the returned owner");
                        return;
                    }
                    firedAlone[0] |= set.isTriggered() || cow.getHealth() < health;
                    helper.assertFalse(firedAlone[0],
                            "The kinetic trap fired with its owner gone; 1.16 needed its owner in the level");
                    helper.assertTrue(!set.isRemoved(),
                            "The kinetic trap went when its owner left; 1.16 kept it");
                    helper.assertTrue(stand.isRemoved(), "Fixture: Diver Down stayed without its user");
                    if (++ticks[0] < 5) {
                        helper.fail("Watching the trap with its owner gone");
                    }
                    back.setNoAi(true);
                    back.setNoGravity(true);
                    back.setUUID(ownerUuid);
                    back.moveTo(scene.origin.x, scene.origin.y, scene.origin.z, 0.0F, 0.0F);
                    helper.assertTrue(helper.getLevel().addFreshEntity(back), "Could not bring the owner back");
                    scene.extra.add(back);
                    returned[0] = true;
                    helper.fail("The owner has just come back");
                });
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16's client target (the vanilla pick) and the trap's precision ray clipped blocks by their outline
    // (BlockMode.OUTLINE): grass before a chest is what Steal Item meets.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_outline_steal",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void stealItemStopsAtGrass(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement);
            try {
                scene.look(0.0F);
                Ability steal = scene.power.getAbility(DiverDownAddon.STEAL_ITEM_NAME);
                scene.block(1.5D, 0, Blocks.DIRT.defaultBlockState());
                BlockPos grass = scene.block(1.5D, 1, Blocks.SHORT_GRASS.defaultBlockState());
                scene.block(3.0D, 1, Blocks.CHEST.defaultBlockState());
                helper.assertTrue(helper.getLevel().getBlockState(grass).is(Blocks.SHORT_GRASS),
                        "Fixture: the grass did not stay");
                helper.assertFalse(steal.checkConditions(scene.power).isPositive(),
                        "Steal Item reached a chest behind grass; 1.16's aim stopped at the grass");
                scene.block(grass, Blocks.AIR.defaultBlockState());
                helper.assertTrue(steal.checkConditions(scene.power).isPositive(),
                        "Fixture: Steal Item refused the chest without the grass");
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // A trap aimed at a flower takes the flower's block; its top is not sturdy, so the trap goes a block lower.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_outline_trap",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void kineticTrapAimedAtAFlowerTakesItsBlock(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement);
            try {
                scene.look(0.0F);
                Ability trap = scene.power.getAbility(DiverDownAddon.KINETIC_TRAP_NAME);
                scene.block(2.0D, 0, Blocks.DIRT.defaultBlockState());
                BlockPos flower = scene.block(2.0D, 1, Blocks.POPPY.defaultBlockState());
                BlockPos wall = scene.block(3.0D, 1, Blocks.STONE.defaultBlockState());
                helper.assertTrue(helper.getLevel().getBlockState(flower).is(Blocks.POPPY),
                        "Fixture: the flower did not stay");
                // a flower stands off its block's centre by a random offset; look through its middle
                Vec3 offset = helper.getLevel().getBlockState(flower).getOffset(helper.getLevel(), flower);
                scene.owner.moveTo(scene.origin.x + offset.x, scene.origin.y, scene.origin.z, 0.0F, 0.0F);
                KineticTrapEntity set = scene.setTrap(trap);
                Vec3 underFlower = Vec3.atBottomCenterOf(flower).add(0.0D, -0.5D, 0.0D);
                helper.assertTrue(set.position().equals(underFlower),
                        "The kinetic trap aimed at a flower is at " + set.position() + ", 1.16 put it at "
                                + underFlower + " (the wall behind is at " + Vec3.atCenterOf(wall) + ")");
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 DiverDownKineticTrap was a StandEntityHeavyAttack: its task kept the block it was pressed at, and
    // getOffsetFromUser put the Stand front3dOffset of it, withXRot(0, the horizontal distance from the user's feet to
    // the block's centre less 1.5, clamped to 0.5 and 2), turned by the user's view (lockOnTargetPosition false), not
    // toward the block. Pressed at stone 3 blocks south, the user turned west and up: 1.5 along the new view.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_trap_stand_offset",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void kineticTrapPutsTheStandAlongTheViewAtItsDistanceFromTheBlock(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement);
            try {
                scene.look(0.0F);
                Ability trap = scene.power.getAbility(DiverDownAddon.KINETIC_TRAP_NAME);
                BlockPos stone = scene.block(3.0D, 1, Blocks.STONE.defaultBlockState());
                DiverDownStandEntity stand = scene.stand;
                helper.assertTrue(stand.isFollowingUser() && !stand.isArmsOnlyMode(),
                        "Fixture: Diver Down does not follow its user in full");
                BufferingState buffering = BufferingState.clickOnly();
                trap.onKeyPress(helper.getLevel(), scene.owner, null, InputMethod.CLICK, 0.0F, buffering);
                EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
                helper.assertTrue(buffering.isActionSuccess && action instanceof KineticTrapAbility.KineticTrapAction
                                && action.getPhase() == ActionPhase.WINDUP,
                        "The kinetic trap did not start as a Stand action winding up; 1.16 made it a heavy attack");
                ActionTarget held = action.getTaskTarget(helper.getLevel());
                helper.assertTrue(held.getType() == ActionTarget.TargetType.BLOCK && stone.equals(held.getBlockPos()),
                        "1.16 kept the block the kinetic trap was pressed at as its task's target; it holds " + held);
                float yaw = 90.0F;
                float pitch = -20.0F;
                scene.owner.setYRot(yaw);
                scene.owner.setXRot(pitch);
                scene.owner.setYHeadRot(yaw);
                scene.owner.setYBodyRot(yaw);
                Vec3 centre = Vec3.atCenterOf(stone);
                double toBlock = centre.subtract(scene.owner.position()).multiply(1.0D, 0.0D, 1.0D).length();
                double front = Mth.clamp(toBlock - 1.5D, Math.min(0.5D, stand.getEffectiveRange()),
                        Math.min(2.0D, stand.getMaxRange()));
                helper.assertTrue(Math.abs(front - 1.5D) < 1.0E-6D,
                        "Fixture: the Stand's front offset " + front + " is clamped; the stone is " + toBlock + " away");
                Vec3 expected = new Vec3(0.0D, 0.0D, front)
                        .xRot(-pitch * Mth.DEG_TO_RAD)
                        .yRot(-yaw * Mth.DEG_TO_RAD);
                scene.poll(0, () -> {
                    helper.assertTrue(stand.getCurStandAction() == action && action.getPhase() == ActionPhase.WINDUP,
                            "Fixture: the kinetic trap is no longer winding up, phase " + action.getPhase());
                    stand.updatePosition(scene.owner);
                    Vec3 actual = stand.position().subtract(scene.owner.position());
                    helper.assertTrue(actual.distanceTo(expected) < 1.0E-4D,
                            "1.16 put Diver Down setting a kinetic trap at the stone 3 blocks south, its user looking"
                                    + " west and up, at its user's feet + " + expected + " (front3dOffset along the view);"
                                    + " got " + actual + " (offset " + stand.offsetFromUser.getRelativeOffset() + " "
                                    + stand.offsetFromUser.getRotations() + ")");
                });
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 StandEntity.tick ran updatePosition before it counted the summon lock down or ticked the task, and
    // updatePosition asked the kinetic trap's getOffsetFromUser (StandEntityHeavyAttack: front3dOffset of the block its
    // task kept) right before it placed the Stand: a user who stepped toward the block or away had Diver Down at its new
    // distance in that same tick, also while the lock held the task back.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_trap_offset_update",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void kineticTrapOffsetFollowsItsUserInTheStandsPositionUpdateAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement);
            try {
                scene.look(0.0F);
                Ability trap = scene.power.getAbility(DiverDownAddon.KINETIC_TRAP_NAME);
                BlockPos stone = scene.block(3.0D, 1, Blocks.STONE.defaultBlockState());
                DiverDownStandEntity stand = scene.stand;
                helper.assertTrue(stand.isFollowingUser() && !stand.isArmsOnlyMode(),
                        "Fixture: Diver Down does not follow its user in full");
                BufferingState buffering = BufferingState.clickOnly();
                trap.onKeyPress(helper.getLevel(), scene.owner, null, InputMethod.CLICK, 0.0F, buffering);
                EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
                helper.assertTrue(buffering.isActionSuccess && action instanceof KineticTrapAbility.KineticTrapAction
                                && action.getPhase() == ActionPhase.WINDUP
                                && stone.equals(action.getTaskTarget(helper.getLevel()).getBlockPos()),
                        "Fixture: the kinetic trap did not start winding up at the stone");

                // the user steps 0.5 toward the stone; the Stand's position update comes before the action's tick
                scene.owner.moveTo(scene.origin.x, scene.origin.y, scene.origin.z + 0.5D, 0.0F, 0.0F);
                expectTrapOffset(helper, scene, stand, stone,
                        "after its user stepped 0.5 toward the stone, before the action's tick");

                // the summon lock holds the trap at its first windup tick; the user steps back 0.3
                stand.summonLockTicks = 2;
                LivingComponentAction.getComponent(stand).tick();
                helper.assertTrue(stand.getCurStandAction() == action && action.getPhase() == ActionPhase.WINDUP
                                && action.getPhaseTick() == 0.0F,
                        "Fixture: the summon lock did not hold the kinetic trap back: " + action.getPhase()
                                + " tick " + action.getPhaseTick());
                scene.owner.moveTo(scene.origin.x, scene.origin.y, scene.origin.z + 0.2D, 0.0F, 0.0F);
                expectTrapOffset(helper, scene, stand, stone,
                        "held by the summon lock, its user stepped back to 0.2 (1.16 updatePosition ran through the"
                                + " lock)");
                stand.summonLockTicks = 0;
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.stand.summonLockTicks = 0;
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 front3dOffset: withXRot(0, the horizontal distance from the user's feet to the block's centre less 1.5,
    // clamped to 0.5 and 2), along the user's view (south here)
    private static void expectTrapOffset(GameTestHelper helper, Scene scene, DiverDownStandEntity stand,
            BlockPos block, String when) {
        double toBlock = Vec3.atCenterOf(block).subtract(scene.owner.position()).multiply(1.0D, 0.0D, 1.0D).length();
        double maxFront = Math.min(2.0D, stand.getMaxRange());
        double front = Mth.clamp(toBlock - 1.5D, Math.min(0.5D, stand.getEffectiveRange()), maxFront);
        helper.assertTrue(front > 0.5D && front < maxFront,
                "Fixture: the Stand's front offset " + front + " is clamped; the block is " + toBlock + " away");
        stand.updatePosition(scene.owner);
        Vec3 actual = stand.position().subtract(scene.owner.position());
        Vec3 expected = new Vec3(0.0D, 0.0D, front);
        helper.assertTrue(actual.distanceTo(expected) < 1.0E-4D,
                "1.16 put Diver Down setting a kinetic trap at its user's feet + " + expected + " " + when
                        + "; got " + actual + " (offset " + stand.offsetFromUser.getRelativeOffset() + " "
                        + stand.offsetFromUser.getRotations() + ")");
    }

    // 1.16 DiverDownKineticTrap.standPerform asked DiverDownEntity.aimWithThisOrUser(5, task target), which kept only an
    // entity target: the block it was pressed at gave way to the user's ray at the strike (DiverDownEntity.java:190-210).
    // A miss or a mob there set no trap; a manually controlled Stand, or one inside a target, aimed with its own ray;
    // and the Stand turned toward what it chose, by at most attack speed / 16 * 18 degrees.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_trap_reaim",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void kineticTrapAimsAgainWhenItStrikes(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 8, placement -> {
            Scene scene = new Scene(helper, placement);
            try {
                scene.look(0.0F);
                Ability trap = scene.power.getAbility(DiverDownAddon.KINETIC_TRAP_NAME);
                DiverDownStandEntity stand = scene.stand;
                Vec3 standHome = stand.position();
                BlockPos pressed = scene.block(3.0D, 1, Blocks.STONE.defaultBlockState());
                BlockPos west = BlockPos.containing(scene.origin.x - 3.0D, scene.origin.y + 1.0D, scene.origin.z);
                scene.block(west, Blocks.STONE.defaultBlockState());

                // pressed at the stone south, the user turned to the stone west before the strike
                List<KineticTrapEntity> turned = scene.strike(trap, () -> {
                    scene.standFacing(standHome, 0.0F);
                    scene.turn(90.0F, 0.0F);
                });
                helper.assertTrue(turned.size() == 1 && turned.get(0).blockPosition().equals(west),
                        "1.16 aimWithThisOrUser re-aimed at the strike: the trap goes into the stone the user looks at"
                                + " then (" + west + "), not the one pressed at (" + pressed + "); got "
                                + turned.stream().map(Entity::blockPosition).toList());
                Vec3 toWest = Vec3.atCenterOf(west).subtract(stand.getEyePosition());
                float max = (float) stand.getAttackSpeed() / 16.0F * 18.0F;
                float yaw = -(float) Mth.atan2(toWest.x, toWest.z) * Mth.RAD_TO_DEG;
                float pitch = -(float) Mth.atan2(toWest.y, toWest.horizontalDistance()) * Mth.RAD_TO_DEG;
                float expectedYaw = Mth.clamp(Mth.degreesDifference(0.0F, yaw), -max, max);
                float expectedPitch = Mth.clamp(pitch, -max, max);
                helper.assertTrue(max < 89.0F && Math.abs(stand.getYRot() - expectedYaw) < 1.0E-3F
                                && Math.abs(stand.getYHeadRot() - expectedYaw) < 1.0E-3F
                                && Math.abs(stand.getXRot() - expectedPitch) < 1.0E-3F,
                        "1.16 MCUtil.rotateTowards turned Diver Down from yaw 0 toward the stone west by at most "
                                + max + " degrees: yaw " + expectedYaw + ", pitch " + expectedPitch + "; got yaw "
                                + stand.getYRot() + ", head " + stand.getYHeadRot() + ", pitch " + stand.getXRot());

                // the user looks up at the strike: the miss stays a miss, the Stand's own ray is not asked
                List<KineticTrapEntity> sky = scene.strike(trap, () -> {
                    scene.standFacing(standHome, 0.0F);
                    scene.turn(0.0F, -90.0F);
                });
                helper.assertTrue(sky.isEmpty(),
                        "1.16 set no trap when the user's ray met nothing at the strike; got "
                                + sky.stream().map(Entity::blockPosition).toList());

                // a cow steps into the user's ray before the strike
                Cow[] stepped = new Cow[1];
                List<KineticTrapEntity> mob = scene.strike(trap, () -> {
                    scene.standFacing(standHome, 0.0F);
                    scene.turn(0.0F, 0.0F);
                    stepped[0] = scene.mob(1.8D, 0.0D);
                });
                helper.assertTrue(mob.isEmpty(),
                        "1.16 set no trap when the user's ray met a mob at the strike; got "
                                + mob.stream().map(Entity::blockPosition).toList());
                stepped[0].discard();

                // a manually controlled Stand 3 blocks east, facing south, aims with its own ray
                Vec3 east = standHome.add(3.0D, 0.0D, 0.0D);
                BlockPos[] own = new BlockPos[1];
                List<KineticTrapEntity> manual = scene.strike(trap, () -> {
                    scene.turn(0.0F, 0.0F);
                    stand.setManuallyControlled(true);
                    scene.standFacing(east, 0.0F);
                    own[0] = BlockPos.containing(east.x, stand.getEyeY(), east.z + 2.0D);
                    scene.block(own[0], Blocks.STONE.defaultBlockState());
                });
                stand.setManuallyControlled(false);
                helper.assertTrue(manual.size() == 1 && manual.get(0).blockPosition().equals(own[0]),
                        "1.16 aimed a manually controlled Diver Down with its own ray, not its user's: the trap goes"
                                + " into " + own[0] + "; got " + manual.stream().map(Entity::blockPosition).toList());

                // inside a cow 3 blocks east, facing south: the Stand's own precision ray (a mob user has the aim
                // assist), from inside the host's box grown by the Stand's precision, meets the host first
                Cow host = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(host != null, "Could not create a cow");
                scene.prepareMob(host, east);
                List<KineticTrapEntity> inside = scene.strike(trap, () -> {
                    scene.turn(0.0F, 0.0F);
                    scene.standFacing(east, 0.0F);
                    helper.assertTrue(stand.setEmbeddedTarget(host), "Fixture: Diver Down did not go inside the cow");
                });
                stand.clearEmbeddedTarget();
                helper.assertTrue(inside.isEmpty(),
                        "1.16 aimed Diver Down inside a target (isInside) with its own precisionRayTrace, which took the"
                                + " host whose grown hitbox holds the Stand's eyes: no trap, not one in " + own[0]
                                + " or the stone its user looks at; got "
                                + inside.stream().map(Entity::blockPosition).toList());
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // The re-aim at the strike follows the user's aim assist setting (owner, 2026-10-16): on, 1.16's precisionRayTrace
    // (StandEntity.java:1736-1770: hitboxes grown by the Stand's precision, players and hostile mobs first), so a cow
    // just beside the crosshair takes the strike and no trap is set; a cow behind the aimed stone stays hidden by it,
    // the walls rule of every unmarked skill. Off, the crosshair only: the stone. Inside a target the Stand's own ray.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_trap_reaim_assist",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void kineticTrapReAimFollowsTheAimAssist(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 8, placement -> {
            Scene scene = new Scene(helper, placement, true);
            try {
                scene.look(0.0F);
                Ability trap = scene.power.getAbility(DiverDownAddon.KINETIC_TRAP_NAME);
                DiverDownStandEntity stand = scene.stand;
                Vec3 standHome = stand.position();
                PlayerClientBroadcastedSettings settings = ((ServerPlayer) scene.owner)
                        .getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get());
                BlockPos far = scene.block(4.0D, 1, Blocks.STONE.defaultBlockState());
                Cow beside = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(beside != null, "Could not create a cow");
                scene.prepareMob(beside, new Vec3(scene.origin.x + 0.9D, scene.origin.y, scene.origin.z + 2.5D));
                Vec3 eyes = scene.owner.getEyePosition();
                helper.assertTrue(beside.getBoundingBox().clip(eyes, eyes.add(0.0D, 0.0D, 5.0D)).isEmpty(),
                        "Fixture: the cow beside the crosshair is on it");
                Runnable aimSouth = () -> {
                    scene.standFacing(standHome, 0.0F);
                    scene.turn(0.0F, 0.0F);
                };

                settings.standAimAssist = true;
                List<KineticTrapEntity> assisted = scene.strike(trap, aimSouth);
                helper.assertTrue(assisted.isEmpty(),
                        "With the aim assist, 1.16's precision ray met the cow 0.9 blocks beside the crosshair (its"
                                + " hitbox grown by Diver Down's precision) before the stone at " + far
                                + ": no trap; got " + assisted.stream().map(Entity::blockPosition).toList());
                settings.standAimAssist = false;
                List<KineticTrapEntity> crosshair = scene.strike(trap, aimSouth);
                helper.assertTrue(crosshair.size() == 1 && crosshair.get(0).blockPosition().equals(far),
                        "Without the aim assist the crosshair only: the trap goes into the stone at " + far
                                + ", the cow beside the crosshair does not count; got "
                                + crosshair.stream().map(Entity::blockPosition).toList());
                beside.discard();

                // a cow behind a nearer stone, on the crosshair
                BlockPos near = scene.block(2.0D, 1, Blocks.STONE.defaultBlockState());
                scene.mob(3.5D, 0.5D);
                settings.standAimAssist = true;
                List<KineticTrapEntity> walled = scene.strike(trap, aimSouth);
                helper.assertTrue(walled.size() == 1 && walled.get(0).blockPosition().equals(near),
                        "With the aim assist, the stone at " + near + " hides the cow behind it (walls rule of an"
                                + " unmarked skill): the trap goes into the stone; got "
                                + walled.stream().map(Entity::blockPosition).toList());

                // inside a cow 3 blocks east, facing south, without the aim assist: the Stand's own crosshair
                settings.standAimAssist = false;
                Vec3 east = standHome.add(3.0D, 0.0D, 0.0D);
                Cow host = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(host != null, "Could not create a cow");
                scene.prepareMob(host, east);
                BlockPos[] own = new BlockPos[1];
                List<KineticTrapEntity> inside = scene.strike(trap, () -> {
                    scene.turn(0.0F, 0.0F);
                    scene.standFacing(east, 0.0F);
                    own[0] = BlockPos.containing(east.x, stand.getEyeY(), east.z + 2.0D);
                    scene.block(own[0], Blocks.STONE.defaultBlockState());
                    helper.assertTrue(stand.setEmbeddedTarget(host), "Fixture: Diver Down did not go inside the cow");
                });
                stand.clearEmbeddedTarget();
                helper.assertTrue(inside.size() == 1 && inside.get(0).blockPosition().equals(own[0]),
                        "1.16 aimed Diver Down inside a target (isInside) with its own ray; without the aim assist its"
                                + " crosshair passes out of the host: the trap goes into " + own[0]
                                + ", not the stone its user looks at; got "
                                + inside.stream().map(Entity::blockPosition).toList());
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 DiverDownInsideProtection (holdType) answered checkStandConditions with dd_notinside on every held tick
    // (PowerBaseImpl.tickHeldAction): a Stand that left its target ended the hold with that message.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_protection_held",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void insideProtectionStopsWithItsMessageOnceDiverDownLeavesItsTarget(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement);
            short key = 92;
            try {
                scene.look(0.0F);
                Cow host = scene.mob(2.0D, 0.0D);
                helper.assertTrue(scene.stand.setEmbeddedTarget(host), "Fixture: Diver Down did not go inside the cow");
                Ability protection = scene.power.getAbility(DiverDownAddon.INSIDE_PROTECTION_NAME);
                BufferingState buffering = BufferingState.clickOnly();
                AbilityInput.keyPress(key, protection, scene.owner, null, InputMethod.HOLD, 0.0F, buffering,
                        protection.getAbilityId());
                EntityActionInstance action = scene.stand.getCurStandAction();
                helper.assertTrue(buffering.isActionSuccess && action != null && action.ability == protection,
                        "Fixture: Inside Protection did not start; got " + action);
                EntityActionAbility held = (EntityActionAbility) protection;
                ConditionCheck inside = held.checkHeldActionConditions(action, scene.power);
                helper.assertTrue(inside.isPositive(),
                        "Fixture: the held protection inside the cow was refused: " + inside.getWarning());
                scene.stand.clearEmbeddedTarget();
                ConditionCheck left = held.checkHeldActionConditions(action, scene.power);
                helper.assertTrue(!left.isPositive() && !left.shouldContinueHold() && left.getWarning() != null
                                && left.getWarning().getContents() instanceof TranslatableContents contents
                                && contents.getKey().equals("jojo.message.action_condition.diver_down_not_embedded"),
                        "1.16 answered the held Inside Protection once Diver Down left its target with dd_notinside,"
                                + " which ended the hold; the port answered "
                                + (left.isPositive() ? "a positive check" : String.valueOf(left.getWarning())));
                AbilityInput.keyRelease(key, scene.owner);
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                AbilityInput.keyRelease(key, scene.owner);
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 DiverDownWallGlide (holdType, InitStands :70) required no Stand part, and its checkSpecificConditions gave
    // NEGATIVE_CONTINUE_HOLD, without a message, while Diver Down was inside a target (isInside): the hold went on and
    // the user did not glide until the Stand came out.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_glide_inside",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void wallGlideNeedsNoPartAndPausesWhileDiverDownIsInside(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement);
            short key = 93;
            try {
                scene.look(0.0F);
                Ability glide = scene.power.getAbility(DiverDownAddon.WALL_GLIDE_NAME);
                BlockPos wall = scene.wall(1.0D, 0);
                scene.wall(1.0D, 1);
                scene.owner.setPos(scene.origin.x, scene.origin.y,
                        wall.getZ() - scene.owner.getBbWidth() / 2.0D - 0.005D);
                var instance = scene.power.getStandInstance().orElseThrow();
                helper.assertTrue(instance.removePart(StandPart.MAIN_BODY), "Fixture: the Stand had no main body");
                scene.cleanup.add(() -> instance.addPart(StandPart.MAIN_BODY));
                ConditionCheck noBody = glide.checkConditions(scene.power);
                helper.assertTrue(noBody.isPositive(),
                        "1.16 Wall Glide required no Stand part: a Stand without its main body glides on a wall; got "
                                + (noBody.getWarning() != null ? noBody.getWarning() : "a silent refusal"));
                instance.addPart(StandPart.MAIN_BODY);

                Cow host = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(host != null, "Could not create a cow");
                scene.prepareMob(host, scene.stand.position().add(2.0D, 0.0D, 0.0D));
                helper.assertTrue(scene.stand.setEmbeddedTarget(host), "Fixture: Diver Down did not go inside the cow");
                ConditionCheck inside = glide.checkConditions(scene.power);
                helper.assertTrue(!inside.isPositive() && inside.shouldContinueHold() && inside.getWarning() == null,
                        "1.16 answered Wall Glide on a wall with Diver Down inside a target with NEGATIVE_CONTINUE_HOLD"
                                + " (no message); the port answered " + (inside.isPositive() ? "a positive check"
                                        : inside.getWarning() + ", continue hold " + inside.shouldContinueHold()));
                BufferingState buffering = BufferingState.clickOnly();
                AbilityInput.keyPress(key, glide, scene.owner, null, InputMethod.HOLD, 0.0F, buffering,
                        glide.getAbilityId());
                EntityActionInstance action = scene.stand.getCurStandAction();
                helper.assertTrue(buffering.isActionSuccess && action != null && action.ability == glide,
                        "Fixture: the held Wall Glide did not start; got " + action);
                scene.owner.setDeltaMovement(Vec3.ZERO);
                for (int tick = 0; tick < 3; tick++) {
                    scene.stand.getStandActionComponent().tick();
                }
                Vec3 whileInside = scene.owner.getDeltaMovement();
                helper.assertTrue(!action.isOver() && action.getPhase() == ActionPhase.PERFORM
                                && whileInside.lengthSqr() < 1.0E-6D,
                        "1.16 kept the Wall Glide held but did not move its user while Diver Down was inside a"
                                + " target; over " + action.isOver() + ", phase " + action.getPhase()
                                + ", motion " + whileInside);
                scene.stand.clearEmbeddedTarget();
                scene.stand.getStandActionComponent().tick();
                Vec3 out = scene.owner.getDeltaMovement();
                helper.assertTrue(!action.isOver() && out.z > 0.3D,
                        "Fixture: the held Wall Glide did not glide once Diver Down came out; over "
                                + action.isOver() + ", motion " + out);
                AbilityInput.keyRelease(key, scene.owner);
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                AbilityInput.keyRelease(key, scene.owner);
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 DiverDownStealItem.checkSpecificConditions, on the press and every held tick: a block without a block entity
    // or nothing aimed gave NEGATIVE, no message; a block entity without an inventory gave dd_target_no_inv. The owner
    // kept the port's dd_target_no_inv for every refusal (2026-10-19), as the port showed it before wave 21.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_steal_held_message",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void stealItemAnswersNoInventoryWithoutABlockEntity(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement);
            short key = 94;
            try {
                scene.look(0.0F);
                Ability steal = scene.power.getAbility(DiverDownAddon.STEAL_ITEM_NAME);
                BlockPos aimed = scene.block(2.0D, 1, Blocks.CHEST.defaultBlockState());
                BufferingState buffering = BufferingState.clickOnly();
                AbilityInput.keyPress(key, steal, scene.owner, null, InputMethod.HOLD, 0.0F, buffering,
                        steal.getAbilityId());
                EntityActionInstance action = LivingComponentAction.getCurEntityAction(scene.owner, PowerClass.STAND);
                helper.assertTrue(buffering.isActionSuccess && action != null && action.ability == steal,
                        "Fixture: Steal Item did not start its charge at the chest; got " + action);
                EntityActionAbility held = (EntityActionAbility) steal;
                scene.block(aimed, Blocks.STONE.defaultBlockState());
                ConditionCheck stone = held.checkHeldActionConditions(action, scene.power);
                helper.assertTrue(scene.isNoInventory(stone),
                        "The owner kept dd_target_no_inv for a held Steal Item whose crosshair met stone (no block"
                                + " entity); the port answered " + scene.answer(stone));
                scene.block(aimed, Blocks.ENCHANTING_TABLE.defaultBlockState());
                ConditionCheck table = held.checkHeldActionConditions(action, scene.power);
                helper.assertTrue(!table.isPositive() && table.getWarning() != null
                                && table.getWarning().getContents() instanceof TranslatableContents contents
                                && contents.getKey().equals("jojo.message.action_condition.dd_target_no_inv"),
                        "1.16 answered a held Steal Item at an enchanting table (a block entity without an"
                                + " inventory) with dd_target_no_inv; the port answered "
                                + (table.isPositive() ? "a positive check" : String.valueOf(table.getWarning())));
                AbilityInput.keyRelease(key, scene.owner);
                scene.look(-90.0F);
                ConditionCheck sky = steal.checkConditions(scene.power);
                helper.assertTrue(scene.isNoInventory(sky),
                        "The owner kept dd_target_no_inv for Steal Item pressed at the sky; the port answered "
                                + scene.answer(sky));
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                AbilityInput.keyRelease(key, scene.owner);
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 Steal Item was holdToFire(15, false) with TargetRequirement.ANY; PowerBaseImpl.tickHeldAction checked the
    // crosshair target on every held tick, so a charge whose crosshair left the chest stopped unfired: no steal, no
    // stamina, no cooldown.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_steal_held_aim",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void stealItemStopsWhenItsAimLeavesTheInventory(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement);
            short key = 91;
            try {
                scene.look(0.0F);
                Ability steal = scene.power.getAbility(DiverDownAddon.STEAL_ITEM_NAME);
                scene.block(3.0D, 1, Blocks.CHEST.defaultBlockState());
                float stamina = scene.power.getStamina();
                helper.assertTrue(steal.checkConditions(scene.power).isPositive(),
                        "Fixture: Steal Item refused the chest");
                BufferingState buffering = BufferingState.clickOnly();
                AbilityInput.keyPress(key, steal, scene.owner, null, InputMethod.HOLD, 0.0F, buffering,
                        steal.getAbilityId());
                EntityActionInstance action = LivingComponentAction.getCurEntityAction(scene.owner, PowerClass.STAND);
                helper.assertTrue(buffering.isActionSuccess && action != null && action.ability == steal
                                && action.getPhase() == ActionPhase.BUTTON_CHARGE,
                        "Fixture: Steal Item did not start its 15-tick charge; got " + action);
                for (int tick = 0; tick < 2; tick++) {
                    LivingComponentAction.getComponent(scene.owner, PowerClass.STAND).tick();
                }
                helper.assertTrue(!action.isOver(), "Fixture: the charge at the chest stopped by itself");
                scene.look(-90.0F);
                for (int tick = 0; tick < 2; tick++) {
                    LivingComponentAction.getComponent(scene.owner, PowerClass.STAND).tick();
                }
                helper.assertTrue(action.isOver() && !scene.power.isAbilityOnCooldown(steal.name())
                                && scene.power.getStamina() == stamina,
                        "1.16 tickHeldAction stopped the held Steal Item once its crosshair left the chest (ANY);"
                                + " over " + action.isOver() + ", cooldown "
                                + scene.power.isAbilityOnCooldown(steal.name()) + ", stamina "
                                + scene.power.getStamina());
                AbilityInput.keyRelease(key, scene.owner);
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                AbilityInput.keyRelease(key, scene.owner);
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 DiverDownStealItem.perform 108-110 stole only for a player user; a mob user still paid the cost.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_steal_player_user",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void stealItemStealsOnlyForAPlayer(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement);
            try {
                scene.look(0.0F);
                Ability steal = scene.power.getAbility(DiverDownAddon.STEAL_ITEM_NAME);
                Villager villager = EntityType.VILLAGER.create(helper.getLevel());
                helper.assertTrue(villager != null, "Could not create a villager");
                villager.setNoAi(true);
                villager.setNoGravity(true);
                villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
                villager.moveTo(scene.origin.x, scene.origin.y, scene.origin.z + 2.5D, 0.0F, 0.0F);
                helper.assertTrue(helper.getLevel().addFreshEntity(villager), "Could not add a villager");
                scene.extra.add(villager);
                helper.assertTrue(!villager.getOffers().isEmpty(), "Fixture: the farmer has no offers");
                helper.assertTrue(steal.checkConditions(scene.power).isPositive(),
                        "Fixture: Steal Item refused the farmer");
                EntityActionInstance action = ((EntityActionType) steal).initActionOnAbilityUse(
                        helper.getLevel(), scene.owner, scene.owner, null);
                LivingComponentAction.getComponent(scene.owner, PowerClass.STAND)
                        .setAction(action, scene.owner, SyncType.NO_SYNC);
                helper.assertTrue(LivingComponentAction.getCurEntityAction(scene.owner, PowerClass.STAND) == action,
                        "Fixture: the steal did not start");
                float stamina = scene.power.getStamina();
                action.actionPerformStart();
                helper.assertTrue(scene.power.getStamina() < stamina,
                        "Fixture: the steal by a mob user did not reach its perform (no cost paid)");
                int uses = 0;
                for (MerchantOffer offer : villager.getOffers()) {
                    uses += offer.getUses();
                }
                List<ItemEntity> items = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                        scene.owner.getBoundingBox().inflate(4.0D));
                scene.extra.addAll(items);
                helper.assertTrue(uses == 0 && items.isEmpty(),
                        "Steal Item stole for a mob user (" + uses + " offer uses, " + items.size()
                                + " items dropped); 1.16 stole only for a player");
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 DiverDownStealItem 80-101 took the client's target: an entity before a chest that is neither a player nor
    // a merchant refuses the steal, and a wall before a merchant is the target (walls stop Steal Item).
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_steal_target",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void stealTakesWhatTheAimMeetsFirst(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement);
            try {
                scene.look(0.0F);
                Ability steal = scene.power.getAbility(DiverDownAddon.STEAL_ITEM_NAME);
                BlockPos chest = scene.block(3.0D, 1, Blocks.CHEST.defaultBlockState());
                helper.assertTrue(steal.checkConditions(scene.power).isPositive(),
                        "Fixture: Steal Item refused a bare chest");
                Cow cow = scene.mob(1.8D, 0.0D);
                helper.assertFalse(steal.checkConditions(scene.power).isPositive(),
                        "Steal Item looked past a cow to the chest behind it; 1.16 refused (dd_target_no_inv)");
                cow.discard();
                scene.block(chest, Blocks.AIR.defaultBlockState());

                Villager villager = EntityType.VILLAGER.create(helper.getLevel());
                helper.assertTrue(villager != null, "Could not create a villager");
                villager.setNoAi(true);
                villager.setNoGravity(true);
                villager.moveTo(scene.origin.x, scene.origin.y, scene.origin.z + 2.5D, 0.0F, 0.0F);
                helper.assertTrue(helper.getLevel().addFreshEntity(villager), "Could not add a villager");
                scene.extra.add(villager);
                helper.assertTrue(steal.checkConditions(scene.power).isPositive(),
                        "Fixture: Steal Item refused a villager in the open");
                scene.wall(1.0D, 1);
                helper.assertFalse(steal.checkConditions(scene.power).isPositive(),
                        "Steal Item reached a villager behind a stone wall: walls stop it");
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 DiverDownStealItem was a StandAction (InitStands :77): its user held and fired it with Diver Down
    // unsummoned, and it did not summon the Stand.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_steal_unsummoned",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void stealItemNeedsNoSummonedDiverDownAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement, true);
            short key = 96;
            try {
                scene.look(0.0F);
                Ability steal = scene.power.getAbility(DiverDownAddon.STEAL_ITEM_NAME);
                BlockPos chestPos = scene.block(2.0D, 1, Blocks.CHEST.defaultBlockState());
                helper.assertTrue(helper.getLevel().getBlockEntity(chestPos) instanceof ChestBlockEntity,
                        "Fixture: the chest has no block entity");
                ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(chestPos);
                chest.setItem(0, new ItemStack(Items.DIAMOND, 5));
                scene.power.getPowerType().forceUnsummon(scene.owner, scene.power);
                helper.assertTrue(!scene.power.isSummoned() && DiverDownAddon.activeStand(scene.owner) == null,
                        "Fixture: Diver Down is still summoned");
                ConditionCheck press = steal.checkConditions(scene.power);
                helper.assertTrue(press.isPositive(),
                        "1.16 let Steal Item, a StandAction, be pressed with Diver Down unsummoned; the port answered "
                                + (press.getWarning() != null ? press.getWarning().getString() : "a silent refusal"));
                BufferingState buffering = BufferingState.clickOnly();
                AbilityInput.keyPress(key, steal, scene.owner, null, InputMethod.HOLD, 0.0F, buffering,
                        steal.getAbilityId());
                EntityActionInstance action = LivingComponentAction.getCurEntityAction(scene.owner, PowerClass.STAND);
                helper.assertTrue(buffering.isActionSuccess && action != null && action.ability == steal
                                && action.getPhase() == ActionPhase.BUTTON_CHARGE,
                        "1.16 started the Steal Item charge of a user whose Diver Down is not summoned; got " + action);
                ServerPlayer player = (ServerPlayer) scene.owner;
                scene.cleanup.add(() -> AbilityInput.keyRelease(key, scene.owner));
                scene.poll(0, () -> {
                    helper.assertTrue(action.isOver() || action.getPhase() != ActionPhase.BUTTON_CHARGE,
                            "The Steal Item charge has not fired yet: " + action.getPhase());
                    helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 5
                                    && chest.getItem(0).isEmpty()
                                    && scene.power.isAbilityOnCooldown(steal.name()),
                            "1.16 stole the chest's diamonds for a user whose Diver Down is not summoned; diamonds "
                                    + player.getInventory().countItem(Items.DIAMOND) + ", chest " + chest.getItem(0)
                                    + ", cooldown " + scene.power.isAbilityOnCooldown(steal.name()));
                    helper.assertTrue(!scene.power.isSummoned(), "1.16 Steal Item never summoned Diver Down");
                });
            } catch (Throwable error) {
                AbilityInput.keyRelease(key, scene.owner);
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 DiverDownStealItem.checkSpecificConditions :75 (hasTileEntity): a block without a block entity was refused,
    // though it has an inventory as a composter does (with the owner's kept dd_target_no_inv). A mob or player is aimed
    // at with Diver Down unsummoned too.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_steal_block_entity",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void stealItemTakesOnlyBlockEntitiesAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement);
            try {
                scene.look(0.0F);
                Ability steal = scene.power.getAbility(DiverDownAddon.STEAL_ITEM_NAME);
                BlockPos aimed = scene.block(2.0D, 1, Blocks.COMPOSTER.defaultBlockState());
                helper.assertTrue(helper.getLevel().getBlockEntity(aimed) == null
                                && (helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, aimed,
                                                Direction.NORTH) != null
                                        || helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, aimed,
                                                null) != null),
                        "Fixture: the composter has a block entity or no item handler");
                ConditionCheck composter = steal.checkConditions(scene.power);
                helper.assertTrue(scene.isNoInventory(composter),
                        "1.16 refused Steal Item at a composter (an inventory without a block entity), with the owner's"
                                + " kept dd_target_no_inv; the port answered " + scene.answer(composter));
                scene.block(aimed, Blocks.BARREL.defaultBlockState());
                helper.assertTrue(steal.checkConditions(scene.power).isPositive(),
                        "Fixture: Steal Item refused a barrel");
                scene.block(aimed, Blocks.AIR.defaultBlockState());
                scene.power.getPowerType().forceUnsummon(scene.owner, scene.power);
                helper.assertTrue(!scene.power.isSummoned(), "Fixture: Diver Down is still summoned");
                Villager villager = EntityType.VILLAGER.create(helper.getLevel());
                helper.assertTrue(villager != null, "Could not create a villager");
                villager.setNoAi(true);
                villager.setNoGravity(true);
                villager.moveTo(scene.origin.x, scene.origin.y, scene.origin.z + 2.5D, 0.0F, 0.0F);
                helper.assertTrue(helper.getLevel().addFreshEntity(villager), "Could not add a villager");
                scene.extra.add(villager);
                ConditionCheck merchant = steal.checkConditions(scene.power);
                helper.assertTrue(merchant.isPositive(),
                        "1.16 let Steal Item take a villager with Diver Down unsummoned; the port answered "
                                + (merchant.getWarning() != null ? merchant.getWarning().getString()
                                        : "a silent refusal"));
                villager.discard();
                scene.mob(2.0D, 0.0D);
                ConditionCheck cow = steal.checkConditions(scene.power);
                helper.assertTrue(!cow.isPositive() && cow.getWarning() != null
                                && cow.getWarning().getContents() instanceof TranslatableContents contents
                                && contents.getKey().equals("jojo.message.action_condition.dd_target_no_inv"),
                        "1.16 answered Steal Item at a cow, Diver Down unsummoned, with dd_target_no_inv; the port"
                                + " answered " + (cow.isPositive() ? "a positive check" : String.valueOf(cow.getWarning())));
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 DiverDownStealItem.checkSpecificConditions :91-99 took any player or merchant under the crosshair: the client's
    // vanilla pick, which skips spectators and the user's own Stand and stops at any other pickable entity. No team,
    // creative, invulnerability or PvP rule applied (DiverDownAuthority.canAffect is for Diver Down's other abilities).
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_steal_any_target",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void stealItemTakesAnyPlayerOrMerchantUnderTheCrosshairAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement, true);
            try {
                scene.look(0.0F);
                Ability steal = scene.power.getAbility(DiverDownAddon.STEAL_ITEM_NAME);
                ServerPlayer user = (ServerPlayer) scene.owner;
                boolean pvpBefore = helper.getLevel().getServer().isPvpAllowed();
                scene.cleanup.add(() -> helper.getLevel().getServer().setPvpAllowed(pvpBefore));
                helper.getLevel().getServer().setPvpAllowed(true);

                ServerPlayer creative = scene.targetPlayer("dd-steal-creative", GameType.CREATIVE);
                creative.getInventory().setItem(0, new ItemStack(Items.APPLE, 3));
                scene.expectPress(steal, "a creative player");
                scene.fireSteal(steal);
                helper.assertTrue(user.getInventory().countItem(Items.APPLE) == 3
                                && creative.getInventory().countItem(Items.APPLE) == 0,
                        "1.16 stole a creative player's first stack; the user got "
                                + user.getInventory().countItem(Items.APPLE) + " apples, the player kept "
                                + creative.getInventory().countItem(Items.APPLE));
                scene.moveAside(creative);

                scene.rearm(steal);
                ServerPlayer pvp = scene.targetPlayer("dd-steal-pvp", GameType.SURVIVAL);
                pvp.getInventory().setItem(0, new ItemStack(Items.CARROT, 2));
                helper.getLevel().getServer().setPvpAllowed(false);
                scene.expectPress(steal, "a player while PvP is off");
                scene.fireSteal(steal);
                helper.assertTrue(user.getInventory().countItem(Items.CARROT) == 2,
                        "1.16 stole from a player while PvP is off; the user got "
                                + user.getInventory().countItem(Items.CARROT) + " carrots");
                helper.getLevel().getServer().setPvpAllowed(true);
                scene.moveAside(pvp);

                scene.rearm(steal);
                ServerPlayer mate = scene.targetPlayer("dd-steal-mate", GameType.SURVIVAL);
                mate.getInventory().setItem(0, new ItemStack(Items.BREAD, 4));
                Scoreboard board = helper.getLevel().getScoreboard();
                PlayerTeam team = board.addPlayerTeam("dd_steal_mates");
                scene.cleanup.add(() -> board.removePlayerTeam(team));
                team.setAllowFriendlyFire(false);
                board.addPlayerToTeam(user.getScoreboardName(), team);
                board.addPlayerToTeam(mate.getScoreboardName(), team);
                scene.expectPress(steal, "a teammate without friendly fire");
                scene.fireSteal(steal);
                helper.assertTrue(user.getInventory().countItem(Items.BREAD) == 4,
                        "1.16 stole from a teammate without friendly fire; the user got "
                                + user.getInventory().countItem(Items.BREAD) + " bread");
                scene.moveAside(mate);

                scene.rearm(steal);
                Villager villager = scene.farmer(3.0D);
                villager.setInvulnerable(true);
                ServerPlayer spectator = scene.targetPlayer("dd-steal-spectator", GameType.SPECTATOR);
                spectator.getInventory().setItem(0, new ItemStack(Items.APPLE, 7));
                scene.expectPress(steal, "an invulnerable merchant behind a spectator");
                scene.fireSteal(steal);
                int uses = 0;
                for (MerchantOffer offer : villager.getOffers()) {
                    uses += offer.getUses();
                }
                helper.assertTrue(uses > 0 && spectator.getInventory().countItem(Items.APPLE) == 7,
                        "1.16's crosshair skipped a spectator and took the invulnerable merchant behind it; offer uses "
                                + uses + ", the spectator kept " + spectator.getInventory().countItem(Items.APPLE)
                                + " of 7 apples");
                scene.moveAside(spectator);
                villager.discard();

                scene.rearm(steal);
                BlockPos chest = scene.block(3.0D, 1, Blocks.CHEST.defaultBlockState());
                Boat boat = EntityType.BOAT.create(helper.getLevel());
                helper.assertTrue(boat != null, "Could not create a boat");
                boat.setNoGravity(true);
                boat.moveTo(scene.origin.x, scene.origin.y + 1.2D, scene.origin.z + 2.0D, 0.0F, 0.0F);
                helper.assertTrue(helper.getLevel().addFreshEntity(boat), "Could not add a boat");
                scene.extra.add(boat);
                ConditionCheck boatBefore = steal.checkConditions(scene.power);
                helper.assertTrue(!boatBefore.isPositive() && boatBefore.getWarning() != null
                                && boatBefore.getWarning().getContents() instanceof TranslatableContents contents
                                && contents.getKey().equals("jojo.message.action_condition.dd_target_no_inv"),
                        "1.16's crosshair stopped at a boat before the chest (dd_target_no_inv); the port answered "
                                + scene.answer(boatBefore));
                boat.discard();

                scene.stand.moveTo(scene.origin.x, scene.origin.y, scene.origin.z + 1.5D, 0.0F, 0.0F);
                helper.assertTrue(helper.getLevel().getBlockState(chest).is(Blocks.CHEST),
                        "Fixture: the chest did not stay");
                scene.expectPress(steal, "the chest behind the user's own Stand");
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 DiverDownStealItem.perform :112-117 (stealItemFromCap): the block entity's item handler without a side, the
    // first slot that holds anything, 64 at most. A furnace hit on a side gave its fuel slot to a sided handler.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_steal_handler",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void stealItemTakesTheFirstFilledSlotOfTheSideLessHandlerAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement, true);
            try {
                scene.look(0.0F);
                Ability steal = scene.power.getAbility(DiverDownAddon.STEAL_ITEM_NAME);
                ServerPlayer user = (ServerPlayer) scene.owner;
                BlockPos pos = scene.block(2.0D, 1, Blocks.FURNACE.defaultBlockState());
                helper.assertTrue(helper.getLevel().getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity,
                        "Fixture: the furnace has no block entity");
                AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) helper.getLevel().getBlockEntity(pos);
                furnace.setItem(0, new ItemStack(Items.DIAMOND, 4));
                furnace.setItem(1, new ItemStack(Items.COAL, 2));
                furnace.setItem(2, new ItemStack(Items.IRON_INGOT, 3));
                scene.expectPress(steal, "a furnace hit on its side");
                scene.fireSteal(steal);
                helper.assertTrue(user.getInventory().countItem(Items.DIAMOND) == 4
                                && furnace.getItem(0).isEmpty() && furnace.getItem(1).getCount() == 2,
                        "1.16 took the first filled slot of the furnace (its input), not the fuel its side reaches;"
                                + " the user got " + user.getInventory().countItem(Items.DIAMOND) + " diamonds and "
                                + user.getInventory().countItem(Items.COAL) + " coal");

                scene.rearm(steal);
                scene.block(pos, Blocks.HOPPER.defaultBlockState());
                helper.assertTrue(helper.getLevel().getBlockEntity(pos) instanceof HopperBlockEntity,
                        "Fixture: the hopper has no block entity");
                HopperBlockEntity hopper = (HopperBlockEntity) helper.getLevel().getBlockEntity(pos);
                hopper.setItem(3, new ItemStack(Items.APPLE, 5));
                hopper.setItem(1, new ItemStack(Items.STICK, 2));
                scene.expectPress(steal, "a hopper");
                scene.fireSteal(steal);
                helper.assertTrue(user.getInventory().countItem(Items.STICK) == 2
                                && hopper.getItem(1).isEmpty() && hopper.getItem(3).getCount() == 5,
                        "1.16 took the first filled slot of the hopper (slot 1); the user got "
                                + user.getInventory().countItem(Items.STICK) + " sticks and "
                                + user.getInventory().countItem(Items.APPLE) + " apples");
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 DiverDownStealItem.perform :108-118: a block's inventory was taken with no spawn protection or game mode check
    // (the 1.21 guards were mayInteract and mayUseItemAt, which refuse an Adventure player).
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_steal_adventure",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void stealItemTakesABlockInAdventureModeAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement, true);
            try {
                scene.look(0.0F);
                Ability steal = scene.power.getAbility(DiverDownAddon.STEAL_ITEM_NAME);
                ServerPlayer user = (ServerPlayer) scene.owner;
                user.setGameMode(GameType.ADVENTURE);
                BlockPos pos = scene.block(2.0D, 1, Blocks.CHEST.defaultBlockState());
                ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(pos);
                helper.assertTrue(chest != null, "Fixture: the chest has no block entity");
                chest.setItem(0, new ItemStack(Items.DIAMOND, 5));
                scene.expectPress(steal, "a chest in Adventure mode");
                scene.fireSteal(steal);
                helper.assertTrue(user.getInventory().countItem(Items.DIAMOND) == 5 && chest.getItem(0).isEmpty(),
                        "1.16 took a chest's items in Adventure mode; the user got "
                                + user.getInventory().countItem(Items.DIAMOND) + " diamonds, the chest kept "
                                + chest.getItem(0));
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 perform :127-139 and :163: the result of every offer with uses left, one of them at random, and a use spent on
    // each of those offers; an offer without uses left stays as it is.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_steal_merchant_offers",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void stealItemTakesARandomResultOfEveryStockedOfferAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement, true);
            try {
                scene.look(0.0F);
                Ability steal = scene.power.getAbility(DiverDownAddon.STEAL_ITEM_NAME);
                ServerPlayer user = (ServerPlayer) scene.owner;
                WanderingTrader trader = scene.trader(2.5D,
                        scene.offer(Items.APPLE, 1), scene.offer(Items.STICK, 2),
                        scene.spentOffer(Items.BREAD, 1), scene.offer(Items.CARROT, 3));
                trader.getRandom().setSeed(scene.seedPicking(3, 2));
                scene.expectPress(steal, "a wandering trader");
                scene.fireSteal(steal);
                MerchantOffers offers = trader.getOffers();
                helper.assertTrue(offers.get(0).getUses() == 1 && offers.get(1).getUses() == 1
                                && offers.get(3).getUses() == 1
                                && offers.get(2).getUses() == offers.get(2).getMaxUses(),
                        "1.16 spent a use of every offer with uses left and left the spent one; uses "
                                + offers.get(0).getUses() + "," + offers.get(1).getUses() + ","
                                + offers.get(2).getUses() + "," + offers.get(3).getUses());
                helper.assertTrue(user.getInventory().countItem(Items.CARROT) == 3
                                && scene.heldCount(user) == 3,
                        "1.16 gave the third of the three results at random (carrot x3); the user holds "
                                + scene.heldDescription(user));
                helper.assertTrue(!trader.isRemoved(),
                        "1.16 kept a wandering trader that still had stock after the steal");
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 perform :140-149: a villager's xp falls by a fifth of what its level took, at the floor of its level it drops
    // one level, and with more than two offers per level the first two go.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_steal_merchant_xp",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void stealItemLowersAVillagersXpAndLevelAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement, true);
            try {
                scene.look(0.0F);
                Ability steal = scene.power.getAbility(DiverDownAddon.STEAL_ITEM_NAME);
                MerchantOffer[] five = {scene.offer(Items.APPLE, 1), scene.offer(Items.STICK, 1),
                        scene.offer(Items.BREAD, 1), scene.offer(Items.CARROT, 1), scene.offer(Items.COAL, 1)};

                Villager above = scene.merchantVillager(2.5D, 3, 100, five);
                scene.expectPress(steal, "a level 3 villager");
                scene.fireSteal(steal);
                helper.assertTrue(above.getVillagerXp() == 86 && above.getVillagerData().getLevel() == 3
                                && above.getOffers().size() == 5,
                        "1.16 took 70 / 5 = 14 xp from a level 3 villager and kept its level and offers; xp "
                                + above.getVillagerXp() + ", level " + above.getVillagerData().getLevel()
                                + ", offers " + above.getOffers().size());
                above.discard();

                scene.rearm(steal);
                Villager floor = scene.merchantVillager(2.5D, 3, 75, scene.offer(Items.APPLE, 1),
                        scene.offer(Items.STICK, 1), scene.offer(Items.BREAD, 1), scene.offer(Items.CARROT, 1),
                        scene.offer(Items.COAL, 1));
                scene.fireSteal(steal);
                helper.assertTrue(floor.getVillagerXp() == 61 && floor.getVillagerData().getLevel() == 2,
                        "1.16 dropped a level 3 villager at the floor of its level (xp 75 to 61); xp "
                                + floor.getVillagerXp() + ", level " + floor.getVillagerData().getLevel());
                MerchantOffers left = floor.getOffers();
                helper.assertTrue(left.size() == 3 && left.get(0).getResult().is(Items.BREAD)
                                && left.get(2).getResult().is(Items.COAL),
                        "1.16 removed the first two of five offers once the villager was level 2; "
                                + left.size() + " offers left, the first one " + left.get(0).getResult());
                floor.discard();

                scene.rearm(steal);
                Villager first = scene.merchantVillager(2.5D, 1, 0, scene.offer(Items.APPLE, 1));
                scene.fireSteal(steal);
                helper.assertTrue(first.getVillagerXp() == 0 && first.getVillagerData().getLevel() == 1,
                        "1.16 kept a level 1 villager at xp 0 on level 1; xp " + first.getVillagerXp()
                                + ", level " + first.getVillagerData().getLevel());
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 perform :150-161 and needsToRestock: a wandering trader left with no offer that has uses becomes a nitwit
    // where it stood, and what it gives is one of the results and its milk, blue carpet, lead and invisibility potion.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_steal_trader_nitwit",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void stealItemTurnsASpentWanderingTraderIntoANitwitAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement, true);
            try {
                scene.look(0.0F);
                Ability steal = scene.power.getAbility(DiverDownAddon.STEAL_ITEM_NAME);
                ServerPlayer user = (ServerPlayer) scene.owner;
                List<ItemStack> gifts = List.of(new ItemStack(Items.APPLE), new ItemStack(Items.MILK_BUCKET),
                        new ItemStack(Items.BLUE_CARPET), new ItemStack(Items.LEAD),
                        PotionContents.createItemStack(Items.POTION, Potions.LONG_INVISIBILITY));
                for (int index = 0; index < gifts.size(); index++) {
                    scene.rearm(steal);
                    user.getInventory().clearContent();
                    MerchantOffer lastUse = scene.lastUseOffer(Items.APPLE, 1);
                    WanderingTrader trader = scene.trader(2.5D, lastUse, scene.spentOffer(Items.BREAD, 1));
                    Vec3 stood = trader.position();
                    trader.getRandom().setSeed(scene.seedPicking(gifts.size(), index));
                    scene.expectPress(steal, "a wandering trader on its last use");
                    scene.fireSteal(steal);
                    ItemStack expected = gifts.get(index);
                    helper.assertTrue(scene.heldCount(user) == expected.getCount()
                                    && user.getInventory().countItem(expected.getItem()) == expected.getCount()
                                    && ItemStack.isSameItemSameComponents(scene.firstHeld(user), expected),
                            "1.16 gave gift " + index + " of the spent trader, " + expected + "; the user holds "
                                    + scene.heldDescription(user));
                    helper.assertTrue(trader.isRemoved(),
                            "1.16 removed a wandering trader whose offers were all spent");
                    List<Villager> nitwits = helper.getLevel().getEntitiesOfClass(Villager.class,
                            new AABB(stood, stood).inflate(0.5D),
                            villager -> villager.getVillagerData().getProfession() == VillagerProfession.NITWIT);
                    scene.extra.addAll(nitwits);
                    nitwits.forEach(Entity::discard);
                    helper.assertTrue(nitwits.size() == 1 && nitwits.get(0).position().equals(stood),
                            "1.16 put a nitwit villager where the trader stood; found " + nitwits.size());
                }
                scene.rearm(steal);
                user.getInventory().clearContent();
                WanderingTrader stocked = scene.trader(2.5D, scene.offer(Items.APPLE, 1), scene.offer(Items.STICK, 1));
                scene.fireSteal(steal);
                helper.assertTrue(!stocked.isRemoved(),
                        "1.16 kept a wandering trader that still had offers with uses left");
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 stealItem :171-182, stealItemFromCap and stealItemFromArrayList: with no empty slot the stack was not added
    // but spawned as an ItemEntity at the user's feet, with no pickup delay, which the user then picked up again,
    // merging into the stacks the inventory could hold.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_steal_full_inventory",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void stealItemSpawnsTheStackAtTheUsersFeetWithNoFreeSlotAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement, true);
            try {
                scene.look(0.0F);
                Ability steal = scene.power.getAbility(DiverDownAddon.STEAL_ITEM_NAME);
                ServerPlayer user = (ServerPlayer) scene.owner;
                user.getInventory().clearContent();
                user.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 10));
                for (int slot = 1; slot < user.getInventory().items.size(); slot++) {
                    user.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
                }
                BlockPos pos = scene.block(2.0D, 1, Blocks.CHEST.defaultBlockState());
                ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(pos);
                helper.assertTrue(chest != null, "Fixture: the chest has no block entity");
                chest.setItem(0, new ItemStack(Items.DIAMOND, 5));
                scene.expectPress(steal, "a chest with the user's inventory full");
                scene.fireSteal(steal);
                List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                        user.getBoundingBox().inflate(3.0D), item -> item.getItem().is(Items.DIAMOND));
                scene.extra.addAll(dropped);
                helper.assertTrue(dropped.size() == 1 && dropped.get(0).getItem().getCount() == 5
                                && user.getInventory().countItem(Items.DIAMOND) == 10,
                        "1.16 spawned the 5 diamonds as an item instead of merging them into the full inventory;"
                                + " items " + dropped.size() + ", diamonds held "
                                + user.getInventory().countItem(Items.DIAMOND));
                ItemEntity item = dropped.get(0);
                helper.assertTrue(!item.hasPickUpDelay() && item.position().equals(user.position()),
                        "1.16 spawned the item at the user's feet with no pickup delay; delay "
                                + item.hasPickUpDelay() + ", at " + item.position() + " for the user at "
                                + user.position());
                // what the player's own tick does to an item in reach; this mock player is not ticked
                item.playerTouch(user);
                helper.assertTrue(item.isRemoved() && user.getInventory().countItem(Items.DIAMOND) == 15,
                        "1.16 let the user pick the item up again at once, merging into its 10 diamonds; removed "
                                + item.isRemoved() + ", diamonds " + user.getInventory().countItem(Items.DIAMOND));
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 stealItem :176 took Math.min(count, 64) from a player's slot; a 1.21 stack can hold up to 99.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_steal_player_cap",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void stealItemTakesAtMostSixtyFourFromAPlayerAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement, true);
            try {
                scene.look(0.0F);
                Ability steal = scene.power.getAbility(DiverDownAddon.STEAL_ITEM_NAME);
                ServerPlayer user = (ServerPlayer) scene.owner;
                ServerPlayer target = scene.targetPlayer("dd-steal-big-stack", GameType.SURVIVAL);
                ItemStack big = new ItemStack(Items.APPLE, 80);
                big.set(DataComponents.MAX_STACK_SIZE, 99);
                target.getInventory().setItem(0, big);
                scene.expectPress(steal, "a player holding 80 apples");
                scene.fireSteal(steal);
                helper.assertTrue(scene.heldCount(user) == 64 && target.getInventory().countItem(Items.APPLE) == 16,
                        "1.16 took at most 64 items from the player's slot; the user holds "
                                + scene.heldDescription(user) + ", the player kept "
                                + target.getInventory().countItem(Items.APPLE));
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 GameRenderer.pick through InputHandler.pickMouseTarget: a block up to 4.5 blocks (5 in Creative), an entity up
    // to 3 blocks (5 in Creative). With Diver Down out, EntityStandType.clientHitResult looked again where the pick
    // found no block or entity, with the Stand's precision ray at the user's reach of 5.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_steal_block_reach",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void stealItemReachesBlocksByGameModeAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement, true);
            try {
                Ability steal = scene.power.getAbility(DiverDownAddon.STEAL_ITEM_NAME);
                ServerPlayer user = (ServerPlayer) scene.owner;
                BlockPos chest = scene.block(6.0D, 1, Blocks.CHEST.defaultBlockState());
                scene.standFrom(chest, 4.7D);
                scene.expectPress(steal, "a chest 4.7 blocks away with Diver Down out (the Stand's ray reaches 5)");
                scene.standFrom(chest, 5.3D);
                helper.assertFalse(steal.checkConditions(scene.power).isPositive(),
                        "Steal Item took a chest 5.3 blocks away with Diver Down out; 1.16 reached 5");
                scene.unsummon();
                scene.standFrom(chest, 4.4D);
                scene.expectPress(steal, "a chest 4.4 blocks away");
                scene.standFrom(chest, 4.7D);
                helper.assertFalse(steal.checkConditions(scene.power).isPositive(),
                        "Steal Item took a chest 4.7 blocks away in Survival with Diver Down in; 1.16 reached 4.5");
                user.setGameMode(GameType.CREATIVE);
                scene.standFrom(chest, 4.9D);
                scene.expectPress(steal, "a chest 4.9 blocks away in Creative");
                scene.standFrom(chest, 5.3D);
                helper.assertFalse(steal.checkConditions(scene.power).isPositive(),
                        "Steal Item took a chest 5.3 blocks away in Creative; 1.16 reached 5");
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_steal_entity_reach",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void stealItemReachesEntitiesByGameModeAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement, true);
            try {
                scene.look(0.0F);
                Ability steal = scene.power.getAbility(DiverDownAddon.STEAL_ITEM_NAME);
                ServerPlayer user = (ServerPlayer) scene.owner;
                // the villager's front face is 0.3 block nearer than its middle
                Villager villager = scene.farmer(3.1D);
                scene.expectPress(steal, "a merchant 2.8 blocks away");
                // 1.16 EntityStandType.clientHitResult: the pick misses an entity beyond 3 blocks, and with Diver Down
                // out the Stand's precision ray then takes it
                villager.moveTo(scene.origin.x, scene.origin.y, scene.origin.z + 3.6D, 0.0F, 0.0F);
                scene.expectPress(steal, "a merchant 3.3 blocks away in Survival with Diver Down out (the Stand's"
                        + " precision ray)");
                BlockPos chest = scene.block(4.0D, 1, Blocks.CHEST.defaultBlockState());
                ChestBlockEntity chestEntity = (ChestBlockEntity) helper.getLevel().getBlockEntity(chest);
                chestEntity.setItem(0, new ItemStack(Items.DIAMOND, 5));
                HitResult behind = DiverDownTargeting.crosshair(scene.owner, scene.stand, scene.power.getAbility(DiverDownAddon.STEAL_ITEM_NAME));
                helper.assertTrue(behind instanceof EntityHitResult entityHit && entityHit.getEntity() == villager,
                        "With Diver Down out 1.16's precision ray met the merchant 3.3 blocks away before the chest"
                                + " behind it; the port's target is " + behind);
                scene.fireSteal(steal);
                int uses = 0;
                for (MerchantOffer offer : villager.getOffers()) {
                    uses += offer.getUses();
                }
                helper.assertTrue(uses > 0 && chestEntity.getItem(0).getCount() == 5
                                && user.getInventory().countItem(Items.DIAMOND) == 0,
                        "1.16 stole from the merchant 3.3 blocks away, not from the chest behind it; offer uses "
                                + uses + ", the chest kept " + chestEntity.getItem(0) + ", the user holds "
                                + scene.heldDescription(user));
                scene.rearm(steal);
                scene.block(chest, Blocks.AIR.defaultBlockState());
                scene.unsummon();
                helper.assertFalse(steal.checkConditions(scene.power).isPositive(),
                        "Steal Item took a merchant 3.3 blocks away in Survival with Diver Down in; 1.16 reached"
                                + " entities 3 blocks");
                villager.moveTo(scene.origin.x, scene.origin.y, scene.origin.z + 3.1D, 0.0F, 0.0F);
                scene.expectPress(steal, "a merchant 2.8 blocks away with Diver Down in");
                user.setGameMode(GameType.CREATIVE);
                villager.moveTo(scene.origin.x, scene.origin.y, scene.origin.z + 5.0D, 0.0F, 0.0F);
                scene.expectPress(steal, "a merchant 4.7 blocks away in Creative");
                villager.moveTo(scene.origin.x, scene.origin.y, scene.origin.z + 5.6D, 0.0F, 0.0F);
                helper.assertFalse(steal.checkConditions(scene.power).isPositive(),
                        "Steal Item took a merchant 5.3 blocks away in Creative; 1.16 reached 5");
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 PlayerController.getPickRange as Forge patched it: the user's REACH_DISTANCE, half a block less outside
    // Creative, so a modified reach moved the pick's block reach in both game modes and the Creative entity reach up to
    // the 6-block ray of GameRenderer.pick; the Survival entity reach stayed 3. The 1.21 block interaction range stands
    // for it, the game's own Creative bonus counted once.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_steal_reach_attribute",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void stealItemPickFollowsTheReachAttributeAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 8, placement -> {
            Scene scene = new Scene(helper, placement, true);
            try {
                Ability steal = scene.power.getAbility(DiverDownAddon.STEAL_ITEM_NAME);
                ServerPlayer user = (ServerPlayer) scene.owner;
                scene.unsummon();
                AttributeInstance range = user.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
                helper.assertTrue(range != null && range.getValue() == 4.5D,
                        "Fixture: the user's block interaction range is not 4.5: "
                                + (range != null ? range.getValue() : null));
                AttributeModifier longer = new AttributeModifier(
                        ResourceLocation.fromNamespaceAndPath(DiverDownAddon.MOD_ID, "gametest_longer_reach"), 2.0D,
                        AttributeModifier.Operation.ADD_VALUE);
                range.addTransientModifier(longer);
                BlockPos chest = scene.block(8.0D, 1, Blocks.CHEST.defaultBlockState());
                scene.standFrom(chest, 6.2D);
                scene.expectPress(steal, "a chest 6.2 blocks away in Survival with a reach 2 blocks longer (pick 6.5)");
                scene.standFrom(chest, 6.8D);
                helper.assertFalse(steal.checkConditions(scene.power).isPositive(),
                        "Steal Item took a chest 6.8 blocks away in Survival with a reach 2 blocks longer; 1.16's pick"
                                + " reached 6.5");
                scene.block(chest, Blocks.AIR.defaultBlockState());
                scene.look(0.0F);
                Villager villager = scene.farmer(3.6D);
                helper.assertFalse(steal.checkConditions(scene.power).isPositive(),
                        "Steal Item took a merchant 3.3 blocks away in Survival with a reach 2 blocks longer; 1.16's"
                                + " Survival pick reached entities 3 blocks whatever the reach");

                user.setGameMode(GameType.CREATIVE);
                // what the player's next tick puts on the range in Creative (ServerPlayer.updatePlayerAttributes)
                AttributeModifier creativeBonus = new AttributeModifier(
                        ResourceLocation.withDefaultNamespace("creative_mode_block_range"), 0.5D,
                        AttributeModifier.Operation.ADD_VALUE);
                range.addOrUpdateTransientModifier(creativeBonus);
                villager.moveTo(scene.origin.x, scene.origin.y, scene.origin.z + 6.1D, 0.0F, 0.0F);
                scene.expectPress(steal, "a merchant 5.8 blocks away in Creative with a reach 2 blocks longer");
                villager.moveTo(scene.origin.x, scene.origin.y, scene.origin.z + 6.6D, 0.0F, 0.0F);
                helper.assertFalse(steal.checkConditions(scene.power).isPositive(),
                        "Steal Item took a merchant 6.3 blocks away in Creative with a reach 2 blocks longer; 1.16's"
                                + " Creative entity ray was 6 blocks whatever the reach");
                villager.discard();
                scene.block(chest, Blocks.CHEST.defaultBlockState());
                scene.standFrom(chest, 6.8D);
                scene.expectPress(steal, "a chest 6.8 blocks away in Creative with a reach 2 blocks longer (pick 7)");
                scene.standFrom(chest, 7.3D);
                helper.assertFalse(steal.checkConditions(scene.power).isPositive(),
                        "Steal Item took a chest 7.3 blocks away in Creative with a reach 2 blocks longer; 1.16's pick"
                                + " reached 7 (the game's Creative bonus counts once)");

                range.removeModifier(longer.id());
                range.addTransientModifier(new AttributeModifier(
                        ResourceLocation.fromNamespaceAndPath(DiverDownAddon.MOD_ID, "gametest_shorter_reach"), -2.0D,
                        AttributeModifier.Operation.ADD_VALUE));
                scene.standFrom(chest, 2.8D);
                scene.expectPress(steal, "a chest 2.8 blocks away in Creative with a reach 2 blocks shorter (pick 3)");
                scene.standFrom(chest, 3.2D);
                helper.assertFalse(steal.checkConditions(scene.power).isPositive(),
                        "Steal Item took a chest 3.2 blocks away in Creative with a reach 2 blocks shorter; 1.16's"
                                + " pick reached 3");
                user.setGameMode(GameType.SURVIVAL);
                range.removeModifier(creativeBonus.id());
                scene.standFrom(chest, 2.3D);
                scene.expectPress(steal, "a chest 2.3 blocks away in Survival with a reach 2 blocks shorter (pick 2.5)");
                scene.standFrom(chest, 2.7D);
                helper.assertFalse(steal.checkConditions(scene.power).isPositive(),
                        "Steal Item took a chest 2.7 blocks away in Survival with a reach 2 blocks shorter; 1.16's"
                                + " pick reached 2.5");
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 EntityStandType.clientHitResult: with Diver Down out, an entity the Stand's precision ray finds after a pick
    // that missed is the target, so a mob 3 to 5 blocks away is the target (with Diver Down in the pick misses it). The
    // ray follows the aim assist: on, the hitbox grown by the precision takes a mob beside the crosshair; off, the
    // crosshair only. Every refusal answers the owner's kept dd_target_no_inv.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_steal_stand_ray",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void stealItemTakesTheEntityTheStandsRayFindsAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement, true);
            try {
                scene.look(0.0F);
                Ability steal = scene.power.getAbility(DiverDownAddon.STEAL_ITEM_NAME);
                PlayerClientBroadcastedSettings settings = ((ServerPlayer) scene.owner)
                        .getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get());
                settings.standAimAssist = true;
                Cow cow = scene.mob(4.0D, 0.5D);
                scene.expectNoInventory(steal, "a cow 3.5 blocks away with Diver Down out");
                settings.standAimAssist = false;
                scene.expectNoInventory(steal, "a cow 3.5 blocks away with Diver Down out and the aim assist off");
                cow.discard();

                Cow beside = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(beside != null, "Could not create a cow");
                scene.prepareMob(beside, new Vec3(scene.origin.x + 0.9D, scene.origin.y + 0.5D, scene.origin.z + 3.5D));
                Vec3 eyes = scene.owner.getEyePosition();
                helper.assertTrue(beside.getBoundingBox().clip(eyes, eyes.add(0.0D, 0.0D, 6.0D)).isEmpty(),
                        "Fixture: the cow beside the crosshair is on it");
                settings.standAimAssist = true;
                scene.expectNoInventory(steal, "a cow 0.9 blocks beside the crosshair with the aim assist (its hitbox"
                        + " grown by Diver Down's precision)");
                HitResult assisted = DiverDownTargeting.crosshair(scene.owner, scene.stand, scene.power.getAbility(DiverDownAddon.STEAL_ITEM_NAME));
                helper.assertTrue(assisted instanceof EntityHitResult entityHit && entityHit.getEntity() == beside,
                        "With the aim assist 1.16's precision ray took the cow beside the crosshair; the port's target"
                                + " is " + assisted);
                settings.standAimAssist = false;
                scene.expectKeptNoInventory(steal, "a cow beside the crosshair without the aim assist");
                HitResult crosshairOnly = DiverDownTargeting.crosshair(scene.owner, scene.stand, scene.power.getAbility(DiverDownAddon.STEAL_ITEM_NAME));
                helper.assertTrue(!(crosshairOnly instanceof EntityHitResult),
                        "Without the aim assist the crosshair only: a cow beside it is not aimed at; the port's target"
                                + " is " + crosshairOnly);
                beside.discard();

                settings.standAimAssist = true;
                scene.mob(4.0D, 0.5D);
                scene.unsummon();
                scene.expectKeptNoInventory(steal, "nothing (a cow 3.5 blocks away, Diver Down in)");
                HitResult unsummoned = DiverDownTargeting.crosshair(scene.owner, null, scene.power.getAbility(DiverDownAddon.STEAL_ITEM_NAME));
                helper.assertTrue(!(unsummoned instanceof EntityHitResult),
                        "With Diver Down in, 1.16's pick missed a cow 3.5 blocks away in Survival (entity reach 3); the"
                                + " port's target is " + unsummoned);
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 had no walls rule: the pick and the precision ray stopped at the first block whatever the Stand was doing.
    // The port asks the skill's own answer, so Steal Item pressed in the middle of the Stand's marked attack (through
    // walls) keeps its own: a villager behind a stone block is hidden by it, as with the Stand idle.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_steal_marked_attack",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void stealItemPressedDuringTheStandsMarkedAttackKeepsItsOwnWallsAnswerAsIn116(
            GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement, true);
            try {
                scene.look(0.0F);
                Ability steal = scene.power.getAbility(DiverDownAddon.STEAL_ITEM_NAME);
                PlayerClientBroadcastedSettings settings = ((ServerPlayer) scene.owner)
                        .getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get());
                settings.standAimAssist = false;
                // past the pick's 4.5 blocks, inside the Stand's ray of 5: the ray decides, and the villager's front
                // face (4.7) lies behind the stone's (4.6)
                BlockPos stone = scene.block(5.5D, 1, Blocks.STONE.defaultBlockState());
                scene.standFrom(stone, 4.6D);
                Villager villager = scene.farmer(0.0D);
                villager.moveTo(scene.origin.x, scene.origin.y, scene.owner.getZ() + 5.0D, 0.0F, 0.0F);
                helper.assertTrue(DiverDownTargeting.crosshair(scene.owner, scene.stand, steal)
                                instanceof BlockHitResult idle && idle.getBlockPos().equals(stone),
                        "With Diver Down idle the stone is not the crosshair's first hit");
                scene.expectKeptNoInventory(steal, "a stone block hiding a merchant (Diver Down idle)");

                Ability barrageAbility = scene.power.getAbility(DiverDownAddon.BARRAGE);
                EntityActionInstance barrage = ((EntityActionType) barrageAbility).initActionOnAbilityUse(
                        helper.getLevel(), scene.owner, scene.stand, null);
                LivingComponentAction.getComponent(scene.stand).setAction(barrage, scene.owner, SyncType.NO_SYNC);
                helper.assertTrue(scene.stand.getCurStandAction() == barrage && StandSkillWalls.isMarked(barrage)
                                && !StandSkillWalls.wallsHideEntities(scene.owner),
                        "Fixture: the Stand's barrage is not a marked attack the aimer's answer follows");
                scene.expectKeptNoInventory(steal, "a stone block hiding a merchant, pressed in the Stand's marked"
                        + " barrage (the steal's own walls answer, not the barrage's)");
                HitResult aim = DiverDownTargeting.crosshair(scene.owner, scene.stand, steal);
                helper.assertTrue(aim instanceof BlockHitResult block && block.getBlockPos().equals(stone),
                        "Pressed in the Stand's marked attack the steal's crosshair took the attack's through-walls"
                                + " answer: " + aim);
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 StandEntity.isPickable on the client: another user's Stand is picked only by a client that can see Stands
    // (ClientUtil.canSeeStands: a Stand user, Spirit Vision, a spectator that sees the invisible).
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_steal_stand_pick",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void stealItemPicksAnotherUsersStandOnlyWhenTheUserSeesStandsAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement);
            try {
                Cow viewer = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(viewer != null, "Could not create a viewer");
                viewer.setNoAi(true);
                viewer.setNoGravity(true);
                viewer.moveTo(scene.origin.x + 6.0D, scene.origin.y, scene.origin.z, 0.0F, 0.0F);
                viewer.setYHeadRot(0.0F);
                helper.assertTrue(helper.getLevel().addFreshEntity(viewer), "Could not add the viewer");
                scene.extra.add(viewer);
                scene.stand.moveTo(scene.origin.x + 6.0D, scene.origin.y, scene.origin.z + 1.5D, 0.0F, 0.0F);
                HitResult blind = DiverDownTargeting.crosshair(viewer, null, scene.power.getAbility(DiverDownAddon.STEAL_ITEM_NAME));
                helper.assertFalse(blind instanceof EntityHitResult,
                        "A user that cannot see Stands picked another user's Stand; 1.16 did not pick it: " + blind);
                viewer.addEffect(new MobEffectInstance(ModStatusEffects.SPIRIT_VISION, 200));
                HitResult seen = DiverDownTargeting.crosshair(viewer, null, scene.power.getAbility(DiverDownAddon.STEAL_ITEM_NAME));
                helper.assertTrue(seen instanceof EntityHitResult entityHit && entityHit.getEntity() == scene.stand,
                        "A user that sees Stands did not pick another user's Stand; 1.16 did: " + seen);
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_walls_barrage_mob",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void barrageHitsTheMobItsAimMeetsBehindAWall(GameTestHelper helper) {
        barrageAtAOneBlockWall(helper, true);
    }

    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_walls_barrage_wall",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void barrageBreaksAWallWithNothingBehindIt(GameTestHelper helper) {
        barrageAtAOneBlockWall(helper, false);
    }

    private static void barrageAtAOneBlockWall(GameTestHelper helper, boolean mobBehind) {
        GameTestPlacement.whenTicking(helper, 3, placement -> {
            Scene scene = new Scene(helper, placement);
            try {
                double ahead = 2.2D;
                Vec3 eyes = scene.owner.getEyePosition();
                // down at the middle of a cow 2.2 blocks ahead, over one block half a block ahead at eye level
                scene.look((float) Math.toDegrees(Math.atan2(eyes.y - (scene.origin.y + 0.7D), ahead)));
                BlockPos wall = scene.wall(1.0D, 1);
                ActionTarget crosshair = StandEntityPunchAbility.crosshairPick(
                        scene.stand, scene.owner, 1.0F, entity -> false);
                helper.assertTrue(crosshair.getType() == ActionTarget.TargetType.BLOCK
                                && wall.equals(crosshair.getBlockPos()),
                        "Fixture: the aim does not meet the wall first: " + crosshair);
                BlockState stone = Blocks.STONE.defaultBlockState();
                helper.assertTrue(StandEntityPunch.canStandBreakBlockByStrength(
                                        scene.stand, stone, helper.getLevel(), wall)
                                && StandEntityPunch.canStandBreakBlock(helper.getLevel(), wall, stone, scene.stand),
                        "Fixture: Diver Down cannot break the stone wall");
                Cow mob = mobBehind ? scene.mob(ahead, 0.0D) : null;
                float health = mob != null ? mob.getHealth() : 0.0F;
                Ability barrageAbility = scene.power.getAbility(DiverDownAddon.BARRAGE);
                helper.assertTrue(barrageAbility instanceof EntityActionType,
                        "Fixture: Diver Down has no barrage");
                EntityActionInstance barrage = ((EntityActionType) barrageAbility).initActionOnAbilityUse(
                        helper.getLevel(), scene.owner, scene.stand, null);
                LivingComponentAction.getComponent(scene.stand).setAction(barrage, scene.owner, SyncType.NO_SYNC);
                helper.assertTrue(scene.stand.getCurStandAction() == barrage && StandSkillWalls.isMarked(barrage)
                                && !StandSkillWalls.isMarked(barrageAbility.getAbilityId()),
                        "Diver Down's barrage is not marked through walls as one of its attacks");
                int[] ticks = {0};
                int[] hitTick = {-1};
                scene.poll(0, () -> {
                    ticks[0]++;
                    boolean wallStands = helper.getLevel().getBlockState(wall).is(Blocks.STONE);
                    if (mob == null) {
                        helper.assertTrue(!wallStands,
                                "The barrage did not break the wall with nothing behind it");
                        return;
                    }
                    helper.assertTrue(wallStands,
                            "The barrage broke the wall instead of hitting the cow behind it");
                    if (hitTick[0] < 0 && mob.getHealth() < health) {
                        hitTick[0] = ticks[0];
                    }
                    helper.assertTrue(hitTick[0] >= 0, "The barrage did not hit the cow behind the wall");
                    helper.assertTrue(ticks[0] >= hitTick[0] + 10, "Watching the wall for ten ticks after the hit");
                });
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 InputHandler.pickMouseTarget picked Steal Item's target from the camera entity, the Stand while it is
    // manually controlled (StandUtil.java:160): GameRenderer.pick from the Stand's eyes with the user's pick range (an
    // entity 3 blocks at most in Survival), and EntityStandType.clientHitResult's precision ray from the Stand with the
    // Stand's own reach (StandEntity.getAimDistance :1709-1718), not the user's 5.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_steal_manual_aim",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void stealItemAimsFromAManuallyControlledDiverDownAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 7, placement -> {
            Scene scene = new Scene(helper, placement, true);
            try {
                scene.look(0.0F);
                Ability steal = scene.power.getAbility(DiverDownAddon.STEAL_ITEM_NAME);
                ServerPlayer user = (ServerPlayer) scene.owner;
                BlockPos userChest = scene.block(2.0D, 1, Blocks.CHEST.defaultBlockState());
                ((ChestBlockEntity) helper.getLevel().getBlockEntity(userChest)).setItem(0,
                        new ItemStack(Items.DIAMOND, 5));
                BlockPos standChest = scene.block(5.0D, 1, Blocks.CHEST.defaultBlockState());
                ((ChestBlockEntity) helper.getLevel().getBlockEntity(standChest)).setItem(0,
                        new ItemStack(Items.EMERALD, 3));
                scene.stand.setManuallyControlled(true);
                scene.stand.setNoGravity(true);
                scene.standFacing(new Vec3(scene.origin.x, scene.origin.y, scene.origin.z + 3.0D), 0.0F);
                scene.stand.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(standChest));
                helper.assertTrue(scene.stand.isManuallyControlled(), "Fixture: Diver Down is not manually controlled");
                HitResult aim = DiverDownTargeting.crosshair(scene.owner, scene.stand, scene.power.getAbility(DiverDownAddon.STEAL_ITEM_NAME));
                helper.assertTrue(aim instanceof BlockHitResult block && aim.getType() == HitResult.Type.BLOCK
                                && block.getBlockPos().equals(standChest),
                        "1.16 picked the target from the manually controlled Stand's eyes: the chest it looks at, not"
                                + " the one before the user; the port's target is " + aim);
                scene.expectPress(steal, "the chest the manually controlled Diver Down looks at");
                scene.fireSteal(steal);
                ChestBlockEntity before = (ChestBlockEntity) helper.getLevel().getBlockEntity(userChest);
                helper.assertTrue(user.getInventory().countItem(Items.EMERALD) == 3
                                && before.getItem(0).getCount() == 5,
                        "1.16 stole from the chest the manually controlled Stand looked at; the user holds "
                                + scene.heldDescription(user) + ", the chest before the user kept " + before.getItem(0));

                scene.rearm(steal);
                scene.block(standChest, Blocks.AIR.defaultBlockState());
                double standReach = scene.stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
                helper.assertTrue(standReach < 3.0D, "Fixture: Diver Down's own reach is " + standReach);
                // the villager's front face is 0.3 block nearer than its middle
                Villager villager = scene.farmer(6.1D);
                scene.stand.lookAt(EntityAnchorArgument.Anchor.EYES, villager.getEyePosition());
                scene.expectPress(steal, "a merchant 2.8 blocks before the manually controlled Stand (the pick from"
                        + " its eyes)");
                villager.moveTo(scene.origin.x, scene.origin.y, scene.origin.z + 6.6D, 0.0F, 0.0F);
                scene.stand.lookAt(EntityAnchorArgument.Anchor.EYES, villager.getEyePosition());
                scene.expectKeptNoInventory(steal, "a merchant 3.3 blocks before the manually controlled Stand (beyond the"
                        + " pick's 3 blocks and the Stand's own reach of " + standReach + ")");
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 DiverDownEntityPhasing (TargetRequirement.ENTITY) took the mouse target, picked from the manually controlled
    // Stand's eyes, its precision ray at the Stand's own reach.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_phasing_manual_aim",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void entityPhasingAimsFromAManuallyControlledDiverDownAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 9, placement -> {
            Scene scene = new Scene(helper, placement);
            try {
                scene.look(0.0F);
                Ability phasing = scene.power.getAbility(DiverDownAddon.ENTITY_PHASING_NAME);
                Cow beforeUser = scene.mob(2.0D, 0.0D);
                Cow beforeStand = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(beforeStand != null, "Could not create a cow");
                scene.prepareMob(beforeStand, new Vec3(scene.origin.x, scene.origin.y, scene.origin.z + 5.5D));
                Vec3 standPos = new Vec3(scene.origin.x, scene.origin.y, scene.origin.z + 3.5D);
                scene.stand.setManuallyControlled(true);
                scene.stand.setNoGravity(true);
                scene.standFacing(standPos, 0.0F);
                scene.stand.lookAt(EntityAnchorArgument.Anchor.EYES, beforeStand.getEyePosition());
                phasing.onClick(helper.getLevel(), scene.owner, null);
                helper.assertTrue(scene.stand.getEmbeddedTarget() == beforeStand,
                        "1.16 dived into the cow the manually controlled Stand looked at, not the one before the user;"
                                + " embedded in " + (scene.stand.getEmbeddedTarget() == beforeUser ? "the cow before the"
                                        + " user" : String.valueOf(scene.stand.getEmbeddedTarget())));
                scene.stand.clearEmbeddedTarget();

                scene.standFacing(standPos, 0.0F);
                beforeStand.moveTo(scene.origin.x, scene.origin.y, scene.origin.z + 7.5D, 0.0F, 0.0F);
                scene.stand.lookAt(EntityAnchorArgument.Anchor.EYES, beforeStand.getEyePosition());
                phasing.onClick(helper.getLevel(), scene.owner, null);
                helper.assertTrue(!scene.stand.isEmbedded(),
                        "1.16's ray from the manually controlled Stand reached the Stand's own reach ("
                                + scene.stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE)
                                + "), not a cow 4 blocks before it; embedded in " + scene.stand.getEmbeddedTarget());
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 DiverDownKineticTrap (TargetRequirement.BLOCK) took the mouse target, picked from the manually controlled
    // Stand's eyes.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_trap_manual_aim",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void kineticTrapPressAimsFromAManuallyControlledDiverDownAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement);
            try {
                Ability trap = scene.power.getAbility(DiverDownAddon.KINETIC_TRAP_NAME);
                BlockPos stone = scene.block(3.0D, 0, Blocks.STONE.defaultBlockState());
                scene.stand.setManuallyControlled(true);
                scene.stand.setNoGravity(true);
                scene.standFacing(new Vec3(scene.origin.x, scene.origin.y, scene.origin.z + 1.0D), 0.0F);
                scene.stand.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(stone));
                scene.look(-90.0F);
                ConditionCheck standAims = trap.checkConditions(scene.power);
                helper.assertTrue(standAims.isPositive(),
                        "1.16 took the block the manually controlled Stand looked at while the user looked at the sky;"
                                + " the port answered " + scene.answer(standAims));
                scene.look((float) Math.toDegrees(Math.atan2(
                        scene.owner.getEyeY() - (stone.getY() + 0.5D), stone.getZ() + 0.5D - scene.origin.z)));
                scene.stand.setXRot(-90.0F);
                helper.assertTrue(DiverDownTargeting.block(scene.owner).getType() == HitResult.Type.BLOCK,
                        "Fixture: the user does not look at the stone");
                ConditionCheck userAims = trap.checkConditions(scene.power);
                helper.assertTrue(!userAims.isPositive(),
                        "1.16 refused the trap when the manually controlled Stand looked at the sky, wherever the user"
                                + " looked; the port answered " + scene.answer(userAims));
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 DiverDownEntityPhasing was a StandEntityAction (InitStands :72): a press with Diver Down not summoned
    // summoned it (StandEntityAction.onClick :201-243) and it dived into the target; a press at nothing summoned it
    // and took it back.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_phasing_summon",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void entityPhasingSummonsDiverDownForThePressAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement);
            try {
                scene.look(0.0F);
                Ability phasing = scene.power.getAbility(DiverDownAddon.ENTITY_PHASING_NAME);
                Cow cow = scene.mob(2.5D, 0.0D);
                scene.unsummon();
                ConditionCheck press = phasing.checkConditions(scene.power);
                helper.assertTrue(press.isPositive(),
                        "1.16 accepted Entity Phasing pressed with Diver Down not summoned; the port answered "
                                + scene.answer(press));
                phasing.onClick(helper.getLevel(), scene.owner, null);
                DiverDownStandEntity summoned = DiverDownAddon.activeStand(scene.owner);
                helper.assertTrue(summoned != null && summoned.getEmbeddedTarget() == cow,
                        "1.16 summoned Diver Down for the press and dived into the cow; summoned " + (summoned != null)
                                + ", embedded in " + (summoned != null ? summoned.getEmbeddedTarget() : null));
                summoned.clearEmbeddedTarget();
                scene.unsummon();
                scene.look(-90.0F);
                phasing.onClick(helper.getLevel(), scene.owner, null);
                helper.assertTrue(DiverDownAddon.activeStand(scene.owner) == null,
                        "A press at nothing leaves Diver Down away (1.16 took back a Stand it summoned for it)");
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 picked Entity Phasing's target from a manually controlled Stand with GameRenderer.pick, which meets the user
    // as any other entity. StandEntityAction.checkStandTarget asked StandEntity.canAttack, which refuses the Stand's own
    // user (MCUtil.canHarm), so the press was refused silently, before DiverDownEntityPhasing's dd_self (:51-52) was
    // asked: Diver Down did not dive into what stood behind its user.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_phasing_manual_self",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void entityPhasingAtItsUserFromAManuallyControlledDiverDownIsRefusedAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            Scene scene = new Scene(helper, placement);
            try {
                scene.look(0.0F);
                Ability phasing = scene.power.getAbility(DiverDownAddon.ENTITY_PHASING_NAME);
                DiverDownStandEntity stand = scene.stand;
                stand.setManuallyControlled(true);
                stand.setNoGravity(true);
                // above the barrier block the framework walls the 1x1x1 structure with, where the user's feet are
                double rayY = scene.origin.y + 1.1D;
                // two blocks south of its user, looking north at it, its eyes level with the user's body
                scene.standFacing(new Vec3(scene.origin.x, rayY - stand.getEyeHeight(), scene.origin.z + 2.0D), 180.0F);
                Cow behind = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(behind != null, "Could not create a cow");
                scene.prepareMob(behind, new Vec3(scene.origin.x, rayY - 0.7D, scene.origin.z - 1.6D));
                Vec3 eyes = stand.getEyePosition();
                Vec3 look = stand.getLookAngle();
                helper.assertTrue(Math.abs(eyes.y - rayY) < 1.0E-6D && look.z < -0.999D
                                && scene.owner.getBoundingBox().clip(eyes, eyes.add(look.scale(3.0D))).isPresent(),
                        "Fixture: the manually controlled Stand does not look at its user");
                HitResult pick = DiverDownTargeting.crosshair(scene.owner, stand, scene.power.getAbility(DiverDownAddon.STEAL_ITEM_NAME));
                helper.assertTrue(pick instanceof EntityHitResult hit && hit.getEntity() == scene.owner,
                        "Fixture: the pick from the manually controlled Stand does not meet its user first: " + pick);
                phasing.onClick(helper.getLevel(), scene.owner, null);
                helper.assertTrue(!stand.isEmbedded(),
                        "1.16 refused Entity Phasing silently when the manually controlled Stand's pick met its user"
                                + " (StandEntity.canAttack, before dd_self); the port dived into "
                                + (stand.getEmbeddedTarget() == behind ? "the cow behind the user"
                                        : String.valueOf(stand.getEmbeddedTarget())));
                scene.owner.moveTo(scene.origin.x + 3.0D, scene.origin.y, scene.origin.z, 0.0F, 0.0F);

                // checkStandTarget refused any picked entity the Stand might not attack, or one not living, the same
                // way: a teammate without friendly fire, then a boat, where the user stood
                Cow mate = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(mate != null, "Could not create a cow");
                scene.prepareMob(mate, new Vec3(scene.origin.x, scene.origin.y, scene.origin.z));
                Scoreboard board = helper.getLevel().getScoreboard();
                PlayerTeam team = board.addPlayerTeam("dd_phasing_mates_" + stand.getId());
                scene.cleanup.add(() -> board.removePlayerTeam(team));
                team.setAllowFriendlyFire(false);
                board.addPlayerToTeam(scene.owner.getScoreboardName(), team);
                board.addPlayerToTeam(mate.getScoreboardName(), team);
                pick = DiverDownTargeting.crosshair(scene.owner, stand, scene.power.getAbility(DiverDownAddon.STEAL_ITEM_NAME));
                helper.assertTrue(pick instanceof EntityHitResult hit && hit.getEntity() == mate,
                        "Fixture: the pick from the manually controlled Stand does not meet the teammate first: " + pick);
                phasing.onClick(helper.getLevel(), scene.owner, null);
                helper.assertTrue(!stand.isEmbedded(),
                        "1.16 refused Entity Phasing silently when the manually controlled Stand's pick met a teammate "
                                + "without friendly fire (StandEntity.canAttack); the port dived into "
                                + (stand.getEmbeddedTarget() == behind ? "the cow behind the teammate"
                                        : String.valueOf(stand.getEmbeddedTarget())));
                mate.discard();

                Boat boat = EntityType.BOAT.create(helper.getLevel());
                helper.assertTrue(boat != null, "Could not create a boat");
                boat.setNoGravity(true);
                boat.moveTo(scene.origin.x, rayY - 0.3D, scene.origin.z, 0.0F, 0.0F);
                helper.assertTrue(helper.getLevel().addFreshEntity(boat), "Could not add a boat");
                scene.extra.add(boat);
                pick = DiverDownTargeting.crosshair(scene.owner, stand, scene.power.getAbility(DiverDownAddon.STEAL_ITEM_NAME));
                helper.assertTrue(pick instanceof EntityHitResult hit && hit.getEntity() == boat,
                        "Fixture: the pick from the manually controlled Stand does not meet the boat first: " + pick);
                phasing.onClick(helper.getLevel(), scene.owner, null);
                helper.assertTrue(!stand.isEmbedded(),
                        "1.16 refused Entity Phasing silently when the manually controlled Stand's pick met a boat (not "
                                + "living, StandEntityAction.checkStandTarget); the port dived into "
                                + (stand.getEmbeddedTarget() == behind ? "the cow behind the boat"
                                        : String.valueOf(stand.getEmbeddedTarget())));
                boat.discard();

                phasing.onClick(helper.getLevel(), scene.owner, null);
                helper.assertTrue(stand.getEmbeddedTarget() == behind,
                        "Fixture: with its user out of the way Diver Down did not dive into the cow 3.15 blocks before"
                                + " it; embedded in " + stand.getEmbeddedTarget());
                stand.clearEmbeddedTarget();
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // From a manually controlled Stand 1.16's pick (GameRenderer.pick with the user's PlayerController.getPickRange)
    // reached an entity 3 blocks away in Survival and as far as the pick range, 5, in Creative, before the Stand's
    // precision ray at its own reach of 2.5 was asked.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_phasing_manual_reach",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void entityPhasingFromAManuallyControlledDiverDownReachesThePickAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 8, placement -> {
            Scene scene = new Scene(helper, placement, true);
            try {
                scene.look(0.0F);
                ServerPlayer user = (ServerPlayer) scene.owner;
                Ability phasing = scene.power.getAbility(DiverDownAddon.ENTITY_PHASING_NAME);
                DiverDownStandEntity stand = scene.stand;
                stand.setManuallyControlled(true);
                stand.setNoGravity(true);
                scene.standFacing(new Vec3(scene.origin.x, scene.origin.y, scene.origin.z + 1.0D), 0.0F);
                Vec3 eyes = stand.getEyePosition();
                Cow cow = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(cow != null, "Could not create a cow");
                // the cow's front face the given distance before the Stand's eyes, the look through its middle
                double halfWidth = EntityType.COW.getWidth() / 2.0D;
                scene.prepareMob(cow, new Vec3(eyes.x, eyes.y - 0.7D, eyes.z + 4.5D + halfWidth));

                user.setGameMode(GameType.CREATIVE);
                phasing.onClick(helper.getLevel(), user, null);
                helper.assertTrue(stand.getEmbeddedTarget() == cow,
                        "1.16's pick from the manually controlled Stand reached a cow 4.5 blocks before it for a"
                                + " Creative user (the pick range, 5); the port's ray reaches the Stand's own "
                                + stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + "; embedded in "
                                + stand.getEmbeddedTarget());
                stand.clearEmbeddedTarget();
                scene.standFacing(new Vec3(scene.origin.x, scene.origin.y, scene.origin.z + 1.0D), 0.0F);
                cow.moveTo(eyes.x, eyes.y - 0.7D, eyes.z + 5.4D + halfWidth, 0.0F, 0.0F);
                phasing.onClick(helper.getLevel(), user, null);
                helper.assertTrue(!stand.isEmbedded(),
                        "1.16's Creative pick ended at 5 blocks; Diver Down dived into a cow 5.4 blocks before the"
                                + " Stand: " + stand.getEmbeddedTarget());

                user.setGameMode(GameType.SURVIVAL);
                cow.moveTo(eyes.x, eyes.y - 0.7D, eyes.z + 3.4D + halfWidth, 0.0F, 0.0F);
                phasing.onClick(helper.getLevel(), user, null);
                helper.assertTrue(!stand.isEmbedded(),
                        "1.16's Survival pick took an entity 3 blocks away at most, and the Stand's ray reached 2.5;"
                                + " Diver Down dived into a cow 3.4 blocks before the Stand: " + stand.getEmbeddedTarget());
                cow.moveTo(eyes.x, eyes.y - 0.7D, eyes.z + 2.9D + halfWidth, 0.0F, 0.0F);
                phasing.onClick(helper.getLevel(), user, null);
                helper.assertTrue(stand.getEmbeddedTarget() == cow,
                        "1.16's Survival pick from the manually controlled Stand reached a cow 2.9 blocks before it;"
                                + " embedded in " + stand.getEmbeddedTarget());
                stand.clearEmbeddedTarget();
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 DiverDownKineticTrap (TargetRequirement.BLOCK) took the client's target: GameRenderer.pick with the user's pick
    // range (4.5 in Survival), and with Diver Down out, after a miss, the Stand's precision ray at the user's reach of
    // 5 (EntityStandType.clientHitResult), here under the trap's aim assist and walls rule (owner, 2026-10-16). With
    // Diver Down away the client had no Stand to ask; the port took a fixed 5-block ray either way.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_trap_press_pick",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void kineticTrapPressTakesThePickThenTheStandsRayAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 8, placement -> {
            Scene scene = new Scene(helper, placement, true);
            try {
                scene.look(0.0F);
                Ability trap = scene.power.getAbility(DiverDownAddon.KINETIC_TRAP_NAME);
                PlayerClientBroadcastedSettings settings = ((ServerPlayer) scene.owner)
                        .getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get());
                BlockPos stone = scene.block(6.0D, 1, Blocks.STONE.defaultBlockState());
                scene.standFrom(stone, 4.7D);
                Vec3 eyes = scene.owner.getEyePosition();
                Cow beside = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(beside != null, "Could not create a cow");
                scene.prepareMob(beside, new Vec3(eyes.x + 0.9D, scene.owner.getY(), eyes.z + 2.5D));
                helper.assertTrue(beside.getBoundingBox().clip(eyes, eyes.add(0.0D, 0.0D, 6.0D)).isEmpty(),
                        "Fixture: the cow beside the crosshair is on it");

                settings.standAimAssist = true;
                ConditionCheck assisted = trap.checkConditions(scene.power);
                helper.assertTrue(!assisted.isPositive(),
                        "The pick missed the stone 4.7 blocks away (beyond 4.5), so 1.16 asked Diver Down's precision"
                                + " ray, whose grown hitbox met the cow 0.9 blocks beside the crosshair: no block, refused;"
                                + " the port answered " + scene.answer(assisted));
                settings.standAimAssist = false;
                ConditionCheck crosshairOnly = trap.checkConditions(scene.power);
                helper.assertTrue(crosshairOnly.isPositive(),
                        "Without the aim assist the Stand's ray is the crosshair only and meets the stone 4.7 blocks"
                                + " away within the user's reach of 5; the port answered " + scene.answer(crosshairOnly));
                EntityActionInstance action = ((EntityActionType) trap).initActionOnAbilityUse(
                        helper.getLevel(), scene.owner, scene.stand, null);
                LivingComponentAction.getComponent(scene.stand).setAction(action, scene.owner, SyncType.NO_SYNC);
                ActionTarget held = action.getTaskTarget(helper.getLevel());
                LivingComponentAction.getComponent(scene.stand).setAction(null, scene.owner, SyncType.NO_SYNC);
                helper.assertTrue(held.getType() == ActionTarget.TargetType.BLOCK && stone.equals(held.getBlockPos()),
                        "The trap's task kept " + held + "; 1.16 kept the stone the Stand's ray met at the press");

                settings.standAimAssist = true;
                scene.standFrom(stone, 4.2D);
                eyes = scene.owner.getEyePosition();
                beside.moveTo(eyes.x + 0.9D, scene.owner.getY(), eyes.z + 2.5D, 0.0F, 0.0F);
                ConditionCheck picked = trap.checkConditions(scene.power);
                helper.assertTrue(picked.isPositive(),
                        "The pick met the stone 4.2 blocks away, so 1.16 did not ask the Stand's ray and the cow beside"
                                + " the crosshair did not count; the port answered " + scene.answer(picked));
                beside.discard();

                // a cow on the crosshair behind a stone beyond the pick: the stone hides it from the Stand's ray
                scene.standFrom(stone, 4.6D);
                eyes = scene.owner.getEyePosition();
                Cow walled = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(walled != null, "Could not create a cow");
                scene.prepareMob(walled, new Vec3(eyes.x, eyes.y - 0.7D, stone.getZ() + 1.5D));
                ConditionCheck hidden = trap.checkConditions(scene.power);
                helper.assertTrue(hidden.isPositive(),
                        "The stone 4.6 blocks away hides the cow behind it from the Stand's ray (the walls rule of the"
                                + " trap's strike); the port answered " + scene.answer(hidden));
                walled.discard();

                scene.unsummon();
                scene.standFrom(stone, 4.7D);
                ConditionCheck away = trap.checkConditions(scene.power);
                helper.assertTrue(!away.isPositive(),
                        "With Diver Down away 1.16's client had only the pick, 4.5 blocks in Survival: the stone 4.7"
                                + " blocks away was no target; the port answered " + scene.answer(away));
                scene.standFrom(stone, 4.2D);
                ConditionCheck awayNear = trap.checkConditions(scene.power);
                helper.assertTrue(awayNear.isPositive(),
                        "Fixture: with Diver Down away the pick's stone 4.2 blocks away was refused: "
                                + scene.answer(awayNear));
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    static final class Scene {
        final GameTestHelper helper;
        final Vec3 origin;
        final LivingEntity owner;
        final StandPower power;
        final DiverDownStandEntity stand;
        final List<Cow> mobs = new ArrayList<>();
        final List<Entity> extra = new ArrayList<>();
        final List<BlockPos> placed = new ArrayList<>();
        final List<BlockState> previous = new ArrayList<>();
        final List<Runnable> cleanup = new ArrayList<>();
        boolean closed;

        Scene(GameTestHelper helper, GameTestPlacement placement) {
            this(helper, placement, false);
        }

        // A player owner is a real ServerPlayer on the player list, so the player events and the player tick reach it.
        Scene(GameTestHelper helper, GameTestPlacement placement, boolean playerOwner) {
            this.helper = helper;
            origin = placement.owner.add(0.5D, 0.0D, 0.5D);
            if (playerOwner) {
                owner = serverPlayer(helper);
            } else {
                Cow cow = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(cow != null, "Could not create the Diver Down user");
                cow.setNoAi(true);
                owner = cow;
            }
            owner.setNoGravity(true);
            owner.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
            owner.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
            owner.setHealth(100.0F);
            if (!playerOwner) {
                helper.getLevel().addFreshEntity(owner);
            }
            power = PowerClass.STAND.attachGet(owner);
            power.setStand(DiverDownAddon.DIVER_DOWN.get());
            power.getStandInstance().orElseThrow().addPart(StandPart.ARMS);
            power.skipProgression();
            power.setResolveLevel(4);
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(DiverDownAddon.DIVER_DOWN.get().summon(owner, power), "Could not summon Diver Down");
            stand = (DiverDownStandEntity) power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Missing the Diver Down Stand");
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
        }

        void look(float pitch) {
            owner.moveTo(origin.x, origin.y, origin.z, 0.0F, pitch);
            owner.setYHeadRot(0.0F);
            owner.setYBodyRot(0.0F);
        }

        BlockPos wall(double ahead, int up) {
            BlockPos pos = BlockPos.containing(origin.x, origin.y + up, origin.z + ahead);
            place(pos);
            return pos;
        }

        void place(BlockPos pos) {
            block(pos, Blocks.STONE.defaultBlockState());
        }

        BlockPos block(double ahead, int up, BlockState state) {
            BlockPos pos = BlockPos.containing(origin.x, origin.y + up, origin.z + ahead);
            block(pos, state);
            return pos;
        }

        void block(BlockPos pos, BlockState state) {
            placed.add(pos);
            previous.add(helper.getLevel().getBlockState(pos));
            helper.getLevel().setBlockAndUpdate(pos, state);
        }

        // three blocks high across the Stand's way; returns where they start
        double stoneColumnAhead(double ahead, BlockState state) {
            BlockPos base = block(ahead, 0, state);
            block(base.above(), state);
            block(base.above(2), state);
            return base.getZ();
        }

        // the server side of a manual movement packet that asks for two and a half blocks forward
        AABB moveStandForward() {
            stand.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
            stand.move(MoverType.PLAYER, new Vec3(0.0D, 0.0D, 2.5D));
            return stand.getBoundingBox();
        }

        KineticTrapEntity setTrap(Ability trap) {
            List<KineticTrapEntity> before = traps();
            performTrap(trap);
            List<KineticTrapEntity> set = traps();
            set.removeAll(before);
            helper.assertTrue(set.size() == 1, "The kinetic trap was not set (" + set.size() + " new traps)");
            extra.add(set.get(0));
            return set.get(0);
        }

        // The kinetic trap pressed and struck at once, its windup skipped: 1.16 set the trap in its one perform tick.
        void performTrap(Ability trap) {
            performTrap(trap, () -> {});
        }

        // betweenPressAndStrike stands for what happens during the windup
        void performTrap(Ability trap, Runnable betweenPressAndStrike) {
            helper.assertTrue(trap instanceof EntityActionType, "The kinetic trap is not a Stand action");
            EntityActionInstance action = ((EntityActionType) trap).initActionOnAbilityUse(
                    helper.getLevel(), owner, stand, null);
            LivingComponentAction actions = LivingComponentAction.getComponent(stand);
            actions.setAction(action, owner, SyncType.NO_SYNC);
            helper.assertTrue(stand.getCurStandAction() == action, "Fixture: the kinetic trap did not start");
            betweenPressAndStrike.run();
            action.actionPerformStart();
            actions.setAction(null, owner, SyncType.NO_SYNC);
        }

        // the traps the strike set, kept for the cleanup
        List<KineticTrapEntity> strike(Ability trap, Runnable betweenPressAndStrike) {
            List<KineticTrapEntity> before = traps();
            performTrap(trap, betweenPressAndStrike);
            List<KineticTrapEntity> set = traps();
            set.removeAll(before);
            extra.addAll(set);
            return set;
        }

        void turn(float yaw, float pitch) {
            owner.moveTo(origin.x, origin.y, origin.z, yaw, pitch);
            owner.setYHeadRot(yaw);
            owner.setYBodyRot(yaw);
        }

        void standFacing(Vec3 pos, float yaw) {
            stand.moveTo(pos.x, pos.y, pos.z, yaw, 0.0F);
            stand.setYHeadRot(yaw);
            stand.setYBodyRot(yaw);
        }

        // Spec setters are memory-only; the user's config file is never saved.
        void damageMultiplier(double value) {
            ModConfigSpec.ConfigValue<Double> multiplier = JojoModConfig.COMMON_SPEC.getValues()
                    .get(List.of("Stand settings", "standPowerMultiplier"));
            double before = multiplier.get();
            cleanup.add(() -> multiplier.set(before));
            multiplier.set(value);
            helper.assertTrue(JojoModConfig.getCommonConfigInstance(false).standDamageMultiplier.get() == value,
                    "Fixture: the live Stand damage multiplier is not " + value);
        }

        List<PlayLevelSoundEvent.AtPosition> punchSounds() {
            List<PlayLevelSoundEvent.AtPosition> heard = new ArrayList<>();
            Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
                if (event.getLevel() == helper.getLevel() && event.getSound() != null
                        && event.getSound().value() == DiverDownAddon.DIVER_DOWN_PUNCH_LIGHT.get()) {
                    heard.add(event);
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, PlayLevelSoundEvent.AtPosition.class,
                    listener);
            cleanup.add(() -> NeoForge.EVENT_BUS.unregister(listener));
            return heard;
        }

        List<KineticTrapEntity> traps() {
            List<KineticTrapEntity> traps = new ArrayList<>();
            for (Entity entity : helper.getLevel().getAllEntities()) {
                if (entity instanceof KineticTrapEntity trap && !trap.isRemoved()
                        && owner.getUUID().equals(trap.getOwnerUuid())) {
                    traps.add(trap);
                }
            }
            return traps;
        }

        String answer(ConditionCheck check) {
            return check.isPositive() ? "a positive check"
                    : check.getWarning() != null ? check.getWarning().getString() : "a silent refusal";
        }

        void expectPress(Ability steal, String target) {
            ConditionCheck press = steal.checkConditions(power);
            helper.assertTrue(press.isPositive(),
                    "1.16 let Steal Item take " + target + " under the crosshair; the port answered " + answer(press));
        }

        void expectNoInventory(Ability steal, String target) {
            ConditionCheck press = steal.checkConditions(power);
            helper.assertTrue(isNoInventory(press),
                    "1.16 answered Steal Item at " + target + " with dd_target_no_inv; the port answered "
                            + answer(press));
        }

        // 1.16 refused these silently; the owner kept the port's dd_target_no_inv for them (2026-10-19)
        void expectKeptNoInventory(Ability steal, String target) {
            ConditionCheck press = steal.checkConditions(power);
            helper.assertTrue(isNoInventory(press),
                    "1.16 refused Steal Item at " + target + " (the owner kept dd_target_no_inv); the port answered "
                            + answer(press));
        }

        boolean isNoInventory(ConditionCheck check) {
            return !check.isPositive() && check.getWarning() != null
                    && check.getWarning().getContents() instanceof TranslatableContents contents
                    && contents.getKey().equals("jojo.message.action_condition.dd_target_no_inv");
        }

        // The steal's perform, the charge skipped: what a held press does when its 15 ticks are over.
        void fireSteal(Ability steal) {
            EntityActionInstance action = ((EntityActionType) steal).initActionOnAbilityUse(
                    helper.getLevel(), owner, owner, null);
            LivingComponentAction.getComponent(owner, PowerClass.STAND).setAction(action, owner, SyncType.NO_SYNC);
            helper.assertTrue(LivingComponentAction.getCurEntityAction(owner, PowerClass.STAND) == action,
                    "Fixture: the steal did not start");
            action.actionPerformStart();
            LivingComponentAction.getComponent(owner, PowerClass.STAND).setAction(null, owner, SyncType.NO_SYNC);
        }

        void rearm(Ability steal) {
            power.setAbilityCooldown(steal.name(), 0);
            power.setStamina(power.getMaxStamina());
        }

        // a player 2.5 blocks ahead of the user, on its look
        ServerPlayer targetPlayer(String name, GameType mode) {
            ServerPlayer player = serverPlayer(helper, name, mode);
            player.setNoGravity(true);
            player.moveTo(origin.x, origin.y, origin.z + 2.5D, 0.0F, 0.0F);
            cleanup.add(() -> player.connection.disconnect(Component.literal("Diver Down steal GameTest complete")));
            return player;
        }

        void moveAside(ServerPlayer player) {
            player.moveTo(origin.x + 8.0D, origin.y, origin.z, 0.0F, 0.0F);
        }

        Villager farmer(double ahead) {
            Villager villager = EntityType.VILLAGER.create(helper.getLevel());
            helper.assertTrue(villager != null, "Could not create a villager");
            villager.setNoAi(true);
            villager.setNoGravity(true);
            villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
            villager.moveTo(origin.x, origin.y, origin.z + ahead, 0.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(villager), "Could not add a villager");
            extra.add(villager);
            helper.assertTrue(!villager.getOffers().isEmpty(), "Fixture: the farmer has no offers");
            return villager;
        }

        // The user stands so that the front face of the block is distance blocks ahead of its eyes.
        void standFrom(BlockPos block, double distance) {
            owner.moveTo(origin.x, origin.y, block.getZ() - distance, 0.0F, 0.0F);
            owner.setYHeadRot(0.0F);
            owner.setYBodyRot(0.0F);
        }

        void unsummon() {
            power.getPowerType().forceUnsummon(owner, power);
            helper.assertTrue(DiverDownAddon.activeStand(owner) == null, "Fixture: Diver Down is still summoned");
        }

        MerchantOffer offer(Item result, int count) {
            return new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(result, count), 12, 1, 0.05F);
        }

        MerchantOffer spentOffer(Item result, int count) {
            MerchantOffer offer = offer(result, count);
            offer.setToOutOfStock();
            return offer;
        }

        MerchantOffer lastUseOffer(Item result, int count) {
            MerchantOffer offer = offer(result, count);
            while (offer.getUses() < offer.getMaxUses() - 1) {
                offer.increaseUses();
            }
            return offer;
        }

        Villager merchantVillager(double ahead, int level, int xp, MerchantOffer... offers) {
            Villager villager = EntityType.VILLAGER.create(helper.getLevel());
            helper.assertTrue(villager != null, "Could not create a villager");
            villager.setNoAi(true);
            villager.setNoGravity(true);
            villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER)
                    .setLevel(level));
            villager.setVillagerXp(xp);
            MerchantOffers list = new MerchantOffers();
            list.addAll(List.of(offers));
            villager.setOffers(list);
            villager.moveTo(origin.x, origin.y, origin.z + ahead, 0.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(villager), "Could not add a villager");
            extra.add(villager);
            return villager;
        }

        WanderingTrader trader(double ahead, MerchantOffer... offers) {
            WanderingTrader trader = EntityType.WANDERING_TRADER.create(helper.getLevel());
            helper.assertTrue(trader != null, "Could not create a wandering trader");
            trader.setNoAi(true);
            trader.setNoGravity(true);
            MerchantOffers list = trader.getOffers();
            list.clear();
            list.addAll(List.of(offers));
            trader.moveTo(origin.x, origin.y, origin.z + ahead, 0.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(trader), "Could not add a wandering trader");
            extra.add(trader);
            return trader;
        }

        // a seed with which a random source picks index of bound, as the merchant's own source will
        long seedPicking(int bound, int index) {
            for (long seed = 0L; seed < 10_000L; seed++) {
                if (RandomSource.create(seed).nextInt(bound) == index) {
                    return seed;
                }
            }
            throw new IllegalStateException("No seed picks " + index + " of " + bound);
        }

        int heldCount(ServerPlayer player) {
            int count = 0;
            for (ItemStack stack : player.getInventory().items) {
                count += stack.getCount();
            }
            return count;
        }

        ItemStack firstHeld(ServerPlayer player) {
            for (ItemStack stack : player.getInventory().items) {
                if (!stack.isEmpty()) {
                    return stack;
                }
            }
            return ItemStack.EMPTY;
        }

        String heldDescription(ServerPlayer player) {
            List<String> held = new ArrayList<>();
            for (ItemStack stack : player.getInventory().items) {
                if (!stack.isEmpty()) {
                    held.add(stack.toString());
                }
            }
            return held.toString();
        }

        Cow mob(double ahead, double lift) {
            Cow mob = EntityType.COW.create(helper.getLevel());
            helper.assertTrue(mob != null, "Could not create a cow");
            prepareMob(mob, new Vec3(origin.x, origin.y + lift, origin.z + ahead));
            Vec3 eyes = owner.getEyePosition();
            helper.assertTrue(mob.getBoundingBox().clip(eyes, eyes.add(owner.getViewVector(1.0F).scale(10.0D)))
                    .isPresent(), "Fixture: the cow is not on the user's look");
            return mob;
        }

        void prepareMob(Cow mob, Vec3 pos) {
            mob.setNoAi(true);
            mob.setNoGravity(true);
            mob.noPhysics = true;
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0D);
            mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
            mob.setHealth(500.0F);
            mob.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(mob), "Could not add a cow");
            mobs.add(mob);
        }

        // Runs the conditions each tick until they hold; after OWN_TICKS the last unmet one fails the test.
        void poll(int waited, Runnable conditions) {
            poll(waited, OWN_TICKS, conditions);
        }

        void poll(int waited, int budget, Runnable conditions) {
            helper.runAfterDelay(1, () -> {
                try {
                    conditions.run();
                } catch (GameTestAssertException unmet) {
                    if (waited + 1 < budget - 5) {
                        poll(waited + 1, budget, conditions);
                        return;
                    }
                    close();
                    throw unmet;
                } catch (Throwable error) {
                    close();
                    throw error;
                }
                close();
                helper.succeed();
            });
        }

        void close() {
            if (closed) {
                return;
            }
            closed = true;
            cleanup.forEach(Runnable::run);
            LivingComponentAction.getComponent(stand).setAction(null, owner, SyncType.NO_SYNC);
            LivingComponentAction.getComponent(owner).setAction(null, owner, SyncType.NO_SYNC);
            LivingComponentAction.getComponent(owner, PowerClass.STAND).setAction(null, owner, SyncType.NO_SYNC);
            mobs.forEach(Cow::discard);
            extra.forEach(Entity::discard);
            for (int i = placed.size() - 1; i >= 0; i--) {
                helper.getLevel().setBlockAndUpdate(placed.get(i), previous.get(i));
            }
            DiverDownAuthority.clearOwnedBy(helper.getLevel().getServer(), owner.getUUID());
            if (power.hasPower() && power.isSummoned()) {
                power.getPowerType().forceUnsummon(owner, power);
            }
            if (owner instanceof ServerPlayer player) {
                player.connection.disconnect(Component.literal("Diver Down kinetic trap GameTest complete"));
            } else {
                owner.discard();
            }
        }
    }

    // As GameTestHelper.makeMockServerPlayerInLevel, with the NeoForge channel set up and in survival mode.
    static ServerPlayer serverPlayer(GameTestHelper helper) {
        return serverPlayer(helper, "dd-trap-owner", GameType.SURVIVAL);
    }

    static ServerPlayer serverPlayer(GameTestHelper helper, String name, GameType mode) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile,
                cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(mode);
        return player;
    }
}
