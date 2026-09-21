package org.openhab.binding.modbusext.internal;

import java.util.List;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.binding.generic.ChannelTransformation;
import org.openhab.core.types.State;
import org.openhab.core.types.TypeParser;

@NonNullByDefault
public final class ModbusExtTransformation {
    public static final String TRANSFORM_DEFAULT = "default";

    private final @Nullable ChannelTransformation transformation;
    private final @Nullable String constantOutput;

    public ModbusExtTransformation(@Nullable List<String> transformationList) {
        if (transformationList == null || transformationList.isEmpty()
                || transformationList.stream().allMatch(String::isBlank)) {
            transformation = null;
            constantOutput = "";
            return;
        }

        String firstLine = transformationList.get(0).trim();
        if (transformationList.size() == 1 && firstLine.equalsIgnoreCase(TRANSFORM_DEFAULT)) {
            transformation = null;
            constantOutput = null;
        } else if (transformationList.stream().allMatch(ChannelTransformation::isValidTransformation)) {
            transformation = new ChannelTransformation(transformationList);
            constantOutput = null;
        } else {
            transformation = null;
            constantOutput = firstLine;
        }
    }

    public boolean isIdentityTransform() {
        return transformation == null && constantOutput == null;
    }

    public String transform(String value) {
        if (transformation != null) {
            return Objects.requireNonNull(transformation.apply(value).orElse(""));
        }
        return Objects.requireNonNullElse(constantOutput, value);
    }

    public @Nullable State transformState(List<Class<? extends State>> acceptedTypes, State state) {
        return TypeParser.parseState(acceptedTypes, transform(state.toString()));
    }
}
