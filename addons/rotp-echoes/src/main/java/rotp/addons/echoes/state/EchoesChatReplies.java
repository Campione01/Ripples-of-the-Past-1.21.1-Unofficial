package rotp.addons.echoes.state;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntUnaryOperator;

// 092 EchoAIHandler reply tables and pick order (no game classes)
public final class EchoesChatReplies {
    public static final int COOLDOWN_TICKS = 5;
    public static final int MAX_NICKNAME_LENGTH = 20;
    public static final int RANDOM_LINES = 29;

    private static final String[] SHIT_SUFFIXES = {" S.H.I.T", " S.H.I.T!", " S.H.I.T...", " S.H.I.T!?", " S.H.I.T!!", " S.H.I.T!!!", " S.H.I.T?!", " S.H.I.T....", " S.H.I.T??", " S.H.I.T!?!"};
    private static final String[] MILD_CURSES = {" Oh hell no.", " What the hell.", " Oh bloody hell.", " For crying out loud.", " Oh for heaven's sake.", " Good grief.", " Oh my god.", " What on earth.", " Oh darn.", " Bloody hell.", " Oh crap.", " What the heck.", " Oh jeez.", " Oh man.", " For Pete's sake.", " Oh blast.", " What in the world.", " Oh bother.", " Oh dear.", " What the devil.", " Oh for the love of god.", " Holy crap.", " What in tarnation.", " Oh fiddlesticks.", " For goodness sake."};
    private static final String[] ROASTS = {"You serious?", "That's it?", "You can do better than that.", "Git gud.", "My grandma plays better than you.", "Are you even trying?", "I can't even...", "Bro, you okay?", "You sure about that?", "I can't with you.", "Are you for real?", "You're trolling, right?", "This is painful to watch.", "I've seen better gameplay from a potato.", "You're something else.", "Stop feeding, please.", "Are you on my team or theirs?", "My blood pressure is rising.", "That's one way to do it... the wrong way.", "Is this your first time?", "Hands are optional, I guess.", "Brain lag?", "I just... never mind.", "This game is not for everyone.", "I'm embarrassed for you.", "Just afk, it's safer.", "Are you playing with your feet?", "I have no words.", "Maybe try turning it off and on again?", "Pro gamer move... not.", "What was that?", "You can't be serious.", "I need a break from this.", "Are you practicing to be this bad?", "Unbelievable.", "I'm done."};
    private static final String[] CURSE_WORDS = {"fuck", "shit", "damn", "hell", "crap", "darn", "bloody", "goddamn", "wtf", "ass", "\u64cd", "\u9760", "\u8349"};
    private static final String[] ROAST_WORDS = {"\u83dc", "\u5f31", "\u5783\u573e", "\u5e9f\u7269", "\u83dc\u9e1f", "\u5f69\u7b14", "\u840c\u65b0", "\u65b0\u624b", "\u4e0d\u4f1a\u73a9", "\u83dc\u9e21", "noob", "weak", "trash", "garbage", "useless", "\u5e9f", "\u6e23", "\u7b28", "\u8822"};
    private static final String[] GREETING_WORDS = {"\u4f60\u597d", "\u55e8", "hello", "hi", "\u563f", "\u5582", "\u5728\u5417", "\u5728\u4e0d\u5728", "hey", "yo", "\u3053\u3093\u306b\u3061\u306f", "\uc548\ub155", "\u043f\u0440\u0438\u0432\u0435\u0442", "\u6c5d\u5b89", "\u8db3\u4e0b", "\u541b\u5b89", "\u5b89\u597d"};
    private static final Map<String, Map<String, String>> LANGUAGE_REPLIES =
            new HashMap<String, Map<String, String>>();
    // HashMap like 092, so overlapping keywords resolve the same way
    private static final Map<String, String> KEYWORD_MAP;

    private EchoesChatReplies() {}

    // nickname is non-null when the message set one
    public record Reply(String text, String nickname) {}

    // 092 getPlayerLanguage, fed by the client's language option
    public static String language(String code) {
        if (code == null) {
            return "en";
        }
        String lower = code.toLowerCase(Locale.ROOT);
        if (lower.equals("lzh")) {
            return "lzh";
        }
        String prefix = lower.startsWith("zh") ? "zh" : lower.split("_")[0];
        return LANGUAGE_REPLIES.containsKey(prefix) ? prefix : "en";
    }

    public static String line(String lang, String key) {
        Map<String, String> table = LANGUAGE_REPLIES.getOrDefault(
                lang, LANGUAGE_REPLIES.get("en"));
        String reply = table.get(key);
        return reply != null ? reply : LANGUAGE_REPLIES.get("en").get(key);
    }

    // 092 handleChat order: nickname, keyword, greeting, curse, roast, random
    public static Reply reply(
            String message,
            String lang,
            String playerName,
            IntUnaryOperator random) {
        String trimmed = message.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("\u53eb\u6211") || lower.startsWith("call me")) {
            String nickname = nickname(
                    trimmed, lower.startsWith("\u53eb\u6211") ? 2 : 8);
            if (nickname != null) {
                return new Reply(
                        line(lang, "okay") + " " + nickname + "\u3002",
                        nickname);
            }
        }
        if (lower.startsWith("\u6211\u53eb")
                || lower.startsWith("i am")
                || lower.startsWith("im ")) {
            String nickname = nickname(
                    trimmed,
                    lower.startsWith("\u6211\u53eb")
                            ? 2
                            : lower.startsWith("i am") ? 5 : 3);
            if (nickname != null) {
                return new Reply(
                        line(lang, "got_it") + " " + nickname + "\u3002",
                        nickname);
            }
        }
        for (Map.Entry<String, String> entry : KEYWORD_MAP.entrySet()) {
            if (lower.contains(entry.getKey())) {
                // 092's ruins search never matched; it always said ruins_none
                return new Reply(line(lang, entry.getValue()), null);
            }
        }
        if (containsAny(lower, GREETING_WORDS)) {
            return new Reply(line(lang, "greeting"), null);
        }
        if (containsAny(lower, CURSE_WORDS)) {
            return new Reply(pick(MILD_CURSES, random), null);
        }
        if (containsAny(lower, ROAST_WORDS)) {
            return new Reply(pick(ROASTS, random), null);
        }
        String reply = line(
                lang, "random" + (1 + random.applyAsInt(RANDOM_LINES)));
        if (random.applyAsInt(10) < 3 && !playerName.isEmpty()) {
            reply = playerName + "\uff0c" + reply;
        }
        return new Reply(reply, null);
    }

    // 092 sendReply: 50% S.H.I.T suffix
    public static String withSuffix(String line, IntUnaryOperator random) {
        if (random.applyAsInt(2) == 0) {
            return line + pick(SHIT_SUFFIXES, random);
        }
        return line;
    }

    public static List<String> mildCurses() {
        return List.of(MILD_CURSES);
    }

    public static List<String> roasts() {
        return List.of(ROASTS);
    }

    public static List<String> suffixes() {
        return List.of(SHIT_SUFFIXES);
    }

    private static String nickname(String trimmed, int offset) {
        // 092 threw on a bare "call me" / "i am"; treat it as no nickname
        if (trimmed.length() < offset) {
            return null;
        }
        String nickname = trimmed.substring(offset).trim();
        return nickname.isEmpty() || nickname.length() > MAX_NICKNAME_LENGTH
                ? null
                : nickname;
    }

    private static boolean containsAny(String lower, String[] words) {
        for (String word : words) {
            if (lower.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private static String pick(String[] choices, IntUnaryOperator random) {
        return choices[random.applyAsInt(choices.length)];
    }

    // 092 EchoAIHandler static tables, verbatim
    static {
        HashMap<String, String> zh = new HashMap<String, String>();
        zh.put("greeting", "\u4f60\u597d\uff0c\u6211\u662f\u56de\u97f3ACT3\u3002");
        zh.put("whats_up", "\u4ec0\u4e48\u4e8b\uff1f");
        zh.put("listening", "\u6211\u5728\u542c\u3002");
        zh.put("go_ahead", "\u8bf4\u5427\u3002");
        zh.put("huh", "\u55ef\uff1f");
        zh.put("here_we_go", "\u53c8\u6765\u4e86\u3002");
        zh.put("got_it", "\u660e\u767d\u4e86\u3002");
        zh.put("okay", "\u597d\u7684\u3002");
        zh.put("roger", "\u6536\u5230\u3002");
        zh.put("mm", "\u55ef\u3002");
        zh.put("alright", "\u884c\u3002");
        zh.put("sure", "\u53ef\u4ee5\u3002");
        zh.put("no_problem", "\u6ca1\u95ee\u9898\u3002");
        zh.put("confused1", "\u4e0d\u592a\u660e\u767d\u4f60\u7684\u610f\u601d\u3002");
        zh.put("confused2", "\u80fd\u8bf4\u6e05\u695a\u70b9\u5417\uff1f");
        zh.put("confused3", "\u6ca1\u542c\u61c2\u3002");
        zh.put("confused4", "\u4ec0\u4e48\u610f\u601d\uff1f");
        zh.put("confused5", "\u518d\u8bf4\u4e00\u904d\uff1f");
        zh.put("confused6", "\u542c\u4e0d\u61c2\u3002");
        zh.put("how_to_play", "\u53bb\u51b0\u539f\u627e\u9668\u77f3\u5751\u505a\u66ff\u8eab\u7bad\u83b7\u5f97\u66ff\u8eab\uff0c\u53bb\u9ad8\u5c71\u627e\u6ce2\u7eb9\u5854\u5b66\u6ce2\u7eb9\uff0c\u53bb\u4e1b\u6797\u627e\u67f1\u4e4b\u7537\u9057\u8ff9\u62ff\u77f3\u9b3c\u9762\u53d8\u5438\u8840\u9b3c\u3002");
        zh.put("hamon", "\u53bb\u9ad8\u5c71\u5730\u5f62\uff0c\u627e\u5230\u4f20\u8bf4\u4e2d\u7684\u6ce2\u7eb9\u5854\uff0c\u91cc\u9762\u6709\u4e00\u4e2a\u6ce2\u7eb9\u5927\u5e08\uff0c\u5411\u4ed6\u5b66\u4e60\u5c31\u80fd\u83b7\u5f97\u6ce2\u7eb9\u529b\u91cf\u3002");
        zh.put("vampire", "\u53bb\u6742\u8349\u4e1b\u751f\u7684\u4e1b\u6797\uff0c\u4f3c\u4e4e\u6709\u4e00\u4e2a\u53e4\u8001\u7684\u67f1\u4e4b\u7537\u9057\u8ff9\uff0c\u6df1\u5165\u63a2\u7d22\u5c31\u80fd\u627e\u5230\u77f3\u9b3c\u9762\u3002\u6234\u4e0a\u77f3\u9b3c\u9762\uff0c\u7528\u5c0f\u5200\u5272\u81ea\u5df1\uff0c\u5c31\u80fd\u53d8\u6210\u5438\u8840\u9b3c\u3002");
        zh.put("stand", "\u53bb\u5bd2\u51b7\u7684\u5730\u65b9\uff0c\u90a3\u91cc\u4f3c\u4e4e\u6709\u6d41\u661f\u5760\u843d\u8fc7... \u627e\u5230\u9668\u77f3\u5751\uff0c\u91cc\u9762\u7684\u9668\u77f3\u6838\u5fc3\u53ef\u4ee5\u5236\u4f5c\u66ff\u8eab\u4e4b\u7bad\u3002\u7528\u66ff\u8eab\u7bad\u53f3\u952e\u81ea\u5df1\uff0c\u5c31\u6709\u673a\u4f1a\u83b7\u5f97\u66ff\u8eab\uff01");
        zh.put("echoes", "\u56de\u97f3ACT2\u53ef\u4ee5\u8fdb\u5316\u5230ACT3\uff0c\u4f7f\u7528\u8fdb\u5316\u6280\u80fd\u5c31\u884c\u3002\u9700\u8981\u6d88\u8017\u7cbe\u795e\u80fd\u91cf\u3002");
        zh.put("no_eat", "\u6211\u662f\u66ff\u8eab\uff0c\u4e0d\u9700\u8981\u5403\u996d\u3002");
        zh.put("no_sleep", "\u66ff\u8eab\u4e0d\u7528\u7761\u89c9\u3002");
        zh.put("no_tired", "\u66ff\u8eab\u4e0d\u4f1a\u7d2f\u3002");
        zh.put("weather", "\u6211\u662f\u66ff\u8eab\uff0c\u611f\u77e5\u4e0d\u5230\u5929\u6c14\u3002\u4f60\u81ea\u5df1\u62ac\u5934\u770b\u770b\u5457\u3002");
        zh.put("love", "\u554a\uff1f");
        zh.put("handsome", "\u8c22\u8c22\u3002");
        zh.put("beautiful", "\u8c22\u8c22\u5938\u5956\u3002");
        zh.put("cute", "...\u4f60\u8bf4\u5565\uff1f");
        zh.put("ruins_none", "\u9644\u8fd1\u6ca1\u53d1\u73b0\u4ec0\u4e48\u7279\u522b\u7684\u5730\u5f62\u3002");
        zh.put("ruins_nearby", "\u9644\u8fd1\u6709: ");
        zh.put("ruins_etc", " \u7b49...");
        zh.put("no_stand", "\u4f60\u6ca1\u6709\u56de\u97f3ACT3\u66ff\u8eab\uff0c\u65e0\u6cd5\u4e0e\u6211\u5bf9\u8bdd\u3002");
        zh.put("random1", "\u55ef\u55ef\uff0c\u6211\u5728\u542c\u3002");
        zh.put("random2", "\u7136\u540e\u5462\uff1f");
        zh.put("random3", "\u771f\u7684\u5417\uff1f");
        zh.put("random4", "\u54c7\uff0c\u8fd9\u6837\u554a\u3002");
        zh.put("random5", "\u633a\u6709\u610f\u601d\u7684\u3002");
        zh.put("random6", "\u6211\u4e0d\u592a\u61c2\u8fd9\u4e2a\u3002");
        zh.put("random7", "\u4f60\u89c9\u5f97\u5462\uff1f");
        zh.put("random8", "\u4e5f\u8bb8\u5427\u3002");
        zh.put("random9", "\u8c01\u77e5\u9053\u5462\u3002");
        zh.put("random10", "\u54c8\u54c8\u3002");
        zh.put("random11", "\u597d\u5427\u3002");
        zh.put("random12", "\u539f\u6765\u5982\u6b64\u3002");
        zh.put("random13", "\u660e\u767d\u4e86\u3002");
        zh.put("random14", "\u7ee7\u7eed\u8bf4\u5427\u3002");
        zh.put("random15", "\u6211\u5728\u60f3\u4e8b\u60c5\u3002");
        zh.put("random16", "\u662f\u5417\uff1f");
        zh.put("random17", "\u4e0d\u4f1a\u5427\uff1f");
        zh.put("random18", "\u5929\u54ea\u3002");
        zh.put("random19", "\u55ef\u54fc\u3002");
        zh.put("random20", "\u884c\u5427\u3002");
        zh.put("random21", "\u968f\u4fbf\u3002");
        zh.put("random22", "\u90fd\u53ef\u4ee5\u3002");
        zh.put("random23", "\u522b\u7070\u5fc3\u3002");
        zh.put("random24", "\u76f8\u4fe1\u81ea\u5df1\u3002");
        zh.put("random25", "\u52aa\u529b\u5c31\u597d\u3002");
        zh.put("random26", "\u4f60\u8bf4\u5f97\u5bf9\u3002");
        zh.put("random27", "\u6709\u9053\u7406\u3002");
        zh.put("random28", "\u786e\u5b9e\u3002");
        zh.put("random29", "\u6ca1\u9519\u3002");
        LANGUAGE_REPLIES.put("zh", zh);
        HashMap<String, String> lzh = new HashMap<String, String>();
        lzh.put("greeting", "\u8db3\u4e0b\u5b89\u597d\uff0c\u543e\u4e43\u56de\u97f3ACT3\u4e5f\u3002");
        lzh.put("whats_up", "\u4f55\u4e8b\uff1f");
        lzh.put("listening", "\u9858\u805e\u5176\u8a73\u3002");
        lzh.put("go_ahead", "\u8acb\u8a00\u3002");
        lzh.put("huh", "\u55ef\uff1f");
        lzh.put("here_we_go", "\u53c8\u4f86\u77e3\u3002");
        lzh.put("got_it", "\u5df2\u77e5\u3002");
        lzh.put("okay", "\u5584\u3002");
        lzh.put("roger", "\u8afe\u3002");
        lzh.put("mm", "\u558f\u3002");
        lzh.put("alright", "\u53ef\u3002");
        lzh.put("sure", "\u7136\u3002");
        lzh.put("no_problem", "\u7121\u59a8\u3002");
        lzh.put("confused1", "\u4e0d\u89e3\u6c5d\u610f\u3002");
        lzh.put("confused2", "\u80fd\u660e\u8a00\u5426\uff1f");
        lzh.put("confused3", "\u672a\u805e\u3002");
        lzh.put("confused4", "\u4f55\u610f\uff1f");
        lzh.put("confused5", "\u518d\u8a00\u4e00\u904d\uff1f");
        lzh.put("confused6", "\u543e\u4e0d\u660e\u4e5f\u3002");
        lzh.put("how_to_play", "\u5f80\u51b0\u539f\u5c0b\u9695\u5751\u4ee5\u88fd\u66ff\u8eab\u7bad\uff0c\u767b\u9ad8\u5c71\u8a2a\u6ce2\u7d0b\u5854\u4ee5\u5b78\u6ce2\u7d0b\uff0c\u5165\u53e2\u6797\u63a2\u67f1\u4e4b\u7537\u907a\u8de1\u4ee5\u5f97\u77f3\u9b3c\u9762\uff0c\u53ef\u5316\u70ba\u5438\u8840\u9b3c\u3002");
        lzh.put("hamon", "\u5f80\u9ad8\u5c71\u4e4b\u5730\uff0c\u5c0b\u50b3\u8aaa\u4e2d\u4e4b\u6ce2\u7d0b\u5854\uff0c\u5167\u6709\u6ce2\u7d0b\u5927\u5e2b\uff0c\u5411\u5176\u6c42\u5b78\u53ef\u5f97\u6ce2\u7d0b\u4e4b\u529b\u3002");
        lzh.put("vampire", "\u5f80\u53e2\u6797\u6df1\u8655\uff0c\u6709\u67f1\u4e4b\u7537\u53e4\u8de1\uff0c\u6df1\u5165\u63a2\u7d22\u53ef\u5f97\u77f3\u9b3c\u9762\u3002\u6234\u4e4b\uff0c\u4ee5\u5200\u81ea\u50b7\uff0c\u5373\u53ef\u5316\u70ba\u5438\u8840\u9b3c\u3002");
        lzh.put("stand", "\u5f80\u5bd2\u5730\uff0c\u6709\u9695\u77f3\u589c\u843d\u4e4b\u8655... \u5c0b\u9695\u5751\uff0c\u53d6\u5176\u6838\u5fc3\u53ef\u88fd\u66ff\u8eab\u7bad\u3002\u4ee5\u7bad\u81ea\u523a\uff0c\u53ef\u5f97\u66ff\u8eab\uff01");
        lzh.put("echoes", "\u56de\u97f3ACT2\u53ef\u9032\u5316\u70baACT3\uff0c\u7528\u9032\u5316\u4e4b\u6280\u5373\u53ef\u3002\u9700\u8017\u7cbe\u795e\u4e4b\u529b\u3002");
        lzh.put("no_eat", "\u543e\u4e43\u66ff\u8eab\uff0c\u4e0d\u98df\u4eba\u9593\u7159\u706b\u3002");
        lzh.put("no_sleep", "\u66ff\u8eab\u7121\u9700\u7761\u7720\u3002");
        lzh.put("no_tired", "\u66ff\u8eab\u4e0d\u77e5\u75b2\u618a\u3002");
        lzh.put("weather", "\u543e\u4e43\u66ff\u8eab\uff0c\u4e0d\u5bdf\u5929\u5019\u3002\u6c5d\u81ea\u89c0\u4e4b\u3002");
        lzh.put("love", "\u566b\uff1f");
        lzh.put("handsome", "\u591a\u8b1d\u3002");
        lzh.put("beautiful", "\u627f\u8499\u8b2c\u8b9a\u3002");
        lzh.put("cute", "...\u6c5d\u4f55\u8a00\uff1f");
        lzh.put("ruins_none", "\u9644\u8fd1\u7121\u7570\u8c61\u3002");
        lzh.put("ruins_nearby", "\u9644\u8fd1\u6709: ");
        lzh.put("ruins_etc", " \u7b49...");
        lzh.put("no_stand", "\u6c5d\u7121\u56de\u97f3ACT3\u66ff\u8eab\uff0c\u543e\u4e0d\u80fd\u8207\u6c5d\u8a00\u3002");
        lzh.put("random1", "\u8afe\uff0c\u543e\u5728\u807d\u3002");
        lzh.put("random2", "\u7136\u5f8c\uff1f");
        lzh.put("random3", "\u679c\u771f\uff1f");
        lzh.put("random4", "\u539f\u4f86\u5982\u6b64\u3002");
        lzh.put("random5", "\u6709\u8da3\u3002");
        lzh.put("random6", "\u543e\u4e0d\u660e\u6b64\u7406\u3002");
        lzh.put("random7", "\u6c5d\u610f\u5982\u4f55\uff1f");
        lzh.put("random8", "\u6216\u7136\u3002");
        lzh.put("random9", "\u5b70\u77e5\uff1f");
        lzh.put("random10", "\u54c8\u54c8\u3002");
        lzh.put("random11", "\u5584\u3002");
        lzh.put("random12", "\u77ad\u7136\u3002");
        lzh.put("random13", "\u5df2\u77e5\u3002");
        lzh.put("random14", "\u8acb\u7e8c\u8a00\u3002");
        lzh.put("random15", "\u543e\u6709\u6240\u601d\u3002");
        lzh.put("random16", "\u8aa0\u7136\uff1f");
        lzh.put("random17", "\u8c48\u6709\u6b64\u7406\uff1f");
        lzh.put("random18", "\u5929\u54ea\u3002");
        lzh.put("random19", "\u55ef\u54fc\u3002");
        lzh.put("random20", "\u53ef\u3002");
        lzh.put("random21", "\u96a8\u4fbf\u3002");
        lzh.put("random22", "\u7686\u53ef\u3002");
        lzh.put("random23", "\u52ff\u9912\u3002");
        lzh.put("random24", "\u4fe1\u5df1\u3002");
        lzh.put("random25", "\u76e1\u529b\u800c\u70ba\u3002");
        lzh.put("random26", "\u6c5d\u8a00\u751a\u662f\u3002");
        lzh.put("random27", "\u6709\u7406\u3002");
        lzh.put("random28", "\u8aa0\u7136\u3002");
        lzh.put("random29", "\u6b63\u662f\u3002");
        LANGUAGE_REPLIES.put("lzh", lzh);
        HashMap<String, String> ja = new HashMap<String, String>();
        ja.put("greeting", "\u3053\u3093\u306b\u3061\u306f\u3001\u30a8\u30b3\u30fc\u30baACT3\u3060\u3002");
        ja.put("whats_up", "\u4f55\u304b\u7528\uff1f");
        ja.put("listening", "\u805e\u3044\u3066\u308b\u3002");
        ja.put("go_ahead", "\u8a00\u3063\u3066\u307f\u308d\u3002");
        ja.put("huh", "\u3093\uff1f");
        ja.put("here_we_go", "\u307e\u305f\u304b\u3002");
        ja.put("got_it", "\u308f\u304b\u3063\u305f\u3002");
        ja.put("okay", "\u30aa\u30fc\u30b1\u30fc\u3002");
        ja.put("roger", "\u4e86\u89e3\u3002");
        ja.put("mm", "\u3046\u3093\u3002");
        ja.put("alright", "\u3088\u3057\u3002");
        ja.put("sure", "\u3044\u3044\u3088\u3002");
        ja.put("no_problem", "\u554f\u984c\u306a\u3044\u3002");
        ja.put("confused1", "\u610f\u5473\u304c\u308f\u304b\u3089\u306a\u3044\u3002");
        ja.put("confused2", "\u3082\u3063\u3068\u660e\u78ba\u306b\u8a00\u3063\u3066\u304f\u308c\u3002");
        ja.put("confused3", "\u805e\u304d\u53d6\u308c\u306a\u304b\u3063\u305f\u3002");
        ja.put("confused4", "\u3069\u3046\u3044\u3046\u610f\u5473\uff1f");
        ja.put("confused5", "\u3082\u3046\u4e00\u5ea6\u8a00\u3063\u3066\u304f\u308c\u3002");
        ja.put("confused6", "\u7406\u89e3\u3067\u304d\u306a\u3044\u3002");
        ja.put("how_to_play", "\u6c37\u539f\u306e\u9695\u77f3\u7a74\u3067\u30b9\u30bf\u30f3\u30c9\u306e\u77e2\u3092\u4f5c\u308a\u3001\u9ad8\u5c71\u306e\u6ce2\u7d0b\u30bf\u30ef\u30fc\u3067\u6ce2\u7d0b\u3092\u5b66\u3073\u3001\u30b8\u30e3\u30f3\u30b0\u30eb\u306e\u67f1\u306e\u7537\u907a\u8de1\u3067\u77f3\u4eee\u9762\u3092\u898b\u3064\u3051\u3066\u5438\u8840\u9b3c\u306b\u306a\u308d\u3046\u3002");
        ja.put("hamon", "\u5c71\u5cb3\u5730\u5e2f\u306b\u884c\u304d\u3001\u4f1d\u8aac\u306e\u6ce2\u7d0b\u30bf\u30ef\u30fc\u3092\u898b\u3064\u3051\u3088\u3046\u3002\u4e2d\u306b\u6ce2\u7d0b\u306e\u9054\u4eba\u304c\u3044\u3066\u3001\u5f7c\u306b\u5b66\u3079\u3070\u6ce2\u7d0b\u306e\u529b\u3092\u5f97\u3089\u308c\u308b\u3002");
        ja.put("vampire", "\u30b8\u30e3\u30f3\u30b0\u30eb\u306e\u5965\u6df1\u304f\u306b\u306f\u53e4\u4ee3\u306e\u67f1\u306e\u7537\u306e\u907a\u8de1\u304c\u3042\u308b\u3068\u8a00\u308f\u308c\u3066\u3044\u308b\u3002\u77f3\u4eee\u9762\u3092\u898b\u3064\u3051\u3066\u3001\u305d\u308c\u3092\u304b\u3076\u308a\u3001\u30ca\u30a4\u30d5\u3067\u81ea\u5206\u3092\u5207\u308c\u3070\u5438\u8840\u9b3c\u306b\u306a\u308c\u308b\u3002");
        ja.put("stand", "\u5bd2\u3044\u5834\u6240\u306b\u6d41\u661f\u304c\u843d\u3061\u305f\u75d5\u8de1\u304c\u3042\u308b... \u9695\u77f3\u7a74\u3092\u898b\u3064\u3051\u3066\u3001\u4e2d\u306e\u9695\u77f3\u30b3\u30a2\u3067\u30b9\u30bf\u30f3\u30c9\u306e\u77e2\u3092\u4f5c\u308d\u3046\u3002\u30b9\u30bf\u30f3\u30c9\u306e\u77e2\u3092\u81ea\u5206\u306b\u4f7f\u3048\u3070\u3001\u30b9\u30bf\u30f3\u30c9\u3092\u5f97\u3089\u308c\u308b\u30c1\u30e3\u30f3\u30b9\u304c\u3042\u308b\uff01");
        ja.put("echoes", "\u30a8\u30b3\u30fc\u30baACT2\u306f\u9032\u5316\u30b9\u30ad\u30eb\u3092\u4f7f\u3046\u3053\u3068\u3067ACT3\u306b\u9032\u5316\u3067\u304d\u308b\u3002\u7cbe\u795e\u30a8\u30cd\u30eb\u30ae\u30fc\u3092\u6d88\u8cbb\u3059\u308b\u3002");
        ja.put("no_eat", "\u79c1\u306f\u30b9\u30bf\u30f3\u30c9\u3060\u304b\u3089\u3001\u98df\u4e8b\u306f\u5fc5\u8981\u306a\u3044\u3002");
        ja.put("no_sleep", "\u30b9\u30bf\u30f3\u30c9\u306f\u7720\u3089\u306a\u304f\u3066\u3044\u3044\u3002");
        ja.put("no_tired", "\u30b9\u30bf\u30f3\u30c9\u306f\u75b2\u308c\u306a\u3044\u3002");
        ja.put("weather", "\u30b9\u30bf\u30f3\u30c9\u306b\u5929\u6c17\u306f\u308f\u304b\u3089\u306a\u3044\u3002\u81ea\u5206\u3067\u898b\u3066\u307f\u308d\u3002");
        ja.put("love", "\u306f\uff1f");
        ja.put("handsome", "\u3042\u308a\u304c\u3068\u3046\u3002");
        ja.put("beautiful", "\u8912\u3081\u3066\u304f\u308c\u3066\u3042\u308a\u304c\u3068\u3046\u3002");
        ja.put("cute", "...\u4f55\u8a00\u3063\u3066\u308b\u3093\u3060\uff1f");
        ja.put("ruins_none", "\u8fd1\u304f\u306b\u7279\u5225\u306a\u5730\u5f62\u306f\u898b\u3064\u304b\u3089\u306a\u304b\u3063\u305f\u3002");
        ja.put("ruins_nearby", "\u8fd1\u304f\u306b\u3042\u308b: ");
        ja.put("ruins_etc", " \u306a\u3069...");
        ja.put("no_stand", "\u30a8\u30b3\u30fc\u30baACT3\u3092\u6301\u3063\u3066\u3044\u306a\u3044\u306e\u3067\u3001\u8a71\u305b\u307e\u305b\u3093\u3002");
        ja.put("random1", "\u3046\u3093\u3046\u3093\u3001\u805e\u3044\u3066\u308b\u3002");
        ja.put("random2", "\u305d\u308c\u3067\uff1f");
        ja.put("random3", "\u672c\u5f53\u306b\uff1f");
        ja.put("random4", "\u3078\u3047\u3001\u305d\u3046\u306a\u3093\u3060\u3002");
        ja.put("random5", "\u9762\u767d\u3044\u306d\u3002");
        ja.put("random6", "\u3088\u304f\u308f\u304b\u3089\u306a\u3044\u306a\u3002");
        ja.put("random7", "\u3069\u3046\u601d\u3046\uff1f");
        ja.put("random8", "\u304b\u3082\u3057\u308c\u306a\u3044\u306d\u3002");
        ja.put("random9", "\u8ab0\u306b\u3082\u308f\u304b\u3089\u306a\u3044\u3088\u3002");
        ja.put("random10", "\u306f\u306f\u3002");
        ja.put("random11", "\u308f\u304b\u3063\u305f\u3002");
        ja.put("random12", "\u306a\u308b\u307b\u3069\u3002");
        ja.put("random13", "\u4e86\u89e3\u3002");
        ja.put("random14", "\u7d9a\u3051\u3066\u3002");
        ja.put("random15", "\u8003\u3048\u4e8b\u3057\u3066\u305f\u3002");
        ja.put("random16", "\u305d\u3046\u306a\u306e\uff1f");
        ja.put("random17", "\u307e\u3055\u304b\uff1f");
        ja.put("random18", "\u3042\u3089\u3089\u3002");
        ja.put("random19", "\u3046\u3093\u3046\u3093\u3002");
        ja.put("random20", "\u3044\u3044\u3088\u3002");
        ja.put("random21", "\u597d\u304d\u306b\u3057\u3066\u3002");
        ja.put("random22", "\u3069\u3063\u3061\u3067\u3082\u3044\u3044\u3088\u3002");
        ja.put("random23", "\u8ae6\u3081\u306a\u3044\u3067\u3002");
        ja.put("random24", "\u81ea\u5206\u3092\u4fe1\u3058\u3066\u3002");
        ja.put("random25", "\u9811\u5f35\u308c\u3070\u3044\u3044\u3002");
        ja.put("random26", "\u6b63\u3057\u3044\u3088\u3002");
        ja.put("random27", "\u305d\u308c\u3082\u305d\u3046\u3060\u306d\u3002");
        ja.put("random28", "\u78ba\u304b\u306b\u3002");
        ja.put("random29", "\u305d\u306e\u901a\u308a\u3002");
        LANGUAGE_REPLIES.put("ja", ja);
        HashMap<String, String> ko = new HashMap<String, String>();
        ko.put("greeting", "\uc548\ub155, \ub098\ub294 \uc5d0\ucf54\uc988 ACT3\uc57c.");
        ko.put("whats_up", "\ubb34\uc2a8 \uc77c\uc774\uc57c?");
        ko.put("listening", "\ub4e3\uace0 \uc788\uc5b4.");
        ko.put("go_ahead", "\ub9d0\ud574\ubd10.");
        ko.put("huh", "\uc751?");
        ko.put("here_we_go", "\ub610 \uc654\ub124.");
        ko.put("got_it", "\uc54c\uc558\uc5b4.");
        ko.put("okay", "\uc88b\uc544.");
        ko.put("roger", "\uc218\uc2e0 \uc644\ub8cc.");
        ko.put("mm", "\uc74c.");
        ko.put("alright", "\uc88b\uc544.");
        ko.put("sure", "\ubb3c\ub860\uc774\uc9c0.");
        ko.put("no_problem", "\ubb38\uc81c \uc5c6\uc5b4.");
        ko.put("confused1", "\ubb34\uc2a8 \ub73b\uc778\uc9c0 \ubaa8\ub974\uaca0\uc5b4.");
        ko.put("confused2", "\ub354 \uba85\ud655\ud558\uac8c \ub9d0\ud574\uc904\ub798?");
        ko.put("confused3", "\ubabb \ub4e4\uc5c8\uc5b4.");
        ko.put("confused4", "\ubb34\uc2a8 \ub73b\uc774\uc57c?");
        ko.put("confused5", "\ub2e4\uc2dc \ub9d0\ud574\ubd10.");
        ko.put("confused6", "\uc774\ud574\uac00 \uc548 \ub3fc.");
        ko.put("how_to_play", "\uc5bc\uc74c \ud3c9\uc6d0\uc758 \uc6b4\uc11d \uad6c\ub369\uc774\uc5d0\uc11c \uc2a4\ud0e0\ub4dc \ud654\uc0b4\uc744 \ub9cc\ub4e4\uace0, \uc0b0\uc758 \ud30c\ub3d9 \ud0c0\uc6cc\uc5d0\uc11c \ud30c\ub3d9\uc744 \ubc30\uc6b0\uace0, \uc815\uae00\uc758 \uae30\ub465\uc758 \ub0a8\uc790 \uc720\uc801\uc5d0\uc11c \uc11d\uac00\uba74\uc744 \ucc3e\uc544 \ubc40\ud30c\uc774\uc5b4\uac00 \ub418\uc790.");
        ko.put("hamon", "\uc0b0\uc545 \uc9c0\ud615\uc73c\ub85c \uac00\uc11c \uc804\uc124\uc758 \ud30c\ub3d9 \ud0c0\uc6cc\ub97c \ucc3e\uc544\ub77c. \uadf8 \uc548\uc5d0 \ud30c\ub3d9\uc758 \ub2ec\uc778\uc774 \uc788\uc5b4, \uadf8\uc5d0\uac8c \ubc30\uc6b0\uba74 \ud30c\ub3d9\uc758 \ud798\uc744 \uc5bb\uc744 \uc218 \uc788\ub2e4.");
        ko.put("vampire", "\uc815\uae00 \uae4a\uc219\ud55c \uacf3\uc5d0 \uace0\ub300 \uae30\ub465\uc758 \ub0a8\uc790 \uc720\uc801\uc774 \uc788\ub2e4\uace0 \ud55c\ub2e4. \uc11d\uac00\uba74\uc744 \ucc3e\uc544 \uc4f0\uace0, \uce7c\ub85c \uc790\uc2e0\uc744 \ubca0\uba74 \ubc40\ud30c\uc774\uc5b4\uac00 \ub420 \uc218 \uc788\ub2e4.");
        ko.put("stand", "\ucd94\uc6b4 \uacf3\uc5d0 \uc720\uc131\uc774 \ub5a8\uc5b4\uc9c4 \ud754\uc801\uc774 \uc788\ub2e4... \uc6b4\uc11d \uad6c\ub369\uc774\ub97c \ucc3e\uc544 \uadf8 \uc548\uc758 \uc6b4\uc11d \ucf54\uc5b4\ub85c \uc2a4\ud0e0\ub4dc \ud654\uc0b4\uc744 \ub9cc\ub4e4\uc790. \uc2a4\ud0e0\ub4dc \ud654\uc0b4\uc744 \uc790\uc2e0\uc5d0\uac8c \uc0ac\uc6a9\ud558\uba74 \uc2a4\ud0e0\ub4dc\ub97c \uc5bb\uc744 \uc218 \uc788\ub2e4!");
        ko.put("echoes", "\uc5d0\ucf54\uc988 ACT2\ub294 \uc9c4\ud654 \uc2a4\ud0ac\uc744 \uc0ac\uc6a9\ud558\uc5ec ACT3\ub85c \uc9c4\ud654\ud560 \uc218 \uc788\ub2e4. \uc815\uc2e0 \uc5d0\ub108\uc9c0\ub97c \uc18c\ube44\ud55c\ub2e4.");
        ko.put("no_eat", "\ub098\ub294 \uc2a4\ud0e0\ub4dc\ub77c\uc11c \ubc25\uc744 \uba39\uc744 \ud544\uc694\uac00 \uc5c6\uc5b4.");
        ko.put("no_sleep", "\uc2a4\ud0e0\ub4dc\ub294 \uc798 \ud544\uc694\uac00 \uc5c6\uc5b4.");
        ko.put("no_tired", "\uc2a4\ud0e0\ub4dc\ub294 \ud53c\uace4\ud558\uc9c0 \uc54a\uc544.");
        ko.put("weather", "\ub098\ub294 \uc2a4\ud0e0\ub4dc\ub77c\uc11c \ub0a0\uc528\ub97c \ub290\ub084 \uc218 \uc5c6\uc5b4. \uc9c1\uc811 \ubc16\uc744 \ubd10.");
        ko.put("love", "\uc5b4?");
        ko.put("handsome", "\uace0\ub9c8\uc6cc.");
        ko.put("beautiful", "\uce6d\ucc2c \uace0\ub9c8\uc6cc.");
        ko.put("cute", "...\ubb50\ub77c\uace0?");
        ko.put("ruins_none", "\uadfc\ucc98\uc5d0 \ud2b9\ubcc4\ud55c \uc9c0\ud615\uc774 \ubc1c\uacac\ub418\uc9c0 \uc54a\uc558\uc5b4.");
        ko.put("ruins_nearby", "\uadfc\ucc98\uc5d0: ");
        ko.put("ruins_etc", " \ub4f1...");
        ko.put("no_stand", "\uc5d0\ucf54\uc988 ACT3 \uc2a4\ud0e0\ub4dc\uac00 \uc5c6\uc5b4\uc11c \ub300\ud654\ud560 \uc218 \uc5c6\uc5b4.");
        ko.put("random1", "\uc74c\uc74c, \ub4e3\uace0 \uc788\uc5b4.");
        ko.put("random2", "\uadf8\ub798\uc11c?");
        ko.put("random3", "\uc815\ub9d0?");
        ko.put("random4", "\uc640, \uadf8\ub807\uad6c\ub098.");
        ko.put("random5", "\uc7ac\ubbf8\uc788\ub124.");
        ko.put("random6", "\uc798 \ubaa8\ub974\uaca0\uc5b4.");
        ko.put("random7", "\ub108\ub294 \uc5b4\ub5bb\uac8c \uc0dd\uac01\ud574?");
        ko.put("random8", "\uc544\ub9c8\ub3c4.");
        ko.put("random9", "\ub204\uac00 \uc54c\uaca0\uc5b4.");
        ko.put("random10", "\ud558\ud558.");
        ko.put("random11", "\uc54c\uc558\uc5b4.");
        ko.put("random12", "\uadf8\ub807\uad6c\ub098.");
        ko.put("random13", "\uc774\ud574\ud588\uc5b4.");
        ko.put("random14", "\uacc4\uc18d \ub9d0\ud574.");
        ko.put("random15", "\uc0dd\uac01\ud558\uace0 \uc788\uc5c8\uc5b4.");
        ko.put("random16", "\uadf8\ub798?");
        ko.put("random17", "\uc124\ub9c8?");
        ko.put("random18", "\uc138\uc0c1\uc5d0.");
        ko.put("random19", "\uc74c\ud760.");
        ko.put("random20", "\uc88b\uc544.");
        ko.put("random21", "\uc544\ubb34\uac70\ub098.");
        ko.put("random22", "\ub458 \ub2e4 \uad1c\ucc2e\uc544.");
        ko.put("random23", "\ud3ec\uae30\ud558\uc9c0 \ub9c8.");
        ko.put("random24", "\uc790\uc2e0\uc744 \ubbff\uc5b4.");
        ko.put("random25", "\ub178\ub825\ud558\uba74 \ub3fc.");
        ko.put("random26", "\ub9de\uc544.");
        ko.put("random27", "\uc77c\ub9ac\uac00 \uc788\uc5b4.");
        ko.put("random28", "\ud655\uc2e4\ud788.");
        ko.put("random29", "\ub9de\uc544.");
        LANGUAGE_REPLIES.put("ko", ko);
        HashMap<String, String> ru = new HashMap<String, String>();
        ru.put("greeting", "\u041f\u0440\u0438\u0432\u0435\u0442, \u044f Echoes ACT3.");
        ru.put("whats_up", "\u0427\u0442\u043e \u0441\u043b\u0443\u0447\u0438\u043b\u043e\u0441\u044c?");
        ru.put("listening", "\u042f \u0441\u043b\u0443\u0448\u0430\u044e.");
        ru.put("go_ahead", "\u0414\u0430\u0432\u0430\u0439.");
        ru.put("huh", "\u0410?");
        ru.put("here_we_go", "\u041e\u043f\u044f\u0442\u044c.");
        ru.put("got_it", "\u041f\u043e\u043d\u044f\u043b.");
        ru.put("okay", "\u0425\u043e\u0440\u043e\u0448\u043e.");
        ru.put("roger", "\u0422\u0430\u043a \u0442\u043e\u0447\u043d\u043e.");
        ru.put("mm", "\u041c\u043c.");
        ru.put("alright", "\u041b\u0430\u0434\u043d\u043e.");
        ru.put("sure", "\u041a\u043e\u043d\u0435\u0447\u043d\u043e.");
        ru.put("no_problem", "\u0411\u0435\u0437 \u043f\u0440\u043e\u0431\u043b\u0435\u043c.");
        ru.put("confused1", "\u042f \u043d\u0435 \u0441\u043e\u0432\u0441\u0435\u043c \u043f\u043e\u043d\u0438\u043c\u0430\u044e, \u0447\u0442\u043e \u0442\u044b \u0438\u043c\u0435\u0435\u0448\u044c \u0432 \u0432\u0438\u0434\u0443.");
        ru.put("confused2", "\u041c\u043e\u0436\u0435\u0448\u044c \u0441\u043a\u0430\u0437\u0430\u0442\u044c \u043f\u043e\u043d\u044f\u0442\u043d\u0435\u0435?");
        ru.put("confused3", "\u042f \u043d\u0435 \u0440\u0430\u0441\u0441\u043b\u044b\u0448\u0430\u043b.");
        ru.put("confused4", "\u0427\u0442\u043e \u0442\u044b \u0438\u043c\u0435\u0435\u0448\u044c \u0432 \u0432\u0438\u0434\u0443?");
        ru.put("confused5", "\u041f\u043e\u0432\u0442\u043e\u0440\u0438?");
        ru.put("confused6", "\u042f \u043d\u0435 \u043f\u043e\u043d\u0438\u043c\u0430\u044e.");
        ru.put("how_to_play", "\u0418\u0434\u0438 \u0432 \u043b\u0435\u0434\u044f\u043d\u043e\u0439 \u0431\u0438\u043e\u043c, \u043d\u0430\u0439\u0434\u0438 \u043c\u0435\u0442\u0435\u043e\u0440\u0438\u0442\u043d\u044b\u0439 \u043a\u0440\u0430\u0442\u0435\u0440 \u0438 \u0441\u0434\u0435\u043b\u0430\u0439 \u0421\u0442\u0440\u0435\u043b\u0443 \u0421\u0442\u0435\u043d\u0434\u0430. \u041d\u0430\u0439\u0434\u0438 \u0411\u0430\u0448\u043d\u044e \u0425\u0430\u043c\u043e\u043d\u0430 \u0432 \u0433\u043e\u0440\u0430\u0445 \u0438 \u043d\u0430\u0443\u0447\u0438\u0441\u044c \u0425\u0430\u043c\u043e\u043d\u0443. \u041d\u0430\u0439\u0434\u0438 \u041a\u0430\u043c\u0435\u043d\u043d\u0443\u044e \u041c\u0430\u0441\u043a\u0443 \u0432 \u0440\u0443\u0438\u043d\u0430\u0445 \u0421\u0442\u043e\u043b\u043f\u043e\u0432\u044b\u0445 \u041b\u044e\u0434\u0435\u0439 \u0432 \u0434\u0436\u0443\u043d\u0433\u043b\u044f\u0445 \u0438 \u0441\u0442\u0430\u043d\u044c \u0432\u0430\u043c\u043f\u0438\u0440\u043e\u043c.");
        ru.put("hamon", "\u041e\u0442\u043f\u0440\u0430\u0432\u043b\u044f\u0439\u0441\u044f \u0432 \u0433\u043e\u0440\u044b \u0438 \u043d\u0430\u0439\u0434\u0438 \u043b\u0435\u0433\u0435\u043d\u0434\u0430\u0440\u043d\u0443\u044e \u0411\u0430\u0448\u043d\u044e \u0425\u0430\u043c\u043e\u043d\u0430. \u0412\u043d\u0443\u0442\u0440\u0438 \u0435\u0441\u0442\u044c \u043c\u0430\u0441\u0442\u0435\u0440 \u0425\u0430\u043c\u043e\u043d\u0430, \u043a\u043e\u0442\u043e\u0440\u044b\u0439 \u043d\u0430\u0443\u0447\u0438\u0442 \u0442\u0435\u0431\u044f \u0441\u0438\u043b\u0435 \u0425\u0430\u043c\u043e\u043d\u0430.");
        ru.put("vampire", "\u041e\u0442\u043f\u0440\u0430\u0432\u043b\u044f\u0439\u0441\u044f \u0432 \u0433\u0443\u0441\u0442\u044b\u0435 \u0434\u0436\u0443\u043d\u0433\u043b\u0438, \u0442\u0430\u043c \u0435\u0441\u0442\u044c \u0434\u0440\u0435\u0432\u043d\u0438\u0435 \u0440\u0443\u0438\u043d\u044b \u0421\u0442\u043e\u043b\u043f\u043e\u0432\u044b\u0445 \u041b\u044e\u0434\u0435\u0439. \u041d\u0430\u0439\u0434\u0438 \u041a\u0430\u043c\u0435\u043d\u043d\u0443\u044e \u041c\u0430\u0441\u043a\u0443, \u043d\u0430\u0434\u0435\u043d\u044c \u0435\u0451 \u0438 \u043f\u043e\u0440\u0435\u0436\u044c \u0441\u0435\u0431\u044f \u043d\u043e\u0436\u043e\u043c, \u0447\u0442\u043e\u0431\u044b \u0441\u0442\u0430\u0442\u044c \u0432\u0430\u043c\u043f\u0438\u0440\u043e\u043c.");
        ru.put("stand", "\u041e\u0442\u043f\u0440\u0430\u0432\u043b\u044f\u0439\u0441\u044f \u0432 \u0445\u043e\u043b\u043e\u0434\u043d\u043e\u0435 \u043c\u0435\u0441\u0442\u043e, \u0442\u0443\u0434\u0430 \u0443\u043f\u0430\u043b \u043c\u0435\u0442\u0435\u043e\u0440\u0438\u0442... \u041d\u0430\u0439\u0434\u0438 \u043a\u0440\u0430\u0442\u0435\u0440, \u0438\u0437 \u043c\u0435\u0442\u0435\u043e\u0440\u0438\u0442\u043d\u043e\u0433\u043e \u044f\u0434\u0440\u0430 \u043c\u043e\u0436\u043d\u043e \u0441\u0434\u0435\u043b\u0430\u0442\u044c \u0421\u0442\u0440\u0435\u043b\u0443 \u0421\u0442\u0435\u043d\u0434\u0430. \u0418\u0441\u043f\u043e\u043b\u044c\u0437\u0443\u0439 \u0421\u0442\u0440\u0435\u043b\u0443 \u0421\u0442\u0435\u043d\u0434\u0430 \u043d\u0430 \u0441\u0435\u0431\u0435, \u0447\u0442\u043e\u0431\u044b \u043f\u043e\u043b\u0443\u0447\u0438\u0442\u044c \u0421\u0442\u0435\u043d\u0434!");
        ru.put("echoes", "Echoes ACT2 \u043c\u043e\u0436\u0435\u0442 \u044d\u0432\u043e\u043b\u044e\u0446\u0438\u043e\u043d\u0438\u0440\u043e\u0432\u0430\u0442\u044c \u0432 ACT3, \u0438\u0441\u043f\u043e\u043b\u044c\u0437\u0443\u044f \u043d\u0430\u0432\u044b\u043a \u044d\u0432\u043e\u043b\u044e\u0446\u0438\u0438. \u0422\u0440\u0435\u0431\u0443\u0435\u0442\u0441\u044f \u044d\u043d\u0435\u0440\u0433\u0438\u044f \u0420\u0435\u0448\u0438\u043c\u043e\u0441\u0442\u0438.");
        ru.put("no_eat", "\u042f \u0421\u0442\u0435\u043d\u0434, \u043c\u043d\u0435 \u043d\u0435 \u043d\u0443\u0436\u043d\u043e \u0435\u0441\u0442\u044c.");
        ru.put("no_sleep", "\u0421\u0442\u0435\u043d\u0434\u0430\u043c \u043d\u0435 \u043d\u0443\u0436\u0435\u043d \u0441\u043e\u043d.");
        ru.put("no_tired", "\u0421\u0442\u0435\u043d\u0434\u044b \u043d\u0435 \u0443\u0441\u0442\u0430\u044e\u0442.");
        ru.put("weather", "\u042f \u0421\u0442\u0435\u043d\u0434, \u044f \u043d\u0435 \u0447\u0443\u0432\u0441\u0442\u0432\u0443\u044e \u043f\u043e\u0433\u043e\u0434\u0443. \u041f\u043e\u0441\u043c\u043e\u0442\u0440\u0438 \u0441\u0430\u043c.");
        ru.put("love", "\u0410?");
        ru.put("handsome", "\u0421\u043f\u0430\u0441\u0438\u0431\u043e.");
        ru.put("beautiful", "\u0421\u043f\u0430\u0441\u0438\u0431\u043e \u0437\u0430 \u043a\u043e\u043c\u043f\u043b\u0438\u043c\u0435\u043d\u0442.");
        ru.put("cute", "...\u0427\u0442\u043e?");
        ru.put("ruins_none", "\u0420\u044f\u0434\u043e\u043c \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u043e \u043d\u0438\u0447\u0435\u0433\u043e \u043e\u0441\u043e\u0431\u0435\u043d\u043d\u043e\u0433\u043e.");
        ru.put("ruins_nearby", "\u0420\u044f\u0434\u043e\u043c: ");
        ru.put("ruins_etc", " \u0438 \u0434\u0440...");
        ru.put("no_stand", "\u0423 \u0442\u0435\u0431\u044f \u043d\u0435\u0442 \u0421\u0442\u0435\u043d\u0434\u0430 Echoes ACT3, \u044f \u043d\u0435 \u043c\u043e\u0433\u0443 \u0441 \u0442\u043e\u0431\u043e\u0439 \u0433\u043e\u0432\u043e\u0440\u0438\u0442\u044c.");
        ru.put("random1", "\u041c\u043c\u043c, \u044f \u0441\u043b\u0443\u0448\u0430\u044e.");
        ru.put("random2", "\u0418 \u0447\u0442\u043e \u0434\u0430\u043b\u044c\u0448\u0435?");
        ru.put("random3", "\u041f\u0440\u0430\u0432\u0434\u0430?");
        ru.put("random4", "\u0412\u0430\u0443, \u0432\u043e\u0442 \u043a\u0430\u043a.");
        ru.put("random5", "\u042d\u0442\u043e \u0438\u043d\u0442\u0435\u0440\u0435\u0441\u043d\u043e.");
        ru.put("random6", "\u042f \u043d\u0435 \u0441\u043e\u0432\u0441\u0435\u043c \u043f\u043e\u043d\u0438\u043c\u0430\u044e \u044d\u0442\u043e.");
        ru.put("random7", "\u041a\u0430\u043a \u0434\u0443\u043c\u0430\u0435\u0448\u044c?");
        ru.put("random8", "\u041c\u043e\u0436\u0435\u0442 \u0431\u044b\u0442\u044c.");
        ru.put("random9", "\u041a\u0442\u043e \u0437\u043d\u0430\u0435\u0442.");
        ru.put("random10", "\u0425\u0430-\u0445\u0430.");
        ru.put("random11", "\u041b\u0430\u0434\u043d\u043e.");
        ru.put("random12", "\u041f\u043e\u043d\u044f\u0442\u043d\u043e.");
        ru.put("random13", "\u042f\u0441\u043d\u043e.");
        ru.put("random14", "\u041f\u0440\u043e\u0434\u043e\u043b\u0436\u0430\u0439.");
        ru.put("random15", "\u042f \u0434\u0443\u043c\u0430\u043b.");
        ru.put("random16", "\u041f\u0440\u0430\u0432\u0434\u0430?");
        ru.put("random17", "\u041d\u0435 \u043c\u043e\u0436\u0435\u0442 \u0431\u044b\u0442\u044c?");
        ru.put("random18", "\u041e \u0431\u043e\u0436\u0435.");
        ru.put("random19", "\u0410\u0433\u0430.");
        ru.put("random20", "\u0425\u043e\u0440\u043e\u0448\u043e.");
        ru.put("random21", "\u041a\u0430\u043a \u0445\u043e\u0447\u0435\u0448\u044c.");
        ru.put("random22", "\u041e\u0431\u0430 \u0432\u0430\u0440\u0438\u0430\u043d\u0442\u0430 \u043f\u043e\u0434\u0445\u043e\u0434\u044f\u0442.");
        ru.put("random23", "\u041d\u0435 \u0441\u0434\u0430\u0432\u0430\u0439\u0441\u044f.");
        ru.put("random24", "\u0412\u0435\u0440\u044c \u0432 \u0441\u0435\u0431\u044f.");
        ru.put("random25", "\u041f\u0440\u043e\u0441\u0442\u043e \u0441\u0442\u0430\u0440\u0430\u0439\u0441\u044f.");
        ru.put("random26", "\u0422\u044b \u043f\u0440\u0430\u0432.");
        ru.put("random27", "\u042d\u0442\u043e \u0438\u043c\u0435\u0435\u0442 \u0441\u043c\u044b\u0441\u043b.");
        ru.put("random28", "\u0414\u0435\u0439\u0441\u0442\u0432\u0438\u0442\u0435\u043b\u044c\u043d\u043e.");
        ru.put("random29", "\u0418\u043c\u0435\u043d\u043d\u043e.");
        LANGUAGE_REPLIES.put("ru", ru);
        HashMap<String, String> en = new HashMap<String, String>();
        en.put("greeting", "Hello, I'm Echoes ACT3.");
        en.put("whats_up", "What's up?");
        en.put("listening", "I'm listening.");
        en.put("go_ahead", "Go ahead.");
        en.put("huh", "Huh?");
        en.put("here_we_go", "Here we go again.");
        en.put("got_it", "Got it.");
        en.put("okay", "Okay.");
        en.put("roger", "Roger that.");
        en.put("mm", "Mm.");
        en.put("alright", "Alright.");
        en.put("sure", "Sure.");
        en.put("no_problem", "No problem.");
        en.put("confused1", "I don't quite get what you mean.");
        en.put("confused2", "Can you be more clear?");
        en.put("confused3", "I didn't catch that.");
        en.put("confused4", "What do you mean?");
        en.put("confused5", "Say that again?");
        en.put("confused6", "I don't understand.");
        en.put("how_to_play", "Go to the ice biome, find the meteor crater and craft the Stand Arrow. Go to the Hamon Tower in the mountains and learn Hamon. Find the Stone Mask in the Pillar Men ruins in the jungle and become a vampire.");
        en.put("hamon", "Go to the mountains and find the legendary Hamon Tower. There's a Hamon master inside who can teach you the power of Hamon.");
        en.put("vampire", "Go to the dense jungle, there seems to be an ancient Pillar Men ruin. Find the Stone Mask, put it on and cut yourself with a knife to become a vampire.");
        en.put("stand", "Go to a cold place, a meteor seems to have fallen there... Find the crater, the meteor core can be used to craft a Stand Arrow. Use the Stand Arrow on yourself to get a Stand!");
        en.put("echoes", "Echoes ACT2 can evolve to ACT3 by using the evolve ability. Costs Resolve energy.");
        en.put("no_eat", "I'm a Stand, I don't need to eat.");
        en.put("no_sleep", "Stands don't sleep.");
        en.put("no_tired", "Stands don't get tired.");
        en.put("weather", "I'm a Stand, I can't feel the weather. Look outside yourself.");
        en.put("love", "Huh?");
        en.put("handsome", "Thanks.");
        en.put("beautiful", "Thanks for the compliment.");
        en.put("cute", "...What did you say?");
        en.put("ruins_none", "No special terrain found nearby.");
        en.put("ruins_nearby", "Nearby: ");
        en.put("ruins_etc", " etc...");
        en.put("no_stand", "You don't have Echoes ACT3 Stand, I can't talk to you.");
        en.put("random1", "Mm, I'm listening.");
        en.put("random2", "And then?");
        en.put("random3", "Really?");
        en.put("random4", "Wow, I see.");
        en.put("random5", "That's interesting.");
        en.put("random6", "I don't really get this.");
        en.put("random7", "What do you think?");
        en.put("random8", "Maybe.");
        en.put("random9", "Who knows.");
        en.put("random10", "Haha.");
        en.put("random11", "Alright.");
        en.put("random12", "I see.");
        en.put("random13", "Got it.");
        en.put("random14", "Go on.");
        en.put("random15", "I'm thinking.");
        en.put("random16", "Is that so?");
        en.put("random17", "No way?");
        en.put("random18", "Oh my.");
        en.put("random19", "Uh huh.");
        en.put("random20", "Fine.");
        en.put("random21", "Whatever.");
        en.put("random22", "Either is fine.");
        en.put("random23", "Don't lose hope.");
        en.put("random24", "Believe in yourself.");
        en.put("random25", "Just do your best.");
        en.put("random26", "You're right.");
        en.put("random27", "That makes sense.");
        en.put("random28", "Indeed.");
        en.put("random29", "Exactly.");
        LANGUAGE_REPLIES.put("en", en);
        KEYWORD_MAP = new HashMap<String, String>();
        KEYWORD_MAP.put("\u600e\u4e48\u73a9", "how_to_play");
        KEYWORD_MAP.put("\u600e\u4e48\u5f00\u59cb", "how_to_play");
        KEYWORD_MAP.put("\u65b0\u624b", "how_to_play");
        KEYWORD_MAP.put("\u653b\u7565", "how_to_play");
        KEYWORD_MAP.put("\u6ce2\u7eb9", "hamon");
        KEYWORD_MAP.put("\u6ce2\u7d0b", "hamon");
        KEYWORD_MAP.put("\u5438\u8840\u9b3c", "vampire");
        KEYWORD_MAP.put("\u66ff\u8eab", "stand");
        KEYWORD_MAP.put("\u56de\u97f3", "echoes");
        KEYWORD_MAP.put("\u5403\u98ef", "no_eat");
        KEYWORD_MAP.put("\u7761\u89c9", "no_sleep");
        KEYWORD_MAP.put("\u7d2f", "no_tired");
        KEYWORD_MAP.put("\u9057\u8ff9", "ruins_none");
        KEYWORD_MAP.put("how to play", "how_to_play");
        KEYWORD_MAP.put("hamon", "hamon");
        KEYWORD_MAP.put("vampire", "vampire");
        KEYWORD_MAP.put("stand", "stand");
        KEYWORD_MAP.put("echoes", "echoes");
        KEYWORD_MAP.put("eat", "no_eat");
        KEYWORD_MAP.put("sleep", "no_sleep");
        KEYWORD_MAP.put("tired", "no_tired");
        KEYWORD_MAP.put("ruin", "ruins_none");
        KEYWORD_MAP.put("\u904a\u3073\u65b9", "how_to_play");
        KEYWORD_MAP.put("\u6ce2\u7d0b", "hamon");
        KEYWORD_MAP.put("\u5438\u8840\u9b3c", "vampire");
        KEYWORD_MAP.put("\u30b9\u30bf\u30f3\u30c9", "stand");
        KEYWORD_MAP.put("\u30a8\u30b3\u30fc\u30ba", "echoes");
        KEYWORD_MAP.put("\u98df\u3079\u308b", "no_eat");
        KEYWORD_MAP.put("\u7720\u308b", "no_sleep");
        KEYWORD_MAP.put("\u75b2\u308c\u305f", "no_tired");
        KEYWORD_MAP.put("\u907a\u8de1", "ruins_none");
        KEYWORD_MAP.put("\uc5b4\ub5bb\uac8c \ud574", "how_to_play");
        KEYWORD_MAP.put("\ud30c\ub3d9", "hamon");
        KEYWORD_MAP.put("\ubc40\ud30c\uc774\uc5b4", "vampire");
        KEYWORD_MAP.put("\uc2a4\ud0e0\ub4dc", "stand");
        KEYWORD_MAP.put("\uc5d0\ucf54\uc988", "echoes");
        KEYWORD_MAP.put("\uba39", "no_eat");
        KEYWORD_MAP.put("\uc790", "no_sleep");
        KEYWORD_MAP.put("\ud53c\uace4", "no_tired");
        KEYWORD_MAP.put("\uc720\uc801", "ruins_none");
    }
}
