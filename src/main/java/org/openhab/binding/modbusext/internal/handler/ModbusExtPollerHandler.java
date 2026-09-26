package org.openhab.binding.modbusext.internal.handler;

import static org.openhab.binding.modbusext.internal.ModbusExtBindingConstants.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.modbusext.internal.config.ModbusPollerConfig;
import org.openhab.core.io.transport.modbus.AsyncModbusFailure;
import org.openhab.core.io.transport.modbus.AsyncModbusReadResult;
import org.openhab.core.io.transport.modbus.ModbusBitUtilities;
import org.openhab.core.io.transport.modbus.ModbusCommunicationInterface;
import org.openhab.core.io.transport.modbus.ModbusConstants;
import org.openhab.core.io.transport.modbus.ModbusConstants.ValueType;
import org.openhab.core.io.transport.modbus.ModbusFailureCallback;
import org.openhab.core.io.transport.modbus.ModbusReadCallback;
import org.openhab.core.io.transport.modbus.ModbusReadFunctionCode;
import org.openhab.core.io.transport.modbus.ModbusReadRequestBlueprint;
import org.openhab.core.io.transport.modbus.ModbusRegisterArray;
import org.openhab.core.io.transport.modbus.ModbusWriteCoilRequestBlueprint;
import org.openhab.core.io.transport.modbus.ModbusWriteRegisterRequestBlueprint;
import org.openhab.core.io.transport.modbus.PollTask;
import org.openhab.core.thing.AutoUpdatePolicy;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
public class ModbusExtPollerHandler extends BaseBridgeHandler
        implements ModbusReadCallback, ModbusFailureCallback<ModbusReadRequestBlueprint> {
    private static final long PULSE_VERIFY_DELAY_MILLIS = 300;
    private static final long PULSE_RETRY_DELAY_MILLIS = 100;
    private static final int PULSE_RESET_RETRIES = 3;

    private final Logger logger = LoggerFactory.getLogger(ModbusExtPollerHandler.class);
    private volatile @Nullable PollTask pollTask;
    private volatile @Nullable ModbusCommunicationInterface comms;
    private volatile @Nullable AsyncModbusReadResult lastResult;
    private final AtomicReference<@Nullable ModbusRegisterArray> lastPolledRegisterCache = new AtomicReference<>();
    private final ConcurrentHashMap<ChannelUID, AtomicLong> pulseGenerations = new ConcurrentHashMap<>();
    private volatile long lastResultTimestamp;
    private ModbusPollerConfig config = new ModbusPollerConfig();
    private volatile List<ModbusChannelRuntime> channelRuntimes = List.of();

    public ModbusExtPollerHandler(Bridge bridge) { super(bridge); }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        ModbusChannelRuntime runtime = channelRuntimes.stream().filter(candidate -> candidate.uid().equals(channelUID)).findFirst().orElse(null);
        if (runtime == null) { logger.warn("Ignoring command {} for unknown channel {}", command, channelUID); return; }
        if (command == RefreshType.REFRESH) { logger.trace("Ignoring REFRESH command for channel {}", channelUID); return; }
        Integer writeStart = runtime.writeStart();
        if (writeStart == null) { logger.debug("Ignoring command {} for {}: writeStart is not configured", command, channelUID); return; }
        var transformedCommand = runtime.transformWriteCommand(command);
        if (transformedCommand.isEmpty()) { logger.warn("Cannot process command {} for channel {} because write transformation was unsuccessful", command, channelUID); return; }
        Command writeCommand = transformedCommand.get();
        ModbusCommunicationInterface localComms = comms;
        ModbusExtEndpointHandler<?> endpoint = getEndpointHandler();
        if (localComms == null || endpoint == null) { logger.warn("Cannot write channel {} because Modbus endpoint is not online", channelUID); return; }
        if (THING_TYPE_COIL_POLLER.equals(thing.getThingTypeUID())) {
            var value = ModbusBitUtilities.translateCommand2Boolean(writeCommand);
            if (value.isEmpty()) { logger.warn("Cannot convert command {} for channel {} to a coil value", command, channelUID); return; }
            ModbusWriteCoilRequestBlueprint request = new ModbusWriteCoilRequestBlueprint(endpoint.getSlaveId(), writeStart, value.get(), false, config.maxTries);
            localComms.submitOneTimeWrite(request, result -> logger.debug("Coil write succeeded for channel {}: {}", channelUID, result), failure -> logger.warn("Coil write failed for channel {}: {}", channelUID, failure));
            return;
        }
        if (THING_TYPE_HOLDING_POLLER.equals(thing.getThingTypeUID())) {
            if (runtime.isPulseMode()) { handlePulseCommand(runtime, writeCommand, localComms, endpoint); return; }
            writeHolding(runtime, writeCommand, localComms, endpoint); return;
        }
        logger.debug("Ignoring command {} for {}: this poller is read-only", command, channelUID);
    }

    private void handlePulseCommand(ModbusChannelRuntime runtime, Command command, ModbusCommunicationInterface localComms,
            ModbusExtEndpointHandler<?> endpoint) {
        var requested = ModbusBitUtilities.translateCommand2Boolean(command);
        if (requested.isEmpty()) { logger.warn("Cannot convert pulse command {} for channel {} to boolean", command, runtime.uid()); return; }
        AsyncModbusReadResult result = lastResult;
        if (result == null) { logger.warn("Cannot pulse channel {} because no feedback poll result is available", runtime.uid()); return; }
        var feedback = runtime.extractFeedbackBoolean(result);
        if (feedback.isEmpty()) { logger.warn("Cannot pulse channel {} because feedback cannot be converted to boolean", runtime.uid()); return; }
        if (!runtime.tryBeginPulse(feedback.get(), requested.get())) {
            logger.debug("Pulse command stored/no pulse required for channel {} (feedback={}, requested={})", runtime.uid(), feedback.get(), requested.get());
            return;
        }
        startPulse(runtime, localComms, endpoint, feedback.get(), requested.get());
    }

    private long nextPulseGeneration(ModbusChannelRuntime runtime) {
        return pulseGenerations.computeIfAbsent(runtime.uid(), ignored -> new AtomicLong()).incrementAndGet();
    }

    private boolean isCurrentPulseGeneration(ModbusChannelRuntime runtime, long generation) {
        AtomicLong current = pulseGenerations.get(runtime.uid());
        return current != null && current.get() == generation;
    }

    private void startPulse(ModbusChannelRuntime runtime, ModbusCommunicationInterface localComms,
            ModbusExtEndpointHandler<?> endpoint, boolean feedback, boolean requested) {
        long generation = nextPulseGeneration(runtime);
        logger.debug("Starting {} ms pulse for channel {} (generation={}, feedback={}, requested={})",
                runtime.pulseDurationMillis(), runtime.uid(), generation, feedback, requested);
        if (!submitHoldingBit(runtime, true, localComms, endpoint, () -> scheduler.schedule(
                () -> submitHoldingBit(runtime, false, localComms, endpoint,
                        () -> pulseWriteCompleted(runtime, localComms, endpoint, generation),
                        failure -> {
                            runtime.finishPulse();
                            logger.error("CRITICAL: failed to reset pulse output OFF for channel {}: {}", runtime.uid(), failure);
                            scheduler.schedule(() -> forcePulseOutputOff(runtime, localComms, endpoint, generation, PULSE_RESET_RETRIES),
                                    PULSE_RETRY_DELAY_MILLIS, TimeUnit.MILLISECONDS);
                        }),
                runtime.pulseDurationMillis(), TimeUnit.MILLISECONDS), failure -> {
            runtime.finishPulse();
            logger.warn("Failed to start pulse for channel {}: {}", runtime.uid(), failure);
        })) runtime.finishPulse();
    }

    private void pulseWriteCompleted(ModbusChannelRuntime runtime, ModbusCommunicationInterface localComms,
            ModbusExtEndpointHandler<?> endpoint, long generation) {
        boolean startPending = runtime.completePulseAndBeginPending();
        scheduler.schedule(() -> verifyPulseOutputOff(runtime, localComms, endpoint, generation, PULSE_RESET_RETRIES),
                PULSE_VERIFY_DELAY_MILLIS, TimeUnit.MILLISECONDS);
        if (startPending) {
            logger.debug("Starting pending pulse immediately for channel {}", runtime.uid());
            startPulse(runtime, localComms, endpoint, false, false);
        }
    }

    private void verifyPulseOutputOff(ModbusChannelRuntime runtime, ModbusCommunicationInterface localComms,
            ModbusExtEndpointHandler<?> endpoint, long generation, int retriesRemaining) {
        if (!isCurrentPulseGeneration(runtime, generation)) {
            logger.trace("Skipping stale pulse guard for channel {} (generation={})", runtime.uid(), generation);
            return;
        }
        Integer writeStart = runtime.writeStart();
        if (writeStart == null) return;
        ModbusReadRequestBlueprint request = new ModbusReadRequestBlueprint(endpoint.getSlaveId(),
                ModbusReadFunctionCode.READ_MULTIPLE_REGISTERS, writeStart, 1, config.maxTries);
        localComms.submitOneTimePoll(request, result -> {
            if (!isCurrentPulseGeneration(runtime, generation)) {
                logger.trace("Ignoring stale pulse guard result for channel {} (generation={})", runtime.uid(), generation);
                return;
            }
            var registers = result.getRegisters();
            if (registers.isEmpty()) {
                logger.warn("Pulse guard could not read physical output for channel {}", runtime.uid());
                retryPulseVerification(runtime, localComms, endpoint, generation, retriesRemaining);
                return;
            }
            boolean on = isRegisterBitSet(registers.get(), 0, runtime.writeSubIndex());
            if (!on) { logger.debug("Pulse output physically confirmed OFF for channel {} (generation={})", runtime.uid(), generation); return; }
            logger.warn("Pulse output for channel {} is still ON after pulse; forcing OFF (generation={}, {} retries remaining)", runtime.uid(), generation, retriesRemaining);
            forcePulseOutputOff(runtime, localComms, endpoint, generation, retriesRemaining);
        }, failure -> {
            if (!isCurrentPulseGeneration(runtime, generation)) return;
            logger.warn("Pulse guard read failed for channel {}: {}", runtime.uid(), failure);
            retryPulseVerification(runtime, localComms, endpoint, generation, retriesRemaining);
        });
    }

    private void forcePulseOutputOff(ModbusChannelRuntime runtime, ModbusCommunicationInterface localComms,
            ModbusExtEndpointHandler<?> endpoint, long generation, int retriesRemaining) {
        if (!isCurrentPulseGeneration(runtime, generation)) {
            logger.trace("Skipping stale forced OFF for channel {} (generation={})", runtime.uid(), generation);
            return;
        }
        if (retriesRemaining <= 0) {
            logger.error("CRITICAL: pulse output for channel {} could not be confirmed OFF after {} reset attempts", runtime.uid(), PULSE_RESET_RETRIES);
            return;
        }
        if (!submitHoldingBit(runtime, false, localComms, endpoint,
                () -> scheduler.schedule(() -> verifyPulseOutputOff(runtime, localComms, endpoint, generation, retriesRemaining - 1), PULSE_RETRY_DELAY_MILLIS, TimeUnit.MILLISECONDS),
                failure -> {
                    if (!isCurrentPulseGeneration(runtime, generation)) return;
                    logger.error("CRITICAL: forced OFF write failed for pulse channel {}: {}", runtime.uid(), failure);
                    scheduler.schedule(() -> forcePulseOutputOff(runtime, localComms, endpoint, generation, retriesRemaining - 1), PULSE_RETRY_DELAY_MILLIS, TimeUnit.MILLISECONDS);
                })) scheduler.schedule(() -> forcePulseOutputOff(runtime, localComms, endpoint, generation, retriesRemaining - 1), PULSE_RETRY_DELAY_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void retryPulseVerification(ModbusChannelRuntime runtime, ModbusCommunicationInterface localComms,
            ModbusExtEndpointHandler<?> endpoint, long generation, int retriesRemaining) {
        if (!isCurrentPulseGeneration(runtime, generation)) return;
        if (retriesRemaining <= 0) {
            logger.error("CRITICAL: pulse output for channel {} could not be verified OFF after {} attempts", runtime.uid(), PULSE_RESET_RETRIES);
            return;
        }
        scheduler.schedule(() -> verifyPulseOutputOff(runtime, localComms, endpoint, generation, retriesRemaining - 1), PULSE_RETRY_DELAY_MILLIS, TimeUnit.MILLISECONDS);
    }

    static boolean isRegisterBitSet(ModbusRegisterArray registers, int registerIndex, int bitIndex) {
        byte[] bytes = registers.getBytes(); int offset = registerIndex * 2;
        if (offset + 1 >= bytes.length || bitIndex < 0 || bitIndex > 15) return false;
        int value = ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
        return (value & (1 << bitIndex)) != 0;
    }

    private void writeHolding(ModbusChannelRuntime runtime, Command command, ModbusCommunicationInterface localComms, ModbusExtEndpointHandler<?> endpoint) {
        ValueType valueType = runtime.writeValueType(); Integer writeStart = runtime.writeStart(); if (valueType == null || writeStart == null) return;
        boolean rmw = valueType == ValueType.BIT || valueType == ValueType.INT8 || valueType == ValueType.UINT8; ModbusRegisterArray data;
        if (rmw) { synchronized (lastPolledRegisterCache) { ModbusRegisterArray current = lastPolledRegisterCache.get(); if (current == null) { logger.warn("Cannot write {} for channel {} because holding-register cache is not populated", valueType, runtime.uid()); return; } int relative = writeStart - config.start; if (valueType == ValueType.BIT) { var value = ModbusBitUtilities.translateCommand2Boolean(command); if (value.isEmpty()) return; current = HoldingRegisterRmw.writeBit(current, relative, runtime.writeSubIndex(), value.get()); } else { ModbusRegisterArray commandData = ModbusBitUtilities.commandToRegisters(command, valueType); byte[] bytes = commandData.getBytes(); current = HoldingRegisterRmw.writeByte(current, relative, runtime.writeSubIndex(), bytes[bytes.length - 1]); } lastPolledRegisterCache.set(current); data = HoldingRegisterRmw.singleRegister(current, relative); } } else data = ModbusBitUtilities.commandToRegisters(command, valueType);
        submitHolding(runtime, data, rmw, localComms, endpoint);
    }

    private boolean submitHoldingBit(ModbusChannelRuntime runtime, boolean value, ModbusCommunicationInterface localComms, ModbusExtEndpointHandler<?> endpoint, Runnable success, java.util.function.Consumer<Object> failure) {
        Integer writeStart = runtime.writeStart(); if (writeStart == null) return false; ModbusRegisterArray data;
        synchronized (lastPolledRegisterCache) { ModbusRegisterArray current = lastPolledRegisterCache.get(); if (current == null) { logger.warn("Cannot write pulse bit for channel {} because holding-register cache is not populated", runtime.uid()); return false; } int relative = writeStart - config.start; current = HoldingRegisterRmw.writeBit(current, relative, runtime.writeSubIndex(), value); lastPolledRegisterCache.set(current); data = HoldingRegisterRmw.singleRegister(current, relative); }
        ModbusWriteRegisterRequestBlueprint request = new ModbusWriteRegisterRequestBlueprint(endpoint.getSlaveId(), writeStart, data, false, config.maxTries); localComms.submitOneTimeWrite(request, result -> success.run(), f -> failure.accept(f)); return true;
    }

    private void submitHolding(ModbusChannelRuntime runtime, ModbusRegisterArray data, boolean rmw, ModbusCommunicationInterface localComms, ModbusExtEndpointHandler<?> endpoint) {
        Integer writeStart = runtime.writeStart(); if (writeStart == null) return; ModbusWriteRegisterRequestBlueprint request = new ModbusWriteRegisterRequestBlueprint(endpoint.getSlaveId(), writeStart, data, data.size() > 1, config.maxTries); localComms.submitOneTimeWrite(request, result -> { lastResult = null; lastResultTimestamp = 0; if (!rmw) lastPolledRegisterCache.set(null); logger.debug("Holding-register write succeeded for channel {}: {}", runtime.uid(), result); }, failure -> { lastPolledRegisterCache.set(null); logger.warn("Holding-register write failed for channel {}: {}", runtime.uid(), failure); });
    }

    @Override public synchronized void initialize() {
        unregisterPollTask(); config = getConfigAs(ModbusPollerConfig.class); ModbusReadFunctionCode functionCode = getReadFunctionCode();
        if (functionCode == null) { updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Unsupported poller thing type: " + thing.getThingTypeUID()); return; }
        if (config.start < 0 || config.length < 1) { updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "start must be >= 0 and length must be >= 1"); return; }
        boolean registerPoll = isRegisterFunction(functionCode); if (registerPoll && config.length > ModbusConstants.MAX_REGISTERS_READ_COUNT) { updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Register poll length exceeds Modbus protocol limit"); return; } if (!registerPoll && config.length > ModbusConstants.MAX_BITS_READ_COUNT) { updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Bit poll length exceeds Modbus protocol limit"); return; }
        if (!buildChannelRuntimes(registerPoll)) return;
        applyAutoUpdatePolicies();
        ModbusExtEndpointHandler<?> endpoint = getEndpointHandler(); if (endpoint == null) { updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, "Modbus endpoint is offline"); return; } ModbusCommunicationInterface localComms = endpoint.getCommunicationInterface(); if (localComms == null) { updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, "Modbus endpoint communication interface is not initialized"); return; } comms = localComms;
        ModbusReadRequestBlueprint request = new ModbusReadRequestBlueprint(endpoint.getSlaveId(), functionCode, config.start, config.length, config.maxTries); if (config.refresh <= 0) { updateStatus(ThingStatus.ONLINE, ThingStatusDetail.NONE, "Polling disabled"); return; } pollTask = localComms.registerRegularPoll(request, config.refresh, 0, this, this); updateStatus(ThingStatus.ONLINE);
    }

    private void applyAutoUpdatePolicies() {
        List<Channel> channels = new ArrayList<>(thing.getChannels().size());
        boolean changed = false;
        for (Channel channel : thing.getChannels()) {
            ModbusChannelRuntime runtime = channelRuntimes.stream()
                    .filter(candidate -> candidate.uid().equals(channel.getUID())).findFirst().orElse(null);
            AutoUpdatePolicy desired = runtime != null && runtime.isPulseMode()
                    ? AutoUpdatePolicy.VETO : AutoUpdatePolicy.DEFAULT;
            if (channel.getAutoUpdatePolicy() != desired) {
                channels.add(ChannelBuilder.create(channel).withAutoUpdatePolicy(desired).build());
                changed = true;
                logger.debug("Setting auto-update policy {} for channel {}", desired, channel.getUID());
            } else {
                channels.add(channel);
            }
        }
        if (changed) {
            updateThing(editThing().withChannels(channels).build());
        }
    }

    @Override public void handle(AsyncModbusReadResult result) {
        lastResult = result; lastResultTimestamp = System.currentTimeMillis();
        if (THING_TYPE_HOLDING_POLLER.equals(thing.getThingTypeUID())) result.getRegisters().ifPresent(registers -> lastPolledRegisterCache.set(new ModbusRegisterArray(registers.getBytes())));
        long now = System.currentTimeMillis();
        for (ModbusChannelRuntime runtime : channelRuntimes) if (runtime.hasRead()) {
            if (runtime.isPulseMode()) runtime.extractFeedbackBoolean(result).ifPresent(runtime::observePulseFeedback);
            var state = runtime.extract(result); if (runtime.shouldUpdate(state, now)) updateState(runtime.uid(), state);
        }
        ThingStatusInfo status = thing.getStatusInfo(); if (status.getStatus() == ThingStatus.OFFLINE && status.getStatusDetail() == ThingStatusDetail.COMMUNICATION_ERROR) updateStatus(ThingStatus.ONLINE);
    }
    @Override public void handle(AsyncModbusFailure<ModbusReadRequestBlueprint> failure) { logger.debug("Poller {} read failed: {}", thing.getUID(), failure); updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, failure.toString()); }
    public @Nullable AsyncModbusReadResult getCachedResult() { AsyncModbusReadResult r = lastResult; if (r == null) return null; return config.cacheMillis < 0 || System.currentTimeMillis() - lastResultTimestamp <= config.cacheMillis ? r : null; }
    @Override public synchronized void dispose() { unregisterPollTask(); lastResult = null; lastPolledRegisterCache.set(null); pulseGenerations.clear(); channelRuntimes = List.of(); }
    private boolean buildChannelRuntimes(boolean registerPoll) { List<ModbusChannelRuntime> runtimes = new ArrayList<>(); var view = new ModbusChannelRuntime.ModbusPollerConfigView(config.start, config.length, registerPoll); for (Channel channel : thing.getChannels()) try { runtimes.add(ModbusChannelRuntime.create(channel, view)); } catch (IllegalArgumentException e) { updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, e.getMessage()); return false; } channelRuntimes = List.copyOf(runtimes); return true; }
    private void unregisterPollTask() { PollTask task = pollTask; ModbusCommunicationInterface c = comms; pollTask = null; comms = null; if (task != null && c != null) c.unregisterRegularPoll(task); }
    private @Nullable ModbusExtEndpointHandler<?> getEndpointHandler() { Bridge parent = getBridge(); if (parent == null || parent.getStatus() != ThingStatus.ONLINE) return null; ThingHandler handler = parent.getHandler(); return handler instanceof ModbusExtEndpointHandler<?> endpoint ? endpoint : null; }
    private @Nullable ModbusReadFunctionCode getReadFunctionCode() { ThingTypeUID type = thing.getThingTypeUID(); if (THING_TYPE_COIL_POLLER.equals(type)) return ModbusReadFunctionCode.READ_COILS; if (THING_TYPE_DISCRETE_POLLER.equals(type)) return ModbusReadFunctionCode.READ_INPUT_DISCRETES; if (THING_TYPE_HOLDING_POLLER.equals(type)) return ModbusReadFunctionCode.READ_MULTIPLE_REGISTERS; if (THING_TYPE_INPUT_POLLER.equals(type)) return ModbusReadFunctionCode.READ_INPUT_REGISTERS; return null; }
    private boolean isRegisterFunction(ModbusReadFunctionCode code) { return code == ModbusReadFunctionCode.READ_INPUT_REGISTERS || code == ModbusReadFunctionCode.READ_MULTIPLE_REGISTERS; }
    @Override public synchronized void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) { dispose(); initialize(); }
}
