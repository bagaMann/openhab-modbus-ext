package org.openhab.binding.modbusext.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

@NonNullByDefault
public class ModbusChannelConfig {
    public String readStart = "";
    public String readValueType = "";
    public String readTransform = "default";

    public String writeStart = "";
    public String writeValueType = "uint16";
    public String writeType = "holding";
    public String writeTransform = "default";

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
                + writeType + "'}";
    }
}
