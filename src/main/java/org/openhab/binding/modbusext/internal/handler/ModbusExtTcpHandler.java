package org.openhab.binding.modbusext.internal.handler;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.modbusext.internal.config.ModbusTcpConfig;
import org.openhab.core.io.transport.modbus.ModbusManager;
import org.openhab.core.io.transport.modbus.endpoint.EndpointPoolConfiguration;
import org.openhab.core.io.transport.modbus.endpoint.ModbusTCPSlaveEndpoint;
import org.openhab.core.thing.Bridge;

@NonNullByDefault
public class ModbusExtTcpHandler extends ModbusExtEndpointHandler<ModbusTCPSlaveEndpoint> {
    public ModbusExtTcpHandler(Bridge bridge, ModbusManager modbusManager) {
        super(bridge, modbusManager);
    }

    @Override
    protected EndpointPoolConfiguration configureEndpoint() {
        ModbusTcpConfig config = getConfigAs(ModbusTcpConfig.class);
        if (config.host.isBlank()) {
            throw new IllegalArgumentException("host must not be empty");
        }
        if (config.port < 1 || config.port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        slaveId = config.id;
        endpoint = new ModbusTCPSlaveEndpoint(config.host, config.port, config.rtuEncoded);

        EndpointPoolConfiguration pool = new EndpointPoolConfiguration();
        pool.setConnectMaxTries(config.connectMaxTries);
        pool.setAfterConnectionDelayMillis(config.afterConnectionDelayMillis);
        pool.setConnectTimeoutMillis(config.connectTimeoutMillis);
        pool.setInterConnectDelayMillis(config.timeBetweenReconnectMillis);
        pool.setInterTransactionDelayMillis(config.timeBetweenTransactionsMillis);
        pool.setReconnectAfterMillis(config.reconnectAfterMillis);
        pool.setReceiveTimeoutMillis(config.receiveTimeoutMillis);
        return pool;
    }
}
