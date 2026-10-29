package rotp.addons.sexpistols;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.api.timestop.TimeStopEntityMovementAuthorizers;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.timestop.TimeStopLearning;
import rotp.core.subsystems.timestop.TimeStopState;
import com.mojang.authlib.GameProfile;

import rotp.addons.sexpistols.content.revolver.entity.RevolverBulletEntity;
import rotp.addons.sexpistols.content.revolver.item.RevolverItem;
import rotp.addons.sexpistols.content.revolver.state.RevolverMemberAttachments;
import rotp.addons.sexpistols.content.revolver.state.RevolverSpecialChambers;
import rotp.addons.sexpistols.content.revolver.state.RevolverSpecialChambers.ShotType;
import rotp.addons.sexpistols.content.revolver.state.RevolverState;
import rotp.addons.sexpistols.content.revolver.state.RevolverState.Chamber;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsCoordinator;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsEntity;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsMemberState;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsMoveSelectedAbility;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsReloadAbility;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsSpecialShotAbility;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsTargetMode;
import rotp.addons.sexpistols.network.RevolverCasingEjectPayload;
import rotp.addons.sexpistols.registry.SexPistolsAttachments;
import rotp.addons.sexpistols.registry.SexPistolsDataComponents;
import rotp.addons.sexpistols.registry.SexPistolsItems;
import rotp.addons.sexpistols.registry.SexPistolsSounds;
import rotp.addons.sexpistols.registry.SexPistolsStands;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder(SexPistolsIds.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SexPistolsGameTests {
    private static final short PIERCING_KEY = 73;
    private static final short QUICK_RELOAD_KEY = 74;
    private static final short STAND_RELOAD_KEY = 75;
    private static final short SPLITTING_KEY = 76;
    private static final short ENCIRCLEMENT_KEY = 77;
    private static final short MOVE_KEY = 78;
    private static final short OTHER_KEY = 79;
    private static final short VISION_KEY = 80;
    // 1.16 SexPistolsMoveSelectedAction MOVE_SPEED and MOVE_MAX_DISTANCE and
    // InitStands :104 cooldown(4), as the test's own numbers.
    private static final double DONOR_MOVE_STEP = 0.36D;
    private static final double DONOR_MOVE_MAX_DISTANCE = 10.0D;
    private static final int DONOR_MOVE_COOLDOWN = 4;
    private static final int MOVE_FIXTURE_REACH = 16;
    // 1.16 InitStands :130 range(40, 40); the user walks this far off.
    private static final double DONOR_SEX_PISTOLS_RANGE = 40.0D;
    private static final double FIXTURE_RANGE_WALK = 45.0D;
    private static final int FIXTURE_RANGE_MARGIN = 4;
    private static final int QUICK_RELOAD_TICK = 4;
    private static final int DONOR_REGEN_PAUSE_TICKS = 10;
    private static final float DONOR_QUICK_RELOAD_BULLET_COST = 5.0F;
    private static final int FROZEN_OBSERVATION_TICKS = 5;
    private static final int CHUNK_LAST_BLOCK = 15;
    private static final int OWNER_LAST_BLOCK = CHUNK_LAST_BLOCK - 1;
    private static final PowerSkillUnlocks.Owner TEST_UNLOCKS =
            PowerSkillUnlocks.register(SexPistolsIds.id(
                    "piercing_lifetime_gametest"));

    private SexPistolsGameTests() {}

    // Own batch: its Time Stop covers the neighbouring test plots.
    @GameTest(
            templateNamespace = "rotp_addon_api_smoke",
            template = "empty",
            batch = "sexPistolsTimeStop",
            timeoutTicks = 260 + GameTestChunkTickets.WAIT_TICKS)
    public static void piercingShotSettlesWithinEightyNonFrozenTicks(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> piercingShotSettlesWithinEightyNonFrozenTicksBody(h));
    }

    private static void piercingShotSettlesWithinEightyNonFrozenTicksBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = makeCompatibleMockPlayer(
                helper, "sex-pistols-piercing-owner");
        ServerPlayer timeStopper = makeCompatibleMockPlayer(
                helper, "sex-pistols-piercing-time-stopper");
        positionPlayer(helper, owner, 1, 2, 1);
        positionPlayer(helper, timeStopper, 3, 2, 1);
        // The shot leaves the plot's chunk on its first tick and flies up to +4 chunks south.
        GameTestChunkTickets tickets = GameTestChunkTickets.forTest(helper).coverBox(
                owner.getX(), owner.getZ(), owner.getX(), owner.getZ() + 4 * 16);
        Runnable[] scenario = {null};

        tickets.onEachTickWhenTicking(() -> {
            owner.connection.disconnect(Component.literal(
                    "Sex Pistols piercing GameTest complete"));
            timeStopper.connection.disconnect(Component.literal(
                    "Sex Pistols piercing GameTest complete"));
        }, () -> {
            if (scenario[0] != null) {
                scenario[0].run();
                return;
            }
            scenario[0] = firePiercingShotAndObserve(
                    helper, level, owner, timeStopper);
        });
    }

    // Runs the body every tick from the first tick on which the chunks around
    // the owner tick entities; the wait has its own budget in real time.
    private static void onEachTickInTickingChunk(
            GameTestHelper helper,
            ServerLevel level,
            ServerPlayer owner,
            Runnable onTimeout,
            Runnable body) {
        GameTestChunkTickets.forTest(helper)
                .cover(owner.position())
                .onEachTickWhenTicking(onTimeout, body);
    }

    private static Runnable firePiercingShotAndObserve(
            GameTestHelper helper,
            ServerLevel level,
            ServerPlayer owner,
            ServerPlayer timeStopper) {
        StandPower power = prepareSexPistols(helper, owner);
        grantTimeStop(helper, timeStopper);
        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(owner);
        coordinator.cycleTargetMode(owner, false);
        coordinator.cycleTargetMode(owner, false);
        helper.assertTrue(
                coordinator.targetMode()
                        == SexPistolsTargetMode.FRIENDLY,
                "The production coordinator did not select friendly "
                        + "targets for the fixture.");

        ItemStack revolver = new ItemStack(
                SexPistolsItems.REVOLVER.get());
        owner.setItemSlot(EquipmentSlot.MAINHAND, revolver);
        owner.getAbilities().instabuild = true;
        primePiercingThroughAbility(
                helper, owner, power, coordinator, revolver);
        helper.assertTrue(
                RevolverItem.closeReloadMode(
                        level, owner, revolver),
                "The production revolver could not close after priming.");

        Cow target = EntityType.COW.create(level);
        helper.assertTrue(
                target != null,
                "The piercing damage target could not be created.");
        if (target == null) {
            return () -> {};
        }
        target.setNoAi(true);

        owner.setYRot(0.0F);
        owner.setYHeadRot(0.0F);
        owner.setXRot(8.0F);
        RevolverItem revolverItem =
                (RevolverItem) revolver.getItem();
        revolverItem.use(level, owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(
                owner.isUsingItem()
                        && RevolverItem.state(revolver).aiming(),
                "The production revolver did not enter aiming state.");
        owner.releaseUsingItem();

        List<RevolverBulletEntity> bullets =
                level.getEntitiesOfClass(
                        RevolverBulletEntity.class,
                        new AABB(owner.blockPosition()).inflate(8.0D),
                        bullet -> bullet.getOwner() == owner);
        helper.assertTrue(
                bullets.size() == 1,
                "Production fire created " + bullets.size()
                        + " revolver bullets instead of one.");
        if (bullets.isEmpty()) {
            return () -> {};
        }
        RevolverBulletEntity bullet = bullets.getFirst();
        Vec3 targetCenter = bullet.position().add(
                bullet.getDeltaMovement().normalize().scale(3.0D));
        target.moveTo(
                targetCenter.x,
                targetCenter.y - target.getBbHeight() * 0.5D,
                targetCenter.z,
                0.0F,
                0.0F);
        helper.assertTrue(
                level.addFreshEntity(target),
                "The piercing damage target could not join the level.");
        float targetHealthBefore = target.getHealth();
        helper.assertTrue(
                bullet.specialShotType() == ShotType.PIERCING
                        && bullet.attachedMemberMask() == 0x3F
                        && coordinator.inFlightMemberMask() == 0x3F,
                "Production fire did not transfer the primed members "
                        + "to a PIERCING root.");
        assertOwnerEpochBound(helper, bullet, owner);

        TimeStopState timeStop = level.getData(
                ModDataAttachmentTypes.TIME_STOP.get());
        int timeStopInstanceId = timeStopper.getId();
        int[] phase = {0};
        int[] elapsedTicks = {0};
        int[] decelerationTicks = {0};
        int[] frozenTicks = {0};
        int[] frozenPiercingTicks = {-1};
        int[] freezeStartEntityTicks = {-1};
        boolean[] resumeObserved = {false};
        Vec3[] frozenPosition = {Vec3.ZERO};

        return () -> {
            try {
            elapsedTicks[0]++;
            if (phase[0] == 0) {
                if (target.getHealth() >= targetHealthBefore) {
                    helper.assertTrue(
                            elapsedTicks[0] <= 3,
                            "The production PIERCING trace did not "
                                    + "damage its first target.");
                    return;
                }
                helper.assertTrue(
                        bullet.isAlive()
                                && bullet.isSpecialActive(),
                        "PIERCING destroyed its root on first hit.");
                helper.assertTrue(
                        coordinator.loadedMemberMask() == 0
                                && coordinator
                                        .inFlightMemberMask() == 0,
                        "PIERCING activation did not release every "
                                + "coordinator member.");

                // Keep the lifetime fixture inside loaded chunks after
                // production speed, activation, and damage were observed.
                bullet.setDeltaMovement(new Vec3(0.0D, 0.0D, 0.55D));
                TimeStopState.Instance instance =
                        TimeStopState.Instance.of(
                                timeStopInstanceId,
                                20,
                                20,
                                new ChunkPos(bullet.blockPosition()),
                                4,
                                timeStopper,
                                "sex_pistols_piercing_gametest");
                helper.assertTrue(
                        timeStop.tryPutInstance(instance),
                        "Production TimeStopState rejected the "
                                + "PIERCING freeze fixture.");
                freezeStartEntityTicks[0] = bullet.tickCount;
                JojoMod.getLogger().info("PIERCING_LIFETIME stopStart entityTicks={} piercingTicks={} entityTicking={}",
                        bullet.tickCount, piercingTicks(bullet), level.isPositionEntityTicking(bullet.blockPosition()));
                phase[0] = 1;
                return;
            }

            if (phase[0] == 1) {
                decelerationTicks[0]++;
                helper.assertTrue(
                        bullet.isAlive(),
                        "PIERCING expired during Time Stop slowdown "
                                + "before reaching a frozen state.");
                helper.assertTrue(
                        decelerationTicks[0] <= 8,
                        "PIERCING did not reach a fully frozen state "
                                + "within the bounded slowdown window: "
                                + "instancePresent="
                                + timeStop.getInstance(
                                        timeStopInstanceId).isPresent()
                                + ", covers="
                                + timeStop.getInstance(
                                                timeStopInstanceId)
                                        .filter(instance ->
                                                instance.covers(
                                                        new ChunkPos(
                                                                bullet.blockPosition())))
                                        .isPresent()
                                + ", phase=" + bullet.timeStopPhase()
                                + ", remaining="
                                + bullet.timeStopSlowdownTicksRemaining()
                                + ", entityTicks=" + bullet.tickCount
                                + ", freezeStartEntityTicks=" + freezeStartEntityTicks[0]
                                + ", piercingTicks=" + piercingTicks(bullet)
                                + ", areaStopped=" + timeStop.isTimeStopped(bullet)
                                + ", movementAuthorized="
                                + TimeStopEntityMovementAuthorizers.canMoveInStoppedTime(bullet)
                                + ", shouldFreeze=" + timeStop.shouldFreeze(bullet)
                                + ", entityTicking=" + level.isPositionEntityTicking(bullet.blockPosition())
                                + ", bulletChunk="
                                + new ChunkPos(bullet.blockPosition())
                                + ".");
                if (bullet.timeStopPhase()
                        != RevolverBulletEntity.TimeStopPhase.FROZEN) {
                    return;
                }
                frozenPiercingTicks[0] = piercingTicks(bullet);
                frozenPosition[0] = bullet.position();
                JojoMod.getLogger().info("PIERCING_LIFETIME frozen slowdownObservations={} entityTicks={} piercingTicks={}",
                        decelerationTicks[0], bullet.tickCount, frozenPiercingTicks[0]);
                phase[0] = 2;
                return;
            }

            if (phase[0] == 2) {
                helper.assertTrue(
                        bullet.isAlive()
                                && piercingTicks(bullet)
                                        == frozenPiercingTicks[0]
                                && bullet.position().distanceToSqr(
                                        frozenPosition[0]) < 1.0E-8D,
                        "Fully frozen PIERCING consumed lifetime or "
                                + "continued moving.");
                frozenTicks[0]++;
                if (frozenTicks[0] < FROZEN_OBSERVATION_TICKS) {
                    return;
                }
                timeStop.removeInstance(timeStopInstanceId);
                JojoMod.getLogger().info("PIERCING_LIFETIME resumeRequested frozenObservations={} entityTicks={} piercingTicks={}",
                        frozenTicks[0], bullet.tickCount, piercingTicks(bullet));
                phase[0] = 3;
                return;
            }

            int consumedTicks = piercingTicks(bullet);
            if (bullet.isAlive()) {
                helper.assertTrue(
                        bullet.timeStopPhase()
                                == RevolverBulletEntity
                                        .TimeStopPhase.NORMAL,
                        "PIERCING did not resume after Time Stop.");
                if (!resumeObserved[0]) {
                    resumeObserved[0] = true;
                    JojoMod.getLogger().info("PIERCING_LIFETIME resumed entityTicks={} piercingTicks={} speed={}",
                            bullet.tickCount, consumedTicks, bullet.getDeltaMovement().length());
                }
                // The donor removes on > 80, after allowing all eighty movement ticks.
                helper.assertTrue(
                        consumedTicks <= RevolverBulletEntity
                                .PIERCING_LIFETIME_TICKS,
                        "PIERCING remained alive after "
                                + consumedTicks
                                + " non-frozen lifetime ticks.");
                return;
            }

            helper.assertTrue(
                    consumedTicks <= RevolverBulletEntity
                            .PIERCING_LIFETIME_TICKS,
                    "PIERCING removal exceeded the 80-tick "
                            + "non-frozen lifetime: "
                            + consumedTicks + ".");
            helper.assertTrue(
                    coordinator.loadedMemberMask() == 0
                            && coordinator.inFlightMemberMask() == 0,
                    "PIERCING removal left coordinator members loaded "
                            + "or in flight.");
            JojoMod.getLogger().info("PIERCING_LIFETIME removed entityTicks={} piercingTicks={} frozenObservations={}",
                    bullet.tickCount, consumedTicks, frozenTicks[0]);
            cleanup(
                    level,
                    timeStop,
                    timeStopInstanceId,
                    owner,
                    timeStopper,
                    target,
                    coordinator);
            helper.succeed();
            }
            catch (RuntimeException | Error failure) {
                cleanup(level, timeStop, timeStopInstanceId, owner, timeStopper, target, coordinator);
                throw failure;
            }
        };
    }

    // 1.16 SexPistolsStandReloadAction.quickReload paid through
    // SexPistolsStaminaUtil.consumeStamina, which pauses regen for 10 ticks.
    @GameTest(
            templateNamespace = "rotp_addon_api_smoke",
            template = "empty",
            timeoutTicks = 60 + GameTestChunkTickets.WAIT_TICKS)
    public static void paidQuickReloadPausesStaminaRegenForTenTicks(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> paidQuickReloadPausesStaminaRegenForTenTicksBody(h));
    }

    private static void paidQuickReloadPausesStaminaRegenForTenTicksBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = makeCompatibleMockPlayer(
                helper, "sex-pistols-quick-reload");
        positionPlayer(helper, owner, 1, 2, 1);
        owner.setGameMode(GameType.SURVIVAL);
        owner.setNoGravity(true);
        StandPower power = prepareSexPistols(helper, owner);
        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(owner);
        ItemStack revolver = new ItemStack(
                SexPistolsItems.REVOLVER.get());
        owner.setItemSlot(EquipmentSlot.MAINHAND, revolver);
        owner.getInventory().add(new ItemStack(
                SexPistolsItems.REVOLVER_AMMO.get(),
                RevolverState.CHAMBER_COUNT));
        power.setStamina(power.getMaxStamina() * 0.5F);

        int[] tick = {0};
        float[] firstStamina = {0.0F};
        float[] regenPerTick = {0.0F};
        float[] paidStamina = {0.0F};

        helper.onEachTick(() -> {
            try {
                // No socket drives the vanilla player tick (PlayerTickEvent) of a mock player.
                owner.doTick();
                tick[0]++;
                float stamina = power.getStamina();
                if (tick[0] == 1) {
                    firstStamina[0] = stamina;
                    return;
                }
                if (tick[0] < QUICK_RELOAD_TICK) {
                    return;
                }
                if (tick[0] == QUICK_RELOAD_TICK) {
                    regenPerTick[0] = (stamina - firstStamina[0])
                            / (QUICK_RELOAD_TICK - 1);
                    helper.assertTrue(
                            power.usesStamina()
                                    && !power.isStaminaInfinite()
                                    && regenPerTick[0] > 0.0F,
                            "The fixture has no measurable stamina regen: "
                                    + regenPerTick[0] + " per tick.");
                    quickReloadThroughAbility(helper, owner, power);
                    int loaded = RevolverItem.state(revolver).liveCount();
                    paidStamina[0] = power.getStamina();
                    float cost = loaded * DONOR_QUICK_RELOAD_BULLET_COST;
                    helper.assertTrue(
                            loaded == RevolverState.CHAMBER_COUNT
                                    && Math.abs(stamina - cost
                                            - paidStamina[0]) < 0.01F,
                            "Quick reload loaded " + loaded
                                    + " bullets for "
                                    + (stamina - paidStamina[0])
                                    + " stamina instead of six for "
                                    + cost + ".");
                    return;
                }

                int sincePayment = tick[0] - QUICK_RELOAD_TICK;
                float regained = stamina - paidStamina[0];
                if (sincePayment <= DONOR_REGEN_PAUSE_TICKS) {
                    // The regen of the payment tick may land before the first cancel is queued.
                    helper.assertTrue(
                            regained <= regenPerTick[0] * 1.25F,
                            "A paid quick reload did not pause stamina "
                                    + "regen: +" + regained + " after "
                                    + sincePayment + " ticks at "
                                    + regenPerTick[0] + " per tick.");
                    return;
                }
                if (regained >= regenPerTick[0] * 1.75F) {
                    cleanupOwner(level, owner, coordinator);
                    helper.succeed();
                    return;
                }
                helper.assertTrue(
                        sincePayment <= DONOR_REGEN_PAUSE_TICKS + 4,
                        "Stamina regen did not resume after the 10-tick "
                                + "pause: +" + regained + " after "
                                + sincePayment + " ticks.");
            }
            catch (RuntimeException | Error failure) {
                cleanupOwner(level, owner, coordinator);
                throw failure;
            }
        });
    }

    // 1.16 InitEntities: revolver_bullet is 0.25 x 0.25;
    // RevolverItem.shootBulletWithMask spawns it at eye + direction * 0.45.
    @GameTest(
            templateNamespace = "rotp_addon_api_smoke",
            template = "empty",
            timeoutTicks = 20 + GameTestChunkTickets.WAIT_TICKS)
    public static void revolverBulletKeepsDonorSizeAndSpawnPoint(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> revolverBulletKeepsDonorSizeAndSpawnPointBody(h));
    }

    private static void revolverBulletKeepsDonorSizeAndSpawnPointBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = makeCompatibleMockPlayer(
                helper, "sex-pistols-bullet-spawn");
        positionPlayer(helper, owner, 1, 2, 1);
        owner.setGameMode(GameType.SURVIVAL);
        try {
            ItemStack revolver = new ItemStack(
                    SexPistolsItems.REVOLVER.get());
            owner.setItemSlot(EquipmentSlot.MAINHAND, revolver);
            owner.getInventory().add(new ItemStack(
                    SexPistolsItems.REVOLVER_AMMO.get()));
            helper.assertTrue(
                    RevolverItem.openForStandReload(
                                    level, owner, revolver, true, true)
                            && RevolverItem.loadSelectedChamberIfEmpty(
                                    owner, revolver)
                            && RevolverItem.closeReloadMode(
                                    level, owner, revolver),
                    "The production revolver could not be loaded.");
            owner.setYRot(35.0F);
            owner.setYHeadRot(35.0F);
            owner.setXRot(-20.0F);
            ((RevolverItem) revolver.getItem()).use(
                    level, owner, InteractionHand.MAIN_HAND);
            helper.assertTrue(
                    owner.isUsingItem()
                            && RevolverItem.state(revolver).aiming(),
                    "The production revolver did not enter aiming state.");
            // Recoil turns the shooter after the shot.
            Vec3 donorSpawn = owner.getEyePosition().add(
                    owner.getLookAngle().scale(0.45D));
            owner.releaseUsingItem();

            List<RevolverBulletEntity> bullets =
                    level.getEntitiesOfClass(
                            RevolverBulletEntity.class,
                            new AABB(owner.blockPosition()).inflate(8.0D),
                            bullet -> bullet.getOwner() == owner);
            helper.assertTrue(
                    bullets.size() == 1,
                    "Production fire created " + bullets.size()
                            + " revolver bullets instead of one.");
            RevolverBulletEntity bullet = bullets.getFirst();
            Vec3 offset = bullet.position().subtract(donorSpawn);
            helper.assertTrue(
                    bullet.getBbWidth() == 0.25F
                            && bullet.getBbHeight() == 0.25F
                            && offset.length() < 1.0E-6D,
                    "The revolver bullet is " + bullet.getBbWidth()
                            + " x " + bullet.getBbHeight()
                            + " and spawns " + offset
                            + " away from eye + look * 0.45; the donor "
                            + "bullet is 0.25 x 0.25 with no offset.");
            helper.succeed();
        }
        finally {
            RevolverBulletEntity.discardOwnedBy(level, owner.getUUID());
            owner.connection.disconnect(Component.literal(
                    "Sex Pistols bullet spawn GameTest complete"));
        }
    }

    // 1.16 SexPistolsStandReloadAction.checkConditions only asks for a revolver
    // in the main hand, and SexPistolsEntities.isPistolAvailable only reads the
    // revive timer and the loaded mask: members load with the Stand put away.
    @GameTest(
            templateNamespace = "rotp_addon_api_smoke",
            template = "empty",
            timeoutTicks = 100 + GameTestChunkTickets.WAIT_TICKS)
    public static void standReloadAttachesAMemberWithTheStandPutAway(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> standReloadAttachesAMemberWithTheStandPutAwayBody(h));
    }

    private static void standReloadAttachesAMemberWithTheStandPutAwayBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = makeCompatibleMockPlayer(
                helper, "sex-pistols-reload-put-away");
        positionPlayer(helper, owner, 1, 2, 1);
        owner.setGameMode(GameType.SURVIVAL);
        owner.setNoGravity(true);
        StandPower power = prepareSexPistols(helper, owner);
        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(owner);
        ItemStack revolver = giveRevolverWithLiveRounds(
                helper, owner, 1, 0);
        // The recall runs on the level's entity tick; positionPlayer holds
        // the tickets of the owner's chunks.
        Runnable finish = () -> cleanupOwner(level, owner, coordinator);
        power.getPowerType().forceUnsummon(owner, power);
        int[] tick = {0};

        onEachTickInTickingChunk(helper, level, owner, finish, () -> {
            try {
                owner.doTick();
                tick[0]++;
                if (power.isSummoned()
                        || coordinator.isSummoned()
                        || coordinator.trackedMemberCount() > 0
                        || RevolverItem.reloadCooldown(revolver) > 0) {
                    helper.assertTrue(
                            tick[0] <= 60,
                            "Sex Pistols was not put away within 60 ticks: "
                                    + "powerSummoned=" + power.isSummoned()
                                    + ", swarmSummoned="
                                    + coordinator.isSummoned()
                                    + ", tracked="
                                    + coordinator.trackedMemberCount()
                                    + ", reloadCooldown="
                                    + RevolverItem.reloadCooldown(revolver)
                                    + ", entityTicking="
                                    + level.isPositionEntityTicking(
                                            owner.blockPosition())
                                    + ".");
                    return;
                }
                pressReload(
                        helper,
                        owner,
                        power,
                        SexPistolsIds.SEX_PISTOLS_STAND_RELOAD_PATH,
                        STAND_RELOAD_KEY,
                        "Stand Reload with Sex Pistols put away");
                helper.assertTrue(power.getAbilityCooldown(SexPistolsIds.SEX_PISTOLS_STAND_RELOAD_PATH) == 0,
                        "Standard reload added an ability cooldown; the donor registers zero.");
                int chamber = RevolverItem.state(revolver)
                        .selectedChamber();
                int attached = RevolverMemberAttachments
                        .chamberMemberMask(revolver, chamber);
                helper.assertTrue(
                        coordinator.loadedMemberMask() == 1
                                && attached == 1
                                && RevolverItem.state(revolver)
                                        .reloadMode()
                                && !coordinator.isSummoned()
                                && !power.isSummoned()
                                && coordinator.trackedMemberCount() == 0,
                        "Stand Reload with the Stand put away did not "
                                + "attach member No.1 to the selected "
                                + "chamber as in the donor: loadedMask="
                                + coordinator.loadedMemberMask()
                                + ", chamberMask=" + attached
                                + ", open="
                                + RevolverItem.state(revolver).reloadMode()
                                + ", summoned=" + coordinator.isSummoned()
                                + ", tracked="
                                + coordinator.trackedMemberCount() + ".");
                finish.run();
                helper.succeed();
            }
            catch (RuntimeException | Error failure) {
                finish.run();
                throw failure;
            }
        });
    }

    // 1.16: the SexPistolsEntities effect only exists after a summon, and
    // reloadSelected without it ends in the 0.45 click after the cylinder opened.
    @GameTest(
            templateNamespace = "rotp_addon_api_smoke",
            template = "empty",
            timeoutTicks = 60 + GameTestChunkTickets.WAIT_TICKS)
    public static void standReloadBeforeTheFirstSummonOnlyClicks(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> standReloadBeforeTheFirstSummonOnlyClicksBody(h));
    }

    private static void standReloadBeforeTheFirstSummonOnlyClicksBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = makeCompatibleMockPlayer(
                helper, "sex-pistols-reload-never-summoned");
        positionPlayer(helper, owner, 1, 2, 1);
        owner.setGameMode(GameType.SURVIVAL);
        owner.setNoGravity(true);
        StandPower power = StandPower.get(owner);
        power.setStand(SexPistolsStands.SEX_PISTOLS.get());
        power.skipProgression();
        power.setStamina(power.getMaxStamina());
        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(owner);
        ItemStack revolver = giveRevolverWithLiveRounds(
                helper, owner, 1, 0);
        int[] tick = {0};

        helper.onEachTick(() -> {
            try {
                owner.doTick();
                tick[0]++;
                if (RevolverItem.reloadCooldown(revolver) > 0) {
                    helper.assertTrue(
                            tick[0] <= 30,
                            "The revolver reload cooldown did not run out.");
                    return;
                }
                forgetSentPackets(owner);
                pressReload(
                        helper,
                        owner,
                        power,
                        SexPistolsIds.SEX_PISTOLS_STAND_RELOAD_PATH,
                        STAND_RELOAD_KEY,
                        "Stand Reload before the first summon");
                List<String> heard = reloadSoundsAt(owner);
                List<String> donor = List.of(
                        "rotp_sex_pistols:revolver_open@0.85/1.0",
                        "minecraft:block.dispenser.fail@0.45/1.0");
                helper.assertTrue(
                        heard.equals(donor)
                                && coordinator.loadedMemberMask() == 0
                                && RevolverMemberAttachments
                                        .attachedMemberMask(revolver) == 0
                                && RevolverItem.state(revolver)
                                        .reloadMode()
                                && !coordinator.isSummoned(),
                        "Stand Reload before the first summon differs "
                                + "from the donor (open the cylinder, then "
                                + "the 0.45 fail click, no member): heard="
                                + heard + ", expected=" + donor
                                + ", loadedMask="
                                + coordinator.loadedMemberMask()
                                + ", open="
                                + RevolverItem.state(revolver).reloadMode()
                                + ".");
                cleanupOwner(level, owner, coordinator);
                helper.succeed();
            }
            catch (RuntimeException | Error failure) {
                cleanupOwner(level, owner, coordinator);
                throw failure;
            }
        });
    }

    // 1.16 SexPistolsStandReloadAction.perform: 0.35 click while the reload
    // cooldown runs; open 0.85/1.0, casings 0.75/1.0, speedloader 0.65/1.15.
    @GameTest(
            templateNamespace = "rotp_addon_api_smoke",
            template = "empty",
            timeoutTicks = 60 + GameTestChunkTickets.WAIT_TICKS)
    public static void standReloadFeedbackUsesDonorSounds(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> standReloadFeedbackUsesDonorSoundsBody(h));
    }

    private static void standReloadFeedbackUsesDonorSoundsBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = makeCompatibleMockPlayer(
                helper, "sex-pistols-reload-sounds");
        positionPlayer(helper, owner, 1, 2, 1);
        owner.setGameMode(GameType.SURVIVAL);
        owner.setNoGravity(true);
        StandPower power = prepareSexPistols(helper, owner);
        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(owner);
        ItemStack revolver = giveRevolverWithLiveRounds(
                helper, owner, 2, 1);
        int[] tick = {0};
        boolean[] blockedChecked = {false};

        helper.onEachTick(() -> {
            try {
                owner.doTick();
                tick[0]++;
                if (!blockedChecked[0]) {
                    blockedChecked[0] = true;
                    helper.assertTrue(
                            RevolverItem.reloadCooldown(revolver) > 0,
                            "The fixture left no reload cooldown to "
                                    + "block the first press.");
                    forgetSentPackets(owner);
                    pressReload(
                            helper,
                            owner,
                            power,
                            SexPistolsIds.SEX_PISTOLS_STAND_RELOAD_PATH,
                            STAND_RELOAD_KEY,
                            "Stand Reload during the reload cooldown");
                    List<String> heard = reloadSoundsAt(owner);
                    List<String> donor = List.of(
                            "minecraft:block.dispenser.fail@0.35/1.0");
                    helper.assertTrue(
                            heard.equals(donor)
                                    && coordinator.loadedMemberMask() == 0
                                    && !RevolverItem.state(revolver)
                                            .reloadMode(),
                            "Stand Reload during the reload cooldown "
                                    + "differs from the donor (only the "
                                    + "0.35 fail click): heard=" + heard
                                    + ", expected=" + donor
                                    + ", loadedMask="
                                    + coordinator.loadedMemberMask() + ".");
                    return;
                }
                if (RevolverItem.reloadCooldown(revolver) > 0) {
                    helper.assertTrue(
                            tick[0] <= 30,
                            "The revolver reload cooldown did not run out.");
                    return;
                }
                forgetSentPackets(owner);
                pressReload(
                        helper,
                        owner,
                        power,
                        SexPistolsIds.SEX_PISTOLS_STAND_RELOAD_PATH,
                        STAND_RELOAD_KEY,
                        "Stand Reload after the reload cooldown");
                List<String> heard = reloadSoundsAt(owner);
                List<String> donor = List.of(
                        "rotp_sex_pistols:revolver_shellsout@0.75/1.0",
                        "rotp_sex_pistols:revolver_open@0.85/1.0",
                        "rotp_sex_pistols:revolver_speedloader@0.65/1.15");
                helper.assertTrue(
                        heard.equals(donor)
                                && coordinator.loadedMemberMask() == 1,
                        "A successful Stand Reload differs from the "
                                + "donor sounds: heard=" + heard
                                + ", expected=" + donor + ", loadedMask="
                                + coordinator.loadedMemberMask() + ".");
                cleanupOwner(level, owner, coordinator);
                helper.succeed();
            }
            catch (RuntimeException | Error failure) {
                cleanupOwner(level, owner, coordinator);
                throw failure;
            }
        });
    }

    // 1.16 quick reload: fail clicks at pitch 1.2, speedloader 0.65/1.35.
    @GameTest(
            templateNamespace = "rotp_addon_api_smoke",
            template = "empty",
            timeoutTicks = 60 + GameTestChunkTickets.WAIT_TICKS)
    public static void quickReloadFeedbackUsesDonorSounds(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> quickReloadFeedbackUsesDonorSoundsBody(h));
    }

    private static void quickReloadFeedbackUsesDonorSoundsBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = makeCompatibleMockPlayer(
                helper, "sex-pistols-quick-reload-sounds");
        positionPlayer(helper, owner, 1, 2, 1);
        owner.setGameMode(GameType.SURVIVAL);
        owner.setNoGravity(true);
        StandPower power = prepareSexPistols(helper, owner);
        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(owner);
        ItemStack revolver = new ItemStack(
                SexPistolsItems.REVOLVER.get());
        owner.setItemSlot(EquipmentSlot.MAINHAND, revolver);
        owner.getInventory().add(new ItemStack(
                SexPistolsItems.REVOLVER_AMMO.get(),
                RevolverState.CHAMBER_COUNT));
        helper.assertTrue(
                RevolverItem.openForStandReload(
                                level, owner, revolver, true, true)
                        && RevolverItem.loadSelectedChamberIfEmpty(
                                owner, revolver),
                "The production revolver could not be loaded.");
        int[] tick = {0};
        boolean[] blockedChecked = {false};

        helper.onEachTick(() -> {
            try {
                owner.doTick();
                tick[0]++;
                if (!blockedChecked[0]) {
                    blockedChecked[0] = true;
                    helper.assertTrue(
                            RevolverItem.reloadCooldown(revolver) > 0,
                            "The fixture left no reload cooldown to "
                                    + "block the first press.");
                    forgetSentPackets(owner);
                    pressReload(
                            helper,
                            owner,
                            power,
                            SexPistolsIds.SEX_PISTOLS_QUICK_RELOAD_PATH,
                            QUICK_RELOAD_KEY,
                            "Quick Reload during the reload cooldown");
                    List<String> heard = reloadSoundsAt(owner);
                    List<String> donor = List.of(
                            "minecraft:block.dispenser.fail@0.35/1.2");
                    helper.assertTrue(
                            heard.equals(donor)
                                    && RevolverItem.state(revolver)
                                            .liveCount() == 1,
                            "Quick Reload during the reload cooldown "
                                    + "differs from the donor (only the "
                                    + "0.35 fail click at pitch 1.2): "
                                    + "heard=" + heard + ", expected="
                                    + donor + ".");
                    return;
                }
                if (RevolverItem.reloadCooldown(revolver) > 0) {
                    helper.assertTrue(
                            tick[0] <= 30,
                            "The revolver reload cooldown did not run out.");
                    return;
                }
                forgetSentPackets(owner);
                pressReload(
                        helper,
                        owner,
                        power,
                        SexPistolsIds.SEX_PISTOLS_QUICK_RELOAD_PATH,
                        QUICK_RELOAD_KEY,
                        "Quick Reload after the reload cooldown");
                helper.assertTrue(power.getAbilityCooldown(SexPistolsIds.SEX_PISTOLS_QUICK_RELOAD_PATH) == 40,
                        "Quick reload lost its donor forty-tick ability cooldown.");
                List<String> heard = reloadSoundsAt(owner);
                List<String> donor = List.of(
                        "rotp_sex_pistols:revolver_speedloader@0.65/1.35");
                helper.assertTrue(
                        heard.equals(donor)
                                && RevolverItem.state(revolver)
                                        .liveCount()
                                        == RevolverState.CHAMBER_COUNT,
                        "A successful Quick Reload differs from the "
                                + "donor sounds: heard=" + heard
                                + ", expected=" + donor + ", live="
                                + RevolverItem.state(revolver).liveCount()
                                + ".");
                cleanupOwner(level, owner, coordinator);
                helper.succeed();
            }
            catch (RuntimeException | Error failure) {
                cleanupOwner(level, owner, coordinator);
                throw failure;
            }
        });
    }

    // 1.16: the SexPistolsEntities effect is saved with the Stand power and
    // is dropped when its user dies (StandEffectInstance.removeOnUserDeath).
    @GameTest(
            templateNamespace = "rotp_addon_api_smoke",
            template = "empty",
            timeoutTicks = 20 + GameTestChunkTickets.WAIT_TICKS)
    public static void summonedSwarmIsRememberedUntilItsUserDies(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> summonedSwarmIsRememberedUntilItsUserDiesBody(h));
    }

    private static void summonedSwarmIsRememberedUntilItsUserDiesBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = makeCompatibleMockPlayer(
                helper, "sex-pistols-swarm-memory");
        positionPlayer(helper, owner, 1, 2, 1);
        owner.setGameMode(GameType.SURVIVAL);
        owner.setNoGravity(true);
        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(owner);
        try {
            StandPower power = prepareSexPistols(helper, owner);
            power.getPowerType().forceUnsummon(owner, power);
            CompoundTag saved = coordinator.serializeNBT(
                    level.registryAccess());
            SexPistolsCoordinator reloaded = new SexPistolsCoordinator();
            reloaded.deserializeNBT(level.registryAccess(), saved);
            boolean afterUnsummon = saved.getBoolean("SwarmEstablished");
            boolean afterReload = reloaded
                    .serializeNBT(level.registryAccess())
                    .getBoolean("SwarmEstablished");
            owner.kill();
            boolean afterDeath = coordinator
                    .serializeNBT(level.registryAccess())
                    .getBoolean("SwarmEstablished");
            helper.assertTrue(
                    owner.isDeadOrDying()
                            && afterUnsummon
                            && afterReload
                            && !afterDeath,
                    "The summoned swarm is not remembered as in the donor "
                            + "(kept when put away and saved, dropped on "
                            + "death): afterUnsummon=" + afterUnsummon
                            + ", afterSaveAndLoad=" + afterReload
                            + ", afterDeath=" + afterDeath
                            + ", dead=" + owner.isDeadOrDying() + ".");
            helper.succeed();
        }
        finally {
            cleanupOwner(level, owner, coordinator);
        }
    }

    // 1.16 RevolverBulletEntity.getLoadedKickStand asks the swarm effect for
    // the loaded member whether or not the Stand is out, and
    // SexPistolsEntities.releaseLoadedPistol recreates that member at the kick
    // point and marks the swarm summoned; no other member comes out.
    @GameTest(
            templateNamespace = "rotp_addon_api_smoke",
            template = "empty",
            timeoutTicks = 160 + GameTestChunkTickets.WAIT_TICKS)
    public static void loadedMemberShotStaysGuidedWithTheStandPutAway(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> loadedMemberShotStaysGuidedWithTheStandPutAwayBody(h));
    }

    private static void loadedMemberShotStaysGuidedWithTheStandPutAwayBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = makeCompatibleMockPlayer(
                helper, "sex-pistols-put-away-shot");
        positionPlayer(helper, owner, 1, 2, 1);
        owner.setGameMode(GameType.SURVIVAL);
        owner.setNoGravity(true);
        StandPower power = prepareSexPistols(helper, owner);
        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(owner);
        ItemStack revolver = giveRevolverWithLiveRounds(
                helper, owner, 1, 0);
        Runnable finish = () -> cleanupOwner(level, owner, coordinator);
        power.getPowerType().forceUnsummon(owner, power);
        int[] tick = {0};
        int[] phase = {0};
        int[] phaseStart = {0};

        onEachTickInTickingChunk(helper, level, owner, finish, () -> {
            try {
                owner.doTick();
                tick[0]++;
                if (phase[0] == 0) {
                    if (!isPutAwayAndReady(
                            power, coordinator, revolver)) {
                        helper.assertTrue(
                                tick[0] <= 60,
                                "Sex Pistols was not put away within "
                                        + "60 ticks.");
                        return;
                    }
                    pressReload(
                            helper,
                            owner,
                            power,
                            SexPistolsIds.SEX_PISTOLS_STAND_RELOAD_PATH,
                            STAND_RELOAD_KEY,
                            "Stand Reload with Sex Pistols put away");
                    helper.assertTrue(
                            coordinator.loadedMemberMask() == 1
                                    && RevolverItem.closeReloadMode(
                                            level, owner, revolver),
                            "The fixture could not load member No.1 "
                                    + "with the Stand put away.");
                    // Straight up: the kick point, some 18 blocks out,
                    // stays inside the plot's own ticking chunk.
                    owner.setXRot(-90.0F);
                    ((RevolverItem) revolver.getItem()).use(
                            level, owner, InteractionHand.MAIN_HAND);
                    owner.releaseUsingItem();
                    List<RevolverBulletEntity> bullets =
                            level.getEntitiesOfClass(
                                    RevolverBulletEntity.class,
                                    new AABB(owner.blockPosition())
                                            .inflate(8.0D),
                                    bullet -> bullet.getOwner() == owner);
                    int bulletMask = bullets.size() == 1
                            ? bullets.getFirst().attachedMemberMask()
                            : -1;
                    helper.assertTrue(
                            bulletMask == 1
                                    && coordinator.inFlightMemberMask()
                                            == 1,
                            "A shot from a chamber carrying member No.1 "
                                    + "with the Stand put away is not "
                                    + "the donor's guided shot: bullets="
                                    + bullets.size()
                                    + ", bulletMemberMask=" + bulletMask
                                    + ", inFlightMask="
                                    + coordinator.inFlightMemberMask()
                                    + ", loadedMask="
                                    + coordinator.loadedMemberMask()
                                    + ".");
                    phase[0] = 1;
                    phaseStart[0] = tick[0];
                    return;
                }
                SexPistolsEntity member = coordinator.member(level, 1);
                if (phase[0] == 1) {
                    if (member == null) {
                        helper.assertTrue(
                                tick[0] - phaseStart[0] <= 12,
                                "The released member was not recreated "
                                        + "for the kick within 12 ticks "
                                        + "of the shot: summoned="
                                        + coordinator.isSummoned()
                                        + ", loadedMask="
                                        + coordinator.loadedMemberMask()
                                        + ", inFlightMask="
                                        + coordinator.inFlightMemberMask()
                                        + ".");
                        return;
                    }
                    double fromOwner = member.distanceTo(owner);
                    helper.assertTrue(
                            fromOwner > 5.0D
                                    && coordinator.isSummoned()
                                    && power.isSummoned()
                                    && coordinator.loadedMemberMask() == 0
                                    && coordinator.trackedMemberCount()
                                            == 1,
                            "The released member did not come out at the "
                                    + "kick point with the swarm marked "
                                    + "summoned: distanceFromOwner="
                                    + fromOwner + ", swarmSummoned="
                                    + coordinator.isSummoned()
                                    + ", powerSummoned="
                                    + power.isSummoned() + ", loadedMask="
                                    + coordinator.loadedMemberMask()
                                    + ", tracked="
                                    + coordinator.trackedMemberCount()
                                    + ".");
                    phase[0] = 2;
                    phaseStart[0] = tick[0];
                    return;
                }
                helper.assertTrue(
                        coordinator.trackedMemberCount() == 1
                                && coordinator.isSummoned(),
                        "Members that were never released came out "
                                + "after the shot: tracked="
                                + coordinator.trackedMemberCount()
                                + ", summoned=" + coordinator.isSummoned()
                                + ", ticksAfterRelease="
                                + (tick[0] - phaseStart[0]) + ".");
                if (tick[0] - phaseStart[0] < 10) {
                    return;
                }
                finish.run();
                helper.succeed();
            }
            catch (RuntimeException | Error failure) {
                finish.run();
                throw failure;
            }
        });
    }

    // 1.16 RevolverItem.releasePistolsFromChamber -> recallLoadedPistols ->
    // releaseLoadedPistol: taking the round out brings its member back out.
    @GameTest(
            templateNamespace = "rotp_addon_api_smoke",
            template = "empty",
            timeoutTicks = 160 + GameTestChunkTickets.WAIT_TICKS)
    public static void extractedMemberRoundBringsTheMemberOut(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> extractedMemberRoundBringsTheMemberOutBody(h));
    }

    private static void extractedMemberRoundBringsTheMemberOutBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = makeCompatibleMockPlayer(
                helper, "sex-pistols-put-away-extract");
        positionPlayer(helper, owner, 1, 2, 1);
        owner.setGameMode(GameType.SURVIVAL);
        owner.setNoGravity(true);
        StandPower power = prepareSexPistols(helper, owner);
        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(owner);
        ItemStack revolver = giveRevolverWithLiveRounds(
                helper, owner, 1, 0);
        Runnable finish = () -> cleanupOwner(level, owner, coordinator);
        power.getPowerType().forceUnsummon(owner, power);
        int[] tick = {0};
        int[] phase = {0};
        int[] phaseStart = {0};

        onEachTickInTickingChunk(helper, level, owner, finish, () -> {
            try {
                owner.doTick();
                tick[0]++;
                if (phase[0] == 0) {
                    if (!isPutAwayAndReady(
                            power, coordinator, revolver)) {
                        helper.assertTrue(
                                tick[0] <= 60,
                                "Sex Pistols was not put away within "
                                        + "60 ticks.");
                        return;
                    }
                    pressReload(
                            helper,
                            owner,
                            power,
                            SexPistolsIds.SEX_PISTOLS_STAND_RELOAD_PATH,
                            STAND_RELOAD_KEY,
                            "Stand Reload with Sex Pistols put away");
                    helper.assertTrue(
                            coordinator.loadedMemberMask() == 1
                                    && RevolverItem.state(revolver)
                                            .reloadMode(),
                            "The fixture could not load member No.1 "
                                    + "with the Stand put away.");
                    phase[0] = 1;
                    phaseStart[0] = tick[0];
                    return;
                }
                if (phase[0] == 1) {
                    if (RevolverItem.reloadCooldown(revolver) > 0) {
                        helper.assertTrue(
                                tick[0] - phaseStart[0] <= 30,
                                "The revolver reload cooldown did not "
                                        + "run out.");
                        return;
                    }
                    owner.setShiftKeyDown(true);
                    ((RevolverItem) revolver.getItem()).use(
                            level, owner, InteractionHand.MAIN_HAND);
                    owner.setShiftKeyDown(false);
                    SexPistolsEntity member =
                            coordinator.member(level, 1);
                    double fromOwner = member != null
                            ? member.distanceTo(owner)
                            : -1.0D;
                    helper.assertTrue(
                            member != null
                                    && fromOwner < 3.0D
                                    && coordinator.isSummoned()
                                    && power.isSummoned()
                                    && coordinator.loadedMemberMask() == 0
                                    && coordinator.trackedMemberCount()
                                            == 1
                                    && RevolverItem.state(revolver)
                                            .liveCount() == 0,
                            "Taking a round that carries member No.1 out "
                                    + "with the Stand put away did not "
                                    + "bring the member back out as in "
                                    + "the donor: memberOut="
                                    + (member != null)
                                    + ", distanceFromOwner=" + fromOwner
                                    + ", swarmSummoned="
                                    + coordinator.isSummoned()
                                    + ", powerSummoned="
                                    + power.isSummoned() + ", loadedMask="
                                    + coordinator.loadedMemberMask()
                                    + ", tracked="
                                    + coordinator.trackedMemberCount()
                                    + ", live="
                                    + RevolverItem.state(revolver)
                                            .liveCount() + ".");
                    phase[0] = 2;
                    phaseStart[0] = tick[0];
                    return;
                }
                helper.assertTrue(
                        coordinator.trackedMemberCount() == 1
                                && coordinator.isSummoned(),
                        "Members that were never released came out "
                                + "after the round was taken out: tracked="
                                + coordinator.trackedMemberCount()
                                + ", summoned=" + coordinator.isSummoned()
                                + ".");
                if (tick[0] - phaseStart[0] < 10) {
                    return;
                }
                finish.run();
                helper.succeed();
            }
            catch (RuntimeException | Error failure) {
                finish.run();
                throw failure;
            }
        });
    }

    // 1.16 SexPistolsPiercingShotAction (the other two have the same shape):
    // checkConditions asks only for the revolver, perform only for the swarm
    // effect, so the shot is primed with the Stand put away.
    @GameTest(
            templateNamespace = "rotp_addon_api_smoke",
            template = "empty",
            timeoutTicks = 100 + GameTestChunkTickets.WAIT_TICKS)
    public static void piercingShotPrimesWithTheStandPutAway(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> piercingShotPrimesWithTheStandPutAwayBody(h));
    }

    private static void piercingShotPrimesWithTheStandPutAwayBody(GameTestHelper helper) {
        specialShotPrimesWithTheStandPutAway(
                helper,
                SexPistolsIds.SEX_PISTOLS_PIERCING_SHOT_PATH,
                ShotType.PIERCING,
                PIERCING_KEY);
    }

    @GameTest(
            templateNamespace = "rotp_addon_api_smoke",
            template = "empty",
            timeoutTicks = 100 + GameTestChunkTickets.WAIT_TICKS)
    public static void splittingShotPrimesWithTheStandPutAway(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> splittingShotPrimesWithTheStandPutAwayBody(h));
    }

    private static void splittingShotPrimesWithTheStandPutAwayBody(GameTestHelper helper) {
        specialShotPrimesWithTheStandPutAway(
                helper,
                SexPistolsIds.SEX_PISTOLS_SPLITTING_SHOT_PATH,
                ShotType.SPLITTING,
                SPLITTING_KEY);
    }

    @GameTest(
            templateNamespace = "rotp_addon_api_smoke",
            template = "empty",
            timeoutTicks = 100 + GameTestChunkTickets.WAIT_TICKS)
    public static void encirclementPrimesWithTheStandPutAway(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> encirclementPrimesWithTheStandPutAwayBody(h));
    }

    private static void encirclementPrimesWithTheStandPutAwayBody(GameTestHelper helper) {
        specialShotPrimesWithTheStandPutAway(
                helper,
                SexPistolsIds.SEX_PISTOLS_ENCIRCLEMENT_PATH,
                ShotType.ENCIRCLEMENT,
                ENCIRCLEMENT_KEY);
    }

    private static void specialShotPrimesWithTheStandPutAway(
            GameTestHelper helper,
            String abilityPath,
            ShotType shotType,
            short key) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = makeCompatibleMockPlayer(
                helper, "sex-pistols-put-away-" + key);
        positionPlayer(helper, owner, 1, 2, 1);
        owner.setGameMode(GameType.SURVIVAL);
        owner.setNoGravity(true);
        StandPower power = prepareSexPistols(helper, owner);
        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(owner);
        ItemStack revolver = giveRevolverWithLiveRounds(
                helper, owner, 1, 0);
        Runnable finish = () -> cleanupOwner(level, owner, coordinator);
        power.getPowerType().forceUnsummon(owner, power);
        int[] tick = {0};

        onEachTickInTickingChunk(helper, level, owner, finish, () -> {
            try {
                owner.doTick();
                tick[0]++;
                if (!isPutAwayAndReady(power, coordinator, revolver)) {
                    helper.assertTrue(
                            tick[0] <= 60,
                            "Sex Pistols was not put away within 60 "
                                    + "ticks.");
                    return;
                }
                forgetSentPackets(owner);
                pressAbility(
                        helper,
                        owner,
                        power,
                        abilityPath,
                        key,
                        shotType + " with Sex Pistols put away");
                int chamber = RevolverItem.state(revolver)
                        .selectedChamber();
                int attached = RevolverMemberAttachments
                        .chamberMemberMask(revolver, chamber);
                List<String> heard = reloadSoundsAt(owner);
                List<String> donor = List.of(
                        "rotp_sex_pistols:revolver_open@0.85/1.0",
                        "rotp_sex_pistols:revolver_speedloader@0.65/1.15");
                helper.assertTrue(
                        RevolverSpecialChambers.has(
                                        revolver, chamber, shotType)
                                && attached == 0x3F
                                && coordinator.loadedMemberMask() == 0x3F
                                && heard.equals(donor)
                                && !coordinator.isSummoned()
                                && !power.isSummoned(),
                        shotType + " with the Stand put away did not "
                                + "prime the selected chamber with all "
                                + "six members as in the donor: primed="
                                + RevolverSpecialChambers.has(
                                        revolver, chamber, shotType)
                                + ", chamberMask=" + attached
                                + ", loadedMask="
                                + coordinator.loadedMemberMask()
                                + ", heard=" + heard + ", expected="
                                + donor + ", summoned="
                                + coordinator.isSummoned() + ".");
                finish.run();
                helper.succeed();
            }
            catch (RuntimeException | Error failure) {
                finish.run();
                throw failure;
            }
        });
    }

    // 1.16 special shots: without the swarm effect (never summoned) perform
    // answers with the dispenser click at 0.45 / 1.0 before anything opens.
    @GameTest(
            templateNamespace = "rotp_addon_api_smoke",
            template = "empty",
            timeoutTicks = 60 + GameTestChunkTickets.WAIT_TICKS)
    public static void specialShotBeforeTheFirstSummonOnlyClicks(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> specialShotBeforeTheFirstSummonOnlyClicksBody(h));
    }

    private static void specialShotBeforeTheFirstSummonOnlyClicksBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = makeCompatibleMockPlayer(
                helper, "sex-pistols-special-never-summoned");
        positionPlayer(helper, owner, 1, 2, 1);
        owner.setGameMode(GameType.SURVIVAL);
        owner.setNoGravity(true);
        StandPower power = StandPower.get(owner);
        power.setStand(SexPistolsStands.SEX_PISTOLS.get());
        power.skipProgression();
        power.setStamina(power.getMaxStamina());
        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(owner);
        ItemStack revolver = giveRevolverWithLiveRounds(
                helper, owner, 1, 0);
        int[] tick = {0};

        helper.onEachTick(() -> {
            try {
                owner.doTick();
                tick[0]++;
                if (RevolverItem.reloadCooldown(revolver) > 0) {
                    helper.assertTrue(
                            tick[0] <= 30,
                            "The revolver reload cooldown did not run out.");
                    return;
                }
                forgetSentPackets(owner);
                pressAbility(
                        helper,
                        owner,
                        power,
                        SexPistolsIds.SEX_PISTOLS_ENCIRCLEMENT_PATH,
                        ENCIRCLEMENT_KEY,
                        "Encirclement before the first summon");
                List<String> heard = reloadSoundsAt(owner);
                List<String> donor = List.of(
                        "minecraft:block.dispenser.fail@0.45/1.0");
                helper.assertTrue(
                        heard.equals(donor)
                                && coordinator.loadedMemberMask() == 0
                                && RevolverSpecialChambers
                                        .encoded(revolver) == 0
                                && !RevolverItem.state(revolver)
                                        .reloadMode(),
                        "Encirclement before the first summon differs "
                                + "from the donor (only the 0.45 fail "
                                + "click): heard=" + heard + ", expected="
                                + donor + ", loadedMask="
                                + coordinator.loadedMemberMask()
                                + ", open="
                                + RevolverItem.state(revolver).reloadMode()
                                + ".");
                cleanupOwner(level, owner, coordinator);
                helper.succeed();
            }
            catch (RuntimeException | Error failure) {
                cleanupOwner(level, owner, coordinator);
                throw failure;
            }
        });
    }

    // 1.16 special shots: an empty selected chamber with no ammunition opens
    // the cylinder (0.85 / 1.0) and then clicks (0.45 / 1.0); no member loads.
    @GameTest(
            templateNamespace = "rotp_addon_api_smoke",
            template = "empty",
            timeoutTicks = 100 + GameTestChunkTickets.WAIT_TICKS)
    public static void specialShotWithoutAmmunitionOpensThenClicks(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> specialShotWithoutAmmunitionOpensThenClicksBody(h));
    }

    private static void specialShotWithoutAmmunitionOpensThenClicksBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = makeCompatibleMockPlayer(
                helper, "sex-pistols-special-no-ammo");
        positionPlayer(helper, owner, 1, 2, 1);
        owner.setGameMode(GameType.SURVIVAL);
        owner.setNoGravity(true);
        StandPower power = prepareSexPistols(helper, owner);
        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(owner);
        ItemStack revolver = new ItemStack(
                SexPistolsItems.REVOLVER.get());
        owner.setItemSlot(EquipmentSlot.MAINHAND, revolver);
        Runnable finish = () -> cleanupOwner(level, owner, coordinator);
        power.getPowerType().forceUnsummon(owner, power);
        int[] tick = {0};

        onEachTickInTickingChunk(helper, level, owner, finish, () -> {
            try {
                owner.doTick();
                tick[0]++;
                if (!isPutAwayAndReady(power, coordinator, revolver)) {
                    helper.assertTrue(
                            tick[0] <= 60,
                            "Sex Pistols was not put away within 60 "
                                    + "ticks.");
                    return;
                }
                forgetSentPackets(owner);
                pressAbility(
                        helper,
                        owner,
                        power,
                        SexPistolsIds.SEX_PISTOLS_SPLITTING_SHOT_PATH,
                        SPLITTING_KEY,
                        "Splitting Shot without ammunition");
                List<String> heard = reloadSoundsAt(owner);
                List<String> donor = List.of(
                        "rotp_sex_pistols:revolver_open@0.85/1.0",
                        "minecraft:block.dispenser.fail@0.45/1.0");
                helper.assertTrue(
                        heard.equals(donor)
                                && coordinator.loadedMemberMask() == 0
                                && RevolverSpecialChambers
                                        .encoded(revolver) == 0
                                && RevolverItem.state(revolver)
                                        .reloadMode(),
                        "Splitting Shot without ammunition differs from "
                                + "the donor (open the cylinder, then the "
                                + "0.45 fail click, no member): heard="
                                + heard + ", expected=" + donor
                                + ", loadedMask="
                                + coordinator.loadedMemberMask()
                                + ", open="
                                + RevolverItem.state(revolver).reloadMode()
                                + ".");
                finish.run();
                helper.succeed();
            }
            catch (RuntimeException | Error failure) {
                finish.run();
                throw failure;
            }
        });
    }

    // 1.16 SexPistolsPiercingShotAction.perform (90-97) opens the cylinder
    // without ejectSpentCasings: only SexPistolsStandReloadAction (85) and the
    // manual toggle (323) eject, so a special shot leaves the casings in.
    @GameTest(
            templateNamespace = "rotp_addon_api_smoke",
            template = "empty",
            timeoutTicks = 100 + GameTestChunkTickets.WAIT_TICKS)
    public static void specialShotLeavesSpentCasingsInTheCylinder(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> specialShotLeavesSpentCasingsInTheCylinderBody(h));
    }

    private static void specialShotLeavesSpentCasingsInTheCylinderBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = makeCompatibleMockPlayer(
                helper, "sex-pistols-special-casings");
        positionPlayer(helper, owner, 1, 2, 1);
        owner.setGameMode(GameType.SURVIVAL);
        owner.setNoGravity(true);
        StandPower power = prepareSexPistols(helper, owner);
        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(owner);
        ItemStack revolver = giveRevolverWithLiveRounds(
                helper, owner, 2, 1);
        Runnable finish = () -> cleanupOwner(level, owner, coordinator);
        power.getPowerType().forceUnsummon(owner, power);
        int[] tick = {0};

        onEachTickInTickingChunk(helper, level, owner, finish, () -> {
            try {
                owner.doTick();
                tick[0]++;
                if (!isPutAwayAndReady(power, coordinator, revolver)) {
                    helper.assertTrue(
                            tick[0] <= 60,
                            "Sex Pistols was not put away within 60 "
                                    + "ticks.");
                    return;
                }
                RevolverState before = RevolverItem.state(revolver);
                int spentChamber = before.firstChamber(Chamber.SPENT);
                int selected = before.selectedChamber();
                forgetSentPackets(owner);
                pressAbility(
                        helper,
                        owner,
                        power,
                        SexPistolsIds.SEX_PISTOLS_PIERCING_SHOT_PATH,
                        PIERCING_KEY,
                        "Piercing Shot on a cylinder with a spent casing");
                RevolverState after = RevolverItem.state(revolver);
                List<String> heard = reloadSoundsAt(owner);
                int casingVisuals = casingVisualsSentTo(owner);
                List<String> donor = List.of(
                        "rotp_sex_pistols:revolver_open@0.85/1.0",
                        "rotp_sex_pistols:revolver_speedloader@0.65/1.15");
                helper.assertTrue(
                        heard.equals(donor)
                                && casingVisuals == 0
                                && after.reloadMode()
                                && after.spentCount() == 1
                                && after.chamber(spentChamber)
                                        == Chamber.SPENT
                                && after.selectedChamber() == selected
                                && RevolverSpecialChambers.has(
                                        revolver,
                                        selected,
                                        ShotType.PIERCING),
                        "Piercing Shot on a closed cylinder with one spent "
                                + "casing differs from the donor (open and "
                                + "prime only, the casing stays in its "
                                + "chamber): heard=" + heard + ", expected="
                                + donor + ", casingVisuals=" + casingVisuals
                                + ", spent=" + after.spentCount()
                                + ", chamber" + spentChamber + "="
                                + after.chamber(spentChamber)
                                + ", selected=" + after.selectedChamber()
                                + ", open=" + after.reloadMode()
                                + ", primed="
                                + RevolverSpecialChambers.has(
                                        revolver,
                                        selected,
                                        ShotType.PIERCING)
                                + ".");
                finish.run();
                helper.succeed();
            }
            catch (RuntimeException | Error failure) {
                finish.run();
                throw failure;
            }
        });
    }

    // 1.16 RevolverItem: loadAmmoIntoSelectedChamber (435), handleServerFillChamber
    // (381) and loadAvailableAmmoIntoEmptyChambers (410) all load any chamber
    // without a round, so a round goes in over a casing a special shot left.
    @GameTest(
            templateNamespace = "rotp_addon_api_smoke",
            template = "empty",
            timeoutTicks = 200 + GameTestChunkTickets.WAIT_TICKS)
    public static void roundsLoadOverCasingsASpecialShotLeftIn(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> roundsLoadOverCasingsASpecialShotLeftInBody(h));
    }

    private static void roundsLoadOverCasingsASpecialShotLeftInBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = makeCompatibleMockPlayer(
                helper, "sex-pistols-special-spent-load");
        positionPlayer(helper, owner, 1, 2, 1);
        owner.setGameMode(GameType.SURVIVAL);
        owner.setNoGravity(true);
        StandPower power = prepareSexPistols(helper, owner);
        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(owner);
        ItemStack revolver = new ItemStack(
                SexPistolsItems.REVOLVER.get());
        // A closed, fired-out cylinder: casings in chambers 0, 1 and 4.
        revolver.set(
                SexPistolsDataComponents.REVOLVER_STATE.get(),
                RevolverState.EMPTY
                        .withChamber(0, Chamber.SPENT)
                        .withChamber(1, Chamber.SPENT)
                        .withChamber(4, Chamber.SPENT));
        owner.setItemSlot(EquipmentSlot.MAINHAND, revolver);
        owner.getInventory().add(new ItemStack(
                SexPistolsItems.REVOLVER_AMMO.get()));
        Runnable finish = () -> cleanupOwner(level, owner, coordinator);
        power.getPowerType().forceUnsummon(owner, power);
        int[] tick = {0};
        int[] phase = {0};

        onEachTickInTickingChunk(helper, level, owner, finish, () -> {
            try {
                owner.doTick();
                tick[0]++;
                if (!isPutAwayAndReady(power, coordinator, revolver)) {
                    helper.assertTrue(
                            tick[0] <= 150,
                            "Sex Pistols was not put away or the reload "
                                    + "cooldown did not run out before "
                                    + "phase " + phase[0] + ".");
                    return;
                }
                forgetSentPackets(owner);
                if (phase[0] == 0) {
                    phase[0] = 1;
                    pressAbility(
                            helper,
                            owner,
                            power,
                            SexPistolsIds.SEX_PISTOLS_SPLITTING_SHOT_PATH,
                            SPLITTING_KEY,
                            "Splitting Shot on a spent selected chamber");
                    RevolverState state = RevolverItem.state(revolver);
                    List<String> heard = reloadSoundsAt(owner);
                    int casingVisuals = casingVisualsSentTo(owner);
                    List<String> donor = List.of(
                            "rotp_sex_pistols:revolver_open@0.85/1.0",
                            "rotp_sex_pistols:"
                                    + "revolver_speedloader@0.65/1.15");
                    helper.assertTrue(
                            heard.equals(donor)
                                    && casingVisuals == 0
                                    && state.reloadMode()
                                    && state.selectedChamber() == 0
                                    && state.chamber(0) == Chamber.LIVE
                                    && state.chamber(1) == Chamber.SPENT
                                    && state.chamber(4) == Chamber.SPENT
                                    && state.liveCount() == 1
                                    && ammunition(owner) == 0
                                    && RevolverSpecialChambers.has(
                                            revolver,
                                            0,
                                            ShotType.SPLITTING),
                            "Splitting Shot on a spent selected chamber "
                                    + "differs from the donor (open, load "
                                    + "the round over that casing, prime; "
                                    + "the other casings stay): heard="
                                    + heard + ", expected=" + donor
                                    + ", casingVisuals=" + casingVisuals
                                    + ", chambers=" + chambers(state)
                                    + ", selected="
                                    + state.selectedChamber()
                                    + ", open=" + state.reloadMode()
                                    + ", ammunition=" + ammunition(owner)
                                    + ", primed="
                                    + RevolverSpecialChambers.has(
                                            revolver,
                                            0,
                                            ShotType.SPLITTING)
                                    + ".");
                    owner.getInventory().add(new ItemStack(
                            SexPistolsItems.REVOLVER_AMMO.get()));
                    return;
                }
                if (phase[0] == 1) {
                    phase[0] = 2;
                    ((RevolverItem) revolver.getItem()).use(
                            level, owner, InteractionHand.MAIN_HAND);
                    RevolverState state = RevolverItem.state(revolver);
                    List<String> heard = revolverSoundsAt(owner);
                    List<String> donor = List.of(
                            "rotp_sex_pistols:"
                                    + "revolver_speedloader@0.85/1.0");
                    helper.assertTrue(
                            heard.equals(donor)
                                    && state.chamber(1) == Chamber.LIVE
                                    && state.chamber(2) == Chamber.EMPTY
                                    && state.chamber(4) == Chamber.SPENT
                                    && state.selectedChamber() == 2
                                    && ammunition(owner) == 0,
                            "Loading one round by hand next to a casing "
                                    + "differs from the donor (the round "
                                    + "goes into the next chamber without "
                                    + "a round, casing or not): heard="
                                    + heard + ", expected=" + donor
                                    + ", chambers=" + chambers(state)
                                    + ", selected="
                                    + state.selectedChamber()
                                    + ", ammunition=" + ammunition(owner)
                                    + ".");
                    owner.getInventory().add(new ItemStack(
                            SexPistolsItems.REVOLVER_AMMO.get(),
                            RevolverState.CHAMBER_COUNT));
                    return;
                }
                pressReload(
                        helper,
                        owner,
                        power,
                        SexPistolsIds.SEX_PISTOLS_QUICK_RELOAD_PATH,
                        QUICK_RELOAD_KEY,
                        "Quick Reload on an open cylinder with a casing");
                RevolverState state = RevolverItem.state(revolver);
                List<String> heard = reloadSoundsAt(owner);
                List<String> donor = List.of(
                        "rotp_sex_pistols:revolver_speedloader@0.65/1.35");
                helper.assertTrue(
                        heard.equals(donor)
                                && state.liveCount()
                                        == RevolverState.CHAMBER_COUNT
                                && state.spentCount() == 0
                                && state.selectedChamber() == 2
                                && ammunition(owner) == 2,
                        "Quick Reload on an open cylinder that still holds "
                                + "a casing differs from the donor (every "
                                + "chamber without a round is loaded, four "
                                + "rounds here): heard=" + heard
                                + ", expected=" + donor + ", chambers="
                                + chambers(state) + ", selected="
                                + state.selectedChamber()
                                + ", ammunition=" + ammunition(owner)
                                + ".");
                finish.run();
                helper.succeed();
            }
            catch (RuntimeException | Error failure) {
                finish.run();
                throw failure;
            }
        });
    }

    private static int ammunition(ServerPlayer owner) {
        return owner.getInventory().countItem(
                SexPistolsItems.REVOLVER_AMMO.get());
    }

    private static List<Chamber> chambers(RevolverState state) {
        List<Chamber> chambers = new ArrayList<>();
        for (int chamber = 0;
                chamber < RevolverState.CHAMBER_COUNT;
                chamber++) {
            chambers.add(state.chamber(chamber));
        }
        return chambers;
    }

    // The casing-eject visuals the player's own client was sent.
    private static int casingVisualsSentTo(ServerPlayer owner) {
        EmbeddedChannel channel = sentPackets(owner);
        channel.flushOutbound();
        int count = 0;
        for (Object message : channel.outboundMessages()) {
            if (message instanceof ClientboundCustomPayloadPacket packet
                    && packet.payload()
                            instanceof RevolverCasingEjectPayload) {
                count++;
            }
        }
        return count;
    }

    // 1.16 RevolverItem plays every manual action at a fixed volume and
    // pitch (the line numbers are the donor's): dry fire 0.75/1.0 (288), open
    // and close 0.85/1.0 (329), refused fill = dispenser click 0.45 at 1.6
    // (391, full) or 1.0 (396, no ammunition), speedloader 0.85/1.0 (403),
    // nothing to take out = dry fire 0.45/1.2 (466), round out 0.85/1.0
    // (476), chamber switch = item frame rotate 0.5 at 1.15 / 0.9 (496),
    // casings on open 0.75/1.0 (506), fire 0.95/1.0 (589).
    @GameTest(
            templateNamespace = "rotp_addon_api_smoke",
            template = "empty",
            timeoutTicks = 400 + GameTestChunkTickets.WAIT_TICKS)
    public static void manualRevolverActionsUseDonorSounds(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> manualRevolverActionsUseDonorSoundsBody(h));
    }

    private static void manualRevolverActionsUseDonorSoundsBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = makeCompatibleMockPlayer(
                helper, "sex-pistols-manual-sounds");
        positionPlayer(helper, owner, 1, 2, 1);
        owner.setGameMode(GameType.SURVIVAL);
        owner.setNoGravity(true);
        ItemStack revolver = new ItemStack(
                SexPistolsItems.REVOLVER.get());
        owner.setItemSlot(EquipmentSlot.MAINHAND, revolver);
        owner.getInventory().add(new ItemStack(
                SexPistolsItems.REVOLVER_AMMO.get()));
        RevolverItem item = (RevolverItem) revolver.getItem();
        Runnable rightClick = () ->
                item.use(level, owner, InteractionHand.MAIN_HAND);
        Runnable sneakRightClick = () -> {
            owner.setShiftKeyDown(true);
            item.use(level, owner, InteractionHand.MAIN_HAND);
            owner.setShiftKeyDown(false);
        };
        Runnable pullTrigger = () -> {
            owner.setYRot(0.0F);
            owner.setYHeadRot(0.0F);
            owner.setXRot(-10.0F);
            item.use(level, owner, InteractionHand.MAIN_HAND);
            owner.releaseUsingItem();
            RevolverBulletEntity.discardOwnedBy(level, owner.getUUID());
        };
        List<SoundStep> steps = List.of(
                new SoundStep(
                        "open the cylinder",
                        sneakRightClick,
                        List.of("rotp_sex_pistols:revolver_open@0.85/1.0")),
                new SoundStep(
                        "load one round",
                        rightClick,
                        List.of("rotp_sex_pistols:"
                                + "revolver_speedloader@0.85/1.0")),
                new SoundStep(
                        "load with no ammunition left",
                        rightClick,
                        List.of("minecraft:block.dispenser.fail@0.45/1.0")),
                new SoundStep(
                        "next chamber",
                        () -> RevolverItem.switchSelectedChamber(
                                level, owner, revolver, true),
                        List.of("minecraft:entity.item_frame."
                                + "rotate_item@0.5/1.15")),
                new SoundStep(
                        "previous chamber",
                        () -> RevolverItem.switchSelectedChamber(
                                level, owner, revolver, false),
                        List.of("minecraft:entity.item_frame."
                                + "rotate_item@0.5/0.9")),
                new SoundStep(
                        "take the live round out",
                        sneakRightClick,
                        List.of("rotp_sex_pistols:"
                                + "revolver_shellsout@0.85/1.0")),
                new SoundStep(
                        "take a round out of an empty cylinder",
                        sneakRightClick,
                        List.of("rotp_sex_pistols:dry_fire@0.45/1.2")),
                new SoundStep(
                        "close the cylinder",
                        () -> RevolverItem.closeReloadMode(
                                level, owner, revolver),
                        List.of("rotp_sex_pistols:revolver_close@0.85/1.0")),
                new SoundStep(
                        "dry fire",
                        pullTrigger,
                        List.of("rotp_sex_pistols:dry_fire@0.75/1.0")),
                new SoundStep(
                        "fixture: open and fill all six chambers",
                        () -> {
                            owner.getInventory().add(new ItemStack(
                                    SexPistolsItems.REVOLVER_AMMO.get(),
                                    5));
                            sneakRightClick.run();
                            int loaded = RevolverItem
                                    .loadAvailableAmmoIntoEmptyChambers(
                                            owner, revolver, 6, true);
                            helper.assertTrue(
                                    loaded == RevolverState.CHAMBER_COUNT,
                                    "The fixture filled " + loaded
                                            + " chambers instead of six.");
                        },
                        null),
                new SoundStep(
                        "load into a full cylinder",
                        rightClick,
                        List.of("minecraft:block.dispenser.fail@0.45/1.6")),
                new SoundStep(
                        "fixture: close the cylinder",
                        () -> RevolverItem.closeReloadMode(
                                level, owner, revolver),
                        null),
                new SoundStep(
                        "fire",
                        pullTrigger,
                        List.of("rotp_sex_pistols:fire@0.95/1.0")),
                new SoundStep(
                        "open the cylinder on a spent casing",
                        sneakRightClick,
                        List.of(
                                "rotp_sex_pistols:"
                                        + "revolver_shellsout@0.75/1.0",
                                "rotp_sex_pistols:"
                                        + "revolver_open@0.85/1.0")));
        int[] next = {0};
        int[] waited = {0};
        List<String> differences = new ArrayList<>();

        helper.onEachTick(() -> {
            try {
                owner.doTick();
                if (RevolverItem.reloadCooldown(revolver) > 0
                        || RevolverItem.shotCooldown(revolver) > 0) {
                    helper.assertTrue(
                            ++waited[0] <= 30,
                            "The revolver cooldowns did not run out "
                                    + "before step " + next[0] + ".");
                    return;
                }
                waited[0] = 0;
                SoundStep step = steps.get(next[0]++);
                forgetSentPackets(owner);
                step.action().run();
                List<String> heard = revolverSoundsAt(owner);
                if (step.donor() != null
                        && !heard.equals(step.donor())) {
                    differences.add(step.name() + ": heard=" + heard
                            + ", donor=" + step.donor());
                }
                if (next[0] < steps.size()) {
                    return;
                }
                helper.assertTrue(
                        differences.isEmpty(),
                        differences.size() + " manual revolver action(s) "
                                + "differ from the donor sounds: "
                                + String.join("; ", differences) + ".");
                RevolverBulletEntity.discardOwnedBy(
                        level, owner.getUUID());
                owner.connection.disconnect(Component.literal(
                        "Sex Pistols manual sounds GameTest complete"));
                helper.succeed();
            }
            catch (RuntimeException | Error failure) {
                RevolverBulletEntity.discardOwnedBy(
                        level, owner.getUUID());
                owner.connection.disconnect(Component.literal(
                        "Sex Pistols manual sounds GameTest complete"));
                throw failure;
            }
        });
    }

    private record SoundStep(
            String name, Runnable action, List<String> donor) {}

    private static boolean isPutAwayAndReady(
            StandPower power,
            SexPistolsCoordinator coordinator,
            ItemStack revolver) {
        return !power.isSummoned()
                && !coordinator.isSummoned()
                && coordinator.trackedMemberCount() == 0
                && RevolverItem.reloadCooldown(revolver) <= 0;
    }

    private static void pressAbility(
            GameTestHelper helper,
            ServerPlayer owner,
            StandPower power,
            String abilityPath,
            short key,
            String what) {
        Ability ability = power.getAbility(abilityPath);
        helper.assertTrue(
                ability != null
                        && AbilityInput.withConditionCheck(
                                ability, owner),
                "Production ability input rejected " + what + ".");
        FriendlyByteBuf input = new FriendlyByteBuf(
                Unpooled.buffer());
        try {
            AbilityInput.keyPress(
                    key,
                    ability,
                    owner,
                    input,
                    InputMethod.CLICK,
                    0.0F,
                    BufferingState.clickCanBuffer(),
                    ability.abilityId);
        }
        finally {
            input.release();
        }
    }

    // Every revolver sound of the manual path the player's client was sent
    // from its own position, in order, as id@volume/pitch.
    private static List<String> revolverSoundsAt(ServerPlayer owner) {
        EmbeddedChannel channel = sentPackets(owner);
        channel.flushOutbound();
        List<SoundEvent> manual = List.of(
                SexPistolsSounds.REVOLVER_OPEN.get(),
                SexPistolsSounds.REVOLVER_CLOSE.get(),
                SexPistolsSounds.REVOLVER_SHELLS_OUT.get(),
                SexPistolsSounds.REVOLVER_SPEEDLOADER.get(),
                SexPistolsSounds.DRY_FIRE.get(),
                SexPistolsSounds.FIRE.get(),
                SoundEvents.DISPENSER_FAIL,
                SoundEvents.ITEM_FRAME_ROTATE_ITEM,
                SoundEvents.UI_BUTTON_CLICK.value());
        List<String> heard = new ArrayList<>();
        for (Object message : channel.outboundMessages()) {
            if (!(message instanceof ClientboundSoundPacket sound)) {
                continue;
            }
            SoundEvent event = sound.getSound().value();
            if (!manual.contains(event)
                    || Math.hypot(
                            sound.getX() - owner.getX(),
                            sound.getZ() - owner.getZ()) >= 1.0D) {
                continue;
            }
            heard.add(BuiltInRegistries.SOUND_EVENT.getKey(event)
                    + "@" + sound.getVolume()
                    + "/" + sound.getPitch());
        }
        return heard;
    }

    private static ItemStack giveRevolverWithLiveRounds(
            GameTestHelper helper,
            ServerPlayer owner,
            int liveRounds,
            int firedRounds) {
        ServerLevel level = helper.getLevel();
        ItemStack revolver = new ItemStack(
                SexPistolsItems.REVOLVER.get());
        owner.setItemSlot(EquipmentSlot.MAINHAND, revolver);
        owner.getInventory().add(new ItemStack(
                SexPistolsItems.REVOLVER_AMMO.get(), liveRounds));
        helper.assertTrue(
                RevolverItem.openForStandReload(
                                level, owner, revolver, true, true)
                        && RevolverItem.loadAvailableAmmoIntoEmptyChambers(
                                owner, revolver, liveRounds, true)
                                == liveRounds
                        && RevolverItem.closeReloadMode(
                                level, owner, revolver),
                "The production revolver could not be loaded.");
        for (int shot = 0; shot < firedRounds; shot++) {
            ((RevolverItem) revolver.getItem()).use(
                    level, owner, InteractionHand.MAIN_HAND);
            owner.releaseUsingItem();
            RevolverBulletEntity.discardOwnedBy(level, owner.getUUID());
        }
        RevolverState state = RevolverItem.state(revolver);
        helper.assertTrue(
                state.spentCount() == firedRounds
                        && state.liveCount() == liveRounds - firedRounds
                        && state.chamber(state.selectedChamber())
                                == Chamber.LIVE
                        && !state.reloadMode(),
                "The revolver fixture is not closed on a live round with "
                        + firedRounds + " spent casing(s): live="
                        + state.liveCount() + ", spent="
                        + state.spentCount() + ".");
        return revolver;
    }

    private static void pressReload(
            GameTestHelper helper,
            ServerPlayer owner,
            StandPower power,
            String abilityPath,
            short key,
            String what) {
        Ability ability = power.getAbility(abilityPath);
        helper.assertTrue(
                ability instanceof SexPistolsReloadAbility
                        && AbilityInput.withConditionCheck(
                                ability, owner),
                "Production ability input rejected " + what + ".");
        FriendlyByteBuf input = new FriendlyByteBuf(
                Unpooled.buffer());
        try {
            AbilityInput.keyPress(
                    key,
                    ability,
                    owner,
                    input,
                    InputMethod.CLICK,
                    0.0F,
                    BufferingState.clickCanBuffer(),
                    ability.abilityId);
        }
        finally {
            input.release();
        }
    }

    private static EmbeddedChannel sentPackets(ServerPlayer player) {
        return (EmbeddedChannel) player.connection
                .getConnection()
                .channel();
    }

    private static void forgetSentPackets(ServerPlayer player) {
        EmbeddedChannel channel = sentPackets(player);
        channel.flushOutbound();
        channel.outboundMessages().clear();
    }

    // The revolver and fail-click sounds the player's client was sent from
    // its own position, in order, as id@volume/pitch.
    private static List<String> reloadSoundsAt(ServerPlayer owner) {
        EmbeddedChannel channel = sentPackets(owner);
        channel.flushOutbound();
        List<String> heard = new ArrayList<>();
        for (Object message : channel.outboundMessages()) {
            if (!(message instanceof ClientboundSoundPacket sound)) {
                continue;
            }
            SoundEvent event = sound.getSound().value();
            if (event != SexPistolsSounds.REVOLVER_OPEN.get()
                    && event != SexPistolsSounds
                            .REVOLVER_SHELLS_OUT.get()
                    && event != SexPistolsSounds
                            .REVOLVER_SPEEDLOADER.get()
                    && event != SoundEvents.DISPENSER_FAIL) {
                continue;
            }
            if (Math.hypot(
                    sound.getX() - owner.getX(),
                    sound.getZ() - owner.getZ()) >= 1.0D) {
                continue;
            }
            heard.add(BuiltInRegistries.SOUND_EVENT.getKey(event)
                    + "@" + sound.getVolume()
                    + "/" + sound.getPitch());
        }
        return heard;
    }

    private static void quickReloadThroughAbility(
            GameTestHelper helper,
            ServerPlayer owner,
            StandPower power) {
        Ability ability = power.getAbility(
                SexPistolsIds.SEX_PISTOLS_QUICK_RELOAD_PATH);
        helper.assertTrue(
                ability instanceof SexPistolsReloadAbility
                        && AbilityInput.withConditionCheck(
                                ability, owner),
                "Production ability input rejected the quick reload.");
        FriendlyByteBuf input = new FriendlyByteBuf(
                Unpooled.buffer());
        try {
            AbilityInput.keyPress(
                    QUICK_RELOAD_KEY,
                    ability,
                    owner,
                    input,
                    InputMethod.CLICK,
                    0.0F,
                    BufferingState.clickCanBuffer(),
                    ability.abilityId);
        }
        finally {
            input.release();
        }
    }

    // 1.16: a summoned pistol idles at its own random place around the user
    // (SexPistolsStandType), feet-relative and turned with the view yaw, and
    // a recalled one at its fixed place (SexPistolsEntities.getDefaultOffset);
    // only idle pistols follow the user offset.
    @GameTest(
            templateNamespace = "rotp_addon_api_smoke",
            template = "empty",
            timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void idlePistolsKeepTheDonorPlaces(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> idlePistolsKeepTheDonorPlacesBody(h));
    }

    private static void idlePistolsKeepTheDonorPlacesBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = makeCompatibleMockPlayer(
                helper, "sex-pistols-idle-places");
        positionPlayer(helper, owner, 1, 2, 1);
        owner.setNoGravity(true);
        owner.setYRot(90.0F);
        owner.setYHeadRot(90.0F);
        owner.yBodyRot = 0.0F;
        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(owner);
        try {
            prepareSexPistols(helper, owner);
        }
        catch (RuntimeException | Error failure) {
            cleanupOwner(level, owner, coordinator);
            throw failure;
        }
        helper.runAfterDelay(2, () -> {
            try {
                for (int slot = 0; slot < SexPistolsCoordinator.MEMBER_COUNT; slot++) {
                    SexPistolsEntity member = coordinator.member(
                            level, SexPistolsIds.SEX_PISTOLS_NUMBERS[slot]);
                    Vec3 offset = member.getIdleOffset();
                    double radius = Math.hypot(offset.x, offset.z);
                    double layerY = 0.15D + slot % 3 / 2.0D * 1.15D;
                    helper.assertTrue(
                            member.isFollowingUser()
                                    && radius >= 0.85D - 1.0E-4D
                                    && radius <= 1.4D + 1.0E-4D
                                    && Math.abs(offset.y - layerY)
                                            <= 0.175D + 1.0E-4D,
                            "Pistol slot " + slot + " idles off the donor "
                                    + "summon spread: " + offset);
                    assertIdleAt(helper, owner, member, offset, slot);
                    // The core measures the offset from the user's feet, as
                    // 1.16 did, so the donor height goes in unchanged.
                    helper.assertTrue(
                            member.offsetFromUser.idleOffset.equals(offset)
                                    && member.Y_OFFSET == offset.y,
                            "Pistol slot " + slot + " converts its donor "
                                    + "place " + offset + " to "
                                    + member.offsetFromUser.idleOffset
                                    + ", default y " + member.Y_OFFSET);
                    member.setCommandTarget(
                            member.position().add(3.0D, 0.0D, 0.0D));
                    member.setMemberState(SexPistolsMemberState.SCOUTING);
                }
            }
            catch (RuntimeException | Error failure) {
                cleanupOwner(level, owner, coordinator);
                throw failure;
            }
        });
        helper.runAfterDelay(6, () -> {
            try {
                for (int slot = 0; slot < SexPistolsCoordinator.MEMBER_COUNT; slot++) {
                    SexPistolsEntity member = coordinator.member(
                            level, SexPistolsIds.SEX_PISTOLS_NUMBERS[slot]);
                    Vec3 idle = expectedIdlePosition(
                            owner, member.getIdleOffset());
                    helper.assertTrue(
                            !member.isFollowingUser()
                                    && member.position().distanceTo(idle) > 1.0D,
                            "A scouting pistol is held on the user offset: slot "
                                    + slot + " is "
                                    + member.position().distanceTo(idle)
                                    + " from its idle place.");
                }
                coordinator.recallAttachmentsAndScouts(level, owner);
            }
            catch (RuntimeException | Error failure) {
                cleanupOwner(level, owner, coordinator);
                throw failure;
            }
        });
        helper.runAfterDelay(8, () -> {
            try {
                for (int slot = 0; slot < SexPistolsCoordinator.MEMBER_COUNT; slot++) {
                    SexPistolsEntity member = coordinator.member(
                            level, SexPistolsIds.SEX_PISTOLS_NUMBERS[slot]);
                    double angle = Math.PI * 2.0D * slot / 6.0D;
                    Vec3 fixed = new Vec3(
                            Math.cos(angle) * 1.05D,
                            0.15D + slot % 3 * 0.575D,
                            Math.sin(angle) * 1.05D);
                    helper.assertTrue(
                            member.getIdleOffset().distanceTo(fixed) < 1.0E-5D,
                            "A recalled pistol did not take its fixed donor "
                                    + "place: slot " + slot + " has "
                                    + member.getIdleOffset());
                    assertIdleAt(helper, owner, member, fixed, slot);
                }
                helper.succeed();
            }
            finally {
                cleanupOwner(level, owner, coordinator);
            }
        });
    }

    // The view yaw is 90 (facing -x): the user's left is +z, forward is -x.
    private static Vec3 expectedIdlePosition(ServerPlayer owner, Vec3 feetOffset) {
        return owner.position().add(-feetOffset.z, feetOffset.y, feetOffset.x);
    }

    private static void assertIdleAt(
            GameTestHelper helper,
            ServerPlayer owner,
            SexPistolsEntity member,
            Vec3 feetOffset,
            int slot) {
        Vec3 expected = expectedIdlePosition(owner, feetOffset);
        helper.assertTrue(
                member.position().distanceTo(expected) < 1.0E-3D,
                "Pistol slot " + slot + " idles at "
                        + member.position().subtract(owner.position())
                        + " from the user's feet, expected "
                        + expected.subtract(owner.position()));
    }

    // 1.16 InitStands :95-104: every Sex Pistols control was a StandAction,
    // which ended the power's held action once it went off
    // (PowerBaseImpl.onClickAction, stopHeldAction(false)) and was refused by
    // a held action that let none through. The member selection was the
    // mouse wheel (SexPistolsClientEvents.onMouseScroll :66-89,
    // SwitchSexPistolsSelectionPacket), no action, so it did neither.
    @GameTest(
            templateNamespace = "rotp_addon_api_smoke",
            template = "empty")
    public static void onlyTheMemberSelectionLeavesAHeldActionAloneAsIn116(
            GameTestHelper helper) {
        ServerPlayer owner = makeCompatibleMockPlayer(
                helper, "sex-pistols-held-press");
        try {
            StandPower power = StandPower.get(owner);
            helper.assertTrue(
                    power != null,
                    "The mock owner has no Stand power attachment.");
            power.setStand(SexPistolsStands.SEX_PISTOLS.get());
            power.skipProgression();
            List<String> wrong = new ArrayList<>();
            for (String path : List.of(
                    SexPistolsIds.SEX_PISTOLS_SELECT_PATH,
                    SexPistolsIds.SEX_PISTOLS_TARGET_MODE_PATH,
                    SexPistolsIds.SEX_PISTOLS_TRANSFER_ORDER_PATH,
                    SexPistolsIds.SEX_PISTOLS_MOVE_SELECTED_PATH,
                    SexPistolsIds.SEX_PISTOLS_STAND_RELOAD_PATH,
                    SexPistolsIds.SEX_PISTOLS_QUICK_RELOAD_PATH,
                    SexPistolsIds.SEX_PISTOLS_RECALL_PATH,
                    SexPistolsIds.SEX_PISTOLS_TRAJECTORY_VISION_PATH,
                    SexPistolsIds.SEX_PISTOLS_PIERCING_SHOT_PATH,
                    SexPistolsIds.SEX_PISTOLS_SPLITTING_SHOT_PATH,
                    SexPistolsIds.SEX_PISTOLS_ENCIRCLEMENT_PATH)) {
                Ability ability = power.getAbility(path);
                boolean donorAction = !path.equals(
                        SexPistolsIds.SEX_PISTOLS_SELECT_PATH);
                if (ability == null
                        || ability.endsOtherHoldOnPress() != donorAction) {
                    wrong.add(path + "="
                            + (ability == null
                                    ? "missing"
                                    : ability.endsOtherHoldOnPress()));
                }
            }
            helper.assertTrue(
                    wrong.isEmpty(),
                    "Sex Pistols abilities whose press ends (true) or "
                            + "leaves (false) the power's held action "
                            + "differ from 1.16 (only the member "
                            + "selection, the mouse wheel, leaves it): "
                            + wrong);
            helper.succeed();
        }
        finally {
            owner.connection.disconnect(Component.literal(
                    "Sex Pistols held press GameTest complete"));
        }
    }

    // 1.16 InitStands :95 built sex_pistols_trajectory_vision as a bare
    // StandAction.Builder: no cooldown, so the toggle could be pressed again
    // at once. The port took cooldown(4) after every toggle.
    @GameTest(
            templateNamespace = "rotp_addon_api_smoke",
            template = "empty")
    public static void trajectoryVisionTakesNoCooldownAsIn116(
            GameTestHelper helper) {
        ServerPlayer owner = makeCompatibleMockPlayer(
                helper, "sex-pistols-trajectory-vision");
        try {
            StandPower power = StandPower.get(owner);
            helper.assertTrue(
                    power != null,
                    "The mock owner has no Stand power attachment.");
            power.setStand(SexPistolsStands.SEX_PISTOLS.get());
            power.skipProgression();
            // a Creative user takes no plain cooldown whatever it is
            owner.setGameMode(GameType.SURVIVAL);
            String path = SexPistolsIds.SEX_PISTOLS_TRAJECTORY_VISION_PATH;
            Ability vision = power.getAbility(path);
            helper.assertTrue(
                    vision != null && !power.isUserCreative(),
                    "Setup: Sex Pistols has no trajectory vision, or its "
                            + "user is in Creative.");
            SexPistolsCoordinator coordinator =
                    SexPistolsAttachments.coordinator(owner);
            boolean shown = coordinator.trajectoryVision();
            for (int press = 1; press <= 3; press++) {
                helper.assertTrue(
                        vision.checkConditions(power).isPositive(),
                        "1.16 refused no press of the trajectory vision "
                                + "(no cooldown), but press " + press
                                + " is refused, cooldown "
                                + power.getAbilityCooldown(path));
                BufferingState state = BufferingState.clickCanBuffer();
                FriendlyByteBuf input =
                        new FriendlyByteBuf(Unpooled.buffer());
                try {
                    AbilityInput.keyPress(
                            VISION_KEY,
                            vision,
                            owner,
                            input,
                            InputMethod.CLICK,
                            0.0F,
                            state,
                            vision.abilityId);
                }
                finally {
                    input.release();
                }
                AbilityInput.keyRelease(VISION_KEY, owner);
                shown = !shown;
                helper.assertTrue(
                        state.isActionSuccess
                                && coordinator.trajectoryVision() == shown
                                && !power.isAbilityOnCooldown(path)
                                && power.getAbilityCooldown(path) == 0,
                        "1.16 set no cooldown for the trajectory vision "
                                + "(a bare StandAction.Builder): press "
                                + press + " went off "
                                + state.isActionSuccess + ", shown "
                                + coordinator.trajectoryVision()
                                + " (expected " + shown + "), cooldown "
                                + power.getAbilityCooldown(path));
            }
            helper.succeed();
        }
        finally {
            owner.connection.disconnect(Component.literal(
                    "Sex Pistols trajectory vision GameTest complete"));
        }
    }

    // 1.16 SexPistolsMoveSelectedAction (Next Album 0.1.11) was a holdType
    // StandAction: held tick 1 took the member's own position and the user's
    // look (onHoldTick :93-131), each later held tick moved the member 0.36
    // further along that line, up to 10 (tickMovingState :198-214), and the
    // release stopped it where it was, fixed, so the next press recalled it
    // (stoppedHolding :134-142). Its cooldown came as the hold ended
    // (PowerBaseImpl.stopHeldAction), and while it was held the power's other
    // actions were refused (checkRequirements :255); the member selection,
    // the mouse wheel and no action, was not. Its own batch: a neighbour's
    // item on the user's line would turn the first press into a retrieval.
    @GameTest(
            templateNamespace = "rotp_addon_api_smoke",
            template = "empty",
            batch = "sexPistolsMoveHold",
            timeoutTicks = 160 + GameTestChunkTickets.WAIT_TICKS)
    public static void moveSelectedFliesTheMemberOnlyWhileItsKeyIsHeldAsIn116(
            GameTestHelper helper) {
        GameTestChunkTickets.forTest(helper)
                .coverAround(
                        helper.absolutePos(BlockPos.ZERO),
                        MOVE_FIXTURE_REACH)
                .whenTicking(() -> {}, h -> moveSelectedHoldBody(h));
    }

    private static void moveSelectedHoldBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = makeCompatibleMockPlayer(
                helper, "sex-pistols-move-hold");
        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(owner);
        MoveHoldScene scene;
        try {
            positionPlayer(helper, owner, 1, 2, 1);
            owner.setGameMode(GameType.SURVIVAL);
            owner.setNoGravity(true);
            // level, along +x
            owner.setYRot(-90.0F);
            owner.setYHeadRot(-90.0F);
            owner.yBodyRot = -90.0F;
            owner.setXRot(0.0F);
            scene = new MoveHoldScene(
                    helper,
                    owner,
                    prepareSexPistols(helper, owner),
                    coordinator);
        }
        catch (RuntimeException | Error failure) {
            cleanupOwner(level, owner, coordinator);
            throw failure;
        }
        helper.onEachTick(() -> {
            try {
                if (scene.tick()) {
                    cleanupOwner(level, owner, coordinator);
                    helper.succeed();
                }
            }
            catch (RuntimeException | Error failure) {
                cleanupOwner(level, owner, coordinator);
                throw failure;
            }
        });
    }

    private record MovePress(
            @Nullable ConditionCheck check, BufferingState state) {}

    private static final class MoveHoldScene {
        private final GameTestHelper helper;
        private final ServerPlayer owner;
        private final StandPower power;
        private final SexPistolsCoordinator coordinator;
        private SexPistolsEntity member;
        private EntityActionInstance hold;
        private Vec3 look = Vec3.ZERO;
        private Vec3 pressedAt = Vec3.ZERO;
        private Vec3 origin = Vec3.ZERO;
        private Vec3 stoppedAt = Vec3.ZERO;
        private double along;
        private int heldTicks;
        private int stage;
        private int stageTicks;

        private MoveHoldScene(
                GameTestHelper helper,
                ServerPlayer owner,
                StandPower power,
                SexPistolsCoordinator coordinator) {
            this.helper = helper;
            this.owner = owner;
            this.power = power;
            this.coordinator = coordinator;
        }

        // true once every row has passed
        private boolean tick() {
            stageTicks++;
            switch (stage) {
                case 0 -> {
                    if (stageTicks < 3) {
                        return false;
                    }
                    member = coordinator.member(owner.serverLevel(), 1);
                    helper.assertTrue(
                            coordinator.selectedNumber() == 1
                                    && member != null
                                    && member.getMemberState()
                                            == SexPistolsMemberState.FORMATION
                                    && SexPistolsMoveSelectedAbility
                                            .findLookedAtItem(owner, 10.0D)
                                            == null,
                            "setup: No. 1 is not the selected idle member, "
                                    + "or an item lies on the user's line.");
                    startHold("the first press");
                    next(1);
                }
                case 1 -> {
                    flightTick();
                    if (heldTicks == 6) {
                        helper.assertTrue(
                                !power.isAbilityOnCooldown(
                                        SexPistolsIds
                                                .SEX_PISTOLS_MOVE_SELECTED_PATH),
                                "1.16 set no cooldown while the move was "
                                        + "held: " + moveCooldown());
                        assertOthersRefused("during the flight");
                    }
                    if (heldTicks == 12) {
                        release();
                        next(2);
                    }
                }
                case 2 -> {
                    assertStays("after the release");
                    if (stageTicks == 1) {
                        SexPistolsTargetMode mode = coordinator.targetMode();
                        MovePress after = press(
                                SexPistolsIds.SEX_PISTOLS_TARGET_MODE_PATH,
                                InputMethod.CLICK,
                                OTHER_KEY);
                        AbilityInput.keyRelease(OTHER_KEY, owner);
                        helper.assertTrue(
                                after.state().isActionSuccess
                                        && coordinator.targetMode() != mode,
                                "setup: the target mode does not go off "
                                        + "once the move is let go.");
                    }
                    if (stageTicks == 8) {
                        helper.assertTrue(
                                !power.isAbilityOnCooldown(
                                        SexPistolsIds
                                                .SEX_PISTOLS_MOVE_SELECTED_PATH),
                                "setup: the move is still cooling down: "
                                        + moveCooldown());
                        startHold("the press after the release");
                        next(3);
                    }
                }
                case 3 -> {
                    helper.assertTrue(
                            (int) hold.getPhaseTick() == 1
                                    && member.getMemberState()
                                            == SexPistolsMemberState.FORMATION
                                    && hold.getPhase() == ActionPhase.PERFORM,
                            "1.16 recalled the member the release had left "
                                    + "fixed on the next press "
                                    + "(returnToUserAfterKick): state "
                                    + member.getMemberState() + ", the hold in "
                                    + hold.getPhase());
                    release();
                    next(4);
                }
                case 4 -> {
                    if (stageTicks < 6) {
                        return false;
                    }
                    helper.assertTrue(
                            !power.isAbilityOnCooldown(
                                    SexPistolsIds
                                            .SEX_PISTOLS_MOVE_SELECTED_PATH)
                                    && member.getMemberState()
                                            == SexPistolsMemberState.FORMATION
                                    && member.isFollowingUser(),
                            "setup: the recalled No. 1 is not back in the "
                                    + "formation: " + member.getMemberState());
                    startHold("the long hold");
                    next(5);
                }
                case 5 -> {
                    flightTick();
                    if (heldTicks == 8) {
                        MovePress select = press(
                                SexPistolsIds.SEX_PISTOLS_SELECT_PATH,
                                InputMethod.CLICK,
                                OTHER_KEY);
                        AbilityInput.keyRelease(OTHER_KEY, owner);
                        helper.assertTrue(
                                select.state().isActionSuccess
                                        && coordinator.selectedNumber() != 1
                                        && hold.getPhase() == ActionPhase.PERFORM
                                        && AbilityInput.isHeldByKey(owner, hold),
                                "1.16 picked the member with the mouse wheel, "
                                        + "no action, during the held move: "
                                        + "went off "
                                        + select.state().isActionSuccess
                                        + ", selected No. "
                                        + coordinator.selectedNumber()
                                        + ", the hold in " + hold.getPhase());
                    }
                    if (heldTicks == 34) {
                        helper.assertTrue(
                                along == DONOR_MOVE_MAX_DISTANCE
                                        && hold.getPhase() == ActionPhase.PERFORM
                                        && AbilityInput.isHeldByKey(owner, hold),
                                "1.16 stopped the flight at 10 blocks and "
                                        + "held the move until its release: "
                                        + "the hold in " + hold.getPhase());
                        assertOthersRefused("after the flight reached 10 blocks");
                        release();
                        next(6);
                    }
                }
                case 6 -> {
                    assertStays("after the long hold");
                    return stageTicks >= 3;
                }
                default -> throw new IllegalStateException(
                        "Unknown stage " + stage);
            }
            return false;
        }

        private void next(int nextStage) {
            stage = nextStage;
            stageTicks = 0;
        }

        private void startHold(String which) {
            look = owner.getLookAngle();
            pressedAt = member.position();
            MovePress pressed = press(
                    SexPistolsIds.SEX_PISTOLS_MOVE_SELECTED_PATH,
                    InputMethod.HOLD,
                    MOVE_KEY);
            hold = LivingComponentAction.getCurEntityAction(owner, PowerClass.STAND);
            helper.assertTrue(
                    pressed.state().isActionSuccess
                            && hold instanceof SexPistolsMoveSelectedAbility
                                    .MoveSelectedAction
                            && hold.getPhase() == ActionPhase.PERFORM
                            && AbilityInput.isHeldByKey(owner, hold),
                    "1.16 held Move No. 1 from its press (holdType): "
                            + which + " went off "
                            + pressed.state().isActionSuccess
                            + ", the user's action " + hold);
            heldTicks = 0;
            along = 0.0D;
        }

        private void flightTick() {
            int ticks = (int) hold.getPhaseTick();
            helper.assertTrue(
                    ticks == heldTicks + 1
                            && hold.getPhase() == ActionPhase.PERFORM,
                    "setup: the held move ticked to " + ticks + " after "
                            + heldTicks + ", phase " + hold.getPhase());
            heldTicks = ticks;
            if (heldTicks == 1) {
                origin = member.position();
                helper.assertTrue(
                        origin.distanceTo(pressedAt) < 1.0E-3D
                                && member.getMemberState()
                                        == SexPistolsMemberState.SCOUTING,
                        "1.16 fixed the member where it was on the first "
                                + "held tick: it is "
                                + origin.distanceTo(pressedAt)
                                + " from where the key was pressed, state "
                                + member.getMemberState());
                return;
            }
            along = Math.min(
                    DONOR_MOVE_MAX_DISTANCE, along + DONOR_MOVE_STEP);
            Vec3 expected = origin.add(look.scale(along));
            helper.assertTrue(
                    member.position().distanceTo(expected) < 1.0E-6D,
                    "1.16 moved the member 0.36 a held tick along the "
                            + "user's look from its own position, up to 10: "
                            + "after " + heldTicks + " held ticks it is "
                            + member.position().subtract(origin)
                            + " from its start, expected "
                            + expected.subtract(origin));
        }

        private void release() {
            AbilityInput.keyRelease(MOVE_KEY, owner);
            stoppedAt = member.position();
            helper.assertTrue(
                    hold.isOver() || hold.getPhase() == ActionPhase.RECOVERY,
                    "1.16 ended the move as its key was let go: the hold "
                            + "is still in " + hold.getPhase());
            helper.assertTrue(
                    moveCooldown() == DONOR_MOVE_COOLDOWN,
                    "1.16 set the move's cooldown(4) as its hold ended "
                            + "(stopHeldAction): " + moveCooldown());
        }

        private void assertStays(String when) {
            helper.assertTrue(
                    member.isAlive()
                            && member.position().distanceTo(stoppedAt)
                                    < 1.0E-6D
                            && member.getMemberState()
                                    == SexPistolsMemberState.SCOUTING
                            && member.getCommandTarget().distanceTo(stoppedAt)
                                    < 1.0E-6D,
                    "1.16 stopped the member where it was as the key was "
                            + "let go and left it fixed (stoppedHolding): "
                            + when + " it is "
                            + member.position().distanceTo(stoppedAt)
                            + " from there, state "
                            + member.getMemberState());
        }

        private void assertOthersRefused(String when) {
            List<String> wrong = new ArrayList<>();
            for (String path : List.of(
                    SexPistolsIds.SEX_PISTOLS_TARGET_MODE_PATH,
                    SexPistolsIds.SEX_PISTOLS_TRAJECTORY_VISION_PATH)) {
                SexPistolsTargetMode mode = coordinator.targetMode();
                boolean vision = coordinator.trajectoryVision();
                MovePress pressed = press(
                        path, InputMethod.CLICK, OTHER_KEY);
                AbilityInput.keyRelease(OTHER_KEY, owner);
                if (pressed.check() != ConditionCheck.NEGATIVE
                        || pressed.state().isActionSuccess
                        || pressed.state().shouldBuffer
                        || coordinator.targetMode() != mode
                        || coordinator.trajectoryVision() != vision) {
                    wrong.add(path + " went off "
                            + pressed.state().isActionSuccess
                            + ", buffered " + pressed.state().shouldBuffer
                            + ", silently refused "
                            + (pressed.check() == ConditionCheck.NEGATIVE));
                }
            }
            helper.assertTrue(
                    wrong.isEmpty()
                            && hold.getPhase() == ActionPhase.PERFORM
                            && AbilityInput.isHeldByKey(owner, hold),
                    "1.16 refused the power's other actions, silently, "
                            + "while Move No. 1 was held "
                            + "(heldAllowsOtherAction false) " + when
                            + ": " + wrong + ", the hold in "
                            + hold.getPhase());
        }

        private int moveCooldown() {
            return power.getAbilityCooldown(
                    SexPistolsIds.SEX_PISTOLS_MOVE_SELECTED_PATH);
        }

        // As ClAbilityInputPacket handles a press: the key's resolved
        // ability, its condition check, then the press.
        private MovePress press(
                String path, InputMethod method, short key) {
            Ability base = power.getAbility(path);
            helper.assertTrue(
                    base != null, "Sex Pistols has no " + path + ".");
            AbilityConditionCheck container = resolve(base);
            BufferingState state = BufferingState.clickCanBuffer();
            if (container != null
                    && AbilityInput.withConditionCheck(
                            container, owner, method)) {
                AbilityInput.keyPress(
                        key,
                        container.ability,
                        owner,
                        null,
                        method,
                        0.0F,
                        state,
                        base.getAbilityId());
            }
            return new MovePress(
                    container != null ? container.conditionCheck : null,
                    state);
        }

        // The power resolves its keys once a tick; a press in the same tick
        // as a change must not see the keys of before it.
        private AbilityConditionCheck resolve(Ability ability) {
            try {
                Field cached = Power.class.getDeclaredField(
                        "cachedMovesThisTick");
                cached.setAccessible(true);
                cached.setBoolean(power, false);
            }
            catch (ReflectiveOperationException e) {
                throw new IllegalStateException(
                        "Power.cachedMovesThisTick is not reachable", e);
            }
            return power.updateAvailableMoves()
                    .getContextVariationContainer(ability);
        }
    }

    // 1.16 SexPistolsMoveSelectedAction.getTranslatedName :145-164 named the
    // pistol (entity.rotp_nextalbum.sex_pistols_N) and, giving or picking up,
    // the item (the synced carried item, the looked-at item entity), and
    // findLookedAtItem :180 took an item whose grown box held the user's eye.
    // Its own batch: the items lie on the user's line.
    @GameTest(
            templateNamespace = "rotp_addon_api_smoke",
            template = "empty",
            batch = "sexPistolsMoveNames",
            timeoutTicks = 120 + GameTestChunkTickets.WAIT_TICKS)
    public static void moveSelectedNamesThePistolAndTheItemAsIn116(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> moveSelectedNamesBody(h));
    }

    private static void moveSelectedNamesBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = makeCompatibleMockPlayer(
                helper, "sex-pistols-move-names");
        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(owner);
        StandPower power;
        try {
            positionPlayer(helper, owner, 1, 2, 1);
            owner.setNoGravity(true);
            owner.setYRot(-90.0F);
            owner.setYHeadRot(-90.0F);
            owner.setXRot(0.0F);
            power = prepareSexPistols(helper, owner);
        }
        catch (RuntimeException | Error failure) {
            cleanupOwner(level, owner, coordinator);
            throw failure;
        }
        Ability move = power.getAbility(
                SexPistolsIds.SEX_PISTOLS_MOVE_SELECTED_PATH);
        String key = move.getTranslationKey();
        Component pistol = Component.translatable(
                SexPistolsMoveSelectedAbility.PISTOL_NAME_KEY, 1);
        ItemEntity[] fetched = new ItemEntity[1];
        int[] ticks = new int[1];
        helper.onEachTick(() -> {
            try {
                ticks[0]++;
                SexPistolsEntity member = coordinator.member(level, 1);
                if (ticks[0] == 3) {
                    helper.assertTrue(
                            member != null
                                    && coordinator.selectedNumber() == 1
                                    && member.getMemberState()
                                            == SexPistolsMemberState.FORMATION
                                    && SexPistolsMoveSelectedAbility
                                            .findLookedAtItem(owner, 10.0D)
                                            == null,
                            "setup: No. 1 is not the selected idle member, "
                                    + "or an item lies on the user's line.");
                    assertMoveName(helper, move, power, key, "the idle "
                            + "member", pistol);

                    ItemEntity atEye = looseItem(level, owner.getEyePosition(),
                            new ItemStack(Items.APPLE));
                    ItemEntity found = SexPistolsMoveSelectedAbility
                            .findLookedAtItem(owner, 10.0D);
                    atEye.discard();
                    helper.assertTrue(
                            found == atEye,
                            "1.16 took an item whose box, grown by 0.4, "
                                    + "held the user's eye "
                                    + "(findLookedAtItem :180, the eye "
                                    + "position): found " + found);

                    ItemStack gold = new ItemStack(Items.GOLD_INGOT);
                    fetched[0] = looseItem(level, owner.getEyePosition()
                            .add(owner.getLookAngle().scale(3.0D)), gold);
                    helper.assertTrue(
                            SexPistolsMoveSelectedAbility.findLookedAtItem(
                                    owner, 10.0D) == fetched[0],
                            "setup: the gold ingot on the user's line is "
                                    + "not the looked-at item.");
                    assertMoveName(helper, move, power, key + ".pickup",
                            "an item on the user's line", pistol,
                            gold.getHoverName());
                    member.startRetrievingItem(fetched[0]);
                    assertMoveName(helper, move, power, key + ".recall",
                            "a fetching member", pistol);
                    return;
                }
                if (ticks[0] < 3 || member == null
                        || !member.hasCarriedItem()) {
                    helper.assertTrue(
                            ticks[0] < 80,
                            "setup: No. 1 did not fetch the gold ingot.");
                    return;
                }
                ItemStack carried = new ItemStack(Items.GOLD_INGOT);
                assertMoveName(helper, move, power, key + ".give",
                        "a member carrying an item", pistol,
                        carried.getHoverName());
                boolean synced = member.getEntityData().getNonDefaultValues()
                        .stream()
                        .anyMatch(value -> value.value()
                                        instanceof ItemStack stack
                                && ItemStack.isSameItem(stack, carried));
                helper.assertTrue(
                        synced,
                        "1.16 synced the carried item to the clients "
                                + "(CARRIED_ITEM), whose HUD names it: "
                                + "it is not in the member's synced data.");
                member.deliverCarriedItem();
                assertMoveName(helper, move, power, key,
                        "the member after giving the item", pistol);
                cleanupOwner(level, owner, coordinator);
                helper.succeed();
            }
            catch (RuntimeException | Error failure) {
                if (fetched[0] != null) {
                    fetched[0].discard();
                }
                cleanupOwner(level, owner, coordinator);
                throw failure;
            }
        });
    }

    private static ItemEntity looseItem(
            ServerLevel level, Vec3 centre, ItemStack stack) {
        ItemEntity item = new ItemEntity(
                level, centre.x, centre.y - 0.125D, centre.z, stack,
                0.0D, 0.0D, 0.0D);
        item.setNoGravity(true);
        item.setNeverPickUp();
        level.addFreshEntity(item);
        return item;
    }

    private static void assertMoveName(
            GameTestHelper helper,
            Ability move,
            StandPower power,
            String key,
            String when,
            Object... args) {
        Component name = move.getName(power);
        boolean matches = name.getContents()
                        instanceof TranslatableContents contents
                && contents.getKey().equals(key)
                && java.util.Arrays.equals(contents.getArgs(), args);
        helper.assertTrue(
                matches,
                "1.16 named the move " + key + " with "
                        + java.util.Arrays.toString(args)
                        + " (getTranslatedName :145-164) for " + when
                        + ": it is " + name);
    }

    // 1.16 sent no remote-fixed pistol back for distance (SexPistolsEntity
    // tick :175-202 has no such rule): the core's StandEntity.move held it
    // within the Stand's max range of its user, range(40, 40) for Sex
    // Pistols (InitStands :130), dragged along, and it stayed where that
    // left it. Its own batch: the user walks 45 blocks off.
    @GameTest(
            templateNamespace = "rotp_addon_api_smoke",
            template = "empty",
            batch = "sexPistolsFixedRange",
            timeoutTicks = 80 + GameTestChunkTickets.WAIT_TICKS)
    public static void aFixedMemberStaysWithinTheMaxRangeOfItsUserAsIn116(
            GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        GameTestChunkTickets.forTest(helper)
                .coverBox(
                        origin.getX() - FIXTURE_RANGE_MARGIN,
                        origin.getZ() - FIXTURE_RANGE_MARGIN,
                        origin.getX() + FIXTURE_RANGE_WALK
                                + FIXTURE_RANGE_MARGIN,
                        origin.getZ() + FIXTURE_RANGE_MARGIN)
                .whenTicking(() -> {}, h -> fixedMemberRangeBody(h));
    }

    private static void fixedMemberRangeBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = makeCompatibleMockPlayer(
                helper, "sex-pistols-fixed-range");
        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(owner);
        try {
            positionPlayer(helper, owner, 1, 2, 1);
            owner.setNoGravity(true);
            prepareSexPistols(helper, owner);
        }
        catch (RuntimeException | Error failure) {
            cleanupOwner(level, owner, coordinator);
            throw failure;
        }
        Vec3[] fixedAt = new Vec3[1];
        int[] ticks = new int[1];
        helper.onEachTick(() -> {
            try {
                ticks[0]++;
                SexPistolsEntity member = coordinator.member(level, 1);
                if (ticks[0] < 3) {
                    return;
                }
                if (ticks[0] == 3) {
                    helper.assertTrue(
                            member != null
                                    && member.getMaxRange()
                                            == DONOR_SEX_PISTOLS_RANGE,
                            "setup: No. 1's max range is "
                                    + (member != null
                                            ? member.getMaxRange()
                                            : "missing")
                                    + ", 1.16 range(40, 40).");
                    member.holdCommandPosition();
                    fixedAt[0] = member.position();
                    placeOwnerFrom(owner, fixedAt[0], 20.0D);
                    return;
                }
                Vec3 user = owner.position();
                Vec3 expected = ticks[0] <= 8
                        ? fixedAt[0]
                        : user.add(fixedAt[0].subtract(user)
                                .scale(DONOR_SEX_PISTOLS_RANGE
                                        / fixedAt[0].distanceTo(user)));
                if (ticks[0] > 13) {
                    expected = fixedAt[0].add(
                            FIXTURE_RANGE_WALK - DONOR_SEX_PISTOLS_RANGE,
                            0.0D, 0.0D);
                }
                helper.assertTrue(
                        member != null
                                && member.isAlive()
                                && member.getMemberState()
                                        == SexPistolsMemberState.SCOUTING
                                && member.position().distanceTo(expected)
                                        < 1.0E-6D
                                && member.getCommandTarget()
                                        .distanceTo(expected) < 1.0E-6D,
                        "1.16 left a fixed pistol where it was, however "
                                + "far its user went, held only within "
                                + "the Stand's max range 40 of the user "
                                + "(StandEntity.move) and left there: "
                                + "tick " + ticks[0] + ", the user "
                                + user.distanceTo(fixedAt[0])
                                + " from its place, it is "
                                + (member != null
                                        ? member.getMemberState() + " "
                                                + member.position()
                                                        .subtract(expected)
                                        : "missing")
                                + " from where 1.16 had it.");
                if (ticks[0] == 8) {
                    placeOwnerFrom(owner, fixedAt[0], FIXTURE_RANGE_WALK);
                }
                else if (ticks[0] == 13) {
                    placeOwnerFrom(owner, fixedAt[0], 20.0D);
                }
                else if (ticks[0] == 17) {
                    cleanupOwner(level, owner, coordinator);
                    helper.succeed();
                }
            }
            catch (RuntimeException | Error failure) {
                cleanupOwner(level, owner, coordinator);
                throw failure;
            }
        });
    }

    private static void placeOwnerFrom(
            ServerPlayer owner, Vec3 place, double alongX) {
        owner.moveTo(place.x + alongX, place.y, place.z);
    }

    private static void cleanupOwner(
            ServerLevel level,
            ServerPlayer owner,
            SexPistolsCoordinator coordinator) {
        coordinator.cleanup(level, owner);
        owner.connection.disconnect(Component.literal(
                "Sex Pistols quick reload GameTest complete"));
    }

    private static StandPower prepareSexPistols(
            GameTestHelper helper, ServerPlayer owner) {
        StandPower power = StandPower.get(owner);
        helper.assertTrue(
                power != null,
                "The mock owner has no Stand power attachment.");
        if (power == null) {
            throw new IllegalStateException(
                    "Missing Stand power attachment");
        }
        power.setStand(SexPistolsStands.SEX_PISTOLS.get());
        power.skipProgression();
        power.setStamina(power.getMaxStamina());
        helper.assertTrue(
                SexPistolsStands.SEX_PISTOLS.get()
                        .summon(owner, power),
                "Sex Pistols could not be summoned.");
        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(owner);
        helper.assertTrue(
                power.isSummoned()
                        && coordinator.isSummoned()
                        && coordinator.trackedMemberCount()
                                == SexPistolsCoordinator.MEMBER_COUNT,
                "Sex Pistols did not create its six-member production "
                        + "coordinator.");
        for (int slot = 0; slot < SexPistolsCoordinator.MEMBER_COUNT; slot++) {
            int number = SexPistolsIds.SEX_PISTOLS_NUMBERS[slot];
            helper.assertTrue(
                    coordinator.member(owner.serverLevel(), number) != null,
                    "Sex Pistols member No." + number + " does not resolve "
                            + "in the level: it forms at "
                            + new ChunkPos(BlockPos.containing(
                                    SexPistolsEntity.formationPosition(
                                            owner, slot)))
                            + ", the owner stands in chunk "
                            + new ChunkPos(owner.blockPosition())
                            + ", and only the owner's chunk is guaranteed "
                            + "to be loaded.");
        }
        return power;
    }

    private static void primePiercingThroughAbility(
            GameTestHelper helper,
            ServerPlayer owner,
            StandPower power,
            SexPistolsCoordinator coordinator,
            ItemStack revolver) {
        Ability ability = power.getAbility(
                SexPistolsIds.SEX_PISTOLS_PIERCING_SHOT_PATH);
        helper.assertTrue(
                ability instanceof SexPistolsSpecialShotAbility
                        && AbilityInput.withConditionCheck(
                                ability, owner),
                "Production ability input rejected PIERCING priming.");
        FriendlyByteBuf input = new FriendlyByteBuf(
                Unpooled.buffer());
        try {
            AbilityInput.keyPress(
                    PIERCING_KEY,
                    ability,
                    owner,
                    input,
                    InputMethod.CLICK,
                    0.0F,
                    BufferingState.clickCanBuffer(),
                    ability.abilityId);
        }
        finally {
            input.release();
        }

        int chamber = RevolverItem.state(revolver)
                .selectedChamber();
        helper.assertTrue(
                RevolverItem.state(revolver).reloadMode()
                        && RevolverItem.state(revolver)
                                .chamber(chamber) == Chamber.LIVE
                        && RevolverSpecialChambers.has(
                                revolver, chamber, ShotType.PIERCING)
                        && RevolverMemberAttachments
                                .chamberMemberMask(
                                        revolver, chamber) == 0x3F
                        && coordinator.loadedMemberMask() == 0x3F,
                "PIERCING ability input did not create a live special "
                        + "chamber with all six members.");
        power.setStamina(power.getMaxStamina());
    }

    private static void grantTimeStop(
            GameTestHelper helper, ServerPlayer player) {
        ResourceLocation theWorld = JojoMod.resLoc("the_world");
        StandType standType =
                JojoRegistries.DEFAULT_STANDS_REG.get(theWorld);
        helper.assertTrue(
                standType != null,
                "The World is missing from the Stand registry.");
        StandPower power = PowerClass.STAND.attachGet(player);
        StandPowerTransitions.Result inserted =
                StandPowerTransitions.insert(
                        power, new StandInstance(standType));
        helper.assertTrue(
                inserted.status()
                        == StandPowerTransitions.Status.APPLIED,
                "The Time Stop fixture could not grant The World: "
                        + inserted.status() + ".");
        PowerSkillUnlocks.Result unlocked = TEST_UNLOCKS.forceUnlock(
                power, TimeStopLearning.TIME_STOP);
        helper.assertTrue(
                unlocked == PowerSkillUnlocks.Result.UNLOCKED
                        || unlocked
                                == PowerSkillUnlocks.Result
                                        .ALREADY_UNLOCKED,
                "The Time Stop fixture could not unlock Time Stop: "
                        + unlocked + ".");
    }

    private static void assertOwnerEpochBound(
            GameTestHelper helper,
            RevolverBulletEntity bullet,
            ServerPlayer owner) {
        CompoundTag tag = new CompoundTag();
        bullet.saveWithoutId(tag);
        BigInteger epoch;
        try {
            epoch = new BigInteger(
                    tag.getString("SexPistolsOwnerEpoch"));
        }
        catch (NumberFormatException exception) {
            epoch = BigInteger.valueOf(-1L);
        }
        helper.assertTrue(
                tag.hasUUID("SexPistolsOwner")
                        && owner.getUUID().equals(
                                tag.getUUID("SexPistolsOwner"))
                        && epoch.signum() >= 0,
                "RevolverItem fire did not bind a valid owner epoch.");
    }

    private static int piercingTicks(
            RevolverBulletEntity bullet) {
        CompoundTag tag = new CompoundTag();
        bullet.saveWithoutId(tag);
        return tag.getInt("PiercingTicks");
    }

    private static void positionPlayer(
            GameTestHelper helper,
            ServerPlayer player,
            int x,
            int y,
            int z) {
        BlockPos origin = helper.absolutePos(new BlockPos(0, y, 0));
        BlockPos pos = helper.absolutePos(new BlockPos(
                insideOriginChunk(origin.getX(), x),
                y,
                insideOriginChunk(origin.getZ(), z)));
        player.moveTo(
                pos.getX() + 0.5D,
                pos.getY(),
                pos.getZ() + 0.5D);
        GameTestChunkTickets.forTest(helper).cover(pos);
        helper.assertTrue(
                new ChunkPos(pos).equals(new ChunkPos(origin)),
                "The fixture player stands in chunk "
                        + new ChunkPos(pos) + ", outside the chunk "
                        + new ChunkPos(origin) + " of the test origin, "
                        + "which is the one that ticks entities.");
        helper.assertTrue(
                (pos.getX() & CHUNK_LAST_BLOCK) >= 1
                        && (pos.getX() & CHUNK_LAST_BLOCK)
                                <= OWNER_LAST_BLOCK
                        && (pos.getZ() & CHUNK_LAST_BLOCK) >= 1
                        && (pos.getZ() & CHUNK_LAST_BLOCK)
                                <= OWNER_LAST_BLOCK,
                "The fixture player stands in block "
                        + (pos.getX() & CHUNK_LAST_BLOCK) + ","
                        + (pos.getZ() & CHUNK_LAST_BLOCK)
                        + " of its chunk, at its edge: "
                        + "the Sex Pistols members form up to 0.72 blocks "
                        + "around the owner and would be created in a "
                        + "neighbour chunk.");
        JojoMod.getLogger().info(
                "SEX_PISTOLS_PLACEMENT {} template block {},{} player block {},{}",
                player.getGameProfile().getName(),
                origin.getX() & CHUNK_LAST_BLOCK,
                origin.getZ() & CHUNK_LAST_BLOCK,
                pos.getX() & CHUNK_LAST_BLOCK,
                pos.getZ() & CHUNK_LAST_BLOCK);
    }

    // An offset that would come within a block of the east or south edge of
    // the origin's chunk is mirrored; the fixtures aim along their own axes,
    // not the grid. The margin keeps the Sex Pistols members, which form up
    // to 0.72 blocks around their owner, in the owner's chunk.
    private static int insideOriginChunk(int originCoordinate, int offset) {
        return (originCoordinate & CHUNK_LAST_BLOCK) + offset
                        > OWNER_LAST_BLOCK
                ? -offset
                : offset;
    }

    private static ServerPlayer makeCompatibleMockPlayer(
            GameTestHelper helper, String playerName) {
        GameProfile profile = new GameProfile(
                UUID.randomUUID(), playerName);
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(
                        profile, false);
        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                profile,
                cookie.clientInformation());
        Connection connection = new Connection(
                PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        helper.getLevel()
                .getServer()
                .getPlayerList()
                .placeNewPlayer(connection, player, cookie);
        return player;
    }

    private static void cleanup(
            ServerLevel level,
            TimeStopState timeStop,
            int timeStopInstanceId,
            ServerPlayer owner,
            ServerPlayer timeStopper,
            Cow target,
            SexPistolsCoordinator coordinator) {
        timeStop.removeInstance(timeStopInstanceId);
        target.discard();
        coordinator.cleanup(level, owner);
        owner.connection.disconnect(Component.literal(
                "Sex Pistols piercing GameTest complete"));
        timeStopper.connection.disconnect(Component.literal(
                "Sex Pistols piercing GameTest complete"));
    }
}
