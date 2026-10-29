package rotp.addons.tickettoride.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.channel.embedded.EmbeddedChannel;
import org.slf4j.Logger;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.StartAttacking;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.tickettoride.TicketToRideAddon;
import rotp.addons.tickettoride.TicketToRideEvents;
import rotp.addons.tickettoride.ability.TicketToRideAbility;
import rotp.addons.tickettoride.ability.TicketToRideAbility.Mode;
import rotp.addons.tickettoride.ability.TicketToRideAbilityService;
import rotp.addons.tickettoride.entity.DeadPhotoMementoEntity;
import rotp.addons.tickettoride.entity.TicketToRideStandEntity;
import rotp.addons.tickettoride.item.TearsBladeData;
import rotp.addons.tickettoride.item.TearsBladeItem;
import rotp.addons.tickettoride.state.LoveTrainService;
import rotp.addons.tickettoride.state.LoveTrainState;
import rotp.addons.tickettoride.state.TearsHazardService;
import rotp.addons.tickettoride.state.TearsWoundService;
import rotp.addons.tickettoride.state.TraceEnchantService;
import rotp.addons.tickettoride.state.TicketToRideBehaviorService;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.MathUtil;

/**
 * 1.16.5 ExplodAction and its registration (InitStands "explod"): holdToFire(20, true), 75 stamina, 120-tick
 * cooldown, arms required; the marked target is looked up when the action fires. Every case presses and releases
 * the ability through the server input entry and ticks the Stand's action.
 */
@GameTestHolder("rotp_ttr_explod")
@PrefixGameTestTemplate(false)
public final class TicketToRideExplodGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final short KEY = 7;
    private static final int CHARGE_TICKS = 22;
    private static final float STAMINA_COST = 75.0F;
    private static final int COOLDOWN = 120;
    private static final int BRANCH_TNT = 0;
    private static final int BRANCH_EXPLOSION = 1;
    private static final int BRANCH_CREEPER = 2;
    // A ServerPlayer ignores ordinary damage for its first 60 ticks in the world.
    private static final int SPAWN_PROTECTION_TICKS = 65;

    private TicketToRideExplodGameTests() {}

    // getTargetsInViewCone(user, 45.0f): the angle between the look vector and the direction from the user's
    // eyes to the entity's position (its feet) is at most 45 degrees.
    @GameTest(template = "empty", batch = "ttr_explod_cone", timeoutTicks = 100)
    public static void coneIsFortyFiveDegreesToTheTargetsFeet(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "TtrExplodCone", false)) {
            Vec3 look = f.user.getLookAngle();
            Vec3 right = new Vec3(-look.z, 0.0D, look.x).normalize();
            Vec3 down = new Vec3(0.0D, -1.0D, 0.0D);

            Zombie wide = f.target(f.offset(look, right, 5.0D, 40.0D));
            helper.assertTrue(f.angleTo(wide.getEyePosition()) > 22.5D,
                    "Fixture: the 40-degree target's eyes must be outside the old 22.5-degree cone");
            helper.assertTrue(f.fireHits(wide),
                    "A marked target 40 degrees off the look vector (to its feet) was not hit: feetAngle="
                            + f.angleTo(wide.position()));
            f.remove(wide);

            Zombie inside = f.target(f.offset(look, right, 5.0D, 44.5D));
            helper.assertTrue(f.fireHits(inside), "A marked target 44.5 degrees off the look vector was not hit");
            f.remove(inside);

            Zombie outside = f.target(f.offset(look, right, 5.0D, 45.5D));
            helper.assertTrue(!f.fireHits(outside), "A marked target 45.5 degrees off the look vector was hit");
            f.remove(outside);

            // Feet 50 degrees below the look vector, eyes almost on it: the donor measures to the feet.
            Zombie close = f.target(f.offset(look, down, 2.0D, 50.0D));
            helper.assertTrue(f.angleTo(close.getEyePosition()) < 22.5D,
                    "Fixture: the close target's eyes must be inside even the old 22.5-degree cone: "
                            + f.angleTo(close.getEyePosition()));
            helper.assertTrue(!f.fireHits(close),
                    "A marked target whose feet are 50 degrees off the look vector was hit because its eyes are in view");
            f.remove(close);

            Zombie below = f.target(f.offset(look, down, 5.0D, 50.0D));
            double eyeAngle = f.angleTo(below.getEyePosition());
            helper.assertTrue(eyeAngle > 22.5D && eyeAngle < 45.0D,
                    "Fixture: the lower target's eyes must be between 22.5 and 45 degrees: " + eyeAngle);
            helper.assertTrue(!f.fireHits(below),
                    "A marked target whose feet are 50 degrees off the look vector was hit (eyes at " + eyeAngle + ")");
            f.remove(below);

            LOGGER.info("R764_TTR_EXPLOD_PASS cone hit=40.0,44.5 miss=45.5,feet50/eyes{}", String.format("%.1f", eyeAngle));
        }
        helper.succeed();
    }

    // world.explode(standEntity, x, y, z, 4.0f, false, Explosion.Mode.BREAK): the Stand is the source, so its user
    // is not spared, and the blocks break with all their drops whatever mobGriefing says.
    @GameTest(template = "empty", batch = "ttr_explod_direct", timeoutTicks = 200)
    public static void directExplosionComesFromTheStandAndAlwaysBreaksBlocks(GameTestHelper helper) {
        Fixture f = new Fixture(helper, "TtrExplodDirect", true);
        helper.runAfterDelay(SPAWN_PROTECTION_TICKS, () -> {
            try {
                f.reset();
                BlockPos userBlock = f.user.blockPosition();
                BlockPos targetBlock = userBlock.east(6);
                f.buildBlastBox(userBlock.offset(-2, -1, -3), userBlock.offset(9, 4, 3));
                List<BlockPos> planks = new ArrayList<>();
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        planks.add(targetBlock.offset(1, dy, dz));
                        if (dz != 0) {
                            planks.add(targetBlock.offset(0, dy, dz));
                        }
                    }
                }
                for (BlockPos pos : planks) {
                    f.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState());
                }
                f.setMobGriefing(false);

                Zombie target = f.target(Vec3.atBottomCenterOf(targetBlock));
                float healthBefore = f.user.getHealth();
                helper.assertTrue(f.fire(target, BRANCH_EXPLOSION), "Explod did not start with a marked target in view");

                long broken = planks.stream().filter(pos -> f.level.getBlockState(pos).isAir()).count();
                AABB box = new AABB(userBlock.offset(-2, -1, -3).getCenter(), userBlock.offset(9, 4, 3).getCenter()).inflate(1.0D);
                int dropped = f.level.getEntitiesOfClass(ItemEntity.class, box, item -> item.getItem().is(Items.OAK_PLANKS))
                        .stream().mapToInt(item -> item.getItem().getCount()).sum();
                DamageSource onTarget = target.getLastDamageSource();
                DamageSource onUser = f.user.getLastDamageSource();
                String outcome = "broken=" + broken + "/" + planks.size() + ", dropped=" + dropped
                        + ", targetSource=" + describe(onTarget) + ", userHealth=" + healthBefore + "->" + f.user.getHealth()
                        + ", userSource=" + describe(onUser);
                helper.assertTrue(broken == planks.size(),
                        "The direct explosion did not break the blocks around the target with mobGriefing off: " + outcome);
                helper.assertTrue(dropped == planks.size(),
                        "The direct explosion did not drop every block it broke: " + outcome);
                helper.assertTrue(onTarget != null && onTarget.getEntity() == f.stand,
                        "The direct explosion did not come from the Stand entity: " + outcome);
                helper.assertTrue(f.user.getHealth() < healthBefore && onUser != null && onUser.getEntity() == f.stand,
                        "The user, six blocks from the target, was spared by the direct explosion: " + outcome);
                LOGGER.info("R764_TTR_EXPLOD_PASS direct_explosion {}", outcome);
            }
            finally {
                f.close();
            }
            helper.succeed();
        });
    }

    // StandEntityTask consumed the stamina after standPerform whatever it found, and PowerBaseImpl.performAction
    // set the cooldown on every fire. A hold released before 20 ticks never fired.
    @GameTest(template = "empty", batch = "ttr_explod_miss", timeoutTicks = 100)
    public static void firingWithoutATargetStillCostsStaminaAndCooldown(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "TtrExplodMiss", false)) {
            Vec3 ahead = f.ahead();
            Vec3 behind = f.behind();
            Zombie target = f.target(ahead);

            // The target leaves the view during the charge: the action still fires, at nothing.
            f.reset();
            float stamina = f.power.getStamina();
            helper.assertTrue(f.press(f.explod) && f.charge(CHARGE_TICKS), "Explod did not charge with a marked target in view");
            target.moveTo(behind.x, behind.y, behind.z, 0.0F, 0.0F);
            f.release(BRANCH_TNT);
            String miss = "stamina=" + stamina + "->" + f.power.getStamina() + ", cooldown=" + f.cooldown()
                    + ", effects=" + (f.effectsAt(ahead) + f.effectsAt(behind));
            helper.assertTrue(f.effectsAt(ahead) + f.effectsAt(behind) == 0,
                    "Explod hit a marked target behind the user: " + miss);
            helper.assertTrue(Math.abs(stamina - f.power.getStamina() - STAMINA_COST) < 0.01F,
                    "A fire that found no target did not cost 75 stamina: " + miss);
            helper.assertTrue(f.cooldown() == COOLDOWN,
                    "A fire that found no target did not start the 120-tick cooldown: " + miss);

            // Released early: nothing fires, nothing is paid.
            target.moveTo(ahead.x, ahead.y, ahead.z, 0.0F, 0.0F);
            f.reset();
            stamina = f.power.getStamina();
            helper.assertTrue(f.press(f.explod) && !f.charge(10), "Explod was fully charged after 10 ticks");
            f.release(BRANCH_TNT);
            String early = "stamina=" + stamina + "->" + f.power.getStamina() + ", cooldown=" + f.cooldown()
                    + ", effects=" + f.effectsAt(ahead);
            helper.assertTrue(f.effectsAt(ahead) == 0 && f.power.getStamina() == stamina && f.cooldown() == 0,
                    "A hold released after 10 ticks fired or was charged for: " + early);

            // Control: a full charge on a target in view fires, for the same price.
            f.reset();
            stamina = f.power.getStamina();
            helper.assertTrue(f.fire(target, BRANCH_TNT), "Explod did not start with a marked target in view");
            String hit = "stamina=" + stamina + "->" + f.power.getStamina() + ", cooldown=" + f.cooldown()
                    + ", effects=" + f.effectsAt(ahead);
            helper.assertTrue(f.effectsAt(ahead) == 1
                            && Math.abs(stamina - f.power.getStamina() - STAMINA_COST) < 0.01F && f.cooldown() == COOLDOWN,
                    "A fire that found its target did not cost 75 stamina and the 120-tick cooldown: " + hit);
            LOGGER.info("R764_TTR_EXPLOD_PASS miss_cost miss[{}] early[{}] hit[{}]", miss, early, hit);
        }
        helper.succeed();
    }

    // No stamina condition in 1.16: StandEntityTask paid the 75 after standPerform and ignored the result, so a
    // short bar still fires and drops to 0.
    @GameTest(template = "empty", batch = "ttr_explod_short_bar", timeoutTicks = 100)
    public static void explodFiresOnAShortBarAndDrainsIt(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "TtrExplodShort", false)) {
            Vec3 ahead = f.ahead();
            Zombie target = f.target(ahead);
            f.reset();
            f.power.setStamina(30.0F);
            helper.assertTrue(f.fire(target, BRANCH_TNT),
                    "1.16 started explod at any stamina, but it was refused on a 30 bar: "
                            + f.explod.checkConditions(f.power).getWarning());
            String fired = "stamina=30.0->" + f.power.getStamina() + ", cooldown=" + f.cooldown()
                    + ", effects=" + f.effectsAt(ahead);
            helper.assertTrue(f.effectsAt(ahead) == 1, "Explod did not fire on a 30 stamina bar: " + fired);
            helper.assertTrue(f.power.getStamina() == 0.0F && f.cooldown() == COOLDOWN,
                    "The 75 cost drains a 30 bar to 0 and the cooldown still starts: " + fired);
            LOGGER.info("R764_TTR_EXPLOD_PASS short_bar {}", fired);
        }
        helper.succeed();
    }

    // standEntity.playSound(STAND_UNSUMMON_SOUND, 1.0f, 1.0f) when a marked target is found, and only then.
    @GameTest(template = "empty", batch = "ttr_explod_sound", timeoutTicks = 100)
    public static void foundTargetPlaysTheStandUnsummonSoundAtTheStand(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "TtrExplodSound", false)) {
            ResourceLocation unsummon = ModSoundEvents.STAND_UNSUMMON.get().getLocation();
            Vec3 ahead = f.ahead();
            Vec3 behind = f.behind();
            Zombie target = f.target(ahead);

            f.listenToSounds();
            helper.assertTrue(f.fire(target, BRANCH_TNT), "Explod did not start with a marked target in view");
            List<PlayedSound> played = f.sounds.stream().filter(sound -> sound.id().equals(unsummon)).toList();
            helper.assertTrue(played.size() == 1,
                    "Finding a marked target did not play the Stand unsummon sound once: " + f.sounds);
            PlayedSound sound = played.get(0);
            helper.assertTrue(sound.pos().distanceTo(f.stand.position()) < 1.0E-6D
                            && sound.source() == f.stand.getSoundSource()
                            && sound.volume() == 1.0F && sound.pitch() == 1.0F,
                    "The Stand unsummon sound was not played at the Stand at volume 1 and pitch 1: " + sound
                            + ", stand=" + f.stand.position() + "/" + f.stand.getSoundSource());

            f.sounds.clear();
            f.reset();
            helper.assertTrue(f.press(f.explod) && f.charge(CHARGE_TICKS), "Explod did not charge with a marked target in view");
            target.moveTo(behind.x, behind.y, behind.z, 0.0F, 0.0F);
            f.release(BRANCH_TNT);
            helper.assertTrue(f.sounds.stream().noneMatch(missed -> missed.id().equals(unsummon)),
                    "A fire that found no target played the Stand unsummon sound: " + f.sounds);
            LOGGER.info("R764_TTR_EXPLOD_PASS stand_sound {}", sound);
        }
        helper.succeed();
    }

    // spawnTNTExplosion: new TNTEntity(world, x, y, z, null) with a 10-tick fuse; spawnCreeperExplosion: an ignited creeper.
    @GameTest(template = "empty", batch = "ttr_explod_tnt", timeoutTicks = 100)
    public static void tntHasNoOwnerAndTheCreeperIsIgnited(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "TtrExplodTnt", false)) {
            Vec3 ahead = f.ahead();
            Zombie target = f.target(ahead);
            AABB near = new AABB(ahead, ahead).inflate(0.5D);

            helper.assertTrue(f.fire(target, BRANCH_TNT), "Explod did not start with a marked target in view");
            List<PrimedTnt> tnts = f.level.getEntitiesOfClass(PrimedTnt.class, near);
            helper.assertTrue(tnts.size() == 1 && f.level.getEntitiesOfClass(Creeper.class, near).isEmpty(),
                    "The TNT branch did not spawn exactly one primed TNT at the target: " + tnts);
            PrimedTnt tnt = tnts.get(0);
            String tntState = "owner=" + tnt.getOwner() + ", fuse=" + tnt.getFuse() + ", pos=" + tnt.position();
            helper.assertTrue(tnt.position().distanceTo(ahead) < 1.0E-6D && tnt.getFuse() == 10,
                    "The primed TNT is not at the target's position with a 10-tick fuse: " + tntState);
            helper.assertTrue(tnt.getOwner() == null, "The primed TNT has an owner: " + tntState);
            tnt.discard();

            f.reset();
            helper.assertTrue(f.fire(target, BRANCH_CREEPER), "Explod did not start with a marked target in view");
            List<Creeper> creepers = f.level.getEntitiesOfClass(Creeper.class, near);
            helper.assertTrue(creepers.size() == 1 && f.level.getEntitiesOfClass(PrimedTnt.class, near, Entity::isAlive).isEmpty(),
                    "The creeper branch did not spawn exactly one creeper at the target: " + creepers);
            Creeper creeper = creepers.get(0);
            String creeperState = "ignited=" + creeper.isIgnited() + ", pos=" + creeper.position();
            creeper.discard();
            helper.assertTrue(creeper.isIgnited() && creeper.position().distanceTo(ahead) < 1.0E-6D,
                    "The creeper is not ignited at the target's position: " + creeperState);
            LOGGER.info("R764_TTR_EXPLOD_PASS tnt_and_creeper tnt[{}] creeper[{}]", tntState, creeperState);
        }
        helper.succeed();
    }

    // InitStands partsRequired: arms for explod and emiya, the main body for crash, randommob and meteor.
    @GameTest(template = "empty", batch = "ttr_explod_parts", timeoutTicks = 100)
    public static void lostStandPartsBlockTheActionsThatNeedThem(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "TtrExplodParts", false)) {
            Vec3 ahead = f.ahead();
            Zombie target = f.target(ahead);
            List<String> arms = List.of(TicketToRideAddon.EXPLOD, TicketToRideAddon.TRACE);
            List<String> body = List.of(TicketToRideAddon.CRASH, TicketToRideAddon.RANDOM_MOB, TicketToRideAddon.METEOR);
            List<String> none = List.of(TicketToRideAddon.LIGHTNING_SINGLE, TicketToRideAddon.LIGHTNING,
                    TicketToRideAddon.ITEM_FILL_NAME, TicketToRideAddon.LOVE_TRAIN_NAME,
                    TicketToRideAddon.SHARE_LOVE_TRAIN, TicketToRideAddon.REMOVE_SHARED,
                    TicketToRideAddon.REMOVE_LOVE_TRAIN);

            f.reset();
            for (String name : concat(arms, body, none)) {
                helper.assertTrue(f.missingPart(name) == null, name + " is blocked for a Stand part with all parts present");
            }

            helper.assertTrue(f.instance.removePart(StandPart.ARMS), "Could not take the Stand's arms");
            for (String name : arms) {
                helper.assertTrue("no_stand_part.arms".equals(f.missingPart(name)),
                        name + " does not need the Stand's arms: " + f.missingPart(name));
            }
            for (String name : concat(body, none)) {
                helper.assertTrue(f.missingPart(name) == null, name + " needs the Stand's arms: " + f.missingPart(name));
            }
            helper.assertTrue(!f.press(f.explod) && f.standAction.getAction() == null,
                    "Explod started for a Stand without arms");
            f.instance.addPart(StandPart.ARMS);

            helper.assertTrue(f.instance.removePart(StandPart.MAIN_BODY), "Could not take the Stand's main body");
            for (String name : body) {
                helper.assertTrue("no_stand_part.main_body".equals(f.missingPart(name)),
                        name + " does not need the Stand's main body: " + f.missingPart(name));
            }
            for (String name : concat(arms, none)) {
                helper.assertTrue(f.missingPart(name) == null, name + " needs the Stand's main body: " + f.missingPart(name));
            }
            f.instance.addPart(StandPart.MAIN_BODY);

            // 1.16 checked the parts again on every held tick: arms lost during the charge end it.
            f.reset();
            helper.assertTrue(f.press(f.explod) && !f.charge(5), "Explod did not start charging with all parts present");
            EntityActionInstance charge = f.standAction.getAction();
            f.instance.removePart(StandPart.ARMS);
            f.standAction.tick();
            helper.assertTrue(charge.isOver() && f.standAction.getAction() == null,
                    "Losing the Stand's arms did not end the explod charge: phase=" + charge.getPhase());
            f.instance.addPart(StandPart.ARMS);

            f.reset();
            helper.assertTrue(f.fire(target, BRANCH_TNT) && f.effectsAt(ahead) == 1,
                    "Explod did not fire again once the Stand had its arms back");
            LOGGER.info("R764_TTR_EXPLOD_PASS stand_parts arms={} main_body={} none={}", arms, body, none);
        }
        helper.succeed();
    }

    // 1.16 StandEntity.taskOffset: MobCrash's task kept the marked entity it was pressed at (standKeepsTarget), so
    // while it charged, the Stand's offset was turned by the yaw from the user's eyes to that entity, wherever the
    // user looked, and the Stand turned to it.
    @GameTest(template = "empty", batch = "ttr_crash_offset", timeoutTicks = 100)
    public static void crashTurnsItsOffsetToTheMarkedTargetAsIn116(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "TtrCrashOffset", false)) {
            f.user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standSide = HumanoidArm.RIGHT;
            f.user.setMainArm(HumanoidArm.RIGHT);
            Zombie target = f.target(f.ahead());
            LivingComponentAction.getComponent(f.user).entityAim.setTarget(new ActionTarget(target));
            helper.assertTrue(f.press(f.ability(TicketToRideAddon.CRASH)), "Crash did not start charging");
            EntityActionInstance crash = f.standAction.getAction();
            helper.assertTrue(crash.getPhase() == ActionPhase.WINDUP && f.stand.isFollowingUser(),
                    "Setup: crash is not charging with the Stand following its user: " + crash.getPhase());
            helper.assertTrue(crash.getTaskTarget(f.level).getEntity() == target
                            && crash.standRotationTarget != null && crash.standRotationTarget.getEntity() == target,
                    "1.16 MobCrash.standKeepsTarget kept the marked zombie it was pressed at and the Stand turned to it;"
                            + " the task holds " + crash.getTaskTarget(f.level) + ", the Stand turns to "
                            + crash.standRotationTarget);

            // the user looks south, its body turned 50 degrees
            f.user.setYRot(0.0F);
            f.user.yRotO = 0.0F;
            f.user.setYHeadRot(0.0F);
            f.user.yHeadRotO = 0.0F;
            f.user.yBodyRot = 50.0F;
            f.user.yBodyRotO = 50.0F;
            f.stand.updatePosition(f.user);
            Vec3 relative = f.stand.offsetFromUser.getRelativeOffset();
            helper.assertTrue(f.stand.offsetFromUser.getRotations() == StandOffsetFromUser.Rotations.HEAD,
                    "Setup: the default offset is not turned by the yaw alone: " + f.stand.offsetFromUser.getRotations());
            float yawToTarget = MathUtil.yRotDegFromVec(target.getEyePosition().subtract(f.user.getEyePosition()));
            // 1.16 StandEntity.updatePosition put the Stand at its user's feet plus the whole offset, never shortened
            // to the max range (only StandEntity.move held a Stand within it): Ticket to Ride's 1.079-block idle
            // offset is longer than its max range 1
            Vec3 expected = relative.yRot(-yawToTarget * MathUtil.DEG_TO_RAD);
            helper.assertTrue(expected.length() > f.stand.getMaxRangeForMovement(f.user),
                    "Setup: the turned offset " + expected + " is not longer than the Stand's max range "
                            + f.stand.getMaxRangeForMovement(f.user));
            Vec3 actual = f.stand.position().subtract(f.user.position());
            helper.assertTrue(actual.distanceTo(expected) < 1.0E-4D,
                    "1.16 put Ticket to Ride charging a crash pressed at a marked zombie east of its user, who then looked"
                            + " south, at its user's feet + " + expected + " (the offset " + relative
                            + " turned to the zombie), got " + actual);
            LOGGER.info("R764_TTR_EXPLOD_PASS crash_offset offset={} at={}", relative, actual);
        }
        helper.succeed();
    }

    // 1.16 StandEntityAction.overrideVanillaMouseTarget (PowerBaseImpl.checkTarget): MobCrash, TargetRequirement.NONE,
    // pressed at a torch (no collision shape) took what a collision ray from the Stand's eyes met behind it, so its task
    // kept the marked zombie there and the Stand turned to it; with nothing behind, the press kept nothing. The Stand's
    // eyes are level with its user's, 0.35 ahead, looking the same way, so the ray runs along the user's crosshair line.
    @GameTest(template = "empty", batch = "ttr_crash_torch", timeoutTicks = 100)
    public static void crashPressedAtATorchTakesTheMarkedMobBehindAsIn116(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "TtrCrashTorch", false)) {
            Vec3 eyes = f.user.getEyePosition();
            BlockPos torch = BlockPos.containing(eyes).east();
            f.clearArea(torch, torch.east(3));
            f.setBlockQuietly(torch, Blocks.WALL_TORCH.defaultBlockState()
                    .setValue(WallTorchBlock.FACING, Direction.WEST));
            helper.assertTrue(f.level.getBlockState(torch).is(Blocks.WALL_TORCH)
                            && f.level.getBlockState(torch).getCollisionShape(f.level, torch).isEmpty(),
                    "Setup: no torch without a collision shape at " + torch);
            ActionTarget pressedAt = new ActionTarget(torch, Direction.WEST);

            f.aimStandAlongTheUsersEyeLine();
            LivingComponentAction.getComponent(f.user).entityAim.setTarget(pressedAt);
            helper.assertTrue(f.press(f.ability(TicketToRideAddon.CRASH)), "Crash pressed at a lone torch did not start");
            EntityActionInstance alone = f.standAction.getAction();
            helper.assertTrue(alone.getTaskTarget(f.level).getType() == ActionTarget.TargetType.EMPTY
                            && alone.standRotationTarget == null,
                    "1.16's crash pressed at a torch with nothing behind it kept no target; the port's task holds "
                            + alone.getTaskTarget(f.level) + ", the Stand turns to " + alone.standRotationTarget);
            f.reset();

            Zombie behind = f.target(new Vec3(eyes.x + 2.0D, eyes.y - EntityType.ZOMBIE.getHeight() / 2.0D, eyes.z));
            f.aimStandAlongTheUsersEyeLine();
            LivingComponentAction.getComponent(f.user).entityAim.setTarget(pressedAt);
            helper.assertTrue(f.press(f.ability(TicketToRideAddon.CRASH)), "Crash pressed at the torch did not start");
            EntityActionInstance crash = f.standAction.getAction();
            helper.assertTrue(crash.getTaskTarget(f.level).getEntity() == behind
                            && crash.standRotationTarget != null && crash.standRotationTarget.getEntity() == behind,
                    "1.16's crash pressed at a torch took the marked zombie behind it (overrideVanillaMouseTarget) and"
                            + " the Stand turned to it; the port's task holds " + crash.getTaskTarget(f.level)
                            + ", the Stand turns to " + crash.standRotationTarget + " (torch " + torch + ")");
            LOGGER.info("R764_TTR_EXPLOD_PASS crash_torch torch={} target={}", torch, behind.position());
        }
        helper.succeed();
    }

    private static final Map<ResourceKey<Enchantment>, String> TABLE_116_TYPES = new LinkedHashMap<>();

    static {
        for (ResourceKey<Enchantment> key : List.of(Enchantments.PROTECTION, Enchantments.FIRE_PROTECTION,
                Enchantments.BLAST_PROTECTION, Enchantments.PROJECTILE_PROTECTION)) {
            TABLE_116_TYPES.put(key, "armor");
        }
        for (ResourceKey<Enchantment> key : List.of(Enchantments.FEATHER_FALLING, Enchantments.DEPTH_STRIDER,
                Enchantments.FROST_WALKER, Enchantments.SOUL_SPEED)) {
            TABLE_116_TYPES.put(key, "feet");
        }
        TABLE_116_TYPES.put(Enchantments.RESPIRATION, "head");
        TABLE_116_TYPES.put(Enchantments.AQUA_AFFINITY, "head");
        TABLE_116_TYPES.put(Enchantments.THORNS, "chest");
        for (ResourceKey<Enchantment> key : List.of(Enchantments.SHARPNESS, Enchantments.SMITE,
                Enchantments.BANE_OF_ARTHROPODS, Enchantments.KNOCKBACK, Enchantments.FIRE_ASPECT, Enchantments.LOOTING,
                Enchantments.SWEEPING_EDGE)) {
            TABLE_116_TYPES.put(key, "weapon");
        }
        TABLE_116_TYPES.put(Enchantments.EFFICIENCY, "digger");
        TABLE_116_TYPES.put(Enchantments.FORTUNE, "digger");
        TABLE_116_TYPES.put(Enchantments.UNBREAKING, "breakable");
        TABLE_116_TYPES.put(Enchantments.MENDING, "breakable");
        for (ResourceKey<Enchantment> key : List.of(Enchantments.POWER, Enchantments.PUNCH, Enchantments.FLAME,
                Enchantments.INFINITY)) {
            TABLE_116_TYPES.put(key, "bow");
        }
        TABLE_116_TYPES.put(Enchantments.LUCK_OF_THE_SEA, "rod");
        TABLE_116_TYPES.put(Enchantments.LURE, "rod");
        for (ResourceKey<Enchantment> key : List.of(Enchantments.LOYALTY, Enchantments.IMPALING, Enchantments.CHANNELING)) {
            TABLE_116_TYPES.put(key, "trident");
        }
        for (ResourceKey<Enchantment> key : List.of(Enchantments.MULTISHOT, Enchantments.QUICK_CHARGE,
                Enchantments.PIERCING)) {
            TABLE_116_TYPES.put(key, "crossbow");
        }
        TABLE_116_TYPES.put(Enchantments.VANISHING_CURSE, "vanishable");
    }

    // 1.16.5 EnchantmentType.canEnchant per the category of each pool enchantment (Forge's Item.canApplyAtEnchantingTable
    // is that category's check): the oracle the port's table rule is compared with, class by class.
    private static boolean table116(ItemStack stack, String type) {
        net.minecraft.world.item.Item item = stack.getItem();
        boolean breakable = stack.isDamageableItem();
        boolean armor = item instanceof net.minecraft.world.item.ArmorItem
                && !(item instanceof net.minecraft.world.item.AnimalArmorItem);
        net.minecraft.world.entity.EquipmentSlot slot = armor
                ? ((net.minecraft.world.item.ArmorItem) item).getEquipmentSlot() : null;
        boolean headBlock = item instanceof net.minecraft.world.item.BlockItem block
                && (block.getBlock() instanceof net.minecraft.world.level.block.AbstractSkullBlock
                        || block.getBlock() instanceof net.minecraft.world.level.block.CarvedPumpkinBlock);
        return switch (type) {
            case "armor" -> armor;
            case "feet" -> armor && slot == net.minecraft.world.entity.EquipmentSlot.FEET;
            case "head" -> armor && slot == net.minecraft.world.entity.EquipmentSlot.HEAD;
            case "chest" -> armor && slot == net.minecraft.world.entity.EquipmentSlot.CHEST;
            case "weapon" -> item instanceof net.minecraft.world.item.SwordItem;
            case "digger" -> item instanceof net.minecraft.world.item.DiggerItem;
            case "breakable" -> breakable;
            case "bow" -> item instanceof net.minecraft.world.item.BowItem;
            case "rod" -> item instanceof net.minecraft.world.item.FishingRodItem;
            case "trident" -> item instanceof net.minecraft.world.item.TridentItem;
            case "crossbow" -> item instanceof net.minecraft.world.item.CrossbowItem;
            case "vanishable" -> breakable || armor || headBlock
                    || item instanceof net.minecraft.world.item.ElytraItem
                    || item instanceof net.minecraft.world.item.CompassItem
                    || item instanceof net.minecraft.world.item.FishingRodItem
                    || item instanceof net.minecraft.world.item.ProjectileWeaponItem
                    || item instanceof net.minecraft.world.item.TridentItem;
            default -> throw new IllegalStateException(type);
        };
    }

    // 1.16 TraceOnAction.applyEnchantments (:125-131) and canApplyAtLeastOne (:160-162) asked
    // canApplyAtEnchantingTable, the item's category: an axe takes no Sharpness, Smite or Bane of Arthropods, shears no
    // Efficiency, a book nothing; a hand whose item takes none of the pool is passed over for the other hand
    // (findTraceableHand :95-101). The vanilla items of 1.16 take exactly what their category lets them.
    @GameTest(template = "empty", batch = "ttr_trace_table", timeoutTicks = 100)
    public static void traceOnTakesTheEnchantingTableRuleOf116(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "TtrTraceTable", false)) {
            var pool = f.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            List<String> off = new ArrayList<>();
            for (net.minecraft.world.item.Item item : BuiltInRegistries.ITEM) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                if (!id.getNamespace().equals("minecraft") || id.getPath().equals("wolf_armor")) {
                    continue;
                }
                ItemStack stack = new ItemStack(item);
                for (Map.Entry<ResourceKey<Enchantment>, String> entry : TABLE_116_TYPES.entrySet()) {
                    boolean want = table116(stack, entry.getValue());
                    if (TraceEnchantService.canApplyAtEnchantingTable(pool.getOrThrow(entry.getKey()), stack) != want) {
                        off.add(id.getPath() + "/" + entry.getKey().location().getPath() + (want ? " refused" : " allowed"));
                    }
                }
            }
            helper.assertTrue(off.isEmpty(), "1.16's enchanting table rule differs for " + off.size() + " vanilla pairs: " + off);

            java.util.function.BiFunction<ItemStack, ResourceKey<Enchantment>, Integer> levelOf =
                    (stack, key) -> stack.getEnchantments().getLevel(pool.getOrThrow(key));
            java.util.function.Function<ItemStack, String> rows = stack -> {
                boolean traced = TraceEnchantService.apply(f.level, stack);
                return "traced=" + traced + " sharpness=" + levelOf.apply(stack, Enchantments.SHARPNESS) + " smite="
                        + levelOf.apply(stack, Enchantments.SMITE) + " bane=" + levelOf.apply(stack, Enchantments.BANE_OF_ARTHROPODS)
                        + " knockback=" + levelOf.apply(stack, Enchantments.KNOCKBACK) + " efficiency="
                        + levelOf.apply(stack, Enchantments.EFFICIENCY) + " fortune=" + levelOf.apply(stack, Enchantments.FORTUNE)
                        + " unbreaking=" + levelOf.apply(stack, Enchantments.UNBREAKING) + " mending="
                        + levelOf.apply(stack, Enchantments.MENDING);
            };
            Map<String, String> seen = new LinkedHashMap<>();
            Map<String, String> want = new LinkedHashMap<>();
            seen.put("sword", rows.apply(new ItemStack(Items.IRON_SWORD)));
            want.put("sword", "traced=true sharpness=5 smite=5 bane=5 knockback=2 efficiency=0 fortune=0 unbreaking=3 mending=1");
            seen.put("axe", rows.apply(new ItemStack(Items.IRON_AXE)));
            want.put("axe", "traced=true sharpness=0 smite=0 bane=0 knockback=0 efficiency=9 fortune=5 unbreaking=3 mending=1");
            seen.put("shears", rows.apply(new ItemStack(Items.SHEARS)));
            want.put("shears", "traced=true sharpness=0 smite=0 bane=0 knockback=0 efficiency=0 fortune=0 unbreaking=3 mending=1");
            ItemStack book = new ItemStack(Items.BOOK);
            seen.put("book", rows.apply(book) + " traceable=" + TraceEnchantService.isTraceable(f.level.registryAccess(), book));
            want.put("book", "traced=false sharpness=0 smite=0 bane=0 knockback=0 efficiency=0 fortune=0 unbreaking=0 mending=0"
                    + " traceable=false");

            // the hand with nothing to take is passed over, in the condition and when it performs
            Inventory inventory = f.user.getInventory();
            f.user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            f.user.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_AXE));
            seen.put("hands", "condition=" + TicketToRideAbilityService.canUse(Mode.TRACE, f.user).isPositive()
                    + " traced=" + TicketToRideAbilityService.perform(f.level, f.user, f.power, Mode.TRACE)
                    + " stick=" + TraceEnchantService.isTraced(f.user.getMainHandItem())
                    + " axe=" + TraceEnchantService.isTraced(f.user.getOffhandItem()) + " axeSharpness="
                    + levelOf.apply(f.user.getOffhandItem(), Enchantments.SHARPNESS));
            want.put("hands", "condition=true traced=true stick=false axe=true axeSharpness=0");
            f.user.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            seen.put("none", "condition=" + TicketToRideAbilityService.canUse(Mode.TRACE, f.user).isPositive());
            want.put("none", "condition=false");
            inventory.clearContent();
            helper.assertTrue(seen.equals(want), "1.16's Trace On took the enchanting table rule: got " + seen + ", 1.16 " + want);
            LOGGER.info("R764_TTR_EXPLOD_PASS trace_table {}", seen);
        }
        helper.succeed();
    }

    // 1.16 PowerBaseImpl.performAction set the cooldown on every fire and StandEntityTask paid the stamina after
    // standPerform whatever it found: a Trace On whose item was swapped away before it performed only told the user
    // (TraceOnAction.standPerform :88-91, message.trace_on.none), and still cost its 750 and its 1200-tick cooldown.
    @GameTest(template = "empty", batch = "ttr_trace_swapped", timeoutTicks = 100)
    public static void traceOnWithItsItemSwappedAwayStillCostsStaminaAndCooldownAsIn116(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "TtrTraceSwapped", false)) {
            f.reset();
            EntityActionAbility trace = f.ability(TicketToRideAddon.TRACE);
            f.power.setAbilityCooldown(TicketToRideAddon.TRACE, 0);
            f.user.getInventory().clearContent();
            f.user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            helper.assertTrue(f.press(trace), "Trace On was refused with a sword in the hand");
            EntityActionInstance action = f.standAction.getAction();
            f.user.getInventory().clearContent();
            float stamina = f.power.getStamina();
            action.actionPerformStart();
            int cooldown = f.power.getAbilityCooldown(TicketToRideAddon.TRACE);
            String state = "stamina=" + stamina + "->" + f.power.getStamina() + ", cooldown=" + cooldown;
            helper.assertTrue(Math.abs(stamina - f.power.getStamina() - Math.min(750.0F, stamina)) < 0.01F
                            && cooldown == 1200,
                    "1.16 charged Trace On its 750 stamina and 1200-tick cooldown with no item left to trace: " + state);
            LOGGER.info("R764_TTR_EXPLOD_PASS trace_swapped {}", state);
        }
        helper.succeed();
    }

    // 1.16 ExplodAction, MobCrash, SummonTaggedMobsAction, SingleLightingHit, lightinghit and
    // SummonDeadphotomemtorAction had no condition of their own: with no marked mob around they charged, fired at
    // nothing and still cost their stamina and cooldown, and explod could be charged before its target was marked.
    @GameTest(template = "empty", batch = "ttr_no_mark", timeoutTicks = 100)
    public static void targetlessMovesChargeAndPayWithNoMarkedMob(GameTestHelper helper) {
        record Move(String name, int holdTicks, boolean firesOnRelease, float stamina, int cooldown) {}
        try (Fixture f = new Fixture(helper, "TtrNoMark", false)) {
            List<Move> moves = List.of(
                    new Move(TicketToRideAddon.EXPLOD, 20, true, 75.0F, 120),
                    new Move(TicketToRideAddon.LIGHTNING_SINGLE, 12, true, 75.0F, 120),
                    new Move(TicketToRideAddon.LIGHTNING, 30, true, 75.0F, 1200),
                    new Move(TicketToRideAddon.CRASH, 20, false, 150.0F, 0),
                    new Move(TicketToRideAddon.RANDOM_MOB, 20, false, 75.0F, 0),
                    new Move(TicketToRideAddon.METEOR, 20, false, 750.0F, 0));
            List<String> fired = new ArrayList<>();
            for (Move move : moves) {
                EntityActionAbility ability = f.ability(move.name());
                f.reset();
                f.power.setAbilityCooldown(move.name(), 0);
                ConditionCheck check = ability.checkConditions(f.power);
                helper.assertTrue(check.isPositive(),
                        "1.16 let " + move.name() + " be pressed with no marked mob around; it was refused with "
                                + check.getWarning());
                float stamina = f.power.getStamina();
                helper.assertTrue(f.press(ability), move.name() + " did not start with no marked mob around");
                for (int tick = 0; tick < move.holdTicks() + 2; tick++) {
                    f.standAction.tick();
                }
                if (move.firesOnRelease()) {
                    f.release(BRANCH_TNT);
                }
                int cooldown = f.power.getAbilityCooldown(move.name());
                String state = move.name() + " stamina=" + stamina + "->" + f.power.getStamina()
                        + " cooldown=" + cooldown;
                helper.assertTrue(Math.abs(stamina - f.power.getStamina() - Math.min(move.stamina(), stamina)) < 0.01F
                                && cooldown == move.cooldown(),
                        "1.16 charged and fired " + move.name() + " at nothing for its "
                                + move.stamina() + " stamina and " + move.cooldown() + "-tick cooldown: " + state);
                fired.add(state);
            }

            // pre-charged: the mob is marked only after the full charge, and the release still hits it
            f.reset();
            Vec3 ahead = f.ahead();
            helper.assertTrue(f.press(f.explod) && f.charge(CHARGE_TICKS),
                    "1.16 charged explod with no marked mob around; it did not");
            f.target(ahead);
            f.release(BRANCH_TNT);
            helper.assertTrue(f.effectsAt(ahead) == 1,
                    "1.16 explod charged before its target was marked hit it on release: effects=" + f.effectsAt(ahead));
            LOGGER.info("R764_TTR_EXPLOD_PASS no_mark fired={} precharged=hit", fired);
        }
        helper.succeed();
    }

    // 1.16 SummonTaggedMobsAction.standPerform took every marked entity in the user's box grown by 10 and did not
    // leave the user out: a user marked by another Ticket to Ride user's tears blade had the mobs summoned around
    // itself (StandAggroHandler then kept them off a Ticket to Ride user, as the port's onMobTargetChange does).
    // The user's own Stand was not marked, so no mob came for it.
    @GameTest(template = "empty", batch = "ttr_randommob_self", timeoutTicks = 100)
    public static void randomMobSummonsAroundAMarkedUser(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "TtrRandomMobSelf", false)) {
            f.mark(f.user);
            long seed = seedForMobType(5, 0);
            String result = f.randomMobs(seed, false, f.user.position());
            long atStand = f.joined.stream().filter(entity -> entity instanceof Mob mob && mob.getTarget() == f.stand)
                    .count();
            helper.assertTrue(result.startsWith("ok") && atStand == 0,
                    "1.16 summoned randommob's three mobs around its marked user, once, and none came for its Stand: "
                            + result + " at its Stand=" + atStand);
            LOGGER.info("R764_TTR_EXPLOD_PASS randommob_self mobs={} around_user={}", f.joinedOf(Mob.class), result);
        }
        helper.succeed();
    }

    // 1.16 SummonTaggedMobsAction: for each marked entity one type from MOB_TYPES (bee, phantom x2, cave spider, wolf,
    // silverfish x3) for all three mobs, each up to 10 blocks off on x and z, phantoms 10 blocks up, silverfish, cave
    // spiders and wolves 0.1 up, bees 5 up; a phantom is dropped by day, a bee is a baby half the time, every mob has
    // TEARS for good and no target of its own, the evoker cast-spell sound plays where it spawns, and the Stand plays
    // its unsummon sound once. All of it from the level's random.
    @GameTest(template = "empty", batch = "ttr_randommob_mobs", timeoutTicks = 100)
    public static void randomMobSpawnsThe116MobsAroundTheMarkedEntity(GameTestHelper helper) {
        record Case(String label, long seed, boolean night) {}
        try (Fixture f = new Fixture(helper, "TtrRandomMobMobs", false)) {
            Zombie target = f.target(f.ahead());
            List<Case> cases = new ArrayList<>(List.of(new Case("phantom_night", seedForMobType(1, 0), true),
                    new Case("phantom_day", seedForMobType(2, 0), false),
                    new Case("cave_spider", seedForMobType(3, 0), false),
                    new Case("wolf", seedForMobType(4, 0), false),
                    new Case("silverfish", seedForMobType(7, 0), false)));
            for (int i = 0; i < 4; i++) {
                cases.add(new Case("bee" + i, seedForMobType(0, i), false));
            }
            List<String> results = new ArrayList<>();
            int babies = 0;
            int adults = 0;
            for (Case c : cases) {
                String result = f.randomMobs(c.seed(), c.night(), target.position());
                helper.assertTrue(result.startsWith("ok"), "1.16 SummonTaggedMobsAction (" + c.label() + "): " + result);
                results.add(c.label() + ":" + result);
                for (Entity entity : f.joined) {
                    if (entity.getType() == EntityType.BEE) {
                        if (((Mob) entity).isBaby()) {
                            babies++;
                        }
                        else {
                            adults++;
                        }
                    }
                }
                f.discardJoined();
            }
            helper.assertTrue(babies > 0 && adults > 0,
                    "1.16 made a summoned bee a baby half the time: babies=" + babies + " adults=" + adults);
            LOGGER.info("R764_TTR_EXPLOD_PASS randommob_mobs bees={}/{} {}", babies, adults, results);
        }
        helper.succeed();
    }

    // 1.16 MobBehaviorHandler: a summoned mob lived 200 ticks, counted down on each of its updates; every 40 ticks
    // (tickCount % 40 == 0) the 200 started again if it could see a marked entity within 20 blocks, and it was removed
    // when they ran out.
    @GameTest(template = "empty", batch = "ttr_randommob_lifetime", timeoutTicks = 100)
    public static void misfortuneMobLifetimeStartsAgainEveryFortyTicksInSight(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "TtrRandomMobLifetime", false)) {
            Zombie target = f.target(f.ahead());
            String spawned = f.randomMobs(seedForMobType(5, 0), false, target.position());
            helper.assertTrue(spawned.startsWith("ok") && f.joinedOf(Mob.class) == 3, "Setup: randommob did not spawn: "
                    + spawned);
            Mob mob = (Mob) f.joined.stream().filter(Mob.class::isInstance).findFirst().orElseThrow();
            Vec3 at = target.position().add(0.0D, 0.0D, 4.0D);
            mob.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
            // the barrier walls of neighbouring test structures and village blocks must not cut the sight line
            f.clearArea(BlockPos.containing(target.position()).offset(-1, -1, -1), BlockPos.containing(at).offset(1, 3, 1));
            helper.assertTrue(mob.hasLineOfSight(target), "Setup: the mob cannot see the marked zombie: "
                    + sight(mob, target));

            int joinedWith = lifetime(mob);
            mob.tickCount = 39;
            mob.getPersistentData().putInt(MISFORTUNE_LIFETIME, 5);
            TicketToRideBehaviorService.tickMob(mob);
            int offBeat = lifetime(mob);
            mob.tickCount = 40;
            TicketToRideBehaviorService.tickMob(mob);
            int onBeat = lifetime(mob);

            BlockPos cell = BlockPos.containing(at);
            f.buildBlastBox(cell.offset(-1, -1, -1), cell.offset(1, 2, 1));
            helper.assertTrue(!mob.hasLineOfSight(target), "Setup: the shell does not hide the marked zombie");
            mob.tickCount = 80;
            mob.getPersistentData().putInt(MISFORTUNE_LIFETIME, 5);
            TicketToRideBehaviorService.tickMob(mob);
            int hidden = lifetime(mob);

            mob.tickCount = 81;
            mob.getPersistentData().putInt(MISFORTUNE_LIFETIME, 1);
            TicketToRideBehaviorService.tickMob(mob);
            String state = "joined=" + joinedWith + " offBeat=" + offBeat + " onBeat=" + onBeat + " hidden=" + hidden
                    + " removed=" + mob.isRemoved();
            helper.assertTrue(joinedWith == 200 && offBeat == 4 && onBeat == 200 && hidden == 4 && mob.isRemoved(),
                    "1.16 started a summoned mob's 200 ticks again only every 40 ticks and only with a marked entity in"
                            + " sight, and removed it when they ran out: " + state);
            LOGGER.info("R764_TTR_EXPLOD_PASS randommob_lifetime {}", state);
        }
        helper.succeed();
    }

    // 1.16 MobBehaviorHandler.onEntitySpawn and onLivingUpdate (:63-103): every mob that joined the world with TEARS, not
    // only randommob's, got MOB_TIMERS.put(mob, 200), a fresh 200 on every join (the map was never saved, so a mob
    // loaded from disk started again); a mob that joined without TEARS had no timer even if it got TEARS later; the
    // count only went down while the mob had TEARS, and the mob was removed when it reached 0.
    @GameTest(template = "empty", batch = "ttr_tears_lifetime", timeoutTicks = 100)
    public static void everyMobJoiningWithTearsGetsAFresh200TickLifetime(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "TtrTearsLifetime", false)) {
            Vec3 at = f.user.position().add(9.0D, 0.0D, 0.0D);
            Zombie given = f.join(f.created(EntityType.ZOMBIE, at, true));
            int onJoin = lifetime(given);
            TicketToRideBehaviorService.tickMob(given);
            int afterTick = lifetime(given);

            // saved with 5 left and loaded again: a fresh 200
            given.getPersistentData().putInt(MISFORTUNE_LIFETIME, 5);
            Zombie reloaded = f.reload(given);
            int onReload = lifetime(reloaded);

            // TEARS lost: the count waits; TEARS back: it goes on; at 0 the mob is removed
            reloaded.getPersistentData().putInt(MISFORTUNE_LIFETIME, 2);
            reloaded.removeEffect(TicketToRideAddon.TEARS);
            TicketToRideBehaviorService.tickMob(reloaded);
            int withoutTears = lifetime(reloaded);
            reloaded.addEffect(new MobEffectInstance(TicketToRideAddon.TEARS, MobEffectInstance.INFINITE_DURATION, 0,
                    false, false, true));
            TicketToRideBehaviorService.tickMob(reloaded);
            boolean aliveAtOne = !reloaded.isRemoved();
            TicketToRideBehaviorService.tickMob(reloaded);
            boolean removedAtZero = reloaded.isRemoved();

            // joined without TEARS: no lifetime, also after TEARS is given, and a saved count is dropped on the join
            Zombie plain = f.join(f.created(EntityType.ZOMBIE, at.add(0.0D, 0.0D, 3.0D), false));
            plain.addEffect(new MobEffectInstance(TicketToRideAddon.TEARS, MobEffectInstance.INFINITE_DURATION, 0,
                    false, false, true));
            for (int i = 0; i < 3; i++) {
                TicketToRideBehaviorService.tickMob(plain);
            }
            int plainLifetime = lifetime(plain);
            plain.removeEffect(TicketToRideAddon.TEARS);
            plain.getPersistentData().putInt(MISFORTUNE_LIFETIME, 1);
            Zombie plainReloaded = f.reload(plain);
            plainReloaded.addEffect(new MobEffectInstance(TicketToRideAddon.TEARS, MobEffectInstance.INFINITE_DURATION,
                    0, false, false, true));
            TicketToRideBehaviorService.tickMob(plainReloaded);
            String state = "join=" + onJoin + " tick=" + afterTick + " reload=" + onReload + " withoutTears="
                    + withoutTears + " aliveAt1=" + aliveAtOne + " removedAt0=" + removedAtZero + " plain=" + plainLifetime
                    + " plainReloaded=" + lifetime(plainReloaded) + "/removed=" + plainReloaded.isRemoved();
            helper.assertTrue(onJoin == 200 && afterTick == 199 && onReload == 200,
                    "1.16 gave every mob joining with TEARS a fresh 200-tick lifetime, also when it was loaded again: "
                            + state);
            helper.assertTrue(withoutTears == 2 && aliveAtOne && removedAtZero,
                    "1.16 counted the lifetime down only while the mob had TEARS and removed it at 0: " + state);
            helper.assertTrue(plainLifetime == -1 && lifetime(plainReloaded) == -1 && !plainReloaded.isRemoved(),
                    "1.16 gave no lifetime to a mob that joined without TEARS, even with TEARS later: " + state);
            LOGGER.info("R764_TTR_EXPLOD_PASS tears_lifetime {}", state);
        }
        helper.succeed();
    }

    /** The mob's misfortune lifetime, or -1 if it has none. */
    private static int lifetime(Mob mob) {
        return mob.getPersistentData().contains(MISFORTUNE_LIFETIME)
                ? mob.getPersistentData().getInt(MISFORTUNE_LIFETIME)
                : -1;
    }

    // 1.16 SummonDeadphotomemtorAction searched new AxisAlignedBB(standEntity.blockPosition()).inflate(128), and
    // SingleLightingHit.holdTick pulled from new AxisAlignedBB(user.blockPosition()).inflate(5): boxes of a block,
    // not of the user. The test puts marked zombies just inside one box and just outside the other.
    @GameTest(template = "empty", batch = "ttr_search_boxes", timeoutTicks = 100)
    public static void meteorAndPullSearchTheBoxesOfABlock(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "TtrSearchBoxes", false)) {
            Vec3 feet = f.user.position();
            helper.assertTrue(feet.y == Math.floor(feet.y), "Setup: the user does not stand on a block's floor: " + feet);
            f.listenToJoins();

            // the Stand 3 blocks up: its box reaches 132 above the user's feet, the user's own 129.8
            Zombie high = f.target(feet.add(0.0D, 130.5D, 0.0D));
            long aboveStand = f.meteorWithStandAt(feet.add(0.0D, 3.0D, 0.0D));
            f.remove(high);
            // the Stand 3 blocks down: its box reaches 126 above the user's feet
            Zombie low = f.target(feet.add(0.0D, 127.0D, 0.0D));
            long belowStand = f.meteorWithStandAt(feet.add(0.0D, -3.0D, 0.0D));
            f.remove(low);

            // the user's block box ends at x + 6 of the block, 0.2 past the user's box grown by 5; on y it ends at
            // 6, 0.8 short of the user's
            Zombie east = f.target(feet.add(5.7D, 0.0D, 0.0D));
            Zombie above = f.target(feet.add(0.0D, 6.5D, 0.0D));
            f.reset();
            helper.assertTrue(f.press(f.ability(TicketToRideAddon.LIGHTNING_SINGLE)), "lighting_single did not start");
            for (int tick = 0; tick < 15; tick++) {
                f.standAction.tick();
            }
            f.reset();
            String state = "meteor: Stand above=" + aboveStand + " Stand below=" + belowStand + "; pull: east="
                    + east.getDeltaMovement() + " above=" + above.getDeltaMovement();
            helper.assertTrue(aboveStand == 1 && belowStand == 0
                            && east.getDeltaMovement().lengthSqr() > 0.0D && above.getDeltaMovement().lengthSqr() == 0.0D,
                    "1.16 meteor searched the Stand's block box grown by 128 and lighting_single pulled from the user's"
                            + " block box grown by 5: " + state);
            LOGGER.info("R764_TTR_EXPLOD_PASS search_boxes {}", state);
        }
        helper.succeed();
    }

    // 1.16 SummonTaggedMobsAction.MOB_TYPES and calculateSpawnPos
    private static final List<EntityType<?>> MOB_TYPES_116 = List.of(EntityType.BEE, EntityType.PHANTOM,
            EntityType.PHANTOM, EntityType.CAVE_SPIDER, EntityType.WOLF, EntityType.SILVERFISH, EntityType.SILVERFISH,
            EntityType.SILVERFISH);
    private static final String MISFORTUNE_LIFETIME = "rotp_ttr.MisfortuneLifetime";
    private static final String SUMMON_OWNER = "rotp_ttr.SummonOwner";

    /** The skip-th seed whose first level random draw picks this entry of MOB_TYPES. */
    private static long seedForMobType(int index, int skip) {
        long seed = 0L;
        for (int found = -1; ; seed++) {
            if (RandomSource.create(seed).nextInt(MOB_TYPES_116.size()) == index && ++found == skip) {
                return seed;
            }
        }
    }

    // 1.16 TearsBlade.isEntityMarked read each entity's own data, so a marked user's Stand carried no mark:
    // lighting (all), crash and meteor, which leave the user out, found nothing, and a mob looking for the
    // nearest marked entity did not go for the Stand.
    @GameTest(template = "empty", batch = "ttr_stand_mark", timeoutTicks = 100)
    public static void aMarkedUsersStandCarriesNoMark(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "TtrStandMark", false)) {
            f.mark(f.user);
            // behind its user, who looks east
            f.stand.moveTo(f.user.getX() - 1.5D, f.user.getY(), f.user.getZ(), -90.0F, 0.0F);
            boolean standMarked = TearsWoundService.isMarked(f.stand);
            f.listenToJoins();
            f.use(TicketToRideAddon.LIGHTNING, 30, true);
            long bolts = f.joinedOf(LightningBolt.class);
            f.use(TicketToRideAddon.CRASH, 20, false);
            long horses = f.joinedOf(Horse.class);
            f.use(TicketToRideAddon.METEOR, 20, false);
            long mementos = f.joinedOf(DeadPhotoMementoEntity.class);
            String fired = "lighting=" + bolts + " crash=" + horses + " meteor=" + mementos;

            Vec3 away = f.stand.position().subtract(f.user.position());
            helper.assertTrue(away.lengthSqr() > 1.0E-4D, "Setup: the Stand stands where its user does");
            Zombie bystander = f.unmarkedZombie(f.stand.position().add(away.normalize().scale(0.5D)));
            helper.assertTrue(bystander.distanceToSqr(f.stand) < bystander.distanceToSqr(f.user),
                    "Setup: the bystander is not nearer to the Stand than to the user");
            TicketToRideBehaviorService.tickMob(bystander);
            String state = "standMarked=" + standMarked + " " + fired + " bystanderTarget=" + bystander.getTarget();
            helper.assertTrue(!standMarked && bolts + horses + mementos == 0 && bystander.getTarget() != f.stand,
                    "1.16 read the Stand's own data: a marked user's Stand is not marked and nothing came for it: "
                            + state);
            LOGGER.info("R764_TTR_EXPLOD_PASS stand_mark {}", state);
        }
        helper.succeed();
    }

    // 1.16 PlayerEntity.attack called the item's hurtEnemy only after the target's hurt returned true, and for a
    // Stand it never did: GameplayEventHandler.onLivingHurtStart cancelled the hit and dealt it again through a
    // ModdedDamageSourceWrapper. So TearsBlade.hurtEnemy (:108-119) spent no round and marked nobody.
    @GameTest(template = "empty", batch = "ttr_blade_stand", timeoutTicks = 100)
    public static void tearsBladeHitOnAStandSpendsNoRoundAndMarksNobody(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "TtrBladeStand", false);
                Fixture other = new Fixture(helper, "TtrBladeStandOther", true)) {
            other.user.moveTo(f.user.getX(), f.user.getY(), f.user.getZ() + 3.0D, 90.0F, 0.0F);
            other.stand.moveTo(other.user.getX(), other.user.getY(), other.user.getZ(), 90.0F, 0.0F);
            // ten rounds: 1.16 handed the blade out empty
            ItemStack loaded = TearsBladeItem.createOwned(f.user);
            loaded.set(TicketToRideAddon.TEARS_BLADE_DATA.get(),
                    loaded.get(TicketToRideAddon.TEARS_BLADE_DATA.get()).withAmmo(10));
            f.user.setItemInHand(InteractionHand.MAIN_HAND, loaded);
            f.user.addEffect(new MobEffectInstance(ModStatusEffects.INTEGRATED_STAND, 200, 0, false, false, true));
            helper.assertTrue(TearsBladeItem.isActiveOwned(f.user.getMainHandItem(), f.user),
                    "Setup: the user does not hold its active Tears Blade");

            f.user.attack(other.stand);
            boolean landed = f.user.getLastHurtMob() == other.stand;
            String onStand = "landed=" + landed + " ammo=" + TearsBladeItem.getAmmo(f.user.getMainHandItem())
                    + " userMarked=" + TearsWoundService.isMarked(other.user)
                    + " standMarked=" + TearsWoundService.isMarked(other.stand);
            helper.assertTrue(landed, "Setup: the blade hit on the Stand did not land: " + onStand);
            helper.assertTrue(TearsBladeItem.getAmmo(f.user.getMainHandItem()) == 10
                            && !TearsWoundService.isMarked(other.user) && !TearsWoundService.isMarked(other.stand),
                    "1.16 never reached TearsBlade.hurtEnemy for a Stand, so no round was spent and nobody was marked: "
                            + onStand);

            Zombie zombie = f.unmarkedZombie(f.user.position().add(1.5D, 0.0D, 0.0D));
            f.user.attack(zombie);
            String onZombie = "ammo=" + TearsBladeItem.getAmmo(f.user.getMainHandItem())
                    + " marked=" + TearsWoundService.isMarked(zombie);
            helper.assertTrue(TearsBladeItem.getAmmo(f.user.getMainHandItem()) == 9
                            && TearsWoundService.isMarked(zombie),
                    "Setup: a blade hit on a zombie did not spend a round and mark it: " + onZombie);
            LOGGER.info("R764_TTR_EXPLOD_PASS blade_stand stand[{}] zombie[{}]", onStand, onZombie);
        }
        helper.succeed();
    }

    // 1.16 MobBehaviorHandler.onEntitySpawn and modifyMobAI (:63-76, :119-133): a mob that joined the level with
    // TEARS kept its own target goals (clearAllGoals removed only the running ones, and none runs at the join) and
    // got a NearestAttackableTargetGoal at priority 2 for marked entities if it was a monster, and one at priority 3
    // for any living entity but Ticket to Ride's Stand. A mob without TEARS got nothing. Both goals pick the nearest
    // entity the mob sees (1.16 EntityPredicate and 1.21 TargetingConditions check the line of sight).
    @GameTest(template = "empty", batch = "ttr_tears_ai", timeoutTicks = 100)
    public static void mobJoiningWithTearsGetsThe116TargetGoals(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "TtrTearsAi", false)) {
            Vec3 at = f.user.position().add(9.0D, 0.0D, 0.0D);
            Zombie plain = f.created(EntityType.ZOMBIE, at, false);
            GoalChange plainChange = GoalChange.onJoin(f, plain);
            plain.discard();
            Mob wolf = f.created(EntityType.WOLF, at, true);
            GoalChange wolfChange = GoalChange.onJoin(f, wolf);
            wolf.discard();
            Zombie zombie = f.created(EntityType.ZOMBIE, at, true);
            GoalChange zombieChange = GoalChange.onJoin(f, zombie);
            String goals = "plain zombie " + plainChange + ", wolf " + wolfChange + ", zombie " + zombieChange;
            helper.assertTrue(plainChange.toString().equals("added=[] removed=[]")
                            && wolfChange.toString().equals("added=[3:NearestAttackableTargetGoal] removed=[]")
                            && zombieChange.toString().equals(
                                    "added=[2:NearestAttackableTargetGoal, 3:NearestAttackableTargetGoal] removed=[]"),
                    "1.16 kept the target goals of a mob joining with TEARS and added priority 2 (monsters) and 3: "
                            + goals);

            // a marked cow 2.5 blocks off behind obsidian, an unmarked one 4 off, a marked one 6 off in sight
            Vec3 hiddenAt = at.add(-2.5D, 0.0D, 0.0D);
            // the barrier walls of neighbouring test structures and village blocks must not cut the sight lines
            f.clearArea(BlockPos.containing(at).offset(-1, 0, -5), BlockPos.containing(at).offset(1, 3, 6));
            BlockPos cell = BlockPos.containing(hiddenAt);
            f.buildBlastBox(cell.offset(-1, -1, -1), cell.offset(1, 2, 1));
            Mob hidden = f.join(f.created(EntityType.COW, hiddenAt, false));
            Mob unmarked = f.join(f.created(EntityType.COW, at.add(0.0D, 0.0D, -4.0D), false));
            Mob seen = f.join(f.created(EntityType.COW, at.add(0.0D, 0.0D, 6.0D), false));
            f.mark(hidden);
            f.mark(seen);
            helper.assertTrue(!zombie.hasLineOfSight(hidden) && zombie.hasLineOfSight(seen)
                            && zombie.hasLineOfSight(unmarked),
                    "Setup: the zombie sees the hidden cow or not the ones in sight: hidden " + sight(zombie, hidden)
                            + ", seen " + sight(zombie, seen) + ", unmarked " + sight(zombie, unmarked));
            LivingEntity byMarked = firstTarget(zombie, zombieChange.added().get(0));
            LivingEntity byAny = firstTarget(zombie, zombieChange.added().get(1));
            String picked = "priority2=" + describeTarget(byMarked, hidden, unmarked, seen)
                    + " priority3=" + describeTarget(byAny, hidden, unmarked, seen);
            helper.assertTrue(byMarked == seen && byAny == unmarked,
                    "1.16 priority 2 took the nearest marked entity in sight and priority 3 the nearest living one in"
                            + " sight: " + picked);
            LOGGER.info("R764_TTR_EXPLOD_PASS tears_ai {}; {}", goals, picked);
        }
        helper.succeed();
    }

    // 1.16 MobBehaviorHandler.onLivingAttack (:105-117): a mob with TEARS attacking a marked entity gave it three of
    // NEGATIVE_EFFECTS (:61: slowness 200/1, weakness 200/0, poison 100/0, nausea 300/0, blindness 200/0, wither
    // 200/0), shuffled. An unmarked target, or an attacker without TEARS, got nothing. The attacker's random is seeded
    // before each hit, so every hit is checked against the shuffle that seed makes.
    @GameTest(template = "empty", batch = "ttr_tears_hit", timeoutTicks = 100)
    public static void tearsMobHitGivesAMarkedTargetThreeOfThe116Effects(GameTestHelper helper) {
        Map<Holder<MobEffect>, String> effects116 = Map.of(MobEffects.MOVEMENT_SLOWDOWN, "200/1",
                MobEffects.WEAKNESS, "200/0", MobEffects.POISON, "100/0", MobEffects.CONFUSION, "300/0",
                MobEffects.BLINDNESS, "200/0", MobEffects.WITHER, "200/0");
        try (Fixture f = new Fixture(helper, "TtrTearsHit", false)) {
            Vec3 at = f.user.position().add(9.0D, 0.0D, 0.0D);
            Zombie tearsMob = f.join(f.created(EntityType.ZOMBIE, at, true));
            // as a randommob summon is
            tearsMob.getPersistentData().putUUID(SUMMON_OWNER, f.user.getUUID());
            Zombie plainMob = f.join(f.created(EntityType.ZOMBIE, at.add(0.0D, 0.0D, 3.0D), false));
            long[] seeds = {1L, 2L, 3L, 4L};
            Set<String> drawn = new TreeSet<>();
            for (long seed : seeds) {
                drawn.addAll(shuffled116(seed).subList(0, 3));
            }
            helper.assertTrue(drawn.size() > 3, "Setup: the four seeds draw the same three effects: " + drawn);
            List<String> hits = new ArrayList<>();
            boolean shuffled = true;
            for (int i = 0; i < seeds.length; i++) {
                Mob cow = f.join(f.created(EntityType.COW, at.add(2.0D, 0.0D, i - 2.0D), false));
                f.mark(cow);
                tearsMob.getRandom().setSeed(seeds[i]);
                cow.hurt(tearsMob.damageSources().mobAttack(tearsMob), 1.0F);
                String got = effectsOf(cow);
                String expected = shuffled116(seeds[i]).subList(0, 3).stream().sorted().toList().toString();
                hits.add(got + (got.equals(expected) ? "" : " (1.16: " + expected + ")"));
                boolean ok = cow.getActiveEffects().size() == 3 && cow.getTicksFrozen() == 0
                        && cow.getActiveEffects().stream().allMatch(effect -> (effect.getDuration() + "/"
                                + effect.getAmplifier()).equals(effects116.get(effect.getEffect())));
                helper.assertTrue(ok, "1.16 gave a marked entity hit by a TEARS mob three of its six effects: " + got);
                shuffled &= got.equals(expected);
            }
            Mob unmarked = f.join(f.created(EntityType.COW, at.add(-2.0D, 0.0D, 0.0D), false));
            unmarked.hurt(tearsMob.damageSources().mobAttack(tearsMob), 1.0F);
            Mob byPlain = f.join(f.created(EntityType.COW, at.add(-2.0D, 0.0D, 2.0D), false));
            f.mark(byPlain);
            byPlain.hurt(plainMob.damageSources().mobAttack(plainMob), 1.0F);
            String others = "unmarked=" + effectsOf(unmarked) + " frozen=" + unmarked.getTicksFrozen()
                    + " byMobWithoutTears=" + effectsOf(byPlain) + " frozen=" + byPlain.getTicksFrozen();
            helper.assertTrue(unmarked.getActiveEffects().isEmpty() && unmarked.getTicksFrozen() == 0
                            && byPlain.getActiveEffects().isEmpty() && byPlain.getTicksFrozen() == 0,
                    "1.16 gave nothing to an unmarked target or for a mob without TEARS: " + others);
            helper.assertTrue(shuffled, "1.16 shuffled the six effects for every hit: " + hits);
            LOGGER.info("R764_TTR_EXPLOD_PASS tears_hit {} {} drawn={}", hits, others, drawn);
        }
        helper.succeed();
    }

    // 1.16 MobBehaviorHandler.onLivingUpdate and applyPassiveEffects (:96-101, :139-144): on the 40-tick renewal of its
    // timer (a mob that joined with TEARS and sees a marked entity within 20 blocks), a mob of PASSIVE_MOBS (slime, squid,
    // dolphin) gave that entity one of NEGATIVE_EFFECTS, shuffled, but only a SlimeEntity did it. A magma cube is a slime
    // but not of PASSIVE_MOBS; a slime given TEARS after it joined had no timer.
    @GameTest(template = "empty", batch = "ttr_tears_slime", timeoutTicks = 100)
    public static void tearsSlimeGivesTheMarkedEntityItSeesOneEffectEveryFortyTicks(GameTestHelper helper) {
        record Case(String label, EntityType<? extends Mob> type, boolean tearsAtJoin, int tickCount, boolean hidden,
                long seed, boolean gives) {}
        long witherSeed = seedWhereFirstIs("minecraft:wither/200/0");
        long poisonSeed = seedWhereFirstIs("minecraft:poison/100/0");
        List<Case> cases = List.of(
                new Case("slime_wither", EntityType.SLIME, true, 40, false, witherSeed, true),
                new Case("slime_poison", EntityType.SLIME, true, 80, false, poisonSeed, true),
                new Case("slime_off_beat", EntityType.SLIME, true, 39, false, witherSeed, false),
                new Case("slime_hidden", EntityType.SLIME, true, 40, true, witherSeed, false),
                new Case("slime_tears_after_join", EntityType.SLIME, false, 40, false, witherSeed, false),
                new Case("squid", EntityType.SQUID, true, 40, false, witherSeed, false),
                new Case("dolphin", EntityType.DOLPHIN, true, 40, false, witherSeed, false),
                new Case("magma_cube", EntityType.MAGMA_CUBE, true, 40, false, witherSeed, false));
        try (Fixture f = new Fixture(helper, "TtrTearsSlime", false)) {
            Vec3 at = f.user.position().add(9.0D, 0.0D, 0.0D);
            List<String> results = new ArrayList<>();
            boolean ok = true;
            for (Case c : cases) {
                Mob mob = f.join(f.created(c.type(), at, c.tearsAtJoin()));
                if (!c.tearsAtJoin()) {
                    mob.addEffect(new MobEffectInstance(TicketToRideAddon.TEARS, MobEffectInstance.INFINITE_DURATION, 0,
                            false, false, true));
                }
                Vec3 cowAt = at.add(0.0D, 0.0D, 4.0D);
                // the classic flat test world can have a village building here
                f.clearArea(BlockPos.containing(at).offset(-2, 0, -2), BlockPos.containing(cowAt).offset(2, 3, 2));
                if (c.hidden()) {
                    BlockPos cell = BlockPos.containing(cowAt);
                    f.buildBlastBox(cell.offset(-1, -1, -1), cell.offset(1, 2, 1));
                }
                Mob cow = f.join(f.created(EntityType.COW, cowAt, false));
                f.mark(cow);
                helper.assertTrue(mob.hasLineOfSight(cow) != c.hidden(),
                        "Setup (" + c.label() + "): the mob's sight of the marked cow is not as planned: " + sight(mob, cow));
                mob.tickCount = c.tickCount();
                mob.getRandom().setSeed(c.seed());
                TicketToRideBehaviorService.tickMob(mob);
                String expected = c.gives() ? "[" + shuffled116(c.seed()).get(0) + "]" : "[]";
                String got = effectsOf(cow);
                ok &= got.equals(expected);
                results.add(c.label() + "=" + got + (got.equals(expected) ? "" : " (1.16: " + expected + ")"));
                cow.discard();
                mob.discard();
                f.restoreBlocks();
            }
            helper.assertTrue(ok, "1.16 had only a slime with a timer give the marked entity it saw one shuffled effect on"
                    + " the 40-tick beat: " + results);
            LOGGER.info("R764_TTR_EXPLOD_PASS tears_slime {}", results);
        }
        helper.succeed();
    }

    private static String sight(LivingEntity from, LivingEntity to) {
        Vec3 eyes = new Vec3(from.getX(), from.getEyeY(), from.getZ());
        Vec3 target = new Vec3(to.getX(), to.getEyeY(), to.getZ());
        BlockHitResult hit = from.level().clip(new ClipContext(eyes, target, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, from));
        return "from " + eyes + " to " + target + " hit " + hit.getType() + " at " + hit.getBlockPos() + " "
                + from.level().getBlockState(hit.getBlockPos());
    }

    // 1.16 MobBehaviorHandler.NEGATIVE_EFFECTS (:61), in its order, as effectsOf prints them.
    private static final List<String> NEGATIVE_EFFECTS_116 = List.of("minecraft:slowness/200/1",
            "minecraft:weakness/200/0", "minecraft:poison/100/0", "minecraft:nausea/300/0", "minecraft:blindness/200/0",
            "minecraft:wither/200/0");

    /** NEGATIVE_EFFECTS shuffled by a random with this seed, as the port draws them. */
    private static List<String> shuffled116(long seed) {
        List<String> effects = new ArrayList<>(NEGATIVE_EFFECTS_116);
        Util.shuffle(effects, RandomSource.create(seed));
        return effects;
    }

    private static long seedWhereFirstIs(String effect) {
        long seed = 1L;
        while (!shuffled116(seed).get(0).equals(effect)) {
            seed++;
        }
        return seed;
    }

    // 1.16 StandAggroHandler (:58-126): onLivingSetAttackTarget ran after a mob's target was set; if it was a player
    // with Ticket to Ride whom the mob had not been hurt by (getRevengeTarget) and who had not last hit the mob
    // (getLastAttackedEntity), the target was set to null, unless the mob was an aggressive piglin, a zombified piglin
    // hurt by that player, an angry wolf or a bee (its target was already the player); then piglins, zombified piglins
    // (24 blocks), wolves (16) and bees (20) of the same kind that targeted the player dropped it too. onLivingAttack
    // made any mob hit by any player target that player.
    @GameTest(template = "empty", batch = "ttr_aggro", timeoutTicks = 100)
    public static void mobsDropATicketUserTheyHaveNotFoughtAndTurnToAPlayerWhoHitsThem(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "TtrAggro", false)) {
            ServerPlayer plain = FakePlayerFactory.get(f.level, new GameProfile(UUID.randomUUID(), "TtrAggroPlain"));
            plain.moveTo(f.user.getX(), f.user.getY(), f.user.getZ() + 2.0D, -90.0F, 0.0F);
            Vec3 at = f.user.position().add(4.0D, 0.0D, 0.0D);
            Mob cow = f.join(f.created(EntityType.COW, at.add(0.0D, 0.0D, 5.0D), false));
            Map<String, String> seen = new LinkedHashMap<>();
            Map<String, String> want = new LinkedHashMap<>();

            Zombie zombie = f.join(f.created(EntityType.ZOMBIE, at, false));
            zombie.setTarget(cow);
            zombie.setTarget(f.user);
            seen.put("zombie", targetName(zombie, f.user, plain, cow));
            want.put("zombie", "none");

            Zombie hitBy = f.join(f.created(EntityType.ZOMBIE, at.add(0.0D, 0.0D, 1.0D), false));
            f.user.setLastHurtMob(hitBy);
            hitBy.setTarget(f.user);
            f.user.setLastHurtMob(null);
            seen.put("lastHitByUser", targetName(hitBy, f.user, plain, cow));
            want.put("lastHitByUser", "user");

            Zombie hurtBy = f.join(f.created(EntityType.ZOMBIE, at.add(0.0D, 0.0D, 2.0D), false));
            hurtBy.setLastHurtByMob(f.user);
            hurtBy.setTarget(f.user);
            seen.put("hurtByUser", targetName(hurtBy, f.user, plain, cow));
            want.put("hurtByUser", "user");

            Zombie byPlain = f.join(f.created(EntityType.ZOMBIE, at.add(0.0D, 0.0D, 3.0D), false));
            byPlain.setTarget(plain);
            seen.put("plainPlayer", targetName(byPlain, f.user, plain, cow));
            want.put("plainPlayer", "plain");

            Mob bee = f.join(f.created(EntityType.BEE, at.add(0.0D, 2.0D, 0.0D), false));
            bee.setTarget(f.user);
            seen.put("bee", targetName(bee, f.user, plain, cow));
            want.put("bee", "user");

            Wolf angry = f.join(f.created(EntityType.WOLF, at.add(2.0D, 0.0D, 0.0D), false));
            angry.setRemainingPersistentAngerTime(400);
            angry.setTarget(f.user);
            seen.put("angryWolf", targetName(angry, f.user, plain, cow));
            want.put("angryWolf", "user");

            // a calm wolf turning to the user takes the angry one's target away too; a zombie's target is not a group's
            Wolf calm = f.join(f.created(EntityType.WOLF, at.add(2.0D, 0.0D, 2.0D), false));
            calm.setTarget(f.user);
            seen.put("calmWolf", targetName(calm, f.user, plain, cow));
            want.put("calmWolf", "none");
            seen.put("angryWolfAfterGroup", targetName(angry, f.user, plain, cow));
            want.put("angryWolfAfterGroup", "none");
            seen.put("hurtByUserAfterGroup", targetName(hurtBy, f.user, plain, cow));
            want.put("hurtByUserAfterGroup", "user");

            // hits: any player's hit turns the mob to that player, then the Ticket to Ride rule applies
            Mob hitCow = f.join(f.created(EntityType.COW, at.add(-2.0D, 0.0D, 2.0D), false));
            plain.attack(hitCow);
            seen.put("cowHitByPlain", targetName(hitCow, f.user, plain, cow));
            want.put("cowHitByPlain", "plain");
            Zombie hitZombie = f.join(f.created(EntityType.ZOMBIE, at.add(-2.0D, 0.0D, -2.0D), false));
            f.user.attack(hitZombie);
            boolean landed = hitZombie.getLastHurtByMob() == f.user;
            seen.put("zombieHitByUser", targetName(hitZombie, f.user, plain, cow));
            want.put("zombieHitByUser", "none");
            hitZombie.setTarget(f.user);
            seen.put("zombieHitByUserThenTargets", targetName(hitZombie, f.user, plain, cow));
            want.put("zombieHitByUserThenTargets", "user");

            // brain attackers pick their target in StartAttacking; 1.16's setAttackTarget(null) never reached the
            // ATTACK_TARGET memory, so they went on attacking the user
            helper.assertTrue(f.level.getDifficulty() != Difficulty.PEACEFUL, "Setup: brain mobs attack nobody on peaceful");
            for (EntityType<? extends Mob> type : List.of(EntityType.HOGLIN, EntityType.PIGLIN)) {
                Mob brainMob = f.join(f.created(type, at.add(-4.0D, 0.0D, 0.0D), false));
                boolean started = StartAttacking.<Mob>create(mob -> Optional.of(f.user))
                        .tryStart(f.level, brainMob, f.level.getGameTime());
                LivingEntity attacked = brainMob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
                String brainKey = type.toShortString() + "Brain";
                seen.put(brainKey, (started ? "" : "refused/") + (attacked == null ? "none"
                        : attacked == f.user ? "user" : attacked.getType().toShortString()));
                want.put(brainKey, "user");
                brainMob.discard();
            }

            helper.assertTrue(landed, "Setup: the user's hit on the zombie did not land: " + seen);
            helper.assertTrue(seen.equals(want), "1.16 StandAggroHandler: mobs drop a Ticket to Ride user they have not"
                    + " fought (to no target, with their group) and turn to any player who hits them: got " + seen
                    + ", 1.16 " + want);
            LOGGER.info("R764_TTR_EXPLOD_PASS aggro {}", seen);
        }
        helper.succeed();
    }

    private static String targetName(Mob mob, Player user, Player plain, LivingEntity cow) {
        LivingEntity target = mob.getTarget();
        return target == null ? "none" : target == user ? "user" : target == plain ? "plain" : target == cow ? "cow"
                : target.getType().toShortString();
    }

    // 1.16 TearsEffectEventHandler.onMobUpdate (:126-135): on every update of every mob, its target became the nearest
    // marked entity within 20 blocks but itself, summoned or not, on any tick.
    @GameTest(template = "empty", batch = "ttr_retarget", timeoutTicks = 100)
    public static void everyMobTurnsToTheNearestMarkedEntityEveryTick(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "TtrRetarget", false)) {
            Vec3 at = f.user.position().add(9.0D, 0.0D, 0.0D);
            Zombie plain = f.join(f.created(EntityType.ZOMBIE, at, false));
            Mob near = f.join(f.created(EntityType.COW, at.add(0.0D, 0.0D, 4.0D), false));
            Mob far = f.join(f.created(EntityType.COW, at.add(0.0D, 0.0D, -6.0D), false));
            f.mark(near);
            f.mark(far);
            List<String> ticks = new ArrayList<>();
            boolean ok = true;
            for (int tick = 1; tick <= 3; tick++) {
                plain.tickCount = tick;
                // the far cow comes nearest on the second tick, and the near one is nearest again on the third
                far.moveTo(at.x, at.y, at.z + (tick == 2 ? -2.0D : -6.0D), 0.0F, 0.0F);
                TicketToRideBehaviorService.tickMob(plain);
                Mob expected = tick == 2 ? far : near;
                ok &= plain.getTarget() == expected;
                ticks.add(tick + ":" + (plain.getTarget() == near ? "near" : plain.getTarget() == far ? "far"
                        : String.valueOf(plain.getTarget())));
            }
            helper.assertTrue(ok, "1.16 turned every mob to the nearest marked entity on every tick: " + ticks);
            LOGGER.info("R764_TTR_EXPLOD_PASS retarget {}", ticks);
        }
        helper.succeed();
    }

    // Cost of the 1.16 retarget (TearsEffectEventHandler.onMobUpdate: every mob, every tick, a search of its box grown by
    // 20 for marked entities): a level with no marked entity runs no search at all; once a mark exists the search and
    // the 1.16 retarget run as before; the window closes the tick after the last mark is gone and opens again when a
    // marked entity is loaded. Timings are logged, not asserted.
    @GameTest(template = "empty", batch = "ttr_idle_cost", timeoutTicks = 100)
    public static void mobTicksSearchForMarkedEntitiesOnlyWhileTheLevelHasOne(GameTestHelper helper) {
        Fixture f = new Fixture(helper, "TtrIdleCost", false);
        Map<String, String> seen = new LinkedHashMap<>();
        Zombie[] zombie = new Zombie[1];
        Mob[] cow = new Mob[1];
        CompoundTag[] saved = new CompoundTag[1];
        helper.runAfterDelay(1, () -> f.guard(() -> {
            List<LivingEntity> leftover = new ArrayList<>();
            for (Entity entity : f.level.getAllEntities()) {
                if (entity instanceof LivingEntity living && TearsWoundService.isMarked(living)) {
                    leftover.add(living);
                }
            }
            helper.assertTrue(leftover.isEmpty(), "Setup: marked entities are left in the level: " + leftover);
            Vec3 at = f.user.position().add(9.0D, 0.0D, 0.0D);
            zombie[0] = f.join(f.created(EntityType.ZOMBIE, at, false));
            // on its 40-tick beat a mob with a misfortune lifetime also looks for a marked entity in sight
            Zombie tears = f.join(f.created(EntityType.ZOMBIE, at.add(0.0D, 0.0D, -3.0D), true));
            tears.tickCount = 40;
            long before = TicketToRideBehaviorService.markedSearches();
            for (int i = 0; i < 5; i++) {
                TicketToRideBehaviorService.tickMob(zombie[0]);
            }
            TicketToRideBehaviorService.tickMob(tears);
            tears.discard();
            seen.put("noMark", "searches=" + (TicketToRideBehaviorService.markedSearches() - before) + " anyMarked="
                    + TearsWoundService.anyMarked(f.level));
            seen.put("idleNanosPerTick", String.valueOf(nanosPerTick(zombie[0], 20000)));

            cow[0] = f.join(f.created(EntityType.COW, at.add(0.0D, 0.0D, 4.0D), false));
            f.mark(cow[0]);
            before = TicketToRideBehaviorService.markedSearches();
            TicketToRideBehaviorService.tickMob(zombie[0]);
            seen.put("marked", "searches=" + (TicketToRideBehaviorService.markedSearches() - before) + " target="
                    + targetName(zombie[0], f.user, f.user, cow[0]));
            seen.put("markedNanosPerTick", String.valueOf(nanosPerTick(zombie[0], 20000)));

            saved[0] = cow[0].saveWithoutId(new CompoundTag());
            saved[0].putString("id", EntityType.getKey(EntityType.COW).toString());
            saved[0].remove("UUID");
            cow[0].discard();
            zombie[0].setTarget(null);
            before = TicketToRideBehaviorService.markedSearches();
            TicketToRideBehaviorService.tickMob(zombie[0]);
            seen.put("goneSameTick", "searches=" + (TicketToRideBehaviorService.markedSearches() - before));
        }));
        helper.runAfterDelay(2, () -> f.guard(() -> {
            long before = TicketToRideBehaviorService.markedSearches();
            TicketToRideBehaviorService.tickMob(zombie[0]);
            seen.put("goneNextTick", "searches=" + (TicketToRideBehaviorService.markedSearches() - before) + " anyMarked="
                    + TearsWoundService.anyMarked(f.level));

            Entity loaded = EntityType.create(saved[0], f.level).orElse(null);
            helper.assertTrue(loaded instanceof Mob, "Setup: the saved marked cow did not load");
            cow[0] = f.join((Mob) loaded);
            before = TicketToRideBehaviorService.markedSearches();
            TicketToRideBehaviorService.tickMob(zombie[0]);
            seen.put("reloaded", "searches=" + (TicketToRideBehaviorService.markedSearches() - before) + " target="
                    + targetName(zombie[0], f.user, f.user, cow[0]));
            Map<String, String> want = new LinkedHashMap<>(seen);
            want.put("noMark", "searches=0 anyMarked=false");
            want.put("marked", "searches=1 target=cow");
            want.put("goneSameTick", "searches=1");
            want.put("goneNextTick", "searches=0 anyMarked=false");
            want.put("reloaded", "searches=1 target=cow");
            helper.assertTrue(seen.equals(want), "Mob ticks must search for marked entities only while the level may"
                    + " have one, and then turn to it as 1.16 did: got " + seen + ", want " + want);
            LOGGER.info("R764_TTR_EXPLOD_PASS idle_cost {}", seen);
            f.close();
            helper.succeed();
        }));
    }

    // The per-level mark window above must not keep a level after it unloads: a single-player server runs on across
    // the worlds it opens. Unloading the nether drops its window and leaves the overworld's alone.
    @GameTest(template = "empty", batch = "ttr_level_unload", timeoutTicks = 100)
    public static void markWindowsLetALevelGoWhenItUnloads(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "TtrLevelUnload", false)) {
            ServerLevel nether = f.level.getServer().getLevel(Level.NETHER);
            helper.assertTrue(nether != null && nether != f.level, "Setup: the test server has no nether level");
            Mob away = EntityType.COW.create(nether);
            helper.assertTrue(away != null, "Setup: could not create a cow in the nether");
            f.mark(away);
            f.target(f.ahead());
            boolean subscribed;
            try {
                subscribed = TicketToRideEvents.class.getMethod("onLevelUnload", LevelEvent.Unload.class)
                        .isAnnotationPresent(SubscribeEvent.class);
            }
            catch (NoSuchMethodException missing) {
                subscribed = false;
            }
            String before = "nether=" + TearsWoundService.holdsWindow(nether) + " overworld="
                    + TearsWoundService.holdsWindow(f.level);
            TicketToRideEvents.onLevelUnload(new LevelEvent.Unload(nether));
            String after = "nether=" + TearsWoundService.holdsWindow(nether) + " overworld="
                    + TearsWoundService.holdsWindow(f.level);
            String state = "subscribed=" + subscribed + " before[" + before + "] after[" + after + "]";
            helper.assertTrue(subscribed && before.equals("nether=true overworld=true")
                            && after.equals("nether=false overworld=true"),
                    "An unloading level must leave the mark windows at once, and only that level: " + state);
            LOGGER.info("R764_TTR_EXPLOD_PASS level_unload {}", state);
        }
        helper.succeed();
    }

    /** Average nanoseconds of one tickMob call over the given number of calls. */
    private static long nanosPerTick(Mob mob, int calls) {
        long started = System.nanoTime();
        for (int i = 0; i < calls; i++) {
            TicketToRideBehaviorService.tickMob(mob);
        }
        return (System.nanoTime() - started) / calls;
    }

    // 1.16 SingleLightingHit.holdTick (:116-120): every 10 held ticks applyKnockback(0.2f, user.x - e.x, user.z - e.z)
    // on each marked entity near the user. LivingEntity.applyKnockback subtracts the normalized ratio, so it pushed
    // them away from the user; it fired the knockback event, honoured knockback resistance, gave min(0.4, y / 2 +
    // 0.2) upward motion on the ground, and no sideways push to an entity straight above the user.
    @GameTest(template = "empty", batch = "ttr_push", timeoutTicks = 100)
    public static void lightingSinglePushesMarkedEntitiesAwayAsKnockback(GameTestHelper helper) {
        List<LivingEntity> knocked = new ArrayList<>();
        Consumer<LivingKnockBackEvent> listener = event -> knocked.add(event.getEntity());
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingKnockBackEvent.class, listener);
        try (Fixture f = new Fixture(helper, "TtrPush", false)) {
            Vec3 feet = f.user.position();
            Zombie east = f.target(feet.add(3.0D, 0.0D, 0.0D));
            Zombie grounded = f.target(feet.add(0.0D, 0.0D, 3.0D));
            grounded.setOnGround(true);
            Zombie resistant = f.target(feet.add(-3.0D, 0.0D, 0.0D));
            resistant.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
            Zombie above = f.target(feet.add(0.0D, 3.0D, 0.0D));
            f.reset();
            helper.assertTrue(f.press(f.ability(TicketToRideAddon.LIGHTNING_SINGLE)), "lighting_single did not start");
            for (int tick = 0; tick < 15; tick++) {
                f.standAction.tick();
            }
            f.reset();
            // the strength is the float 0.2f, as in 1.16
            String state = "east=" + east.getDeltaMovement() + " grounded=" + grounded.getDeltaMovement() + " resistant="
                    + resistant.getDeltaMovement() + " above=" + above.getDeltaMovement() + " events=" + knocked.size();
            helper.assertTrue(east.getDeltaMovement().distanceTo(new Vec3(0.2D, 0.0D, 0.0D)) < 1.0E-6D
                            && grounded.getDeltaMovement().distanceTo(new Vec3(0.0D, 0.2D, 0.2D)) < 1.0E-6D
                            && resistant.getDeltaMovement().lengthSqr() == 0.0D
                            && above.getDeltaMovement().lengthSqr() == 0.0D
                            && List.of(east, grounded, resistant, above).stream()
                                    .allMatch(zombie -> knocked.stream().filter(zombie::equals).count() == 1L),
                    "1.16 pushed the marked entities 0.2 away from the user as knockback (event, resistance, 0.2 up on"
                            + " the ground, nothing sideways straight above): " + state);
            LOGGER.info("R764_TTR_EXPLOD_PASS push {}", state);
        }
        finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        helper.succeed();
    }

    // 1.16 SingleLightingHit.onHoldTickClientEffect (:53-59): on every held tick each client put a love_train_light
    // particle where JojoModUtil.rayTrace(user, 100, e -> e != stand) hit: the nearest pickable entity on the look ray,
    // even behind a block, else the block's outline. lightinghit.onHoldTickClientEffect (:91-95) looked for marked
    // entities on the client, where TearsBlade.isEntityMarked read persistent data that never reached it: no particle.
    // The particle is spawned on the client from TicketToRideAbilityService.holdParticleAt, checked here on the server.
    @GameTest(template = "empty", batch = "ttr_hold_particles", timeoutTicks = 100)
    public static void lightingSingleHoldParticleSitsWhereThe116RayHits(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "TtrHoldParticles", false)) {
            Vec3 eyes = f.user.getEyePosition();
            BlockPos eyeBlock = BlockPos.containing(eyes);
            f.clearArea(eyeBlock.offset(1, -1, -1), eyeBlock.offset(12, 1, 1));
            BlockPos wall = eyeBlock.east(6);
            f.setBlock(wall, Blocks.STONE.defaultBlockState());
            Map<String, String> seen = new LinkedHashMap<>();
            Map<String, String> want = new LinkedHashMap<>();

            seen.put("block", point(TicketToRideAbilityService.holdParticleAt(Mode.LIGHTNING_SINGLE, f.user, f.stand),
                    eyes));
            want.put("block", point(new Vec3(wall.getX(), eyes.y, eyes.z), eyes));

            Zombie behind = f.target(new Vec3(eyes.x + 10.0D, f.user.getY(), eyes.z));
            seen.put("entityBehindBlock", point(TicketToRideAbilityService.holdParticleAt(Mode.LIGHTNING_SINGLE, f.user,
                    f.stand), eyes));
            want.put("entityBehindBlock", point(new Vec3(behind.getBoundingBox().minX, eyes.y, eyes.z), eyes));
            seen.put("filtered", point(TicketToRideAbilityService.holdParticleAt(Mode.LIGHTNING_SINGLE, f.user, behind),
                    eyes));
            want.put("filtered", want.get("block"));

            for (Mode mode : Mode.values()) {
                if (mode != Mode.LIGHTNING_SINGLE) {
                    seen.put(mode.name(), point(TicketToRideAbilityService.holdParticleAt(mode, f.user, f.stand), eyes));
                    want.put(mode.name(), "none");
                }
            }
            helper.assertTrue(seen.equals(want), "1.16 put a love_train_light particle where lighting_single's ray hit"
                    + " (the nearest entity, blocks or not, else the block) and none for the other moves: got " + seen
                    + ", 1.16 " + want);
            LOGGER.info("R764_TTR_EXPLOD_PASS hold_particles {}", seen);
        }
        helper.succeed();
    }

    private static String point(@Nullable Vec3 at, Vec3 from) {
        return at == null ? "none" : String.format(Locale.ROOT, "(%+.4f, %+.4f, %+.4f)", at.x - from.x, at.y - from.y,
                at.z - from.z);
    }

    // 1.16 LoveTrainEventHandler (:104-336), LoveTrainAction, GiveLFAction, TearsEffects and InitStatusEffect: every 5
    // ticks a living entity inside min(3 + r * 1.2, 27) of the user is put back onto that edge (+0.1) and sent outwards
    // at 0.5 (Stands and Love Train holders or passengers stay); the sea parts along the full look vector through a point
    // 1.5 above the feet, six columns across, water and lava blocks only; holders and passengers in range take no
    // knockback and no blast push; projectiles vanish only at the holder; the effects show no particles; TEARS and the
    // passenger effect are NEUTRAL (4611416) and take Regeneration from a player without blocking other healing.
    @GameTest(template = "empty", batch = "ttr_love_train", timeoutTicks = 100)
    public static void loveTrainWallSeaAndProtectionAreThe116Ones(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "TtrLoveTrain", false)) {
            Map<String, String> seen = new LinkedHashMap<>();
            Map<String, String> want = new LinkedHashMap<>();
            Vec3 feet = f.user.position();
            f.use(TicketToRideAddon.LOVE_TRAIN_NAME, 50, true);
            MobEffectInstance train = f.user.getEffect(TicketToRideAddon.LOVE_TRAIN);
            seen.put("effect", train == null ? "none" : train.getDuration() + "/particles=" + train.isVisible()
                    + "/icon=" + train.showIcon());
            want.put("effect", "12000/particles=false/icon=true");

            // A grown train needs a start thousands of ticks back; the test server is younger than that, so its clock
            // runs ahead inside this one tick and is set back before the tick ends.
            ServerLevelData clock = (ServerLevelData) f.level.getLevelData();
            long timeBefore = clock.getGameTime();
            clock.setGameTime(timeBefore + 20000L);
            try {
            // a train grown to radius 5: the wall stands at 9
            LoveTrainState state = TicketToRideAddon.loveTrainState(f.user);
            long now = f.level.getGameTime();
            state.activate(now - 4700L, now + 7300L);
            float radius = LoveTrainService.radius(f.user);
            double repel = Math.min(3.0D + radius * 1.2D, 27.0D);
            Zombie outsider = f.unmarkedZombie(feet.add(2.0D, 0.0D, 0.0D));
            Zombie passenger = f.unmarkedZombie(feet.add(0.0D, 0.0D, 2.0D));
            LoveTrainService.share(f.user, passenger);
            MobEffectInstance shared = passenger.getEffect(TicketToRideAddon.SHARED_LOVE_TRAIN);
            seen.put("shared", shared == null ? "none" : shared.getDuration() + "/particles=" + shared.isVisible());
            want.put("shared", "120000/particles=false");
            f.stand.moveTo(feet.x - 1.5D, feet.y, feet.z, -90.0F, 0.0F);
            Vec3 standAt = f.stand.position();
            f.user.tickCount = 5;
            LoveTrainService.tick(f.user);
            seen.put("outsider", String.format(Locale.ROOT, "%.4f/%s", outsider.getX() - feet.x,
                    outsider.getDeltaMovement()));
            want.put("outsider", String.format(Locale.ROOT, "%.4f/%s", repel + 0.1D, new Vec3(0.5D, 0.0D, 0.0D)));
            seen.put("passenger", String.valueOf(passenger.position().subtract(feet)));
            want.put("passenger", String.valueOf(new Vec3(0.0D, 0.0D, 2.0D)));
            seen.put("stand", String.valueOf(f.stand.position().equals(standAt)));
            want.put("stand", "true");

            // knockback and blast push spare the holder and the passenger in range
            outsider.setDeltaMovement(Vec3.ZERO);
            outsider.knockback(0.4D, -1.0D, 0.0D);
            passenger.knockback(0.4D, -1.0D, 0.0D);
            f.user.setDeltaMovement(Vec3.ZERO);
            f.user.knockback(0.4D, -1.0D, 0.0D);
            seen.put("knockback", "outsider=" + (outsider.getDeltaMovement().lengthSqr() > 0.0D) + " passenger="
                    + passenger.getDeltaMovement() + " user=" + f.user.getDeltaMovement());
            want.put("knockback", "outsider=true passenger=" + Vec3.ZERO + " user=" + Vec3.ZERO);
            Zombie victim = f.unmarkedZombie(feet.add(0.0D, 0.0D, 5.0D));
            // a barrier wall of a neighbouring test structure must not hide the victim from the blast
            f.clearArea(BlockPos.containing(feet).offset(-2, 0, 0), BlockPos.containing(feet).offset(2, 3, 6));
            f.level.explode(null, feet.x, feet.y, feet.z + 3.5D, 2.0F, Level.ExplosionInteraction.NONE);
            seen.put("blast", "victim=" + (victim.getDeltaMovement().z > 0.0D) + " passenger="
                    + passenger.getDeltaMovement() + "/" + passenger.getHealth() + " user=" + f.user.getDeltaMovement());
            want.put("blast", "victim=true passenger=" + Vec3.ZERO + "/" + passenger.getMaxHealth() + " user="
                    + Vec3.ZERO);

            // projectiles vanish at the holder only
            Arrow atPassenger = EntityType.ARROW.create(f.level);
            Arrow atUser = EntityType.ARROW.create(f.level);
            ProjectileImpactEvent passengerHit = new ProjectileImpactEvent(atPassenger, new EntityHitResult(passenger));
            ProjectileImpactEvent userHit = new ProjectileImpactEvent(atUser, new EntityHitResult(f.user));
            TicketToRideEvents.onProjectileImpact(passengerHit);
            TicketToRideEvents.onProjectileImpact(userHit);
            seen.put("projectiles", "passenger=" + passengerHit.isCanceled() + "/" + atPassenger.isRemoved() + " user="
                    + userHit.isCanceled() + "/" + atUser.isRemoved());
            want.put("projectiles", "passenger=false/false user=true/true");

            // the sea parts, radius 2.25 (off a whole metre), the user looking east from the middle of its block
            state.activate(now - 1950L, now + 7300L);
            BlockPos base = BlockPos.containing(feet);
            ChunkPos chunk = new ChunkPos(base);
            BlockPos origin = new BlockPos(chunk.getMinBlockX(), base.getY(), chunk.getMinBlockZ());
            Map<String, BlockPos> cells = new LinkedHashMap<>();
            cells.put("waterAhead", origin.offset(4, 0, 8));
            cells.put("waterPast", origin.offset(5, 0, 8));
            cells.put("lavaSide", origin.offset(3, 1, 11));
            cells.put("waterOutside", origin.offset(3, 0, 12));
            cells.put("loggedStairs", origin.offset(2, 0, 8));
            f.clearArea(origin.offset(0, 0, 5), origin.offset(6, 2, 12));
            BlockState water = Blocks.WATER.defaultBlockState();
            f.setBlock(cells.get("waterAhead"), water);
            f.setBlock(cells.get("waterPast"), water);
            f.setBlock(cells.get("lavaSide"), Blocks.LAVA.defaultBlockState());
            f.setBlock(cells.get("waterOutside"), water);
            f.setBlock(cells.get("loggedStairs"), Blocks.OAK_STAIRS.defaultBlockState()
                    .setValue(BlockStateProperties.WATERLOGGED, true));
            f.user.setXRot(0.0F);
            f.user.xRotO = 0.0F;
            f.user.tickCount = 10;
            LoveTrainService.tick(f.user);
            StringBuilder sea = new StringBuilder();
            cells.forEach((name, pos) -> sea.append(name).append('=').append(blockName(f.level.getBlockState(pos)))
                    .append(' '));
            seen.put("sea", sea.toString().trim());
            want.put("sea", "waterAhead=air waterPast=water lavaSide=air waterOutside=water loggedStairs=oak_stairs");
            // looking 60 degrees down, the line reaches half as far
            f.setBlock(cells.get("waterAhead"), water);
            f.user.setXRot(60.0F);
            f.user.xRotO = 60.0F;
            f.user.tickCount = 15;
            LoveTrainService.tick(f.user);
            f.user.setXRot(0.0F);
            f.user.xRotO = 0.0F;
            seen.put("seaLookingDown", blockName(f.level.getBlockState(cells.get("waterAhead"))));
            want.put("seaLookingDown", "water");

            // TEARS and the passenger effect: NEUTRAL, Regeneration taken from a player, other healing kept
            ServerPlayer cursed = FakePlayerFactory.get(f.level, new GameProfile(UUID.randomUUID(), "TtrLoveTrainCursed"));
            List<String> regen = new ArrayList<>();
            for (Holder<MobEffect> effect : List.of(TicketToRideAddon.TEARS, TicketToRideAddon.SHARED_LOVE_TRAIN)) {
                cursed.removeAllEffects();
                cursed.addEffect(new MobEffectInstance(effect, 200, 0));
                cursed.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
                TicketToRideEvents.onEntityTick(new EntityTickEvent.Post(cursed));
                cursed.setHealth(10.0F);
                cursed.heal(2.0F);
                regen.add(effect.getRegisteredName() + ":regen=" + cursed.hasEffect(MobEffects.REGENERATION) + "/heal="
                        + cursed.getHealth());
            }
            cursed.removeAllEffects();
            seen.put("regeneration", String.valueOf(regen));
            want.put("regeneration", "[rotp_ttr:tears:regen=false/heal=12.0, rotp_ttr:lovetrainss:regen=false/heal=12.0]");
            seen.put("categories", TicketToRideAddon.TEARS.get().getCategory() + "/" + TicketToRideAddon.TEARS.get().getColor()
                    + " " + TicketToRideAddon.SHARED_LOVE_TRAIN.get().getCategory() + "/"
                    + TicketToRideAddon.SHARED_LOVE_TRAIN.get().getColor() + " "
                    + TicketToRideAddon.LOVE_TRAIN.get().getCategory() + "/" + TicketToRideAddon.LOVE_TRAIN.get().getColor());
            want.put("categories", "NEUTRAL/4611416 NEUTRAL/4611416 BENEFICIAL/16766720");
            LoveTrainService.deactivate(f.user);
            }
            finally {
                clock.setGameTime(timeBefore);
            }
            helper.assertTrue(seen.equals(want), "1.16 Love Train: the wall puts entities back on its edge, the sea parts"
                    + " along the look, holders and passengers in range take no knockback or blast push, projectiles"
                    + " vanish at the holder, no effect particles, NEUTRAL TEARS that take Regeneration: got " + seen
                    + ", 1.16 " + want);
            LOGGER.info("R764_TTR_EXPLOD_PASS love_train {}", seen);
        }
        helper.succeed();
    }

    // 1.16 MobCrash.spawnUnluckyHorse / findValidSpawnPosition (:53-104) and UnluckyHorseGoal (:20-146): the horse comes
    // 5 to 15 blocks from the nearest marked entity in a random 3D direction (ten tries for a free block above a solid
    // one), untamed, with Unluck 900 (ambient, particles shown), aggressive; its goal (MOVE, LOOK, priority 2) runs at
    // the nearest marked entity within 64 and within 1.7 hits it for 30% of its health + 6 magic, sets its motion to
    // (dx, 0.5, dz) normalized * 2.5, takes 60% + 5 itself, waits 2 ticks, and in Unluck's last 20 ticks puffs and is
    // removed at 1. A horse with no marked entity around just stays. An ordinary horse gets no such goal.
    @GameTest(template = "empty", batch = "ttr_crash_horse", timeoutTicks = 100)
    public static void crashHorseIsThe116UnluckyHorse(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "TtrCrashHorse", false)) {
            Map<String, String> seen = new LinkedHashMap<>();
            Map<String, String> want = new LinkedHashMap<>();
            f.listenToJoins();
            f.listenToSounds();
            // on the ground, so that the 1.16 search can find a free block above a solid one
            Zombie marked = f.target(f.user.position().add(5.0D, 0.0D, 0.0D));
            long seed = 0L;
            while (horseSpawn116(f.level, marked, RandomSource.create(seed)) == null && seed < 1000L) {
                seed++;
            }
            helper.assertTrue(horseSpawn116(f.level, marked, RandomSource.create(seed)) != null,
                    "Setup: no seed below 1000 lets the 1.16 search find a spawn block");
            f.reset();
            f.power.setAbilityCooldown(TicketToRideAddon.CRASH, 0);
            helper.assertTrue(f.press(f.ability(TicketToRideAddon.CRASH)), "crash did not start");
            for (int tick = 0; tick < 22; tick++) {
                f.level.getRandom().setSeed(seed);
                f.standAction.tick();
            }
            List<Horse> horses = f.joined.stream().filter(Horse.class::isInstance).map(Horse.class::cast).toList();
            helper.assertTrue(horses.size() == 1, "crash did not summon one horse: " + horses);
            Horse horse = horses.get(0);
            RandomSource draws = RandomSource.create(seed);
            Vec3 expected = horseSpawn116(f.level, marked, draws);
            float yawExpected = draws.nextFloat() * 360.0F;
            MobEffectInstance unluck = horse.getEffect(MobEffects.UNLUCK);
            WrappedGoal wrapped = horse.goalSelector.getAvailableGoals().stream()
                    .filter(goal -> goal.getGoal() instanceof TicketToRideBehaviorService.UnluckyHorseGoal)
                    .findFirst().orElse(null);
            seen.put("spawn", String.valueOf(horse.position().equals(expected) && horse.getYRot() == yawExpected));
            want.put("spawn", "true");
            seen.put("horse", "tamed=" + horse.isTamed() + " age=" + horse.getAge() + " aggressive=" + horse.isAggressive()
                    + " unluck=" + (unluck == null ? "none" : unluck.getDuration() + "/" + unluck.isAmbient() + "/"
                    + unluck.isVisible()) + " goal=" + (wrapped == null ? "none" : wrapped.getPriority() + "/"
                    + wrapped.getGoal().getFlags()));
            want.put("horse", "tamed=false age=0 aggressive=true unluck=900/true/true goal=2/[MOVE, LOOK]");
            helper.assertTrue(wrapped != null, "Setup: the crash horse has no unlucky goal: " + seen);
            Goal goal = wrapped.getGoal();

            // the nearest marked entity in 64, not the one crash was pressed at; 1.8 away: no hit, 1.6 away: a hit
            horse.setNoAi(true);
            horse.setNoGravity(true);
            marked.discard();
            Vec3 h = horse.position();
            Zombie near = f.target(h.add(1.8D, 0.0D, 0.0D));
            Zombie far = f.target(h.add(0.0D, 0.0D, 10.0D));
            f.sounds.clear();
            boolean canUse = goal.canUse();
            goal.start();
            goal.tick();
            seen.put("at1.8", "canUse=" + canUse + " near=" + near.getHealth() + " far=" + far.getHealth()
                    + " horse=" + horse.getHealth());
            want.put("at1.8", "canUse=true near=20.0 far=20.0 horse=" + horse.getMaxHealth());
            near.moveTo(h.x + 1.6D, h.y, h.z, 0.0F, 0.0F);
            goal.canUse();
            goal.tick();
            Vec3 fling = new Vec3(near.getX() - horse.getX(), 0.5D, near.getZ() - horse.getZ()).normalize().scale(2.5D);
            DamageSource hit = near.getLastDamageSource();
            DamageSource back = horse.getLastDamageSource();
            List<String> sounds = f.sounds.stream().map(sound -> sound.id().getPath()).toList();
            seen.put("hit", String.format(Locale.ROOT, "near=%.1f/%s motion=%s horse=%.1f/%s sounds=%s",
                    near.getHealth(), hit == null ? "none" : hit.getMsgId(), near.getDeltaMovement().distanceTo(fling)
                            < 1.0E-9D, horse.getHealth(), back == null ? "none" : back.getMsgId(), sounds));
            want.put("hit", String.format(Locale.ROOT, "near=%.1f/magic motion=true horse=%.1f/magic sounds=%s",
                    20.0F - 12.0F, horse.getMaxHealth() - (12.0F * 0.6F + 5.0F),
                    // the horse's own hurt sound, then the goal's HORSE_HURT, as 1.16
                    List.of("entity.generic.explode", "entity.player.attack.knockback", "entity.horse.hurt",
                            "entity.horse.hurt")));
            // two ticks of cooldown, then the next hit
            near.setHealth(20.0F);
            near.invulnerableTime = 0;
            goal.tick();
            goal.tick();
            float waited = near.getHealth();
            goal.tick();
            seen.put("cooldown", waited + "->" + near.getHealth());
            want.put("cooldown", "20.0->8.0");

            // no marked entity around: the horse stays; Unluck's end: removed at 1
            near.discard();
            far.discard();
            seen.put("noTarget", "canUse=" + goal.canUse() + " removed=" + horse.isRemoved());
            want.put("noTarget", "canUse=false removed=false");
            horse.removeEffect(MobEffects.UNLUCK);
            horse.addEffect(new MobEffectInstance(MobEffects.UNLUCK, 1, 0, true, true));
            seen.put("lastTick", "canUse=" + goal.canUse());
            goal.tick();
            seen.put("lastTick", seen.get("lastTick") + " removed=" + horse.isRemoved());
            want.put("lastTick", "canUse=true removed=true");

            Horse plain = f.join(f.created(EntityType.HORSE, f.user.position().add(0.0D, 0.0D, 4.0D), false));
            seen.put("plainHorse", String.valueOf(plain.goalSelector.getAvailableGoals().stream()
                    .anyMatch(other -> other.getGoal() instanceof TicketToRideBehaviorService.UnluckyHorseGoal)));
            want.put("plainHorse", "false");
            helper.assertTrue(seen.equals(want), "1.16 crash: the unlucky horse spawns where MobCrash put it and its"
                    + " goal hunts the nearest marked entity, hits within 1.7 and leaves at the end of Unluck: got " + seen
                    + ", 1.16 " + want);
            LOGGER.info("R764_TTR_EXPLOD_PASS crash_horse {}", seen);
        }
        helper.succeed();
    }

    // 1.16 Deadphotomemtor (1 x 1) with ProjectileAndEntityEventHandler.onLivingUpdate, which ran before the move: it
    // sets the motion to the nearest marked entity's eyes (1.2 across, 0.8 down) and moves by it that same tick; with a
    // non-air block one below or a marked entity touching its box grown by 1.1 it throws the shockwave, a 9 blast that
    // breaks blocks (as TNT) whatever mobGriefing says, and bleeds the marked within 6; hitting a wall on the move
    // (Deadphotomemtor.tick, after it) makes a 6 blast that breaks blocks only with mobGriefing. The blasts are caught
    // at their start and cancelled, so the test area stays whole.
    @GameTest(template = "empty", batch = "ttr_meteor_flight", timeoutTicks = 100)
    public static void mementoFliesAndExplodesAsIn116(GameTestHelper helper) {
        List<String> blasts = new ArrayList<>();
        Consumer<ExplosionEvent.Start> listener = event -> {
            if (event.getExplosion().getDirectSourceEntity() instanceof DeadPhotoMementoEntity) {
                blasts.add(event.getExplosion().radius() + "/" + event.getExplosion().getBlockInteraction());
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, ExplosionEvent.Start.class, listener);
        try (Fixture f = new Fixture(helper, "TtrMeteorFlight", false)) {
            Map<String, String> seen = new LinkedHashMap<>();
            Map<String, String> want = new LinkedHashMap<>();
            f.setMobGriefing(false);
            Vec3 feet = f.user.position();
            f.clearArea(BlockPos.containing(feet).offset(1, 0, -6), BlockPos.containing(feet).offset(12, 9, 6));

            // flies to the marked zombie's eyes in the same tick it is aimed
            Zombie marked = f.target(feet.add(6.0D, 0.0D, 0.0D));
            DeadPhotoMementoEntity memento = f.memento(feet.add(9.0D, 6.0D, 0.0D));
            seen.put("size", memento.getBbWidth() + "x" + memento.getBbHeight());
            want.put("size", "1.0x1.0");
            Vec3 before = memento.position();
            Vec3 aim = marked.getEyePosition().subtract(before).normalize();
            memento.tick();
            Vec3 moved = memento.position().subtract(before);
            seen.put("firstTick", String.format(Locale.ROOT, "(%.4f, %.4f, %.4f)", moved.x, moved.y, moved.z));
            want.put("firstTick", String.format(Locale.ROOT, "(%.4f, %.4f, %.4f)", aim.x * 1.2D, -0.8D, aim.z * 1.2D));

            // touching the marked zombie's box (centres 1.4 apart): the shockwave, and the zombie bleeds
            memento.moveTo(marked.getX() + 1.4D, marked.getY() + 1.0D, marked.getZ());
            blasts.clear();
            memento.tick();
            MobEffectInstance bleeding = marked.getEffect(ModStatusEffects.BLEEDING);
            seen.put("touch", blasts + " removed=" + memento.isRemoved() + " bleeding="
                    + (bleeding == null ? "none" : bleeding.getDuration()));
            want.put("touch", "[9.0/DESTROY] removed=true bleeding=200");

            // a block one below, nothing marked near: the shockwave too
            marked.discard();
            Zombie far = f.target(feet.add(6.0D, 0.0D, 5.0D));
            f.setBlock(BlockPos.containing(feet.add(10.0D, -1.0D, -4.0D)), Blocks.STONE.defaultBlockState());
            DeadPhotoMementoEntity low = f.memento(feet.add(10.0D, 0.6D, -4.0D));
            blasts.clear();
            low.tick();
            seen.put("lowAboveGround", blasts + " removed=" + low.isRemoved());
            want.put("lowAboveGround", "[9.0/DESTROY] removed=true");

            // flying into a wall high up: the 6 impact blast, blocks kept with mobGriefing off
            BlockPos high = BlockPos.containing(feet.add(9.0D, 6.0D, -2.0D));
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    f.setBlock(high.offset(-1, dy, dz), Blocks.STONE.defaultBlockState());
                }
            }
            DeadPhotoMementoEntity wall = f.memento(Vec3.atBottomCenterOf(high).add(0.0D, 0.0D, 0.0D));
            wall.moveTo(high.getX() + 0.55D, high.getY(), high.getZ() + 0.5D);
            blasts.clear();
            wall.tick();
            seen.put("wall", blasts + " removed=" + wall.isRemoved() + " far=" + far.hasEffect(ModStatusEffects.BLEEDING));
            want.put("wall", "[6.0/KEEP] removed=true far=false");
            helper.assertTrue(seen.equals(want), "1.16 Dead Photo Memento: 1 x 1, aimed and moved in the same tick, the 9"
                    + " shockwave on touching a marked entity or one block above the ground, the 6 impact blast on a wall:"
                    + " got " + seen + ", 1.16 " + want);
            LOGGER.info("R764_TTR_EXPLOD_PASS meteor_flight {}", seen);
        }
        finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        helper.succeed();
    }

    // 1.16 TearsEffectEventHandler (:46-190), every handler on every update of a marked entity: on the ground in rain
    // (anywhere it rains) moving faster than 0.15, 10.5% a tick: slowness 300/2, misshapen legs 400 and 8 fall damage;
    // a player weakness 120 every 100 ticks of rain; TNT within 3 lit by the entity, centred, with its sound; in water
    // 7.5% a tick: slowness 100/2 and spirit vision 200; the first beehive within the box grown by 5, 4.5% a tick: broken
    // into 3 + rand(3) bees (drawn every round) that target it, angry 400, age -1, health 10; a fall from 3 blocks:
    // hidden slowness and misshapen legs, 5 + (d - 3) / 2 fall damage, the skeleton hurt sound; a marked attacker 15%:
    // half the hit back as generic damage with the player hurt sound; a marked player at a door 15%: bleeding and the
    // iron trapdoor sound, no damage; with a lava bucket 10%: lava at the feet whatever stood there, lava damage, the
    // lava pop sound, the bucket back into the inventory. The entity's random is seeded so that each roll passes.
    @GameTest(template = "empty", batch = "ttr_tears_hazards", timeoutTicks = 100)
    public static void tearsMarkHazardsAreThe116Ones(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "TtrTearsHazards", false)) {
            Map<String, String> seen = new LinkedHashMap<>();
            Map<String, String> want = new LinkedHashMap<>();
            f.listenToSounds();
            f.listenToJoins();
            Vec3 feet = f.user.position();
            BlockPos base = BlockPos.containing(feet);
            f.clearArea(base.offset(2, -1, -6), base.offset(13, 4, 6));
            float rainBefore = f.level.getRainLevel(1.0F);
            try {
                f.level.setRainLevel(1.0F);
                Zombie runner = f.target(feet.add(4.0D, 0.0D, -4.0D));
                runner.setOnGround(true);
                runner.setDeltaMovement(0.3D, 0.0D, 0.0D);
                runner.tickCount = 7;
                runner.getRandom().setSeed(seedWhere(r -> r.nextDouble() < 0.105D));
                TearsHazardService.tick(runner);
                seen.put("rainSlip", effectsOf(runner) + " health=" + runner.getHealth() + "/" + describe(
                        runner.getLastDamageSource()));
                want.put("rainSlip", "[jojo_ripples:misshapen_legs/400/0, minecraft:slowness/300/2] health=12.0/fall/null");

                f.mark(f.user);
                f.user.tickCount = 100;
                TearsHazardService.tick(f.user);
                MobEffectInstance weakness = f.user.getEffect(MobEffects.WEAKNESS);
                MobEffectInstance poison = f.user.getEffect(MobEffects.POISON);
                seen.put("rainWeakness", (weakness == null ? "none" : weakness.getDuration() + "/" + weakness.getAmplifier())
                        + " poison=" + (poison != null));
                want.put("rainWeakness", "120/0 poison=false");
                f.user.removeAllEffects();
            }
            finally {
                f.level.setRainLevel(rainBefore);
            }

            Zombie swimmer = f.target(feet.add(6.0D, 0.0D, 3.0D));
            f.setBlock(BlockPos.containing(swimmer.position()), Blocks.WATER.defaultBlockState());
            swimmer.baseTick();
            swimmer.tickCount = 7;
            swimmer.getRandom().setSeed(seedWhere(r -> r.nextDouble() < 0.075D));
            TearsHazardService.tick(swimmer);
            seen.put("water", effectsOf(swimmer));
            want.put("water", "[jojo_ripples:spirit_vision/200/0, minecraft:slowness/100/2]");
            swimmer.discard();
            f.setBlock(BlockPos.containing(swimmer.position()), Blocks.AIR.defaultBlockState());

            Zombie lighter = f.target(feet.add(8.0D, 0.0D, -3.0D));
            BlockPos tnt = BlockPos.containing(lighter.position()).offset(2, 0, 0);
            f.setBlock(tnt, Blocks.TNT.defaultBlockState());
            lighter.tickCount = 7;
            f.sounds.clear();
            TearsHazardService.tick(lighter);
            List<PrimedTnt> lit = f.level.getEntitiesOfClass(PrimedTnt.class, new AABB(tnt).inflate(0.5D));
            seen.put("tnt", blockName(f.level.getBlockState(tnt)) + " lit=" + lit.stream().map(primed ->
                    primed.position().subtract(Vec3.atLowerCornerOf(tnt)) + "/owner=" + (primed.getOwner() == lighter))
                    .toList() + " sound=" + f.sounds.stream().map(sound -> sound.id().getPath()).toList());
            want.put("tnt", "air lit=[(0.5, 0.5, 0.5)/owner=true] sound=[entity.tnt.primed]");
            lit.forEach(Entity::discard);

            Zombie keeper = f.target(feet.add(10.0D, 0.0D, 3.0D));
            BlockPos hive = BlockPos.containing(keeper.position()).offset(0, 0, 3);
            f.setBlock(hive, Blocks.BEEHIVE.defaultBlockState());
            long hiveSeed = seedWhere(r -> r.nextDouble() < 0.045D);
            RandomSource bees = RandomSource.create(hiveSeed);
            bees.nextDouble();
            int beeCount = 0;
            while (beeCount < 3 + bees.nextInt(3)) {
                beeCount++;
            }
            keeper.tickCount = 7;
            keeper.getRandom().setSeed(hiveSeed);
            f.joined.clear();
            f.sounds.clear();
            TearsHazardService.tick(keeper);
            List<String> swarm = f.joined.stream().filter(Bee.class::isInstance).map(Bee.class::cast)
                    .map(bee -> (bee.getTarget() == keeper) + "/" + bee.getRemainingPersistentAngerTime() + "/"
                            + bee.getHealth() + "/" + bee.getAge()).toList();
            seen.put("hive", blockName(f.level.getBlockState(hive)) + " bees=" + swarm + " sound="
                    + f.sounds.stream().map(sound -> sound.id().getPath()).toList());
            List<String> wantSwarm = new ArrayList<>();
            for (int i = 0; i < beeCount; i++) {
                wantSwarm.add("true/400/10.0/-1");
            }
            want.put("hive", "air bees=" + wantSwarm + " sound=[block.stone.break]");
            f.joined.forEach(Entity::discard);

            Zombie faller = f.target(feet.add(4.0D, 0.0D, 4.0D));
            faller.setSilent(false);
            faller.getRandom().setSeed(seedWhere(r -> r.nextFloat() < 0.3F * 1.5F));
            f.sounds.clear();
            TearsHazardService.onFall(faller, 6.0F);
            seen.put("fall", faller.getActiveEffects().stream().map(effect -> effect.getEffect().getRegisteredName() + "/"
                    + effect.getDuration() + "/" + effect.getAmplifier() + "/" + effect.isVisible() + "/"
                    + effect.showIcon()).sorted().toList() + " health=" + faller.getHealth() + "/"
                    + describe(faller.getLastDamageSource()) + " sound=" + f.sounds.stream()
                            .map(sound -> sound.id().getPath()).filter(id -> !id.contains("zombie")).toList());
            want.put("fall", "[jojo_ripples:misshapen_legs/450/0/false/false, minecraft:slowness/300/2/false/false]"
                    + " health=13.5/fall/null sound=[entity.skeleton.hurt]");

            Zombie biter = f.target(feet.add(10.0D, 0.0D, -5.0D));
            biter.setSilent(false);
            Mob bitten = f.join(f.created(EntityType.COW, feet.add(11.0D, 0.0D, -5.0D), false));
            biter.getRandom().setSeed(seedWhere(r -> r.nextFloat() < 0.15F));
            f.sounds.clear();
            bitten.hurt(biter.damageSources().mobAttack(biter), 4.0F);
            seen.put("recoil", "health=" + biter.getHealth() + "/" + describe(biter.getLastDamageSource()) + " sound="
                    + f.sounds.stream().map(sound -> sound.id().getPath())
                            .filter(id -> !id.contains("cow") && !id.contains("zombie")).toList());
            want.put("recoil", "health=18.0/generic/null sound=[entity.player.hurt]");

            ServerPlayer cursed = FakePlayerFactory.get(f.level, new GameProfile(UUID.randomUUID(), "TtrHazardsCursed"));
            cursed.moveTo(feet.x + 6.0D, feet.y, feet.z - 1.0D, 0.0F, 0.0F);
            f.mark(cursed);
            BlockPos door = BlockPos.containing(cursed.position()).offset(0, 0, 1);
            f.setBlock(door, Blocks.OAK_DOOR.defaultBlockState());
            cursed.getRandom().setSeed(seedWhere(r -> r.nextFloat() < 0.15F));
            f.sounds.clear();
            TicketToRideEvents.onDoorUse(new PlayerInteractEvent.RightClickBlock(cursed, InteractionHand.MAIN_HAND, door,
                    new BlockHitResult(Vec3.atCenterOf(door), net.minecraft.core.Direction.NORTH, door, false)));
            MobEffectInstance bleed = cursed.getEffect(ModStatusEffects.BLEEDING);
            seen.put("door", (bleed == null ? "none" : bleed.getDuration() + "/" + bleed.getAmplifier()) + " sound="
                    + f.sounds.stream().map(sound -> sound.id().getPath()).toList());
            want.put("door", "200/0 sound=[block.iron_trapdoor.close]");

            BlockPos stand = cursed.blockPosition();
            f.setBlock(stand, Blocks.GLASS.defaultBlockState());
            cursed.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LAVA_BUCKET));
            cursed.getRandom().setSeed(seedWhere(r -> r.nextFloat() < 0.10F));
            f.sounds.clear();
            PlayerInteractEvent.RightClickItem use = new PlayerInteractEvent.RightClickItem(cursed,
                    InteractionHand.MAIN_HAND);
            TicketToRideEvents.onLavaBucketUse(use);
            seen.put("lavaBucket", blockName(f.level.getBlockState(stand)) + " cancelled=" + use.isCanceled() + " hand="
                    + cursed.getMainHandItem().getItem() + " buckets=" + cursed.getInventory().countItem(Items.BUCKET)
                    + " sound=" + f.sounds.stream().map(sound -> sound.id().getPath()).toList());
            want.put("lavaBucket", "lava cancelled=true hand=" + Items.BUCKET + " buckets=1 sound=[block.lava.pop]");
            f.setBlock(stand, Blocks.AIR.defaultBlockState());
            helper.assertTrue(seen.equals(want), "1.16 TEARS mark hazards, every update, with the 1.16 effects, damage"
                    + " and sounds: got " + seen + ", 1.16 " + want);
            LOGGER.info("R764_TTR_EXPLOD_PASS tears_hazards {}", seen);
        }
        helper.succeed();
    }

    // 1.16 InitStands standSound, ExplodAction (:30-53), GameplayHandler.onPlayerTick (:35-66), TearsBlade (no Ammo tag:
    // 0), RefillAmmoAction.standPerform (:57-82) and TraceOnAction.playEffects (:110-115): explod and crash play
    // STAND_PUNCH_LIGHT and randommob AJA_STONE_BEAM when they perform; explod takes the first marked entity of its
    // cone in the level's order and plays no TNT sound of its own; the blade comes empty into the first free inventory
    // slot and stays where the player puts it; a refill sounds the raid horn at the player with totem particles; Trace
    // On's enchanting sound is played by the player, at its position.
    @GameTest(template = "empty", batch = "ttr_small_moves", timeoutTicks = 100)
    public static void smallerMovesAreThe116Ones(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "TtrSmallMoves", false)) {
            Map<String, String> seen = new LinkedHashMap<>();
            Map<String, String> want = new LinkedHashMap<>();
            Map<String, String> sounds = new LinkedHashMap<>();
            for (Mode mode : Mode.values()) {
                SoundEvent sound = TicketToRideAbility.standPerformSound(mode);
                if (sound != null) {
                    sounds.put(mode.name(), sound.getLocation().getPath());
                }
            }
            seen.put("standSounds", String.valueOf(sounds));
            want.put("standSounds", "{EXPLOD=stand_punch_light, CRASH=stand_punch_light, RANDOM_MOB=aja_stone_beam}");

            // two marked zombies in the cone, in one section: the farther one joined first
            f.listenToSounds();
            Vec3 look = f.user.getLookAngle();
            Vec3 side = new Vec3(-look.z, 0.0D, look.x).normalize();
            Zombie far = f.target(f.offset(look, side, 9.0D, 10.0D));
            Zombie near = f.target(f.offset(look, side, 4.0D, -10.0D));
            helper.assertTrue(SectionPos.of(far).equals(SectionPos.of(near)), "Setup: the two zombies are not in one section");
            f.reset();
            f.sounds.clear();
            f.press(f.explod);
            f.charge(CHARGE_TICKS);
            f.release(BRANCH_TNT);
            seen.put("explodTarget", "far=" + f.effectsAt(far.position()) + " near=" + f.effectsAt(near.position())
                    + " tntSound=" + f.sounds.stream().anyMatch(sound -> sound.id().getPath().equals("entity.tnt.primed")));
            want.put("explodTarget", "far=1 near=0 tntSound=false");
            f.remove(far);
            f.remove(near);

            // the blade: empty, into the first free slot, left where it is
            Inventory inventory = f.user.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
            inventory.setItem(0, new ItemStack(Items.COBBLESTONE));
            inventory.selected = 4;
            TearsBladeItem.enforceOwnedItem(f.user);
            int granted = -1;
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                if (inventory.getItem(slot).is(TicketToRideAddon.TEARS_BLADE.get())) {
                    granted = slot;
                }
            }
            inventory.selected = 6;
            TearsBladeItem.enforceOwnedItem(f.user);
            seen.put("blade", "slot=" + granted + " ammo=" + (granted >= 0 ? TearsBladeItem.getAmmo(inventory.getItem(granted))
                    : -1) + " afterReselect=" + (granted >= 0 && inventory.getItem(granted).is(TicketToRideAddon.TEARS_BLADE
                    .get())) + " selectedHolds=" + inventory.getSelected().getItem());
            want.put("blade", "slot=1 ammo=0 afterReselect=true selectedHolds=" + Items.AIR);

            // the refill
            inventory.selected = granted;
            f.sounds.clear();
            TearsBladeItem.refillOwnedInHands(f.user);
            seen.put("refill", "ammo=" + TearsBladeItem.getAmmo(f.user.getMainHandItem()) + " sounds=" + f.sounds.stream()
                    .map(sound -> sound.id().getPath() + "@" + sound.pos().equals(f.user.position())).toList());
            want.put("refill", "ammo=30 sounds=[event.raid.horn@true]");

            // Trace On
            var pool = f.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            sword.enchant(pool.getOrThrow(Enchantments.SHARPNESS), 3);
            inventory.setItem(inventory.selected, sword);
            f.sounds.clear();
            boolean traced = TicketToRideAbilityService.perform(f.level, f.user, f.power, Mode.TRACE);
            seen.put("trace", "traced=" + traced + " sounds=" + f.sounds.stream()
                    .map(sound -> sound.id().getPath() + "@" + sound.pos().equals(f.user.position()) + "/" + sound.pitch())
                    .toList());
            want.put("trace", "traced=true sounds=[block.enchantment_table.use@true/1.2]");
            // 1.16 FIXED_POOL; an enchantment the item had keeps its level (the first entry counted)
            ItemStack crossbow = new ItemStack(Items.CROSSBOW);
            ItemStack trident = new ItemStack(Items.TRIDENT);
            ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
            TraceEnchantService.apply(f.level, crossbow);
            TraceEnchantService.apply(f.level, trident);
            TraceEnchantService.apply(f.level, pickaxe);
            ItemStack tracedSword = inventory.getSelected();
            java.util.function.BiFunction<ItemStack, ResourceKey<Enchantment>, Integer> levelOf =
                    (stack, key) -> stack.getEnchantments().getLevel(pool.getOrThrow(key));
            seen.put("tracePool", "sword sharpness=" + levelOf.apply(tracedSword, Enchantments.SHARPNESS) + " smite="
                    + levelOf.apply(tracedSword, Enchantments.SMITE) + " unbreaking=" + levelOf.apply(tracedSword, Enchantments.UNBREAKING)
                    + " crossbow multishot=" + levelOf.apply(crossbow, Enchantments.MULTISHOT) + " quickCharge="
                    + levelOf.apply(crossbow, Enchantments.QUICK_CHARGE) + " piercing="
                    + levelOf.apply(crossbow, Enchantments.PIERCING) + " trident riptide="
                    + levelOf.apply(trident, Enchantments.RIPTIDE) + " loyalty=" + levelOf.apply(trident, Enchantments.LOYALTY)
                    + " pickaxe silkTouch=" + levelOf.apply(pickaxe, Enchantments.SILK_TOUCH) + " efficiency="
                    + levelOf.apply(pickaxe, Enchantments.EFFICIENCY) + " binding=" + levelOf.apply(pickaxe,
                            Enchantments.BINDING_CURSE));
            want.put("tracePool", "sword sharpness=3 smite=5 unbreaking=3 crossbow multishot=1 quickCharge=10 piercing=5"
                    + " trident riptide=0 loyalty=3 pickaxe silkTouch=0 efficiency=9 binding=0");
            helper.assertTrue(seen.equals(want), "1.16: the moves' Stand sounds, explod's first marked target, the empty"
                    + " blade in the first free slot, the refill's raid horn and Trace On's sound at the player: got " + seen
                    + ", 1.16 " + want);
            LOGGER.info("R764_TTR_EXPLOD_PASS small_moves {}", seen);
        }
        helper.succeed();
    }

    // 1.16 GiveLFAction / RemoveLFAction (:20-30, TargetRequirement.ENTITY) and Action.checkRangeAndTarget (:162-202):
    // the target the client sent with the press (EntityStandType.clientHitResult :122-151: the vanilla pick, the
    // user's aim, when it holds an entity), within 8 blocks of the Stand (StandAction.getPerformer), 4 when the Stand
    // cannot see it, else "target_too_far"; any entity but the user passes, and only a living one takes or loses the
    // passenger effect. The Stand's own synced aim is not that target.
    @GameTest(template = "empty", batch = "ttr_share_target", timeoutTicks = 100)
    public static void shareAndRemoveTakeTheCrosshairTargetWithin8(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "TtrShareTarget", false)) {
            Map<String, String> seen = new LinkedHashMap<>();
            Map<String, String> want = new LinkedHashMap<>();
            java.util.function.Function<Mode, String> check = mode -> {
                ConditionCheck result = TicketToRideAbilityService.canUse(mode, f.user);
                if (result.isPositive()) {
                    return "ok";
                }
                return result.getWarning() != null && result.getWarning().getContents() instanceof TranslatableContents key
                        ? key.getKey().replace("jojo.message.action_condition.", "") : "silent";
            };
            Consumer<Entity> standAim = entity -> f.standAction.entityAim.setTarget(
                    entity != null ? new ActionTarget(entity) : ActionTarget.EMPTY);
            Consumer<Entity> userAim = entity -> LivingComponentAction.getComponent(f.user).entityAim.setTarget(
                    entity != null ? new ActionTarget(entity) : ActionTarget.EMPTY);
            // the 0 x 0 Stand's eyes are at its feet; on the floor's top face the sight line may graze the floor
            f.stand.setPos(f.stand.getX(), f.stand.getY() + 1.0D, f.stand.getZ());
            Vec3 eyes = f.stand.getEyePosition();
            Zombie near = f.target(eyes.add(7.5D, 0.0D, 0.0D));
            Zombie far = f.target(eyes.add(9.0D, 0.0D, 2.0D));
            Zombie hidden = f.target(eyes.add(0.0D, 0.0D, -6.0D));
            standAim.accept(null);

            userAim.accept(near);
            String shareCheck = check.apply(Mode.SHARE_LOVE_TRAIN);
            boolean shared = TicketToRideAbilityService.perform(f.level, f.user, f.power, Mode.SHARE_LOVE_TRAIN);
            boolean hasEffect = near.hasEffect(TicketToRideAddon.SHARED_LOVE_TRAIN);
            String removeCheck = check.apply(Mode.REMOVE_SHARED);
            boolean removed = TicketToRideAbilityService.perform(f.level, f.user, f.power, Mode.REMOVE_SHARED);
            seen.put("near", shareCheck + "/" + removeCheck + " shared=" + shared + "/" + hasEffect + " removed=" + removed
                    + "/" + near.hasEffect(TicketToRideAddon.SHARED_LOVE_TRAIN));
            want.put("near", "ok/ok shared=true/true removed=true/false");

            userAim.accept(far);
            seen.put("far", check.apply(Mode.SHARE_LOVE_TRAIN) + "/" + check.apply(Mode.REMOVE_SHARED));
            want.put("far", "target_too_far/target_too_far");

            userAim.accept(hidden);
            String open = check.apply(Mode.SHARE_LOVE_TRAIN);
            BlockPos wall = BlockPos.containing(eyes.add(0.0D, 0.0D, -3.0D));
            for (BlockPos pos : BlockPos.betweenClosed(wall.offset(-1, -1, 0), wall.offset(1, 2, 0))) {
                f.setBlock(pos.immutable(), Blocks.STONE.defaultBlockState());
            }
            seen.put("hidden", "open=" + open + " walled=" + check.apply(Mode.SHARE_LOVE_TRAIN));
            want.put("hidden", "open=ok walled=target_too_far");

            Boat boat = EntityType.BOAT.create(f.level);
            helper.assertTrue(boat != null, "Could not create the boat");
            boat.moveTo(eyes.x + 4.0D, eyes.y, eyes.z, 0.0F, 0.0F);
            boat.setNoGravity(true);
            helper.assertTrue(f.level.addFreshEntity(boat), "Could not add the boat");
            f.spawned.add(boat);
            userAim.accept(boat);
            seen.put("boat", check.apply(Mode.SHARE_LOVE_TRAIN) + " shared="
                    + TicketToRideAbilityService.perform(f.level, f.user, f.power, Mode.SHARE_LOVE_TRAIN));
            want.put("boat", "ok shared=false");
            boat.discard();

            userAim.accept(null);
            seen.put("empty", check.apply(Mode.SHARE_LOVE_TRAIN));
            want.put("empty", "no_target");
            // 1.16 clientHitResult sent the vanilla pick, the user's aim, whatever the Stand aimed at
            userAim.accept(near);
            seen.put("userAimOnly", check.apply(Mode.SHARE_LOVE_TRAIN));
            want.put("userAimOnly", "ok");
            // the Stand's aim (the near zombie, out of the user's 5-block ray) is not what 1.16 pressed at
            userAim.accept(null);
            standAim.accept(near);
            seen.put("standAimOnly", check.apply(Mode.SHARE_LOVE_TRAIN));
            want.put("standAimOnly", "no_target");

            String sight = "stand=" + f.stand.position() + " eyes=" + eyes + " user=" + f.user.position() + " sees near/far/hidden="
                    + f.stand.hasLineOfSight(near) + "/" + f.stand.hasLineOfSight(far) + "/" + f.stand.hasLineOfSight(hidden)
                    + " blockBelowEyes=" + f.level.getBlockState(BlockPos.containing(eyes).below());
            helper.assertTrue(seen.equals(want), "1.16 GiveLF / RemoveLF: the pressed target within 8 blocks of the Stand,"
                    + " 4 out of its sight, any entity: got " + seen + ", 1.16 " + want + " (" + sight + ")");
            LOGGER.info("R764_TTR_EXPLOD_PASS share_target {} ({})", seen, sight);
        }
        helper.succeed();
    }

    // 1.16 GiveLFAction / RemoveLFAction were plain StandActions (InitStands :69-70) that performed on the target their
    // press was checked with (Action.perform(world, user, power, target)), from the user while no Stand was out
    // (StandAction.getPerformer :190-193). The port's press summons the Stand (D-rotp-ticket-to-ride-04), which takes
    // the user's press aim (the core's rule for a Stand a press summons); the action runs in the user's Stand-power
    // slot, through the new Stand's summon lock, after the user looks away: it still acts on the mob the press was
    // checked with and takes the 400 stamina once.
    @GameTest(template = "empty", batch = "ttr_share_press", timeoutTicks = 100)
    public static void shareAndRemovePressedWithTheStandAwayActOnThePressedMob(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "TtrSharePress", false)) {
            Zombie mob = f.unmarkedZombie(f.user.position().add(2.0D, 0.0D, 0.0D));
            Map<String, String> seen = new LinkedHashMap<>();
            seen.put("share", f.pressWithTheStandAway(TicketToRideAddon.SHARE_LOVE_TRAIN, mob));
            seen.put("remove", f.pressWithTheStandAway(TicketToRideAddon.REMOVE_SHARED, mob));
            Map<String, String> want = new LinkedHashMap<>();
            want.put("share", "passed=true started=true summoned=true standAim=pressed effect=true paid=400.0 over=true");
            want.put("remove", "passed=true started=true summoned=true standAim=pressed effect=false paid=400.0 over=true");
            helper.assertTrue(seen.equals(want), "1.16 GiveLF / RemoveLF pressed with the Stand away: the Stand comes out"
                    + " aimed at the pressed mob (a summoning press gives it its user's aim), that mob gets /"
                    + " loses the passenger effect and 400 stamina is paid once:"
                    + " got " + seen + ", want " + want);
            LOGGER.info("R764_TTR_EXPLOD_PASS share_press {}", seen);
        }
        helper.succeed();
    }

    // 1.16 EntityStandType.clientHitResult (:122-151) sent the vanilla pick when it held an entity other than the Stand,
    // else, with the Stand out, StandEntity.precisionRayTrace from the camera with friendly fire on (:1726-1740): any
    // entity, a teammate or the user's own tamed wolf too. The port's Stand aim keeps only what the Stand may attack
    // (StandEntityAbility.canDefaultTargetEntityForAiming): without the aim assist its crosshair line passes over them,
    // and with it so does its precision ray after a missed pick.
    @GameTest(template = "empty", batch = "ttr_share_allies", timeoutTicks = 100)
    public static void shareAndRemoveTakeTeammatesAndTamedAnimalsAsIn116(GameTestHelper helper) {
        Scoreboard scoreboard = helper.getLevel().getScoreboard();
        PlayerTeam team = scoreboard.addPlayerTeam("ttr_share_allies");
        try (Fixture f = new Fixture(helper, "TtrShareAllies", false)) {
            team.setAllowFriendlyFire(false);
            PlayerClientBroadcastedSettings settings =
                    f.user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get());
            Map<String, String> seen = new LinkedHashMap<>();
            Map<String, String> want = new LinkedHashMap<>();
            Vec3 eyes = f.user.getEyePosition();

            settings.standAimAssist = false;
            Wolf wolf = f.join(f.created(EntityType.WOLF, eyes.add(2.0D, -0.4D, 0.0D), false));
            wolf.setTame(true, false);
            wolf.setOwnerUUID(f.user.getUUID());
            Zombie behind = f.unmarkedZombie(eyes.add(4.0D, -1.0D, 0.0D));
            seen.put("ownWolf", f.shareAndRemove(wolf, true, behind));
            want.put("ownWolf", "standAim=other shared=true/false removed=true");
            wolf.discard();

            Zombie mate = f.unmarkedZombie(eyes.add(2.0D, -1.0D, 0.0D));
            scoreboard.addPlayerToTeam(f.user.getScoreboardName(), team);
            scoreboard.addPlayerToTeam(mate.getScoreboardName(), team);
            seen.put("teammate", f.shareAndRemove(mate, true, behind));
            want.put("teammate", "standAim=other shared=true/false removed=true");
            mate.discard();
            behind.discard();

            // past the 3-block vanilla pick: the press carries no entity, and 1.16 took the precision ray's
            Wolf farWolf = f.join(f.created(EntityType.WOLF, eyes.add(4.0D, -0.4D, 0.0D), false));
            farWolf.setTame(true, false);
            farWolf.setOwnerUUID(f.user.getUUID());
            settings.standAimAssist = true;
            seen.put("missAssist", f.shareAndRemove(farWolf, false, null));
            want.put("missAssist", "standAim=other shared=true/false removed=true");
            settings.standAimAssist = false;
            seen.put("missNoAssist", f.shareAndRemove(farWolf, false, null));
            want.put("missNoAssist", "standAim=other shared=true/false removed=true");

            helper.assertTrue(seen.equals(want), "1.16 GiveLF / RemoveLF took a teammate or the user's own tamed wolf"
                    + " under the crosshair: got " + seen + ", want " + want);
            LOGGER.info("R764_TTR_EXPLOD_PASS share_allies {}", seen);
        }
        finally {
            scoreboard.removePlayerTeam(team);
        }
        helper.succeed();
    }

    // The Tear Blade's life with its owner. 1.16 GameplayHandler.onPlayerTick (:35-66) gave it by player.addItem (the
    // first free main slot, none when the main inventory is full, the selected item left alone) and discarded a dropped
    // one (onEntityJoinWorld); the port keeps one owner-bound blade and purges the others and every blade on unsummon,
    // login, dimension change and death (D-rotp-ticket-to-ride-07). Was TicketToRideInventoryGameTests, never run.
    @GameTest(template = "empty", batch = "ttr_blade_lifecycle", timeoutTicks = 100)
    public static void tearBladeLifecycleWithItsOwner(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "TtrBladeLifecycle", false)) {
            Map<String, String> seen = new LinkedHashMap<>();
            Map<String, String> want = new LinkedHashMap<>();
            Inventory inventory = f.user.getInventory();
            StandType type = TicketToRideAddon.TICKET_TO_RIDE.get();
            Runnable playerTick = () -> TicketToRideEvents.onPlayerTick(new PlayerTickEvent.Post(f.user));
            Runnable clear = () -> {
                for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                    inventory.setItem(slot, ItemStack.EMPTY);
                }
                f.user.containerMenu.setCarried(ItemStack.EMPTY);
            };
            java.util.function.Supplier<String> state = () -> {
                List<String> blades = new ArrayList<>();
                int cobblestone = 0;
                for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                    ItemStack stack = inventory.getItem(slot);
                    if (stack.is(TicketToRideAddon.TEARS_BLADE.get())) {
                        blades.add(slot + (TearsBladeItem.isOwnedBy(stack, f.user) ? "" : "/foreign"));
                    }
                    else if (stack.is(Items.COBBLESTONE)) {
                        cobblestone += stack.getCount();
                    }
                }
                ItemStack carried = f.user.containerMenu.getCarried();
                if (carried.is(TicketToRideAddon.TEARS_BLADE.get())) {
                    blades.add("carried");
                }
                return "blades=" + blades + " selected=" + inventory.selected + ":" + inventory.getSelected().getItem()
                        + " cobblestone=" + cobblestone;
            };

            clear.run();
            inventory.selected = 4;
            for (int slot = 0; slot < 9; slot++) {
                inventory.setItem(slot, new ItemStack(Items.COBBLESTONE));
            }
            playerTick.run();
            seen.put("fullHotbar", state.get());
            want.put("fullHotbar", "blades=[9] selected=4:minecraft:cobblestone cobblestone=9");

            clear.run();
            for (int slot = 0; slot < inventory.items.size(); slot++) {
                inventory.setItem(slot, new ItemStack(Items.COBBLESTONE));
            }
            playerTick.run();
            seen.put("fullInventory", state.get() + " offhand=" + f.user.getOffhandItem().getItem());
            want.put("fullInventory", "blades=[] selected=4:minecraft:cobblestone cobblestone=36 offhand=minecraft:air");

            clear.run();
            inventory.selected = 7;
            playerTick.run();
            int grantedSlot = -1;
            for (int slot = 0; grantedSlot < 0 && slot < inventory.getContainerSize(); slot++) {
                grantedSlot = inventory.getItem(slot).is(TicketToRideAddon.TEARS_BLADE.get()) ? slot : -1;
            }
            helper.assertTrue(grantedSlot >= 0, "No Tear Blade was granted before the drop: " + state.get());
            ItemStack granted = inventory.getItem(grantedSlot).copy();
            inventory.setItem(grantedSlot, ItemStack.EMPTY);
            ItemEntity dropped = f.user.drop(granted, false);
            helper.assertTrue(dropped != null, "The survival drop made no item entity");
            dropped.tick();
            boolean discarded = dropped.isRemoved();
            playerTick.run();
            seen.put("drop", "discarded=" + discarded + " " + state.get());
            want.put("drop", "discarded=true blades=[0] selected=7:minecraft:air cobblestone=0");

            inventory.setItem(9, TearsBladeItem.createOwned(f.user));
            ItemStack foreign = new ItemStack(TicketToRideAddon.TEARS_BLADE.get());
            foreign.set(TicketToRideAddon.TEARS_BLADE_DATA.get(), new TearsBladeData(UUID.randomUUID(), 10));
            inventory.setItem(10, foreign);
            inventory.setItem(36, TearsBladeItem.createOwned(f.user));
            f.user.containerMenu.setCarried(foreign.copy());
            String before = state.get();
            playerTick.run();
            seen.put("duplicates", before + " -> " + state.get());
            want.put("duplicates", "blades=[0, 9, 10/foreign, 36, carried] selected=7:minecraft:air cobblestone=0"
                    + " -> blades=[0] selected=7:minecraft:air cobblestone=0");

            List<String> purges = new ArrayList<>();
            type.forceUnsummon(f.user, f.power);
            purges.add("unsummon=" + state.get());
            helper.assertTrue(type.summon(f.user, f.power), "Could not summon Ticket to Ride again");
            purges.add("summon=" + state.get());
            TicketToRideEvents.onPlayerLogin(new PlayerEvent.PlayerLoggedInEvent(f.user));
            purges.add("login=" + state.get());
            playerTick.run();
            TicketToRideEvents.onPlayerChangedDimension(
                    new PlayerEvent.PlayerChangedDimensionEvent(f.user, Level.OVERWORLD, Level.NETHER));
            purges.add("dimension=" + state.get());
            playerTick.run();
            TicketToRideEvents.onLivingDeath(new LivingDeathEvent(f.user, f.user.damageSources().generic()));
            purges.add("death=" + state.get());
            seen.put("purges", String.valueOf(purges));
            String none = "blades=[] selected=7:minecraft:air cobblestone=0";
            want.put("purges", "[unsummon=" + none + ", summon=blades=[0] selected=7:minecraft:air cobblestone=0, login="
                    + none + ", dimension=" + none + ", death=" + none + "]");

            helper.assertTrue(seen.equals(want), "The Tear Blade's life: 1.16 addItem grant and drop discard, the port's"
                    + " single owner-bound blade and purges: got " + seen + ", want " + want);
            LOGGER.info("R764_TTR_EXPLOD_PASS blade_lifecycle {}", seen);
        }
        helper.succeed();
    }

    // 1.16 LoveTrainAction.spawnFullActivationEffect (:79-119): 8 pillars 1 out (count 1, up 0.05, speed 0.3f) at heights
    // 0..3 step 0.2, 30% a short pillar 1.5 up; 30 + 50 ground pillars 0.1 up within 1 and 3 (speed 0.4f); a wall 4 out
    // (12 x heights 0..2 step 0.3, count 2, speed 0.35f), all around the feet. LoveTrainEventHandler.spawnFullParticleSystem
    // (:173-233), every 5 ticks: the ring (every other of floor(2 pi r) columns, 3 to 50) a block below the feet, count 2;
    // decorative walls at 0.6 r (40%); every 15 ticks the inner ring at 1.3 r; 80% short pillars on the ring; no balls.
    // DispelLoveTrainAction (:45-52): 50 love_train_light at y + 1. LoveTrainAction.onHoldTickClientEffect (:37-65): every
    // 3 held ticks a 4-point spiral at the feet, every 5 three sparks.
    @GameTest(template = "empty", batch = "ttr_love_train_particles", timeoutTicks = 100)
    public static void loveTrainParticlesAreThe116Ones(GameTestHelper helper) {
        Vec3 c = new Vec3(10.5D, 64.0D, 20.5D);
        String pillar = TicketToRideAddon.LOVE_TRAIN_LIGHT_PILLAR.getId().getPath();
        String shortPillar = TicketToRideAddon.LOVE_TRAIN_LIGHT_SHORT.getId().getPath();
        String light = TicketToRideAddon.LOVE_TRAIN_LIGHT.getId().getPath();
        record Burst(String type, Vec3 at, int count, Vec3 spread, double speed) {}
        List<Burst> activation = new ArrayList<>();
        LoveTrainService.activationParticles(c, RandomSource.create(3L), (type, x, y, z, count, dx, dy, dz, speed) ->
                activation.add(new Burst(BuiltInRegistries.PARTICLE_TYPE.getKey(type.getType()).getPath(),
                        new Vec3(x, y, z), count, new Vec3(dx, dy, dz), speed)));
        int heights = 0;
        for (double y = 0.0D; y <= 3.0D; y += 0.2D) {
            heights++;
        }
        int wallHeights = 0;
        for (double y = 0.0D; y <= 2.0D; y += 0.3D) {
            wallHeights++;
        }
        java.util.function.ToDoubleFunction<Burst> across = b -> Math.hypot(b.at().x - c.x, b.at().z - c.z);
        Map<String, String> seen = new LinkedHashMap<>();
        Map<String, String> want = new LinkedHashMap<>();
        seen.put("activation", "pillars1=" + activation.stream().filter(b -> b.type().equals(pillar) && b.count() == 1
                        && Math.abs(across.applyAsDouble(b) - 1.0D) < 1.0E-9D && b.speed() == (double) 0.3F).count()
                + " ground=" + activation.stream().filter(b -> b.type().equals(pillar) && b.at().y == c.y + 0.1D
                        && b.speed() == (double) 0.4F && across.applyAsDouble(b) <= 3.0D).count()
                + " wall4=" + activation.stream().filter(b -> b.type().equals(pillar) && b.count() == 2
                        && Math.abs(across.applyAsDouble(b) - 4.0D) < 1.0E-9D && b.speed() == (double) 0.35F).count()
                + " shorts=" + activation.stream().filter(b -> b.type().equals(shortPillar) && b.at().y == c.y + 1.5D).count()
                + " other=" + activation.stream().filter(b -> !b.type().equals(pillar) && !b.type().equals(shortPillar))
                        .count());
        long shorts = activation.stream().filter(b -> b.type().equals(shortPillar)).count();
        want.put("activation", "pillars1=" + 8 * heights + " ground=80 wall4=" + 12 * wallHeights + " shorts=" + shorts
                + " other=0");
        helper.assertTrue(shorts <= 8 && activation.size() == 8 * heights + 80 + 12 * wallHeights + shorts,
                "Setup: unexpected activation burst count " + activation.size());

        List<Burst> ring = new ArrayList<>();
        LoveTrainService.persistentParticles(c, 5.0F, true, RandomSource.create(4L), (type, x, y, z, count, dx, dy, dz,
                speed) -> ring.add(new Burst(BuiltInRegistries.PARTICLE_TYPE.getKey(type.getType()).getPath(),
                        new Vec3(x, y, z), count, new Vec3(dx, dy, dz), speed)));
        seen.put("ring", "columns=" + LoveTrainService.columns(5.0F) + " ring=" + ring.stream().filter(b ->
                        b.type().equals(pillar) && b.count() == 2 && b.at().y == c.y - 1.0D
                        && Math.abs(across.applyAsDouble(b) - 5.0D) < 1.0E-9D).count()
                + " inner=" + ring.stream().filter(b -> b.type().equals(pillar) && b.count() == 1
                        && Math.abs(across.applyAsDouble(b) - 6.5D) < 1.0E-9D).count()
                + " aboveFeet=" + ring.stream().filter(b -> b.at().y > c.y).count()
                + " other=" + ring.stream().filter(b -> !b.type().equals(pillar) && !b.type().equals(shortPillar)).count());
        want.put("ring", "columns=31 ring=16 inner=3 aboveFeet=0 other=0");

        List<Burst> dispel = new ArrayList<>();
        LoveTrainService.dispelParticles(c, (type, x, y, z, count, dx, dy, dz, speed) -> dispel.add(new Burst(
                BuiltInRegistries.PARTICLE_TYPE.getKey(type.getType()).getPath(), new Vec3(x, y, z), count,
                new Vec3(dx, dy, dz), speed)));
        seen.put("dispel", String.valueOf(dispel));
        want.put("dispel", String.valueOf(List.of(new Burst(light, c.add(0.0D, 1.0D, 0.0D), 50,
                new Vec3(0.5D, 0.5D, 0.5D), 0.2D))));

        List<String> hold = new ArrayList<>();
        for (int ticks : new int[] {3, 4, 5, 15}) {
            List<String> spawned = new ArrayList<>();
            LoveTrainService.holdParticles(c, ticks, RandomSource.create(ticks), (type, x, y, z, vx, vy, vz) -> {
                String id = BuiltInRegistries.PARTICLE_TYPE.getKey(type.getType()).getPath();
                spawned.add(id.equals(light) && y == c.y
                        ? String.format(Locale.ROOT, "spiral/%.4f/%.5f", Math.hypot(x - c.x, z - c.z), vy)
                        : y >= c.y + 1.0D && y <= c.y + 3.0D ? "spark" : "other:" + id + "@" + (y - c.y));
            });
            hold.add(ticks + "=" + spawned.stream().distinct().sorted().toList() + "x" + spawned.size());
        }
        seen.put("hold", String.valueOf(hold));
        want.put("hold", "[3=[spiral/0.8375/0.05188]x4, 4=[]x0, 5=[spark]x3, 15=[spark, spiral/0.9875/0.05938]x7]");
        helper.assertTrue(seen.equals(want), "1.16 Love Train particles (activation, the ring every 5 ticks, dispel, the"
                + " held charge): got " + seen + ", 1.16 " + want);
        LOGGER.info("R764_TTR_EXPLOD_PASS love_train_particles {}", seen);
        helper.succeed();
    }

    /** The first seed whose fresh random passes the roll. */
    private static long seedWhere(java.util.function.Predicate<RandomSource> roll) {
        long seed = 1L;
        while (!roll.test(RandomSource.create(seed))) {
            seed++;
        }
        return seed;
    }

    /** The point 1.16 findValidSpawnPosition found within its ten tries, or null when it fell back. */
    @Nullable
    private static Vec3 horseSpawn116(ServerLevel level, LivingEntity target, RandomSource draws) {
        for (int i = 0; i < 10; i++) {
            double distance = 5.0D + draws.nextDouble() * 10.0D;
            double yaw = draws.nextDouble() * Math.PI * 2.0D;
            double pitch = draws.nextDouble() * Math.PI - Math.PI / 2.0D;
            Vec3 at = new Vec3(target.getX() + distance * Math.cos(yaw) * Math.cos(pitch),
                    target.getY() + distance * Math.sin(pitch),
                    target.getZ() + distance * Math.sin(yaw) * Math.cos(pitch));
            BlockPos pos = BlockPos.containing(at);
            if (level.isInWorldBounds(pos) && !level.getBlockState(pos).liquid()
                    && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                    && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) {
                return at;
            }
        }
        return null;
    }

    private static String blockName(BlockState state) {
        return state.getBlock().builtInRegistryHolder().key().location().getPath();
    }

    // 1.16 SingleLightingHit.standPerform (:62-79): real LightningBoltEntity bolts with no cause and setDamage(6 clear,
    // 8 rain, 10 thunder), 1, 2 or 3 of them, each at the target's position plus (nextDouble - 0.5) * 2 on x and z from
    // the world's random. The target was hurt by the bolt's own strike (lightning damage and fire), not directly.
    @GameTest(template = "empty", batch = "ttr_bolts", timeoutTicks = 100)
    public static void lightingSingleSummonsRealBoltsAroundTheTarget(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "TtrBolts", false)) {
            f.listenToJoins();
            Mob cow = f.join(f.created(EntityType.COW, f.ahead(), false));
            f.mark(cow);
            GameRules.BooleanValue fireTick = f.level.getGameRules().getRule(GameRules.RULE_DOFIRETICK);
            boolean fireTickBefore = fireTick.get();
            float rainBefore = f.level.getRainLevel(1.0F);
            float thunderBefore = f.level.getThunderLevel(1.0F);
            try {
                // no fire blocks in the test area; the bolt still sets the cow alight
                fireTick.set(false, f.level.getServer());
                f.level.setRainLevel(0.0F);
                f.level.setThunderLevel(0.0F);
                List<LightningBolt> clear = f.lightingSingle(7L);
                float afterPerform = cow.getHealth();
                String clearState = describeBolts(clear, cow, 7L, 6.0F);
                helper.assertTrue(clear.size() == 1 && clearState.startsWith("ok"),
                        "1.16 struck one real 6-damage bolt up to 1 block off the target in clear weather: " + clearState);
                helper.assertTrue(afterPerform == cow.getMaxHealth(),
                        "1.16 did not hurt the target before its bolt struck: health=" + afterPerform);
                clear.get(0).tick();
                DamageSource source = cow.getLastDamageSource();
                String struck = "health=" + cow.getHealth() + "/" + cow.getMaxHealth() + " source="
                        + describe(source) + " fire=" + cow.getRemainingFireTicks();
                helper.assertTrue(source != null && source.is(DamageTypes.LIGHTNING_BOLT)
                                && Math.abs(cow.getMaxHealth() - cow.getHealth() - 6.0F) < 1.0E-4F
                                && cow.getRemainingFireTicks() > 0,
                        "1.16 bolt struck the target for its 6 lightning damage and set it alight: " + struck);

                f.level.setRainLevel(1.0F);
                f.level.setThunderLevel(1.0F);
                helper.assertTrue(f.level.isThundering(), "Setup: could not make it thunder");
                List<LightningBolt> storm = f.lightingSingle(8L);
                String stormState = describeBolts(storm, cow, 8L, 10.0F);
                helper.assertTrue(storm.size() == 3 && stormState.startsWith("ok"),
                        "1.16 struck three real 10-damage bolts in a thunderstorm: " + stormState);
                LOGGER.info("R764_TTR_EXPLOD_PASS bolts clear[{}] struck[{}] storm[{}]", clearState, struck, stormState);
            }
            finally {
                fireTick.set(fireTickBefore, f.level.getServer());
                f.level.setRainLevel(rainBefore);
                f.level.setThunderLevel(thunderBefore);
            }
        }
        helper.succeed();
    }

    // 1.16 lightinghit.standPerform and strikeEntityWithLightning (:48-69): for every marked entity but the user in the
    // user's box grown by 100, 1, 2 or 3 real bolts with no cause and setDamage(6 clear, 8 rain, 10 thunder), each at
    // the target plus (nextDouble - 0.5) * 2 on x and z, then the Stand summon sound at the target (HOSTILE, volume 1,
    // pitch 0.8 + nextFloat * 0.4), all from the world's random in that order. No damage of its own.
    @GameTest(template = "empty", batch = "ttr_bolts_all", timeoutTicks = 100)
    public static void lightingStrikesRealBoltsAndTheSummonSoundAtEveryMarkedEntity(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "TtrBoltsAll", false)) {
            f.listenToJoins();
            f.listenToSounds();
            Vec3 feet = f.user.position();
            Mob left = f.join(f.created(EntityType.COW, feet.add(6.0D, 0.0D, -4.0D), false));
            Mob right = f.join(f.created(EntityType.COW, feet.add(6.0D, 0.0D, 4.0D), false));
            Mob unmarked = f.join(f.created(EntityType.COW, feet.add(11.0D, 0.0D, 0.0D), false));
            f.mark(left);
            f.mark(right);
            f.mark(f.user);
            List<LivingEntity> order = f.level.getEntitiesOfClass(LivingEntity.class,
                    f.user.getBoundingBox().inflate(100.0D)).stream()
                    .filter(entity -> TearsWoundService.isMarked(entity) && entity != f.user).toList();
            helper.assertTrue(order.size() == 2 && order.containsAll(List.of(left, right)),
                    "Setup: the 1.16 search does not find exactly the two marked cows: " + order);
            GameRules.BooleanValue fireTick = f.level.getGameRules().getRule(GameRules.RULE_DOFIRETICK);
            boolean fireTickBefore = fireTick.get();
            float rainBefore = f.level.getRainLevel(1.0F);
            float thunderBefore = f.level.getThunderLevel(1.0F);
            try {
                fireTick.set(false, f.level.getServer());
                f.level.setRainLevel(0.0F);
                f.level.setThunderLevel(0.0F);
                List<LightningBolt> clear = f.lighting(TicketToRideAddon.LIGHTNING, 30, 11L);
                String clearState = describeLighting(clear, f.summonSounds(), order, 11L, 1, 6.0F);
                helper.assertTrue(clearState.startsWith("ok"),
                        "1.16 struck one real 6-damage bolt up to 1 block off every marked entity but the user and played"
                                + " the Stand summon sound there, in clear weather: " + clearState);
                String untouched = "left=" + left.getHealth() + " right=" + right.getHealth() + " unmarked="
                        + unmarked.getHealth() + "/" + unmarked.getRemainingFireTicks();
                helper.assertTrue(left.getHealth() == left.getMaxHealth() && right.getHealth() == right.getMaxHealth()
                                && unmarked.getHealth() == unmarked.getMaxHealth(),
                        "1.16 lighting did no damage of its own before its bolts struck: " + untouched);
                LightningBolt first = clear.stream().filter(bolt -> bolt.distanceToSqr(left) < 4.0D).findFirst()
                        .orElseThrow();
                first.tick();
                DamageSource source = left.getLastDamageSource();
                String struck = "health=" + left.getHealth() + "/" + left.getMaxHealth() + " source=" + describe(source)
                        + " fire=" + left.getRemainingFireTicks();
                helper.assertTrue(source != null && source.is(DamageTypes.LIGHTNING_BOLT)
                                && Math.abs(left.getMaxHealth() - left.getHealth() - 6.0F) < 1.0E-4F
                                && left.getRemainingFireTicks() > 0,
                        "1.16 bolt struck its target for 6 lightning damage and set it alight: " + struck);

                f.level.setRainLevel(1.0F);
                f.level.setThunderLevel(1.0F);
                helper.assertTrue(f.level.isThundering(), "Setup: could not make it thunder");
                List<LightningBolt> storm = f.lighting(TicketToRideAddon.LIGHTNING, 30, 12L);
                String stormState = describeLighting(storm, f.summonSounds(), order, 12L, 3, 10.0F);
                helper.assertTrue(stormState.startsWith("ok"),
                        "1.16 struck three real 10-damage bolts at every marked entity in a thunderstorm: " + stormState);
                LOGGER.info("R764_TTR_EXPLOD_PASS bolts_all clear[{}] untouched[{}] struck[{}] storm[{}]", clearState,
                        untouched, struck, stormState);
            }
            finally {
                fireTick.set(fireTickBefore, f.level.getServer());
                f.level.setRainLevel(rainBefore);
                f.level.setThunderLevel(thunderBefore);
            }
        }
        helper.succeed();
    }

    /** "ok ..." when the bolts and summon sounds are 1.16's for these targets, drawn in order from the seed. */
    private static String describeLighting(List<LightningBolt> bolts, List<PlayedSound> summons,
            List<LivingEntity> targets, long seed, int perTarget, float damage) {
        RandomSource draws = RandomSource.create(seed);
        boolean ok = bolts.size() == targets.size() * perTarget && summons.size() == targets.size();
        List<String> seen = new ArrayList<>();
        int bolt = 0;
        for (int t = 0; t < targets.size(); t++) {
            LivingEntity target = targets.get(t);
            for (int i = 0; i < perTarget && bolt < bolts.size(); i++, bolt++) {
                LightningBolt struck = bolts.get(bolt);
                double x = target.getX() + (draws.nextDouble() - 0.5D) * 2.0D;
                double z = target.getZ() + (draws.nextDouble() - 0.5D) * 2.0D;
                ok &= struck.getX() == x && struck.getY() == target.getY() && struck.getZ() == z
                        && struck.getDamage() == damage && struck.getCause() == null;
                seen.add(String.format(Locale.ROOT, "%d(%+.3f, %+.3f, %+.3f)/%s/cause=%s", t,
                        struck.getX() - target.getX(), struck.getY() - target.getY(), struck.getZ() - target.getZ(),
                        struck.getDamage(), struck.getCause()));
            }
            float pitch = 0.8F + draws.nextFloat() * 0.4F;
            if (t < summons.size()) {
                PlayedSound sound = summons.get(t);
                ok &= sound.pos().equals(target.position()) && sound.source() == SoundSource.HOSTILE
                        && sound.volume() == 1.0F && sound.pitch() == pitch;
                seen.add(String.format(Locale.ROOT, "%d:summon/%s/%.1f/%.4f", t, sound.source(), sound.volume(),
                        sound.pitch()));
            }
        }
        return (ok ? "ok " : "differs ") + seen + " bolts=" + bolts.size() + " sounds=" + summons.size();
    }

    // 1.16 SummonDeadphotomemtorAction.summonDeadphotomemtorAboveTarget (:54-62): the Dead Photo Memento was put at the
    // target's x - 15, y + 21, z + 15.1.
    @GameTest(template = "empty", batch = "ttr_meteor_spawn", timeoutTicks = 100)
    public static void meteorDropsTheMementoFromThe116Point(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "TtrMeteorSpawn", false)) {
            f.listenToJoins();
            Zombie target = f.target(f.ahead());
            f.use(TicketToRideAddon.METEOR, 20, false);
            List<Entity> mementos = f.joined.stream().filter(DeadPhotoMementoEntity.class::isInstance).toList();
            Vec3 expected = new Vec3(target.getX() - 15.0D, target.getY() + 21.0D, target.getZ() + 15.1D);
            String state = "mementos=" + mementos.stream().map(entity -> entity.position().subtract(target.position()))
                    .toList();
            helper.assertTrue(mementos.size() == 1 && mementos.get(0).position().equals(expected),
                    "1.16 put the Dead Photo Memento at the target's x - 15, y + 21, z + 15.1: " + state);
            LOGGER.info("R764_TTR_EXPLOD_PASS meteor_spawn {}", state);
        }
        helper.succeed();
    }

    // Owner decision (wave 28): the donor's attackRecoveryFollowup(LIGHTING) on meteor (InitStands :66), which the 1.16
    // core never offered. While meteor's task recovers and lighting can be used (1.16 canSetFollowUp), the meteor slot
    // resolves to lighting, and a press of it ends the recovery and starts lighting's charge at lighting's own cost;
    // otherwise the slot is meteor and its press starts nothing during the recovery.
    @GameTest(template = "empty", batch = "ttr_meteor_followup", timeoutTicks = 100)
    public static void meteorRecoveryFollowsUpIntoLighting(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "TtrMeteorFollowUp", false)) {
            String meteorSlot = TicketToRideAddon.METEOR;
            String lightingSlot = TicketToRideAddon.LIGHTNING;
            EntityActionAbility meteor = f.ability(meteorSlot);
            EntityActionAbility lighting = f.ability(lightingSlot);
            f.reset();
            f.power.setAbilityCooldown(lightingSlot, 0);
            String idle = f.slot(meteorSlot);
            helper.assertTrue(idle.equals("meteor/ok"), "With no Stand action the meteor slot is not meteor: " + idle);

            helper.assertTrue(f.press(meteor), "meteor did not start");
            f.standAction.tick();
            String windup = describeAction(f.standAction.getAction()) + " slot=" + f.slot(meteorSlot);
            helper.assertTrue(windup.equals("meteor@WINDUP slot=meteor/ok"),
                    "During meteor's charge the meteor slot is not meteor: " + windup);
            EntityActionInstance meteorTask = f.toRecovery();
            AbilityInput.keyRelease(KEY, f.user);
            String recovery = "slot=" + f.slot(meteorSlot) + " own=" + f.slot(lightingSlot);
            helper.assertTrue(recovery.equals("slot=lighting/ok own=lighting/ok"),
                    "During meteor's recovery the meteor slot does not resolve to lighting: " + recovery);

            f.power.setStamina(f.power.getMaxStamina());
            float stamina = f.power.getStamina();
            BufferingState pressed = f.pressSlot(meteorSlot);
            EntityActionInstance started = f.standAction.getAction();
            String press = "started=" + describeAction(started) + " meteorOver=" + meteorTask.isOver()
                    + " buffered=" + pressed.shouldBuffer;
            helper.assertTrue(started != null && started.ability == lighting && started.getPhase() == ActionPhase.WINDUP
                            && meteorTask.isOver() && pressed.isActionSuccess && !pressed.shouldBuffer,
                    "A press of the meteor slot during meteor's recovery did not end it and start lighting's charge: "
                            + press);
            helper.assertTrue(f.charge(32), "lighting was not fully charged after 32 ticks: "
                    + describeAction(f.standAction.getAction()));
            f.release(BRANCH_TNT);
            String paid = "stamina=-" + (stamina - f.power.getStamina())
                    + " cooldown=" + f.power.getAbilityCooldown(lightingSlot)
                    + " meteorCooldown=" + f.power.getAbilityCooldown(meteorSlot);
            helper.assertTrue(Math.abs(stamina - f.power.getStamina() - 75.0F) < 0.01F
                            && f.power.getAbilityCooldown(lightingSlot) == 1200
                            && f.power.getAbilityCooldown(meteorSlot) == 0,
                    "The follow-up lighting did not cost its own 75 stamina and 1200-tick cooldown: " + paid);

            // lighting cooling down: meteor's recovery keeps the meteor slot, and its press starts nothing new
            f.reset();
            helper.assertTrue(f.press(meteor), "meteor did not start a second time");
            meteorTask = f.toRecovery();
            AbilityInput.keyRelease(KEY, f.user);
            String coolingSlot = f.slot(meteorSlot);
            BufferingState again = f.pressSlot(meteorSlot);
            String cooling = "slot=" + coolingSlot + " pressed=" + describeAction(f.standAction.getAction())
                    + " same=" + (f.standAction.getAction() == meteorTask) + " buffered=" + again.shouldBuffer;
            helper.assertTrue(coolingSlot.equals("meteor/ok") && f.standAction.getAction() == meteorTask
                            && meteorTask.getPhase() == ActionPhase.RECOVERY && !again.isActionSuccess,
                    "With lighting on its cooldown, meteor's recovery did not keep the meteor slot, or its press did"
                            + " something: " + cooling);
            AbilityInput.keyRelease(KEY, f.user);
            for (int tick = 0; tick < 25 && f.standAction.getAction() != null; tick++) {
                f.standAction.tick();
            }
            f.power.setAbilityCooldown(lightingSlot, 0);
            String after = describeAction(f.standAction.getAction()) + " slot=" + f.slot(meteorSlot);
            helper.assertTrue(after.equals("none slot=meteor/ok"),
                    "Once meteor's recovery is over the meteor slot is not meteor again: " + after);

            // lighting's own slot also ends meteor's recovery
            helper.assertTrue(f.press(meteor), "meteor did not start a third time");
            meteorTask = f.toRecovery();
            AbilityInput.keyRelease(KEY, f.user);
            f.pressSlot(lightingSlot);
            String ownSlot = describeAction(f.standAction.getAction()) + " meteorOver=" + meteorTask.isOver();
            helper.assertTrue(ownSlot.equals("lighting@WINDUP meteorOver=true"),
                    "lighting pressed on its own slot did not end meteor's recovery: " + ownSlot);

            // another move's recovery neither offers lighting nor lets it in
            f.reset();
            helper.assertTrue(f.press(f.explod) && f.charge(CHARGE_TICKS), "explod did not charge");
            f.release(BRANCH_TNT);
            f.standAction.tick();
            EntityActionInstance explodTask = f.standAction.getAction();
            String explodSlot = describeAction(explodTask) + " slot=" + f.slot(meteorSlot);
            BufferingState intoExplod = f.pressSlot(lightingSlot);
            String explodRecovery = explodSlot + " pressed=" + describeAction(f.standAction.getAction())
                    + " buffered=" + intoExplod.shouldBuffer;
            helper.assertTrue(explodSlot.equals("explod@RECOVERY slot=meteor/ok") && f.standAction.getAction() == explodTask
                            && !intoExplod.isActionSuccess,
                    "explod's recovery offered lighting in the meteor slot or let lighting cut it: " + explodRecovery);
            AbilityInput.keyRelease(KEY, f.user);
            LOGGER.info("R764_TTR_EXPLOD_PASS meteor_followup {idle={}, windup={}, recovery={}, press={}, paid={},"
                    + " cooling={}, after={}, ownSlot={}, explodRecovery={}}", idle, windup, recovery, press, paid,
                    cooling, after, ownSlot, explodRecovery);
        }
        helper.succeed();
    }

    // 1.16 InitStands (034) built every move but share, remove and removel with a hold (holdToFire, holdType), and
    // StandEntityAction.canBeQueued (getHoldDurationMax == 0) queued none of them behind the Stand's task; its client
    // sent no press of them while the power held an action (ActionsOverlayGui.onActionClick :2121-2123). During explod's
    // recovery, which holds their presses back in the input buffer, they are drawn by their own checks, as the port draws
    // its other held actions, not queued (0.75). removel stays a click (the owner's decision), share and remove too.
    @GameTest(template = "empty", batch = "ttr_held_draw", timeoutTicks = 100)
    public static void heldMovesAreDrawnByTheirOwnChecksDuringAStandTaskAsIn116(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "TtrHeldDraw", false)) {
            List<String> held = List.of(TicketToRideAddon.EXPLOD, TicketToRideAddon.TRACE, TicketToRideAddon.CRASH,
                    TicketToRideAddon.RANDOM_MOB, TicketToRideAddon.LIGHTNING_SINGLE, TicketToRideAddon.LIGHTNING,
                    TicketToRideAddon.METEOR, TicketToRideAddon.LOVE_TRAIN_NAME, TicketToRideAddon.ITEM_FILL_NAME,
                    TicketToRideAddon.GIVE_TEARS_NAME);
            List<String> usable = List.of(TicketToRideAddon.CRASH, TicketToRideAddon.RANDOM_MOB,
                    TicketToRideAddon.LIGHTNING_SINGLE, TicketToRideAddon.LIGHTNING, TicketToRideAddon.METEOR,
                    TicketToRideAddon.LOVE_TRAIN_NAME, TicketToRideAddon.GIVE_TEARS_NAME);
            f.reset();
            helper.assertTrue(f.press(f.explod) && f.charge(CHARGE_TICKS), "Setup: explod did not charge");
            f.release(BRANCH_TNT);
            f.standAction.tick();
            EntityActionInstance explodTask = f.standAction.getAction();
            helper.assertTrue(explodTask != null && explodTask.ability == f.explod
                            && explodTask.getPhase() == ActionPhase.RECOVERY,
                    "Setup: explod is not in its recovery: " + describeAction(explodTask));
            AvailableAbilities moves = f.freshMoves();
            List<String> drawn = new ArrayList<>();
            List<String> wrong = new ArrayList<>();
            for (String name : held) {
                if (name.equals(TicketToRideAddon.EXPLOD)) {
                    continue;
                }
                AbilityConditionCheck slot = moves.getContextVariationContainer(name);
                ConditionCheck look = slot != null ? slot.drawnCondition(f.user) : null;
                String row = name + "=" + (slot == null ? "none" : (slot.conditionCheck.isPositive() ? "ok" : "refused")
                        + "/" + (look == null ? "nothing" : look == ConditionCheck.NEGATIVE_QUEUED ? "queued"
                                : look.isPositive() ? "usable" : "greyed")
                        + (f.ability(name).wouldBufferInput(f.user) ? "/buffered" : "/taken"));
                drawn.add(row);
                if (slot == null || look != slot.conditionCheck
                        || usable.contains(name) && !(slot.conditionCheck.isPositive()
                                && f.ability(name).wouldBufferInput(f.user))) {
                    wrong.add(row);
                }
            }
            helper.assertTrue(wrong.isEmpty(), "1.16 queued no held move behind the Stand's task: during explod's"
                    + " recovery, which keeps their presses in the input buffer, they must be drawn by their own checks"
                    + " (usable ones usable, not queued): " + wrong + " of " + drawn);
            List<String> flags = new ArrayList<>();
            for (String name : List.of(TicketToRideAddon.EXPLOD, TicketToRideAddon.TRACE, TicketToRideAddon.CRASH,
                    TicketToRideAddon.RANDOM_MOB, TicketToRideAddon.LIGHTNING_SINGLE, TicketToRideAddon.LIGHTNING,
                    TicketToRideAddon.METEOR, TicketToRideAddon.LOVE_TRAIN_NAME, TicketToRideAddon.ITEM_FILL_NAME,
                    TicketToRideAddon.GIVE_TEARS_NAME, TicketToRideAddon.SHARE_LOVE_TRAIN,
                    TicketToRideAddon.REMOVE_SHARED, TicketToRideAddon.REMOVE_LOVE_TRAIN)) {
                if (f.ability(name).isHoldAction() != held.contains(name)) {
                    flags.add(name + "=" + f.ability(name).isHoldAction());
                }
            }
            helper.assertTrue(flags.isEmpty(), "isHoldAction must answer 1.16's hold (holdToFire / holdType) for every"
                    + " move but share, remove and the dispel (a click): " + flags);
            f.reset();
            LOGGER.info("R764_TTR_EXPLOD_PASS held_draw {}", drawn);
        }
        helper.succeed();
    }

    // 1.16 GiveLFAction / RemoveLFAction (InitStands :69-70) were plain StandActions that the user's power performed at
    // once (PowerBaseImpl.onClickAction), never the Stand's task, so no task queued them (StandEntityAction.canBeQueued
    // and checkTaskCancelling were the Stand's tasks' rules). In explod's recovery, which holds the Stand's next press
    // back, share and remove are drawn by their own checks and go off at once in the user's Stand-power slot, beside the
    // recovery, which runs on. During explod's held charge 1.16 refused them (checkRequirements: the held action let
    // none through); the port keeps that press in the input buffer (owner decision N2) and draws the slot unavailable.
    @GameTest(template = "empty", batch = "ttr_share_beside_task", timeoutTicks = 100)
    public static void shareAndRemoveGoOffAtOnceBesideAStandTaskAsIn116(GameTestHelper helper) {
        short pressKey = 8;
        try (Fixture f = new Fixture(helper, "TtrShareBesideTask", false)) {
            Zombie mob = f.unmarkedZombie(f.user.position().add(2.0D, 0.0D, 0.0D));
            LivingComponentAction userSlot = LivingComponentAction.getComponent(f.user, PowerClass.STAND);
            java.util.function.BiFunction<String, EntityActionInstance, String> pressBeside = (name, task) -> {
                EntityActionAbility ability = f.ability(name);
                LivingComponentAction.getComponent(f.user).entityAim.setTarget(new ActionTarget(mob));
                f.power.setStamina(f.power.getMaxStamina());
                float before = f.power.getStamina();
                ActionPhase taskPhase = task.getPhase();
                float taskTick = task.getPhaseTick();
                AbilityConditionCheck slot = f.freshMoves().getContextVariationContainer(name);
                ConditionCheck look = slot != null ? slot.drawnCondition(f.user) : null;
                String drawn = slot == null || slot.ability != ability ? "none"
                        : look == ConditionCheck.NEGATIVE_QUEUED ? "queued" : look.isPositive() ? "usable" : "greyed";
                AbilityConditionCheck check = f.freshMoves().getCheckedContainerFor(f.power, ability);
                BufferingState state = BufferingState.clickCanBuffer();
                if (AbilityInput.withConditionCheck(check, f.user, InputMethod.CLICK)) {
                    AbilityInput.keyPress(pressKey, check.ability, f.user, null, InputMethod.CLICK, 0.0F, state,
                            ability.abilityId);
                }
                AbilityInput.keyRelease(pressKey, f.user);
                EntityActionInstance own = userSlot.getAction();
                String inSlot = own == null ? "none" : own.ability == ability ? "own" : "other";
                for (int tick = 0; tick < 5 && userSlot.getAction() != null; tick++) {
                    userSlot.tick();
                }
                return "drawn=" + drawn + " started=" + state.isActionSuccess + " buffered=" + state.shouldBuffer
                        + " userSlot=" + inSlot + " effect=" + mob.hasEffect(TicketToRideAddon.SHARED_LOVE_TRAIN)
                        + " paid=" + (before - f.power.getStamina()) + " task=" + (f.standAction.getAction() == task
                                && !task.isOver() && task.getPhase() == taskPhase && task.getPhaseTick() == taskTick
                                ? "unchanged" : describeAction(f.standAction.getAction()));
            };
            Map<String, String> seen = new LinkedHashMap<>();
            Map<String, String> want = new LinkedHashMap<>();

            f.reset();
            helper.assertTrue(f.press(f.explod) && f.charge(CHARGE_TICKS), "Setup: explod did not charge");
            f.release(BRANCH_TNT);
            f.standAction.tick();
            EntityActionInstance recovery = f.standAction.getAction();
            helper.assertTrue(recovery != null && recovery.ability == f.explod
                            && recovery.getPhase() == ActionPhase.RECOVERY,
                    "Setup: explod is not in its recovery: " + describeAction(recovery));
            helper.assertTrue(f.ability(TicketToRideAddon.REMOVE_LOVE_TRAIN).isQueuedBehindStandTask(f.user),
                    "Setup: explod's recovery does not queue the dispel, a click the Stand performs");
            seen.put("shareInRecovery", pressBeside.apply(TicketToRideAddon.SHARE_LOVE_TRAIN, recovery));
            want.put("shareInRecovery", "drawn=usable started=true buffered=false userSlot=own effect=true paid=400.0"
                    + " task=unchanged");
            seen.put("removeInRecovery", pressBeside.apply(TicketToRideAddon.REMOVE_SHARED, recovery));
            want.put("removeInRecovery", "drawn=usable started=true buffered=false userSlot=own effect=false"
                    + " paid=400.0 task=unchanged");

            f.reset();
            helper.assertTrue(f.press(f.explod), "Setup: explod did not start again");
            f.standAction.tick();
            EntityActionInstance charge = f.standAction.getAction();
            helper.assertTrue(charge != null && charge.ability == f.explod && charge.getPhase() == ActionPhase.WINDUP
                            && AbilityInput.getHeldAction(f.user, PowerClass.STAND) == charge,
                    "Setup: explod is not held in its charge: " + describeAction(charge));
            seen.put("shareInCharge", pressBeside.apply(TicketToRideAddon.SHARE_LOVE_TRAIN, charge));
            want.put("shareInCharge", "drawn=greyed started=false buffered=true userSlot=none effect=false paid=0.0"
                    + " task=unchanged");

            helper.assertTrue(seen.equals(want), "1.16 performed share / remove at once beside the Stand's task and"
                    + " refused them during a held charge (the port buffers that press): got " + seen + ", want "
                    + want);
            f.reset();
            LOGGER.info("R764_TTR_EXPLOD_PASS share_beside_task {}", seen);
        }
        helper.succeed();
    }

    private static String describeAction(@Nullable EntityActionInstance action) {
        return action == null || action.isOver() ? "none"
                : (action.ability instanceof Ability ability ? ability.name() : "?") + "@" + action.getPhase();
    }

    /** "ok ..." when every bolt is real, has no cause, the damage and the 1.16 place drawn from the seed. */
    private static String describeBolts(List<LightningBolt> bolts, LivingEntity target, long seed, float damage) {
        RandomSource draws = RandomSource.create(seed);
        boolean ok = true;
        List<String> seen = new ArrayList<>();
        for (LightningBolt bolt : bolts) {
            double x = target.getX() + (draws.nextDouble() - 0.5D) * 2.0D;
            double z = target.getZ() + (draws.nextDouble() - 0.5D) * 2.0D;
            ok &= bolt.getX() == x && bolt.getY() == target.getY() && bolt.getZ() == z
                    && bolt.getDamage() == damage && bolt.getCause() == null;
            seen.add(String.format(Locale.ROOT, "(%+.3f, %+.3f, %+.3f)/%s/cause=%s", bolt.getX() - target.getX(),
                    bolt.getY() - target.getY(), bolt.getZ() - target.getZ(), bolt.getDamage(), bolt.getCause()));
        }
        return (ok ? "ok " : "differs ") + seen;
    }

    private static String effectsOf(LivingEntity entity) {
        return entity.getActiveEffects().stream().map(effect -> effect.getEffect().getRegisteredName() + "/"
                + effect.getDuration() + "/" + effect.getAmplifier()).sorted().toList().toString();
    }

    private record GoalChange(List<WrappedGoal> added, List<WrappedGoal> removed) {
        static GoalChange onJoin(Fixture f, Mob mob) {
            Set<WrappedGoal> before = new HashSet<>(mob.targetSelector.getAvailableGoals());
            f.join(mob);
            Set<WrappedGoal> after = mob.targetSelector.getAvailableGoals();
            return new GoalChange(after.stream().filter(goal -> !before.contains(goal)).toList(),
                    before.stream().filter(goal -> !after.contains(goal)).toList());
        }

        @Override
        public String toString() {
            return "added=" + describe(added) + " removed=" + describe(removed);
        }

        private static List<String> describe(List<WrappedGoal> goals) {
            return goals.stream().map(goal -> goal.getPriority() + ":" + goal.getGoal().getClass().getSimpleName())
                    .sorted().toList();
        }
    }

    /** Runs the goal's own target search until it starts, and returns what it set as the target. */
    @Nullable
    private static LivingEntity firstTarget(Mob mob, WrappedGoal goal) {
        for (int i = 0; i < 200; i++) {
            if (goal.canUse()) {
                goal.start();
                LivingEntity target = mob.getTarget();
                goal.stop();
                mob.setTarget(null);
                return target;
            }
        }
        return null;
    }

    private static String describeTarget(@Nullable LivingEntity target, LivingEntity hidden, LivingEntity unmarked,
            LivingEntity seen) {
        return target == null ? "none" : target == hidden ? "hidden_marked" : target == unmarked ? "unmarked"
                : target == seen ? "seen_marked" : target.getType().toShortString();
    }

    @SafeVarargs
    private static List<String> concat(List<String>... lists) {
        List<String> all = new ArrayList<>();
        for (List<String> list : lists) {
            all.addAll(list);
        }
        return all;
    }

    private static String describe(@Nullable DamageSource source) {
        return source == null ? "none" : source.getMsgId() + "/" + source.getEntity();
    }

    private record PlayedSound(ResourceLocation id, Vec3 pos, SoundSource source, float volume, float pitch) {}

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final ServerPlayer user;
        private final List<Entity> spawned = new ArrayList<>();
        private final Map<BlockPos, BlockState> replaced = new LinkedHashMap<>();
        private final List<PlayedSound> sounds = new ArrayList<>();
        private final List<Entity> joined = new ArrayList<>();
        @Nullable private Consumer<PlayLevelSoundEvent.AtPosition> soundListener;
        @Nullable private Consumer<EntityJoinLevelEvent> joinListener;
        @Nullable private Boolean mobGriefingBefore;
        @Nullable private Long dayTimeBefore;
        private StandPower power;
        private StandInstance instance;
        private TicketToRideStandEntity stand;
        private LivingComponentAction standAction;
        private EntityActionAbility explod;
        private boolean closed;

        private Fixture(GameTestHelper helper, String name, boolean online) {
            this.helper = helper;
            level = helper.getLevel();
            // GameTest keeps only the template's own chunk loaded for sure, so everything stays inside it.
            BlockPos template = helper.absolutePos(new BlockPos(0, 2, 0));
            ChunkPos chunk = new ChunkPos(template);
            Vec3 feet = new Vec3(chunk.getMinBlockX() + 2.5D, template.getY(), chunk.getMinBlockZ() + 8.5D);
            user = online
                    ? new OnlinePlayer(level, name)
                    : FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));
            try {
                if (user instanceof OnlinePlayer onlinePlayer) {
                    onlinePlayer.login();
                    helper.assertTrue(level.getEntity(user.getUUID()) == user, "Native PlayerList login did not register the user");
                }
                // Looking east, level.
                user.moveTo(feet.x, feet.y, feet.z, -90.0F, 0.0F);
                user.setYHeadRot(-90.0F);
                if (!online) {
                    helper.assertTrue(level.addFreshEntity(user), "Could not add the Ticket to Ride test player");
                }
                user.setGameMode(GameType.SURVIVAL);
                helper.assertTrue(!user.getAbilities().instabuild, "The test player is not in survival");

                power = PowerClass.STAND.attachGet(user);
                StandPowerTransitions.Result inserted = StandPowerTransitions.insert(
                        power, new StandInstance(TicketToRideAddon.TICKET_TO_RIDE.get()));
                helper.assertTrue(inserted.status() == StandPowerTransitions.Status.APPLIED,
                        "Could not grant Ticket to Ride: " + inserted.status());
                // explod unlocks at Resolve level 2
                power.skipProgression();
                instance = power.getStandInstance().orElse(null);
                helper.assertTrue(instance != null, "The granted Ticket to Ride has no Stand instance");
                helper.assertTrue(TicketToRideAddon.TICKET_TO_RIDE.get().summon(user, power)
                                && power.getSummonedStandEntity() instanceof TicketToRideStandEntity,
                        "Production Ticket to Ride summon failed");
                stand = (TicketToRideStandEntity) power.getSummonedStandEntity();
                standAction = LivingComponentAction.getComponent(stand);
                explod = ability(TicketToRideAddon.EXPLOD);
                helper.assertTrue(explod.isAbilityAvailable(power), "Explod is not unlocked for the test player");
                helper.assertTrue(power.getMaxStamina() >= STAMINA_COST && !power.isStaminaInfinite(),
                        "The test player cannot pay stamina: max=" + power.getMaxStamina());
                reset();
            }
            catch (RuntimeException | Error exception) {
                close();
                throw exception;
            }
        }

        private EntityActionAbility ability(String name) {
            Ability found = power.getAbility(name);
            helper.assertTrue(found instanceof EntityActionAbility, "Missing Ticket to Ride ability " + name);
            return (EntityActionAbility) found;
        }

        /** No action, no held key, no cooldown, full stamina and no summon lock (the Stand's own tick is not run). */
        private void reset() {
            user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
            standAction.setAction(null, SyncType.NO_SYNC);
            stand.summonLockTicks = 0;
            power.setAbilityCooldown(TicketToRideAddon.EXPLOD, 0);
            power.setStamina(power.getMaxStamina());
        }

        private int cooldown() {
            return power.getAbilityCooldown(TicketToRideAddon.EXPLOD);
        }

        /**
         * Uses randommob by day or night with the level's random seeded on every tick, and checks what joined the
         * level and the sounds against 1.16 for one marked entity at center. The type and the first mob's x and z
         * are the first draws; the mobs' own construction takes from the same random after that (a bee's goals do).
         * @return "ok ..." or what differs
         */
        private String randomMobs(long seed, boolean night, Vec3 center) {
            if (joinListener == null) {
                listenToJoins();
            }
            if (soundListener == null) {
                listenToSounds();
            }
            if (dayTimeBefore == null) {
                dayTimeBefore = level.getDayTime();
            }
            level.setDayTime(night ? 18000L : 6000L);
            level.updateSkyBrightness();
            helper.assertTrue(level.isNight() == night, "Setup: could not make it " + (night ? "night" : "day"));
            reset();
            joined.clear();
            sounds.clear();
            helper.assertTrue(press(ability(TicketToRideAddon.RANDOM_MOB)), "randommob did not start");
            for (int tick = 0; tick < 22; tick++) {
                level.getRandom().setSeed(seed);
                standAction.tick();
            }
            RandomSource draws = RandomSource.create(seed);
            EntityType<?> type = MOB_TYPES_116.get(draws.nextInt(MOB_TYPES_116.size()));
            double firstX = center.x + (draws.nextDouble() - 0.5D) * 20.0D;
            double firstZ = center.z + (draws.nextDouble() - 0.5D) * 20.0D;
            double height = type == EntityType.PHANTOM ? 10.0D
                    : type == EntityType.SILVERFISH || type == EntityType.CAVE_SPIDER || type == EntityType.WOLF
                            ? 0.1D
                            : 5.0D;
            int count = type == EntityType.PHANTOM && !night ? 0 : 3;

            List<Mob> mobs = joined.stream().filter(Mob.class::isInstance).map(Mob.class::cast).toList();
            ResourceLocation spell = SoundEvents.EVOKER_CAST_SPELL.getLocation();
            ResourceLocation unsummon = ModSoundEvents.STAND_UNSUMMON.get().getLocation();
            List<PlayedSound> spells = sounds.stream().filter(sound -> sound.id().equals(spell)).toList();
            List<PlayedSound> standSounds = sounds.stream().filter(sound -> sound.id().equals(unsummon)).toList();
            String seen = "type=" + type.toShortString() + " first=(" + firstX + ", " + firstZ + ") count=" + count
                    + " mobs=" + mobs.stream().map(Fixture::describeMob).toList() + " spells=" + spells
                    + " stand=" + standSounds;
            if (mobs.size() != count || spells.size() != count) {
                return "not the 1.16 number of mobs or cast-spell sounds: " + seen;
            }
            for (int i = 0; i < count; i++) {
                Mob mob = mobs.get(i);
                MobEffectInstance tears = mob.getEffect(TicketToRideAddon.TEARS);
                PlayedSound sound = spells.get(i);
                if (mob.getType() != type || Math.abs(mob.getX() - center.x) > 10.0D
                        || Math.abs(mob.getZ() - center.z) > 10.0D || Math.abs(mob.getY() - center.y - height) > 1.0E-9D
                        || i == 0 && (mob.getX() != firstX || mob.getZ() != firstZ)
                        || !(mob.getYRot() >= 0.0F && mob.getYRot() < 360.0F)) {
                    return "mob " + i + " is not the 1.16 type, place or facing: " + seen;
                }
                if (tears == null || !tears.isInfiniteDuration() || mob.getTarget() != null) {
                    return "mob " + i + " has no endless TEARS or a target of its own: " + seen;
                }
                if (sound.pos().distanceTo(mob.position()) > 1.0E-9D || sound.volume() != 1.0F
                        || !(sound.pitch() >= 0.8F && sound.pitch() <= 1.2F) || sound.source() != mob.getSoundSource()) {
                    return "mob " + i + " did not get the 1.16 cast-spell sound: " + seen;
                }
            }
            if (standSounds.size() != 1 || standSounds.get(0).pos().distanceTo(stand.position()) > 1.0E-6D) {
                return "the Stand did not play its unsummon sound once: " + seen;
            }
            return "ok " + type.toShortString() + "x" + count;
        }

        private static String describeMob(Mob mob) {
            MobEffectInstance tears = mob.getEffect(TicketToRideAddon.TEARS);
            return mob.getType().toShortString() + "@" + mob.position() + "/" + mob.getYRot() + (mob.isBaby() ? "/baby" : "")
                    + " tears=" + (tears == null ? "none" : tears.getDuration()) + " target=" + mob.getTarget();
        }

        private void discardJoined() {
            joined.forEach(Entity::discard);
            joined.clear();
        }

        /** Presses the move, holds it for its charge and, if it fires on the release, lets go. */
        private void use(String name, int holdTicks, boolean firesOnRelease) {
            EntityActionAbility ability = ability(name);
            reset();
            power.setAbilityCooldown(name, 0);
            joined.clear();
            helper.assertTrue(press(ability), name + " did not start");
            for (int tick = 0; tick < holdTicks + 2; tick++) {
                standAction.tick();
            }
            if (firesOnRelease) {
                release(BRANCH_TNT);
            }
        }

        /** Holds lighting_single, then releases it with the level's random seeded; returns the bolts that joined. */
        private List<LightningBolt> lightingSingle(long seed) {
            return lighting(TicketToRideAddon.LIGHTNING_SINGLE, 12, seed);
        }

        /** Holds a lightning move past its charge, then releases it with the level's random seeded. */
        private List<LightningBolt> lighting(String name, int holdTicks, long seed) {
            EntityActionAbility ability = ability(name);
            reset();
            power.setAbilityCooldown(name, 0);
            joined.clear();
            sounds.clear();
            helper.assertTrue(press(ability), name + " did not start");
            for (int tick = 0; tick < holdTicks + 2; tick++) {
                standAction.tick();
            }
            level.getRandom().setSeed(seed);
            release(BRANCH_TNT);
            return joined.stream().filter(LightningBolt.class::isInstance).map(LightningBolt.class::cast).toList();
        }

        private List<PlayedSound> summonSounds() {
            ResourceLocation summon = ModSoundEvents.STAND_SUMMON.get().getLocation();
            return sounds.stream().filter(sound -> sound.id().equals(summon)).toList();
        }

        /** Uses meteor with the Stand held at standAt; returns how many Dead Photo Mementos it summoned. */
        private long meteorWithStandAt(Vec3 standAt) {
            EntityActionAbility meteor = ability(TicketToRideAddon.METEOR);
            reset();
            joined.clear();
            stand.moveTo(standAt.x, standAt.y, standAt.z, -90.0F, 0.0F);
            helper.assertTrue(press(meteor), "meteor did not start");
            for (int tick = 0; tick < 22; tick++) {
                stand.moveTo(standAt.x, standAt.y, standAt.z, -90.0F, 0.0F);
                standAction.tick();
            }
            long summoned = joinedOf(DeadPhotoMementoEntity.class);
            joined.forEach(Entity::discard);
            joined.clear();
            return summoned;
        }

        /** A Dead Photo Memento of this user's Stand, added to the level at this point. */
        private DeadPhotoMementoEntity memento(Vec3 at) {
            DeadPhotoMementoEntity memento = TicketToRideAddon.DEAD_PHOTO_MEMENTO.get().create(level);
            helper.assertTrue(memento != null, "Could not create a Dead Photo Memento");
            memento.initialize(user, stand);
            memento.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(memento), "Could not add a Dead Photo Memento");
            spawned.add(memento);
            return memento;
        }

        private long joinedOf(Class<? extends Entity> type) {
            return joined.stream().filter(type::isInstance).count();
        }

        /** Every entity that joins the level from now on, wherever it lands; they are discarded on close. */
        private void listenToJoins() {
            joinListener = event -> {
                if (event.getLevel() == level && !(event.getEntity() instanceof Player)) {
                    joined.add(event.getEntity());
                    spawned.add(event.getEntity());
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, EntityJoinLevelEvent.class, joinListener);
        }

        /** A mob that stays where it is put, with endless TEARS if tears; not in the level yet. */
        private <T extends Mob> T created(EntityType<T> type, Vec3 feet, boolean tears) {
            T mob = type.create(level);
            helper.assertTrue(mob != null, "Could not create a " + type.toShortString());
            mob.setNoAi(true);
            mob.setNoGravity(true);
            mob.setSilent(true);
            mob.setPersistenceRequired();
            mob.moveTo(feet.x, feet.y, feet.z, 0.0F, 0.0F);
            if (tears) {
                mob.addEffect(new MobEffectInstance(TicketToRideAddon.TEARS, MobEffectInstance.INFINITE_DURATION, 0,
                        false, false, true));
            }
            return mob;
        }

        /** Runs a step of a test that spans ticks; a failure closes the fixture first. */
        private void guard(Runnable step) {
            try {
                step.run();
            }
            catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        /** Saves the mob, removes it and adds what loads from the save, as a chunk reload does. */
        @SuppressWarnings("unchecked")
        private <T extends Mob> T reload(T mob) {
            CompoundTag saved = mob.saveWithoutId(new CompoundTag());
            saved.putString("id", EntityType.getKey(mob.getType()).toString());
            saved.remove("UUID");
            mob.discard();
            Entity loaded = EntityType.create(saved, level).orElse(null);
            helper.assertTrue(loaded != null && loaded.getType() == mob.getType(),
                    "Could not load the saved " + mob.getType().toShortString());
            return join((T) loaded);
        }

        private <T extends Mob> T join(T mob) {
            helper.assertTrue(level.addFreshEntity(mob), "Could not add a " + mob.getType().toShortString());
            spawned.add(mob);
            return mob;
        }

        private Zombie unmarkedZombie(Vec3 feet) {
            Zombie zombie = EntityType.ZOMBIE.create(level);
            helper.assertTrue(zombie != null, "Could not create a zombie");
            zombie.setNoAi(true);
            zombie.setNoGravity(true);
            zombie.setSilent(true);
            zombie.moveTo(feet.x, feet.y, feet.z, 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(zombie), "Could not add a zombie");
            spawned.add(zombie);
            return zombie;
        }

        /** The server side of a held key press: the fresh condition check, then the key press itself. */
        private boolean press(EntityActionAbility ability) {
            AbilityConditionCheck check = power.updateAvailableMoves().getCheckedContainerFor(power, ability);
            if (!AbilityInput.withConditionCheck(check, user, InputMethod.HOLD)) {
                return false;
            }
            AbilityInput.keyPress(KEY, check.ability, user, null, InputMethod.HOLD, 0.0F,
                    BufferingState.clickCanBuffer(), ability.abilityId);
            return standAction.getAction() != null;
        }

        /** What the slot resolves to now on the server, as "ability/ok" or "ability/refused" ("none" if empty). */
        private String slot(String slotName) {
            AbilityConditionCheck resolved = freshMoves().getContextVariationContainer(slotName);
            return resolved == null ? "none"
                    : resolved.ability.name() + "/" + (resolved.conditionCheck.isPositive() ? "ok" : "refused");
        }

        /** The server side of a held press of a slot (ClAbilityInputPacket): what it resolves to, its check, the press. */
        private BufferingState pressSlot(String slotName) {
            AbilityConditionCheck resolved = freshMoves().getContextVariationContainer(slotName);
            BufferingState state = BufferingState.clickCanBuffer();
            if (resolved != null && AbilityInput.withConditionCheck(resolved, user, InputMethod.HOLD)) {
                AbilityInput.keyPress(KEY, resolved.ability, user, null, InputMethod.HOLD, 0.0F, state,
                        ability(slotName).abilityId);
            }
            return state;
        }

        /**
         * The server side of a click on the share / remove slot (ClAbilityInputPacket: the check, then the press), then
         * the user's Stand-power slot, where the action runs, ticked to its end (a Stand a press summons keeps its
         * summon lock); {@code beforeTheStandActs} runs between the two.
         * @return "passed=... started=..." and whether the action is over
         */
        private String click(String name, Runnable beforeTheStandActs) {
            EntityActionAbility ability = ability(name);
            AbilityConditionCheck check = freshMoves().getCheckedContainerFor(power, ability);
            boolean passed = AbilityInput.withConditionCheck(check, user, InputMethod.CLICK);
            BufferingState state = BufferingState.clickCanBuffer();
            if (passed) {
                AbilityInput.keyPress(KEY, check.ability, user, null, InputMethod.CLICK, 0.0F, state,
                        ability.abilityId);
            }
            beforeTheStandActs.run();
            LivingComponentAction userSlot = LivingComponentAction.getComponent(user, PowerClass.STAND);
            for (int tick = 0; tick < 5 && userSlot.getAction() != null; tick++) {
                userSlot.tick();
            }
            StandEntity acting = power.getSummonedStandEntity();
            EntityActionInstance standTask = acting != null ? LivingComponentAction.getCurEntityAction(acting) : null;
            return "passed=" + passed + " started=" + state.isActionSuccess + " over="
                    + (userSlot.getAction() == null && standTask == null);
        }

        /**
         * Puts the Stand away, aims the user (its vanilla pick) at the target and clicks the slot; before the summoned
         * Stand acts, the user looks straight up at nothing, as its next aim packet would say.
         */
        private String pressWithTheStandAway(String name, LivingEntity target) {
            if (power.isSummoned()) {
                TicketToRideAddon.TICKET_TO_RIDE.get().forceUnsummon(user, power);
            }
            StandEntity previous = power.getSummonedStandEntity();
            helper.assertTrue(previous == null, "Setup: the Stand is still out before " + name);
            user.setYRot(-90.0F);
            user.setYHeadRot(-90.0F);
            user.setXRot(0.0F);
            LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(target));
            power.setAbilityCooldown(name, 0);
            power.setStamina(power.getMaxStamina());
            float before = power.getStamina();
            String[] summoned = {"none"};
            String clicked = click(name, () -> {
                StandEntity out = power.getSummonedStandEntity();
                ActionTarget standAim = out != null ? LivingComponentAction.getComponent(out).entityAim.getTarget() : null;
                summoned[0] = "summoned=" + (out instanceof TicketToRideStandEntity && level.getEntity(out.getId()) == out)
                        + " standAim=" + (standAim == null ? "none"
                                : standAim.sameTarget(new ActionTarget(target)) ? "pressed" : standAim.getType());
                user.setXRot(-90.0F);
                LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
            });
            String paid = String.valueOf(before - power.getStamina());
            user.setXRot(0.0F);
            int over = clicked.indexOf(" over=");
            return clicked.substring(0, over) + " " + summoned[0] + " effect="
                    + target.hasEffect(TicketToRideAddon.SHARED_LOVE_TRAIN) + " paid=" + paid + clicked.substring(over);
        }

        /**
         * With the Stand out: the user's vanilla pick is the target if {@code picked}, else nothing; the Stand's aim is
         * what the user's client computes and sends (StandEntityPunchAbility.userStandAim with the default aiming
         * filter, ClAbilityInputPacket filling an empty one with the pick). Then share and remove are clicked.
         * @return whether the Stand's aim held the target, and who got and lost the passenger effect
         */
        private String shareAndRemove(LivingEntity target, boolean picked, @Nullable LivingEntity other) {
            reset();
            ActionTarget pick = picked ? new ActionTarget(target) : ActionTarget.EMPTY;
            LivingComponentAction.getComponent(user).entityAim.setTarget(pick);
            ActionTarget clientStandAim = StandEntityPunchAbility.userStandAim(stand, user, pick,
                    entity -> StandEntityAbility.canDefaultTargetEntityForAiming(stand, entity));
            standAction.entityAim.setTarget(clientStandAim.getType() == ActionTarget.TargetType.EMPTY ? pick
                    : clientStandAim);
            String aimed = standAction.entityAim.getTarget().getType() == ActionTarget.TargetType.ENTITY
                    && standAction.entityAim.getTarget().getEntity() == target ? "target" : "other";
            String shareClick = click(TicketToRideAddon.SHARE_LOVE_TRAIN, () -> {});
            String shared = target.hasEffect(TicketToRideAddon.SHARED_LOVE_TRAIN) + "/"
                    + (other != null && other.hasEffect(TicketToRideAddon.SHARED_LOVE_TRAIN));
            if (other != null) {
                other.removeEffect(TicketToRideAddon.SHARED_LOVE_TRAIN);
            }
            reset();
            String removeClick = click(TicketToRideAddon.REMOVE_SHARED, () -> {});
            String removed = String.valueOf(!target.hasEffect(TicketToRideAddon.SHARED_LOVE_TRAIN));
            target.removeEffect(TicketToRideAddon.SHARED_LOVE_TRAIN);
            String clicks = "passed=true started=true over=true";
            return "standAim=" + aimed + " shared=" + shared + " removed=" + removed
                    + (shareClick.equals(clicks) && removeClick.equals(clicks) ? ""
                            : " share=(" + shareClick + ") remove=(" + removeClick + ")");
        }

        // the power resolves its slots once a tick (Power.updateAvailableMoves), and the fixture does not tick it
        private AvailableAbilities freshMoves() {
            try {
                Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
                cached.setAccessible(true);
                cached.setBoolean(power, false);
            }
            catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
            }
            return power.updateAvailableMoves();
        }

        /** Ticks the running meteor until its recovery has started; returns it. */
        private EntityActionInstance toRecovery() {
            EntityActionInstance action = standAction.getAction();
            for (int tick = 0; tick < 30 && action != null && action.getPhase() != ActionPhase.RECOVERY; tick++) {
                standAction.tick();
            }
            helper.assertTrue(action != null && !action.isOver() && action == standAction.getAction()
                            && action.getPhase() == ActionPhase.RECOVERY
                            && action.ability == ability(TicketToRideAddon.METEOR),
                    "Setup: meteor did not reach its recovery: " + describeAction(standAction.getAction()));
            return action;
        }

        /** @return whether the charge is full */
        private boolean charge(int ticks) {
            for (int tick = 0; tick < ticks; tick++) {
                standAction.tick();
            }
            EntityActionInstance action = standAction.getAction();
            return action != null && action.getPhase() == ActionPhase.WINDUP
                    && action.getPhaseTick() >= action.getCurPhaseLength();
        }

        /** Lets go of the key and runs the tick in which a released full charge performs. */
        private void release(int branch) {
            AbilityInput.keyRelease(KEY, user);
            // explod picks its misfortune with user.getRandom().nextInt(3)
            long seed = 0L;
            while (RandomSource.create(seed).nextInt(3) != branch) {
                seed++;
            }
            user.getRandom().setSeed(seed);
            standAction.tick();
        }

        /** Marks the target, then presses, fully charges and releases explod. */
        private boolean fire(LivingEntity target, int branch) {
            mark(target);
            if (!press(explod)) {
                return false;
            }
            helper.assertTrue(charge(CHARGE_TICKS), "Explod was not fully charged after " + CHARGE_TICKS + " ticks");
            release(branch);
            return true;
        }

        /** @return whether a full explod use put its TNT on this target */
        private boolean fireHits(LivingEntity target) {
            reset();
            fire(target, BRANCH_TNT);
            return effectsAt(target.position()) > 0;
        }

        private int effectsAt(Vec3 pos) {
            AABB near = new AABB(pos, pos).inflate(0.5D);
            return level.getEntitiesOfClass(PrimedTnt.class, near, Entity::isAlive).size()
                    + level.getEntitiesOfClass(Creeper.class, near, Entity::isAlive).size();
        }

        /**
         * Five blocks from the user's eyes, 10 degrees off the look vector. Not on the vector itself: there the
         * 1.16 angle, acos of a dot product that rounding can push past 1, is not a number.
         */
        private Vec3 ahead() {
            Vec3 look = user.getLookAngle();
            return offset(look, new Vec3(-look.z, 0.0D, look.x).normalize(), 5.0D, 10.0D);
        }

        private Vec3 behind() {
            Vec3 look = user.getLookAngle();
            return offset(look, new Vec3(-look.z, 0.0D, look.x).normalize(), 5.0D, 170.0D);
        }

        /** The Stand part whose loss blocks this ability now ("no_stand_part.arms"), or null if none does. */
        @Nullable
        private String missingPart(String abilityName) {
            Ability found = power.getAbility(abilityName);
            helper.assertTrue(found != null, "Missing Ticket to Ride ability " + abilityName);
            ConditionCheck check = found.checkConditions(power);
            Component warning = check.getWarning();
            if (check.isPositive() || warning == null
                    || !(warning.getContents() instanceof TranslatableContents contents)) {
                return null;
            }
            int at = contents.getKey().indexOf("no_stand_part.");
            return at >= 0 ? contents.getKey().substring(at) : null;
        }

        private void mark(LivingEntity target) {
            helper.assertTrue(TearsWoundService.mark(target, user, 6000) && TearsWoundService.isMarked(target),
                    "Could not mark the target with tears");
        }

        private Zombie target(Vec3 feet) {
            Zombie zombie = EntityType.ZOMBIE.create(level);
            helper.assertTrue(zombie != null, "Could not create the target");
            zombie.setNoAi(true);
            zombie.setNoGravity(true);
            zombie.setSilent(true);
            zombie.setPersistenceRequired();
            zombie.moveTo(feet.x, feet.y, feet.z, 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(zombie), "Could not add the target");
            spawned.add(zombie);
            mark(zombie);
            return zombie;
        }

        /** Discards the target and whatever explod put on it. */
        private void remove(LivingEntity target) {
            AABB near = new AABB(target.position(), target.position()).inflate(0.5D);
            level.getEntitiesOfClass(PrimedTnt.class, near).forEach(Entity::discard);
            level.getEntitiesOfClass(Creeper.class, near).forEach(Entity::discard);
            target.discard();
        }

        /** A point at the given distance from the user's eyes, the given angle off the look vector towards side. */
        private Vec3 offset(Vec3 look, Vec3 side, double distance, double degrees) {
            double radians = Math.toRadians(degrees);
            return user.getEyePosition()
                    .add(look.scale(distance * Math.cos(radians)))
                    .add(side.scale(distance * Math.sin(radians)));
        }

        private double angleTo(Vec3 point) {
            return Math.toDegrees(Math.acos(user.getLookAngle().dot(point.subtract(user.getEyePosition()).normalize())));
        }

        private void listenToSounds() {
            soundListener = event -> {
                if (event.getLevel() == level && event.getSound() != null) {
                    sounds.add(new PlayedSound(event.getSound().value().getLocation(), event.getPosition(),
                            event.getSource(), event.getOriginalVolume(), event.getOriginalPitch()));
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, PlayLevelSoundEvent.AtPosition.class, soundListener);
        }

        private void setBlock(BlockPos pos, BlockState state) {
            replaced.putIfAbsent(pos.immutable(), level.getBlockState(pos));
            level.setBlockAndUpdate(pos, state);
        }

        /** A closed obsidian shell, so that the blast reaches nothing outside this test. */
        private void buildBlastBox(BlockPos min, BlockPos max) {
            helper.assertTrue(new ChunkPos(min).equals(new ChunkPos(max)), "The blast box leaves the test chunk");
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                boolean shell = pos.getX() == min.getX() || pos.getX() == max.getX()
                        || pos.getY() == min.getY() || pos.getY() == max.getY()
                        || pos.getZ() == min.getZ() || pos.getZ() == max.getZ();
                setBlock(pos, shell ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
        }

        // without neighbour updates, so a wall torch with nothing to hang on stays
        private void setBlockQuietly(BlockPos pos, BlockState state) {
            replaced.putIfAbsent(pos.immutable(), level.getBlockState(pos));
            level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }

        // the Stand's eyes level with its user's, 0.35 along the user's look, looking the same way
        private void aimStandAlongTheUsersEyeLine() {
            Vec3 eyes = user.getEyePosition().add(user.getLookAngle().scale(0.35D));
            stand.moveTo(eyes.x, eyes.y - stand.getEyeHeight(), eyes.z, user.getYRot(), user.getXRot());
            stand.setYHeadRot(user.getYHeadRot());
        }

        private void clearArea(BlockPos min, BlockPos max) {
            helper.assertTrue(new ChunkPos(min).equals(new ChunkPos(max)), "The cleared area leaves the test chunk");
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                if (!level.getBlockState(pos).isAir()) {
                    setBlock(pos, Blocks.AIR.defaultBlockState());
                }
            }
        }

        private void restoreBlocks() {
            List<Map.Entry<BlockPos, BlockState>> restore = new ArrayList<>(replaced.entrySet());
            for (int i = restore.size() - 1; i >= 0; i--) {
                level.setBlockAndUpdate(restore.get(i).getKey(), restore.get(i).getValue());
            }
            replaced.clear();
        }

        private void setMobGriefing(boolean value) {
            GameRules.BooleanValue rule = level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
            if (mobGriefingBefore == null) {
                mobGriefingBefore = rule.get();
            }
            rule.set(value, level.getServer());
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                if (soundListener != null) {
                    NeoForge.EVENT_BUS.unregister(soundListener);
                }
                if (joinListener != null) {
                    NeoForge.EVENT_BUS.unregister(joinListener);
                }
                user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
                if (standAction != null) {
                    standAction.setAction(null, SyncType.NO_SYNC);
                }
                if (instance != null) {
                    for (StandPart part : StandPart.values()) {
                        instance.addPart(part);
                    }
                }
                if (power != null && power.isSummoned()) {
                    TicketToRideAddon.TICKET_TO_RIDE.get().forceUnsummon(user, power);
                }
                for (Entity entity : spawned) {
                    AABB near = entity.getBoundingBox().inflate(12.0D);
                    level.getEntitiesOfClass(PrimedTnt.class, near).forEach(Entity::discard);
                    level.getEntitiesOfClass(Creeper.class, near).forEach(Entity::discard);
                    level.getEntitiesOfClass(ItemEntity.class, near).forEach(Entity::discard);
                    entity.discard();
                }
                if (user instanceof OnlinePlayer onlinePlayer) {
                    onlinePlayer.logout();
                }
                else {
                    user.discard();
                }
            }
            finally {
                if (mobGriefingBefore != null) {
                    level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(mobGriefingBefore, level.getServer());
                }
                if (dayTimeBefore != null) {
                    level.setDayTime(dayTimeBefore);
                    level.updateSkyBrightness();
                }
                restoreBlocks();
            }
        }
    }

    /** A real logged-in player: a FakePlayer cannot be hurt. */
    private static final class OnlinePlayer extends ServerPlayer {
        private final Connection network;
        private final EmbeddedChannel channel;
        private boolean loggedOut;

        private OnlinePlayer(ServerLevel level, String name) {
            super(level.getServer(), level, new GameProfile(UUID.randomUUID(), name),
                    ClientInformation.createDefault());
            network = new Connection(PacketFlow.SERVERBOUND) {
                @Override
                public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocol, T listener) {
                    super.setupInboundProtocol(protocol, listener);
                    if (listener instanceof ServerGamePacketListenerImpl) {
                        // Native login syncs datapacks before returning; this server-only fixture has no client.
                        OnlinePlayer.this.connection = new SilentConnection(
                                serverLevel(), OnlinePlayer.this, this);
                    }
                }
            };
            channel = new EmbeddedChannel(network);
        }

        private void login() {
            server.getPlayerList().placeNewPlayer(network, this,
                    CommonListenerCookie.createInitial(getGameProfile(), false));
        }

        private void logout() {
            if (loggedOut) {
                return;
            }
            loggedOut = true;
            if (server.getPlayerList().getPlayer(getUUID()) == this) server.getPlayerList().remove(this);
            discard();
            channel.finishAndReleaseAll();
        }
    }

    private static final class SilentConnection extends ServerGamePacketListenerImpl {
        private SilentConnection(ServerLevel level, ServerPlayer player, Connection network) {
            super(level.getServer(), network, player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener listener) {}
    }
}
