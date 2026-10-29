package rotp.addons.weatherreport.network;

import java.util.Optional;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

public enum WeatherChoice {
    CLEAR(0, 1),
    RAIN(1, 1 << 1),
    THUNDER(2, 1 << 2);

    public static final int ALL_ALLOWED_MASK =
            CLEAR.mask | RAIN.mask | THUNDER.mask;
    public static final int CLEAR_DURATION_MIN = 12_000;
    public static final int CLEAR_DURATION_BOUND = 168_000;
    public static final int PRECIPITATION_DURATION_MIN = 3_600;
    public static final int PRECIPITATION_DURATION_BOUND = 12_000;

    private final byte networkId;
    private final int mask;

    WeatherChoice(int networkId, int mask) {
        this.networkId = (byte) networkId;
        this.mask = mask;
    }

    public byte networkId() {
        return networkId;
    }

    public int mask() {
        return mask;
    }

    public static Optional<WeatherChoice> fromNetworkId(byte id) {
        for (WeatherChoice choice : values()) {
            if (choice.networkId == id) {
                return Optional.of(choice);
            }
        }
        return Optional.empty();
    }

    public void apply(ServerLevel level) {
        RandomSource random = level.getRandom();
        switch (this) {
            case CLEAR -> level.setWeatherParameters(
                    random.nextInt(CLEAR_DURATION_BOUND)
                            + CLEAR_DURATION_MIN,
                    0,
                    false,
                    false);
            case RAIN -> level.setWeatherParameters(
                    0,
                    random.nextInt(PRECIPITATION_DURATION_BOUND)
                            + PRECIPITATION_DURATION_MIN,
                    true,
                    false);
            case THUNDER -> level.setWeatherParameters(
                    0,
                    random.nextInt(PRECIPITATION_DURATION_BOUND)
                            + PRECIPITATION_DURATION_MIN,
                    true,
                    true);
        }
    }
}
