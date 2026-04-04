package dev.engine.graphics.shader.params;

import dev.engine.core.math.Mat4;
import dev.engine.graphics.shader.SlangParamsBlock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ObjectParamsTest {

    @Test
    void isRecord() {
        assertTrue(ObjectParams.class.isRecord());
    }

    @Test
    void hasExpectedFields() {
        var params = new ObjectParams(Mat4.IDENTITY);
        assertEquals(Mat4.IDENTITY, params.world());
    }

    @Test
    void generatesSlangBlock() {
        var block = SlangParamsBlock.fromRecord("Object", ObjectParams.class);
        var code = block.generateUbo();
        assertTrue(code.contains("interface IObjectParams"));
        assertTrue(code.contains("float4x4 world()"));
        assertTrue(code.contains("static UboObjectParams object"));
    }

}
