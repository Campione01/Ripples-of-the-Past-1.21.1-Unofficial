package rotp.addons.planetwaves;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.planetwaves.ability.MeteorAttractionAbility;
import rotp.addons.planetwaves.entity.MeteoriteEntity;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * 1.16 PlanetWaves*MeteoriteShower.standPerform scheduled the shower's three meteors at its first perform tick, off the
 * Stand's task (0, 240 and 480 ms), and the task performed for that one tick: a punch pressed right after the shower
 * fired went through at once, and none of the three meteors was lost. The port calls them on the perform's ticks 0, 5
 * and 10.
 */
@GameTestHolder(PlanetWavesAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlanetWavesMeteorShowerGameTests {
    private static final short SHOWER_KEY = 127;
    private static final short PUNCH_KEY = 128;
    private static final List<Long> SHOWER_TICKS = List.of(0L, 5L, 10L);

    private PlanetWavesMeteorShowerGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void aPunchAfterTheShowerFiredLosesNoMeteorAsIn116(GameTestHelper helper) {
        new ShowerScene(helper, "PwShowerPunch", true).start();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void anUncutShowerCallsEachMeteorOnce(GameTestHelper helper) {
        new ShowerScene(helper, "PwShowerUncut", false).start();
    }

    private static final class ShowerScene {
        final GameTestHelper helper;
        final String name;
        final boolean punch;
        final List<Long> meteorTicks = new ArrayList<>();
        final List<MeteoriteEntity> meteors = new ArrayList<>();
        Consumer<EntityJoinLevelEvent> listener;
        PlanetWavesChunkTickets chunks;
        FakePlayer owner;
        StandPower power;
        StandEntity stand;
        EntityActionInstance shower;
        int ticks;
        boolean released;
        long fired = -1L;
        boolean closed;

        ShowerScene(GameTestHelper helper, String name, boolean punch) {
            this.helper = helper;
            this.name = name;
            this.punch = punch;
        }

        void start() {
            try {
                setUp();
                helper.onEachTick(this::tick);
            }
            catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        void setUp() {
            // the user, its Stand beside it, and the shower's meteors 2.5 blocks around the Stand; the Stand only
            // performs in a chunk that ticks entities
            chunks = new PlanetWavesChunkTickets(helper).hold(
                    helper.absoluteVec(new Vec3(-3.0D, 0.0D, -3.0D)), helper.absoluteVec(new Vec3(6.0D, 0.0D, 6.0D)));
            owner = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
            owner.setPos(helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D)));
            helper.getLevel().addNewPlayer(owner);
            power = PowerClass.STAND.attachGet(owner);
            power.setStand(PlanetWavesAddon.PLANET_WAVES.get());
            power.setResolveLevel(4);
            helper.assertTrue(PlanetWavesAddon.PLANET_WAVES.get().summon(owner, power), "summon failed");
            stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "no summoned Planet Waves");
            stand.summonLockTicks = 0;
            power.setStamina(power.getMaxStamina());
            listener = event -> {
                if (!closed && !event.getLevel().isClientSide()
                        && event.getEntity() instanceof MeteoriteEntity meteor
                        && meteor.resolveController() == stand) {
                    meteors.add(meteor);
                    meteorTicks.add(event.getLevel().getGameTime());
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, listener);
        }

        void tick() {
            if (closed) {
                return;
            }
            try {
                ticks++;
                if (shower == null) {
                    if (ticks >= 3) {
                        BufferingState state = press("vertical_shower", InputMethod.HOLD, SHOWER_KEY);
                        shower = stand.getCurStandAction();
                        helper.assertTrue(state.isActionSuccess && shower != null
                                        && shower.ability instanceof MeteorAttractionAbility meteor && meteor.shower()
                                        && shower.getPhase() == ActionPhase.BUTTON_CHARGE,
                                "setup: the held key did not start the vertical shower's charge: " + shower);
                    }
                }
                else if (!released) {
                    if (shower.getPhase() == ActionPhase.BUTTON_CHARGE
                            && shower.getPhaseTick() >= shower.getCurPhaseLength()) {
                        AbilityInput.keyRelease(SHOWER_KEY, owner);
                        released = true;
                    }
                    helper.assertTrue(ticks < 60, "setup: the shower's charge did not fill: " + shower.getPhase()
                            + " " + shower.getPhaseTick());
                }
                else if (fired < 0L) {
                    if (!meteorTicks.isEmpty()) {
                        fired = meteorTicks.get(0);
                        if (punch) {
                            pressPunch();
                        }
                    }
                    helper.assertTrue(ticks < 80, "setup: the released shower called no meteor: " + shower.getPhase());
                }
                else if (helper.getLevel().getGameTime() >= fired + 12L) {
                    List<Long> offsets = new ArrayList<>();
                    for (long tick : meteorTicks) {
                        offsets.add(tick - fired);
                    }
                    helper.assertTrue(offsets.equals(SHOWER_TICKS),
                            (punch
                                    ? "1.16 scheduled the shower's three meteors when it fired, so a punch pressed right"
                                            + " after lost none of them"
                                    : "the shower calls each of its three meteors once")
                                    + ": expected meteors at the perform's ticks " + SHOWER_TICKS + ", the port called "
                                    + meteorTicks.size() + " at " + offsets);
                    close();
                    helper.succeed();
                }
            }
            catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        // 1.16: the task's one-tick perform was over, so a punch went through at once (K5-21 E in the port).
        void pressPunch() {
            helper.assertTrue(stand.getCurStandAction() == shower && shower.getPhase() == ActionPhase.PERFORM
                            && shower.getPhaseTick() >= 1.0F,
                    "setup: the shower is not past its perform's first tick: " + shower.getPhase() + " "
                            + shower.getPhaseTick());
            BufferingState state = press("punch", InputMethod.CLICK, PUNCH_KEY);
            EntityActionInstance now = stand.getCurStandAction();
            helper.assertTrue(state.isActionSuccess && now != null && now != shower
                            && now.ability == power.getAbility("punch") && shower.isOver(),
                    "setup: the punch pressed after the shower fired did not replace it at once: started "
                            + state.isActionSuccess + ", buffered " + state.shouldBuffer + ", the Stand's action " + now);
        }

        // the key's resolved ability, its condition check, then the press, as ClAbilityInputPacket does it
        BufferingState press(String ability, InputMethod inputMethod, short key) {
            BufferingState state = BufferingState.clickCanBuffer();
            Ability base = power.getAbility(ability);
            helper.assertTrue(base != null, "Missing ability " + ability);
            AbilityConditionCheck container = resolve(base);
            helper.assertTrue(container != null && AbilityInput.withConditionCheck(container, owner, inputMethod),
                    "setup: " + ability + " was refused");
            AbilityInput.keyPress(key, container.ability, owner, null, inputMethod, 0, state, base.getAbilityId());
            return state;
        }

        // the power resolves its keys once a tick (Power.updateAvailableMoves), and its user does not tick here
        AbilityConditionCheck resolve(Ability base) {
            try {
                Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
                cached.setAccessible(true);
                cached.setBoolean(power, false);
            }
            catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
            }
            return power.updateAvailableMoves().getContextVariationContainer(base);
        }

        void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (listener != null) {
                NeoForge.EVENT_BUS.unregister(listener);
            }
            meteors.forEach(MeteoriteEntity::discard);
            if (owner != null) {
                AbilityInput.keyRelease(SHOWER_KEY, owner);
                AbilityInput.keyRelease(PUNCH_KEY, owner);
                owner.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
                if (power != null && power.isSummoned()) {
                    PlanetWavesAddon.PLANET_WAVES.get().forceUnsummon(owner, power);
                }
                EventHooks.firePlayerLoggedOut(owner);
                owner.discard();
            }
            if (chunks != null) {
                chunks.release();
            }
        }
    }
}
