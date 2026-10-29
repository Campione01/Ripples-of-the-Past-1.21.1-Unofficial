package rotp.addons.ger.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

import com.mojang.logging.LogUtils;

import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

public final class GerRtzVfxSettings {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NORMAL_KEY = "normal";
    private static final String TIME_ERASE_KEY = "timeErase";
    private static final Path PATH = FMLPaths.CONFIGDIR.get()
            .resolve("geraddon-rtz-vfx.properties");

    private static Mode normalMode = Mode.NORMAL;
    private static Mode timeEraseMode = Mode.TIME_ERASE;
    private static boolean loaded;

    private GerRtzVfxSettings() {}

    public enum Mode {
        NORMAL("normal"),
        TIME_ERASE("time_erase"),
        OFF("off");

        private final String id;

        Mode(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Mode parse(String value) {
            String normalized = value == null
                    ? ""
                    : value.trim()
                            .toLowerCase(Locale.ROOT);
            for (Mode mode : values()) {
                if (mode.id.equals(normalized)) {
                    return mode;
                }
            }
            return null;
        }
    }

    public static Mode modeFor(boolean timeEraseTrigger) {
        load();
        return timeEraseTrigger
                ? timeEraseMode
                : normalMode;
    }

    public static Mode normalMode() {
        load();
        return normalMode;
    }

    public static Mode timeEraseMode() {
        load();
        return timeEraseMode;
    }

    public static void setNormalMode(Mode mode) {
        load();
        normalMode = mode;
        save();
    }

    public static void setTimeEraseMode(Mode mode) {
        load();
        timeEraseMode = mode;
        save();
    }

    public static void reset() {
        normalMode = Mode.NORMAL;
        timeEraseMode = Mode.TIME_ERASE;
        loaded = true;
        save();
    }

    private static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (!Files.isRegularFile(PATH)) {
            save();
            return;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(PATH)) {
            properties.load(input);
            Mode parsedNormal =
                    Mode.parse(properties.getProperty(NORMAL_KEY));
            Mode parsedTimeErase =
                    Mode.parse(properties.getProperty(
                            TIME_ERASE_KEY));
            normalMode = parsedNormal != null
                    ? parsedNormal
                    : Mode.NORMAL;
            timeEraseMode = parsedTimeErase != null
                    ? parsedTimeErase
                    : Mode.TIME_ERASE;
        }
        catch (IOException exception) {
            LOGGER.warn(
                    "Failed to load GER RTZ VFX settings",
                    exception);
        }
    }

    private static synchronized void save() {
        Properties properties = new Properties();
        properties.setProperty(NORMAL_KEY, normalMode.id());
        properties.setProperty(
                TIME_ERASE_KEY, timeEraseMode.id());
        try {
            Files.createDirectories(PATH.getParent());
            try (OutputStream output =
                    Files.newOutputStream(PATH)) {
                properties.store(
                        output,
                        "GER addon client-side RTZ VFX preferences");
            }
        }
        catch (IOException exception) {
            LOGGER.warn(
                    "Failed to save GER RTZ VFX settings",
                    exception);
        }
    }
}
