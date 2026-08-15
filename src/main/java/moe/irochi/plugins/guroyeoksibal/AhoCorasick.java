package moe.irochi.plugins.guroyeoksibal;

import java.util.*;

public final class AhoCorasick {

    private static final int ROOT = 0;

    private final List<Map<Character, Integer>> goTo;
    private final int[] failure;
    private final boolean[] output;
    private final int[] outputLength;
    private final int[] outputLink;

    private AhoCorasick(List<Map<Character, Integer>> goTo, int[] failure,
                        boolean[] output, int[] outputLength, int[] outputLink) {
        this.goTo = goTo;
        this.failure = failure;
        this.output = output;
        this.outputLength = outputLength;
        this.outputLink = outputLink;
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
        int[] outputLink = new int[stateCount];
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
                outputLink[v] = output[failure[v]] ? failure[v] : outputLink[failure[v]];
            }
        }

        return new AhoCorasick(goTo, failure, output, outputLength, outputLink);
    }

    public List<int[]> findMatches(String text) {
        List<int[]> matches = new ArrayList<>();
        int state = ROOT;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            Integer next = goTo.get(state).get(c);
            while (next == null && state != ROOT) {
                state = failure[state];
                next = goTo.get(state).get(c);
            }
            state = (next != null) ? next : ROOT;
            for (int s = output[state] ? state : outputLink[state]; s != ROOT; s = outputLink[s]) {
                matches.add(new int[]{i - outputLength[s] + 1, i + 1});
            }
        }
        if (matches.size() > 1) {
            matches.sort(Comparator.comparingInt(m -> m[0]));
        }
        return matches;
    }
}
