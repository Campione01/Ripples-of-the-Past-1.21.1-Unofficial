package rotp.addons.stickyfingers.ability;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import rotp.core.event.RipplesAbilityKeyPressEvent;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.util.functions.JojoModUtil;

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.StickyFingersPhase1Spec;
import rotp.addons.stickyfingers.block.StickyFingersZipperBlock;
import rotp.addons.stickyfingers.state.ZipperInGround;
import rotp.addons.stickyfingers.state.ZipperLeaseData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class StickyFingersZipperAbility extends Ability {
    // 1.16 place_zipper was a hold action (holdTick placed while held).
    private static final Map<UUID, HeldZipping> HELD =
            new ConcurrentHashMap<>();
    // 1.16's client power held place_zipper too (setHeldAction on both
    // sides), so the user's own client refused the other actions as well.
    private static final Map<UUID, HeldZipping> CLIENT_HELD =
            new ConcurrentHashMap<>();

    public StickyFingersZipperAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        partsRequired(StandPart.ARMS);
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    // 1.16 zipper actions did not ignore a stunned performer.
    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        return checkPerformerStun(context);
    }

    @Override
    public String getSpriteName(Power<?> context) {
        LivingEntity user =
                context != null ? context.getUser() : null;
        if (user == null) {
            return super.getSpriteName(context);
        }
        Vec3 start = user.getEyePosition();
        Vec3 end = start.add(user.getViewVector(1.0F).scale(
                StickyFingersPhase1Spec.ZIPPER_RANGE));
        BlockHitResult hit = ZipperInGround.clip(
                user.level(), user, new ClipContext(
                        start,
                        end,
                        ClipContext.Block.OUTLINE,
                        ClipContext.Fluid.NONE,
                        user));
        if (hit.getType() == HitResult.Type.BLOCK
                && user.level().getBlockState(hit.getBlockPos())
                        .is(StickyFingersAddon
                                .STICKY_FINGERS_ZIPPER.get())) {
            // 1.16: plain press removes, Shift toggles
            return user.isShiftKeyDown()
                    ? "sticky_fingers_toggle_zipper"
                    : "sticky_fingers_remove_zipper";
        }
        return super.getSpriteName(context);
    }

    @Override
    public HeldInput onKeyPress(
            Level level,
            LivingEntity user,
            FriendlyByteBuf extraClientInput,
            InputMethod inputMethod,
            float clickHoldResolveTime,
            BufferingState bufferingState) {
        bufferingState.isActionSuccess = true;
        if (level.isClientSide()) {
            if (inputMethod != InputMethod.HOLD
                    || !startsPlacingHold(level, user)) {
                return null;
            }
            HeldZipping held = new HeldZipping(null);
            CLIENT_HELD.put(user.getUUID(), held);
            return held;
        }
        BlockPos placingFrom = press(level, user);
        if (placingFrom == null || inputMethod != InputMethod.HOLD) {
            return null;
        }
        // keeps zipping each newly aimed block until the key is released
        HeldZipping held = new HeldZipping(placingFrom);
        HELD.put(user.getUUID(), held);
        return held;
    }

    @Override
    public void onClick(
            Level level,
            LivingEntity user,
            FriendlyByteBuf extraClientInput) {
        press(level, user);
    }

    // Returns the aimed block when the press is a placing press.
    @Nullable
    private static BlockPos press(Level level, LivingEntity user) {
        if (!(level instanceof ServerLevel serverLevel)
                || !(user instanceof ServerPlayer serverPlayer)
                || !StickyFingersAddon.isStickyFingersActive(user)) {
            return null;
        }

        BlockHitResult hitResult = aim(serverLevel, user);
        if (hitResult.getType() != HitResult.Type.BLOCK) {
            reject(serverPlayer);
            return null;
        }

        BlockState targetedState =
                serverLevel.getBlockState(hitResult.getBlockPos());
        boolean targetedZipper = targetedState.is(
                StickyFingersAddon.STICKY_FINGERS_ZIPPER.get());
        boolean targetedOpenZipper =
                targetedZipper
                        && targetedState.getValue(
                                StickyFingersZipperBlock.OPEN);
        ZipperLeaseData.UseResult result =
                ZipperLeaseData.get(serverLevel)
                        .use(serverPlayer, hitResult);
        // 1.16: a press on a zipper is remove/toggle, never a hold
        BlockPos placingFrom = targetedZipper
                ? null
                : hitResult.getBlockPos().immutable();
        if (!result.success()) {
            reject(serverPlayer);
            return placingFrom;
        }
        if (result == ZipperLeaseData.UseResult.OPENED) {
            JojoModUtil.sayVoiceLine(
                    serverPlayer, StickyFingersAddon.BRUNO_UNZIP);
        }
        else if (result == ZipperLeaseData.UseResult.CLOSED
                || (result == ZipperLeaseData.UseResult.RESTORED
                        && targetedOpenZipper)) {
            JojoModUtil.sayVoiceLine(
                    serverPlayer, StickyFingersAddon.BRUNO_ZIP);
        }
        return placingFrom;
    }

    // The client's side of press(): a held press at a block that is no
    // zipper is a placing hold.
    private static boolean startsPlacingHold(
            Level level, LivingEntity user) {
        if (!StickyFingersAddon.isStickyFingersActive(user)) {
            return false;
        }
        Vec3 start = user.getEyePosition();
        Vec3 end = start.add(user.getViewVector(1.0F).scale(
                StickyFingersPhase1Spec.ZIPPER_RANGE));
        BlockHitResult hit = ZipperInGround.clip(
                level, user, new ClipContext(
                        start,
                        end,
                        ClipContext.Block.OUTLINE,
                        ClipContext.Fluid.NONE,
                        user));
        return hit.getType() == HitResult.Type.BLOCK
                && !level.getBlockState(hit.getBlockPos()).is(
                        StickyFingersAddon.STICKY_FINGERS_ZIPPER.get());
    }

    private static BlockHitResult aim(
            ServerLevel serverLevel, LivingEntity user) {
        Vec3 start = user.getEyePosition();
        Vec3 end = start.add(user.getViewVector(1.0F).scale(
                StickyFingersPhase1Spec.ZIPPER_RANGE));
        // skips the open zipper the user stands in (1.16 in-ground zipping)
        BlockHitResult hitResult = ZipperInGround.clip(
                serverLevel, user, new ClipContext(
                        start,
                        end,
                        ClipContext.Block.OUTLINE,
                        ClipContext.Fluid.NONE,
                        user));
        return hitResult;
    }

    // 1.16 holdTick: while held, zip each newly aimed block.
    static void tickHeld(ServerPlayer player) {
        HeldZipping held = HELD.get(player.getUUID());
        if (held == null) {
            return;
        }
        // 1.16 tickHeldAction: key up, lost Stand, a power the user may no
        // longer use (canUsePower: a spectator) or stun ends the hold
        if (!AbilityInput.isHeldByKey(player, held)
                || !player.isAlive()
                || !StickyFingersAddon.isStickyFingersActive(player)
                || !StandPower.get(player).canUsePower()
                || !checkPerformerStun(StandPower.get(player))
                        .isPositive()) {
            HELD.remove(player.getUUID(), held);
            return;
        }
        BlockHitResult hit = aim(player.serverLevel(), player);
        if (hit.getType() != HitResult.Type.BLOCK
                || hit.getBlockPos().equals(held.lastTarget)) {
            return;
        }
        held.lastTarget = hit.getBlockPos().immutable();
        // zippers under the sweep are skipped, as in 1.16
        ZipperLeaseData.get(player.serverLevel()).place(player, hit);
    }

    // 1.16 place_zipper (070 InitStands :87) was a held StandAction whose
    // heldAllowsOtherAction stayed false: while its key was down
    // PowerBaseImpl.checkRequirements (:255) refused every other action of
    // the Stand power, silently. A key handler that was no action
    // (endsOtherHoldOnPress false, manual control) never got there.
    public static boolean refusedByHeldZipper(
            @Nullable LivingEntity user, @Nullable Ability ability) {
        HeldZipping held = user != null
                ? (user.level().isClientSide() ? CLIENT_HELD : HELD)
                        .get(user.getUUID())
                : null;
        return held != null
                && ability != null
                && ability.abilityId.powerClass() == PowerClass.STAND
                && ability.endsOtherHoldOnPress()
                && AbilityInput.isHeldByKey(user, held);
    }

    // 1.16's checkRequirements refused them on both sides: the HUD showed
    // them unusable and the client did not take the press. Set here, after
    // every ability's own condition check and the finisher keys.
    @Override
    public Ability replaceWithSubAbility(
            Power<?> context, AvailableAbilities abilities) {
        LivingEntity user = context != null ? context.getUser() : null;
        for (AbilityConditionCheck other : abilities._inMoveset.values()) {
            if (other.ability != this
                    && refusedByHeldZipper(user, other.ability)) {
                other.conditionCheck = ConditionCheck.NEGATIVE;
            }
        }
        return super.replaceWithSubAbility(context, abilities);
    }

    // the client's hold ends as the server's tickHeld ends it
    private static void tickClientHeld(Player player) {
        HeldZipping held = CLIENT_HELD.get(player.getUUID());
        StandPower power = StandPower.get(player);
        if (held != null
                && (!AbilityInput.isHeldByKey(player, held)
                        || !player.isAlive()
                        || !StickyFingersAddon.isStickyFingersActive(player)
                        || power == null
                        || !power.canUsePower()
                        || !checkPerformerStun(power).isPositive())) {
            CLIENT_HELD.remove(player.getUUID(), held);
        }
    }

    private static final class HeldZipping implements HeldInput {
        private BlockPos lastTarget;

        private HeldZipping(BlockPos lastTarget) {
            this.lastTarget = lastTarget;
        }

        @Override
        public void onKeyRelease(LivingEntity user) {
            HELD.remove(user.getUUID(), this);
            CLIENT_HELD.remove(user.getUUID(), this);
        }
    }

    @EventBusSubscriber(modid = StickyFingersAddon.MOD_ID)
    public static final class HeldTicks {
        private HeldTicks() {}

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                tickHeld(player);
            }
            else if (event.getEntity().level().isClientSide()) {
                tickClientHeld(event.getEntity());
            }
        }

        @SubscribeEvent
        public static void onAbilityKeyPress(
                RipplesAbilityKeyPressEvent event) {
            if (refusedByHeldZipper(event.getEntity(), event.getAbility())) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onLogout(
                PlayerEvent.PlayerLoggedOutEvent event) {
            HELD.remove(event.getEntity().getUUID());
        }
    }

    private static void reject(ServerPlayer player) {
        player.displayClientMessage(
                Component.translatable(
                        "rotp_stfn.message.zipper_rejected"),
                true);
    }
}
