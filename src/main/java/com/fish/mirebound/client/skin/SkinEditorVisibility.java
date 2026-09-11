package com.fish.mirebound.client.skin;

import java.util.HashSet;
import java.util.Set;

/** Each model part owns two independently visible preview layers. */
final class SkinEditorVisibility {
    private final Set<String> hidden = new HashSet<>();
    private long revision;
    long revision() { return revision; }
    boolean visible(String part, boolean outer) { return !hidden.contains(key(part, outer)); }
    boolean visible(SkinEditorMesh.Face face) { return visible(face.part(), face.outer()); }
    void toggle(String part, boolean outer) {
        String key = key(part, outer);
        if (!hidden.remove(key)) hidden.add(key);
        revision++;
    }
    void togglePart(String part, boolean hasOuter) {
        boolean hide = visible(part, false) || hasOuter && visible(part, true);
        set(part, false, !hide);
        if (hasOuter) set(part, true, !hide);
        revision++;
    }
    void reset() { hidden.clear(); revision++; }
    private void set(String part, boolean outer, boolean visible) {
        if (visible) hidden.remove(key(part, outer)); else hidden.add(key(part, outer));
    }
    private static String key(String part, boolean outer) { return part + (outer ? ":outer" : ":base"); }
}
