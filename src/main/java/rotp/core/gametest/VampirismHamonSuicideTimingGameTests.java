package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.vampirism.VampirismPowerType;
import rotp.core.impl.powers.vampirism.abilities.VampirismHamonSuicideAbility;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;

/**
 * 1.16 VampirismHamonSuicide (holdToFire 100) under PowerBaseImpl.tickHeldAction: holdTick runs with the one-based
 * ticksHeld 1..99 (self damage on ticksHeld % 10 == 5, Hamon Spread on 30) and the action fires inside the 100th tick.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VampirismHamonSuicideTimingGameTests {
    private static final short KEY = 72;

    private VampirismHamonSuicideTimingGameTests() {}

    private record Hit(int heldTick, float amount) {}

    @GameTest(template = "empty", batch = "vampire_hamon_suicide_timing")
    public static void suicidePulsesSpreadsAndFiresOnDonorHeldTicks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos template = helper.absolutePos(BlockPos.ZERO);
        ChunkPos chunk = new ChunkPos(template);
        Vec3 origin = new Vec3(chunk.getMinBlockX() + 8.5D, template.getY() + 40.0D, chunk.getMinBlockZ() + 8.5D);
        helper.assertTrue(level.getEntities((Entity) null, new AABB(origin, origin).inflate(8.0D)).isEmpty(),
                "SUICIDE-TIMING premise: scene contains a foreign entity");

        Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
        List<Hit> hits = new ArrayList<>();
        int[] heldTick = { 0 };
        Consumer<LivingIncomingDamageEvent> incoming = event -> {
            if (event.getEntity() == user && event.getSource().is(ModDamageTypes.HAMON)
                    && event.getSource().getEntity() == user) {
                hits.add(new Hit(heldTick[0], event.getOriginalAmount()));
            }
        };
        boolean pressed = false;
        try {
            user.setNoGravity(true);
            user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(user), "SUICIDE-TIMING premise: user was not added");
            // Enough health to live through the ten pulses, so the hold reaches its 100th tick.
            user.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024.0D);
            user.setHealth(user.getMaxHealth());
            PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
            power.setPowerType(ModPlayerPowers.VAMPIRISM.get());
            VampirismData data = PlayerPower.getPowerData(user, ModPlayerPowers.VAMPIRISM).orElseThrow();
            // No kept Hamon strength: the action fires without its explosion.
            data.setVampireHamonUser(true, null);
            Ability ability = power.getAbility("vampirism_hamon_suicide");
            helper.assertTrue(ability instanceof VampirismHamonSuicideAbility
                            && ability.abilityType == VampirismPowerType.VAMPIRE_HAMON_SUICIDE.get()
                            && data.isVampireHamonUser() && data.getHamonStrengthLevel() == 0
                            && user.getHealth() >= 1000.0F && HamonAbilityHelpers.hamonDamageAmount(user, 4.0F) > 0.0F,
                    "SUICIDE-TIMING premise: not a healthy former Hamon user with the registered Suicide");

            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingIncomingDamageEvent.class, incoming);
            LivingComponentAction component = LivingComponentAction.getComponent(user);
            AvailableAbilities available = new AvailableAbilities();
            available.update(power, power.getMoveset());
            helper.assertTrue(AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), user,
                    InputMethod.HOLD), "SUICIDE-TIMING premise: registered HOLD was not admitted");
            HeldInputEntry held = AbilityInput.keyPress(KEY, ability, user, null, InputMethod.HOLD,
                    0.0F, BufferingState.clickOnly(), ability.getAbilityId());
            pressed = true;
            EntityActionInstance action = component.getAction();
            helper.assertTrue(held != null && action != null && held.action == action
                            && action.getPhase() == ActionPhase.WINDUP && action.getCurPhaseLength() == 100.0F
                            && action.getPhaseTick() == 0.0F && hits.isEmpty(),
                    "SUICIDE-TIMING premise: registered HOLD did not install a fresh 100 tick WINDUP");

            int spreadTick = -1;
            int overTick = -1;
            for (int tick = 1; tick <= 104; tick++) {
                heldTick[0] = tick;
                // A naturally ticking player has shed its hurt cooldown between two pulses.
                user.invulnerableTime = 0;
                component.tick();
                if (spreadTick < 0 && user.hasEffect(ModStatusEffects.HAMON_SPREAD)) spreadTick = tick;
                if (overTick < 0 && component.getAction() == null) overTick = tick;
            }
            helper.assertTrue(!hits.isEmpty(), "SUICIDE-TIMING: the held Suicide never hurt its user");
            float pulseAmount = hits.stream().map(Hit::amount).min(Float::compare).orElseThrow();
            List<Integer> pulses = hits.stream().filter(hit -> hit.amount < pulseAmount * 25.0F).map(Hit::heldTick).toList();
            List<Integer> fires = hits.stream().filter(hit -> hit.amount >= pulseAmount * 25.0F).map(Hit::heldTick).toList();
            String observed = "pulses=" + pulses + " spread=" + spreadTick + " fire=" + fires + " over=" + overTick
                    + " pulseAmount=" + pulseAmount;
            JojoMod.LOGGER.info("SUICIDE-TIMING {}", observed);
            helper.assertTrue(pulses.equals(List.of(5, 15, 25, 35, 45, 55, 65, 75, 85, 95)) && spreadTick == 30
                            && fires.equals(List.of(100)) && overTick == 100,
                    "Suicide held ticks differ from 1.16 (pulses 5..95, spread 30, fire and end 100): " + observed);
        }
        finally {
            NeoForge.EVENT_BUS.unregister(incoming);
            try {
                if (pressed) AbilityInput.keyRelease(KEY, user);
                LivingComponentAction.getComponent(user).setAction(null, user, SyncType.NO_SYNC);
            }
            finally {
                user.discard();
            }
        }
        helper.succeed();
    }
}
