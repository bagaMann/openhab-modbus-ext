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
    void activePulseKeepsLatestDesiredState() {
        PulseWriteController controller = new PulseWriteController();
        assertTrue(controller.tryBegin(false, true));
        assertTrue(controller.isActive());
        assertFalse(controller.tryBegin(false, false));
        assertEquals(false, controller.desiredState().orElseThrow());
    }

    @Test
    void completedPulseUpdatesExpectedStateWithoutWaitingForPoll() {
        PulseWriteController controller = new PulseWriteController();
        assertTrue(controller.tryBegin(false, true));
        assertFalse(controller.completeAndBeginPending());
        assertFalse(controller.isActive());
        assertEquals(true, controller.expectedState().orElseThrow());
    }

    @Test
    void latestOppositeCommandStartsPendingPulseImmediately() {
        PulseWriteController controller = new PulseWriteController();
        assertTrue(controller.request(false, true));
        assertFalse(controller.request(false, false));

        assertTrue(controller.completeAndBeginPending());
        assertTrue(controller.isActive());
        assertEquals(true, controller.expectedState().orElseThrow());

        assertFalse(controller.completeAndBeginPending());
        assertFalse(controller.isActive());
        assertEquals(false, controller.expectedState().orElseThrow());
    }

    @Test
    void repeatedCommandsCollapseToLatestDesiredState() {
        PulseWriteController controller = new PulseWriteController();
        assertTrue(controller.request(false, true));
        assertFalse(controller.request(false, false));
        assertFalse(controller.request(false, true));
        assertFalse(controller.request(false, false));

        assertTrue(controller.completeAndBeginPending());
        assertFalse(controller.completeAndBeginPending());
        assertEquals(false, controller.expectedState().orElseThrow());
    }

    @Test
    void staleFeedbackDoesNotCancelExpectedStateBetweenPolls() {
        PulseWriteController controller = new PulseWriteController();
        assertTrue(controller.request(false, true));
        assertFalse(controller.completeAndBeginPending());

        // Regular feedback may still contain OFF. A new OFF command must use the expected ON state
        // and therefore start another toggle immediately.
        assertTrue(controller.request(false, false));
    }

    @Test
    void freshFeedbackCanResynchroniseIdleController() {
        PulseWriteController controller = new PulseWriteController();
        controller.observeFeedback(true);
        assertEquals(true, controller.expectedState().orElseThrow());
        assertTrue(controller.request(true, false));
    }

    @Test
    void clearResetsLifecycleAndStates() {
        PulseWriteController controller = new PulseWriteController();
        assertTrue(controller.request(false, true));
        controller.clear();
        assertFalse(controller.isActive());
        assertTrue(controller.desiredState().isEmpty());
        assertTrue(controller.expectedState().isEmpty());
    }
}
