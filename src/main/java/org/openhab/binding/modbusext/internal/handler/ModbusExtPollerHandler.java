package org.openhab.binding.modbusext.internal.handler;

import static org.openhab.binding.modbusext.internal.ModbusExtBindingConstants.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.modbusext.internal.config.ModbusPollerConfig;
import org.openhab.core.io.transport.modbus.AsyncModbusFailure;
import org.openhab.core.io.transport.modbus.AsyncModbusReadResult;
import org.openhab.core.io.transport.modbus.ModbusCommunicationInterface;
import org.openhab.core.io.transport.modbus.ModbusConstants;
import org.openhab.core.io.transport.modbus.ModbusFailureCallback;
import org.openhab.core.io.transport.modbus.ModbusReadCallback;
import org.openhab.core.io.transport.modbus.ModbusReadFunctionCode;
import org.openhab.core.io.transport.modbus.ModbusReadRequestBlueprint;
import org.openhab.core.io.transport.modbus.ModbusBitUtilities;
import org.openhab.core.io.transport.modbus.ModbusWriteCoilRequestBlueprint;
import org.openhab.core.io.transport.modbus.ModbusWriteRegisterRequestBlueprint;
import org.openhab.core.io.transport.modbus.ModbusRegisterArray;
import org.openhab.core.io.transport.modbus.ModbusConstants.ValueType;
import org.openhab.core.io.transport.modbus.PollTask;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
public class ModbusExtPollerHandler extends BaseBridgeHandler
        implements ModbusReadCallback, ModbusFailureCallback<ModbusReadRequestBlueprint> {
    private final Logger logger = LoggerFactory.getLogger(ModbusExtPollerHandler.class);
    private volatile @Nullable PollTask pollTask;
    private volatile @Nullable ModbusCommunicationInterface comms;
    private volatile @Nullable AsyncModbusReadResult lastResult;
    private final AtomicReference<@Nullable ModbusRegisterArray> lastPolledRegisterCache = new AtomicReference<>();
    private volatile long lastResultTimestamp;
    private ModbusPollerConfig config = new ModbusPollerConfig();
    private volatile List<ModbusChannelRuntime> channelRuntimes = List.of();

    public ModbusExtPollerHandler(Bridge bridge) {
        super(bridge);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        ModbusChannelRuntime runtime = channelRuntimes.stream().filter(candidate -> candidate.uid().equals(channelUID))
                .findFirst().orElse(null);
        if (runtime == null) {
            logger.warn("Ignoring command {} for unknown channel {}", command, channelUID);
            return;
        }

        Integer writeStart = runtime.writeStart();
        if (writeStart == null) {
            logger.debug("Ignoring command {} for {}: writeStart is not configured", command, channelUID);
            return;
        }

        var transformedCommand = runtime.transformWriteCommand(command);
        if (transformedCommand.isEmpty()) {
            logger.warn("Cannot process command {} for channel {} because write transformation was unsuccessful",
                    command, channelUID);
            return;
        }
        Command writeCommand = transformedCommand.get();
        if (writeCommand != command) {
            logger.trace("Write transformation for channel {} converted '{}' to '{}'", channelUID, command,
                    writeCommand);
        }

        ModbusCommunicationInterface localComms = comms;
        ModbusExtEndpointHandler<?> endpoint = getEndpointHandler();
        if (localComms == null || endpoint == null) {
            logger.warn("Cannot write channel {} because Modbus endpoint is not online", channelUID);
            return;
        }

        if (THING_TYPE_COIL_POLLER.equals(thing.getThingTypeUID())) {
            var value = ModbusBitUtilities.translateCommand2Boolean(writeCommand);
            if (value.isEmpty()) {
                logger.warn("Cannot convert command {} for channel {} to a coil value", command, channelUID);
                return;
            }
            ModbusWriteCoilRequestBlueprint request = new ModbusWriteCoilRequestBlueprint(endpoint.getSlaveId(),
                    writeStart, value.get(), false, config.maxTries);
            logger.debug("Writing coil {}={} for channel {}", writeStart, value.get(), channelUID);
            localComms.submitOneTimeWrite(request,
                    result -> logger.debug("Coil write succeeded for channel {}: {}", channelUID, result),
                    failure -> logger.warn("Coil write failed for channel {}: {}", channelUID, failure));
            return;
        }

        if (THING_TYPE_HOLDING_POLLER.equals(thing.getThingTypeUID())) {
            ValueType valueType = runtime.writeValueType();
            if (valueType == null) {
                logger.warn("Cannot write channel {} because writeValueType is not configured", channelUID);
                return;
            }

            ModbusRegisterArray data;
            boolean readModifyWrite = valueType == ValueType.BIT || valueType == ValueType.INT8
                    || valueType == ValueType.UINT8;
            if (readModifyWrite) {
                ModbusRegisterArray cached = lastPolledRegisterCache.get();
                if (cached == null) {
                    logger.warn("Cannot write {} for channel {} because holding-register cache is not populated",
                            valueType, channelUID);
                    return;
                }

                int relative = writeStart - config.start;
                synchronized (lastPolledRegisterCache) {
                    ModbusRegisterArray current = lastPolledRegisterCache.get();
                    if (current == null) {
                        logger.warn("Cannot write {} for channel {} because holding-register cache was invalidated",
                                valueType, channelUID);
                        return;
                    }
                    if (valueType == ValueType.BIT) {
                        var value = ModbusBitUtilities.translateCommand2Boolean(writeCommand);
                        if (value.isEmpty()) {
                            logger.warn("Cannot convert command {} for channel {} to a register bit", command,
                                    channelUID);
                            return;
                        }
                        current = HoldingRegisterRmw.writeBit(current, relative, runtime.writeSubIndex(), value.get());
                    } else {
                        ModbusRegisterArray commandData = ModbusBitUtilities.commandToRegisters(writeCommand, valueType);
                        byte[] commandBytes = commandData.getBytes();
                        current = HoldingRegisterRmw.writeByte(current, relative, runtime.writeSubIndex(),
                                commandBytes[commandBytes.length - 1]);
                    }

                    lastPolledRegisterCache.set(current);
                    data = HoldingRegisterRmw.singleRegister(current, relative);
                }
            } else {
                data = ModbusBitUtilities.commandToRegisters(writeCommand, valueType);
            }

            boolean writeMultiple = data.size() > 1;
            ModbusWriteRegisterRequestBlueprint request = new ModbusWriteRegisterRequestBlueprint(endpoint.getSlaveId(),
                    writeStart, data, writeMultiple, config.maxTries);
            logger.debug("Writing holding register {} as {} for channel {}", writeStart, valueType, channelUID);
            localComms.submitOneTimeWrite(request, result -> {
                lastResult = null;
                lastResultTimestamp = 0;
                if (!readModifyWrite) {
                    lastPolledRegisterCache.set(null);
                }
                logger.debug("Holding-register write succeeded for channel {}: {}", channelUID, result);
            }, failure -> {
                lastPolledRegisterCache.set(null);
                logger.warn("Holding-register write failed for channel {}: {}", channelUID, failure);
            });
            return;
        }

        logger.debug("Ignoring command {} for {}: this poller is read-only", command, channelUID);
    }

    @Override
    public synchronized void initialize() {
        unregisterPollTask();
        config = getConfigAs(ModbusPollerConfig.class);

        ModbusReadFunctionCode functionCode = getReadFunctionCode();
        if (functionCode == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Unsupported poller thing type: " + thing.getThingTypeUID());
            return;
        }
        if (config.start < 0 || config.length < 1) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "start must be >= 0 and length must be >= 1");
            return;
        }
        boolean registerPoll = isRegisterFunction(functionCode);
        if (registerPoll && config.length > ModbusConstants.MAX_REGISTERS_READ_COUNT) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Register poll length exceeds Modbus protocol limit");
            return;
        }
        if (!registerPoll && config.length > ModbusConstants.MAX_BITS_READ_COUNT) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Bit poll length exceeds Modbus protocol limit");
            return;
        }

        if (!buildChannelRuntimes(registerPoll)) {
            return;
        }

        ModbusExtEndpointHandler<?> endpoint = getEndpointHandler();
        if (endpoint == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, "Modbus endpoint is offline");
            return;
        }
        ModbusCommunicationInterface localComms = endpoint.getCommunicationInterface();
        if (localComms == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE,
                    "Modbus endpoint communication interface is not initialized");
            return;
        }
        comms = localComms;

        ModbusReadRequestBlueprint request = new ModbusReadRequestBlueprint(endpoint.getSlaveId(), functionCode,
                config.start, config.length, config.maxTries);

        if (config.refresh <= 0) {
            updateStatus(ThingStatus.ONLINE, ThingStatusDetail.NONE, "Polling disabled");
            return;
        }
        pollTask = localComms.registerRegularPoll(request, config.refresh, 0, this, this);
        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void handle(AsyncModbusReadResult result) {
        lastResult = result;
        lastResultTimestamp = System.currentTimeMillis();
        if (THING_TYPE_HOLDING_POLLER.equals(thing.getThingTypeUID())) {
            result.getRegisters().ifPresent(registers ->
                    lastPolledRegisterCache.set(new ModbusRegisterArray(registers.getBytes())));
        }
        for (ModbusChannelRuntime runtime : channelRuntimes) {
            updateState(runtime.uid(), runtime.extract(result));
        }
        ThingStatusInfo status = thing.getStatusInfo();
        if (status.getStatus() == ThingStatus.OFFLINE
                && status.getStatusDetail() == ThingStatusDetail.COMMUNICATION_ERROR) {
            updateStatus(ThingStatus.ONLINE);
        }
        logger.trace("Poller {} distributed one response to {} channels", thing.getUID(), channelRuntimes.size());
    }

    @Override
    public void handle(AsyncModbusFailure<ModbusReadRequestBlueprint> failure) {
        logger.debug("Poller {} read failed: {}", thing.getUID(), failure);
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, failure.toString());
    }

    public @Nullable AsyncModbusReadResult getCachedResult() {
        AsyncModbusReadResult result = lastResult;
        if (result == null) {
            return null;
        }
        if (config.cacheMillis < 0 || System.currentTimeMillis() - lastResultTimestamp <= config.cacheMillis) {
            return result;
        }
        return null;
    }

    @Override
    public synchronized void dispose() {
        unregisterPollTask();
        lastResult = null;
        lastPolledRegisterCache.set(null);
        channelRuntimes = List.of();
    }


    private boolean buildChannelRuntimes(boolean registerPoll) {
        List<ModbusChannelRuntime> runtimes = new ArrayList<>();
        ModbusChannelRuntime.ModbusPollerConfigView view =
                new ModbusChannelRuntime.ModbusPollerConfigView(config.start, config.length, registerPoll);
        for (Channel channel : thing.getChannels()) {
            try {
                runtimes.add(ModbusChannelRuntime.create(channel, view));
            } catch (IllegalArgumentException e) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, e.getMessage());
                logger.debug("Invalid channel configuration for {}: {}", channel.getUID(), e.getMessage());
                return false;
            }
        }
        channelRuntimes = List.copyOf(runtimes);
        return true;
    }

    private void unregisterPollTask() {
        PollTask task = pollTask;
        ModbusCommunicationInterface localComms = comms;
        pollTask = null;
        comms = null;
        if (task != null && localComms != null) {
            localComms.unregisterRegularPoll(task);
        }
    }

    private @Nullable ModbusExtEndpointHandler<?> getEndpointHandler() {
        Bridge parent = getBridge();
        if (parent == null || parent.getStatus() != ThingStatus.ONLINE) {
            return null;
        }
        ThingHandler handler = parent.getHandler();
        return handler instanceof ModbusExtEndpointHandler<?> endpoint ? endpoint : null;
    }

    private @Nullable ModbusReadFunctionCode getReadFunctionCode() {
        ThingTypeUID type = thing.getThingTypeUID();
        if (THING_TYPE_COIL_POLLER.equals(type)) {
            return ModbusReadFunctionCode.READ_COILS;
        } else if (THING_TYPE_DISCRETE_POLLER.equals(type)) {
            return ModbusReadFunctionCode.READ_INPUT_DISCRETES;
        } else if (THING_TYPE_HOLDING_POLLER.equals(type)) {
            return ModbusReadFunctionCode.READ_MULTIPLE_REGISTERS;
        } else if (THING_TYPE_INPUT_POLLER.equals(type)) {
            return ModbusReadFunctionCode.READ_INPUT_REGISTERS;
        }
        return null;
    }

    private boolean isRegisterFunction(ModbusReadFunctionCode code) {
        return code == ModbusReadFunctionCode.READ_INPUT_REGISTERS
                || code == ModbusReadFunctionCode.READ_MULTIPLE_REGISTERS;
    }

    @Override
    public synchronized void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        dispose();
        initialize();
    }
}
