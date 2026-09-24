package org.openhab.binding.modbusext.internal.handler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PulseWriteControllerTest {
    @Test
    void pulsesWhenOffFeedbackMustBecomeOn() {
        assertTrue(PulseWriteController.shouldPulse(false, true));
    }

    @Test
    void pulsesWhenOnFeedbackMustBecomeOff() {
        assertTrue(PulseWriteController.shouldPulse(true, false));
    }

    @Test
    void doesNothingWhenFeedbackAlreadyOn() {
        assertFalse(PulseWriteController.shouldPulse(true, true));
    }

    @Test
    void doesNothingWhenFeedbackAlreadyOff() {
        assertFalse(PulseWriteController.shouldPulse(false, false));
    }

    @Test
    void rejectsSecondPulseWhileFirstIsActive() {
        PulseWriteController controller = new PulseWriteController();
        assertTrue(controller.tryBegin(false, true));
        assertTrue(controller.isActive());
        assertFalse(controller.tryBegin(false, true));
    }

    @Test
    void allowsNewPulseAfterPreviousPulseFinishes() {
        PulseWriteController controller = new PulseWriteController();
        assertTrue(controller.tryBegin(false, true));
        controller.finish();
        assertFalse(controller.isActive());
        assertTrue(controller.tryBegin(true, false));
    }
}
