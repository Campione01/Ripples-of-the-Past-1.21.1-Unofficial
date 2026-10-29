package rotp.addons.madeinheaven;

public final class MadeInHeavenTimeSystemIcons {
    private MadeInHeavenTimeSystemIcons() {}

    public static String spriteName(
            boolean newIcons, boolean clear) {
        return "time_system/"
                + (newIcons ? "new/" : "old/")
                + "mih_time_system."
                + (clear ? "clear" : "acceleration");
    }
}
