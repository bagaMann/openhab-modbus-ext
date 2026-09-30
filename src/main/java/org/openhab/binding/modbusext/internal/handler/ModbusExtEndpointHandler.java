package org.openhab.binding.modbusext.internal.handler;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.io.transport.modbus.ModbusCommunicationInterface;
import org.openhab.core.io.transport.modbus.ModbusManager;
import org.openhab.core.io.transport.modbus.endpoint.EndpointPoolConfiguration;
import org.openhab.core.io.transport.modbus.endpoint.ModbusSlaveEndpoint;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;

@NonNullByDefault
public abstract class ModbusExtEndpointHandler<E extends ModbusSlaveEndpoint> extends BaseBridgeHandler {
    protected final ModbusManager modbusManager;
    protected volatile @Nullable E endpoint;
    protected volatile @Nullable ModbusCommunicationInterface comms;
    protected volatile int slaveId = 1;

    protected ModbusExtEndpointHandler(Bridge bridge, ModbusManager modbusManager) {
        super(bridge);
        this.modbusManager = modbusManager;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    @Override
    public synchronized void initialize() {
        disposeCommunication();
        try {
            EndpointPoolConfiguration pool = configureEndpoint();
            E localEndpoint = endpoint;
            if (localEndpoint == null) {
                throw new IllegalStateException("Endpoint was not configured");
            }
            comms = modbusManager.newModbusCommunicationInterface(localEndpoint, pool);
            updateStatus(ThingStatus.ONLINE);
        } catch (IllegalArgumentException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, e.getMessage());
        } catch (RuntimeException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Endpoint initialization failed: " + e.getMessage());
        }
    }

    protected abstract EndpointPoolConfiguration configureEndpoint();

    public @Nullable ModbusCommunicationInterface getCommunicationInterface() {
        return comms;
    }

    public int getSlaveId() {
        return slaveId;
    }

    @Override
    public synchronized void dispose() {
        disposeCommunication();
    }

    private void disposeCommunication() {
        ModbusCommunicationInterface localComms = comms;
        comms = null;
        if (localComms != null) {
            try {
                localComms.close();
            } catch (Exception ignored) {
            }
        }
    }
}
