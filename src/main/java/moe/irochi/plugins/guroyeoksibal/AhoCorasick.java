package moe.irochi.plugins.guroyeoksibal;

import java.util.*;

public final class AhoCorasick {

    private static final int ROOT = 0;

    private final List<Map<Character, Integer>> goTo;
    private final int[] failure;
    private final boolean[] output;
    private final int[] outputLength;
    private final int size;

    private AhoCorasick(List<Map<Character, Integer>> goTo, int[] failure,
                        boolean[] output, int[] outputLength, int size) {
        this.goTo = goTo;
        this.failure = failure;
        this.output = output;
        this.outputLength = outputLength;
        this.size = size;
    }

    public static AhoCorasick build(Collection<String> patterns) {
        List<Map<Character, Integer>> goTo = new ArrayList<>();
        goTo.add(new HashMap<>());
        List<Boolean> out = new ArrayList<>();
        out.add(false);
        List<Integer> outLen = new ArrayList<>();
        outLen.add(0);
        int stateCount = 1;

        for (String pattern : patterns) {
            if (pattern.isEmpty()) continue;
            int state = ROOT;
            for (int i = 0; i < pattern.length(); i++) {
                char c = pattern.charAt(i);
                Integer next = goTo.get(state).get(c);
                if (next == null) {
                    next = stateCount++;
                    goTo.get(state).put(c, next);
                    goTo.add(new HashMap<>());
                    out.add(false);
                    outLen.add(0);
                }
                state = next;
            }
            out.set(state, true);
            outLen.set(state, pattern.length());
        }

        boolean[] output = new boolean[stateCount];
        int[] outputLength = new int[stateCount];
        for (int i = 0; i < stateCount; i++) {
            output[i] = out.get(i);
            outputLength[i] = outLen.get(i);
        }

        int[] failure = new int[stateCount];
        Queue<Integer> queue = new ArrayDeque<>();

        for (int child : goTo.get(ROOT).values()) {
            failure[child] = ROOT;
            queue.add(child);
        }

        while (!queue.isEmpty()) {
            int u = queue.poll();
            for (Map.Entry<Character, Integer> entry : goTo.get(u).entrySet()) {
                char c = entry.getKey();
                int v = entry.getValue();
                queue.add(v);

                int f = failure[u];
                while (f != ROOT && !goTo.get(f).containsKey(c)) {
                    f = failure[f];
                }
                Integer fTarget = goTo.get(f).get(c);
                failure[v] = (fTarget != null && fTarget != v) ? fTarget : ROOT;

                if (output[failure[v]]) {
                    output[v] = true;
                    if (outputLength[v] == 0) {
                        outputLength[v] = outputLength[failure[v]];
                    }
                }
            }
        }

        return new AhoCorasick(goTo, failure, output, outputLength, stateCount);
    }

    public String findFirstMatch(String text) {
        int state = ROOT;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            while (state != ROOT && !goTo.get(state).containsKey(c)) {
                state = failure[state];
            }
            Integer next = goTo.get(state).get(c);
            state = (next != null) ? next : ROOT;
            if (output[state]) {
                int len = outputLength[state];
                return text.substring(i - len + 1, i + 1);
            }
        }
        return null;
    }

    public boolean containsAny(String text) {
        int state = ROOT;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            while (state != ROOT && !goTo.get(state).containsKey(c)) {
                state = failure[state];
            }
            Integer next = goTo.get(state).get(c);
            state = (next != null) ? next : ROOT;
            if (output[state]) return true;
        }
        return false;
    }

    public String replaceAll(String text, char replaceChar) {
        List<int[]> matches = new ArrayList<>();
        int state = ROOT;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            while (state != ROOT && !goTo.get(state).containsKey(c)) {
                state = failure[state];
            }
            Integer next = goTo.get(state).get(c);
            state = (next != null) ? next : ROOT;
            if (output[state]) {
                matches.add(new int[]{i - outputLength[state] + 1, i + 1});
            }
        }

        if (matches.isEmpty()) return text;

        matches.sort(Comparator.comparingInt(m -> m[0]));

        char[] result = text.toCharArray();
        int pos = 0;
        for (int[] m : matches) {
            if (m[0] < pos) continue;
            Arrays.fill(result, m[0], m[1], replaceChar);
            pos = m[1];
        }
        return new String(result);
    }

    public int stateCount() {
        return size;
    }
}
