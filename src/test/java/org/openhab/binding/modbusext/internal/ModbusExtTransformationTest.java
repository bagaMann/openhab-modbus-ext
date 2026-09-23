package org.openhab.binding.modbusext.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.StringType;

class ModbusExtTransformationTest {
    @Test
    void defaultTransformationIsIdentity() {
        ModbusExtTransformation transformation = new ModbusExtTransformation(List.of("default"));
        assertTrue(transformation.isIdentityTransform());
        assertEquals("17", transformation.transform("17"));
    }

    @Test
    void constantNumericOutputConvertsToDecimalCommand() {
        ModbusExtTransformation transformation = new ModbusExtTransformation(List.of("42"));
        var command = ModbusExtTransformation.tryConvertToCommand(transformation.transform("0"));
        assertTrue(command.isPresent());
        assertEquals(new DecimalType(new BigDecimal("42")), command.get());
    }

    @Test
    void constantOnOutputConvertsToOnOffCommand() {
        var command = ModbusExtTransformation.tryConvertToCommand("ON");
        assertTrue(command.isPresent());
        assertSame(OnOffType.ON, command.get());
    }

    @Test
    void constantOpenOutputConvertsToOpenClosedCommand() {
        var command = ModbusExtTransformation.tryConvertToCommand("OPEN");
        assertTrue(command.isPresent());
        assertSame(OpenClosedType.OPEN, command.get());
    }

    @Test
    void transformedNumericStateCanBecomeStringState() {
        ModbusExtTransformation transformation = new ModbusExtTransformation(List.of("text"));
        var transformed = transformation.transformState(List.of(StringType.class), new DecimalType("17"));
        assertEquals(new StringType("text"), transformed);
    }

    @Test
    void invalidOutputDoesNotConvertToCommand() {
        assertTrue(ModbusExtTransformation.tryConvertToCommand("not-a-modbus-command").isEmpty());
    }
}
