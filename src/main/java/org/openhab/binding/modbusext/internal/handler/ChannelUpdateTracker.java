package org.openhab.binding.modbusext.internal.handler;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.types.State;

@NonNullByDefault
final class ChannelUpdateTracker {
    private final long updateUnchangedValuesEveryMillis;
    private @Nullable State lastState;
    private long lastUpdateMillis;

    ChannelUpdateTracker(long updateUnchangedValuesEveryMillis) {
        this.updateUnchangedValuesEveryMillis = updateUnchangedValuesEveryMillis;
    }

    synchronized boolean shouldUpdate(State state, long nowMillis) {
        State previous = lastState;
        if (previous == null || !previous.equals(state)) {
            lastState = state;
            lastUpdateMillis = nowMillis;
            return true;
        }
        if (updateUnchangedValuesEveryMillis <= 0) {
            return false;
        }
        if (nowMillis - lastUpdateMillis >= updateUnchangedValuesEveryMillis) {
            lastUpdateMillis = nowMillis;
            return true;
        }
        return false;
    }
}
