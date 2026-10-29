package rotp.addons.d4c;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.init.power.ModStands;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.addons.d4c.ability.D4CPhase2BAbility;
import rotp.addons.d4c.ability.D4CSummonKickAbility;
import rotp.addons.d4c.block.D4CPortalBlock;
import rotp.addons.d4c.entity.D4CAnnihilationCloneEntity;
import rotp.addons.d4c.entity.D4CMarkerEntity;
import rotp.addons.d4c.entity.D4CSummonKickEntity;
import rotp.addons.d4c.travel.D4CDimensionTargets;
import rotp.addons.d4c.travel.D4CDimensionTravelService;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(D4CAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class D4CGameTests {
    private static final double EPSILON = 1.0E-4D;
    private static final int ENTITY_TICK_START_TIMEOUT_TICKS = 200;

    private D4CGameTests() {}

    @GameTest(
            template = "empty",
            timeoutTicks = ENTITY_TICK_START_TIMEOUT_TICKS + GameTestChunkTickets.WAIT_TICKS)
    public static void cloneArmsOnFirstServerTickAndCopiesDimensions(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> cloneArmsOnFirstServerTickAndCopiesDimensionsBody(h));
    }

    private static void cloneArmsOnFirstServerTickAndCopiesDimensionsBody(GameTestHelper helper) {
        Cow owner = helper.spawnWithNoFreeWill(
                EntityType.COW, 1, 2, 1);
        Cow target = helper.spawnWithNoFreeWill(
                EntityType.COW, 2, 2, 2);
        D4CAnnihilationCloneEntity clone =
                D4CAddon.ANNIHILATION_CLONE.get()
                        .create(helper.getLevel());
        helper.assertTrue(
                clone != null,
                "The annihilation clone entity could not be created.");
        if (clone == null) {
            return;
        }

        clone.moveTo(
                target.getX(),
                target.getY(),
                target.getZ(),
                target.getYRot(),
                target.getXRot());
        clone.initialize(owner, target);
        helper.assertTrue(
                clone.annihilationTicks() < 0,
                "The clone armed during its summon frame.");
        assertDimensionsMatch(helper, clone, target);

        helper.assertTrue(
                helper.getLevel().addFreshEntity(clone),
                "The clone was rejected by the server level.");
        helper.succeedWhen(() -> {
            helper.assertTrue(
                    clone.lifeTicks() > 0,
                    "The clone has not received its first server tick.");
            helper.assertTrue(
                    clone.annihilationTicks() == clone.lifeTicks() - 1,
                    "The clone did not arm on its first server tick: life="
                            + clone.lifeTicks()
                            + ", annihilation="
                            + clone.annihilationTicks()
                            + ".");
            assertDimensionsMatch(helper, clone, target);
            clone.discard();
            owner.discard();
            target.discard();
        });
    }

    // 1.16 ag/ab.java:214-250: contact only arms; first hit at tick 20
    @GameTest(template = "empty", timeoutTicks = 20 + GameTestChunkTickets.WAIT_TICKS)
    public static void annihilationFirstHitLandsOnTickTwenty(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> annihilationFirstHitLandsOnTickTwentyBody(h));
    }

    private static void annihilationFirstHitLandsOnTickTwentyBody(GameTestHelper helper) {
        Cow owner = helper.spawnWithNoFreeWill(
                EntityType.COW, 1, 2, 1);
        Cow target = helper.spawnWithNoFreeWill(
                EntityType.COW, 2, 2, 2);
        D4CAnnihilationCloneEntity clone =
                D4CAddon.ANNIHILATION_CLONE.get()
                        .create(helper.getLevel());
        helper.assertTrue(
                clone != null,
                "The annihilation clone entity could not be created.");
        if (clone == null) {
            return;
        }

        clone.moveTo(
                target.getX(),
                target.getY(),
                target.getZ(),
                target.getYRot(),
                target.getXRot());
        clone.initialize(owner, target);
        float targetHealthBefore = target.getHealth();
        float cloneHealthBefore = clone.getHealth();
        helper.assertTrue(
                helper.getLevel().addFreshEntity(clone),
                "The clone was rejected by the server level.");
        helper.assertTrue(
                clone.annihilationTicks() == -1
                        && close(target.getHealth(), targetHealthBefore)
                        && close(clone.getHealth(), cloneHealthBefore),
                "The summon frame armed or damaged the annihilation pair.");

        // true damage: Resistance II must not reduce the hit
        target.addEffect(new MobEffectInstance(
                MobEffects.DAMAGE_RESISTANCE, 100, 1));
        clone.tick();
        helper.assertTrue(
                clone.lifeTicks() == 1
                        && clone.annihilationTicks() == 0,
                "The intersecting clone did not arm on its first "
                        + "eligible server tick.");
        helper.assertTrue(
                close(target.getHealth(), targetHealthBefore),
                "The contact tick damaged the target; 1.16 only "
                        + "arms the annihilation there.");
        helper.assertTrue(
                close(clone.getHealth(), cloneHealthBefore),
                "The clone damaged itself on first contact; "
                        + "1.16 rejected that self-damage.");
        helper.assertTrue(
                target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                        && clone.hasEffect(
                                MobEffects.MOVEMENT_SLOWDOWN),
                "The freshly armed pair did not receive the "
                        + "first-contact lock effect.");
        for (int i = 1; i < 20; i++) {
            clone.tick();
        }
        helper.assertTrue(
                clone.annihilationTicks() == 19
                        && close(target.getHealth(), targetHealthBefore),
                "The target was hit before annihilation tick 20: "
                        + "annihilation=" + clone.annihilationTicks()
                        + ", health=" + target.getHealth() + ".");
        clone.tick();
        helper.assertTrue(
                clone.annihilationTicks() == 20
                        && close(
                                target.getHealth(),
                                targetHealthBefore
                                        - target.getMaxHealth() * 0.025F),
                "The first 2.5% true-damage hit did not land on "
                        + "annihilation tick 20: health="
                        + target.getHealth() + ".");
        clone.discard();
        owner.discard();
        target.discard();
        helper.succeed();
    }

    // 1.16 ag/ab.java:125-187: only the target or lifespan ends it
    @GameTest(template = "empty", timeoutTicks = 20 + GameTestChunkTickets.WAIT_TICKS)
    public static void annihilationSurvivesOwnerLoss(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> annihilationSurvivesOwnerLossBody(h));
    }

    private static void annihilationSurvivesOwnerLossBody(GameTestHelper helper) {
        Cow owner = helper.spawnWithNoFreeWill(
                EntityType.COW, 1, 2, 1);
        Cow target = helper.spawnWithNoFreeWill(
                EntityType.COW, 2, 2, 2);
        D4CAnnihilationCloneEntity clone =
                D4CAddon.ANNIHILATION_CLONE.get()
                        .create(helper.getLevel());
        helper.assertTrue(
                clone != null,
                "The annihilation clone entity could not be created.");
        if (clone == null) {
            return;
        }

        clone.moveTo(
                target.getX(),
                target.getY(),
                target.getZ(),
                target.getYRot(),
                target.getXRot());
        clone.initialize(owner, target);
        helper.assertTrue(
                helper.getLevel().addFreshEntity(clone),
                "The clone was rejected by the server level.");
        // dead user still in the level
        owner.kill();
        clone.tick();
        helper.assertTrue(
                !clone.isRemoved() && clone.annihilationTicks() == 0,
                "A dead D4C user cancelled the annihilation.");
        // user gone (logout analogue)
        owner.discard();
        clone.tick();
        helper.assertTrue(
                !clone.isRemoved() && clone.annihilationTicks() == 1,
                "A missing D4C user cancelled the annihilation.");
        target.discard();
        clone.tick();
        helper.assertTrue(
                clone.isRemoved(),
                "Losing the target did not end the clone.");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80 + GameTestChunkTickets.WAIT_TICKS)
    public static void annihilationCloneApproachesWithinTwelveTicks(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> annihilationCloneApproachesWithinTwelveTicksBody(h));
    }

    private static void annihilationCloneApproachesWithinTwelveTicksBody(GameTestHelper helper) {
        Cow owner = helper.spawnWithNoFreeWill(
                EntityType.COW, 1, 2, 1);
        Cow target = helper.spawnWithNoFreeWill(
                EntityType.COW, 4, 2, 1);
        D4CAnnihilationCloneEntity clone =
                D4CAddon.ANNIHILATION_CLONE.get()
                        .create(helper.getLevel());
        helper.assertTrue(
                clone != null,
                "The annihilation clone entity could not be created.");
        if (clone == null) {
            return;
        }

        clone.moveTo(
                target.getX() - 2.0D,
                target.getY(),
                target.getZ(),
                target.getYRot(),
                target.getXRot());
        clone.initialize(owner, target);
        helper.assertTrue(
                helper.getLevel().addFreshEntity(clone),
                "The approaching clone was rejected by the server level.");

        int[] elapsed = {0};
        int[] activeTicks = {0};
        helper.onEachTick(() -> {
            elapsed[0]++;
            if (clone.annihilationTicks() < 0) {
                helper.assertTrue(
                        elapsed[0] <= 12,
                        "The production clone did not reach contact "
                                + "within 12 ticks.");
                return;
            }
            helper.assertTrue(
                    activeTicks[0] > 0
                            || clone.isCurrentlyColliding(),
                    "The production clone armed outside the bounded "
                            + "contact window.");
            helper.assertTrue(
                    clone.annihilationTicks() >= activeTicks[0],
                    "The donor non-player annihilation timer stalled "
                            + "after first contact.");
            activeTicks[0]++;
            if (activeTicks[0] < 45) {
                return;
            }
            clone.discard();
            owner.discard();
            target.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void annihilationCloneCannotCrossSealedWall(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> annihilationCloneCannotCrossSealedWallBody(h));
    }

    private static void annihilationCloneCannotCrossSealedWallBody(GameTestHelper helper) {
        for (int y = 1; y <= 4; y++) {
            for (int edge = 0; edge <= 2; edge++) {
                helper.getLevel().setBlockAndUpdate(
                        helper.absolutePos(new BlockPos(2, y, edge)),
                        Blocks.OBSIDIAN.defaultBlockState());
                helper.getLevel().setBlockAndUpdate(
                        helper.absolutePos(new BlockPos(4, y, edge)),
                        Blocks.OBSIDIAN.defaultBlockState());
                helper.getLevel().setBlockAndUpdate(
                        helper.absolutePos(new BlockPos(2 + edge, y, 0)),
                        Blocks.OBSIDIAN.defaultBlockState());
                helper.getLevel().setBlockAndUpdate(
                        helper.absolutePos(new BlockPos(2 + edge, y, 2)),
                        Blocks.OBSIDIAN.defaultBlockState());
            }
        }

        Cow owner = helper.spawnWithNoFreeWill(
                EntityType.COW, 1, 2, 3);
        Cow target = helper.spawnWithNoFreeWill(
                EntityType.COW, 3, 2, 1);
        D4CAnnihilationCloneEntity clone =
                D4CAddon.ANNIHILATION_CLONE.get()
                        .create(helper.getLevel());
        helper.assertTrue(
                clone != null,
                "The wall-safety clone entity could not be created.");
        if (clone == null) {
            return;
        }

        clone.moveTo(
                target.getX() - 2.0D,
                target.getY(),
                target.getZ(),
                target.getYRot(),
                target.getXRot());
        clone.initialize(owner, target);
        helper.assertTrue(
                helper.getLevel().addFreshEntity(clone),
                "The wall-safety clone was rejected by the server level.");
        double wallMinX = helper.absolutePos(
                new BlockPos(2, 2, 1)).getX();

        int[] elapsed = {0};
        helper.onEachTick(() -> {
            elapsed[0]++;
            helper.assertTrue(
                    clone.getBoundingBox().maxX <= wallMinX + EPSILON,
                    "The annihilation clone crossed a sealed block wall.");
            helper.assertTrue(
                    clone.annihilationTicks() < 0
                            && !clone.isCurrentlyColliding(),
                    "The blocked clone armed without reaching its target.");
            if (elapsed[0] < 14) {
                return;
            }
            clone.discard();
            owner.discard();
            target.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 570 + GameTestChunkTickets.WAIT_TICKS)
    public static void loveTrainActivatesThroughHeldAbilityInput(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> loveTrainActivatesThroughHeldAbilityInputBody(h));
    }

    private static void loveTrainActivatesThroughHeldAbilityInputBody(GameTestHelper helper) {
        ServerPlayer player = makeServerPlayer(
                helper, "d4c-love-train-gametest");
        StandPower power = StandPower.get(player);
        helper.assertTrue(
                power != null,
                "The mock player has no Stand power attachment.");
        if (power == null) {
            return;
        }

        power.setStand(D4CAddon.D4C.get());
        power.skipProgression();
        power.setStamina(power.getMaxStamina());
        helper.assertTrue(
                D4CAddon.D4C.get().summon(player, power),
                "D4C could not be summoned for the input-path test.");
        StandEntity stand = power.getSummonedStandEntity();
        helper.assertTrue(
                stand != null && stand.isAddedToLevel(),
                "The D4C Stand entity was not added to the server level.");
        if (stand == null) {
            return;
        }
        stand.summonLockTicks = 0;

        ItemStack corpse =
                new ItemStack(D4CAddon.COMPLETE_SAINT_CORPSE.get());
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                corpse,
                tag -> tag.putBoolean("xray_enabled", true));
        player.getInventory().setItem(0, corpse);
        helper.assertTrue(
                D4CPhase2BService.hasCorpseItem(
                        player,
                        D4CAddon.COMPLETE_SAINT_CORPSE.get()),
                "The component-bearing complete Saint Corpse failed "
                        + "D4C item admission.");

        Ability ability = power.getAbility("love_train");
        helper.assertTrue(
                ability instanceof D4CPhase2BAbility,
                "The player's moveset did not resolve the Love Train "
                        + "ability instance.");
        helper.assertTrue(
                AbilityInput.withConditionCheck(ability, player),
                "Love Train input preflight rejected a component-bearing "
                        + "complete Saint Corpse.");

        var held = AbilityInput.keyPress(
                (short) 73,
                ability,
                player,
                new FriendlyByteBuf(Unpooled.buffer()),
                InputMethod.HOLD,
                0.0F,
                BufferingState.clickCanBuffer(),
                ability.abilityId);
        helper.assertTrue(
                held != null,
                "The server AbilityInput path did not create a held "
                        + "Love Train action.");
        EntityActionInstance action =
                LivingComponentAction.getCurEntityAction(stand);
        helper.assertTrue(
                action != null
                        && action.ability == ability
                        && action.getPhase() == ActionPhase.BUTTON_CHARGE
                        && close(action.getPhaseTick(), 0.0D),
                "Love Train did not enter its production button-charge "
                        + "action at tick 0.");

        boolean[] observedFinalChargeTick = {false};
        helper.onEachTick(() -> {
            boolean loveTrainActive =
                    player.hasEffect(D4CAddon.LOVE_TRAIN);
            boolean luckyGoddessActive =
                    player.hasEffect(D4CAddon.LUCKY_GODDESS);
            boolean active =
                    loveTrainActive && luckyGoddessActive;
            if (action.getPhase() == ActionPhase.BUTTON_CHARGE) {
                helper.assertTrue(
                        !loveTrainActive && !luckyGoddessActive,
                        "Love Train committed before the 540-tick hold "
                                + "completed.");
                helper.assertTrue(
                        player.getInventory().contains(
                                stack -> stack.is(
                                        D4CAddon
                                                .COMPLETE_SAINT_CORPSE
                                                .get())),
                        "The complete corpse was consumed before action "
                                + "commit.");
                helper.assertTrue(
                        action.getPhaseTick() <= 539.0F,
                        "Love Train remained in button charge past "
                                + "tick 539.");
                if (close(action.getPhaseTick(), 539.0D)) {
                    observedFinalChargeTick[0] = true;
                }
            } else if (!observedFinalChargeTick[0]) {
                helper.fail(
                        "Love Train left button charge before observing "
                                + "all 540 action ticks.");
            }
            if (active) {
                helper.assertTrue(
                        observedFinalChargeTick[0],
                        "Love Train activated before the final "
                                + "button-charge tick.");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(
                    player.hasEffect(D4CAddon.LOVE_TRAIN)
                            && player.hasEffect(D4CAddon.LUCKY_GODDESS),
                    "Love Train did not enter active state on the first "
                            + "perform tick after the 540-tick hold.");
            helper.assertTrue(
                    !player.getInventory().contains(
                            stack -> stack.is(
                                    D4CAddon.COMPLETE_SAINT_CORPSE.get()))
                            && D4CPhase2BService
                                    .ownsLoveTrainCorpse(player),
                    "Love Train did not atomically consume and retain "
                            + "the complete corpse.");
            stand.discard();
            player.discard();
        });
    }

    // Own batch: Love Train's misfortune pool is every living entity within
    // 20 blocks of the target, so in the shared default batch the neighbouring
    // tests' cows and players are candidates too and the direct attacker is
    // punished only by chance. Batches run one after another with the previous
    // batch's structures cleared, which makes the attacker the only candidate.
    @GameTest(
            template = "empty",
            timeoutTicks = 20 + GameTestChunkTickets.WAIT_TICKS,
            batch = "loveTrainMisfortune")
    public static void loveTrainCancelsServerDamageEffectsAndKnockback(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> loveTrainCancelsServerDamageEffectsAndKnockbackBody(h));
    }

    private static void loveTrainCancelsServerDamageEffectsAndKnockbackBody(GameTestHelper helper) {
        Cow target = helper.spawnWithNoFreeWill(
                EntityType.COW, 2, 2, 2);
        Cow attacker = helper.spawnWithNoFreeWill(
                EntityType.COW, 3, 2, 2);
        boolean loveTrainApplied = target.addEffect(
                new MobEffectInstance(
                        D4CAddon.LOVE_TRAIN,
                        100,
                        0,
                        false,
                        false,
                        true));
        helper.assertTrue(
                loveTrainApplied
                        && target.hasEffect(D4CAddon.LOVE_TRAIN),
                "Love Train could not be applied to the server target.");

        float healthBefore = target.getHealth();
        float attackerHealthBefore = attacker.getHealth();
        boolean hurt = target.hurt(
                helper.getLevel().damageSources().mobAttack(attacker),
                5.0F);
        helper.assertTrue(
                !hurt && close(target.getHealth(), healthBefore),
                "Love Train did not cancel incoming server damage.");
        helper.assertTrue(
                close(
                        attacker.getHealth(),
                        attackerHealthBefore
                                - attacker.getMaxHealth() * 0.2F),
                "Love Train did not allow the direct attacker as the "
                        + "legacy misfortune candidate.");

        boolean poisonApplied = target.addEffect(
                new MobEffectInstance(MobEffects.POISON, 100));
        helper.assertTrue(
                !poisonApplied && !target.hasEffect(MobEffects.POISON),
                "Love Train did not reject a harmful server effect.");

        Vec3 movementBefore = target.getDeltaMovement();
        target.knockback(1.0D, 1.0D, 0.0D);
        helper.assertTrue(
                target.getDeltaMovement().distanceToSqr(movementBefore)
                        <= EPSILON * EPSILON,
                "Love Train did not cancel server knockback.");

        target.discard();
        attacker.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20 + GameTestChunkTickets.WAIT_TICKS)
    public static void loveTrainLightWallRestoresDonorServerSemantics(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> loveTrainLightWallRestoresDonorServerSemanticsBody(h));
    }

    private static void loveTrainLightWallRestoresDonorServerSemanticsBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = makeServerPlayer(
                helper, "d4c_light_wall_owner");
        player.setYRot(0.0F);
        player.addEffect(new MobEffectInstance(
                D4CAddon.LOVE_TRAIN,
                100,
                0,
                false,
                false,
                true));
        player.addEffect(new MobEffectInstance(
                D4CAddon.LOVE_TRAIN_LIGHT_WALL,
                100,
                0,
                false,
                false,
                true));

        Cow hostileOwner = helper.spawnWithNoFreeWill(
                EntityType.COW, 2, 2, 2);
        hostileOwner.setPos(
                player.getX() + 8.0D,
                player.getY(),
                player.getZ() + 8.0D);
        Cow punishmentTarget = helper.spawnWithNoFreeWill(
                EntityType.COW, 3, 2, 2);
        punishmentTarget.setPos(
                player.getX() + 5.0D,
                player.getY(),
                player.getZ() + 5.0D);
        Cow wallTarget = helper.spawnWithNoFreeWill(
                EntityType.COW, 4, 2, 2);
        wallTarget.setPos(
                player.getX(),
                player.getY(),
                player.getZ() + 8.0D);

        Snowball hostileProjectile =
                new Snowball(level, hostileOwner);
        hostileProjectile.moveTo(
                player.getX(),
                player.getY() + 1.0D,
                player.getZ() + 10.0D,
                0.0F,
                0.0F);
        Snowball selfProjectile = new Snowball(level, player);
        selfProjectile.moveTo(
                player.getX(),
                player.getY() + 1.0D,
                player.getZ() + 12.0D,
                0.0F,
                0.0F);
        helper.assertTrue(
                level.addFreshEntity(hostileProjectile)
                        && level.addFreshEntity(selfProjectile),
                "Light Wall test projectiles could not be added.");

        float punishmentHealth = punishmentTarget.getHealth();
        D4CCorpseService.tickPlayer(player);

        helper.assertTrue(
                hostileProjectile.isRemoved(),
                "Light Wall did not discard a non-owner projectile.");
        helper.assertTrue(
                !selfProjectile.isRemoved(),
                "Light Wall discarded its owner's projectile.");
        helper.assertTrue(
                close(
                        punishmentTarget.getHealth(),
                        punishmentHealth
                                - D4CPhase2BSpec
                                        .LIGHT_WALL_PUNISH_DAMAGE),
                "Light Wall interception did not apply donor magic damage.");
        helper.assertTrue(
                punishmentTarget.getDeltaMovement().lengthSqr()
                        > EPSILON * EPSILON,
                "Light Wall interception did not knock back "
                        + "nearby non-player living entities.");
        helper.assertTrue(
                Math.abs(wallTarget.getX() - player.getX()) >= 1.9D
                        && wallTarget.hasEffect(
                                MobEffects.MOVEMENT_SLOWDOWN),
                "Light Wall did not force a living entity off the wall "
                        + "and apply donor slowness.");

        hostileOwner.discard();
        punishmentTarget.discard();
        wallTarget.discard();
        selfProjectile.discard();
        player.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20 + GameTestChunkTickets.WAIT_TICKS)
    public static void deathDropAndStandlessInventoryProvenance(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> deathDropAndStandlessInventoryProvenanceBody(h));
    }

    private static void deathDropAndStandlessInventoryProvenanceBody(GameTestHelper helper) {
        D4CParallelItemService.clearRuntimeState();
        Cow pulled = helper.spawnWithNoFreeWill(
                EntityType.COW, 2, 2, 2);
        D4CParallelItemService.markPulledByD4C(pulled);
        Vec3 deathPosition = pulled.position();
        boolean killed = pulled.hurt(
                helper.getLevel().damageSources().generic(),
                Float.MAX_VALUE);
        helper.assertTrue(
                killed && !pulled.isAlive(),
                "The pulled entity did not enter the death event path.");

        ItemStack nearStack = new ItemStack(Items.DIAMOND);
        ItemEntity nearDrop = new ItemEntity(
                helper.getLevel(),
                deathPosition.x + 5.0D,
                deathPosition.y,
                deathPosition.z,
                nearStack);
        ItemEntity farDrop = new ItemEntity(
                helper.getLevel(),
                deathPosition.x + 5.01D,
                deathPosition.y,
                deathPosition.z,
                new ItemStack(Items.DIAMOND));
        helper.assertTrue(
                D4CParallelItemService.isProtectedDeathDrop(
                        helper.getLevel(), nearDrop),
                "A nearby pulled-entity death drop lost provenance "
                        + "protection.");
        helper.assertTrue(
                !D4CParallelItemService.isProtectedDeathDrop(
                        helper.getLevel(), farDrop),
                "Death-drop provenance protection exceeded the legacy "
                        + "five-block axis range.");
        helper.assertTrue(
                !D4CParallelItemService.isPlayerTossed(nearStack),
                "Death-drop protection wrote PlayerTossed to ItemStack.");

        ServerPlayer standless =
                makeDetachedServerPlayer(helper, "d4c-standless");
        ItemStack nativeStack = new ItemStack(Items.DIAMOND);
        ItemStack parallelStack = new ItemStack(Items.DIAMOND);
        D4CParallelItemService.markParallel(parallelStack);
        standless.getInventory().setItem(0, nativeStack);
        standless.getInventory().setItem(1, parallelStack);
        standless.tickCount = 20;
        D4CParallelItemService.tickPlayer(standless);
        helper.assertTrue(
                !standless.getInventory().getItem(0).isEmpty()
                        && !standless.getInventory().getItem(1).isEmpty(),
                "A standless player was scanned for inventory "
                        + "annihilation.");

        D4CParallelItemService.clearRuntimeState();
        helper.assertTrue(
                !D4CParallelItemService.isProtectedDeathDrop(
                        helper.getLevel(), nearDrop),
                "Death-drop provenance survived explicit runtime cleanup.");

        nearDrop.discard();
        farDrop.discard();
        pulled.discard();
        standless.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void parallelItemOriginSurvivesEntityAndStackRoundTrips(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> parallelItemOriginSurvivesEntityAndStackRoundTripsBody(h));
    }

    private static void parallelItemOriginSurvivesEntityAndStackRoundTripsBody(GameTestHelper helper) {
        D4CParallelItemService.clearRuntimeState();
        ServerLevel level = helper.getLevel();
        Vec3 origin = Vec3.atCenterOf(
                helper.absolutePos(new BlockPos(2, 10, 2)));
        ItemEntity generated = new ItemEntity(
                level,
                origin.x,
                origin.y,
                origin.z,
                new ItemStack(Items.DIAMOND, 12));
        generated.getEntityData().packDirty();
        D4CParallelItemService.markParallelOrigin(generated);
        helper.assertTrue(
                D4CParallelItemService.isParallel(generated.getItem())
                        && generated.getEntityData().isDirty(),
                "Parallel source admission did not write and synchronize "
                        + "the provenance component.");
        helper.assertTrue(
                level.addFreshEntity(generated),
                "The parallel-tagged ItemEntity could not join its level.");

        CompoundTag savedStack = (CompoundTag) generated.getItem().save(
                level.registryAccess());
        ItemStack restoredStack = ItemStack.parseOptional(
                level.registryAccess(), savedStack);
        ItemStack splitStack = restoredStack.split(5);
        helper.assertTrue(
                restoredStack.getCount() == 7
                        && splitStack.getCount() == 5
                        && D4CParallelItemService.isParallel(restoredStack)
                        && D4CParallelItemService.isParallel(splitStack),
                "Parallel provenance did not survive stack save/split "
                        + "round trips.");

        CompoundTag savedEntity = new CompoundTag();
        helper.assertTrue(
                generated.save(savedEntity),
                "The tagged ItemEntity refused its normal save path.");
        generated.discard();
        Entity restoredEntity = EntityType.loadEntityRecursive(
                savedEntity,
                helper.getLevel(),
                entity -> entity);
        helper.assertTrue(
                restoredEntity instanceof ItemEntity,
                "The tagged ItemEntity did not load from its saved data.");
        if (!(restoredEntity instanceof ItemEntity restoredItem)) {
            return;
        }
        restoredItem.setUUID(UUID.randomUUID());
        restoredItem.setPos(Vec3.atCenterOf(
                helper.absolutePos(new BlockPos(2, 8, 2))));
        helper.assertTrue(
                helper.getLevel().addFreshEntity(restoredItem)
                        && D4CParallelItemService.isParallel(
                                restoredItem.getItem()),
                "Parallel provenance did not survive entity reload and "
                        + "level re-admission.");

        ItemEntity tossed = new ItemEntity(
                level,
                origin.x + 2.0D,
                origin.y,
                origin.z,
                new ItemStack(Items.EMERALD, 3));
        D4CParallelItemService.markPlayerTossed(tossed);
        helper.assertTrue(
                level.addFreshEntity(tossed)
                        && !D4CParallelItemService.isParallel(
                                tossed.getItem()),
                "A player-tossed native stack gained parallel provenance.");
        CompoundTag tossedData = new CompoundTag();
        helper.assertTrue(
                tossed.save(tossedData),
                "The player-tossed ItemEntity refused its save path.");
        tossed.discard();
        Entity restoredTossedEntity = EntityType.loadEntityRecursive(
                tossedData, level, entity -> entity);
        helper.assertTrue(
                restoredTossedEntity instanceof ItemEntity,
                "The player-tossed ItemEntity did not reload.");
        if (!(restoredTossedEntity instanceof ItemEntity restoredTossed)) {
            return;
        }
        restoredTossed.setUUID(UUID.randomUUID());
        restoredTossed.setPos(
                origin.x + 3.0D,
                origin.y,
                origin.z);
        helper.assertTrue(
                level.addFreshEntity(restoredTossed)
                        && restoredTossed.getPersistentData().getBoolean(
                                D4CParallelItemService.PLAYER_TOSSED_TAG)
                        && !D4CParallelItemService.isParallel(
                                restoredTossed.getItem()),
                "Player-toss provenance did not survive entity reload.");

        restoredItem.discard();
        restoredTossed.discard();
        D4CParallelItemService.clearRuntimeState();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void parallelItemCounterpartCollisionMatchesDonorScope(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> parallelItemCounterpartCollisionMatchesDonorScopeBody(h));
    }

    private static void parallelItemCounterpartCollisionMatchesDonorScopeBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 center = Vec3.atCenterOf(
                helper.absolutePos(new BlockPos(2, 10, 2)));

        ItemStack parallelDiamonds = new ItemStack(Items.DIAMOND, 5);
        D4CParallelItemService.markParallel(parallelDiamonds);
        ItemEntity parallelItem = spawnItem(
                helper, level, center, parallelDiamonds);
        ItemEntity nativeItem = spawnItem(
                helper,
                level,
                center.add(0.1D, 0.0D, 0.0D),
                new ItemStack(Items.DIAMOND, 7));
        D4CParallelItemService.tickItems(level);
        helper.assertTrue(
                !parallelItem.isAlive() && !nativeItem.isAlive(),
                "Opposite-origin identical item stacks did not annihilate "
                        + "on donor contact.");

        ItemEntity nativeA = spawnItem(
                helper, level, center, new ItemStack(Items.DIAMOND));
        ItemEntity nativeB = spawnItem(
                helper, level, center, new ItemStack(Items.DIAMOND));
        D4CParallelItemService.tickItems(level);
        helper.assertTrue(
                nativeA.isAlive() && nativeB.isAlive(),
                "Same-origin item stacks falsely annihilated.");
        nativeA.discard();
        nativeB.discard();

        ItemStack parallelEmerald = new ItemStack(Items.EMERALD);
        D4CParallelItemService.markParallel(parallelEmerald);
        ItemEntity foreignParallel = spawnItem(
                helper, level, center, parallelEmerald);
        ItemEntity foreignNative = spawnItem(
                helper, level, center, new ItemStack(Items.DIAMOND));
        D4CParallelItemService.tickItems(level);
        helper.assertTrue(
                foreignParallel.isAlive() && foreignNative.isAlive(),
                "Different item types falsely annihilated.");
        foreignParallel.discard();
        foreignNative.discard();

        ItemStack distantParallelStack = new ItemStack(Items.DIAMOND);
        D4CParallelItemService.markParallel(distantParallelStack);
        ItemEntity distantParallel = spawnItem(
                helper, level, center, distantParallelStack);
        ItemEntity distantNative = spawnItem(
                helper,
                level,
                center.add(5.01D, 0.0D, 0.0D),
                new ItemStack(Items.DIAMOND));
        D4CParallelItemService.tickItems(level);
        helper.assertTrue(
                distantParallel.isAlive()
                        && distantNative.isAlive()
                        && distantParallel.getDeltaMovement()
                                .lengthSqr() == 0.0D
                        && distantNative.getDeltaMovement()
                                .lengthSqr() == 0.0D,
                "Out-of-range counterparts attracted or annihilated.");
        distantParallel.discard();
        distantNative.discard();

        BlockPos matchingBlock = helper.absolutePos(
                new BlockPos(8, 10, 8));
        level.setBlockAndUpdate(
                matchingBlock, Blocks.DIRT.defaultBlockState());
        ItemStack parallelDirtStack = new ItemStack(Items.DIRT, 4);
        D4CParallelItemService.markParallel(parallelDirtStack);
        ItemEntity parallelDirt = spawnItem(
                helper,
                level,
                Vec3.atCenterOf(matchingBlock.above()),
                parallelDirtStack);
        D4CParallelItemService.tickItems(level);
        helper.assertTrue(
                parallelDirt.isAlive()
                        && level.getBlockState(matchingBlock)
                                .is(Blocks.DIRT),
                "An untossed parallel stack falsely triggered a block "
                        + "counterpart.");
        D4CParallelItemService.markPlayerTossed(parallelDirt);
        D4CParallelItemService.tickItems(level);
        helper.assertTrue(
                !parallelDirt.isAlive(),
                "A player-tossed parallel stack did not trigger its "
                        + "real-world block counterpart.");

        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void parallelItemInventoryCollisionMatchesDonorScope(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> parallelItemInventoryCollisionMatchesDonorScopeBody(h));
    }

    private static void parallelItemInventoryCollisionMatchesDonorScopeBody(GameTestHelper helper) {
        ServerPlayer player = makeServerPlayer(
                helper, "d4c-parallel-inventory");
        player.moveTo(Vec3.atCenterOf(
                helper.absolutePos(new BlockPos(2, 10, 2))));
        StandPower power = StandPower.get(player);
        helper.assertTrue(
                power != null,
                "The inventory collision player has no Stand attachment.");
        if (power == null) {
            return;
        }
        power.setStand(D4CAddon.D4C.get());
        ItemStack d4cNative = new ItemStack(Items.EMERALD, 2);
        ItemStack d4cParallel = new ItemStack(Items.EMERALD, 3);
        D4CParallelItemService.markParallel(d4cParallel);
        player.getInventory().setItem(0, d4cNative);
        player.getInventory().setItem(1, d4cParallel);
        player.tickCount = 20;
        D4CParallelItemService.tickPlayer(player);
        helper.assertTrue(
                !player.getInventory().getItem(0).isEmpty()
                        && !player.getInventory().getItem(1).isEmpty(),
                "The donor-exempt D4C user was scanned for inventory "
                        + "annihilation.");
        player.getInventory().setItem(0, ItemStack.EMPTY);
        player.getInventory().setItem(1, ItemStack.EMPTY);

        power.setStand(ModStands.STAR_PLATINUM.get());
        ItemStack nativeStack = new ItemStack(Items.DIAMOND, 4);
        ItemStack parallelStack = new ItemStack(Items.DIAMOND, 6);
        D4CParallelItemService.markParallel(parallelStack);
        int offhandSlot = player.getInventory().getContainerSize() - 1;
        player.getInventory().setItem(0, nativeStack);
        player.getInventory().setItem(offhandSlot, parallelStack);
        player.tickCount = 20;
        D4CParallelItemService.tickPlayer(player);
        helper.assertTrue(
                player.getInventory().getItem(0).isEmpty()
                        && player.getInventory().getItem(offhandSlot)
                                .isEmpty(),
                "A non-D4C Stand user's held/offhand counterparts did "
                        + "not annihilate as complete stacks.");

        player.discard();
        helper.succeed();
    }

    @GameTest(
            template = "empty",
            timeoutTicks = ENTITY_TICK_START_TIMEOUT_TICKS + GameTestChunkTickets.WAIT_TICKS)
    public static void flagDiscardsAtExactAnimationDuration(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> flagDiscardsAtExactAnimationDurationBody(h));
    }

    private static void flagDiscardsAtExactAnimationDurationBody(GameTestHelper helper) {
        ServerPlayer owner =
                makeDetachedServerPlayer(helper, "d4c-flag-owner");
        Cow anchor = helper.spawnWithNoFreeWill(
                EntityType.COW, 2, 2, 2);
        D4CMarkerEntity shortFlag =
                D4CAddon.FLAG.get().create(helper.getLevel());
        D4CMarkerEntity longFlag =
                D4CAddon.FLAG.get().create(helper.getLevel());
        helper.assertTrue(
                shortFlag != null && longFlag != null,
                "Flag entities could not be created.");
        if (shortFlag == null || longFlag == null) {
            return;
        }
        shortFlag.initialize(
                owner, anchor, D4CMarkerEntity.Mode.KICK_ORIGIN);
        longFlag.initialize(
                owner, anchor, D4CMarkerEntity.Mode.KICK_FLAG);
        boolean[] observedFinalActiveAge = {false, false};
        boolean[] flagsAdded = {false};
        helper.onEachTick(() -> {
            if (!flagsAdded[0]) {
                if (!helper.getLevel().isPositionEntityTicking(
                        shortFlag.blockPosition())
                        || !helper.getLevel().isPositionEntityTicking(
                                longFlag.blockPosition())) {
                    return;
                }
                helper.assertTrue(
                        helper.getLevel().addFreshEntity(shortFlag)
                                && helper.getLevel().addFreshEntity(longFlag),
                        "Flag entities were rejected by the server level.");
                flagsAdded[0] = true;
                return;
            }
            observeFlagLifetime(
                    helper,
                    shortFlag,
                    observedFinalActiveAge,
                    0,
                    "Mode-0");
            observeFlagLifetime(
                    helper,
                    longFlag,
                    observedFinalActiveAge,
                    1,
                    "Mode-1");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(
                    shortFlag.isRemoved() && longFlag.isRemoved(),
                    "The flag entities have not completed both "
                            + "animation lifetimes.");
            helper.assertTrue(
                    observedFinalActiveAge[0]
                            && observedFinalActiveAge[1]
                            && shortFlag.ageTicks()
                                    == shortFlag.animationDurationTicks()
                            && longFlag.ageTicks()
                                    == longFlag.animationDurationTicks(),
                    "The flag entities did not remain active through "
                            + "their final animation ages before discard.");
            owner.discard();
            anchor.discard();
        });
    }

    private static void observeFlagLifetime(
            GameTestHelper helper,
            D4CMarkerEntity flag,
            boolean[] observedFinalActiveAge,
            int index,
            String label) {
        int age = flag.ageTicks();
        int duration = flag.animationDurationTicks();
        if (!flag.isRemoved()) {
            helper.assertTrue(
                    age < duration,
                    label + " flag remained active at age " + age + ".");
            if (age == duration - 1) {
                observedFinalActiveAge[index] = true;
            }
            return;
        }
        helper.assertTrue(
                age == duration,
                label + " flag discarded at age " + age
                        + " instead of " + duration + ".");
    }

    // 1.16 aa/aj.java:119-121: the portal is written at the target's block
    // position whatever stands there
    @GameTest(
            template = "empty",
            timeoutTicks = 120 + GameTestChunkTickets.WAIT_TICKS,
            batch = "pullInFeetBlock")
    public static void pullInPlacesPortalOverNonReplaceableFeetBlock(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> pullInPlacesPortalOverNonReplaceableFeetBlockBody(h));
    }

    private static void pullInPlacesPortalOverNonReplaceableFeetBlockBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(
                level.getServer().getLevel(Level.NETHER) != null,
                "The GameTest server has no Nether level to pull into.");
        // high above the grid: neighbouring tests scan 20 blocks for mobs
        BlockPos origin = helper.absolutePos(new BlockPos(1, 60, 1));
        buildPullStage(level, origin);
        BlockPos feet = origin.offset(1, 1, 3);
        level.setBlockAndUpdate(
                feet, Blocks.WHITE_CARPET.defaultBlockState());
        String tag = "rotp_d4c_pull_feet_gametest";
        Cow target = spawnPullTarget(helper, level, feet, 0.0625D, tag);
        helper.assertTrue(
                target.blockPosition().equals(feet)
                        && !level.getBlockState(feet).canBeReplaced(),
                "The pull-in fixture is not standing in a "
                        + "non-replaceable block.");
        ServerPlayer owner =
                makePullOwner(level, "d4c-pull-feet", origin);
        StandPower power = startPullIn(
                helper, owner, D4CDimensionTargets.NETHER);

        boolean[] markerSeen = {false};
        helper.onEachTick(() -> markerSeen[0] |=
                pullMarkerPresent(level, target));
        helper.succeedWhen(() -> {
            helper.assertTrue(
                    markerSeen[0],
                    "Pull-in has not committed: no pull marker follows "
                            + "the target yet.");
            BlockState state = level.getBlockState(feet);
            boolean portalAtFeet =
                    state.getBlock() instanceof D4CPortalBlock portal
                            && portal.targetDimension().equals(
                                    D4CDimensionTargets.NETHER);
            helper.assertTrue(
                    portalAtFeet || target.isRemoved(),
                    "Pull-in left the block at the target's feet alone: "
                            + "feetBlock=" + state.getBlock()
                            + ", targetRemoved=" + target.isRemoved()
                            + "; 1.16 wrote the selected portal there and "
                            + "sent the target through.");
            endPullIn(level, origin, owner, power, tag);
        });
    }

    // Owner decision D-rotp-d4c-06: a pulled mob with the Overworld
    // selected inside the Overworld is sent to the parallel world, as a
    // player is. Runs the real pull; on a server without that level the
    // portal is refused and the mob is left where it stands
    @GameTest(
            template = "empty",
            timeoutTicks = 200 + GameTestChunkTickets.WAIT_TICKS,
            batch = "pullInOverworldMobParallel")
    public static void pullInOnMobWithOverworldSelectedAsksForParallelWorld(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> pullInOnMobWithOverworldSelectedAsksForParallelWorldBody(h));
    }

    private static void pullInOnMobWithOverworldSelectedAsksForParallelWorldBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(
                level.dimension() == Level.OVERWORLD,
                "The GameTest level is not the Overworld.");
        ServerLevel parallel = level.getServer().getLevel(
                D4CDimensionTargets.levelKey(
                        D4CDimensionTargets.PARALLEL_WORLD));
        BlockPos origin = helper.absolutePos(new BlockPos(1, 60, 1));
        buildPullStage(level, origin);
        BlockPos feet = origin.offset(1, 1, 3);
        String tag = "rotp_d4c_pull_overworld_parallel_gametest";
        Cow target = spawnPullTarget(helper, level, feet, 0.0D, tag);
        ServerPlayer owner =
                makePullOwner(level, "d4c-pull-overworld-mob", origin);
        StandPower power = startPullIn(
                helper, owner, D4CDimensionTargets.OVERWORLD);
        ResourceLocation asked =
                D4CDimensionTravelService.pulledDestination(
                        level.dimension().location(),
                        ResourceLocation.parse(
                                owner.getPersistentData().getString(
                                        SELECTED_DIMENSION_KEY)),
                        false);
        helper.assertTrue(
                D4CDimensionTargets.PARALLEL_WORLD.equals(asked),
                "Pulling a mob with the Overworld selected inside the "
                        + "Overworld asks for " + asked + ", expected "
                        + D4CDimensionTargets.PARALLEL_WORLD + ".");

        boolean[] markerSeen = {false};
        boolean[] portalSeen = {false};
        helper.onEachTick(() -> {
            markerSeen[0] |= pullMarkerPresent(level, target);
            if (level.getBlockState(feet).getBlock()
                    instanceof D4CPortalBlock) {
                portalSeen[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(
                    markerSeen[0],
                    "Pull-in has not committed: no pull marker follows "
                            + "the target yet.");
            helper.assertTrue(
                    !pullMarkerPresent(level, target),
                    "The pull marker has not finished its attempts yet.");
            if (parallel == null) {
                helper.assertTrue(
                        !portalSeen[0]
                                && !(level.getBlockState(feet).getBlock()
                                        instanceof D4CPortalBlock),
                        "A portal was placed for a pulled mob although "
                                + "the server has no parallel world: "
                                + "feetBlock="
                                + level.getBlockState(feet).getBlock()
                                + ".");
                helper.assertTrue(
                        !target.isRemoved()
                                && target.level() == level
                                && target.blockPosition().equals(feet),
                        "The pulled mob left the stage without a "
                                + "parallel world to go to: removed="
                                + target.isRemoved() + ", at "
                                + target.blockPosition() + ", expected "
                                + feet + ".");
            } else {
                boolean arrived = !parallel.getEntities(
                                EntityType.COW,
                                cow -> cow.getTags().contains(tag))
                        .isEmpty();
                helper.assertTrue(
                        arrived,
                        "The pulled mob did not arrive in the parallel "
                                + "world: removed=" + target.isRemoved()
                                + ", at " + target.blockPosition()
                                + ", feetBlock="
                                + level.getBlockState(feet).getBlock()
                                + ".");
                parallel.getEntities(
                                EntityType.COW,
                                cow -> cow.getTags().contains(tag))
                        .forEach(Entity::discard);
            }
            endPullIn(level, origin, owner, power, tag);
        });
    }

    // 1.16 ab/ae.java:137-149 (and ab/af, ab/ac): a portal used inside its
    // own dimension still sends the entity somewhere
    @GameTest(
            template = "empty",
            timeoutTicks = 600 + GameTestChunkTickets.WAIT_TICKS,
            batch = "pullInSameDimension")
    public static void pullInWithCurrentDimensionSelectedStillTeleports(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> pullInWithCurrentDimensionSelectedStillTeleportsBody(h));
    }

    private static void pullInWithCurrentDimensionSelectedStillTeleportsBody(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();
        ServerLevel nether =
                overworld.getServer().getLevel(Level.NETHER);
        helper.assertTrue(
                nether != null,
                "The GameTest server has no Nether level to pull from.");
        if (nether == null) {
            return;
        }
        BlockPos anchor = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos origin =
                new BlockPos(anchor.getX(), 200, anchor.getZ());
        BlockPos feet = origin.offset(1, 1, 3);
        String tag = "rotp_d4c_pull_same_dimension_gametest";
        // the test server does not wait between ticks, so the Nether
        // around the stage is generated here rather than awaited
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                nether.getChunk(chunkX + x, chunkZ + z);
            }
        }
        GameTestChunkTickets netherTickets = new GameTestChunkTickets(helper, nether).coverBox(
                origin.getX(), origin.getZ(), origin.getX() + 2, origin.getZ() + 4);
        ServerPlayer owner =
                makePullOwner(nether, "d4c-pull-nether", origin);

        Cow[] target = {null};
        StandPower[] power = {null};
        boolean[] markerSeen = {false};
        // A wait that runs out fails the test; nothing of it may stay in the Nether for the batches after it.
        netherTickets.onEachTickWhenTicking(() -> {
            if (power[0] != null) {
                endPullIn(nether, origin, owner, power[0], tag);
                return;
            }
            for (ServerLevel loaded : overworld.getServer().getAllLevels()) {
                loaded.getEntities(EntityType.COW, cow -> cow.getTags().contains(tag)).forEach(Entity::discard);
            }
            owner.discard();
        }, () -> {
            if (target[0] != null) {
                markerSeen[0] |= pullMarkerPresent(nether, target[0]);
                return;
            }
            if (!nether.isPositionEntityTicking(origin)
                    || !nether.isPositionEntityTicking(feet)) {
                return;
            }
            buildPullStage(nether, origin);
            target[0] = spawnPullTarget(helper, nether, feet, 0.0D, tag);
            power[0] = startPullIn(
                    helper, owner, D4CDimensionTargets.NETHER);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(
                    target[0] != null,
                    "The Nether stage is not entity ticking yet.");
            helper.assertTrue(
                    markerSeen[0],
                    "Pull-in has not committed in the Nether: no pull "
                            + "marker follows the target yet.");
            boolean arrived = !overworld.getEntities(
                            EntityType.COW,
                            cow -> cow.getTags().contains(tag))
                    .isEmpty();
            helper.assertTrue(
                    arrived,
                    "Pull-in with the Nether selected inside the Nether "
                            + "sent the target nowhere: targetStillInNether="
                            + !target[0].isRemoved()
                            + ", feetBlock="
                            + nether.getBlockState(feet).getBlock()
                            + "; the 1.16 nether portal used inside the "
                            + "Nether leads to the Overworld.");
            overworld.getEntities(
                            EntityType.COW,
                            cow -> cow.getTags().contains(tag))
                    .forEach(Entity::discard);
            netherTickets.release();
            endPullIn(nether, origin, owner, power[0], tag);
        });
    }

    // 1.16 ab/af.java:140-152 (overworld portal), ab/ae.java:137-149
    // (nether), ab/ac.java:139-149 (end), ab/ag.java:253-265 (parallel).
    // The Overworld-in-Overworld row is the owner's decision D-rotp-d4c-06
    // for non-players, not the donor's ab/af.java:215-239; see
    // OwnerDecisionD4CPullInGameTests
    @GameTest(template = "empty", timeoutTicks = 20 + GameTestChunkTickets.WAIT_TICKS)
    public static void pullInDestinationFollowsDonorPortalTable(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> pullInDestinationFollowsDonorPortalTableBody(h));
    }

    private static void pullInDestinationFollowsDonorPortalTableBody(GameTestHelper helper) {
        ResourceLocation overworld = D4CDimensionTargets.OVERWORLD;
        ResourceLocation nether = D4CDimensionTargets.NETHER;
        ResourceLocation end = D4CDimensionTargets.END;
        ResourceLocation parallel = D4CDimensionTargets.PARALLEL_WORLD;
        ResourceLocation atum = D4CDimensionTargets.LEGACY_ORDER.get(4);
        for (boolean player : new boolean[] {true, false}) {
            assertPulledDestination(
                    helper, overworld, overworld, player, parallel);
            assertPulledDestination(
                    helper, nether, nether, player, overworld);
            assertPulledDestination(helper, end, end, player, overworld);
            assertPulledDestination(
                    helper, parallel, parallel, player, overworld);
            assertPulledDestination(helper, atum, atum, player, overworld);
            assertPulledDestination(
                    helper, overworld, parallel, player, parallel);
            assertPulledDestination(
                    helper, overworld, nether, player, nether);
            assertPulledDestination(
                    helper, nether, overworld, player, overworld);
            assertPulledDestination(
                    helper, nether, parallel, player, parallel);
            assertPulledDestination(helper, end, nether, player, nether);
        }
        helper.succeed();
    }

    private static void assertPulledDestination(
            GameTestHelper helper,
            ResourceLocation current,
            ResourceLocation selected,
            boolean playerTarget,
            ResourceLocation expected) {
        ResourceLocation actual =
                D4CDimensionTravelService.pulledDestination(
                        current, selected, playerTarget);
        helper.assertTrue(
                expected.equals(actual),
                "Pull-in of a " + (playerTarget ? "player" : "non-player")
                        + " with " + selected + " selected inside "
                        + current + " leads to " + actual
                        + ", expected " + expected + ".");
    }

    private static final String SELECTED_DIMENSION_KEY =
            "rotp_d4c.phase2a.selected_dimension";

    private static void buildPullStage(ServerLevel level, BlockPos origin) {
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 4; z++) {
                level.setBlockAndUpdate(
                        origin.offset(x, 0, z),
                        Blocks.STONE.defaultBlockState());
                for (int y = 1; y <= 3; y++) {
                    level.setBlockAndUpdate(
                            origin.offset(x, y, z),
                            Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static Cow spawnPullTarget(
            GameTestHelper helper,
            ServerLevel level,
            BlockPos feet,
            double yOffset,
            String tag) {
        Cow target = EntityType.COW.create(level);
        helper.assertTrue(
                target != null,
                "The pull-in target could not be created.");
        target.moveTo(
                feet.getX() + 0.5D,
                feet.getY() + yOffset,
                feet.getZ() + 0.5D,
                0.0F,
                0.0F);
        target.removeFreeWill();
        target.addTag(tag);
        helper.assertTrue(
                level.addFreshEntity(target),
                "The pull-in target was rejected by its level.");
        return target;
    }

    // Stands at the near end of the stage, looking at the target two
    // blocks ahead (+Z).
    private static ServerPlayer makePullOwner(
            ServerLevel level, String name, BlockPos origin) {
        MinecraftServer server = level.getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        ServerPlayer player = new ServerPlayer(
                server,
                level,
                profile,
                ClientInformation.createDefault());
        Connection connection =
                new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new SilentServerPacketListener(
                server,
                connection,
                player,
                CommonListenerCookie.createInitial(profile, false));
        player.moveTo(
                origin.getX() + 1.5D,
                origin.getY() + 1.0D,
                origin.getZ() + 1.5D,
                0.0F,
                25.0F);
        level.addNewPlayer(player);
        return player;
    }

    private static StandPower startPullIn(
            GameTestHelper helper,
            ServerPlayer owner,
            ResourceLocation selectedDimension) {
        owner.getPersistentData().putString(
                SELECTED_DIMENSION_KEY, selectedDimension.toString());
        StandPower power = StandPower.get(owner);
        helper.assertTrue(
                power != null,
                "The pull-in owner has no Stand power attachment.");
        power.setStand(D4CAddon.D4C.get());
        power.skipProgression();
        power.setStamina(power.getMaxStamina());
        helper.assertTrue(
                D4CAddon.D4C.get().summon(owner, power),
                "D4C could not be summoned for the pull-in test.");
        StandEntity stand = power.getSummonedStandEntity();
        helper.assertTrue(
                stand != null && stand.isAddedToLevel(),
                "The pull-in D4C Stand entity was not added to its level.");
        stand.summonLockTicks = 0;
        Ability ability =
                power.getAbility("pullin_parallel_world_travel");
        helper.assertTrue(
                ability instanceof D4CPhase2BAbility,
                "The moveset did not resolve the pull-in ability.");
        helper.assertTrue(
                AbilityInput.withConditionCheck(ability, owner),
                "Pull-in input preflight rejected the target two blocks "
                        + "ahead of the user.");
        var held = AbilityInput.keyPress(
                (short) 74,
                ability,
                owner,
                new FriendlyByteBuf(Unpooled.buffer()),
                InputMethod.HOLD,
                0.0F,
                BufferingState.clickCanBuffer(),
                ability.abilityId);
        helper.assertTrue(
                held != null,
                "The server AbilityInput path did not create a held "
                        + "pull-in action.");
        return power;
    }

    private static boolean pullMarkerPresent(
            ServerLevel level, Entity target) {
        return !level.getEntities(
                        D4CAddon.FLAG.get(),
                        marker -> marker.mode()
                                        == D4CMarkerEntity.Mode.PULL
                                && marker.distanceToSqr(target) < 9.0D)
                .isEmpty();
    }

    private static void endPullIn(
            ServerLevel level,
            BlockPos origin,
            ServerPlayer owner,
            StandPower power,
            String tag) {
        StandEntity stand = power.getSummonedStandEntity();
        if (stand != null) {
            stand.discard();
        }
        owner.discard();
        for (ServerLevel loaded : level.getServer().getAllLevels()) {
            loaded.getEntities(
                            EntityType.COW,
                            cow -> cow.getTags().contains(tag))
                    .forEach(Entity::discard);
        }
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 4; z++) {
                for (int y = 0; y <= 3; y++) {
                    level.setBlockAndUpdate(
                            origin.offset(x, y, z),
                            Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static ServerPlayer makeDetachedServerPlayer(
            GameTestHelper helper,
            String name) {
        return new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), name),
                ClientInformation.createDefault());
    }

    private static ItemEntity spawnItem(
            GameTestHelper helper,
            ServerLevel level,
            Vec3 position,
            ItemStack stack) {
        ItemEntity item = new ItemEntity(
                level,
                position.x,
                position.y,
                position.z,
                stack);
        item.setNoGravity(true);
        item.setNeverPickUp();
        item.setDeltaMovement(Vec3.ZERO);
        helper.assertTrue(
                level.addFreshEntity(item),
                "The item collision fixture could not join the level.");
        return item;
    }

    // 1.16 d4c_item_throw had no stamina condition and StandEntityTask paid
    // its 100 after the throw with the result ignored: it throws on an empty
    // bar, which stays at 0.
    @GameTest(template = "empty", timeoutTicks = 20 + GameTestChunkTickets.WAIT_TICKS)
    public static void itemThrowRunsOnAnEmptyBar(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> itemThrowRunsOnAnEmptyBarBody(h));
    }

    private static void itemThrowRunsOnAnEmptyBarBody(GameTestHelper helper) {
        ServerPlayer player = makeServerPlayer(
                helper, "d4c-item-throw-empty");
        StandPower power = summonD4C(helper, player);
        player.setItemSlot(
                net.minecraft.world.entity.EquipmentSlot.OFFHAND,
                new ItemStack(Items.STONE, 2));
        power.setStamina(0.0F);
        Ability ability = power.getAbility("d4c_item_throw");
        helper.assertTrue(
                ability instanceof D4CPhase2BAbility
                        && AbilityInput.withConditionCheck(ability, player),
                "1.16 had no stamina check: the item throw must be "
                        + "admitted on an empty bar.");
        AbilityInput.keyPress(
                (short) 74,
                ability,
                player,
                new FriendlyByteBuf(Unpooled.buffer()),
                InputMethod.CLICK,
                0.0F,
                BufferingState.clickCanBuffer(),
                ability.abilityId);
        helper.succeedWhen(() -> {
            helper.assertTrue(
                    player.getOffhandItem().getCount() == 1,
                    "The item throw did not throw on an empty bar.");
            helper.assertTrue(
                    power.getStamina() == 0.0F,
                    "The paid 100 must leave the bar at 0: "
                            + power.getStamina());
            player.getOffhandItem().setCount(0);
            power.getSummonedStandEntity().discard();
            player.discard();
        });
    }

    // 1.16 aa/aa/ae.java: summon_kick was a plain StandAction, performed by the user: no autoSummonStand, so a
    // user without the Stand out kicks without summoning it, and the cost is paid before the kick.
    @GameTest(template = "empty", timeoutTicks = 20 + GameTestChunkTickets.WAIT_TICKS)
    public static void summonKickNeitherSummonsTheStandNorNeedsIt(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> summonKickWithoutTheStandBody(h));
    }

    private static void summonKickWithoutTheStandBody(GameTestHelper helper) {
        ServerPlayer player = makeServerPlayer(helper, "d4c-kick-no-stand");
        StandPower power = StandPower.get(player);
        power.setStand(D4CAddon.D4C.get());
        power.skipProgression();
        power.setStamina(power.getMaxStamina());
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(1, 2, 4));
        cow.setNoAi(true);
        try {
            helper.assertTrue(power.getSummonedStandEntity() == null && !power.isStaminaInfinite(),
                    "Setup: D4C is out, or the stamina is infinite.");
            lookAt(player, cow);
            // the cow under the crosshair, as the user's client sends its vanilla pick
            LivingComponentAction.getComponent(player).entityAim
                    .setTarget(new ActionTarget(cow));
            Ability kick = power.getAbility("summon_kick");
            helper.assertTrue(kick instanceof D4CSummonKickAbility && AbilityInput.withConditionCheck(kick, player),
                    "1.16 summon_kick needed no Stand out: it must be admitted by a user without it, got " + kick);
            float before = power.getStamina();
            pressKick(kick, player);
            helper.assertTrue(power.getSummonedStandEntity() == null,
                    "1.16 summon_kick had no autoSummonStand: the press summoned D4C.");
            List<D4CSummonKickEntity> kicks = helper.getLevel().getEntitiesOfClass(D4CSummonKickEntity.class,
                    new AABB(player.blockPosition()).inflate(16.0D));
            helper.assertTrue(kicks.size() == 1, "The press did not summon the kick: " + kicks.size());
            kicks.forEach(Entity::discard);
            helper.assertTrue(before - power.getStamina() == StandAbilityStamina.effectiveCost(power, 250.0F),
                    "1.16 StandAction.onPerform paid the kick's 250 once: " + before + " -> " + power.getStamina());
            helper.assertTrue(power.isAbilityOnCooldown("summon_kick"),
                    "The kick did not start its cooldown.");
        }
        finally {
            cow.discard();
            player.discard();
        }
        helper.succeed();
    }

    // The same plain action left the Stand's own task alone: D4C kept punching while its user kicked.
    @GameTest(template = "empty", timeoutTicks = 20 + GameTestChunkTickets.WAIT_TICKS)
    public static void summonKickLeavesTheStandsTaskAlone(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> summonKickLeavesTheStandsTaskBody(h));
    }

    private static void summonKickLeavesTheStandsTaskBody(GameTestHelper helper) {
        ServerPlayer player = makeServerPlayer(helper, "d4c-kick-task");
        StandPower power = summonD4C(helper, player);
        StandEntity stand = power.getSummonedStandEntity();
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(1, 2, 4));
        cow.setNoAi(true);
        try {
            lookAt(player, cow);
            EntityActionType barrage = (EntityActionType) power.getAbility("d4c_barrage");
            EntityActionInstance running = barrage.createActionObj();
            barrage.initActionFromConfig(running, helper.getLevel(), player, stand);
            running.setStartingPhase();
            LivingComponentAction.getComponent(stand).setAction(running, player, SyncType.NO_SYNC);
            helper.assertTrue(stand.getCurStandAction() == running, "Setup: the barrage is not D4C's action.");

            Ability kick = power.getAbility("summon_kick");
            helper.assertTrue(AbilityInput.withConditionCheck(kick, player),
                    "Setup: the kick was refused with D4C out.");
            pressKick(kick, player);
            helper.assertTrue(stand.getCurStandAction() == running,
                    "1.16 summon_kick was no Stand task: D4C's own action must go on, it is "
                            + stand.getCurStandAction());
            List<D4CSummonKickEntity> kicks = helper.getLevel().getEntitiesOfClass(D4CSummonKickEntity.class,
                    new AABB(player.blockPosition()).inflate(16.0D));
            helper.assertTrue(kicks.size() == 1, "The press did not summon the kick: " + kicks.size());
            kicks.forEach(Entity::discard);
        }
        finally {
            cow.discard();
            stand.discard();
            player.discard();
        }
        helper.succeed();
    }

    private static void lookAt(ServerPlayer player, Entity target) {
        Vec3 aim = target.position().add(0.0D, 0.7D, 0.0D).subtract(player.getEyePosition());
        player.setYRot((float) Math.toDegrees(Math.atan2(-aim.x, aim.z)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(aim.y, Math.sqrt(aim.x * aim.x + aim.z * aim.z))));
        player.setYHeadRot(player.getYRot());
    }

    private static void pressKick(Ability kick, ServerPlayer player) {
        AbilityInput.keyPress((short) 75, kick, player, new FriendlyByteBuf(Unpooled.buffer()), InputMethod.CLICK,
                0.0F, BufferingState.clickCanBuffer(), kick.abilityId);
    }

    // 1.16 annihilation_mark checkStandConditions: with the Stand out a bar
    // under 50 refuses without a message, before the target is looked at.
    @GameTest(template = "empty", timeoutTicks = 20 + GameTestChunkTickets.WAIT_TICKS)
    public static void annihilationMarkRefusesSilentlyUnderFifty(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> annihilationMarkRefusesSilentlyUnderFiftyBody(h));
    }

    private static void annihilationMarkRefusesSilentlyUnderFiftyBody(GameTestHelper helper) {
        ServerPlayer player = makeServerPlayer(
                helper, "d4c-mark-stamina");
        StandPower power = summonD4C(helper, player);
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(1, 2, 4));
        cow.setNoAi(true);
        Vec3 aim = cow.position().add(0.0D, 0.7D, 0.0D)
                .subtract(player.getEyePosition());
        player.setYRot((float) Math.toDegrees(Math.atan2(-aim.x, aim.z)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(
                aim.y, Math.sqrt(aim.x * aim.x + aim.z * aim.z))));
        player.setYHeadRot(player.getYRot());
        Ability ability = power.getAbility("annihilation_mark");
        helper.assertTrue(
                ability instanceof D4CPhase2BAbility,
                "Annihilation Mark is not a D4C action.");
        try {
            power.setStamina(50.0F);
            helper.assertTrue(
                    ability.checkSpecificConditions(power).isPositive(),
                    "Setup: Annihilation Mark refused the cow in view on "
                            + "a bar of 50.");
            power.setStamina(49.0F);
            var refusal = ability.checkSpecificConditions(power);
            helper.assertTrue(
                    !refusal.isPositive() && refusal.getWarning() == null,
                    "1.16 refused a bar under 50 without a message: "
                            + refusal.isPositive() + " "
                            + refusal.getWarning());
            cow.discard();
            power.setStamina(0.0F);
            refusal = ability.checkSpecificConditions(power);
            helper.assertTrue(
                    !refusal.isPositive() && refusal.getWarning() == null,
                    "1.16 checked the bar before the target: an empty bar "
                            + "and no enemy refuse without a message, got "
                            + refusal.getWarning());
        }
        finally {
            cow.discard();
            power.getSummonedStandEntity().discard();
            player.discard();
        }
        helper.succeed();
    }

    // 1.16 d4c_barrage registered its cry with the one-argument standSound
    // (playInArmsOnly true): an arms-only D4C still cried through the barrage.
    @GameTest(template = "empty", timeoutTicks = 20 + GameTestChunkTickets.WAIT_TICKS)
    public static void barrageCriesWithTheArmsOnly(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> barrageCriesWithTheArmsOnlyBody(h));
    }

    private static void barrageCriesWithTheArmsOnlyBody(GameTestHelper helper) {
        ServerPlayer player = makeServerPlayer(
                helper, "d4c-barrage-cry");
        StandPower power = summonD4C(helper, player);
        StandEntity stand = power.getSummonedStandEntity();
        try {
            Ability ability = power.getAbility("d4c_barrage");
            helper.assertTrue(
                    ability instanceof StandEntityBarrageAbility,
                    "The registered d4c_barrage is not a barrage: "
                            + ability);
            EntityActionInstance barrage =
                    ((EntityActionType) ability).createActionObj();
            helper.assertTrue(
                    playsBarrageCry(barrage, stand),
                    "Setup: the fully summoned D4C does not cry.");
            stand.setArmsOnlyMode(true, true);
            helper.assertTrue(
                    stand.isArmsOnlyMode(),
                    "Setup: D4C did not switch to its arms only.");
            helper.assertTrue(
                    playsBarrageCry(barrage, stand),
                    "1.16 played the d4c_barrage cry with the arms only "
                            + "(one-argument standSound); the port stayed "
                            + "silent.");
        }
        finally {
            stand.discard();
            player.discard();
        }
        helper.succeed();
    }

    private static boolean playsBarrageCry(
            EntityActionInstance barrage, StandEntity stand) {
        try {
            Method method = StandEntityBarrageAbility.StandEntityBarrage.class
                    .getDeclaredMethod(
                            "playsBarrageCry", Level.class, StandEntity.class);
            method.setAccessible(true);
            return (Boolean) method.invoke(barrage, stand.level(), stand);
        }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "The core barrage cry check is not reachable", e);
        }
    }

    private static StandPower summonD4C(
            GameTestHelper helper, ServerPlayer player) {
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        StandPower power = StandPower.get(player);
        helper.assertTrue(
                power != null,
                "The mock player has no Stand power attachment.");
        power.setStand(D4CAddon.D4C.get());
        power.skipProgression();
        power.setStamina(power.getMaxStamina());
        helper.assertTrue(
                D4CAddon.D4C.get().summon(player, power)
                        && power.getSummonedStandEntity() != null,
                "D4C could not be summoned.");
        power.getSummonedStandEntity().summonLockTicks = 0;
        helper.assertTrue(
                !power.isStaminaInfinite(),
                "Setup: the D4C user's stamina is infinite.");
        return power;
    }

    private static ServerPlayer makeServerPlayer(
            GameTestHelper helper,
            String name) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        GameProfile profile = new GameProfile(
                UUID.randomUUID(), name);
        ServerPlayer player = new ServerPlayer(
                server,
                level,
                profile,
                ClientInformation.createDefault());
        Connection connection =
                new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(profile, false);
        new SilentServerPacketListener(
                server, connection, player, cookie);
        BlockPos spawn = helper.absolutePos(
                new BlockPos(1, 2, 1));
        player.moveTo(
                spawn.getX() + 0.5D,
                spawn.getY(),
                spawn.getZ() + 0.5D);
        level.addNewPlayer(player);
        return player;
    }

    private static final class SilentServerPacketListener
            extends ServerGamePacketListenerImpl {
        private SilentServerPacketListener(
                MinecraftServer server,
                Connection connection,
                ServerPlayer player,
                CommonListenerCookie cookie) {
            super(server, connection, player, cookie);
        }

        @Override
        public void send(Packet<?> packet) {
        }

        @Override
        public void send(
                Packet<?> packet,
                PacketSendListener sendListener) {
        }
    }

    private static void assertDimensionsMatch(
            GameTestHelper helper,
            D4CAnnihilationCloneEntity clone,
            Cow target) {
        helper.assertTrue(
                close(clone.getBbWidth(), target.getBbWidth())
                        && close(
                                clone.getBbHeight(),
                                target.getBbHeight()),
                "The clone did not copy its target dimensions: clone="
                        + clone.getBbWidth()
                        + "x"
                        + clone.getBbHeight()
                        + ", target="
                        + target.getBbWidth()
                        + "x"
                        + target.getBbHeight());
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) <= EPSILON;
    }
}
