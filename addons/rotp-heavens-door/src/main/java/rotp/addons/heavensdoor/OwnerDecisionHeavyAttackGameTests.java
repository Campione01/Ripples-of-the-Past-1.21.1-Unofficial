package rotp.addons.heavensdoor;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.heavensdoor.ability.HeavensDoorFinisherAbility;
import rotp.addons.heavensdoor.ability.RemoveBookAbility;
import rotp.addons.heavensdoor.entity.HeavensDoorStandEntity;
import rotp.addons.heavensdoor.state.BookStateService;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchChargedAbility;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.ControlSchemeTemplate;
import rotp.core.powersystem.ability.controls.InputBindTemplate;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;

/**
 * Owner answer of 2026-10-07, "follow the original": as in 1.16 InitStands :62-70
 * Remove Book is Heaven's Door's heavy attack with the finisher as its variation,
 * and the Stand has no charged heavy.
 */
@GameTestHolder(HeavensDoorAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OwnerDecisionHeavyAttackGameTests {
    private static final String FINISHER = "finisher_punch";
    private static final Set<String> DONOR_ABILITIES = Set.of(
            "punch",
            "barrage",
            HeavensDoorAddon.REMOVE_BOOK_ID,
            FINISHER,
            "guard",
            HeavensDoorAddon.OPEN_BOOK_ID,
            HeavensDoorAddon.TEAR_PAGE_ID,
            HeavensDoorAddon.RETURN_PAGES_ID);

    private OwnerDecisionHeavyAttackGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void removeBookIsTheOnlyHeavyAndNoChargedHeavyExists(GameTestHelper helper) {
        StandType standType = HeavensDoorAddon.HEAVENS_DOOR.get();
        MovesetBuilder defaults = standType.getDefaultMoveset();
        helper.assertTrue(defaults.abilities.keySet().containsAll(DONOR_ABILITIES),
                "Heaven's Door lost a 1.16 action: " + new TreeSet<>(defaults.abilities.keySet()));
        for (String schemeName : new String[] { "hotbar", "keybinds" }) {
            ControlSchemeTemplate scheme = defaults.controlSchemes.get(schemeName);
            helper.assertTrue(scheme != null, "Heaven's Door lost its " + schemeName + " scheme");
            helper.assertTrue(HeavensDoorAddon.REMOVE_BOOK_ID.equals(clickRmbAbility(scheme)),
                    schemeName + " scheme must bind the RMB heavy input to Remove Book, got "
                            + clickRmbAbility(scheme));
            String charged = boundToChargedHeavyInput(scheme);
            helper.assertTrue(charged == null,
                    schemeName + " scheme binds " + charged + " to the charged-heavy input; 1.16 had no charged heavy");
        }

        FakePlayer owner = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "HdHeavy"));
        owner.getAbilities().instabuild = false;
        owner.setPos(helper.absoluteVec(new Vec3(1.5D, 2.0D, 2.5D)));
        helper.getLevel().addNewPlayer(owner);
        Cow cow = EntityType.COW.create(helper.getLevel());
        StandPower power = PowerClass.STAND.attachGet(owner);
        try {
            helper.assertTrue(cow != null, "Cow fixture failed");
            cow.setNoAi(true);
            cow.setPos(helper.absoluteVec(new Vec3(3.5D, 2.0D, 2.5D)));
            helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Cow fixture could not join the level");
            power.setStand(HeavensDoorAddon.HEAVENS_DOOR.get());
            power.setResolveLevel(4);
            helper.assertTrue(HeavensDoorAddon.HEAVENS_DOOR.get().summon(owner, power), "Heaven's Door summon failed");
            HeavensDoorStandEntity stand = HeavensDoorAddon.controller(owner);
            helper.assertTrue(stand != null, "Missing summoned Heaven's Door");
            stand.summonLockTicks = 0;
            LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
            power.setStamina(power.getMaxStamina());

            Set<String> heavies = new TreeSet<>();
            for (Map.Entry<String, Ability> entry : power.getMoveset().abilities.entrySet()) {
                helper.assertTrue(!"heavy_charged".equals(entry.getKey())
                                && !(entry.getValue() instanceof StandEntityHeavyPunchChargedAbility),
                        "Heaven's Door has a charged heavy under '" + entry.getKey() + "'; 1.16 had none");
                if (entry.getValue() instanceof StandEntityHeavyPunchAbility) {
                    heavies.add(entry.getKey());
                }
            }
            helper.assertTrue(heavies.equals(new TreeSet<>(Set.of(HeavensDoorAddon.REMOVE_BOOK_ID, FINISHER))),
                    "1.16 heavy attacks were Remove Book and its finisher only, the moveset has " + heavies);

            Ability removeBook = power.getAbility(HeavensDoorAddon.REMOVE_BOOK_ID);
            Ability finisher = power.getAbility(FINISHER);
            helper.assertTrue(removeBook instanceof RemoveBookAbility && finisher instanceof HeavensDoorFinisherAbility,
                    "Remove Book or the finisher is not the module's own ability");
            helper.assertTrue(finisher.isStandFinisherOf != null
                            && HeavensDoorAddon.REMOVE_BOOK_ID.equals(finisher.isStandFinisherOf.baseAbilityName())
                            && finisher.isStandFinisherOf.minFinisherValue() == 0.5F,
                    "The finisher is not the half-meter variation of Remove Book");

            helper.assertTrue(BookStateService.markFromHit(stand, cow), "Book marking failed");
            HeavensDoorGameTestAims.lookAt(owner, cow);
            helper.assertTrue(BookStateService.authorizedTarget(owner, cow) == cow, "Fixture book is not authorized");

            assertHeavyAt(helper, power, stand, 0.0F, removeBook);
            assertHeavyAt(helper, power, stand, 0.49F, removeBook);
            assertHeavyAt(helper, power, stand, 0.6F, finisher);
            assertHeavyAt(helper, power, stand, 0.0F, removeBook);
            helper.succeed();
        }
        finally {
            if (power != null && power.isSummoned()) {
                HeavensDoorAddon.HEAVENS_DOOR.get().forceUnsummon(owner, power);
            }
            if (cow != null) {
                cow.discard();
            }
            EventHooks.firePlayerLoggedOut(owner);
            owner.discard();
        }
    }

    private static void assertHeavyAt(GameTestHelper helper, StandPower power, HeavensDoorStandEntity stand,
            float meter, Ability expected) {
        stand.setFinisherMeter(meter);
        power.tick(); // moves are cached per tick
        Ability resolved = power.updateAvailableMoves().getContextVariation(HeavensDoorAddon.REMOVE_BOOK_ID);
        helper.assertTrue(resolved == expected, "At finisher meter " + meter
                + " the RMB heavy resolved to " + (resolved != null ? resolved.name() : "nothing")
                + " instead of " + expected.name());
    }

    private static String clickRmbAbility(ControlSchemeTemplate scheme) {
        for (ControlSchemeTemplate.GroupTemplate group : scheme.groups.values()) {
            for (Map.Entry<String, Pair<InputMethod, InputBindTemplate>> bind : group.separateBinds.entrySet()) {
                if (bind.getValue().getFirst() == InputMethod.CLICK && bind.getValue().getSecond() == InputKey.RMB) {
                    return bind.getKey();
                }
            }
            for (ControlSchemeTemplate.SeparateBindTemplate bind : group.additionalSeparateBinds) {
                if (bind.inputMethod() == InputMethod.CLICK && bind.input() == InputKey.RMB) {
                    return bind.ability();
                }
            }
        }
        return null;
    }

    private static String boundToChargedHeavyInput(ControlSchemeTemplate scheme) {
        for (ControlSchemeTemplate.GroupTemplate group : scheme.groups.values()) {
            for (Map.Entry<String, Pair<InputMethod, InputBindTemplate>> bind : group.separateBinds.entrySet()) {
                if (bind.getValue().getSecond() == MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT) {
                    return bind.getKey();
                }
            }
            for (ControlSchemeTemplate.SeparateBindTemplate bind : group.additionalSeparateBinds) {
                if (bind.input() == MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT) {
                    return bind.ability();
                }
            }
        }
        return null;
    }
}
