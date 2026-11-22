package dev.gtnh.nodeathheads.core;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class DeathHooks {
    // Forge posts one event per death. Weak keys also cover cases where OpenBlocks skips the event.
    private static final Set<Object> SUPPRESSED_HEADS =
        Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<Object, Boolean>()));

    private DeathHooks() {}

    public static void noteSuppressedHead(Object event) {
        SUPPRESSED_HEADS.add(event);
    }

    public static boolean shouldSkipEmptyGrave(boolean empty, Object event) {
        return empty && !SUPPRESSED_HEADS.contains(event);
    }
}
