package rotp.addons.sexpistols;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.sexpistols.registry.SexPistolsStands;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandPower;

/**
 * 1.16 Action.getCooldownTechnical, the first part of a cooldown(technical, additional, resolveMultiplier), which a
 * Stand-bearing mob waited (+ 10) before its next pick and Resolve never shortened. The Sex Pistols registrations of
 * 025 Next Album InitStands.java:96-104: the three special shots cooldown(240 / 160 / 240, 0, 0.5f), all technical, and
 * the plain cooldown(4), (4), (0), (40), (10), (4) of the commands, the reloads, the recall and the move, and the
 * trajectory vision, a bare StandAction.Builder (:95) without any cooldown.
 */
@GameTestHolder(SexPistolsIds.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SexPistolsCooldownTechnicalGameTests {
    private SexPistolsCooldownTechnicalGameTests() {}

    private record Row(String ability, int technical, int additional, float resolveMultiplier) {}

    private static final List<Row> ROWS = List.of(
            new Row(SexPistolsIds.SEX_PISTOLS_PIERCING_SHOT_PATH, 160, 0, 0.5F),
            new Row(SexPistolsIds.SEX_PISTOLS_SPLITTING_SHOT_PATH, 240, 0, 0.5F),
            new Row(SexPistolsIds.SEX_PISTOLS_ENCIRCLEMENT_PATH, 240, 0, 0.5F),
            new Row(SexPistolsIds.SEX_PISTOLS_TARGET_MODE_PATH, 0, 4, 0.0F),
            new Row(SexPistolsIds.SEX_PISTOLS_TRANSFER_ORDER_PATH, 0, 4, 0.0F),
            new Row(SexPistolsIds.SEX_PISTOLS_MOVE_SELECTED_PATH, 0, 4, 0.0F),
            new Row(SexPistolsIds.SEX_PISTOLS_STAND_RELOAD_PATH, 0, 0, 0.0F),
            new Row(SexPistolsIds.SEX_PISTOLS_QUICK_RELOAD_PATH, 0, 40, 0.0F),
            new Row(SexPistolsIds.SEX_PISTOLS_RECALL_PATH, 0, 10, 0.0F),
            new Row(SexPistolsIds.SEX_PISTOLS_TRAJECTORY_VISION_PATH, 0, 0, 0.0F));

    @GameTest(
            templateNamespace = "rotp_addon_api_smoke",
            template = "empty",
            batch = "sexPistolsCooldownTechnical",
            timeoutTicks = 40)
    public static void theShotsKeepTheDonorsTechnicalCooldownAndTheCommandsStayPlain(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        Cow user = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(user != null, "setup: no user");
        Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
        helper.assertTrue(helper.getLevel().addFreshEntity(user), "setup: could not add the user");
        try {
            StandPower power = PowerClass.STAND.attachGet(user);
            power.setStand(SexPistolsStands.SEX_PISTOLS.get());
            for (Row row : ROWS) {
                Ability ability = power.getAbility(row.ability());
                helper.assertTrue(ability != null, "setup: Sex Pistols has no " + row.ability());
                int technical = ability.getCooldownTechnical(power);
                int full = ability.getCooldown(power, -1);
                if (technical != row.technical() || full != row.technical() + row.additional()) {
                    wrong.add(row.ability() + " reports technical " + technical + " of a full " + full + ", the donor's "
                            + row.technical() + " of " + (row.technical() + row.additional()));
                }
            }
            user.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 200));
            for (Row row : ROWS) {
                int inResolve = power.getAbility(row.ability()).getCooldown(power, -1);
                int expected = row.technical() + (int) ((float) row.additional() * row.resolveMultiplier());
                if (inResolve != expected) {
                    wrong.add(row.ability() + " takes " + inResolve + " ticks in Resolve, the donor's " + expected);
                }
            }
        }
        finally {
            user.discard();
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 Sex Pistols cooldown(technical, additional, multiplier): "
                + String.join("; ", wrong));
        helper.succeed();
    }
}
