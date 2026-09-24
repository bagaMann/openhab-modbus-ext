package org.openhab.binding.modbusext.internal.handler;

import java.util.List;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.modbusext.internal.ModbusExtTransformation;
import org.openhab.binding.modbusext.internal.config.ModbusChannelConfig;
import org.openhab.core.io.transport.modbus.AsyncModbusReadResult;
import org.openhab.core.io.transport.modbus.BitArray;
import org.openhab.core.io.transport.modbus.ModbusBitUtilities;
import org.openhab.core.io.transport.modbus.ModbusConstants.ValueType;
import org.openhab.core.io.transport.modbus.ModbusRegisterArray;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.types.Command;
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
    private final String itemType;
    private final ModbusExtTransformation readTransformation;
    private final @Nullable Integer writeStart;
    private final int writeSubIndex;
    private final @Nullable ValueType writeValueType;
    private final ModbusExtTransformation writeTransformation;
    private final ChannelUpdateTracker updateTracker;

    private ModbusChannelRuntime(ChannelUID uid, int pollStart, int pollLength, boolean registerPoll, int readIndex,
            int readSubIndex, ValueType readValueType, String itemType, ModbusExtTransformation readTransformation,
            @Nullable Integer writeStart, int writeSubIndex, @Nullable ValueType writeValueType,
            ModbusExtTransformation writeTransformation, long updateUnchangedValuesEveryMillis) {
        this.uid = uid;
        this.pollStart = pollStart;
        this.pollLength = pollLength;
        this.registerPoll = registerPoll;
        this.readIndex = readIndex;
        this.readSubIndex = readSubIndex;
        this.readValueType = readValueType;
        this.itemType = itemType;
        this.readTransformation = readTransformation;
        this.writeStart = writeStart;
        this.writeSubIndex = writeSubIndex;
        this.writeValueType = writeValueType;
        this.writeTransformation = writeTransformation;
        this.updateTracker = new ChannelUpdateTracker(updateUnchangedValuesEveryMillis);
    }

    static ModbusChannelRuntime create(Channel channel, ModbusPollerConfigView poller) {
        ModbusChannelConfig config = channel.getConfiguration().as(ModbusChannelConfig.class);
        boolean hasRead = config.readStart != null && !config.readStart.isBlank();
        boolean hasWrite = config.writeStart != null && !config.writeStart.isBlank();
        if (!hasRead && !hasWrite) {
            throw new IllegalArgumentException("Channel " + channel.getUID() + " has neither readStart nor writeStart");
        }

        String[] parts = hasRead ? config.readStart.split("\\.", 2) : new String[] { Integer.toString(poller.start()) };
        final int index;
        final int subIndex;
        try {
            index = hasRead ? Integer.parseInt(parts[0]) : poller.start();
            subIndex = parts.length == 2 ? Integer.parseInt(parts[1]) : 0;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid readStart '" + config.readStart + "'", e);
        }

        String configuredReadValueType = config.readValueType == null ? "" : config.readValueType.trim();
        ValueType valueType;
        if (!hasRead) {
            valueType = ValueType.BIT;
        } else if (!poller.registerPoll() && configuredReadValueType.isBlank()) {
            valueType = ValueType.BIT;
        } else {
            valueType = ValueType.fromConfigValue(configuredReadValueType);
            if (valueType == null) {
                throw new IllegalArgumentException("Invalid readValueType '" + config.readValueType + "'");
            }
        }

        if (hasRead && !poller.registerPoll()) {
            if (valueType != ValueType.BIT) {
                throw new IllegalArgumentException("Coil/discrete channels only support readValueType=bit");
            }
            if (parts.length == 2) {
                throw new IllegalArgumentException("X.Y notation is not valid for coil/discrete polls");
            }
        } else if (hasRead) {
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

        if (hasRead) {
            int startBit = index * (poller.registerPoll() ? 16 : 1) + subIndex * valueType.getBits();
            int pollStartBit = poller.start() * (poller.registerPoll() ? 16 : 1);
            int pollEndBit = (poller.start() + poller.length()) * (poller.registerPoll() ? 16 : 1) - 1;
            if (startBit < pollStartBit || startBit + valueType.getBits() - 1 > pollEndBit) {
                throw new IllegalArgumentException("Channel read range is outside the poller range");
            }
        }

        Integer writeStart = null;
        int writeSubIndex = 0;
        ValueType writeValueType = null;
        if (config.writeStart != null && !config.writeStart.isBlank()) {
            String[] writeParts = config.writeStart.split("\\.", 2);
            try {
                writeStart = Integer.valueOf(writeParts[0]);
                writeSubIndex = writeParts.length == 2 ? Integer.parseInt(writeParts[1]) : 0;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid writeStart '" + config.writeStart + "'", e);
            }
            if (writeStart < 0) {
                throw new IllegalArgumentException("writeStart must be >= 0");
            }

            String configuredWriteValueType = config.writeValueType == null ? "" : config.writeValueType.trim();
            if (!poller.registerPoll()) {
                if (writeParts.length == 2) {
                    throw new IllegalArgumentException("X.Y notation is not valid for coil writes");
                }
                writeValueType = ValueType.BIT;
            } else {
                writeValueType = ValueType.fromConfigValue(configuredWriteValueType);
                if (writeValueType == null) {
                    throw new IllegalArgumentException("Invalid writeValueType '" + config.writeValueType + "'");
                }
                int writeBits = writeValueType.getBits();
                if (writeBits < 16 && writeParts.length != 2) {
                    throw new IllegalArgumentException(
                            "X.Y is required for bit/int8/uint8 holding-register writes");
                }
                if (writeBits >= 16 && writeParts.length == 2) {
                    throw new IllegalArgumentException(
                            "X.Y is only valid for holding-register write types smaller than 16 bits");
                }
                int itemsPerRegister = 16 / writeBits;
                if (writeBits < 16 && (writeSubIndex < 0 || writeSubIndex >= itemsPerRegister)) {
                    throw new IllegalArgumentException("Write sub-index is outside the register");
                }
                if (writeBits < 16
                        && (writeStart < poller.start() || writeStart >= poller.start() + poller.length())) {
                    throw new IllegalArgumentException(
                            "Sub-register write address must be inside the holding poller range for read-modify-write");
                }
            }
        }

        return new ModbusChannelRuntime(channel.getUID(), poller.start(), poller.length(), poller.registerPoll(),
                hasRead ? index : -1,
                subIndex, valueType, channel.getAcceptedItemType(),
                new ModbusExtTransformation(List.of(config.readTransform)), writeStart, writeSubIndex, writeValueType,
                new ModbusExtTransformation(List.of(config.writeTransform)), config.updateUnchangedValuesEveryMillis);
    }

    ChannelUID uid() {
        return uid;
    }

    @Nullable Integer writeStart() {
        return writeStart;
    }

    int writeSubIndex() {
        return writeSubIndex;
    }

    @Nullable ValueType writeValueType() {
        return writeValueType;
    }

    Optional<Command> transformWriteCommand(Command command) {
        if (writeTransformation.isIdentityTransform()) {
            return Optional.of(command);
        }
        return ModbusExtTransformation.tryConvertToCommand(writeTransformation.transform(command.toString()));
    }

    boolean hasRead() {
        return readIndex >= 0;
    }

    boolean shouldUpdate(State state, long nowMillis) {
        return updateTracker.shouldUpdate(state, nowMillis);
    }

    State extract(AsyncModbusReadResult result) {
        if (!hasRead()) {
            return UnDefType.UNDEF;
        }
        State numeric;
        if (registerPoll) {
            Optional<ModbusRegisterArray> registers = result.getRegisters();
            numeric = registers.<State>map(this::extractRegisters).orElse(UnDefType.UNDEF);
        } else {
            Optional<BitArray> bits = result.getBits();
            numeric = bits.<State>map(this::extractBits).orElse(UnDefType.UNDEF);
        }
        return adaptToItemType(numeric);
    }

    private State adaptToItemType(State numeric) {
        if (numeric == UnDefType.UNDEF) {
            return numeric;
        }
        boolean boolValue = !DecimalType.ZERO.equals(numeric);
        if (readTransformation.isIdentityTransform()) {
            return switch (itemType) {
                case "Switch" -> OnOffType.from(boolValue);
                case "Contact" -> boolValue ? OpenClosedType.OPEN : OpenClosedType.CLOSED;
                case "Number" -> numeric;
                case "String" -> new StringType(numeric.toString());
                case "Dimmer", "Rollershutter" -> toPercentType(numeric);
                case "DateTime" -> UnDefType.UNDEF;
                default -> numeric;
            };
        }
        List<Class<? extends State>> accepted = switch (itemType) {
            case "Switch" -> List.of(OnOffType.class);
            case "Contact" -> List.of(OpenClosedType.class);
            case "Number" -> List.of(DecimalType.class);
            case "String" -> List.of(StringType.class);
            case "Dimmer", "Rollershutter" -> List.of(PercentType.class);
            case "DateTime" -> List.of(DateTimeType.class);
            default -> List.of(DecimalType.class);
        };
        State transformed = readTransformation.transformState(accepted, numeric);
        return transformed != null ? transformed : UnDefType.UNDEF;
    }

    private State toPercentType(State numeric) {
        if (numeric instanceof DecimalType decimal) {
            try {
                return new PercentType(decimal.toBigDecimal());
            } catch (IllegalArgumentException e) {
                return UnDefType.UNDEF;
            }
        }
        return UnDefType.UNDEF;
    }

    private State extractRegisters(ModbusRegisterArray registers) {
        int bits = readValueType.getBits();
        int extractIndex = bits >= 16 ? readIndex - pollStart
                : (readIndex - pollStart) * (16 / bits) + readSubIndex;
        return ModbusBitUtilities.extractStateFromRegisters(registers, extractIndex, readValueType)
                .<State>map(v -> v).orElse(UnDefType.UNDEF);
    }

    private State extractBits(BitArray bits) {
        return bits.getBit(readIndex - pollStart) ? new DecimalType(java.math.BigDecimal.ONE) : DecimalType.ZERO;
    }

    record ModbusPollerConfigView(int start, int length, boolean registerPoll) {
    }
}
