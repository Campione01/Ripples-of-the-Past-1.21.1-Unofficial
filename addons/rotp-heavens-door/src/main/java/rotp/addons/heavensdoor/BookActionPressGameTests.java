package rotp.addons.heavensdoor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.heavensdoor.ability.HeavensDoorTargeting;
import rotp.addons.heavensdoor.ability.RemoveBookAbility.RemoveBookAction;
import rotp.addons.heavensdoor.ability.ReturnPagesAbility.ReturnPageAction;
import rotp.addons.heavensdoor.ability.TearOutAPageAbility.TearPageAction;
import rotp.addons.heavensdoor.entity.HeavensDoorStandEntity;
import rotp.addons.heavensdoor.item.PageItem;
import rotp.addons.heavensdoor.state.BookStateService;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.target.ActionTarget;

/**
 * 1.16 Heaven's Door's book actions took the client's mouse target, which InputHandler.pickMouseTarget picked from the
 * camera entity: the Stand while it is manually controlled (StandUtil.java:160). Tear Out a Page, Return Pages and
 * Remove Book were Stand entity actions, so a press with the Stand away summoned it and went on
 * (StandEntityAction.onClick :201-211); the owner's rule of 2026-10-19 has Open Book's press summon it as well.
 */
@GameTestHolder(HeavensDoorAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BookActionPressGameTests {
    private static final short KEY = 125;
    private static final String FINISHER = "finisher_punch";
    private static final String NO_BOOK = "jojo.message.action_condition.heavens_door_no_book";
    private static final String NO_OWNED_BOOK = "rotp_hd.message.no_owned_book";

    private BookActionPressGameTests() {}

    // 040 OpenBook.java:58-59, RemoveBook.java:77-78 and HeavensDoorFinisherPunch :95-96 required an ENTITY mouse
    // target, picked from the Stand under manual control.
    @GameTest(template = "empty", timeoutTicks = HeavensDoorGameTestArea.WAIT_TEST_TICKS + 80)
    public static void bookActionsAimFromAManuallyControlledStandAsIn116(GameTestHelper helper) {
        HeavensDoorGameTestArea.whenLoaded(helper,
                () -> bookActionsAimFromAManuallyControlledStandAsIn116InLoadedArea(helper));
    }

    private static void bookActionsAimFromAManuallyControlledStandAsIn116InLoadedArea(GameTestHelper helper) {
        try (Scene scene = new Scene(helper, "HdBookManualAim")) {
            HeavensDoorStandEntity stand = scene.summon();
            helper.assertTrue(BookStateService.markFromHit(stand, scene.booked)
                            && BookStateService.markFromHit(stand, scene.other),
                    "Book marking failed");
            stand.setManuallyControlled(true);
            helper.assertTrue(stand.isManuallyControlled(), "fixture: the Stand is not manually controlled");
            Vec3 standPos = helper.absoluteVec(new Vec3(2.5D, 2.0D, 2.0D));
            stand.moveTo(standPos.x, standPos.y, standPos.z, 0.0F, 0.0F);
            Ability removeBook = scene.power.getAbility(HeavensDoorAddon.REMOVE_BOOK_ID);
            Ability finisher = scene.power.getAbility(FINISHER);
            helper.assertTrue(removeBook != null && finisher != null, "fixture: Remove Book or the finisher is missing");

            // the Stand looks at a booked cow, the user at a cow without a book
            scene.aim(stand, scene.booked, scene.plain);
            ConditionCheck remove = removeBook.checkConditions(scene.power);
            helper.assertTrue(remove.isPositive(),
                    "1.16 accepted Remove Book at the booked cow the manually controlled Stand looked at; the port"
                            + " answered " + Scene.key(remove));
            ConditionCheck finish = finisher.checkConditions(scene.power);
            helper.assertTrue(finish.isPositive(),
                    "1.16 accepted the finisher at the booked cow the manually controlled Stand looked at; the port"
                            + " answered " + Scene.key(finish));

            // the other way round
            scene.aim(stand, scene.plain, scene.booked);
            remove = removeBook.checkConditions(scene.power);
            helper.assertTrue(NO_BOOK.equals(Scene.key(remove)),
                    "1.16 refused Remove Book when the manually controlled Stand looked at a cow without a book,"
                            + " wherever the user looked; the port answered " + Scene.key(remove));
            finish = finisher.checkConditions(scene.power);
            helper.assertTrue(NO_BOOK.equals(Scene.key(finish)),
                    "1.16 refused the finisher when the manually controlled Stand looked at a cow without a book;"
                            + " the port answered " + Scene.key(finish));
            scene.press(HeavensDoorAddon.OPEN_BOOK_ID, InputMethod.CLICK);
            helper.assertTrue(scene.sessionTarget() == null && scene.owner.keys().equals(List.of(NO_OWNED_BOOK)),
                    "Open Book found no book where the manually controlled Stand looked; it opened "
                            + scene.sessionTarget() + " with " + scene.owner.keys());

            scene.aim(stand, scene.booked, scene.plain);
            scene.press(HeavensDoorAddon.OPEN_BOOK_ID, InputMethod.CLICK);
            helper.assertTrue(scene.booked.getUUID().equals(scene.sessionTarget()),
                    "1.16 opened the book of the cow the manually controlled Stand looked at; the session holds "
                            + scene.sessionTarget());
            helper.succeed();
        }
    }

    // 040 OpenBook.java :32, :58-59 was a StandAction with TargetRequirement.ENTITY and no overrideVanillaMouseTarget
    // (only StandEntityAction had one): pressed at a torch with a booked cow behind it, the mouse target stayed the
    // torch, no entity, and no book opened. Without the torch the same press opened the cow's book.
    @GameTest(template = "empty", timeoutTicks = HeavensDoorGameTestArea.WAIT_TEST_TICKS + 80)
    public static void openBookPressedAtATorchKeepsTheTorchAsIn116(GameTestHelper helper) {
        HeavensDoorGameTestArea.whenLoaded(helper,
                () -> openBookPressedAtATorchKeepsTheTorchAsIn116InLoadedArea(helper));
    }

    private static void openBookPressedAtATorchKeepsTheTorchAsIn116InLoadedArea(GameTestHelper helper) {
        BlockPos torch = null;
        try (Scene scene = new Scene(helper, "HdOpenBookTorch")) {
            HeavensDoorStandEntity marker = scene.summon();
            helper.assertTrue(BookStateService.markFromHit(marker, scene.booked), "Book marking failed");
            scene.unsummon();
            scene.plain.setPos(scene.plain.position().add(0.0D, 40.0D, 0.0D));
            scene.other.setPos(scene.other.position().add(0.0D, 40.0D, 0.0D));
            // the user looks south, level, at the booked cow 2.5 blocks ahead, a wall torch on that line a block ahead
            scene.owner.moveTo(scene.owner.getX(), scene.owner.getY(), scene.owner.getZ(), 0.0F, 0.0F);
            scene.owner.setYHeadRot(0.0F);
            scene.booked.setPos(scene.owner.getX(), scene.owner.getEyeY() - scene.booked.getBbHeight() / 2.0D,
                    scene.owner.getZ() + 2.5D);
            torch = BlockPos.containing(scene.owner.getEyePosition()).south();
            helper.getLevel().setBlock(torch, Blocks.WALL_TORCH.defaultBlockState()
                    .setValue(WallTorchBlock.FACING, Direction.NORTH), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            HeavensDoorGameTestAims.send(scene.owner);
            helper.assertTrue(torch.equals(HeavensDoorTargeting.pressedAim(scene.owner).getBlockPos()),
                    "fixture: the user's crosshair does not meet the torch: "
                            + HeavensDoorTargeting.pressedAim(scene.owner));
            scene.press(HeavensDoorAddon.OPEN_BOOK_ID, InputMethod.CLICK);
            helper.assertTrue(scene.sessionTarget() == null && scene.owner.keys().equals(List.of(NO_OWNED_BOOK)),
                    "1.16's Open Book pressed at a torch kept the torch, no entity, and opened no book; the port opened "
                            + scene.sessionTarget() + " with " + scene.owner.keys());
            helper.getLevel().setBlock(torch, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            HeavensDoorGameTestAims.send(scene.owner);
            scene.owner.messages.clear();
            scene.press(HeavensDoorAddon.OPEN_BOOK_ID, InputMethod.CLICK);
            helper.assertTrue(scene.booked.getUUID().equals(scene.sessionTarget()),
                    "fixture: without the torch Open Book did not open the booked cow's book; the session holds "
                            + scene.sessionTarget() + ", messages " + scene.owner.keys());
            helper.succeed();
        }
        finally {
            if (torch != null) {
                helper.getLevel().setBlock(torch, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    // 040 TearOutAPage :58, :147-149, RemoveBook :42, :77-79 and HeavensDoorFinisherPunch :43, :111-113 were
    // StandEntityActions with an ENTITY requirement: PowerBaseImpl.checkTarget ran StandEntityAction
    // .overrideVanillaMouseTarget (:181-198) on the mouse target the user's crosshair picked by block outlines, so a
    // torch there gave way to what the summoned Stand's collision ray met from the Stand's eyes along its look, not to
    // what lay behind the torch on the user's line. Without the torch the user's crosshair decided.
    @GameTest(template = "empty", timeoutTicks = HeavensDoorGameTestArea.WAIT_TEST_TICKS + 80)
    public static void standEntityBookActionsPressedAtATorchTakeTheStandsRayAsIn116(GameTestHelper helper) {
        HeavensDoorGameTestArea.whenLoaded(helper,
                () -> standEntityBookActionsPressedAtATorchTakeTheStandsRayAsIn116InLoadedArea(helper));
    }

    private static void standEntityBookActionsPressedAtATorchTakeTheStandsRayAsIn116InLoadedArea(
            GameTestHelper helper) {
        BlockPos torch = null;
        List<String> wrong = new ArrayList<>();
        try (Scene scene = new Scene(helper, "HdStandRayTorch")) {
            HeavensDoorStandEntity stand = scene.summon();
            helper.assertTrue(BookStateService.markFromHit(stand, scene.booked), "Book marking failed");
            scene.other.setPos(scene.other.position().add(0.0D, 40.0D, 0.0D));
            // the user looks south, level, at a wall torch a block ahead; Heaven's Door stands 1.5 blocks east with its
            // eyes level with the user's, looking south too; a cow 2.5 blocks ahead on each of their lines
            scene.owner.moveTo(scene.owner.getX(), scene.owner.getY(), scene.owner.getZ(), 0.0F, 0.0F);
            scene.owner.setYHeadRot(0.0F);
            stand.moveTo(scene.owner.getX() + 1.5D, scene.owner.getEyeY() - stand.getEyeHeight(), scene.owner.getZ(),
                    0.0F, 0.0F);
            stand.setYHeadRot(0.0F);
            torch = BlockPos.containing(scene.owner.getEyePosition()).south();
            helper.getLevel().setBlock(torch, Blocks.WALL_TORCH.defaultBlockState()
                    .setValue(WallTorchBlock.FACING, Direction.NORTH), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            Ability tear = scene.power.getAbility(HeavensDoorAddon.TEAR_PAGE_ID);
            Ability removeBook = scene.power.getAbility(HeavensDoorAddon.REMOVE_BOOK_ID);
            Ability finisher = scene.power.getAbility(FINISHER);
            helper.assertTrue(tear != null && removeBook != null && finisher != null,
                    "fixture: Tear Out a Page, Remove Book or the finisher is missing");

            // the booked cow on Heaven's Door's line, the cow without a book behind the torch on the user's
            standRayRow(scene, stand, scene.booked, scene.plain, true,
                    "the booked cow on Heaven's Door's line, a cow without a book behind the torch on the user's",
                    List.of(tear, removeBook, finisher), wrong);
            // the other way round
            standRayRow(scene, stand, scene.plain, scene.booked, false,
                    "a cow without a book on Heaven's Door's line, the booked cow behind the torch on the user's",
                    List.of(tear, removeBook, finisher), wrong);
            // no torch: the user's crosshair meets the booked cow
            helper.getLevel().setBlock(torch, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            standRayRow(scene, stand, scene.plain, scene.booked, true,
                    "no torch, the booked cow on the user's line, a cow without a book on Heaven's Door's",
                    List.of(tear, removeBook, finisher), wrong);
            helper.assertTrue(wrong.isEmpty(), "1.16 overrideVanillaMouseTarget for Heaven's Door's Stand entity "
                    + "actions: " + wrong);
            helper.succeed();
        }
        finally {
            if (torch != null) {
                helper.getLevel().setBlock(torch, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    private static void standRayRow(Scene scene, HeavensDoorStandEntity stand, Cow onStandsLine, Cow onUsersLine,
            boolean taken, String row, List<Ability> abilities, List<String> wrong) {
        onStandsLine.setPos(stand.getX(), scene.owner.getEyeY() - onStandsLine.getBbHeight() / 2.0D,
                scene.owner.getZ() + 2.5D);
        onUsersLine.setPos(scene.owner.getX(), scene.owner.getEyeY() - onUsersLine.getBbHeight() / 2.0D,
                scene.owner.getZ() + 2.5D);
        HeavensDoorGameTestAims.send(scene.owner);
        ActionTarget aim = HeavensDoorTargeting.pressedAim(scene.owner);
        boolean torch = scene.helper.getLevel().getBlockState(
                BlockPos.containing(scene.owner.getEyePosition()).south()).is(Blocks.WALL_TORCH);
        scene.helper.assertTrue(torch ? aim.getType() == ActionTarget.TargetType.BLOCK
                        : aim.getEntity() == onUsersLine,
                "fixture: the user's crosshair must meet " + (torch ? "the torch" : onUsersLine) + ", it meets "
                        + aim);
        for (Ability ability : abilities) {
            scene.power.setStamina(scene.power.getMaxStamina());
            ConditionCheck check = ability.checkConditions(scene.power);
            if (check.isPositive() != taken) {
                wrong.add(row + ": 1.16 " + (taken ? "took" : "refused") + " " + ability.name()
                        + ", the port answered " + Scene.key(check));
            }
        }
    }

    // 1.16's mouse target was the vanilla pick (InputHandler.pickMouseTarget: mc.hitResult, an entity only within the
    // player's entity reach, 3 blocks in Survival, by its own box), with the Stand away nothing more
    // (EntityStandType.clientHitResult :122-123 needed the power active). A booked cow on the user's line 4 blocks
    // ahead, or one whose box the line passes 0.2 blocks beside, was no target: the Stand entity actions refused the
    // press and Open Book found no book. Within reach the same cow was taken.
    @GameTest(template = "empty", timeoutTicks = HeavensDoorGameTestArea.WAIT_TEST_TICKS + 80)
    public static void bookActionsWithTheStandAwayTakeOnlyTheVanillaPickAsIn116(GameTestHelper helper) {
        HeavensDoorGameTestArea.whenLoaded(helper,
                () -> bookActionsWithTheStandAwayTakeOnlyTheVanillaPickAsIn116InLoadedArea(helper));
    }

    private static void bookActionsWithTheStandAwayTakeOnlyTheVanillaPickAsIn116InLoadedArea(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        try (Scene scene = new Scene(helper, "HdVanillaPick")) {
            HeavensDoorStandEntity marker = scene.summon();
            helper.assertTrue(BookStateService.markFromHit(marker, scene.booked), "Book marking failed");
            scene.unsummon();
            scene.plain.setPos(scene.plain.position().add(0.0D, 40.0D, 0.0D));
            scene.other.setPos(scene.other.position().add(0.0D, 40.0D, 0.0D));
            Ability tear = scene.power.getAbility(HeavensDoorAddon.TEAR_PAGE_ID);
            Ability removeBook = scene.power.getAbility(HeavensDoorAddon.REMOVE_BOOK_ID);
            helper.assertTrue(tear != null && removeBook != null, "fixture: Tear Out a Page or Remove Book is missing");
            scene.lookSouth();
            for (double[] row : new double[][] {{0.0D, 4.0D}, {0.65D, 2.0D}}) {
                scene.placeBooked(row[0], row[1]);
                HeavensDoorGameTestAims.send(scene.owner);
                helper.assertTrue(LivingComponentAction.getAim(scene.owner).getTarget().getType()
                                == ActionTarget.TargetType.EMPTY,
                        "fixture: the vanilla pick must miss the booked cow " + row[1] + " blocks ahead, " + row[0]
                                + " aside");
                String at = "the booked cow " + row[1] + " blocks ahead, " + row[0] + " aside";
                if (scene.aimed() != null) {
                    wrong.add("1.16's mouse target was no entity at " + at + "; the port's aim meets "
                            + scene.aimed());
                }
                if (tear.checkConditions(scene.power).isPositive()) {
                    wrong.add("1.16 refused Tear Out a Page at " + at);
                }
                if (removeBook.checkConditions(scene.power).isPositive()) {
                    wrong.add("1.16 refused Remove Book at " + at);
                }
                scene.press(HeavensDoorAddon.OPEN_BOOK_ID, InputMethod.CLICK);
                if (scene.sessionTarget() != null || !scene.owner.keys().equals(List.of(NO_OWNED_BOOK))) {
                    wrong.add("1.16's Open Book found no book at " + at + "; the port opened "
                            + scene.sessionTarget() + " with " + scene.owner.keys());
                }
                scene.unsummon();
            }
            scene.placeBooked(0.0D, 2.5D);
            HeavensDoorGameTestAims.send(scene.owner);
            helper.assertTrue(scene.aimed() == scene.booked, "fixture: the vanilla pick must meet the booked cow"
                    + " 2.5 blocks ahead; it meets " + scene.aimed());
            ConditionCheck press = tear.checkConditions(scene.power);
            if (!press.isPositive()) {
                wrong.add("1.16 took Tear Out a Page at the booked cow within reach; the port answered "
                        + Scene.key(press));
            }
            scene.press(HeavensDoorAddon.OPEN_BOOK_ID, InputMethod.CLICK);
            if (!scene.booked.getUUID().equals(scene.sessionTarget())) {
                wrong.add("1.16 opened the book of the booked cow within reach; the session holds "
                        + scene.sessionTarget() + ", messages " + scene.owner.keys());
            }
            helper.assertTrue(wrong.isEmpty(), "1.16's vanilla pick with the Stand away: " + wrong);
            helper.succeed();
        }
    }

    // EntityStandType.clientHitResult :122-151: with the Stand out, a vanilla pick that met nothing gave way to the
    // Stand's precision ray from the user's eyes at the user's reach (StandEntity.precisionRayTrace), so the booked
    // cow 4 blocks ahead was the target of every book action; the Stand taken back, the same view met nothing.
    @GameTest(template = "empty", timeoutTicks = HeavensDoorGameTestArea.WAIT_TEST_TICKS + 80)
    public static void bookActionsBehindAMissTakeTheStandsPrecisionRayAsIn116(GameTestHelper helper) {
        HeavensDoorGameTestArea.whenLoaded(helper,
                () -> bookActionsBehindAMissTakeTheStandsPrecisionRayAsIn116InLoadedArea(helper));
    }

    private static void bookActionsBehindAMissTakeTheStandsPrecisionRayAsIn116InLoadedArea(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        try (Scene scene = new Scene(helper, "HdPrecisionRay")) {
            HeavensDoorStandEntity stand = scene.summon();
            helper.assertTrue(BookStateService.markFromHit(stand, scene.booked), "Book marking failed");
            scene.plain.setPos(scene.plain.position().add(0.0D, 40.0D, 0.0D));
            scene.other.setPos(scene.other.position().add(0.0D, 40.0D, 0.0D));
            Ability tear = scene.power.getAbility(HeavensDoorAddon.TEAR_PAGE_ID);
            Ability removeBook = scene.power.getAbility(HeavensDoorAddon.REMOVE_BOOK_ID);
            Ability finisher = scene.power.getAbility(FINISHER);
            helper.assertTrue(tear != null && removeBook != null && finisher != null,
                    "fixture: Tear Out a Page, Remove Book or the finisher is missing");
            scene.lookSouth();
            scene.placeBooked(0.0D, 4.0D);
            HeavensDoorGameTestAims.send(scene.owner);
            helper.assertTrue(LivingComponentAction.getAim(scene.owner).getTarget().getType()
                            == ActionTarget.TargetType.EMPTY,
                    "fixture: the vanilla pick must miss the booked cow 4 blocks ahead");
            helper.assertTrue(scene.aimed() == scene.booked,
                    "1.16's Stand precision ray took the booked cow behind a vanilla miss; the aim meets "
                            + scene.aimed());
            for (Ability ability : List.of(tear, removeBook, finisher)) {
                scene.power.setStamina(scene.power.getMaxStamina());
                ConditionCheck check = ability.checkConditions(scene.power);
                if (!check.isPositive()) {
                    wrong.add("1.16 took " + ability.name() + " at the cow the precision ray met; the port answered "
                            + Scene.key(check));
                }
            }
            scene.unsummon();
            if (scene.aimed() != null || tear.checkConditions(scene.power).isPositive()) {
                wrong.add("with the Stand away 1.16 had no precision ray: the aim meets " + scene.aimed());
            }
            helper.assertTrue(wrong.isEmpty(), "1.16's precision ray behind a miss: " + wrong);
            helper.succeed();
        }
    }

    // 1.16 TearOutAPage, ReturnPages and RemoveBook were StandEntityActions (StandEntityAction.onClick :201-211
    // summoned the Stand before checkRequirements); the owner's rule has Open Book do the same.
    @GameTest(template = "empty", timeoutTicks = HeavensDoorGameTestArea.WAIT_TEST_TICKS + 100)
    public static void bookActionsSummonHeavensDoorForThePressAsIn116(GameTestHelper helper) {
        HeavensDoorGameTestArea.whenLoaded(helper,
                () -> bookActionsSummonHeavensDoorForThePressAsIn116InLoadedArea(helper));
    }

    private static void bookActionsSummonHeavensDoorForThePressAsIn116InLoadedArea(GameTestHelper helper) {
        try (Scene scene = new Scene(helper, "HdBookAwayPress")) {
            HeavensDoorStandEntity marker = scene.summon();
            helper.assertTrue(BookStateService.markFromHit(marker, scene.booked)
                            && BookStateService.markFromHit(marker, scene.other),
                    "Book marking failed");

            scene.unsummon();
            // with the Stand away only the vanilla pick: the cows within the user's entity reach
            scene.owner.setPos(helper.absoluteVec(new Vec3(1.5D, 2.0D, 2.5D)));
            HeavensDoorGameTestAims.lookAt(scene.owner, scene.booked);
            EntityActionInstance tear = scene.press(HeavensDoorAddon.TEAR_PAGE_ID, InputMethod.HOLD);
            helper.assertTrue(tear instanceof TearPageAction && tear.getTaskTarget(helper.getLevel()).getEntity()
                            == scene.booked,
                    "1.16 summoned Heaven's Door for Tear Out a Page and charged at the booked cow; the action is "
                            + tear);
            tear.setPhaseStart(ActionPhase.PERFORM);
            tear._tickAction();
            helper.assertTrue(scene.pages(scene.booked) == 1,
                    "the summoned Heaven's Door tore the page; pages " + scene.pages(scene.booked));
            AbilityInput.keyRelease(KEY, scene.owner);

            scene.unsummon();
            ItemStack page = scene.takePageIntoMainHand();
            int pagesHeld = page.getCount();
            EntityActionInstance returned = scene.press(HeavensDoorAddon.RETURN_PAGES_ID, InputMethod.CLICK);
            helper.assertTrue(returned instanceof ReturnPageAction,
                    "1.16 summoned Heaven's Door for Return Pages; the action is " + returned);
            if (!returned.isOver()) {
                returned.setPhaseStart(ActionPhase.PERFORM);
                returned._tickAction();
            }
            helper.assertTrue(scene.pages(scene.booked) == 0
                            && scene.owner.getMainHandItem().getCount() == pagesHeld - 1,
                    "the summoned Heaven's Door returned the page; pages " + scene.pages(scene.booked) + ", held "
                            + scene.owner.getMainHandItem());
            AbilityInput.keyRelease(KEY, scene.owner);

            scene.unsummon();
            HeavensDoorGameTestAims.lookAt(scene.owner, scene.other);
            EntityActionInstance remove = scene.press(HeavensDoorAddon.REMOVE_BOOK_ID, InputMethod.CLICK);
            helper.assertTrue(remove instanceof RemoveBookAction,
                    "1.16 summoned Heaven's Door for Remove Book; the action is " + remove);
            remove.setPhaseStart(ActionPhase.PERFORM);
            remove._tickAction();
            helper.assertTrue(!HeavensDoorAddon.bookState(scene.other).active(),
                    "the summoned Heaven's Door took the book off the aimed cow");
            AbilityInput.keyRelease(KEY, scene.owner);

            scene.unsummon();
            HeavensDoorGameTestAims.lookAt(scene.owner, scene.booked);
            scene.press(HeavensDoorAddon.OPEN_BOOK_ID, InputMethod.CLICK);
            helper.assertTrue(HeavensDoorAddon.controller(scene.owner) != null
                            && scene.booked.getUUID().equals(scene.sessionTarget()),
                    "Open Book summoned Heaven's Door and opened the booked cow's book; summoned "
                            + (HeavensDoorAddon.controller(scene.owner) != null) + ", session " + scene.sessionTarget());

            // a refused press leaves the Stand away (1.16 took back a Stand it summoned for a refused press)
            scene.unsummon();
            scene.owner.messages.clear();
            scene.owner.setXRot(-90.0F);
            HeavensDoorGameTestAims.send(scene.owner);
            scene.press(HeavensDoorAddon.OPEN_BOOK_ID, InputMethod.CLICK);
            helper.assertTrue(HeavensDoorAddon.controller(scene.owner) == null
                            && scene.owner.keys().equals(List.of(NO_OWNED_BOOK)),
                    "Open Book at nothing keeps its message and summons nothing; summoned "
                            + (HeavensDoorAddon.controller(scene.owner) != null) + ", " + scene.owner.keys());
            helper.succeed();
        }
    }

    // 1.16 StandEntityAction.checkStandTarget ran after the press summoned the Stand: a booked teammate without
    // friendly fire refused the press silently, and the Stand summoned for it was taken back.
    @GameTest(template = "empty", timeoutTicks = HeavensDoorGameTestArea.WAIT_TEST_TICKS + 80)
    public static void tearPageWithTheStandAwayRefusesWhatTheStandMayNotAttack(GameTestHelper helper) {
        HeavensDoorGameTestArea.whenLoaded(helper,
                () -> tearPageWithTheStandAwayRefusesWhatTheStandMayNotAttackInLoadedArea(helper));
    }

    private static void tearPageWithTheStandAwayRefusesWhatTheStandMayNotAttackInLoadedArea(GameTestHelper helper) {
        try (Scene scene = new Scene(helper, "HdBookAwayAlly")) {
            HeavensDoorStandEntity marker = scene.summon();
            helper.assertTrue(BookStateService.markFromHit(marker, scene.booked), "Book marking failed");
            scene.unsummon();
            Scoreboard board = helper.getLevel().getScoreboard();
            PlayerTeam team = board.addPlayerTeam("hd-away-allies-" + helper.getTick());
            try {
                team.setAllowFriendlyFire(false);
                board.addPlayerToTeam(scene.owner.getScoreboardName(), team);
                board.addPlayerToTeam(scene.booked.getScoreboardName(), team);
                scene.owner.setPos(helper.absoluteVec(new Vec3(1.5D, 2.0D, 2.5D)));
                HeavensDoorGameTestAims.lookAt(scene.owner, scene.booked);
                helper.assertTrue(scene.aimed() == scene.booked,
                        "fixture: the view must meet the booked teammate cow");
                ConditionCheck press = scene.power.getAbility(HeavensDoorAddon.TEAR_PAGE_ID)
                        .checkConditions(scene.power);
                helper.assertTrue(!press.isPositive() && press.getWarning() == null,
                        "1.16 refused Tear Out a Page at a teammate without friendly fire silently, the Stand away"
                                + " too; the port answered "
                                + (press.isPositive() ? "a positive check" : Scene.key(press)));
                helper.assertTrue(HeavensDoorAddon.controller(scene.owner) == null,
                        "the refused press summoned Heaven's Door");
            }
            finally {
                board.removePlayerTeam(team);
            }
            helper.succeed();
        }
    }

    private static final class Scene implements AutoCloseable {
        final GameTestHelper helper;
        final RecordingPlayer owner;
        final Cow booked;
        final Cow other;
        final Cow plain;
        final StandPower power;

        Scene(GameTestHelper helper, String name) {
            this.helper = helper;
            owner = new RecordingPlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
            owner.getAbilities().instabuild = false;
            owner.setPos(helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D)));
            helper.getLevel().addNewPlayer(owner);
            booked = cow(new Vec3(3.5D, 2.0D, 4.5D));
            other = cow(new Vec3(-0.5D, 2.0D, 4.5D));
            plain = cow(new Vec3(1.5D, 2.0D, 4.5D));
            power = PowerClass.STAND.attachGet(owner);
            power.setStand(HeavensDoorAddon.HEAVENS_DOOR.get());
            power.setResolveLevel(4);
        }

        private Cow cow(Vec3 position) {
            Cow cow = EntityType.COW.create(helper.getLevel());
            helper.assertTrue(cow != null, "Cow fixture failed");
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.setPos(helper.absoluteVec(position));
            helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Cow fixture could not join the level");
            return cow;
        }

        HeavensDoorStandEntity summon() {
            helper.assertTrue(HeavensDoorAddon.HEAVENS_DOOR.get().summon(owner, power),
                    "Heaven's Door summon failed");
            HeavensDoorStandEntity stand = HeavensDoorAddon.controller(owner);
            helper.assertTrue(stand != null, "Missing summoned Heaven's Door");
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
            LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
            return stand;
        }

        void unsummon() {
            if (power.isSummoned()) {
                HeavensDoorAddon.HEAVENS_DOOR.get().forceUnsummon(owner, power);
            }
            helper.assertTrue(HeavensDoorAddon.controller(owner) == null, "fixture: Heaven's Door is still out");
            power.setStamina(power.getMaxStamina());
            owner.messages.clear();
        }

        // under manual control the client's camera is the Stand: the user's own view sends nothing
        void aim(HeavensDoorStandEntity stand, LivingEntity standLooksAt, LivingEntity userLooksAt) {
            owner.lookAt(EntityAnchorArgument.Anchor.EYES, userLooksAt.getEyePosition());
            HeavensDoorGameTestAims.standLooksAt(owner, stand, standLooksAt);
            helper.assertTrue(aimed() == standLooksAt,
                    "fixture: the Stand must look at " + standLooksAt + " while the user looks at " + userLooksAt
                            + "; the aim meets " + aimed());
        }

        // the user looks south, level
        void lookSouth() {
            owner.moveTo(owner.getX(), owner.getY(), owner.getZ(), 0.0F, 0.0F);
            owner.setYHeadRot(0.0F);
        }

        // the booked cow before the user's eyes, its centre this far aside (east) and ahead (south)
        void placeBooked(double aside, double ahead) {
            booked.setPos(owner.getX() + aside, owner.getEyeY() - booked.getBbHeight() / 2.0D, owner.getZ() + ahead);
        }

        // what the book actions take: 1.16's mouse target
        LivingEntity aimed() {
            return HeavensDoorTargeting.livingTarget(HeavensDoorTargeting.pressedAim(owner));
        }

        // The server's press: the condition check, then the key press (the core summons a Stand entity action's
        // Stand); returns the summoned Stand's action, if any.
        EntityActionInstance press(String abilityId, InputMethod input) {
            power.tick(); // moves are cached per tick
            Ability ability = power.getAbility(abilityId);
            AbilityConditionCheck selected = power.updateAvailableMoves().getContextVariationContainer(ability);
            helper.assertTrue(selected != null && selected.ability == ability,
                    abilityId + " is not in the moveset as itself: " + (selected != null ? selected.ability : null));
            helper.assertTrue(AbilityInput.withConditionCheck(selected, owner, input),
                    "1.16 performed the press of " + abilityId + "; the port refused it with "
                            + key(selected.conditionCheck));
            AbilityInput.keyPress(KEY, ability, owner, null, input, 0, BufferingState.clickOnly(),
                    ability.getAbilityId());
            HeavensDoorStandEntity stand = HeavensDoorAddon.controller(owner);
            return stand != null ? stand.getCurStandAction() : null;
        }

        UUID sessionTarget() {
            CompoundTag session = owner.getPersistentData().getCompound("rotp_hd.BookCommandSession");
            return session.hasUUID("Target") ? session.getUUID("Target") : null;
        }

        int pages(Cow cow) {
            return HeavensDoorAddon.bookState(cow).pageCount();
        }

        ItemStack takePageIntoMainHand() {
            for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
                ItemStack stack = owner.getInventory().getItem(slot);
                if (PageItem.data(stack) != null) {
                    owner.getInventory().setItem(slot, ItemStack.EMPTY);
                    owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
                    return owner.getMainHandItem();
                }
            }
            throw new IllegalStateException("fixture: the tear gave no page");
        }

        static String key(ConditionCheck check) {
            if (check.isPositive()) {
                return "a positive check";
            }
            Component warning = check.getWarning();
            return warning == null ? "a silent refusal"
                    : warning.getContents() instanceof TranslatableContents translatable ? translatable.getKey()
                    : warning.getString();
        }

        @Override
        public void close() {
            AbilityInput.keyRelease(KEY, owner);
            if (power.isSummoned()) {
                HeavensDoorAddon.HEAVENS_DOOR.get().forceUnsummon(owner, power);
            }
            booked.discard();
            other.discard();
            plain.discard();
            EventHooks.firePlayerLoggedOut(owner);
            owner.discard();
        }
    }

    // Keeps the action-bar messages a FakePlayer would drop.
    private static final class RecordingPlayer extends FakePlayer {
        final List<Component> messages = new ArrayList<>();

        RecordingPlayer(ServerLevel level, GameProfile profile) {
            super(level, profile);
        }

        @Override
        public void displayClientMessage(Component message, boolean actionBar) {
            messages.add(message);
        }

        List<String> keys() {
            List<String> found = new ArrayList<>();
            for (Component message : messages) {
                found.add(message.getContents() instanceof TranslatableContents translatable
                        ? translatable.getKey()
                        : message.getString());
            }
            return found;
        }
    }
}
