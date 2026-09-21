package org.openhab.binding.modbusext.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;

@NonNullByDefault
public class ModbusTcpConfig {
    public String host = "localhost";
    public int port = 502;
    public int id = 1;
    public boolean rtuEncoded = false;
    public int timeBetweenTransactionsMillis = 60;
    public int timeBetweenReconnectMillis = 0;
    public int connectMaxTries = 1;
    public int reconnectAfterMillis = 0;
    public int afterConnectionDelayMillis = 0;
    public int connectTimeoutMillis = 10000;
    public int receiveTimeoutMillis = 3000;
}
