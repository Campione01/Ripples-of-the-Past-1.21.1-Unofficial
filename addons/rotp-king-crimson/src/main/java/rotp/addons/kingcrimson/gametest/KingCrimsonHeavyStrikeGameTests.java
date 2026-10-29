package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.mechanics.KnockbackCollisionImpact;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.DamageUtil;

/** Ground Punch and Desperate Eye Jab are 1.16 StandEntityHeavyAttacks; Chop, Impale and Ground Slam are light attacks. */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonHeavyStrikeGameTests {
    static final String GROUND_PUNCH = "kingcrimson_groundpunch";
    static final String EYEJAB = "kingcrimson_desperate_eyejab";
    static final String CHOP = "kingcrimson_chop";
    static final String IMPALE = "kingcrimson_impale";
    static final String GROUND_SLAM = "kingcrimson_groundslam";
    private static final short SECOND_INPUT = 192;
    private KingCrimsonHeavyStrikeGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_heavy_block")
    public static void heavyStrikesExplodeTheirBlockTarget(GameTestHelper helper) {
        KingCrimsonGroundGameTestFixture.identity(helper, KingCrimsonHeavyStrikeGameTests.class);
        var checks = new KingCrimsonGroundGameTestFixture.Checks();
        for (String ability : List.of(GROUND_PUNCH, EYEJAB, CHOP)) {
            String id = "H1/block/" + ability;
            try (var scene = new KingCrimsonGroundGameTestFixture(helper, id, 1)) {
                prepare(scene, ability);
                scene.stand.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10);
                BlockPos block = BlockPos.containing(scene.origin).offset(0, 0, 2);
                scene.block(block, Blocks.DIRT.defaultBlockState());
                // A heavy strike pressed at a block keeps nothing and aims again (1.16 StandEntityHeavyAttack
                // .standKeepsTarget, StandEntity.punch): the user looks at the block, and the bystander stands beside
                // the struck face, inside the explosion's reach but off the Stand's precision ray along that aim.
                // A light strike keeps the block (StandEntityLightAttack.standKeepsTarget), so its bystander stays on
                // the ray, where a strike that aimed again would take it.
                if (heavy(ability)) scene.aimUserAt(Vec3.atCenterOf(block));
                var observer = scene.cow(Vec3.atCenterOf(block).add(heavy(ability) ? 2.0 : 1.2, -0.5, -0.6), 200);
                scene.premise(scene.offUserPrecisionRay(observer) == heavy(ability), "Bystander "
                        + (heavy(ability) ? "off" : "on") + " the precision ray, precision=" + scene.stand.getPrecision());
                ownSpawns(scene, Vec3.atCenterOf(block));
                float explosionDamage = StandEntityHeavyPunchAbility.calcExplosionDamage(scene.stand);
                scene.cast(new ActionTarget(block, Direction.NORTH), base(ability), ability);
                // 1.16 HeavyPunchBlockInstance: the blast is the Stand's explosion source, as the core heavy blast's
                var blast = scene.hits(observer).stream().filter(hit -> hit.source.is(ModDamageTypes.STAND_EXPLOSION)).toList();
                if (heavy(ability)) {
                    checks.check(blast.size() == 1 && KingCrimsonGroundGameTestFixture.near(blast.getFirst().amount, explosionDamage)
                                    && blast.getFirst().source.getDirectEntity() == scene.stand
                                    && ((DamageSourceModified) blast.getFirst().source).jojo_ripples$standPower() == scene.power,
                            id + " heavy block punch explosion hurt the bystander once for " + explosionDamage
                                    + " with a stand_explosion carrying the Stand power: "
                                    + scene.hits(observer).stream().map(hit -> hit.amount + " " + hit.source.getMsgId()).toList());
                    checks.check(scene.level.getBlockState(block).isAir(), id + " heavy block explosion destroyed the struck block");
                }
                else checks.check(scene.hits(observer).isEmpty(), id + " light block punch makes no explosion: " + scene.hits(observer).size());
            }
        }
        checks.finish(helper, "KC-HEAVY-BLOCK", 3);
    }

    // 1.16 HeavyPunchBlockInstance (StandEntityHeavyAttack :394-412): doHit ran the plain block punch
    // (StandEntity.breakBlock) before the blast, and playImpactSound was true, so StandEntity.attackTarget sounded the
    // strike's punchSound after the hit on any block that was not air, a zero-hardness one too.
    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_heavy_block_punch")
    public static void heavyStrikesPunchABlockBeforeTheBlastAndAlwaysSound(GameTestHelper helper) {
        KingCrimsonGroundGameTestFixture.identity(helper, KingCrimsonHeavyStrikeGameTests.class);
        var checks = new KingCrimsonGroundGameTestFixture.Checks();
        for (String ability : List.of(GROUND_PUNCH, EYEJAB)) {
            SoundEvent impact = ability.equals(GROUND_PUNCH) ? KingCrimsonAddon.KINGCRIMSON_GROUNDPUNCH.get()
                    : KingCrimsonAddon.KINGCRIMSON_DESPERATE_EYEJAB.get();
            for (BlockState state : List.of(Blocks.DIRT.defaultBlockState(), Blocks.SLIME_BLOCK.defaultBlockState())) {
                String id = "H5/" + ability + "/" + BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
                try (var scene = new KingCrimsonGroundGameTestFixture(helper, id, 1)) {
                    prepare(scene, ability);
                    scene.stand.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10);
                    BlockPos block = BlockPos.containing(scene.origin).offset(0, 0, 2);
                    scene.block(block, state);
                    scene.aimUserAt(Vec3.atCenterOf(block));
                    ownSpawns(scene, Vec3.atCenterOf(block));
                    List<String> order = new ArrayList<>();
                    scene.listen(ExplosionEvent.Detonate.class, event -> {
                        if (event.getLevel() == scene.level) {
                            order.add("blast:" + (scene.level.getBlockState(block).isAir() ? "air" : "block"));
                        }
                    });
                    scene.listen(PlayLevelSoundEvent.AtPosition.class, event -> {
                        if (event.getLevel() == scene.level && event.getSound() != null
                                && event.getSound().value() == impact) {
                            order.add("sound:" + (scene.level.getBlockState(block).isAir() ? "air" : "block"));
                        }
                    });
                    scene.cast(new ActionTarget(block, Direction.NORTH), base(ability), ability);
                    checks.check(order.equals(List.of("blast:air", "sound:air")), id + " 1.16 broke the block with the"
                            + " plain punch before the blast and sounded the strike once after both: " + order);
                }
            }
        }
        checks.finish(helper, "KC-HEAVY-BLOCK-PUNCH", 4);
    }

    // 1.16 KingCrimsonDesperateEyejab.punchEntity set impactSound(KINGCRIMSON_IMPALE) after copyProperties, so a hit on an
    // entity sounded the impale; the action's punchSound KINGCRIMSON_EYEJAB stayed for a block. Ground Punch sounds its
    // own for both (KingCrimsonGroundPunch.punchEntity impactSound(KINGCRIMSON_GROUNDPUNCH)).
    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_eyejab_sound")
    public static void desperateEyejabSoundsTheImpaleOnAnEntityAndItsOwnOnABlock(GameTestHelper helper) {
        KingCrimsonGroundGameTestFixture.identity(helper, KingCrimsonHeavyStrikeGameTests.class);
        var checks = new KingCrimsonGroundGameTestFixture.Checks();
        List<SoundEvent> strikeSounds = List.of(KingCrimsonAddon.KINGCRIMSON_IMPALE.get(),
                KingCrimsonAddon.KINGCRIMSON_DESPERATE_EYEJAB.get(), KingCrimsonAddon.KINGCRIMSON_GROUNDPUNCH.get());
        for (String ability : List.of(EYEJAB, GROUND_PUNCH)) {
            for (boolean onBlock : List.of(false, true)) {
                SoundEvent expected = ability.equals(GROUND_PUNCH) ? KingCrimsonAddon.KINGCRIMSON_GROUNDPUNCH.get()
                        : onBlock ? KingCrimsonAddon.KINGCRIMSON_DESPERATE_EYEJAB.get()
                        : KingCrimsonAddon.KINGCRIMSON_IMPALE.get();
                String id = "E1/" + ability + (onBlock ? "/block" : "/entity");
                try (var scene = new KingCrimsonGroundGameTestFixture(helper, id, 1)) {
                    prepare(scene, ability);
                    scene.stand.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10);
                    List<String> heard = new ArrayList<>();
                    scene.listen(PlayLevelSoundEvent.AtPosition.class, event -> {
                        if (event.getLevel() == scene.level && event.getSound() != null
                                && strikeSounds.contains(event.getSound().value())) {
                            heard.add(event.getSound().value().getLocation().getPath());
                        }
                    });
                    if (onBlock) {
                        BlockPos block = BlockPos.containing(scene.origin).offset(0, 0, 2);
                        scene.block(block, Blocks.DIRT.defaultBlockState());
                        scene.aimUserAt(Vec3.atCenterOf(block));
                        ownSpawns(scene, Vec3.atCenterOf(block));
                        scene.cast(new ActionTarget(block, Direction.NORTH), base(ability), ability);
                    }
                    else {
                        var cow = scene.cow(scene.origin.add(0, 0, 1.5), 200);
                        scene.cast(cow, base(ability), ability);
                    }
                    checks.check(heard.equals(List.of(expected.getLocation().getPath())),
                            id + " 1.16 sounded " + expected.getLocation().getPath() + " once; heard " + heard);
                }
            }
        }
        checks.finish(helper, "KC-EYEJAB-SOUND", 4);
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_heavy_hooks")
    public static void heavyStrikesArmImpactAndStandImmunity(GameTestHelper helper) {
        KingCrimsonGroundGameTestFixture.identity(helper, KingCrimsonHeavyStrikeGameTests.class);
        var checks = new KingCrimsonGroundGameTestFixture.Checks();
        for (String ability : List.of(GROUND_PUNCH, EYEJAB, CHOP)) {
            String id = "H2/living/" + ability;
            try (var scene = new KingCrimsonGroundGameTestFixture(helper, id, 1)) {
                prepare(scene, ability);
                scene.stand.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6);
                var cow = scene.cow(scene.origin.add(0, 0, 1.5), 200);
                scene.cast(cow, base(ability), ability);
                scene.premise(scene.hits(cow).size() == 1 && Boolean.TRUE.equals(scene.hits(cow).getFirst().result), "Actual hurt primary");
                var impact = KnockbackCollisionImpact.getExistingHandler(cow);
                boolean armed = impact != null && impact.isActive();
                boolean immune = StandEntity.isStandAttackInvulnerable(cow);
                if (heavy(ability)) {
                    checks.check(armed, id + " hurt target gets the heavy knockback collision impact");
                    if (armed) {
                        var saved = impact.serializeNBT(scene.level.registryAccess());
                        checks.check(KingCrimsonGroundGameTestFixture.near(saved.getFloat("ExplosionRadius"), radius(scene.stand))
                                        && KingCrimsonGroundGameTestFixture.near(saved.getDouble("Power"), cow.getDeltaMovement().length()),
                                id + " impact radius=" + saved.getFloat("ExplosionRadius") + " power=" + saved.getDouble("Power")
                                        + " sampled after the knockback=" + cow.getDeltaMovement().length());
                    }
                    checks.check(immune, id + " 10-tick Stand immunity follows the hit");
                    DamageSource followUp = new DamageSource(DamageUtil.type(scene.level, ModDamageTypes.STAND_ATTACK), scene.stand, scene.user);
                    checks.check(!scene.stand.hurtWithStandAttack(cow, followUp, 1), id + " an immediate follow-up Stand hit is refused");
                }
                else checks.check(!armed && !immune, id + " light strike arms no impact and leaves no Stand immunity");
            }
        }
        for (String ability : List.of(GROUND_PUNCH, EYEJAB)) {
            String id = "H2/nonliving/" + ability;
            try (var scene = new KingCrimsonGroundGameTestFixture(helper, id, 1)) {
                prepare(scene, ability);
                scene.stand.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6);
                var boat = scene.boat(scene.origin.add(0, 0, 1.5));
                scene.cast(boat, base(ability), ability);
                checks.check(scene.hits(boat).size() == 1 && Boolean.TRUE.equals(scene.hits(boat).getFirst().result),
                        id + " the strike hurts the boat: " + scene.hits(boat).size());
                var impact = KnockbackCollisionImpact.getExistingHandler(boat);
                checks.check(impact != null && impact.isActive() && KingCrimsonGroundGameTestFixture.near(
                                impact.serializeNBT(scene.level.registryAccess()).getFloat("ExplosionRadius"), radius(scene.stand)),
                        id + " hurt non-living target gets the heavy knockback collision impact");
            }
        }
        checks.finish(helper, "KC-HEAVY-HOOKS", 5);
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_heavy_barrage")
    public static void heavyStrikesStopAHurtStandsBarrage(GameTestHelper helper) {
        KingCrimsonGroundGameTestFixture.identity(helper, KingCrimsonHeavyStrikeGameTests.class);
        var checks = new KingCrimsonGroundGameTestFixture.Checks();
        for (String ability : List.of(GROUND_PUNCH, EYEJAB, CHOP)) {
            String id = "H3/barrage/" + ability;
            try (var fixture = new KingCrimsonGroundAdmissionFixture(helper, id, 1)) {
                var caster = fixture.actor(fixture.origin, fixture.origin, 0, 4, 40);
                var defender = fixture.actor(fixture.origin.add(0, 0, 6), fixture.origin.add(0, 0, 1.5), 180, 1, 16);
                defender.user().getAttribute(ModEntityAttributes.STAND_MAX_RANGE).setBaseValue(8);
                defender.user().getAttribute(ModEntityAttributes.STAND_EFFECTIVE_RANGE).setBaseValue(8);
                defender.user().getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
                StandEntity.updateStandStatAttributes(defender.stand(), defender.user());
                // Under the King Crimson barrage health gate of a high-Resolve user
                defender.user().setHealth(60);
                var barrage = fixture.press(defender, null, "barrage", "barrage", InputMethod.HOLD, KingCrimsonGroundAdmissionFixture.GUARD);
                for (int tick = 0; barrage != null && barrage.getPhase() == ActionPhase.WINDUP && tick < 40; tick++) barrage._tickAction();
                fixture.premise(barrage != null && barrage.getPhase() == ActionPhase.PERFORM
                        && LivingComponentAction.getCurEntityAction(defender.stand()) == barrage, "Defender barrage is running");
                int[] incoming = {0};
                fixture.listen(LivingIncomingDamageEvent.class, event -> {
                    if (event.getEntity() == defender.stand() && event.getSource().getDirectEntity() == caster.stand()) incoming[0]++;
                });
                if (ability.equals(EYEJAB)) caster.user().setHealth(60);
                caster.stand().setFinisherMeter(0.75F);
                EntityActionInstance strike = fixture.press(caster, defender.stand(), base(ability), ability, InputMethod.CLICK,
                        KingCrimsonGroundAdmissionFixture.ATTACK);
                for (int tick = 0; strike != null && strike.punchedTarget == null && !strike.isOver() && tick < 100; tick++) strike._tickAction();
                fixture.premise(strike != null && strike.punchedTarget != null && strike.punchedTarget.getMainEntity() == defender.stand()
                        && incoming[0] == 1, "Strike reached and hurt the barraging Stand");
                boolean stopped = LivingComponentAction.getCurEntityAction(defender.stand()) == barrage
                        && barrage.getPhase() == ActionPhase.RECOVERY;
                var impact = KnockbackCollisionImpact.getExistingHandler(defender.user());
                boolean armed = impact != null && impact.isActive();
                if (heavy(ability)) {
                    checks.check(stopped, id + " a heavy hit sends the hurt Stand's barrage into RECOVERY: " + barrage.getPhase());
                    checks.check(armed && KingCrimsonGroundGameTestFixture.near(
                                    impact.serializeNBT(fixture.level.registryAccess()).getFloat("ExplosionRadius"), radius(caster.stand())),
                            id + " the impact is armed on the hurt Stand's user");
                }
                else checks.check(!stopped && barrage.getPhase() == ActionPhase.PERFORM && !armed,
                        id + " a light hit leaves the barrage running and arms no impact");
            }
        }
        checks.finish(helper, "KC-HEAVY-BARRAGE", 3);
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_heavy_phases")
    public static void heavyStrikesUseHeavyPhasesSpeedAndStamina(GameTestHelper helper) {
        KingCrimsonGroundGameTestFixture.identity(helper, KingCrimsonHeavyStrikeGameTests.class);
        var checks = new KingCrimsonGroundGameTestFixture.Checks();
        for (String ability : List.of(GROUND_PUNCH, EYEJAB, CHOP, IMPALE, GROUND_SLAM)) {
            String id = "H4/phases/" + ability;
            try (var scene = new KingCrimsonGroundGameTestFixture(helper, id, 1)) {
                prepare(scene, ability);
                var cow = scene.cow(scene.origin.add(0, 0, 1.5), 200);
                float stamina = scene.power.getStamina();
                float lightPerform = StandStatFormulas.getLightAttackRecovery(scene.stand.getAttackSpeed(), 0.75F);
                EntityActionInstance action = scene.press(cow, base(ability), ability);
                // Owner rule of 2026-10-15: only a Stand's own guard slows its user; every strike walks at full speed.
                float walk = 1.0F;
                float perform = action.phasesLength.getFloat(ActionPhase.PERFORM);
                checks.check(action.getPhase() == ActionPhase.WINDUP && KingCrimsonGroundGameTestFixture.near(action.userWalkSpeed, walk),
                        id + " windup walk speed=" + action.userWalkSpeed + " expected=" + walk);
                checks.check(heavy(ability) ? perform == 1 : KingCrimsonGroundGameTestFixture.near(perform, lightPerform),
                        id + " PERFORM length=" + perform + (heavy(ability) ? " expected=1" : " expected light recovery=" + lightPerform));
                if (ability.equals(GROUND_PUNCH)) checks.check(KingCrimsonGroundGameTestFixture.near(scene.power.getStamina(), stamina),
                        id + " no stamina is taken before PERFORM: " + scene.power.getStamina());
                for (int tick = 0; action.getPhase() == ActionPhase.WINDUP && tick < 100; tick++) action._tickAction();
                scene.premise(action.getPhase() == ActionPhase.PERFORM && action.punchedTarget == null, "Reached PERFORM");
                checks.check(KingCrimsonGroundGameTestFixture.near(action.userWalkSpeed, walk), id + " perform walk speed=" + action.userWalkSpeed);
                action._tickAction();
                scene.premise(action.punchedTarget != null && action.punchedTarget.getMainEntity() == cow, "Performed on the cow");
                if (heavy(ability)) checks.check(action.getPhase() == ActionPhase.RECOVERY, id + " one PERFORM tick, then " + action.getPhase());
                for (int tick = 0; action.getPhase() == ActionPhase.PERFORM && tick < 100; tick++) action._tickAction();
                scene.premise(action.getPhase() == ActionPhase.RECOVERY, "Reached RECOVERY");
                checks.check(KingCrimsonGroundGameTestFixture.near(action.userWalkSpeed, 1.0F), id + " recovery walk speed=" + action.userWalkSpeed);
                Ability punch = scene.power.getAbility("punch");
                boolean free = action.canBeCancelledInto((EntityActionType) punch);
                // 1.16 StandEntityHeavyAttack.isFreeRecovery is isFinisher: Ground Punch is the finisher, Eye Jab is not.
                checks.check(free == !ability.equals(EYEJAB), id + " recovery cancellable into a punch=" + free);
                if (ability.equals(EYEJAB)) {
                    var selected = scene.power.updateAvailableMoves().getContextVariationContainer(punch);
                    BufferingState buffer = BufferingState.clickCanBuffer();
                    AbilityInput.keyPress(SECOND_INPUT, selected.ability, scene.user, null, InputMethod.CLICK, 0, buffer, punch.getAbilityId());
                    AbilityInput.keyRelease(SECOND_INPUT, scene.user);
                    checks.check(buffer.shouldBuffer && !buffer.isActionSuccess && scene.stand.getCurStandAction() == action,
                            id + " a punch pressed in the Eye Jab recovery waits in the buffer");
                }
                checks.check(KingCrimsonGroundGameTestFixture.near(scene.power.getStamina(), stamina - cost(ability)),
                        id + " PERFORM drained the 1.16 stamina cost " + cost(ability) + ": " + stamina + " -> "
                                + scene.power.getStamina());
            }
        }
        try (var scene = new KingCrimsonGroundGameTestFixture(helper, "H4/short-stamina/" + GROUND_PUNCH, 1)) {
            scene.power.setStamina(20);
            var cow = scene.cow(scene.origin.add(0, 0, 1.5), 200);
            scene.cast(cow, "heavy_punch", GROUND_PUNCH);
            checks.check(scene.hits(cow).size() == 1 && Boolean.TRUE.equals(scene.hits(cow).getFirst().result)
                            && KingCrimsonGroundGameTestFixture.near(scene.power.getStamina(), 0),
                    "H4/short-stamina the finisher is not refused and drains the shortfall: stamina=" + scene.power.getStamina());
        }
        for (String ability : List.of(CHOP, IMPALE, EYEJAB)) {
            for (float start : new float[] {150, 300}) {
                String id = "H4/cost/" + ability + "/" + (int) start;
                try (var scene = new KingCrimsonGroundGameTestFixture(helper, id, 1)) {
                    prepare(scene, ability);
                    scene.power.setStamina(start);
                    var cow = scene.cow(scene.origin.add(0, 0, 1.5), 200);
                    scene.cast(cow, base(ability), ability);
                    checks.check(KingCrimsonGroundGameTestFixture.near(scene.power.getStamina(), Math.max(start - cost(ability), 0)),
                            id + " PERFORM paid the 1.16 staminaCost " + cost(ability) + ": " + start + " -> "
                                    + scene.power.getStamina());
                }
            }
        }
        checks.finish(helper, "KC-HEAVY-PHASES", 12);
    }

    // 1.16 InitStands: Chop, Impale and Eye Jab staminaCost(200); Ground Slam keeps the light-attack builder's 10,
    // Ground Punch the heavy-attack builder's 50.
    static float cost(String ability) {
        return ability.equals(GROUND_PUNCH) ? 50 : ability.equals(GROUND_SLAM) ? 10 : 200;
    }

    static String base(String ability) {
        return ability.equals(CHOP) || ability.equals(IMPALE) ? ability : "heavy_punch";
    }

    static boolean heavy(String ability) {
        return ability.equals(GROUND_PUNCH) || ability.equals(EYEJAB);
    }

    /** Arms-only for Ground Slam, low health for Eye Jab, so the registered inputs select them. */
    static void prepare(KingCrimsonGroundGameTestFixture scene, String ability) {
        if (ability.equals(GROUND_SLAM)) scene.stand.setArmsOnlyMode(true, true);
        if (ability.equals(EYEJAB)) scene.user.setHealth(60);
    }

    private static float radius(StandEntity stand) {
        return Math.max(StandEntityHeavyPunchAbility.calcExplosionRadius(stand) - 0.5F, 0);
    }

    private static void ownSpawns(KingCrimsonGroundGameTestFixture scene, Vec3 center) {
        scene.listen(EntityJoinLevelEvent.class, event -> {
            Entity entity = event.getEntity();
            if (event.getLevel() == scene.level && !(entity instanceof LivingEntity) && entity.position().distanceTo(center) < 6) {
                scene.owned.add(entity);
            }
        });
    }
}
