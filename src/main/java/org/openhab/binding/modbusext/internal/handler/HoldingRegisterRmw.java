package org.openhab.binding.modbusext.internal.handler;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.io.transport.modbus.ModbusRegisterArray;

@NonNullByDefault
final class HoldingRegisterRmw {
    private HoldingRegisterRmw() {
    }

    static ModbusRegisterArray writeBit(ModbusRegisterArray registers, int relativeRegister, int bit, boolean value) {
        byte[] bytes = registers.getBytes();
        int byteIndex = relativeRegister * 2 + (bit >= 8 ? 0 : 1);
        int bitWithinByte = bit % 8;
        if (value) {
            bytes[byteIndex] |= 1 << bitWithinByte;
        } else {
            bytes[byteIndex] &= ~(1 << bitWithinByte);
        }
        return new ModbusRegisterArray(bytes);
    }

    static ModbusRegisterArray writeByte(ModbusRegisterArray registers, int relativeRegister, int subIndex,
            byte value) {
        byte[] bytes = registers.getBytes();
        int byteIndex = relativeRegister * 2 + (subIndex == 0 ? 1 : 0);
        bytes[byteIndex] = value;
        return new ModbusRegisterArray(bytes);
    }

    static ModbusRegisterArray singleRegister(ModbusRegisterArray registers, int relativeRegister) {
        byte[] bytes = registers.getBytes();
        return new ModbusRegisterArray(bytes[relativeRegister * 2], bytes[relativeRegister * 2 + 1]);
    }
}
