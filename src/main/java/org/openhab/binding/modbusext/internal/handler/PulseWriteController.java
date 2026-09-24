package org.openhab.binding.modbusext.internal.handler;

import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Decision and lifecycle guard for pulse/toggle outputs.
 *
 * The physical output is only pulsed when the requested logical state differs
 * from the feedback state. Only one pulse may be active for a channel at a time.
 */
@NonNullByDefault
final class PulseWriteController {
    private final AtomicBoolean active = new AtomicBoolean();

    static boolean shouldPulse(boolean feedback, boolean requested) {
        return feedback != requested;
    }

    boolean tryBegin(boolean feedback, boolean requested) {
        return shouldPulse(feedback, requested) && active.compareAndSet(false, true);
    }

    void finish() {
        active.set(false);
    }

    boolean isActive() {
        return active.get();
    }
}
