package rotp.addons.madeinheaven.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.madeinheaven.MadeInHeavenAddon;
import rotp.addons.madeinheaven.ability.MadeInHeavenChopAbility;
import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.MathUtil;

/**
 * 1.16 041 Chop kept StandEntityHeavyAttack.standPerform -> StandEntity.punch -> aimWithThisOrUser (:1695-1700): before
 * the hit the Stand turned toward the eyes of what the strike aimed at, its yaw and pitch each by at most
 * attackSpeed / 16 x 18 degrees, its head onto the new yaw; with nothing in reach, toward what its user's precision ray
 * met, which the strike then missed.
 */
@GameTestHolder("rotp_mih_melee")
@PrefixGameTestTemplate(false)
public final class MadeInHeavenChopTurnGameTests {
    private static final short ATTACK = 161;
    private static final PowerSkillUnlocks.Owner UNLOCKS =
            PowerSkillUnlocks.register(MadeInHeavenAddon.id("chop_turn_tests"));

    private MadeInHeavenChopTurnGameTests() {}

    @GameTest(template = "empty", templateNamespace = "rotp_mih_melee", timeoutTicks = 100, batch = "mihChopTurn")
    public static void chopTurnsTheStandTowardWhatItStrikesAsIn116(GameTestHelper helper) {
        List<String> failures = new ArrayList<>();
        for (boolean beyondReach : new boolean[] {false, true}) {
            strike(helper, beyondReach, failures);
        }
        helper.assertTrue(failures.isEmpty(), String.join("; ", failures));
        helper.succeed();
    }

    private static void strike(GameTestHelper helper, boolean beyondReach, List<String> failures) {
        String row = beyondReach ? "a Chop pressed at nothing, its user aiming at a cow beyond the Stand's reach"
                : "a Chop at a cow in reach";
        ServerLevel level = helper.getLevel();
        ChunkPos chunk = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
        Vec3 origin = new Vec3(chunk.getMiddleBlockX() + 0.5, helper.absolutePos(BlockPos.ZERO).getY() + 8,
                chunk.getMiddleBlockZ() + 0.5);
        StandType type = MadeInHeavenAddon.MADE_IN_HEAVEN.get();
        Player user = new Player(level, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "mih-chop-turn")) {
            @Override public boolean isCreative() { return false; }
            @Override public boolean isSpectator() { return false; }
        };
        StandPower power = null;
        Cow cow = null;
        Object[] listener = {null};
        try {
            user.noPhysics = true;
            user.setNoGravity(true);
            user.moveTo(origin.x, origin.y, origin.z, 0, 0);
            user.setYHeadRot(0);
            helper.assertTrue(level.addFreshEntity(user), "setup: the user was not added");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
                    == StandPowerTransitions.Status.APPLIED, "setup: Made in Heaven was not granted");
            power.setResolveLevel(6);
            helper.assertTrue(type.summon(user, power), "setup: Made in Heaven was not summoned");
            StandEntity stand = power.getSummonedStandEntity();
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
            stand.setCanFollowUser(false);
            stand.setNoGravity(true);
            stand.noPhysics = true;
            stand.moveTo(origin.x, origin.y, origin.z, 0, 0);
            stand.setYHeadRot(0);
            user.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(15);
            user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(16);
            user.getAttribute(ModEntityAttributes.STAND_DURABILITY).setBaseValue(12);
            StandEntity.updateStandStatAttributes(stand, user);
            stand.updateStrengthMultipliers();
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = true;
            var unlock = UNLOCKS.forceUnlock(power, "chop");
            helper.assertTrue(unlock == PowerSkillUnlocks.Result.UNLOCKED
                    || unlock == PowerSkillUnlocks.Result.ALREADY_UNLOCKED, "setup: Chop was not unlocked");
            power.setStamina(power.getMaxStamina());
            stand.setFinisherMeter(0.75F);

            double reach = stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
            Vec3 side = new Vec3(0.6, 0, 1).normalize();
            cow = EntityType.COW.create(level);
            Vec3 at = origin.add(side.scale(beyondReach ? reach + 1.0 : 1.5));
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
            cow.setHealth(200);
            cow.moveTo(at.x, at.y, at.z, 0, 0);
            helper.assertTrue(level.addFreshEntity(cow), "setup: the cow was not added");
            user.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getEyePosition());
            ActionTarget pressed = beyondReach ? ActionTarget.EMPTY : new ActionTarget(cow);
            if (beyondReach) {
                helper.assertTrue(StandEntityPunchAbility.userPrecisionRayTarget(stand, user).getEntity() == cow
                        && StandEntityPunchAbility.validatePunchTarget(stand, new ActionTarget(cow)).isEmpty(level),
                        "setup: the user's precision ray does not meet the cow beyond the Stand's reach " + reach);
            }
            LivingComponentAction.getComponent(user).entityAim.setTarget(pressed);
            LivingComponentAction.getComponent(stand).entityAim.setTarget(pressed);

            Ability base = power.getAbility("heavy_punch");
            helper.assertTrue(base != null, "setup: no heavy_punch");
            var selected = power.updateAvailableMoves().getContextVariationContainer(base);
            helper.assertTrue(selected != null && AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK),
                    "setup: the heavy key was refused (" + row + ")");
            BufferingState buffer = BufferingState.clickOnly();
            var entry = AbilityInput.keyPress(ATTACK, selected.ability, user, null, InputMethod.CLICK, 0, buffer,
                    base.getAbilityId());
            EntityActionInstance action = entry != null && entry.action instanceof EntityActionInstance started
                    ? started : stand.getCurStandAction();
            helper.assertTrue(buffer.isActionSuccess && action instanceof MadeInHeavenChopAbility.MadeInHeavenChop,
                    "setup: the heavy key did not start the Chop (" + row + "): " + action);
            for (int tick = 0; action.getPhase() == ActionPhase.WINDUP && tick < 100; tick++) {
                action._tickAction();
            }
            helper.assertTrue(action.getPhase() == ActionPhase.PERFORM && action.punchedTarget == null,
                    "setup: the Chop did not reach its perform (" + row + ")");

            Vec3 toEyes = cow.getEyePosition().subtract(stand.getEyePosition());
            float aimYaw = MathUtil.yRotDegFromVec(toEyes);
            float aimPitch = MathUtil.xRotDegFromVec(toEyes);
            stand.setYRot(aimYaw + 60);
            stand.setXRot(aimPitch + 40);
            stand.setYHeadRot(aimYaw + 60);
            float yaw0 = stand.getYRot();
            float pitch0 = stand.getXRot();
            float max = (float) stand.getAttackSpeed() / 16 * 18;
            helper.assertTrue(max > 0 && max < 40, "setup: the Stand turns by up to " + max + " degrees");
            float yaw = yaw0 + Mth.clamp(Mth.degreesDifference(yaw0, aimYaw), -max, max);
            float pitch = pitch0 + Mth.clamp(Mth.degreesDifference(pitch0, aimPitch), -max, max);
            float[] atHit = {Float.NaN, Float.NaN};
            Cow struck = cow;
            java.util.function.Consumer<LivingIncomingDamageEvent> onHit = event -> {
                if (event.getEntity() == struck && Float.isNaN(atHit[0])) {
                    atHit[0] = stand.getYRot();
                    atHit[1] = stand.getXRot();
                }
            };
            listener[0] = onHit;
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LivingIncomingDamageEvent.class, onHit);
            action._tickAction();
            helper.assertTrue(action.punchedTarget != null, "setup: the Chop did not perform (" + row + ")");
            String what = row + " (Stand at yaw " + yaw0 + ", pitch " + pitch0 + ", turning by up to " + max + " degrees)";
            if (beyondReach) {
                helper.assertTrue(Float.isNaN(atHit[0]) && cow.getHealth() == cow.getMaxHealth(),
                        "setup: " + what + " hit the cow");
            }
            else {
                helper.assertTrue(action.punchedTarget.getMainEntity() == cow && !Float.isNaN(atHit[0]),
                        "setup: " + what + " did not strike the cow");
                if (!(near(atHit[0], yaw) && near(atHit[1], pitch))) {
                    failures.add("1.16 turned " + what + " toward the cow's eyes before the hit, to yaw " + yaw
                            + ", pitch " + pitch + "; it struck at yaw " + atHit[0] + ", pitch " + atHit[1]);
                }
            }
            if (!(near(stand.getYRot(), yaw) && near(stand.getXRot(), pitch) && near(stand.getYHeadRot(), yaw))) {
                failures.add("1.16 turned " + what + " toward the cow's eyes, to yaw " + yaw + ", pitch " + pitch
                        + " and its head onto that yaw; it is at yaw " + stand.getYRot() + ", pitch " + stand.getXRot()
                        + ", head " + stand.getYHeadRot());
            }
        }
        finally {
            if (listener[0] != null) {
                NeoForge.EVENT_BUS.unregister(listener[0]);
            }
            AbilityInput.keyRelease(ATTACK, user);
            if (power != null && power.isSummoned()) {
                type.forceUnsummon(user, power);
            }
            if (cow != null) {
                cow.discard();
            }
            user.discard();
        }
    }

    private static boolean near(float angle, float expected) {
        return Math.abs(Mth.degreesDifference(angle, expected)) < 1.0E-3F;
    }
}
