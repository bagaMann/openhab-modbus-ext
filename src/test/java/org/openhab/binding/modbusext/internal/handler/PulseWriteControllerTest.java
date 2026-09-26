package org.openhab.binding.modbusext.internal.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PulseWriteControllerTest {
    @Test void pulsesWhenOffFeedbackMustBecomeOn() { assertTrue(PulseWriteController.shouldPulse(false, true)); }
    @Test void pulsesWhenOnFeedbackMustBecomeOff() { assertTrue(PulseWriteController.shouldPulse(true, false)); }
    @Test void doesNothingWhenFeedbackAlreadyOn() { assertFalse(PulseWriteController.shouldPulse(true, true)); }
    @Test void doesNothingWhenFeedbackAlreadyOff() { assertFalse(PulseWriteController.shouldPulse(false, false)); }

    @Test
    void rejectsSecondPulseWhileFirstIsActiveButKeepsLatestDesiredState() {
        PulseWriteController controller = new PulseWriteController();
        assertTrue(controller.tryBegin(false, true));
        assertTrue(controller.isActive());
        assertFalse(controller.tryBegin(false, false));
        assertEquals(false, controller.desiredState().orElseThrow());
    }

    @Test
    void allowsNewPulseAfterPreviousPulseFinishes() {
        PulseWriteController controller = new PulseWriteController();
        assertTrue(controller.tryBegin(false, true));
        controller.finish();
        assertFalse(controller.isActive());
        assertTrue(controller.tryBegin(true, false));
    }

    @Test
    void pendingPulseUsesLatestDesiredState() {
        PulseWriteController controller = new PulseWriteController();

        // First command starts a pulse. A later command arrives while it is active;
        // it must replace the desired state rather than queue another pulse.
        assertTrue(controller.request(false, true));
        assertFalse(controller.request(false, false));
        controller.finish();

        // Feedback already equals the latest desired state (OFF), so no new pulse.
        assertFalse(controller.tryBeginPending(false));
        assertFalse(controller.isActive());

        // While feedback is still OFF, request ON. That request itself starts the pulse.
        assertTrue(controller.request(false, true));
        assertTrue(controller.isActive());
        assertEquals(true, controller.desiredState().orElseThrow());
    }

    @Test
    void pendingPulseStartsWhenLatestDesiredStateDiffersAfterActivePulse() {
        PulseWriteController controller = new PulseWriteController();
        assertTrue(controller.request(false, true));
        assertFalse(controller.request(false, false));
        controller.finish();

        // Simulate feedback changing to ON while the latest desired state remains OFF.
        assertTrue(controller.tryBeginPending(true));
        assertTrue(controller.isActive());
    }

    @Test
    void clearResetsLifecycleAndDesiredState() {
        PulseWriteController controller = new PulseWriteController();
        assertTrue(controller.request(false, true));
        controller.clear();
        assertFalse(controller.isActive());
        assertTrue(controller.desiredState().isEmpty());
    }
}
