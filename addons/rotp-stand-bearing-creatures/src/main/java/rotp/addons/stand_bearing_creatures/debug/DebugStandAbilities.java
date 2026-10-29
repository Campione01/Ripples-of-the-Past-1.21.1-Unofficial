package rotp.addons.stand_bearing_creatures.debug;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.client.standskin.StandSkin;
import rotp.core.client.standskin.StandSkinsLoader;
import rotp.core.core.JojoRegistries;
import rotp.core.mechanics.standdisc.StandDiscItem;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.playerpower.PlayerPowerType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import rotp.addons.stand_bearing_creatures.MobsWithPowersAddon;
import rotp.addons.stand_bearing_creatures.entity.goal.ManualHeldInput;
import rotp.addons.stand_bearing_creatures.util.MobPowerUtil;
import rotp.addons.stand_bearing_creatures.util.RainbowTextUtil;

public final class DebugStandAbilities {
    private static final ResourceLocation STAND_ICON =
            ResourceLocation.fromNamespaceAndPath(
                    "jojo_ripples",
                    "stand_unsummon");
    private static final ResourceLocation POWER_ICON =
            ResourceLocation.fromNamespaceAndPath(
                    "jojo_ripples",
                    "hamon_breath");
    private static final ResourceLocation NO_AI_ICON =
            ResourceLocation.fromNamespaceAndPath(
                    "jojo_ripples",
                    "time_stop");
    private static final ResourceLocation AGGRO_ICON =
            ResourceLocation.fromNamespaceAndPath(
                    "jojo_ripples",
                    "punch");

    private abstract static class DebugAbility extends Ability {
        protected DebugAbility(
                AbilityType<?> type,
                AbilityId id) {
            super(type, id);
            noResolveUnlock();
        }

        protected abstract String label(Power<?> context);

        protected abstract boolean accepts(
                @Nullable LivingEntity target,
                Power<?> context);

        protected abstract void perform(
                Level level,
                LivingEntity user,
                LivingEntity target,
                Power<?> context);

        protected abstract ResourceLocation fallbackSprite();

        @Override
        public Component getName(Power<?> context) {
            return RainbowTextUtil.rainbow(label(context), false);
        }

        @Override
        public ConditionCheck checkSpecificConditions(Power<?> context) {
            return accepts(aimedLiving(context), context)
                    ? ConditionCheck.POSITIVE
                    : ConditionCheck.NEGATIVE;
        }

        @Override
        public void onClick(
                Level level,
                LivingEntity user,
                FriendlyByteBuf extraClientInput) {
            if (level.isClientSide()) {
                return;
            }
            Power<?> context = getUserPower(user);
            LivingEntity target = aimedLiving(context);
            if (target != null && accepts(target, context)) {
                perform(level, user, target, context);
            }
        }

        @Override
        public ResourceLocation getSpriteId(Power<?> context) {
            return fallbackSprite();
        }
    }

    public static final class GiveRandomStand extends DebugAbility {
        public GiveRandomStand(AbilityType<?> type, AbilityId id) {
            super(type, id);
        }

        @Override
        protected String label(Power<?> context) {
            LivingEntity user =
                    context != null ? context.getUser() : null;
            StandInstance fromDisc = heldDiscStand(user);
            Component name = fromDisc != null
                    ? fromDisc.getStandName(
                            user != null
                                    && user.level().isClientSide())
                    : null;
            return name != null
                    ? "Give [" + name.getString() + "] Stand"
                    : "Give Random Stand";
        }

        @Override
        protected boolean accepts(
                @Nullable LivingEntity target,
                Power<?> context) {
            return target != null
                    && MobPowerUtil.isDebugPowerTarget(target)
                    && !MobPowerUtil.hasStand(target);
        }

        @Override
        protected void perform(
                Level level,
                LivingEntity user,
                LivingEntity target,
                Power<?> context) {
            if (!MobPowerUtil.attachDebugPowers(target)) {
                return;
            }
            StandInstance fromDisc = heldDiscStand(user);
            boolean granted = fromDisc != null
                    ? MobPowerUtil.grantStand(
                            target,
                            fromDisc.getStandType(),
                            false)
                    : MobPowerUtil.grantStand(
                            target,
                            MobPowerUtil.randomStand(target),
                            false);
            if (granted) {
                StandPower power = StandPower.get(target);
                if (power != null) {
                    power.setResolveLevel(4);
                }
            }
        }

        @Override
        protected ResourceLocation fallbackSprite() {
            return STAND_ICON;
        }
    }

    public static final class GiveRandomPower extends DebugAbility {
        public GiveRandomPower(AbilityType<?> type, AbilityId id) {
            super(type, id);
            isSubAbility = true;
        }

        @Override
        protected String label(Power<?> context) {
            return "Give Random Power";
        }

        @Override
        protected boolean accepts(
                @Nullable LivingEntity target,
                Power<?> context) {
            return target != null
                    && MobPowerUtil.isDebugPowerTarget(target)
                    && !MobPowerUtil.hasPlayerPower(target);
        }

        @Override
        protected void perform(
                Level level,
                LivingEntity user,
                LivingEntity target,
                Power<?> context) {
            if (!MobPowerUtil.attachDebugPowers(target)) {
                return;
            }
            PlayerPowerType<?> selected =
                    MobPowerUtil.randomPlayerPower(target);
            PlayerPower power = PlayerPower.get(target);
            if (power != null && selected != null) {
                power.setPowerType(selected);
            }
        }

        @Override
        protected ResourceLocation fallbackSprite() {
            return POWER_ICON;
        }
    }

    public static final class RemoveStand extends DebugAbility {
        public RemoveStand(AbilityType<?> type, AbilityId id) {
            super(type, id);
        }

        @Override
        protected String label(Power<?> context) {
            return "Remove Stand";
        }

        @Override
        protected boolean accepts(
                @Nullable LivingEntity target,
                Power<?> context) {
            return target != null && MobPowerUtil.hasStand(target);
        }

        @Override
        protected void perform(
                Level level,
                LivingEntity user,
                LivingEntity target,
                Power<?> context) {
            StandPower power = StandPower.get(target);
            if (power != null && power.hasPower()) {
                StandPowerTransitions.fullReset(
                        power,
                        new StandPowerTransitions.TransitionContext(
                                MobsWithPowersAddon.id(
                                        "debug_remove_stand"),
                                user));
            }
        }

        @Override
        protected ResourceLocation fallbackSprite() {
            return STAND_ICON;
        }
    }

    public static final class RemovePower extends DebugAbility {
        public RemovePower(AbilityType<?> type, AbilityId id) {
            super(type, id);
            isSubAbility = true;
        }

        @Override
        protected String label(Power<?> context) {
            return "Remove Power";
        }

        @Override
        protected boolean accepts(
                @Nullable LivingEntity target,
                Power<?> context) {
            return target != null && MobPowerUtil.hasPlayerPower(target);
        }

        @Override
        protected void perform(
                Level level,
                LivingEntity user,
                LivingEntity target,
                Power<?> context) {
            PlayerPower power = PlayerPower.get(target);
            if (power != null && power.hasPower()) {
                power.setPowerType(null);
            }
        }

        @Override
        protected ResourceLocation fallbackSprite() {
            return STAND_ICON;
        }
    }

    public static final class NoAi extends DebugAbility {
        public NoAi(AbilityType<?> type, AbilityId id) {
            super(type, id);
        }

        @Override
        protected String label(Power<?> context) {
            return "No Ai";
        }

        @Override
        protected boolean accepts(
                @Nullable LivingEntity target,
                Power<?> context) {
            return target instanceof Mob;
        }

        @Override
        protected void perform(
                Level level,
                LivingEntity user,
                LivingEntity target,
                Power<?> context) {
            Mob mob = (Mob) target;
            mob.setNoAi(!mob.isNoAi());
        }

        @Override
        protected ResourceLocation fallbackSprite() {
            return NO_AI_ICON;
        }
    }

    public static final class UseRandomAction extends DebugAbility {
        public UseRandomAction(AbilityType<?> type, AbilityId id) {
            super(type, id);
        }

        @Override
        protected String label(Power<?> context) {
            return "Use Random Action";
        }

        @Override
        protected boolean accepts(
                @Nullable LivingEntity target,
                Power<?> context) {
            return target != null && MobPowerUtil.hasStand(target);
        }

        @Override
        protected void perform(
                Level level,
                LivingEntity user,
                LivingEntity target,
                Power<?> context) {
            StandPower power = StandPower.get(target);
            if (power == null || !power.hasPower()) {
                return;
            }
            // 1.16 listed the actions through the same randomizing call.
            MobPowerUtil.randomizeTimeStopTraining(
                    power, target.getRandom());
            List<Ability> actions = new ArrayList<>(
                    power.updateAvailableMoves()
                            .inMovesetAndCanBeUsed
                            .values());
            // Same pool as the mob AI: no port-only ungated moves (R148).
            actions.removeIf(action ->
                    action == null
                            || action.name().equals(name())
                            || MobPowerUtil.isPortOnlyUngatedMove(action));
            if (actions.isEmpty()) {
                return;
            }
            Ability selected = actions.get(
                    target.getRandom().nextInt(actions.size()));
            // 1.16 UseRandomAction held a hold-to-fire action
            // (setHeldAction) instead of clicking it.
            ManualHeldInput.press(selected, target);
        }

        @Override
        public ResourceLocation getSpriteId(Power<?> context) {
            List<Ability> abilities = JojoRegistries.ABILITY_TYPES_REG
                    .stream()
                    .map(type -> type._defaultAbilityInstance)
                    .filter(ability -> ability != this)
                    .toList();
            if (!abilities.isEmpty()) {
                int index = (int) ((System.currentTimeMillis() / 1000L)
                        % abilities.size());
                try {
                    return abilities.get(index).getSpriteId(context);
                }
                catch (RuntimeException ignored) {
                }
            }
            return fallbackSprite();
        }

        @Override
        protected ResourceLocation fallbackSprite() {
            return POWER_ICON;
        }
    }

    public static final class ToggleSummon extends DebugAbility {
        public ToggleSummon(AbilityType<?> type, AbilityId id) {
            super(type, id);
        }

        @Override
        protected String label(Power<?> context) {
            return "Toggle Summon";
        }

        @Override
        protected boolean accepts(
                @Nullable LivingEntity target,
                Power<?> context) {
            return target != null && MobPowerUtil.hasStand(target);
        }

        @Override
        protected void perform(
                Level level,
                LivingEntity user,
                LivingEntity target,
                Power<?> context) {
            StandPower power = StandPower.get(target);
            if (power != null && power.getPowerType() != null) {
                power.getPowerType().onUserSummonCommand(target, power);
            }
        }

        @Override
        public void renderAbilityIcon(
                Power<?> context,
                GuiGraphics guiGraphics,
                TextureAtlasSprite fallback,
                float x,
                float y,
                int color) {
            StandPower targetPower =
                    StandPower.get(aimedLiving(context));
            StandInstance stand = targetPower != null
                    ? targetPower.getStandInstance().orElse(null)
                    : null;
            StandSkin skin = stand != null
                    && StandSkinsLoader.getInstance() != null
                    ? StandSkinsLoader.getInstance().getSkin(stand)
                    : null;
            if (skin != null) {
                skin.getStandIcon().render(
                        guiGraphics.pose(),
                        x,
                        y,
                        16.0F,
                        16.0F,
                        color);
                return;
            }
            super.renderAbilityIcon(
                    context,
                    guiGraphics,
                    fallback,
                    x,
                    y,
                    color);
        }

        @Override
        protected ResourceLocation fallbackSprite() {
            return STAND_ICON;
        }
    }

    public static final class AggroOneToTwo extends DebugAbility {
        public AggroOneToTwo(AbilityType<?> type, AbilityId id) {
            super(type, id);
        }

        @Override
        protected String label(Power<?> context) {
            LivingEntity user =
                    context != null ? context.getUser() : null;
            Mob first = MobPowerUtil.getDebugAggroFirst(user);
            return first != null
                    ? "Aggro [" + first.getName().getString()
                            + "] To Two Entity"
                    : "Aggro One To Two Entity";
        }

        @Override
        protected boolean accepts(
                @Nullable LivingEntity target,
                Power<?> context) {
            LivingEntity user =
                    context != null ? context.getUser() : null;
            return target != null
                    && (MobPowerUtil.getDebugAggroFirst(user) != null
                            || target instanceof Mob);
        }

        @Override
        protected void perform(
                Level level,
                LivingEntity user,
                LivingEntity target,
                Power<?> context) {
            Mob first = MobPowerUtil.getDebugAggroFirst(user);
            if (first == null) {
                MobPowerUtil.setDebugAggroFirst(user, (Mob) target);
                return;
            }
            if (first.isAlive()) {
                first.setTarget(target);
            }
            MobPowerUtil.setDebugAggroFirst(user, null);
        }

        @Override
        protected ResourceLocation fallbackSprite() {
            return AGGRO_ICON;
        }
    }

    @Nullable
    static LivingEntity aimedLiving(@Nullable Power<?> context) {
        LivingEntity user = context != null ? context.getUser() : null;
        if (user == null) {
            return null;
        }
        var aim = LivingComponentAction.getAim(user);
        ActionTarget target = aim != null
                ? aim.getTarget().resolveEntityId(user.level())
                : ActionTarget.EMPTY;
        return target.getMainEntity() instanceof LivingEntity living
                ? living
                : null;
    }

    @Nullable
    private static StandInstance heldDiscStand(
            @Nullable LivingEntity user) {
        if (user == null) {
            return null;
        }
        for (ItemStack stack : List.of(
                user.getMainHandItem(),
                user.getOffhandItem())) {
            if (stack.getItem() instanceof StandDiscItem) {
                StandInstance stand =
                        StandDiscItem.getStandInstance(stack);
                if (stand != null) {
                    return stand;
                }
            }
        }
        return null;
    }

    private DebugStandAbilities() {}
}
