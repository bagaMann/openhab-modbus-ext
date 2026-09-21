package org.openhab.binding.modbusext.internal.handler;

import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.modbusext.internal.config.ModbusChannelConfig;
import org.openhab.core.io.transport.modbus.AsyncModbusReadResult;
import org.openhab.core.io.transport.modbus.BitArray;
import org.openhab.core.io.transport.modbus.ModbusBitUtilities;
import org.openhab.core.io.transport.modbus.ModbusConstants.ValueType;
import org.openhab.core.io.transport.modbus.ModbusRegisterArray;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;

@NonNullByDefault
final class ModbusChannelRuntime {
    private final ChannelUID uid;
    private final int pollStart;
    private final int pollLength;
    private final boolean registerPoll;
    private final int readIndex;
    private final int readSubIndex;
    private final ValueType readValueType;

    private ModbusChannelRuntime(ChannelUID uid, int pollStart, int pollLength, boolean registerPoll, int readIndex,
            int readSubIndex, ValueType readValueType) {
        this.uid = uid;
        this.pollStart = pollStart;
        this.pollLength = pollLength;
        this.registerPoll = registerPoll;
        this.readIndex = readIndex;
        this.readSubIndex = readSubIndex;
        this.readValueType = readValueType;
    }

    static ModbusChannelRuntime create(Channel channel, ModbusPollerConfigView poller) {
        ModbusChannelConfig config = channel.getConfiguration().as(ModbusChannelConfig.class);
        if (config.readStart == null || config.readStart.isBlank()) {
            throw new IllegalArgumentException("Channel " + channel.getUID() + " has no readStart");
        }

        String[] parts = config.readStart.split("\\.", 2);
        final int index;
        final int subIndex;
        try {
            index = Integer.parseInt(parts[0]);
            subIndex = parts.length == 2 ? Integer.parseInt(parts[1]) : 0;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid readStart '" + config.readStart + "'", e);
        }

        ValueType valueType = ValueType.fromConfigValue(config.readValueType);
        if (valueType == null) {
            throw new IllegalArgumentException("Invalid readValueType '" + config.readValueType + "'");
        }

        if (!poller.registerPoll()) {
            if (valueType != ValueType.BIT) {
                throw new IllegalArgumentException("Coil/discrete channels only support readValueType=bit");
            }
            if (parts.length == 2) {
                throw new IllegalArgumentException("X.Y notation is not valid for coil/discrete polls");
            }
        } else {
            int bits = valueType.getBits();
            if (bits >= 16 && parts.length == 2) {
                throw new IllegalArgumentException("X.Y is only valid for value types smaller than 16 bits");
            }
            if (bits < 16 && parts.length != 2) {
                throw new IllegalArgumentException("X.Y is required for bit/int8/uint8 register values");
            }
            int itemsPerRegister = 16 / bits;
            if (bits < 16 && (subIndex < 0 || subIndex >= itemsPerRegister)) {
                throw new IllegalArgumentException("Sub-index is outside the register");
            }
        }

        int startBit = index * (poller.registerPoll() ? 16 : 1) + subIndex * valueType.getBits();
        int pollStartBit = poller.start() * (poller.registerPoll() ? 16 : 1);
        int pollEndBit = (poller.start() + poller.length()) * (poller.registerPoll() ? 16 : 1) - 1;
        if (startBit < pollStartBit || startBit + valueType.getBits() - 1 > pollEndBit) {
            throw new IllegalArgumentException("Channel read range is outside the poller range");
        }

        return new ModbusChannelRuntime(channel.getUID(), poller.start(), poller.length(), poller.registerPoll(), index,
                subIndex, valueType);
    }

    ChannelUID uid() {
        return uid;
    }

    State extract(AsyncModbusReadResult result) {
        if (registerPoll) {
            Optional<ModbusRegisterArray> registers = result.getRegisters();
            return registers.<State>map(this::extractRegisters).orElse(UnDefType.UNDEF);
        }
        Optional<BitArray> bits = result.getBits();
        return bits.<State>map(this::extractBits).orElse(UnDefType.UNDEF);
    }

    private State extractRegisters(ModbusRegisterArray registers) {
        int bits = readValueType.getBits();
        int extractIndex = bits >= 16 ? readIndex - pollStart
                : (readIndex - pollStart) * (16 / bits) + readSubIndex;
        return ModbusBitUtilities.extractStateFromRegisters(registers, extractIndex, readValueType)
                .<State>map(v -> v).orElse(UnDefType.UNDEF);
    }

    private State extractBits(BitArray bits) {
        return bits.getBit(readIndex - pollStart) ? DecimalType.ONE : DecimalType.ZERO;
    }

    record ModbusPollerConfigView(int start, int length, boolean registerPoll) {
    }
}
