package org.openhab.binding.modbusext.internal.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class ModbusChannelConfigTest {
    @Test
    void registerValueTypesDefaultToUint16() {
        ModbusChannelConfig config = new ModbusChannelConfig();

        assertEquals("uint16", config.readValueType);
        assertEquals("uint16", config.writeValueType);
    }

    @Test
    void scheduledWriteDefaultsAreSafe() {
        ModbusChannelConfig config = new ModbusChannelConfig();

        assertFalse(config.scheduledWrite);
        assertEquals(86400, config.scheduledWriteIntervalSeconds);
        assertEquals("unixTime", config.scheduledValueSource);
    }
}
