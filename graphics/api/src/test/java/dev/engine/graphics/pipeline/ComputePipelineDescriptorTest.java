package dev.engine.graphics.pipeline;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ComputePipelineDescriptorTest {
    @Test void fromSource() {
        var desc = ComputePipelineDescriptor.of(new ShaderSource(ShaderStage.COMPUTE, "void main() {}"));
        assertNotNull(desc.shader());
        assertNull(desc.binary());
        assertFalse(desc.hasSpirv());
    }

    @Test void fromSpirv() {
        var desc = ComputePipelineDescriptor.ofSpirv(new ShaderBinary(ShaderStage.COMPUTE, new byte[]{1, 2, 3}));
        assertNull(desc.shader());
        assertNotNull(desc.binary());
        assertTrue(desc.hasSpirv());
    }
}
