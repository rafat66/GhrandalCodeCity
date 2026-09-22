package Ghrandal.codecity;

import java.util.List;
import java.util.function.Function;

final class ModelUtils {
    private ModelUtils() {}
    static <T> String joinNames(List<T> items, Function<T, String> nameOf) {
        StringBuilder sb = new StringBuilder();
        int shown = Math.min(items.size(), 10);
        for (int i = 0; i < shown; i++) {
            if (i > 0) sb.append(", ");
            sb.append(nameOf.apply(items.get(i)));
        }
        if (items.size() > shown) sb.append(", … (").append(items.size() - shown).append(" more)");
        return sb.toString();
    }
}
