package rotp.addons.cmoon.gametest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.cmoon.CMoonAddon;
import rotp.addons.cmoon.CMoonEvents;
import rotp.addons.cmoon.entity.CMoonStandEntity;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.DamageUtil;

/**
 * 1.16 GravityCounter.EventForAbility: a LOW LivingHurtEvent listener. The hit gets there past the hurt cooldown,
 * lands at 0, stuns the damage source's entity itself and plays the counter sound to everyone nearby.
 */
@GameTestHolder(CMoonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CMoonGravityCounterGameTests {
    private static final short KEY = 7695;
    private static final short HOLD_KEY = 7696;
    private static final TicketType<UUID> TICKET = TicketType.create(
            "cmoon_gravity_counter", Comparator.<UUID>naturalOrder());
    // ServerPlayer.tick counts its 60-tick spawn protection down first
    private static final int SPAWN_PROTECTION_TICKS = 70;

    private CMoonGravityCounterGameTests() {}

    @GameTest(template = "empty", batch = "cmCounterLanded", timeoutTicks = 200)
    public static void counteredStandHitLandsAtZeroStunsTheStandAndIsHeardByTheDefender(GameTestHelper helper) {
        GravityTestSupport.identity(helper, CMoonEvents.class, CMoonGravityCounterGameTests.class);
        Fixture fixture = new Fixture(helper);
        try {
            RecordingServerPlayer recorded = fixture.recorded("cm-counter-defender", fixture.origin);
            LivingEntity defender = recorded.player;
            CMoonStandEntity defenderStand = fixture.grant(defender);
            Player attacker = fixture.plainPlayer("cm-counter-attacker", fixture.origin.add(4, 0, 0));
            CMoonStandEntity attackerStand = fixture.grant(attacker);
            int[] ticks = {0};
            helper.onEachTick(() -> {
                if (fixture.closed || ++ticks[0] < SPAWN_PROTECTION_TICKS) return;
                fixture.armCounter(defender, defenderStand);
                recorded.clear();
                float health = defender.getHealth();
                boolean hurt = defender.hurt(fixture.standHit(attackerStand), 4.0F);

                fixture.check(hurt && defender.getHealth() == health && defender.invulnerableTime == 20
                                && defender.lastHurt == 4.0F && defender.hurtTime == 10,
                        "1.16 setAmount(0) inside actuallyHurt: the countered hit lands at 0 with the full hurt cooldown"
                                + " (hurt=" + hurt + " health " + health + "->" + defender.getHealth()
                                + " cooldown=" + defender.invulnerableTime + " lastHurt=" + defender.lastHurt
                                + " hurtTime=" + defender.hurtTime + ")");
                fixture.check(!defenderStand.isCounterActive(), "the counter was not used up by the hit");
                MobEffectInstance standStun = attackerStand.getEffect(ModStatusEffects.STUN);
                fixture.check(standStun != null && standStun.getDuration() == 75 && standStun.getAmplifier() == 1,
                        "1.16 stuns the damage source's entity, here the attacking Stand: " + standStun);
                fixture.check(!attacker.hasEffect(ModStatusEffects.STUN),
                        "1.16 leaves the attacking Stand's user free; it was stunned");
                List<ClientboundSoundPacket> sounds = recorded.sounds(CMoonAddon.C_MOON_GRAVITY_COUNTER_SOUND.get());
                // the packet reads its position back through a float: blocks apart this far out
                Vec3 at = Vec3.atCenterOf(defender.blockPosition());
                fixture.check(sounds.size() == 1 && sounds.get(0).getSource() == SoundSource.PLAYERS
                                && sounds.get(0).getVolume() == 1.0F && sounds.get(0).getPitch() == 1.0F
                                && at.distanceTo(new Vec3(sounds.get(0).getX(), sounds.get(0).getY(),
                                        sounds.get(0).getZ())) < 2.0D,
                        "1.16 playSound(null, blockPosition, ...): the countering player hears the counter sound;"
                                + " packets to them: " + sounds.stream().map(sound -> sound.getSource() + " "
                                        + sound.getVolume() + "/" + sound.getPitch() + " at " + sound.getX() + ","
                                        + sound.getY() + "," + sound.getZ()).toList() + ", defender at " + at);
                fixture.finish("countered Stand hit");
            });
        }
        catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    /**
     * 1.16 GravityCounter cancelled the hit at LivingHurtEvent, so LivingDamageEvent never fired for it: the
     * defender's Resolve bookkeeping (ResolveCounter.onGettingAttacked) and the stop of its held actions
     * (PowerBaseImpl.onUserGettingAttacked) never saw it.
     */
    @GameTest(template = "empty", batch = "cmCounterUnseen", timeoutTicks = 200)
    public static void counteredHitRecordsNoResolveAndStopsNoHeldAction(GameTestHelper helper) {
        GravityTestSupport.identity(helper, CMoonEvents.class, CMoonGravityCounterGameTests.class);
        Fixture fixture = new Fixture(helper);
        try {
            RecordingServerPlayer recorded = fixture.recorded("cm-counter-defender", fixture.origin);
            ServerPlayer defender = recorded.player;
            CMoonStandEntity defenderStand = fixture.grant(defender);
            // a mob: the test server allows no PvP
            Pig attacker = EntityType.PIG.create(fixture.level);
            helper.assertTrue(attacker != null, "Premise: no attacker");
            attacker.setNoAi(true);
            attacker.setNoGravity(true);
            attacker.moveTo(fixture.origin.x + 4.0D, fixture.origin.y, fixture.origin.z, 90, 0);
            helper.assertTrue(fixture.level.addFreshEntity(attacker), "Premise: attacker added");
            fixture.onClose(attacker::discard);
            Cow prey = EntityType.COW.create(fixture.level);
            helper.assertTrue(prey != null, "Premise: no prey");
            prey.setNoAi(true);
            prey.setNoGravity(true);
            prey.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 1.5D, 180, 0);
            helper.assertTrue(fixture.level.addFreshEntity(prey), "Premise: prey added");
            fixture.onClose(prey::discard);
            // the armour cut is what lets a hit zeroed at LivingDamageEvent.Pre still stop a hold at Post
            defender.getAttribute(Attributes.ARMOR).setBaseValue(10.0D);
            int[] ticks = {0};
            helper.onEachTick(() -> {
                if (fixture.closed || ++ticks[0] < SPAWN_PROTECTION_TICKS) return;
                StandPower stand = StandPower.get(defender);
                helper.assertTrue(stand.usesResolve() && !stand.resolveCounter.hpOnGettingAttacked.isPresent(),
                        "Premise: C-Moon uses Resolve and has no bookkeeping yet");
                fixture.armCounter(defender, defenderStand);

                PlayerPower vampirism = PowerClass.PLAYER_POWER.attachGet(defender);
                vampirism.setPowerType(ModPlayerPowers.VAMPIRISM.get());
                Ability drain = vampirism.getAbility("vampirism_blood_drain");
                LivingComponentAction component = LivingComponentAction.getComponent(defender);
                component.entityAim.setTarget(new ActionTarget(prey));
                helper.assertTrue(drain != null && drain.checkConditions(vampirism).isPositive(),
                        "Premise: Blood Drain is usable on the prey");
                fixture.keyUsers.add(defender);
                HeldInputEntry entry = AbilityInput.keyPress(HOLD_KEY, drain, defender, null, InputMethod.HOLD,
                        0.0F, BufferingState.clickOnly(), drain.getAbilityId());
                EntityActionInstance hold = component.getAction();
                fixture.onClose(() -> {
                    AbilityInput.keyRelease(HOLD_KEY, defender);
                    component.entityAim.setTarget(ActionTarget.EMPTY);
                    component.setAction(null, defender, SyncType.NO_SYNC);
                });
                helper.assertTrue(entry != null && hold != null && entry.action == hold
                        && hold.getPhase() == ActionPhase.PERFORM, "Premise: the Blood Drain hold started");

                float health = defender.getHealth();
                boolean hurt = defender.hurt(defender.damageSources().mobAttack(attacker), 10.0F);
                fixture.check(hurt && defender.getHealth() == health && !defenderStand.isCounterActive(),
                        "Premise: the hit was countered (hurt=" + hurt + " health " + health + "->"
                                + defender.getHealth() + " armed=" + defenderStand.isCounterActive() + ")");
                fixture.check(!stand.resolveCounter.hpOnGettingAttacked.isPresent(),
                        "1.16 a countered hit never reached the Resolve bookkeeping: hpOnGettingAttacked="
                                + stand.resolveCounter.hpOnGettingAttacked);
                fixture.check(component.getAction() == hold && !hold.isOver(),
                        "1.16 a countered hit never stopped a held action: over=" + hold.isOver()
                                + " current=" + (component.getAction() == hold));
                fixture.finish("countered hit unseen");
            });
        }
        catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    // Stand attacks bypass the hurt cooldown (1.16 hurtThroughInvulTicks), so the gate is shown with a player's hit
    @GameTest(template = "empty", batch = "cmCounterHurtCooldown", timeoutTicks = 100)
    public static void hurtCooldownRunsBeforeTheCounterAndAStrongerHitIsCountered(GameTestHelper helper) {
        GravityTestSupport.identity(helper, CMoonEvents.class, CMoonGravityCounterGameTests.class);
        Fixture fixture = new Fixture(helper);
        try {
            Player defender = fixture.plainPlayer("cm-counter-defender", fixture.origin);
            CMoonStandEntity defenderStand = fixture.grant(defender);
            Player attacker = fixture.plainPlayer("cm-counter-attacker", fixture.origin.add(4, 0, 0));
            int[] ticks = {0};
            helper.onEachTick(() -> {
                if (fixture.closed || ++ticks[0] < 3) return;
                fixture.armCounter(defender, defenderStand);
                float health = defender.getHealth();

                boolean ownStand = defender.hurt(fixture.standHit(defenderStand), 5.0F);
                fixture.check(ownStand && defender.getHealth() == health - 5.0F && defenderStand.isCounterActive()
                                && !defenderStand.hasEffect(ModStatusEffects.STUN),
                        "a hit from the defender's own Stand passes and the counter stays armed (hurt=" + ownStand
                                + " health " + health + "->" + defender.getHealth() + ")");

                boolean attackerless = defender.hurt(defender.damageSources().generic(), 7.0F);
                fixture.check(attackerless && defender.getHealth() == health - 7.0F && defenderStand.isCounterActive(),
                        "1.16: damage with no attacker passes and the counter stays armed (hurt=" + attackerless
                                + " health " + health + "->" + defender.getHealth() + ")");

                boolean refused = defender.hurt(defender.damageSources().playerAttack(attacker), 6.0F);
                fixture.check(!refused && defenderStand.isCounterActive() && !attacker.hasEffect(ModStatusEffects.STUN),
                        "1.16: a hit no stronger than the last one inside the hurt cooldown never reaches the counter"
                                + " (hurt=" + refused + " armed=" + defenderStand.isCounterActive() + ")");

                float before = defender.getHealth();
                boolean stronger = defender.hurt(defender.damageSources().playerAttack(attacker), 10.0F);
                fixture.check(stronger && defender.getHealth() == before && defender.lastHurt == 10.0F
                                && !defenderStand.isCounterActive() && attacker.hasEffect(ModStatusEffects.STUN),
                        "1.16: a stronger hit inside the hurt cooldown is countered at 0 and still counts as landed"
                                + " (hurt=" + stronger + " health " + before + "->" + defender.getHealth()
                                + " lastHurt=" + defender.lastHurt + ")");
                fixture.finish("hurt cooldown order");
            });
        }
        catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    private static final class Fixture implements GameTestListener, AutoCloseable {
        final GameTestHelper helper;
        final ServerLevel level;
        final Vec3 origin;
        final UUID ticketId = UUID.randomUUID();
        final List<ChunkPos> chunks = new ArrayList<>();
        final List<Player> plainPlayers = new ArrayList<>();
        final List<StandPower> powers = new ArrayList<>();
        final List<LivingEntity> keyUsers = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        final List<Runnable> closers = new ArrayList<>();
        RecordingServerPlayer recorded;
        boolean closed;

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            level = helper.getLevel();
            origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 3, 1)));
            helper.testInfo.addListener(this);
            ChunkPos centre = new ChunkPos(BlockPos.containing(origin));
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    ChunkPos chunk = new ChunkPos(centre.x + x, centre.z + z);
                    level.getChunkSource().addRegionTicket(TICKET, chunk, 2, ticketId);
                    chunks.add(chunk);
                }
            }
        }

        RecordingServerPlayer recorded(String name, Vec3 pos) {
            recorded = new RecordingServerPlayer(helper, name, pos);
            prepare(recorded.player);
            return recorded;
        }

        Player plainPlayer(String name, Vec3 pos) {
            Player player = new Player(level, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), name)) {
                @Override public boolean isSpectator() { return false; }
                @Override public boolean isCreative() { return false; }
            };
            plainPlayers.add(player);
            player.moveTo(pos.x, pos.y, pos.z, 0, 0);
            prepare(player);
            helper.assertTrue(level.addFreshEntity(player), "Premise: native player added");
            return player;
        }

        private static void prepare(Player player) {
            player.setNoGravity(true);
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
            player.setHealth(200);
        }

        CMoonStandEntity grant(LivingEntity user) {
            StandPower power = PowerClass.STAND.attachGet(user);
            powers.add(power);
            helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(CMoonAddon.C_MOON.get())).status()
                            == StandPowerTransitions.Status.APPLIED && CMoonAddon.C_MOON.get().summon(user, power)
                            && power.getSummonedStandEntity() instanceof CMoonStandEntity,
                    "Premise: registered C-Moon granted and summoned");
            CMoonStandEntity stand = (CMoonStandEntity) power.getSummonedStandEntity();
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
            stand.setNoGravity(true);
            power.setStamina(power.getMaxStamina());
            return stand;
        }

        void armCounter(LivingEntity user, CMoonStandEntity stand) {
            StandPower power = StandPower.get(user);
            power.setStamina(power.getMaxStamina());
            Ability counter = power.getAbility(CMoonAddon.GRAVITY_COUNTER);
            var checked = power.updateAvailableMoves().getContextVariationContainer(counter);
            helper.assertTrue(checked != null && AbilityInput.withConditionCheck(checked, user, InputMethod.CLICK),
                    "Premise: the registered counter input was refused");
            BufferingState buffering = BufferingState.clickOnly();
            keyUsers.add(user);
            AbilityInput.keyPress(KEY, checked.ability, user, null, InputMethod.CLICK, 0, buffering,
                    counter.getAbilityId());
            AbilityInput.keyRelease(KEY, user);
            helper.assertTrue(buffering.isActionSuccess && stand.isCounterActive(),
                    "Premise: the registered counter did not arm");
        }

        DamageSource standHit(StandEntity stand) {
            return new DamageSource(DamageUtil.type(level, ModDamageTypes.STAND_ATTACK), stand);
        }

        void check(boolean passed, String message) {
            if (!passed) failures.add(message);
        }

        /** Runs when the fixture closes, before the Stands and players go. */
        void onClose(Runnable closer) {
            closers.add(closer);
        }

        void finish(String scene) {
            close();
            helper.assertTrue(failures.isEmpty(), "CM-COUNTER " + scene + " failed: " + failures);
            helper.succeed();
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            closers.forEach(Runnable::run);
            for (LivingEntity user : keyUsers) {
                AbilityInput.keyRelease(KEY, user);
            }
            for (StandPower power : powers) {
                if (power.isSummoned() && power.getUser() != null) {
                    CMoonAddon.C_MOON.get().forceUnsummon(power.getUser(), power);
                }
            }
            plainPlayers.forEach(Player::discard);
            if (recorded != null) recorded.close();
            for (ChunkPos chunk : chunks) {
                level.getChunkSource().removeRegionTicket(TICKET, chunk, 2, ticketId);
            }
        }

        @Override public void testStructureLoaded(GameTestInfo info) {}
        @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo oldInfo, GameTestInfo newInfo, GameTestRunner runner) { close(); }
    }
}
