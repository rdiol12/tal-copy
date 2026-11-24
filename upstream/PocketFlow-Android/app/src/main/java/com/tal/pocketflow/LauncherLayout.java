package com.tal.pocketflow;

import java.util.Arrays;

/** Stable slot numbers let the two app pages retain empty spaces. */
final class LauncherLayout {
    static final String PREFERENCE = "launcherLayout";
    static final int APP_PAGES = 2, COLUMNS = 3, SLOTS_PER_PAGE = 9, EMPTY = -1;
    private static final int[] DEFAULT = {
            11, 1, 3, 4, 2, 7, EMPTY, EMPTY, EMPTY,
            8, 9, 10, 5, 6, EMPTY, EMPTY, EMPTY, EMPTY
    };
    private final int[] slots;

    LauncherLayout(String saved) {
        slots = DEFAULT.clone();
        String[] tokens = saved.split(",", -1);
        if (tokens.length != slots.length) return;
        int[] candidate = new int[slots.length];
        try {
            for (int i = 0; i < slots.length; i++) candidate[i] = Integer.parseInt(tokens[i]);
        } catch (NumberFormatException ignored) {
            return;
        }
        int[] actual = candidate.clone();
        int[] expected = DEFAULT.clone();
        Arrays.sort(actual);
        Arrays.sort(expected);
        if (Arrays.equals(actual, expected)) System.arraycopy(candidate, 0, slots, 0, slots.length);
    }

    int at(int index) { return slots[index]; }

    void move(int source, int destination) {
        int previous = slots[destination];
        slots[destination] = slots[source];
        slots[source] = previous;
    }

    String encode() {
        StringBuilder result = new StringBuilder();
        for (int slot : slots) {
            if (result.length() > 0) result.append(',');
            result.append(slot);
        }
        return result.toString();
    }
}
