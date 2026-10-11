package rotp.core.client.entityanim.molang;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import team.unnamed.mocha.lexer.MolangLexer;
import team.unnamed.mocha.lexer.Token;
import team.unnamed.mocha.lexer.TokenKind;

/** Own-side workaround for Mocha 3.0.1 binding prefix minus before call postfixes. */
final class MolangCallNegation {
    private MolangCallNegation() {}

    static String normalize(String source) throws IOException {
        if (source.indexOf('-') < 0 || source.indexOf('(') < 0) return source;
        List<Token> tokens = MolangLexer.tokenizeAll(source);
        int[] ends = matchingEnds(tokens);
        if (ends == null) return source;
        int[] openings = null;
        int[] closings = null;
        int pairs = 0;
        for (int i = 0; i + 1 < tokens.size(); i++) {
            if (tokens.get(i).kind() != TokenKind.SUB
                    || i > 0 && endsOperand(tokens.get(i - 1).kind())) continue;
            int end = callOperandEnd(tokens, ends, i + 1);
            if (end < 0) continue;
            int startOffset = tokens.get(i + 1).start();
            int endOffset = tokens.get(end).end();
            if (startOffset < 0 || endOffset < startOffset || endOffset > source.length()) return source;
            if (openings == null) {
                openings = new int[source.length() + 1];
                closings = new int[source.length() + 1];
            }
            openings[startOffset]++;
            closings[endOffset]++;
            pairs++;
        }
        if (pairs == 0) return source;
        StringBuilder result = new StringBuilder(source.length() + pairs * 2);
        for (int i = 0; i <= source.length(); i++) {
            result.append(")".repeat(closings[i]));
            result.append("(".repeat(openings[i]));
            if (i < source.length()) result.append(source.charAt(i));
        }
        return result.toString();
    }

    private static int[] matchingEnds(List<Token> tokens) {
        int[] ends = new int[tokens.size()];
        Arrays.fill(ends, -1);
        int[] stack = new int[tokens.size()];
        int depth = 0;
        for (int i = 0; i < tokens.size(); i++) {
            TokenKind kind = tokens.get(i).kind();
            if (kind == TokenKind.ERROR) return null;
            // Access after a call/group/index is also outside this parser release's grammar.
            if (kind == TokenKind.DOT && i > 0
                    && (tokens.get(i - 1).kind() == TokenKind.RPAREN
                    || tokens.get(i - 1).kind() == TokenKind.RBRACKET
                    || tokens.get(i - 1).kind() == TokenKind.RBRACE)) return null;
            // This release has no comment grammar. Preserve its original unsupported-input contract.
            if (kind == TokenKind.SLASH && i + 1 < tokens.size()
                    && (tokens.get(i + 1).kind() == TokenKind.SLASH
                    || tokens.get(i + 1).kind() == TokenKind.STAR)) return null;
            switch (kind) {
                case LPAREN, LBRACKET, LBRACE -> stack[depth++] = i;
                case RPAREN, RBRACKET, RBRACE -> {
                    if (depth == 0) return null;
                    int open = stack[--depth];
                    TokenKind expected = switch (tokens.get(open).kind()) {
                        case LPAREN -> TokenKind.RPAREN;
                        case LBRACKET -> TokenKind.RBRACKET;
                        case LBRACE -> TokenKind.RBRACE;
                        default -> throw new IllegalStateException();
                    };
                    if (kind != expected) return null;
                    ends[open] = i;
                }
                default -> {}
            }
        }
        return depth == 0 ? ends : null;
    }

    private static boolean endsOperand(TokenKind kind) {
        return switch (kind) {
            case IDENTIFIER, FLOAT, STRING, TRUE, FALSE, RPAREN, RBRACKET, RBRACE -> true;
            default -> false;
        };
    }

    private static int callOperandEnd(List<Token> tokens, int[] ends, int start) {
        int end;
        if (tokens.get(start).kind() == TokenKind.IDENTIFIER) end = start;
        else if (tokens.get(start).kind() == TokenKind.LPAREN
                || tokens.get(start).kind() == TokenKind.LBRACE) end = ends[start];
        else return -1;
        boolean call = false;
        // Wrap the entire postfix operand, not the surrounding arithmetic or an explicitly negated callee.
        while (end >= 0 && end + 1 < tokens.size()) {
            int next = end + 1;
            switch (tokens.get(next).kind()) {
                case DOT -> {
                    if (next + 1 >= tokens.size() || tokens.get(next + 1).kind() != TokenKind.IDENTIFIER) return -1;
                    end = next + 1;
                }
                case LPAREN, LBRACKET -> {
                    if (tokens.get(next).kind() == TokenKind.LPAREN) call = true;
                    end = ends[next];
                }
                default -> { return call ? end : -1; }
            }
        }
        return call ? end : -1;
    }
}
