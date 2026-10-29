package rotp.addons.kingcrimson.gametest;

import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.core.init.ModItems;

/**
 * 1.16 en_us: 'Throw Projectile' / 'Throw Projectiles', and the refusal texts of KingCrimsonProjectileThrow; a Stand
 * Arrow is refused with the 'nope' condition, whose text is a single space.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonThrowTextGameTests {
    private KingCrimsonThrowTextGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_throw_text")
    public static void throwUsesTheDonorTexts(GameTestHelper helper) {
        KingCrimsonThrowGameTestFixture.identity(helper, KingCrimsonThrowTextGameTests.class);
        var checks = new KingCrimsonThrowGameTestFixture.Checks();
        Language language = Language.getInstance();
        for (var name : Map.of(
                "rotp_kingcrimson.ability.kingcrimson_projectile_throw", "Throw Projectile",
                "rotp_kingcrimson.ability.kingcrimson_projectile_throw_tripple", "Throw Projectiles").entrySet()) {
            checks.check(name.getValue().equals(language.getOrDefault(name.getKey())),
                    "text/name " + name.getKey() + "='" + language.getOrDefault(name.getKey()) + "'");
        }
        record Case(String id, ItemStack main, ItemStack off, String key, String text) {}
        for (Case value : new Case[] {
                new Case("stand-arrow", new ItemStack(ModItems.STAND_ARROW.get()), ItemStack.EMPTY, "stand_arrow_not_throwable", " "),
                new Case("beetle-arrow", new ItemStack(ModItems.STAND_ARROW_BEETLE.get()), ItemStack.EMPTY, "stand_arrow_not_throwable", " "),
                new Case("empty-hand", ItemStack.EMPTY, ItemStack.EMPTY, "no_throwable_item_in_mainhand", "You don't have any items to throw"),
                new Case("dirt", new ItemStack(Items.DIRT), ItemStack.EMPTY, "not_throwable", "You can't throw that item"),
                new Case("molotov", new ItemStack(ModItems.MOLOTOV.get()), ItemStack.EMPTY, "molotov_flint_and_steel",
                        "You must hold Flint and Steel in your offhand")}) {
            for (String ability : new String[] {KingCrimsonThrowGameTestFixture.SINGLE, KingCrimsonThrowGameTestFixture.TRIPLE}) {
                String id = "text/" + ability + "/" + value.id();
                try (var scene = new KingCrimsonThrowGameTestFixture(helper, id, KingCrimsonThrowGameTestFixture.Mode.FINITE, 600, 73)) {
                    scene.hand(value.main().copy()); scene.offhand(value.off().copy());
                    var selected = scene.power.updateAvailableMoves().getContextVariationContainer(scene.power.getAbility(ability));
                    scene.premise(selected != null, "Registered throw container");
                    Component warning = selected.conditionCheck.getWarning();
                    String key = "rotp_kingcrimson.message." + value.key();
                    checks.check(!selected.conditionCheck.isPositive() && warning != null
                                    && warning.getContents() instanceof TranslatableContents translatable
                                    && translatable.getKey().equals(key) && value.text().equals(warning.getString()),
                            id + " refusal " + key + " reads '" + (warning != null ? warning.getString() : null)
                                    + "' expected '" + value.text() + "'");
                }
            }
        }
        checks.finish(helper, "KC-THROW-TEXT", 12);
    }
}
