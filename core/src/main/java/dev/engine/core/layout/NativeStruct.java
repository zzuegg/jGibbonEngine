package dev.engine.core.layout;

import dev.engine.core.Discoverable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a record for compile-time metadata and struct layout generation.
 *
 * <p>The annotation processor generates a {@code <RecordName>_NativeStruct} class
 * that registers:
 * <ul>
 *   <li>Record component metadata in {@link RecordRegistry} (replaces
 *       {@code Class.getRecordComponents()} for TeaVM compatibility)</li>
 *   <li>PACKED and STD140 struct layouts in {@link StructLayout}</li>
 * </ul>
 *
 * <p>This enables the full engine pipeline (StructLayout, SlangParamsBlock)
 * to work on platforms without reflection support. Implicitly
 * {@link Discoverable} — the generated companion class survives TeaVM DCE.
 */
@Discoverable
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NativeStruct {}
