package rotp.addons.kingcrimson.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.core.init.ModSpecialActions;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * 1.16 KingCrimsonProjectileThrow(Tripple) are StandEntityLightAttacks: the builder default
 * standAutoSummonMode(MAIN_ARM) brings out one arm, which goes away when the task ends, and
 * noFinisherBarDecay() holds the finisher meter for the whole throw.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonThrowArmGameTests {
    private KingCrimsonThrowArmGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 2000, batch = "kc_throw_arm")
    public static void throwSummonsOneArmAndHoldsTheFinisherMeter(GameTestHelper helper) {
        KingCrimsonThrowGameTestFixture.identity(helper, KingCrimsonThrowArmGameTests.class);
        var suite = new ArmSuite(helper);
        try { suite.start(); } catch (RuntimeException | Error error) { suite.close(); throw error; }
    }

    private static final class ArmSuite extends KingCrimsonThrowGameTestFixture.Suite {
        enum Kind { UNSUMMONED, ARMS_ONLY, FULL, FINISHER }
        Kind kind;
        boolean triple, meterHeld;
        int meterSamples;
        float meterSeen;

        ArmSuite(GameTestHelper helper) { super(helper, "KC-THROW-ARM", 8); }

        @Override void prepare(int index) {
            kind = Kind.values()[index % 4]; triple = index >= 4;
            meterHeld = true; meterSamples = 0; meterSeen = -1;
            scene = new KingCrimsonThrowGameTestFixture(helper, "arm/" + (triple ? "triple/" : "single/") + kind,
                    KingCrimsonThrowGameTestFixture.Mode.FINITE, 600, 73);
            scene.hand(new ItemStack(Items.ARROW, 3));
            String name = triple ? KingCrimsonThrowGameTestFixture.TRIPLE : KingCrimsonThrowGameTestFixture.SINGLE;
            switch (kind) {
                case UNSUMMONED -> {
                    KingCrimsonAddon.KING_CRIMSON.get().forceUnsummon(scene.user, scene.power);
                    scene.premise(!scene.power.isSummoned(), "King Crimson put away before the throw");
                    Ability ability = scene.power.getAbility(name);
                    var selected = scene.power.updateAvailableMoves().getContextVariationContainer(ability);
                    scene.premise(selected != null && selected.ability == ability
                            && AbilityInput.withConditionCheck(selected, scene.user, InputMethod.CLICK), "Registered throw admission");
                    BufferingState buffer = BufferingState.clickOnly();
                    AbilityInput.keyPress(KingCrimsonThrowGameTestFixture.FIRST, ability, scene.user, null, InputMethod.CLICK, 0,
                            buffer, ability.getAbilityId());
                    StandEntity body = scene.power.getSummonedStandEntity();
                    scene.premise(buffer.isActionSuccess && body != null && body.getCurStandAction() != null
                            && body.getCurStandAction().ability == ability, "The throw summoned King Crimson and started");
                    checks.check(body.isArmsOnlyMode() && body.showArm(InteractionHand.MAIN_HAND),
                            scene.id + " the throw summons only the arm: armsOnly=" + body.isArmsOnlyMode());
                    scene.owned.add(body);
                    scene.stand = body; scene.ability = ability; scene.action = body.getCurStandAction();
                    scene.inputUserAge = scene.user.tickCount; scene.inputStandAge = body.tickCount;
                }
                case ARMS_ONLY -> {
                    scene.stand.setArmsOnlyMode(true, false);
                    scene.press(name, KingCrimsonThrowGameTestFixture.FIRST);
                    checks.check(scene.action != null && scene.stand.isArmsOnlyMode(),
                            scene.id + " an arms-only King Crimson stays arms-only: armsOnly=" + scene.stand.isArmsOnlyMode());
                }
                case FULL -> {
                    scene.press(name, KingCrimsonThrowGameTestFixture.FIRST);
                    scene.premise(scene.action != null && !scene.stand.isArmsOnlyMode(), "Full body throw admitted");
                }
                case FINISHER -> {
                    scene.stand.setFinisherMeter(0.75F);
                    scene.press(name, KingCrimsonThrowGameTestFixture.FIRST);
                    scene.premise(scene.action != null, "Throw admitted with a 0.75 meter");
                    scene.listen(EntityTickEvent.Post.class, event -> {
                        if (event.getEntity() != scene.stand || scene.action == null || scene.action.isOver()) return;
                        meterSamples++; meterSeen = scene.stand.getFinisherMeter();
                        meterHeld &= KingCrimsonThrowGameTestFixture.near(meterSeen, 0.75F);
                    });
                }
            }
            scene.premise(scene.action != null, "Admitted throw action");
        }

        @Override boolean verify() {
            if (!scene.performed) return false;
            EntityActionInstance current = scene.stand.getCurStandAction();
            if (current == scene.action && !scene.stand.isRemoved()) return false;
            int count = triple ? 3 : 1;
            checks.check(scene.spawns.size() == count, scene.id + " one batch thrown: " + scene.spawns.size());
            boolean unsummoning = scene.stand.isRemoved() || !scene.power.isSummoned()
                    || current != null && current.ability == ModSpecialActions.STAND_UNSUMMON.get();
            switch (kind) {
                case UNSUMMONED, ARMS_ONLY -> checks.check(unsummoning,
                        scene.id + " the arm goes away when the throw ends: current=" + (current != null ? current.ability : null));
                case FULL -> checks.check(!unsummoning && !scene.stand.isArmsOnlyMode(),
                        scene.id + " a full body stays out after the throw");
                case FINISHER -> checks.check(meterHeld && meterSamples > 5,
                        scene.id + " the finisher meter holds during the throw: samples=" + meterSamples + " last=" + meterSeen);
            }
            return true;
        }

        @Override void release() {}
    }
}
