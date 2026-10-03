package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.LightBeamEntity;
import rotp.core.impl.powers.vampirism.SunWeakness;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.mechanics.JojoDefinitions;
import rotp.core.powersystem.PowerClass;
import rotp.core.util.functions.DamageUtil;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SunWeaknessGameTests {
    private SunWeaknessGameTests() {}

    @GameTest(template = "empty", batch = "sun_weakness_motion", skyAccess = true, timeoutTicks = 150)
    public static void environmentalSunlightKeepsDamageAndBurnWithoutKnockback(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        // Let the real ServerPlayer's sixty-tick spawn immunity expire under sun protection.
        helper.runAfterDelay(63, () -> guarded(fixture, () -> {
            fixture.assertSunlight();
            fixture.player.addEffect(new MobEffectInstance(ModStatusEffects.SUN_RESISTANCE, 200));
            fixture.assertNoSunDamage("SUN_RESISTANCE");
            fixture.player.removeEffect(ModStatusEffects.SUN_RESISTANCE);
            fixture.level.setBlockAndUpdate(fixture.shade, Blocks.STONE.defaultBlockState());
            helper.runAfterDelay(3, () -> guarded(fixture, () -> {
                helper.assertTrue(!fixture.level.canSeeSky(fixture.sunPos()), "Shade did not block the actual sun-check position");
                fixture.assertNoSunDamage("Opaque shade");
                fixture.level.setBlockAndUpdate(fixture.shade, fixture.oldShade);
                helper.runAfterDelay(3, () -> guarded(fixture, () -> {
                    fixture.assertSunlight();
                    fixture.player.move(MoverType.SELF, new Vec3(0, -0.01D, 0));
                    fixture.player.setDeltaMovement(Vec3.ZERO);
                    fixture.player.invulnerableTime = 0;
                    helper.assertTrue(fixture.player.onGround() && !fixture.player.getAbilities().invulnerable
                                    && fixture.player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) == 0.0D,
                            "Positive sunlight fixture is not grounded, vulnerable and resistance0");
                    float health = fixture.player.getHealth();
                    SunWeakness.tickSunBurn(fixture.player, fixture.level);
                    fixture.firstMotion = fixture.player.getDeltaMovement();
                    var burn = fixture.player.getEffect(ModStatusEffects.VAMPIRE_SUN_BURN);
                    helper.assertTrue(health - fixture.player.getHealth() == SunWeakness.SUN_DAMAGE
                                    && fixture.hits.size() == 1 && burn != null
                                    && burn.getDuration() == 60 && burn.getAmplifier() == 0,
                            "Production sunlight did not apply one4-damage hit and the original60-tick burn");
                    float after = fixture.player.getHealth();
                    SunWeakness.tickSunBurn(fixture.player, fixture.level);
                    helper.assertTrue(fixture.player.getHealth() == after && fixture.hits.size() == 1
                                    && fixture.player.getEffect(ModStatusEffects.VAMPIRE_SUN_BURN).getDuration() == 60,
                            "Immediate repeat bypassed the unchanged invulnerability admission");
                    LogUtils.getLogger().info("R579 first sunlight: tick={}, damage={}, burn=60/0, motion={}, knockbacks={}, ground={}, sourceNoKnockback={}",
                            fixture.player.tickCount, health - after, fixture.firstMotion, fixture.knockbacks,
                            fixture.player.onGround(), DamageUtil.make(fixture.level, ModDamageTypes.ULTRAVIOLET)
                                    .is(DamageTypeTags.NO_KNOCKBACK));
                    fixture.measureCadence = true;
                }));
            }));
        }));
        helper.onEachTick(() -> guarded(fixture, () -> {
            if (!fixture.measureCadence || fixture.hits.size() < 3) {
                return;
            }
            SunHit second = fixture.hits.get(1);
            SunHit third = fixture.hits.get(2);
            helper.assertTrue(fixture.hits.size() == 3 && second.tick() % 20 == 0
                            && third.tick() - second.tick() == 20
                            && fixture.hits.stream().allMatch(hit -> hit.damage() == SunWeakness.SUN_DAMAGE),
                    "Natural EventHandler sunlight cadence/damage changed: " + fixture.hits);
            var burn = fixture.player.getEffect(ModStatusEffects.VAMPIRE_SUN_BURN);
            helper.assertTrue(burn != null && burn.getDuration() > 0,
                    "Natural sunlight follow-ups lost SUN_BURN");
            fixture.assertSourceBoundary();
            LogUtils.getLogger().info("R579 complete: hits={}, firstMotion={}, knockbacks={}, burnDuration={}, burnAmplifier={}",
                    fixture.hits, fixture.firstMotion, fixture.knockbacks, burn.getDuration(), burn.getAmplifier());
            helper.assertTrue(fixture.firstMotion.lengthSqr() < 1.0E-12D && fixture.knockbacks.isEmpty(),
                    "Environmental sunlight added knockback: motion=" + fixture.firstMotion + ", events=" + fixture.knockbacks);
            helper.assertTrue(DamageUtil.make(fixture.level, ModDamageTypes.ULTRAVIOLET).is(DamageTypeTags.NO_KNOCKBACK),
                    "Environmental sunlight must retain the donor no-knockback behavior");
            fixture.close();
            helper.succeed();
        }));
        helper.runAfterDelay(140, () -> guarded(fixture, () -> {
            helper.fail("Sunlight fixture did not complete its real periodic follow-ups: " + fixture.hits);
        }));
    }

    private static void guarded(Fixture fixture, Runnable action) {
        if (fixture.closed.get()) {
            return;
        }
        try {
            action.run();
        }
        catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    private record SunHit(int tick, float damage) {}
    private record Knockback(int tick, float strength, double x, double z) {}

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final ServerLevelData levelData;
        private final long oldDayTime;
        private final int oldClearWeatherTime;
        private final int oldRainTime;
        private final int oldThunderTime;
        private final boolean oldRaining;
        private final boolean oldThundering;
        private final float oldRainLevel;
        private final float oldThunderLevel;
        private final BlockPos floor;
        private final BlockPos shade;
        private final Map<BlockPos, BlockState> oldFloor = new LinkedHashMap<>();
        private final BlockState oldShade;
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final List<SunHit> hits = new ArrayList<>();
        private final List<Knockback> knockbacks = new ArrayList<>();
        private final Consumer<LivingDamageEvent.Post> damageListener;
        private final Consumer<LivingKnockBackEvent> knockbackListener;
        private boolean measureCadence;
        private Vec3 firstMotion = Vec3.ZERO;

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            level = helper.getLevel();
            levelData = (ServerLevelData) level.getLevelData();
            oldDayTime = level.getDayTime();
            oldClearWeatherTime = levelData.getClearWeatherTime();
            oldRainTime = levelData.getRainTime();
            oldThunderTime = levelData.getThunderTime();
            oldRaining = levelData.isRaining();
            oldThundering = levelData.isThundering();
            oldRainLevel = level.getRainLevel(1.0F);
            oldThunderLevel = level.getThunderLevel(1.0F);
            BlockPos origin = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(origin);
            floor = new BlockPos(chunk.getMinBlockX() + 8, origin.getY() + 15, chunk.getMinBlockZ() + 8);
            shade = floor.above(5);
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    BlockPos pos = floor.offset(x, 0, z);
                    oldFloor.put(pos, level.getBlockState(pos));
                }
            }
            oldShade = level.getBlockState(shade);
            GameProfile profile = new GameProfile(UUID.randomUUID(), "SunWeaknessProbe");
            player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault()) {
                @Override
                public void tick() {
                    super.tick();
                    // No real socket drives the second vanilla player-tick half in this fixture.
                    doTick();
                }
            };
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            player.connection = new SilentConnection(level, connection, player);
            damageListener = event -> {
                if (event.getEntity() == player && event.getSource().is(ModDamageTypes.ULTRAVIOLET)) {
                    hits.add(new SunHit(player.tickCount, event.getNewDamage()));
                }
            };
            knockbackListener = event -> {
                if (event.getEntity() == player) {
                    knockbacks.add(new Knockback(player.tickCount, event.getStrength(), event.getRatioX(), event.getRatioZ()));
                }
            };
            try {
                level.setDayTime(6000);
                level.setWeatherParameters(10000, 0, false, false);
                oldFloor.keySet().forEach(pos -> level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()));
                player.setPos(Vec3.atBottomCenterOf(floor.above()));
                player.setGameMode(GameType.SURVIVAL);
                player.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.0D);
                PowerClass.PLAYER_POWER.attachGet(player).setPowerType(ModPlayerPowers.VAMPIRISM.get());
                player.addEffect(new MobEffectInstance(ModStatusEffects.SUN_RESISTANCE, 200));
                level.addNewPlayer(player);
                NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, damageListener);
                NeoForge.EVENT_BUS.addListener(LivingKnockBackEvent.class, knockbackListener);
                helper.assertTrue(JojoDefinitions.isUndeadOrVampiric(player) && !player.isCreative(),
                        "Registered Survival vampirism setup failed");
            }
            catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        private BlockPos sunPos() {
            return BlockPos.containing(player.getX(), Math.round(player.getY(1.0D)), player.getZ());
        }

        private void assertSunlight() {
            helper.assertTrue(SunWeakness.isSunny(level) && level.canSeeSky(sunPos())
                            && player.getLightLevelDependentMagicValue() > 0.5F,
                    "Sunlight fixture failed day/weather/sky/brightness eligibility");
        }

        private void assertNoSunDamage(String label) {
            player.invulnerableTime = 0;
            player.setDeltaMovement(Vec3.ZERO);
            float health = player.getHealth();
            SunWeakness.tickSunBurn(player, level);
            helper.assertTrue(player.getHealth() == health && player.getDeltaMovement().lengthSqr() == 0.0D
                            && !player.hasEffect(ModStatusEffects.VAMPIRE_SUN_BURN) && hits.isEmpty() && knockbacks.isEmpty(),
                    label + " failed the negative sunlight control");
        }

        private void assertSourceBoundary() {
            var sunlight = DamageUtil.make(level, ModDamageTypes.ULTRAVIOLET);
            ProbeBeam beam = new ProbeBeam(level);
            try {
                var attributed = beam.source(player);
                helper.assertTrue(sunlight.getEntity() == null && sunlight.getDirectEntity() == null
                                && sunlight.getSourcePosition() == null
                                && attributed.is(ModDamageTypes.ULTRAVIOLET_ENTITY)
                                && attributed.getEntity() == player && attributed.getDirectEntity() == beam
                                && !attributed.is(DamageTypeTags.NO_KNOCKBACK),
                        "Environmental and attributed beam UV boundaries were mixed");
                for (var tag : List.of(DamageTypeTags.BYPASSES_ARMOR, DamageTypeTags.BYPASSES_WOLF_ARMOR,
                        DamageTypeTags.BYPASSES_ENCHANTMENTS, DamageTypeTags.BYPASSES_EFFECTS)) {
                    helper.assertTrue(sunlight.is(tag) && attributed.is(tag), "Existing ultraviolet tag changed: " + tag.location());
                }
                helper.assertTrue(sunlight.getFoodExhaustion() == attributed.getFoodExhaustion()
                                && sunlight.type().scaling() == attributed.type().scaling(),
                        "Existing UV damage values changed");
            }
            finally {
                beam.discard();
            }
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            NeoForge.EVENT_BUS.unregister(damageListener);
            NeoForge.EVENT_BUS.unregister(knockbackListener);
            EventHooks.firePlayerLoggedOut(player);
            player.getAdvancements().stopListening();
            player.discard();
            channel.finishAndReleaseAll();
            oldFloor.forEach(level::setBlockAndUpdate);
            level.setBlockAndUpdate(shade, oldShade);
            level.setDayTime(oldDayTime);
            levelData.setClearWeatherTime(oldClearWeatherTime);
            levelData.setRainTime(oldRainTime);
            levelData.setThunderTime(oldThunderTime);
            levelData.setRaining(oldRaining);
            levelData.setThundering(oldThundering);
            level.setRainLevel(oldRainLevel);
            level.setThunderLevel(oldThunderLevel);
        }
    }

    private static final class SilentConnection extends ServerGamePacketListenerImpl {
        private SilentConnection(ServerLevel level, Connection connection, ServerPlayer player) {
            super(level.getServer(), connection, player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener listener) {}
    }

    private static final class ProbeBeam extends LightBeamEntity {
        private ProbeBeam(Level level) {
            super(ModEntityTypes.AJA_STONE_BEAM.get(), level);
        }

        private net.minecraft.world.damagesource.DamageSource source(ServerPlayer owner) {
            return getDamageSource(owner);
        }
    }
}
