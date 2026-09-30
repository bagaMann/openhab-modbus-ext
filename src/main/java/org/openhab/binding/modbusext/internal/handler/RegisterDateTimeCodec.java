package org.openhab.binding.modbusext.internal.handler;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.io.transport.modbus.ModbusRegisterArray;
import org.openhab.core.library.types.DateTimeType;

@NonNullByDefault
final class RegisterDateTimeCodec {
    static final String VALUE_TYPE = "datetime_ym_dh_ms";
    static final int REGISTER_COUNT = 3;

    private RegisterDateTimeCodec() {
    }

    static Optional<DateTimeType> decode(ModbusRegisterArray registers, int registerIndex) {
        if (registerIndex < 0 || registerIndex + REGISTER_COUNT > registers.size()) {
            return Optional.empty();
        }

        int yearMonth = registers.getRegister(registerIndex);
        int dayHour = registers.getRegister(registerIndex + 1);
        int minuteSecond = registers.getRegister(registerIndex + 2);

        int year = 2000 + ((yearMonth >>> 8) & 0xff);
        int month = yearMonth & 0xff;
        int day = (dayHour >>> 8) & 0xff;
        int hour = dayHour & 0xff;
        int minute = (minuteSecond >>> 8) & 0xff;
        int second = minuteSecond & 0xff;

        try {
            LocalDateTime local = LocalDateTime.of(year, month, day, hour, minute, second);
            return Optional.of(new DateTimeType(local.atZone(ZoneId.systemDefault())));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }
}
