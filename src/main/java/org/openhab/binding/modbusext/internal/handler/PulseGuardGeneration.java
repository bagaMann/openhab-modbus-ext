package org.openhab.binding.modbusext.internal.handler;

import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Monotonic generation token used to make asynchronous pulse-guard work safe.
 * Any callback or retry belonging to an older pulse becomes stale as soon as a
 * newer pulse starts.
 */
@NonNullByDefault
final class PulseGuardGeneration {
    private final AtomicLong generation = new AtomicLong();

    long next() {
        return generation.incrementAndGet();
    }

    boolean isCurrent(long candidate) {
        return candidate > 0 && generation.get() == candidate;
    }
}
