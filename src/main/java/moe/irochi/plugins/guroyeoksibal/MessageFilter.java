package moe.irochi.plugins.guroyeoksibal;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntPredicate;

public final class MessageFilter {

    public record Result(String replacement, String matchedPattern) {}

    private record Variant(String text, int[] toNorm, boolean[] head) {}

    private static final String[] JONG_TO_CHO = {
            "",
            "\u1100", // ㄱ
            "\u1101", // ㄲ
            "\u1100\u1109", // ㄳ
            "\u1102", // ㄴ
            "\u1102\u110C", // ㄵ
            "\u1102\u1112", // ㄶ
            "\u1103", // ㄷ
            "\u1105", // ㄹ
            "\u1105\u1100", // ㄺ
            "\u1105\u1106", // ㄻ
            "\u1105\u1107", // ㄼ
            "\u1105\u1109", // ㄽ
            "\u1105\u1110", // ㄾ
            "\u1105\u1111", // ㄿ
            "\u1105\u1112", // ㅀ
            "\u1106", // ㅁ
            "\u1107", // ㅂ
            "\u1107\u1109", // ㅄ
            "\u1109", // ㅅ
            "\u110A", // ㅆ
            "\u110B", // ㅇ
            "\u110C", // ㅈ
            "\u110E", // ㅊ
            "\u110F", // ㅋ
            "\u1110", // ㅌ
            "\u1111", // ㅍ
            "\u1112", // ㅎ
    };

    private MessageFilter() {}

    public static String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    private static List<String> foldRoots(String base) {
        return List.of(base, foldLeet(base), foldHangulLeet(base));
    }

    public static List<String> patternVariants(String word) {
        String normalized = normalize(word);
        String base = fold(normalized).text();
        LinkedHashSet<String> variants = new LinkedHashSet<>();
        for (String root : foldRoots(base)) {
            variants.add(root);
            variants.add(stripText(root));
            variants.add(dedupeText(root));
        }
        variants.remove("");
        return List.copyOf(variants);
    }

    public static Result filter(String message, AhoCorasick matcher, boolean replaceMode, char replaceChar) {
        String normalized = normalize(message);
        Variant base = fold(normalized);

        List<Variant> variants = new ArrayList<>(9);
        Set<String> seen = new HashSet<>();
        for (String rootText : foldRoots(base.text())) {
            Variant root = new Variant(rootText, base.toNorm(), base.head());
            if (!seen.add(rootText)) continue;
            variants.add(root);
            for (Variant derived : List.of(strip(root), dedupe(root))) {
                if (seen.add(derived.text())) {
                    variants.add(derived);
                }
            }
        }

        String matchedPattern = null;
        boolean[] mask = replaceMode ? new boolean[normalized.length()] : null;
        for (Variant v : variants) {
            for (int[] m : matcher.findMatches(v.text())) {
                if (!isValidMatch(v, m, normalized)) continue;
                if (matchedPattern == null) {
                    matchedPattern = v.text().substring(m[0], m[1]);
                }
                if (!replaceMode) {
                    return new Result(null, matchedPattern);
                }
                for (int j = v.toNorm()[m[0]]; j <= v.toNorm()[m[1] - 1]; j++) {
                    mask[j] = true;
                }
            }
        }
        if (matchedPattern == null) return null;

        boolean changed = false;
        for (int i = 0; i < normalized.length(); i++) {
            if (mask[i] && normalized.charAt(i) != replaceChar) {
                changed = true;
                break;
            }
        }
        if (changed) {
            String rebuilt;
            if (message.equals(normalized) && message.length() == message.codePointCount(0, message.length())) {
                rebuilt = maskNormalized(normalized, mask, replaceChar);
            } else {
                rebuilt = rebuild(message, normalized, mask, replaceChar);
                if (rebuilt == null) {
                    rebuilt = maskNormalized(normalized, mask, replaceChar);
                }
            }
            return new Result(rebuilt, matchedPattern);
        }
        return new Result(null, matchedPattern);
    }

    private static String maskNormalized(String normalized, boolean[] mask, char replaceChar) {
        StringBuilder sb = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            sb.append(mask[i] ? replaceChar : normalized.charAt(i));
        }
        return sb.toString();
    }

    private static boolean isValidMatch(Variant v, int[] m, String normalized) {
        int[] toNorm = v.toNorm();
        if (m[0] > 0 && !v.head()[m[0]]) return false;
        if (m[1] < v.text().length() && !v.head()[m[1]]) return false;
        boolean spaced = false;
        outer:
        for (int j = m[0]; j < m[1] - 1; j++) {
            for (int p = toNorm[j] + 1; p < toNorm[j + 1]; p++) {
                char c = normalized.charAt(p);
                if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
                    spaced = true;
                    break outer;
                }
            }
        }
        // 띄어 쓴 매치("쒸 발")는 앞뒤에 단어 글자가 붙어 있으면 오탐("다시 발로", "10시 발 열차")으로 보고 버림
        if (spaced) {
            if (letterBefore(normalized, toNorm[m[0]], MessageFilter::isWordChar)) return false;
            if (letterAfter(normalized, toNorm[m[1] - 1] + 1, MessageFilter::isWordChar)) return false;
        }
        if (isAsciiLetters(v.text(), m[0], m[1])) {
            if (letterBefore(normalized, toNorm[m[0]], MessageFilter::isAsciiLetter)) return false;
            if (letterAfter(normalized, toNorm[m[1] - 1] + 1, MessageFilter::isAsciiLetter)) return false;
        }
        return true;
    }

    private static boolean letterBefore(String normalized, int idx, IntPredicate isLetter) {
        int p = idx;
        while (p >= 2 && normalized.charAt(p - 2) == '§' && isLegacyCode(normalized.charAt(p - 1))) {
            p -= 2;
        }
        return p > 0 && isLetter.test(normalized.codePointBefore(p));
    }

    private static boolean letterAfter(String normalized, int idx, IntPredicate isLetter) {
        int q = idx;
        while (q + 1 < normalized.length() && normalized.charAt(q) == '§' && isLegacyCode(normalized.charAt(q + 1))) {
            q += 2;
        }
        return q < normalized.length() && isLetter.test(normalized.codePointAt(q));
    }

    // 홑자모("ㅋㅋ")는 단어 경계로 치지 않음. "시 발ㅋㅋ" 우회 방지
    private static boolean isWordChar(int cp) {
        if ((cp >= 0x1100 && cp <= 0x11FF) || (cp >= 0x3130 && cp <= 0x318F)
                || (cp >= 0xA960 && cp <= 0xA97F) || (cp >= 0xD7B0 && cp <= 0xD7FF)) return false;
        return Character.isLetterOrDigit(cp);
    }

    private static boolean isAsciiLetter(int cp) {
        return (cp >= 'a' && cp <= 'z') || (cp >= 'A' && cp <= 'Z');
    }

    private static boolean isAsciiLetters(String text, int from, int to) {
        for (int i = from; i < to; i++) {
            char c = text.charAt(i);
            if (c < 'a' || c > 'z') return false;
        }
        return true;
    }

    private static String rebuild(String original, String normalized, boolean[] mask, char replaceChar) {
        StringBuilder sb = new StringBuilder(original.length());
        int ni = 0;
        for (int oi = 0; oi < original.length(); ) {
            int windowEnd = oi;
            String folded = null;
            for (int w = 0; w < 4 && windowEnd < original.length(); w++) {
                windowEnd += Character.charCount(original.codePointAt(windowEnd));
                String candidate = normalize(original.substring(oi, windowEnd));
                if (candidate.isEmpty() || normalized.regionMatches(ni, candidate, 0, candidate.length())) {
                    folded = candidate;
                    break;
                }
            }
            if (folded == null) {
                return null;
            }
            if (folded.isEmpty()) {
                sb.append(original, oi, windowEnd);
                oi = windowEnd;
                continue;
            }
            boolean masked = false;
            for (int k = 0; k < folded.length(); k++) {
                if (mask[ni + k]) {
                    masked = true;
                    break;
                }
            }
            if (masked) {
                sb.append(replaceChar);
            } else {
                sb.append(original, oi, windowEnd);
            }
            ni += folded.length();
            oi = windowEnd;
        }
        return ni == normalized.length() ? sb.toString() : null;
    }

    private static Variant fold(String normalized) {
        StringBuilder sb = new StringBuilder(normalized.length());
        int[] map = new int[normalized.length() * 4 + 1];
        boolean[] head = new boolean[normalized.length() * 4 + 1];
        int lastSource = -1;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c == '§' && i + 1 < normalized.length() && isLegacyCode(normalized.charAt(i + 1))) {
                i++;
                continue;
            }
            if (c >= 0xAC00 && c <= 0xD7A3) {
                int s = c - 0xAC00;
                head[sb.length()] = lastSource != i;
                lastSource = i;
                map[sb.length()] = i;
                sb.append((char) (0x1100 + s / 588));
                map[sb.length()] = i;
                sb.append((char) (0x1161 + (s % 588) / 28));
                int t = s % 28;
                if (t > 0) {
                    for (char j : JONG_TO_CHO[t].toCharArray()) {
                        map[sb.length()] = i;
                        sb.append(j);
                    }
                }
            } else if (c >= 0x11A8 && c <= 0x11C2) {
                for (char j : JONG_TO_CHO[c - 0x11A7].toCharArray()) {
                    head[sb.length()] = lastSource != i;
                    lastSource = i;
                    map[sb.length()] = i;
                    sb.append(j);
                }
            } else {
                head[sb.length()] = lastSource != i;
                lastSource = i;
                map[sb.length()] = i;
                sb.append(c);
            }
        }
        return new Variant(sb.toString(), Arrays.copyOf(map, sb.length()), Arrays.copyOf(head, sb.length()));
    }

    private static Variant strip(Variant v) {
        String text = v.text();
        StringBuilder sb = new StringBuilder(text.length());
        int[] map = new int[text.length()];
        boolean[] head = new boolean[text.length()];
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int cc = Character.charCount(cp);
            if (Character.isLetter(cp)) {
                for (int k = 0; k < cc; k++) {
                    head[sb.length()] = v.head()[i + k];
                    map[sb.length()] = v.toNorm()[i + k];
                    sb.append(text.charAt(i + k));
                }
            }
            i += cc;
        }
        return new Variant(sb.toString(), Arrays.copyOf(map, sb.length()), Arrays.copyOf(head, sb.length()));
    }

    private static Variant dedupe(Variant v) {
        String text = v.text();
        StringBuilder sb = new StringBuilder(text.length());
        int[] map = new int[text.length()];
        boolean[] head = new boolean[text.length()];
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            char prev = sb.length() > 0 ? sb.charAt(sb.length() - 1) : 0;
            if (c == prev) continue;
            if (c == 'ᄋ' && prev != 0 && i + 1 < text.length() && text.charAt(i + 1) == prev) continue;
            head[sb.length()] = v.head()[i];
            map[sb.length()] = v.toNorm()[i];
            sb.append(c);
        }
        return new Variant(sb.toString(), Arrays.copyOf(map, sb.length()), Arrays.copyOf(head, sb.length()));
    }

    private static String dedupeText(String text) {
        return dedupe(plain(text)).text();
    }

    private static String stripText(String text) {
        return strip(plain(text)).text();
    }

    private static Variant plain(String text) {
        boolean[] head = new boolean[text.length()];
        Arrays.fill(head, true);
        return new Variant(text, new int[text.length()], head);
    }

    private static String foldLeet(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            sb.append(foldLeetChar(text.charAt(i)));
        }
        return sb.toString();
    }

    private static String foldHangulLeet(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean slashPair = (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '\\')
                    || (c == '\\' && i > 0 && text.charAt(i - 1) == '/');
            sb.append(switch (c) {
                case '^', '人' -> 'ᄉ';
                case '@' -> 'ᄋ';
                default -> slashPair ? 'ᄉ' : c;
            });
        }
        for (int pass = 0; pass < 4; pass++) {
            boolean changed = false;
            for (int i = 0; i < sb.length(); i++) {
                char folded = foldHangulShapeChar(sb.charAt(i));
                if (folded == sb.charAt(i)) continue;
                if (!isJamo(i > 0 ? sb.charAt(i - 1) : 0)
                        && !isJamo(i + 1 < sb.length() ? sb.charAt(i + 1) : 0)) continue;
                sb.setCharAt(i, folded);
                changed = true;
            }
            if (!changed) break;
        }
        return sb.toString();
    }

    private static char foldHangulShapeChar(char c) {
        return switch (c) {
            case '1', 'l', 'i', '|', '!' -> 'ᅵ'; // ㅣ
            case '0', 'o' -> 'ᄋ'; // ㅇ
            case 'h' -> 'ᅢ';
            default -> c;
        };
    }

    private static boolean isJamo(char c) {
        return c >= 0x1100 && c <= 0x11FF;
    }

    private static char foldLeetChar(char c) {
        return switch (c) {
            case '1', '!' -> 'i';
            case '0' -> 'o';
            case '3' -> 'e';
            case '4', '@' -> 'a';
            case '5', '$' -> 's';
            case '7' -> 't';
            default -> c;
        };
    }

    public static boolean isLegacyCode(char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
                || (c >= 'k' && c <= 'o') || (c >= 'K' && c <= 'O')
                || c == 'r' || c == 'R' || c == 'x' || c == 'X';
    }

    public static String stripLegacyCodes(String s) {
        if (s.indexOf('§') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length() && isLegacyCode(s.charAt(i + 1))) {
                i++;
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
