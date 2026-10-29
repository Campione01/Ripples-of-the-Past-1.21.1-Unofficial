package rotp.addons.ultimate_lifeform;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ultimate_lifeform.action.UltimateLifeformCombat;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformData;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformSpec;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;

/**
 * 1.16 PowerBaseImpl.tickHeldAction re-ran NonStandAction.checkConditions on
 * every tick an Ultimate Lifeform action was held: the energy the hold still
 * needed, the hands, the action's own conditions and the mode. A failure ended
 * the hold and sent its message.
 */
@GameTestHolder(UltimateLifeformAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UltimateHeldRecheckGameTests {
    private UltimateHeldRecheckGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void lightFlashChargeEndsOnceTheEnergyFallsShort(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateHeldFlash",
                "0b6f3c52-8e14-4d27-a9c1-5d2e7f8a1b31");
        holder.data.setPreviousMode(holder.player, PillarmanMode.LIGHT);
        holder.data.setCommandEnergy(holder.player,
                UltimateLifeformData.MAX_ENERGY);
        helper.runAfterDelay(1, () -> holder.press(
                helper, UltimateLifeformSpec.LIGHT_FLASH, (short) 32_100));
        helper.runAfterDelay(5, () -> holder.data.setCommandEnergy(
                holder.player, 100.0F));
        helper.runAfterDelay(8, () -> holder.data.setCommandEnergy(
                holder.player, UltimateLifeformData.MAX_ENERGY));
        helper.runAfterDelay(55, () -> {
            helper.assertTrue(
                    !holder.data.isAbilityOnCooldown(
                                    UltimateLifeformSpec.LIGHT_FLASH.id)
                            && !holder.isRunning(
                                    UltimateLifeformSpec.LIGHT_FLASH),
                    "1.16 ended the Light Flash charge once the energy fell"
                            + " below its 500; it charged on and fired");
            helper.assertTrue(
                    holder.saw("no_energy_ultimate_lifeform"),
                    "1.16 ended the Light Flash charge with the energy"
                            + " message, got " + holder.messages);
            holder.close((short) 32_100);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void evasionHoldEndsWithTheEnergyMessage(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateHeldEvasion",
                "4c1d8a27-3b6e-4f90-8d25-a7e9c3b0f412");
        helper.runAfterDelay(1, () -> holder.press(
                helper, UltimateLifeformSpec.EVASION, (short) 32_101));
        helper.runAfterDelay(5, () -> helper.assertTrue(
                holder.data.getHeldAbility() == UltimateLifeformSpec.EVASION,
                "Premise: Evasion did not start"));
        for (int tick = 6; tick <= 12; tick++) {
            helper.runAfterDelay(tick, () -> holder.data.setCommandEnergy(
                    holder.player, 0.0F));
        }
        helper.runAfterDelay(14, () -> {
            helper.assertTrue(
                    holder.data.getHeldAbility() == null
                            && !holder.isRunning(UltimateLifeformSpec.EVASION),
                    "Premise: Evasion went on without its held energy");
            helper.assertTrue(
                    holder.saw("no_energy_ultimate_lifeform"),
                    "1.16 ended the Evasion hold that could not pay its 15"
                            + " energy with the energy message, got "
                            + holder.messages);
            holder.close((short) 32_101);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void barrageHoldEndsWhenAHandFills(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateHeldHands",
                "9e2a4f61-7c3b-4d18-b5e0-2f8d6a1c9e73");
        helper.runAfterDelay(1, () -> holder.press(
                helper, UltimateLifeformSpec.ULTIMATE_BARRAGE,
                (short) 32_102));
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(
                    holder.data.getHeldAbility()
                            == UltimateLifeformSpec.ULTIMATE_BARRAGE,
                    "Premise: the Ultimate Barrage did not start");
            holder.player.setItemInHand(
                    InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
        });
        helper.runAfterDelay(9, () -> {
            helper.assertTrue(
                    holder.data.getHeldAbility() == null
                            && !holder.isRunning(
                                    UltimateLifeformSpec.ULTIMATE_BARRAGE),
                    "1.16 ended the held Ultimate Barrage once a hand was"
                            + " filled; it went on");
            helper.assertTrue(
                    holder.saw("hands"),
                    "1.16 ended the held Ultimate Barrage with the hands"
                            + " message, got " + holder.messages);
            holder.close((short) 32_102);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void unlimitedPowerHoldEndsOnceTheEnergyIsFull(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateHeldPower",
                "2d7b9e14-6a3f-4c85-91d2-e4b8f0a7c635");
        holder.data.setCommandEnergy(holder.player, 9_000.0F);
        helper.runAfterDelay(1, () -> holder.press(
                helper, UltimateLifeformSpec.UNLIMITED_POWER,
                (short) 32_103));
        helper.runAfterDelay(5, () -> helper.assertTrue(
                holder.data.getHeldAbility()
                        == UltimateLifeformSpec.UNLIMITED_POWER,
                "Premise: Unlimited Power did not start"));
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(
                    holder.data.getHeldAbility() == null
                            && !holder.isRunning(
                                    UltimateLifeformSpec.UNLIMITED_POWER),
                    "1.16 UltimateLifeformUnlimitedPower.checkSpecificConditions"
                            + " ended the hold once the energy was full; it"
                            + " went on at " + holder.data.getEnergy());
            helper.assertTrue(
                    holder.saw("unlimited_power"),
                    "1.16 ended the full Unlimited Power hold with its"
                            + " message, got " + holder.messages);
            holder.close((short) 32_103);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void absoluteImmortalityHoldEndsAtFullHealthWithAbsorptionVI(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateHeldImmortal",
                "7f3c2b85-1e9d-4a60-8c47-b6d0e2f5a918");
        // the Ultimate health boost raises the maximum above the spawn health
        helper.runAfterDelay(1, () -> {
            holder.player.setHealth(holder.player.getMaxHealth());
            holder.press(helper, UltimateLifeformSpec.ABSOLUTE_IMMORTALITY,
                    (short) 32_104);
        });
        helper.runAfterDelay(3, () -> {
            holder.player.setHealth(holder.player.getMaxHealth());
            helper.assertTrue(
                    holder.data.getHeldAbility()
                            == UltimateLifeformSpec.ABSOLUTE_IMMORTALITY,
                    "Premise: Absolute Immortality did not start");
        });
        helper.runAfterDelay(45, () -> {
            MobEffectInstance absorption =
                    holder.player.getEffect(MobEffects.ABSORPTION);
            helper.assertTrue(
                    absorption != null && absorption.getAmplifier() == 5,
                    "Premise: the hold did not raise Absorption to VI: "
                            + absorption + " health="
                            + holder.player.getHealth() + "/"
                            + holder.player.getMaxHealth() + " held="
                            + holder.data.getHeldAbility());
            helper.assertTrue(
                    holder.data.getHeldAbility() == null
                            && !holder.isRunning(
                                    UltimateLifeformSpec.ABSOLUTE_IMMORTALITY),
                    "1.16 UltimateLifeformAbsoluteImmortality"
                            + ".checkSpecificConditions ended the hold at full"
                            + " health with Absorption VI; it went on");
            helper.assertTrue(
                    holder.saw("absolute_immortality"),
                    "1.16 ended the Absolute Immortality hold with its"
                            + " message, got " + holder.messages);
            holder.close((short) 32_104);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 90, batch = "ulf_own_divine_sandstorm_pays_three_energy_a_held_ti")
    public static void divineSandstormPaysThreeEnergyAHeldTickOnceCharged(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateHeldSandstorm",
                "5a8e1d36-2f4c-4b97-a3e0-c9d7f1b2e846");
        holder.data.setPreviousMode(holder.player, PillarmanMode.WIND);
        // no sunlight regeneration under the Ultimate armor
        holder.data.setArmorType(
                holder.player, UltimateLifeformData.ArmorType.NORMAL);
        holder.data.setCommandEnergy(holder.player, 1_000.0F);
        float[] energy = new float[2];
        helper.runAfterDelay(1, () -> holder.press(
                helper, UltimateLifeformSpec.DIVINE_SANDSTORM,
                (short) 32_105));
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(
                    holder.data.getHeldAbility()
                            == UltimateLifeformSpec.DIVINE_SANDSTORM,
                    "Premise: the Divine Sandstorm did not get past its"
                            + " charge");
            energy[0] = holder.data.getEnergy();
        });
        helper.runAfterDelay(60, () -> {
            energy[1] = holder.data.getEnergy();
            float paid = energy[0] - energy[1];
            helper.assertTrue(
                    paid >= 27.0F && paid <= 33.0F,
                    "1.16 PillarmanDivineSandstorm cost 3 energy each held"
                            + " tick once charged; ten ticks cost " + paid);
            holder.data.setCommandEnergy(holder.player, 4.0F);
        });
        helper.runAfterDelay(66, () -> {
            helper.assertTrue(
                    holder.data.getHeldAbility() == null
                            && !holder.isRunning(
                                    UltimateLifeformSpec.DIVINE_SANDSTORM),
                    "1.16 ended the Divine Sandstorm hold once it could not"
                            + " pay its 3 energy; it went on at "
                            + holder.data.getEnergy());
            helper.assertTrue(
                    holder.saw("no_energy_ultimate_lifeform"),
                    "1.16 ended the drained Divine Sandstorm with the energy"
                            + " message, got " + holder.messages);
            holder.close((short) 32_105);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 30, batch = "ulf_own_divine_sandstorm_charge_ends_with_no_energy")
    public static void divineSandstormChargeEndsWithNoEnergyLeft(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateHeldExhausted",
                "c4e7a913-5b2d-4f08-96a1-3d8f0e6b7c52");
        holder.data.setPreviousMode(holder.player, PillarmanMode.WIND);
        holder.data.setArmorType(
                holder.player, UltimateLifeformData.ArmorType.NORMAL);
        holder.data.setCommandEnergy(holder.player, 0.0F);
        helper.runAfterDelay(1, () -> holder.press(
                helper, UltimateLifeformSpec.DIVINE_SANDSTORM,
                (short) 32_106));
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(
                    !holder.isRunning(UltimateLifeformSpec.DIVINE_SANDSTORM),
                    "1.16 PillarmanDivineSandstorm.checkSpecificConditions"
                            + " ended a held charge with no energy at all; it"
                            + " charged on");
            helper.assertTrue(
                    holder.saw("no_energy_pillarman"),
                    "1.16 ended the empty Divine Sandstorm charge with"
                            + " no_energy_pillarman, got " + holder.messages);
            holder.close((short) 32_106);
            helper.succeed();
        });
    }

    // 1.16 ActionsOverlayGui.renderActionIcon (:1157-1160) filled a held
    // action's slot green once held its holdToFire length; the sandstorm and
    // the rift (040 holdToFire(40, true)) went on sending waves from their
    // 40th held tick until let go, green all that time, and none before.
    @GameTest(template = "empty", timeoutTicks = 80, batch = "ulf_own_sandstorm_and_rift_are_ready_to_fire_once_ch")
    public static void sandstormAndRiftAreReadyToFireOnceChargedAsIn116(
            GameTestHelper helper) {
        Holder sandstorm = holder(helper, "UltimateGreenSandstorm",
                "7d2c9e41-6a3b-4f58-8e1d-2b4c6a8f0e13");
        Holder rift = holder(helper, "UltimateGreenRift",
                "8e3d0f52-7b4c-4069-9f2e-3c5d7b901f24");
        short sandstormKey = (short) 32_500;
        short riftKey = (short) 32_501;
        for (Holder holder : List.of(sandstorm, rift)) {
            holder.data.setPreviousMode(holder.player, PillarmanMode.WIND);
            holder.data.setArmorType(
                    holder.player, UltimateLifeformData.ArmorType.NORMAL);
        }
        helper.runAfterDelay(1, () -> {
            sandstorm.press(helper, UltimateLifeformSpec.DIVINE_SANDSTORM,
                    sandstormKey);
            rift.press(helper, UltimateLifeformSpec.ATMOSPHERIC_RIFT, riftKey);
        });
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(
                    sandstorm.isRunning(UltimateLifeformSpec.DIVINE_SANDSTORM)
                            && rift.isRunning(
                                    UltimateLifeformSpec.ATMOSPHERIC_RIFT),
                    "Premise: the sandstorm or the rift is not charging");
            helper.assertTrue(
                    !sandstorm.readyToFire(UltimateLifeformSpec.DIVINE_SANDSTORM)
                            && !rift.readyToFire(
                                    UltimateLifeformSpec.ATMOSPHERIC_RIFT),
                    "1.16 drew no green before the 40th held tick: sandstorm="
                            + sandstorm.readyToFire(
                                    UltimateLifeformSpec.DIVINE_SANDSTORM)
                            + " rift=" + rift.readyToFire(
                                    UltimateLifeformSpec.ATMOSPHERIC_RIFT));
        });
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(
                    sandstorm.data.getHeldAbility()
                                    == UltimateLifeformSpec.DIVINE_SANDSTORM
                            && rift.data.getHeldAbility()
                                    == UltimateLifeformSpec.ATMOSPHERIC_RIFT,
                    "Premise: the sandstorm or the rift did not get past its"
                            + " charge");
            helper.assertTrue(
                    sandstorm.readyToFire(UltimateLifeformSpec.DIVINE_SANDSTORM)
                            && rift.readyToFire(
                                    UltimateLifeformSpec.ATMOSPHERIC_RIFT),
                    "1.16 filled the slot green while the charged sandstorm"
                            + " and rift sent their waves: sandstorm="
                            + sandstorm.readyToFire(
                                    UltimateLifeformSpec.DIVINE_SANDSTORM)
                            + " rift=" + rift.readyToFire(
                                    UltimateLifeformSpec.ATMOSPHERIC_RIFT));
            AbilityInput.keyRelease(sandstormKey, sandstorm.player);
            AbilityInput.keyRelease(riftKey, rift.player);
        });
        helper.runAfterDelay(53, () -> {
            helper.assertTrue(
                    !sandstorm.isRunning(UltimateLifeformSpec.DIVINE_SANDSTORM)
                            && !rift.isRunning(
                                    UltimateLifeformSpec.ATMOSPHERIC_RIFT)
                            && !sandstorm.readyToFire(
                                    UltimateLifeformSpec.DIVINE_SANDSTORM)
                            && !rift.readyToFire(
                                    UltimateLifeformSpec.ATMOSPHERIC_RIFT),
                    "1.16 ended the hold and its green when the key was let"
                            + " go");
            sandstorm.close(sandstormKey);
            rift.close(riftKey);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void bladeBarrageHoldEndsWhenTheModeChanges(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateHeldMode",
                "e1b5d724-9c3a-4e86-b0f7-6a2c8d4e1f95");
        holder.data.setPreviousMode(holder.player, PillarmanMode.LIGHT);
        helper.runAfterDelay(1, () -> holder.press(
                helper, UltimateLifeformSpec.BLADE_BARRAGE, (short) 32_107));
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(
                    holder.data.getHeldAbility()
                            == UltimateLifeformSpec.BLADE_BARRAGE,
                    "Premise: the Blade Barrage did not start");
            holder.data.setPreviousMode(holder.player, PillarmanMode.WIND);
        });
        helper.runAfterDelay(9, () -> {
            helper.assertTrue(
                    holder.data.getHeldAbility() == null
                            && !holder.isRunning(
                                    UltimateLifeformSpec.BLADE_BARRAGE),
                    "1.16 isUnlocked ended the held Blade Barrage once the"
                            + " mode was no longer Light; it went on");
            helper.assertTrue(
                    holder.saw("wrong_ultimate_lifeform_mode"),
                    "the held Blade Barrage ended without the mode message,"
                            + " got " + holder.messages);
            holder.close((short) 32_107);
            helper.succeed();
        });
    }

    // 1.16 PowerBaseImpl.checkTarget ran before the conditions:
    // UltimateLifeformAbsorbtion (TargetRequirement.ENTITY, 2 blocks) could
    // not be pressed without an entity in reach.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void absorptionPressNeedsAnEntityInReach(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateAbsorbPress",
                "3e9b7d10-4a2c-4f85-b6e1-8d0c5a2f7b49");
        Ability absorption = holder.power.getAbility(
                UltimateLifeformSpec.ABSORPTION.id);
        helper.assertTrue(absorption != null,
                "Premise: no absorption in the moveset");
        holder.player.setXRot(-90.0F);
        var empty = absorption.checkConditions(holder.power);
        helper.assertTrue(
                !empty.isPositive() && empty.getWarning() == null,
                "1.16 refused Absorption pressed with no entity under the"
                        + " crosshair, without a message: got "
                        + (empty.isPositive() ? "admitted"
                                : String.valueOf(empty.getWarning())));
        Cow far = cow(helper, new Vec3(2.5D, 2.0D, 6.5D));
        holder.aim(far);
        var tooFar = absorption.checkConditions(holder.power);
        helper.assertTrue(
                !tooFar.isPositive() && isWarning(tooFar, "target_too_far"),
                "1.16 refused Absorption at a cow 4 blocks away with"
                        + " target_too_far: got "
                        + (tooFar.isPositive() ? "admitted"
                                : tooFar.getWarning()));
        far.setPos(far.getX(), far.getY(), far.getZ() - 2.75D);
        helper.assertTrue(
                absorption.checkConditions(holder.power).isPositive(),
                "Premise: Absorption at a cow 1.25 blocks away was refused");
        far.discard();
        holder.close((short) 32_108);
        helper.succeed();
    }

    // 1.16 Absorption (PillarmanAbsorption.getTargetRequirement ENTITY) and
    // every held action took the client's mouse target, the vanilla pick with
    // no margin: a cow just beside an empty crosshair was no target. The
    // port's target_too_far for a cow farther along the look is kept.
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void absorptionTakesOnlyTheEntityUnderTheCrosshairAsIn116(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateAbsorbPick",
                "5b2e9c71-0d4f-4a63-8e15-c7a9d3f2b086");
        Ability absorption = holder.power.getAbility(
                UltimateLifeformSpec.ABSORPTION.id);
        helper.assertTrue(absorption != null,
                "Premise: no absorption in the moveset");
        // 1.25 blocks ahead, its side 0.15 beside the look line
        Cow cow = cow(helper, new Vec3(3.1D, 2.0D, 3.75D));
        float[] health = new float[1];
        helper.runAfterDelay(1, () -> {
            lookBeside(holder, cow);
            Entity target = UltimateLifeformCombat.absorptionTarget(
                    holder.player, holder.data);
            helper.assertTrue(target == null,
                    "1.16 Absorption took only the entity under the"
                            + " crosshair; with none, the cow beside it was"
                            + " taken: " + target);
            var beside = absorption.checkConditions(holder.power);
            helper.assertTrue(
                    !beside.isPositive() && beside.getWarning() == null,
                    "1.16 refused Absorption with nothing under the"
                            + " crosshair and a cow beside it, without a"
                            + " message: got "
                            + (beside.isPositive() ? "admitted"
                                    : String.valueOf(beside.getWarning())));
            // 4 blocks east, away from the first cow's margin
            Cow ahead = cow(helper, new Vec3(6.5D, 2.0D, 2.5D));
            LivingComponentAction.getComponent(holder.player)
                    .entityAim.setTarget(ActionTarget.EMPTY);
            holder.player.lookAt(EntityAnchorArgument.Anchor.EYES,
                    ahead.getBoundingBox().getCenter());
            var tooFar = absorption.checkConditions(holder.power);
            helper.assertTrue(
                    !tooFar.isPositive() && isWarning(tooFar, "target_too_far"),
                    "the port's kept target_too_far for a cow 4 blocks along"
                            + " the look past the pick: got "
                            + (tooFar.isPositive() ? "admitted"
                                    : tooFar.getWarning()));
            ahead.discard();
            holder.aim(cow);
            holder.press(helper, UltimateLifeformSpec.ABSORPTION,
                    (short) 32_111);
        });
        for (int tick = 1; tick <= 14; tick++) {
            helper.runAfterDelay(tick, () -> cow.invulnerableTime = 0);
        }
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(
                    holder.data.getHeldAbility()
                                    == UltimateLifeformSpec.ABSORPTION
                            && cow.getHealth() < 1_000.0F,
                    "Premise: Absorption did not drain the aimed cow");
            lookBeside(holder, cow);
        });
        helper.runAfterDelay(6, () -> health[0] = cow.getHealth());
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(
                    holder.data.getHeldAbility()
                            == UltimateLifeformSpec.ABSORPTION,
                    "Premise: the Absorption hold ended with "
                            + holder.messages);
            helper.assertTrue(cow.getHealth() == health[0],
                    "1.16 held Absorption drained only the entity under the"
                            + " crosshair; the cow beside an empty one lost "
                            + (health[0] - cow.getHealth()));
            AbilityInput.keyRelease((short) 32_111, holder.player);
        });
        helper.runAfterDelay(14, () -> {
            lookBeside(holder, cow);
            holder.press(helper, UltimateLifeformSpec.EVASION,
                    (short) 32_112);
        });
        helper.runAfterDelay(18, () -> {
            helper.assertTrue(
                    holder.data.getHeldAbility()
                            == UltimateLifeformSpec.EVASION,
                    "Premise: Evasion did not start");
            LivingEntity held = holder.data.getHeldTarget(
                    holder.player, 100.0D);
            helper.assertTrue(held == null,
                    "1.16 held the mouse target; pressed with nothing under"
                            + " the crosshair, the held target was the cow"
                            + " beside it: " + held);
            cow.discard();
            holder.close((short) 32_112);
            helper.succeed();
        });
    }

    // the aim the client sends is empty (its pick missed) while the look
    // passes 0.15 beside the cow's side, inside a 0.3 pick margin
    private static void lookBeside(Holder holder, Cow cow) {
        LivingComponentAction.getComponent(holder.player)
                .entityAim.setTarget(ActionTarget.EMPTY);
        AABB box = cow.getBoundingBox();
        holder.player.lookAt(EntityAnchorArgument.Anchor.EYES,
                new Vec3(box.minX - 0.15D,
                        box.getCenter().y,
                        box.getCenter().z));
    }

    // Each held tick asked the target first: without one in reach the
    // hold-only Absorption went on without absorbing and nothing else was
    // asked; with one, a filled hand ended it.
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void absorptionHoldPausesWithoutATargetAndChecksTheHandWithOne(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateAbsorbHold",
                "8a4f2c63-d1e7-4b09-95c3-2e6b7f0d1a84");
        Cow cow = cow(helper, new Vec3(2.5D, 2.0D, 3.75D));
        float[] health = new float[1];
        helper.runAfterDelay(1, () -> {
            holder.aim(cow);
            holder.press(helper, UltimateLifeformSpec.ABSORPTION,
                    (short) 32_109);
        });
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(
                    holder.data.getHeldAbility()
                                    == UltimateLifeformSpec.ABSORPTION
                            && cow.getHealth() < 1_000.0F,
                    "Premise: Absorption did not drain the cow in reach");
            cow.setPos(cow.getX(), cow.getY(), cow.getZ() + 1.75D);
            holder.aim(cow);
        });
        helper.runAfterDelay(8, () -> {
            health[0] = cow.getHealth();
            holder.player.setItemInHand(
                    InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
        });
        // no hurt cooldown hides an absorption
        for (int tick = 8; tick <= 14; tick++) {
            helper.runAfterDelay(tick, () -> cow.invulnerableTime = 0);
        }
        helper.runAfterDelay(14, () -> {
            helper.assertTrue(
                    holder.data.getHeldAbility()
                            == UltimateLifeformSpec.ABSORPTION,
                    "1.16 kept the Absorption hold with no target in reach"
                            + " and never asked for the hand; it ended with "
                            + holder.messages);
            helper.assertTrue(
                    cow.getHealth() == health[0],
                    "1.16 did not absorb on a held tick without a target in"
                            + " reach (2 blocks); the cow 3 blocks away lost "
                            + (health[0] - cow.getHealth()));
            cow.setPos(cow.getX(), cow.getY(), cow.getZ() - 1.75D);
            holder.aim(cow);
        });
        helper.runAfterDelay(18, () -> {
            helper.assertTrue(
                    holder.data.getHeldAbility() == null
                            && !holder.isRunning(
                                    UltimateLifeformSpec.ABSORPTION),
                    "1.16 ended the Absorption hold once its target was in"
                            + " reach again and the hand was filled; it went"
                            + " on");
            helper.assertTrue(
                    holder.saw("hand"),
                    "the Absorption hold ended without the hand message, got "
                            + holder.messages);
            cow.discard();
            holder.close((short) 32_109);
            helper.succeed();
        });
    }

    // 1.16 PillarmanAbsorption.checkSpecificConditions; the difficulty is
    // switched and restored within the tick, so nothing despawns.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void absorptionIsRefusedAndEndedOnPeaceful(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateAbsorbPeace",
                "6d0e8b47-2c5a-4f19-a7b3-9e1f4c8d2a56");
        Cow cow = cow(helper, new Vec3(2.5D, 2.0D, 3.75D));
        Ability absorption = holder.power.getAbility(
                UltimateLifeformSpec.ABSORPTION.id);
        MinecraftServer server = helper.getLevel().getServer();
        Difficulty before = helper.getLevel().getDifficulty();
        helper.runAfterDelay(1, () -> {
            holder.aim(cow);
            ConditionCheck press;
            server.setDifficulty(Difficulty.PEACEFUL, true);
            try {
                press = absorption.checkConditions(holder.power);
            }
            finally {
                server.setDifficulty(before, true);
            }
            helper.assertTrue(
                    !press.isPositive() && isWarning(press, "peaceful"),
                    "1.16 refused Absorption on Peaceful with its message: "
                            + press.getWarning());
            holder.press(helper, UltimateLifeformSpec.ABSORPTION,
                    (short) 32_110);
        });
        helper.runAfterDelay(4, () -> {
            EntityActionInstance action =
                    LivingComponentAction.getCurEntityAction(holder.player);
            helper.assertTrue(
                    action != null && !action.isOver()
                            && action.ability == absorption
                            && absorption instanceof EntityActionAbility,
                    "Premise: Absorption is not held");
            ConditionCheck held;
            server.setDifficulty(Difficulty.PEACEFUL, true);
            try {
                held = ((EntityActionAbility) absorption)
                        .checkHeldActionConditions(action, holder.power);
            }
            finally {
                server.setDifficulty(before, true);
            }
            helper.assertTrue(
                    !held.isPositive() && !held.shouldContinueHold()
                            && isWarning(held, "peaceful"),
                    "1.16 ended the Absorption hold on Peaceful with its"
                            + " message: " + held.getWarning()
                            + " continue=" + held.shouldContinueHold());
            cow.discard();
            holder.close((short) 32_110);
            helper.succeed();
        });
    }

    private static boolean isWarning(ConditionCheck check, String postfix) {
        return check.getWarning() != null
                && check.getWarning().getContents()
                        instanceof TranslatableContents contents
                && ("jojo.message.action_condition." + postfix)
                        .equals(contents.getKey());
    }

    private static Cow cow(GameTestHelper helper, Vec3 relative) {
        Cow cow = EntityType.COW.create(helper.getLevel());
        if (cow == null) {
            throw new IllegalStateException("Cow fixture failed");
        }
        Vec3 position = Vec3.atBottomCenterOf(helper.absolutePos(
                net.minecraft.core.BlockPos.ZERO)).add(
                        relative.x - 0.5D, relative.y, relative.z - 0.5D);
        cow.setPos(position);
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1_000.0D);
        cow.setHealth(1_000.0F);
        helper.getLevel().addFreshEntity(cow);
        return cow;
    }

    private static Holder holder(
            GameTestHelper helper, String name, String uuid) {
        List<Component> messages = new ArrayList<>();
        ServerPlayer player = new FakePlayer(
                helper.getLevel(),
                new GameProfile(UUID.fromString(uuid), name)) {
            @Override
            public void displayClientMessage(
                    Component message, boolean overlay) {
                if (overlay) {
                    messages.add(message);
                }
            }
        };
        player.setPos(Vec3.atBottomCenterOf(
                helper.absolutePos(new net.minecraft.core.BlockPos(2, 2, 2))));
        player.setGameMode(GameType.SURVIVAL);
        if (!helper.getLevel().addFreshEntity(player)) {
            throw new IllegalStateException(
                    "Held recheck player could not be added: " + name);
        }
        PlayerPower power = PowerClass.PLAYER_POWER.attachGet(player);
        if (!power.trySetPowerType(
                UltimateLifeformAddon.ULTIMATE_LIFEFORM.get())) {
            throw new IllegalStateException(
                    "Held recheck power grant failed: " + name);
        }
        power.updateAvailableMoves();
        UltimateLifeformData data = power.getCurTypeData(
                UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElseThrow();
        // a new Ultimate Lifeform starts with no energy (1.16); these
        // fixtures start full
        data.setCommandEnergy(player, UltimateLifeformData.MAX_ENERGY);
        return new Holder(player, power, data, messages);
    }

    private record Holder(
            ServerPlayer player,
            PlayerPower power,
            UltimateLifeformData data,
            List<Component> messages) {
        void press(GameTestHelper helper,
                UltimateLifeformSpec spec,
                short key) {
            Ability ability = power.getAbility(spec.id);
            helper.assertTrue(ability != null,
                    "Premise: no " + spec.id + " in the moveset");
            BufferingState buffering = BufferingState.clickCanBuffer();
            Object held;
            FriendlyByteBuf extra =
                    new FriendlyByteBuf(Unpooled.buffer());
            try {
                held = AbilityInput.keyPress(
                        key,
                        ability,
                        player,
                        extra,
                        InputMethod.HOLD,
                        0.0F,
                        buffering,
                        ability.getAbilityId());
            }
            finally {
                extra.release();
            }
            helper.assertTrue(held != null || buffering.isActionSuccess,
                    "Premise: the " + spec.id + " press was refused: "
                            + messages);
        }

        void aim(Entity target) {
            LivingComponentAction.getComponent(player)
                    .entityAim.setTarget(new ActionTarget(target));
            player.lookAt(EntityAnchorArgument.Anchor.EYES,
                    target.getBoundingBox().getCenter());
        }

        boolean isRunning(UltimateLifeformSpec spec) {
            EntityActionInstance current =
                    LivingComponentAction.getCurEntityAction(player);
            return current != null
                    && !current.isOver()
                    && current.ability == power.getAbility(spec.id);
        }

        boolean readyToFire(UltimateLifeformSpec spec) {
            return power.getAbility(spec.id).isHeldReadyToFire(player);
        }

        boolean saw(String postfix) {
            String key = "jojo.message.action_condition." + postfix;
            return messages.stream().anyMatch(message ->
                    message.getContents()
                                    instanceof TranslatableContents contents
                            && key.equals(contents.getKey()));
        }

        void close(short key) {
            AbilityInput.keyRelease(key, player);
            power.setPowerType(null);
            player.discard();
        }
    }
}
