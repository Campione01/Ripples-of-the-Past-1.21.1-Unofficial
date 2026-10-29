package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.init.ModEntityAttributes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.netcode.TrEntityActionPhaseTimePacket;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.powersystem.standpower.type.StandType;

/**
 * 1.16 clients got each task phase with its length (StandEntityTask serializer), so a throw's PERFORM never ran
 * with a placeholder length and then jumped back. StandEntityLightAttack.onTaskSet alternated the punching arm and
 * showed both arms of an arms-only Stand for an off-hand swing.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonThrowSyncGameTests {
    private static final short INPUT = 195;
    private KingCrimsonThrowSyncGameTests() {}

    enum Kind { STEADY, SPEED_CHANGE, ARMS_ONLY_OFF_HAND }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_throw_sync")
    public static void throwKeepsClientPhasesAndAlternatesTheArm(GameTestHelper helper) {
        KingCrimsonThrowGameTestFixture.identity(helper, KingCrimsonThrowSyncGameTests.class);
        var checks = new KingCrimsonThrowGameTestFixture.Checks();
        int scenes = 0;
        for (String name : List.of(KingCrimsonThrowGameTestFixture.SINGLE, KingCrimsonThrowGameTestFixture.TRIPLE)) {
            for (Kind kind : Kind.values()) {
                scene(helper, checks, name, kind);
                scenes++;
            }
        }
        checks.finish(helper, "KC-THROW-SYNC", scenes);
    }

    private static void scene(GameTestHelper helper, KingCrimsonThrowGameTestFixture.Checks checks, String name, Kind kind) {
        String id = "sync/" + name + "/" + kind;
        ServerLevel level = helper.getLevel();
        Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 24, 1)));
        KingCrimsonChunkTickets chunks = new KingCrimsonChunkTickets(level);
        FakePlayer user = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "kc-throw-sync"));
        Recording connection = new Recording(user);
        StandType type = KingCrimsonAddon.KING_CRIMSON.get();
        StandPower power = null;
        StandEntity stand = null;
        try {
            chunks.force(origin);
            user.moveTo(origin.x, origin.y, origin.z, 0, 0);
            premise(helper, level.addFreshEntity(user), id, "Stand owner added");
            // The owner's chunk counts as sent, so the owner tracks its Stand once the Stand is added.
            connection.chunkSender.dropChunk(user, user.chunkPosition());
            power = PowerClass.STAND.attachGet(user);
            premise(helper, StandPowerTransitions.insert(power, new StandInstance(type)).status()
                    == StandPowerTransitions.Status.APPLIED, id, "Registered KC grant");
            power.skipProgression(); power.setResolveLevel(3);
            premise(helper, type.summon(user, power), id, "Registered KC summon");
            stand = power.getSummonedStandEntity();
            premise(helper, stand != null && stand.isAddedToLevel() && !stand.isArmsOnlyMode(), id, "Full KC body");
            stand.setNoGravity(true); stand.setCanFollowUser(false);
            stand.summonLockTicks = 0; stand.gradualSummonWeaknessTicks = 0;
            user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(14);
            StandEntity.updateStandStatAttributes(stand, user);
            stand.updateStrengthMultipliers(); stand.setFinisherMeter(0);
            power.setStamina(power.getMaxStamina());
            user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ARROW, 5));
            // An idle Stand: its last swing is older than the previous tick.
            stand.tickCount += 10;
            if (kind == Kind.ARMS_ONLY_OFF_HAND) {
                stand.setArmsOnlyMode(true, false);
                stand.alternateHands();
                stand.tickCount += 10;
                premise(helper, stand.isArmsOnlyMode() && !stand.showArm(InteractionHand.OFF_HAND)
                        && stand.getPunchingHand() == InteractionHand.OFF_HAND, id, "Main arm out, the off hand swings next");
            }
            InteractionHand next = stand.getPunchingHand();
            Ability ability = power.getAbility(name);
            var selected = power.updateAvailableMoves().getContextVariationContainer(ability);
            premise(helper, selected != null && selected.ability == ability
                    && AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK), id, "Registered throw admission");
            BufferingState buffer = BufferingState.clickOnly();
            AbilityInput.keyPress(INPUT, ability, user, null, InputMethod.CLICK, 0, buffer, ability.getAbilityId());
            EntityActionInstance action = stand.getCurStandAction();
            premise(helper, buffer.isActionSuccess && action != null && action.ability == ability, id, "Throw started");
            float perform = StandStatFormulas.getLightAttackRecovery(stand.getAttackSpeed(), stand.getFinisherMeter());
            checks.check(action.phasesLength.getFloat(ActionPhase.PERFORM) == perform,
                    id + " the action reaches clients with PERFORM=" + action.phasesLength.getFloat(ActionPhase.PERFORM)
                            + " expected the donor light recovery " + perform);
            InteractionHand other = next == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            checks.check(stand.getPunchingHand() == other,
                    id + " the throw swings the " + next + " arm, the next punch the other one: " + stand.getPunchingHand());
            if (kind == Kind.ARMS_ONLY_OFF_HAND) {
                checks.check(stand.isArmsOnlyMode() && stand.showArm(InteractionHand.OFF_HAND)
                        && stand.showArm(InteractionHand.MAIN_HAND), id + " an off-hand throw of an arms-only Stand shows both arms");
            }
            connection.packets.clear();
            boolean changed = false;
            for (int step = 0; !action.isOver() && step < 200; step++) {
                if (kind == Kind.SPEED_CHANGE && !changed && action.getPhase() == ActionPhase.WINDUP) {
                    user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(28);
                    StandEntity.updateStandStatAttributes(stand, user);
                    stand.updateStrengthMultipliers();
                    changed = true;
                }
                action._tickAction();
            }
            premise(helper, action.isOver(), id, "Throw ran to its end");
            int syncs = phaseSyncs(connection, stand);
            if (kind == Kind.SPEED_CHANGE) {
                checks.check(syncs >= 1, id + " a PERFORM/RECOVERY length changed by the live stats is sent: " + syncs);
            }
            else {
                checks.check(syncs == 0, id + " no phase resync when the entry lengths are unchanged: " + syncs);
            }
        }
        finally {
            AbilityInput.keyRelease(INPUT, user);
            if (stand != null) LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
            if (power != null && power.isSummoned()) type.forceUnsummon(user, power);
            for (Entity projectile : level.getEntitiesOfClass(Projectile.class, new AABB(origin, origin).inflate(64),
                    projectile -> projectile.getOwner() == user)) {
                projectile.discard();
            }
            user.discard();
            chunks.release();
        }
    }

    private static int phaseSyncs(Recording connection, StandEntity stand) {
        int count = 0;
        for (Packet<?> packet : connection.packets) {
            if (packet instanceof ClientboundCustomPayloadPacket custom
                    && custom.payload() instanceof TrEntityActionPhaseTimePacket phaseTime
                    && phaseTime.performerId() == stand.getId()) {
                count++;
            }
        }
        return count;
    }

    private static void premise(GameTestHelper helper, boolean value, String id, String message) {
        helper.assertTrue(value, "KC-THROW-SYNC PREMISE " + id + " " + message);
    }

    private static final class Recording extends ServerGamePacketListenerImpl {
        final List<Packet<?>> packets = new ArrayList<>();

        Recording(FakePlayer player) {
            super(player.getServer(), player.connection.getConnection(), player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        @Override public void send(Packet<?> packet) { packets.add(packet); }
        @Override public void send(Packet<?> packet, PacketSendListener listener) { packets.add(packet); }
    }
}
