package org.openhab.binding.modbusext.internal.handler;

import java.util.Map;

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
import org.openhab.core.io.transport.modbus.PollTask;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
public class ModbusExtPollerHandler extends BaseBridgeHandler
        implements ModbusReadCallback, ModbusFailureCallback<ModbusReadRequestBlueprint> {
    private static final Map<String, ModbusReadFunctionCode> READ_TYPES = Map.of(
            "coil", ModbusReadFunctionCode.READ_COILS,
            "discrete", ModbusReadFunctionCode.READ_INPUT_DISCRETES,
            "holding", ModbusReadFunctionCode.READ_MULTIPLE_REGISTERS,
            "input", ModbusReadFunctionCode.READ_INPUT_REGISTERS);

    private final Logger logger = LoggerFactory.getLogger(ModbusExtPollerHandler.class);
    private volatile @Nullable PollTask pollTask;
    private volatile @Nullable ModbusCommunicationInterface comms;
    private volatile @Nullable AsyncModbusReadResult lastResult;
    private volatile long lastResultTimestamp;
    private ModbusPollerConfig config = new ModbusPollerConfig();

    public ModbusExtPollerHandler(Bridge bridge) {
        super(bridge);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // Channel command/write support is added after the read path is validated.
    }

    @Override
    public synchronized void initialize() {
        unregisterPollTask();
        config = getConfigAs(ModbusPollerConfig.class);

        ModbusReadFunctionCode functionCode = READ_TYPES.get(config.type);
        if (functionCode == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Unsupported poll type: " + config.type);
            return;
        }
        if (config.start < 0 || config.length < 1) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "start must be >= 0 and length must be >= 1");
            return;
        }
        if (isRegisterFunction(functionCode) && config.length > ModbusConstants.MAX_REGISTERS_READ_COUNT) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Register poll length exceeds Modbus protocol limit");
            return;
        }
        if (!isRegisterFunction(functionCode) && config.length > ModbusConstants.MAX_BITS_READ_COUNT) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Bit poll length exceeds Modbus protocol limit");
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
        // Next step: fan this same result out to all configured channel runtimes.
        logger.trace("Poller {} received {}", thing.getUID(), result);
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
