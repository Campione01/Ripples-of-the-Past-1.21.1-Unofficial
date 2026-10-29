package rotp.addons.ultimate_lifeform;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ultimate_lifeform.action.UltimateLifeformAbility;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformData;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformSpec;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;

/**
 * The 1.16 held and player action rules the core's K5-48 asks of a non-Stand power: PillarmanAbsorption
 * .heldAllowsOtherAction (:124-126, inherited by 040 UltimateLifeformAbsorbtion) let the power's other actions through
 * its hold, and the eight 040 actions that implemented IPlayerAction set a continuous player action in their perform,
 * during which 1.16 Action.checkConditions (:140-143) refused every action that could not be used in it (all but a
 * Stand's) and the HUD drew them unavailable.
 */
@GameTestHolder(UltimateLifeformAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UltimateHeldAndPlayerActionGameTests {
    private static final Set<UltimateLifeformSpec> DONOR_PLAYER_ACTIONS = Set.of(
            UltimateLifeformSpec.HEAVY_PUNCH,
            UltimateLifeformSpec.HAMON_OVERDRIVE_BEAT,
            UltimateLifeformSpec.BLADE_SLASH,
            UltimateLifeformSpec.BLADE_DASH_ATTACK,
            UltimateLifeformSpec.ERRATIC_BLAZE_KING,
            UltimateLifeformSpec.ULTIMATE_HAMON_OVERDRIVE,
            UltimateLifeformSpec.ULTIMATE_HAMON_KICK,
            UltimateLifeformSpec.HAMON_SHOCK);

    private UltimateHeldAndPlayerActionGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void heldAbsorptionLetsThePowersOtherSlotsThroughAsIn116(GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateHeldAbsorption", "c3e2a4b6-2d3f-4e5a-8b6c-7d8e9f0a1b01");
        List<String> wrong = new ArrayList<>();
        for (UltimateLifeformSpec spec : UltimateLifeformSpec.values()) {
            boolean allows = holder.instance(spec).heldAllowsOtherAbilities();
            if (allows != (spec == UltimateLifeformSpec.ABSORPTION)) {
                wrong.add(spec.id + " lets the others through its hold: " + allows);
            }
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 heldAllowsOtherAction was true for Absorption alone: "
                + String.join("; ", wrong));
        Ability scaleShot = holder.ability(UltimateLifeformSpec.SCALE_SHOT);
        Ability horn = holder.ability(UltimateLifeformSpec.HORN_ATTACK);
        Cow cow = cow(helper, new Vec3(2.5D, 2.0D, 3.75D));
        short absorbKey = (short) 32_701;
        short barrageKey = (short) 32_702;
        helper.runAfterDelay(1, () -> {
            holder.aim(cow);
            holder.press(helper, UltimateLifeformSpec.ABSORPTION, absorbKey);
        });
        helper.runAfterDelay(5, () -> {
            EntityActionInstance held = AbilityInput.getHeldAction(holder.player, PowerClass.PLAYER_POWER);
            helper.assertTrue(specOf(held) == UltimateLifeformSpec.ABSORPTION,
                    "setup: Absorption is not the power's held action: " + held);
            boolean scaleBack = scaleShot.isHeldBackByHeldAction(holder.player);
            boolean hornBack = horn.isHeldBackByHeldAction(holder.player);
            helper.assertTrue(held.heldAllowsOtherAbilities() && !scaleBack && !hornBack,
                    "1.16 let the power's other actions through a held Absorption and drew them usable: lets"
                            + " others through " + held.heldAllowsOtherAbilities() + ", Scale Shot held back "
                            + scaleBack + ", Horn Attack held back " + hornBack);
            AbilityInput.keyRelease(absorbKey, holder.player);
        });
        helper.runAfterDelay(8, () -> holder.press(helper, UltimateLifeformSpec.ULTIMATE_BARRAGE, barrageKey));
        helper.runAfterDelay(11, () -> {
            EntityActionInstance held = AbilityInput.getHeldAction(holder.player, PowerClass.PLAYER_POWER);
            helper.assertTrue(specOf(held) == UltimateLifeformSpec.ULTIMATE_BARRAGE,
                    "setup: the Ultimate Barrage is not the power's held action: " + held);
            boolean scaleBack = scaleShot.isHeldBackByHeldAction(holder.player);
            boolean hornBack = horn.isHeldBackByHeldAction(holder.player);
            helper.assertTrue(!held.heldAllowsOtherAbilities() && scaleBack && hornBack,
                    "1.16 refused the power's other actions while the Ultimate Barrage was held and drew them"
                            + " unavailable: lets others through " + held.heldAllowsOtherAbilities()
                            + ", Scale Shot held back " + scaleBack + ", Horn Attack held back " + hornBack);
            cow.discard();
            holder.close(barrageKey);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void playerActionMovesGateThePowerDuringTheirPerformAsIn116(GameTestHelper helper) {
        Holder holder = holder(helper, "UltimatePlayerAction", "c3e2a4b6-2d3f-4e5a-8b6c-7d8e9f0a1b02");
        List<String> wrong = new ArrayList<>();
        for (UltimateLifeformSpec spec : UltimateLifeformSpec.values()) {
            for (ActionPhase phase : ActionPhase.values()) {
                EntityActionInstance action = holder.instance(spec);
                for (ActionPhase length : ActionPhase.values()) {
                    action.phasesLength.put(length, 10.0F);
                }
                action.setPhase(phase, 0);
                boolean expected = phase == ActionPhase.PERFORM && DONOR_PLAYER_ACTIONS.contains(spec);
                if (action.isPlayerAction() != expected) {
                    wrong.add(spec.id + " in " + phase + " is a player action: " + action.isPlayerAction());
                }
            }
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 set a continuous player action in the perform of the eight"
                + " IPlayerAction moves alone: " + String.join("; ", wrong));
        Ability horn = holder.ability(UltimateLifeformSpec.HORN_ATTACK);
        short punchKey = (short) 32_703;
        String[] perform = new String[1];
        String[] recovery = new String[1];
        holder.aimNothing();
        helper.assertTrue(holder.tryClick(UltimateLifeformSpec.HEAVY_PUNCH, punchKey),
                "setup: the heavy punch press was refused: " + holder.messages);
        // registered here, not from a delayed task: adding ticks to the helper's schedule while it runs one can run
        // that task again
        helper.onEachTick(() -> {
            EntityActionInstance action = LivingComponentAction.getCurEntityAction(holder.player);
            if (action == null || action.isOver() || specOf(action) != UltimateLifeformSpec.HEAVY_PUNCH) {
                return;
            }
            String state = "player action " + action.isPlayerAction() + ", Horn Attack drawn unavailable "
                    + horn.isHeldBackByHeldAction(holder.player) + ", its press refused "
                    + !horn.checkConditions(holder.power).isPositive();
            if (action.getPhase() == ActionPhase.PERFORM && perform[0] == null) {
                perform[0] = state;
            }
            else if (action.getPhase() == ActionPhase.RECOVERY && recovery[0] == null) {
                recovery[0] = state;
            }
        });
        helper.runAfterDelay(20, () -> {
            String during = "player action true, Horn Attack drawn unavailable true, its press refused false";
            String after = "player action false, Horn Attack drawn unavailable false, its press refused false";
            helper.assertTrue(during.equals(perform[0]) && after.equals(recovery[0]),
                    "1.16 made the heavy punch's perform a continuous player action (the power's slots drawn"
                            + " unavailable, a press waiting behind it) and nothing after it: in its PERFORM "
                            + perform[0] + "; in its RECOVERY " + recovery[0]);
            holder.close(punchKey);
            helper.succeed();
        });
    }

    private static UltimateLifeformSpec specOf(EntityActionInstance action) {
        return action != null && action.ability instanceof UltimateLifeformAbility ultimate ? ultimate.spec() : null;
    }

    private static Cow cow(GameTestHelper helper, Vec3 relative) {
        Cow cow = EntityType.COW.create(helper.getLevel());
        if (cow == null) {
            throw new IllegalStateException("Cow fixture failed");
        }
        cow.setPos(Vec3.atBottomCenterOf(helper.absolutePos(BlockPos.ZERO))
                .add(relative.x - 0.5D, relative.y, relative.z - 0.5D));
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1_000.0D);
        cow.setHealth(1_000.0F);
        helper.getLevel().addFreshEntity(cow);
        return cow;
    }

    private static Holder holder(GameTestHelper helper, String name, String uuid) {
        List<Component> messages = new ArrayList<>();
        ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.fromString(uuid), name)) {
            @Override
            public void displayClientMessage(Component message, boolean overlay) {
                if (overlay) {
                    messages.add(message);
                }
            }
        };
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        player.setGameMode(GameType.SURVIVAL);
        if (!helper.getLevel().addFreshEntity(player)) {
            throw new IllegalStateException("Held and player action test player could not be added: " + name);
        }
        PlayerPower power = PowerClass.PLAYER_POWER.attachGet(player);
        if (!power.trySetPowerType(UltimateLifeformAddon.ULTIMATE_LIFEFORM.get())) {
            throw new IllegalStateException("Held and player action test power grant failed: " + name);
        }
        power.updateAvailableMoves();
        UltimateLifeformData data = power.getCurTypeData(UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElseThrow();
        data.setCommandEnergy(player, UltimateLifeformData.MAX_ENERGY);
        return new Holder(player, power, data, messages);
    }

    private record Holder(ServerPlayer player, PlayerPower power, UltimateLifeformData data,
            List<Component> messages) {
        Ability ability(UltimateLifeformSpec spec) {
            Ability ability = power.getAbility(spec.id);
            if (ability == null) {
                throw new IllegalStateException("No " + spec.id);
            }
            return ability;
        }

        // a fresh, unassigned action of the move, as its client copy is decoded
        EntityActionInstance instance(UltimateLifeformSpec spec) {
            return new UltimateLifeformAbility.UltimateActionInstance((UltimateLifeformAbility) ability(spec));
        }

        void press(GameTestHelper helper, UltimateLifeformSpec spec, short key) {
            helper.assertTrue(input(spec, key, InputMethod.HOLD),
                    "setup: the " + spec.id + " press was refused: " + messages);
        }

        boolean tryClick(UltimateLifeformSpec spec, short key) {
            try {
                return input(spec, key, InputMethod.CLICK);
            }
            finally {
                AbilityInput.keyRelease(key, player);
            }
        }

        // whether the press went through at once (not refused, not waiting in the input buffer)
        private boolean input(UltimateLifeformSpec spec, short key, InputMethod method) {
            Ability ability = ability(spec);
            BufferingState buffering = BufferingState.clickCanBuffer();
            FriendlyByteBuf extra = new FriendlyByteBuf(Unpooled.buffer());
            try {
                Object held = AbilityInput.keyPress(key, ability, player, extra, method, 0.0F, buffering,
                        ability.getAbilityId());
                return !buffering.isBuffered() && (held != null || buffering.isActionSuccess);
            }
            finally {
                extra.release();
            }
        }

        void aim(Entity target) {
            player.lookAt(EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
            player.yRotO = player.getYRot();
            player.xRotO = player.getXRot();
            player.setYBodyRot(player.getYRot());
            player.yBodyRotO = player.getYRot();
            LivingComponentAction.getComponent(player).entityAim.setTarget(new ActionTarget(target));
        }

        void aimNothing() {
            LivingComponentAction.getComponent(player).entityAim.setTarget(ActionTarget.EMPTY);
        }

        void close(short key) {
            AbilityInput.keyRelease(key, player);
            power.setPowerType(null);
            player.discard();
        }
    }
}
