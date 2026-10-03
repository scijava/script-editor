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

import java.awt.HeadlessException;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.CaretListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.Element;
import javax.swing.text.Position;

import org.fife.ui.rtextarea.CaretStyle;
import org.fife.ui.rtextarea.ConfigurableCaret;
import org.fife.ui.rtextarea.RTextArea;

/**
 * Modal, vim-style key handling for an {@link RTextArea}.
 * <p>
 * Key events are routed here via {@link #processKeyEvent(KeyEvent)}; keys can
 * also be fed directly via {@link #feed(String)}. Control characters stand in
 * for special keys: {@link #ESC} for Escape, {@code '\n'} for Enter,
 * {@code '\b'} for Backspace, and {@code ctrl('r')} etc. for Ctrl+key.
 * </p>
 * <p>
 * Search and {@code :s} patterns are Java regular expressions, plus vim's
 * {@code \<} and {@code \>} word boundaries.
 * </p>
 *
 * @author Curtis Rueden
 */
public class VimHandler {

	public enum Mode {
		NORMAL, INSERT, VISUAL, VISUAL_LINE, COMMAND_LINE
	}

	public static final char ESC = 27;

	public static char ctrl(final char c) {
		return (char) (c & 0x1f);
	}

	private final RTextArea area;
	private boolean enabled;
	private Mode mode = Mode.NORMAL;

	/** Keys of the normal/visual mode command typed so far. */
	private final StringBuilder pending = new StringBuilder();

	/** Text typed after ':', '/' or '?'. */
	private final StringBuilder cmdLine = new StringBuilder();
	private char cmdLineType;
	private String message;

	private final Map<Character, Register> registers = new HashMap<>();
	private final Map<Character, Position> marks = new HashMap<>();

	private int visualAnchor, visualCursor;
	private int visualSelStart = -1, visualSelEnd = -1;
	private int lastVisualStartLine = -1, lastVisualEndLine = -1;

	/** Column to aim for on vertical motions; -1 if none. */
	private int desiredColumn = -1;

	/** Cursor position after the last command. */
	private int lastCursor = -1;

	private char lastFindType, lastFindChar;
	private String lastSearch;
	private boolean lastSearchForward = true;

	/** Keys which reproduce the last change, for '.'. */
	private String lastChange;
	private String insertPrefix;
	private StringBuilder insertRecording;
	private boolean replaying;
	private String replayKeys;

	/** Whether an atomic (single undo step) edit is in progress. */
	private boolean editing;

	private Consumer<String> statusListener;
	private Predicate<String> exHandler;
	private CaretStyle originalCaretStyle;
	private Boolean ctrlKeysFree;
	private final CaretListener caretClamp = e -> SwingUtilities.invokeLater(
		this::clampIfIdle);

	public VimHandler(final RTextArea area) {
		this.area = area;
	}

	// -- Configuration --

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(final boolean enabled) {
		if (this.enabled == enabled) return;
		this.enabled = enabled;
		pending.setLength(0);
		cmdLine.setLength(0);
		message = null;
		mode = Mode.NORMAL;
		if (enabled) {
			if (area.getCaret() instanceof ConfigurableCaret) {
				originalCaretStyle = ((ConfigurableCaret) area.getCaret()).getStyle();
			}
			area.addCaretListener(caretClamp);
			area.setCaretPosition(area.getCaretPosition()); // drop any selection
			clamp();
		}
		else {
			area.removeCaretListener(caretClamp);
		}
		updateCaretStyle();
		fireStatus();
	}

	public Mode getMode() {
		return mode;
	}

	/**
	 * Sets the listener notified of status line changes: the current mode,
	 * partial command or message; {@code null} when vim mode is disabled.
	 */
	public void setStatusListener(final Consumer<String> listener) {
		statusListener = listener;
		fireStatus();
	}

	/**
	 * Sets the handler for ex commands this class does not know, such as
	 * {@code w} or {@code q}. It returns false if it does not know the command
	 * either.
	 */
	public void setExHandler(final Predicate<String> handler) {
		exHandler = handler;
	}

	public String getStatus() {
		if (!enabled) return null;
		if (mode == Mode.COMMAND_LINE) return cmdLineType + cmdLine.toString();
		if (message != null) return message;
		final String modeText;
		switch (mode) {
			case INSERT:
				modeText = "-- INSERT --";
				break;
			case VISUAL:
				modeText = "-- VISUAL --";
				break;
			case VISUAL_LINE:
				modeText = "-- VISUAL LINE --";
				break;
			default:
				modeText = "";
		}
		if (pending.length() == 0) return modeText;
		return modeText + (modeText.isEmpty() ? "" : "   ") + printable(pending);
	}

	// -- Key handling --

	/**
	 * Handles a key event destined for the text area.
	 *
	 * @return true if the event was handled, and must not be processed further.
	 */
	public boolean processKeyEvent(final KeyEvent e) {
		if (!enabled) return false;
		final int id = e.getID();
		final int code = e.getKeyCode();
		final boolean ctrlDown = e.isControlDown();
		final boolean other = e.isMetaDown() || e.isAltDown() || e.isAltGraphDown();

		if (id == KeyEvent.KEY_PRESSED && (code == KeyEvent.VK_ESCAPE ||
			ctrlDown && !other && code == KeyEvent.VK_OPEN_BRACKET))
		{
			feed(ESC);
			// Note: Escape is passed on, so that it also dismisses popups.
			return code != KeyEvent.VK_ESCAPE;
		}

		if (mode == Mode.INSERT) {
			recordInsert(e);
			return false;
		}

		if (id == KeyEvent.KEY_TYPED) {
			final char c = e.getKeyChar();
			if (!e.isMetaDown() && !(ctrlDown && !e.isAltDown()) && c >= ' ' &&
				c != KeyEvent.VK_DELETE)
			{
				feed(c);
			}
			// Note: we swallow everything else, so that nothing gets inserted.
			return true;
		}
		if (id != KeyEvent.KEY_PRESSED) return false;

		if (other) return false;
		if (ctrlDown) {
			if (!areCtrlKeysFree()) return false;
			final char c = Character.toLowerCase((char) code);
			if ("rdufbnp".indexOf(c) < 0) return false;
			feed(ctrl(c));
			return true;
		}
		final boolean cmd = mode == Mode.COMMAND_LINE;
		switch (code) {
			case KeyEvent.VK_ENTER:
				feed('\n');
				return true;
			case KeyEvent.VK_BACK_SPACE:
				feed(cmd ? '\b' : 'h');
				return true;
			case KeyEvent.VK_DELETE:
				if (!cmd) feed('x');
				return true;
			case KeyEvent.VK_LEFT:
				if (!cmd) feed('h');
				return true;
			case KeyEvent.VK_RIGHT:
				if (!cmd) feed('l');
				return true;
			case KeyEvent.VK_UP:
				if (!cmd) feed('k');
				return true;
			case KeyEvent.VK_DOWN:
				if (!cmd) feed('j');
				return true;
			case KeyEvent.VK_HOME:
				if (!cmd) feed('0');
				return true;
			case KeyEvent.VK_END:
				if (!cmd) feed('$');
				return true;
			case KeyEvent.VK_PAGE_UP:
				if (!cmd) feed(ctrl('b'));
				return true;
			case KeyEvent.VK_PAGE_DOWN:
				if (!cmd) feed(ctrl('f'));
				return true;
			default:
				// Note: unhandled function keys etc. stay available as shortcuts.
				return !e.isActionKey();
		}
	}

	public void feed(final String keys) {
		for (final char c : keys.toCharArray())
			feed(c);
	}

	public void feed(final char c) {
		if (!enabled) return;
		message = null;
		try {
			switch (mode) {
				case INSERT:
					insertKey(c);
					break;
				case COMMAND_LINE:
					commandLineKey(c);
					break;
				default:
					commandKey(c);
			}
		}
		finally {
			endEdit();
		}
		final String replay = replayKeys;
		if (replay != null) {
			replayKeys = null;
			replaying = true;
			area.beginAtomicEdit();
			try {
				feed(replay);
			}
			finally {
				area.endAtomicEdit();
				replaying = false;
			}
		}
		updateCaretStyle();
		fireStatus();
	}

	// -- Insert mode --

	private void insertKey(final char c) {
		if (c == ESC) {
			leaveInsert();
			return;
		}
		recordInsert(c);
		final int start = area.getSelectionStart(), end = area.getSelectionEnd();
		if (c == '\b') {
			if (start < end) remove(start, end);
			else if (start > 0) remove(start - 1, start);
		}
		else {
			replace(start, end, String.valueOf(c));
			// Note: the caret only follows insertions on the EDT by default.
			area.setCaretPosition(start + 1);
		}
	}

	private void recordInsert(final KeyEvent e) {
		if (e.getID() == KeyEvent.KEY_TYPED) {
			if (e.isControlDown() || e.isMetaDown()) return;
			final char c = e.getKeyChar();
			if (c >= ' ' && c != KeyEvent.VK_DELETE || c == '\n' || c == '\b' ||
				c == '\t') recordInsert(c);
		}
		else if (e.getID() == KeyEvent.KEY_PRESSED && e.isActionKey() &&
			insertRecording != null)
		{
			// The caret moved elsewhere; '.' will only repeat what follows.
			insertPrefix = "i";
			insertRecording.setLength(0);
		}
	}

	private void recordInsert(final char c) {
		if (insertRecording != null && !replaying) insertRecording.append(c);
	}

	private void enterInsert(final int pos, final String keys) {
		area.setCaretPosition(pos);
		mode = Mode.INSERT;
		insertPrefix = keys;
		insertRecording = new StringBuilder();
	}

	private void leaveInsert() {
		mode = Mode.NORMAL;
		if (insertRecording != null && !replaying) {
			lastChange = insertPrefix + insertRecording + ESC;
		}
		insertRecording = null;
		final int pos = area.getCaretPosition();
		if (pos > lineStart(pos)) area.setCaretPosition(pos - 1);
		clamp();
	}

	// -- Command line mode --

	private void enterCommandLine(final char type, final String initial) {
		cmdLineType = type;
		cmdLine.setLength(0);
		cmdLine.append(initial);
		mode = Mode.COMMAND_LINE;
	}

	private void commandLineKey(final char c) {
		if (c == ESC) {
			mode = Mode.NORMAL;
			return;
		}
		if (c == '\b') {
			if (cmdLine.length() == 0) mode = Mode.NORMAL;
			else cmdLine.setLength(cmdLine.length() - 1);
			return;
		}
		if (c != '\n') {
			cmdLine.append(c);
			return;
		}
		mode = Mode.NORMAL;
		final String line = cmdLine.toString();
		if (cmdLineType == ':') ex(line.trim());
		else {
			if (!line.isEmpty()) lastSearch = line;
			lastSearchForward = cmdLineType == '/';
			final int pos = search(area.getCaretPosition(), lastSearchForward);
			if (pos >= 0) moveTo(pos);
		}
		clamp();
	}

	private static final Pattern EX_RANGE = Pattern.compile(
		"^(%|'<,'>|([0-9]+|\\.|\\$)(?:,([0-9]+|\\.|\\$))?)?\\s*(.*)$");

	private void ex(final String command) {
		if (command.isEmpty()) return;
		final Matcher m = EX_RANGE.matcher(command);
		if (!m.matches()) return;
		final String range = m.group(1);
		final String rest = m.group(4);
		final int cur = lineOf(area.getCaretPosition());
		int first = cur, last = cur;
		if ("%".equals(range)) {
			first = 0;
			last = lineCount() - 1;
		}
		else if ("'<,'>".equals(range)) {
			if (lastVisualStartLine < 0) {
				message = "E20: Mark not set";
				return;
			}
			first = lastVisualStartLine;
			last = Math.min(lastVisualEndLine, lineCount() - 1);
		}
		else if (range != null) {
			first = exLine(m.group(2), cur);
			last = m.group(3) == null ? first : exLine(m.group(3), cur);
		}
		if (first > last) {
			final int t = first;
			first = last;
			last = t;
		}

		if (rest.isEmpty()) {
			if (range != null) moveTo(firstNonBlank(last));
		}
		else if (rest.equals("d")) {
			deleteLines(first, last, '"');
		}
		else if (rest.startsWith("s") && rest.length() > 1 &&
			!Character.isLetterOrDigit(rest.charAt(1)))
		{
			substitute(rest.substring(1), first, last);
		}
		else if (rest.equals("noh") || rest.equals("nohlsearch")) {
			// Note: searches are not highlighted, so there is nothing to clear.
		}
		else if (range != null || exHandler == null || !exHandler.test(rest)) {
			message = "E492: Not an editor command: " + command;
		}
	}

	private int exLine(final String spec, final int cur) {
		if (".".equals(spec)) return cur;
		if ("$".equals(spec)) return lineCount() - 1;
		return Math.max(0, Math.min(Integer.parseInt(spec) - 1, lineCount() - 1));
	}

	private void substitute(final String args, final int first, final int last) {
		final char delim = args.charAt(0);
		final List<String> parts = new ArrayList<>();
		final StringBuilder part = new StringBuilder();
		for (int i = 1; i < args.length(); i++) {
			final char c = args.charAt(i);
			if (c == '\\' && i + 1 < args.length() && args.charAt(i + 1) == delim) {
				part.append(delim);
				i++;
			}
			else if (c == delim && parts.size() < 2) {
				parts.add(part.toString());
				part.setLength(0);
			}
			else part.append(c);
		}
		parts.add(part.toString());
		while (parts.size() < 3)
			parts.add("");
		final String flags = parts.get(2);
		if (!parts.get(0).isEmpty()) lastSearch = parts.get(0);
		final Pattern p = compile(lastSearch, flags.contains("i"));
		if (p == null) return;
		final String replacement = replacement(parts.get(1));
		final boolean global = flags.contains("g");

		final String t = text();
		final int start = startOfLine(first), end = endOfLine(last);
		final String[] lines = t.substring(start, end).split("\n", -1);
		int lastChanged = -1;
		for (int i = 0; i < lines.length; i++) {
			final Matcher m = p.matcher(lines[i]);
			if (!m.find()) continue;
			lines[i] = global ? m.replaceAll(replacement) : m.replaceFirst(
				replacement);
			lastChanged = first + i;
		}
		if (lastChanged < 0) {
			message = "E486: Pattern not found: " + lastSearch;
			return;
		}
		replace(start, end, String.join("\n", lines));
		moveTo(firstNonBlank(lastChanged));
	}

	/** Converts a vim replacement string into a {@link Matcher} one. */
	private static String replacement(final String s) {
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			final char c = s.charAt(i);
			if (c == '\\' && i + 1 < s.length()) {
				final char n = s.charAt(++i);
				if (Character.isDigit(n)) sb.append('$').append(n);
				else if (n == 'n' || n == 'r') sb.append('\n');
				else if (n == 't') sb.append('\t');
				else sb.append(Matcher.quoteReplacement(String.valueOf(n)));
			}
			else if (c == '&') sb.append("$0");
			else sb.append(Matcher.quoteReplacement(String.valueOf(c)));
		}
		return sb.toString();
	}

	// -- Normal and visual mode --

	/** Thrown when a command needs more keys. */
	private static class Incomplete extends RuntimeException {

		Incomplete() {
			super(null, null, false, false);
		}
	}

	/** Thrown when a command cannot be carried out. */
	private static class Invalid extends RuntimeException {

		Invalid() {
			super(null, null, false, false);
		}
	}

	private static final Incomplete INCOMPLETE = new Incomplete();
	private static final Invalid INVALID = new Invalid();

	private static class Keys {

		private final String s;
		private int i;

		Keys(final String s) {
			this.s = s;
		}

		char next() {
			if (i >= s.length()) throw INCOMPLETE;
			return s.charAt(i++);
		}

		char peek() {
			if (i >= s.length()) throw INCOMPLETE;
			return s.charAt(i);
		}

		/** Parses a count; 0 if there is none. */
		int count() {
			int n = 0;
			while (i < s.length() && Character.isDigit(s.charAt(i)) && (n > 0 || s
				.charAt(i) != '0'))
			{
				n = 10 * n + s.charAt(i++) - '0';
				if (i >= s.length()) throw INCOMPLETE;
			}
			return n;
		}
	}

	private static class Register {

		final String text;
		final boolean linewise;

		Register(final String text, final boolean linewise) {
			this.text = text;
			this.linewise = linewise;
		}
	}

	/** Result of a motion. */
	private static class Motion {

		int target;
		boolean linewise, inclusive, vertical;

		Motion(final int target) {
			this.target = target;
		}

		Motion linewise() {
			linewise = true;
			return this;
		}

		Motion inclusive() {
			inclusive = true;
			return this;
		}
	}

	private void commandKey(final char c) {
		if (mode == Mode.NORMAL) adoptForeignSelection();
		else syncVisual();
		if (c == ESC) {
			if (pending.length() == 0 && isVisual()) exitVisual(visualCursor);
			pending.setLength(0);
			return;
		}
		pending.append(c);
		final String keys = pending.toString();
		try {
			if (mode == Mode.NORMAL) normal(new Keys(keys), keys);
			else visual(new Keys(keys));
		}
		catch (final Incomplete exc) {
			return;
		}
		catch (final Invalid exc) {
			beep();
		}
		catch (final BadLocationException exc) {
			beep();
		}
		pending.setLength(0);
		lastCursor = isVisual() ? visualCursor : area.getCaretPosition();
	}

	private void normal(final Keys k, final String keys)
		throws BadLocationException
	{
		final char reg = register(k);
		final int count1 = k.count();
		final int n = Math.max(1, count1);
		final int cur = area.getCaretPosition();
		final char c = k.next();
		switch (c) {
			case 'd':
			case 'c':
			case 'y':
			case '<':
			case '>':
				operator(String.valueOf(c), count1, k, reg, keys);
				return;
			case 'x':
				operator("d", count1, new Keys("l"), reg, keys);
				return;
			case 'X':
				operator("d", count1, new Keys("h"), reg, keys);
				return;
			case 's':
				operator("c", count1, new Keys("l"), reg, keys);
				return;
			case 'S':
				operator("c", count1, new Keys("c"), reg, keys);
				return;
			case 'C':
				operator("c", count1, new Keys("$"), reg, keys);
				return;
			case 'D':
				operator("d", count1, new Keys("$"), reg, keys);
				return;
			case 'Y':
				operator("y", count1, new Keys("y"), reg, keys);
				return;
			case 'i':
				enterInsert(cur, keys);
				return;
			case 'a':
				enterInsert(cur < lineEnd(cur) ? cur + 1 : cur, keys);
				return;
			case 'I':
				enterInsert(firstNonBlank(lineOf(cur)), keys);
				return;
			case 'A':
				enterInsert(lineEnd(cur), keys);
				return;
			case 'o': {
				final int line = lineOf(cur);
				final String indent = indentOf(line);
				final int end = lineEnd(cur);
				replace(end, end, "\n" + indent);
				enterInsert(end + 1 + indent.length(), keys);
				return;
			}
			case 'O': {
				final int line = lineOf(cur);
				final String indent = indentOf(line);
				final int start = lineStart(cur);
				replace(start, start, indent + "\n");
				enterInsert(start + indent.length(), keys);
				return;
			}
			case 'p':
			case 'P':
				put(reg, n, c == 'p');
				lastChange = keys;
				return;
			case 'J':
				join(lineOf(cur), Math.max(2, n), true);
				lastChange = keys;
				return;
			case 'r': {
				final char r = k.next();
				if (r == ESC) return;
				final int end = cur + n;
				if (end > lineEnd(cur)) throw INVALID;
				if (r == '\n') {
					replace(cur, end, "\n");
					moveTo(cur + 1);
				}
				else {
					replace(cur, end, repeat(String.valueOf(r), n));
					moveTo(end - 1);
				}
				lastChange = keys;
				return;
			}
			case '~': {
				final int end = Math.min(cur + n, lineEnd(cur));
				if (end == cur) throw INVALID;
				replace(cur, end, toggleCase(text().substring(cur, end)));
				moveTo(end);
				lastChange = keys;
				return;
			}
			case 'u':
				for (int i = 0; i < n && area.canUndo(); i++)
					area.undoLastAction();
				clamp();
				return;
			case 'v':
				enterVisual(Mode.VISUAL);
				return;
			case 'V':
				enterVisual(Mode.VISUAL_LINE);
				return;
			case '.':
				if (lastChange == null) throw INVALID;
				replayKeys = count1 > 0 ? withCount(lastChange, count1) : lastChange;
				return;
			case ':':
				enterCommandLine(':', count1 == 0 ? "" : count1 == 1 ? "." : ".," +
					Math.min(lineCount(), lineOf(cur) + count1));
				return;
			case '/':
			case '?':
				enterCommandLine(c, "");
				return;
			case 'm': {
				final char name = k.next();
				if (!Character.isLetter(name)) throw INVALID;
				marks.put(name, area.getDocument().createPosition(cur));
				return;
			}
			case 'Z': {
				final char z = k.next();
				if (z == 'Z') ex("x");
				else if (z == 'Q') ex("q!");
				else throw INVALID;
				return;
			}
			case 'z': {
				final char z = k.next();
				if (z != 'z' && z != 't' && z != 'b') throw INVALID;
				scrollTo(cur, z);
				return;
			}
			case 'g': {
				final char g = k.peek();
				if (g == 'u' || g == 'U' || g == '~') {
					k.next();
					operator("g" + g, count1, k, reg, keys);
					return;
				}
				if (g == 'J') {
					k.next();
					join(lineOf(cur), Math.max(2, n), false);
					lastChange = keys;
					return;
				}
				break;
			}
			default:
				if (c == ctrl('r')) {
					for (int i = 0; i < n && area.canRedo(); i++)
						area.redoLastAction();
					clamp();
					return;
				}
		}
		// Not a command, so it must be a motion.
		k.i--;
		final Motion m = motion(k, count1, ' ');
		if (m == null) throw INVALID;
		moveTo(m.linewise && !m.vertical ? firstNonBlank(lineOf(m.target))
			: m.target);
		if (!m.vertical) desiredColumn = -1;
	}

	/** Parses an optional register prefix, e.g. {@code "a}. */
	private char register(final Keys k) {
		if (k.peek() != '"') return '"';
		k.next();
		final char r = k.next();
		if (!Character.isLetterOrDigit(r) && "\"+*-".indexOf(r) < 0) throw INVALID;
		return r;
	}

	private void operator(final String op, final int count1, final Keys k,
		final char reg, final String keys) throws BadLocationException
	{
		final int count2 = k.count();
		final boolean hasCount = count1 > 0 || count2 > 0;
		final int n = Math.max(1, count1) * Math.max(1, count2);
		final int cur = area.getCaretPosition();
		final char m = k.next();
		final char opKey = op.charAt(op.length() - 1);
		final int start, end;
		final boolean linewise;
		if (m == opKey || op.length() == 2 && m == 'g' && k.peek() == opKey) {
			// Doubled operator, e.g. dd: apply to [count] lines.
			if (m != opKey) k.next();
			final int line = lineOf(cur);
			start = line;
			end = Math.min(line + n - 1, lineCount() - 1);
			linewise = true;
		}
		else if (m == 'i' || m == 'a') {
			final int[] range = textObject(m == 'a', k.next(), cur, cur);
			start = range[0];
			end = range[1];
			linewise = false;
		}
		else {
			k.i--;
			final Motion mo = motion(k, hasCount ? n : 0, opKey);
			if (mo == null) throw INVALID;
			if (mo.linewise) {
				start = Math.min(lineOf(cur), lineOf(mo.target));
				end = Math.max(lineOf(cur), lineOf(mo.target));
				linewise = true;
			}
			else {
				int s = Math.min(cur, mo.target);
				int e = Math.max(cur, mo.target);
				if (mo.inclusive) e = Math.min(e + 1, length());
				start = s;
				end = e;
				linewise = false;
			}
		}
		apply(op, start, end, linewise, reg, n);
		if (!op.equals("y") && !op.equals("c")) lastChange = keys;
	}

	/**
	 * Applies an operator to a range: offsets [start, end), or lines
	 * [start, end] if linewise.
	 */
	private void apply(final String op, final int start, final int end,
		final boolean linewise, final char reg, final int n)
		throws BadLocationException
	{
		final String keys = pending.toString();
		switch (op) {
			case "d":
				if (linewise) deleteLines(start, end, reg);
				else {
					setRegister(reg, text().substring(start, end), false, false);
					remove(start, end);
					moveTo(start);
				}
				return;
			case "y":
				if (linewise) {
					setRegister(reg, linesText(start, end), true, true);
					final int cur = area.getCaretPosition();
					if (lineOf(cur) != start) moveTo(firstNonBlank(start));
				}
				else {
					setRegister(reg, text().substring(start, end), false, true);
					moveTo(start);
				}
				return;
			case "c":
				if (linewise) {
					setRegister(reg, linesText(start, end), true, false);
					final int from = firstNonBlank(start);
					remove(from, endOfLine(end));
					enterInsert(from, keys);
				}
				else {
					setRegister(reg, text().substring(start, end), false, false);
					remove(start, end);
					enterInsert(start, keys);
				}
				return;
			case ">":
			case "<": {
				final int first = linewise ? start : lineOf(start);
				final int last = linewise ? end : lineOf(Math.max(start, end - 1));
				shift(first, last, op.equals(">") ? n : -n);
				moveTo(firstNonBlank(first));
				return;
			}
			default: {
				// Case operators: gu, gU, g~.
				final int s = linewise ? startOfLine(start) : start;
				final int e = linewise ? endOfLine(end) : end;
				final String t = text().substring(s, e);
				replace(s, e, op.equals("gu") ? t.toLowerCase() : op.equals("gU") ? t
					.toUpperCase() : toggleCase(t));
				moveTo(s);
			}
		}
	}

	private void visual(final Keys k) throws BadLocationException {
		final char reg = register(k);
		final int count1 = k.count();
		final int n = Math.max(1, count1);
		final char c = k.next();
		final boolean lines = mode == Mode.VISUAL_LINE;
		final int lo = Math.min(visualAnchor, visualCursor);
		final int hi = Math.max(visualAnchor, visualCursor);
		final int start = lines ? lineOf(lo) : lo;
		final int end = lines ? lineOf(hi) : Math.min(hi + 1, length());
		switch (c) {
			case 'v':
			case 'V': {
				final Mode m = c == 'v' ? Mode.VISUAL : Mode.VISUAL_LINE;
				if (mode == m) exitVisual(visualCursor);
				else {
					mode = m;
					updateVisual();
				}
				return;
			}
			case 'o': {
				final int t = visualAnchor;
				visualAnchor = visualCursor;
				visualCursor = t;
				updateVisual();
				return;
			}
			case 'd':
			case 'x':
			case 'y':
			case 'c':
			case 's':
			case '<':
			case '>':
			case 'u':
			case 'U':
			case '~': {
				exitVisual(lo);
				final String op = c == 'x' ? "d" : c == 's' ? "c" : c == 'u' ||
					c == 'U' ? "g" + c : c == '~' ? "g~" : String.valueOf(c);
				apply(op, start, end, lines, reg, n);
				// Note: '.' does not repeat visual mode changes.
				insertRecording = null;
				return;
			}
			case 'D':
			case 'X':
			case 'Y':
			case 'C':
			case 'S':
			case 'R': {
				exitVisual(lo);
				final String op = c == 'D' || c == 'X' ? "d" : c == 'Y' ? "y" : "c";
				apply(op, lineOf(lo), lineOf(hi), true, reg, n);
				insertRecording = null;
				return;
			}
			case 'J':
			case 'g':
				if (c == 'g' && k.peek() != 'J') break;
				if (c == 'g') k.next();
				exitVisual(lo);
				join(lineOf(lo), Math.max(2, lineOf(hi) - lineOf(lo) + 1), c == 'J');
				return;
			case 'r': {
				final char r = k.next();
				if (r == ESC) return;
				exitVisual(lo);
				final int s = lines ? lineStart(lo) : start;
				final int e = lines ? lineEnd(hi) : end;
				replace(s, e, text().substring(s, e).replaceAll("[^\n]", Matcher
					.quoteReplacement(String.valueOf(r))));
				moveTo(s);
				return;
			}
			case 'p':
			case 'P': {
				final Register r = getRegister(reg);
				if (r == null) throw INVALID;
				exitVisual(lo);
				if (lines) {
					deleteLines(start, end, '-');
					// Note: put above the following line, unless the last line was cut.
					put(r, n, start >= lineCount());
				}
				else {
					setRegister('-', text().substring(start, end), false, false);
					remove(start, end);
					area.setCaretPosition(start);
					put(r, n, r.linewise);
				}
				return;
			}
			case 'i':
			case 'a': {
				final int[] range = textObject(c == 'a', k.next(), lo, hi);
				visualAnchor = range[0];
				visualCursor = Math.max(range[0], range[1] - 1);
				updateVisual();
				return;
			}
			case ':':
				exitVisual(visualCursor);
				enterCommandLine(':', "'<,'>");
				return;
		}
		k.i--;
		final Motion m = motion(k, count1, ' ', visualCursor);
		if (m == null) throw INVALID;
		visualCursor = Math.min(m.target, Math.max(0, length() - 1));
		if (!m.vertical) desiredColumn = -1;
		updateVisual();
	}

	private void enterVisual(final Mode m) {
		mode = m;
		visualAnchor = visualCursor = area.getCaretPosition();
		updateVisual();
	}

	private void exitVisual(final int pos) {
		lastVisualStartLine = lineOf(Math.min(visualAnchor, visualCursor));
		lastVisualEndLine = lineOf(Math.max(visualAnchor, visualCursor));
		mode = Mode.NORMAL;
		visualSelStart = visualSelEnd = -1;
		moveTo(Math.min(pos, length()));
	}

	private boolean isVisual() {
		return mode == Mode.VISUAL || mode == Mode.VISUAL_LINE;
	}

	/** Highlights the visual selection in the text area. */
	private void updateVisual() {
		final int lo = Math.min(visualAnchor, visualCursor);
		final int hi = Math.max(visualAnchor, visualCursor);
		final int s, e;
		if (mode == Mode.VISUAL_LINE) {
			s = lineStart(lo);
			e = lineEnd(hi);
		}
		else {
			s = lo;
			e = Math.min(hi + 1, length());
		}
		if (visualCursor >= visualAnchor) {
			area.setCaretPosition(s);
			area.moveCaretPosition(e);
		}
		else {
			area.setCaretPosition(e);
			area.moveCaretPosition(s);
		}
		visualSelStart = s;
		visualSelEnd = e;
	}

	/** Starts visual mode if text was selected with the mouse. */
	private void adoptForeignSelection() {
		final Caret caret = area.getCaret();
		if (caret.getDot() == caret.getMark()) return;
		mode = Mode.VISUAL;
		adoptSelection(caret);
	}

	/** Reconciles the visual selection with changes made outside vim. */
	private void syncVisual() {
		if (area.getSelectionStart() == visualSelStart && area
			.getSelectionEnd() == visualSelEnd) return;
		final Caret caret = area.getCaret();
		if (caret.getDot() == caret.getMark()) {
			mode = Mode.NORMAL;
			visualSelStart = visualSelEnd = -1;
			clamp();
		}
		else {
			mode = Mode.VISUAL;
			adoptSelection(caret);
		}
	}

	private void adoptSelection(final Caret caret) {
		final int dot = caret.getDot(), mark = caret.getMark();
		visualAnchor = dot > mark ? mark : mark - 1;
		visualCursor = dot > mark ? dot - 1 : dot;
		updateVisual();
	}

	// -- Motions --

	private Motion motion(final Keys k, final int count, final char op) {
		return motion(k, count, op, area.getCaretPosition());
	}

	/**
	 * Parses and evaluates a motion from the given position.
	 *
	 * @param count The motion's count, or 0 if none was given.
	 * @param op The pending operator, or ' ' if none.
	 * @return The motion, or null if the keys are not a motion.
	 */
	private Motion motion(final Keys k, final int count, final char op,
		final int cur)
	{
		final int n = Math.max(1, count);
		final String t = text();
		final int len = t.length();
		final int line = lineOf(cur);
		final char c = k.next();
		switch (c) {
			case 'h':
			case '\b':
				return new Motion(Math.max(lineStart(cur), cur - n));
			case 'l':
			case ' ':
				return new Motion(Math.min(lineEnd(cur), cur + n));
			case 'j':
			case 'k':
			case '+':
			case '-':
			case '\n':
			case 14: // Ctrl-N
			case 16: { // Ctrl-P
				final boolean down = c == 'j' || c == '+' || c == '\n' || c == 14;
				final int target = down ? line + n : line - n;
				if (target < 0 || target >= lineCount()) throw INVALID;
				if (c == '+' || c == '-' || c == '\n') {
					return new Motion(firstNonBlank(target)).linewise();
				}
				return vertical(cur, target);
			}
			case 4: // Ctrl-D
			case 21: // Ctrl-U
			case 6: // Ctrl-F
			case 2: { // Ctrl-B
				final int page = pageLines();
				final int amount = c == 4 || c == 21 ? Math.max(1, page / 2) : Math
					.max(1, page - 2);
				final int dir = c == 4 || c == 6 ? 1 : -1;
				final int target = Math.max(0, Math.min(lineCount() - 1, line + dir *
					n * amount));
				return vertical(cur, target);
			}
			case '0':
				return new Motion(lineStart(cur));
			case '^':
				return new Motion(firstNonBlank(line));
			case '_':
				return new Motion(firstNonBlank(Math.min(line + n - 1, lineCount() -
					1))).linewise();
			case '$': {
				final Motion m = new Motion(endOfLine(Math.min(line + n - 1,
					lineCount() - 1)));
				desiredColumn = Integer.MAX_VALUE;
				m.vertical = true;
				return m;
			}
			case '|':
				return new Motion(Math.min(lineStart(cur) + n - 1, lineEnd(cur)));
			case 'w':
			case 'W': {
				final boolean big = c == 'W';
				if (op == 'c' && !Character.isWhitespace(charAt(t, cur))) {
					// Note: cw acts like ce, as in vim.
					int pos = cur;
					for (int i = 0; i < n; i++) {
						pos = i == 0 && pos + 1 < len && cls(t.charAt(pos), big) != cls(t
							.charAt(pos + 1), big) ? pos : wordEnd(t, pos, big);
					}
					return new Motion(pos).inclusive();
				}
				int pos = cur;
				for (int i = 0; i < n; i++) {
					final int next = wordForward(t, pos, big);
					// Note: an operator's last word does not reach into the next line.
					if (op != ' ' && i == n - 1 && lineOf(next) > lineOf(pos)) {
						pos = lineEnd(pos);
						break;
					}
					pos = next;
				}
				return new Motion(pos);
			}
			case 'b':
			case 'B': {
				int pos = cur;
				for (int i = 0; i < n; i++)
					pos = wordBackward(t, pos, c == 'B');
				return new Motion(pos);
			}
			case 'e':
			case 'E': {
				int pos = cur;
				for (int i = 0; i < n; i++)
					pos = wordEnd(t, pos, c == 'E');
				return new Motion(pos).inclusive();
			}
			case 'G':
				return new Motion(firstNonBlank(count > 0 ? Math.min(count, lineCount()) -
					1 : lineCount() - 1)).linewise();
			case 'g': {
				final char g = k.next();
				if (g == 'g') {
					return new Motion(firstNonBlank(count > 0 ? Math.min(count,
						lineCount()) - 1 : 0)).linewise();
				}
				if (g == 'e' || g == 'E') {
					int pos = cur;
					for (int i = 0; i < n; i++)
						pos = wordEndBackward(t, pos, g == 'E');
					return new Motion(pos).inclusive();
				}
				if (g == '_') {
					final int l = Math.min(line + n - 1, lineCount() - 1);
					int pos = endOfLine(l);
					while (pos > startOfLine(l) && Character.isWhitespace(t.charAt(pos -
						1))) pos--;
					return new Motion(Math.max(startOfLine(l), pos - 1)).inclusive();
				}
				return null;
			}
			case 'f':
			case 'F':
			case 't':
			case 'T': {
				final char target = k.next();
				if (target == ESC) throw INVALID;
				lastFindType = c;
				lastFindChar = target;
				return find(c, target, n, cur, false);
			}
			case ';':
			case ',': {
				if (lastFindType == 0) throw INVALID;
				char type = lastFindType;
				if (c == ',') type = Character.isUpperCase(type) ? Character
					.toLowerCase(type) : Character.toUpperCase(type);
				return find(type, lastFindChar, n, cur, true);
			}
			case '%': {
				final int pos = matchBracket(t, cur);
				if (pos < 0) throw INVALID;
				return new Motion(pos).inclusive();
			}
			case '}':
			case '{': {
				final boolean down = c == '}';
				int l = line;
				for (int i = 0; i < n; i++) {
					while (l >= 0 && l < lineCount() && isBlankLine(l))
						l += down ? 1 : -1;
					while (l >= 0 && l < lineCount() && !isBlankLine(l))
						l += down ? 1 : -1;
				}
				if (l < 0) return new Motion(0);
				if (l >= lineCount()) return new Motion(len);
				return new Motion(startOfLine(l));
			}
			case 'H':
			case 'M':
			case 'L': {
				final int[] visible = visibleLines();
				final int target = c == 'H' ? Math.min(visible[0] + n - 1,
					visible[1]) : c == 'L' ? Math.max(visible[1] - n + 1, visible[0])
						: (visible[0] + visible[1]) / 2;
				return new Motion(firstNonBlank(target)).linewise();
			}
			case 'n':
			case 'N':
			case '*':
			case '#': {
				if (c == '*' || c == '#') {
					final int[] word = wordAt(t, cur);
					if (word == null) throw INVALID;
					lastSearch = "\\<" + Pattern.quote(t.substring(word[0], word[1])) +
						"\\>";
					lastSearchForward = c == '*';
				}
				final boolean forward = (c == 'N') != lastSearchForward;
				int pos = cur;
				for (int i = 0; i < n; i++) {
					pos = search(pos, forward);
					if (pos < 0) throw INVALID;
				}
				return new Motion(pos);
			}
			case '`':
			case '\'': {
				final Position p = marks.get(k.next());
				if (p == null) {
					message = "E20: Mark not set";
					throw INVALID;
				}
				final int pos = Math.min(p.getOffset(), len);
				return c == '`' ? new Motion(pos) : new Motion(firstNonBlank(lineOf(
					pos))).linewise();
			}
			default:
				return null;
		}
	}

	private Motion vertical(final int cur, final int targetLine) {
		// Note: the caret may have been moved by other means, e.g. the mouse.
		if (desiredColumn < 0 || cur != lastCursor) {
			desiredColumn = cur - lineStart(cur);
		}
		final int start = startOfLine(targetLine);
		final int max = lineEnd(start) - start;
		final Motion m = new Motion(start + Math.min(desiredColumn, max))
			.linewise();
		m.vertical = true;
		return m;
	}

	private Motion find(final char type, final char target, final int n,
		final int cur, final boolean repeat)
	{
		final String t = text();
		final int start = lineStart(cur), end = lineEnd(cur);
		final boolean forward = Character.isLowerCase(type);
		final boolean till = type == 't' || type == 'T';
		int pos = cur;
		// Note: repeating a 't' must not get stuck just before its target.
		if (till && repeat) pos += forward ? 1 : -1;
		for (int i = 0; i < n; i++) {
			do {
				pos += forward ? 1 : -1;
				if (pos < start || pos >= end) throw INVALID;
			}
			while (t.charAt(pos) != target);
		}
		if (till) pos += forward ? -1 : 1;
		final Motion m = new Motion(pos);
		if (forward) m.inclusive();
		return m;
	}

	private int search(final int from, final boolean forward) {
		if (lastSearch == null) {
			message = "E35: No previous regular expression";
			return -1;
		}
		final Pattern p = compile(lastSearch, false);
		if (p == null) return -1;
		final String t = text();
		final Matcher m = p.matcher(t);
		int found = -1;
		if (forward) {
			if (from + 1 <= t.length() && m.find(from + 1)) found = m.start();
			else if (m.find(0)) found = m.start();
		}
		else {
			int last = -1;
			while (m.find()) {
				if (m.start() < from) found = m.start();
				last = m.start();
			}
			if (found < 0) found = last;
		}
		if (found < 0) message = "E486: Pattern not found: " + lastSearch;
		return found;
	}

	private Pattern compile(final String pattern, final boolean ignoreCase) {
		final String regex = pattern.replace("\\<", "\\b").replace("\\>", "\\b");
		final int flags = ignoreCase ? Pattern.CASE_INSENSITIVE : 0;
		try {
			return Pattern.compile(regex, flags);
		}
		catch (final PatternSyntaxException exc) {
			message = "E486: Invalid pattern: " + pattern;
			return null;
		}
	}

	/**
	 * Finds a text object around the given range.
	 *
	 * @return The object's offsets [start, end).
	 */
	private int[] textObject(final boolean around, final char type,
		final int lo, final int hi)
	{
		final String t = text();
		switch (type) {
			case 'w':
			case 'W': {
				final boolean big = type == 'W';
				final int pos = hi > lo ? hi + 1 : lo;
				if (pos >= t.length() || t.charAt(pos) == '\n') {
					if (hi > lo) throw INVALID;
				}
				int s = hi > lo ? lo : pos, e = pos;
				final int c = cls(charAt(t, pos), big);
				final int ls = lineStart(pos), le = lineEnd(pos);
				if (hi == lo) while (s > ls && cls(t.charAt(s - 1), big) == c) s--;
				while (e < le && cls(t.charAt(e), big) == c) e++;
				if (around) {
					if (c != 0) {
						final int e0 = e;
						while (e < le && cls(t.charAt(e), big) == 0) e++;
						if (e == e0 && hi == lo) {
							while (s > ls && cls(t.charAt(s - 1), big) == 0) s--;
						}
					}
					else if (e < le) {
						final int c2 = cls(t.charAt(e), big);
						while (e < le && cls(t.charAt(e), big) == c2) e++;
					}
				}
				return new int[] { s, e };
			}
			case '"':
			case '\'':
			case '`': {
				final int ls = lineStart(lo), le = lineEnd(lo);
				final List<Integer> quotes = new ArrayList<>();
				for (int i = ls; i < le; i++) {
					if (t.charAt(i) == type && (i == ls || t.charAt(i - 1) != '\\')) {
						quotes.add(i);
					}
				}
				for (int i = 0; i + 1 < quotes.size(); i += 2) {
					final int open = quotes.get(i), close = quotes.get(i + 1);
					if (close < lo) continue;
					if (!around) return new int[] { open + 1, close };
					int e = close + 1;
					while (e < le && (t.charAt(e) == ' ' || t.charAt(e) == '\t'))
						e++;
					return new int[] { open, e };
				}
				throw INVALID;
			}
			default: {
				final String pairs = "()[]{}<>";
				final char openChar;
				switch (type) {
					case 'b':
					case '(':
					case ')':
						openChar = '(';
						break;
					case 'B':
					case '{':
					case '}':
						openChar = '{';
						break;
					case '[':
					case ']':
						openChar = '[';
						break;
					case '<':
					case '>':
						openChar = '<';
						break;
					default:
						throw INVALID;
				}
				final char closeChar = pairs.charAt(pairs.indexOf(openChar) + 1);
				int open = -1;
				int depth = 0;
				for (int i = Math.min(lo, t.length() - 1); i >= 0; i--) {
					final char ch = t.charAt(i);
					if (ch == closeChar && i != lo) depth++;
					else if (ch == openChar) {
						if (depth == 0) {
							open = i;
							break;
						}
						depth--;
					}
				}
				if (open < 0) throw INVALID;
				final int close = matchBracket(t, open);
				if (close < 0 || close < hi) throw INVALID;
				return around ? new int[] { open, close + 1 } : new int[] { open + 1,
					close };
			}
		}
	}

	// -- Editing operations --

	private void put(final char reg, final int n, final boolean after) {
		final Register r = getRegister(reg);
		if (r == null) throw INVALID;
		put(r, n, after);
	}

	private void put(final Register r, final int n, final boolean after) {
		final String text = repeat(r.text, n);
		final int cur = area.getCaretPosition();
		if (r.linewise) {
			final String t = text();
			final String lines = text.endsWith("\n") ? text : text + "\n";
			if (after) {
				final int end = lineEnd(cur);
				if (end >= t.length()) {
					// Note: the last line has no newline to insert after.
					replace(end, end, "\n" + lines.substring(0, lines.length() - 1));
					moveTo(firstNonBlank(lineOf(end + 1)));
				}
				else {
					replace(end + 1, end + 1, lines);
					moveTo(firstNonBlank(lineOf(end + 1)));
				}
			}
			else {
				final int start = lineStart(cur);
				replace(start, start, lines);
				moveTo(firstNonBlank(lineOf(start)));
			}
		}
		else {
			final int pos = after && cur < lineEnd(cur) ? cur + 1 : cur;
			replace(pos, pos, text);
			moveTo(Math.max(pos, pos + text.length() - 1));
		}
	}

	private void deleteLines(final int first, final int last, final char reg) {
		setRegister(reg, linesText(first, last), true, false);
		int start = startOfLine(first);
		int end = endOfLine(last);
		if (end < length()) end++;
		else if (start > 0) start--;
		remove(start, end);
		moveTo(firstNonBlank(Math.min(first, lineCount() - 1)));
	}

	private void join(final int line, final int count, final boolean spaces) {
		final int last = Math.min(line + count - 1, lineCount() - 1);
		if (last == line) throw INVALID;
		int pos = -1;
		for (int i = line; i < last; i++) {
			final String t = text();
			final int end = endOfLine(line);
			int next = end + 1;
			while (spaces && next < t.length() && (t.charAt(next) == ' ' || t
				.charAt(next) == '\t')) next++;
			String sep = "";
			if (spaces && next < t.length() && t.charAt(next) != '\n' && t.charAt(
				next) != ')' && end > lineStart(end) && !Character.isWhitespace(t
					.charAt(end - 1))) sep = " ";
			replace(end, next, sep);
			pos = end;
		}
		moveTo(pos);
	}

	private void shift(final int first, final int last, final int n) {
		final int tabSize = Math.max(1, area.getTabSize());
		final String unit = area.getTabsEmulated() ? repeat(" ", tabSize) : "\t";
		for (int l = first; l <= last; l++) {
			final int start = startOfLine(l);
			if (lineEnd(start) == start) continue;
			if (n > 0) replace(start, start, repeat(unit, n));
			else {
				// Remove up to n levels of indentation.
				final String t = text();
				int width = 0, pos = start;
				while (pos < t.length() && width < -n * tabSize) {
					final char c = t.charAt(pos);
					if (c == ' ') width++;
					else if (c == '\t') width += tabSize - width % tabSize;
					else break;
					pos++;
				}
				remove(start, pos);
			}
		}
	}

	private void replace(final int start, final int end, final String s) {
		if (!area.isEditable()) {
			beep();
			throw INVALID;
		}
		if (!editing) {
			area.beginAtomicEdit();
			editing = true;
		}
		area.replaceRange(s, start, end);
	}

	private void remove(final int start, final int end) {
		if (start < end) replace(start, end, "");
	}

	private void endEdit() {
		if (!editing) return;
		editing = false;
		area.endAtomicEdit();
	}

	// -- Registers --

	private Register getRegister(final char name) {
		if (name == '+' || name == '*') {
			try {
				final Object data = Toolkit.getDefaultToolkit().getSystemClipboard()
					.getData(DataFlavor.stringFlavor);
				return data == null ? null : new Register(data.toString(), false);
			}
			catch (final Exception exc) {
				return null;
			}
		}
		return registers.get(Character.toLowerCase(name));
	}

	private void setRegister(final char name, final String text,
		final boolean linewise, final boolean yank)
	{
		Register r = new Register(text, linewise);
		final char lower = Character.toLowerCase(name);
		if (name == '+' || name == '*') {
			try {
				Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
					new StringSelection(text), null);
			}
			catch (final HeadlessException | IllegalStateException exc) {
				message = "Clipboard unavailable";
			}
		}
		else if (Character.isUpperCase(name)) {
			// Note: an uppercase register name appends to the register.
			final Register old = registers.get(lower);
			if (old != null) r = new Register(old.text + text, old.linewise ||
				linewise);
			registers.put(lower, r);
		}
		else if (name != '"') registers.put(name, r);
		else if (yank) registers.put('0', r);
		registers.put('"', r);
	}

	// -- Text helpers --

	private String text() {
		return area.getText();
	}

	private int length() {
		return area.getDocument().getLength();
	}

	private Element root() {
		return area.getDocument().getDefaultRootElement();
	}

	private int lineCount() {
		return root().getElementCount();
	}

	private int lineOf(final int pos) {
		return root().getElementIndex(pos);
	}

	private int lineStart(final int pos) {
		return root().getElement(lineOf(pos)).getStartOffset();
	}

	/** Gets the offset of the newline ending the given position's line. */
	private int lineEnd(final int pos) {
		return Math.min(root().getElement(lineOf(pos)).getEndOffset() - 1,
			length());
	}

	private String linesText(final int first, final int last) {
		return text().substring(startOfLine(first), endOfLine(last)) + "\n";
	}

	private int startOfLine(final int line) {
		return root().getElement(line).getStartOffset();
	}

	private int endOfLine(final int line) {
		return lineEnd(startOfLine(line));
	}

	private boolean isBlankLine(final int line) {
		return startOfLine(line) == endOfLine(line);
	}

	private int firstNonBlank(final int line) {
		final String t = text();
		final int start = root().getElement(line).getStartOffset();
		final int end = lineEnd(start);
		int pos = start;
		while (pos < end && (t.charAt(pos) == ' ' || t.charAt(pos) == '\t'))
			pos++;
		return pos;
	}

	private String indentOf(final int line) {
		final int start = root().getElement(line).getStartOffset();
		return text().substring(start, firstNonBlank(line));
	}

	private void moveTo(final int pos) {
		area.setCaretPosition(Math.max(0, Math.min(pos, length())));
		clamp();
	}

	/** Keeps the caret off the end of a non-empty line, as in normal mode. */
	private void clamp() {
		if (mode != Mode.NORMAL) return;
		final int pos = area.getCaretPosition();
		if (pos > lineStart(pos) && pos == lineEnd(pos)) {
			area.setCaretPosition(pos - 1);
		}
	}

	private void clampIfIdle() {
		final Caret caret = area.getCaret();
		if (enabled && mode == Mode.NORMAL && caret.getDot() == caret.getMark()) {
			clamp();
		}
	}

	private int pageLines() {
		final int h = area.getVisibleRect().height;
		final int lineHeight = area.getLineHeight();
		return h > 0 && lineHeight > 0 ? Math.max(1, h / lineHeight) : 20;
	}

	private int[] visibleLines() {
		final Rectangle r = area.getVisibleRect();
		if (r.height <= 0) return new int[] { 0, lineCount() - 1 };
		final int top = area.viewToModel2D(r.getLocation());
		final int bottom = area.viewToModel2D(new java.awt.Point(r.x, r.y +
			r.height - 1));
		return new int[] { lineOf(top), lineOf(bottom) };
	}

	private void scrollTo(final int pos, final char where)
		throws BadLocationException
	{
		final Rectangle2D caret = area.modelToView2D(pos);
		final Rectangle visible = area.getVisibleRect();
		if (caret == null || visible.height <= 0) return;
		final int y = (int) caret.getY();
		final int h = (int) caret.getHeight();
		final int top = where == 't' ? y : where == 'b' ? y + h - visible.height
			: y + h / 2 - visible.height / 2;
		area.scrollRectToVisible(new Rectangle(visible.x, Math.max(0, top),
			visible.width, visible.height));
	}

	private void updateCaretStyle() {
		final boolean block = enabled && mode != Mode.INSERT;
		final CaretStyle style = block ? CaretStyle.BLOCK_STYLE
			: originalCaretStyle;
		if (style != null) area.setCaretStyle(RTextArea.INSERT_MODE, style);
	}

	private boolean areCtrlKeysFree() {
		if (ctrlKeysFree == null) {
			try {
				// Note: where Ctrl is the menu shortcut key, Ctrl+key is left to menus.
				final int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
				ctrlKeysFree = (mask & InputEvent.CTRL_DOWN_MASK) == 0;
			}
			catch (final HeadlessException exc) {
				ctrlKeysFree = true;
			}
		}
		return ctrlKeysFree;
	}

	private void fireStatus() {
		if (statusListener != null) statusListener.accept(getStatus());
	}

	private void beep() {
		UIManager.getLookAndFeel().provideErrorFeedback(area);
	}

	private static int cls(final char c, final boolean big) {
		if (Character.isWhitespace(c)) return 0;
		if (big) return 1;
		return Character.isLetterOrDigit(c) || c == '_' ? 2 : 1;
	}

	private static char charAt(final String t, final int pos) {
		return pos < t.length() ? t.charAt(pos) : '\n';
	}

	private static int wordForward(final String t, int pos, final boolean big) {
		final int len = t.length();
		if (pos >= len) return len;
		final int c = cls(t.charAt(pos), big);
		if (c != 0) while (pos < len && cls(t.charAt(pos), big) == c)
			pos++;
		while (pos < len && Character.isWhitespace(t.charAt(pos))) {
			// Note: an empty line counts as a word.
			if (t.charAt(pos) == '\n' && pos + 1 < len && t.charAt(pos + 1) == '\n') {
				return pos + 1;
			}
			pos++;
		}
		return pos;
	}

	private static int wordEnd(final String t, int pos, final boolean big) {
		final int len = t.length();
		pos++;
		while (pos < len && Character.isWhitespace(t.charAt(pos)))
			pos++;
		if (pos >= len) return Math.max(0, len - 1);
		final int c = cls(t.charAt(pos), big);
		while (pos + 1 < len && cls(t.charAt(pos + 1), big) == c)
			pos++;
		return pos;
	}

	private static int wordBackward(final String t, int pos, final boolean big) {
		pos--;
		while (pos > 0 && Character.isWhitespace(t.charAt(pos))) {
			if (t.charAt(pos) == '\n' && t.charAt(pos - 1) == '\n') return pos;
			pos--;
		}
		if (pos <= 0) return 0;
		final int c = cls(t.charAt(pos), big);
		while (pos > 0 && cls(t.charAt(pos - 1), big) == c)
			pos--;
		return pos;
	}

	private static int wordEndBackward(final String t, int pos,
		final boolean big)
	{
		if (pos >= t.length()) pos = t.length() - 1;
		if (pos <= 0) return 0;
		final int c = cls(t.charAt(pos), big);
		if (c != 0) while (pos > 0 && cls(t.charAt(pos), big) == c)
			pos--;
		while (pos > 0 && Character.isWhitespace(t.charAt(pos)))
			pos--;
		return pos;
	}

	/** Finds the keyword under or after the given position: [start, end). */
	private static int[] wordAt(final String t, final int pos) {
		int s = pos;
		while (s < t.length() && t.charAt(s) != '\n' && cls(t.charAt(s),
			false) != 2) s++;
		if (s >= t.length() || cls(t.charAt(s), false) != 2) return null;
		int e = s;
		while (s > 0 && cls(t.charAt(s - 1), false) == 2)
			s--;
		while (e < t.length() && cls(t.charAt(e), false) == 2)
			e++;
		return new int[] { s, e };
	}

	/**
	 * Finds the bracket matching the first bracket at or after the given
	 * position on its line.
	 *
	 * @return The matching bracket's offset, or -1 if none.
	 */
	private static int matchBracket(final String t, int pos) {
		final String brackets = "()[]{}";
		while (pos < t.length() && t.charAt(pos) != '\n' && brackets.indexOf(t
			.charAt(pos)) < 0) pos++;
		if (pos >= t.length() || brackets.indexOf(t.charAt(pos)) < 0) return -1;
		final char c = t.charAt(pos);
		final int index = brackets.indexOf(c);
		final boolean forward = index % 2 == 0;
		final char other = brackets.charAt(forward ? index + 1 : index - 1);
		int depth = 0;
		for (int i = pos; i >= 0 && i < t.length(); i += forward ? 1 : -1) {
			final char ch = t.charAt(i);
			if (ch == c) depth++;
			else if (ch == other && --depth == 0) return i;
		}
		return -1;
	}

	private static String toggleCase(final String s) {
		final StringBuilder sb = new StringBuilder(s.length());
		for (final char c : s.toCharArray()) {
			sb.append(Character.isUpperCase(c) ? Character.toLowerCase(c) : Character
				.toUpperCase(c));
		}
		return sb.toString();
	}

	private static String repeat(final String s, final int n) {
		final StringBuilder sb = new StringBuilder(s.length() * n);
		for (int i = 0; i < n; i++)
			sb.append(s);
		return sb.toString();
	}

	/** Replaces the count of a recorded command. */
	private static String withCount(final String keys, final int count) {
		final Matcher m = Pattern.compile("^(\".)?[1-9]?[0-9]*").matcher(keys);
		m.find();
		final String reg = m.group(1) == null ? "" : m.group(1);
		return reg + count + keys.substring(m.end());
	}

	private static String printable(final CharSequence keys) {
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < keys.length(); i++) {
			final char c = keys.charAt(i);
			if (c < ' ') sb.append('^').append((char) (c + '@'));
			else sb.append(c);
		}
		return sb.toString();
	}
}
