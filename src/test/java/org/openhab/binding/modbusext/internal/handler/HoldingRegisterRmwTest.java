package org.openhab.binding.modbusext.internal.handler;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;
import org.openhab.core.io.transport.modbus.ModbusRegisterArray;

class HoldingRegisterRmwTest {
    @Test
    void uint8LowBytePreservesHighByte() {
        assertRegister(0x1256, HoldingRegisterRmw.writeByte(register(0x1234), 0, 0, (byte) 0x56));
    }

    @Test
    void uint8HighBytePreservesLowByte() {
        assertRegister(0x7834, HoldingRegisterRmw.writeByte(register(0x1234), 0, 1, (byte) 0x78));
    }

    @Test
    void signedLowBytePreservesHighByte() {
        assertRegister(0x12FF, HoldingRegisterRmw.writeByte(register(0x1234), 0, 0, (byte) -1));
    }

    @Test
    void signedHighBytePreservesLowByte() {
        assertRegister(0xFE34, HoldingRegisterRmw.writeByte(register(0x1234), 0, 1, (byte) -2));
    }

    @Test
    void consecutiveByteWritesAccumulateWithoutRepoll() {
        ModbusRegisterArray cache = HoldingRegisterRmw.writeByte(register(0x1234), 0, 0, (byte) 0x56);
        cache = HoldingRegisterRmw.writeByte(cache, 0, 1, (byte) 0x78);
        assertRegister(0x7856, cache);
    }

    @Test
    void bitWritesAccumulateWithoutRepoll() {
        ModbusRegisterArray cache = register(0x0002);
        cache = HoldingRegisterRmw.writeBit(cache, 0, 2, true);
        assertRegister(0x0006, cache);
        cache = HoldingRegisterRmw.writeBit(cache, 0, 3, true);
        assertRegister(0x000E, cache);
        cache = HoldingRegisterRmw.writeBit(cache, 0, 2, false);
        assertRegister(0x000A, cache);
    }

    @Test
    void extractsOnlyRequestedRegister() {
        ModbusRegisterArray registers = new ModbusRegisterArray((byte) 0x12, (byte) 0x34, (byte) 0xAB, (byte) 0xCD);
        assertArrayEquals(new byte[] { (byte) 0xAB, (byte) 0xCD },
                HoldingRegisterRmw.singleRegister(registers, 1).getBytes());
    }

    private static ModbusRegisterArray register(int value) {
        return new ModbusRegisterArray((byte) (value >>> 8), (byte) value);
    }

    private static void assertRegister(int expected, ModbusRegisterArray actual) {
        assertArrayEquals(new byte[] { (byte) (expected >>> 8), (byte) expected }, actual.getBytes());
    }
}
