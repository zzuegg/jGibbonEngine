package dev.engine.core.layout.processor;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Annotation processor that generates {@code <RecordName>_NativeStruct} classes
 * for records annotated with {@code @NativeStruct}.
 *
 * <p>Generated classes register record component metadata in {@code RecordRegistry}
 * via a {@code static {}} block. This provides the same information as
 * {@code Class.getRecordComponents()} without requiring runtime reflection,
 * making it work on TeaVM and other platforms without reflection support.
 *
 * <p>The processor ALSO generates the layout registration (PACKED + STD140)
 * and direct write methods, so no reflection is needed for struct layout either.
 *
 * <p>This processor uses string-based annotation lookup to avoid a circular
 * dependency between the {@code core} and {@code core-processor} modules.
 */
@SupportedAnnotationTypes("dev.engine.core.layout.NativeStruct")
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public class NativeStructProcessor extends AbstractProcessor {

    private static final String NATIVE_STRUCT_ANNOTATION = "dev.engine.core.layout.NativeStruct";

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (var annotation : annotations) {
            for (var element : roundEnv.getElementsAnnotatedWith(annotation)) {
                if (element.getKind() != ElementKind.RECORD) {
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR, "@NativeStruct can only be applied to records", element);
                    continue;
                }
                generateNativeStruct((TypeElement) element);
            }
        }
        return true;
    }

    // --- Field computation ---

    private record FieldInfo(String name, String typeName, int offset, int size, int alignment, String typeClass) {}

    private List<FieldInfo> computeFields(List<? extends RecordComponentElement> components, boolean std140) {
        var fields = new ArrayList<FieldInfo>();
        int currentOffset = 0;

        for (var comp : components) {
            var typeMirror = comp.asType();
            var typeName = typeMirror.toString();

            if (isNestedNativeStruct(typeMirror)) {
                // Recursively expand nested @NativeStruct records
                var nestedElement = (TypeElement) ((DeclaredType) typeMirror).asElement();
                var nestedFields = computeFields(nestedElement.getRecordComponents(), std140);
                int nestedAlign = std140 ? 16 : 1;
                if (std140) {
                    currentOffset = align(currentOffset, nestedAlign);
                }
                int nestedBase = currentOffset;
                int nestedSize = 0;
                for (var nf : nestedFields) {
                    fields.add(new FieldInfo(
                            comp.getSimpleName().toString() + "." + nf.name,
                            nf.typeName, nestedBase + nf.offset, nf.size, nf.alignment, nf.typeClass));
                    nestedSize = Math.max(nestedSize, nf.offset + nf.size);
                }
                currentOffset += nestedSize;
            } else {
                int fieldSize = sizeOf(typeName);
                int fieldAlign = alignmentOf(typeName, std140);
                currentOffset = align(currentOffset, fieldAlign);
                String typeClass = typeClassLiteral(typeName);
                fields.add(new FieldInfo(comp.getSimpleName().toString(), typeName,
                        currentOffset, fieldSize, fieldAlign, typeClass));
                currentOffset += fieldSize;
            }
        }
        return fields;
    }

    private int computeTotalSize(List<FieldInfo> fields, boolean std140) {
        if (fields.isEmpty()) return 0;
        int maxAlign = 1;
        int end = 0;
        for (var f : fields) {
            maxAlign = Math.max(maxAlign, f.alignment);
            end = Math.max(end, f.offset + f.size);
        }
        if (std140) {
            end = align(end, maxAlign);
        }
        return end;
    }

    private boolean isNestedNativeStruct(TypeMirror typeMirror) {
        if (typeMirror.getKind() != TypeKind.DECLARED) return false;
        var element = ((DeclaredType) typeMirror).asElement();
        if (element.getKind() != ElementKind.RECORD) return false;
        // Check if it has @NativeStruct AND is not a known primitive type
        var qualifiedName = ((TypeElement) element).getQualifiedName().toString();
        if (isKnownType(qualifiedName)) return false;
        return hasNativeStructAnnotation(element);
    }

    private boolean hasNativeStructAnnotation(Element element) {
        for (var mirror : element.getAnnotationMirrors()) {
            var annotationType = ((TypeElement) mirror.getAnnotationType().asElement()).getQualifiedName().toString();
            if (NATIVE_STRUCT_ANNOTATION.equals(annotationType)) return true;
        }
        return false;
    }

    // --- Type mapping ---

    private static boolean isKnownType(String typeName) {
        return switch (typeName) {
            case "dev.engine.core.math.Vec2", "dev.engine.core.math.Vec3",
                 "dev.engine.core.math.Vec4", "dev.engine.core.math.Mat4",
                 "dev.engine.core.math.Quat" -> true;
            default -> false;
        };
    }

    private static int sizeOf(String typeName) {
        return switch (typeName) {
            case "float", "java.lang.Float" -> 4;
            case "int", "java.lang.Integer" -> 4;
            case "boolean", "java.lang.Boolean" -> 4;
            case "double", "java.lang.Double" -> 8;
            case "long", "java.lang.Long" -> 8;
            case "short", "java.lang.Short" -> 2;
            case "byte", "java.lang.Byte" -> 1;
            case "dev.engine.core.math.Vec2" -> 8;
            case "dev.engine.core.math.Vec3" -> 12;
            case "dev.engine.core.math.Vec4" -> 16;
            case "dev.engine.core.math.Mat4" -> 64;
            case "dev.engine.core.math.Quat" -> 16;
            default -> throw new IllegalArgumentException("Unsupported type: " + typeName);
        };
    }

    private static int alignmentOf(String typeName, boolean std140) {
        if (!std140) return 1; // packed: no alignment
        return switch (typeName) {
            case "float", "java.lang.Float" -> 4;
            case "int", "java.lang.Integer" -> 4;
            case "boolean", "java.lang.Boolean" -> 4;
            case "double", "java.lang.Double" -> 8;
            case "long", "java.lang.Long" -> 8;
            case "short", "java.lang.Short" -> 2;
            case "byte", "java.lang.Byte" -> 1;
            case "dev.engine.core.math.Vec2" -> 8;
            case "dev.engine.core.math.Vec3" -> 16;
            case "dev.engine.core.math.Vec4" -> 16;
            case "dev.engine.core.math.Mat4" -> 16;
            case "dev.engine.core.math.Quat" -> 16;
            default -> 4;
        };
    }

    private static String typeClassLiteral(String typeName) {
        return switch (typeName) {
            case "float" -> "float.class";
            case "int" -> "int.class";
            case "boolean" -> "boolean.class";
            case "double" -> "double.class";
            case "long" -> "long.class";
            case "short" -> "short.class";
            case "byte" -> "byte.class";
            case "java.lang.Float" -> "Float.class";
            case "java.lang.Integer" -> "Integer.class";
            case "java.lang.Boolean" -> "Boolean.class";
            case "java.lang.Double" -> "Double.class";
            case "java.lang.Long" -> "Long.class";
            case "java.lang.Short" -> "Short.class";
            case "java.lang.Byte" -> "Byte.class";
            case "dev.engine.core.math.Vec2" -> "Vec2.class";
            case "dev.engine.core.math.Vec3" -> "Vec3.class";
            case "dev.engine.core.math.Vec4" -> "Vec4.class";
            case "dev.engine.core.math.Mat4" -> "Mat4.class";
            case "dev.engine.core.math.Quat" -> "Quat.class";
            default -> typeName + ".class";
        };
    }

    private static int align(int offset, int alignment) {
        return (offset + alignment - 1) & ~(alignment - 1);
    }

    // --- Code generation ---

    private void generateNativeStruct(TypeElement record) {
        var packageName = processingEnv.getElementUtils().getPackageOf(record).getQualifiedName().toString();
        var className = record.getSimpleName().toString();
        var genClassName = className + "_NativeStruct";
        var qualifiedRecordName = record.getQualifiedName().toString();

        var components = record.getRecordComponents();
        var packedFields = computeFields(components, false);
        var std140Fields = computeFields(components, true);
        int packedSize = computeTotalSize(packedFields, false);
        int std140Size = computeTotalSize(std140Fields, true);

        try {
            var file = processingEnv.getFiler().createSourceFile(packageName + "." + genClassName, record);
            try (var writer = file.openWriter()) {
                generateSource(writer, packageName, className, genClassName,
                        qualifiedRecordName, components, packedFields, std140Fields, packedSize, std140Size);
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Failed to generate _NativeStruct class: " + e.getMessage(), record);
        }
    }

    private void generateSource(Writer w, String packageName, String className, String genClassName,
                                String qualifiedRecordName,
                                List<? extends RecordComponentElement> components,
                                List<FieldInfo> packedFields, List<FieldInfo> std140Fields,
                                int packedSize, int std140Size) throws IOException {
        w.write("package " + packageName + ";\n\n");
        w.write("import dev.engine.core.layout.*;\n");
        w.write("import dev.engine.core.memory.NativeMemory;\n");
        w.write("import dev.engine.core.math.*;\n");
        w.write("import java.util.List;\n\n");
        w.write("/**\n");
        w.write(" * Generated metadata and struct layout for {@link " + className + "}.\n");
        w.write(" * Registers record component info in {@link RecordRegistry} and\n");
        w.write(" * PACKED/STD140 layouts in {@link StructLayout} on class load.\n");
        w.write(" */\n");
        w.write("public final class " + genClassName + " {\n\n");
        w.write("    public static final int SIZE_PACKED = " + packedSize + ";\n");
        w.write("    public static final int SIZE_STD140 = " + std140Size + ";\n\n");

        // writePacked method
        w.write("    public static void writePacked(NativeMemory mem, long off, " + className + " r) {\n");
        emitWriteStatements(w, packedFields, components, "r", "off");
        w.write("    }\n\n");

        // writeStd140 method
        w.write("    public static void writeStd140(NativeMemory mem, long off, " + className + " r) {\n");
        emitWriteStatements(w, std140Fields, components, "r", "off");
        w.write("    }\n\n");

        // Helper methods for composite types used
        var usedTypes = new java.util.LinkedHashSet<String>();
        collectUsedTypes(packedFields, usedTypes);
        collectUsedTypes(std140Fields, usedTypes);
        for (var type : usedTypes) {
            emitHelperMethod(w, type);
        }

        // Static registration block
        w.write("    static {\n");

        // 1. Register record component metadata in RecordRegistry
        w.write("        // Register record component metadata (replaces Class.getRecordComponents())\n");
        w.write("        RecordRegistry.register(" + className + ".class, new RecordRegistry.ComponentInfo[]{\n");
        for (int i = 0; i < components.size(); i++) {
            var comp = components.get(i);
            var compName = comp.getSimpleName().toString();
            var compType = comp.asType().toString();
            var typeClassStr = typeClassLiteral(compType);
            w.write("            new RecordRegistry.ComponentInfo(\"" + compName + "\", " + typeClassStr + ",\n");
            w.write("                rec -> ((" + className + ") rec)." + compName + "())");
            if (i < components.size() - 1) w.write(",");
            w.write("\n");
        }
        w.write("        });\n\n");

        // 2. Register struct layouts (same as before)
        w.write("        // Register struct layouts\n");
        emitRegistration(w, className, "PACKED", packedFields, packedSize);
        emitRegistration(w, className, "STD140", std140Fields, std140Size);
        w.write("    }\n\n");

        // init() method
        w.write("    /** Force class loading to trigger static registration. */\n");
        w.write("    public static void init() {}\n");
        w.write("}\n");
    }

    private void emitWriteStatements(Writer w, List<FieldInfo> fields,
                                     List<? extends RecordComponentElement> components,
                                     String recordVar, String offsetVar) throws IOException {
        for (var field : fields) {
            String accessor = buildAccessor(recordVar, field.name, components);
            emitWriteForType(w, field.typeName, offsetVar + " + " + field.offset, accessor);
        }
    }

    private String buildAccessor(String recordVar, String fieldPath,
                                 List<? extends RecordComponentElement> components) {
        var parts = fieldPath.split("\\.");
        var sb = new StringBuilder(recordVar);
        for (var part : parts) {
            sb.append(".").append(part).append("()");
        }
        return sb.toString();
    }

    private void emitWriteForType(Writer w, String typeName, String offsetExpr, String accessor) throws IOException {
        switch (typeName) {
            case "float", "java.lang.Float" ->
                    w.write("        mem.putFloat(" + offsetExpr + ", " + accessor + ");\n");
            case "int", "java.lang.Integer" ->
                    w.write("        mem.putInt(" + offsetExpr + ", " + accessor + ");\n");
            case "boolean", "java.lang.Boolean" ->
                    w.write("        mem.putInt(" + offsetExpr + ", " + accessor + " ? 1 : 0);\n");
            case "double", "java.lang.Double" ->
                    w.write("        mem.putDouble(" + offsetExpr + ", " + accessor + ");\n");
            case "long", "java.lang.Long" ->
                    w.write("        mem.putLong(" + offsetExpr + ", " + accessor + ");\n");
            case "short", "java.lang.Short" ->
                    w.write("        mem.putShort(" + offsetExpr + ", " + accessor + ");\n");
            case "byte", "java.lang.Byte" ->
                    w.write("        mem.putByte(" + offsetExpr + ", " + accessor + ");\n");
            case "dev.engine.core.math.Vec2" ->
                    w.write("        writeVec2(mem, " + offsetExpr + ", " + accessor + ");\n");
            case "dev.engine.core.math.Vec3" ->
                    w.write("        writeVec3(mem, " + offsetExpr + ", " + accessor + ");\n");
            case "dev.engine.core.math.Vec4" ->
                    w.write("        writeVec4(mem, " + offsetExpr + ", " + accessor + ");\n");
            case "dev.engine.core.math.Mat4" ->
                    w.write("        writeMat4(mem, " + offsetExpr + ", " + accessor + ");\n");
            case "dev.engine.core.math.Quat" ->
                    w.write("        writeQuat(mem, " + offsetExpr + ", " + accessor + ");\n");
            default ->
                    w.write("        // TODO: unsupported type " + typeName + "\n");
        }
    }

    private void collectUsedTypes(List<FieldInfo> fields, java.util.Set<String> types) {
        for (var f : fields) {
            if (f.typeName.startsWith("dev.engine.core.math.")) {
                types.add(f.typeName);
            }
        }
    }

    private void emitHelperMethod(Writer w, String typeName) throws IOException {
        switch (typeName) {
            case "dev.engine.core.math.Vec2" -> {
                w.write("    private static void writeVec2(NativeMemory mem, long off, Vec2 v) {\n");
                w.write("        mem.putFloat(off, v.x()); mem.putFloat(off + 4, v.y());\n");
                w.write("    }\n\n");
            }
            case "dev.engine.core.math.Vec3" -> {
                w.write("    private static void writeVec3(NativeMemory mem, long off, Vec3 v) {\n");
                w.write("        mem.putFloat(off, v.x()); mem.putFloat(off + 4, v.y()); mem.putFloat(off + 8, v.z());\n");
                w.write("    }\n\n");
            }
            case "dev.engine.core.math.Vec4" -> {
                w.write("    private static void writeVec4(NativeMemory mem, long off, Vec4 v) {\n");
                w.write("        mem.putFloat(off, v.x()); mem.putFloat(off + 4, v.y()); mem.putFloat(off + 8, v.z()); mem.putFloat(off + 12, v.w());\n");
                w.write("    }\n\n");
            }
            case "dev.engine.core.math.Mat4" -> {
                w.write("    private static void writeMat4(NativeMemory mem, long off, Mat4 m) {\n");
                w.write("        // Column-major for GPU\n");
                w.write("        mem.putFloat(off, m.m00()); mem.putFloat(off + 4, m.m10()); mem.putFloat(off + 8, m.m20()); mem.putFloat(off + 12, m.m30());\n");
                w.write("        mem.putFloat(off + 16, m.m01()); mem.putFloat(off + 20, m.m11()); mem.putFloat(off + 24, m.m21()); mem.putFloat(off + 28, m.m31());\n");
                w.write("        mem.putFloat(off + 32, m.m02()); mem.putFloat(off + 36, m.m12()); mem.putFloat(off + 40, m.m22()); mem.putFloat(off + 44, m.m32());\n");
                w.write("        mem.putFloat(off + 48, m.m03()); mem.putFloat(off + 52, m.m13()); mem.putFloat(off + 56, m.m23()); mem.putFloat(off + 60, m.m33());\n");
                w.write("    }\n\n");
            }
            case "dev.engine.core.math.Quat" -> {
                w.write("    private static void writeQuat(NativeMemory mem, long off, Quat q) {\n");
                w.write("        mem.putFloat(off, q.x()); mem.putFloat(off + 4, q.y()); mem.putFloat(off + 8, q.z()); mem.putFloat(off + 12, q.w());\n");
                w.write("    }\n\n");
            }
        }
    }

    private void emitRegistration(Writer w, String className, String modeName,
                                  List<FieldInfo> fields, int size) throws IOException {
        w.write("        StructLayout.register(" + className + ".class, LayoutMode." + modeName + ",\n");
        w.write("            StructLayout.manual(" + className + ".class, LayoutMode." + modeName + ",\n");
        w.write("                List.of(\n");
        for (int i = 0; i < fields.size(); i++) {
            var f = fields.get(i);
            w.write("                    new StructLayout.Field(\"" + f.name + "\", " + f.typeClass + ", " +
                    f.offset + ", " + f.size + ", " + f.alignment + ")");
            if (i < fields.size() - 1) w.write(",");
            w.write("\n");
        }
        w.write("                ),\n");
        w.write("                " + size + ", (mem, off, rec) -> write" + modeName.substring(0, 1) +
                modeName.substring(1).toLowerCase() + "(mem, off, (" + className + ") rec)));\n");
    }
}
