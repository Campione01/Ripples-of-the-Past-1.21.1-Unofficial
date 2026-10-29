package rotp.addons.hermitpurple.ability;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.hermitpurple.HermitPurpleAddon;
import rotp.addons.hermitpurple.entity.HermitPurpleStandEntity;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;

/**
 * A client's copy of a synced action is of its power type's base-moveset instance of the ability
 * (EntityActionType#encodeAbility sends it without its user), not of the user's own one that the client's moveset, keys
 * and HUD hold. 1.16's client asked the same checks of its synced task as the server of its own, so each row builds the
 * action once of the user's own ability, as the server holds it, and once of the base-moveset one, as the client holds
 * it, sets it as the Stand's action and asks what the client asks of it: whether a press waits behind it (the press it
 * predicts, EntityActionAbility#wouldBufferInput) and whether a slot is drawn queued behind it (isQueuedBehindStandTask).
 */
@GameTestHolder("rotp_hp_lash")
@PrefixGameTestTemplate(false)
public final class HermitClientCopyGameTests {
    private HermitClientCopyGameTests() {}

    /*
     * 1.16 hp_camera (061 InitStands:83, HPCamera) was a non-hold StandEntityAction nothing cancelled: another action
     * pressed during it was queued (its slot drawn queued), its own press refused (canClickDuringTask). The port answers
     * the own ability "cancels" and refuses that press first (refusesOwnAbilityPressedAgain).
     */
    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 40, batch = "hp_client_copy")
    public static void spiritCameraTellsItsOwnAbilityOnItsUsersClientAsOnTheServerAsIn116(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, "HPCopyCamera");
        try {
            Ability[] copies = fixture.ownAndBase(HermitPurpleAddon.HP_CAMERA_NAME);
            Ability own = copies[0];
            Ability base = copies[1];
            Ability attack = fixture.power.getAbility(HermitPurpleAddon.HP_VINE_ATTACK_NAME);
            helper.assertTrue(attack instanceof EntityActionAbility, "setup: no vine attack");
            for (Ability of : new Ability[] {own, base}) {
                String side = of == own ? "the server" : "its user's client";
                EntityActionInstance camera = fixture.run(of);
                helper.assertTrue(of == own || camera.ability != own,
                        "setup: the client's copy of hp_camera is the user's own ability, identity would match it");
                helper.assertTrue(camera.canBeCancelledInto((EntityActionType) own)
                                && !((EntityActionType) own).shouldBufferInput(fixture.standAction)
                                && camera.refusesOwnAbilityPressedAgain(),
                        "the server's hp_camera answers its own ability 'cancels' (the press is refused first, not held"
                                + " back); as " + side + " holds it: cancels " + camera.canBeCancelledInto((EntityActionType) own)
                                + " (identity: " + (camera.ability == own) + "), held back "
                                + ((EntityActionType) own).shouldBufferInput(fixture.standAction));
                helper.assertTrue(!camera.canBeCancelledInto((EntityActionType) attack)
                                && ((EntityActionAbility) attack).isQueuedBehindStandTask(fixture.user),
                        "1.16 queued a vine attack pressed during hp_camera and drew its slot queued; as " + side
                                + " holds it: cancels " + camera.canBeCancelledInto((EntityActionType) attack) + ", drawn queued "
                                + ((EntityActionAbility) attack).isQueuedBehindStandTask(fixture.user));
                fixture.clear();
            }
            LogUtils.getLogger().info("R770_HP_CLIENT_COPY_PASS {}", "camera");
        }
        finally {
            fixture.close();
        }
        helper.succeed();
    }

    /*
     * 1.16 hp_block (061 InitStands:75, HPThorns) was a holdType Stand action: any other Stand action replaced it at once
     * (isCancelable), its own press did not (canClickDuringTask). The client predicts a press from the block's answer:
     * with the identity compare its copy let the block's own press replace it at once where the server holds it back.
     * The own press is refused before that (refusesOwnAbilityPressedAgain), on the client's copy as on the server.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 40, batch = "hp_client_copy")
    public static void thornsHoldBackTheirOwnPressOnTheirUsersClientAsOnTheServerAsIn116(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, "HPCopyThorns");
        try {
            Ability[] copies = fixture.ownAndBase(HermitPurpleAddon.HP_BLOCK_NAME);
            Ability own = copies[0];
            Ability base = copies[1];
            Ability attack = fixture.power.getAbility(HermitPurpleAddon.HP_VINE_ATTACK_NAME);
            helper.assertTrue(attack instanceof EntityActionAbility, "setup: no vine attack");
            for (Ability of : new Ability[] {own, base}) {
                String side = of == own ? "the server" : "its user's client";
                EntityActionInstance thorns = fixture.run(of);
                helper.assertTrue(of == own || thorns.ability != own,
                        "setup: the client's copy of hp_block is the user's own ability, identity would match it");
                helper.assertTrue(!thorns.canBeCancelledInto((EntityActionType) own)
                                && ((EntityActionAbility) own).wouldBufferInput(fixture.user),
                        "the server's hp_block holds its own press back (it waits, 1.16 did not let it replace the block);"
                                + " as " + side + " holds it: cancels " + thorns.canBeCancelledInto((EntityActionType) own)
                                + " (identity: " + (thorns.ability != own) + "), the press predicted held back "
                                + ((EntityActionAbility) own).wouldBufferInput(fixture.user));
                fixture.power._curAvailableMoves.update(fixture.power, fixture.power.getMoveset());
                AbilityConditionCheck again = fixture.power.updateAvailableMoves().getContextVariationContainer(own);
                helper.assertTrue(thorns.refusesOwnAbilityPressedAgain() && again != null && again.ability == own
                                && !again.conditionCheck.isPositive() && again.conditionCheck.getWarning() == null,
                        "1.16 refused hp_block pressed again during the block (canClickDuringTask); as " + side
                                + " holds it the key's check is " + (again == null ? "nothing" : again.ability.name()
                                        + (again.conditionCheck.isPositive() ? " positive" : " a refusal")));
                helper.assertTrue(thorns.canBeCancelledInto((EntityActionType) attack)
                                && !((EntityActionAbility) attack).wouldBufferInput(fixture.user),
                        "1.16 let a vine attack replace hp_block at once; as " + side + " holds it: cancels "
                                + thorns.canBeCancelledInto((EntityActionType) attack) + ", the press predicted held back "
                                + ((EntityActionAbility) attack).wouldBufferInput(fixture.user));
                fixture.clear();
                fixture.power._curAvailableMoves.update(fixture.power, fixture.power.getMoveset());
                AbilityConditionCheck idle = fixture.power.updateAvailableMoves().getContextVariationContainer(own);
                helper.assertTrue(idle != null && idle.conditionCheck.isPositive(),
                        "setup: hp_block is refused with no action running, so its refusal above proves nothing");
            }
            LogUtils.getLogger().info("R770_HP_CLIENT_COPY_PASS {}", "thorns");
        }
        finally {
            fixture.close();
        }
        helper.succeed();
    }

    private static final class Fixture {
        final GameTestHelper helper;
        final FakePlayer user;
        final StandPower power;
        final HermitPurpleStandEntity stand;
        final LivingComponentAction standAction;

        Fixture(GameTestHelper helper, String name) {
            this.helper = helper;
            Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
            user = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
            user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
            user.setNoGravity(true);
            helper.assertTrue(helper.getLevel().addFreshEntity(user), "setup: could not add the user");
            power = PowerClass.STAND.attachGet(user);
            try {
                user.setGameMode(GameType.SURVIVAL);
                power.setStand(HermitPurpleAddon.HERMIT_PURPLE.get());
                power.getStandInstance().orElseThrow().addPart(StandPart.ARMS);
                power.skipProgression();
                power.setStamina(power.getMaxStamina());
                helper.assertTrue(HermitPurpleAddon.HERMIT_PURPLE.get().summon(user, power), "setup: Hermit summon failed");
                stand = HermitPurpleAddon.controller(user);
                helper.assertTrue(stand != null, "setup: the summon produced no Hermit controller");
                standAction = LivingComponentAction.getComponent(stand);
            }
            catch (RuntimeException | Error failure) {
                if (power.hasPower() && power.isSummoned()) {
                    power.getPowerType().forceUnsummon(user, power);
                }
                user.discard();
                throw failure;
            }
        }

        Ability[] ownAndBase(String name) {
            Ability own = power.getAbility(name);
            Ability base = power.getPowerType().getBaseMoveset().getAbility(name);
            helper.assertTrue(own instanceof EntityActionAbility && base instanceof EntityActionAbility && base != own
                            && base.getAbilityId().equals(own.getAbilityId()),
                    "setup: the power type's base-moveset " + name + " is not another instance of the user's own: " + base);
            return new Ability[] {own, base};
        }

        EntityActionInstance run(Ability of) {
            EntityActionInstance action = ((EntityActionAbility) of).initActionOnAbilityUse(helper.getLevel(), user, stand, null);
            standAction.setAction(null, user, SyncType.NO_SYNC);
            standAction.setAction(action, user, SyncType.NO_SYNC);
            helper.assertTrue(standAction.getAction() == action && action.ability == of && !action.isOver(),
                    "setup: " + of.name() + " is not the Stand's running action: " + standAction.getAction());
            return action;
        }

        void clear() {
            standAction.setAction(null, user, SyncType.NO_SYNC);
        }

        void close() {
            try {
                clear();
                if (power.hasPower() && power.isSummoned()) {
                    power.getPowerType().forceUnsummon(user, power);
                }
            }
            finally {
                if (!user.isRemoved()) {
                    user.discard();
                }
            }
        }
    }
}
