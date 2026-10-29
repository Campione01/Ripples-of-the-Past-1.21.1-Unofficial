package rotp.addons.cmoon.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import rotp.addons.cmoon.CMoonAddon;
import rotp.addons.cmoon.ability.HeartInversionAbility;
import rotp.addons.cmoon.entity.CMoonStandEntity;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 cm_heavy_punch: with the finisher meter at 0.5 the heavy punch is always Heart Inversion
 * (StandEntityHeavyAttack.replaceAction), whose hit adds disableBlocking(proximityRatio - 0.25) and whose
 * punchSound is the impact sound.
 */
@GameTestHolder(CMoonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeartInversionGameTests {
    private static final short KEY = 7301;
    // the hit is one roll of at least 0.75; sixteen misses in a row are 2e-10
    private static final int NEAR_ATTEMPTS = 16;
    private static final int FAR_ATTEMPTS = 40;
    private static final double NEAR = 0.6D;
    private static final double MISS_STAND_BACK = 8.0D;
    // the barrier ring around the 4x4x4 test area, on the line from the user's eyes to the targets 5 blocks ahead
    private static final BlockPos SIGHT_LINE_BARRIER = new BlockPos(1, 3, 4);

    private HeartInversionGameTests() {}

    @GameTest(template = "inversion_fixture", timeoutTicks = 100)
    public static void heavyInputIsRefusedWhenHeartInversionCannotFire(GameTestHelper helper) {
        Fixture f = new Fixture(helper, "c0de0001-0000-4000-8000-00000000c301", "CMoonHeavyGate");
        Mob destroyTarget = null;
        try {
            float max = f.power.getMaxStamina();
            helper.assertTrue(max >= 200.0F, "C-Moon fixture has too little stamina: " + max);

            f.expectHeavy(0.0F, max, CMoonAddon.HEAVY_PUNCH, "an empty finisher meter");
            f.expectHeavy(0.6F, max, CMoonAddon.HEART_INVERSION, "a charged finisher meter");
            f.expectHeavy(0.6F, 10.0F, null,
                    "1.16: a charged finisher meter under 50 stamina (Heart Inversion is refused, nothing replaces it)");
            f.expectHeavy(0.4F, 10.0F, CMoonAddon.HEAVY_PUNCH,
                    "1.16: the plain heavy punch under 50 stamina (it has no stamina admission)");

            destroyTarget = f.husk(f.userPos.add(4.0D, 0.0D, 0.0D));
            f.stand.setAutoAttackTarget(destroyTarget);
            helper.assertTrue(f.stand.hasAutoAttackTarget(), "Destroy target was not set");
            f.expectHeavy(0.6F, max, null,
                    "1.16: a charged finisher meter during Destroy (isAtt refuses Heart Inversion)");
            f.expectHeavy(0.4F, max, CMoonAddon.HEAVY_PUNCH, "1.16: the plain heavy punch during Destroy");
            f.stand.clearAutoAttackTarget();

            f.user.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 200, 0));
            f.expectHeavy(0.6F, 10.0F, null,
                    "1.16: a charged finisher meter under 50 stamina in Resolve (power.getStamina() < 50 is raw)");
            f.user.removeEffect(ModStatusEffects.RESOLVE);
            helper.succeed();
        }
        finally {
            if (destroyTarget != null) destroyTarget.discard();
            f.close();
        }
    }

    @GameTest(template = "inversion_fixture", timeoutTicks = 100)
    public static void heartInversionBreaksStandGuardBeforeItsDamage(GameTestHelper helper) {
        Fixture f = new Fixture(helper, "c0de0001-0000-4000-8000-00000000c302", "CMoonGuardBreak");
        List<Defender> defenders = new ArrayList<>();
        try {
            Vec3 guardPos = f.userPos.add(0.0D, 0.0D, 5.0D);

            // at the edge of reach the chance is 0: the guard always holds; a fresh defender per hit, as below, so that
            // every hit rolls
            for (int i = 0; i < FAR_ATTEMPTS; i++) {
                Defender far = new Defender(f, defenders, guardPos);
                HeartInversionAbility.HeartInversion hit = f.heartInversion(far.stand, 2.65D, 0.0F);
                helper.assertTrue(hit.punchedTarget != null && hit.punchedTarget.getMainEntity() == far.stand,
                        "Far Heart Inversion did not reach the guarding Stand");
                helper.assertTrue(far.stand.isStandBlocking(),
                        "1.16: disableBlocking(proximityRatio - 0.25) is 0 at the edge of reach, but hit " + (i + 1)
                                + " broke the guard");
                helper.assertTrue(far.stand.isCurrentAttackBlocked(),
                        "The fixture's guard does not block a Heart Inversion from the front");
                far.close();
            }

            // a fresh defender per hit: a Stand hit is followed by Stand invulnerability ticks
            Defender broken = null;
            for (int i = 0; i < NEAR_ATTEMPTS && broken == null; i++) {
                Defender near = new Defender(f, defenders, guardPos);
                HeartInversionAbility.HeartInversion hit = f.heartInversion(near.stand, NEAR, 0.75F);
                helper.assertTrue(hit.punchedTarget != null && hit.punchedTarget.getMainEntity() == near.stand,
                        "Near Heart Inversion did not reach the guarding Stand");
                if (near.stand.isStandBlocking()) {
                    near.close();
                }
                else {
                    broken = near;
                }
            }
            helper.assertTrue(broken != null, "1.16: Heart Inversion disableBlocking(proximityRatio - 0.25) breaks a "
                    + "Stand's guard, but " + NEAR_ATTEMPTS + " point-blank hits left it up");
            helper.assertTrue(!broken.stand.canStartBlocking(),
                    "1.16: a broken guard is locked for getBlockingBreakTicks(durability)");
            helper.assertTrue(!broken.stand.isCurrentAttackBlocked(),
                    "1.16: the guard break is rolled before the damage, so the breaking hit is not blocked");
            helper.succeed();
        }
        finally {
            defenders.forEach(Defender::close);
            f.close();
        }
    }

    // 1.16 StandEntityPunch.doAttack returns on LivingUtilCap.onStandAttack 0 before it reaches onAttack
    @GameTest(template = "inversion_fixture", timeoutTicks = 100)
    public static void heartInversionSwallowedByStandInvulnerabilityRollsNoGuardBreak(GameTestHelper helper) {
        Fixture f = new Fixture(helper, "c0de0001-0000-4000-8000-00000000c305", "CMoonStandInvul");
        List<Defender> defenders = new ArrayList<>();
        Defender[] guard = new Defender[1];
        int[] reachedHurt = new int[1];
        Consumer<LivingIncomingDamageEvent> listener = event -> {
            if (guard[0] != null && event.getEntity() == guard[0].stand) {
                reachedHurt[0]++;
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingIncomingDamageEvent.class, listener);
        try {
            guard[0] = new Defender(f, defenders, f.userPos.add(0.0D, 0.0D, 5.0D));
            StandEntity guarding = guard[0].stand;

            // from the edge of reach the chance is 0: this hit only starts the Stand invulnerability ticks
            f.heartInversion(guarding, 2.65D, 0.0F);
            helper.assertTrue(reachedHurt[0] == 1,
                    "Fixture: the first Heart Inversion reached the guarding Stand's hurt " + reachedHurt[0] + " times");
            helper.assertTrue(guarding.isStandBlocking(), "Fixture: the first Heart Inversion broke the guard");

            long seed = 764L;
            float nextUndrawn = RandomSource.create(seed).nextFloat();
            for (int i = 0; i < NEAR_ATTEMPTS; i++) {
                EntityActionInstance hit = f.perform(guarding, NEAR, 0.75F, CMoonAddon.HEART_INVERSION,
                        () -> f.stand.getRandom().setSeed(seed));
                helper.assertTrue(hit.punchedTarget != null && hit.punchedTarget.getMainEntity() == guarding,
                        "Near Heart Inversion did not reach the guarding Stand");
                helper.assertTrue(reachedHurt[0] == 1, "Fixture: point-blank hit " + (i + 1)
                        + " was not swallowed by the Stand invulnerability ticks");
                helper.assertTrue(guarding.isStandBlocking(),
                        "1.16: a Heart Inversion swallowed by Stand invulnerability ticks never reaches onAttack, but "
                                + "point-blank hit " + (i + 1) + " broke the guard");
                helper.assertTrue(Float.compare(f.stand.getRandom().nextFloat(), nextUndrawn) == 0,
                        "1.16: a Heart Inversion swallowed by Stand invulnerability ticks rolls nothing, but point-blank "
                                + "hit " + (i + 1) + " drew from the Stand's random");
            }
            helper.succeed();
        }
        finally {
            NeoForge.EVENT_BUS.unregister(listener);
            defenders.forEach(Defender::close);
            f.close();
        }
    }

    @GameTest(template = "inversion_fixture", timeoutTicks = 100)
    public static void heartInversionDisablesRaisedShield(GameTestHelper helper) {
        Fixture f = new Fixture(helper, "c0de0001-0000-4000-8000-00000000c303", "CMoonShield");
        List<Player> blockers = new ArrayList<>();
        try {
            Vec3 pos = f.userPos.add(0.0D, 0.0D, 5.0D);
            for (int i = 0; i < FAR_ATTEMPTS; i++) {
                Player blocker = f.blocker(blockers, pos);
                float health = blocker.getHealth();
                f.heartInversion(blocker, 2.6D, 0.0F);
                helper.assertTrue(blocker.getHealth() < health, "Far Heart Inversion did not hurt the player");
                helper.assertTrue(!blocker.getCooldowns().isOnCooldown(Items.SHIELD) && blocker.isUsingItem(),
                        "1.16: disableBlocking(proximityRatio - 0.25) is 0 at the edge of reach, but hit " + (i + 1)
                                + " disabled the shield");
                blocker.discard();
            }

            boolean disabled = false;
            for (int i = 0; i < NEAR_ATTEMPTS && !disabled; i++) {
                Player blocker = f.blocker(blockers, pos);
                float health = blocker.getHealth();
                f.heartInversion(blocker, NEAR, 0.75F);
                helper.assertTrue(blocker.getHealth() < health, "Near Heart Inversion did not hurt the player");
                if (blocker.getCooldowns().isOnCooldown(Items.SHIELD)) {
                    helper.assertTrue(!blocker.isUsingItem(), "1.16: a disabled shield is lowered");
                    disabled = true;
                }
                blocker.discard();
            }
            helper.assertTrue(disabled, "1.16: a hurting Heart Inversion puts a raised shield on cooldown "
                    + "(DamageUtil.disableShield), but " + NEAR_ATTEMPTS + " point-blank hits left it up");

            // the plain heavy punch has no disableBlocking
            for (int i = 0; i < NEAR_ATTEMPTS; i++) {
                Player blocker = f.blocker(blockers, pos);
                float health = blocker.getHealth();
                f.perform(blocker, NEAR, 0.75F, CMoonAddon.HEAVY_PUNCH);
                helper.assertTrue(blocker.getHealth() < health, "The plain heavy punch did not hurt the player");
                helper.assertTrue(!blocker.getCooldowns().isOnCooldown(Items.SHIELD) && blocker.isUsingItem(),
                        "1.16: the plain heavy punch does not disable a shield");
                blocker.discard();
            }
            helper.succeed();
        }
        finally {
            blockers.forEach(Entity::discard);
            f.close();
        }
    }

    @GameTest(template = "inversion_fixture", timeoutTicks = 100)
    public static void heartInversionHitPlaysItsOwnImpactSound(GameTestHelper helper) {
        Fixture f = new Fixture(helper, "c0de0001-0000-4000-8000-00000000c304", "CMoonImpactSound");
        Mob target = null;
        List<Played> played = new ArrayList<>();
        Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
            if (event.getSound() != null) {
                played.add(new Played(event.getSound().value().getLocation(), event.getPosition()));
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayLevelSoundEvent.AtPosition.class, listener);
        try {
            target = f.husk(f.userPos.add(0.0D, 0.0D, 5.0D));
            ResourceLocation heart = CMoonAddon.C_MOON_HEART_INVERSION_SOUND.getId();
            ResourceLocation coreHeavy = ModSoundEvents.STAND_PUNCH_HEAVY.getId();
            Vec3 centre = target.getBoundingBox().getCenter();

            // 1.16 StandMissedPunch never plays (playImpactSound is targetWasHit); the port keeps a swing from the Stand
            HeartInversionAbility.HeartInversion miss = f.heartInversion(null, 0.0D, 0.0F);
            helper.assertTrue(miss.punchedTarget != null && miss.punchedTarget.getType() == ActionTarget.TargetType.EMPTY,
                    "The untargeted Heart Inversion hit something: " + miss.punchedTarget);
            helper.assertTrue(miss.playedMissSound, "A missed Heart Inversion lost its sound from the Stand");
            helper.assertTrue(played.stream().noneMatch(sound -> sound.id().equals(heart) || sound.id().equals(coreHeavy)),
                    "A missed Heart Inversion played an impact sound: " + played);

            played.clear();
            float health = target.getHealth();
            HeartInversionAbility.HeartInversion hit = f.heartInversion(target, NEAR, 0.75F);
            helper.assertTrue(target.getHealth() < health, "Heart Inversion did not hurt the husk");
            helper.assertTrue(!hit.playedMissSound,
                    "1.16: a Heart Inversion that lands plays its sound at the target only, not from the Stand too");
            helper.assertTrue(played.stream().noneMatch(sound -> sound.id().equals(coreHeavy)),
                    "1.16: Heart Inversion's punchSound is its only impact sound, but the core heavy impact played: "
                            + played);
            helper.assertTrue(played.stream().filter(sound -> sound.id().equals(heart)
                    && sound.pos().distanceTo(centre) < 1.0E-4D).count() == 1,
                    "1.16: cm_heart_inversion plays once at the target's centre " + centre + ", played: " + played);
            helper.succeed();
        }
        finally {
            NeoForge.EVENT_BUS.unregister(listener);
            if (target != null) target.discard();
            f.close();
        }
    }

    /** 1.16 IPunch.playImpactSound: the punch sound plays at the target only when the hit landed. */
    @GameTest(template = "inversion_fixture", timeoutTicks = 100)
    public static void punchImpactSoundPlaysOnlyForALandedHit(GameTestHelper helper) {
        Fixture f = new Fixture(helper, "c0de0001-0000-4000-8000-00000000c305", "CMoonPunchImpact");
        Mob[] refusing = {null};
        List<Played> played = new ArrayList<>();
        Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
            if (event.getSound() != null) {
                played.add(new Played(event.getSound().value().getLocation(), event.getPosition()));
            }
        };
        Consumer<LivingIncomingDamageEvent> refuse = event -> {
            if (event.getEntity() == refusing[0]) {
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayLevelSoundEvent.AtPosition.class, listener);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingIncomingDamageEvent.class, refuse);
        try {
            Mob target = f.husk(f.userPos.add(0.0D, 0.0D, 5.0D));
            ResourceLocation punchSound = CMoonAddon.C_MOON_PUNCH_SOUND.getId();

            refusing[0] = target;
            float health = target.getHealth();
            EntityActionInstance refused = f.punch(target);
            helper.assertTrue(refused.punchedTarget != null && refused.punchedTarget.getMainEntity() == target
                    && target.getHealth() == health, "Premise: the refused punch reached the husk and did not hurt it");
            helper.assertTrue(played.stream().noneMatch(sound -> sound.id().equals(punchSound)),
                    "1.16: a punch that did not land plays no impact sound, played: " + played);

            refusing[0] = null;
            played.clear();
            f.punch(target);
            helper.assertTrue(target.getHealth() < health, "The landed punch did not hurt the husk");
            helper.assertTrue(played.stream().filter(sound -> sound.id().equals(punchSound)).count() == 1,
                    "1.16: a punch that lands plays cm_punch once at the target, played: " + played);
            helper.succeed();
        }
        finally {
            NeoForge.EVENT_BUS.unregister(listener);
            NeoForge.EVENT_BUS.unregister(refuse);
            f.close();
        }
    }

    private record Played(ResourceLocation id, Vec3 pos) {}

    // a second C-Moon user whose Stand holds its guard, facing the attacker
    private static final class Defender {
        final Fixture f;
        final Player player;
        final StandPower power;
        final StandEntity stand;

        Defender(Fixture f, List<Defender> defenders, Vec3 standPos) {
            this.f = f;
            GameTestHelper helper = f.helper;
            player = f.mockPlayer(standPos.add(0.0D, 0.0D, 1.0D));
            power = PowerClass.STAND.attachGet(player);
            defenders.add(this);
            helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(f.type)).status()
                    == StandPowerTransitions.Status.APPLIED && f.type.summon(player, power),
                    "Could not give the defender a summoned C-Moon");
            stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "The defender's Stand is missing");
            Ability ability = power.getAbility(CMoonAddon.GUARD);
            helper.assertTrue(ability instanceof EntityActionType, "C-Moon's guard is missing");
            EntityActionType actionType = (EntityActionType) ability;
            EntityActionInstance action = actionType.createActionObj();
            actionType.initActionFromConfig(action, helper.getLevel(), player, stand);
            action.phasesLength.put(ActionPhase.BUTTON_CHARGE, 0F);
            action.phasesLength.put(ActionPhase.WINDUP, 0F);
            action.phasesLength.put(ActionPhase.PERFORM, 2000F);
            action.setStartingPhase();
            power.setStamina(power.getMaxStamina());
            LivingComponentAction.getComponent(stand).setAction(action, player, SyncType.NO_SYNC);
            stand.moveTo(standPos.x, standPos.y, standPos.z, 180.0F, 0.0F);
            stand.setYHeadRot(180.0F);
            stand.yRotO = 180.0F;
            stand.yHeadRotO = 180.0F;
            helper.assertTrue(stand.isStandBlocking(), "The held guard is not blocking");
        }

        void close() {
            if (power.isSummoned()) {
                f.type.forceUnsummon(player, power);
            }
            player.discard();
        }
    }

    // 1.16 StandEntity.getProximityRatio (JojoModUtil.getDistance) - 0.25, clamped as StandEntityPunch.disableBlocking
    private static float donorDisableBlockingChance(StandEntity stand, Entity target) {
        double range = stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        AABB box = target.getBoundingBox();
        Vec3 eye = stand.getEyePosition();
        double distance;
        if (box.contains(eye)) {
            distance = 0;
        }
        else {
            Vec3 end = new Vec3(
                    Mth.lerp(0.5D, box.minX, box.maxX),
                    Mth.lerp(stand.getBbHeight() == 0 ? 0 : stand.getEyeHeight() / stand.getBbHeight(),
                            box.minY, box.maxY),
                    Mth.lerp(0.5D, box.minZ, box.maxZ));
            distance = box.clip(eye, end).map(clip -> eye.distanceTo(clip) - stand.getBbWidth() / 2).orElse(-1D);
        }
        double ratio = range > 0 ? 1 - distance / range : 0;
        return Mth.clamp((float) ratio - 0.25F, 0, 1);
    }

    private static final class Fixture {
        final GameTestHelper helper;
        final EntityStandType type;
        final Player user;
        final Vec3 userPos;
        final StandPower power;
        final CMoonStandEntity stand;
        final LivingComponentAction component;
        final List<Entity> spawned = new ArrayList<>();

        Fixture(GameTestHelper helper, String uuid, String name) {
            this.helper = helper;
            type = CMoonAddon.C_MOON.get();
            user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.fromString(uuid), name));
            user.getAbilities().instabuild = false;
            userPos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
            // The user's aim picks what a hit strikes (1.16 clientHitResult, StandEntity.aimWithThisOrUser) and, as walls
            // block a Stand's bodily attack (owner, 2026-10-14 and 2026-10-15), stops at the barrier; the targets stand
            // beyond it, so the user gets a clear line to them.
            helper.assertTrue(helper.getBlockState(SIGHT_LINE_BARRIER).is(Blocks.BARRIER),
                    "The test area's barrier is not where the user's line of sight crosses it");
            helper.setBlock(SIGHT_LINE_BARRIER, Blocks.AIR);
            user.moveTo(userPos.x, userPos.y, userPos.z, 0.0F, 0.0F);
            user.setYHeadRot(0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the C-Moon user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
                    == StandPowerTransitions.Status.APPLIED, "Could not grant C-Moon");
            helper.assertTrue(type.summon(user, power), "Could not summon C-Moon");
            helper.assertTrue(power.getSummonedStandEntity() instanceof CMoonStandEntity,
                    "The summoned Stand is not C-Moon");
            stand = (CMoonStandEntity) power.getSummonedStandEntity();
            stand.summonLockTicks = 0;
            component = LivingComponentAction.getComponent(stand);
            component.setAction(null, user, SyncType.NO_SYNC);
            helper.assertTrue(!power.isStaminaInfinite(), "The fixture user has infinite stamina");
        }

        // the server's ability input: the resolved heavy_punch variation, its admission, then the key press
        @Nullable
        EntityActionInstance pressHeavy() {
            Ability heavy = power.getAbility(CMoonAddon.HEAVY_PUNCH);
            helper.assertTrue(heavy != null, "C-Moon has no heavy punch");
            AvailableAbilities available = new AvailableAbilities();
            available.update(power, power.getMoveset());
            AbilityConditionCheck resolved = available.getContextVariationContainer(heavy);
            if (!AbilityInput.withConditionCheck(resolved, user, InputMethod.CLICK)) {
                return null;
            }
            AbilityInput.keyPress(KEY, resolved.ability, user, null, InputMethod.CLICK, 0.0F,
                    BufferingState.clickOnly(), heavy.getAbilityId());
            return component.getAction();
        }

        void expectHeavy(float meter, float stamina, @Nullable String expected, String label) {
            component.setAction(null, user, SyncType.NO_SYNC);
            stand.setFinisherMeter(meter);
            helper.assertTrue(stand.getFinisherMeter() == meter, "Could not set the finisher meter to " + meter);
            power.setStamina(stamina);
            helper.assertTrue(power.getStamina() == stamina, "Could not set stamina to " + stamina);
            EntityActionInstance started = pressHeavy();
            String startedName = started != null && started.ability instanceof Ability ability ? ability.name() : null;
            component.setAction(null, user, SyncType.NO_SYNC);
            helper.assertTrue(expected == null ? startedName == null : expected.equals(startedName),
                    "The heavy input with " + label + " must "
                            + (expected == null ? "do nothing" : "start " + expected)
                            + ", it started " + startedName);
        }

        HeartInversionAbility.HeartInversion heartInversion(@Nullable Entity target, double distance, float minChance) {
            EntityActionInstance action = perform(target, distance, minChance, CMoonAddon.HEART_INVERSION);
            return (HeartInversionAbility.HeartInversion) action;
        }

        // presses the heavy input aimed at the target and runs its perform start from the given distance;
        // minChance 0 asks for a distance where the 1.16 chance is exactly 0
        // a null target is a punch at nothing
        EntityActionInstance perform(@Nullable Entity target, double distance, float minChance, String expectedAbility) {
            return perform(target, distance, minChance, expectedAbility, null);
        }

        EntityActionInstance perform(@Nullable Entity target, double distance, float minChance, String expectedAbility,
                @Nullable Runnable beforePerformStart) {
            boolean finisher = CMoonAddon.HEART_INVERSION.equals(expectedAbility);
            component.setAction(null, user, SyncType.NO_SYNC);
            stand.setFinisherMeter(finisher ? 1.0F : 0.0F);
            power.setStamina(power.getMaxStamina());
            component.entityAim.setTarget(target != null ? new ActionTarget(target) : ActionTarget.EMPTY);
            EntityActionInstance action = pressHeavy();
            helper.assertTrue(action != null && action.ability == power.getAbility(expectedAbility),
                    "The heavy input did not start " + expectedAbility + ": " + action);
            if (target != null) {
                Vec3 pos = target.position();
                stand.moveTo(pos.x, pos.y, pos.z - distance, 0.0F, 0.0F);
                stand.setYHeadRot(0.0F);
                float chance = donorDisableBlockingChance(stand, target);
                helper.assertTrue(chance >= minChance && (minChance != 0 || chance == 0),
                        "Fixture distance " + distance + " gives a 1.16 chance of " + chance);
            }
            else {
                // The user's own aim decides an untargeted punch (1.16 aimWithThisOrUser) and may meet a target
                // ahead or the test area's barrier walls; standing back puts them out of the Stand's reach.
                stand.moveTo(userPos.x, userPos.y, userPos.z - MISS_STAND_BACK, 0.0F, 0.0F);
                stand.setYHeadRot(0.0F);
            }
            if (beforePerformStart != null) {
                beforePerformStart.run();
            }
            action.actionPerformStart();
            component.setAction(null, user, SyncType.NO_SYNC);
            component.entityAim.setTarget(ActionTarget.EMPTY);
            return action;
        }

        // the light punch, aimed at the target from point-blank range, up to its perform start
        EntityActionInstance punch(Entity target) {
            component.setAction(null, user, SyncType.NO_SYNC);
            power.setStamina(power.getMaxStamina());
            component.entityAim.setTarget(new ActionTarget(target));
            Ability ability = power.getAbility(CMoonAddon.PUNCH);
            helper.assertTrue(ability instanceof EntityActionType, "C-Moon's punch is missing");
            EntityActionType actionType = (EntityActionType) ability;
            EntityActionInstance action = actionType.createActionObj();
            actionType.initActionFromConfig(action, helper.getLevel(), user, stand);
            action.setStartingPhase();
            component.setAction(action, user, SyncType.NO_SYNC);
            Vec3 pos = target.position();
            stand.moveTo(pos.x, pos.y, pos.z - NEAR, 0.0F, 0.0F);
            stand.setYHeadRot(0.0F);
            action.actionPerformStart();
            component.setAction(null, user, SyncType.NO_SYNC);
            component.entityAim.setTarget(ActionTarget.EMPTY);
            return action;
        }

        Mob husk(Vec3 pos) {
            Mob mob = EntityType.HUSK.create(helper.getLevel());
            helper.assertTrue(mob != null, "Could not create a husk");
            mob.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            mob.setNoAi(true);
            mob.setNoGravity(true);
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0D);
            mob.setHealth(500.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(mob), "Could not add a husk");
            spawned.add(mob);
            return mob;
        }

        // a real server player is never the local player, unlike GameTestHelper.makeMockPlayer
        Player mockPlayer(Vec3 pos) {
            Player player = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F,
                    new GameProfile(UUID.randomUUID(), "test-mock-player")) {
                @Override
                public boolean isSpectator() {
                    return false;
                }

                @Override
                public boolean isCreative() {
                    return false;
                }
            };
            player.moveTo(pos.x, pos.y, pos.z, 180.0F, 0.0F);
            player.setYHeadRot(180.0F);
            player.getAbilities().invulnerable = false;
            helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add a player");
            spawned.add(player);
            return player;
        }

        // under 5 ticks of use the shield is up but not blocking yet, so the hit gets through
        Player blocker(List<Player> blockers, Vec3 pos) {
            Player blocker = mockPlayer(pos);
            blocker.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
            blocker.startUsingItem(InteractionHand.OFF_HAND);
            helper.assertTrue(blocker.isUsingItem() && !blocker.isBlocking(), "The shield is not freshly raised");
            blockers.add(blocker);
            return blocker;
        }

        void close() {
            if (power.isSummoned()) {
                type.forceUnsummon(user, power);
            }
            spawned.forEach(Entity::discard);
            user.discard();
        }
    }
}
