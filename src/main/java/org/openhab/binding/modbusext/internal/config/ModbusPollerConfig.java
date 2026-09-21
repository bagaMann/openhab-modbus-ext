package org.openhab.binding.modbusext.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;

@NonNullByDefault
public class ModbusPollerConfig {
    public int start = 0;
    public int length = 1;
    public String type = "holding";
    public long refresh = 500;
    public int maxTries = 3;
    public long cacheMillis = 50;
}
