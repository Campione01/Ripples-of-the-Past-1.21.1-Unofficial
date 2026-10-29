package rotp.addons.ultimate_lifeform.power;

import javax.annotation.Nullable;

import rotp.core.impl.powers.pillarman.PillarmanMode;

public enum UltimateLifeformSpec {
    HEAVY_PUNCH("heavy_punch", 400, 0, 0, 0, 0, false, null),
    HAMON_OVERDRIVE("hamon_overdrive", 0, 0, 125, 0, 0, false, null),
    HAMON_OVERDRIVE_BEAT("hamon_overdrive_beat", 0, 0, 375, 0, 0, false, null),
    STONE_FORM("stone_form", 0, 0, 0, 0, 40, true, null),
    ENHANCED_SENSES("enhanced_senses", 0, 0.05F, 0, 0, 0, true, null),
    TENTACLES("tentacles", 300, 0, 0, 80, 0, false, null),
    EVASION("evasion", 0, 15, 0, 50, 0, true, null),
    ULTIMATE_AGILITY("ultimate_agility", 0, 1, 0, 0, 0, true, null),
    HORN_ATTACK("horn_attack", 60, 0, 0, 60, 0, false, null),
    BLADE_SLASH("blade_slash", 600, 0, 0, 0, 0, false, PillarmanMode.LIGHT),
    BLADE_DASH_ATTACK("blade_dash_attack", 800, 0, 0, 0, 10, true, PillarmanMode.LIGHT),
    BLADE_BARRAGE("blade_barrage", 0, 100, 0, 0, 0, true, PillarmanMode.LIGHT),
    LIGHT_FLASH("light_flash", 500, 0, 0, 80, 40, true, PillarmanMode.LIGHT),
    LIGHT_FLASH_DECOY("light_flash_decoy", 800, 0, 0, 120, 50, true, PillarmanMode.LIGHT),
    SMALL_SANDSTORM("small_sandstorm", 20, 0, 0, 10, 0, false, PillarmanMode.WIND),
    ERRATIC_BLAZE_KING("erratic_blaze_king", 400, 0, 0, 0, 0, false, PillarmanMode.HEAT),
    GIANT_CARTHWHEEL_PRISON("giant_carthwheel_prison", 2500, 0, 0, 100, 30, true, PillarmanMode.HEAT),
    SELF_DETONATION("self_detonation", 3000, 0, 0, 0, 60, true, PillarmanMode.HEAT),
    DIVINE_SANDSTORM("divine_sandstorm", 0, 0, 0, 0, 40, true, PillarmanMode.WIND),
    ATMOSPHERIC_RIFT("atmospheric_rift", 0, 0, 0, 0, 40, true, PillarmanMode.WIND),
    ULTIMATE_HAMON_OVERDRIVE("ultimate_hamon_overdrive", 750, 0, 1000, 50, 10, true, null),
    ULTIMATE_HAMON_SENDO_OVERDRIVE("ultimate_hamon_sendo_overdrive", 0, 0, 750, 100, 0, false, null),
    ULTIMATE_BARRAGE("ultimate_barrage", 0, 70, 0, 0, 0, true, null),
    ULTIMATE_HAMON_BARRAGE("ultimate_hamon_barrage", 0, 70, 0, 0, 0, true, null),
    ULTIMATE_HAMON_KICK("ultimate_hamon_kick", 1200, 0, 500, 100, 0, false, null),
    ULTIMATE_HAMON_TURQUOISE_BLUE_OVERDRIVE("ultimate_hamon_turquoise_blue_overdrive", 0, 0, 750, 75, 0, false, null),
    HAMON_SHOCK("hamon_shock", 0, 0, 400, 0, 0, false, null),
    HAMON_PLANT_INFUSION("hamon_plant_infusion", 0, 0, 500, 0, 0, false, null),
    HAMON_ORGANISM_INFUSION("hamon_organism_infusion", 0, 0, 500, 0, 0, false, null),
    ULTIMATE_FLIGHT_DASH("ultimate_flight_dash", 100, 0, 0, 20, 0, false, null),
    SCALE_SHOT("scale_shot", 0, 40, 0, 200, 0, true, null),
    SCALE_SPLIT_SHOT("scale_split_shot", 800, 0, 0, 200, 0, false, null),
    BUNNY_SUMMON("bunny_summon", 400, 0, 0, 600, 20, true, null),
    ABSORPTION("absorption", 0, 0, 0, 120, 0, true, null),
    HAMON_BREATH("hamon_breath", 0, 0, 0, 0, 0, true, null),
    PHANTOM_SUMMON("phantom_summon", 600, 0, 0, 600, 30, true, null),
    GUARDIAN_LASER("guardian_laser", 0, 20, 0, 0, 0, true, null),
    ARMOR_PROTECTION("armor_protection", 0, 0, 0, 20, 0, false, null),
    ARMOR_FIRE_PROTECTION("armor_fire_protection", 0, 0, 0, 10, 0, false, null),
    OVERWHELMING_DESPAIR("overwhelming_despair", 1000, 0, 0, 400, 0, false, null),
    NIGHT_VISION("night_vision", 0, 0, 0, 10, 0, false, null),
    ENDERMAN_TELEPORT("enderman_teleport", 600, 0, 0, 100, 0, false, null),
    UNLIMITED_POWER("unlimited_power", 0, 0, 0, 2400, 0, true, null),
    ABSOLUTE_IMMORTALITY("absolute_immortality", 0, 0, 0, 2400, 0, true, null),
    HYPNOTIZE("hypnotize", 0, 0, 50, 8000, 0, false, null),
    HAMON_LIFE_MAGNETISM("hamon_life_magnetism", 200, 0, 300, 0, 0, false, null),
    WALL_CLIMBING("wall_climbing", 0, 100, 0, 0, 0, false, null);

    public final String id;
    public final float energyCost;
    public final float holdEnergyCost;
    public final int hamonCost;
    public final int cooldown;
    public final int chargeTicks;
    public final boolean held;
    @Nullable public final PillarmanMode requiredMode;

    UltimateLifeformSpec(
            String id,
            float energyCost,
            float holdEnergyCost,
            int hamonCost,
            int cooldown,
            int chargeTicks,
            boolean held,
            @Nullable PillarmanMode requiredMode) {
        this.id = id;
        this.energyCost = energyCost;
        this.holdEnergyCost = holdEnergyCost;
        this.hamonCost = hamonCost;
        this.cooldown = cooldown;
        this.chargeTicks = chargeTicks;
        this.held = held;
        this.requiredMode = requiredMode;
    }

    public boolean continuesAfterCharge() {
        return switch (this) {
            case BLADE_DASH_ATTACK,
                    DIVINE_SANDSTORM,
                    ATMOSPHERIC_RIFT,
                    ULTIMATE_HAMON_OVERDRIVE -> true;
            default -> false;
        };
    }

    // 1.16 holdToFire(40, true) moves whose waves came from holdTick: past
    // the charge the hold went on, ready to fire (the HUD's green), until let
    // go; here that is the held PERFORM after the BUTTON_CHARGE
    public boolean readyToFireInHeldPerform() {
        return continuesAfterCharge() && !firesOnRelease();
    }

    // 1.16 holdToFire(n, true) moves whose work is in perform: the charge is
    // held as long as the key (Blade Dash's getHoldDurationMax is
    // Integer.MAX_VALUE) and fires on release once n ticks were held.
    public boolean firesOnRelease() {
        return this == BLADE_DASH_ATTACK
                || this == ULTIMATE_HAMON_OVERDRIVE;
    }

    // 1.16 PillarmanGiantCarthwheelPrison / PillarmanSelfDetonation called
    // builder.holdType() after the add-on's holdToFire(n, false): held as
    // long as the key, fired by the release once n ticks were held, never by
    // itself; the move is its perform alone
    public boolean firesOnReleaseOnly() {
        return this == GIANT_CARTHWHEEL_PRISON
                || this == SELF_DETONATION;
    }

    public boolean chargeHeldUntilRelease() {
        return firesOnRelease() || firesOnReleaseOnly();
    }

    // the 040 actions that implemented IPlayerAction (UltimateLifeformHeavyPunch,
    // HamonBeat, BladeSlash, BladeDash, ErraticBlazeKing,
    // HamonUltimateOverdrive, HamonKick, HamonShock): perform set their
    // continuous player action
    public boolean isPlayerAction() {
        return switch (this) {
            case HEAVY_PUNCH,
                    HAMON_OVERDRIVE_BEAT,
                    BLADE_SLASH,
                    BLADE_DASH_ATTACK,
                    ERRATIC_BLAZE_KING,
                    ULTIMATE_HAMON_OVERDRIVE,
                    ULTIMATE_HAMON_KICK,
                    HAMON_SHOCK -> true;
            default -> false;
        };
    }

    // 1.16 Action.holdOnly: no hold to fire, a hold limit (holdType)
    public boolean holdOnly() {
        return held && chargeTicks == 0;
    }

    // 1.16 Builder.cooldown(technical, additional): the technical part also
    // applied to a creative user (Tentacles' cooldown(80, 0))
    public int technicalCooldown() {
        return this == TENTACLES ? cooldown : 0;
    }

    /*
     * 1.16 Action.getCooldown(power, ticksHeld): the technical cooldown plus
     * the additional one, none of that in creative; PillarmanEvasion scaled
     * its additional one by the ticks held over the 50-tick limit.
     */
    public int cooldownTicks(boolean creative, int ticksHeld) {
        int additional = creative ? 0 : cooldown - technicalCooldown();
        if (this == EVASION && maxActionTicks() > 0) {
            additional = additional * Math.max(ticksHeld, 0) / maxActionTicks();
        }
        return technicalCooldown() + additional;
    }

    public boolean startsServerAction() {
        return held && (chargeTicks == 0 || continuesAfterCharge())
                || scriptedPerformTicks() > 0;
    }

    public int scriptedPerformTicks() {
        return switch (this) {
            case HEAVY_PUNCH, HAMON_OVERDRIVE_BEAT -> 8;
            case BLADE_SLASH -> 20;
            case ERRATIC_BLAZE_KING -> 40;
            case ULTIMATE_HAMON_KICK -> 10;
            case HAMON_SHOCK -> 15;
            default -> 0;
        };
    }

    public int maxActionTicks() {
        return switch (this) {
            case EVASION -> 50;
            case BLADE_DASH_ATTACK -> 15;
            case ULTIMATE_HAMON_OVERDRIVE -> 8;
            case SCALE_SHOT -> 100;
            // 1.16 PillarmanAbsorption's constructor called holdType() after
            // the registration's holdType(400): no limit
            case ABSORPTION -> 0;
            case ULTIMATE_HAMON_KICK -> 0;
            default -> scriptedPerformTicks();
        };
    }

    // Energy a tick of the server action costs. 1.16
    // PillarmanDivineSandstorm.getHeldTickEnergyCost: 3 once the charge is
    // done, which is when the port starts the server action.
    public float heldTickEnergyCost() {
        return switch (this) {
            case DIVINE_SANDSTORM, ATMOSPHERIC_RIFT -> 3.0F;
            default -> holdEnergyCost;
        };
    }

    // 1.16 heldWalkSpeed, and the 0.5 getWalkSpeed of the heavy punch and
    // blade slash instances; the input only slowed below 1, so Evasion's 5
    // changed nothing
    public float heldWalkSpeed() {
        return switch (this) {
            case HAMON_OVERDRIVE_BEAT,
                    STONE_FORM,
                    ENHANCED_SENSES,
                    ABSORPTION,
                    HEAVY_PUNCH,
                    BLADE_SLASH -> 0.5F;
            case ULTIMATE_AGILITY,
                    BLADE_DASH_ATTACK,
                    LIGHT_FLASH,
                    LIGHT_FLASH_DECOY,
                    GIANT_CARTHWHEEL_PRISON,
                    SELF_DETONATION,
                    ATMOSPHERIC_RIFT,
                    HAMON_BREATH -> 0.0F;
            case BLADE_BARRAGE,
                    DIVINE_SANDSTORM -> 0.2F;
            case ERRATIC_BLAZE_KING,
                    ULTIMATE_HAMON_OVERDRIVE -> 0.1F;
            case ULTIMATE_BARRAGE,
                    ULTIMATE_HAMON_BARRAGE -> 0.3F;
            case HAMON_SHOCK -> 0.25F;
            case GUARDIAN_LASER -> 0.8F;
            default -> 1.0F;
        };
    }

    public boolean requiresBothHandsFree() {
        return switch (this) {
            case HEAVY_PUNCH,
                    HAMON_OVERDRIVE_BEAT,
                    ENHANCED_SENSES,
                    BLADE_SLASH,
                    BLADE_DASH_ATTACK,
                    BLADE_BARRAGE,
                    LIGHT_FLASH,
                    LIGHT_FLASH_DECOY,
                    SMALL_SANDSTORM,
                    ERRATIC_BLAZE_KING,
                    GIANT_CARTHWHEEL_PRISON,
                    DIVINE_SANDSTORM,
                    ATMOSPHERIC_RIFT,
                    ULTIMATE_HAMON_OVERDRIVE,
                    ULTIMATE_HAMON_SENDO_OVERDRIVE,
                    ULTIMATE_BARRAGE,
                    ULTIMATE_HAMON_BARRAGE,
                    ULTIMATE_HAMON_TURQUOISE_BLUE_OVERDRIVE,
                    HAMON_SHOCK,
                    HAMON_PLANT_INFUSION,
                    HAMON_ORGANISM_INFUSION,
                    HYPNOTIZE -> true;
            default -> false;
        };
    }

    public boolean requiresMainHandFree() {
        return requiresBothHandsFree()
                || switch (this) {
                    case HAMON_OVERDRIVE,
                            ULTIMATE_HAMON_KICK,
                            ULTIMATE_FLIGHT_DASH,
                            BUNNY_SUMMON,
                            ABSORPTION,
                            PHANTOM_SUMMON,
                            OVERWHELMING_DESPAIR,
                            HAMON_LIFE_MAGNETISM -> true;
                    default -> false;
                };
    }

    public boolean requiresOffHandFree() {
        return requiresBothHandsFree();
    }

    // 1.16 UltimateLifeformAction.checkConditions compared the charge with
    // the raw hamonCost field; hamonCost here is getHamonCost(), its half.
    public int hamonThreshold() {
        return hamonCost * 2;
    }

    // 1.16 took getHamonCost() only in the consumeEnergy overrides of these
    // moves and in the kick's createContinuousActionInstance; the other
    // Hamon moves needed the charge but spent none of it.
    public int hamonSpentOnStart() {
        return switch (this) {
            case ULTIMATE_HAMON_OVERDRIVE,
                    ULTIMATE_HAMON_SENDO_OVERDRIVE,
                    ULTIMATE_HAMON_KICK,
                    ULTIMATE_HAMON_TURQUOISE_BLUE_OVERDRIVE,
                    HYPNOTIZE -> hamonCost;
            default -> 0;
        };
    }

    public boolean consumesHamonOnStart() {
        return hamonSpentOnStart() > 0;
    }

    // 1.16 UltimateLifeformActions .ignoresPerformerStun() (stone_form,
    // enhanced_senses, horn_attack) and the ignoresPerformerStun()
    // overrides of UltimateLifeformAbsorbtion and
    // UltimateLifeformOverwhelmingDespair. Stone form stuns its own
    // user every tick, so leaving it needs this.
    public boolean ignoresPerformerStun() {
        return switch (this) {
            case STONE_FORM,
                    ENHANCED_SENSES,
                    HORN_ATTACK,
                    ABSORPTION,
                    OVERWHELMING_DESPAIR -> true;
            default -> false;
        };
    }

    // 1.16 swingHand() actions (the add-on has no withUserPunch):
    // Action.onPerform reset the player's attack strength.
    public boolean resetsAttackStrengthOnPerform() {
        return switch (this) {
            case HEAVY_PUNCH,
                    HAMON_OVERDRIVE,
                    SMALL_SANDSTORM,
                    ULTIMATE_HAMON_OVERDRIVE,
                    ULTIMATE_HAMON_SENDO_OVERDRIVE,
                    ULTIMATE_HAMON_KICK,
                    ULTIMATE_HAMON_TURQUOISE_BLUE_OVERDRIVE,
                    HAMON_PLANT_INFUSION,
                    HAMON_ORGANISM_INFUSION -> true;
            default -> false;
        };
    }

    public boolean usesUltimateHamon() {
        return switch (this) {
            case HAMON_OVERDRIVE,
                    HAMON_OVERDRIVE_BEAT,
                    ULTIMATE_HAMON_OVERDRIVE,
                    ULTIMATE_HAMON_SENDO_OVERDRIVE,
                    ULTIMATE_HAMON_BARRAGE,
                    ULTIMATE_HAMON_KICK,
                    ULTIMATE_HAMON_TURQUOISE_BLUE_OVERDRIVE,
                    HAMON_SHOCK,
                    HAMON_PLANT_INFUSION,
                    HAMON_ORGANISM_INFUSION,
                    HAMON_BREATH,
                    HYPNOTIZE,
                    HAMON_LIFE_MAGNETISM -> true;
            default -> false;
        };
    }

    // 1.16 Action.Builder#shout: Kars' voice line, or null
    @Nullable
    public String legacyShout() {
        return switch (this) {
            case HEAVY_PUNCH, BLADE_SLASH, BLADE_DASH_ATTACK, BUNNY_SUMMON,
                    ULTIMATE_HAMON_KICK,
                    ULTIMATE_HAMON_TURQUOISE_BLUE_OVERDRIVE -> "kars_attack";
            case SCALE_SHOT -> "kars_scale_shot";
            case UNLIMITED_POWER -> "kars_unlimited_power";
            case ABSOLUTE_IMMORTALITY -> "kars_absolute_immortality";
            default -> null;
        };
    }

    // 1.16 SHIFT variations kept their shout while sneaking
    public boolean shoutsWhileSneaking() {
        return this == BLADE_DASH_ATTACK
                || this == ULTIMATE_HAMON_KICK
                || this == ABSOLUTE_IMMORTALITY;
    }

    @Nullable
    public static UltimateLifeformSpec byId(String id) {
        for (UltimateLifeformSpec spec : values()) {
            if (spec.id.equals(id)) {
                return spec;
            }
        }
        return null;
    }
}
