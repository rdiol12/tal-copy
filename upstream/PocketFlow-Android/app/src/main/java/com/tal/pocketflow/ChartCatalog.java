package com.tal.pocketflow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class ChartCatalog {
    static final List<String> LABELS = Collections.unmodifiableList(Arrays.asList(
            "התפלגות לפי קטגוריה", "התפלגות לפי בית עסק", "התפלגות לפי אמצעי תשלום",
            "6 חודשים במבט אחד", "הוראות קבע מול חיובים מזדמנים", "קצב הוצאות מצטבר",
            "מגמה לפי קטגוריה", "התפלגות לפי בן משפחה", "ניצול מסגרות אשראי", "הכנסות מול הוצאות"));

    static List<Integer> order(String saved) {
        List<Integer> result = new ArrayList<>();
        for (String token : saved.split(",")) {
            try {
                int id = Integer.parseInt(token);
                if (id >= 0 && id < LABELS.size() && !result.contains(id)) result.add(id);
            } catch (NumberFormatException ignored) {
                // Older or malformed preferences fall back to the catalog below.
            }
        }
        for (int id = 0; id < LABELS.size(); id++) if (!result.contains(id)) result.add(id);
        return result;
    }

    static void move(List<Integer> order, int index, int delta) {
        int target = index + delta;
        if (target >= 0 && target < order.size()) Collections.swap(order, index, target);
    }

    private ChartCatalog() {}
}
