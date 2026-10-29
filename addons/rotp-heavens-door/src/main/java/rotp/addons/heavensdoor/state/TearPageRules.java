package rotp.addons.heavensdoor.state;

/**
 * 1.16 TearOutAPage.checkTarget: the first reason a page cannot be torn,
 * in donor order with the owner's removed health gate (no_book, 10_page, too_far), or null.
 * Pure so the contract can run it.
 */
public final class TearPageRules {
    public static final String NO_BOOK = "no_book";
    public static final String TOO_MANY_PAGES = "10_page";
    public static final String TOO_FAR = "too_far";

    private TearPageRules() {}

    public static String refusal(
            boolean book,
            int pageCount,
            int maxPages,
            double distanceSq,
            double range) {
        if (!book) {
            return NO_BOOK;
        }
        if (pageCount >= maxPages) {
            return TOO_MANY_PAGES;
        }
        if (distanceSq > range * range) {
            return TOO_FAR;
        }
        return null;
    }
}
