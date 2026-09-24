package org.openhab.binding.modbusext.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;

@NonNullByDefault
public class ModbusChannelConfig {
    public String readStart = "";
    public String readValueType = "uint16";
    public String readTransform = "default";

    public String writeStart = "";
    public String writeValueType = "uint16";
    public String writeType = "holding";
    public String writeTransform = "default";
    public String writeMode = "direct";
    public long pulseDurationMillis = 200;

    public long updateUnchangedValuesEveryMillis = 1000;

    public boolean hasReadAddress() {
        return !readStart.isBlank();
    }

    public boolean hasWriteAddress() {
        return !writeStart.isBlank();
    }

    @Override
    public String toString() {
        return "ModbusChannelConfig{readStart='" + readStart + "', readValueType='" + readValueType
                + "', writeStart='" + writeStart + "', writeValueType='" + writeValueType + "', writeType='"
                + writeType + "', writeMode='" + writeMode + "', pulseDurationMillis=" + pulseDurationMillis + "}";
    }
}
