package rotp.addons.diverdown.ability;

import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.diverdown.DiverDownAddon;
import rotp.addons.diverdown.entity.DiverDownStandEntity;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.LivingComponentAction;

/**
 * 1.16 gave Spring Legs (049 DiverDownSpringLegs :53), Bone Shard Bomb (DiverDownMakeBoneShardBomb :48) and the shards'
 * Slowness (GameplayEvents.onEntityHurt :83) as EffectInstance(effect, duration, amplifier, false, false, true): no
 * particles around the target, only the icon on its HUD.
 */
@GameTestHolder(DiverDownAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DiverDownEffectIconGameTests {
    private static final String TEMPLATES = "rotp_dd_review_fixes";
    private static final int OWN_TICKS = 120;

    private DiverDownEffectIconGameTests() {}

    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_effect_icons",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void springLegsBoneShardBombAndTheShardsSlownessShowOnlyTheirIconAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 3, placement -> {
            DiverDownWallsGameTests.Scene scene = new DiverDownWallsGameTests.Scene(helper, placement);
            try {
                DiverDownStandEntity stand = scene.stand;
                int springLegsColor = DiverDownAddon.SPRING_LEGS_EFFECT.get().getColor();
                int boneShardBombColor = DiverDownAddon.BONE_SHARD_BOMB_EFFECT.get().getColor();
                helper.assertTrue(springLegsColor == 0x404040 && boneShardBombColor == 0x404040,
                        "1.16 InitEffects (:25-26) registered Spring Legs and Bone Shard Bomb with the colour 0x404040; "
                                + "the port's are " + Integer.toHexString(springLegsColor) + " and "
                                + Integer.toHexString(boneShardBombColor));
                Cow cow = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(cow != null, "Could not create a cow");
                scene.prepareMob(cow, scene.origin.add(0.0D, 0.0D, 2.0D));
                helper.assertTrue(stand.setEmbeddedTarget(cow), "Fixture: Diver Down did not dive into the cow");
                press(scene, DiverDownAddon.SPRING_LEGS_NAME);
                int[] stage = {0};
                scene.poll(0, OWN_TICKS, () -> {
                    if (stage[0] == 0) {
                        expectIconOnly(helper, cow, DiverDownAddon.SPRING_LEGS_EFFECT, "Spring Legs");
                        stage[0] = 1;
                    }
                    if (stage[0] == 1) {
                        // Bone Shard Bomb is pressed once Spring Legs is over, from inside the cow again
                        if (LivingComponentAction.getCurEntityAction(stand) != null) {
                            throw new GameTestAssertException("Spring Legs has not ended yet");
                        }
                        helper.assertTrue(stand.setEmbeddedTarget(cow),
                                "Fixture: Diver Down did not dive into the cow again");
                        press(scene, DiverDownAddon.BONE_SHARD_BOMB_NAME);
                        stage[0] = 2;
                    }
                    if (stage[0] == 2) {
                        expectIconOnly(helper, cow, DiverDownAddon.BONE_SHARD_BOMB_EFFECT, "Bone Shard Bomb");
                        cow.invulnerableTime = 0;
                        helper.assertTrue(cow.hurt(helper.getLevel().damageSources().mobAttack(scene.owner), 1.0F),
                                "Fixture: the hit on the bombed cow was refused");
                        helper.assertTrue(!cow.hasEffect(DiverDownAddon.BONE_SHARD_BOMB_EFFECT),
                                "Fixture: the hit did not set the bone shards off");
                        stage[0] = 3;
                    }
                    expectIconOnly(helper, cow, MobEffects.MOVEMENT_SLOWDOWN, "the bone shards' Slowness");
                });
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    private static void press(DiverDownWallsGameTests.Scene scene, String name) {
        Ability ability = scene.power.getAbility(name);
        scene.power.setStamina(scene.power.getMaxStamina());
        BufferingState buffering = BufferingState.clickOnly();
        ability.onKeyPress(scene.helper.getLevel(), scene.owner, null, InputMethod.CLICK, 0.0F, buffering);
        scene.helper.assertTrue(buffering.isActionSuccess, "Fixture: " + name + " did not start");
    }

    private static void expectIconOnly(GameTestHelper helper, Cow cow, Holder<MobEffect> effect, String label) {
        MobEffectInstance instance = cow.getEffect(effect);
        if (instance == null) {
            throw new GameTestAssertException(label + " is not on the cow yet");
        }
        helper.assertTrue(!instance.isVisible() && instance.showIcon() && !instance.isAmbient(),
                "1.16 gave " + label + " with no particles and only its icon (EffectInstance(..., false, false, "
                        + "true)); the port's instance has visible " + instance.isVisible() + ", icon "
                        + instance.showIcon() + ", ambient " + instance.isAmbient());
    }
}
