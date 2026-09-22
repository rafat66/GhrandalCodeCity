package Ghrandal.codecity;

import java.util.*;

/**
 * Cached prefix-search index for the Code City model.
 * Built once when a model is loaded instead of scanning every class/method/
 * attribute/local variable on every keystroke.
 */
class SearchIndex {
    private final NavigableMap<String, List<Ref>> entries = new TreeMap<>();
    private final Map<String, Ref> exact = new HashMap<>();

    static SearchIndex build(Model model) {
        SearchIndex index = new SearchIndex();
        if (model == null) return index;

        for (ClassInfo c : model.classes) {
            index.add(c.fullName(), new ClassRef(c));
            index.add(c.name, new ClassRef(c));
            for (AttributeInfo a : c.attributes) index.add(c.name + "." + a.name, new AttributeRef(c, a));
            for (MethodInfo m : c.methods) {
                index.add(c.name + "." + m.name + "()", new MethodRef(c, m));
                index.add(m.name, new MethodRef(c, m));
                for (int i = 0; i < m.locals.size(); i++) {
                    index.add(m.locals.get(i), new LocalVariableRef(c, m, i));
                    index.add(c.name + "." + m.name + "." + m.locals.get(i), new LocalVariableRef(c, m, i));
                }
            }
        }
        for (District root : model.roots) indexDistrict(index, root);
        return index;
    }

    private static void indexDistrict(SearchIndex index, District d) {
        if (d != null) {
            if (d.name != null && !d.name.isBlank()) index.add(d.name, new DistrictRef(d));
            if (d.path != null && !d.path.isBlank()) index.add(d.path, new DistrictRef(d));
            for (District child : d.children) indexDistrict(index, child);
        }
    }

    private void add(String text, Ref ref) {
        if (text == null || text.isBlank() || ref == null) return;
        String key = normalize(text);
        entries.computeIfAbsent(key, k -> new ArrayList<>()).add(ref);
        exact.putIfAbsent(key, ref);
    }

    Ref exact(String query) {
        if (query == null) return null;
        return exact.get(normalize(query));
    }

    List<Ref> prefix(String query, int limit) {
        if (query == null || query.isBlank() || limit <= 0) return Collections.emptyList();
        String needle = normalize(query);
        String upper = needle + Character.MAX_VALUE;
        LinkedHashSet<Ref> result = new LinkedHashSet<>();
        for (List<Ref> refs : entries.subMap(needle, true, upper, true).values()) {
            for (Ref ref : refs) {
                result.add(ref);
                if (result.size() >= limit) return new ArrayList<>(result);
            }
        }
        return new ArrayList<>(result);
    }

    private static String normalize(String s) {
        return s.trim().toLowerCase(Locale.ROOT);
    }
}
