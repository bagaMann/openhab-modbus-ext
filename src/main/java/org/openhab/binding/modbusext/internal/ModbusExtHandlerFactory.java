package org.openhab.binding.modbusext.internal;

import static org.openhab.binding.modbusext.internal.ModbusExtBindingConstants.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.modbusext.internal.handler.ModbusExtPollerHandler;
import org.openhab.binding.modbusext.internal.handler.ModbusExtTcpHandler;
import org.openhab.core.io.transport.modbus.ModbusManager;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = ThingHandlerFactory.class, configurationPid = "binding.modbusext")
@NonNullByDefault
public class ModbusExtHandlerFactory extends BaseThingHandlerFactory {
    private @Nullable ModbusManager manager;

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID type = thing.getThingTypeUID();
        if (THING_TYPE_TCP.equals(type)) {
            ModbusManager localManager = manager;
            return localManager == null ? null : new ModbusExtTcpHandler((Bridge) thing, localManager);
        }
        if (POLLER_THING_TYPES.contains(type)) {
            return new ModbusExtPollerHandler((Bridge) thing);
        }
        return null;
    }

    @Reference
    public void setModbusManager(ModbusManager manager) {
        this.manager = manager;
    }

    public void unsetModbusManager(ModbusManager manager) {
        this.manager = null;
    }
}
