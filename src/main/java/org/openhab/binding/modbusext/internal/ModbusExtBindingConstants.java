package org.openhab.binding.modbusext.internal;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

@NonNullByDefault
public final class ModbusExtBindingConstants {
    public static final String BINDING_ID = "modbusext";

    public static final ThingTypeUID THING_TYPE_TCP = new ThingTypeUID(BINDING_ID, "tcp");
    public static final ThingTypeUID THING_TYPE_SERIAL = new ThingTypeUID(BINDING_ID, "serial");
    public static final ThingTypeUID THING_TYPE_POLLER = new ThingTypeUID(BINDING_ID, "poller");

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Set.of(THING_TYPE_TCP, THING_TYPE_SERIAL,
            THING_TYPE_POLLER);

    private ModbusExtBindingConstants() {
    }
}
