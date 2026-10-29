package rotp.addons.cmoon.gametest;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mojang.datafixers.util.Pair;

import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.cmoon.CMoonAddon;
import rotp.addons.cmoon.ability.CMoonBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility.StandEntityBarrage;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.ControlSchemeTemplate;
import rotp.core.powersystem.ability.controls.InputBindTemplate;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;

@GameTestHolder(CMoonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CMoonMovesetGameTests {
    private CMoonMovesetGameTests() {}

    /**
     * D-core-25: a 1.16 Shift variation is its own special-wheel slot, and D-core-07: Shift + right mouse is the
     * charged heavy punch. The counter (1.16 Shift variation of the block slot, fired on click) gets a click slot
     * where the block slot was, and no key of its own.
     */
    @GameTest(template = "empty", batch = "cmMoveset", timeoutTicks = 20)
    public static void gravityCounterIsItsOwnClickWheelSlot(GameTestHelper helper) {
        MovesetBuilder moveset = CMoonAddon.C_MOON.get().getDefaultMoveset();
        List<String> failures = new ArrayList<>();
        for (var scheme : moveset.controlSchemes.entrySet()) {
            for (ControlSchemeTemplate.GroupTemplate group : scheme.getValue().groups.values()) {
                for (Map.Entry<String, Pair<InputMethod, InputBindTemplate>> bind : group.separateBinds.entrySet()) {
                    checkBind(failures, scheme.getKey(), bind.getKey(), bind.getValue().getFirst(),
                            bind.getValue().getSecond());
                }
                for (ControlSchemeTemplate.SeparateBindTemplate bind : group.additionalSeparateBinds) {
                    checkBind(failures, scheme.getKey(), bind.ability(), bind.inputMethod(), bind.input());
                }
            }
        }
        ControlSchemeTemplate hotbar = moveset.controlSchemes.get("hotbar");
        List<Map<InputMethod, String>> wheel = new ArrayList<>();
        if (hotbar != null) {
            hotbar.groups.values().forEach(group -> group.hotbars.forEach(slots -> slots.slots.forEach(
                    slot -> wheel.add(slot.get(null)))));
        }
        List<Map<InputMethod, String>> expected = List.of(
                Map.of(InputMethod.CLICK, CMoonAddon.GRAVITY_COUNTER),
                Map.of(InputMethod.HOLD, CMoonAddon.CENTER_OF_GRAVITY),
                Map.of(InputMethod.HOLD, CMoonAddon.DESTROY),
                Map.of(InputMethod.HOLD, CMoonAddon.GRAVITY_SHIFT));
        if (!wheel.equals(expected)) {
            failures.add("hotbar wheel " + wheel + " instead of " + expected);
        }
        helper.assertTrue(failures.isEmpty(), "CM-MOVESET counter input: " + failures);
        helper.succeed();
    }

    private static void checkBind(List<String> failures, String scheme, String ability, InputMethod method,
            InputBindTemplate input) {
        if (CMoonAddon.GRAVITY_COUNTER.equals(ability)) {
            failures.add(scheme + " binds the counter to its own key: " + method + " " + describe(input));
        }
        boolean shiftRmb = input == MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT
                || input instanceof InputKey key && key.device == InputKey.RMB.device
                        && key.keyCode == InputKey.RMB.keyCode && key.modifier == InputKey.Modifier.SHIFT;
        if (shiftRmb && !(CMoonAddon.HEAVY_CHARGED.equals(ability) && method == InputMethod.HOLD)) {
            failures.add(scheme + " puts " + ability + " " + method + " on the charged heavy key " + describe(input));
        }
    }

    private static String describe(InputBindTemplate input) {
        return input instanceof InputKey key ? key.name + "+" + key.modifier : String.valueOf(input);
    }

    /** 1.16 cm_barrage keeps the StandEntityMeleeBarrage.Builder hit sound, STAND_PUNCH_LIGHT. */
    @GameTest(template = "empty", batch = "cmMoveset", timeoutTicks = 20)
    public static void barrageHitSoundIsTheLightPunchImpact(GameTestHelper helper) throws ReflectiveOperationException {
        Ability barrage = CMoonAddon.C_MOON.get().getBaseMoveset().getAbility(CMoonAddon.BARRAGE);
        helper.assertTrue(barrage instanceof CMoonBarrageAbility, "Premise: registered C-Moon barrage: " + barrage);
        EntityActionInstance action = ((EntityActionType) barrage).createActionObj();
        helper.assertTrue(action instanceof StandEntityBarrage, "Premise: barrage action: " + action);
        // the action sends this holder in every barrage hit sound packet
        Method hitSound = StandEntityBarrage.class.getDeclaredMethod("getBarrageHitSound");
        hitSound.setAccessible(true);
        @SuppressWarnings("unchecked")
        Holder<SoundEvent> sound = (Holder<SoundEvent>) hitSound.invoke(action);
        helper.assertTrue(sound != null && sound.value() == ModSoundEvents.STAND_PUNCH_LIGHT.get(),
                "CM-MOVESET barrage hit sound is " + (sound != null ? sound.value().getLocation() : null)
                        + ", 1.16 played " + ModSoundEvents.STAND_PUNCH_LIGHT.getId());
        helper.succeed();
    }
}
