package dev.engine.bindings.slang;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a ProgramLayout (ShaderReflection) pointer and provides access
 * to shader parameter reflection data via the Slang C reflection API.
 *
 * <p>Unlike the COM interfaces, reflection uses plain C functions
 * (e.g. {@code spReflection_GetParameterCount}) which are bound in
 * {@link SlangNative}.
 */
public class SlangReflection {

    // SlangParameterCategory values for binding queries
    public static final int SLANG_PARAMETER_CATEGORY_MIXED = 0;
    public static final int SLANG_PARAMETER_CATEGORY_CONSTANT_BUFFER = 1;
    public static final int SLANG_PARAMETER_CATEGORY_SHADER_RESOURCE = 2;
    public static final int SLANG_PARAMETER_CATEGORY_UNORDERED_ACCESS = 3;
    public static final int SLANG_PARAMETER_CATEGORY_SAMPLER_STATE = 5;
    public static final int SLANG_PARAMETER_CATEGORY_DESCRIPTOR_TABLE_SLOT = 7;
    public static final int SLANG_PARAMETER_CATEGORY_REGISTER_SPACE = 12;
    public static final int SLANG_PARAMETER_CATEGORY_SUB_ELEMENT_REGISTER_SPACE = 16;

    private final MemorySegment layoutPtr;

    SlangReflection(MemorySegment layoutPtr) {
        this.layoutPtr = layoutPtr;
    }

    /**
     * Returns the number of global shader parameters.
     */
    public int getParameterCount() {
        return SlangNative.reflectionGetParameterCount(layoutPtr);
    }

    /**
     * Returns reflection info for the parameter at the given index.
     */
    public ParameterReflection getParameterByIndex(int index) {
        var paramPtr = SlangNative.reflectionGetParameterByIndex(layoutPtr, index);
        if (paramPtr.equals(MemorySegment.NULL)) return null;
        return new ParameterReflection(paramPtr);
    }

    /**
     * Returns all parameters as a list.
     */
    public List<ParameterReflection> getParameters() {
        int count = getParameterCount();
        var list = new ArrayList<ParameterReflection>(count);
        for (int i = 0; i < count; i++) {
            var param = getParameterByIndex(i);
            if (param != null) list.add(param);
        }
        return list;
    }

    /**
     * Reflection data for a single shader parameter (VariableLayoutReflection).
     */
    public static class ParameterReflection {

        private final MemorySegment variableLayoutPtr;

        ParameterReflection(MemorySegment variableLayoutPtr) {
            this.variableLayoutPtr = variableLayoutPtr;
        }

        /**
         * Returns the parameter name.
         */
        public String name() {
            var variablePtr = SlangNative.reflectionVariableLayoutGetVariable(variableLayoutPtr);
            if (variablePtr.equals(MemorySegment.NULL)) return null;
            return SlangNative.reflectionVariableGetName(variablePtr);
        }

        /**
         * Returns the binding offset for the given parameter category.
         *
         * @param category one of the SLANG_PARAMETER_CATEGORY_* constants
         */
        public long bindingOffset(int category) {
            return SlangNative.reflectionVariableLayoutGetOffset(variableLayoutPtr, category);
        }

        /**
         * Returns the binding space/set for the given parameter category.
         *
         * @param category one of the SLANG_PARAMETER_CATEGORY_* constants
         */
        public long bindingSpace(int category) {
            return SlangNative.reflectionVariableLayoutGetSpace(variableLayoutPtr, category);
        }

        /**
         * Convenience: returns the descriptor table slot binding index.
         */
        public long binding() {
            return bindingOffset(SLANG_PARAMETER_CATEGORY_DESCRIPTOR_TABLE_SLOT);
        }

        /**
         * Convenience: returns the register space.
         */
        public long space() {
            return bindingSpace(SLANG_PARAMETER_CATEGORY_DESCRIPTOR_TABLE_SLOT);
        }

        @Override
        public String toString() {
            return "Parameter{name=" + name() + ", binding=" + binding() + ", space=" + space() + "}";
        }
    }
}
