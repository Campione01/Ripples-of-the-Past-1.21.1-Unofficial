package rotp.addons.d4c;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.Unpooled;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.d4c.entity.D4CSummonKickEntity;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.subsystems.target.ActionTarget;

/**
 * 1.16 bound the Saint Corpse X-ray and the Corpse Finder to keys of their own (ad/ab.java :50-54, U and R) whose
 * packets toggled the X-ray and asked for guidance without an Action: PowerBaseImpl.onClickAction never ran for them,
 * so a held barrage went on and did not refuse them. summon_kick (aa/aa/ae.java) was a StandAction: once it went off,
 * onClickAction's stopHeldAction(false) ended the held barrage.
 */
@GameTestHolder(D4CAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class D4CHeldPressGameTests {
    private D4CHeldPressGameTests() {}

    @GameTest(template = "empty", batch = "d4cCorpseKeysDuringABarrage", timeoutTicks = 80)
    public static void corpseKeysLeaveAHeldBarrageAloneAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        try (D4CGameTestScene scene = new D4CGameTestScene(helper, "d4c-corpse-keys")) {
            Cow cow = scene.cow(new Vec3(0.0D, 0.0D, 6.0D));
            lookAt(scene, cow);
            scene.owner.getInventory().setItem(0, new ItemStack(D4CAddon.COMPLETE_SAINT_CORPSE.get()));
            EntityActionInstance barrage = scene.pressAndHold("d4c_barrage", (short) 231);
            helper.assertTrue(barrage != null, "fixture: " + scene.fixtureFailure);
            short key = 232;
            for (String name : List.of(D4CAddon.SAINT_CORPSE_XRAY_NAME, D4CAddon.CORPSE_FINDER_NAME)) {
                if (!press(scene, name, key++)) {
                    wrong.add(name + " was refused during the held barrage");
                }
                scene.tick();
                if (!scene.held(barrage)) {
                    wrong.add(name + " ended the held barrage (phase " + barrage.getPhase() + ")");
                }
            }
            if (!xrayEnabled(scene)) {
                wrong.add("the X-ray key did not toggle the X-ray on");
            }
            // the cow under the crosshair, as the user's client sends its pick
            LivingComponentAction.getComponent(scene.owner).entityAim.setTarget(new ActionTarget(cow));
            if (!press(scene, "summon_kick", key)) {
                wrong.add("fixture: summon_kick was refused during the held barrage");
            }
            scene.tick();
            if (scene.held(barrage)) {
                wrong.add("summon_kick, a 1.16 StandAction, did not end the held barrage");
            }
            scene.around(D4CSummonKickEntity.class).forEach(Entity::discard);
        }
        helper.assertTrue(wrong.isEmpty(),
                "1.16's X-ray and Corpse Finder keys were no Actions and left a held barrage alone: " + wrong);
        helper.succeed();
    }

    // A real click through the press check the client's packet runs; whether it went off
    private static boolean press(D4CGameTestScene scene, String name, short keyId) {
        scene.power.tick();
        Ability ability = scene.power.getAbility(name);
        if (ability == null || !AbilityInput.withConditionCheck(ability, scene.owner)) {
            return false;
        }
        BufferingState press = BufferingState.clickOnly();
        AbilityInput.keyPress(keyId, ability, scene.owner, new FriendlyByteBuf(Unpooled.buffer()), InputMethod.CLICK,
                0, press, ability.getAbilityId());
        AbilityInput.keyRelease(keyId, scene.owner);
        return press.isActionSuccess;
    }

    private static boolean xrayEnabled(D4CGameTestScene scene) {
        return scene.owner.getInventory().getItem(0)
                .getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag()
                .getBoolean("xray_enabled");
    }

    private static void lookAt(D4CGameTestScene scene, Entity target) {
        Vec3 aim = target.position().add(0.0D, 0.7D, 0.0D).subtract(scene.owner.getEyePosition());
        scene.owner.setYRot((float) Math.toDegrees(Math.atan2(-aim.x, aim.z)));
        scene.owner.setXRot((float) -Math.toDegrees(Math.atan2(aim.y, Math.sqrt(aim.x * aim.x + aim.z * aim.z))));
        scene.owner.setYHeadRot(scene.owner.getYRot());
    }
}
