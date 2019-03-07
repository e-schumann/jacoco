/*******************************************************************************
 * Copyright (c) 2009, 2019 Mountainminds GmbH & Co. KG and Contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Marc R. Hoffmann - initial API and implementation
 *    
 *******************************************************************************/
package org.jacoco.core.internal.instr;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

/**
 * Unit tests for {@link InstrSupport}.
 */
public class InstrSupportTest {

	private Printer printer;
	private TraceMethodVisitor trace;

	@Rule
	public ExpectedException exception = ExpectedException.none();

	@Before
	public void setup() {
		printer = new Textifier();
		trace = new TraceMethodVisitor(printer);
	}

	@Test
	public void classReaderFor_should_read_java_13_class() {
		final byte[] bytes = createJava13Class();

		final ClassReader classReader = InstrSupport.classReaderFor(bytes);

		classReader.accept(new ClassVisitor(InstrSupport.ASM_API_VERSION) {
			@Override
			public void visit(final int version, final int access,
					final String name, final String signature,
					final String superName, final String[] interfaces) {
				assertEquals(Opcodes.V12 + 1, version);
			}
		}, 0);

		assertArrayEquals(createJava13Class(), bytes);
	}

	private static byte[] createJava13Class() {
		final ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V12 + 1, 0, "Foo", null, "java/lang/Object", null);
		cw.visitEnd();
		return cw.toByteArray();
	}

	@Test
	public void getMajorVersion_should_read_unsigned_two_bytes_at_offset_6() {
		final byte[] bytes = new byte[] { //
				(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, // magic
				(byte) 0xFF, (byte) 0xFF, // minor_version
				(byte) 0x80, (byte) 0x12 // major_version
		};

		assertEquals(32786, InstrSupport.getMajorVersion(bytes));
	}

	@Test
	public void setMajorVersion_should_write_unsigned_two_bytes_at_offset_6() {
		final byte[] bytes = new byte[8];

		InstrSupport.setMajorVersion(32786, bytes);

		assertArrayEquals(
				new byte[] { (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, // magic
						(byte) 0x00, (byte) 0x00, // minor_version
						(byte) 0x80, (byte) 0x12 // major_version
				}, bytes);
	}

	@Test
	public void getVersionMajor_should_return_major_version() {
		final byte[] bytes = new byte[] { //
				(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, // magic
				0x00, 0x03, // minor_version
				(byte) 0x80, 0x12, // major_version
				0x00, 0x02, // constant_pool_count
				0x01, 0x00, 0x00 // constant_pool
		};

		assertEquals(32786, InstrSupport.getVersionMajor(new ClassReader(bytes)));
	}

	@Test
	public void needFrames_should_return_false_for_versions_less_than_1_6() {
		assertFalse(InstrSupport.needsFrames(Opcodes.V1_1));
		assertFalse(InstrSupport.needsFrames(Opcodes.V1_2));
		assertFalse(InstrSupport.needsFrames(Opcodes.V1_3));
		assertFalse(InstrSupport.needsFrames(Opcodes.V1_4));
		assertFalse(InstrSupport.needsFrames(Opcodes.V1_5));
	}

	@Test
	public void needFrames_should_return_true_for_versions_greater_than_or_equal_to_1_6() {
		assertTrue(InstrSupport.needsFrames(Opcodes.V1_6));
		assertTrue(InstrSupport.needsFrames(Opcodes.V1_7));
		assertTrue(InstrSupport.needsFrames(Opcodes.V1_8));
		assertTrue(InstrSupport.needsFrames(Opcodes.V9));
		assertTrue(InstrSupport.needsFrames(Opcodes.V10));
		assertTrue(InstrSupport.needsFrames(Opcodes.V11));
		assertTrue(InstrSupport.needsFrames(Opcodes.V12));
		assertTrue(InstrSupport.needsFrames(Opcodes.V12 + 1));
	}

	@Test
	public void assertNotIntrumented_should_accept_non_jacoco_memebers() {
		InstrSupport.assertNotInstrumented("run", "Foo");
	}

	@Test
	public void assertNotIntrumented_should_throw_exception_when_jacoco_data_field_is_present() {
		exception.expect(IllegalStateException.class);
		exception.expectMessage(
				"Cannot process instrumented class Foo. Please supply original non-instrumented classes.");

		InstrSupport.assertNotInstrumented("$jacocoData", "Foo");
	}

	@Test
	public void assertNotIntrumented_should_throw_exception_when_jacoco_init_method_is_present() {
		exception.expect(IllegalStateException.class);
		exception.expectMessage(
				"Cannot process instrumented class Foo. Please supply original non-instrumented classes.");

		InstrSupport.assertNotInstrumented("$jacocoInit", "Foo");
	}

	@Test
	public void testPushIntM2147483648() {
		InstrSupport.push(trace, -2147483648);
		assertInstruction("LDC -2147483648");
	}

	@Test
	public void testPushIntM32768() {
		InstrSupport.push(trace, -32768);
		assertInstruction("SIPUSH -32768");
	}

	@Test
	public void testPushIntM128() {
		InstrSupport.push(trace, -128);
		assertInstruction("BIPUSH -128");
	}

	@Test
	public void testPushIntM1() {
		InstrSupport.push(trace, -1);
		assertInstruction("ICONST_M1");
	}

	@Test
	public void testPushInt0() {
		InstrSupport.push(trace, 0);
		assertInstruction("ICONST_0");
	}

	@Test
	public void testPushInt1() {
		InstrSupport.push(trace, 1);
		assertInstruction("ICONST_1");
	}

	@Test
	public void testPushInt2() {
		InstrSupport.push(trace, 2);
		assertInstruction("ICONST_2");
	}

	@Test
	public void testPushInt3() {
		InstrSupport.push(trace, 3);
		assertInstruction("ICONST_3");
	}

	@Test
	public void testPushInt4() {
		InstrSupport.push(trace, 4);
		assertInstruction("ICONST_4");
	}

	@Test
	public void testPushInt5() {
		InstrSupport.push(trace, 5);
		assertInstruction("ICONST_5");
	}

	@Test
	public void testPushInt127() {
		InstrSupport.push(trace, 127);
		assertInstruction("BIPUSH 127");
	}

	@Test
	public void testPushInt32767() {
		InstrSupport.push(trace, 32767);
		assertInstruction("SIPUSH 32767");
	}

	@Test
	public void testPushInt2147483647() {
		InstrSupport.push(trace, 2147483647);
		assertInstruction("LDC 2147483647");
	}

	private void assertInstruction(String expected) {
		assertEquals(1, printer.getText().size());
		assertEquals(expected, printer.getText().get(0).toString().trim());
	}

}
