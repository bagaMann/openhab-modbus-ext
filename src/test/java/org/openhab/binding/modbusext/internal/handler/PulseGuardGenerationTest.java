package org.openhab.binding.modbusext.internal.handler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PulseGuardGenerationTest {
    @Test
    void newlyStartedPulseInvalidatesPreviousGuard() {
        PulseGuardGeneration generations = new PulseGuardGeneration();

        long first = generations.next();
        assertTrue(generations.isCurrent(first));

        long second = generations.next();
        assertFalse(generations.isCurrent(first));
        assertTrue(generations.isCurrent(second));
    }

    @Test
    void delayedReadResultFromPreviousPulseIsStale() {
        PulseGuardGeneration generations = new PulseGuardGeneration();

        long readGeneration = generations.next();
        generations.next(); // a new pulse starts while the old READ is in flight

        assertFalse(generations.isCurrent(readGeneration));
    }

    @Test
    void delayedRetryFromPreviousPulseIsStale() {
        PulseGuardGeneration generations = new PulseGuardGeneration();

        long retryGeneration = generations.next();
        generations.next(); // a new pulse starts before the scheduled retry runs

        assertFalse(generations.isCurrent(retryGeneration));
    }

    @Test
    void zeroIsNeverAValidGeneration() {
        PulseGuardGeneration generations = new PulseGuardGeneration();
        assertFalse(generations.isCurrent(0));
    }
}
