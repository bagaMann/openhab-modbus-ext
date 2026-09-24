package org.openhab.binding.modbusext.internal.handler;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * State-independent decision logic for pulse/toggle outputs.
 *
 * The physical output is only pulsed when the requested logical state differs
 * from the feedback state. The feedback and output addresses are intentionally
 * not coupled; address mapping belongs to the channel configuration.
 */
@NonNullByDefault
final class PulseWriteController {
    private PulseWriteController() {
    }

    static boolean shouldPulse(boolean feedback, boolean requested) {
        return feedback != requested;
    }
}
