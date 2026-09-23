package org.openhab.binding.modbusext.internal.handler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.openhab.core.library.types.DecimalType;

class ChannelUpdateTrackerTest {
    @Test
    void firstStateIsAlwaysPublished() {
        ChannelUpdateTracker tracker = new ChannelUpdateTracker(1000);
        assertTrue(tracker.shouldUpdate(new DecimalType(1), 100));
    }

    @Test
    void changedStateIsPublishedImmediately() {
        ChannelUpdateTracker tracker = new ChannelUpdateTracker(1000);
        assertTrue(tracker.shouldUpdate(new DecimalType(1), 100));
        assertTrue(tracker.shouldUpdate(new DecimalType(2), 200));
    }

    @Test
    void unchangedStateIsSuppressedBeforeInterval() {
        ChannelUpdateTracker tracker = new ChannelUpdateTracker(1000);
        assertTrue(tracker.shouldUpdate(new DecimalType(1), 100));
        assertFalse(tracker.shouldUpdate(new DecimalType(1), 1099));
        assertTrue(tracker.shouldUpdate(new DecimalType(1), 1100));
    }

    @Test
    void repeatedUnchangedUpdateRestartsInterval() {
        ChannelUpdateTracker tracker = new ChannelUpdateTracker(1000);
        assertTrue(tracker.shouldUpdate(new DecimalType(1), 100));
        assertTrue(tracker.shouldUpdate(new DecimalType(1), 1100));
        assertFalse(tracker.shouldUpdate(new DecimalType(1), 1500));
        assertTrue(tracker.shouldUpdate(new DecimalType(1), 2100));
    }

    @Test
    void nonPositiveIntervalDisablesRepeatedUnchangedUpdates() {
        ChannelUpdateTracker tracker = new ChannelUpdateTracker(0);
        assertTrue(tracker.shouldUpdate(new DecimalType(1), 100));
        assertFalse(tracker.shouldUpdate(new DecimalType(1), 100000));
        assertTrue(tracker.shouldUpdate(new DecimalType(2), 100001));
    }
}
