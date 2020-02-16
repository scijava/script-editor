/*
 * #%L
 * Script Editor and Interpreter for SciJava script languages.
 * %%
 * Copyright (C) 2009 - 2020 SciJava developers.
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

package org.scijava.ui.swing.script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.swing.text.BadLocationException;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenTypes;

/**
 * TODO
 *
 * @author Johannes Schindelin
 */
public class TokenFunctions implements Iterable<Token> {

	RSyntaxTextArea textArea;

	public TokenFunctions(final RSyntaxTextArea textArea) {
		this.textArea = textArea;
	}

	public static boolean tokenEquals(final Token token, final char[] text) {
		return token.is(TokenTypes.RESERVED_WORD, text);
	}

	public static boolean isIdentifier(final Token token) {
		if (token.getType() != TokenTypes.IDENTIFIER) return false;
		final char[] tokenText = token.getLexeme().toCharArray();
		if (tokenText == null || !Character.isJavaIdentifierStart(tokenText[0])) return false;
		for (int i = 1; i < tokenText.length; i++)
			if (!Character.isJavaIdentifierPart(tokenText[i])) return false;
		return true;
	}

	public static String getText(final Token token) {
		return token.getLexeme();
	}

	public void replaceToken(final Token token, final String text) {
		textArea.replaceRange(text, token.getOffset(), token.getEndOffset());
	}

	class TokenIterator implements Iterator<Token> {

		int line = -1;
		Token current, next;

		@Override
		public boolean hasNext() {
			if (next == null) getNextToken();
			return next != null;
		}

		@Override
		public Token next() {
			current = next;
			next = null;
			return current;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}

		void getNextToken() {
			if (current != null) {
				next = current.getNextToken();
				if (next != null) return;
			}

			while (next == null) {
				if (++line >= textArea.getLineCount()) return;

				next = textArea.getTokenListForLine(line);
			}
		}
	}

	@Override
	public Iterator<Token> iterator() {
		return new TokenIterator();
	}

	public static boolean isDot(final Token token) {
		return token.is(TokenTypes.IDENTIFIER, ".");
	}

	/* The following methods are Java-specific */

	public static boolean isClass(final Token token) {
		return token.is(TokenTypes.RESERVED_WORD, "class");
	}

	public String getClassName() {
		boolean classSeen = false;
		for (final Token token : this)
			if (isClass(token)) classSeen = true;
			else if (classSeen && isIdentifier(token)) return getText(token);
		return null;
	}

	public void setClassName(final String className) {
		boolean classSeen = false;
		for (final Token token : this)
			if (isClass(token)) classSeen = true;
			else if (classSeen && isIdentifier(token)) {
				replaceToken(token, className);
				return;
			}
	}

	class Import implements Comparable<Import> {

		int startOffset, endOffset;
		String classOrPackage;

		Import(final int start, final int end, final String text) {
			startOffset = start;
			endOffset = end;
			classOrPackage = text;
		}

		String getPackage() {
			final int dot = classOrPackage.lastIndexOf('.');
			return dot < 0 ? "" : classOrPackage.substring(0, dot);
		}

		public boolean equals(final Import imp) {
			return classOrPackage.equals(imp.classOrPackage);
		}

		@Override
		public int compareTo(final Import imp) {
			return classOrPackage.compareTo(imp.classOrPackage);
		}

		@Override
		public String toString() {
			return "Import(" + classOrPackage + "," + startOffset + "-" + endOffset +
				")";
		}
	}

	Token skipNonCode(final TokenIterator iter, Token current) {
		for (;;) {
			switch (current.getType()) {
				case TokenTypes.COMMENT_DOCUMENTATION:
				case TokenTypes.COMMENT_EOL:
				case TokenTypes.COMMENT_MULTILINE:
				case TokenTypes.WHITESPACE:
					break;
				default:
					return current;
			}
			if (!iter.hasNext()) return null;
			current = iter.next();
		}
	}

	int skipToEOL(final TokenIterator iter, Token current) {
		int end = textArea.getDocument().getLength();
		for (;;) {
			if (current.getType() == TokenTypes.NULL || !iter.hasNext()) return end;
			end = current.getEndOffset();
			current = iter.next();
		}
	}

	public final char[] importChars = { 'i', 'm', 'p', 'o', 'r', 't' };

	public List<Import> getImports() {
		final List<Import> result = new ArrayList<>();

		final TokenIterator iter = new TokenIterator();
		while (iter.hasNext()) {
			Token token = iter.next();
			final int offset = token.getOffset();
			token = skipNonCode(iter, token);
			if (tokenEquals(token, importChars)) {
				do {
					if (!iter.hasNext()) return result;
					token = iter.next();
				}
				while (!isIdentifier(token));
				final int start = token.getOffset();
				int end = start;
				do {
					if (!iter.hasNext()) return result;
					token = iter.next();
					if (isDot(token) && iter.hasNext()) token = iter.next();
					end = token.getEndOffset();
				}
				while (isIdentifier(token));
				String identifier = getText(start, end);
				if (identifier.endsWith(";")) identifier =
					identifier.substring(0, identifier.length() - 1);
				end = skipToEOL(iter, token);
				result.add(new Import(offset, end, identifier));
			}
		}

		return result;
	}

	public String getText(final int start, final int end) {
		try {
			return textArea.getText(start, end - start);
		}
		catch (final BadLocationException e) { /* ignore */}
		return "";
	}

	public boolean emptyLineAt(final int offset) {
		return getText(offset, offset + 2).equals("\n\n");
	}

	public boolean eolAt(final int offset) {
		return getText(offset, offset + 1).equals("\n");
	}

	void removeImport(final Import imp) {
		final int start = imp.startOffset;
		int end = imp.endOffset;
		if (emptyLineAt(start - 2) && emptyLineAt(end)) end += 2;
		else if (eolAt(end)) end++;
		textArea.replaceRange("", start, end);
	}

	public void removeUnusedImports() {
		final Set<String> identifiers = getAllUsedIdentifiers();
		final List<Import> imports = getImports();
		for (int i = imports.size() - 1; i >= 0; i--) {
			final Import imp = imports.get(i);
			String clazz = imp.classOrPackage;
			if (clazz.endsWith(".*")) continue;
			final int dot = clazz.lastIndexOf('.');
			if (dot >= 0) clazz = clazz.substring(dot + 1);
			if (!identifiers.contains(clazz)) removeImport(imp);
		}
	}

	public void addImport(final String className) {
		final List<Import> imports = getImports();

		if (imports.size() == 0) {
			final TokenIterator iter = new TokenIterator();
			int offset = 0;
			boolean insertLF = false;
			while (iter.hasNext()) {
				final Token token = iter.next();
				if (token.getType() != TokenTypes.RESERVED_WORD) {
					insertLF = false;
					continue;
				}
				if (getText(token).equals("package")) {
					skipToEOL(iter, token);
					insertLF = true;
				}
				else {
					offset = token.getOffset();
					break;
				}
			}
			textArea.insert((insertLF ? "\n" : "") + "import " + className + ";\n\n",
				offset);
			return;
		}

		String string = "import " + className + ";\n";
		final Import imp = new Import(0, 0, className);
		int after = -1, startOffset, endOffset;

		for (int i = 0; i < imports.size(); i++) {
			final int cmp = imports.get(i).compareTo(imp);
			if (cmp == 0) return;
			if (cmp < 0) after = i;
		}

		// 'after' is the index of the import after which we
		// want to insert the current import.
		if (after < 0) {
			startOffset = imports.get(0).startOffset;
			if (startOffset > 1 &&
				!getText(startOffset - 2, startOffset).equals("\n\n")) string =
				"\n" + string;
		}
		else {
			startOffset = imports.get(after).endOffset;
			string = "\n" + string;
			if (!imp.getPackage().equals(imports.get(after).getPackage())) string =
				"\n" + string;
		}
		if (after + 1 < imports.size()) {
			endOffset = imports.get(after + 1).startOffset;
			if (!imp.getPackage().equals(imports.get(after + 1).getPackage())) string +=
				"\n";
		}
		else {
			if (after < 0) endOffset = startOffset;
			else endOffset = imports.get(after).endOffset;
			if (endOffset + 1 < textArea.getDocument().getLength() &&
				!getText(endOffset, endOffset + 2).equals("\n\n")) string += "\n";
		}
		textArea.replaceRange(string, startOffset, endOffset);
	}

	public Set<String> getAllUsedIdentifiers() {
		final Set<String> result = new HashSet<>();
		boolean classSeen = false;
		String className = null;
		for (final Token token : this)
			if (isClass(token)) classSeen = true;
			else if (classSeen && className == null && isIdentifier(token)) className =
				getText(token);
			else if (classSeen && isIdentifier(token)) result.add(getText(token));
		return result;
	}

	public void sortImports() {
		final List<Import> imports = getImports();
		if (imports.size() == 0) return;
		int start = imports.get(0).startOffset;
		while (emptyLineAt(start - 2))
			start--;
		int end = imports.get(imports.size() - 1).endOffset;
		while (eolAt(end))
			end++;

		Collections.sort(imports, new Comparator<Import>() {

			@Override
			public int compare(final Import i1, final Import i2) {
				return i1.classOrPackage.compareTo(i2.classOrPackage);
			}

			@Override
			public boolean equals(final Object o) {
				return false;
			}
		});

		final StringBuffer buffer = new StringBuffer();
		String lastPrefix = null;
		String lastImport = null;
		for (final Import imp : imports) {
			if (imp.classOrPackage.equals(lastImport)) continue;
			lastImport = imp.classOrPackage;

			final String prefix = imp.getPackage();
			if (!prefix.equals(lastPrefix)) {
				buffer.append("\n");
				lastPrefix = prefix;
			}
			// TODO: honor comments
			buffer.append(getText(imp.startOffset, imp.endOffset));
			buffer.append("\n");
		}
		buffer.append("\n");

		textArea.replaceRange(buffer.toString(), start, end);
	}

	public void removeTrailingWhitespace() {
		int end = textArea.getDocument().getLength();

		// Turn CR and CRLF into LF
		for (int i = end - 1; i >= 0; i--)
			if (getText(i, i + 1).equals("\r")) {
				final boolean isCRLF =
					i < end - 1 && getText(i + 1, i + 2).equals("\n");
				textArea.replaceRange("\n", i, i + 1 + (isCRLF ? 1 : 0));
				if (isCRLF) end--;
			}

		// remove trailing empty lines
		int realEnd = end;
		if (eolAt(end - 1)) {
			while (eolAt(end - 2) || getText(end - 2, end - 1).equals("\r"))
				end--;
			if (end < realEnd) textArea.replaceRange("", end - 1, realEnd - 1);
		}

		// remove trailing white space from each line
		for (int i = textArea.getLineCount() - 1; i >= 0; i--)
			try {
				final int start = textArea.getLineStartOffset(i);
				if (eolAt(end - 1)) end--;
				if (start == end) continue;
				final String line = getText(start, end);
				realEnd = end;
				while (end - start - 1 >= 0 &&
					Character.isWhitespace(line.charAt(end - start - 1)))
					end--;
				if (end < realEnd) textArea.replaceRange("", end, realEnd);
				end = start;
			}
			catch (final BadLocationException e) { /* cannot happen */}
	}
}
