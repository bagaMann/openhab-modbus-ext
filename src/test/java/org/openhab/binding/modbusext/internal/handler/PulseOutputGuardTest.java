package org.openhab.binding.modbusext.internal.handler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.openhab.core.io.transport.modbus.ModbusRegisterArray;

class PulseOutputGuardTest {
    @Test
    void detectsLowBitAsOn() {
        assertTrue(ModbusExtPollerHandler.isRegisterBitSet(register(0x0001), 0, 0));
        assertFalse(ModbusExtPollerHandler.isRegisterBitSet(register(0x0001), 0, 1));
    }

    @Test
    void detectsHighBitAsOn() {
        assertTrue(ModbusExtPollerHandler.isRegisterBitSet(register(0x8000), 0, 15));
        assertFalse(ModbusExtPollerHandler.isRegisterBitSet(register(0x8000), 0, 14));
    }

    @Test
    void detectsBitSevenAtWriteStartOneDotSeven() {
        assertTrue(ModbusExtPollerHandler.isRegisterBitSet(register(0x0080), 0, 7));
        assertFalse(ModbusExtPollerHandler.isRegisterBitSet(register(0x0000), 0, 7));
    }

    @Test
    void decodesSecondRegisterIndependently() {
        ModbusRegisterArray registers = new ModbusRegisterArray(new byte[] { 0x00, 0x00, 0x01, 0x00 });
        assertTrue(ModbusExtPollerHandler.isRegisterBitSet(registers, 1, 8));
        assertFalse(ModbusExtPollerHandler.isRegisterBitSet(registers, 0, 8));
    }

    @Test
    void invalidIndexesAreSafelyOff() {
        ModbusRegisterArray registers = register(0xffff);
        assertFalse(ModbusExtPollerHandler.isRegisterBitSet(registers, 1, 0));
        assertFalse(ModbusExtPollerHandler.isRegisterBitSet(registers, 0, -1));
        assertFalse(ModbusExtPollerHandler.isRegisterBitSet(registers, 0, 16));
    }

    private static ModbusRegisterArray register(int value) {
        return new ModbusRegisterArray(new byte[] { (byte) (value >>> 8), (byte) value });
    }
}
