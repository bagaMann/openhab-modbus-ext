package org.openhab.binding.modbusext.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;

@NonNullByDefault
public class ModbusSerialConfig {
    public String port = "";
    public int id = 1;
    public int baud = 9600;
    public String stopBits = "1.0";
    public String parity = "none";
    public int dataBits = 8;
    public String encoding = "rtu";
    public boolean echo = false;
    public int receiveTimeoutMillis = 1500;
    public String flowControlIn = "none";
    public String flowControlOut = "none";
    public int timeBetweenTransactionsMillis = 35;
    public int connectMaxTries = 1;
    public int afterConnectionDelayMillis = 0;
    public int connectTimeoutMillis = 10000;
}
