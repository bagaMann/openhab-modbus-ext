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
    private final AtomicReference<Boolean> expectedState = new AtomicReference<>();
    private final AtomicReference<Boolean> pulseResultState = new AtomicReference<>();

    static boolean shouldPulse(boolean feedback, boolean requested) {
        return feedback != requested;
    }

    /**
     * Records the latest requested state. While a pulse is active the command is not lost: it becomes the pending
     * desired state. When idle, the controller uses its expected logical state rather than a possibly stale regular
     * Modbus poll result.
     */
    boolean request(boolean feedback, boolean requested) {
        desiredState.set(requested);
        if (active.get()) {
            return false;
        }

        Boolean current = expectedState.get();
        if (current == null) {
            current = feedback;
            expectedState.compareAndSet(null, feedback);
        }
        if (!shouldPulse(current, requested)) {
            return false;
        }
        if (!active.compareAndSet(false, true)) {
            return false;
        }
        pulseResultState.set(!current);
        return true;
    }

    boolean tryBegin(boolean feedback, boolean requested) {
        return request(feedback, requested);
    }

    /**
     * Marks the physical pulse (ON then OFF) as completed. If a newer command arrived while it was active and that
     * command requires another toggle, the next pulse is reserved immediately and this method returns true.
     */
    boolean completeAndBeginPending() {
        Boolean result = pulseResultState.getAndSet(null);
        if (result != null) {
            expectedState.set(result);
        }
        active.set(false);

        Boolean desired = desiredState.get();
        Boolean current = expectedState.get();
        if (desired == null || current == null || !shouldPulse(current, desired)) {
            return false;
        }
        if (!active.compareAndSet(false, true)) {
            return false;
        }
        pulseResultState.set(!current);
        return true;
    }

    /** Update the logical state from real feedback when no pulse is in flight. */
    void observeFeedback(boolean feedback) {
        if (!active.get()) {
            expectedState.set(feedback);
        }
    }

    void finish() {
        active.set(false);
        pulseResultState.set(null);
    }

    boolean isActive() {
        return active.get();
    }

    Optional<Boolean> desiredState() {
        return Optional.ofNullable(desiredState.get());
    }

    Optional<Boolean> expectedState() {
        return Optional.ofNullable(expectedState.get());
    }

    boolean tryBeginPending(boolean feedback) {
        observeFeedback(feedback);
        Boolean desired = desiredState.get();
        return desired != null && request(feedback, desired);
    }

    void clear() {
        active.set(false);
        desiredState.set(null);
        expectedState.set(null);
        pulseResultState.set(null);
    }
}
