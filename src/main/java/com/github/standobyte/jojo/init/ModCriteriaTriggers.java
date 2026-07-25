package com.github.standobyte.jojo.init;

import java.util.Optional;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.mrpresident.MrPresidentRoomStateOwner;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCriteriaTriggers {
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGER_TYPES = DeferredRegister.create(Registries.TRIGGER_TYPE, JojoMod.MOD_ID);

    public static final DeferredHolder<CriterionTrigger<?>, NoConditionsTrigger> AFK = TRIGGER_TYPES.register("afk", NoConditionsTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, GetPowerTrigger> GET_POWER = TRIGGER_TYPES.register("get_power", GetPowerTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, NoConditionsTrigger> SOUL_ASCENSION = TRIGGER_TYPES.register("soul_ascension", NoConditionsTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, NoConditionsTrigger> ABANDON_HAMON = TRIGGER_TYPES.register("abandon_hamon", NoConditionsTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, NoConditionsTrigger> SUMMON_STAND = TRIGGER_TYPES.register("summon_stand", NoConditionsTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, RpsGameTrigger> RPS_GAME = TRIGGER_TYPES.register("rps_game", RpsGameTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, LastHamonTrigger> LAST_HAMON = TRIGGER_TYPES.register("last_hamon", LastHamonTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, HamonStatsTrigger> HAMON_STATS = TRIGGER_TYPES.register("hamon_stats", HamonStatsTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, HamonChargeKillTrigger> HAMON_CHARGE_KILL = TRIGGER_TYPES.register("hamon_charge_kill", HamonChargeKillTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, PeopleDrainedTrigger> VAMPIRE_PEOPLE_DRAINED = TRIGGER_TYPES.register("vampire_people_drained", PeopleDrainedTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, NoConditionsTrigger> VAMPIRE_HAMON_DAMAGE_SCARF = TRIGGER_TYPES.register("vampire_hamon_damage_scarf", NoConditionsTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, StandArrowHitTrigger> STAND_ARROW_HIT = TRIGGER_TYPES.register("stand_arrow_hit", StandArrowHitTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, MetModdedMobTrigger> MET_MODDED_MOB = TRIGGER_TYPES.register("met_modded_mob", MetModdedMobTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, NoConditionsTrigger> COCO_JUMBO_KEY = TRIGGER_TYPES.register("coco_jumbo_key", NoConditionsTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, NoConditionsTrigger> MR_PRESIDENT_ROOM_ENTERED = TRIGGER_TYPES.register("mr_president_room_entered", NoConditionsTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, NoConditionsTrigger> MR_PRESIDENT_ROOM_WALLS = TRIGGER_TYPES.register("mr_president_walls", NoConditionsTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, PlayerKilledEntityTrigger> PLAYER_KILLED_ENTITY = TRIGGER_TYPES.register("player_killed_entity", PlayerKilledEntityTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, PlayerKilledEntityTrigger> KILL_PILLARMAN = TRIGGER_TYPES.register("kill_pillarman", PlayerKilledEntityTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, TempleMapTrigger> TEMPLE_MAP = TRIGGER_TYPES.register("temple_map", TempleMapTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, NoConditionsTrigger> EVOLVE_PILLARMAN = TRIGGER_TYPES.register("pillarman_evolve", NoConditionsTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, NoConditionsTrigger> EVOLVE_PILLARMAN_AJA = TRIGGER_TYPES.register("pillarman_evolve_aja", NoConditionsTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, NoConditionsTrigger> MASK_SUICIDE = TRIGGER_TYPES.register("mask_suicide", NoConditionsTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, StoneMaskDestroyedTrigger> STONE_MASK_DESTROYED = TRIGGER_TYPES.register("destroy_stone_mask", StoneMaskDestroyedTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, NoConditionsTrigger> PILLARMAN_WIND_MODE = TRIGGER_TYPES.register("pillarman_wind_mode", NoConditionsTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, NoConditionsTrigger> PILLARMAN_HEAT_MODE = TRIGGER_TYPES.register("pillarman_heat_mode", NoConditionsTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, NoConditionsTrigger> PILLARMAN_LIGHT_MODE = TRIGGER_TYPES.register("pillarman_light_mode", NoConditionsTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, NoConditionsTrigger> COFFIN_SLEEP = TRIGGER_TYPES.register("coffin_sleep", NoConditionsTrigger::new);

    private ModCriteriaTriggers() {}

    public enum PowerClassification {
        STAND,
        NON_STAND;

        public static final Codec<PowerClassification> CODEC = Codec.STRING.xmap(
                name -> valueOf(name.toUpperCase()),
                value -> value.name().toLowerCase());
    }

    public record PowerCondition(Optional<PowerClassification> classification, Optional<ResourceLocation> type,
            Optional<MinMaxBounds.Ints> pillarman_stage) {
        public static final Codec<PowerCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                PowerClassification.CODEC.optionalFieldOf("classification").forGetter(PowerCondition::classification),
                ResourceLocation.CODEC.optionalFieldOf("type").forGetter(PowerCondition::type),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("pillarman_stage").forGetter(PowerCondition::pillarman_stage)
        ).apply(instance, PowerCondition::new));

        public boolean matches(Power<?> power) {
            if (power == null || !power.hasPower() || power.getPowerType() == null) {
                return false;
            }
            boolean classificationMatches = classification.map(value -> switch (value) {
                case STAND -> power.getPowerClass() == PowerClass.STAND;
                case NON_STAND -> power.getPowerClass() == PowerClass.PLAYER_POWER;
            }).orElse(true);
            boolean typeMatches = type.map(value -> value.equals(power.getPowerType().getId())).orElse(true);
            boolean stageMatches = pillarman_stage.map(stage -> power instanceof PlayerPower playerPower
                    && playerPower.getCurTypeData(ModPlayerPowers.PILLAR_MAN)
                            .map(pillarman -> stage.matches(pillarman.getEvolutionStage()))
                            .orElse(false)).orElse(true);
            return classificationMatches && typeMatches && stageMatches;
        }

        public boolean matches(LivingEntity user) {
            return classification.map(value -> switch (value) {
                case STAND -> matches(StandPower.get(user));
                case NON_STAND -> matches(PlayerPower.get(user));
            }).orElseGet(() -> matches(StandPower.get(user)) || matches(PlayerPower.get(user)));
        }
    }

    public static final class NoConditionsTrigger extends SimpleCriterionTrigger<NoConditionsTrigger.Instance> {
        @Override
        public Codec<Instance> codec() {
            return Instance.CODEC;
        }

        public void trigger(ServerPlayer player) {
            trigger(player, instance -> true);
        }

        public record Instance(Optional<ContextAwarePredicate> player) implements SimpleInstance {
            public static final Codec<Instance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player)
            ).apply(instance, Instance::new));
        }
    }

    public static final class StoneMaskDestroyedTrigger extends SimpleCriterionTrigger<StoneMaskDestroyedTrigger.Instance> {
        @Override
        public Codec<Instance> codec() {
            return Instance.CODEC;
        }

        public void trigger(ServerPlayer player, Block block, ItemStack itemUsed, ItemStack stoneMaskItem) {
            trigger(player, instance -> instance.matches(block, itemUsed, stoneMaskItem));
        }

        public record Instance(Optional<ContextAwarePredicate> player, Optional<ResourceLocation> block, Optional<ItemPredicate> item_used, Optional<ItemPredicate> stone_mask_item) implements SimpleInstance {
            public static final Codec<Instance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    ResourceLocation.CODEC.optionalFieldOf("block").forGetter(Instance::block),
                    ItemPredicate.CODEC.optionalFieldOf("item_used").forGetter(Instance::item_used),
                    ItemPredicate.CODEC.optionalFieldOf("stone_mask_item").forGetter(Instance::stone_mask_item)
            ).apply(instance, Instance::new));

            public boolean matches(Block block, ItemStack itemUsed, ItemStack stoneMaskItem) {
                return this.block.map(value -> value.equals(BuiltInRegistries.BLOCK.getKey(block))).orElse(true)
                        && item_used.map(predicate -> predicate.test(itemUsed)).orElse(true)
                        && stone_mask_item.map(predicate -> predicate.test(stoneMaskItem)).orElse(true);
            }
        }
    }

    public static final class GetPowerTrigger extends SimpleCriterionTrigger<GetPowerTrigger.Instance> {
        @Override
        public Codec<Instance> codec() {
            return Instance.CODEC;
        }

        public void trigger(ServerPlayer player, Power<?> power) {
            trigger(player, instance -> instance.matches(power));
        }

        public record Instance(Optional<ContextAwarePredicate> player, PowerCondition jojopower) implements SimpleInstance {
            public static final Codec<Instance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    PowerCondition.CODEC.fieldOf("jojopower").forGetter(Instance::jojopower)
            ).apply(instance, Instance::new));

            public boolean matches(Power<?> power) {
                return jojopower.matches(power);
            }
        }
    }

    public static final class HamonStatsTrigger extends SimpleCriterionTrigger<HamonStatsTrigger.Instance> {
        @Override
        public Codec<Instance> codec() {
            return Instance.CODEC;
        }

        public void trigger(ServerPlayer player, HamonData hamon) {
            trigger(player, instance -> instance.matches(hamon));
        }

        public record HamonStatsCondition(MinMaxBounds.Ints strength_level, MinMaxBounds.Ints control_level,
                Optional<Float> breathing_level, Optional<Integer> breathing_training_level) {
            public static final Codec<HamonStatsCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    MinMaxBounds.Ints.CODEC.optionalFieldOf("strength_level", MinMaxBounds.Ints.ANY).forGetter(HamonStatsCondition::strength_level),
                    MinMaxBounds.Ints.CODEC.optionalFieldOf("control_level", MinMaxBounds.Ints.ANY).forGetter(HamonStatsCondition::control_level),
                    Codec.FLOAT.optionalFieldOf("breathing_level").forGetter(HamonStatsCondition::breathing_level),
                    Codec.INT.optionalFieldOf("breathing_training_level").forGetter(HamonStatsCondition::breathing_training_level)
            ).apply(instance, HamonStatsCondition::new));

            public boolean matches(HamonData hamon) {
                return strength_level.matches(hamon.getHamonStrengthLevel())
                        && control_level.matches(hamon.getHamonControlLevel())
                        && breathing_level.map(value -> hamon.getBreathingLevel() >= value).orElse(true)
                        && breathing_training_level.map(value -> hamon.getTrainingTicks() >= value * 1200).orElse(true);
            }
        }

        public record Instance(Optional<ContextAwarePredicate> player, HamonStatsCondition hamon_stats) implements SimpleInstance {
            public static final Codec<Instance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    HamonStatsCondition.CODEC.fieldOf("hamon_stats").forGetter(Instance::hamon_stats)
            ).apply(instance, Instance::new));

            public boolean matches(HamonData hamon) {
                return hamon_stats.matches(hamon);
            }
        }
    }

    public static final class LastHamonTrigger extends SimpleCriterionTrigger<LastHamonTrigger.Instance> {
        @Override
        public Codec<Instance> codec() {
            return Instance.CODEC;
        }

        public void trigger(ServerPlayer player, @Nullable Entity hamonSource) {
            trigger(player, instance -> instance.matches(player, hamonSource));
        }

        public record Instance(Optional<ContextAwarePredicate> player,
                Optional<ContextAwarePredicate> source) implements SimpleInstance {
            public static final Codec<Instance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("source").forGetter(Instance::source)
            ).apply(instance, Instance::new));

            public boolean matches(ServerPlayer player, @Nullable Entity hamonSource) {
                return source.map(predicate -> hamonSource != null
                        && predicate.matches(EntityPredicate.createContext(player, hamonSource))).orElse(true);
            }
        }
    }

    public static final class HamonChargeKillTrigger extends SimpleCriterionTrigger<HamonChargeKillTrigger.Instance> {
        @Override
        public Codec<Instance> codec() {
            return Instance.CODEC;
        }

        public void trigger(ServerPlayer player, LivingEntity killed, @Nullable Entity chargedEntity) {
            trigger(player, instance -> instance.matches(player, killed, chargedEntity));
        }

        public record Instance(Optional<ContextAwarePredicate> player,
                Optional<ContextAwarePredicate> killed_entity, Optional<PowerCondition> killed_power,
                Optional<ContextAwarePredicate> charged_entity) implements SimpleInstance {
            public static final Codec<Instance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("killed_entity").forGetter(Instance::killed_entity),
                    PowerCondition.CODEC.optionalFieldOf("killed_power").forGetter(Instance::killed_power),
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("charged_entity").forGetter(Instance::charged_entity)
            ).apply(instance, Instance::new));

            public boolean matches(ServerPlayer player, LivingEntity killed, @Nullable Entity chargedEntity) {
                if (killed_entity.isPresent()
                        && !killed_entity.get().matches(EntityPredicate.createContext(player, killed))) {
                    return false;
                }
                if (charged_entity.isPresent() && (chargedEntity == null
                        || !charged_entity.get().matches(EntityPredicate.createContext(player, chargedEntity)))) {
                    return false;
                }
                return killed_power.map(condition -> condition.matches(killed)).orElse(true);
            }
        }
    }

    public static final class PeopleDrainedTrigger extends SimpleCriterionTrigger<PeopleDrainedTrigger.Instance> {
        @Override
        public Codec<Instance> codec() {
            return Instance.CODEC;
        }

        public void trigger(ServerPlayer player, int peopleDrained, int zombiesCreated) {
            trigger(player, instance -> instance.matches(peopleDrained, zombiesCreated));
        }

        public record Instance(Optional<ContextAwarePredicate> player, MinMaxBounds.Ints people_drained,
                MinMaxBounds.Ints zombies_created) implements SimpleInstance {
            public static final Codec<Instance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    MinMaxBounds.Ints.CODEC.optionalFieldOf("people_drained", MinMaxBounds.Ints.ANY).forGetter(Instance::people_drained),
                    MinMaxBounds.Ints.CODEC.optionalFieldOf("zombies_created", MinMaxBounds.Ints.ANY).forGetter(Instance::zombies_created)
            ).apply(instance, Instance::new));

            public boolean matches(int peopleDrained, int zombiesCreated) {
                return people_drained.matches(peopleDrained) && zombies_created.matches(zombiesCreated);
            }
        }
    }

    public static final class RpsGameTrigger extends SimpleCriterionTrigger<RpsGameTrigger.Instance> {
        @Override
        public Codec<Instance> codec() {
            return Instance.CODEC;
        }

        public void trigger(ServerPlayer player, boolean wonGame, boolean standTaken) {
            trigger(player, instance -> instance.matches(wonGame, standTaken));
        }

        public record RpsGameCondition(Optional<Boolean> won_game, Optional<Boolean> stand_taken) {
            public static final Codec<RpsGameCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.BOOL.optionalFieldOf("won_game").forGetter(RpsGameCondition::won_game),
                    Codec.BOOL.optionalFieldOf("stand_taken").forGetter(RpsGameCondition::stand_taken)
            ).apply(instance, RpsGameCondition::new));

            public boolean matches(boolean wonGame, boolean standTaken) {
                return won_game.map(value -> value == wonGame).orElse(true)
                        && stand_taken.map(value -> value == standTaken).orElse(true);
            }
        }

        public record Instance(Optional<ContextAwarePredicate> player, RpsGameCondition rps_game) implements SimpleInstance {
            public static final Codec<Instance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    RpsGameCondition.CODEC.fieldOf("rps_game").forGetter(Instance::rps_game)
            ).apply(instance, Instance::new));

            public boolean matches(boolean wonGame, boolean standTaken) {
                return rps_game.matches(wonGame, standTaken);
            }
        }
    }

    public static final class StandArrowHitTrigger extends SimpleCriterionTrigger<StandArrowHitTrigger.Instance> {
        @Override
        public Codec<Instance> codec() {
            return Instance.CODEC;
        }

        public void trigger(ServerPlayer player, LivingEntity target, boolean gaveStand, boolean shotSelf) {
            StandPower targetStand = StandPower.get(target);
            trigger(player, instance -> instance.matches(player, target, gaveStand, targetStand, shotSelf));
        }

        public record ArrowHitCondition(Optional<Boolean> gave_stand, Optional<PowerCondition> target_stand, Optional<Boolean> shot_self) {
            public static final Codec<ArrowHitCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.BOOL.optionalFieldOf("gave_stand").forGetter(ArrowHitCondition::gave_stand),
                    PowerCondition.CODEC.optionalFieldOf("target_stand").forGetter(ArrowHitCondition::target_stand),
                    Codec.BOOL.optionalFieldOf("shot_self").forGetter(ArrowHitCondition::shot_self)
            ).apply(instance, ArrowHitCondition::new));

            public boolean matches(boolean gaveStand, StandPower targetStand, boolean shotSelf) {
                return gave_stand.map(value -> value == gaveStand).orElse(true)
                        && target_stand.map(condition -> condition.matches(targetStand)).orElse(true)
                        && shot_self.map(value -> value == shotSelf).orElse(true);
            }
        }

        public record Instance(Optional<ContextAwarePredicate> player, Optional<ContextAwarePredicate> target, ArrowHitCondition arrow_hit) implements SimpleInstance {
            public static final Codec<Instance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("target").forGetter(Instance::target),
                    ArrowHitCondition.CODEC.fieldOf("arrow_hit").forGetter(Instance::arrow_hit)
            ).apply(instance, Instance::new));

            public boolean matches(ServerPlayer player, LivingEntity targetEntity, boolean gaveStand, StandPower targetStand, boolean shotSelf) {
                if (target.isPresent() && !target.get().matches(EntityPredicate.createContext(player, targetEntity))) {
                    return false;
                }
                return arrow_hit.matches(gaveStand, targetStand, shotSelf);
            }
        }
    }

    public static final class PlayerKilledEntityTrigger extends SimpleCriterionTrigger<PlayerKilledEntityTrigger.Instance> {
        @Override
        public Codec<Instance> codec() {
            return Instance.CODEC;
        }

        public void trigger(ServerPlayer player, LivingEntity killer, LivingEntity killed) {
            trigger(player, instance -> instance.matches(killer, killed));
        }

        public record Instance(Optional<ContextAwarePredicate> player, Optional<PowerCondition> power, Optional<PowerCondition> killed_power) implements SimpleInstance {
            public static final Codec<Instance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    PowerCondition.CODEC.optionalFieldOf("power").forGetter(Instance::power),
                    PowerCondition.CODEC.optionalFieldOf("killed_power").forGetter(Instance::killed_power)
            ).apply(instance, Instance::new));

            public boolean matches(LivingEntity killer, LivingEntity killed) {
                return power.map(condition -> condition.matches(killer)).orElse(true)
                        && killed_power.map(condition -> condition.matches(killed)).orElse(true);
            }
        }
    }

    public static final class TempleMapTrigger extends SimpleCriterionTrigger<TempleMapTrigger.Instance> {
        @Override
        public Codec<Instance> codec() {
            return Instance.CODEC;
        }

        public void trigger(ServerPlayer player, String templeMap) {
            trigger(player, instance -> instance.matches(templeMap));
        }

        public record TempleMapCondition(Optional<String> temple_map) {
            public static final Codec<TempleMapCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.optionalFieldOf("temple_map").forGetter(TempleMapCondition::temple_map)
            ).apply(instance, TempleMapCondition::new));

            public boolean matches(String templeMap) {
                return temple_map.map(value -> value.equals(templeMap)).orElse(true);
            }
        }

        public record Instance(Optional<ContextAwarePredicate> player, TempleMapCondition temple_map) implements SimpleInstance {
            public static final Codec<Instance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    TempleMapCondition.CODEC.fieldOf("temple_map").forGetter(Instance::temple_map)
            ).apply(instance, Instance::new));

            public boolean matches(String templeMap) {
                return temple_map.matches(templeMap);
            }
        }
    }

    public static final class MetModdedMobTrigger extends SimpleCriterionTrigger<MetModdedMobTrigger.Instance> {
        @Override
        public Codec<Instance> codec() {
            return Instance.CODEC;
        }

        public void trigger(ServerPlayer player, Entity entity) {
            trigger(player, instance -> instance.matches(player, entity));
        }

        public record Instance(Optional<ContextAwarePredicate> player, Optional<ContextAwarePredicate> entity) implements SimpleInstance {
            public static final Codec<Instance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("entity").forGetter(Instance::entity)
            ).apply(instance, Instance::new));

            public boolean matches(ServerPlayer player, Entity metEntity) {
                return entity.map(predicate -> predicate.matches(EntityPredicate.createContext(player, metEntity))).orElse(true);
            }
        }
    }

    public static void triggerGetPower(ServerPlayer player, Power<?> power) {
        GET_POWER.get().trigger(player, power);
    }

    public static void triggerSoulAscension(ServerPlayer player) {
        SOUL_ASCENSION.get().trigger(player);
    }

    public static void triggerSummonStand(ServerPlayer player) {
        SUMMON_STAND.get().trigger(player);
    }

    public static void triggerRpsGame(ServerPlayer player, boolean wonGame, boolean standTaken) {
        RPS_GAME.get().trigger(player, wonGame, standTaken);
    }

    public static void triggerAbandonHamon(ServerPlayer player) {
        ABANDON_HAMON.get().trigger(player);
    }

    public static void triggerAfk(ServerPlayer player) {
        AFK.get().trigger(player);
    }

    public static void triggerHamonStats(ServerPlayer player, HamonData hamon) {
        HAMON_STATS.get().trigger(player, hamon);
    }

    public static void triggerLastHamon(ServerPlayer player, Entity hamonSource) {
        LAST_HAMON.get().trigger(player, hamonSource);
    }

    public static void triggerHamonChargeKill(ServerPlayer player, LivingEntity killed, @Nullable Entity chargedEntity) {
        HAMON_CHARGE_KILL.get().trigger(player, killed, chargedEntity);
    }

    public static void triggerVampirePeopleDrained(ServerPlayer player, int peopleDrained, int zombiesCreated) {
        VAMPIRE_PEOPLE_DRAINED.get().trigger(player, peopleDrained, zombiesCreated);
    }

    public static void triggerVampireHamonDamageScarf(ServerPlayer player) {
        VAMPIRE_HAMON_DAMAGE_SCARF.get().trigger(player);
    }

    public static void triggerGetPowerIfPresent(ServerPlayer player) {
        StandPower stand = StandPower.get(player);
        if (stand != null && stand.hasPower()) {
            triggerGetPower(player, stand);
            return;
        }
        PlayerPower power = PlayerPower.get(player);
        if (power != null && power.hasPower()) {
            triggerGetPower(player, power);
        }
    }

    public static void triggerStandArrowHit(ServerPlayer player, LivingEntity target, boolean gaveStand, boolean shotSelf) {
        STAND_ARROW_HIT.get().trigger(player, target, gaveStand, shotSelf);
    }

    public static void triggerMetModdedMob(ServerPlayer player, Entity entity) {
        MET_MODDED_MOB.get().trigger(player, entity);
    }

    public static void triggerCocoJumboKey(ServerPlayer player) {
        COCO_JUMBO_KEY.get().trigger(player);
    }

    public static void triggerMrPresidentRoomEntered(ServerPlayer player) {
        MR_PRESIDENT_ROOM_ENTERED.get().trigger(player);
    }

    public static void triggerMrPresidentRoomWalls(ServerPlayer player) {
        MR_PRESIDENT_ROOM_WALLS.get().trigger(player);
    }

    public static void triggerPlayerKilledEntity(ServerPlayer player, LivingEntity killer, LivingEntity killed) {
        PLAYER_KILLED_ENTITY.get().trigger(player, killer, killed);
        KILL_PILLARMAN.get().trigger(player, killer, killed);
    }

    public static void triggerTempleMap(ServerPlayer player, String templeMap) {
        TEMPLE_MAP.get().trigger(player, templeMap);
    }

    public static void triggerPillarmanEvolve(ServerPlayer player) {
        EVOLVE_PILLARMAN.get().trigger(player);
    }

    public static void triggerPillarmanEvolveAja(ServerPlayer player) {
        EVOLVE_PILLARMAN_AJA.get().trigger(player);
    }

    public static void triggerMaskSuicide(ServerPlayer player) {
        MASK_SUICIDE.get().trigger(player);
    }

    public static void triggerStoneMaskDestroyed(ServerPlayer player, Block block, ItemStack itemUsed, ItemStack stoneMaskItem) {
        STONE_MASK_DESTROYED.get().trigger(player, block, itemUsed, stoneMaskItem);
    }

    public static void triggerPillarmanWindMode(ServerPlayer player) {
        PILLARMAN_WIND_MODE.get().trigger(player);
    }

    public static void triggerPillarmanHeatMode(ServerPlayer player) {
        PILLARMAN_HEAT_MODE.get().trigger(player);
    }

    public static void triggerPillarmanLightMode(ServerPlayer player) {
        PILLARMAN_LIGHT_MODE.get().trigger(player);
    }

    public static void triggerCoffinSleep(ServerPlayer player) {
        COFFIN_SLEEP.get().trigger(player);
    }

    @EventBusSubscriber(modid = JojoMod.MOD_ID)
    public static final class TriggerHooks {
        private static final long AFK_TRIGGER_INTERVAL_MS = 30_000L;

        @SubscribeEvent
        public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                PlayerPower.getPowerData(player, ModPlayerPowers.HAMON)
                        .ifPresent(hamon -> hamon.checkHamonMastery(player));
            }
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (event.getEntity().level().isClientSide()) {
                return;
            }
            if (event.getEntity() instanceof ServerPlayer player) {
                long idleMs = net.minecraft.Util.getMillis() - player.getLastActionTime();
                if (idleMs >= AFK_TRIGGER_INTERVAL_MS) {
                    ModCriteriaTriggers.triggerAfk(player);
                }
            }
        }

        @SubscribeEvent
        public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                MrPresidentRoomStateOwner.get(player.server).checkRoomWallsAdvancement(player);
            }
        }

        @SubscribeEvent
        public static void onLivingDeath(LivingDeathEvent event) {
            LivingEntity killed = event.getEntity();
            if (killed.level().isClientSide()) {
                return;
            }
            Entity killer = event.getSource().getEntity();
            if (killer instanceof StandEntity killerStand && killerStand.getUser() != null) {
                killer = killerStand.getUser();
            }
            if (killed.is(killer)) {
                return;
            }
            if (killer instanceof ServerPlayer player) {
                triggerPlayerKilledEntity(player, player, killed);
            }
        }
    }
}
