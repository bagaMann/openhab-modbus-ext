package org.openhab.binding.modbusext.internal.handler;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.modbusext.internal.config.ModbusSerialConfig;
import org.openhab.core.io.transport.modbus.ModbusManager;
import org.openhab.core.io.transport.modbus.endpoint.EndpointPoolConfiguration;
import org.openhab.core.io.transport.modbus.endpoint.ModbusSerialSlaveEndpoint;
import org.openhab.core.thing.Bridge;

@NonNullByDefault
public class ModbusExtSerialHandler extends ModbusExtEndpointHandler<ModbusSerialSlaveEndpoint> {
    public ModbusExtSerialHandler(Bridge bridge, ModbusManager modbusManager) {
        super(bridge, modbusManager);
    }

    @Override
    protected EndpointPoolConfiguration configureEndpoint() {
        ModbusSerialConfig config = getConfigAs(ModbusSerialConfig.class);
        if (config.port.isBlank()) {
            throw new IllegalArgumentException("port must not be empty");
        }
        if (config.id < 0 || config.id > 247) {
            throw new IllegalArgumentException("slave id must be between 0 and 247");
        }
        if (config.baud <= 0) {
            throw new IllegalArgumentException("baud must be greater than 0");
        }
        if (config.dataBits < 5 || config.dataBits > 8) {
            throw new IllegalArgumentException("data bits must be between 5 and 8");
        }
        if (config.receiveTimeoutMillis < 0 || config.timeBetweenTransactionsMillis < 0
                || config.afterConnectionDelayMillis < 0 || config.connectTimeoutMillis < 0) {
            throw new IllegalArgumentException("serial timing values must not be negative");
        }
        if (config.connectMaxTries < 1) {
            throw new IllegalArgumentException("connectMaxTries must be at least 1");
        }

        slaveId = config.id;
        endpoint = new ModbusSerialSlaveEndpoint(config.port, config.baud, config.flowControlIn, config.flowControlOut,
                config.dataBits, config.stopBits, config.parity, config.encoding, config.echo,
                config.receiveTimeoutMillis);

        EndpointPoolConfiguration pool = new EndpointPoolConfiguration();
        pool.setConnectMaxTries(config.connectMaxTries);
        pool.setAfterConnectionDelayMillis(config.afterConnectionDelayMillis);
        pool.setConnectTimeoutMillis(config.connectTimeoutMillis);
        pool.setInterTransactionDelayMillis(config.timeBetweenTransactionsMillis);

        // Match the openHAB Modbus serial transport policy: keep a serial
        // connection instead of periodically reconnecting it.
        pool.setInterConnectDelayMillis(1000);
        pool.setReconnectAfterMillis(-1);
        return pool;
    }
}
