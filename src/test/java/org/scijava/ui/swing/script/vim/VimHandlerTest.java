/*
 * #%L
 * Script Editor and Interpreter for SciJava script languages.
 * %%
 * Copyright (C) 2009 - 2026 SciJava developers.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package org.scijava.ui.swing.script.vim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.scijava.ui.swing.script.vim.VimHandler.ESC;
import static org.scijava.ui.swing.script.vim.VimHandler.ctrl;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import org.fife.ui.rtextarea.RTextArea;
import org.junit.Before;
import org.junit.Test;
import org.scijava.ui.swing.script.vim.VimHandler.Mode;

/**
 * Tests {@link VimHandler}.
 *
 * @author Curtis Rueden
 */
public class VimHandlerTest {

	private RTextArea area;
	private VimHandler vim;

	@Before
	public void setUp() {
		area = new RTextArea();
		vim = new VimHandler(area);
		vim.setEnabled(true);
	}

	/**
	 * Sets the text, with the caret where '|' is, then types the given keys.
	 */
	private void run(final String text, final String keys) {
		onEDT(() -> {
			final int caret = text.indexOf('|');
			area.setText(text.replace("|", ""));
			area.discardAllEdits();
			area.setCaretPosition(Math.max(caret, 0));
		});
		type(keys);
	}

	private void type(final String keys) {
		onEDT(() -> vim.feed(keys));
	}

	/**
	 * Runs code on the EDT, as {@link VimHandler} queues caret adjustments
	 * there.
	 */
	private static void onEDT(final Runnable r) {
		try {
			SwingUtilities.invokeAndWait(r);
			SwingUtilities.invokeAndWait(() -> {});
		}
		catch (final InterruptedException | InvocationTargetException exc) {
			throw new RuntimeException(exc);
		}
	}

	/** Asserts the text, with the caret where '|' is. */
	private void assertText(final String expected) {
		final int caret = area.getCaretPosition();
		final String t = area.getText();
		assertEquals(expected, t.substring(0, caret) + "|" + t.substring(caret));
	}

	@Test
	public void testMotions() {
		run("|foo bar.baz\nqux", "w");
		assertText("foo |bar.baz\nqux");
		type("w");
		assertText("foo bar|.baz\nqux");
		type("W");
		assertText("foo bar.baz\n|qux");
		type("b");
		assertText("foo bar.|baz\nqux");
		type("$");
		assertText("foo bar.ba|z\nqux");
		type("0e");
		assertText("fo|o bar.baz\nqux");
		type("j");
		assertText("foo bar.baz\nqu|x");
		type("ggfa");
		assertText("foo b|ar.baz\nqux");
		type(";");
		assertText("foo bar.b|az\nqux");
		type("G");
		assertText("foo bar.baz\n|qux");
	}

	@Test
	public void testVerticalMotionKeepsColumn() {
		run("abc|def\nx\nabcdef", "jj");
		assertText("abcdef\nx\nabc|def");
		run("ab|c\nabcdef\nab", "$jj");
		assertText("abc\nabcdef\na|b");
		type("k");
		assertText("abc\nabcde|f\nab");
	}

	@Test
	public void testCounts() {
		run("|a b c d e", "3w");
		assertText("a b c |d e");
		type("2dw");
		assertText("a b c| ");
		assertEquals("a b c ", area.getText());
	}

	@Test
	public void testInsert() {
		run("|world", "ihello " + ESC);
		assertText("hello| world");
		assertEquals(Mode.NORMAL, vim.getMode());
		type("A!" + ESC);
		assertText("hello world|!");
		type("ox" + ESC + "Oy" + ESC);
		assertText("hello world!\n|y\nx");
		type("I-" + ESC);
		assertText("hello world!\n|-y\nx");
	}

	@Test
	public void testOpenLineKeepsIndent() {
		run("\tfo|o", "obar" + ESC);
		assertText("\tfoo\n\tba|r");
	}

	@Test
	public void testDeleteAndPut() {
		run("o|ne\ntwo\nthree", "dd");
		assertText("|two\nthree");
		type("p");
		assertText("two\n|one\nthree");
		type("x");
		assertText("two\n|ne\nthree");
		type("P");
		assertText("two\n|one\nthree");
		type("Gdd");
		assertText("two\n|one");
		type("D");
		assertText("two\n|");
	}

	@Test
	public void testPutLinewiseAtEnd() {
		run("o|ne\ntwo", "yyjp");
		assertText("one\ntwo\n|one");
	}

	@Test
	public void testDeleteWordStopsAtLineEnd() {
		run("foo |bar\nbaz", "dw");
		assertText("foo| \nbaz");
	}

	@Test
	public void testChange() {
		run("|foo bar", "cwbaz" + ESC);
		assertText("ba|z bar");
		run("foo(|a, b)", "ci(x" + ESC);
		assertText("foo(|x)");
		run("s = \"he|llo\"", "ci\"bye" + ESC);
		assertText("s = \"by|e\"");
		run("  fo|o\nbar", "ccx" + ESC);
		assertText("  |x\nbar");
		run("foo |bar baz", "C!" + ESC);
		assertText("foo |!");
	}

	@Test
	public void testTextObjects() {
		run("foo b|ar baz", "daw");
		assertText("foo |baz");
		run("a [b, [c|]] d", "di[");
		assertText("a [b, [|]] d");
		run("a [b, [c]|] d", "da[");
		assertText("a | d");
		run("if (x) {\n\tfo|o();\n}", "diB");
		assertText("if (x) {|}");
	}

	@Test
	public void testUndoRedo() {
		run("|one two", "dwdw");
		assertEquals("", area.getText());
		type("u");
		assertEquals("two", area.getText());
		type("u");
		assertEquals("one two", area.getText());
		type(String.valueOf(ctrl('r')));
		assertEquals("two", area.getText());
	}

	@Test
	public void testRepeat() {
		run("|a b c d", "dw..");
		assertText("|d");
		run("|foo\nfoo\nfoo", "cwbar" + ESC + "j0.j0.");
		assertEquals("bar\nbar\nbar", area.getText());
		type("u");
		assertEquals("bar\nbar\nfoo", area.getText());
	}

	@Test
	public void testVisual() {
		run("|one two three", "vwd");
		assertText("|wo three");
		assertEquals(Mode.NORMAL, vim.getMode());
		run("a\n|b\nc\nd", "Vjy");
		assertText("a\n|b\nc\nd");
		type("Gp");
		assertText("a\nb\nc\nd\n|b\nc");
		run("|abc def", "veU");
		assertText("|ABC def");
		run("a\n|b\nc", "Vjd");
		assertText("|a");
	}

	@Test
	public void testVisualFromMouseSelection() {
		run("|hello world", "");
		onEDT(() -> area.select(6, 11));
		type("d");
		assertText("hello| ");
	}

	@Test
	public void testShiftAndJoin() {
		area.setTabsEmulated(true);
		area.setTabSize(2);
		run("|a\nb", ">j");
		assertEquals("  a\n  b", area.getText());
		type("<<");
		assertEquals("a\n  b", area.getText());
		type("J");
		assertText("a| b");
	}

	@Test
	public void testReplaceAndCase() {
		run("|abc", "rx");
		assertText("|xbc");
		type("2~");
		assertText("XB|c");
		type("gUU");
		assertText("|XBC");
	}

	@Test
	public void testSearch() {
		run("|foo bar foo bar", "/bar\n");
		assertText("foo |bar foo bar");
		type("n");
		assertText("foo bar foo |bar");
		type("n");
		assertText("foo |bar foo bar");
		type("N");
		assertText("foo bar foo |bar");
		type("0*");
		assertText("foo bar |foo bar");
	}

	@Test
	public void testEx() {
		run("|a\nb\nc", ":3\n");
		assertText("a\nb\n|c");
		run("|foo foo\nfoo", ":%s/foo/bar/g\n");
		assertEquals("bar bar\nbar", area.getText());
		run("|x1 x2", ":s/x(\\d)/<\\1>/\n");
		assertEquals("<1> x2", area.getText());

		final List<String> commands = new ArrayList<>();
		vim.setExHandler(cmd -> commands.add(cmd) && cmd.equals("w"));
		type(":w\n");
		assertEquals("[w]", commands.toString());
		assertNull(vimMessage());
		type(":bogus\n");
		assertTrue(vimMessage().startsWith("E492"));
	}

	@Test
	public void testRegisters() {
		run("|one two", "\"ayw" + "w\"byw" + "\"aP");
		assertText("one one| two");
		type("\"bp");
		assertText("one one tw|otwo");
	}

	@Test
	public void testStatus() {
		final List<String> statuses = new ArrayList<>();
		vim.setStatusListener(statuses::add);
		run("|abc", "i");
		assertEquals("-- INSERT --", vim.getStatus());
		type(ESC + "2d");
		assertEquals("2d", vim.getStatus());
		type(String.valueOf(ESC));
		type(":wq");
		assertEquals(":wq", vim.getStatus());
		onEDT(() -> vim.setEnabled(false));
		assertNull(statuses.get(statuses.size() - 1));
	}

	@Test
	public void testCaretStaysOnLine() {
		run("ab|c\n", "l");
		assertText("ab|c\n");
		run("|abc", "a" + ESC);
		assertText("|abc");
		run("|abc", "A" + ESC);
		assertText("ab|c");
	}

	@Test
	public void testKeyEventRouting() {
		run("|abc", "");
		// Typed characters are consumed in normal mode...
		assertTrue(vim.processKeyEvent(typed('x', 0)));
		assertEquals("bc", area.getText());
		// ...as are their key presses, so no bindings fire.
		assertTrue(vim.processKeyEvent(pressed(KeyEvent.VK_X, 0)));
		// Shortcuts and function keys stay available to menus.
		assertFalse(vim.processKeyEvent(pressed(KeyEvent.VK_S,
			InputEvent.META_DOWN_MASK)));
		assertFalse(vim.processKeyEvent(pressed(KeyEvent.VK_F5, 0)));
		// Insert mode leaves typing to the text area.
		vim.processKeyEvent(typed('i', 0));
		assertEquals(Mode.INSERT, vim.getMode());
		assertFalse(vim.processKeyEvent(typed('y', 0)));
		// Escape is handled, but passed on so it can also close popups.
		assertFalse(vim.processKeyEvent(pressed(KeyEvent.VK_ESCAPE, 0)));
		assertEquals(Mode.NORMAL, vim.getMode());
	}

	private KeyEvent typed(final char c, final int modifiers) {
		return new KeyEvent(area, KeyEvent.KEY_TYPED, 0, modifiers,
			KeyEvent.VK_UNDEFINED, c);
	}

	private KeyEvent pressed(final int code, final int modifiers) {
		return new KeyEvent(area, KeyEvent.KEY_PRESSED, 0, modifiers, code,
			KeyEvent.CHAR_UNDEFINED);
	}

	private String vimMessage() {
		final String status = vim.getStatus();
		return status.isEmpty() ? null : status;
	}
}
