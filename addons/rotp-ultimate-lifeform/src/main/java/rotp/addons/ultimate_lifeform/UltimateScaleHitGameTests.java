package rotp.addons.ultimate_lifeform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ultimate_lifeform.entity.UltimateScaleProjectileEntity;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.entity.HamonProjectileShieldEntity;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

/**
 * Donor hit rules of the scale (1.16 UltimateLifeformScaleProjectileEntity): every scale is ticked by the level
 * and meets its target, block or owner through its own sweep.
 */
@GameTestHolder(UltimateLifeformAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UltimateScaleHitGameTests {
    private static final float DONOR_DAMAGE = 5.0F;
    private static final double OWNER_Y = 2.2D;
    // Every fixture scale rolls below the poison threshold unless a test reseeds it.
    private static final long NO_POISON_SEED = seedWithRoll(0.62D, 0.66D);
    // The shield scans 24 blocks for projectiles, so these tests run apart from the other scale tests.
    private static final String REFLECTION_BATCH = "ulf_scale_reflection";
    private static final short SHIELD_KEY = 37;
    // A scene spans 8 blocks and the grid puts tests 6 and 7 apart; other tests' mobs wander and their projectiles fly
    // on, and a scale stops at the first living entity in its way. The grid grows east and south from its first corner,
    // at most 8 plots of 13 blocks a row, so a scene this far west of its template stands west of every plot, beside
    // only scenes of this class, and keeps its template's place in a chunk.
    private static final BlockPos AWAY = new BlockPos(-256, 0, 0);

    private UltimateScaleHitGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void scalesOfOneVolleyAllDamageThroughInvulnerabilityTicks(GameTestHelper helper) {
        Scene scene = new Scene(helper);
        Cow owner = scene.cow(1.5D, 1.5D);
        Cow target = scene.cow(1.5D, 4.5D);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0D);
        target.setHealth(40.0F);
        UltimateScaleProjectileEntity first = scene.launch(owner, 1.0F);
        UltimateScaleProjectileEntity second = scene.launch(owner, 1.0F);
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(first.isRemoved() && second.isRemoved(), "Both scales must have reached the target.");
            helper.assertTrue(target.getHealth() == 40.0F - 2.0F * DONOR_DAMAGE,
                    "Two scales landing in the same tick deal 5 each through invulnerability ticks, but health went 40.0 -> "
                            + target.getHealth());
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void scaleHitPoisonsOnlyAboveTheDonorRoll(GameTestHelper helper) {
        Scene scene = new Scene(helper);
        long poisonSeed = seedWithRoll(0.66D, 0.70D);
        Cow poisonedTarget = scene.cow(1.5D, 4.5D);
        Cow cleanTarget = scene.cow(5.5D, 4.5D);
        Map<LivingEntity, List<MobEffectInstance>> added = new LinkedHashMap<>();
        scene.listen(MobEffectEvent.Added.class, event -> {
            if (event.getEntity() == poisonedTarget || event.getEntity() == cleanTarget) {
                added.computeIfAbsent(event.getEntity(), key -> new ArrayList<>())
                        .add(new MobEffectInstance(event.getEffectInstance()));
            }
        });
        UltimateScaleProjectileEntity poisoning = scene.prepare(scene.cow(1.5D, 1.5D), 1.0F);
        poisoning.getRandom().setSeed(poisonSeed);
        scene.add(poisoning);
        UltimateScaleProjectileEntity clean = scene.prepare(scene.cow(5.5D, 1.5D), 1.0F);
        scene.add(clean);
        helper.runAtTickTime(8, () -> {
            helper.assertTrue(poisoning.isRemoved() && clean.isRemoved()
                            && poisonedTarget.getHealth() < 10.0F && cleanTarget.getHealth() == 10.0F - DONOR_DAMAGE,
                    "Both scales must have hit their targets: poisoned=" + poisonedTarget.getHealth()
                            + " clean=" + cleanTarget.getHealth());
            List<MobEffectInstance> poison = added.getOrDefault(poisonedTarget, List.of());
            helper.assertTrue(poison.size() == 1 && poison.get(0).is(MobEffects.POISON)
                            && poison.get(0).getDuration() == 60 && poison.get(0).getAmplifier() == 1,
                    "A roll above 0.66 gives the living target Poison II for 60 ticks, but the target received " + poison);
            helper.assertTrue(!added.containsKey(cleanTarget) && !cleanTarget.hasEffect(MobEffects.POISON),
                    "A roll of at most 0.66 gives no poison, but the target received " + added.get(cleanTarget));
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void scaleBreaksSoftBlockThenRechecksAndStopsAtHardOrProtectedBlock(GameTestHelper helper) {
        Scene scene = new Scene(helper);
        // hardness: melon 1.0 (the donor limit, inclusive), terracotta 1.25
        BlockPos soft = scene.block(1, Blocks.MELON);
        BlockPos hard = scene.block(3, Blocks.TERRACOTTA);
        BlockPos guarded = scene.block(5, Blocks.MELON);
        Cow softOwner = scene.cow(1.5D, 1.5D);
        Cow hardOwner = scene.cow(3.5D, 1.5D);
        Cow guardedOwner = scene.cow(5.5D, 1.5D);
        Cow softTarget = scene.cow(1.5D, 3.6D);
        Cow hardTarget = scene.cow(3.5D, 3.6D);
        Cow guardedTarget = scene.cow(5.5D, 3.6D);
        scene.listen(LivingDestroyBlockEvent.class, event -> {
            if (event.getEntity() == guardedOwner) {
                event.setCanceled(true);
            }
        });
        List<PlayLevelSoundEvent.AtPosition> sounds = new ArrayList<>();
        scene.listen(PlayLevelSoundEvent.AtPosition.class, event -> {
            if (event.getLevel() == scene.level && event.getSource() == SoundSource.BLOCKS
                    && scene.bounds.contains(event.getPosition())) {
                sounds.add(event);
            }
        });
        UltimateScaleProjectileEntity softScale = scene.launch(softOwner, 2.0F);
        UltimateScaleProjectileEntity hardScale = scene.launch(hardOwner, 2.0F);
        UltimateScaleProjectileEntity guardedScale = scene.launch(guardedOwner, 2.0F);
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(softScale.isRemoved() && hardScale.isRemoved() && guardedScale.isRemoved(),
                    "A scale is removed in the tick of its block hit.");
            helper.assertTrue(scene.level.getBlockState(soft).isAir(),
                    "A block of hardness 1.0 is broken by the scale, but it is still " + scene.level.getBlockState(soft));
            helper.assertTrue(Math.abs(softScale.getDeltaMovement().length() - 1.8D) < 1.0E-6D,
                    "Breaking a block slows the scale to 0.9 speed, but 2.0 became " + softScale.getDeltaMovement().length());
            helper.assertTrue(softTarget.getHealth() == 10.0F - DONOR_DAMAGE,
                    "The re-check after the break hits the target behind the block, but its health is "
                            + softTarget.getHealth());
            helper.assertTrue(scene.level.getBlockState(hard).is(Blocks.TERRACOTTA) && hardTarget.getHealth() == 10.0F,
                    "A block harder than 1.0 stops the scale: block=" + scene.level.getBlockState(hard)
                            + " targetHealth=" + hardTarget.getHealth());
            helper.assertTrue(scene.level.getBlockState(guarded).is(Blocks.MELON) && guardedTarget.getHealth() == 10.0F,
                    "A block its owner may not destroy stops the scale: block=" + scene.level.getBlockState(guarded)
                            + " targetHealth=" + guardedTarget.getHealth());
            helper.assertTrue(hitSounds(sounds, hard, SoundType.STONE) == 1 && hitSounds(sounds, guarded, SoundType.WOOD) == 1
                            && sounds.size() == 2,
                    "Each unbroken block plays its hit sound once at volume 1.0 and half pitch, but the block sounds were "
                            + describe(sounds));
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void scaleIsRemovedWhenItsOwnerDiesOrIsRemoved(GameTestHelper helper) {
        Scene scene = new Scene(helper);
        Cow dying = scene.cow(1.5D, 1.5D);
        Cow leaving = scene.cow(5.5D, 1.5D);
        UltimateScaleProjectileEntity ofDying = scene.launch(dying, 0.1F);
        UltimateScaleProjectileEntity ofLeaving = scene.launch(leaving, 0.1F);
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(!ofDying.isRemoved() && !ofLeaving.isRemoved() && dying.isAlive() && leaving.isAlive(),
                    "Both slow scales must still be flying beside their living owners.");
            dying.kill();
            leaving.discard();
            helper.assertTrue(!dying.isAlive() && !dying.isRemoved() && leaving.isRemoved(),
                    "Fixture owners must be dying and removed respectively.");
        });
        helper.runAtTickTime(6, () -> {
            helper.assertTrue(ofDying.isRemoved(),
                    "A scale is removed when its owner is dead, but it still flies at age " + ofDying.getElapsedLifeTicks());
            helper.assertTrue(ofLeaving.isRemoved(),
                    "A scale is removed when its owner has left the level, but it still flies at age "
                            + ofLeaving.getElapsedLifeTicks());
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void scaleNeverHitsItsOwnerUnlessDeflected(GameTestHelper helper) {
        scaleNeverHitsItsOwnerUnlessDeflected(helper, new Scene(helper));
    }

    // The check 41 failure with a stand-in for another test's mob: a motionless cow on the returning scale's path where
    // the scene would stand at its template, its chunks held so that it is there wherever the grid puts the template.
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void scaleNeverHitsItsOwnerUnlessDeflectedBesideAnotherTestsMob(GameTestHelper helper) {
        Vec3 path = Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO)).add(1.5D, OWNER_Y, 3.0D);
        new UltimateLifeformChunkTickets(helper).hold(path.add(-8.0D, 0.0D, -8.0D), path.add(8.0D, 0.0D, 8.0D));
        Scene scene = new Scene(helper);
        Cow stray = new Cow(EntityType.COW, scene.level);
        stray.setPos(path.x, path.y, path.z);
        stray.setNoAi(true);
        scene.add(stray);
        scaleNeverHitsItsOwnerUnlessDeflected(helper, scene);
    }

    private static void scaleNeverHitsItsOwnerUnlessDeflected(GameTestHelper helper, Scene scene) {
        Cow owner = scene.cow(1.5D, 4.5D);
        Cow behindOwner = scene.cow(1.5D, 6.5D);
        UltimateScaleProjectileEntity returning = scene.prepare(owner, 0.5F);
        // Starts behind its owner, so the vanilla left-owner latch closes on the first tick.
        returning.setPos(returning.getX(), returning.getY(), returning.getZ() - 3.5D);
        scene.add(returning);

        Cow deflectedOwner = scene.cow(5.5D, 1.5D);
        UltimateScaleProjectileEntity deflected = scene.prepare(deflectedOwner, 1.0F);
        Deflector deflector = new Deflector(scene.level, deflected);
        scene.place(deflector, 5.5D, 5.2D);
        scene.add(deflected);

        helper.runAtTickTime(14, () -> {
            helper.assertTrue(owner.getHealth() == 10.0F,
                    "A scale that left its owner passes through the owner, but the owner's health is " + owner.getHealth());
            helper.assertTrue(returning.isRemoved() && behindOwner.getHealth() == 10.0F - DONOR_DAMAGE,
                    "The same scale still hits the entity behind its owner: removed=" + returning.isRemoved()
                            + " health=" + behindOwner.getHealth());
            helper.assertTrue(deflector.calls == 1 && deflector.getHealth() == 10.0F,
                    "The deflecting entity must have reversed the scale once without being hurt: calls=" + deflector.calls);
            helper.assertTrue(deflected.isRemoved() && deflectedOwner.getHealth() == 10.0F - DONOR_DAMAGE,
                    "A deflected scale can hit its owner: removed=" + deflected.isRemoved()
                            + " ownerHealth=" + deflectedOwner.getHealth());
            helper.succeed();
        });
    }

    // 1.16 JojoModUtil.deflectProjectile marked every deflected scale, so the Hamon shield's reversal let it hit its owner.
    @GameTest(template = "empty", batch = REFLECTION_BATCH, timeoutTicks = 60)
    public static void scaleReversedByHamonProjectileShieldHitsItsOwner(GameTestHelper helper) {
        Scene scene = new Scene(helper);
        Player shieldUser = scene.player(4.0D, 1.5D);
        PlayerPower power = PowerClass.PLAYER_POWER.attachGet(shieldUser);
        power.setPowerType(ModPlayerPowers.HAMON.get());
        scene.onClose(() -> power.setPowerType(null));
        HamonData hamon = PlayerPower.getPowerData(shieldUser, ModPlayerPowers.HAMON).orElseThrow();
        hamon.learnSkill(ModHamonSkills.PROJECTILE_SHIELD.get());
        hamon.setBreathStability(hamon.getMaxBreathStability());
        hamon.setEnergy(hamon.getMaxEnergy());
        HamonProjectileShieldEntity shield = scene.holdShield(shieldUser, power);
        hamon.setEnergy(hamon.getMaxEnergy());
        float energyBefore = hamon.getEnergy();
        float userHealth = shieldUser.getHealth();

        Cow owner = scene.cow(4.0D, 6.9D);
        owner.setYRot(180.0F);
        owner.setYHeadRot(180.0F);
        owner.yBodyRot = 180.0F;
        UltimateScaleProjectileEntity scale = scene.launch(owner, 1.0F);
        helper.assertTrue(scale.getDeltaMovement().z < -0.99D, "Fixture scale must fly at the shield: " + scale.getDeltaMovement());

        for (int tick = 0; tick < 8 && scale.getDeltaMovement().z < 0.0D && !scale.isRemoved(); tick++) {
            scale.tick();
            shield.tick();
        }
        helper.assertTrue(!scale.isRemoved() && scale.getDeltaMovement().z > 0.99D && shield.isAlive()
                        && Math.abs(hamon.getEnergy() - (energyBefore - 20.0F)) < 0.01F && owner.getHealth() == 10.0F,
                "The real Hamon projectile shield must have reversed the scale for 20 energy: removed=" + scale.isRemoved()
                        + " motion=" + scale.getDeltaMovement() + " energy=" + energyBefore + "->" + hamon.getEnergy());

        for (int tick = 0; tick < 6 && !scale.isRemoved(); tick++) {
            scale.tick();
        }
        helper.assertTrue(scale.isRemoved() && owner.getHealth() == 10.0F - DONOR_DAMAGE,
                "A scale reversed by the Hamon projectile shield hits its owner for 5: removed=" + scale.isRemoved()
                        + " ownerHealth=" + owner.getHealth() + " scale=" + scene.relative(scale));
        helper.assertTrue(shieldUser.getHealth() == userHealth, "The shield user must be unhurt: " + shieldUser.getHealth());
        helper.succeed();
    }

    // 1.16 SilverChariotEntity.deflectProjectile used the same JojoModUtil.deflectProjectile mark.
    @GameTest(template = "empty", batch = REFLECTION_BATCH, timeoutTicks = 60)
    public static void scaleDeflectedBySilverChariotRapierHitsItsOwner(GameTestHelper helper) {
        Scene scene = new Scene(helper);
        StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("silver_chariot"));
        helper.assertTrue(standType != null, "Missing Silver Chariot Stand type");
        Player standUser = FakePlayerFactory.get(scene.level, new GameProfile(UUID.randomUUID(), "ScaleChariotUser"));
        standUser.moveTo(scene.origin.getX() + 7.0D, scene.origin.getY() + OWNER_Y, scene.origin.getZ() + 6.5D, 180.0F, 0.0F);
        scene.add(standUser);
        StandPower power = PowerClass.STAND.attachGet(standUser);
        helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
                == StandPowerTransitions.Status.APPLIED, "Could not grant Silver Chariot");
        scene.onClose(() -> {
            if (power.isSummoned()) {
                standType.forceUnsummon(standUser, power);
            }
        });
        helper.assertTrue(standType.summon(standUser, power), "Could not summon Silver Chariot");
        StandEntity stand = power.getSummonedStandEntity();
        helper.assertTrue(stand != null, "Summoned Silver Chariot is missing");
        power.setResolveLevel(power.getMaxResolveLevel());
        stand.moveTo(scene.origin.getX() + 4.0D, scene.origin.getY() + OWNER_Y, scene.origin.getZ() + 6.5D, 180.0F, 0.0F);
        stand.setYHeadRot(180.0F);
        helper.assertTrue(stand.isSilverChariotRapierVisible() && power.getResolveLevel() >= 4
                        && stand.getLookAngle().z < -0.99D,
                "Fixture Silver Chariot must hold its rapier at resolve 4 or higher and face the scale: rapier="
                        + stand.isSilverChariotRapierVisible() + " resolve=" + power.getResolveLevel() + " look=" + stand.getLookAngle());

        Cow owner = scene.cow(4.0D, 1.5D);
        UltimateScaleProjectileEntity scale = scene.launch(owner, 1.0F);
        for (int tick = 0; tick < 3; tick++) {
            scale.tick();
        }
        helper.assertTrue(!scale.isRemoved() && scale.getDeltaMovement().z > 0.99D,
                "Fixture scale must still fly at Silver Chariot: removed=" + scale.isRemoved() + " motion=" + scale.getDeltaMovement());
        stand.deflectSilverChariotNearbyProjectiles();
        helper.assertTrue(scale.getDeltaMovement().z < -0.99D && owner.getHealth() == 10.0F,
                "Silver Chariot's rapier must have sent the scale back along its look: " + scale.getDeltaMovement());

        for (int tick = 0; tick < 6 && !scale.isRemoved(); tick++) {
            scale.tick();
        }
        helper.assertTrue(scale.isRemoved() && owner.getHealth() == 10.0F - DONOR_DAMAGE,
                "A scale deflected by Silver Chariot hits its owner for 5: removed=" + scale.isRemoved()
                        + " ownerHealth=" + owner.getHealth() + " scale=" + scene.relative(scale));
        helper.succeed();
    }

    // 1.16 set the deflected mark only in JojoModUtil.deflectProjectile; a blast that throws a scale back is no deflection.
    @GameTest(template = "empty", batch = REFLECTION_BATCH, timeoutTicks = 60)
    public static void scaleThrownBackByAnExplosionStillPassesThroughItsOwner(GameTestHelper helper) {
        Scene scene = new Scene(helper);
        Cow owner = scene.cow(4.0D, 1.5D);
        Cow behindOwner = scene.cow(4.0D, 0.2D);
        UltimateScaleProjectileEntity scale = scene.prepare(owner, 0.1F);
        scale.setPos(scale.getX(), scale.getY(), scale.getZ() + 3.0D);
        scene.add(scale);
        scale.tick();
        Vec3 before = scale.getDeltaMovement();
        scene.level.explode(null, scale.getX(), scale.getEyeY(), scale.getZ() + 0.5D, 1.0F, Level.ExplosionInteraction.NONE);
        Vec3 thrown = scale.getDeltaMovement();
        helper.assertTrue(thrown.dot(before) < 0.0D && thrown.z < -0.3D && owner.getHealth() == 10.0F
                        && behindOwner.getHealth() == 10.0F,
                "Fixture blast must throw the scale back at its owner without hurting the cows: " + before + " -> " + thrown
                        + " owner=" + owner.getHealth() + " behind=" + behindOwner.getHealth());
        scale.tick();
        CompoundTag saved = new CompoundTag();
        scale.saveWithoutId(saved);
        helper.assertTrue(saved.contains("IsDeflected") && !saved.getBoolean("IsDeflected"),
                "A blast is not a deflection, but the scale saved IsDeflected=" + saved.getBoolean("IsDeflected"));
        for (int tick = 0; tick < 20 && !scale.isRemoved(); tick++) {
            scale.tick();
        }
        helper.assertTrue(owner.getHealth() == 10.0F,
                "A scale thrown back by a blast passes through its owner, but the owner's health is " + owner.getHealth());
        helper.assertTrue(scale.isRemoved() && behindOwner.getHealth() == 10.0F - DONOR_DAMAGE,
                "The same scale still hits the entity behind its owner: removed=" + scale.isRemoved()
                        + " health=" + behindOwner.getHealth() + " scale=" + scene.relative(scale));
        helper.succeed();
    }

    private static long seedWithRoll(double aboveExclusive, double atMost) {
        for (long seed = 1L; seed < 100_000L; seed++) {
            double roll = RandomSource.create(seed).nextDouble();
            if (roll > aboveExclusive && roll <= atMost) {
                return seed;
            }
        }
        throw new IllegalStateException("No seed rolls inside (" + aboveExclusive + ", " + atMost + "]");
    }

    private static long hitSounds(List<PlayLevelSoundEvent.AtPosition> sounds, BlockPos pos, SoundType type) {
        return sounds.stream().filter(event -> event.getSound() != null && event.getSound().value() == type.getHitSound()
                && event.getSource() == SoundSource.BLOCKS && event.getOriginalVolume() == 1.0F
                && event.getOriginalPitch() == type.getPitch() * 0.5F
                && event.getPosition().distanceToSqr(Vec3.atCenterOf(pos)) < 1.0E-8D).count();
    }

    private static String describe(List<PlayLevelSoundEvent.AtPosition> sounds) {
        List<String> lines = new ArrayList<>();
        for (PlayLevelSoundEvent.AtPosition event : sounds) {
            lines.add((event.getSound() == null ? "null" : event.getSound().value().getLocation()) + "@" + event.getPosition()
                    + " " + event.getSource() + " volume=" + event.getOriginalVolume() + " pitch=" + event.getOriginalPitch());
        }
        return lines.toString();
    }

    private static final class Scene implements GameTestListener {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final BlockPos origin;
        private final AABB bounds;
        private final List<Entity> owned = new ArrayList<>();
        private final List<BlockPos> blocks = new ArrayList<>();
        private final List<Object> listeners = new ArrayList<>();
        private final List<Runnable> closers = new ArrayList<>();
        private boolean closed;

        Scene(GameTestHelper helper) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.origin = helper.absolutePos(BlockPos.ZERO).offset(AWAY);
            this.bounds = new AABB(origin.getX(), origin.getY(), origin.getZ(),
                    origin.getX() + 8.0D, origin.getY() + 8.0D, origin.getZ() + 8.0D);
            helper.testInfo.addListener(this);
            new UltimateLifeformChunkTickets(helper).hold(Vec3.atLowerCornerOf(origin),
                    Vec3.atLowerCornerOf(origin).add(8.0D, 0.0D, 8.0D));
        }

        /** A motionless cow facing +Z; a scale launched from it flies at relative y 3.2. */
        Cow cow(double x, double z) {
            Cow cow = new Cow(EntityType.COW, level);
            place(cow, x, z);
            return cow;
        }

        void place(Cow cow, double x, double z) {
            cow.setPos(origin.getX() + x, origin.getY() + OWNER_Y, origin.getZ() + z);
            cow.setXRot(0.0F);
            cow.setYRot(0.0F);
            cow.setYHeadRot(0.0F);
            cow.yBodyRot = 0.0F;
            cow.setNoAi(true);
            add(cow);
        }

        /** A weightless survival player facing +Z; the tests drive its held action themselves. */
        Player player(double x, double z) {
            Player player = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "ScaleShieldUser")) {
                @Override public boolean isCreative() { return false; }
                @Override public boolean isSpectator() { return false; }
            };
            player.setNoGravity(true);
            player.moveTo(origin.getX() + x, origin.getY() + OWNER_Y, origin.getZ() + z, 0.0F, 0.0F);
            player.setYHeadRot(0.0F);
            add(player);
            return player;
        }

        /** Raises the registered Projectile Shield as a held action; its plane stands 2 blocks ahead of the user. */
        HamonProjectileShieldEntity holdShield(Player user, PlayerPower power) {
            Ability found = power.getAbility("projectile_shield");
            helper.assertTrue(found instanceof EntityActionAbility, "Missing registered projectile_shield");
            EntityActionInstance action = ((EntityActionAbility) found).initActionOnAbilityUse(level, user, user, null);
            LivingComponentAction component = LivingComponentAction.getComponent(user);
            component.setAction(action, user, SyncType.NO_SYNC);
            user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys
                    .put(SHIELD_KEY, new HeldInputEntry(SHIELD_KEY, 1L, PowerClass.PLAYER_POWER, action));
            onClose(() -> {
                user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
                component.setAction(null, SyncType.NO_SYNC);
            });
            user.tickCount++;
            component.tick();
            helper.assertTrue(action.getPhase() == ActionPhase.PERFORM,
                    "Projectile Shield must hold from the press: phase=" + action.getPhase());
            HamonProjectileShieldEntity shield = level.getEntitiesOfClass(HamonProjectileShieldEntity.class,
                    user.getBoundingBox().inflate(8.0D), candidate -> candidate.isAlive() && candidate.getOwnerEntity() == user)
                    .stream().findFirst().orElse(null);
            helper.assertTrue(shield != null, "The held Projectile Shield tick made no shield");
            owned.add(shield);
            return shield;
        }

        void onClose(Runnable closer) {
            closers.add(0, closer);
        }

        Vec3 relative(Entity entity) {
            return entity.position().subtract(origin.getX(), origin.getY(), origin.getZ());
        }

        BlockPos block(int x, Block block) {
            BlockPos pos = origin.offset(x, 3, 2);
            BlockState state = block.defaultBlockState();
            helper.assertTrue(level.setBlockAndUpdate(pos, state), "Fixture block was not placed at " + pos);
            blocks.add(pos);
            return pos;
        }

        UltimateScaleProjectileEntity prepare(LivingEntity owner, float speed) {
            UltimateScaleProjectileEntity scale = UltimateLifeformAddon.SCALE_PROJECTILE.get().create(level);
            helper.assertTrue(scale != null, "The registered scale type created no entity.");
            scale.initialize(owner, DONOR_DAMAGE, false, speed, 0.0F);
            scale.getRandom().setSeed(NO_POISON_SEED);
            return scale;
        }

        UltimateScaleProjectileEntity launch(LivingEntity owner, float speed) {
            UltimateScaleProjectileEntity scale = prepare(owner, speed);
            add(scale);
            return scale;
        }

        void add(Entity entity) {
            owned.add(entity);
            helper.assertTrue(level.addFreshEntity(entity), "Fixture entity did not join the level: " + entity);
        }

        <T extends Event> void listen(Class<T> type, Consumer<T> listener) {
            listeners.add(listener);
            NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, type, listener);
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            listeners.forEach(NeoForge.EVENT_BUS::unregister);
            listeners.clear();
            closers.forEach(Runnable::run);
            closers.clear();
            for (Entity entity : owned) {
                if (!entity.isRemoved()) {
                    entity.discard();
                }
            }
            for (BlockPos pos : blocks) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            level.getEntitiesOfClass(ItemEntity.class, bounds.inflate(1.0D)).forEach(Entity::discard);
        }

        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
    }

    private static final class Deflector extends Cow {
        private final Projectile reversed;
        private int calls;

        Deflector(ServerLevel level, Projectile reversed) {
            super(EntityType.COW, level);
            this.reversed = reversed;
        }

        @Override
        public ProjectileDeflection deflection(Projectile projectile) {
            if (projectile == reversed) {
                calls++;
                return ProjectileDeflection.REVERSE;
            }
            return super.deflection(projectile);
        }
    }
}
