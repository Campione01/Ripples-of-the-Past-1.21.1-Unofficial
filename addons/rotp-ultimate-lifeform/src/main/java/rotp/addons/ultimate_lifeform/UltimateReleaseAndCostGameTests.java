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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ultimate_lifeform.action.UltimateLifeformCombat;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformData;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformSpec;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import rotp.core.impl.powers.vampirism.abilities.VampirismBloodDrainAbility;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;

/**
 * 1.16 rules of when an Ultimate Lifeform move fires and what it costs:
 * holdToFire(n, true) moves that do their work in perform fire on release,
 * held as long as the key is.
 */
@GameTestHolder(UltimateLifeformAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UltimateReleaseAndCostGameTests {
    private UltimateReleaseAndCostGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 50)
    public static void bladeDashChargeIsHeldUntilTheReleaseFiresIt(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateReleaseDash",
                "1f7c3a52-4b8e-4d61-9a2f-6c0e8b3d5a17");
        holder.data.setPreviousMode(holder.player, PillarmanMode.LIGHT);
        holder.data.setArmorType(
                holder.player, UltimateLifeformData.ArmorType.NORMAL);
        holder.data.setCommandEnergy(holder.player,
                UltimateLifeformData.MAX_ENERGY);
        short key = (short) 32_120;
        helper.runAfterDelay(1, () -> holder.press(
                helper, UltimateLifeformSpec.BLADE_DASH_ATTACK, key));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(
                    holder.phase(UltimateLifeformSpec.BLADE_DASH_ATTACK)
                                    == ActionPhase.BUTTON_CHARGE
                            && holder.data.getHeldAbility() == null
                            && holder.data.getEnergy()
                                    == UltimateLifeformData.MAX_ENERGY,
                    "1.16 held the charged Blade Dash until the key was"
                            + " released; it dashed at the end of the"
                            + " charge: phase="
                            + holder.phase(
                                    UltimateLifeformSpec.BLADE_DASH_ATTACK)
                            + " held=" + holder.data.getHeldAbility()
                            + " energy=" + holder.data.getEnergy());
            AbilityInput.keyRelease(key, holder.player);
        });
        helper.runAfterDelay(34, () -> {
            helper.assertTrue(
                    holder.data.getHeldAbility()
                                    == UltimateLifeformSpec.BLADE_DASH_ATTACK
                            && holder.data.getEnergy()
                                    == UltimateLifeformData.MAX_ENERGY
                                            - 800.0F,
                    "1.16 dashed on the release of a charged Blade Dash"
                            + " and paid its 800: held="
                            + holder.data.getHeldAbility() + " energy="
                            + holder.data.getEnergy());
            holder.close(key);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void bladeDashHeldPastItsChargeStillNeedsItsEnergy(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateReleaseDashCost",
                "2a8d4b63-5c9f-4e72-8b3a-7d1f9c4e6b28");
        holder.data.setPreviousMode(holder.player, PillarmanMode.LIGHT);
        holder.data.setArmorType(
                holder.player, UltimateLifeformData.ArmorType.NORMAL);
        holder.data.setCommandEnergy(holder.player,
                UltimateLifeformData.MAX_ENERGY);
        short key = (short) 32_121;
        helper.runAfterDelay(1, () -> holder.press(
                helper, UltimateLifeformSpec.BLADE_DASH_ATTACK, key));
        helper.runAfterDelay(20, () -> holder.data.setCommandEnergy(
                holder.player, 500.0F));
        helper.runAfterDelay(24, () -> {
            helper.assertTrue(
                    holder.data.getHeldAbility() == null
                            && !holder.isRunning(
                                    UltimateLifeformSpec.BLADE_DASH_ATTACK)
                            && holder.saw("no_energy_ultimate_lifeform"),
                    "1.16 kept asking for the Blade Dash's 800 energy on"
                            + " every held tick until the release; the hold"
                            + " 9 ticks past the charge went on: held="
                            + holder.data.getHeldAbility() + " messages="
                            + holder.messages);
            holder.close(key);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void bladeDashReleasedWithAFilledHandDoesNotFire(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateReleaseDashHand",
                "3b9e5c74-6d0a-4f83-9c4b-8e2a0d5f7c39");
        holder.data.setPreviousMode(holder.player, PillarmanMode.LIGHT);
        holder.data.setArmorType(
                holder.player, UltimateLifeformData.ArmorType.NORMAL);
        holder.data.setCommandEnergy(holder.player,
                UltimateLifeformData.MAX_ENERGY);
        short key = (short) 32_122;
        helper.runAfterDelay(1, () -> holder.press(
                helper, UltimateLifeformSpec.BLADE_DASH_ATTACK, key));
        helper.runAfterDelay(16, () -> {
            helper.assertTrue(
                    holder.phase(UltimateLifeformSpec.BLADE_DASH_ATTACK)
                            == ActionPhase.BUTTON_CHARGE,
                    "Premise: the Blade Dash is not charged and held: "
                            + holder.phase(
                                    UltimateLifeformSpec.BLADE_DASH_ATTACK));
            holder.player.setItemInHand(
                    InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
            AbilityInput.keyRelease(key, holder.player);
        });
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(
                    holder.data.getHeldAbility() == null
                            && !holder.isRunning(
                                    UltimateLifeformSpec.BLADE_DASH_ATTACK)
                            && holder.data.getEnergy()
                                    == UltimateLifeformData.MAX_ENERGY,
                    "1.16 stopHeldAction(true) checked the Blade Dash once"
                            + " more on release and did not dash with a"
                            + " filled hand: held="
                            + holder.data.getHeldAbility() + " energy="
                            + holder.data.getEnergy());
            holder.close(key);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 50)
    public static void ultimateHamonOverdriveStrikesOnTheRelease(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateReleaseOverdrive",
                "4c0f6d85-7e1b-4a94-8d5c-9f3b1e6a8d40");
        holder.data.setArmorType(
                holder.player, UltimateLifeformData.ArmorType.NORMAL);
        holder.data.setCommandEnergy(holder.player,
                UltimateLifeformData.MAX_ENERGY);
        holder.data.addHamonCharge(3_000);
        short key = (short) 32_123;
        String id = UltimateLifeformSpec.ULTIMATE_HAMON_OVERDRIVE.id;
        helper.runAfterDelay(1, () -> holder.press(
                helper, UltimateLifeformSpec.ULTIMATE_HAMON_OVERDRIVE, key));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(
                    holder.phase(UltimateLifeformSpec
                                    .ULTIMATE_HAMON_OVERDRIVE)
                                    == ActionPhase.BUTTON_CHARGE
                            && !holder.data.isAbilityOnCooldown(id)
                            && holder.data.getEnergy()
                                    == UltimateLifeformData.MAX_ENERGY,
                    "1.16 held the charged Ultimate Hamon Overdrive until"
                            + " the key was released; it struck at the end"
                            + " of the charge: phase="
                            + holder.phase(UltimateLifeformSpec
                                    .ULTIMATE_HAMON_OVERDRIVE)
                            + " cooldown="
                            + holder.data.isAbilityOnCooldown(id));
            AbilityInput.keyRelease(key, holder.player);
        });
        helper.runAfterDelay(34, () -> {
            helper.assertTrue(
                    holder.data.isAbilityOnCooldown(id)
                            && holder.data.getEnergy()
                                    == UltimateLifeformData.MAX_ENERGY
                                            - 750.0F,
                    "1.16 struck on the release of a charged Ultimate Hamon"
                            + " Overdrive and paid its 750: cooldown="
                            + holder.data.isAbilityOnCooldown(id)
                            + " energy=" + holder.data.getEnergy());
            holder.close(key);
            helper.succeed();
        });
    }

    // 1.16 Action.checkHeldItems: "hand" for the moves that need only a free
    // main hand, at the press and on a held tick; the two-handed moves' own
    // checkHeldItems said "hands".
    @GameTest(template = "empty", timeoutTicks = 30)
    public static void mainHandOnlyMovesAskForTheHandAtThePressAndWhileHeld(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateReleaseHand",
                "5d1a7e96-8f2c-4ba5-9e6d-0a4c2f7b9e51");
        holder.data.setCommandEnergy(holder.player,
                UltimateLifeformData.MAX_ENERGY);
        holder.data.addHamonCharge(3_000);
        short key = (short) 32_124;
        // 1.16 Hamon Overdrive took an entity under the crosshair first
        Cow aimed = cow(helper, new Vec3(2.5D, 2.0D, 4.25D));
        holder.aim(aimed);
        holder.player.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
        for (UltimateLifeformSpec spec : new UltimateLifeformSpec[] {
                UltimateLifeformSpec.BUNNY_SUMMON,
                UltimateLifeformSpec.PHANTOM_SUMMON,
                UltimateLifeformSpec.OVERWHELMING_DESPAIR,
                UltimateLifeformSpec.HAMON_OVERDRIVE,
                UltimateLifeformSpec.HAMON_LIFE_MAGNETISM,
                UltimateLifeformSpec.ULTIMATE_BARRAGE}) {
            Ability ability = holder.power.getAbility(spec.id);
            helper.assertTrue(ability != null,
                    "Premise: no " + spec.id + " in the moveset");
            ConditionCheck press = ability.checkConditions(holder.power);
            String expected = spec.requiresBothHandsFree() ? "hands" : "hand";
            helper.assertTrue(
                    !press.isPositive() && isWarning(press, expected),
                    "1.16 refused " + spec.id + " with a filled main hand"
                            + " with the \"" + expected + "\" message, got "
                            + (press.isPositive() ? "admitted"
                                    : String.valueOf(press.getWarning())));
        }
        holder.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.runAfterDelay(1, () -> holder.press(
                helper, UltimateLifeformSpec.BUNNY_SUMMON, key));
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(
                    holder.phase(UltimateLifeformSpec.BUNNY_SUMMON)
                            == ActionPhase.BUTTON_CHARGE,
                    "Premise: the Bunny Summon is not charging");
            holder.player.setItemInHand(
                    InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
        });
        helper.runAfterDelay(9, () -> {
            helper.assertTrue(
                    !holder.isRunning(UltimateLifeformSpec.BUNNY_SUMMON)
                            && holder.saw("hand") && !holder.saw("hands"),
                    "1.16 ended the held Bunny Summon with the \"hand\""
                            + " message once the main hand was filled, got "
                            + holder.messages);
            aimed.discard();
            holder.close(key);
            helper.succeed();
        });
    }

    // 1.16 NonStandPowerType.consumeEnergy took nothing from a creative user,
    // so a creative hold never ran out of energy.
    @GameTest(template = "empty", timeoutTicks = 30)
    public static void creativeHoldPaysNoHeldTickEnergy(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateReleaseCreative",
                "6e2b8fa7-9a3d-4cb6-8f7e-1b5d3a8c0f62", GameType.CREATIVE);
        holder.data.setArmorType(
                holder.player, UltimateLifeformData.ArmorType.NORMAL);
        holder.data.setCommandEnergy(holder.player, 50.0F);
        short key = (short) 32_125;
        helper.runAfterDelay(1, () -> holder.press(
                helper, UltimateLifeformSpec.ULTIMATE_BARRAGE, key));
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(
                    holder.data.getHeldAbility()
                                    == UltimateLifeformSpec.ULTIMATE_BARRAGE
                            && holder.data.getEnergy() == 50.0F,
                    "1.16 took no held tick energy from a creative user; the"
                            + " creative Ultimate Barrage hold paid or ended:"
                            + " held=" + holder.data.getHeldAbility()
                            + " energy=" + holder.data.getEnergy());
            holder.close(key);
            helper.succeed();
        });
    }

    // 1.16 UltimateLifeformAbsorbtion.absorbEntity: each absorbed hit gave
    // bloodDrainMultiplier (the config value of the difficulty) * 70 energy.
    @GameTest(template = "empty", timeoutTicks = 30)
    public static void absorbedHitGivesSeventyTimesTheBloodDrainMultiplier(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateReleaseAbsorb",
                "7f3c9ab8-0b4e-4dc7-9a8f-2c6e4b9d1a73");
        holder.data.setArmorType(
                holder.player, UltimateLifeformData.ArmorType.NORMAL);
        holder.data.setCommandEnergy(holder.player, 1_000.0F);
        float multiplier = VampirismBloodDrainAbility.bloodDrainMultiplier(
                helper.getLevel());
        helper.assertTrue(multiplier != 1.0F,
                "Premise: the blood drain multiplier of this difficulty is 1,"
                        + " which cannot tell the rules apart");
        Cow cow = cow(helper, new Vec3(2.5D, 2.0D, 3.75D));
        short key = (short) 32_126;
        helper.runAfterDelay(1, () -> {
            holder.aim(cow);
            holder.press(helper, UltimateLifeformSpec.ABSORPTION, key);
        });
        helper.runAfterDelay(6, () -> {
            float gained = holder.data.getEnergy() - 1_000.0F;
            helper.assertTrue(cow.getHealth() < 1_000.0F,
                    "Premise: Absorption did not drain the cow in reach");
            helper.assertTrue(
                    Math.abs(gained - multiplier * 70.0F) < 1.0E-3F,
                    "1.16 gave " + multiplier + " x 70 energy for an"
                            + " absorbed hit; got " + gained);
            cow.discard();
            holder.close(key);
            helper.succeed();
        });
    }

    // 1.16 UltimateLifeformHamonBarrage.holdTick took 20 Hamon a tick
    // without a check (UltimateLifeformData.consumeHamonCharge only
    // subtracted), and its hits did not depend on the charge: the barrage
    // went on hitting at 0 Hamon.
    @GameTest(template = "empty", timeoutTicks = 30)
    public static void hamonBarrageGoesOnHittingAtZeroHamon(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateReleaseHamonBarrage",
                "8a4d0bc9-1c5f-4ed8-8b9a-3d7f5c0e2b84");
        holder.data.setCommandEnergy(holder.player,
                UltimateLifeformData.MAX_ENERGY);
        holder.data.addHamonCharge(40);
        Cow cow = cow(helper, new Vec3(2.5D, 2.0D, 4.25D));
        short key = (short) 32_127;
        float[] health = new float[1];
        helper.runAfterDelay(1, () -> {
            holder.aim(cow);
            holder.press(
                    helper, UltimateLifeformSpec.ULTIMATE_HAMON_BARRAGE, key);
        });
        for (int tick = 2; tick <= 14; tick++) {
            helper.runAfterDelay(tick, () -> cow.invulnerableTime = 0);
        }
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(
                    holder.data.getHamonCharge() == 0,
                    "Premise: the barrage did not drain the 40 Hamon: "
                            + holder.data.getHamonCharge());
            health[0] = cow.getHealth();
        });
        helper.runAfterDelay(14, () -> {
            helper.assertTrue(
                    holder.data.getHeldAbility()
                                    == UltimateLifeformSpec
                                            .ULTIMATE_HAMON_BARRAGE
                            && cow.getHealth() < health[0],
                    "1.16 kept the Ultimate Hamon Barrage going and hitting"
                            + " at 0 Hamon; held="
                            + holder.data.getHeldAbility() + " lost="
                            + (health[0] - cow.getHealth()));
            cow.discard();
            holder.close(key);
            helper.succeed();
        });
    }

    // 1.16 UltimateLifeformAction.checkConditions: the charge had to reach the
    // raw hamonCost (2000, 250, 600), twice what getHamonCost() spent; a
    // shorter charge was refused (1.16 without a message; the port's
    // "no_hamon_charge" is kept, owner 2026-10-19).
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void hamonMovesNeedTheirRawHamonCost(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateHamonThreshold",
                "9b5e1cd0-2d6a-4fe9-9cab-4e8a6d1f3c95");
        holder.data.setCommandEnergy(holder.player,
                UltimateLifeformData.MAX_ENERGY);
        // 1.16 Hamon Overdrive took an entity under the crosshair first
        Cow aimed = cow(helper, new Vec3(2.5D, 2.0D, 4.25D));
        holder.aim(aimed);
        Object[][] moves = {
                {UltimateLifeformSpec.ULTIMATE_HAMON_OVERDRIVE, 2_000},
                {UltimateLifeformSpec.HAMON_OVERDRIVE, 250},
                {UltimateLifeformSpec.HAMON_LIFE_MAGNETISM, 600}};
        for (Object[] move : moves) {
            UltimateLifeformSpec spec = (UltimateLifeformSpec) move[0];
            int raw = (Integer) move[1];
            Ability ability = holder.power.getAbility(spec.id);
            helper.assertTrue(ability != null,
                    "Premise: no " + spec.id + " in the moveset");
            // Life Magnetism needs leaves to glide on
            holder.player.setItemInHand(InteractionHand.OFF_HAND,
                    spec == UltimateLifeformSpec.HAMON_LIFE_MAGNETISM
                            ? new ItemStack(Items.OAK_LEAVES)
                            : ItemStack.EMPTY);
            setHamon(holder.data, raw - 1);
            ConditionCheck below = ability.checkConditions(holder.power);
            helper.assertTrue(
                    !below.isPositive() && isWarning(below, "no_hamon_charge"),
                    "1.16 refused " + spec.id + " below its raw Hamon cost "
                            + raw + " (the port says \"no_hamon_charge\"); at "
                            + (raw - 1)
                            + " got " + (below.isPositive() ? "admitted"
                                    : String.valueOf(below.getWarning())));
            setHamon(holder.data, raw);
            ConditionCheck at = ability.checkConditions(holder.power);
            helper.assertTrue(at.isPositive(),
                    "1.16 admitted " + spec.id + " at its raw Hamon cost "
                            + raw + ", got " + at.getWarning());
        }
        aimed.discard();
        holder.close((short) 32_128);
        helper.succeed();
    }

    // 1.16 rechecked the Hamon charge on every held tick: a charging Ultimate
    // Hamon Overdrive whose charge fell below 2000 ended silently.
    @GameTest(template = "empty", timeoutTicks = 30)
    public static void heldUltimateHamonOverdriveEndsSilentlyBelowItsRawCost(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateHamonHeldCheck",
                "0c6f2de1-3e7b-4a0a-8dbc-5f9b7e2a4da6");
        holder.data.setArmorType(
                holder.player, UltimateLifeformData.ArmorType.NORMAL);
        holder.data.setCommandEnergy(holder.player,
                UltimateLifeformData.MAX_ENERGY);
        setHamon(holder.data, 2_500);
        short key = (short) 32_129;
        String id = UltimateLifeformSpec.ULTIMATE_HAMON_OVERDRIVE.id;
        helper.runAfterDelay(1, () -> holder.press(
                helper, UltimateLifeformSpec.ULTIMATE_HAMON_OVERDRIVE, key));
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(
                    holder.phase(UltimateLifeformSpec.ULTIMATE_HAMON_OVERDRIVE)
                            == ActionPhase.BUTTON_CHARGE,
                    "Premise: the Ultimate Hamon Overdrive is not charging");
            setHamon(holder.data, 1_500);
        });
        helper.runAfterDelay(9, () -> {
            helper.assertTrue(
                    !holder.isRunning(
                                    UltimateLifeformSpec.ULTIMATE_HAMON_OVERDRIVE)
                            && holder.messages.isEmpty(),
                    "1.16 ended the held Ultimate Hamon Overdrive silently"
                            + " once the charge fell below 2000: phase="
                            + holder.phase(UltimateLifeformSpec
                                    .ULTIMATE_HAMON_OVERDRIVE)
                            + " messages=" + holder.messages);
            AbilityInput.keyRelease(key, holder.player);
        });
        helper.runAfterDelay(13, () -> {
            helper.assertTrue(
                    !holder.data.isAbilityOnCooldown(id)
                            && holder.data.getHamonCharge() == 1_500
                            && holder.data.getEnergy()
                                    == UltimateLifeformData.MAX_ENERGY,
                    "The ended hold still struck on the release: cooldown="
                            + holder.data.isAbilityOnCooldown(id) + " hamon="
                            + holder.data.getHamonCharge());
            holder.close(key);
            helper.succeed();
        });
    }

    // 1.16 Ultimate Hamon Overdrive spent getHamonCost() (1000) when it fired
    // and emptied the rest of the charge only on a living entity it struck.
    @GameTest(template = "empty", timeoutTicks = 50)
    public static void ultimateHamonOverdriveAtNothingSpendsHalfItsRawCost(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateHamonSpent",
                "1d7a3ef2-4f8c-4b1b-9ecd-6a0c8f3b5eb7");
        holder.data.setArmorType(
                holder.player, UltimateLifeformData.ArmorType.NORMAL);
        holder.data.setCommandEnergy(holder.player,
                UltimateLifeformData.MAX_ENERGY);
        setHamon(holder.data, 3_000);
        short key = (short) 32_130;
        String id = UltimateLifeformSpec.ULTIMATE_HAMON_OVERDRIVE.id;
        helper.runAfterDelay(1, () -> holder.press(
                helper, UltimateLifeformSpec.ULTIMATE_HAMON_OVERDRIVE, key));
        helper.runAfterDelay(16, () ->
                AbilityInput.keyRelease(key, holder.player));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(holder.data.isAbilityOnCooldown(id),
                    "Premise: the released Ultimate Hamon Overdrive did not"
                            + " fire");
            helper.assertTrue(holder.data.getHamonCharge() == 2_000,
                    "1.16 spent 1000 of the 3000 Hamon on an Ultimate Hamon"
                            + " Overdrive that struck nothing; left "
                            + holder.data.getHamonCharge());
            holder.close(key);
            helper.succeed();
        });
    }

    // 1.16 UltimateLifeformAction.checkConditions had no creative exemption,
    // and UltimateLifeformData.consumeHamonCharge (:137-142) subtracted before
    // its isUserCreative() return: a creative user needed and paid Hamon.
    @GameTest(template = "empty", timeoutTicks = 50)
    public static void creativeUserNeedsAndPaysHamon(GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateHamonCreative",
                "2e8b4f03-5a9d-4c2c-8fde-7b1d9a4c6fc8", GameType.CREATIVE);
        holder.data.setArmorType(
                holder.player, UltimateLifeformData.ArmorType.NORMAL);
        setHamon(holder.data, 1_999);
        Ability ability = holder.power.getAbility(
                UltimateLifeformSpec.ULTIMATE_HAMON_OVERDRIVE.id);
        helper.assertTrue(ability != null,
                "Premise: no Ultimate Hamon Overdrive in the moveset");
        ConditionCheck below = ability.checkConditions(holder.power);
        helper.assertTrue(!below.isPositive()
                        && isWarning(below, "no_hamon_charge"),
                "1.16 refused a creative user's Ultimate Hamon Overdrive"
                        + " below 2000 Hamon (\"no_hamon_charge\"); at 1999 got "
                        + (below.isPositive() ? "admitted"
                                : String.valueOf(below.getWarning())));
        setHamon(holder.data, 3_000);
        short key = (short) 32_131;
        helper.runAfterDelay(1, () -> holder.press(
                helper, UltimateLifeformSpec.ULTIMATE_HAMON_OVERDRIVE, key));
        helper.runAfterDelay(16, () ->
                AbilityInput.keyRelease(key, holder.player));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(holder.data.getHamonCharge() == 2_000,
                    "1.16 took 1000 Hamon from a creative user's Ultimate"
                            + " Hamon Overdrive; left "
                            + holder.data.getHamonCharge());
            holder.close(key);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void creativeHamonBarrageDrainsTheCharge(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateHamonCreativeBarrage",
                "3f9c5a14-6bae-4d3d-9aef-8c2eab5d7ad9", GameType.CREATIVE);
        holder.data.setArmorType(
                holder.player, UltimateLifeformData.ArmorType.NORMAL);
        setHamon(holder.data, 40);
        short key = (short) 32_132;
        helper.runAfterDelay(1, () -> holder.press(
                helper, UltimateLifeformSpec.ULTIMATE_HAMON_BARRAGE, key));
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(
                    holder.data.getHeldAbility()
                                    == UltimateLifeformSpec
                                            .ULTIMATE_HAMON_BARRAGE
                            && holder.data.getHamonCharge() == 0,
                    "1.16 drained a creative user's Hamon by 20 a held tick"
                            + " of the Ultimate Hamon Barrage: held="
                            + holder.data.getHeldAbility() + " hamon="
                            + holder.data.getHamonCharge());
            holder.close(key);
            helper.succeed();
        });
    }

    // 1.16 NonStandPower.canLeap / onLeap (:202, :208) asked hasEnergy and
    // called consumeEnergy, both creative-exempt: a creative user leapt
    // without the 1200 energy and kept it.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void creativeLeapNeedsAndTakesNoEnergy(GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateCreativeLeap",
                "4a0d6b25-7cbf-4e4e-8b0f-9d3fbc6e8bea");
        holder.data.setArmorType(
                holder.player, UltimateLifeformData.ArmorType.NORMAL);
        holder.data.setCommandEnergy(holder.player, 50.0F);
        helper.assertTrue(!holder.power.canLeap(),
                "Premise: a survival user leapt without the 1200 energy");
        holder.player.setGameMode(GameType.CREATIVE);
        helper.assertTrue(holder.power.canLeap(),
                "1.16 let a creative user leap with 50 of the 1200 energy");
        holder.data.setCommandEnergy(holder.player, 2_000.0F);
        holder.power.onLeap();
        helper.assertTrue(holder.data.getEnergy() == 2_000.0F,
                "1.16 took no leap energy from a creative user; energy="
                        + holder.data.getEnergy());
        holder.close((short) 32_133);
        helper.succeed();
    }

    // 1.16 UltimateLifeformLifeMagnetism: checkHeldItems (:78-83) passed with
    // leaves in either hand, checkSpecificConditions (:71-76) refused with
    // "leaves" unless leaves were aimed at, held or in the inventory, and
    // perform (:85-102) turned aimed leaves into a glider where they stood.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void lifeMagnetismNeedsLeavesAndTakesThemInEitherHand(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateLifeMagnetism",
                "5b1e7c36-8dc0-4f5f-9c1a-0e4acd7f9cfb");
        holder.data.setCommandEnergy(holder.player,
                UltimateLifeformData.MAX_ENERGY);
        setHamon(holder.data, 3_000);
        UltimateLifeformSpec spec = UltimateLifeformSpec.HAMON_LIFE_MAGNETISM;
        Ability ability = holder.power.getAbility(spec.id);
        helper.assertTrue(ability != null,
                "Premise: no Hamon Life Magnetism in the moveset");
        holder.player.setXRot(-90.0F);
        ConditionCheck none = ability.checkConditions(holder.power);
        helper.assertTrue(!none.isPositive() && isWarning(none, "leaves"),
                "1.16 refused Life Magnetism with the \"leaves\" message"
                        + " without leaves, got "
                        + (none.isPositive() ? "admitted"
                                : String.valueOf(none.getWarning())));
        holder.player.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_LEAVES));
        ConditionCheck mainHand = ability.checkConditions(holder.power);
        helper.assertTrue(mainHand.isPositive(),
                "1.16 admitted Life Magnetism with leaves in the main hand,"
                        + " got " + mainHand.getWarning());
        holder.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        holder.player.getInventory().setItem(
                12, new ItemStack(Items.BIRCH_LEAVES, 3));
        ConditionCheck inventory = ability.checkConditions(holder.power);
        helper.assertTrue(inventory.isPositive(),
                "1.16 admitted Life Magnetism with leaves in the inventory,"
                        + " got " + inventory.getWarning());
        net.minecraft.core.BlockPos leaves =
                helper.absolutePos(new net.minecraft.core.BlockPos(2, 2, 4));
        helper.getLevel().setBlockAndUpdate(
                leaves, net.minecraft.world.level.block.Blocks.OAK_LEAVES
                        .defaultBlockState());
        holder.player.lookAt(EntityAnchorArgument.Anchor.EYES,
                Vec3.atCenterOf(leaves));
        LivingComponentAction.getComponent(holder.player).entityAim.setTarget(
                new ActionTarget(leaves, net.minecraft.core.Direction.NORTH));
        UltimateLifeformCombat.perform(holder.player, holder.data, spec, 0);
        List<rotp.core.impl.powers.hamon.entity.LeavesGliderEntity> gliders =
                helper.getLevel().getEntitiesOfClass(
                        rotp.core.impl.powers.hamon.entity.LeavesGliderEntity
                                .class,
                        new net.minecraft.world.phys.AABB(leaves).inflate(2.0D));
        helper.assertTrue(
                helper.getLevel().getBlockState(leaves).isAir()
                        && gliders.size() == 1
                        && holder.player.getVehicle() == null
                        && holder.player.getInventory().getItem(12)
                                .getCount() == 3,
                "1.16 turned the aimed leaves into a glider where they stood"
                        + " and kept the inventory leaves: block="
                        + helper.getLevel().getBlockState(leaves)
                        + " gliders=" + gliders.size() + " riding="
                        + holder.player.getVehicle() + " inventory="
                        + holder.player.getInventory().getItem(12));
        gliders.forEach(Entity::discard);
        holder.close((short) 32_134);
        helper.succeed();
    }

    // 1.16 UltimateLifeformHamonKick.checkSpecificConditions (:79-81) was
    // ActionConditionResult.noMessage(onGround), asked after the hands; the
    // port's "ground" message is kept (owner 2026-10-19).
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void sendoKickInTheAirIsRefusedAfterTheHands(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateKickAir",
                "6c2f8d47-9ed1-4a6a-8d2b-1f5bde8a0d0c");
        holder.data.setCommandEnergy(holder.player,
                UltimateLifeformData.MAX_ENERGY);
        setHamon(holder.data, 3_000);
        Ability ability = holder.power.getAbility(
                UltimateLifeformSpec.ULTIMATE_HAMON_KICK.id);
        helper.assertTrue(ability != null,
                "Premise: no Sendo kick in the moveset");
        holder.player.setOnGround(true);
        ConditionCheck ground = ability.checkConditions(holder.power);
        helper.assertTrue(ground.isPositive(),
                "Premise: the Sendo kick on the ground was refused: "
                        + ground.getWarning());
        holder.player.setOnGround(false);
        ConditionCheck air = ability.checkConditions(holder.power);
        helper.assertTrue(!air.isPositive() && isWarning(air, "ground"),
                "1.16 refused the Sendo kick in the air (the port says"
                        + " \"ground\"), got " + (air.isPositive() ? "admitted"
                                : String.valueOf(air.getWarning())));
        holder.player.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
        ConditionCheck hand = ability.checkConditions(holder.power);
        helper.assertTrue(!hand.isPositive() && isWarning(hand, "hand"),
                "1.16 asked for the free main hand before the ground; in the"
                        + " air with a stone in hand got "
                        + (hand.isPositive() ? "admitted"
                                : String.valueOf(hand.getWarning())));
        holder.close((short) 32_135);
        helper.succeed();
    }

    // 1.16 UltimateLifeformSendoOverdrive: getTargetRequirement BLOCK
    // (:112-114) made PowerBaseImpl.checkTarget refuse any other target (the
    // port's "block_target" is kept), and checkSpecificConditions (:91-95)
    // refused silently while the core's Hamon Sendo Overdrive targeted the
    // same block face; the add-on's own waves blocked neither a press nor
    // another wave.
    @GameTest(template = "empty", timeoutTicks = 20, batch = "ulf_own_sendo_overdrive_refuses_other_targets_and_co")
    public static void sendoOverdriveRefusesOtherTargetsAndCoreWaves(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateSendoTarget",
                "7d3a9e58-0fe2-4b7b-9e3c-2a6ceb9b1e1d");
        holder.data.setCommandEnergy(holder.player,
                UltimateLifeformData.MAX_ENERGY);
        setHamon(holder.data, 3_000);
        UltimateLifeformSpec spec =
                UltimateLifeformSpec.ULTIMATE_HAMON_SENDO_OVERDRIVE;
        Ability ability = holder.power.getAbility(spec.id);
        helper.assertTrue(ability != null,
                "Premise: no Ultimate Sendo Overdrive in the moveset");
        Cow cow = cow(helper, new Vec3(2.5D, 2.0D, 4.25D));
        holder.aim(cow);
        ConditionCheck entity = ability.checkConditions(holder.power);
        helper.assertTrue(!entity.isPositive()
                        && isWarning(entity, "block_target"),
                "1.16 refused the Ultimate Sendo Overdrive at a mob (the port"
                        + " says \"block_target\"), got "
                        + (entity.isPositive() ? "admitted"
                                : String.valueOf(entity.getWarning())));
        cow.discard();
        net.minecraft.core.BlockPos block =
                helper.absolutePos(new net.minecraft.core.BlockPos(2, 2, 4));
        helper.getLevel().setBlockAndUpdate(block,
                net.minecraft.world.level.block.Blocks.STONE
                        .defaultBlockState());
        holder.player.lookAt(EntityAnchorArgument.Anchor.EYES,
                Vec3.atCenterOf(block));
        LivingComponentAction.getComponent(holder.player).entityAim.setTarget(
                new ActionTarget(block, net.minecraft.core.Direction.NORTH));
        ConditionCheck free = ability.checkConditions(holder.power);
        helper.assertTrue(free.isPositive(),
                "Premise: the Ultimate Sendo Overdrive at a free block face"
                        + " was refused: " + free.getWarning());
        UltimateLifeformCombat.perform(holder.player, holder.data, spec, 0);
        UltimateLifeformCombat.perform(holder.player, holder.data, spec, 0);
        List<rotp.addons.ultimate_lifeform.entity.UltimateAreaEffectEntity>
                waves = helper.getLevel().getEntitiesOfClass(
                        rotp.addons.ultimate_lifeform.entity
                                .UltimateAreaEffectEntity.class,
                        new net.minecraft.world.phys.AABB(block).inflate(4.0D),
                        area -> area.isSendo()
                                && area.getOwner() == holder.player);
        ConditionCheck ownWaves = ability.checkConditions(holder.power);
        helper.assertTrue(waves.size() == 2 && ownWaves.isPositive(),
                "1.16 put a second Ultimate Sendo Overdrive on a block face"
                        + " that already had one and still admitted a press:"
                        + " waves=" + waves.size() + " press="
                        + (ownWaves.isPositive() ? "admitted"
                                : String.valueOf(ownWaves.getWarning())));
        waves.forEach(Entity::discard);
        rotp.core.impl.powers.hamon.entity.HamonSendoOverdriveEntity core =
                new rotp.core.impl.powers.hamon.entity
                        .HamonSendoOverdriveEntity(helper.getLevel(),
                                holder.player,
                                net.minecraft.core.Direction.Axis.Z);
        core.setBlockTarget(block, net.minecraft.core.Direction.NORTH);
        core.setPos(Vec3.atCenterOf(block));
        helper.getLevel().addFreshEntity(core);
        ConditionCheck coreWave = ability.checkConditions(holder.power);
        helper.assertTrue(
                !coreWave.isPositive() && coreWave.getWarning() == null,
                "1.16 refused the Ultimate Sendo Overdrive silently while a"
                        + " Hamon Sendo Overdrive targeted that block face,"
                        + " got " + (coreWave.isPositive() ? "admitted"
                                : String.valueOf(coreWave.getWarning())));
        core.discard();
        holder.close((short) 32_136);
        helper.succeed();
    }

    // 1.16 NonStandAction.checkConditions (core :58-64) asked for the energy
    // right after UltimateLifeformAction's Hamon, then Action.checkConditions
    // the hands and the action's own conditions, and PowerBaseImpl
    // .checkRequirements isUnlocked (the mode) last.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void energyIsAskedBeforeTheHandsAndTheMode(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateEnergyOrder",
                "8e4b0f69-1af3-4c8c-8f4d-3b7dfc0c2f2e");
        holder.data.setCommandEnergy(holder.player, 0.0F);
        holder.data.setPreviousMode(holder.player, PillarmanMode.HEAT);
        holder.player.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
        Ability ability = holder.power.getAbility(
                UltimateLifeformSpec.BLADE_SLASH.id);
        helper.assertTrue(ability != null,
                "Premise: no Blade Slash in the moveset");
        String[] expected = {
                "no_energy_ultimate_lifeform",
                "hands",
                "wrong_ultimate_lifeform_mode"};
        for (int step = 0; step < expected.length; step++) {
            if (step == 1) {
                holder.data.setCommandEnergy(holder.player,
                        UltimateLifeformData.MAX_ENERGY);
            }
            else if (step == 2) {
                holder.player.setItemInHand(
                        InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
            ConditionCheck press = ability.checkConditions(holder.power);
            helper.assertTrue(
                    !press.isPositive() && isWarning(press, expected[step]),
                    "1.16 asked for the energy, then the hands, then the mode;"
                            + " step " + step + " expected \"" + expected[step]
                            + "\", got " + (press.isPositive() ? "admitted"
                                    : String.valueOf(press.getWarning())));
        }
        holder.data.setPreviousMode(holder.player, PillarmanMode.LIGHT);
        ConditionCheck admitted = ability.checkConditions(holder.power);
        helper.assertTrue(admitted.isPositive(),
                "Premise: the Blade Slash with energy, free hands and the"
                        + " light mode was refused: " + admitted.getWarning());
        holder.close((short) 32_137);
        helper.succeed();
    }

    // 1.16 UltimateLifeformLiquidWalkingMixin (Entity.move after collide) put
    // an Ultimate Lifeform with Hamon on a fluid's surface, and
    // UltimateLifeformLiquidWalking.isLiquidWalking (:61-79) took
    // waterWalkingTickCost() = 50 (UltimateLifeformData :393-395; the previous
    // tick flag was never set) on every tenth event of its static counter, one
    // event per bottom corner that reached the fluid.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void liquidWalkingStandsOnWaterAndPaysFiftyEveryTenthEvent(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateLiquidWalk",
                "9f5c1a7b-2b04-4d9d-9a5e-4c8e0d1d3a3f");
        net.minecraft.core.BlockPos pool = pool(helper,
                new net.minecraft.core.BlockPos(2, 1, 2),
                net.minecraft.world.level.block.Blocks.WATER);
        setHamon(holder.data, 3_000);
        double surface = pool.getY() + 8.0D / 9.0D;
        holder.player.setOnGround(false);
        holder.player.setPos(pool.getX() + 0.5D, pool.getY() + 1.0D,
                pool.getZ() + 0.5D);
        holder.player.move(net.minecraft.world.entity.MoverType.SELF,
                new Vec3(0.0D, -0.5D, 0.0D));
        helper.assertTrue(Math.abs(holder.player.getY() - surface) < 1.0E-4D
                        && holder.player.onGround(),
                "1.16 stopped an Ultimate Lifeform with Hamon on the water"
                        + " surface " + surface + "; y=" + holder.player.getY()
                        + " onGround=" + holder.player.onGround());
        for (int move = 1; move < 10; move++) {
            holder.player.move(net.minecraft.world.entity.MoverType.SELF,
                    new Vec3(0.0D, -0.1D, 0.0D));
        }
        helper.assertTrue(Math.abs(holder.player.getY() - surface) < 1.0E-4D,
                "1.16 kept the Ultimate Lifeform on the water surface " + surface
                        + "; y=" + holder.player.getY());
        helper.assertTrue(holder.data.getHamonCharge() == 2_800,
                "1.16 took 50 Hamon on every tenth liquid walking event; 10"
                        + " moves over 4 corners left 2800 of 3000, got "
                        + holder.data.getHamonCharge());
        holder.close((short) 32_138);
        helper.succeed();
    }

    // 1.16 isLiquidWalking (:61-79): no walk at 0 Hamon, on water while
    // burning, or while shift is held after a double shift press.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void liquidWalkingNeedsHamonNoFireOnWaterAndNoDoubleShift(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateLiquidSink",
                "0a6d2b8c-3c15-4eae-8b6f-5d9f1e2e4b40");
        net.minecraft.core.BlockPos pool = pool(helper,
                new net.minecraft.core.BlockPos(2, 1, 2),
                net.minecraft.world.level.block.Blocks.WATER);
        String[] cases = {"no Hamon", "burning", "double shift"};
        for (int step = 0; step < cases.length; step++) {
            setHamon(holder.data, step == 0 ? 0 : 3_000);
            holder.player.setRemainingFireTicks(step == 1 ? 100 : 0);
            if (step == 2) {
                holder.player.setShiftKeyDown(true);
                holder.data.setDoubleShiftPress(holder.player);
            }
            holder.player.setOnGround(false);
            holder.player.setPos(pool.getX() + 0.5D, pool.getY() + 1.0D,
                    pool.getZ() + 0.5D);
            holder.player.move(net.minecraft.world.entity.MoverType.SELF,
                    new Vec3(0.0D, -0.5D, 0.0D));
            helper.assertTrue(
                    Math.abs(holder.player.getY() - (pool.getY() + 0.5D))
                            < 1.0E-4D,
                    "1.16 let the Ultimate Lifeform sink into the water with "
                            + cases[step] + "; y=" + holder.player.getY());
        }
        holder.player.setShiftKeyDown(false);
        holder.player.clearFire();
        holder.close((short) 32_139);
        helper.succeed();
    }

    // 1.16 isLiquidWalking: lava held up a walker too, and hurt it with
    // DamageSource.HOT_FLOOR (1) unless fire immune or wearing Frost Walker.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void liquidWalkingOnLavaBurnsTheFeet(GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateLavaWalk",
                "1b7e3c9d-4d26-4fbf-9c70-6eaf2f3f5c51");
        net.minecraft.core.BlockPos pool = pool(helper,
                new net.minecraft.core.BlockPos(2, 1, 2),
                net.minecraft.world.level.block.Blocks.LAVA);
        setHamon(holder.data, 3_000);
        holder.player.setOnGround(false);
        holder.player.setPos(pool.getX() + 0.5D, pool.getY() + 1.0D,
                pool.getZ() + 0.5D);
        holder.player.move(net.minecraft.world.entity.MoverType.SELF,
                new Vec3(0.0D, -0.5D, 0.0D));
        double surface = pool.getY() + 8.0D / 9.0D;
        helper.assertTrue(Math.abs(holder.player.getY() - surface) < 1.0E-4D,
                "1.16 stopped an Ultimate Lifeform with Hamon on the lava"
                        + " surface " + surface + "; y=" + holder.player.getY());
        helper.assertTrue(!holder.hurts.isEmpty()
                        && holder.hurts.stream().allMatch("hotFloor 1.0"::equals),
                "1.16 hurt a liquid walker on lava with HOT_FLOOR 1; hurts="
                        + holder.hurts);
        holder.player.clearFire();
        holder.close((short) 32_140);
        helper.succeed();
    }

    // 1.16 UltimateLifeformFlightDash.checkConditions (:30-35) returned
    // ActionConditionResult.NEGATIVE when not gliding, before the energy; the
    // port's "ultimate_flight_dash" message is kept (owner 2026-10-19).
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void flightDashIsRefusedBeforeTheEnergyWhenNotGliding(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateFlightDashGround",
                "2c8f4d0e-5e37-4a0c-8d81-7fb0a4a6d562");
        Ability ability = holder.power.getAbility(
                UltimateLifeformSpec.ULTIMATE_FLIGHT_DASH.id);
        helper.assertTrue(ability != null,
                "Premise: no Ultimate Flight Dash in the moveset");
        holder.data.setCommandEnergy(holder.player, 0.0F);
        ConditionCheck groundNoEnergy = ability.checkConditions(holder.power);
        helper.assertTrue(
                !groundNoEnergy.isPositive()
                        && isWarning(groundNoEnergy, "ultimate_flight_dash"),
                "1.16 refused the Ultimate Flight Dash when not gliding, before"
                        + " the energy (the port says \"ultimate_flight_dash\");"
                        + " got "
                        + (groundNoEnergy.isPositive() ? "admitted"
                                : String.valueOf(
                                        groundNoEnergy.getWarning())));
        holder.player.startFallFlying();
        helper.assertTrue(holder.player.isFallFlying(),
                "Premise: the test player is not gliding");
        ConditionCheck glidingNoEnergy = ability.checkConditions(holder.power);
        helper.assertTrue(
                !glidingNoEnergy.isPositive()
                        && isWarning(glidingNoEnergy,
                                "no_energy_ultimate_lifeform"),
                "Premise: a gliding Ultimate Flight Dash without energy did"
                        + " not ask for it: " + glidingNoEnergy.getWarning());
        holder.data.setCommandEnergy(holder.player,
                UltimateLifeformData.MAX_ENERGY);
        ConditionCheck gliding = ability.checkConditions(holder.power);
        helper.assertTrue(gliding.isPositive(),
                "Premise: a gliding Ultimate Flight Dash was refused: "
                        + gliding.getWarning());
        holder.player.stopFallFlying();
        ConditionCheck ground = ability.checkConditions(holder.power);
        helper.assertTrue(!ground.isPositive()
                        && isWarning(ground, "ultimate_flight_dash"),
                "1.16 refused the Ultimate Flight Dash when not gliding (the"
                        + " port says \"ultimate_flight_dash\"); got "
                        + (ground.isPositive() ? "admitted"
                                : String.valueOf(ground.getWarning())));
        holder.close((short) 32_141);
        helper.succeed();
    }

    // 1.16 UltimateLifeformProtectionArmor and FireProtectionArmor
    // .checkConditions (:39-44) returned ActionConditionResult.NEGATIVE while
    // the other armor's effect was on, whatever the stored armor type said;
    // the port's "other_armor_active" message is kept (owner 2026-10-19).
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void armorIsRefusedWhileTheOtherArmorEffectIsOn(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateArmorEffect",
                "3d9a5e1f-6f48-4b1d-9e92-80c1b5b7e673");
        Ability armor = holder.power.getAbility(
                UltimateLifeformSpec.ARMOR_PROTECTION.id);
        Ability fireArmor = holder.power.getAbility(
                UltimateLifeformSpec.ARMOR_FIRE_PROTECTION.id);
        helper.assertTrue(armor != null && fireArmor != null,
                "Premise: no armor moves in the moveset");
        holder.data.setArmorType(
                holder.player, UltimateLifeformData.ArmorType.FIRE);
        helper.assertTrue(holder.player.hasEffect(
                        UltimateLifeformAddon.ULTIMATE_FIRE_ARMOR),
                "1.16 setArmorType put the Fire Armor effect on at once");
        ConditionCheck underFire = armor.checkConditions(holder.power);
        helper.assertTrue(
                !underFire.isPositive()
                        && isWarning(underFire, "other_armor_active"),
                "1.16 refused Armor Protection while the Fire Armor effect was"
                        + " on (the port says \"other_armor_active\"); got "
                        + (underFire.isPositive() ? "admitted"
                                : String.valueOf(underFire.getWarning())));
        holder.player.removeEffect(UltimateLifeformAddon.ULTIMATE_FIRE_ARMOR);
        ConditionCheck fireTypeNoEffect = armor.checkConditions(holder.power);
        helper.assertTrue(fireTypeNoEffect.isPositive(),
                "1.16 asked for the Fire Armor effect, not the armor type:"
                        + " without the effect Armor Protection was admitted;"
                        + " got " + fireTypeNoEffect.getWarning());
        holder.data.setArmorType(
                holder.player, UltimateLifeformData.ArmorType.NONE);
        ConditionCheck free = fireArmor.checkConditions(holder.power);
        helper.assertTrue(free.isPositive(),
                "Premise: Fire Armor without any armor was refused: "
                        + free.getWarning());
        holder.player.addEffect(new net.minecraft.world.effect
                .MobEffectInstance(UltimateLifeformAddon.ULTIMATE_ARMOR,
                        100, 0, false, false, false));
        ConditionCheck underArmor = fireArmor.checkConditions(holder.power);
        helper.assertTrue(
                !underArmor.isPositive()
                        && isWarning(underArmor, "other_armor_active"),
                "1.16 refused Fire Armor while the Armor Protection effect was"
                        + " on (\"other_armor_active\"), armor type "
                        + holder.data.getArmorType() + "; got "
                        + (underArmor.isPositive() ? "admitted"
                                : String.valueOf(underArmor.getWarning())));
        holder.player.removeEffect(UltimateLifeformAddon.ULTIMATE_ARMOR);
        holder.close((short) 32_142);
        helper.succeed();
    }

    // 1.16 UltimateLifeformData.setArmorType put the armor effect on once for
    // 1000000 ticks and nothing refreshed it: milk took it off while the armor
    // type stayed; UltimateArmorEffect took itself off after an in fire, on
    // fire or lava hit (not hot floor) and UltimateFireArmorEffect put the fire
    // out; tickUser took only the fire armor effect off after 1200 cancelled
    // fire hits; GameplayEventHandler.cancelDamage stopped lava, hot floor, in
    // fire and on fire hits only while the fire armor effect was on; onLeap
    // asked for the effects; tick set a dead user's armor type to NONE.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void armorEffectComesOffByMilkFireAndDeath(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateArmorLife",
                "a4c1d7e2-3b5f-4a60-9d1e-7f2b8c4e6a90");
        ServerPlayer player = holder.player;
        UltimateLifeformData data = holder.data;
        data.setCommandEnergy(player, UltimateLifeformData.MAX_ENERGY);
        data.setArmorType(player, UltimateLifeformData.ArmorType.NORMAL);
        helper.assertTrue(player.hasEffect(UltimateLifeformAddon.ULTIMATE_ARMOR),
                "Premise: Armor Protection did not put its effect on");
        player.removeEffectsCuredBy(EffectCures.MILK);
        data.tick(holder.power);
        helper.assertTrue(!player.hasEffect(UltimateLifeformAddon.ULTIMATE_ARMOR)
                        && data.getArmorType()
                                == UltimateLifeformData.ArmorType.NORMAL,
                "1.16 milk took the armor effect off for good and the armor"
                        + " type stayed: effect="
                        + player.hasEffect(UltimateLifeformAddon.ULTIMATE_ARMOR)
                        + " type=" + data.getArmorType());
        holder.power.onLeap();
        helper.assertTrue(data.isFlying(),
                "1.16 onLeap asked for the armor effects: after milk the"
                        + " Ultimate Lifeform took off gliding; flying="
                        + data.isFlying());
        data.setFlying(player, false);
        player.stopFallFlying();
        data.setArmorType(player, UltimateLifeformData.ArmorType.NORMAL);
        holder.lastDamage[0] = damage(helper, DamageTypes.HOT_FLOOR);
        data.tick(holder.power);
        helper.assertTrue(player.hasEffect(UltimateLifeformAddon.ULTIMATE_ARMOR),
                "1.16 kept the armor effect after a hot floor hit");
        holder.lastDamage[0] = damage(helper, DamageTypes.IN_FIRE);
        data.tick(holder.power);
        helper.assertTrue(!player.hasEffect(UltimateLifeformAddon.ULTIMATE_ARMOR)
                        && data.getArmorType()
                                == UltimateLifeformData.ArmorType.NORMAL,
                "1.16 took the armor effect off after an in fire hit and kept"
                        + " the armor type: effect="
                        + player.hasEffect(UltimateLifeformAddon.ULTIMATE_ARMOR)
                        + " type=" + data.getArmorType());
        holder.lastDamage[0] = null;
        data.setArmorType(player, UltimateLifeformData.ArmorType.FIRE);
        player.setRemainingFireTicks(100);
        holder.lastDamage[0] = damage(helper, DamageTypes.ON_FIRE);
        data.tick(holder.power);
        holder.lastDamage[0] = null;
        helper.assertTrue(player.getRemainingFireTicks() <= 0
                        && player.hasEffect(
                                UltimateLifeformAddon.ULTIMATE_FIRE_ARMOR),
                "1.16 fire armor put the fire out after an on fire hit and"
                        + " stayed on: fire=" + player.getRemainingFireTicks()
                        + " effect=" + player.hasEffect(
                                UltimateLifeformAddon.ULTIMATE_FIRE_ARMOR));
        helper.assertTrue(cancels(player, damage(helper, DamageTypes.LAVA)),
                "Premise: the fire armor did not stop a lava hit");
        helper.assertTrue(!cancels(player, damage(helper, DamageTypes.FIREBALL)),
                "1.16 fire armor let a fireball hit through");
        for (int hit = 0; hit < 1_200; hit++) {
            data.addFireDamageCount();
        }
        data.tick(holder.power);
        helper.assertTrue(
                !player.hasEffect(UltimateLifeformAddon.ULTIMATE_FIRE_ARMOR)
                        && data.getArmorType()
                                == UltimateLifeformData.ArmorType.FIRE,
                "1.16 took only the fire armor effect off after 1200 cancelled"
                        + " fire hits: effect=" + player.hasEffect(
                                UltimateLifeformAddon.ULTIMATE_FIRE_ARMOR)
                        + " type=" + data.getArmorType());
        helper.assertTrue(!cancels(player, damage(helper, DamageTypes.LAVA)),
                "1.16 stopped lava only while the fire armor effect was on;"
                        + " armor type FIRE without the effect stopped it");
        data.setArmorType(player, UltimateLifeformData.ArmorType.NORMAL);
        player.setHealth(0.0F);
        data.tick(holder.power);
        player.setHealth(player.getMaxHealth());
        helper.assertTrue(
                data.getArmorType() == UltimateLifeformData.ArmorType.NONE,
                "1.16 set a dead user's armor type to NONE; got "
                        + data.getArmorType());
        holder.close((short) 32_149);
        helper.succeed();
    }

    // 1.16 UltimateLifeformProtectionArmor / FireProtectionArmor.perform
    // (:46-55) toggled by the armor's effect, not by the stored type: a press
    // after milk or the 1200 fire hits had taken the effect off put it on.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void armorPressTogglesByTheEffect(GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateArmorToggle",
                "b5d2e8f3-4c6a-4b71-8e2f-0a3c9d5f7b01");
        ServerPlayer player = holder.player;
        UltimateLifeformData data = holder.data;
        UltimateLifeformCombat.perform(player, data,
                UltimateLifeformSpec.ARMOR_PROTECTION, 0);
        helper.assertTrue(
                data.getArmorType() == UltimateLifeformData.ArmorType.NORMAL
                        && player.hasEffect(UltimateLifeformAddon.ULTIMATE_ARMOR),
                "Premise: Armor Protection did not turn the armor on");
        player.removeEffectsCuredBy(EffectCures.MILK);
        UltimateLifeformCombat.perform(player, data,
                UltimateLifeformSpec.ARMOR_PROTECTION, 0);
        helper.assertTrue(
                data.getArmorType() == UltimateLifeformData.ArmorType.NORMAL
                        && player.hasEffect(UltimateLifeformAddon.ULTIMATE_ARMOR),
                "1.16 put the armor back on with a press after milk had taken"
                        + " its effect off: type=" + data.getArmorType()
                        + " effect=" + player.hasEffect(
                                UltimateLifeformAddon.ULTIMATE_ARMOR));
        UltimateLifeformCombat.perform(player, data,
                UltimateLifeformSpec.ARMOR_PROTECTION, 0);
        helper.assertTrue(
                data.getArmorType() == UltimateLifeformData.ArmorType.NONE
                        && !player.hasEffect(
                                UltimateLifeformAddon.ULTIMATE_ARMOR),
                "Premise: a press with the armor on did not turn it off");
        UltimateLifeformCombat.perform(player, data,
                UltimateLifeformSpec.ARMOR_FIRE_PROTECTION, 0);
        player.removeEffect(UltimateLifeformAddon.ULTIMATE_FIRE_ARMOR);
        UltimateLifeformCombat.perform(player, data,
                UltimateLifeformSpec.ARMOR_FIRE_PROTECTION, 0);
        helper.assertTrue(
                data.getArmorType() == UltimateLifeformData.ArmorType.FIRE
                        && player.hasEffect(
                                UltimateLifeformAddon.ULTIMATE_FIRE_ARMOR),
                "1.16 put the fire armor back on with a press after its effect"
                        + " had come off: type=" + data.getArmorType()
                        + " effect=" + player.hasEffect(
                                UltimateLifeformAddon.ULTIMATE_FIRE_ARMOR));
        data.setArmorType(player, UltimateLifeformData.ArmorType.NONE);
        holder.close((short) 32_150);
        helper.succeed();
    }

    // 1.16 put the armor effects (UltimateLifeformData.setArmorType :303-323)
    // and the power's passive effects (tickUser :115-126, tick :199-206) on
    // with EffectInstance(..., false, false, true): no particles, icon shown,
    // re-applied on every tick; only the health boost and saturation were
    // hidden. toggleNightVision and onClear removed the effects whatever
    // their flags.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void powerEffectsShowTheirIconsAs116(GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateEffectIcons",
                "d7f4a015-6e8c-4d93-a041-2c5ebf719d23");
        ServerPlayer player = holder.player;
        UltimateLifeformData data = holder.data;
        data.setArmorType(player, UltimateLifeformData.ArmorType.NORMAL);
        assertShownIcon(helper, player, UltimateLifeformAddon.ULTIMATE_ARMOR,
                "Armor Protection");
        data.setArmorType(player, UltimateLifeformData.ArmorType.NONE);
        helper.assertTrue(!player.hasEffect(UltimateLifeformAddon.ULTIMATE_ARMOR),
                "1.16 setArmorType(NONE) took the armor effect off");
        data.setArmorType(player, UltimateLifeformData.ArmorType.FIRE);
        assertShownIcon(helper, player,
                UltimateLifeformAddon.ULTIMATE_FIRE_ARMOR, "Fire Armor");
        data.toggleNightVision(player);
        data.tick(holder.power);
        assertShownIcon(helper, player,
                net.minecraft.world.effect.MobEffects.NIGHT_VISION,
                "Night Vision");
        player.removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.NIGHT_VISION,
                300, 0, false, false, true));
        data.tick(holder.power);
        int nightVisionTicks = player.getEffect(
                net.minecraft.world.effect.MobEffects.NIGHT_VISION).getDuration();
        helper.assertTrue(nightVisionTicks == 400,
                "1.16 put Night Vision on again for 400 ticks on every tick; "
                        + "got " + nightVisionTicks);
        data.toggleNightVision(player);
        helper.assertTrue(!player.hasEffect(
                        net.minecraft.world.effect.MobEffects.NIGHT_VISION),
                "1.16 toggleNightVision took the shown Night Vision off");
        data.toggleStoneForm(player);
        data.tick(holder.power);
        assertShownIcon(helper, player,
                rotp.core.init.ModStatusEffects.STUN, "stone form stun");
        assertShownIcon(helper, player,
                net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE,
                "stone form Resistance");
        assertShownIcon(helper, player,
                net.minecraft.world.effect.MobEffects.BLINDNESS,
                "stone form Blindness");
        assertShownIcon(helper, player,
                net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE,
                "stone form Fire Resistance");
        data.toggleStoneForm(player);
        player.setSprinting(true);
        data.tick(holder.power);
        player.setSprinting(false);
        assertShownIcon(helper, player,
                net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED,
                "sprint Speed");
        net.minecraft.world.effect.MobEffectInstance healthBoost =
                player.getEffect(UltimateLifeformAddon.ULTIMATE_HEALTH_BOOST);
        helper.assertTrue(healthBoost != null && !healthBoost.showIcon(),
                "1.16 kept the health boost icon hidden: " + healthBoost);
        holder.power.setPowerType(null);
        helper.assertTrue(
                !player.hasEffect(UltimateLifeformAddon.ULTIMATE_FIRE_ARMOR),
                "1.16 onClear took the shown Fire Armor effect off");
        holder.close((short) 32_151);
        helper.succeed();
    }

    private static void assertShownIcon(GameTestHelper helper,
            ServerPlayer player,
            net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
            String what) {
        net.minecraft.world.effect.MobEffectInstance instance =
                player.getEffect(effect);
        helper.assertTrue(instance != null
                        && instance.showIcon()
                        && !instance.isVisible()
                        && !instance.isAmbient(),
                "1.16 showed the " + what + " icon without particles; got "
                        + (instance == null ? "no effect"
                                : "icon=" + instance.showIcon()
                                        + " particles=" + instance.isVisible()));
    }

    // 1.16 UltimateArmorEffect / UltimateFireArmorEffect.applyEffectTick set the
    // Caelus ELYTRA_FLIGHT base to 0 on every tick the user was fall flying
    // with the effect on, so an armor effect that came on during a glide (not
    // only through setArmorType) ended the Ultimate Lifeform's flight.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void armorEffectsShutTheGlideOffEveryTick(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateArmorGlide",
                "e8a5b126-7f9d-4ea4-b152-3d6fc082ae34");
        ServerPlayer player = holder.player;
        UltimateLifeformData data = holder.data;
        holder.power.onLeap();
        data.tick(holder.power);
        helper.assertTrue(player.isFallFlying() && hasUltimateFlight(player),
                "Premise: a glide without armor lost its flight: gliding="
                        + player.isFallFlying()
                        + " flight=" + hasUltimateFlight(player));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                UltimateLifeformAddon.ULTIMATE_ARMOR, 100, 0,
                false, false, true));
        data.tick(holder.power);
        helper.assertTrue(!hasUltimateFlight(player),
                "1.16 UltimateArmorEffect shut the flight off on a tick the user"
                        + " glided with it; flight=" + hasUltimateFlight(player));
        player.removeEffect(UltimateLifeformAddon.ULTIMATE_ARMOR);
        rotp.addons.ultimate_lifeform.power.UltimateLifeformPowerType
                .enableFlight(player);
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                UltimateLifeformAddon.ULTIMATE_FIRE_ARMOR, 100, 0,
                false, false, true));
        data.tick(holder.power);
        helper.assertTrue(!hasUltimateFlight(player),
                "1.16 UltimateFireArmorEffect shut the flight off on a tick the"
                        + " user glided with it; flight="
                        + hasUltimateFlight(player));
        player.removeEffect(UltimateLifeformAddon.ULTIMATE_FIRE_ARMOR);
        player.stopFallFlying();
        holder.close((short) 32_152);
        helper.succeed();
    }

    private static boolean hasUltimateFlight(ServerPlayer player) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance flight =
                player.getAttribute(com.illusivesoulworks.caelus.api.CaelusApi
                        .getInstance().getFallFlyingAttribute());
        return flight != null && flight.hasModifier(
                rotp.addons.ultimate_lifeform.power.UltimateLifeformPowerType
                        .FLIGHT_MODIFIER_ID);
    }

    // 1.16 GameplayEventHandler.cancelDamage (:310-316) ran on LivingAttackEvent,
    // which Forge posted at the head of PlayerEntity.hurt: a fire hit under the
    // fire armor effect counted toward the 1200 and was cancelled even when
    // Fire Resistance or the creative mode would have refused it right after.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fireArmorCountsHitsAtTheHeadOfPlayerHurt(
            GameTestHelper helper) {
        ServerPlayer player = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.fromString(
                        "f9b6c237-8a0e-4fb5-a263-4e7a0d93bf45"),
                        "UltimateFireArmorHurt")) {
            // reach Player.hurt the way a real player does
            @Override
            public boolean isInvulnerableTo(DamageSource source) {
                return false;
            }
        };
        try {
            java.lang.reflect.Field spawnInvulnerable = ServerPlayer.class
                    .getDeclaredField("spawnInvulnerableTime");
            spawnInvulnerable.setAccessible(true);
            spawnInvulnerable.setInt(player, 0);
        }
        catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Premise: no ServerPlayer.spawnInvulnerableTime", exception);
        }
        player.setPos(Vec3.atBottomCenterOf(
                helper.absolutePos(new net.minecraft.core.BlockPos(2, 2, 2))));
        player.setGameMode(GameType.SURVIVAL);
        helper.assertTrue(helper.getLevel().addFreshEntity(player),
                "Premise: the fire armor test player could not be added");
        PlayerPower power = PowerClass.PLAYER_POWER.attachGet(player);
        helper.assertTrue(power.trySetPowerType(
                        UltimateLifeformAddon.ULTIMATE_LIFEFORM.get()),
                "Premise: the power grant failed");
        UltimateLifeformData data = power.getCurTypeData(
                UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElseThrow();
        data.setArmorType(player, UltimateLifeformData.ArmorType.FIRE);
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 200));
        boolean resistedLava = player.hurt(
                damage(helper, DamageTypes.LAVA), 4.0F);
        helper.assertTrue(!resistedLava && data.getFireDamageCount() == 1,
                "1.16 counted a lava hit under the fire armor before the Fire"
                        + " Resistance check; hurt=" + resistedLava
                        + " count=" + data.getFireDamageCount());
        player.removeEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE);
        player.setGameMode(GameType.CREATIVE);
        boolean creativeFire = player.hurt(
                damage(helper, DamageTypes.IN_FIRE), 1.0F);
        helper.assertTrue(!creativeFire && data.getFireDamageCount() == 2,
                "1.16 counted an in fire hit under the fire armor before the"
                        + " creative check; hurt=" + creativeFire
                        + " count=" + data.getFireDamageCount());
        player.setGameMode(GameType.SURVIVAL);
        float health = player.getHealth();
        boolean burning = player.hurt(
                damage(helper, DamageTypes.ON_FIRE), 1.0F);
        helper.assertTrue(!burning && data.getFireDamageCount() == 3
                        && player.getHealth() == health,
                "1.16 cancelled and counted an on fire hit under the fire armor;"
                        + " hurt=" + burning + " count="
                        + data.getFireDamageCount() + " health "
                        + health + " -> " + player.getHealth());
        power.setPowerType(null);
        player.discard();
        helper.succeed();
    }

    // 1.16 UltimateLifeformOrganismInfusion.isBlockLiving (:215-226) was the
    // add-on's own rule: eggs, pumpkins and melons, hay and the other GRASS
    // material blocks were living, logs, coral blocks and bamboo planks were
    // not, and a flower pot counted only with living contents (canChargeBlock
    // :130-146 with the core's isItemLivingMatter: not a dead bush). Plant
    // Infusion ran the Organism Infusion perform, so an egg next to the aimed
    // block was charged too.
    @GameTest(template = "empty", timeoutTicks = 20, batch = "ulf_own_infusions_count_the116_living_blocks")
    public static void infusionsCountThe116LivingBlocks(GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateLivingBlocks",
                "c6e3f904-5d7b-4c82-9f30-1b4dae608c12");
        setHamon(holder.data, 1_000);
        Ability organism = holder.power.getAbility(
                UltimateLifeformSpec.HAMON_ORGANISM_INFUSION.id);
        Ability plant = holder.power.getAbility(
                UltimateLifeformSpec.HAMON_PLANT_INFUSION.id);
        helper.assertTrue(organism != null && plant != null,
                "Premise: no infusions in the moveset");
        Object[][] rows = {
                {net.minecraft.world.level.block.Blocks.TURTLE_EGG, true},
                {net.minecraft.world.level.block.Blocks.DRAGON_EGG, true},
                {net.minecraft.world.level.block.Blocks.PUMPKIN, true},
                {net.minecraft.world.level.block.Blocks.MELON, true},
                {net.minecraft.world.level.block.Blocks.JACK_O_LANTERN, true},
                {net.minecraft.world.level.block.Blocks.HAY_BLOCK, true},
                {net.minecraft.world.level.block.Blocks.TARGET, true},
                {net.minecraft.world.level.block.Blocks.FERN, true},
                {net.minecraft.world.level.block.Blocks.POTTED_DANDELION, true},
                {net.minecraft.world.level.block.Blocks.OAK_LOG, false},
                {net.minecraft.world.level.block.Blocks.TUBE_CORAL_BLOCK, false},
                {net.minecraft.world.level.block.Blocks.BAMBOO_PLANKS, false},
                {net.minecraft.world.level.block.Blocks.DEAD_BUSH, false},
                {net.minecraft.world.level.block.Blocks.POTTED_DEAD_BUSH, false},
                {net.minecraft.world.level.block.Blocks.FLOWER_POT, false}};
        net.minecraft.core.BlockPos spot = new net.minecraft.core.BlockPos(2, 2, 4);
        for (Object[] row : rows) {
            net.minecraft.world.level.block.Block block =
                    (net.minecraft.world.level.block.Block) row[0];
            boolean living = (Boolean) row[1];
            holder.aimBlock(helper, spot, block);
            ConditionCheck check = organism.checkConditions(holder.power);
            helper.assertTrue(living
                            ? check.isPositive()
                            : !check.isPositive()
                                    && isWarning(check, "living_plant"),
                    "1.16 counted " + block + (living ? " as" : " as not")
                            + " living for Hamon Organism Infusion; got "
                            + (check.isPositive() ? "admitted"
                                    : String.valueOf(check.getWarning())));
        }
        holder.aimBlock(helper, spot,
                net.minecraft.world.level.block.Blocks.PUMPKIN);
        ConditionCheck pumpkin = plant.checkConditions(holder.power);
        holder.aimBlock(helper, spot,
                net.minecraft.world.level.block.Blocks.OAK_LOG);
        ConditionCheck log = plant.checkConditions(holder.power);
        helper.assertTrue(pumpkin.isPositive()
                        && !log.isPositive() && isWarning(log, "living_plant"),
                "1.16 Hamon Plant Infusion took a pumpkin and refused a log;"
                        + " got pumpkin=" + (pumpkin.isPositive() ? "admitted"
                                : String.valueOf(pumpkin.getWarning()))
                        + " log=" + (log.isPositive() ? "admitted"
                                : String.valueOf(log.getWarning())));
        net.minecraft.core.BlockPos center = holder.aimBlock(helper, spot,
                net.minecraft.world.level.block.Blocks.OAK_LEAVES);
        net.minecraft.core.BlockPos egg = center.above();
        helper.getLevel().setBlockAndUpdate(egg,
                net.minecraft.world.level.block.Blocks.TURTLE_EGG
                        .defaultBlockState());
        UltimateLifeformCombat.perform(holder.player, holder.data,
                UltimateLifeformSpec.HAMON_PLANT_INFUSION, 0);
        List<rotp.addons.ultimate_lifeform.entity.UltimateAreaEffectEntity>
                charges = helper.getLevel().getEntitiesOfClass(
                        rotp.addons.ultimate_lifeform.entity
                                .UltimateAreaEffectEntity.class,
                        new net.minecraft.world.phys.AABB(center).inflate(2.0D),
                        charge -> charge.isBlockCharge()
                                && charge.getOwner() == holder.player);
        long eggCharges = charges.stream()
                .filter(charge -> egg.equals(charge.getChargedBlockPos()))
                .count();
        charges.forEach(Entity::discard);
        helper.getLevel().setBlockAndUpdate(egg,
                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(center,
                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        helper.assertTrue(eggCharges == 1,
                "1.16 Hamon Plant Infusion charged the turtle egg next to the"
                        + " aimed leaves; charges there=" + eggCharges);
        holder.close((short) 32_151);
        helper.succeed();
    }

    // 1.16 UltimateLifeformOrganismInfusion.overrideVanillaMouseTarget
    // (:159-172): an aimed block became the animal or ambient mob without a
    // core Hamon charge inside its outline, then checkTarget judged that
    // entity (an undead horse is no living mob). Plant Infusion (a BLOCK
    // target) kept the block.
    @GameTest(template = "empty", timeoutTicks = 20, batch = "ulf_own_organism_infusion_takes_the_animal_inside_th")
    public static void organismInfusionTakesTheAnimalInsideTheAimedBlock(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateInfusionAnimal",
                "f9b62c37-801e-4fb5-a263-4e70d193bf45");
        setHamon(holder.data, 1_000);
        Ability organism = holder.power.getAbility(
                UltimateLifeformSpec.HAMON_ORGANISM_INFUSION.id);
        Ability plant = holder.power.getAbility(
                UltimateLifeformSpec.HAMON_PLANT_INFUSION.id);
        helper.assertTrue(organism != null && plant != null,
                "Premise: no infusions in the moveset");
        net.minecraft.core.BlockPos stone = holder.aimBlock(helper,
                new net.minecraft.core.BlockPos(2, 2, 4),
                net.minecraft.world.level.block.Blocks.STONE);
        Cow cow = cow(helper, new Vec3(2.5D, 2.0D, 4.5D));
        ConditionCheck organismCow = organism.checkConditions(holder.power);
        ConditionCheck plantCow = plant.checkConditions(holder.power);
        UltimateLifeformCombat.perform(holder.player, holder.data,
                UltimateLifeformSpec.HAMON_ORGANISM_INFUSION, 0);
        boolean cowCharged = UltimateHamonEntityCharge.get(cow).hasHamonCharge();
        List<rotp.addons.ultimate_lifeform.entity.UltimateAreaEffectEntity>
                charges = helper.getLevel().getEntitiesOfClass(
                        rotp.addons.ultimate_lifeform.entity
                                .UltimateAreaEffectEntity.class,
                        new net.minecraft.world.phys.AABB(stone).inflate(2.0D),
                        charge -> charge.isBlockCharge()
                                && charge.getOwner() == holder.player);
        int blockCharges = charges.size();
        charges.forEach(Entity::discard);
        helper.assertTrue(organismCow.isPositive() && cowCharged
                        && blockCharges == 0
                        && !plantCow.isPositive()
                        && isWarning(plantCow, "living_plant"),
                "1.16 Hamon Organism Infusion aimed at stone took the cow"
                        + " standing inside it, Plant Infusion kept the stone;"
                        + " got organism=" + (organismCow.isPositive()
                                ? "admitted" : String.valueOf(
                                        organismCow.getWarning()))
                        + " cowCharged=" + cowCharged
                        + " blockCharges=" + blockCharges + " plant="
                        + (plantCow.isPositive() ? "admitted"
                                : String.valueOf(plantCow.getWarning())));
        cow.discard();
        Cow charged = cow(helper, new Vec3(2.5D, 2.0D, 4.5D));
        rotp.core.impl.powers.hamon.EntityHamonChargeState.get(charged)
                .setHamonCharge(1.0F, 200, holder.player, 0.0F);
        ConditionCheck chargedCow = organism.checkConditions(holder.power);
        charged.discard();
        net.minecraft.world.entity.animal.horse.SkeletonHorse horse =
                EntityType.SKELETON_HORSE.create(helper.getLevel());
        if (horse == null) {
            throw new IllegalStateException("Skeleton horse fixture failed");
        }
        horse.setPos(Vec3.atBottomCenterOf(stone));
        horse.setNoAi(true);
        horse.setNoGravity(true);
        helper.getLevel().addFreshEntity(horse);
        ConditionCheck undead = organism.checkConditions(holder.power);
        horse.discard();
        helper.getLevel().setBlockAndUpdate(stone,
                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        helper.assertTrue(!chargedCow.isPositive()
                        && isWarning(chargedCow, "living_plant")
                        && !undead.isPositive()
                        && isWarning(undead, "living_mob"),
                "1.16 left a cow with a Hamon charge out (the stone stayed the"
                        + " target) and judged an undead horse as no living"
                        + " mob; got charged cow=" + (chargedCow.isPositive()
                                ? "admitted" : String.valueOf(
                                        chargedCow.getWarning()))
                        + " skeleton horse=" + (undead.isPositive()
                                ? "admitted"
                                : String.valueOf(undead.getWarning())));
        holder.close((short) 32_154);
        helper.succeed();
    }

    // 1.16 InputHandler.pickMouseTarget gave the infusions the vanilla
    // crosshair target only: leaves 6 blocks away, past block reach, were no
    // target, refused (TargetRequirement BLOCK / ANY; the port's
    // "living_plant" / "living_target" are kept), and nothing was charged.
    @GameTest(template = "empty", timeoutTicks = 20, batch = "ulf_own_infusions_take_only_the_crosshair_target")
    public static void infusionsTakeOnlyTheCrosshairTarget(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateInfusionReach",
                "e8a51b26-7f9d-4ea4-b152-3d6fc082ae34");
        setHamon(holder.data, 1_000);
        Ability organism = holder.power.getAbility(
                UltimateLifeformSpec.HAMON_ORGANISM_INFUSION.id);
        Ability plant = holder.power.getAbility(
                UltimateLifeformSpec.HAMON_PLANT_INFUSION.id);
        helper.assertTrue(organism != null && plant != null,
                "Premise: no infusions in the moveset");
        net.minecraft.core.BlockPos leaves = holder.aimBlock(helper,
                new net.minecraft.core.BlockPos(2, 3, 8),
                net.minecraft.world.level.block.Blocks.OAK_LEAVES);
        helper.assertTrue(organism.checkConditions(holder.power).isPositive()
                        && plant.checkConditions(holder.power).isPositive(),
                "Premise: the infusions refused leaves under the crosshair");
        LivingComponentAction.getComponent(holder.player).entityAim
                .setTarget(ActionTarget.EMPTY);
        ConditionCheck organismFar = organism.checkConditions(holder.power);
        ConditionCheck plantFar = plant.checkConditions(holder.power);
        UltimateLifeformCombat.perform(holder.player, holder.data,
                UltimateLifeformSpec.HAMON_ORGANISM_INFUSION, 0);
        List<rotp.addons.ultimate_lifeform.entity.UltimateAreaEffectEntity>
                charges = helper.getLevel().getEntitiesOfClass(
                        rotp.addons.ultimate_lifeform.entity
                                .UltimateAreaEffectEntity.class,
                        new net.minecraft.world.phys.AABB(leaves).inflate(2.0D),
                        charge -> charge.isBlockCharge()
                                && charge.getOwner() == holder.player);
        int charged = charges.size();
        charges.forEach(Entity::discard);
        helper.getLevel().setBlockAndUpdate(leaves,
                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        helper.assertTrue(!organismFar.isPositive()
                        && isWarning(organismFar, "living_target")
                        && !plantFar.isPositive()
                        && isWarning(plantFar, "living_plant")
                        && charged == 0,
                "1.16 infusions took only the crosshair target: leaves 6"
                        + " blocks away with nothing under the crosshair were"
                        + " refused (the port says \"living_target\" /"
                        + " \"living_plant\") and not charged; got organism="
                        + (organismFar.isPositive() ? "admitted"
                                : String.valueOf(organismFar.getWarning()))
                        + " plant=" + (plantFar.isPositive() ? "admitted"
                                : String.valueOf(plantFar.getWarning()))
                        + " charges=" + charged);
        holder.close((short) 32_153);
        helper.succeed();
    }

    // 1.16 ran the aimed moves' checks, the HUD's and the client's own check
    // of a press, on the client's mouse target, as the server did with the
    // target the click sent. The user's copy on its own client never gets
    // the aim back (only a Stand's is sent), so the core's helper
    // (LivingComponentAction.getAimTarget) gives it the aim its client sends.
    // The holder stands in for that copy while its isLocalPlayer() is true
    // (LocalPlayer's), holding no aim, its client's aim in CLIENT_PLAYER_AIM,
    // which ClientsideAim.playerAim is. Its own batch: CLIENT_PLAYER_AIM is
    // global.
    @GameTest(template = "empty", timeoutTicks = 20,
            batch = "ulf_own_client_aim")
    public static void ownClientChecksReadTheAimItSendsAsIn116(
            GameTestHelper helper) {
        boolean[] local = {false};
        Holder holder = holder(helper, "UltimateOwnClientAim",
                "6f0b8d2e-3c1a-4c5e-9f7d-2b8a4e6c1d90", GameType.SURVIVAL,
                local);
        holder.data.setCommandEnergy(holder.player,
                UltimateLifeformData.MAX_ENERGY);
        setHamon(holder.data, 3_000);
        Cow cow = cow(helper, new Vec3(2.5D, 2.0D, 4.0D));
        net.minecraft.world.entity.animal.Wolf wolf =
                EntityType.WOLF.create(helper.getLevel());
        helper.assertTrue(wolf != null, "Premise: wolf fixture failed");
        wolf.setPos(Vec3.atBottomCenterOf(
                helper.absolutePos(new net.minecraft.core.BlockPos(3, 2, 4))));
        wolf.setNoAi(true);
        wolf.setNoGravity(true);
        helper.getLevel().addFreshEntity(wolf);
        net.minecraft.core.BlockPos leaves =
                helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 4));
        helper.getLevel().setBlockAndUpdate(leaves,
                net.minecraft.world.level.block.Blocks.OAK_LEAVES
                        .defaultBlockState());
        ActionTarget atLeaves = new ActionTarget(
                leaves, net.minecraft.core.Direction.NORTH);
        List<String> wrong = new ArrayList<>();
        try {
            ownClientTakesTheAim(helper, holder, local,
                    UltimateLifeformSpec.HAMON_ORGANISM_INFUSION,
                    new ActionTarget(cow), wrong);
            ownClientTakesTheAim(helper, holder, local,
                    UltimateLifeformSpec.HAMON_PLANT_INFUSION, atLeaves, wrong);
            ownClientTakesTheAim(helper, holder, local,
                    UltimateLifeformSpec.HYPNOTIZE, new ActionTarget(wolf),
                    wrong);
            ownClientTakesTheAim(helper, holder, local,
                    UltimateLifeformSpec.HAMON_LIFE_MAGNETISM, atLeaves,
                    wrong);
            ownClientTakesTheAim(helper, holder, local,
                    UltimateLifeformSpec.HAMON_OVERDRIVE, new ActionTarget(cow),
                    wrong);
            // looking away, so that no fallback ray finds the cow: the moves'
            // own target reads (the press, the held recheck, Absorption's
            // client loop) on either side
            holder.player.setYRot(180.0F);
            holder.player.setYHeadRot(180.0F);
            for (boolean ownClient : new boolean[] {false, true}) {
                local[0] = ownClient;
                LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(ownClient
                        ? new ActionTarget(cow) : ActionTarget.EMPTY);
                LivingComponentAction.getComponent(holder.player).entityAim
                        .setTarget(ownClient
                                ? ActionTarget.EMPTY : new ActionTarget(cow));
                Entity crosshair = UltimateLifeformCombat
                        .crosshairTarget(holder.player).getMainEntity();
                Entity infusion = UltimateLifeformCombat.infusionTarget(
                                holder.player,
                                UltimateLifeformSpec.HAMON_ORGANISM_INFUSION)
                        .getMainEntity();
                Entity current = UltimateLifeformCombat
                        .currentTarget(holder.player, 8.0D).getMainEntity();
                Entity absorbed = UltimateLifeformCombat.absorptionTarget(
                        holder.player, holder.data);
                local[0] = false;
                LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(
                        ActionTarget.EMPTY);
                if (crosshair != cow || infusion != cow || current != cow
                        || absorbed != cow) {
                    wrong.add((ownClient ? "the user's own client" : "the server")
                            + " read crosshair " + crosshair + ", infusion "
                            + infusion + ", current " + current
                            + ", absorption " + absorbed + " for the aimed cow");
                }
            }
        }
        finally {
            local[0] = false;
            LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(ActionTarget.EMPTY);
            cow.discard();
            wolf.discard();
            helper.getLevel().setBlockAndUpdate(leaves,
                    net.minecraft.world.level.block.Blocks.AIR
                            .defaultBlockState());
            holder.close((short) 32_160);
        }
        helper.assertTrue(wrong.isEmpty(),
                "1.16 checked the aimed moves on the client's own mouse"
                        + " target: " + String.join("; ", wrong));
        helper.succeed();
    }

    // the move's check (the HUD's, the press's): on the server with the aim
    // its client sent usable and with none refused (premises), and on the
    // user's own client, holding no aim, with the aim it sends usable
    private static void ownClientTakesTheAim(GameTestHelper helper,
            Holder holder, boolean[] local, UltimateLifeformSpec spec,
            ActionTarget aim, List<String> wrong) {
        Ability ability = holder.power.getAbility(spec.id);
        helper.assertTrue(ability != null,
                "Premise: no " + spec.id + " in the moveset");
        LivingComponentAction component =
                LivingComponentAction.getComponent(holder.player);
        local[0] = false;
        LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(ActionTarget.EMPTY);
        component.entityAim.setTarget(aim);
        ConditionCheck server = ability.checkConditions(holder.power);
        component.entityAim.setTarget(ActionTarget.EMPTY);
        ConditionCheck none = ability.checkConditions(holder.power);
        helper.assertTrue(server.isPositive() && !none.isPositive(),
                "Premise: on the server " + spec.id + " with the aim "
                        + (server.isPositive() ? "usable"
                                : String.valueOf(server.getWarning()))
                        + ", with none usable " + none.isPositive());
        local[0] = true;
        LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(aim);
        ConditionCheck client = ability.checkConditions(holder.power);
        local[0] = false;
        LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(ActionTarget.EMPTY);
        if (!client.isPositive()) {
            wrong.add(spec.id + " refused on the user's own client ("
                    + client.getWarning() + ") though usable on the server");
        }
    }

    // 1.16 UltimateLifeformLifeMagnetism (TargetRequirement NONE) and
    // UltimateLifeformTameAnimal (ENTITY) took the vanilla crosshair target
    // only, with no longer ray behind it: leaves 6 blocks away past block
    // reach were no aimed leaves, and Hypnotize with no entity under the
    // crosshair was refused without a message; its checkTarget (:69-84) said
    // "hypnosis" for an entity it could not tame.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void lifeMagnetismAndHypnotizeTakeOnlyTheCrosshairTarget(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateCrosshairOnly",
                "3d85d9b6-a2a8-4030-8125-ddbdcf50eba0");
        holder.data.setCommandEnergy(holder.player,
                UltimateLifeformData.MAX_ENERGY);
        setHamon(holder.data, 3_000);
        Ability magnetism = holder.power.getAbility(
                UltimateLifeformSpec.HAMON_LIFE_MAGNETISM.id);
        Ability hypnotize = holder.power.getAbility(
                UltimateLifeformSpec.HYPNOTIZE.id);
        helper.assertTrue(magnetism != null && hypnotize != null,
                "Premise: no Life Magnetism or Hypnotize in the moveset");
        net.minecraft.core.BlockPos leaves =
                helper.absolutePos(new net.minecraft.core.BlockPos(2, 3, 8));
        helper.getLevel().setBlockAndUpdate(leaves,
                net.minecraft.world.level.block.Blocks.OAK_LEAVES
                        .defaultBlockState());
        holder.player.lookAt(EntityAnchorArgument.Anchor.EYES,
                Vec3.atCenterOf(leaves));
        LivingComponentAction.getComponent(holder.player).entityAim
                .setTarget(ActionTarget.EMPTY);
        ConditionCheck farLeaves = magnetism.checkConditions(holder.power);
        helper.assertTrue(!farLeaves.isPositive()
                        && isWarning(farLeaves, "leaves"),
                "1.16 Life Magnetism took only the crosshair target: leaves 6"
                        + " blocks away with nothing under the crosshair were"
                        + " no leaves (\"leaves\"); got "
                        + (farLeaves.isPositive() ? "admitted"
                                : String.valueOf(farLeaves.getWarning())));
        holder.player.getInventory().setItem(
                12, new ItemStack(Items.BIRCH_LEAVES, 3));
        UltimateLifeformCombat.perform(holder.player, holder.data,
                UltimateLifeformSpec.HAMON_LIFE_MAGNETISM, 0);
        List<rotp.core.impl.powers.hamon.entity.LeavesGliderEntity> farGliders =
                helper.getLevel().getEntitiesOfClass(
                        rotp.core.impl.powers.hamon.entity.LeavesGliderEntity
                                .class,
                        new net.minecraft.world.phys.AABB(leaves).inflate(2.0D));
        boolean leavesStayed = helper.getLevel().getBlockState(leaves)
                .is(net.minecraft.world.level.block.Blocks.OAK_LEAVES);
        int inventoryLeaves = holder.player.getInventory().getItem(12).getCount();
        helper.getLevel().getEntitiesOfClass(
                        rotp.core.impl.powers.hamon.entity.LeavesGliderEntity
                                .class,
                        holder.player.getBoundingBox().inflate(12.0D))
                .forEach(Entity::discard);
        helper.getLevel().setBlockAndUpdate(leaves,
                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        helper.assertTrue(leavesStayed && farGliders.isEmpty()
                        && inventoryLeaves == 2,
                "1.16 Life Magnetism used the inventory leaves, not leaves 6"
                        + " blocks away off the crosshair: leaves stayed="
                        + leavesStayed + " gliders there=" + farGliders.size()
                        + " inventory=" + inventoryLeaves);
        net.minecraft.world.entity.animal.Wolf wolf =
                EntityType.WOLF.create(helper.getLevel());
        helper.assertTrue(wolf != null, "Premise: wolf fixture failed");
        wolf.setPos(Vec3.atBottomCenterOf(
                helper.absolutePos(new net.minecraft.core.BlockPos(2, 3, 7))));
        wolf.setNoAi(true);
        wolf.setNoGravity(true);
        helper.getLevel().addFreshEntity(wolf);
        holder.player.lookAt(EntityAnchorArgument.Anchor.EYES,
                wolf.getBoundingBox().getCenter());
        LivingComponentAction.getComponent(holder.player).entityAim
                .setTarget(ActionTarget.EMPTY);
        ConditionCheck noEntity = hypnotize.checkConditions(holder.power);
        UltimateLifeformCombat.perform(holder.player, holder.data,
                UltimateLifeformSpec.HYPNOTIZE, 0);
        boolean tamedOffCrosshair = wolf.isTame();
        helper.assertTrue(!noEntity.isPositive()
                        && noEntity.getWarning() == null
                        && !tamedOffCrosshair,
                "1.16 Hypnotize took only the crosshair entity: with nothing"
                        + " under the crosshair it was refused without a"
                        + " message and the wolf 5 blocks ahead was not tamed;"
                        + " got " + (noEntity.isPositive() ? "admitted"
                                : String.valueOf(noEntity.getWarning()))
                        + " tamed=" + tamedOffCrosshair);
        holder.aim(wolf);
        ConditionCheck aimedWolf = hypnotize.checkConditions(holder.power);
        helper.assertTrue(aimedWolf.isPositive(),
                "Premise: Hypnotize refused the wolf under the crosshair: "
                        + aimedWolf.getWarning());
        Cow cow = cow(helper, new Vec3(4.5D, 2.0D, 4.5D));
        holder.aim(cow);
        ConditionCheck aimedCow = hypnotize.checkConditions(holder.power);
        helper.assertTrue(!aimedCow.isPositive()
                        && isWarning(aimedCow, "hypnosis"),
                "1.16 UltimateLifeformTameAnimal.checkTarget said \"hypnosis\""
                        + " for an entity it could not tame; got "
                        + (aimedCow.isPositive() ? "admitted"
                                : String.valueOf(aimedCow.getWarning())));
        wolf.discard();
        cow.discard();
        holder.close((short) 32_154);
        helper.succeed();
    }

    // 1.16 Material.EGG (turtle and dragon eggs) was living for the
    // infusions and Plant Infusion answered it with "turtle_egg"; the sniffer
    // egg came after materials and takes the same egg rule.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void snifferEggIsAnEggLikeThe116Eggs(GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateSnifferEgg",
                "d7f40a15-6e8c-4d93-a041-2c5ebf719d23");
        setHamon(holder.data, 1_000);
        Ability organism = holder.power.getAbility(
                UltimateLifeformSpec.HAMON_ORGANISM_INFUSION.id);
        Ability plant = holder.power.getAbility(
                UltimateLifeformSpec.HAMON_PLANT_INFUSION.id);
        helper.assertTrue(organism != null && plant != null,
                "Premise: no infusions in the moveset");
        net.minecraft.core.BlockPos egg = holder.aimBlock(helper,
                new net.minecraft.core.BlockPos(2, 2, 4),
                net.minecraft.world.level.block.Blocks.SNIFFER_EGG);
        ConditionCheck organismCheck = organism.checkConditions(holder.power);
        ConditionCheck plantCheck = plant.checkConditions(holder.power);
        helper.getLevel().setBlockAndUpdate(egg,
                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        helper.assertTrue(organismCheck.isPositive()
                        && !plantCheck.isPositive()
                        && isWarning(plantCheck, "turtle_egg"),
                "the sniffer egg follows 1.16's egg rule (living for Organism"
                        + " Infusion, \"turtle_egg\" for Plant Infusion); got"
                        + " organism=" + (organismCheck.isPositive() ? "admitted"
                                : String.valueOf(organismCheck.getWarning()))
                        + " plant=" + (plantCheck.isPositive() ? "admitted"
                                : String.valueOf(plantCheck.getWarning())));
        holder.close((short) 32_152);
        helper.succeed();
    }

    // Blocks added after 1.16 under 1.16's living-block rule
    // (UltimateLifeformOrganismInfusion.isBlockLiving :215-226), by their kind
    // like the sniffer egg: the newer plants are living, frogspawn is an egg
    // (Plant Infusion says "turtle_egg"), wood roots, mud, rooted dirt,
    // sculk, froglights, amethyst and the bamboo block are not.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void blocksAddedAfter116FollowTheLivingRuleByKind(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateNewerBlocks",
                "ceac9249-5e73-48f5-9f89-d0a0e0536e4b");
        setHamon(holder.data, 1_000);
        Ability organism = holder.power.getAbility(
                UltimateLifeformSpec.HAMON_ORGANISM_INFUSION.id);
        Ability plant = holder.power.getAbility(
                UltimateLifeformSpec.HAMON_PLANT_INFUSION.id);
        helper.assertTrue(organism != null && plant != null,
                "Premise: no infusions in the moveset");
        net.minecraft.world.level.block.state.BlockState lichen =
                net.minecraft.world.level.block.Blocks.GLOW_LICHEN
                        .defaultBlockState()
                        .setValue(net.minecraft.world.level.block.MultifaceBlock
                                .getFaceProperty(net.minecraft.core.Direction.DOWN),
                                true);
        Object[][] rows = {
                {net.minecraft.world.level.block.Blocks.BIG_DRIPLEAF
                        .defaultBlockState(), true},
                {net.minecraft.world.level.block.Blocks.BIG_DRIPLEAF_STEM
                        .defaultBlockState(), true},
                {net.minecraft.world.level.block.Blocks.SPORE_BLOSSOM
                        .defaultBlockState(), true},
                {net.minecraft.world.level.block.Blocks.HANGING_ROOTS
                        .defaultBlockState(), true},
                {lichen, true},
                {net.minecraft.world.level.block.Blocks.MOSS_CARPET
                        .defaultBlockState(), true},
                {net.minecraft.world.level.block.Blocks.MOSS_BLOCK
                        .defaultBlockState(), true},
                {net.minecraft.world.level.block.Blocks.FROGSPAWN
                        .defaultBlockState(), true},
                {net.minecraft.world.level.block.Blocks.AZALEA
                        .defaultBlockState(), true},
                {net.minecraft.world.level.block.Blocks.SMALL_DRIPLEAF
                        .defaultBlockState(), true},
                {net.minecraft.world.level.block.Blocks.CAVE_VINES
                        .defaultBlockState(), true},
                {net.minecraft.world.level.block.Blocks.MANGROVE_PROPAGULE
                        .defaultBlockState(), true},
                {net.minecraft.world.level.block.Blocks.PINK_PETALS
                        .defaultBlockState(), true},
                {net.minecraft.world.level.block.Blocks.TORCHFLOWER
                        .defaultBlockState(), true},
                {net.minecraft.world.level.block.Blocks.PITCHER_PLANT
                        .defaultBlockState(), true},
                {net.minecraft.world.level.block.Blocks.CHERRY_LEAVES
                        .defaultBlockState(), true},
                {net.minecraft.world.level.block.Blocks.ROOTED_DIRT
                        .defaultBlockState(), false},
                {net.minecraft.world.level.block.Blocks.MANGROVE_ROOTS
                        .defaultBlockState(), false},
                {net.minecraft.world.level.block.Blocks.MUD
                        .defaultBlockState(), false},
                {net.minecraft.world.level.block.Blocks.SCULK
                        .defaultBlockState(), false},
                {net.minecraft.world.level.block.Blocks.OCHRE_FROGLIGHT
                        .defaultBlockState(), false},
                {net.minecraft.world.level.block.Blocks.AMETHYST_BLOCK
                        .defaultBlockState(), false},
                {net.minecraft.world.level.block.Blocks.BAMBOO_BLOCK
                        .defaultBlockState(), false}};
        net.minecraft.core.BlockPos spot =
                helper.absolutePos(new net.minecraft.core.BlockPos(2, 2, 4));
        for (Object[] row : rows) {
            net.minecraft.world.level.block.state.BlockState state =
                    (net.minecraft.world.level.block.state.BlockState) row[0];
            boolean living = (Boolean) row[1];
            helper.getLevel().setBlockAndUpdate(spot, state);
            holder.player.lookAt(EntityAnchorArgument.Anchor.EYES,
                    Vec3.atCenterOf(spot));
            LivingComponentAction.getComponent(holder.player).entityAim
                    .setTarget(new ActionTarget(spot,
                            net.minecraft.core.Direction.NORTH));
            ConditionCheck check = organism.checkConditions(holder.power);
            helper.assertTrue(living
                            ? check.isPositive()
                            : !check.isPositive()
                                    && isWarning(check, "living_plant"),
                    "the 1.16 living-block rule by kind counts "
                            + helper.getLevel().getBlockState(spot)
                            + (living ? " as" : " as not")
                            + " living for Hamon Organism Infusion; got "
                            + (check.isPositive() ? "admitted"
                                    : String.valueOf(check.getWarning())));
        }
        helper.getLevel().setBlockAndUpdate(spot,
                net.minecraft.world.level.block.Blocks.FROGSPAWN
                        .defaultBlockState());
        ConditionCheck frogspawn = plant.checkConditions(holder.power);
        helper.getLevel().setBlockAndUpdate(spot,
                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        helper.assertTrue(!frogspawn.isPositive()
                        && isWarning(frogspawn, "turtle_egg"),
                "frogspawn takes 1.16's egg rule (\"turtle_egg\" for Hamon"
                        + " Plant Infusion); got " + (frogspawn.isPositive()
                                ? "admitted"
                                : String.valueOf(frogspawn.getWarning())));
        holder.close((short) 32_155);
        helper.succeed();
    }

    // 1.16 UltimateLifeformPlantInfusion: getTargetRequirement BLOCK (:79-81)
    // refused any other target (the port says "living_plant", owner
    // 2026-10-19); checkTarget (:65-76) said "turtle_egg" for a block of
    // Material.EGG (turtle and dragon eggs) and "living_plant" for a block
    // that is not living.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void plantInfusionSaysTurtleEggOrLivingPlant(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimatePlantTarget",
                "4eab6f20-7059-4c2e-8fa3-91d2c6c8f784");
        setHamon(holder.data, 1_000);
        Ability ability = holder.power.getAbility(
                UltimateLifeformSpec.HAMON_PLANT_INFUSION.id);
        helper.assertTrue(ability != null,
                "Premise: no Hamon Plant Infusion in the moveset");
        Cow cow = cow(helper, new Vec3(2.5D, 2.0D, 4.25D));
        holder.aim(cow);
        ConditionCheck mob = ability.checkConditions(holder.power);
        helper.assertTrue(!mob.isPositive() && isWarning(mob, "living_plant"),
                "1.16 refused Hamon Plant Infusion at a mob (the port says"
                        + " \"living_plant\"); got " + (mob.isPositive() ? "admitted"
                                : String.valueOf(mob.getWarning())));
        cow.discard();
        Object[][] blocks = {
                {net.minecraft.world.level.block.Blocks.TURTLE_EGG, "turtle_egg"},
                {net.minecraft.world.level.block.Blocks.DRAGON_EGG, "turtle_egg"},
                {net.minecraft.world.level.block.Blocks.STONE, "living_plant"},
                {net.minecraft.world.level.block.Blocks.OAK_LEAVES, null}};
        for (Object[] row : blocks) {
            net.minecraft.world.level.block.Block block =
                    (net.minecraft.world.level.block.Block) row[0];
            String expected = (String) row[1];
            net.minecraft.core.BlockPos pos = holder.aimBlock(helper,
                    new net.minecraft.core.BlockPos(2, 2, 4), block);
            ConditionCheck check = ability.checkConditions(holder.power);
            helper.assertTrue(expected == null
                            ? check.isPositive()
                            : !check.isPositive() && isWarning(check, expected),
                    "1.16 answered Hamon Plant Infusion at " + block + " with "
                            + (expected == null ? "admitted"
                                    : "\"" + expected + "\"")
                            + "; got " + (check.isPositive() ? "admitted"
                                    : String.valueOf(check.getWarning())));
            helper.getLevel().setBlockAndUpdate(pos,
                    net.minecraft.world.level.block.Blocks.AIR
                            .defaultBlockState());
        }
        holder.close((short) 32_143);
        helper.succeed();
    }

    // 1.16 UltimateLifeformOrganismInfusion: TargetRequirement ANY refused an
    // empty target (the port says "living_target", owner 2026-10-19);
    // checkTarget (:98-121) said "living_mob" for an entity that is not living
    // and canChargeBlock's "living_plant" for a block that is not living.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void organismInfusionSaysLivingMobLivingPlantOrLivingTarget(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateOrganismTarget",
                "5fbc7031-816a-4d3f-9ab4-a2e3d7d9f895");
        setHamon(holder.data, 1_000);
        Ability ability = holder.power.getAbility(
                UltimateLifeformSpec.HAMON_ORGANISM_INFUSION.id);
        helper.assertTrue(ability != null,
                "Premise: no Hamon Organism Infusion in the moveset");
        // up in the open air, looking at the sky
        Vec3 home = holder.player.position();
        holder.player.setPos(home.add(0.0D, 40.0D, 0.0D));
        holder.player.setXRot(-90.0F);
        LivingComponentAction.getComponent(holder.player).entityAim
                .setTarget(ActionTarget.EMPTY);
        ConditionCheck nothing = ability.checkConditions(holder.power);
        holder.player.setPos(home);
        helper.assertTrue(!nothing.isPositive()
                        && isWarning(nothing, "living_target"),
                "1.16 refused Hamon Organism Infusion at nothing (the port says"
                        + " \"living_target\"); got " + (nothing.isPositive() ? "admitted"
                                : String.valueOf(nothing.getWarning())));
        Vec3 spot = Vec3.atBottomCenterOf(helper.absolutePos(
                new net.minecraft.core.BlockPos(2, 2, 4)));
        net.minecraft.world.entity.decoration.ArmorStand stand =
                new net.minecraft.world.entity.decoration.ArmorStand(
                        helper.getLevel(), spot.x, spot.y, spot.z);
        stand.setNoGravity(true);
        helper.getLevel().addFreshEntity(stand);
        net.minecraft.world.entity.monster.Zombie zombie =
                EntityType.ZOMBIE.create(helper.getLevel());
        if (zombie == null) {
            throw new IllegalStateException("Zombie fixture failed");
        }
        zombie.setPos(spot);
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        helper.getLevel().addFreshEntity(zombie);
        for (Entity notLiving : new Entity[] {stand, zombie}) {
            holder.aim(notLiving);
            ConditionCheck check = ability.checkConditions(holder.power);
            helper.assertTrue(!check.isPositive()
                            && isWarning(check, "living_mob"),
                    "1.16 answered Hamon Organism Infusion at "
                            + notLiving.getType() + " with \"living_mob\"; got "
                            + (check.isPositive() ? "admitted"
                                    : String.valueOf(check.getWarning())));
        }
        stand.discard();
        zombie.discard();
        Cow cow = cow(helper, new Vec3(2.5D, 2.0D, 4.25D));
        holder.aim(cow);
        ConditionCheck living = ability.checkConditions(holder.power);
        helper.assertTrue(living.isPositive(),
                "Premise: Hamon Organism Infusion at a cow was refused: "
                        + living.getWarning());
        cow.discard();
        net.minecraft.core.BlockPos stone = holder.aimBlock(helper,
                new net.minecraft.core.BlockPos(2, 2, 4),
                net.minecraft.world.level.block.Blocks.STONE);
        ConditionCheck block = ability.checkConditions(holder.power);
        helper.assertTrue(!block.isPositive() && isWarning(block, "living_plant"),
                "1.16 answered Hamon Organism Infusion at stone with"
                        + " \"living_plant\"; got " + (block.isPositive()
                                ? "admitted" : String.valueOf(block.getWarning())));
        holder.aimBlock(helper, new net.minecraft.core.BlockPos(2, 2, 4),
                net.minecraft.world.level.block.Blocks.OAK_LEAVES);
        ConditionCheck leaves = ability.checkConditions(holder.power);
        helper.assertTrue(leaves.isPositive(),
                "Premise: Hamon Organism Infusion at leaves was refused: "
                        + leaves.getWarning());
        helper.getLevel().setBlockAndUpdate(stone,
                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        holder.close((short) 32_144);
        helper.succeed();
    }

    // 1.16 UltimateLifeformHamonShock.checkSpecificConditions (:102-105)
    // refused a press with no energy with "some_energy", after the hands
    // (Action.checkConditions); the shock itself costs no energy.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void hamonShockNeedsSomeEnergy(GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateShockEnergy",
                "60cd8142-927b-4e40-8bc5-b3f4e8eaf9a6");
        setHamon(holder.data, 800);
        Ability ability = holder.power.getAbility(
                UltimateLifeformSpec.HAMON_SHOCK.id);
        helper.assertTrue(ability != null,
                "Premise: no Hamon Shock in the moveset");
        holder.data.setCommandEnergy(holder.player, 0.0F);
        ConditionCheck empty = ability.checkConditions(holder.power);
        helper.assertTrue(!empty.isPositive() && isWarning(empty, "some_energy"),
                "1.16 refused Hamon Shock with no energy with \"some_energy\";"
                        + " got " + (empty.isPositive() ? "admitted"
                                : String.valueOf(empty.getWarning())));
        holder.player.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
        ConditionCheck hands = ability.checkConditions(holder.power);
        helper.assertTrue(!hands.isPositive() && isWarning(hands, "hands"),
                "1.16 asked for the hands before the energy of Hamon Shock;"
                        + " got " + (hands.isPositive() ? "admitted"
                                : String.valueOf(hands.getWarning())));
        holder.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        holder.data.setCommandEnergy(holder.player, 1.0F);
        ConditionCheck some = ability.checkConditions(holder.power);
        helper.assertTrue(some.isPositive(),
                "1.16 admitted Hamon Shock with some energy; got "
                        + some.getWarning());
        holder.close((short) 32_145);
        helper.succeed();
    }

    // 1.16 UltimateLifeformEnderTeleport.checkSpecificConditions (:52-54)
    // refused in water with "not_underwater".
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void endermanTeleportInWaterSaysNotUnderwater(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateTeleportWater",
                "71de9253-a38c-4f51-9cc6-c405f9fb0ab7");
        holder.data.setCommandEnergy(holder.player,
                UltimateLifeformData.MAX_ENERGY);
        Ability ability = holder.power.getAbility(
                UltimateLifeformSpec.ENDERMAN_TELEPORT.id);
        helper.assertTrue(ability != null,
                "Premise: no Enderman Teleport in the moveset");
        ConditionCheck dry = ability.checkConditions(holder.power);
        helper.assertTrue(dry.isPositive(),
                "Premise: Enderman Teleport out of water was refused: "
                        + dry.getWarning());
        net.minecraft.core.BlockPos pool = pool(helper,
                new net.minecraft.core.BlockPos(2, 2, 2),
                net.minecraft.world.level.block.Blocks.WATER);
        holder.player.setPos(Vec3.atBottomCenterOf(pool));
        // a fake player does not tick; baseTick updates the water state
        holder.player.baseTick();
        helper.assertTrue(holder.player.isInWaterOrBubble(),
                "Premise: the test player is not in the water");
        ConditionCheck wet = ability.checkConditions(holder.power);
        helper.assertTrue(!wet.isPositive() && isWarning(wet, "not_underwater"),
                "1.16 refused Enderman Teleport in water with"
                        + " \"not_underwater\"; got " + (wet.isPositive()
                                ? "admitted" : String.valueOf(wet.getWarning())));
        holder.close((short) 32_146);
        helper.succeed();
    }

    // 1.16 UltimateLifeformSendoOverdrive took the vanilla crosshair target
    // (InputHandler.pickMouseTarget: a block within 4.5 blocks in survival):
    // overrideVanillaMouseTarget (:75-82) traced a block under the crosshair
    // again for 10 blocks with the COLLIDER shape, nothing under the
    // crosshair was no target and TargetRequirement BLOCK refused it (the
    // port's "block_target" is kept), checkRangeAndTarget said
    // "target_too_far" beyond getMaxRangeSqBlockTarget (100), and perform
    // spawned at that target.
    @GameTest(template = "empty", timeoutTicks = 30, batch = "ulf_own_sendo_overdrive_takes_a_block_within_ten_blo")
    public static void sendoOverdriveTakesABlockWithinTenBlocksAndSpawnsThere(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateSendoRange",
                "82ef03a4-b49d-4062-ad7d-d516a0fc1bc8");
        holder.data.setCommandEnergy(holder.player,
                UltimateLifeformData.MAX_ENERGY);
        setHamon(holder.data, 3_000);
        UltimateLifeformSpec spec =
                UltimateLifeformSpec.ULTIMATE_HAMON_SENDO_OVERDRIVE;
        Ability ability = holder.power.getAbility(spec.id);
        helper.assertTrue(ability != null,
                "Premise: no Ultimate Sendo Overdrive in the moveset");
        net.minecraft.world.level.Level level = helper.getLevel();
        net.minecraft.world.level.block.state.BlockState stoneState =
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        net.minecraft.world.level.block.state.BlockState air =
                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        ActionTarget[] none = {ActionTarget.EMPTY};
        java.util.function.BiConsumer<Vec3, net.minecraft.core.BlockPos> lookFrom =
                (feet, block) -> {
                    holder.player.setPos(feet);
                    holder.player.lookAt(EntityAnchorArgument.Anchor.EYES,
                            Vec3.atCenterOf(block));
                    LivingComponentAction.getComponent(holder.player)
                            .entityAim.setTarget(none[0]);
                };
        // the user's waves are counted as they join the level, so another
        // test's clean-up cannot take them away before the check
        List<net.minecraft.core.BlockPos> sendoSpawns = new ArrayList<>();
        java.util.function.Consumer<
                net.neoforged.neoforge.event.entity.EntityJoinLevelEvent>
                sendoJoins = event -> {
                    if (event.getEntity() instanceof rotp.addons
                                    .ultimate_lifeform.entity
                                    .UltimateAreaEffectEntity area
                            && area.isSendo()
                            && area.getOwner() == holder.player) {
                        sendoSpawns.add(area.getTargetedBlockPos());
                    }
                };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                net.neoforged.bus.api.EventPriority.LOWEST, false,
                net.neoforged.neoforge.event.entity.EntityJoinLevelEvent.class,
                sendoJoins);
        // a wall 6.5 blocks away, past vanilla reach, nothing under the
        // crosshair: refused with the kept message, nothing paid or spawned
        Vec3 front = Vec3.atBottomCenterOf(helper.absolutePos(
                new net.minecraft.core.BlockPos(4, 1, 0)));
        net.minecraft.core.BlockPos near = helper.absolutePos(
                new net.minecraft.core.BlockPos(4, 2, 7));
        level.setBlockAndUpdate(near, stoneState);
        lookFrom.accept(front, near);
        ConditionCheck emptyPress = ability.checkConditions(holder.power);
        helper.assertTrue(!emptyPress.isPositive()
                        && isWarning(emptyPress, "block_target"),
                "1.16 refused the Ultimate Sendo Overdrive with nothing under"
                        + " the crosshair, a wall 6.5 blocks away (the port"
                        + " says \"block_target\"); got "
                        + (emptyPress.isPositive() ? "admitted"
                                : String.valueOf(emptyPress.getWarning())));
        boolean clicked = holder.tryClick(spec, (short) 32_156);
        UltimateLifeformCombat.perform(holder.player, holder.data, spec, 0);
        helper.assertTrue(!clicked && holder.saw("block_target")
                        && holder.data.getHamonCharge() == 3_000
                        && !holder.data.isAbilityOnCooldown(spec.id)
                        && sendoSpawns.isEmpty(),
                "1.16 neither paid nor spawned an Ultimate Sendo Overdrive"
                        + " with nothing under the crosshair: click accepted="
                        + clicked + " hamon=" + holder.data.getHamonCharge()
                        + " cooldown="
                        + holder.data.isAbilityOnCooldown(spec.id)
                        + " waves spawned at=" + sendoSpawns
                        + " messages=" + holder.messages);
        level.setBlockAndUpdate(near, air);
        // a block target beyond 10 blocks is too far
        net.minecraft.core.BlockPos beyond = helper.absolutePos(
                new net.minecraft.core.BlockPos(7, 7, 7));
        level.setBlockAndUpdate(beyond, stoneState);
        Vec3 corner = Vec3.atBottomCenterOf(helper.absolutePos(
                new net.minecraft.core.BlockPos(0, 1, 0)));
        lookFrom.accept(corner, beyond);
        ConditionCheck tooFar = UltimateLifeformCombat.checkSendoTarget(
                holder.player,
                new ActionTarget(beyond, net.minecraft.core.Direction.NORTH));
        helper.assertTrue(!tooFar.isPositive()
                        && isWarning(tooFar, "target_too_far"),
                "1.16 checkRangeAndTarget said \"target_too_far\" for a block"
                        + " beyond 10 blocks; got " + (tooFar.isPositive()
                                ? "admitted" : String.valueOf(tooFar.getWarning())));
        level.setBlockAndUpdate(beyond, air);
        // grass under the crosshair within reach: the COLLIDER trace goes on
        // to the stone 6.5 blocks away behind it
        net.minecraft.core.BlockPos grass = helper.absolutePos(
                new net.minecraft.core.BlockPos(4, 2, 2));
        level.setBlockAndUpdate(grass.below(),
                net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(grass,
                net.minecraft.world.level.block.Blocks.SHORT_GRASS
                        .defaultBlockState());
        level.setBlockAndUpdate(near, stoneState);
        none[0] = new ActionTarget(grass, net.minecraft.core.Direction.NORTH);
        lookFrom.accept(front, near);
        helper.assertTrue(holder.tryClick(spec, (short) 32_147),
                "1.16 admitted the Ultimate Sendo Overdrive with grass under"
                        + " the crosshair and stone behind it; refused: "
                        + holder.messages);
        helper.runAfterDelay(3, () -> {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(
                    sendoJoins);
            helper.assertTrue(sendoSpawns.equals(List.of(near))
                            && holder.data.getHamonCharge() == 2_250,
                    "1.16 traced through the grass under the crosshair to the"
                            + " stone 6.5 blocks away behind it, spawned the"
                            + " waves there and paid 750 Hamon: waves spawned"
                            + " at=" + sendoSpawns + " (stone " + near
                            + ", grass " + grass + ") hamon="
                            + holder.data.getHamonCharge());
            helper.getLevel().getEntitiesOfClass(
                            rotp.addons.ultimate_lifeform.entity
                                    .UltimateAreaEffectEntity.class,
                            new net.minecraft.world.phys.AABB(near)
                                    .inflate(12.0D),
                            area -> area.getOwner() == holder.player)
                    .forEach(Entity::discard);
            holder.close((short) 32_147);
            helper.succeed();
        });
    }

    // 1.16 performed at the target its server checks had just passed, so a
    // Sendo Overdrive was never paid for without a target to spawn at.
    @GameTest(template = "empty", timeoutTicks = 30, batch = "ulf_own_sendo_overdrive_pays_nothing_when_its_target")
    public static void sendoOverdrivePaysNothingWhenItsTargetIsGoneAtThePerform(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateSendoGone",
                "93f014b5-c5ae-4173-be8e-e627b1ad2cd9");
        holder.data.setCommandEnergy(holder.player,
                UltimateLifeformData.MAX_ENERGY);
        setHamon(holder.data, 3_000);
        UltimateLifeformSpec spec =
                UltimateLifeformSpec.ULTIMATE_HAMON_SENDO_OVERDRIVE;
        // straight overhead in the open air, so once it is gone the trace
        // finds only the sky
        holder.player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(
                new net.minecraft.core.BlockPos(2, 42, 2))));
        net.minecraft.core.BlockPos block = holder.aimBlock(helper,
                new net.minecraft.core.BlockPos(2, 45, 2),
                net.minecraft.world.level.block.Blocks.STONE);
        holder.click(helper, spec, (short) 32_148);
        boolean performedAtThePress = holder.data.isAbilityOnCooldown(spec.id);
        helper.getLevel().setBlockAndUpdate(block,
                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        LivingComponentAction.getComponent(holder.player).entityAim
                .setTarget(ActionTarget.EMPTY);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(performedAtThePress
                            ? sendoWavesAt(helper, block) == 1
                                    && holder.data.getHamonCharge() == 2_250
                            : sendoWavesAt(helper, block) == 0
                                    && holder.data.getHamonCharge() == 3_000
                                    && !holder.data.isAbilityOnCooldown(spec.id),
                    "1.16 paid for an Ultimate Sendo Overdrive only with waves"
                            + " to spawn: performed at the press="
                            + performedAtThePress + " waves="
                            + sendoWavesAt(helper, block) + " hamon="
                            + holder.data.getHamonCharge() + " cooldown="
                            + holder.data.isAbilityOnCooldown(spec.id));
            // only this test's own waves: a neighbouring test's stay its own
            helper.getLevel().getEntitiesOfClass(
                            rotp.addons.ultimate_lifeform.entity
                                    .UltimateAreaEffectEntity.class,
                            new net.minecraft.world.phys.AABB(block)
                                    .inflate(12.0D),
                            area -> area.getOwner() == holder.player)
                    .forEach(Entity::discard);
            holder.close((short) 32_148);
            helper.succeed();
        });
    }

    private static int sendoWavesAt(GameTestHelper helper,
            net.minecraft.core.BlockPos block) {
        return helper.getLevel().getEntitiesOfClass(
                        rotp.addons.ultimate_lifeform.entity
                                .UltimateAreaEffectEntity.class,
                        new net.minecraft.world.phys.AABB(block).inflate(12.0D),
                        area -> area.isSendo()
                                && block.equals(area.getTargetedBlockPos()))
                .size();
    }

    // a fluid source in a stone basin, so it cannot flow away
    private static net.minecraft.core.BlockPos pool(GameTestHelper helper,
            net.minecraft.core.BlockPos relative,
            net.minecraft.world.level.block.Block fluid) {
        net.minecraft.core.BlockPos pos = helper.absolutePos(relative);
        net.minecraft.world.level.block.state.BlockState stone =
                net.minecraft.world.level.block.Blocks.STONE
                        .defaultBlockState();
        helper.getLevel().setBlockAndUpdate(pos.below(), stone);
        for (net.minecraft.core.Direction side
                : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            helper.getLevel().setBlockAndUpdate(pos.relative(side), stone);
        }
        helper.getLevel().setBlockAndUpdate(pos, fluid.defaultBlockState());
        return pos;
    }

    private static void setHamon(UltimateLifeformData data, int charge) {
        data.consumeHamonCharge(data.getHamonCharge());
        data.addHamonCharge(charge);
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

    private static boolean isWarning(ConditionCheck check, String postfix) {
        return check.getWarning() != null
                && check.getWarning().getContents()
                        instanceof TranslatableContents contents
                && ("jojo.message.action_condition." + postfix)
                        .equals(contents.getKey());
    }

    private static Holder holder(
            GameTestHelper helper, String name, String uuid) {
        return holder(helper, name, uuid, GameType.SURVIVAL);
    }

    private static Holder holder(
            GameTestHelper helper, String name, String uuid,
            GameType gameType) {
        return holder(helper, name, uuid, gameType, null);
    }

    // with a flag, the player is its own client's copy (LocalPlayer
    // .isLocalPlayer) while the flag is set
    private static Holder holder(
            GameTestHelper helper, String name, String uuid,
            GameType gameType, @javax.annotation.Nullable boolean[] local) {
        List<Component> messages = new ArrayList<>();
        List<String> hurts = new ArrayList<>();
        DamageSource[] lastDamage = new DamageSource[1];
        ServerPlayer player = new FakePlayer(
                helper.getLevel(),
                new GameProfile(UUID.fromString(uuid), name)) {
            @Override
            public boolean isLocalPlayer() {
                return local != null && local[0];
            }

            @Override
            public void displayClientMessage(
                    Component message, boolean overlay) {
                if (overlay) {
                    messages.add(message);
                }
            }

            // a fake player takes no damage, so a test sets the last hit
            @Override
            public DamageSource getLastDamageSource() {
                return lastDamage[0] != null
                        ? lastDamage[0]
                        : super.getLastDamageSource();
            }

            @Override
            public boolean hurt(
                    net.minecraft.world.damagesource.DamageSource source,
                    float amount) {
                hurts.add(source.getMsgId() + " " + amount);
                return super.hurt(source, amount);
            }
        };
        player.setPos(Vec3.atBottomCenterOf(
                helper.absolutePos(new net.minecraft.core.BlockPos(2, 2, 2))));
        player.setGameMode(gameType);
        if (!helper.getLevel().addFreshEntity(player)) {
            throw new IllegalStateException(
                    "Release test player could not be added: " + name);
        }
        PlayerPower power = PowerClass.PLAYER_POWER.attachGet(player);
        if (!power.trySetPowerType(
                UltimateLifeformAddon.ULTIMATE_LIFEFORM.get())) {
            throw new IllegalStateException(
                    "Release test power grant failed: " + name);
        }
        power.updateAvailableMoves();
        UltimateLifeformData data = power.getCurTypeData(
                UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElseThrow();
        return new Holder(player, power, data, messages, hurts, lastDamage);
    }

    private static DamageSource damage(GameTestHelper helper,
            net.minecraft.resources.ResourceKey<
                    net.minecraft.world.damagesource.DamageType> type) {
        return new DamageSource(helper.getLevel().registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries
                        .DAMAGE_TYPE)
                .getHolderOrThrow(type));
    }

    // whether the add-on's fire armor hook at the head of Player.hurt cancels
    // the hit (a fake player never gets that far, see
    // fireArmorCountsHitsAtTheHeadOfPlayerHurt)
    private static boolean cancels(ServerPlayer player, DamageSource source) {
        return UltimateLifeformGameEvents.cancelFireArmorHit(player, source);
    }

    private record Holder(
            ServerPlayer player,
            PlayerPower power,
            UltimateLifeformData data,
            List<Component> messages,
            List<String> hurts,
            DamageSource[] lastDamage) {
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

        void click(GameTestHelper helper,
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
                        InputMethod.CLICK,
                        0.0F,
                        buffering,
                        ability.getAbilityId());
            }
            finally {
                extra.release();
            }
            helper.assertTrue(held != null || buffering.isActionSuccess,
                    "Premise: the " + spec.id + " click was refused: "
                            + messages);
            AbilityInput.keyRelease(key, player);
        }

        // a click as the server's ClAbilityInputPacket takes it: the
        // condition check (its refusal message) first, then the key press;
        // whether it went through. The check is made afresh, as on the tick
        // a click packet arrives (Power.updateAvailableMoves keeps one a tick).
        boolean tryClick(UltimateLifeformSpec spec, short key) {
            Ability ability = power.getAbility(spec.id);
            if (ability == null) {
                throw new IllegalStateException("No " + spec.id);
            }
            rotp.core.powersystem.ability.condition.AvailableAbilities available =
                    new rotp.core.powersystem.ability.condition
                            .AvailableAbilities();
            available.update(power, power.getMoveset());
            if (!AbilityInput.withConditionCheck(
                    available.getContextVariationContainer(ability), player,
                    InputMethod.CLICK)) {
                return false;
            }
            BufferingState buffering = BufferingState.clickCanBuffer();
            FriendlyByteBuf extra =
                    new FriendlyByteBuf(Unpooled.buffer());
            try {
                Object held = AbilityInput.keyPress(
                        key,
                        ability,
                        player,
                        extra,
                        InputMethod.CLICK,
                        0.0F,
                        buffering,
                        ability.getAbilityId());
                return held != null || buffering.isActionSuccess;
            }
            finally {
                extra.release();
                AbilityInput.keyRelease(key, player);
            }
        }

        void aim(Entity target) {
            LivingComponentAction.getComponent(player)
                    .entityAim.setTarget(new ActionTarget(target));
            player.lookAt(EntityAnchorArgument.Anchor.EYES,
                    target.getBoundingBox().getCenter());
        }

        // a block at the relative position, aimed at from its north face
        net.minecraft.core.BlockPos aimBlock(GameTestHelper helper,
                net.minecraft.core.BlockPos relative,
                net.minecraft.world.level.block.Block block) {
            net.minecraft.core.BlockPos pos = helper.absolutePos(relative);
            helper.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
            player.lookAt(EntityAnchorArgument.Anchor.EYES,
                    Vec3.atCenterOf(pos));
            LivingComponentAction.getComponent(player).entityAim.setTarget(
                    new ActionTarget(pos, net.minecraft.core.Direction.NORTH));
            return pos;
        }

        boolean isRunning(UltimateLifeformSpec spec) {
            return phase(spec) != null;
        }

        ActionPhase phase(UltimateLifeformSpec spec) {
            EntityActionInstance current =
                    LivingComponentAction.getCurEntityAction(player);
            return current != null
                            && !current.isOver()
                            && current.ability == power.getAbility(spec.id)
                    ? current.getPhase()
                    : null;
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
