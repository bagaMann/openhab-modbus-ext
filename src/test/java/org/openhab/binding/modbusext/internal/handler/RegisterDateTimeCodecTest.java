package org.openhab.binding.modbusext.internal.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.openhab.core.io.transport.modbus.ModbusRegisterArray;

class RegisterDateTimeCodecTest {
    @Test
    void decodesYearMonthDayHourMinuteSecondFromThreeRegisters() {
        ModbusRegisterArray registers = new ModbusRegisterArray(new byte[] {
                0x1A, 0x09,
                0x1E, 0x10,
                0x2A, 0x3A
        });

        var decoded = RegisterDateTimeCodec.decode(registers, 0);

        assertTrue(decoded.isPresent());
        assertEquals(2026, decoded.orElseThrow().getZonedDateTime().withZoneSameInstant(ZoneId.systemDefault()).getYear());
        assertEquals(9, decoded.orElseThrow().getZonedDateTime().withZoneSameInstant(ZoneId.systemDefault()).getMonthValue());
        assertEquals(30, decoded.orElseThrow().getZonedDateTime().withZoneSameInstant(ZoneId.systemDefault()).getDayOfMonth());
        assertEquals(16, decoded.orElseThrow().getZonedDateTime().withZoneSameInstant(ZoneId.systemDefault()).getHour());
        assertEquals(42, decoded.orElseThrow().getZonedDateTime().withZoneSameInstant(ZoneId.systemDefault()).getMinute());
        assertEquals(58, decoded.orElseThrow().getZonedDateTime().withZoneSameInstant(ZoneId.systemDefault()).getSecond());
    }

    @Test
    void rejectsInvalidCalendarValues() {
        ModbusRegisterArray registers = new ModbusRegisterArray(new byte[] {
                0x1A, 0x0D,
                0x1E, 0x10,
                0x2A, 0x3A
        });

        assertTrue(RegisterDateTimeCodec.decode(registers, 0).isEmpty());
    }
}
