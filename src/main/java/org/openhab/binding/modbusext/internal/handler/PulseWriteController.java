package org.openhab.binding.modbusext.internal.handler;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jdt.annotation.NonNullByDefault;

/** Lifecycle/state guard for pulse/toggle outputs. */
@NonNullByDefault
final class PulseWriteController {
    private final AtomicBoolean active = new AtomicBoolean();
    private final AtomicReference<Boolean> desiredState = new AtomicReference<>();

    static boolean shouldPulse(boolean feedback, boolean requested) {
        return feedback != requested;
    }

    boolean request(boolean feedback, boolean requested) {
        desiredState.set(requested);
        return shouldPulse(feedback, requested) && active.compareAndSet(false, true);
    }

    boolean tryBegin(boolean feedback, boolean requested) {
        return request(feedback, requested);
    }

    void finish() {
        active.set(false);
    }

    boolean isActive() {
        return active.get();
    }

    Optional<Boolean> desiredState() {
        return Optional.ofNullable(desiredState.get());
    }

    boolean tryBeginPending(boolean feedback) {
        Boolean desired = desiredState.get();
        return desired != null && shouldPulse(feedback, desired) && active.compareAndSet(false, true);
    }

    void clear() {
        active.set(false);
        desiredState.set(null);
    }
}
