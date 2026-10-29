package rotp.addons.kingcrimson;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.ModDamageTypes;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.util.functions.DamageUtil;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.ControlSchemeTemplate;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import com.mojang.authlib.GameProfile;

import rotp.addons.kingcrimson.entity.KingCrimsonTimeEraseDecoyEntity;
import rotp.addons.kingcrimson.ability.KingCrimsonTimeEraseAbility.TimeEraseAction;
import rotp.addons.kingcrimson.ability.KingCrimsonTimeSkipAbility;
import rotp.addons.kingcrimson.network.TemporalStateSyncPayload;
import rotp.addons.kingcrimson.network.TimeEraseTransitionPayload;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalState;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalState.Mode;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.subsystems.target.ActionTarget;

@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonGameTests {
    private static final short TIME_ERASE_INPUT_KEY = 1;
    private static final short TIME_SKIP_INPUT_KEY = 3;

    private KingCrimsonGameTests() {}

    @GameTestHolder("rotp_kingcrimson_eye_windup")
    @PrefixGameTestTemplate(false)
    public static final class EyejabWindup {
        @GameTest(template = "empty", templateNamespace = "rotp_kingcrimson_eye_windup",
                timeoutTicks = 30)
        public static void registeredSingleThrowAllowsPunchDuringRecovery(GameTestHelper helper) {
            assertRegisteredThrowRecovery(helper, false);
        }

        @GameTest(template = "empty", templateNamespace = "rotp_kingcrimson_eye_windup",
                timeoutTicks = 30)
        public static void registeredTripleThrowAllowsPunchDuringRecovery(GameTestHelper helper) {
            assertRegisteredThrowRecovery(helper, true);
        }

        private static void assertRegisteredThrowRecovery(GameTestHelper helper, boolean triple) {
            ServerPlayerFixture fixture = makeServerPlayer(helper,
                    triple ? "kc-throw-three" : "kc-throw-one", new BlockPos(1, 2, 1));
            ServerPlayer player = fixture.player();
            player.setGameMode(GameType.SURVIVAL);
            player.setNoGravity(true);
            player.setDeltaMovement(Vec3.ZERO);
            StandPower power = PowerClass.STAND.attachGet(player);
            power.setStand(KingCrimsonAddon.KING_CRIMSON.get());
            power.skipProgression();
            helper.assertTrue(KingCrimsonAddon.KING_CRIMSON.get().summon(player, power),
                    "Projectile-test Stand summon failed");
            StandEntity stand = power.getSummonedStandEntity();
            stand.summonLockTicks = 0;
            LivingComponentAction.getComponent(player);
            LivingComponentAction.getComponent(stand);
            helper.runAfterDelay(2, () -> {
                short throwKey = 33;
                short punchKey = 34;
                try {
                    helper.assertTrue(!player.getAbilities().instabuild,
                            "Projectile ammo fixture is not in Survival");
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ARROW, 16));
                    stand.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(14.0D);
                    stand.setFinisherMeter(0.0F);
                    stand.setHeavyPunchFinisher();
                    stand.setFinisherMeter(0.6F);
                    power.setStamina(power.getMaxStamina());
                    String name = triple ? "kingcrimson_projectile_throw_tripple"
                            : "kingcrimson_projectile_throw";
                    Ability throwAbility = power.getAbility(name);
                    Ability punch = power.getAbility("punch");
                    helper.assertTrue(throwAbility != null && punch instanceof EntityActionType
                                    && AbilityInput.withConditionCheck(throwAbility, player),
                            "Registered projectile input failed its production conditions");
                    BufferingState pressed = BufferingState.clickCanBuffer();
                    FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.buffer());
                    try {
                        AbilityInput.keyPress(throwKey, throwAbility, player, input, InputMethod.CLICK,
                                0.0F, pressed, throwAbility.getAbilityId());
                    } finally {
                        input.release();
                    }
                    EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
                    EntityActionType punchType = (EntityActionType) punch;
                    helper.assertTrue(pressed.isActionSuccess && action != null
                                    && action.ability == throwAbility && action.getPhase() == ActionPhase.WINDUP,
                            "Registered projectile input did not start the expected windup");
                    helper.assertTrue(!action.canBeCancelledInto(punchType)
                                    && punchType.shouldBufferInput(LivingComponentAction.getComponent(stand)),
                            "Projectile windup incorrectly allows immediate punch replacement");
                    for (int tick = 0; tick < 60 && action.getPhase() != ActionPhase.RECOVERY
                            && !action.isOver(); tick++) {
                        action._tickAction();
                    }
                    helper.assertTrue(action.getPhase() == ActionPhase.RECOVERY,
                            "Registered projectile action never reached recovery");
                    helper.assertTrue(stand.getLastHeavyFinisherValue() == 0.0F,
                            "Projectile fixture lost the distinct last-heavy snapshot");
                    float configuredRecovery = action.phasesLength.getFloat(ActionPhase.RECOVERY);
                    float activeRecovery = action.getAnimPhaseLength();
                    int expectedCount = triple ? 3 : 1;
                    List<AbstractArrow> arrows = helper.getLevel().getEntitiesOfClass(AbstractArrow.class,
                            player.getBoundingBox().inflate(32.0D), arrow -> arrow.getOwner() == player);
                    helper.assertTrue(arrows.size() == expectedCount
                                    && player.getMainHandItem().is(Items.ARROW)
                                    && player.getMainHandItem().getCount() == 16 - expectedCount,
                            "Registered throw did not spawn/consume " + expectedCount + " owned arrows");
                    boolean recoveryCanCancel = action.canBeCancelledInto(punchType);
                    helper.assertTrue(AbilityInput.withConditionCheck(punch, player),
                            "Replacement punch failed its production conditions");
                    BufferingState punchPressed = BufferingState.clickCanBuffer();
                    FriendlyByteBuf punchInput = new FriendlyByteBuf(Unpooled.buffer());
                    try {
                        AbilityInput.keyPress(punchKey, punch, player, punchInput, InputMethod.CLICK,
                                0.0F, punchPressed, punch.getAbilityId());
                    } finally {
                        punchInput.release();
                    }
                    EntityActionInstance replacement = LivingComponentAction.getCurEntityAction(stand);
                    helper.assertTrue(configuredRecovery == 16.0F && activeRecovery == 16.0F
                                    && recoveryCanCancel && punchPressed.isActionSuccess && !punchPressed.shouldBuffer
                                    && replacement != null && replacement != action
                                    && replacement.ability == punch,
                            "Projectile recovery mismatch: configuredLength=" + configuredRecovery
                                    + ", activeLength=" + activeRecovery + ", currentActionIsThrow=" + (replacement == action)
                                    + ", canCancel=" + recoveryCanCancel + ", punchSuccess=" + punchPressed.isActionSuccess
                                    + ", punchShouldBuffer=" + punchPressed.shouldBuffer);
                } finally {
                    AbilityInput.keyRelease(throwKey, player);
                    AbilityInput.keyRelease(punchKey, player);
                    for (AbstractArrow arrow : helper.getLevel().getEntitiesOfClass(AbstractArrow.class,
                            player.getBoundingBox().inflate(32.0D), candidate -> candidate.getOwner() == player)) {
                        arrow.discard();
                    }
                    stand.discard();
                    player.discard();
                }
                helper.succeed();
            });
        }

        @GameTest(template = "empty", templateNamespace = "rotp_kingcrimson_eye_windup",
                timeoutTicks = 30)
        public static void registeredUppercutAreaUsesDonorSecondarySemantics(
                GameTestHelper helper) {
            assertRegisteredArea(helper, false, 3.0D, 0.4D);
        }

        @GameTest(template = "empty", templateNamespace = "rotp_kingcrimson_eye_windup",
                timeoutTicks = 30)
        public static void registeredGroundSlamAreaUsesDonorSecondarySemantics(
                GameTestHelper helper) {
            assertRegisteredArea(helper, true, 2.0D, 0.2D);
        }

        private static void assertRegisteredArea(
                GameTestHelper helper, boolean armsOnly, double range, double upwardPush) {
            ServerPlayerFixture fixture = makeServerPlayer(
                    helper, armsOnly ? "kc-slam-area" : "kc-upper-area", new BlockPos(1, 2, 1));
            ServerPlayer player = fixture.player();
            player.setNoGravity(true);
            player.setDeltaMovement(Vec3.ZERO);
            StandPower power = PowerClass.STAND.attachGet(player);
            power.setStand(KingCrimsonAddon.KING_CRIMSON.get());
            power.skipProgression();
            helper.assertTrue(KingCrimsonAddon.KING_CRIMSON.get().summon(player, power),
                    "Area-test Stand summon failed");
            StandEntity stand = power.getSummonedStandEntity();
            stand.summonLockTicks = 0;
            helper.runAfterDelay(2, () -> {
                ServerLevel level = helper.getLevel();
                BlockPos block = player.blockPosition().offset(1, 0, 0);
                BlockState previous = level.getBlockState(block);
                Vec3 center = Vec3.atCenterOf(block);
                Cow target = helper.spawn(EntityType.COW, new BlockPos(4, 3, 2));
                Cow protectedTarget = helper.spawn(EntityType.COW, new BlockPos(4, 3, 3));
                ItemEntity item = new ItemEntity(level, center.x, center.y, center.z + 0.75D,
                        new ItemStack(Items.STONE));
                // Ground Punch's 1.16 heavy block explosion follows the area and hurts the same bystanders,
                // so only the first (area) source is measured here.
                DamageSource[] observedSource = {null};
                float[] areaDamage = {0.0F, 0.0F};
                Consumer<LivingIncomingDamageEvent> observe = event -> {
                    if (event.getEntity() == target && event.getSource().getDirectEntity() == stand
                            && observedSource[0] == null) {
                        observedSource[0] = event.getSource();
                    }
                };
                Consumer<LivingDamageEvent.Post> dealt = event -> {
                    if (event.getSource() == observedSource[0]) {
                        if (event.getEntity() == target) areaDamage[0] += event.getNewDamage();
                        if (event.getEntity() == protectedTarget) areaDamage[1] += event.getNewDamage();
                    }
                };
                short inputKey = 32;
                try {
                    level.setBlock(block, Blocks.BEDROCK.defaultBlockState(), 3);
                    stand.setArmsOnlyMode(armsOnly, true);
                    stand.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(18.0D);
                    stand.setFinisherMeter(0.75F);
                    power.setStamina(power.getMaxStamina());
                    for (Cow cow : List.of(target, protectedTarget)) {
                        cow.setNoAi(true);
                        cow.setNoGravity(true);
                        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
                        cow.setHealth(200.0F);
                        cow.setPos(center.x, center.y + 0.5D, center.z + 0.5D);
                        cow.setDeltaMovement(Vec3.ZERO);
                    }
                    item.setNoGravity(true);
                    item.setDeltaMovement(Vec3.ZERO);
                    helper.assertTrue(level.addFreshEntity(item), "Area-test Item was not added");

                    Vec3 look = center.subtract(player.getEyePosition());
                    player.setYRot((float) Math.toDegrees(Math.atan2(-look.x, look.z)));
                    player.setXRot((float) -Math.toDegrees(Math.atan2(look.y,
                            Math.sqrt(look.x * look.x + look.z * look.z))));
                    ActionTarget aim = new ActionTarget(block, Direction.WEST);
                    helper.assertTrue(HitResultUtil.isTargetWithinRange(aim, stand, level,
                                    stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE),
                                    stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE)),
                            "The real block fixture is outside the Stand's punch range");
                    LivingComponentAction.getComponent(player).entityAim.setTarget(aim);
                    LivingComponentAction.getComponent(stand).entityAim.setTarget(aim);
                    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false,
                            LivingIncomingDamageEvent.class, observe);
                    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false,
                            LivingDamageEvent.Post.class, dealt);

                    String abilityName = armsOnly ? "kingcrimson_groundslam" : "kingcrimson_groundpunch";
                    Ability ability = power.getAbility(abilityName);
                    helper.assertTrue(ability != null && AbilityInput.withConditionCheck(ability, player),
                            "Registered area ability failed its production input conditions");
                    BufferingState pressed = BufferingState.clickCanBuffer();
                    FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.buffer());
                    try {
                        AbilityInput.keyPress(inputKey, ability, player, input, InputMethod.CLICK,
                                0.0F, pressed, ability.getAbilityId());
                    } finally {
                        input.release();
                    }
                    EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
                    helper.assertTrue(pressed.isActionSuccess && action != null && action.ability == ability,
                            "Registered area input did not install the expected action");
                    helper.assertTrue(stand.isArmsOnlyMode() == armsOnly,
                            "Area input changed the fixture's required Stand form");
                    if (armsOnly) {
                        helper.assertTrue(action.phasesLength.getFloat(ActionPhase.WINDUP) == 5.0F
                                        && action.phasesLength.getFloat(ActionPhase.RECOVERY) == 4.0F,
                                "Ground Slam initialization replaced the donor five/four phases");
                    }
                    for (int tick = 0; tick < 60 && action.getPhase() != ActionPhase.PERFORM
                            && !action.isOver(); tick++) {
                        action._tickAction();
                    }
                    helper.assertTrue(action.getPhase() == ActionPhase.PERFORM,
                            "Registered area action never reached perform");
                    float expectedDamage = Math.max(0.25F, (float) (stand.getAttackDamage()
                            * (0.5D - center.distanceTo(target.position()) / range)));
                    helper.assertTrue(expectedDamage > 0.25F && expectedDamage < 20.0F,
                            "Feet-distance fixture cannot distinguish the donor damage");
                    helper.assertTrue(protectedTarget.hurt(protectedTarget.damageSources().generic(), 20.0F)
                                    && protectedTarget.invulnerableTime > 10,
                            "Native damage did not establish fresh vanilla invulnerability");
                    action._tickAction();

                    helper.assertTrue(Math.abs(item.getDeltaMovement().y - upwardPush) < 0.000001D,
                            "Area impact did not push the nonliving Item by " + upwardPush);
                    helper.assertTrue(Math.abs(areaDamage[0] - expectedDamage) < 0.0001F,
                            "Area damage used a bounding-box centre instead of entity feet: expected "
                                    + expectedDamage + ", got " + areaDamage[0]);
                    helper.assertTrue(areaDamage[1] == 0.0F && protectedTarget.invulnerableTime > 10,
                            "Area damage bypassed the secondary target's fresh vanilla cooldown");
                    DamageSource source = observedSource[0];
                    helper.assertTrue(source != null && source.is(DamageTypes.MOB_ATTACK)
                                    && source.getDirectEntity() == stand && source.getEntity() == player
                                    && !source.is(DamageTypeTags.IS_PROJECTILE)
                                    && !source.is(ModDamageTypes.CAN_HURT_STANDS)
                                    && !source.is(ModDamageTypes.STAND_ATTACK),
                            "Area damage lost the ordinary indirect mob source or acquired Stand/projectile tags");
                } finally {
                    NeoForge.EVENT_BUS.unregister(observe);
                    NeoForge.EVENT_BUS.unregister(dealt);
                    AbilityInput.keyRelease(inputKey, player);
                    item.discard();
                    target.discard();
                    protectedTarget.discard();
                    level.setBlock(block, previous, 3);
                    stand.discard();
                    player.discard();
                }
                helper.succeed();
            });
        }

        @GameTest(template = "empty", templateNamespace = "rotp_kingcrimson_eye_windup",
                timeoutTicks = 30)
        public static void registeredChopUsesLastHeavyRecovery(GameTestHelper helper) {
            assertRegisteredStrikeRecovery(helper, "kingcrimson_chop");
        }

        @GameTest(template = "empty", templateNamespace = "rotp_kingcrimson_eye_windup",
                timeoutTicks = 30)
        public static void registeredImpaleUsesLastHeavyRecovery(GameTestHelper helper) {
            assertRegisteredStrikeRecovery(helper, "kingcrimson_impale");
        }

        private static void assertRegisteredStrikeRecovery(GameTestHelper helper, String abilityName) {
            ServerPlayerFixture fixture = makeServerPlayer(
                    helper, abilityName.equals("kingcrimson_chop")
                            ? "kc-chop-recovery" : "kc-impale-recov", new BlockPos(1, 2, 1));
            ServerPlayer player = fixture.player();
            StandPower power = PowerClass.STAND.attachGet(player);
            power.setStand(KingCrimsonAddon.KING_CRIMSON.get());
            power.skipProgression();
            helper.assertTrue(KingCrimsonAddon.KING_CRIMSON.get().summon(player, power),
                    "Stand summon failed");
            StandEntity stand = power.getSummonedStandEntity();
            stand.summonLockTicks = 0;
            helper.runAfterDelay(2, () -> {
                short inputKey = 31;
                try {
                    stand.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(14.0D);
                    stand.setFinisherMeter(0.0F);
                    stand.setHeavyPunchFinisher();
                    stand.setFinisherMeter(0.6F);
                    power.setStamina(power.getMaxStamina());
                    Ability chop = power.getAbility(abilityName);
                    FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.buffer());
                    try {
                        AbilityInput.keyPress(inputKey, chop, player, input,
                                InputMethod.CLICK, 0.0F, BufferingState.clickCanBuffer(),
                                chop.getAbilityId());
                    } finally {
                        input.release();
                    }
                    EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
                    helper.assertTrue(action != null && action.ability == chop,
                            "Registered " + abilityName + " input did not install the action");
                    int expected = StandStatFormulas.getHeavyAttackRecovery(
                            stand.getAttackSpeed(), stand.getLastHeavyFinisherValue());
                    helper.assertTrue(expected == 16 && stand.getLastHeavyFinisherValue() == 0.0F,
                            "Recovery fixture did not retain the distinct last-heavy snapshot");
                    helper.assertTrue(action.phasesLength.getFloat(ActionPhase.RECOVERY) == expected,
                            abilityName + " recovery used the current meter instead of the last-heavy snapshot: "
                                    + action.phasesLength.getFloat(ActionPhase.RECOVERY));
                    for (int tick = 0; tick < 50 && action.getPhase() != ActionPhase.RECOVERY; tick++) {
                        action._tickAction();
                    }
                    helper.assertTrue(action.getPhase() == ActionPhase.RECOVERY
                                    && action.getAnimPhaseLength() == expected,
                            "Chop did not enter the donor recovery duration");
                    for (int tick = 0; tick < expected; tick++) {
                        helper.assertTrue(action.getPhase() == ActionPhase.RECOVERY,
                                "Chop recovery ended prematurely");
                        action._tickAction();
                    }
                    helper.assertTrue(action.getPhase() == null,
                            "Chop did not finish after the expected recovery callbacks");
                } finally {
                    AbilityInput.keyRelease(inputKey, player);
                    stand.discard();
                    player.discard();
                }
                helper.succeed();
            });
        }

        @GameTest(template = "empty", templateNamespace = "rotp_kingcrimson_eye_windup",
                timeoutTicks = 30)
        public static void registeredEyejabUsesTenWindupTicks(GameTestHelper helper) {
            ServerPlayerFixture fixture = makeServerPlayer(
                    helper, "kc-eye-windup", new BlockPos(1, 2, 1));
            ServerPlayer player = fixture.player();
            StandPower power = PowerClass.STAND.attachGet(player);
            power.setStand(KingCrimsonAddon.KING_CRIMSON.get());
            power.skipProgression();
            player.setHealth(5.0F);
            helper.assertTrue(KingCrimsonAddon.KING_CRIMSON.get().summon(player, power),
                    "Stand summon failed");
            StandEntity stand = power.getSummonedStandEntity();
            stand.summonLockTicks = 0;
            helper.runAfterDelay(2, () -> {
                short inputKey = 30;
                try {
                    power.setStamina(power.getMaxStamina());
                    Ability eyejab = power.getAbility("kingcrimson_desperate_eyejab");
                    FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.buffer());
                    try {
                        AbilityInput.keyPress(inputKey, eyejab, player, input,
                                InputMethod.CLICK, 0.0F, BufferingState.clickCanBuffer(),
                                eyejab.getAbilityId());
                    } finally {
                        input.release();
                    }
                    EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
                    helper.assertTrue(action != null && action.ability == eyejab,
                            "Registered Eyejab input did not install the action");
                    helper.assertTrue(action.phasesLength.getFloat(ActionPhase.WINDUP) == 10.0F,
                            "Eyejab initialization replaced the donor ten-tick windup");
                    helper.assertTrue(action.getPhase() == ActionPhase.WINDUP
                                    && action.getAnimPhaseTick(0.0F) == 0.0F
                                    && action.getAnimPhaseLength() == 10.0F,
                            "Eyejab did not start an unskipped ten-tick windup");
                    for (int tick = 0; tick < 10; tick++) {
                        helper.assertTrue(action.getPhase() == ActionPhase.WINDUP,
                                "Eyejab entered perform before all ten windup callbacks");
                        action._tickAction();
                    }
                    helper.assertTrue(action.getPhase() == ActionPhase.PERFORM,
                            "Eyejab did not enter perform after ten windup callbacks");
                    action._tickAction();
                } finally {
                    AbilityInput.keyRelease(inputKey, player);
                    stand.discard();
                    player.discard();
                }
                helper.succeed();
            });
        }
    }

    @GameTest(template = "empty", timeoutTicks = 30, batch = "eraseMeleeOrdering")
    public static void standMeleeEndsEraseBeforeDamageButExistingProjectileDoesNot(
            GameTestHelper helper) {
        ServerPlayerFixture fixture = makeServerPlayer(helper, "kc-melee", new BlockPos(1, 2, 1));
        ServerPlayer player = fixture.player();
        StandPower power = PowerClass.STAND.attachGet(player);
        power.setStand(KingCrimsonAddon.KING_CRIMSON.get());
        power.skipProgression();
        helper.assertTrue(KingCrimsonAddon.KING_CRIMSON.get().summon(player, power), "Stand summon failed");
        StandEntity stand = power.getSummonedStandEntity();
        Cow target = helper.spawn(EntityType.COW, new BlockPos(4, 2, 1));
        Arrow arrow = EntityType.ARROW.create(helper.getLevel());
        arrow.setOwner(player);
        boolean[] sawOrderedMelee = {false};
        Consumer<LivingIncomingDamageEvent> observe = event -> {
            if (event.getEntity() == target && event.getSource().getDirectEntity() == stand) {
                helper.assertTrue(KingCrimsonAddon.temporalState(player).isIdle()
                                && !player.hasEffect(KingCrimsonAddon.TIME_ERASE)
                                && !player.hasEffect(ModStatusEffects.FULL_INVISIBILITY)
                                && !stand.hasEffect(MobEffects.INVISIBILITY),
                        "Melee entered damage calculation before Time Erase cleanup");
                fixture.listener().assertLastTemporalSync(helper, player, Mode.IDLE);
                sawOrderedMelee[0] = true;
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingIncomingDamageEvent.class, observe);
        try {
            helper.assertTrue(KingCrimsonTemporalStateService.beginTimeErase(player), "Erase setup failed");
            player.setYRot(-90.0F);
            player.setXRot(10.0F);
            helper.assertTrue(HitResultUtil.clipEntityLook(player,
                            candidate -> candidate.isPickable() && candidate != stand, 0.0D).getMainEntity() == target,
                    "The caster's own decoy intercepted the real melee target");
            DamageSource incoming = player.damageSources().mobAttack(target);
            helper.assertTrue(!player.hurt(incoming, 2.0F), "Caster did not escape incoming damage during erase");
            helper.assertTrue(target.hurt(player.damageSources().arrow(arrow, player), 2.0F), "Existing projectile did not hit");
            helper.assertTrue(KingCrimsonAddon.temporalState(player).isErasing(player.level().getGameTime()),
                    "A previously fired projectile ended the caster's erase");
            target.invulnerableTime = 0;
            DamageSource melee = new DamageSource(DamageUtil.type(helper.getLevel(), ModDamageTypes.STAND_ATTACK), stand, player);
            float before = target.getHealth();
            helper.assertTrue(target.hurt(melee, 2.0F) && target.getHealth() < before && sawOrderedMelee[0],
                    "Direct Stand attack did not execute after cleanup");
            helper.assertTrue(!KingCrimsonTemporalStateService.blocksDamage(player, incoming),
                    "Caster retained erase immunity after hitting");
        } finally {
            NeoForge.EVENT_BUS.unregister(observe);
            KingCrimsonTemporalStateService.terminate(player);
            arrow.discard();
            target.discard();
            stand.discard();
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30, batch = "erasePunchPhase")
    public static void punchKeepsEraseDuringWindupAndEndsItEvenOnMiss(
            GameTestHelper helper) {
        ServerPlayerFixture fixture = makeServerPlayer(helper, "kc-windup", new BlockPos(1, 2, 1));
        ServerPlayer player = fixture.player();
        StandPower power = PowerClass.STAND.attachGet(player);
        power.setStand(KingCrimsonAddon.KING_CRIMSON.get());
        power.skipProgression();
        helper.assertTrue(KingCrimsonAddon.KING_CRIMSON.get().summon(player, power), "Stand summon failed");
        StandEntity stand = power.getSummonedStandEntity();
        stand.summonLockTicks = 0;
        helper.runAfterDelay(2, () -> {
        try {
            power.setStamina(power.getMaxStamina());
            Ability erase = power.getAbility("kingcrimson_timeerase");
            FriendlyByteBuf held = new FriendlyByteBuf(Unpooled.buffer());
            try {
                AbilityInput.keyPress(TIME_ERASE_INPUT_KEY, erase, player, held, InputMethod.HOLD, 0.0F,
                        BufferingState.clickCanBuffer(), erase.getAbilityId());
            } finally {
                held.release();
            }
            EntityActionInstance eraseAction = LivingComponentAction.getCurEntityAction(stand);
            helper.assertTrue(eraseAction instanceof TimeEraseAction, "Held erase input was not installed");
            helper.assertTrue(eraseAction.getPowerUser() == player, "The live erase action could not resolve its player");
            for (int tick = 0; tick < 30 && !eraseAction.isOver()
                    && !KingCrimsonAddon.temporalState(player).isErasing(player.level().getGameTime()); tick++) {
                eraseAction._tickAction();
            }
            helper.assertTrue(KingCrimsonAddon.temporalState(player).isErasing(player.level().getGameTime()),
                    "Held erase action did not activate: phase=" + eraseAction.getPhase()
                            + ", stamina=" + power.getStamina()
                            + ", canStart=" + KingCrimsonTemporalStateService.canStartTimeErase(player));
            Ability punch = power.getAbility("punch");
            FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.buffer());
            try {
                AbilityInput.keyPress((short) 2, punch, player, input, InputMethod.CLICK, 0.0F,
                        BufferingState.clickCanBuffer(), punch.getAbilityId());
            } finally {
                input.release();
            }
            EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
            helper.assertTrue(action != null && action.ability == punch, "Production punch input did not install the action");
            helper.assertTrue(action.getPhase() == ActionPhase.WINDUP, "Punch did not start with a windup");
            helper.assertTrue(KingCrimsonAddon.temporalState(player).isErasing(player.level().getGameTime()),
                    "Preparing a punch prematurely ended erase");
            for (int tick = 0; tick < 30 && !action.isOver()
                    && !KingCrimsonAddon.temporalState(player).isIdle(); tick++) {
                action._tickAction();
            }
            helper.assertTrue(KingCrimsonAddon.temporalState(player).isIdle(),
                    "A missed punch retained erase during its perform phase");
            fixture.listener().assertLastTemporalSync(helper, player, Mode.IDLE);
            helper.assertTrue(fixture.listener().endSoundCount() == 1, "Erase end feedback was missing or duplicated");
        } finally {
            AbilityInput.keyRelease(TIME_ERASE_INPUT_KEY, player);
            KingCrimsonTemporalStateService.terminate(player);
            stand.discard();
            player.discard();
        }
        helper.succeed();
        });
    }

    // 1.16 kingcrimson_timeerase was holdType() with no recovery: a Stand action pressed during the held erase replaced
    // it at once (StandEntityAction.checkTaskCancelling, isCancelable) and KingCrimsonTimeErase.onTaskStopped ended the
    // erase, whose cooldown followed; any other action of the power ended the hold the same way. The port's punch keeps
    // the erase until its perform (D-rotp-king-crimson-09). The core grab release is no such action: 1.16 King Crimson
    // had no grab (062 InitStands :70-84), so like the manual control key it ends no held action (core K5-27 B).
    @GameTest(template = "empty", timeoutTicks = 60, batch = "eraseGivesWay")
    public static void aHeavyPunchPressedDuringTheHeldEraseReplacesItAndEndsTheEraseAsIn116(
            GameTestHelper helper) {
        ServerPlayerFixture fixture = makeServerPlayer(helper, "kc-erase-gives-way", new BlockPos(1, 2, 1));
        ServerPlayer player = fixture.player();
        StandPower power = PowerClass.STAND.attachGet(player);
        power.setStand(KingCrimsonAddon.KING_CRIMSON.get());
        power.skipProgression();
        helper.assertTrue(KingCrimsonAddon.KING_CRIMSON.get().summon(player, power), "Stand summon failed");
        StandEntity stand = power.getSummonedStandEntity();
        stand.summonLockTicks = 0;
        Cow held = helper.spawn(EntityType.COW, new BlockPos(1, 2, 3));
        helper.runAfterDelay(2, () -> {
        try {
            for (String pressed : new String[] {"heavy_punch", "punch"}) {
                EntityActionInstance erase = holdErase(helper, power, player, stand);
                BufferingState state = pressKey(power, player, pressed, (short) 2);
                EntityActionInstance now = LivingComponentAction.getCurEntityAction(stand);
                helper.assertTrue(state.isActionSuccess && !state.shouldBuffer && now != null && now != erase
                                && now.ability == power.getAbility(pressed) && erase.isOver(),
                        "1.16 let a " + pressed + " pressed during the held Time Erase through at once (holdType,"
                                + " isCancelable): started " + state.isActionSuccess + ", buffered " + state.shouldBuffer
                                + ", the Stand's action " + now + ", the erase over " + erase.isOver());
                boolean erasing = KingCrimsonAddon.temporalState(player).isErasing(player.level().getGameTime());
                if (pressed.equals("punch")) {
                    helper.assertTrue(erasing, "The punch's windup must keep the erase (D-rotp-king-crimson-09)");
                    for (int tick = 0; tick < 30 && !now.isOver()
                            && !KingCrimsonAddon.temporalState(player).isIdle(); tick++) {
                        now._tickAction();
                    }
                }
                helper.assertTrue(KingCrimsonAddon.temporalState(player).isIdle()
                                && power.getAbilityCooldown("kingcrimson_timeerase") > 0,
                        "1.16 KingCrimsonTimeErase.onTaskStopped ended the erase the " + pressed + " replaced, and its"
                                + " cooldown followed: erasing " + !KingCrimsonAddon.temporalState(player).isIdle()
                                + ", cooldown " + power.getAbilityCooldown("kingcrimson_timeerase"));
                clearErase(power, player, stand);
            }

            // the port's grab release, a plain ability of the power, during a core grab
            EntityActionInstance erase = holdErase(helper, power, player, stand);
            stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get()).setGrabTarget(held);
            helper.assertTrue(StandUtil.getStandGrabTarget(power) == held, "setup: the core grab does not hold the cow");
            BufferingState release = pressKey(power, player, "grab_release", (short) 2);
            helper.assertTrue(release.isActionSuccess && StandUtil.getStandGrabTarget(power) == null,
                    "1.16 let any action of the power through a held holdType Stand action (heldAllowsOtherAction), yet the"
                            + " grab release pressed during the held erase did not go off");
            helper.assertTrue(!erase.isOver() && LivingComponentAction.getCurEntityAction(stand) == erase
                            && !KingCrimsonAddon.temporalState(player).isIdle()
                            && power.getAbilityCooldown("kingcrimson_timeerase") == 0,
                    "1.16 King Crimson had no grab, so only its own actions ended the held erase (stopHeldAction(false));"
                            + " the port's grab release, no 1.16 action, ends no held action, as the manual control key"
                            + " (core K5-27 B): over " + erase.isOver() + ", the Stand's action "
                            + LivingComponentAction.getCurEntityAction(stand) + ", erasing "
                            + !KingCrimsonAddon.temporalState(player).isIdle() + ", cooldown "
                            + power.getAbilityCooldown("kingcrimson_timeerase"));
            clearErase(power, player, stand);
        } finally {
            AbilityInput.keyRelease(TIME_ERASE_INPUT_KEY, player);
            player.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
            KingCrimsonTemporalStateService.terminate(player);
            held.discard();
            stand.discard();
            player.discard();
        }
        helper.succeed();
        });
    }

    // pressed by its key as ClAbilityInputPacket does and kept held until the erase is on
    private static EntityActionInstance holdErase(GameTestHelper helper, StandPower power, ServerPlayer player,
            StandEntity stand) {
        power.setStamina(power.getMaxStamina());
        BufferingState state = pressKey(power, player, "kingcrimson_timeerase", TIME_ERASE_INPUT_KEY);
        EntityActionInstance erase = LivingComponentAction.getCurEntityAction(stand);
        helper.assertTrue(state.isActionSuccess && erase instanceof TimeEraseAction,
                "setup: the held erase was not installed: started " + state.isActionSuccess + ", " + erase);
        for (int tick = 0; tick < 30 && !erase.isOver()
                && !KingCrimsonAddon.temporalState(player).isErasing(player.level().getGameTime()); tick++) {
            erase._tickAction();
        }
        helper.assertTrue(KingCrimsonAddon.temporalState(player).isErasing(player.level().getGameTime())
                        && power.getAbilityCooldown("kingcrimson_timeerase") == 0,
                "setup: the held erase did not activate: phase " + erase.getPhase());
        return erase;
    }

    // the key's resolved ability, its condition check, then the press; the moves are resolved again, as the power
    // does once a tick (Power.updateAvailableMoves), since the test does not tick it
    private static BufferingState pressKey(StandPower power, ServerPlayer player, String name, short key) {
        BufferingState state = BufferingState.clickCanBuffer();
        Ability base = power.getAbility(name);
        power._curAvailableMoves.update(power, power.getMoveset());
        var container = power._curAvailableMoves.getContextVariationContainer(base);
        InputMethod method = name.equals("kingcrimson_timeerase") ? InputMethod.HOLD : InputMethod.CLICK;
        if (container != null && AbilityInput.withConditionCheck(container, player, method)) {
            FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.buffer());
            try {
                AbilityInput.keyPress(key, container.ability, player, input, method, 0.0F, state, base.getAbilityId());
            } finally {
                input.release();
            }
        }
        if (method == InputMethod.CLICK) {
            AbilityInput.keyRelease(key, player);
        }
        return state;
    }

    private static void clearErase(StandPower power, ServerPlayer player, StandEntity stand) {
        KingCrimsonTemporalStateService.terminate(player);
        player.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
        LivingComponentAction.getComponent(stand).setAction(null, player,
                rotp.core.powersystem.entityaction.netcode.SyncType.NO_SYNC);
        power.setAbilityCooldown("kingcrimson_timeerase", 0, 0);
        power.setAbilityCooldown("heavy_punch", 0, 0);
        power.setAbilityCooldown("punch", 0, 0);
    }

    // 1.16 StandEntity.tickHeadTurn took the body of a Stand in its summon pose onto its yaw; the decoy's Stand is
    // spawned with the owner's Stand's rotation (copyStandUserRotation, which leaves the body), so its body started
    // at yaw 0 until its first tick put it on the yaw.
    @GameTest(template = "empty", timeoutTicks = 60, batch = "eraseDecoyStandBody")
    public static void theTimeEraseDecoyStandStartsWithItsBodyOnItsYawAsIn116(GameTestHelper helper) {
        ServerPlayerFixture fixture = makeServerPlayer(helper, "kc-decoy-body", new BlockPos(1, 2, 1));
        ServerPlayer player = fixture.player();
        StandPower power = PowerClass.STAND.attachGet(player);
        power.setStand(KingCrimsonAddon.KING_CRIMSON.get());
        power.skipProgression();
        helper.assertTrue(KingCrimsonAddon.KING_CRIMSON.get().summon(player, power), "Stand summon failed");
        StandEntity stand = power.getSummonedStandEntity();
        stand.summonLockTicks = 0;
        helper.runAfterDelay(2, () -> {
        try {
            stand.setYRot(100.0F);
            stand.yRotO = 100.0F;
            helper.assertTrue(KingCrimsonTemporalStateService.beginTimeErase(player),
                    "The production Time Erase service rejected the decoy fixture.");
            List<KingCrimsonTimeEraseDecoyEntity> decoys = helper.getLevel().getEntitiesOfClass(
                    KingCrimsonTimeEraseDecoyEntity.class, player.getBoundingBox().inflate(8.0D));
            helper.assertTrue(decoys.size() == 1, "Setup: the Time Erase spawned " + decoys.size() + " decoys");
            KingCrimsonTimeEraseDecoyEntity decoy = decoys.get(0);
            // the level counts the tick before the entity's tick
            decoy.tickCount = 1;
            decoy.tick();
            StandPower decoyPower = StandPower.get(decoy);
            StandEntity decoyStand = decoyPower != null ? decoyPower.getSummonedStandEntity() : null;
            helper.assertTrue(decoyStand != null, "The decoy summoned no Stand at its first tick");
            helper.assertTrue(decoyStand.getYRot() == 100.0F && decoyStand.yBodyRot == 100.0F
                            && decoyStand.yBodyRotO == 100.0F,
                    "1.16 put a Stand's body on its yaw in its summon pose; the decoy's Stand (yaw "
                            + decoyStand.getYRot() + ") started with its body at " + decoyStand.yBodyRot
                            + " (previous " + decoyStand.yBodyRotO + ")");
        } finally {
            KingCrimsonTemporalStateService.terminate(player);
            stand.discard();
            player.discard();
        }
        helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 50)
    public static void timeEraseProductionInputActivatesAndCleansUp(
            GameTestHelper helper) {
        ServerPlayerFixture fixture = makeServerPlayer(helper);
        ServerPlayer player = fixture.player();
        ServerPlayerFixture observerFixture = makeServerPlayer(
                helper,
                "king-crimson-observer",
                new BlockPos(4, 2, 1));

        StandPower power = PowerClass.STAND.attachGet(player);
        power.setStand(KingCrimsonAddon.KING_CRIMSON.get());
        power.skipProgression();
        helper.assertTrue(
                KingCrimsonAddon.KING_CRIMSON.get().summon(
                        player, power),
                "King Crimson could not be summoned for Time Erase.");
        StandEntity summonedStand = power.getSummonedStandEntity();
        helper.assertTrue(
                summonedStand != null
                        && summonedStand.isAddedToLevel(),
                "The King Crimson Stand was not added to the level.");
        if (summonedStand == null) {
            return;
        }
        summonedStand.summonLockTicks = 0;
        float fullStamina = power.getMaxStamina();
        power.setStamina(fullStamina);

        Ability timeErase =
                power.getAbility("kingcrimson_timeerase");
        helper.assertTrue(
                timeErase != null,
                "King Crimson Time Erase is missing from the live moveset.");
        if (timeErase == null) {
            return;
        }
        assertTimeEraseHotbarHold(helper, power);
        helper.assertTrue(
                AbilityInput.withConditionCheck(timeErase, player),
                "The server rejected Time Erase before input activation.");

        MobEffectInstance previousInvisibility =
                new MobEffectInstance(
                        MobEffects.INVISIBILITY,
                        200,
                        1,
                        true,
                        true,
                        true);
        helper.assertTrue(
                player.addEffect(previousInvisibility),
                "The pre-existing invisibility setup failed.");
        MobEffectInstance previousFullInvisibility =
                new MobEffectInstance(
                        ModStatusEffects.FULL_INVISIBILITY,
                        200,
                        2,
                        false,
                        false,
                        true);
        helper.assertTrue(
                player.addEffect(previousFullInvisibility),
                "The pre-existing full invisibility setup failed.");
        MobEffectInstance previousStandInvisibility =
                new MobEffectInstance(
                        MobEffects.INVISIBILITY,
                        200,
                        1,
                        true,
                        false,
                        true);
        helper.assertTrue(
                summonedStand.addEffect(
                        previousStandInvisibility),
                "The pre-existing Stand invisibility setup failed.");

        boolean[] inputStarted = {false};

        helper.succeedWhen(() -> {
            if (!inputStarted[0]) {
                helper.assertTrue(
                        LivingComponentAction.getCurEntityAction(
                                summonedStand) == null,
                        "King Crimson was not ready for production input.");
                FriendlyByteBuf extraInput =
                        new FriendlyByteBuf(Unpooled.buffer());
                HeldInputEntry heldInput;
                BufferingState bufferingState =
                        BufferingState.clickCanBuffer();
                try {
                    heldInput = AbilityInput.keyPress(
                            TIME_ERASE_INPUT_KEY,
                            timeErase,
                            player,
                            extraInput,
                            InputMethod.HOLD,
                            0.0F,
                            bufferingState,
                            timeErase.getAbilityId());
                } finally {
                    extraInput.release();
                }
                EntityActionInstance installedAction =
                        LivingComponentAction.getCurEntityAction(
                                summonedStand);
                helper.assertTrue(
                        heldInput != null
                                && !bufferingState.shouldBuffer
                                && installedAction
                                        instanceof TimeEraseAction
                                && installedAction.ability == timeErase,
                        "Production AbilityInput did not install the live "
                                + "Time Erase action.");
                inputStarted[0] = true;
            }
            long gameTime = player.level().getGameTime();
            KingCrimsonTemporalState state =
                    KingCrimsonAddon.temporalState(player);
            helper.assertTrue(
                    state.isErasing(gameTime),
                    "The held server action did not enter Time Erase.");
            helper.assertTrue(
                    power.getStamina()
                            <= fullStamina
                                    - KingCrimsonPhase5Spec
                                            .TIME_ERASE_ACTIVATION_STAMINA,
                    "Time Erase did not consume activation stamina.");
            helper.assertTrue(
                    player.hasEffect(KingCrimsonAddon.TIME_ERASE),
                    "Time Erase did not apply its marker effect.");
            helper.assertTrue(
                    player.hasEffect(MobEffects.INVISIBILITY),
                    "Time Erase did not apply regular invisibility.");
            MobEffectInstance activeFullInvisibility =
                    player.getEffect(
                            ModStatusEffects.FULL_INVISIBILITY);
            helper.assertTrue(
                    activeFullInvisibility != null,
                    "Time Erase did not apply full invisibility.");
            helper.assertTrue(
                    activeFullInvisibility.getAmplifier()
                            == MobEffectInstance.MAX_AMPLIFIER,
                    "Time Erase full invisibility did not reach the "
                            + "1.21.1 amplifier ceiling.");
            MobEffectInstance activeStandInvisibility =
                    summonedStand.getEffect(MobEffects.INVISIBILITY);
            helper.assertTrue(
                    activeStandInvisibility != null
                            && activeStandInvisibility
                                    .getAmplifier() == 0,
                    "Time Erase did not hide the summoned Stand.");
            List<KingCrimsonTimeEraseDecoyEntity> decoys =
                    helper.getLevel().getEntitiesOfClass(
                            KingCrimsonTimeEraseDecoyEntity.class,
                            player.getBoundingBox().inflate(8.0D));
            helper.assertTrue(
                    decoys.size() == 1,
                    "Time Erase did not retain exactly one server decoy.");
            KingCrimsonTimeEraseDecoyEntity decoy = decoys.getFirst();
            StandPower decoyPower = StandPower.get(decoy);
            StandEntity projectionStand = decoyPower != null
                    ? decoyPower.getSummonedStandEntity()
                    : null;
            helper.assertTrue(
                    decoyPower != null
                            && decoyPower.canUsePower()
                            && decoyPower.isSummoned()
                            && projectionStand != null
                            && !projectionStand.isRemoved()
                            && projectionStand.getRemovalReason() == null
                            && projectionStand.getHealth() > 0.0F
                            && !projectionStand.healthLinkedWithUser
                            && projectionStand.summonLockTicks == 0
                            && projectionStand.tickCount < 100,
                    "Time Erase did not retain its projection Stand.");
            fixture.listener().assertLastTemporalSync(
                    helper, player, Mode.TIME_ERASE);
            int activeSyncCount =
                    fixture.listener().temporalSyncCount();
            helper.assertTrue(
                    fixture.listener().transitionCount() == 1
                            && observerFixture.listener()
                                    .transitionCount() == 0,
                    "Activation transition was not owner-only.");
            int activeTransitionCount =
                    fixture.listener().transitionCount();

            AbilityInput.keyRelease(
                    TIME_ERASE_INPUT_KEY, player);

            helper.assertTrue(
                    state.isIdle()
                            && !player.hasEffect(
                                    KingCrimsonAddon.TIME_ERASE),
                    "Releasing the held input did not clear Time Erase.");
            MobEffectInstance restored =
                    player.getEffect(MobEffects.INVISIBILITY);
            helper.assertTrue(
                    restored != null
                            && restored.getAmplifier() == 1
                            && restored.isAmbient()
                            && restored.isVisible()
                            && restored.showIcon(),
                    "Cleanup did not restore the previous invisibility.");
            MobEffectInstance restoredFull = player.getEffect(
                    ModStatusEffects.FULL_INVISIBILITY);
            helper.assertTrue(
                    restoredFull != null
                            && restoredFull.getAmplifier() == 2
                            && !restoredFull.isAmbient()
                            && !restoredFull.isVisible()
                            && restoredFull.showIcon(),
                    "Cleanup did not restore full invisibility.");
            MobEffectInstance restoredStand = summonedStand.getEffect(
                    MobEffects.INVISIBILITY);
            helper.assertTrue(
                    restoredStand != null
                            && restoredStand.getAmplifier() == 1
                            && restoredStand.isAmbient()
                            && !restoredStand.isVisible()
                            && restoredStand.showIcon(),
                    "Cleanup did not restore Stand invisibility.");
            helper.assertTrue(
                    power.getAbilityCooldown(
                            "kingcrimson_timeerase") > 0,
                    "Cleanup did not apply the dynamic cooldown.");
            helper.assertTrue(
                    helper.getLevel()
                            .getEntitiesOfClass(
                                    KingCrimsonTimeEraseDecoyEntity.class,
                                    player.getBoundingBox().inflate(8.0D))
                            .isEmpty(),
                    "Cleanup did not discard the Time Erase decoy.");
            helper.assertTrue(
                    fixture.listener().temporalSyncCount()
                            > activeSyncCount,
                    "Cleanup did not send a temporal state sync.");
            fixture.listener().assertLastTemporalSync(
                    helper, player, Mode.IDLE);
            helper.assertTrue(
                    fixture.listener().transitionCount()
                                    == activeTransitionCount + 1
                            && observerFixture.listener()
                                    .transitionCount() == 1,
                    "Termination transition did not reach nearby players.");
            helper.assertTrue(
                    fixture.listener().startSoundStopCount() == 1
                            && observerFixture.listener()
                                    .startSoundStopCount() == 0,
                    "Time Erase did not stop the owner start sound.");
            helper.assertTrue(
                    fixture.listener().endSoundCount() == 1
                            && observerFixture.listener()
                                    .endSoundCount() == 1,
                    "Time Erase end sound did not reach nearby players.");

            player.discard();
            observerFixture.player().discard();
        });
    }

    @GameTest(
            template = "empty",
            timeoutTicks = 20,
            batch = "timeSkipMobAdvance")
    public static void timeSkipAdvancesCowAndMarksItForSync(
            GameTestHelper helper) {
        ServerPlayerFixture fixture = makeServerPlayer(
                helper,
                "king-crimson-time-skip-gametest",
                new BlockPos(1, 2, 1));
        ServerPlayer player = fixture.player();
        StandPower power = PowerClass.STAND.attachGet(player);
        power.setStand(KingCrimsonAddon.KING_CRIMSON.get());
        power.skipProgression();

        Cow cow = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(
                cow != null,
                "Time Skip target Cow could not be created.");
        if (cow == null) {
            return;
        }

        cow.setPos(
                player.getX() + 4.0D,
                player.getY(),
                player.getZ());
        cow.setDeltaMovement(new Vec3(0.1D, 0.0D, 0.0D));
        boolean cowAdded = helper.getLevel().addFreshEntity(cow);
        helper.assertTrue(
                cowAdded
                        && cow.isAlive()
                        && !cow.isRemoved()
                        && helper.getLevel().getEntity(cow.getId()) == cow,
                "Cow setup did not add a live server entity.");
        helper.assertTrue(
                helper.getLevel().getEntitiesOfClass(
                                Cow.class,
                                player.getBoundingBox().inflate(32.0D),
                                candidate -> candidate.isAlive()
                                        && !candidate.isRemoved())
                        .contains(cow),
                "Cow setup was outside the production Time Skip query.");
        Vec3 startPosition = cow.position();

        helper.assertTrue(
                KingCrimsonTemporalStateService.beginTimeSkip(player),
                "Time Skip could not start for Cow advancement.");
        double movedDistance = cow.position().distanceTo(startPosition);
        helper.assertTrue(
                movedDistance >= 0.25D,
                "Time Skip advanced Cow ticks without real displacement: "
                        + movedDistance + ".");
        helper.assertTrue(
                cow.hurtMarked && cow.hasImpulse,
                "Time Skip did not mark the displaced Cow for sync.");
        fixture.listener().assertLastTemporalSync(
                helper, player, Mode.TIME_SKIP);

        KingCrimsonTemporalStateService.terminate(player);
        cow.discard();
        player.discard();
        helper.succeed();
    }

    // 1.16 kingcrimson_timeskip did not ignore performer stun: a stunned
    // performer (the summoned Stand, else the user) could not time skip.
    @GameTest(
            template = "empty",
            timeoutTicks = 20,
            batch = "timeSkipStun")
    public static void stunnedPerformerCannotTimeSkip(
            GameTestHelper helper) {
        ServerPlayerFixture fixture = makeServerPlayer(
                helper,
                "king-crimson-time-skip-stun",
                new BlockPos(1, 2, 1));
        ServerPlayer player = fixture.player();
        StandPower power = PowerClass.STAND.attachGet(player);
        power.setStand(KingCrimsonAddon.KING_CRIMSON.get());
        power.skipProgression();
        StandEntity stand = null;
        try {
            power.setStamina(power.getMaxStamina());
            float stamina = power.getStamina();
            Ability timeSkip = power.getAbility("kingcrimson_timeskip");
            helper.assertTrue(
                    timeSkip instanceof KingCrimsonTimeSkipAbility,
                    "Time Skip ability is missing: " + timeSkip);

            // No Stand out: the user performs.
            player.addEffect(new MobEffectInstance(
                    ModStatusEffects.STUN, 200));
            assertTimeSkipStunRefused(
                    helper, timeSkip, power, player, "a stunned user");
            player.removeEffect(ModStatusEffects.STUN);

            // Stand out: the Stand performs.
            helper.assertTrue(
                    KingCrimsonAddon.KING_CRIMSON.get()
                            .summon(player, power),
                    "Stand summon failed");
            stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "The summoned Stand is missing");
            stand.addEffect(new MobEffectInstance(
                    ModStatusEffects.STUN, 200));
            helper.assertTrue(
                    ModStatusEffects.isStunned(stand),
                    "King Crimson did not take the stun");
            assertTimeSkipStunRefused(
                    helper, timeSkip, power, player,
                    "a stunned King Crimson");
            helper.assertTrue(
                    power.getStamina() == stamina,
                    "A refused Time Skip spent stamina");
            stand.removeEffect(ModStatusEffects.STUN);

            // Control: with no stunned performer the same press starts.
            ConditionCheck clear = timeSkip.checkConditions(power);
            helper.assertTrue(
                    clear.isPositive(),
                    "Time Skip refused with no stun: "
                            + clear.getWarning());
            BufferingState pressed = pressTimeSkip(timeSkip, player);
            helper.assertTrue(
                    pressed.isActionSuccess
                            && !KingCrimsonAddon.temporalState(player)
                                    .isIdle(),
                    "Time Skip did not start once the stun was gone");
        } finally {
            KingCrimsonTemporalStateService.terminate(player);
            if (stand != null) {
                power.setSummonedStand(null);
                stand.discard();
            }
            player.discard();
        }
        helper.succeed();
    }

    private static void assertTimeSkipStunRefused(
            GameTestHelper helper,
            Ability timeSkip,
            StandPower power,
            ServerPlayer player,
            String who) {
        ConditionCheck check = timeSkip.checkConditions(power);
        helper.assertTrue(
                !check.isPositive()
                        && check.getWarning() != null
                        && check.getWarning().getContents()
                                instanceof TranslatableContents contents
                        && "jojo.message.action_condition.stun"
                                .equals(contents.getKey()),
                "Time Skip was not refused as stunned for "
                        + who + ": " + check.getWarning());
        // The server gate before a key press, then the press itself.
        helper.assertTrue(
                !AbilityInput.withConditionCheck(timeSkip, player),
                "The input gate let Time Skip through for " + who);
        BufferingState pressed = pressTimeSkip(timeSkip, player);
        helper.assertTrue(
                !pressed.isActionSuccess
                        && KingCrimsonAddon.temporalState(player).isIdle(),
                "Time Skip started for " + who);
    }

    private static BufferingState pressTimeSkip(
            Ability timeSkip, ServerPlayer player) {
        BufferingState state = BufferingState.clickCanBuffer();
        FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.buffer());
        try {
            AbilityInput.keyPress(
                    TIME_SKIP_INPUT_KEY, timeSkip, player, input,
                    InputMethod.CLICK, 0.0F, state,
                    timeSkip.getAbilityId());
        } finally {
            input.release();
            AbilityInput.keyRelease(TIME_SKIP_INPUT_KEY, player);
        }
        return state;
    }

    private static ServerPlayerFixture makeServerPlayer(
            GameTestHelper helper) {
        return makeServerPlayer(
                helper,
                "king-crimson-gametest",
                new BlockPos(1, 2, 1));
    }

    private static ServerPlayerFixture makeServerPlayer(
            GameTestHelper helper,
            String name,
            BlockPos relativeSpawn) {
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
        SilentServerPacketListener listener =
                new SilentServerPacketListener(
                        server, connection, player, cookie);
        BlockPos spawn = helper.absolutePos(
                relativeSpawn);
        player.moveTo(
                spawn.getX() + 0.5D,
                spawn.getY(),
                spawn.getZ() + 0.5D);
        level.addNewPlayer(player);
        return new ServerPlayerFixture(player, listener);
    }

    private static void assertTimeEraseHotbarHold(
            GameTestHelper helper, StandPower power) {
        ControlSchemeTemplate template =
                power.getPowerType()
                        .makeDefaultControlSchemeTemplate();
        boolean found = template.groups.values().stream()
                .flatMap(group -> group.hotbars.stream())
                .flatMap(hotbar -> hotbar.slots.stream())
                .map(slot -> slot.get(null))
                .filter(inputs -> inputs != null)
                .anyMatch(inputs ->
                        "kingcrimson_timeerase".equals(
                                inputs.get(InputMethod.HOLD)));
        helper.assertTrue(
                found,
                "The current control template does not expose Time Erase "
                        + "as a held hotbar ability.");
    }

    private static final class SilentServerPacketListener
            extends ServerGamePacketListenerImpl {
        private final List<TemporalStateSyncPayload> temporalSyncs =
                new ArrayList<>();
        private int transitions;
        private int startSoundStops;
        private int endSounds;

        private SilentServerPacketListener(
                MinecraftServer server,
                Connection connection,
                ServerPlayer player,
                CommonListenerCookie cookie) {
            super(server, connection, player, cookie);
        }

        @Override
        public void send(Packet<?> packet) {
            capture(packet);
        }

        @Override
        public void send(
                Packet<?> packet,
                PacketSendListener sendListener) {
            capture(packet);
        }

        private void capture(Packet<?> packet) {
            if (packet instanceof ClientboundCustomPayloadPacket custom) {
                if (custom.payload()
                        instanceof TemporalStateSyncPayload payload) {
                    temporalSyncs.add(payload);
                } else if (custom.payload()
                        instanceof TimeEraseTransitionPayload) {
                    transitions++;
                }
            } else if (packet instanceof ClientboundStopSoundPacket stop
                    && KingCrimsonAddon.id("time_erase_start")
                            .equals(stop.getName())) {
                startSoundStops++;
            } else if (packet instanceof ClientboundSoundPacket sound
                    && sound.getSound().value()
                            == KingCrimsonAddon.TIME_ERASE_END.get()) {
                endSounds++;
            }
        }

        private int temporalSyncCount() {
            return temporalSyncs.size();
        }

        private int transitionCount() {
            return transitions;
        }

        private int startSoundStopCount() {
            return startSoundStops;
        }

        private int endSoundCount() {
            return endSounds;
        }

        private void assertLastTemporalSync(
                GameTestHelper helper,
                ServerPlayer player,
                Mode expectedMode) {
            helper.assertTrue(
                    !temporalSyncs.isEmpty(),
                    "Time Erase did not send a temporal state sync.");
            TemporalStateSyncPayload payload =
                    temporalSyncs.get(temporalSyncs.size() - 1);
            helper.assertTrue(
                    payload.entityId() == player.getId()
                            && payload.entityUuid()
                                    .equals(player.getUUID())
                            && payload.modeOrdinal()
                                    == expectedMode.ordinal(),
                    "The temporal state sync did not match "
                            + expectedMode + ".");
        }
    }

    private record ServerPlayerFixture(
            ServerPlayer player,
            SilentServerPacketListener listener) {}
}
