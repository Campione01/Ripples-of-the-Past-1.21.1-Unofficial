package rotp.core.mechanics.clothes.sewing.client;

public final class SewingSearchSmokeTest {
    private SewingSearchSmokeTest() {}

    public static void main(String[] args) {
        expect("", true, "Jotaro Kujo", "School Uniform", true);
        expect("   ", true, "Jotaro Kujo", "School Uniform", true);
        expect("jotaro", true, "Jotaro Kujo", "School Uniform", true);
        expect(" KUJO ", true, "Jotaro Kujo", "School Uniform", true);
        expect("uniform", true, "Jotaro Kujo", "School Uniform", true);
        expect("\u627f\u592a\u90ce", true, "\u7a7a\u6761\u627f\u592a\u90ce", "\u6821\u670d", true);
        expect("Dio", true, "Jotaro Kujo", "School Uniform", false);
        expect("coat", true, "Jotaro Kujo", "School Uniform", false);
        expect("jotaro", false, "Jotaro Kujo", "School Uniform", false);
        expect("", false, "Jotaro Kujo", "School Uniform", true);
        System.out.println("Sewing search smoke test passed (10 cases).");
    }

    private static void expect(String search, boolean byName, String character, String clothes, boolean expected) {
        if (ClothesCharacterUIEntry.matchesSearch(search, byName, character, clothes) != expected) {
            throw new AssertionError("Unexpected sewing name filter result for: " + search);
        }
    }
}
