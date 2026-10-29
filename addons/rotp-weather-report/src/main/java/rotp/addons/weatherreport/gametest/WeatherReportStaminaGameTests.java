package rotp.addons.weatherreport.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.channel.embedded.EmbeddedChannel;
import org.slf4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import rotp.addons.weatherreport.WeatherReportAddon;
import rotp.addons.weatherreport.entity.WeatherReportIcicleEntity;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;

/**
 * 1.16 Weather Report had no stamina condition of its own: StandAction.onPerform and StandEntityTask paid every cost
 * and every ticking cost with the result ignored, and StandPower.consumeStamina drained a short bar to 0. Each case
 * presses the ability the way ClAbilityInputPacket does on a bar too short for its cost.
 */
@GameTestHolder("rotp_wr_reach")
@PrefixGameTestTemplate(false)
public final class WeatherReportStaminaGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final short KEY = 47;
    private static final int TIMEOUT_TICKS = 200;
    private static final int DEADLINE_TICK = 180;

    private WeatherReportStaminaGameTests() {}

    @GameTest(template = "empty", batch = "wr_stamina_icicle", timeoutTicks = TIMEOUT_TICKS)
    public static void icicleStrikeFiresOnAShortBarAndDrainsIt(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, "icicle");
        float[] atSpawn = {-1.0F};
        float[] afterPerform = {-1.0F};
        fixture.run(() -> {
            fixture.listen(EntityJoinLevelEvent.class, event -> {
                if (atSpawn[0] < 0.0F && event.getEntity() instanceof WeatherReportIcicleEntity icicle
                        && icicle.getOwner() == fixture.stand) {
                    atSpawn[0] = fixture.power.getStamina();
                }
            });
            fixture.watchStand(() -> atSpawn[0] >= 0.0F, afterPerform);
            fixture.power.setStamina(100.0F);
            fixture.press(WeatherReportAddon.ICICLE_STRIKE_NAME, InputMethod.CLICK);
        }, () -> {
            if (afterPerform[0] < 0.0F) {
                return false;
            }
            fixture.check(atSpawn[0] == 100.0F,
                    "the icicle must be out before the cost is paid: stamina at its spawn " + atSpawn[0]);
            fixture.check(afterPerform[0] == 0.0F,
                    "the 375 cost drains a 100 bar to 0, the bar held " + afterPerform[0]);
            return true;
        });
    }

    @GameTest(template = "empty", batch = "wr_stamina_give_shield", timeoutTicks = TIMEOUT_TICKS)
    public static void givenCloudShieldLandsOnAShortBarAndDrainsIt(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, "give_shield");
        float[] atShield = {-1.0F};
        fixture.run(() -> {
            Cow cow = fixture.cow(3.0D);
            // ClAimTargetPacket stores the client's target on the user and the Stand.
            LivingComponentAction.getComponent(fixture.user).entityAim.setTarget(new ActionTarget(cow));
            LivingComponentAction.getComponent(fixture.stand).entityAim.setTarget(new ActionTarget(cow));
            fixture.listen(MobEffectEvent.Added.class, event -> {
                if (atShield[0] < 0.0F && event.getEntity() == cow
                        && event.getEffectInstance().is(WeatherReportAddon.CLOUD_SHIELD_EFFECT)) {
                    atShield[0] = fixture.power.getStamina();
                }
            });
            fixture.power.setStamina(100.0F);
            fixture.press(WeatherReportAddon.GIVE_CLOUD_SHIELD_NAME, InputMethod.CLICK);
        }, () -> {
            if (atShield[0] < 0.0F) {
                return false;
            }
            fixture.check(atShield[0] == 0.0F,
                    "the 400 cost drains a 100 bar to 0 before the shield is given, the bar held " + atShield[0]);
            return true;
        });
    }

    @GameTest(template = "empty", batch = "wr_stamina_lightning", timeoutTicks = TIMEOUT_TICKS)
    public static void lightningStrikesOnAShortBarAndDrainsIt(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, "lightning");
        float[] atBolt = {-1.0F};
        fixture.run(() -> {
            fixture.listen(EntityJoinLevelEvent.class, event -> {
                if (event.getEntity() instanceof LightningBolt && event.getLevel() == fixture.level
                        && event.getEntity().distanceToSqr(fixture.user) < 128.0D * 128.0D && atBolt[0] < 0.0F) {
                    atBolt[0] = fixture.power.getStamina();
                    // The strike itself is not under test; it would set the test cell on fire.
                    event.setCanceled(true);
                }
            });
            fixture.power.setStamina(50.0F);
            fixture.press(WeatherReportAddon.LIGHTNING_NAME, InputMethod.HOLD);
        }, () -> {
            if (atBolt[0] < 0.0F) {
                return false;
            }
            fixture.check(atBolt[0] == 0.0F,
                    "the 75 cost drains a 50 bar to 0 before the bolt falls, the bar held " + atBolt[0]);
            return true;
        });
    }

    @GameTest(template = "empty", batch = "wr_stamina_change_weather", timeoutTicks = TIMEOUT_TICKS)
    public static void weatherChangeOpensOnAShortBarAndDrainsIt(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, "change_weather");
        float[] afterPerform = {-1.0F};
        fixture.run(() -> {
            fixture.watchStand(() -> fixture.power.getAbilityCooldown(WeatherReportAddon.CHANGE_WEATHER_NAME) > 0,
                    afterPerform);
            fixture.power.setStamina(50.0F);
            fixture.press(WeatherReportAddon.CHANGE_WEATHER_NAME, InputMethod.HOLD);
        }, () -> {
            if (afterPerform[0] < 0.0F) {
                return false;
            }
            fixture.check(afterPerform[0] == 0.0F,
                    "the 75 cost drains a 50 bar to 0 when the selection opens, the bar held " + afterPerform[0]);
            return true;
        });
    }

    @GameTest(template = "empty", batch = "wr_stamina_wind", timeoutTicks = TIMEOUT_TICKS)
    public static void windKeepsBlowingOnAnEmptyBar(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, "wind");
        EntityActionInstance[] blowing = {null};
        int[] heldTicks = {0};
        fixture.run(() -> {
            fixture.power.setStamina(0.0F);
            fixture.press(WeatherReportAddon.WIND_NAME, InputMethod.HOLD);
            blowing[0] = fixture.stand.getCurStandAction();
            fixture.check(blowing[0] != null, "the wind did not start on an empty bar");
        }, () -> {
            EntityActionInstance action = fixture.stand.getCurStandAction();
            fixture.check(action == blowing[0] && action != null && action.getPhase() == ActionPhase.PERFORM,
                    "1.16 StandEntityTask ignored the failed ticking cost, so the held wind goes on on an empty"
                            + " bar; after " + heldTicks[0] + " held ticks the action is "
                            + (action == null ? "gone" : action.getPhase()));
            fixture.check(fixture.power.getStamina() == 0.0F,
                    "the empty bar held " + fixture.power.getStamina() + " while the wind blew");
            return ++heldTicks[0] >= 10 || !fixture.failures.isEmpty();
        });
    }

    /** A logged-in Weather Report user in the template's entity-ticking chunk, the Stand out and every ability open. */
    private static final class Fixture {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final String id;
        private final List<Runnable> cleanups = new ArrayList<>();
        private final List<String> failures = new ArrayList<>();
        private ServerPlayer user;
        private EmbeddedChannel channel;
        private StandPower power;
        private StandEntity stand;
        private Cow cow;
        private BlockPos userCell;
        private boolean pressed;
        private boolean closed;

        private Fixture(GameTestHelper helper, String id) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.id = id;
        }

        private void run(Runnable press, BooleanSupplier observe) {
            try {
                setUp();
            }
            catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
            helper.onEachTick(() -> {
                if (closed) {
                    return;
                }
                try {
                    helper.assertTrue(helper.getTick() < DEADLINE_TICK, "Timed out: the " + id + " case never"
                            + " performed (stamina " + power.getStamina() + ", action " + stand.getCurStandAction()
                            + ")");
                    if (!pressed) {
                        pressed = true;
                        press.run();
                        return;
                    }
                    if (observe.getAsBoolean()) {
                        close();
                        helper.assertTrue(failures.isEmpty(), "WR-STAMINA " + id + " failed: " + failures);
                        LOGGER.info("R769_WR_STAMINA_PASS {} ", id);
                        helper.succeed();
                    }
                }
                catch (RuntimeException | Error failure) {
                    close();
                    throw failure;
                }
            });
        }

        private void setUp() {
            BlockPos template = helper.absolutePos(new BlockPos(0, 2, 0));
            ChunkPos chunk = new ChunkPos(template);
            // GameTest keeps only the template's own chunk entity-ticking, so everything stays inside it.
            userCell = new BlockPos(chunk.getMinBlockX() + 8, template.getY() + 2, chunk.getMinBlockZ() + 2);
            helper.assertTrue(level.isPositionEntityTicking(userCell)
                            && level.isPositionEntityTicking(userCell.offset(0, 0, 6)),
                    "The stamina fixture is outside the entity-ticking test chunk");
            GameProfile profile = new GameProfile(UUID.randomUUID(), "wr-stamina-" + id);
            CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
            user = new ServerPlayer(level.getServer(), level, profile, cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            level.getServer().getPlayerList().placeNewPlayer(connection, user, cookie);
            user.setGameMode(GameType.SURVIVAL);
            user.setNoGravity(true);
            user.moveTo(userCell.getX() + 0.5D, userCell.getY(), userCell.getZ() + 0.5D, 0.0F, 0.0F);
            user.setYHeadRot(0.0F);

            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(WeatherReportAddon.WEATHER_REPORT.get())).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant Weather Report to the user");
            power.skipProgression();
            power.setResolveLevel(power.getMaxResolveLevel());
            helper.assertTrue(WeatherReportAddon.WEATHER_REPORT.get().summon(user, power)
                            && power.getSummonedStandEntity() != null,
                    "Production Weather Report summon failed");
            stand = power.getSummonedStandEntity();
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
            helper.assertTrue(!power.isStaminaInfinite(), "The fixture's stamina bar is infinite");
        }

        private Cow cow(double ahead) {
            cow = EntityType.COW.create(level);
            helper.assertTrue(cow != null, "Could not create the target");
            cow.setNoAi(true);
            cow.setPersistenceRequired();
            cow.moveTo(userCell.getX() + 0.5D, userCell.getY(), userCell.getZ() + 0.5D + ahead, 180.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(cow), "Could not add the target");
            return cow;
        }

        /** The steps of ClAbilityInputPacket.Handler.handlePress, the condition check first. */
        private void press(String name, InputMethod method) {
            Ability ability = power.getAbility(name);
            helper.assertTrue(ability != null, "Weather Report has no " + name);
            AbilityConditionCheck check = power.updateAvailableMoves().getContextVariationContainer(ability);
            helper.assertTrue(check != null && check.ability == ability,
                    name + " is not available in the server moveset");
            helper.assertTrue(AbilityInput.withConditionCheck(check, user, method),
                    "1.16 had no stamina condition: " + name + " must be admitted at " + power.getStamina()
                            + " stamina, got " + (check.conditionCheck.getWarning() != null
                                    ? check.conditionCheck.getWarning().getString() : "a silent refusal"));
            BufferingState buffering = BufferingState.clickOnly();
            AbilityInput.keyPress(KEY, check.ability, user, null, method, 0.0F, buffering, ability.getAbilityId());
            helper.assertTrue(buffering.isActionSuccess, name + " did not start on the admitted press");
        }

        // The bar after the first Stand tick in which done holds: the tick that performed, before regeneration.
        private void watchStand(BooleanSupplier done, float[] recorded) {
            listen(EntityTickEvent.Post.class, event -> {
                if (event.getEntity() == stand && recorded[0] < 0.0F && done.getAsBoolean()) {
                    recorded[0] = power.getStamina();
                }
            });
        }

        private <E extends Event> void listen(Class<E> type, Consumer<E> listener) {
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, type, listener);
            cleanups.add(() -> NeoForge.EVENT_BUS.unregister(listener));
        }

        private void check(boolean passed, String message) {
            if (!passed) {
                failures.add(message);
            }
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                cleanups.forEach(Runnable::run);
                if (user != null) {
                    AbilityInput.keyRelease(KEY, user);
                    if (power != null && power.isSummoned()) {
                        WeatherReportAddon.WEATHER_REPORT.get().forceUnsummon(user, power);
                    }
                    user.connection.disconnect(Component.literal("Weather Report stamina GameTest complete"));
                }
                if (cow != null && !cow.isRemoved()) {
                    cow.discard();
                }
            }
            finally {
                if (channel != null) {
                    channel.finishAndReleaseAll();
                }
            }
        }
    }
}
