package rotp.addons.killerqueenlion;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.google.gson.stream.JsonReader;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import rotp.addons.killerqueenlion.btd.BitesTheDustService;
import rotp.addons.killerqueenlion.ability.KillerQueenStandCombat;
import rotp.addons.killerqueenlion.entity.BubbleBombEntity;
import rotp.addons.killerqueenlion.entity.BubbleMarkEntity;
import rotp.addons.killerqueenlion.entity.KillerQueenBombProjectile;
import rotp.addons.killerqueenlion.entity.SheerHeartAttackEntity;
import rotp.addons.killerqueenlion.network.KillerQueenStateSyncPayload;
import rotp.addons.killerqueenlion.state.ItemBombService;
import rotp.addons.killerqueenlion.state.KillerQueenState;
import rotp.addons.killerqueenlion.state.KillerQueenStateService;
import rotp.core.JojoModConfig;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility.StandEntityBarrage;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.StringUtil;

/** Behaviour of the 1.16 rotp_zkq8 donor (RotP-Killer_Queen_Lion-1.0) that the port has to keep. */
@GameTestHolder(KillerQueenLionAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KillerQueenDonorParityGameTests {
    private KillerQueenDonorParityGameTests() {}

    private record Fixture(
            ServerPlayer owner, StandPower power, BlockPos pad, KillerQueenGameTests.MockPlayerCleanup cleanup) {}

    private record Input(String ability, InputMethod method) {}

    private record Blast(Vec3 center, float radius) {
        @Override
        public String toString() {
            return String.format(
                    Locale.ROOT, "(%.1f, %.1f, %.1f) radius %.1f", center.x, center.y, center.z, radius);
        }
    }

    // 1.16 KQNormalPunch, KQBarrage, KQHeavyPunch, KQBlock: checkConditions is NEGATIVE with a victim bound.
    // heavy_charged is the port's hold form of kq_heavy_punch.
    private static final List<Input> STAND_COMBAT = List.of(
            new Input(KillerQueenLionAddon.PUNCH_NAME, InputMethod.CLICK),
            new Input(KillerQueenLionAddon.BARRAGE_NAME, InputMethod.HOLD),
            new Input(KillerQueenLionAddon.HEAVY_PUNCH_NAME, InputMethod.CLICK),
            new Input(KillerQueenLionAddon.HEAVY_CHARGED_NAME, InputMethod.HOLD),
            new Input(KillerQueenLionAddon.BLOCK_NAME, InputMethod.HOLD));

    // 1.16 BlockBombAction, ExplodeBlockAction, BlockQuitBomb, EntityExplode, EntityQuitBomb, ItemFBombExplode,
    // ExplodeSnow, SheerHeartAttack carry the same victim check.
    private static final List<Input> BOMBS = List.of(
            new Input(KillerQueenLionAddon.BLOCK_BOMB_NAME, InputMethod.CLICK),
            new Input(KillerQueenLionAddon.BLOCK_BOMB_DETONATE_NAME, InputMethod.HOLD),
            new Input(KillerQueenLionAddon.BLOCK_BOMB_CLEAR_NAME, InputMethod.CLICK),
            new Input(KillerQueenLionAddon.PRIMARY_BOMB_DETONATE_NAME, InputMethod.HOLD),
            new Input(KillerQueenLionAddon.PRIMARY_BOMB_CLEAR_NAME, InputMethod.CLICK),
            new Input(KillerQueenLionAddon.ITEM_BOMB_DETONATE_NAME, InputMethod.HOLD),
            new Input(KillerQueenLionAddon.PROJECTILE_DETONATE_NAME, InputMethod.HOLD),
            new Input(KillerQueenLionAddon.BUBBLE_BLOCK_DETONATE_NAME, InputMethod.CLICK),
            new Input(KillerQueenLionAddon.SHEER_HEART_NAME, InputMethod.CLICK));

    // 1.16 SHABack
    private static final Input SHEER_HEART_RECALL = new Input(
            KillerQueenLionAddon.SHEER_HEART_RECALL_NAME, InputMethod.HOLD);

    private record AttackClip(Input input, String clip, boolean armsOnlyClip) {}

    // 1.16 KillerQueenModel is a HumanoidStandModel, which poses the punch, the barrage and the heavy punch.
    // The port plays the clips of the Lion animation file instead; the arms-only punch has a clip of its own.
    private static final List<AttackClip> ATTACK_CLIPS = List.of(
            new AttackClip(new Input(KillerQueenLionAddon.PUNCH_NAME, InputMethod.CLICK), "punch", true),
            new AttackClip(new Input(KillerQueenLionAddon.BARRAGE_NAME, InputMethod.HOLD), "barrage", false),
            new AttackClip(
                    new Input(KillerQueenLionAddon.HEAVY_PUNCH_NAME, InputMethod.CLICK), "heavy_punch", false));

    private static final String ANIMATION_FILE = "assets/rotp_zkq8/stand_skins/killer_queen/assets/rotp_zkq8/"
            + "animations/killer_queen.animation.json";

    @GameTest(template = "empty", batch = "kqParityBtdGate", timeoutTicks = 20)
    public static void boundBitesTheDustVictimRefusesDonorGatedInput(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-btd-gate", GameType.CREATIVE);
        ServerPlayer owner = kq.owner();
        StandPower power = kq.power();
        ServerLevel level = helper.getLevel();
        ResourceLocation dimension = level.dimension().location();
        KillerQueenState state = KillerQueenLionAddon.state(owner);
        Cow victim = spawnCow(helper, owner, 3.0D, 0.0D);
        Cow carrier = spawnCow(helper, owner, 0.0D, 3.0D);
        BlockPos marked = kq.pad().offset(4, 0, -3);
        BlockPos blockBomb = kq.pad().offset(4, 0, 3);
        level.setBlockAndUpdate(marked, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(blockBomb, Blocks.STONE.defaultBlockState());

        List<Entity> spawned = new ArrayList<>(List.of(victim, carrier));
        makeEveryBombUsable(helper, kq, carrier, marked, blockBomb, spawned);
        List<String> unusable = new ArrayList<>();
        for (Input input : STAND_COMBAT) {
            if (!accepts(owner, power, input)) {
                unusable.add(input.ability());
            }
        }
        for (Input input : BOMBS) {
            if (!accepts(owner, power, input)) {
                unusable.add(input.ability());
            }
        }
        sheerHeartBeside(helper, kq, spawned);
        if (!accepts(owner, power, SHEER_HEART_RECALL)) {
            unusable.add(SHEER_HEART_RECALL.ability());
        }
        KillerQueenStateService.recallSheerHeart(owner);
        helper.assertTrue(unusable.isEmpty(),
                "fixture: without a Bites the Dust victim these inputs must be accepted: " + unusable);

        helper.assertTrue(
                BitesTheDustService.armBitesTheDust(owner, victim.getUUID(), level.getGameTime(), dimension)
                        && state.hasBitesTheDustVictim()
                        && power.getSummonedStandEntity() == null,
                "fixture: the Bites the Dust victim was not bound or the Stand stayed out");

        List<String> accepted = new ArrayList<>();
        short key = 80;
        for (Input input : STAND_COMBAT) {
            boolean pressed = press(owner, power, input, key);
            StandEntity summoned = power.getSummonedStandEntity();
            if (pressed || summoned != null) {
                accepted.add(input.ability()
                        + (summoned != null ? " (the press summoned the Stand)" : ""));
            }
            AbilityInput.keyRelease(key, owner);
            if (summoned != null) {
                power.getPowerType().forceUnsummon(owner, power);
            }
            key++;
        }
        // The donor forces the Stand away every tick, so the bomb inputs only matter before that tick ends.
        makeEveryBombUsable(helper, kq, carrier, marked, blockBomb, spawned);
        for (Input input : BOMBS) {
            if (accepts(owner, power, input)) {
                accepted.add(input.ability());
            }
        }
        sheerHeartBeside(helper, kq, spawned);
        if (accepts(owner, power, SHEER_HEART_RECALL)) {
            accepted.add(SHEER_HEART_RECALL.ability());
        }
        KillerQueenStateService.recallSheerHeart(owner);
        try {
            helper.assertTrue(accepted.isEmpty(),
                    "The donor refuses these inputs while a Bites the Dust victim is bound, the port accepted: "
                            + accepted);
        } finally {
            cleanup(helper, owner, spawned.toArray(Entity[]::new));
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "kqParityBubbleMarkStart", timeoutTicks = 60)
    public static void freshSurvivalUserMarksABlockWithTheBubble(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-mark-start", GameType.SURVIVAL);
        ServerPlayer owner = kq.owner();
        StandPower power = kq.power();
        KillerQueenState state = KillerQueenLionAddon.state(owner);
        helper.assertTrue(
                power.isAbilityUnlocked(KillerQueenLionAddon.BUBBLE_BOMB_NAME)
                        && !power.isAbilityUnlocked(KillerQueenLionAddon.SNOW_BOMB_NAME)
                        && !power.isAbilityUnlocked(KillerQueenLionAddon.PROJECTILE_DETONATE_NAME),
                "fixture: not a fresh resolve-0 Survival Killer Queen");
        boolean mark = power.isAbilityUnlocked(KillerQueenLionAddon.BUBBLE_MARK_NAME);
        boolean detonate = power.isAbilityUnlocked(KillerQueenLionAddon.BUBBLE_BLOCK_DETONATE_NAME);
        helper.assertTrue(mark && detonate,
                "The donor gives kq_bubble_mark and kq_bubble_block_explode from the start (resolve 0); "
                        + "a fresh Survival user has kq_bubble_mark=" + mark
                        + ", kq_bubble_block_explode=" + detonate);
        summon(helper, kq);
        owner.setXRot(90.0F);
        helper.assertTrue(
                press(owner, power, new Input(KillerQueenLionAddon.BUBBLE_MARK_NAME, InputMethod.CLICK), (short) 94),
                "A fresh Survival Killer Queen could not use Bubble Mark");
        helper.runAtTickTime(30, () -> {
            try {
                helper.assertTrue(
                        KillerQueenStateService.hasBubbleBlockMarks(owner)
                                && accepts(owner, power, new Input(
                                        KillerQueenLionAddon.BUBBLE_BLOCK_DETONATE_NAME, InputMethod.CLICK)),
                        "The bubble did not leave a mark the fresh user can detonate; marks="
                                + state.bubbleBlockMarks());
            } finally {
                cleanup(helper, owner);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "kqParityBlockBlast", timeoutTicks = 80)
    public static void blockDetonationsExplodeEveryMarkAtItsBlockCorner(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-block-blast", GameType.CREATIVE);
        ServerPlayer owner = kq.owner();
        StandPower power = kq.power();
        ServerLevel level = helper.getLevel();
        KillerQueenState state = KillerQueenLionAddon.state(owner);
        summon(helper, kq);
        BlockPos kept = kq.pad().offset(7, 0, 0);
        BlockPos removed = kq.pad().offset(7, 0, 4);
        BlockPos bomb = kq.pad().offset(7, 0, -4);
        for (BlockPos position : List.of(kept, removed, bomb)) {
            level.setBlockAndUpdate(position, Blocks.STONE.defaultBlockState());
        }
        helper.assertTrue(
                KillerQueenStateService.markBubbleBlock(owner, kept)
                        && KillerQueenStateService.markBubbleBlock(owner, removed),
                "fixture: the bubble marks were rejected");
        level.setBlockAndUpdate(removed, Blocks.AIR.defaultBlockState());

        List<Blast> blasts = new ArrayList<>();
        Consumer<ExplosionEvent.Start> listener = recordBlasts(level, owner, blasts);
        guarded(listener, () -> helper.assertTrue(
                press(owner, power, new Input(
                        KillerQueenLionAddon.BUBBLE_BLOCK_DETONATE_NAME, InputMethod.CLICK), (short) 90),
                "fixture: the bubble mark detonation press was refused"));
        helper.runAtTickTime(6, () -> guarded(listener, () -> {
            helper.assertTrue(
                    blasts.size() == 2
                            && hasBlast(blasts, kept, 2.0F)
                            && hasBlast(blasts, removed, 2.0F),
                    "The donor explodes at the integer block corner of every stored mark, radius 2.0, whether "
                            + "or not the block is still there. Marks " + kept.toShortString()
                            + " (block present) and " + removed.toShortString()
                            + " (block removed) gave " + blasts);
            helper.assertTrue(
                    level.getBlockState(kept).isAir() && state.bubbleBlockMarks().isEmpty(),
                    "The marked block was not removed or the marks were not cleared");
            blasts.clear();
        }));
        helper.runAtTickTime(20, () -> guarded(listener, () -> helper.assertTrue(
                KillerQueenStateService.markBlockBomb(owner, bomb)
                        && press(owner, power, new Input(
                                KillerQueenLionAddon.BLOCK_BOMB_DETONATE_NAME, InputMethod.HOLD), (short) 91),
                "fixture: the block bomb detonation press was refused")));
        helper.runAtTickTime(45, () -> {
            try {
                helper.assertTrue(
                        blasts.size() == 1 && hasBlast(blasts, bomb, 2.0F),
                        "The donor block bomb explodes at the integer block corner " + bomb.toShortString()
                                + ", radius 2.0; got " + blasts);
            } finally {
                NeoForge.EVENT_BUS.unregister(listener);
                cleanup(helper, owner);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "kqParityBarrageMultiplier", timeoutTicks = 40)
    public static void registeredBarrageAppliesTheStandDamageMultiplierOnce(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-barrage-multi", GameType.CREATIVE);
        ServerPlayer owner = kq.owner();
        StandPower power = kq.power();
        StandEntity stand = summon(helper, kq);
        ModConfigSpec.ConfigValue<Double> multiplier = JojoModConfig.COMMON_SPEC.getValues()
                .get(List.of("Stand settings", "standPowerMultiplier"));
        helper.assertTrue(multiplier != null, "fixture: the Stand damage multiplier config value was not found");
        double previous = multiplier.get();
        helper.runAtTickTime(2, () -> {
            List<String> failures = new ArrayList<>();
            List<Entity> spawned = new ArrayList<>();
            List<DamageSource> sources = new ArrayList<>();
            Cow[] victim = {null};
            Consumer<LivingIncomingDamageEvent> listener = event -> {
                if (event.getEntity() == victim[0]) {
                    sources.add(event.getSource());
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingIncomingDamageEvent.class, listener);
            short key = 120;
            try {
                helper.assertTrue(stand.getUser() == owner && !stand.isArmsOnlyMode(),
                        "fixture: the summoned Killer Queen body does not belong to the input owner");
                for (double factor : new double[] {1.0D, 2.0D, 0.0D}) {
                    Cow cow = spawnCow(helper, owner, 0.0D, 1.5D);
                    spawned.add(cow);
                    victim[0] = cow;
                    cow.setNoGravity(true);
                    sources.clear();
                    multiplier.set(factor);
                    helper.assertTrue(JojoModConfig.getCommonConfigInstance(false).standDamageMultiplier.get() == factor,
                            "fixture: the effective server multiplier did not follow the config override");
                    stand.moveTo(owner.position());
                    Vec3 delta = cow.getEyePosition().subtract(owner.getEyePosition());
                    float yaw = (float) -Math.toDegrees(Math.atan2(delta.x, delta.z));
                    float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
                    owner.setYRot(yaw);
                    owner.setYHeadRot(yaw);
                    owner.setXRot(pitch);
                    owner.yRotO = yaw;
                    owner.xRotO = pitch;
                    LivingComponentAction.getAim(stand).setTarget(new ActionTarget(cow));
                    float health = cow.getHealth();
                    float finisher = stand.getFinisherMeter();
                    double strength = stand.getAttackDamage();
                    double precision = stand.getPrecision();
                    try {
                        helper.assertTrue(press(owner, power, new Input(KillerQueenLionAddon.BARRAGE_NAME, InputMethod.HOLD), key),
                                "fixture: the registered held barrage input was refused");
                        EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
                        helper.assertTrue(action instanceof KillerQueenStandCombat.Barrage.BarrageAction
                                        && action.ability == power.getAbility(KillerQueenLionAddon.BARRAGE_NAME)
                                        && action.getPowerUser() == owner && action.getPerformer() == stand,
                                "fixture: the input did not start the registered Lion barrage with its real owner/body");
                        action.setPhaseStart(ActionPhase.PERFORM);
                        helper.assertTrue(action.getPhase() == ActionPhase.PERFORM,
                                "fixture: the production barrage did not enter PERFORM");
                        action._tickAction();
                        KillerQueenStandCombat.Barrage.BarrageAction barrage =
                                (KillerQueenStandCombat.Barrage.BarrageAction) action;
                        int hits = barrage.hitsThisTick;
                        helper.assertTrue(hits > 0 && action.punchedTarget != null
                                        && action.punchedTarget.getMainEntity() == cow,
                                "fixture: the production barrage tick did not reach the fresh target");
                        // 1.16 StandStatFormulas.getBarrageHitDamage, followed by StandEntity.hurtTarget's M.
                        float raw = 0.04F + (float) strength * 0.01F;
                        if (precision > 0.0D) {
                            double pr = precision / 16.0D;
                            raw *= 1.0D + pr * 0.5D * Math.min(pr, 0.5D);
                        }
                        float expected = raw * hits * (float) factor;
                        float actual = health - cow.getHealth();
                        float finisherAdded = stand.getFinisherMeter() - finisher;
                        helper.assertTrue(Math.abs(finisherAdded - 0.005F * hits) < 0.0001F,
                                "The multiplier changed the production barrage's hit-count finisher contribution");
                        if (factor > 0.0D) {
                            helper.assertTrue(sources.size() == 1 && sources.getFirst().getDirectEntity() == stand
                                            && sources.getFirst() instanceof DamageSourceModified modified
                                            && modified.jojo_ripples$standPower() == power,
                                    "The native barrage damage source lost its summoned body or owner's Stand power");
                        }
                        JojoMod.LOGGER.info("S11-02 native barrage M={} strength={} precision={} hits={} loss={} expected={} finisher={}",
                                factor, strength, precision, hits, actual, expected, finisherAdded);
                        if (Math.abs(actual - expected) > 0.0001F) {
                            failures.add("M=" + factor + ": loss=" + actual + ", expected=" + expected + ", hits=" + hits);
                        }
                    } finally {
                        AbilityInput.keyRelease(key, owner);
                        LivingComponentAction.getComponent(stand).setAction(null, owner, SyncType.NO_SYNC);
                        cow.discard();
                        key++;
                    }
                }
            } finally {
                multiplier.set(previous);
                NeoForge.EVENT_BUS.unregister(listener);
                cleanup(helper, owner, spawned.toArray(Entity[]::new));
            }
            helper.assertTrue(failures.isEmpty(), "The registered Lion barrage must apply the donor server multiplier once: " + failures);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "kqParityBlastMultiplier", timeoutTicks = 20)
    public static void blockBlastRadiusFollowsTheStandDamageMultiplier(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-blast-multi", GameType.CREATIVE);
        ServerPlayer owner = kq.owner();
        StandPower power = kq.power();
        ServerLevel level = helper.getLevel();
        StandEntity stand = summon(helper, kq);
        BlockPos mark = kq.pad().offset(12, 0, 0);
        BlockPos bomb = kq.pad().offset(12, 0, 4);
        level.setBlockAndUpdate(mark, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(bomb, Blocks.STONE.defaultBlockState());
        ModConfigSpec.ConfigValue<Double> multiplier = JojoModConfig.COMMON_SPEC.getValues()
                .get(List.of("Stand settings", "standPowerMultiplier"));
        helper.assertTrue(multiplier != null, "fixture: the Stand damage multiplier config value was not found");
        double previous = multiplier.get();
        List<Blast> blasts = new ArrayList<>();
        Consumer<ExplosionEvent.Start> listener = recordBlasts(level, owner, blasts);
        try {
            multiplier.set(4.0D);
            helper.assertTrue(
                    KillerQueenStateService.markBubbleBlock(owner, mark)
                            && KillerQueenStateService.markBlockBomb(owner, bomb),
                    "fixture: the marks were rejected");
            performNow(helper, owner, power, stand, KillerQueenLionAddon.BUBBLE_BLOCK_DETONATE_NAME);
            performNow(helper, owner, power, stand, KillerQueenLionAddon.BLOCK_BOMB_DETONATE_NAME);
        } finally {
            multiplier.set(previous);
            NeoForge.EVENT_BUS.unregister(listener);
        }
        try {
            helper.assertTrue(
                    blasts.size() == 2 && hasBlast(blasts, mark, 4.0F) && hasBlast(blasts, bomb, 4.0F),
                    "The donor block blast radius is sqrt(standDamageMultiplier) * 2: with the multiplier at 4 "
                            + "both the bubble mark and the block bomb explode with radius 4.0; got " + blasts);
        } finally {
            cleanup(helper, owner);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "kqParityMarkLifetime", timeoutTicks = 60)
    public static void bubbleMarksOutliveIdleTimeDimensionChangeAndRelog(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-mark-life", GameType.CREATIVE);
        ServerPlayer owner = kq.owner();
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        KillerQueenState state = KillerQueenLionAddon.state(owner);
        BlockPos first = kq.pad().offset(7, 0, 0);
        BlockPos second = kq.pad().offset(7, 0, 4);
        BlockPos third = kq.pad().offset(7, 0, -4);
        for (BlockPos position : List.of(first, second, third)) {
            level.setBlockAndUpdate(position, Blocks.STONE.defaultBlockState());
        }
        long lifetime = KillerQueenPhase1Spec.BUBBLE_BLOCK_MARK_LIFETIME_TICKS;
        ServerLevelData clock = (ServerLevelData) level.getLevelData();
        long now = level.getGameTime();

        // Each stage restores the mark the port lost before it, so one run reports every loss.
        List<String> lost = new ArrayList<>();
        helper.assertTrue(KillerQueenStateService.markBubbleBlock(owner, first),
                "fixture: the first bubble mark was rejected");
        try {
            clock.setGameTime(now + lifetime + 1L);
            KillerQueenStateService.validateOwner(owner);
            if (!state.bubbleBlockMarks().contains(first)) {
                lost.add("a mark older than 6000 ticks expired on its own (the donor only drops old marks "
                        + "when a new mark is placed)");
            }
            if (!KillerQueenStateService.markBubbleBlock(owner, second)
                    || !state.bubbleBlockMarks().equals(List.of(second))) {
                lost.add("placing a new mark did not drop the marks older than 6000 ticks and keep the new "
                        + "one: " + state.bubbleBlockMarks());
            }
        } finally {
            clock.setGameTime(now);
        }

        ServerLevel nether = server.getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "fixture: the test server has no Nether");
        Vec3 home = owner.position();
        helper.assertTrue(owner.teleportTo(nether, 1.5D, 80.0D, 1.5D, Set.of(), 0.0F, 0.0F)
                        && owner.level() == nether,
                "fixture: the owner could not change dimension");
        boolean keptInNether = state.bubbleBlockMarks().equals(List.of(second));
        helper.assertTrue(owner.teleportTo(level, home.x, home.y, home.z, Set.of(), 0.0F, 0.0F)
                        && owner.level() == level,
                "fixture: the owner could not return");
        if (!keptInNether
                || !state.bubbleBlockMarks().equals(List.of(second))
                || !KillerQueenStateService.hasBubbleBlockMarks(owner)) {
            lost.add("changing dimension wiped the marks (the donor keeps them)");
            KillerQueenStateService.markBubbleBlock(owner, second);
        }

        UUID ownerUuid = owner.getUUID();
        owner.connection.disconnect(Component.literal("Killer Queen parity relog"));
        helper.assertTrue(server.getPlayerList().getPlayer(ownerUuid) == null,
                "fixture: the owner did not log out");
        ServerPlayer again = kq.cleanup().join(mockPlayer(helper, ownerUuid, "kq-mark-life", null));
        StandPower power = StandPower.get(again);
        helper.assertTrue(
                power != null && KillerQueenLionAddon.isKillerQueenUser(again),
                "fixture: the Stand did not survive the relog");
        KillerQueenState reloaded = KillerQueenLionAddon.state(again);
        if (!reloaded.bubbleBlockMarks().equals(List.of(second))
                || !KillerQueenStateService.hasBubbleBlockMarks(again)) {
            lost.add("logging out wiped the marks (the donor saves them with the player): "
                    + reloaded.bubbleBlockMarks());
        } else {
            try {
                clock.setGameTime(now + 3L * lifetime);
                if (!KillerQueenStateService.markBubbleBlock(again, third)
                        || reloaded.bubbleBlockMarks().size() != 2
                        || !reloaded.bubbleBlockMarks().containsAll(List.of(second, third))) {
                    lost.add("a mark restored from the save expired (the donor keeps no timestamp for it, "
                            + "so it never expires): " + reloaded.bubbleBlockMarks());
                }
            } finally {
                clock.setGameTime(now);
            }
        }
        if (!lost.isEmpty()) {
            cleanup(helper, again);
        }
        helper.assertTrue(lost.isEmpty(), "Bubble block marks were lost where the donor keeps them: " + lost);

        Fixture relogged = new Fixture(again, power, kq.pad(), kq.cleanup());
        power.setStamina(power.getMaxStamina());
        summon(helper, relogged);
        List<Blast> blasts = new ArrayList<>();
        Consumer<ExplosionEvent.Start> listener = recordBlasts(level, again, blasts);
        guarded(listener, () -> helper.assertTrue(
                press(again, power, new Input(
                        KillerQueenLionAddon.BUBBLE_BLOCK_DETONATE_NAME, InputMethod.CLICK), (short) 95),
                "fixture: the detonation press after the relog was refused"));
        helper.runAtTickTime(8, () -> {
            try {
                helper.assertTrue(
                        blasts.size() == 2 && reloaded.bubbleBlockMarks().isEmpty(),
                        "The two kept marks did not both explode after the relog; got " + blasts);
            } finally {
                NeoForge.EVENT_BUS.unregister(listener);
                cleanup(helper, again);
            }
            helper.succeed();
        });
    }

    // The client builds a new player for every respawn packet and loses its copy of the state with the old one,
    // so what the server keeps through a dimension change has to be sent again after that packet.
    @GameTest(template = "empty", batch = "kqParityMarkResync", timeoutTicks = 20)
    public static void keptBubbleMarksAreSentToTheOwnerAgainAfterADimensionChange(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-mark-resync", GameType.CREATIVE);
        ServerPlayer owner = kq.owner();
        ServerLevel level = helper.getLevel();
        ResourceLocation home = level.dimension().location();
        KillerQueenState state = KillerQueenLionAddon.state(owner);
        BlockPos marked = kq.pad().offset(7, 0, 0);
        level.setBlockAndUpdate(marked, Blocks.STONE.defaultBlockState());
        List<String> stale = new ArrayList<>();
        try {
            helper.assertTrue(
                    KillerQueenStateService.markBubbleBlock(owner, marked)
                            && !state.hasPrimaryBomb()
                            && !state.hasBlockBomb()
                            && !state.hasSheerHeart(),
                    "fixture: the owner does not hold bubble marks and nothing else");
            ServerLevel nether = level.getServer().getLevel(Level.NETHER);
            helper.assertTrue(
                    nether != null && owner.connection.getConnection().channel() instanceof EmbeddedChannel,
                    "fixture: no Nether or no recording connection");
            EmbeddedChannel sent = (EmbeddedChannel) owner.connection.getConnection().channel();
            Vec3 back = owner.position();

            sent.outboundMessages().clear();
            helper.assertTrue(owner.teleportTo(nether, 1.5D, 80.0D, 1.5D, Set.of(), 0.0F, 0.0F)
                            && owner.level() == nether,
                    "fixture: the owner could not change dimension");
            if (!marksSentAfterRespawn(helper, sent, owner, List.of(marked), home)) {
                stale.add("entering the Nether");
            }
            sent.outboundMessages().clear();
            helper.assertTrue(owner.teleportTo(level, back.x, back.y, back.z, Set.of(), 0.0F, 0.0F)
                            && owner.level() == level,
                    "fixture: the owner could not return");
            if (!marksSentAfterRespawn(helper, sent, owner, List.of(marked), home)) {
                stale.add("returning to the marks' dimension");
            }
            helper.assertTrue(
                    state.bubbleBlockMarks().equals(List.of(marked))
                            && KillerQueenStateService.hasBubbleBlockMarks(owner),
                    "fixture: the server did not keep the marks");
        } finally {
            cleanup(helper, owner);
        }
        helper.assertTrue(stale.isEmpty(),
                "The server keeps the bubble marks through a dimension change, but after the respawn packet the "
                        + "owner got no state sync carrying them when " + stale + ". The owner's new client "
                        + "player then has no marks: the icons are gone and kq_bubble_block_explode is hidden");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "kqParityClearTiming", timeoutTicks = 40)
    public static void primaryBombClearRunsForTheDonorSixTicks(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-clear-time", GameType.CREATIVE);
        ServerPlayer owner = kq.owner();
        StandPower power = kq.power();
        ServerLevel level = helper.getLevel();
        KillerQueenState state = KillerQueenLionAddon.state(owner);
        StandEntity stand = summon(helper, kq);
        Cow bombed = spawnCow(helper, owner, 3.0D, 0.0D);
        helper.assertTrue(KillerQueenStateService.markPrimaryBomb(owner, bombed),
                "fixture: the primary bomb was rejected");
        long[] pressedAt = {-1L};
        long[] clearedAfter = {-1L};
        long[] endedAfter = {-1L};
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(
                    press(owner, power, new Input(
                            KillerQueenLionAddon.PRIMARY_BOMB_CLEAR_NAME, InputMethod.CLICK), (short) 92),
                    "fixture: the kq_entity_quit press was refused");
            EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
            helper.assertTrue(
                    action != null
                            && action.ability == power.getAbility(KillerQueenLionAddon.PRIMARY_BOMB_CLEAR_NAME),
                    "fixture: the Stand did not start kq_entity_quit");
            pressedAt[0] = level.getGameTime();
        });
        helper.onEachTick(() -> {
            if (pressedAt[0] < 0L || endedAfter[0] >= 0L) {
                return;
            }
            long elapsed = level.getGameTime() - pressedAt[0];
            if (clearedAfter[0] < 0L && !state.hasPrimaryBomb()) {
                clearedAfter[0] = elapsed;
            }
            EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
            if (elapsed > 0L && (action == null || action.isOver())) {
                endedAfter[0] = elapsed;
            }
        });
        helper.runAtTickTime(22, () -> {
            try {
                helper.assertTrue(
                        clearedAfter[0] >= 6L && clearedAfter[0] <= 7L
                                && endedAfter[0] >= 6L && endedAfter[0] <= 7L,
                        "The donor kq_entity_quit is windup 5 + perform 1 with no recovery: the bomb is cleared "
                                + "and the Stand is free 6 ticks after the press. Bomb cleared after "
                                + clearedAfter[0] + " ticks, action over after " + endedAfter[0] + " ticks");
            } finally {
                cleanup(helper, owner, bombed);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "kqParityClearNoArms", timeoutTicks = 40)
    public static void primaryBombClearWorksWithoutStandArms(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-clear-arms", GameType.CREATIVE);
        ServerPlayer owner = kq.owner();
        StandPower power = kq.power();
        KillerQueenState state = KillerQueenLionAddon.state(owner);
        summon(helper, kq);
        Cow bombed = spawnCow(helper, owner, 3.0D, 0.0D);
        helper.assertTrue(KillerQueenStateService.markPrimaryBomb(owner, bombed),
                "fixture: the primary bomb was rejected");
        StandInstance instance = power.getStandInstance().orElse(null);
        helper.assertTrue(
                instance != null
                        && instance.removePart(StandPart.ARMS)
                        && !accepts(owner, power, new Input(
                                KillerQueenLionAddon.PRIMARY_BOMB_NAME, InputMethod.CLICK)),
                "fixture: removing the Stand's arms did not lock kq_entitybomb, which the donor does gate on arms");
        boolean pressed = press(owner, power, new Input(
                KillerQueenLionAddon.PRIMARY_BOMB_CLEAR_NAME, InputMethod.CLICK), (short) 93);
        if (!pressed) {
            instance.addPart(StandPart.ARMS);
            cleanup(helper, owner, bombed);
        }
        helper.assertTrue(pressed,
                "The donor kq_entity_quit requires no Stand part, but the port refused it without arms");
        helper.runAtTickTime(12, () -> {
            try {
                helper.assertTrue(!state.hasPrimaryBomb(),
                        "kq_entity_quit did not clear the bomb for a Stand without arms");
            } finally {
                instance.addPart(StandPart.ARMS);
                cleanup(helper, owner, bombed);
            }
            helper.succeed();
        });
    }

    // 1.16 EntityQuitBomb.checkConditions asked only that Bites the Dust was not armed, and standPerform set the bomb
    // to the user's own UUID: with no bomb the kq_entity_quit slot was there, the press was accepted, the Stand
    // played the unbomb sound and the 50 stamina were paid.
    @GameTest(template = "empty", batch = "kqParityClearNoBomb", timeoutTicks = 40)
    public static void primaryBombClearIsPressableWithNoBomb(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-clear-no-bomb", GameType.SURVIVAL);
        ServerPlayer owner = kq.owner();
        StandPower power = kq.power();
        power.skipProgression();
        power.setStamina(400.0F);
        KillerQueenState state = KillerQueenLionAddon.state(owner);
        StandEntity stand = summon(helper, kq);
        List<String> wrong = new ArrayList<>();
        Input quit = new Input(KillerQueenLionAddon.PRIMARY_BOMB_CLEAR_NAME, InputMethod.CLICK);
        try {
            helper.assertTrue(!state.hasPrimaryBomb(), "fixture: the owner has a bomb");
            if (resolve(power, quit.ability()) == null) {
                wrong.add("kq_entity_quit is hidden with no bomb");
            }
            if (!press(owner, power, quit, (short) 94)) {
                wrong.add("the press of kq_entity_quit with no bomb was refused");
            } else {
                EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
                if (action == null || action.ability != power.getAbility(quit.ability())) {
                    wrong.add("the Stand did not start kq_entity_quit after the press");
                }
            }
        } catch (RuntimeException failure) {
            cleanup(helper, owner);
            throw failure;
        }
        // the bar regenerates between ticks, so the payment is the largest single-tick drop
        float[] last = {power.getStamina()};
        float[] biggestDrop = {0.0F};
        helper.onEachTick(() -> {
            biggestDrop[0] = Math.max(biggestDrop[0], last[0] - power.getStamina());
            last[0] = power.getStamina();
        });
        helper.runAtTickTime(12, () -> {
            try {
                if (biggestDrop[0] < 40.0F) {
                    wrong.add("the 50 stamina were not paid, the bar dropped by " + biggestDrop[0] + " at most");
                }
                if (state.hasPrimaryBomb()) {
                    wrong.add("a bomb appeared");
                }
                helper.assertTrue(wrong.isEmpty(),
                        "1.16 let kq_entity_quit be pressed with no bomb and ran it all the same: " + wrong);
            } finally {
                cleanup(helper, owner);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "kqParityAttackClips", timeoutTicks = 40)
    public static void standAttacksAskForAClipTheAnimationFileHas(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-attack-clips", GameType.CREATIVE);
        ServerPlayer owner = kq.owner();
        StandPower power = kq.power();
        StandEntity stand = summon(helper, kq);
        Set<String> clips = shippedClips(helper);
        helper.runAtTickTime(2, () -> {
            List<String> idle = new ArrayList<>();
            short key = 96;
            try {
                for (AttackClip attack : ATTACK_CLIPS) {
                    String ability = attack.input().ability();
                    boolean pressed = press(owner, power, attack.input(), key);
                    EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
                    if (!pressed || action == null || action.ability != power.getAbility(ability)) {
                        idle.add(ability + " (fixture: the press did not start the action)");
                    } else {
                        // The id the client renderer looks up in the Stand skin's animation set
                        String asked = action.getEntityAnim().name();
                        String clip = withoutHand(asked);
                        if (!clip.equals(attack.clip())
                                || !clips.contains(clip)
                                || (attack.armsOnlyClip() && !clips.contains("armsOnly_" + clip))) {
                            idle.add(ability + " asks for '" + asked + "'");
                        }
                    }
                    AbilityInput.keyRelease(key, owner);
                    LivingComponentAction.getComponent(stand).setAction(null, owner, SyncType.NO_SYNC);
                    key++;
                }
            } finally {
                cleanup(helper, owner);
            }
            helper.assertTrue(idle.isEmpty(),
                    "The donor Stand poses its punch, barrage and heavy punch. The port plays the punch, "
                            + "armsOnly_punch, barrage and heavy_punch clips of killer_queen.animation.json, and "
                            + "an action that asks for a name the file lacks stays in the idle pose: " + idle);
            helper.succeed();
        });
    }

    // 1.16 kq_bubble_block_explode is built with standPose(StandPose.RANGED_ATTACK), the pose of kq_block_explode.
    // The Lion animation file carries that pose as the kq_block_explode clip and has no clip named after this action.
    @GameTest(template = "empty", batch = "kqParityBubbleDetonateClip", timeoutTicks = 40)
    public static void bubbleBlockDetonationAsksForTheRangedAttackClip(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-bubble-clip", GameType.CREATIVE);
        ServerPlayer owner = kq.owner();
        StandPower power = kq.power();
        ServerLevel level = helper.getLevel();
        StandEntity stand = summon(helper, kq);
        Set<String> clips = shippedClips(helper);
        BlockPos marked = kq.pad().offset(7, 0, 0);
        level.setBlockAndUpdate(marked, Blocks.STONE.defaultBlockState());
        helper.runAtTickTime(2, () -> {
            String asked = null;
            try {
                helper.assertTrue(
                        clips.contains("kq_block_explode")
                                && KillerQueenStateService.markBubbleBlock(owner, marked)
                                && press(owner, power, new Input(
                                        KillerQueenLionAddon.BUBBLE_BLOCK_DETONATE_NAME, InputMethod.CLICK),
                                        (short) 99),
                        "fixture: no kq_block_explode clip, or the bubble mark detonation press was refused");
                EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
                helper.assertTrue(
                        action != null
                                && action.ability == power.getAbility(
                                        KillerQueenLionAddon.BUBBLE_BLOCK_DETONATE_NAME),
                        "fixture: the Stand did not start kq_bubble_block_explode");
                // The id the client renderer looks up in the Stand skin's animation set
                asked = action.getEntityAnim().name();
                LivingComponentAction.getComponent(stand).setAction(null, owner, SyncType.NO_SYNC);
            } finally {
                cleanup(helper, owner);
            }
            helper.assertTrue(
                    asked.equals("kq_block_explode"),
                    "The donor Stand strikes the RANGED_ATTACK pose for kq_bubble_block_explode, which the port "
                            + "ships as the kq_block_explode clip. The action asks for '" + asked + "'"
                            + (clips.contains(asked) ? "" : ", a name the animation file lacks, so the Stand "
                                    + "stays in the idle pose"));
            helper.succeed();
        });
    }

    // 1.16 LivingData saves the bomb with its owner as a bare UUID ("Bomb"), CapabilityHandler only sends it again
    // on login and on a dimension change, and PunchBomb.entityInRange looks that UUID up around the owner wherever
    // the owner is. Nothing in the donor drops the bomb when its owner or its target leaves and comes back.
    @GameTest(template = "empty", batch = "kqParityPrimaryBombLifetime", timeoutTicks = 40)
    public static void primaryBombOutlivesLogoutsAndDimensionChanges(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-bomb-life", GameType.CREATIVE);
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ServerPlayer[] owner = {kq.owner()};
        UUID ownerUuid = owner[0].getUUID();
        UUID markedUuid = UUID.randomUUID();
        BlockPos seat = kq.pad().east(3);
        ServerPlayer[] marked = {kq.cleanup().join(mockPlayer(helper, markedUuid, "kq-bomb-marked", seat))};
        Cow cow = spawnCow(helper, owner[0], 0.0D, 3.0D);
        // Each stage restores the bomb the port lost before it, so one run reports every loss.
        List<String> lost = new ArrayList<>();
        List<Blast> blasts = new ArrayList<>();
        try {
            ServerLevel nether = server.getLevel(Level.NETHER);
            helper.assertTrue(
                    nether != null && owner[0].connection.getConnection().channel() instanceof EmbeddedChannel,
                    "fixture: no Nether or no recording connection");
            KillerQueenState state = KillerQueenLionAddon.state(owner[0]);
            Vec3 home = owner[0].position();
            Vec3 away = new Vec3(1.5D, 80.0D, 1.5D);
            helper.assertTrue(
                    KillerQueenStateService.markPrimaryBomb(owner[0], marked[0])
                            && KillerQueenStateService.primaryBombTarget(owner[0]) == marked[0],
                    "fixture: the primary bomb on the player was rejected");

            marked[0].connection.disconnect(Component.literal("Killer Queen parity relog"));
            helper.assertTrue(server.getPlayerList().getPlayer(markedUuid) == null,
                    "fixture: the marked player did not log out");
            boolean keptWhileOffline = markedUuid.equals(state.primaryTargetUuid());
            marked[0] = kq.cleanup().join(mockPlayer(helper, markedUuid, "kq-bomb-marked", seat));
            if (!keptWhileOffline || KillerQueenStateService.primaryBombTarget(owner[0]) != marked[0]) {
                lost.add("the marked player logged out and came back");
                KillerQueenStateService.markPrimaryBomb(owner[0], marked[0]);
            }

            move(helper, marked[0], nether, away);
            boolean keptWhileAway = markedUuid.equals(state.primaryTargetUuid());
            move(helper, marked[0], level, Vec3.atBottomCenterOf(seat));
            if (!keptWhileAway || KillerQueenStateService.primaryBombTarget(owner[0]) != marked[0]) {
                lost.add("the marked player changed dimension and came back");
                KillerQueenStateService.markPrimaryBomb(owner[0], marked[0]);
            }

            EmbeddedChannel sent = (EmbeddedChannel) owner[0].connection.getConnection().channel();
            sent.outboundMessages().clear();
            move(helper, owner[0], nether, away);
            boolean keptInNether = markedUuid.equals(state.primaryTargetUuid());
            boolean resent = syncSentAfterRespawn(
                    helper, sent, owner[0], sync -> markedUuid.equals(sync.primaryTargetUuid()));
            move(helper, owner[0], level, home);
            if (!keptInNether || KillerQueenStateService.primaryBombTarget(owner[0]) != marked[0]) {
                lost.add("the owner changed dimension and came back");
            } else if (!resent) {
                lost.add("the owner changed dimension and the new client player was not sent the kept bomb");
            }

            move(helper, owner[0], nether, away);
            move(helper, marked[0], nether, away);
            helper.assertTrue(
                    KillerQueenStateService.markPrimaryBomb(owner[0], marked[0])
                            && markedUuid.equals(state.primaryTargetUuid()),
                    "fixture: the primary bomb placed in the Nether was rejected");
            move(helper, marked[0], level, Vec3.atBottomCenterOf(seat));
            move(helper, owner[0], level, home);
            if (KillerQueenStateService.primaryBombTarget(owner[0]) != marked[0]) {
                lost.add("a bomb placed in the Nether, after its owner and its target both came to the Overworld "
                        + "(the donor's bomb is a UUID with no dimension)");
            }

            // No donor handler for a death or a Clone touches the bomb: a marked player who dies and respawns, or
            // who is cloned on the way back from the End, is still the bomb; a dead mob's body is the bomb until it
            // leaves the level, then its UUID matches nothing.
            Vec3 seatSpot = Vec3.atBottomCenterOf(seat);
            helper.assertTrue(
                    KillerQueenStateService.markPrimaryBomb(owner[0], marked[0])
                            && markedUuid.equals(state.primaryTargetUuid()),
                    "fixture: the primary bomb on the player was rejected before the death");
            // A player who just changed dimension is invulnerable until the next tick clears the flag
            marked[0].hasChangedDimension();
            marked[0].kill();
            helper.assertTrue(marked[0].isDeadOrDying(), "fixture: the marked player did not die");
            boolean keptOnDeath = markedUuid.equals(state.primaryTargetUuid());
            marked[0] = kq.cleanup().join(respawn(marked[0], false, Entity.RemovalReason.KILLED));
            marked[0].moveTo(seatSpot.x, seatSpot.y, seatSpot.z, 0.0F, 0.0F);
            if (!keptOnDeath || KillerQueenStateService.primaryBombTarget(owner[0]) != marked[0]) {
                lost.add("the marked player died and respawned");
                KillerQueenStateService.markPrimaryBomb(owner[0], marked[0]);
            }

            marked[0] = kq.cleanup().join(respawn(marked[0], true, Entity.RemovalReason.CHANGED_DIMENSION));
            marked[0].moveTo(seatSpot.x, seatSpot.y, seatSpot.z, 0.0F, 0.0F);
            if (!markedUuid.equals(state.primaryTargetUuid())
                    || KillerQueenStateService.primaryBombTarget(owner[0]) != marked[0]) {
                lost.add("the marked player was cloned without dying (the return from the End)");
                KillerQueenStateService.markPrimaryBomb(owner[0], marked[0]);
            }

            Cow doomed = spawnCow(helper, owner[0], 0.0D, -3.0D);
            try {
                helper.assertTrue(
                        KillerQueenStateService.markPrimaryBomb(owner[0], doomed)
                                && KillerQueenStateService.primaryBombTarget(owner[0]) == doomed,
                        "fixture: the primary bomb on the doomed cow was rejected");
                doomed.kill();
                helper.assertTrue(!doomed.isAlive(), "fixture: the marked cow did not die");
                if (KillerQueenStateService.primaryBombTarget(owner[0]) != doomed) {
                    lost.add("the marked mob died and its body, still in the level, was no longer the bomb");
                }
            } finally {
                doomed.discard();
            }
            if (state.hasPrimaryBomb()) {
                lost.add("the marked mob's body left the level and the bomb stayed on its owner");
                state.clearPrimaryBomb();
            }
            helper.assertTrue(lost.isEmpty(), "The primary bomb was lost where the donor keeps it: " + lost);

            helper.assertTrue(
                    KillerQueenStateService.markPrimaryBomb(owner[0], cow)
                            && KillerQueenStateService.primaryBombTarget(owner[0]) == cow,
                    "fixture: the primary bomb on the cow was rejected");
            owner[0].connection.disconnect(Component.literal("Killer Queen parity relog"));
            helper.assertTrue(server.getPlayerList().getPlayer(ownerUuid) == null,
                    "fixture: the owner did not log out");
            owner[0] = kq.cleanup().join(mockPlayer(helper, ownerUuid, "kq-bomb-life", null));
            StandPower power = StandPower.get(owner[0]);
            helper.assertTrue(
                    power != null && KillerQueenLionAddon.isKillerQueenUser(owner[0]),
                    "fixture: the Stand did not survive the relog");
            KillerQueenState reloaded = KillerQueenLionAddon.state(owner[0]);
            if (!cow.getUUID().equals(reloaded.primaryTargetUuid())
                    || KillerQueenStateService.primaryBombTarget(owner[0]) != cow) {
                lost.add("the owner logged out and came back (the donor saves the bomb with the player)");
            }
            helper.assertTrue(lost.isEmpty(), "The primary bomb was lost where the donor keeps it: " + lost);

            power.setStamina(power.getMaxStamina());
            StandEntity stand = summon(helper, new Fixture(owner[0], power, kq.pad(), kq.cleanup()));
            helper.assertTrue(
                    accepts(owner[0], power, new Input(
                            KillerQueenLionAddon.PRIMARY_BOMB_DETONATE_NAME, InputMethod.HOLD)),
                    "The kept bomb cannot be detonated after the relog");
            Vec3 bombed = cow.position();
            Consumer<ExplosionEvent.Start> listener = recordBlasts(level, owner[0], blasts);
            try {
                performNow(helper, owner[0], power, stand, KillerQueenLionAddon.PRIMARY_BOMB_DETONATE_NAME);
            } finally {
                NeoForge.EVENT_BUS.unregister(listener);
            }
            helper.assertTrue(
                    blasts.size() == 1
                            && blasts.get(0).center().distanceToSqr(bombed) < 1.0E-8D
                            && !reloaded.hasPrimaryBomb(),
                    "The kept bomb did not explode at the cow and clear after the relog; got " + blasts);
        } finally {
            if (server.getPlayerList().getPlayer(markedUuid) == marked[0]) {
                marked[0].connection.disconnect(Component.literal("Killer Queen parity GameTest complete"));
            }
            if (server.getPlayerList().getPlayer(ownerUuid) == owner[0]) {
                cleanup(helper, owner[0], cow);
            } else {
                cow.discard();
                KillerQueenGameTests.sheerHeartPad(helper, false);
            }
        }
        helper.succeed();
    }

    // 1.16 StandEntityTask pays a Stand action's cost after standPerform and StandAction.onPerform before perform,
    // both with the result ignored, and the bomb kit has no stamina condition of its own: on a short bar every one
    // of these starts and acts, the bar drops to 0 and no stamina message is sent.
    @GameTest(template = "empty", batch = "kqParityStaminaShortBar", timeoutTicks = 20)
    public static void bombKitStartsOnAShortBarAndDrainsIt(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-short-bar", GameType.SURVIVAL);
        ServerPlayer owner = kq.owner();
        StandPower power = kq.power();
        ServerLevel level = helper.getLevel();
        StandEntity stand = staminaFixture(helper, kq);
        KillerQueenState state = KillerQueenLionAddon.state(owner);
        EmbeddedChannel sent = (EmbeddedChannel) owner.connection.getConnection().channel();
        List<String> wrong = new ArrayList<>();
        // In front of the user, who looks along +Z
        Cow cow = spawnCow(helper, owner, 0.0D, 1.5D);
        try {
            sent.outboundMessages().clear();
            aimStand(stand, new ActionTarget(cow));
            runOnShortBar(helper, owner, power, stand, KillerQueenLionAddon.PRIMARY_BOMB_NAME, 100.0F,
                    () -> KillerQueenStateService.primaryBombTarget(owner) == cow, wrong);
            aimStand(stand, ActionTarget.EMPTY);
            if (!state.hasPrimaryBomb()) {
                helper.assertTrue(KillerQueenStateService.markPrimaryBomb(owner, cow),
                        "fixture: the primary bomb was rejected");
            }
            runOnShortBar(helper, owner, power, stand, KillerQueenLionAddon.PRIMARY_BOMB_CLEAR_NAME, 30.0F,
                    () -> !state.hasPrimaryBomb(), wrong);

            BlockPos block = kq.pad().offset(4, 0, -2);
            level.setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
            helper.assertTrue(KillerQueenStateService.markBlockBomb(owner, block),
                    "fixture: the block bomb was rejected");
            runOnShortBar(helper, owner, power, stand, KillerQueenLionAddon.BLOCK_BOMB_CLEAR_NAME, 2.0F,
                    () -> !state.hasBlockBomb(), wrong);
            if (!state.hasBlockBomb()) {
                helper.assertTrue(KillerQueenStateService.markBlockBomb(owner, block),
                        "fixture: the second block bomb was rejected");
            }
            runOnShortBar(helper, owner, power, stand, KillerQueenLionAddon.BLOCK_BOMB_DETONATE_NAME, 100.0F,
                    () -> !state.hasBlockBomb(), wrong);

            runOnShortBar(helper, owner, power, stand, KillerQueenLionAddon.BUBBLE_MARK_NAME, 20.0F,
                    () -> !level.getEntitiesOfClass(BubbleMarkEntity.class, owner.getBoundingBox().inflate(8.0D),
                            mark -> mark.isOwnedBy(owner.getUUID())).isEmpty(), wrong);
            level.getEntitiesOfClass(BubbleMarkEntity.class, owner.getBoundingBox().inflate(8.0D),
                    mark -> mark.isOwnedBy(owner.getUUID())).forEach(Entity::discard);
            BlockPos marked = kq.pad().offset(-4, 0, -2);
            level.setBlockAndUpdate(marked, Blocks.STONE.defaultBlockState());
            helper.assertTrue(KillerQueenStateService.markBubbleBlock(owner, marked),
                    "fixture: the bubble mark was rejected");
            runOnShortBar(helper, owner, power, stand, KillerQueenLionAddon.BUBBLE_BLOCK_DETONATE_NAME, 100.0F,
                    () -> !KillerQueenStateService.hasBubbleBlockMarks(owner), wrong);

            owner.getInventory().add(new ItemStack(Items.SNOWBALL, 2));
            runOnShortBar(helper, owner, power, stand, KillerQueenLionAddon.SNOW_BOMB_NAME, 50.0F,
                    () -> owner.getInventory().countItem(Items.SNOWBALL) == 1 && bombProjectiles(owner) == 1,
                    wrong);
            runOnShortBar(helper, owner, power, stand, KillerQueenLionAddon.BUBBLE_BOMB_NAME, 30.0F,
                    () -> bombProjectiles(owner) == 6, wrong);
            runOnShortBar(helper, owner, power, stand, KillerQueenLionAddon.PROJECTILE_DETONATE_NAME, 60.0F,
                    () -> bombProjectiles(owner) == 0, wrong);

            runOnShortBar(helper, owner, power, stand, KillerQueenLionAddon.SHEER_HEART_NAME, 100.0F,
                    state::hasSheerHeart, wrong);
            KillerQueenStateService.recallSheerHeart(owner);

            runOnShortBar(helper, owner, power, owner, KillerQueenLionAddon.TIME_MARK_NAME, 20.0F,
                    state::hasBitesTheDustMarker, wrong);
            power.setStamina(100.0F);
            Ability reset = power.getAbility(KillerQueenLionAddon.RESET_DAY_NAME);
            if (reset == null || !reset.checkSpecificConditions(power).isPositive()) {
                wrong.add(KillerQueenLionAddon.RESET_DAY_NAME + " refused at 100 stamina");
            }
            if (staminaMessageSent(sent)) {
                wrong.add("a not-enough-stamina message was sent");
            }
        } finally {
            cleanup(helper, owner, cow);
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 has no stamina condition on these: " + wrong);
        helper.succeed();
    }

    // 1.16 BlockBombAction, ItemFBombExplode and EntityExplode act in standPerform only on more than 124, 20 and
    // 249 stamina, read before the Stand task pays 75, 20 and 250 whatever came of it. The action still starts
    // below that and says nothing.
    @GameTest(template = "empty", batch = "kqParityStaminaFloors", timeoutTicks = 20)
    public static void bombKitKeepsTheDonorStaminaFloorsSilently(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-stamina-floor", GameType.SURVIVAL);
        ServerPlayer owner = kq.owner();
        StandPower power = kq.power();
        ServerLevel level = helper.getLevel();
        StandEntity stand = staminaFixture(helper, kq);
        KillerQueenState state = KillerQueenLionAddon.state(owner);
        EmbeddedChannel sent = (EmbeddedChannel) owner.connection.getConnection().channel();
        List<String> wrong = new ArrayList<>();
        Cow cow = spawnCow(helper, owner, 0.0D, 6.0D);
        ItemEntity[] item = {null};
        try {
            sent.outboundMessages().clear();
            BlockPos block = kq.pad().offset(2, 0, 0);
            level.setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
            // 1.16 BlockBombAction.standPerform bombed the block its user looked at
            owner.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(block));
            aimStand(stand, new ActionTarget(block, Direction.UP));
            runAtFloor(helper, owner, power, stand, KillerQueenLionAddon.BLOCK_BOMB_NAME, 124.0F, 49.0F,
                    () -> !state.hasBlockBomb(), "marked the block", wrong);
            aimStand(stand, new ActionTarget(block, Direction.UP));
            runAtFloor(helper, owner, power, stand, KillerQueenLionAddon.BLOCK_BOMB_NAME, 125.0F, 50.0F,
                    () -> block.equals(KillerQueenStateService.blockBombPos(owner)),
                    "did not mark the block above the floor", wrong);
            aimStand(stand, ActionTarget.EMPTY);
            KillerQueenStateService.clearBlockBomb(owner);

            owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND, 2));
            helper.assertTrue(ItemBombService.markOffhandItem(owner), "fixture: the item bomb was rejected");
            item[0] = new ItemEntity(level, owner.getX() + 4.0D, owner.getY(), owner.getZ(),
                    owner.getMainHandItem().copy());
            item[0].setPickUpDelay(100);
            owner.getInventory().clearContent();
            helper.assertTrue(level.addFreshEntity(item[0]), "fixture: the item bomb could not be dropped");
            runAtFloor(helper, owner, power, stand, KillerQueenLionAddon.ITEM_BOMB_DETONATE_NAME, 20.0F, 0.0F,
                    () -> item[0].isAlive(), "detonated the item", wrong);
            runAtFloor(helper, owner, power, stand, KillerQueenLionAddon.ITEM_BOMB_DETONATE_NAME, 21.0F, 1.0F,
                    () -> !item[0].isAlive(), "did not detonate the item above the floor", wrong);

            helper.assertTrue(KillerQueenStateService.markPrimaryBomb(owner, cow),
                    "fixture: the primary bomb was rejected");
            runAtFloor(helper, owner, power, stand, KillerQueenLionAddon.PRIMARY_BOMB_DETONATE_NAME, 249.0F, 0.0F,
                    () -> KillerQueenStateService.primaryBombTarget(owner) == cow, "exploded the bomb", wrong);
            runAtFloor(helper, owner, power, stand, KillerQueenLionAddon.PRIMARY_BOMB_DETONATE_NAME, 250.0F, 0.0F,
                    () -> !state.hasPrimaryBomb(), "did not explode the bomb above the floor", wrong);
            if (staminaMessageSent(sent)) {
                wrong.add("a not-enough-stamina message was sent");
            }
        } finally {
            cleanup(helper, owner, cow, item[0]);
        }
        helper.assertTrue(wrong.isEmpty(), "The 1.16 stamina floors were not kept: " + wrong);
        helper.succeed();
    }

    // 1.16 SheerHeartAttack.standPerform sends seven bombs, each put on the Stand and moved by
    // (cos(i * 1.2566) * 0.8, 0, sin(i * 1.2566) * 0.8), and gives only the first the task target. isSHpresent
    // refuses a new summon while one of them is out, and SHABack kills them all.
    @GameTest(template = "empty", batch = "kqParitySheerHeartSeven", timeoutTicks = 40)
    public static void sheerHeartAttackSendsSevenBombsAndRecallTakesThemAll(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-sha-seven", GameType.CREATIVE);
        ServerPlayer owner = kq.owner();
        StandPower power = kq.power();
        KillerQueenState state = KillerQueenLionAddon.state(owner);
        StandEntity stand = summon(helper, kq);
        Ability sheerHeart = power.getAbility(KillerQueenLionAddon.SHEER_HEART_NAME);
        Ability recall = power.getAbility(KillerQueenLionAddon.SHEER_HEART_RECALL_NAME);
        Cow cow = spawnCow(helper, owner, 0.0D, 6.0D);
        List<String> wrong = new ArrayList<>();
        List<SheerHeartAttackEntity> bombs = new ArrayList<>();
        try {
            helper.assertTrue(
                    sheerHeart != null && recall != null && sheerHeart.checkSpecificConditions(power).isPositive(),
                    "fixture: sheer_heart is missing or refused before any bomb is out");
            aimStand(stand, new ActionTarget(cow));
            Vec3 origin = stand.position();
            performNow(helper, owner, power, stand, KillerQueenLionAddon.SHEER_HEART_NAME);
            aimStand(stand, ActionTarget.EMPTY);
            bombs.addAll(ownedSheerHearts(owner));
            if (bombs.size() != 7) {
                wrong.add("sent " + bombs.size() + " bombs, not 7");
            }
            List<Vec3> spots = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                spots.add(origin.add(Math.cos(i * 1.2566D) * 0.8D, 0.0D, Math.sin(i * 1.2566D) * 0.8D));
            }
            Vec3 first = spots.getFirst();
            for (SheerHeartAttackEntity bomb : bombs) {
                int spot = -1;
                for (int i = 0; i < spots.size() && spot < 0; i++) {
                    if (spots.get(i).distanceToSqr(bomb.position()) < 1.0E-10D) {
                        spot = i;
                    }
                }
                if (spot < 0) {
                    wrong.add("a bomb at " + bomb.position() + " is on no ring spot around the Stand at " + origin);
                } else {
                    spots.remove(spot);
                }
                if (!bomb.isInvulnerable()) {
                    wrong.add("a bomb is not flagged invulnerable");
                }
            }
            if (!spots.isEmpty()) {
                wrong.add("no bomb on the ring spots " + spots);
            }
            // the first bomb took the task's target, which SheerHeartAttack never kept (no standKeepsTarget)
            if (bombs.stream().anyMatch(bomb -> bomb.getTarget() != null)) {
                wrong.add("a bomb started with a target, the aimed cow "
                        + bombs.stream().filter(bomb -> bomb.getTarget() == cow).count() + " times");
            }
            if (sheerHeart.checkSpecificConditions(power).isPositive()) {
                wrong.add("sheer_heart was accepted again while its bombs are out");
            }
        } catch (RuntimeException | Error failure) {
            cleanup(helper, owner, cow);
            throw failure;
        }
        helper.runAtTickTime(3, () -> {
            try {
                long left = bombs.stream().filter(bomb -> !bomb.isRemoved()).count();
                if (left != bombs.size()) {
                    wrong.add((bombs.size() - left) + " of the bombs were dropped on their first ticks");
                }
                if (!recall.checkSpecificConditions(power).isPositive()) {
                    wrong.add("shear_heart_back was refused with the bombs out");
                } else {
                    performNow(helper, owner, power, stand, KillerQueenLionAddon.SHEER_HEART_RECALL_NAME);
                }
                if (bombs.stream().anyMatch(bomb -> !bomb.isRemoved()) || !ownedSheerHearts(owner).isEmpty()) {
                    wrong.add("shear_heart_back left " + ownedSheerHearts(owner).size() + " bombs");
                }
                if (state.hasSheerHeart() || !sheerHeart.checkSpecificConditions(power).isPositive()) {
                    wrong.add("sheer_heart was not usable again after the recall");
                }
            } finally {
                cleanup(helper, owner, cow);
            }
            helper.assertTrue(wrong.isEmpty(), "1.16 Sheer Heart Attack sends seven bombs: " + wrong);
            helper.succeed();
        });
    }

    // 1.16 SheerHeart.explodeCreeper removes a bomb whose blast left its target dead; isSHpresent lets the owner
    // send Sheer Heart Attack again once none of the seven is left.
    @GameTest(template = "empty", batch = "kqParitySheerHeartKill", timeoutTicks = 100)
    public static void aSheerHeartWhoseBlastKillsItsTargetIsGone(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-sha-kill", GameType.CREATIVE);
        ServerPlayer owner = kq.owner();
        StandPower power = kq.power();
        KillerQueenState state = KillerQueenLionAddon.state(owner);
        StandEntity stand = summon(helper, kq);
        Ability sheerHeart = power.getAbility(KillerQueenLionAddon.SHEER_HEART_NAME);
        Cow first = spawnCow(helper, owner, 0.0D, 6.0D);
        Cow second = spawnCow(helper, owner, 0.0D, -6.0D);
        List<String> wrong = new ArrayList<>();
        SheerHeartAttackEntity[] lead = {null};
        SheerHeartAttackEntity[] last = {null};
        try {
            helper.assertTrue(sheerHeart != null, "fixture: sheer_heart is missing");
            aimStand(stand, new ActionTarget(first));
            performNow(helper, owner, power, stand, KillerQueenLionAddon.SHEER_HEART_NAME);
            aimStand(stand, ActionTarget.EMPTY);
            // the summon gives no bomb a target (1.16 SheerHeartAttack kept none); one is set on the first bomb
            lead[0] = ownedSheerHearts(owner).stream().findFirst().orElse(null);
            helper.assertTrue(lead[0] != null, "fixture: no bomb was sent");
            lead[0].setTarget(first);
            first.setPos(lead[0].getX() + 1.0D, lead[0].getY(), lead[0].getZ());
        } catch (RuntimeException | Error failure) {
            cleanup(helper, owner, first, second);
            throw failure;
        }
        helper.runAtTickTime(30, () -> {
            try {
                helper.assertTrue(!first.isAlive(), "fixture: the first cow survived the blast");
                if (!lead[0].isRemoved()) {
                    wrong.add("the bomb whose blast killed its target stayed (idle "
                            + lead[0].postExplosionIdleTicks() + ")");
                }
                List<SheerHeartAttackEntity> rest = ownedSheerHearts(owner).stream()
                        .filter(bomb -> bomb != lead[0]).toList();
                if (rest.isEmpty()) {
                    wrong.add("no other bomb of the summon is left");
                } else {
                    if (!state.hasSheerHeart() || sheerHeart.checkSpecificConditions(power).isPositive()) {
                        wrong.add("sheer_heart was accepted again while " + rest.size() + " bombs are out");
                    }
                    last[0] = rest.getFirst();
                    rest.stream().skip(1).forEach(Entity::discard);
                    // Bombs stroll; only the pad's own chunk ticks entities
                    BlockPos spot = kq.pad().offset(10, 0, 0);
                    last[0].moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, 0.0F, 0.0F);
                    last[0].setDeltaMovement(Vec3.ZERO);
                    second.setPos(last[0].getX() + 1.0D, last[0].getY(), last[0].getZ());
                    last[0].setTarget(second);
                }
            } catch (RuntimeException | Error failure) {
                cleanup(helper, owner, first, second);
                throw failure;
            }
        });
        helper.runAtTickTime(80, () -> {
            try {
                if (last[0] != null) {
                    helper.assertTrue(!second.isAlive(), "fixture: the second cow survived the blast; bomb at "
                            + last[0].position() + " swell=" + last[0].swellTicks() + " idle="
                            + last[0].postExplosionIdleTicks() + " entityTicking="
                            + helper.getLevel().isPositionEntityTicking(last[0].blockPosition()) + ", " + wrong);
                    if (!last[0].isRemoved()) {
                        wrong.add("the last bomb stayed after its blast killed its target");
                    }
                    if (state.hasSheerHeart() || !sheerHeart.checkSpecificConditions(power).isPositive()) {
                        wrong.add("sheer_heart stayed refused after the last bomb was gone");
                    }
                }
            } finally {
                cleanup(helper, owner, first, second);
            }
            helper.assertTrue(wrong.isEmpty(), "1.16 removes a Sheer Heart that kills its target: " + wrong);
            helper.succeed();
        });
    }

    // 1.16 SheerHeartAttack.isSHpresent looked only for bombs within 140 blocks of the user, and nothing removes a
    // bomb for its owner's distance: bombs left farther away stay and hunt but no longer block a new summon, and they
    // live on beside the new ones when their owner comes back.
    @GameTest(template = "empty", batch = "kqParitySheerHeartFarOwner", timeoutTicks = 100)
    public static void sheerHeartsLeftFarFromTheirOwnerStayAndFreeTheSummon(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-sha-far", GameType.CREATIVE);
        ServerPlayer owner = kq.owner();
        StandPower power = kq.power();
        KillerQueenState state = KillerQueenLionAddon.state(owner);
        StandEntity stand = summon(helper, kq);
        Ability sheerHeart = power.getAbility(KillerQueenLionAddon.SHEER_HEART_NAME);
        List<SheerHeartAttackEntity> bombs = new ArrayList<>();
        List<DamageSource> blasts = new ArrayList<>();
        List<Entity> spawned = new ArrayList<>();
        List<Consumer<LivingIncomingDamageEvent>> hits = new ArrayList<>();
        List<String> wrong = new ArrayList<>();
        Vec3 home = owner.position();
        Vec3 far = home.add(SheerHeartAttackEntity.MAX_OWNER_DISTANCE + 20.0D, 0.0D, 0.0D);
        // The bombs are ordinary despawning mobs: a player near them, not their owner, is what keeps them
        ServerPlayer bystander = kq.cleanup().join(
                mockPlayer(helper, UUID.randomUUID(), "kq-sha-far-bystander", kq.pad().south(6)));
        bystander.setGameMode(GameType.CREATIVE);
        Runnable done = () -> {
            hits.forEach(NeoForge.EVENT_BUS::unregister);
            bystander.connection.disconnect(Component.literal("Killer Queen parity GameTest complete"));
            cleanup(helper, owner, spawned.toArray(Entity[]::new));
        };
        try {
            aimStand(stand, ActionTarget.EMPTY);
            performNow(helper, owner, power, stand, KillerQueenLionAddon.SHEER_HEART_NAME);
            bombs.addAll(ownedSheerHearts(owner));
            helper.assertTrue(bombs.size() == 7 && state.hasSheerHeart(),
                    "fixture: Sheer Heart Attack did not send its seven bombs: " + bombs.size());
            UUID first = state.sheerHeartUuid();
            Husk husk = toughHuskBeside(helper, bombs.getFirst());
            spawned.add(husk);
            hits.add(recordBlastsOn(husk, blasts));
            owner.moveTo(far.x, far.y, far.z, owner.getYRot(), owner.getXRot());
            // One bomb's own tick, before any tick of its owner
            bombs.getFirst().tick();
            if (state.hasSheerHeart()) {
                wrong.add("a bomb's own tick did not let the summon go with its owner 160 blocks away");
            }
            bombs.stream().filter(bomb -> !bomb.isRemoved()).forEach(Entity::tick);
            long removed = bombs.stream().filter(Entity::isRemoved).count();
            if (removed > 0) {
                wrong.add(removed + " of the seven bombs were removed for their owner 160 blocks away");
            }
            if (!sheerHeart.checkSpecificConditions(power).isPositive()) {
                wrong.add("Sheer Heart Attack was refused with its only bombs 160 blocks away");
            } else {
                performNow(helper, owner, power, stand, KillerQueenLionAddon.SHEER_HEART_NAME);
                UUID second = state.sheerHeartUuid();
                if (second == null || second.equals(first)) {
                    wrong.add("no second summon was sent with the first bombs 160 blocks away");
                } else {
                    owner.moveTo(home.x, home.y, home.z, owner.getYRot(), owner.getXRot());
                    bombs.stream().filter(bomb -> !bomb.isRemoved()).forEach(Entity::tick);
                    removed = bombs.stream().filter(Entity::isRemoved).count();
                    if (removed > 0) {
                        wrong.add(removed + " of the first bombs were removed when their owner came back with a "
                                + "second summon out");
                    } else if (!second.equals(state.sheerHeartUuid())) {
                        wrong.add("the first bombs took the summon from the second ones");
                    }
                    // The second summon has been sent; the first bombs now hunt alone with their owner far again. A
                    // recall here would take both summons, as 1.16 SHABack does.
                    ownedSheerHearts(owner).stream()
                            .filter(bomb -> second.equals(bomb.summonLink()))
                            .forEach(Entity::discard);
                    KillerQueenStateService.releaseSheerHeart(owner, second);
                    owner.moveTo(far.x, far.y, far.z, owner.getYRot(), owner.getXRot());
                }
            }
        } catch (RuntimeException | Error failure) {
            done.run();
            throw failure;
        }
        helper.runAtTickTime(80, () -> {
            try {
                long left = bombs.stream().filter(bomb -> !bomb.isRemoved()).count();
                if (left != bombs.size()) {
                    wrong.add((bombs.size() - left) + " of the first bombs did not stay with their owner 160 blocks "
                            + "away");
                }
                if (blasts.isEmpty()) {
                    wrong.add("no bomb blasted the monster beside them with their owner 160 blocks away");
                }
            } finally {
                done.run();
            }
            helper.assertTrue(wrong.isEmpty(),
                    "1.16 keeps Sheer Hearts far from their owner and frees the summon: " + wrong);
            helper.succeed();
        });
    }

    // 1.16 SheerHeart has no death handler and no Killer Queen handler touches it on a death: the bombs of an owner
    // who died stay and hunt, and isSHpresent finds them around him again when he respawns beside them.
    @GameTest(template = "empty", batch = "kqParitySheerHeartOwnerDeath", timeoutTicks = 100)
    public static void sheerHeartsOutliveTheirOwnersDeath(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-sha-death", GameType.CREATIVE);
        MinecraftServer server = helper.getLevel().getServer();
        UUID ownerUuid = kq.owner().getUUID();
        ServerPlayer[] owner = {kq.owner()};
        StandPower power = kq.power();
        KillerQueenState state = KillerQueenLionAddon.state(owner[0]);
        StandEntity stand = summon(helper, kq);
        List<SheerHeartAttackEntity> bombs = new ArrayList<>();
        List<DamageSource> blasts = new ArrayList<>();
        List<Entity> spawned = new ArrayList<>();
        List<Consumer<LivingIncomingDamageEvent>> hits = new ArrayList<>();
        List<String> wrong = new ArrayList<>();
        UUID[] link = {null};
        Runnable done = () -> {
            hits.forEach(NeoForge.EVENT_BUS::unregister);
            bombs.forEach(Entity::discard);
            if (server.getPlayerList().getPlayer(ownerUuid) == owner[0]) {
                cleanup(helper, owner[0], spawned.toArray(Entity[]::new));
            } else {
                spawned.forEach(Entity::discard);
                KillerQueenGameTests.sheerHeartPad(helper, false);
            }
        };
        try {
            aimStand(stand, ActionTarget.EMPTY);
            performNow(helper, owner[0], power, stand, KillerQueenLionAddon.SHEER_HEART_NAME);
            bombs.addAll(ownedSheerHearts(owner[0]));
            helper.assertTrue(bombs.size() == 7 && state.hasSheerHeart(),
                    "fixture: Sheer Heart Attack did not send its seven bombs: " + bombs.size());
            link[0] = state.sheerHeartUuid();
            Husk husk = toughHuskBeside(helper, bombs.getFirst());
            spawned.add(husk);
            hits.add(recordBlastsOn(husk, blasts));
            owner[0].setGameMode(GameType.SURVIVAL);
            owner[0].kill();
            helper.assertTrue(owner[0].isDeadOrDying(), "fixture: the owner did not die");
            long removed = bombs.stream().filter(Entity::isRemoved).count();
            if (removed > 0) {
                wrong.add("its owner's death removed " + removed + " of the seven bombs");
            }
            bombs.stream().filter(bomb -> !bomb.isRemoved()).forEach(Entity::tick);
            long dropped = bombs.stream().filter(Entity::isRemoved).count() - removed;
            if (dropped > 0) {
                wrong.add(dropped + " bombs dropped themselves on their first tick after their owner's death");
            }
            if (state.hasSheerHeart()) {
                wrong.add("the dead owner still holds the summon");
            }
        } catch (RuntimeException | Error failure) {
            done.run();
            throw failure;
        }
        helper.runAtTickTime(80, () -> {
            try {
                long left = bombs.stream().filter(bomb -> !bomb.isRemoved()).count();
                if (left > 0 && blasts.isEmpty()) {
                    wrong.add("no bomb blasted the monster beside them while their owner was dead");
                }
                owner[0] = kq.cleanup().join(respawn(owner[0], false, Entity.RemovalReason.KILLED));
                BlockPos pad = kq.pad();
                owner[0].moveTo(pad.getX() + 0.5D, pad.getY(), pad.getZ() + 0.5D, 0.0F, 0.0F);
                bombs.stream().filter(bomb -> !bomb.isRemoved()).forEach(Entity::tick);
                left = bombs.stream().filter(bomb -> !bomb.isRemoved()).count();
                if (left != bombs.size()) {
                    wrong.add((bombs.size() - left) + " of the seven bombs were gone after their owner died and "
                            + "respawned");
                } else if (!link[0].equals(KillerQueenLionAddon.state(owner[0]).sheerHeartUuid())) {
                    wrong.add("the bombs were not his again when he respawned beside them");
                }
            } finally {
                done.run();
            }
            helper.assertTrue(wrong.isEmpty(), "1.16 keeps the Sheer Hearts of an owner who died: " + wrong);
            helper.succeed();
        });
    }

    // 1.16 SheerHeart has no Stand check of its own: when their owner, alive and beside them, no longer has Killer
    // Queen the bombs stay and hunt, and isSHpresent counts them around him again once he has the Stand back.
    @GameTest(template = "empty", batch = "kqParitySheerHeartStandLost", timeoutTicks = 100)
    public static void sheerHeartsOutliveTheirOwnersLossOfKillerQueen(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-sha-standless", GameType.CREATIVE);
        ServerPlayer owner = kq.owner();
        StandPower power = kq.power();
        KillerQueenState state = KillerQueenLionAddon.state(owner);
        StandEntity stand = summon(helper, kq);
        List<SheerHeartAttackEntity> bombs = new ArrayList<>();
        List<DamageSource> blasts = new ArrayList<>();
        List<Entity> spawned = new ArrayList<>();
        List<Consumer<LivingIncomingDamageEvent>> hits = new ArrayList<>();
        List<String> wrong = new ArrayList<>();
        UUID[] link = {null};
        Runnable done = () -> {
            hits.forEach(NeoForge.EVENT_BUS::unregister);
            cleanup(helper, owner, spawned.toArray(Entity[]::new));
        };
        try {
            aimStand(stand, ActionTarget.EMPTY);
            performNow(helper, owner, power, stand, KillerQueenLionAddon.SHEER_HEART_NAME);
            bombs.addAll(ownedSheerHearts(owner));
            helper.assertTrue(bombs.size() == 7 && state.hasSheerHeart(),
                    "fixture: Sheer Heart Attack did not send its seven bombs: " + bombs.size());
            link[0] = state.sheerHeartUuid();
            Husk husk = toughHuskBeside(helper, bombs.getFirst());
            spawned.add(husk);
            hits.add(recordBlastsOn(husk, blasts));
            power.setStand(null);
            helper.assertTrue(!KillerQueenLionAddon.isKillerQueenUser(owner), "fixture: the owner kept Killer Queen");
            // A mock player runs no player tick by itself; his own tick is what lets go of the summon
            owner.doTick();
            helper.assertTrue(!state.hasSheerHeart(), "fixture: his state still held the bombs after his tick");
            bombs.forEach(Entity::tick);
            long removed = bombs.stream().filter(Entity::isRemoved).count();
            if (removed > 0) {
                wrong.add(removed + " of the seven bombs were removed when their owner beside them lost Killer Queen");
            }
        } catch (RuntimeException | Error failure) {
            done.run();
            throw failure;
        }
        helper.runAtTickTime(80, () -> {
            try {
                long left = bombs.stream().filter(bomb -> !bomb.isRemoved()).count();
                if (left != bombs.size()) {
                    wrong.add((bombs.size() - left) + " of the seven bombs did not stay while their owner had no "
                            + "Killer Queen");
                }
                if (left > 0 && blasts.isEmpty()) {
                    wrong.add("no bomb blasted the monster beside them while their owner had no Killer Queen");
                }
                power.setStand(KillerQueenLionAddon.KILLER_QUEEN.get());
                summon(helper, kq);
                bombs.stream().filter(bomb -> !bomb.isRemoved()).forEach(Entity::tick);
                Ability sheerHeart = power.getAbility(KillerQueenLionAddon.SHEER_HEART_NAME);
                if (left == 0 || !link[0].equals(state.sheerHeartUuid())) {
                    wrong.add("the bombs were not his again when he had Killer Queen back beside them");
                } else if (sheerHeart == null || sheerHeart.isAbilityAvailable(power)) {
                    wrong.add("Sheer Heart Attack was offered again with his bombs beside him");
                }
            } finally {
                done.run();
            }
            helper.assertTrue(wrong.isEmpty(),
                    "1.16 keeps the Sheer Hearts of an owner who lost Killer Queen: " + wrong);
            helper.succeed();
        });
    }

    // 1.16 SheerHeart has no dimension check: a bomb that goes to another dimension by itself, as through a portal,
    // lives on and hunts there, and isSHpresent counts it around its owner there. The bomb is sent from the Nether to
    // this test's pad by the same changeDimension a portal runs, so no Nether chunk has to be generated.
    @GameTest(template = "empty", batch = "kqParitySheerHeartOtherDimension", timeoutTicks = SHEER_HEART_BLAST_TIMEOUT)
    public static void sheerHeartLivesOnInTheDimensionItReaches(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-sha-portal", GameType.CREATIVE);
        ServerPlayer owner = kq.owner();
        StandPower power = kq.power();
        BlockPos pad = kq.pad();
        ServerLevel level = helper.getLevel();
        ServerLevel nether = level.getServer().getLevel(Level.NETHER);
        List<DamageSource> blasts = new ArrayList<>();
        List<Entity> spawned = new ArrayList<>();
        List<Consumer<LivingIncomingDamageEvent>> hits = new ArrayList<>();
        List<String> wrong = new ArrayList<>();
        Runnable done = () -> {
            hits.forEach(NeoForge.EVENT_BUS::unregister);
            cleanup(helper, owner, spawned.toArray(Entity[]::new));
        };
        try {
            helper.assertTrue(nether != null, "fixture: no Nether");
            StandEntity stand = summon(helper, kq);
            SheerHeartAttackEntity sent = KillerQueenLionAddon.SHEER_HEART_ENTITY.get().create(nether);
            helper.assertTrue(sent != null, "fixture: no Sheer Heart Attack entity");
            // Summoned in the Nether
            sent.bind(owner, stand, null);
            helper.assertTrue(!sent.isRemoved(), "fixture: the Sheer Heart bomb was rejected");
            Entity arrived = sent.changeDimension(new DimensionTransition(level,
                    new Vec3(pad.getX() + 3.5D, pad.getY(), pad.getZ() + 0.5D), Vec3.ZERO, -90.0F, 0.0F,
                    DimensionTransition.DO_NOTHING));
            helper.assertTrue(arrived instanceof SheerHeartAttackEntity bomb && bomb.level() == level
                            && sent.getUUID().equals(bomb.getUUID()) && bomb.isOwnedBy(owner.getUUID()),
                    "fixture: the Sheer Heart bomb did not reach this dimension");
            spawned.add(arrived);
            SheerHeartAttackEntity bomb = (SheerHeartAttackEntity) arrived;
            bomb.tick();
            if (bomb.isRemoved()) {
                wrong.add("it was removed in the dimension it reached");
            } else {
                KillerQueenState state = KillerQueenLionAddon.state(owner);
                Ability sheerHeart = power.getAbility(KillerQueenLionAddon.SHEER_HEART_NAME);
                if (!bomb.summonLink().equals(state.sheerHeartUuid())) {
                    wrong.add("it did not hold its owner's summon beside him");
                } else if (sheerHeart == null || sheerHeart.isAbilityAvailable(power)) {
                    wrong.add("Sheer Heart Attack was offered again with his bomb beside him");
                }
                Husk husk = toughHuskBeside(helper, bomb);
                spawned.add(husk);
                hits.add(recordBlastsOn(husk, blasts));
            }
        } catch (RuntimeException | Error failure) {
            done.run();
            throw failure;
        }
        checkOnceTheSheerHeartBlasted(helper, 80,
                () -> spawned.isEmpty() || spawned.getFirst().isRemoved() || !blasts.isEmpty(), () -> {
            try {
                if (spawned.isEmpty() || spawned.getFirst().isRemoved()) {
                    wrong.add("it did not stay in the dimension it reached");
                } else if (blasts.isEmpty()) {
                    wrong.add("it did not blast the monster beside it in the dimension it reached");
                }
            } finally {
                done.run();
            }
            helper.assertTrue(wrong.isEmpty(),
                    "1.16 keeps a Sheer Heart that went to another dimension: " + wrong);
            helper.succeed();
        });
    }

    // 1.16 SheerHeart without an owner, as /summon makes it, is a mob like any other: it stays, hunts, and its blast is
    // its own (explodeCreeper 298). ModEntityTypes 36 registers it summonable and 0.85 x 0.7.
    @GameTest(template = "empty", batch = "kqParitySheerHeartOwnerless", timeoutTicks = SHEER_HEART_BLAST_TIMEOUT)
    public static void anOwnerlessSheerHeartLivesAndHunts(GameTestHelper helper) {
        BlockPos pad = KillerQueenGameTests.sheerHeartPad(helper, true);
        ServerLevel level = helper.getLevel();
        List<SheerHeartAttackEntity> summoned = new ArrayList<>();
        Vec3 at = new Vec3(pad.getX() + 3.5D, pad.getY(), pad.getZ() + 0.5D);
        String refused = summonByCommand(level, KillerQueenLionAddon.SHEER_HEART_ENTITY.get(), at);
        summoned.addAll(level.getEntities(KillerQueenLionAddon.SHEER_HEART_ENTITY.get(),
                new AABB(at, at).inflate(0.5D), bomb -> !bomb.isRemoved()));
        Husk[] husk = {null};
        List<DamageSource> blasts = new ArrayList<>();
        List<Consumer<LivingIncomingDamageEvent>> hits = new ArrayList<>();
        List<String> wrong = new ArrayList<>();
        Runnable done = () -> {
            hits.forEach(NeoForge.EVENT_BUS::unregister);
            summoned.forEach(Entity::discard);
            if (husk[0] != null) {
                husk[0].discard();
            }
            KillerQueenGameTests.sheerHeartPad(helper, false);
        };
        try {
            helper.assertTrue(refused == null && summoned.size() == 1 && summoned.getFirst().getOwnerUuid() == null,
                    "/summon made no Sheer Heart bomb without an owner (the command said: " + refused + ")");
            SheerHeartAttackEntity bomb = summoned.getFirst();
            if (bomb.getBbWidth() != 0.85F || bomb.getBbHeight() != 0.7F) {
                wrong.add("its hitbox is " + bomb.getBbWidth() + " x " + bomb.getBbHeight() + ", not 1.16's 0.85 x 0.7");
            }
            husk[0] = toughHuskBeside(helper, bomb);
            hits.add(recordBlastsOn(husk[0], blasts));
            bomb.tick();
            if (bomb.isRemoved()) {
                wrong.add("it was removed for having no owner");
            }
        } catch (RuntimeException | Error failure) {
            done.run();
            throw failure;
        }
        checkOnceTheSheerHeartBlasted(helper, 80, () -> summoned.getFirst().isRemoved()
                || blasts.stream().anyMatch(source -> source.getEntity() == summoned.getFirst()), () -> {
            SheerHeartAttackEntity bomb = summoned.getFirst();
            try {
                if (bomb.isRemoved()) {
                    wrong.add("it did not stay without an owner");
                } else if (blasts.stream().noneMatch(source -> source.getEntity() == bomb)) {
                    wrong.add("no blast of its own hit the monster beside it (hits from "
                            + blasts.stream().map(DamageSource::getEntity).toList() + ")");
                }
            } finally {
                done.run();
            }
            helper.assertTrue(wrong.isEmpty(), "1.16 keeps a Sheer Heart without an owner: " + wrong);
            helper.succeed();
        });
    }

    // The lone bombs' blast checks above wait for a first target that comes late, by 1.16's random roll or, here,
    // because the monster only arrives at tick 90, past the checks' first tick (80).
    @GameTest(template = "empty", batch = "kqParitySheerHeartLateTarget", timeoutTicks = SHEER_HEART_BLAST_TIMEOUT)
    public static void sheerHeartBlastChecksAwaitALateFirstTarget(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-sha-late", GameType.CREATIVE);
        List<Entity> spawned = new ArrayList<>();
        List<DamageSource> blasts = new ArrayList<>();
        List<Consumer<LivingIncomingDamageEvent>> hits = new ArrayList<>();
        Runnable done = () -> {
            hits.forEach(NeoForge.EVENT_BUS::unregister);
            cleanup(helper, kq.owner(), spawned.toArray(Entity[]::new));
        };
        SheerHeartAttackEntity bomb;
        double walk;
        try {
            sheerHeartBeside(helper, kq, spawned);
            bomb = (SheerHeartAttackEntity) spawned.getFirst();
            walk = bomb.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
            // It stays where the monster will come
            bomb.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0D);
        } catch (RuntimeException | Error failure) {
            done.run();
            throw failure;
        }
        helper.runAtTickTime(90, () -> {
            try {
                bomb.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(walk);
                Husk husk = toughHuskBeside(helper, bomb);
                spawned.add(husk);
                hits.add(recordBlastsOn(husk, blasts));
            } catch (RuntimeException | Error failure) {
                done.run();
                throw failure;
            }
        });
        checkOnceTheSheerHeartBlasted(helper, 80, () -> bomb.isRemoved() || !blasts.isEmpty(), () -> {
            long checkedAt = helper.getTick();
            boolean removed = bomb.isRemoved();
            done.run();
            helper.assertTrue(!removed && !blasts.isEmpty() && checkedAt > 90,
                    "A Sheer Heart blast check did not wait for a first target that came at tick 90: checked at tick "
                            + checkedAt + ", the bomb removed " + removed + ", " + blasts.size() + " blasts");
            helper.succeed();
        });
    }

    // 1.16 SHABack removes every SheerHeart whose owner is the user inside the 140-block box of isSHpresent, of any
    // summon, and leaves the others, a bomb of his own farther away included; isSHpresent then finds none and Sheer
    // Heart Attack can be sent again.
    @GameTest(template = "empty", batch = "kqParitySheerHeartRecallBox", timeoutTicks = 20)
    public static void recallTakesEveryBombOfItsOwnerWithin140Blocks(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-sha-recall", GameType.CREATIVE);
        ServerPlayer owner = kq.owner();
        StandPower power = kq.power();
        ServerLevel level = helper.getLevel();
        KillerQueenState state = KillerQueenLionAddon.state(owner);
        List<Entity> spawned = new ArrayList<>();
        List<String> wrong = new ArrayList<>();
        try {
            StandEntity stand = summon(helper, kq);
            aimStand(stand, ActionTarget.EMPTY);
            performNow(helper, owner, power, stand, KillerQueenLionAddon.SHEER_HEART_NAME);
            List<SheerHeartAttackEntity> linked = ownedSheerHearts(owner);
            spawned.addAll(linked);
            helper.assertTrue(linked.size() == 7 && state.hasSheerHeart(),
                    "fixture: Sheer Heart Attack did not send its seven bombs: " + linked.size());
            UUID link = state.sheerHeartUuid();
            SheerHeartAttackEntity earlier = ownersBomb(helper, owner, stand, null,
                    owner.position().add(3.0D, 0.0D, 4.0D));
            // Outside the 140-block box, in this test's own loaded chunk
            SheerHeartAttackEntity far = ownersBomb(helper, owner, stand, link,
                    owner.position().add(0.0D, SheerHeartAttackEntity.MAX_OWNER_DISTANCE + 10.0D, 0.0D));
            SheerHeartAttackEntity ownerless = KillerQueenLionAddon.SHEER_HEART_ENTITY.get().create(level);
            helper.assertTrue(ownerless != null, "fixture: no Sheer Heart Attack entity");
            ownerless.moveTo(owner.getX() - 3.0D, owner.getY(), owner.getZ(), 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(ownerless), "fixture: the ownerless bomb was rejected");
            spawned.addAll(List.of(earlier, far, ownerless));
            helper.assertTrue(level.getEntities(KillerQueenLionAddon.SHEER_HEART_ENTITY.get(), far.getBoundingBox(),
                            bomb -> bomb == far).size() == 1,
                    "fixture: the far bomb cannot be found in the level");
            performNow(helper, owner, power, stand, KillerQueenLionAddon.SHEER_HEART_RECALL_NAME);
            long left = linked.stream().filter(bomb -> !bomb.isRemoved()).count();
            if (left > 0) {
                wrong.add("the recall left " + left + " bombs of the summon he holds");
            }
            if (!earlier.isRemoved()) {
                wrong.add("the recall left a bomb of an earlier summon of his 5 blocks away");
            }
            if (far.isRemoved()) {
                wrong.add("the recall took a bomb of his summon 150 blocks above him");
            }
            if (ownerless.isRemoved()) {
                wrong.add("the recall took a bomb that is not his");
            }
            Ability sheerHeart = power.getAbility(KillerQueenLionAddon.SHEER_HEART_NAME);
            if (state.hasSheerHeart() || sheerHeart == null || !sheerHeart.isAbilityAvailable(power)) {
                wrong.add("Sheer Heart Attack was not offered again after the recall");
            }
        } finally {
            cleanup(helper, owner, spawned.toArray(Entity[]::new));
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 SHABack removes every bomb of its user within 140 blocks: " + wrong);
        helper.succeed();
    }

    // The real /summon with the server console's permission; the command's failure text, or null when it ran
    private static String summonByCommand(ServerLevel level, EntityType<?> type, Vec3 at) {
        CommandSourceStack source = level.getServer().createCommandSourceStack().withLevel(level).withPosition(at);
        String command = String.format(Locale.ROOT, "summon %s %.2f %.2f %.2f",
                BuiltInRegistries.ENTITY_TYPE.getKey(type), at.x, at.y, at.z);
        try {
            level.getServer().getCommands().getDispatcher().execute(command, source);
            return null;
        } catch (CommandSyntaxException failure) {
            return failure.getMessage();
        }
    }

    // A bomb of the owner's that may belong to another summon than the one he holds
    private static SheerHeartAttackEntity ownersBomb(
            GameTestHelper helper, ServerPlayer owner, StandEntity stand, @Nullable UUID summon, Vec3 at) {
        SheerHeartAttackEntity bomb = KillerQueenLionAddon.SHEER_HEART_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(bomb != null, "fixture: no Sheer Heart Attack entity");
        bomb.bind(owner, stand, null);
        if (summon != null) {
            bomb.joinSummon(summon);
        }
        bomb.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        helper.assertTrue(!bomb.isRemoved() && helper.getLevel().addFreshEntity(bomb),
                "fixture: a Sheer Heart bomb was rejected");
        return bomb;
    }

    // 1.16 SheerHeart takes its first target through NearestAttackableTargetGoal, a 1-in-10 roll each tick (1-in-5 on
    // each second tick here) with no upper bound on the wait, and swells 15 ticks more before it blasts. A check on a
    // lone bomb's blast runs on the first tick from fromTick on at which settled holds, else at the deadline, so a late
    // first target is waited for instead of failing the test by chance.
    static final int SHEER_HEART_BLAST_DEADLINE = 600;
    static final int SHEER_HEART_BLAST_TIMEOUT = SHEER_HEART_BLAST_DEADLINE + 20;

    static void checkOnceTheSheerHeartBlasted(
            GameTestHelper helper, long fromTick, BooleanSupplier settled, Runnable check) {
        boolean[] checked = {false};
        for (long tick = fromTick; tick <= SHEER_HEART_BLAST_DEADLINE; tick++) {
            boolean last = tick == SHEER_HEART_BLAST_DEADLINE;
            helper.runAtTickTime(tick, () -> {
                if (!checked[0] && (last || settled.getAsBoolean())) {
                    checked[0] = true;
                    check.run();
                }
            });
        }
    }

    // A monster 2.5 blocks from the bomb that the blasts do not kill, so the bombs stay (1.16 explodeCreeper 304-306)
    private static Husk toughHuskBeside(GameTestHelper helper, SheerHeartAttackEntity bomb) {
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, 1, 2, 1);
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0D);
        husk.setHealth(1000.0F);
        husk.setPos(bomb.getX() + 2.5D, bomb.getY(), bomb.getZ());
        return husk;
    }

    private static Consumer<LivingIncomingDamageEvent> recordBlastsOn(LivingEntity victim, List<DamageSource> hits) {
        Consumer<LivingIncomingDamageEvent> listener = event -> {
            if (event.getEntity() == victim && event.getSource().is(DamageTypeTags.IS_EXPLOSION)) {
                hits.add(event.getSource());
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingIncomingDamageEvent.class, listener);
        return listener;
    }

    // 1.16 isSHpresent searched only 140 blocks around the user: bombs left farther away, which here have not
    // ticked since the owner moved (as in chunks that stopped ticking), do not block a new summon.
    @GameTest(template = "empty", batch = "kqParitySheerHeartUnticked", timeoutTicks = 20)
    public static void sheerHeartIsAvailableAgainWithItsBombsLeftFarAway(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-sha-unticked", GameType.CREATIVE);
        ServerPlayer owner = kq.owner();
        StandPower power = kq.power();
        KillerQueenState state = KillerQueenLionAddon.state(owner);
        StandEntity stand = summon(helper, kq);
        boolean availableNear;
        boolean heldNear;
        boolean availableFar;
        boolean heldFar;
        long loadedFar;
        List<SheerHeartAttackEntity> bombs = new ArrayList<>();
        try {
            Ability sheerHeart = power.getAbility(KillerQueenLionAddon.SHEER_HEART_NAME);
            helper.assertTrue(sheerHeart != null && sheerHeart.isAbilityAvailable(power),
                    "fixture: Sheer Heart Attack is not available before its first summon");
            aimStand(stand, ActionTarget.EMPTY);
            performNow(helper, owner, power, stand, KillerQueenLionAddon.SHEER_HEART_NAME);
            bombs.addAll(ownedSheerHearts(owner));
            helper.assertTrue(bombs.size() == 7 && state.hasSheerHeart(),
                    "fixture: Sheer Heart Attack did not send its seven bombs: " + bombs.size());
            availableNear = sheerHeart.isAbilityAvailable(power);
            heldNear = state.hasSheerHeart();
            owner.moveTo(owner.getX() + SheerHeartAttackEntity.MAX_OWNER_DISTANCE + 20.0D, owner.getY(),
                    owner.getZ(), owner.getYRot(), owner.getXRot());
            availableFar = sheerHeart.isAbilityAvailable(power);
            heldFar = state.hasSheerHeart();
            loadedFar = bombs.stream().filter(bomb -> !bomb.isRemoved()).count();
        } finally {
            cleanup(helper, owner, bombs.toArray(Entity[]::new));
        }
        helper.assertTrue(!availableNear && heldNear,
                "Sheer Heart Attack was available again with its seven bombs beside the owner");
        helper.assertTrue(loadedFar == 7, "fixture: " + loadedFar + " of the seven bombs were still loaded");
        helper.assertTrue(availableFar && !heldFar,
                "Sheer Heart Attack stayed unavailable with its only bombs 160 blocks away, which 1.16 did not count");
        helper.succeed();
    }

    // 1.16 ran isSHpresent on the client too, so the ability came back there as soon as the user was 140 blocks from
    // the bombs. The port's client reads the synced state and offers no press while it holds the summon, so the
    // owner's own tick, with no press at all, has to release it and send the sync.
    @GameTest(template = "empty", batch = "kqParitySheerHeartClientSync", timeoutTicks = 20)
    public static void sheerHeartComesBackOnTheClientWhenTheOwnerWalksAway(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-sha-client", GameType.CREATIVE);
        ServerPlayer owner = kq.owner();
        StandPower power = kq.power();
        KillerQueenState state = KillerQueenLionAddon.state(owner);
        StandEntity stand = summon(helper, kq);
        KillerQueenState client = new KillerQueenState();
        boolean hiddenNear;
        boolean offeredFar;
        boolean heldFar;
        long loadedFar;
        List<SheerHeartAttackEntity> bombs = new ArrayList<>();
        try {
            helper.assertTrue(owner.connection.getConnection().channel() instanceof EmbeddedChannel,
                    "fixture: no recording connection");
            EmbeddedChannel sent = (EmbeddedChannel) owner.connection.getConnection().channel();
            Ability sheerHeart = power.getAbility(KillerQueenLionAddon.SHEER_HEART_NAME);
            helper.assertTrue(sheerHeart != null && sheerHeart.isAbilityAvailable(power),
                    "fixture: Sheer Heart Attack is not available before its first summon");
            aimStand(stand, ActionTarget.EMPTY);
            performNow(helper, owner, power, stand, KillerQueenLionAddon.SHEER_HEART_NAME);
            bombs.addAll(ownedSheerHearts(owner));
            helper.assertTrue(bombs.size() == 7 && state.hasSheerHeart(),
                    "fixture: Sheer Heart Attack did not send its seven bombs: " + bombs.size());
            owner.doTick();
            applySentSyncs(sent, owner, client);
            hiddenNear = client.hasSheerHeart();
            owner.moveTo(owner.getX() + SheerHeartAttackEntity.MAX_OWNER_DISTANCE + 20.0D, owner.getY(),
                    owner.getZ(), owner.getYRot(), owner.getXRot());
            owner.doTick();
            applySentSyncs(sent, owner, client);
            offeredFar = !client.hasSheerHeart();
            heldFar = state.hasSheerHeart();
            loadedFar = bombs.stream().filter(bomb -> !bomb.isRemoved()).count();
        } finally {
            cleanup(helper, owner, bombs.toArray(Entity[]::new));
        }
        helper.assertTrue(hiddenNear,
                "the client was not told of the summon, or the owner's tick released it with the bombs beside him");
        helper.assertTrue(loadedFar == 7, "fixture: " + loadedFar + " of the seven bombs were still loaded");
        helper.assertTrue(offeredFar && !heldFar,
                "Sheer Heart Attack stayed hidden on the client after a tick 160 blocks from its only bombs (server "
                        + "still holds it: " + heldFar + "); 1.16 offered it again there");
        helper.succeed();
    }

    // 1.16 PunchBomb.replaceAction: while the bombed entity is within 16 blocks of its owner the kq_entitybomb slot
    // shows kq_entity_explo instead (Action.getVisibleAction), never both; farther away the plant comes back.
    @GameTest(template = "empty", batch = "kqParityPrimaryBombSlot", timeoutTicks = 20)
    public static void primaryBombSlotOffersTheDetonationInPlaceOfThePlant(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-bomb-slot", GameType.CREATIVE);
        ServerPlayer owner = kq.owner();
        StandPower power = kq.power();
        Cow cow = spawnCow(helper, owner, 0.0D, 3.0D);
        List<String> wrong = new ArrayList<>();
        try {
            String plant = KillerQueenLionAddon.PRIMARY_BOMB_NAME;
            String detonate = KillerQueenLionAddon.PRIMARY_BOMB_DETONATE_NAME;
            int sharedSlots = 0;
            int detonateSlots = 0;
            rotp.core.powersystem.ability.controls.ControlSchemeTemplate template =
                    power.getPowerType().makeDefaultControlSchemeTemplate();
            for (var group : template.groups.values()) {
                for (var hotbar : group.hotbars) {
                    for (var slot : hotbar.slots) {
                        if (slot.values().stream().anyMatch(binds -> binds.containsValue(detonate))) {
                            detonateSlots++;
                        }
                        java.util.Map<InputMethod, String> base = slot.get(null);
                        if (base != null && plant.equals(base.get(InputMethod.CLICK))
                                && detonate.equals(base.get(InputMethod.HOLD))) {
                            sharedSlots++;
                        }
                    }
                }
            }
            if (sharedSlots != 1 || detonateSlots != 1) {
                wrong.add("kq_entity_explo is not the hold of the kq_entitybomb slot alone: " + sharedSlots
                        + " shared slot(s), " + detonateSlots + " slot(s) holding it");
            }
            primaryBombOffer(power, true, false, "before a bomb", wrong);
            helper.assertTrue(KillerQueenStateService.markPrimaryBomb(owner, cow),
                    "fixture: the primary bomb was rejected");
            primaryBombOffer(power, false, true, "with the bombed cow 3 blocks away", wrong);
            // 1.16 MCUtil.entitiesAround: the cube of 16 around the centre of the owner's box, not a sphere
            cow.setPos(owner.getX() + 12.0D, owner.getY(), owner.getZ() + 12.0D);
            primaryBombOffer(power, false, true,
                    "with the bombed cow 12 blocks east and 12 south (17 away, inside the 1.16 cube)", wrong);
            rotp.core.powersystem.ability.condition.ConditionCheck press =
                    power.getAbility(detonate).checkSpecificConditions(power);
            if (!press.isPositive() && rotp.core.powersystem.ability.condition.ConditionCheck
                    .message("killer_queen_no_primary_bomb").equals(press.getWarning())) {
                wrong.add("with the bombed cow 12 blocks east and 12 south: kq_entity_explo is refused for want of "
                        + "a bomb in range");
            }
            cow.setPos(owner.getX() + 16.5D, owner.getY(), owner.getZ());
            primaryBombOffer(power, true, false, "with the bombed cow 16.5 blocks east, its box outside the cube",
                    wrong);
            cow.setPos(owner.getX(), owner.getY(), owner.getZ() + 20.0D);
            primaryBombOffer(power, true, false, "with the bombed cow 20 blocks away", wrong);
        } finally {
            cleanup(helper, owner, cow);
        }
        helper.assertTrue(wrong.isEmpty(), "The primary bomb slot does not offer what 1.16 shows: " + wrong);
        helper.succeed();
    }

    // 1.16 PunchBomb.entityInRange (MCUtil.entitiesAround, no alive filter) still found a killed bombed mob while its
    // body lay in the level, up to 20 ticks: the slot kept kq_entity_explo and EntityExplode.standPerform exploded
    // at the body. Once the body is gone the plant comes back.
    @GameTest(template = "empty", batch = "kqParityPrimaryBombDeadBody", timeoutTicks = 60)
    public static void aKilledBombedMobStaysTheBombWhileItsBodyLies(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-bomb-dead-body", GameType.SURVIVAL);
        ServerPlayer owner = kq.owner();
        StandPower power = kq.power();
        StandEntity stand = staminaFixture(helper, kq);
        power.skipProgression();
        KillerQueenState state = KillerQueenLionAddon.state(owner);
        String detonate = KillerQueenLionAddon.PRIMARY_BOMB_DETONATE_NAME;
        List<String> wrong = new ArrayList<>();
        Cow detonated = spawnCow(helper, owner, 0.0D, 3.0D);
        // out of the first blast's reach
        Cow left = spawnCow(helper, owner, 12.0D, 0.0D);
        try {
            helper.assertTrue(KillerQueenStateService.markPrimaryBomb(owner, detonated),
                    "fixture: the primary bomb was rejected");
            detonated.kill();
            helper.assertTrue(detonated.isDeadOrDying() && !detonated.isRemoved(),
                    "fixture: the bombed cow did not die or its body is already gone");
            if (!state.hasPrimaryBomb() || KillerQueenStateService.primaryBombTarget(owner) != detonated) {
                wrong.add("the bombed cow was killed and its body, still in the level, is no longer the bomb");
            }
            primaryBombOffer(power, false, true, "with the bombed cow's body lying 3 blocks away", wrong);
            runAtFloor(helper, owner, power, stand, detonate, 250.0F, 0.0F, () -> !state.hasPrimaryBomb(),
                    "did not explode at the bombed cow's body", wrong);

            helper.assertTrue(KillerQueenStateService.markPrimaryBomb(owner, left),
                    "fixture: the second primary bomb was rejected");
            left.kill();
            helper.assertTrue(left.isDeadOrDying(), "fixture: the second bombed cow did not die");
        } catch (RuntimeException failure) {
            cleanup(helper, owner, detonated, left);
            throw failure;
        }
        // LivingEntity.tickDeath removes the body after 20 ticks
        helper.runAfterDelay(25, () -> {
            try {
                helper.assertTrue(left.isRemoved(), "fixture: the second cow's body was not removed");
                if (state.hasPrimaryBomb()) {
                    wrong.add("the bombed cow's body left the level and the bomb stayed on its owner");
                }
                primaryBombOffer(power, true, false, "after the bombed cow's body left the level", wrong);
            } finally {
                cleanup(helper, owner, detonated, left);
            }
            helper.assertTrue(wrong.isEmpty(), "1.16 kept a killed bombed mob as the bomb while its body lay: "
                    + wrong);
            helper.succeed();
        });
    }

    private static void primaryBombOffer(StandPower power, boolean plantShown, boolean detonateShown, String when,
            List<String> wrong) {
        boolean plant = resolve(power, KillerQueenLionAddon.PRIMARY_BOMB_NAME) != null;
        boolean detonate = resolve(power, KillerQueenLionAddon.PRIMARY_BOMB_DETONATE_NAME) != null;
        if (plant != plantShown || detonate != detonateShown) {
            wrong.add(when + ": kq_entitybomb " + (plant ? "shown" : "hidden") + ", kq_entity_explo "
                    + (detonate ? "shown" : "hidden"));
        }
    }

    // 1.16 SheerHeart is a mob saved with its own Owner UUID, and no handler removes it when its owner logs out or
    // changes dimension; isSHpresent and SHABack find it again around the owner when he is back.
    @GameTest(template = "empty", batch = "kqParitySheerHeartAbsentOwner", timeoutTicks = 40)
    public static void sheerHeartOutlivesItsOwnersLogoutAndDimensionChange(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-sha-absent", GameType.CREATIVE);
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        UUID ownerUuid = kq.owner().getUUID();
        ServerPlayer[] owner = {kq.owner()};
        List<Entity> spawned = new ArrayList<>();
        List<String> lost = new ArrayList<>();
        try {
            ServerLevel nether = server.getLevel(Level.NETHER);
            helper.assertTrue(nether != null, "fixture: no Nether");
            sheerHeartBeside(helper, kq, spawned);
            SheerHeartAttackEntity bomb = (SheerHeartAttackEntity) spawned.get(0);
            UUID link = bomb.summonLink();

            owner[0].connection.disconnect(Component.literal("Killer Queen relog"));
            helper.assertTrue(server.getPlayerList().getPlayer(ownerUuid) == null,
                    "fixture: the owner did not log out");
            bomb.tick();
            if (bomb.isRemoved()) {
                lost.add("its owner logged out");
            }
            owner[0] = kq.cleanup().join(mockPlayer(helper, ownerUuid, "kq-sha-absent", kq.pad()));
            owner[0].setGameMode(GameType.CREATIVE);
            KillerQueenState state = KillerQueenLionAddon.state(owner[0]);
            owner[0].doTick();
            bomb.tick();
            if (bomb.isRemoved() || !link.equals(state.sheerHeartUuid())) {
                lost.add("its owner logged back in");
            }

            Vec3 home = owner[0].position();
            move(helper, owner[0], nether, new Vec3(1.5D, 80.0D, 1.5D));
            owner[0].doTick();
            bomb.tick();
            if (bomb.isRemoved()) {
                lost.add("its owner went to the Nether");
            }
            StandPower netherPower = StandPower.get(owner[0]);
            Ability summon = netherPower.getAbility(KillerQueenLionAddon.SHEER_HEART_NAME);
            if (summon == null || !summon.isAbilityAvailable(netherPower)) {
                lost.add("1.16 isSHpresent finds no bomb around its owner in the Nether, so he may summon one there");
            }
            move(helper, owner[0], level, home);
            owner[0].doTick();
            bomb.tick();
            if (bomb.isRemoved() || !link.equals(state.sheerHeartUuid())) {
                lost.add("its owner came back from the Nether");
            }
            StandPower homePower = StandPower.get(owner[0]);
            Ability recall = homePower.getAbility(KillerQueenLionAddon.SHEER_HEART_RECALL_NAME);
            if (recall == null || !recall.isAbilityAvailable(homePower)) {
                lost.add("its owner cannot recall it after his return");
            }
        } finally {
            if (server.getPlayerList().getPlayer(ownerUuid) == owner[0]) {
                cleanup(helper, owner[0], spawned.toArray(Entity[]::new));
            } else {
                spawned.forEach(Entity::discard);
                KillerQueenGameTests.sheerHeartPad(helper, false);
            }
        }
        helper.assertTrue(lost.isEmpty(), "The Sheer Heart Attack was dropped where 1.16 keeps it: " + lost);
        helper.succeed();
    }

    // 1.16 SheerHeart has no owner check in its AI: with its owner away it takes players other than him and monsters
    // as targets on its own, and explodeCreeper sources the blast to itself when getOwner() finds no owner.
    @GameTest(template = "empty", batch = "kqParitySheerHeartHunt", timeoutTicks = SHEER_HEART_BLAST_TIMEOUT)
    public static void sheerHeartHuntsOnItsOwnWhileItsOwnerIsAway(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-sha-hunt", GameType.CREATIVE);
        List<Entity> spawned = new ArrayList<>();
        ServerPlayer[] stranger = {null};
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, 1, 2, 1);
        spawned.add(husk);
        List<String> wrong = new ArrayList<>();
        // A dying mob forgets an attacker that is gone, so the hit is taken as it lands
        List<Entity> hitBy = new ArrayList<>();
        Consumer<LivingIncomingDamageEvent> hits = event -> {
            if (event.getEntity() == husk && event.getSource().is(DamageTypeTags.IS_EXPLOSION)) {
                hitBy.add(event.getSource().getEntity());
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingIncomingDamageEvent.class, hits);
        SheerHeartAttackEntity bomb;
        try {
            sheerHeartBeside(helper, kq, spawned);
            bomb = (SheerHeartAttackEntity) spawned.get(1);
            husk.setPos(bomb.getX() + 2.5D, bomb.getY(), bomb.getZ());
            kq.owner().connection.disconnect(Component.literal("Killer Queen owner away"));
            stranger[0] = kq.cleanup().join(mockPlayer(helper, UUID.randomUUID(), "kq-sha-stranger", kq.pad()));
            stranger[0].setGameMode(GameType.SURVIVAL);
            // Out of the bomb's 16-block follow range: only the monster is in reach
            stranger[0].setPos(kq.pad().getX() + 40.5D, kq.pad().getY(), kq.pad().getZ() + 0.5D);
            if (!bomb.canAttack(husk)) {
                wrong.add("it does not take a monster as its target");
            }
            if (!bomb.canAttack(stranger[0])) {
                wrong.add("it does not take another player as its target");
            }
        } catch (RuntimeException | Error failure) {
            NeoForge.EVENT_BUS.unregister(hits);
            huntCleanup(helper, stranger[0], spawned);
            throw failure;
        }
        checkOnceTheSheerHeartBlasted(helper, 100, () -> bomb.isRemoved() || hitBy.contains(bomb), () -> {
            NeoForge.EVENT_BUS.unregister(hits);
            huntCleanup(helper, stranger[0], spawned);
            if (!hitBy.contains(bomb)) {
                wrong.add("no blast of its own hit the monster beside it (hits from " + hitBy + ")");
            }
            helper.assertTrue(wrong.isEmpty(),
                    "With its owner away the Sheer Heart Attack does not hunt as in 1.16: " + wrong);
            helper.succeed();
        });
    }

    // 1.16 BlockBombAction kept the block it was pressed at (standKeepsTarget) and, with the default
    // lockOnTargetPosition, StandEntity.taskOffset turned the default offset withYOffset(-0.75, 0.2, -0.75) by the yaw
    // from the user's eyes to the block's centre while it ran, wherever the user looked.
    @GameTest(template = "empty", batch = "kqParityBlockBombOffset", timeoutTicks = 20)
    public static void blockBombTurnsTheStandToItsBlockAsTheDonorDid(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-block-bomb-offset", GameType.SURVIVAL);
        ServerPlayer owner = kq.owner();
        ServerLevel level = helper.getLevel();
        StandEntity stand = staminaFixture(helper, kq);
        BlockPos block = kq.pad().offset(3, 1, 0);
        try {
            owner.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standSide = HumanoidArm.RIGHT;
            level.setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
            // the user looks east at the block, 3 blocks away
            turnOwner(owner, -90.0F, -90.0F);
            aimStand(stand, new ActionTarget(block, Direction.WEST));
            Ability ability = kq.power().getAbility(KillerQueenLionAddon.BLOCK_BOMB_NAME);
            helper.assertTrue(ability instanceof EntityActionAbility, "fixture: the block bomb is not an action");
            EntityActionInstance action = ((EntityActionAbility) ability)
                    .initActionOnAbilityUse(level, owner, stand, null);
            LivingComponentAction.getComponent(stand).setAction(action, owner, SyncType.NO_SYNC);
            helper.assertTrue(stand.getCurStandAction() == action && action.getPhase() == ActionPhase.WINDUP
                            && stand.isFollowingUser(),
                    "fixture: the block bomb is not the following Stand's action in its windup");
            ActionTarget held = action.getTaskTarget(level);
            helper.assertTrue(held.getType() == ActionTarget.TargetType.BLOCK && block.equals(held.getBlockPos()),
                    "1.16 BlockBombAction.standKeepsTarget kept the block it was pressed at; the task holds " + held);

            // the user looks south with its body turned away
            turnOwner(owner, 0.0F, 50.0F);
            Vec3 toBlock = Vec3.atCenterOf(block).subtract(owner.getEyePosition());
            float yaw = (float) -Mth.atan2(toBlock.x, toBlock.z) * Mth.RAD_TO_DEG;
            Vec3 expected = new Vec3(-0.75D, 0.2D, -0.75D).yRot(-yaw * Mth.DEG_TO_RAD);
            stand.updatePosition(owner);
            Vec3 actual = stand.position().subtract(owner.position());
            helper.assertTrue(actual.distanceTo(expected) < 1.0E-4D,
                    "1.16 put Killer Queen planting a block bomb 3 blocks east of its user, who looks south, at its"
                            + " user's feet + " + expected + " (the default offset turned to the block), got " + actual
                            + " (offset " + stand.offsetFromUser.getRelativeOffset() + " "
                            + stand.offsetFromUser.getRotations() + ")");
        } finally {
            level.setBlockAndUpdate(block, Blocks.AIR.defaultBlockState());
            cleanup(helper, owner);
        }
        helper.succeed();
    }

    // 1.16 ItemFBomb and PunchBomb (kq_itembomb, kq_entitybomb) are StandEntityLightAttacks: pressed with no Stand
    // out they brought out only its main arm (the builder's MAIN_ARM). With the whole Stand out it stood at
    // front3dOffset, withXRot(0, front) along the user's view (lockOnTargetPosition false), front the horizontal
    // distance from the user's feet to the task's target less 1 and half its width, within 0.5 to 2. The task kept
    // any target (standKeepsTarget), and one pressed at nothing measured its front to the target it took over.
    @GameTest(template = "empty", batch = "kqParityLightBombOffset", timeoutTicks = 20)
    public static void itemAndEntityBombsComeOutAndStandAsTheDonorDid(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-light-bomb-offset", GameType.SURVIVAL);
        ServerPlayer owner = kq.owner();
        StandPower power = kq.power();
        ServerLevel level = helper.getLevel();
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 1);
        try {
            owner.setInvulnerable(true);
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            owner.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standSide = HumanoidArm.RIGHT;
            owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND));
            // the entity bomb is refused unless pressed at an entity, so the user aims at the cow
            cow.setPos(owner.getX() + 2.5D, owner.getY(), owner.getZ());
            turnOwner(owner, -90.0F, -90.0F);
            LivingComponentAction.getComponent(owner).entityAim.setTarget(new ActionTarget(cow));
            short key = 93;
            for (String name : List.of(KillerQueenLionAddon.ITEM_BOMB_NAME, KillerQueenLionAddon.PRIMARY_BOMB_NAME)) {
                Ability ability = power.getAbility(name);
                // a fresh resolution: the power caches its moves within a tick
                rotp.core.powersystem.ability.condition.AvailableAbilities available =
                        new rotp.core.powersystem.ability.condition.AvailableAbilities();
                available.update(power, power.getMoveset());
                AbilityConditionCheck input = available.getContextVariationContainer(ability);
                helper.assertTrue(power.getSummonedStandEntity() == null && input != null && input.ability == ability
                                && AbilityInput.withConditionCheck(input, owner, InputMethod.CLICK),
                        "fixture: " + name + " cannot be pressed with no Stand out");
                AbilityInput.keyPress(key, ability, owner, new FriendlyByteBuf(Unpooled.buffer()), InputMethod.CLICK,
                        0.0F, BufferingState.clickCanBuffer(), ability.abilityId);
                AbilityInput.keyRelease(key++, owner);
                StandEntity arm = power.getSummonedStandEntity();
                helper.assertTrue(arm != null && arm.isArmsOnlyMode() && arm.showArm(InteractionHand.MAIN_HAND)
                                && !arm.showArm(InteractionHand.OFF_HAND),
                        "1.16 brought out only Killer Queen's main arm for " + name + " pressed with no Stand out; got "
                                + (arm == null ? "no Stand" : "arms only " + arm.isArmsOnlyMode() + ", main arm "
                                        + arm.showArm(InteractionHand.MAIN_HAND) + ", off arm "
                                        + arm.showArm(InteractionHand.OFF_HAND)));
                power.getPowerType().forceUnsummon(owner, power);
            }

            StandEntity stand = summon(helper, kq);
            cow.setPos(owner.getX() + 2.5D, owner.getY(), owner.getZ());
            double front = Mth.clamp(cow.position().subtract(owner.position()).horizontalDistance()
                            - (1.0D + cow.getBbWidth() / 2.0D),
                    Math.min(0.5D, stand.getEffectiveRange()), Math.min(2.0D, stand.getMaxRange()));
            helper.assertTrue(front > 0.5D && front < 2.0D, "fixture: the cow does not set the front offset: " + front);

            // the user looks east at the cow and plants an item bomb, then looks south with its body turned away
            turnOwner(owner, -90.0F, -90.0F);
            aimStand(stand, new ActionTarget(cow));
            EntityActionInstance item = startBomb(helper, owner, power, stand, KillerQueenLionAddon.ITEM_BOMB_NAME);
            helper.assertTrue(item.getTaskTarget(level).getEntity() == cow,
                    "1.16 StandEntityLightAttack.standKeepsTarget kept the cow the item bomb was pressed at; the task "
                            + "holds " + item.getTaskTarget(level));
            turnOwner(owner, 0.0F, 50.0F);
            assertFrontAlongView(helper, owner, stand, front, "an item bomb pressed at a cow 2.5 blocks east");

            // the item bomb pressed at nothing, the user still looking south, takes the cow over and measures its
            // front to it (the entity bomb cannot be pressed at nothing: its target requirement is ENTITY)
            aimStand(stand, ActionTarget.EMPTY);
            EntityActionInstance next = startBomb(helper, owner, power, stand, KillerQueenLionAddon.ITEM_BOMB_NAME);
            helper.assertTrue(next.getTaskTarget(level).getEntity() == cow,
                    "1.16 StandEntity.setTask gave the item bomb pressed at nothing the cow of the action it "
                            + "replaced; it holds " + next.getTaskTarget(level));
            assertFrontAlongView(helper, owner, stand, front, "an item bomb pressed at nothing after an action at the cow");

            // the entity bomb is pressed at an entity only; with the user's body turned away it stands the same
            // front along the user's view, measured to the cow it was pressed at
            LivingComponentAction.getComponent(stand).setAction(null, owner, SyncType.NO_SYNC);
            turnOwner(owner, -90.0F, -90.0F);
            aimStand(stand, new ActionTarget(cow));
            EntityActionInstance entity = startBomb(helper, owner, power, stand, KillerQueenLionAddon.PRIMARY_BOMB_NAME);
            helper.assertTrue(entity.getTaskTarget(level).getEntity() == cow,
                    "1.16 StandEntityLightAttack.standKeepsTarget kept the cow the entity bomb was pressed at; the "
                            + "task holds " + entity.getTaskTarget(level));
            turnOwner(owner, 0.0F, 50.0F);
            assertFrontAlongView(helper, owner, stand, front, "an entity bomb pressed at a cow 2.5 blocks east");
        } finally {
            cleanup(helper, owner, cow);
        }
        helper.succeed();
    }

    // 1.16 PunchBomb.standPerform (StandEntityLightAttack): StandEntity.punch at the task's target while the Stand
    // reached it (aimWithThisOrUser), whatever its user looked at; otherwise it aimed from the user's view again.
    // What it struck became the task's target (setTaskTarget).
    @GameTest(template = "empty", batch = "kqParityEntityBombStrike", timeoutTicks = 20)
    public static void entityBombStrikesItsTaskTargetAsTheDonorDid(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-entity-bomb-strike", GameType.SURVIVAL);
        ServerPlayer owner = kq.owner();
        StandPower power = kq.power();
        ServerLevel level = helper.getLevel();
        StandEntity stand = staminaFixture(helper, kq);
        Cow kept = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 1);
        Cow looked = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 1);
        try {
            owner.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standSide = HumanoidArm.RIGHT;
            kept.setPos(owner.getX() + 2.0D, owner.getY(), owner.getZ());
            looked.setPos(owner.getX(), owner.getY(), owner.getZ() - 6.0D);
            // an entity bomb at the cow east of the user; the user then looks south and a second cow comes into the
            // user's view, both in the Stand's reach
            turnOwner(owner, -90.0F, -90.0F);
            aimStand(stand, new ActionTarget(kept));
            EntityActionInstance bomb = startBomb(helper, owner, power, stand, KillerQueenLionAddon.PRIMARY_BOMB_NAME);
            helper.assertTrue(bomb.getTaskTarget(level).getEntity() == kept,
                    "fixture: the entity bomb pressed at the cow did not keep it: " + bomb.getTaskTarget(level));
            turnOwner(owner, 0.0F, 0.0F);
            looked.setPos(owner.getX(), owner.getY(), owner.getZ() + 2.0D);
            aimStand(stand, new ActionTarget(looked));
            LivingComponentAction.getComponent(owner).entityAim.setTarget(new ActionTarget(looked));
            stand.updatePosition(owner);
            stand.setYRot(0.0F);
            Vec3 eyesBefore = stand.getEyePosition();
            performNow(bomb);
            // 1.16 StandEntity.punch -> aimWithThisOrUser turned the Stand toward the cow it struck in the hit's own
            // tick, by at most attackSpeed / 16 x 18 degrees (MCUtil.rotateTowards), not a tick later
            float turnLimit = (float) stand.getAttackSpeed() / 16.0F * 18.0F;
            float wantedTurn = Mth.clamp(Mth.degreesDifference(0.0F,
                    rotp.core.util.functions.MathUtil.yRotDegFromVec(kept.getEyePosition().subtract(eyesBefore))),
                    -turnLimit, turnLimit);
            float turned = Mth.degreesDifference(0.0F, stand.getYRot());
            helper.assertTrue(Math.abs(wantedTurn) > 1.0F && Math.abs(turned - wantedTurn) < 0.5F,
                    "1.16 turned the Stand " + wantedTurn + " degrees toward the cow the entity bomb struck, in the "
                            + "hit's own tick; it turned " + turned);
            helper.assertTrue(KillerQueenStateService.primaryBombTarget(owner) == kept,
                    "1.16 StandEntity.punch struck the cow the entity bomb was pressed at, not the one the user "
                            + "looked at; the bomb is on " + KillerQueenStateService.primaryBombTarget(owner));

            // an entity bomb at that cow, now out of the Stand's reach but kept, the user turned to another cow
            KillerQueenStateService.clearPrimaryBomb(owner);
            kept.setPos(owner.getX() + 5.0D, owner.getY(), owner.getZ());
            looked.setPos(owner.getX(), owner.getY(), owner.getZ() + 1.5D);
            turnOwner(owner, -90.0F, -90.0F);
            aimStand(stand, new ActionTarget(kept));
            bomb = startBomb(helper, owner, power, stand, KillerQueenLionAddon.PRIMARY_BOMB_NAME);
            helper.assertTrue(bomb.getTaskTarget(level).getEntity() == kept,
                    "fixture: the entity bomb did not keep the cow 5 blocks away: " + bomb.getTaskTarget(level));
            turnOwner(owner, 0.0F, 0.0F);
            aimStand(stand, new ActionTarget(looked));
            stand.updatePosition(owner);
            performNow(bomb);
            helper.assertTrue(KillerQueenStateService.primaryBombTarget(owner) == looked,
                    "1.16 aimed the entity bomb from the user's view again once its cow was out of reach, at the cow "
                            + "the user looked at; the bomb is on " + KillerQueenStateService.primaryBombTarget(owner));
            helper.assertTrue(bomb.getTaskTarget(level).getEntity() == looked,
                    "1.16 StandEntity.punch made the cow the entity bomb struck its task's target; the task holds "
                            + bomb.getTaskTarget(level));

            // 1.16 StandEntity.setTaskTarget let server code drop the target of the current task, the struck one too
            helper.assertTrue(keptTarget(bomb) != null, "fixture: the entity bomb keeps no target beside its task's");
            stand.setTaskTarget(null);
            helper.assertTrue(keptTarget(bomb) == null,
                    "1.16 StandEntity.setTaskTarget(null) dropped the one target the entity bomb's task held; the "
                            + "kept target field still holds " + keptTarget(bomb));
            helper.assertTrue(bomb.getTaskTarget(level).isEmpty(level),
                    "1.16 StandEntity.setTaskTarget(null) dropped the target of the entity bomb, the cow it struck; the "
                            + "task still holds " + bomb.getTaskTarget(level));
        } finally {
            cleanup(helper, owner, kept, looked);
        }
        helper.succeed();
    }

    // 1.16 PunchBomb.punchEntity (100 rotp_zkq8 :81-88) stored the bomb on the living entity struck before
    // StandEntity.attackTarget ran the hit (punchEntity, then doHit), so a target that took no damage was bombed too:
    // one whose hurt was refused, one blocking with a shield toward the Stand.
    @GameTest(template = "empty", batch = "kqParityEntityBombRefusedHit", timeoutTicks = 20)
    public static void entityBombPlantsOnATargetThatTakesNoDamageAsTheDonorDid(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-entity-bomb-refused", GameType.SURVIVAL);
        ServerPlayer owner = kq.owner();
        StandPower power = kq.power();
        ServerLevel level = helper.getLevel();
        StandEntity stand = staminaFixture(helper, kq);
        Cow refusing = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 1);
        Cow plain = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 1);
        ShieldBlocker blocker = new ShieldBlocker(level);
        Consumer<LivingIncomingDamageEvent> refuse = event -> {
            if (event.getEntity() == refusing) {
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingIncomingDamageEvent.class, refuse);
        List<String> wrong = new ArrayList<>();
        try {
            Vec3 away = owner.position().add(10.0D, 0.0D, 0.0D);
            refusing.setPos(away);
            plain.setPos(away.add(0.0D, 0.0D, 3.0D));
            // facing north, toward the user and the Stand in front of it
            blocker.moveTo(away.x, away.y, away.z - 3.0D, 180.0F, 0.0F);
            blocker.setYHeadRot(180.0F);
            blocker.getAbilities().invulnerable = false;
            blocker.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
            helper.assertTrue(level.addFreshEntity(blocker), "fixture: the shield blocker could not join the level");
            turnOwner(owner, 0.0F, 0.0F);
            // the target, what it is, whether the punch hurts it
            Object[][] rows = {
                    { refusing, "a cow whose hurt is cancelled", false },
                    { blocker, "a player blocking with a shield toward the Stand", false },
                    { plain, "a cow the punch hurts", true } };
            for (Object[] row : rows) {
                LivingEntity target = (LivingEntity) row[0];
                String what = (String) row[1];
                boolean hurts = (Boolean) row[2];
                Vec3 home = target.position();
                // 2 blocks south of the user, who looks south
                target.setPos(owner.getX(), owner.getY(), owner.getZ() + 2.0D);
                target.invulnerableTime = 0;
                if (target == blocker) {
                    blocker.raiseShield(helper);
                }
                float health = target.getHealth();
                KillerQueenStateService.clearPrimaryBomb(owner);
                aimStand(stand, new ActionTarget(target));
                EntityActionInstance bomb = startBomb(helper, owner, power, stand, KillerQueenLionAddon.PRIMARY_BOMB_NAME);
                aimStand(stand, ActionTarget.EMPTY);
                stand.updatePosition(owner);
                performNow(bomb);
                if (target.getHealth() < health != hurts) {
                    wrong.add("fixture: " + what + (hurts ? " took no damage" : " was hurt") + ", health " + health
                            + " -> " + target.getHealth());
                }
                if (KillerQueenStateService.primaryBombTarget(owner) != target) {
                    wrong.add(what + ": 1.16 PunchBomb.punchEntity bombed it before the hit ran; the bomb is on "
                            + KillerQueenStateService.primaryBombTarget(owner));
                }
                LivingComponentAction.getComponent(stand).setAction(null, owner, SyncType.NO_SYNC);
                target.setPos(home);
            }
        } finally {
            NeoForge.EVENT_BUS.unregister(refuse);
            cleanup(helper, owner, refusing, plain, blocker);
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 kq_entitybomb on a target that takes no damage: " + wrong);
        helper.succeed();
    }

    // GameTestPlayers' survival mock (rotp core) that raises its shield at once: vanilla isBlocking needs 5 use ticks
    private static final class ShieldBlocker extends Player {
        ShieldBlocker(Level level) {
            super(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "kq-shield-blocker"));
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public boolean isCreative() {
            return false;
        }

        void raiseShield(GameTestHelper helper) {
            startUsingItem(InteractionHand.OFF_HAND);
            for (int i = 0; i < 8 && isUsingItem(); i++) {
                updateUsingItem(getUseItem());
            }
            helper.assertTrue(isBlocking(), "fixture: the blocker's shield is not raised");
        }
    }

    // 1.16 PunchBomb.getTargetRequirement is ENTITY: PowerBaseImpl.checkTarget refused the press, with no message,
    // unless the client's target was an entity (a block, nothing, or the user's own precision ray finding none);
    // Action.checkRangeAndTarget answered "target_too_far" beyond 8 blocks, another entity than the user, and with the
    // Stand out StandEntityAction.checkStandTarget wanted a living entity it may attack.
    @GameTest(template = "empty", batch = "kqParityEntityBombTarget", timeoutTicks = 20)
    public static void entityBombIsRefusedUnlessPressedAtAnEntityAsTheDonorDid(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-entity-bomb-target", GameType.SURVIVAL);
        ServerPlayer owner = kq.owner();
        StandPower power = kq.power();
        power.skipProgression();
        StandEntity stand = staminaFixture(helper, kq);
        BlockPos pad = kq.pad();
        Ability bomb = power.getAbility(KillerQueenLionAddon.PRIMARY_BOMB_NAME);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 1);
        Minecart cart = helper.spawn(EntityType.MINECART, 1, 2, 1);
        List<String> wrong = new ArrayList<>();
        try {
            helper.assertTrue(bomb instanceof EntityActionAbility, "fixture: the entity bomb is not an action");
            // the user looks east; the cow and the cart are south of it, out of the view
            turnOwner(owner, -90.0F, -90.0F);
            cow.setPos(owner.getX(), owner.getY(), owner.getZ() + 3.0D);
            cart.setPos(owner.getX(), owner.getY(), owner.getZ() + 4.0D);

            aimUser(owner, ActionTarget.EMPTY);
            expectBombCheck(wrong, bomb, power, "pressed at nothing", false, null);
            expectBombPress(wrong, owner, power, stand, bomb, (short) 91, "pressed at nothing", false);
            aimUser(owner, new ActionTarget(pad.below(), Direction.UP));
            expectBombCheck(wrong, bomb, power, "pressed at a block", false, null);
            expectBombPress(wrong, owner, power, stand, bomb, (short) 92, "pressed at a block", false);
            aimUser(owner, new ActionTarget(owner));
            expectBombCheck(wrong, bomb, power, "pressed at its user", false, null);
            aimUser(owner, new ActionTarget(cart));
            expectBombCheck(wrong, bomb, power, "pressed at a minecart", false, null);

            cow.setPos(owner.getX() + 12.0D, owner.getY(), owner.getZ());
            aimUser(owner, new ActionTarget(cow));
            expectBombCheck(wrong, bomb, power, "pressed at a cow 12 blocks away", false, "target_too_far");

            cow.setPos(owner.getX(), owner.getY(), owner.getZ() + 3.0D);
            aimUser(owner, new ActionTarget(cow));
            expectBombCheck(wrong, bomb, power, "pressed at a cow", true, null);

            // nothing under the crosshair, the cow on the Stand's precision ray from the user's eyes
            cow.setPos(owner.getX() + 2.5D, owner.getY(), owner.getZ());
            aimUser(owner, ActionTarget.EMPTY);
            expectBombCheck(wrong, bomb, power, "pressed at nothing with a cow on the precision ray", true, null);

            aimUser(owner, new ActionTarget(cow));
            expectBombPress(wrong, owner, power, stand, bomb, (short) 93, "pressed at a cow", true);

            // with no Stand out the user is the performer: it is not a target, and the living entity the Stand may
            // attack is asked for only once the Stand is out (StandEntityAction.checkRangeAndTarget)
            power.getPowerType().forceUnsummon(owner, power);
            aimUser(owner, new ActionTarget(owner));
            expectBombCheck(wrong, bomb, power, "pressed at its user with no Stand out", false, null);
            aimUser(owner, new ActionTarget(cart));
            expectBombCheck(wrong, bomb, power, "pressed at a minecart with no Stand out", true, null);
            aimUser(owner, new ActionTarget(cow));
            expectBombCheck(wrong, bomb, power, "pressed at a cow with no Stand out", true, null);
            helper.assertTrue(wrong.isEmpty(), "The 1.16 entity bomb target requirement was not kept: " + wrong);
        } finally {
            cleanup(helper, owner, cow, cart);
        }
        helper.succeed();
    }

    private static void aimUser(ServerPlayer owner, ActionTarget target) {
        LivingComponentAction.getComponent(owner).entityAim.setTarget(target);
    }

    private static void expectBombCheck(List<String> wrong, Ability bomb, StandPower power, String when,
            boolean allowed, String message) {
        ConditionCheck check = bomb.checkSpecificConditions(power);
        Component warning = check.getWarning();
        String key = warning != null && warning.getContents() instanceof TranslatableContents translatable
                ? translatable.getKey() : null;
        String expectedKey = message != null ? "jojo.message.action_condition." + message : null;
        if (check.isPositive() != allowed || !java.util.Objects.equals(key, expectedKey)) {
            wrong.add("the entity bomb " + when + " " + (check.isPositive() ? "was allowed" : "was refused")
                    + " with message " + key + " (1.16: " + (allowed ? "allowed" : "refused") + " with message "
                    + expectedKey + ")");
        }
    }

    private static void expectBombPress(List<String> wrong, ServerPlayer owner, StandPower power, StandEntity stand,
            Ability bomb, short key, String when, boolean starts) {
        // the server's press handler: the condition check first, the press only when it passes
        rotp.core.powersystem.ability.condition.AvailableAbilities available =
                new rotp.core.powersystem.ability.condition.AvailableAbilities();
        available.update(power, power.getMoveset());
        if (available.getContextVariationContainer(bomb) == null) {
            wrong.add("fixture: the entity bomb is not among the available abilities " + when);
            return;
        }
        if (AbilityInput.withConditionCheck(available.getContextVariationContainer(bomb), owner, InputMethod.CLICK)) {
            AbilityInput.keyPress(key, bomb, owner, new FriendlyByteBuf(Unpooled.buffer()), InputMethod.CLICK, 0.0F,
                    BufferingState.clickCanBuffer(), bomb.abilityId);
            AbilityInput.keyRelease(key, owner);
        }
        boolean started = stand.getCurStandAction() != null
                && stand.getCurStandAction().ability == bomb;
        if (started != starts) {
            wrong.add("the entity bomb key " + when + (started ? " started the action" : " did not start it")
                    + " (1.16: " + (starts ? "started" : "refused") + ")");
        }
    }

    private static EntityActionInstance startBomb(GameTestHelper helper, ServerPlayer owner, StandPower power,
            StandEntity stand, String name) {
        Ability ability = power.getAbility(name);
        helper.assertTrue(ability instanceof EntityActionAbility, "fixture: " + name + " is not an action");
        EntityActionInstance action = ((EntityActionAbility) ability)
                .initActionOnAbilityUse(helper.getLevel(), owner, stand, null);
        LivingComponentAction.getComponent(stand).setAction(action, owner, SyncType.NO_SYNC);
        helper.assertTrue(stand.getCurStandAction() == action && !stand.isArmsOnlyMode(),
                "fixture: " + name + " is not the whole Stand's action");
        return action;
    }

    // the windup skipped: the strike runs on this tick
    private static void performNow(EntityActionInstance action) {
        action.setPhaseStart(ActionPhase.PERFORM);
        action._tickAction();
    }

    private static void assertFrontAlongView(GameTestHelper helper, ServerPlayer owner, StandEntity stand,
            double front, String when) {
        Vec3 expected = new Vec3(0.0D, 0.0D, front)
                .xRot(-owner.getXRot() * Mth.DEG_TO_RAD)
                .yRot(-owner.getYRot() * Mth.DEG_TO_RAD);
        stand.updatePosition(owner);
        Vec3 actual = stand.position().subtract(owner.position());
        helper.assertTrue(actual.distanceTo(expected) < 1.0E-4D,
                "1.16 put Killer Queen at its user's feet + " + expected + " during " + when + ", the user looking "
                        + "south, got " + actual + " (offset " + stand.offsetFromUser.getRelativeOffset() + " "
                        + stand.offsetFromUser.getRotations() + ")");
    }

    private static ActionTarget keptTarget(EntityActionInstance action) {
        try {
            java.lang.reflect.Field field = EntityActionInstance.class.getDeclaredField("actionTargetSnapshot");
            field.setAccessible(true);
            ActionTarget target = (ActionTarget) field.get(action);
            return target == null || target.getType() == ActionTarget.TargetType.EMPTY ? null : target;
        }
        catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void turnOwner(ServerPlayer owner, float viewYaw, float bodyYaw) {
        owner.setYRot(viewYaw);
        owner.yRotO = viewYaw;
        owner.setYHeadRot(viewYaw);
        owner.yHeadRotO = viewYaw;
        owner.setXRot(0.0F);
        owner.xRotO = 0.0F;
        owner.yBodyRot = bodyYaw;
        owner.yBodyRotO = bodyYaw;
    }

    // 1.16 BlockBombAction.replaceAction: with a block bomb set, the kq_block_bomb slot is kq_block_explode, fired by
    // the slot's own click, never both; once the bomb is gone the plant comes back.
    @GameTest(template = "empty", batch = "kqParityBlockBombSlot", timeoutTicks = 20)
    public static void blockBombSlotOffersTheDetonationInPlaceOfThePlant(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-block-slot", GameType.CREATIVE);
        ServerPlayer owner = kq.owner();
        StandPower power = kq.power();
        List<String> wrong = new ArrayList<>();
        try {
            summon(helper, kq);
            String plant = KillerQueenLionAddon.BLOCK_BOMB_NAME;
            String detonate = KillerQueenLionAddon.BLOCK_BOMB_DETONATE_NAME;
            int plantSlots = 0;
            int detonateSlots = 0;
            rotp.core.powersystem.ability.controls.ControlSchemeTemplate template =
                    power.getPowerType().makeDefaultControlSchemeTemplate();
            for (var group : template.groups.values()) {
                for (var hotbar : group.hotbars) {
                    for (var slot : hotbar.slots) {
                        if (slot.values().stream().anyMatch(binds -> binds.containsValue(detonate))) {
                            detonateSlots++;
                        }
                        java.util.Map<InputMethod, String> binds = slot.get(null);
                        if (binds != null && plant.equals(binds.get(InputMethod.CLICK))) {
                            plantSlots++;
                        }
                    }
                }
            }
            if (plantSlots != 1 || detonateSlots != 0) {
                wrong.add(plant + " has " + plantSlots + " click slot(s) and " + detonate + " " + detonateSlots
                        + " slot(s) of its own");
            }
            blockBombOffer(owner, power, plant, "before a bomb", wrong);
            helper.assertTrue(KillerQueenStateService.markBlockBomb(owner, kq.pad().below()),
                    "fixture: the block bomb was rejected");
            blockBombOffer(owner, power, detonate, "with the block bomb set", wrong);
            KillerQueenStateService.clearBlockBomb(owner);
            blockBombOffer(owner, power, plant, "after the bomb was cleared", wrong);
        } finally {
            cleanup(helper, owner);
        }
        helper.assertTrue(wrong.isEmpty(), "The block bomb slot does not offer what 1.16 shows: " + wrong);
        helper.succeed();
    }

    private static void blockBombOffer(ServerPlayer owner, StandPower power, String expected, String when,
            List<String> wrong) {
        AbilityConditionCheck container = resolve(power, KillerQueenLionAddon.BLOCK_BOMB_NAME);
        String shown = container != null ? container.ability.name() : "nothing";
        if (!expected.equals(shown)) {
            wrong.add(when + ": the kq_block_bomb slot shows " + shown + ", not " + expected);
        } else if (!AbilityInput.withConditionCheck(container, owner, InputMethod.CLICK)) {
            wrong.add(when + ": a click on the kq_block_bomb slot does not fire " + expected);
        }
    }

    private static void huntCleanup(GameTestHelper helper, @Nullable ServerPlayer stranger, List<Entity> spawned) {
        spawned.forEach(Entity::discard);
        if (stranger != null) {
            stranger.connection.disconnect(Component.literal("Killer Queen SHA hunt GameTest complete"));
        }
        KillerQueenGameTests.sheerHeartPad(helper, false);
    }

    // What the owner's client holds after the state syncs sent so far, applied in order as the payload handler does.
    private static void applySentSyncs(EmbeddedChannel sent, ServerPlayer owner, KillerQueenState client) {
        // The server batches a player's packets within a tick
        sent.flush();
        for (Object message : sent.outboundMessages()) {
            applySync(message, owner, client);
        }
        sent.outboundMessages().clear();
    }

    private static void applySync(Object message, ServerPlayer owner, KillerQueenState client) {
        if (message instanceof ClientboundBundlePacket bundle) {
            for (Packet<?> packet : bundle.subPackets()) {
                applySync(packet, owner, client);
            }
        } else if (message instanceof ClientboundCustomPayloadPacket custom
                && custom.payload() instanceof KillerQueenStateSyncPayload sync
                && sync.ownerUuid().equals(owner.getUUID())) {
            client.applyClientSnapshot(
                    sync.primaryTargetUuid(),
                    sync.primaryTargetDimension(),
                    sync.blockBombPos(),
                    sync.blockBombDimension(),
                    sync.bubbleBlockMarks(),
                    sync.bubbleBlockDimension(),
                    sync.sheerHeartUuid(),
                    sync.sheerHeartDimension(),
                    sync.bitesTheDustMarked(),
                    sync.bitesTheDustVictimUuid(),
                    sync.bitesTheDustVictimDimension(),
                    sync.bitesTheDustMarkerTime(),
                    sync.bitesTheDustMarkerGameTime(),
                    sync.bitesTheDustMarkerDimension(),
                    sync.bitesTheDustMarkerEpoch(),
                    sync.linkedTimeStopId(),
                    sync.bitesTheDustTriggerOwnerUuid(),
                    sync.bitesTheDustTriggerVictimUuid(),
                    sync.bitesTheDustTriggerTicks());
        }
    }

    // 1.16 KQBarrage registered its cry with the one-argument standSound (playInArmsOnly true): an arms-only
    // Killer Queen still cried.
    @GameTest(template = "empty", batch = "kqParityBarrageCry", timeoutTicks = 20)
    public static void barrageCriesInArmsOnlyMode(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-barrage-cry", GameType.CREATIVE);
        ServerPlayer owner = kq.owner();
        StandEntity stand = summon(helper, kq);
        boolean full;
        boolean armsOnly;
        try {
            Ability barrage = kq.power().getAbility(KillerQueenLionAddon.BARRAGE_NAME);
            helper.assertTrue(barrage instanceof EntityActionType, "fixture: no registered Lion barrage");
            EntityActionInstance action = ((EntityActionType) barrage).createActionObj();
            Method playsCry = StandEntityBarrage.class.getDeclaredMethod(
                    "playsBarrageCry", Level.class, StandEntity.class);
            playsCry.setAccessible(true);
            full = (Boolean) playsCry.invoke(action, stand.level(), stand);
            stand.setArmsOnlyMode(true, true);
            armsOnly = (Boolean) playsCry.invoke(action, stand.level(), stand);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("fixture: the core barrage cry hook is missing", e);
        } finally {
            cleanup(helper, owner);
        }
        helper.assertTrue(full, "fixture: the fully summoned Killer Queen does not cry");
        helper.assertTrue(armsOnly, "The arms-only Killer Queen barrage stayed silent; 1.16 KQBarrage cried there");
        helper.succeed();
    }

    private static List<SheerHeartAttackEntity> ownedSheerHearts(ServerPlayer owner) {
        return List.copyOf(((ServerLevel) owner.level()).getEntities(
                KillerQueenLionAddon.SHEER_HEART_ENTITY.get(),
                sheer -> !sheer.isRemoved() && sheer.isOwnedBy(owner.getUUID())));
    }

    /*
     * The fixture's cleanup when a test fails: a player who joined after the test began and a bomb of the owner that
     * the test never handed over go with the owner, so one failure does not leave a player by the shared pad.
     */
    @GameTest(template = "empty", batch = "kqParityMockPlayerCleanup", timeoutTicks = 40)
    public static void aFailedTestLetsGoOfItsRelogsAndTheBombsItNeverHandedOver(GameTestHelper helper) {
        Fixture kq = killerQueen(helper, "kq-cleanup-owner", GameType.CREATIVE);
        ServerPlayer bystander = kq.cleanup().join(
                mockPlayer(helper, UUID.randomUUID(), "kq-cleanup-bystander", kq.pad().south(6)));
        List<Entity> made = new ArrayList<>();
        sheerHeartBeside(helper, kq, made);
        SheerHeartAttackEntity bomb = (SheerHeartAttackEntity) made.getFirst();
        kq.cleanup().testFailed(helper.testInfo, null);
        boolean padReleased = KillerQueenGameTests.sheerHeartPad(helper, false) == null;
        helper.succeedWhen(() -> {
            MinecraftServer server = helper.getLevel().getServer();
            helper.assertTrue(kq.owner().hasDisconnected() && bystander.hasDisconnected()
                            && server.getPlayerList().getPlayer(kq.owner().getUUID()) == null
                            && server.getPlayerList().getPlayer(bystander.getUUID()) == null,
                    "A failed test left a player by the shared pad (" + kq.owner().hasDisconnected()
                            + " owner gone, " + bystander.hasDisconnected() + " bystander gone)");
            helper.assertTrue(bomb.isRemoved() && padReleased,
                    "A failed test kept a bomb it never handed over (" + bomb.isRemoved() + " removed) or its pad ("
                            + padReleased + " released)");
        });
    }

    // A finite bar (Survival) with the Stand out; the user takes no blast damage.
    private static StandEntity staminaFixture(GameTestHelper helper, Fixture kq) {
        kq.owner().setInvulnerable(true);
        helper.assertTrue(!kq.power().isStaminaInfinite() && kq.power().getMaxStamina() >= 250.0F,
                "fixture: the bar is infinite or shorter than 250: " + kq.power().getMaxStamina());
        return summon(helper, kq);
    }

    private static void runOnShortBar(GameTestHelper helper, ServerPlayer owner, StandPower power,
            LivingEntity performer, String name, float stamina, BooleanSupplier acted, List<String> wrong) {
        if (startAndPerform(helper, owner, power, performer, name, stamina, wrong)) {
            if (!acted.getAsBoolean()) {
                wrong.add(name + " did nothing at " + stamina + " stamina");
            }
            if (power.getStamina() != 0.0F) {
                wrong.add(name + " left " + power.getStamina() + " of a " + stamina + " bar");
            }
        }
    }

    private static void runAtFloor(GameTestHelper helper, ServerPlayer owner, StandPower power,
            LivingEntity performer, String name, float stamina, float left, BooleanSupplier expected,
            String failure, List<String> wrong) {
        if (startAndPerform(helper, owner, power, performer, name, stamina, wrong)) {
            if (!expected.getAsBoolean()) {
                wrong.add(name + " " + failure + " at " + stamina + " stamina");
            }
            if (power.getStamina() != left) {
                wrong.add(name + " left " + power.getStamina() + " of a " + stamina + " bar, not " + left);
            }
        }
    }

    // The ability's own conditions at this stamina, then its action run to the perform tick at once.
    private static boolean startAndPerform(GameTestHelper helper, ServerPlayer owner, StandPower power,
            LivingEntity performer, String name, float stamina, List<String> wrong) {
        Ability ability = power.getAbility(name);
        helper.assertTrue(ability instanceof EntityActionAbility, "fixture: " + name + " is not an action");
        power.setStamina(stamina);
        if (!ability.checkSpecificConditions(power).isPositive()) {
            wrong.add(name + " refused at " + stamina + " stamina");
            return false;
        }
        EntityActionInstance action = ((EntityActionAbility) ability)
                .initActionOnAbilityUse(helper.getLevel(), owner, performer, null);
        action.setPhaseStart(ActionPhase.PERFORM);
        LivingComponentAction component = LivingComponentAction.getComponent(performer);
        component.setAction(action, owner, SyncType.NO_SYNC);
        helper.assertTrue(component.getAction() == action, "fixture: " + name + " was not started");
        action._tickAction();
        component.setAction(null, owner, SyncType.NO_SYNC);
        return true;
    }

    private static void aimStand(StandEntity stand, ActionTarget target) {
        LivingComponentAction.getComponent(stand).entityAim.setTarget(target);
    }

    private static int bombProjectiles(ServerPlayer owner) {
        return owner.level().getEntitiesOfClass(KillerQueenBombProjectile.class,
                owner.getBoundingBox().inflate(20.0D), projectile -> projectile.isOwnedBy(owner.getUUID())).size();
    }

    private static boolean staminaMessageSent(EmbeddedChannel sent) {
        // The server batches a player's packets within a tick
        sent.flush();
        for (Object message : sent.outboundMessages()) {
            if (isStaminaMessage(message)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStaminaMessage(Object message) {
        if (message instanceof ClientboundBundlePacket bundle) {
            for (Packet<?> packet : bundle.subPackets()) {
                if (isStaminaMessage(packet)) {
                    return true;
                }
            }
            return false;
        }
        return message instanceof ClientboundSetActionBarTextPacket packet
                && packet.text().getContents() instanceof TranslatableContents text
                && text.getKey().endsWith("not_enough_stamina");
    }

    private static void move(GameTestHelper helper, ServerPlayer player, ServerLevel to, Vec3 spot) {
        helper.assertTrue(
                player.teleportTo(to, spot.x, spot.y, spot.z, Set.of(), 0.0F, 0.0F) && player.level() == to,
                "fixture: " + player.getGameProfile().getName() + " could not move to "
                        + to.dimension().location());
    }

    private static Fixture killerQueen(GameTestHelper helper, String name, GameType mode) {
        BlockPos pad = KillerQueenGameTests.sheerHeartPad(helper, true);
        ServerPlayer owner = mockPlayer(helper, UUID.randomUUID(), name, pad);
        // The player, the bombs he owns and the pad go when the test ends, whether it passes, fails or times out
        KillerQueenGameTests.MockPlayerCleanup cleanup = new KillerQueenGameTests.MockPlayerCleanup(helper, owner);
        helper.testInfo.addListener(cleanup);
        // A Creative grant skips progression, as in 1.16; Survival starts at resolve 0.
        owner.setGameMode(mode);
        StandPower power = StandPower.get(owner);
        helper.assertTrue(power != null, "fixture: the mock player has no Stand power attachment");
        power.setStand(KillerQueenLionAddon.KILLER_QUEEN.get());
        power.setStamina(power.getMaxStamina());
        return new Fixture(owner, power, pad, cleanup);
    }

    private static StandEntity summon(GameTestHelper helper, Fixture kq) {
        StandEntity stand = kq.power().getSummonedStandEntity();
        if (stand == null) {
            helper.assertTrue(
                    KillerQueenLionAddon.KILLER_QUEEN.get().summon(kq.owner(), kq.power()),
                    "fixture: Killer Queen could not be summoned");
            stand = kq.power().getSummonedStandEntity();
        }
        helper.assertTrue(stand != null && stand.isAddedToLevel(),
                "fixture: Killer Queen was not added to the level");
        stand.summonLockTicks = 0;
        return stand;
    }

    private static Cow spawnCow(GameTestHelper helper, ServerPlayer near, double dx, double dz) {
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 1);
        cow.setPos(near.getX() + dx, near.getY(), near.getZ() + dz);
        return cow;
    }

    // Puts the owner in the state in which every gated bomb input is visible and usable.
    private static void makeEveryBombUsable(
            GameTestHelper helper,
            Fixture kq,
            Cow carrier,
            BlockPos marked,
            BlockPos blockBomb,
            List<Entity> spawned) {
        ServerPlayer owner = kq.owner();
        ServerLevel level = helper.getLevel();
        StandEntity stand = summon(helper, kq);
        KillerQueenStateService.markBubbleBlock(owner, marked);
        KillerQueenStateService.markPrimaryBomb(owner, carrier);
        KillerQueenStateService.markBlockBomb(owner, blockBomb);
        if (!ItemBombService.hasBombsInRange(owner, ItemBombService.LEGACY_RANGE)) {
            owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND, 2));
            helper.assertTrue(ItemBombService.markOffhandItem(owner), "fixture: the item bomb was rejected");
            carrier.setItemSlot(EquipmentSlot.MAINHAND, owner.getMainHandItem().copy());
            owner.getInventory().clearContent();
        }
        BubbleBombEntity projectile = new BubbleBombEntity(owner, stand, level);
        helper.assertTrue(!projectile.isRemoved() && level.addFreshEntity(projectile),
                "fixture: the bubble bomb was rejected");
        spawned.add(projectile);
        helper.assertTrue(
                KillerQueenStateService.hasBubbleBlockMarks(owner)
                        && KillerQueenStateService.primaryBombTarget(owner) == carrier
                        && blockBomb.equals(KillerQueenStateService.blockBombPos(owner))
                        && ItemBombService.hasBombsInRange(owner, ItemBombService.LEGACY_RANGE),
                "fixture: a bomb precondition is missing");
    }

    // 1.16 SHABack needed a bomb of the user within the 140 blocks of isSHpresent, not only a summon state.
    private static void sheerHeartBeside(GameTestHelper helper, Fixture kq, List<Entity> spawned) {
        ServerPlayer owner = kq.owner();
        StandEntity stand = summon(helper, kq);
        SheerHeartAttackEntity bomb = KillerQueenLionAddon.SHEER_HEART_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(bomb != null, "fixture: no Sheer Heart Attack entity");
        bomb.bind(owner, stand, null);
        bomb.setPos(owner.getX() + 1.0D, owner.getY(), owner.getZ());
        helper.assertTrue(!bomb.isRemoved() && helper.getLevel().addFreshEntity(bomb)
                        && KillerQueenStateService.linkSheerHeart(owner, bomb),
                "fixture: the Sheer Heart bomb was rejected");
        spawned.add(bomb);
    }

    // Power.updateAvailableMoves() answers from a per-tick cache, and these tests change the state within a tick.
    @Nullable
    private static AbilityConditionCheck resolve(StandPower power, String ability) {
        power._curAvailableMoves.update(power, power.getMoveset());
        return power._curAvailableMoves.getContextVariationContainer(ability);
    }

    // The server side of a key press, as ClAbilityInputPacket.handlePress resolves and checks it.
    private static boolean accepts(ServerPlayer owner, StandPower power, Input input) {
        AbilityConditionCheck resolved = resolve(power, input.ability());
        return resolved != null && AbilityInput.withConditionCheck(resolved, owner, input.method());
    }

    private static boolean press(ServerPlayer owner, StandPower power, Input input, short key) {
        Ability base = power.getAbility(input.ability());
        AbilityConditionCheck resolved = resolve(power, input.ability());
        if (base == null || resolved == null
                || !AbilityInput.withConditionCheck(resolved, owner, input.method())) {
            return false;
        }
        AbilityInput.keyPress(
                key,
                resolved.ability,
                owner,
                new FriendlyByteBuf(Unpooled.buffer()),
                input.method(),
                0.0F,
                BufferingState.clickCanBuffer(),
                base.abilityId);
        return true;
    }

    // Runs the ability's action to its perform tick at once, so a config override stays inside one tick.
    private static void performNow(
            GameTestHelper helper,
            ServerPlayer owner,
            StandPower power,
            StandEntity stand,
            String abilityName) {
        Ability ability = power.getAbility(abilityName);
        helper.assertTrue(ability instanceof EntityActionAbility, "fixture: " + abilityName + " is not an action");
        EntityActionInstance action = ((EntityActionAbility) ability)
                .initActionOnAbilityUse(helper.getLevel(), owner, stand, null);
        action.setPhaseStart(ActionPhase.PERFORM);
        LivingComponentAction component = LivingComponentAction.getComponent(stand);
        component.setAction(action, owner, SyncType.NO_SYNC);
        helper.assertTrue(component.getAction() == action, "fixture: " + abilityName + " was not started");
        action._tickAction();
        component.setAction(null, owner, SyncType.NO_SYNC);
    }

    // Clip names of the shipped Lion animation file, grouped as the animation loader groups them (punch2 is punch).
    private static Set<String> shippedClips(GameTestHelper helper) {
        Set<String> clips = new HashSet<>();
        try {
            Path file = ModList.get().getModFileById(KillerQueenLionAddon.MOD_ID).getFile()
                    .findResource(ANIMATION_FILE);
            try (JsonReader json = new JsonReader(Files.newBufferedReader(file))) {
                json.beginObject();
                while (json.hasNext()) {
                    if (!json.nextName().equals("animations")) {
                        json.skipValue();
                        continue;
                    }
                    json.beginObject();
                    while (json.hasNext()) {
                        clips.add(StringUtil.splitIntAtTheEnd(json.nextName()).getFirst());
                        json.skipValue();
                    }
                    json.endObject();
                }
                json.endObject();
            }
        } catch (IOException | RuntimeException failure) {
            helper.fail("fixture: the Lion animation file could not be read: " + failure);
        }
        helper.assertTrue(
                clips.containsAll(List.of("idle", "punch", "armsOnly_punch", "barrage", "heavy_punch")),
                "fixture: the Lion animation file lost an attack clip; it has " + clips);
        return clips;
    }

    // Whether a state sync with these marks went to the owner after the last respawn packet on the connection.
    private static boolean marksSentAfterRespawn(
            GameTestHelper helper,
            EmbeddedChannel sent,
            ServerPlayer owner,
            List<BlockPos> marks,
            ResourceLocation dimension) {
        return syncSentAfterRespawn(
                helper,
                sent,
                owner,
                sync -> sync.bubbleBlockMarks().equals(marks) && dimension.equals(sync.bubbleBlockDimension()));
    }

    // Whether a state sync of the owner that passes the check was sent after the last respawn packet.
    private static boolean syncSentAfterRespawn(
            GameTestHelper helper,
            EmbeddedChannel sent,
            ServerPlayer owner,
            Predicate<KillerQueenStateSyncPayload> carries) {
        // The server batches a player's packets within a tick
        sent.flush();
        boolean respawned = false;
        boolean resent = false;
        for (Object message : sent.outboundMessages()) {
            if (message instanceof ClientboundRespawnPacket) {
                respawned = true;
                resent = false;
            } else if (respawned && isSync(message, owner, carries)) {
                resent = true;
            }
        }
        helper.assertTrue(respawned, "fixture: the dimension change left no respawn packet on the connection");
        return resent;
    }

    private static boolean isSync(
            Object message, ServerPlayer owner, Predicate<KillerQueenStateSyncPayload> carries) {
        if (message instanceof ClientboundBundlePacket bundle) {
            for (Packet<?> packet : bundle.subPackets()) {
                if (isSync(packet, owner, carries)) {
                    return true;
                }
            }
            return false;
        }
        return message instanceof ClientboundCustomPayloadPacket packet
                && packet.payload() instanceof KillerQueenStateSyncPayload sync
                && sync.ownerUuid().equals(owner.getUUID())
                && carries.test(sync);
    }

    private static String withoutHand(String clip) {
        for (String hand : List.of("_left", "_right")) {
            if (clip.endsWith(hand)) {
                return clip.substring(0, clip.length() - hand.length());
            }
        }
        return clip;
    }

    private static Consumer<ExplosionEvent.Start> recordBlasts(
            ServerLevel level, ServerPlayer source, List<Blast> blasts) {
        Consumer<ExplosionEvent.Start> listener = event -> {
            if (event.getLevel() == level && event.getExplosion().getDirectSourceEntity() == source) {
                blasts.add(new Blast(event.getExplosion().center(), event.getExplosion().radius()));
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ExplosionEvent.Start.class, listener);
        return listener;
    }

    private static void guarded(Consumer<ExplosionEvent.Start> listener, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException | Error failure) {
            NeoForge.EVENT_BUS.unregister(listener);
            throw failure;
        }
    }

    private static boolean hasBlast(List<Blast> blasts, BlockPos corner, float radius) {
        Vec3 expected = new Vec3(corner.getX(), corner.getY(), corner.getZ());
        for (Blast blast : blasts) {
            if (blast.center().distanceToSqr(expected) < 1.0E-8D && blast.radius() == radius) {
                return true;
            }
        }
        return false;
    }

    private static void cleanup(GameTestHelper helper, ServerPlayer owner, Entity... spawned) {
        // clearAll lets the bombs live on, as a death does
        ownedSheerHearts(owner).forEach(Entity::discard);
        KillerQueenStateService.clearAll(owner);
        StandPower power = StandPower.get(owner);
        if (power != null && power.hasPower() && power.getSummonedStandEntity() != null) {
            power.getPowerType().forceUnsummon(owner, power);
        }
        for (Entity entity : spawned) {
            if (entity != null) {
                entity.discard();
            }
        }
        owner.getInventory().clearContent();
        if (owner.connection != null) {
            owner.connection.disconnect(Component.literal("Killer Queen parity GameTest complete"));
        }
        KillerQueenGameTests.sheerHeartPad(helper, false);
    }

    // PlayerList.respawn leaves the connection on the dead player; ServerGamePacketListenerImpl.handleClientCommand
    // moves it to the new one, and without that a later disconnect leaves the new player in the level.
    private static ServerPlayer respawn(ServerPlayer player, boolean keepInventory, Entity.RemovalReason reason) {
        ServerPlayer respawned = player.getServer().getPlayerList().respawn(player, keepInventory, reason);
        respawned.connection.player = respawned;
        return respawned;
    }

    private static ServerPlayer mockPlayer(
            GameTestHelper helper, UUID uuid, String name, @Nullable BlockPos spot) {
        GameProfile profile = new GameProfile(uuid, name);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation());
        if (spot != null) {
            player.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, 0.0F, 0.0F);
        }
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }
}
