/*
 * #%L
 * Script Editor and Interpreter for SciJava script languages.
 * %%
 * Copyright (C) 2009 - 2025 SciJava developers.
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

package org.scijava.ui.swing.script.highliters;

import java.io.IOException;

import javax.swing.text.Segment;

import org.fife.ui.rsyntaxtextarea.AbstractJFlexTokenMaker;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenImpl;
import org.fife.ui.rsyntaxtextarea.TokenTypes;

/**
 * Scanner for the Matlab programming language.
 * <p>
 * Copyright (C) 2009 Sumit Dubey Licensed as per LGPLv3, or any other license
 * at the discretion of Robert Futrell. This implementation was created using <a
 * href="http://www.jflex.de/">JFlex</a> 1.4.1; however, the generated file was
 * modified for performance. Memory allocation needs to be almost completely
 * removed to be competitive with the handwritten lexers (subclasses of
 * {@code AbstractTokenMaker}, so this class has been modified so that
 * Strings are never allocated (via yytext()), and the scanner never has to
 * worry about refilling its buffer (needlessly copying chars around). We can
 * achieve this because RText always scans exactly 1 line of tokens at a time,
 * and hands the scanner this line as an array of characters (a Segment really).
 * Since tokens contain pointers to char arrays instead of Strings holding their
 * contents, there is no need for allocating new memory for Strings.
 * <p>
 * The actual algorithm generated for scanning has, of course, not been
 * modified.
 * <p>
 * If you wish to regenerate this file yourself, keep in mind the following:
 * </p>
 * <ul>
 * <li>The generated {@code JavaTokenMaker.java} file will contain two
 * definitions of both {@code zzRefill} and {@code yyreset}. You
 * should hand-delete the second of each definition (the ones generated by the
 * lexer), as these generated methods modify the input buffer, which we'll never
 * have to do.</li>
 * <li>You should also change the declaration/definition of zzBuffer to NOT be
 * initialized. This is a needless memory allocation for us since we will be
 * pointing the array somewhere else anyway.</li>
 * <li>You should NOT call {@code yylex()} on the generated scanner
 * directly; rather, you should use {@code getTokenList} as you would with
 * any other {@code TokenMaker} instance.</li>
 * </ul>
 *
 * @author Sumit Dubey
 */
public class MatlabTokenMaker extends AbstractJFlexTokenMaker {

	/** This character denotes the end of file */
	public static final int YYEOF = -1;

	/** initial size of the lookahead buffer */
	private static final int ZZ_BUFFERSIZE = 16384;

	/** lexical states */
	public static final int EOL_COMMENT = 6;
	public static final int STRING = 2;
	public static final int YYINITIAL = 0;
	public static final int MLC = 4;

	/**
	 * ZZ_LEXSTATE[l] is the state in the DFA for the lexical state l
	 * ZZ_LEXSTATE[l+1] is the state in the DFA for the lexical state l at the
	 * beginning of a line l is of the form l = 2*k, k a non negative integer
	 */
	private static final int ZZ_LEXSTATE[] = { 0, 0, 1, 1, 2, 2, 3, 3 };

	/**
	 * Translates characters to character classes
	 */
	private static final String ZZ_CMAP_PACKED =
		"\11\0\1\32\1\15\1\0\1\32\1\16\22\0\1\32\1\40\1\16"
			+ "\2\0\1\1\1\42\1\12\2\20\1\42\1\37\1\16\1\37\1\17"
			+ "\1\42\1\5\3\10\4\10\2\4\1\42\1\16\1\40\1\41\1\43"
			+ "\1\42\1\0\3\7\1\35\1\36\1\35\5\6\1\34\13\6\1\33"
			+ "\2\6\1\21\1\6\1\21\1\22\1\6\1\6\1\27\1\14\1\45"
			+ "\1\51\1\25\1\26\1\52\1\46\1\50\1\6\1\44\1\30\1\6"
			+ "\1\13\1\47\1\54\1\6\1\24\1\31\1\23\1\11\1\6\1\53"
			+ "\1\33\1\55\1\6\1\2\1\16\1\3\1\42\uff81\0";

	/**
	 * Translates characters to character classes
	 */
	private static final char[] ZZ_CMAP = zzUnpackCMap(ZZ_CMAP_PACKED);

	/**
	 * Translates DFA states to action switch labels.
	 */
	private static final int[] ZZ_ACTION = zzUnpackAction();

	private static final String ZZ_ACTION_PACKED_0 =
		"\4\0\1\1\1\2\1\3\2\4\1\5\1\6\1\5" + "\1\7\1\1\1\3\1\10\5\5\1\11\3\10\6\5"
			+ "\1\12\1\13\1\14\2\12\1\15\2\12\1\16\1\17"
			+ "\1\20\1\21\1\20\1\21\1\4\1\20\1\22\1\20"
			+ "\11\5\1\10\3\5\1\23\3\5\1\24\1\21\1\0"
			+ "\2\22\17\5\1\25\1\5\1\23\24\5";

	private static int[] zzUnpackAction() {
		final int[] result = new int[109];
		int offset = 0;
		offset = zzUnpackAction(ZZ_ACTION_PACKED_0, offset, result);
		return result;
	}

	private static int zzUnpackAction(final String packed, final int offset,
		final int[] result)
	{
		int i = 0; /* index in packed string  */
		int j = offset; /* index in unpacked array */
		final int l = packed.length();
		while (i < l) {
			int count = packed.charAt(i++);
			final int value = packed.charAt(i++);
			do
				result[j++] = value;
			while (--count > 0);
		}
		return j;
	}

	/**
	 * Translates a state to a row index in the transition table
	 */
	private static final int[] ZZ_ROWMAP = zzUnpackRowMap();

	private static final String ZZ_ROWMAP_PACKED_0 =
		"\0\0\0\56\0\134\0\212\0\270\0\346\0\270\0\u0114"
			+ "\0\u0142\0\u0170\0\270\0\u019e\0\270\0\u01cc\0\u0170\0\u0170"
			+ "\0\u01fa\0\u0228\0\u0256\0\u0284\0\u02b2\0\u02e0\0\270\0\u030e"
			+ "\0\u033c\0\u036a\0\u0398\0\u03c6\0\u03f4\0\u0422\0\u0450\0\u047e"
			+ "\0\u04ac\0\270\0\u04da\0\u0508\0\270\0\270\0\u0536\0\270"
			+ "\0\270\0\u0564\0\u0592\0\u05c0\0\u0564\0\u0564\0\u05ee\0\u061c"
			+ "\0\u064a\0\u0678\0\u06a6\0\u06d4\0\u0702\0\u0730\0\u075e\0\u078c"
			+ "\0\u07ba\0\u07e8\0\u0816\0\u0844\0\u0872\0\u08a0\0\u0170\0\u08ce"
			+ "\0\u08fc\0\u092a\0\270\0\u0958\0\u0986\0\u0564\0\u09b4\0\u09e2"
			+ "\0\u0a10\0\u0a3e\0\u0a6c\0\u0a9a\0\u0ac8\0\u0af6\0\u0b24\0\u0b52"
			+ "\0\u0b80\0\u0bae\0\u0bdc\0\u0c0a\0\u0c38\0\u0c66\0\u0170\0\u0c94"
			+ "\0\u0cc2\0\u0cf0\0\u0d1e\0\u0d4c\0\u0d7a\0\u0da8\0\u0dd6\0\u0e04"
			+ "\0\u0e32\0\u0e60\0\u0e8e\0\u0ebc\0\u0eea\0\u0f18\0\u0f46\0\u0f74"
			+ "\0\u0fa2\0\u0fd0\0\u0ffe\0\u102c\0\u105a";

	private static int[] zzUnpackRowMap() {
		final int[] result = new int[109];
		int offset = 0;
		offset = zzUnpackRowMap(ZZ_ROWMAP_PACKED_0, offset, result);
		return result;
	}

	private static int zzUnpackRowMap(final String packed, final int offset,
		final int[] result)
	{
		int i = 0; /* index in packed string  */
		int j = offset; /* index in unpacked array */
		final int l = packed.length();
		while (i < l) {
			final int high = packed.charAt(i++) << 16;
			result[j++] = high | packed.charAt(i++);
		}
		return j;
	}

	/**
	 * The transition table of the DFA
	 */
	private static final int[] ZZ_TRANS = zzUnpackTrans();

	private static final String ZZ_TRANS_PACKED_0 =
		"\1\5\1\6\2\7\1\10\1\11\2\12\1\10\1\12"
			+ "\1\13\1\12\1\14\1\15\1\5\1\16\1\7\1\17"
			+ "\1\20\1\21\1\22\1\23\1\24\2\12\1\25\1\26"
			+ "\4\12\1\27\2\30\1\27\1\31\1\12\1\32\1\12"
			+ "\1\33\1\34\1\12\1\35\1\36\1\37\1\12\12\40"
			+ "\1\41\2\40\1\42\40\40\1\43\1\44\13\43\1\45"
			+ "\10\43\1\46\17\43\1\46\4\43\1\46\2\43\15\47"
			+ "\1\50\10\47\1\46\17\47\1\46\4\47\1\46\2\47"
			+ "\60\0\1\51\53\0\1\52\3\0\2\10\2\52\1\10"
			+ "\1\52\1\0\2\52\2\0\1\53\3\0\2\52\1\54"
			+ "\1\55\1\52\1\56\1\52\1\0\1\52\1\56\1\55"
			+ "\1\54\5\0\5\52\1\55\5\52\3\0\1\57\1\60"
			+ "\2\52\1\60\1\52\1\0\2\52\2\0\1\53\3\0"
			+ "\2\52\1\54\1\55\1\52\1\56\1\52\1\0\1\61"
			+ "\1\56\1\55\1\54\5\0\5\52\1\55\4\52\4\0"
			+ "\6\12\1\0\2\12\4\0\11\12\1\0\4\12\5\0"
			+ "\12\12\4\0\6\12\1\0\2\12\4\0\3\12\1\62"
			+ "\5\12\1\0\4\12\5\0\12\12\4\0\2\53\2\0"
			+ "\1\53\51\0\6\12\1\0\2\12\4\0\3\12\1\63"
			+ "\5\12\1\0\4\12\5\0\12\12\4\0\6\12\1\0"
			+ "\2\12\4\0\4\12\1\64\4\12\1\0\4\12\5\0"
			+ "\12\12\4\0\6\12\1\0\1\65\1\12\4\0\7\12"
			+ "\1\66\1\12\1\0\4\12\5\0\12\12\4\0\5\12"
			+ "\1\67\1\0\2\12\4\0\6\12\1\70\2\12\1\0"
			+ "\4\12\5\0\3\12\1\71\6\12\4\0\6\12\1\0"
			+ "\2\12\4\0\11\12\1\0\4\12\5\0\7\12\1\72"
			+ "\2\12\32\0\1\26\64\0\1\27\55\0\1\27\1\0"
			+ "\1\73\16\0\6\12\1\0\2\12\4\0\6\12\1\74"
			+ "\2\12\1\0\4\12\5\0\3\12\1\75\6\12\4\0"
			+ "\6\12\1\0\2\12\4\0\2\12\1\76\6\12\1\0"
			+ "\4\12\5\0\12\12\4\0\6\12\1\0\2\12\4\0"
			+ "\5\12\1\77\3\12\1\0\4\12\5\0\12\12\4\0"
			+ "\6\12\1\0\2\12\4\0\7\12\1\100\1\12\1\0"
			+ "\4\12\5\0\12\12\4\0\6\12\1\0\2\12\4\0"
			+ "\11\12\1\0\4\12\5\0\2\12\1\101\7\12\4\0"
			+ "\6\12\1\0\2\12\4\0\4\12\1\102\4\12\1\0"
			+ "\4\12\5\0\12\12\12\40\1\0\2\40\1\0\40\40"
			+ "\12\0\1\46\43\0\1\43\1\0\13\43\1\0\10\43"
			+ "\1\0\17\43\1\0\4\43\1\0\2\43\3\0\1\103"
			+ "\52\0\15\47\1\0\10\47\1\0\17\47\1\0\4\47"
			+ "\1\0\2\47\1\52\3\0\6\52\1\0\2\52\6\0"
			+ "\7\52\1\0\4\52\5\0\13\52\3\0\2\53\2\52"
			+ "\1\53\1\52\1\0\2\52\6\0\2\52\1\54\1\55"
			+ "\3\52\1\0\2\52\1\55\1\54\5\0\5\52\1\55"
			+ "\5\52\3\0\2\104\2\52\1\104\1\52\1\0\2\52"
			+ "\6\0\7\52\1\0\4\52\1\105\4\0\13\52\3\0"
			+ "\2\57\2\52\1\57\1\52\1\0\2\52\2\0\1\53"
			+ "\3\0\2\52\1\54\1\55\3\52\1\0\2\52\1\55"
			+ "\1\54\5\0\5\52\1\55\5\52\3\0\1\57\1\60"
			+ "\2\52\1\60\1\52\1\0\2\52\2\0\1\53\3\0"
			+ "\2\52\1\54\1\55\1\52\1\106\1\52\1\0\1\52"
			+ "\1\106\1\55\1\54\5\0\5\52\1\55\5\52\3\0"
			+ "\2\107\1\52\2\107\1\52\1\0\1\52\1\107\6\0"
			+ "\2\52\3\107\2\52\1\0\2\52\2\107\5\0\1\52"
			+ "\1\107\3\52\1\107\4\52\4\0\6\12\1\0\2\12"
			+ "\4\0\4\12\1\110\4\12\1\0\4\12\5\0\12\12"
			+ "\4\0\5\12\1\111\1\0\2\12\4\0\11\12\1\0"
			+ "\4\12\5\0\11\12\1\77\4\0\6\12\1\0\2\12"
			+ "\4\0\2\12\1\112\6\12\1\0\4\12\5\0\12\12"
			+ "\4\0\6\12\1\0\2\12\4\0\11\12\1\0\4\12"
			+ "\5\0\5\12\1\77\4\12\4\0\6\12\1\0\2\12"
			+ "\4\0\10\12\1\113\1\0\4\12\5\0\12\12\4\0"
			+ "\6\12\1\0\1\114\1\12\4\0\11\12\1\0\4\12"
			+ "\5\0\12\12\4\0\6\12\1\0\2\12\4\0\7\12"
			+ "\1\115\1\12\1\0\4\12\5\0\12\12\4\0\6\12"
			+ "\1\0\2\12\4\0\3\12\1\77\5\12\1\0\4\12"
			+ "\5\0\12\12\4\0\6\12\1\0\2\12\4\0\11\12"
			+ "\1\0\4\12\5\0\4\12\1\116\5\12\43\0\1\27"
			+ "\16\0\6\12\1\0\2\12\4\0\2\12\1\117\5\12"
			+ "\1\120\1\0\4\12\5\0\12\12\4\0\6\12\1\0"
			+ "\1\121\1\12\4\0\11\12\1\0\4\12\5\0\12\12"
			+ "\4\0\6\12\1\0\2\12\4\0\11\12\1\0\4\12"
			+ "\5\0\2\12\1\122\7\12\4\0\6\12\1\0\2\12"
			+ "\4\0\11\12\1\0\4\12\5\0\3\12\1\123\6\12"
			+ "\4\0\6\12\1\0\2\12\4\0\11\12\1\0\4\12"
			+ "\5\0\4\12\1\124\5\12\4\0\6\12\1\0\2\12"
			+ "\4\0\3\12\1\125\5\12\1\0\4\12\5\0\12\12"
			+ "\1\52\3\0\2\104\2\52\1\104\1\52\1\0\2\52"
			+ "\6\0\3\52\1\55\3\52\1\0\2\52\1\55\1\52"
			+ "\5\0\5\52\1\55\4\52\4\0\2\104\2\0\1\104"
			+ "\45\0\1\52\3\0\2\107\1\52\2\107\1\52\1\0"
			+ "\1\52\1\107\6\0\2\52\3\107\1\106\1\52\1\0"
			+ "\1\52\1\106\2\107\5\0\1\52\1\107\3\52\1\107"
			+ "\4\52\4\0\6\12\1\0\2\12\4\0\6\12\1\126"
			+ "\2\12\1\0\4\12\5\0\12\12\4\0\6\12\1\0"
			+ "\2\12\4\0\4\12\1\127\4\12\1\0\4\12\5\0"
			+ "\12\12\4\0\5\12\1\130\1\0\2\12\4\0\11\12"
			+ "\1\0\4\12\5\0\12\12\4\0\6\12\1\0\2\12"
			+ "\4\0\4\12\1\131\4\12\1\0\4\12\5\0\12\12"
			+ "\4\0\6\12\1\0\2\12\4\0\11\12\1\0\4\12"
			+ "\5\0\1\12\1\132\10\12\4\0\6\12\1\0\2\12"
			+ "\4\0\10\12\1\111\1\0\4\12\5\0\12\12\4\0"
			+ "\6\12\1\0\2\12\4\0\2\12\1\117\6\12\1\0"
			+ "\4\12\5\0\12\12\4\0\6\12\1\0\2\12\4\0"
			+ "\11\12\1\0\4\12\5\0\1\12\1\133\10\12\4\0"
			+ "\6\12\1\0\2\12\4\0\4\12\1\77\4\12\1\0"
			+ "\4\12\5\0\12\12\4\0\6\12\1\0\2\12\4\0"
			+ "\2\12\1\134\6\12\1\0\4\12\5\0\12\12\4\0"
			+ "\6\12\1\0\2\12\4\0\4\12\1\135\4\12\1\0"
			+ "\4\12\5\0\12\12\4\0\6\12\1\0\1\12\1\136"
			+ "\4\0\11\12\1\0\4\12\5\0\12\12\4\0\6\12"
			+ "\1\0\2\12\4\0\7\12\1\120\1\12\1\0\4\12"
			+ "\5\0\12\12\4\0\6\12\1\0\2\12\4\0\10\12"
			+ "\1\137\1\0\4\12\5\0\12\12\4\0\6\12\1\0"
			+ "\2\12\4\0\11\12\1\0\4\12\5\0\1\77\11\12"
			+ "\4\0\6\12\1\0\2\12\4\0\3\12\1\140\5\12"
			+ "\1\0\4\12\5\0\12\12\4\0\6\12\1\0\2\12"
			+ "\4\0\11\12\1\0\4\12\5\0\4\12\1\34\5\12"
			+ "\4\0\6\12\1\0\2\12\4\0\2\12\1\141\6\12"
			+ "\1\0\4\12\5\0\12\12\4\0\6\12\1\0\2\12"
			+ "\4\0\11\12\1\0\4\12\5\0\2\12\1\77\7\12"
			+ "\4\0\6\12\1\0\2\12\4\0\11\12\1\0\4\12"
			+ "\5\0\4\12\1\142\5\12\4\0\6\12\1\0\2\12"
			+ "\4\0\3\12\1\143\5\12\1\0\4\12\5\0\12\12"
			+ "\4\0\6\12\1\0\2\12\4\0\6\12\1\144\2\12"
			+ "\1\0\4\12\5\0\12\12\4\0\6\12\1\0\2\12"
			+ "\4\0\11\12\1\0\4\12\5\0\4\12\1\145\5\12"
			+ "\4\0\6\12\1\0\1\77\1\12\4\0\11\12\1\0"
			+ "\4\12\5\0\12\12\4\0\6\12\1\0\2\12\4\0"
			+ "\11\12\1\0\4\12\5\0\4\12\1\146\5\12\4\0"
			+ "\6\12\1\0\1\147\1\12\4\0\11\12\1\0\4\12"
			+ "\5\0\12\12\4\0\6\12\1\0\2\12\4\0\11\12"
			+ "\1\0\4\12\5\0\7\12\1\150\2\12\4\0\6\12"
			+ "\1\0\2\12\4\0\7\12\1\77\1\12\1\0\4\12"
			+ "\5\0\12\12\4\0\6\12\1\0\2\12\4\0\10\12"
			+ "\1\151\1\0\4\12\5\0\12\12\4\0\6\12\1\0"
			+ "\2\12\4\0\11\12\1\0\4\12\5\0\3\12\1\140"
			+ "\6\12\4\0\5\12\1\120\1\0\2\12\4\0\11\12"
			+ "\1\0\4\12\5\0\12\12\4\0\6\12\1\0\2\12"
			+ "\4\0\11\12\1\0\4\12\5\0\4\12\1\152\5\12"
			+ "\4\0\6\12\1\0\2\12\4\0\2\12\1\153\6\12"
			+ "\1\0\4\12\5\0\12\12\4\0\6\12\1\0\2\12"
			+ "\4\0\10\12\1\120\1\0\4\12\5\0\12\12\4\0"
			+ "\6\12\1\0\2\12\4\0\4\12\1\154\4\12\1\0"
			+ "\4\12\5\0\12\12\4\0\6\12\1\0\1\155\1\12"
			+ "\4\0\11\12\1\0\4\12\5\0\12\12\4\0\6\12"
			+ "\1\0\2\12\4\0\2\12\1\77\6\12\1\0\4\12" + "\5\0\12\12";

	private static int[] zzUnpackTrans() {
		final int[] result = new int[4232];
		int offset = 0;
		offset = zzUnpackTrans(ZZ_TRANS_PACKED_0, offset, result);
		return result;
	}

	private static int zzUnpackTrans(final String packed, final int offset,
		final int[] result)
	{
		int i = 0; /* index in packed string  */
		int j = offset; /* index in unpacked array */
		final int l = packed.length();
		while (i < l) {
			int count = packed.charAt(i++);
			int value = packed.charAt(i++);
			value--;
			do
				result[j++] = value;
			while (--count > 0);
		}
		return j;
	}

	/* error codes */
	private static final int ZZ_UNKNOWN_ERROR = 0;
	private static final int ZZ_NO_MATCH = 1;
	private static final int ZZ_PUSHBACK_2BIG = 2;

	/* error messages for the codes above */
	private static final String ZZ_ERROR_MSG[] = {
		"Unkown internal scanner error", "Error: could not match input",
		"Error: pushback value was too large" };

	/**
	 * ZZ_ATTRIBUTE[aState] contains the attributes of state {@code aState}
	 */
	private static final int[] ZZ_ATTRIBUTE = zzUnpackAttribute();

	private static final String ZZ_ATTRIBUTE_PACKED_0 =
		"\4\0\1\11\1\1\1\11\3\1\1\11\1\1\1\11"
			+ "\11\1\1\11\12\1\1\11\2\1\2\11\1\1\2\11" + "\31\1\1\11\1\1\1\0\50\1";

	private static int[] zzUnpackAttribute() {
		final int[] result = new int[109];
		int offset = 0;
		offset = zzUnpackAttribute(ZZ_ATTRIBUTE_PACKED_0, offset, result);
		return result;
	}

	private static int zzUnpackAttribute(final String packed, final int offset,
		final int[] result)
	{
		int i = 0; /* index in packed string  */
		int j = offset; /* index in unpacked array */
		final int l = packed.length();
		while (i < l) {
			int count = packed.charAt(i++);
			final int value = packed.charAt(i++);
			do
				result[j++] = value;
			while (--count > 0);
		}
		return j;
	}

	/** the input device */
	private java.io.Reader zzReader;

	/** the current state of the DFA */
	private int zzState;

	/** the current lexical state */
	private int zzLexicalState = YYINITIAL;

	/**
	 * this buffer contains the current text to be matched and is the source of
	 * the yytext() string
	 */
	private char zzBuffer[] = new char[ZZ_BUFFERSIZE];

	/** the textposition at the last accepting state */
	private int zzMarkedPos;

	/** the current text position in the buffer */
	private int zzCurrentPos;

	/** startRead marks the beginning of the yytext() string in the buffer */
	private int zzStartRead;

	/**
	 * endRead marks the last character in the buffer, that has been read from
	 * input
	 */
	private int zzEndRead;

	/** number of newlines encountered up to the start of the matched text */
	private int yyline;

	/** the number of characters up to the start of the matched text */
	private int yychar;

	/**
	 * the number of characters from the last newline up to the start of the
	 * matched text
	 */
	private int yycolumn;

	/**
	 * zzAtBOL == true <=> the scanner is currently at the beginning of a line
	 */
	private boolean zzAtBOL = true;

	/** zzAtEOF == true <=> the scanner is at the EOF */
	private boolean zzAtEOF;

	/** denotes if the user-EOF-code has already been executed */
	private boolean zzEOFDone;

	/* user code: */

	/**
	 * Constructor. This must be here because JFlex does not generate a
	 * no-parameter constructor.
	 */
	public MatlabTokenMaker() {}

	/**
	 * Adds the token specified to the current linked list of tokens.
	 *
	 * @param tokenType The token's type.
	 * @see #addToken(int, int, int)
	 */
	private void addHyperlinkToken(final int start, final int end,
		final int tokenType)
	{
		final int so = start + offsetShift;
		addToken(zzBuffer, start, end, tokenType, so, true);
	}

	/**
	 * Adds the token specified to the current linked list of tokens.
	 *
	 * @param tokenType The token's type.
	 */
	private void addToken(final int tokenType) {
		addToken(zzStartRead, zzMarkedPos - 1, tokenType);
	}

	/**
	 * Adds the token specified to the current linked list of tokens.
	 *
	 * @param tokenType The token's type.
	 * @see #addHyperlinkToken(int, int, int)
	 */
	private void addToken(final int start, final int end, final int tokenType) {
		final int so = start + offsetShift;
		addToken(zzBuffer, start, end, tokenType, so, false);
	}

	/**
	 * Adds the token specified to the current linked list of tokens.
	 *
	 * @param array The character array.
	 * @param start The starting offset in the array.
	 * @param end The ending offset in the array.
	 * @param tokenType The token's type.
	 * @param startOffset The offset in the document at which this token occurs.
	 * @param hyperlink Whether this token is a hyperlink.
	 */
	@Override
	public void addToken(final char[] array, final int start, final int end,
		final int tokenType, final int startOffset, final boolean hyperlink)
	{
		super.addToken(array, start, end, tokenType, startOffset, hyperlink);
		zzStartRead = zzMarkedPos;
	}

	/**
	 * Returns the first token in the linked list of tokens generated from
	 * {@code text}. This method must be implemented by subclasses so they
	 * can correctly implement syntax highlighting.
	 *
	 * @param text The text from which to get tokens.
	 * @param initialTokenType The token type we should start with.
	 * @param startOffset The offset into the document at which {@code text}
	 *          starts.
	 * @return The first {@code Token} in a linked list representing the
	 *         syntax highlighted text.
	 */
	@Override
	public Token getTokenList(final Segment text, final int initialTokenType,
		final int startOffset)
	{

		resetTokenList();
		this.offsetShift = -text.offset + startOffset;

		// Start off in the proper state.
		int state = TokenTypes.NULL;
		switch (initialTokenType) {
			case TokenTypes.COMMENT_MULTILINE:
				state = MLC;
				start = text.offset;
				break;
			/*case Token.COMMENT_DOCUMENTATION:
				state = DOCCOMMENT;
				start = text.offset;
				break;*/
			case TokenTypes.LITERAL_STRING_DOUBLE_QUOTE:
				state = STRING;
				start = text.offset;
				break;
			default:
				state = TokenTypes.NULL;
		}

		s = text;
		try {
			yyreset(zzReader);
			yybegin(state);
			return yylex();
		}
		catch (final IOException ioe) {
			ioe.printStackTrace();
			return new TokenImpl();
		}

	}

	/**
	 * Refills the input buffer.
	 *
	 * @return {@code true} if EOF was reached, otherwise {@code false}.
	 * @exception IOException if any I/O-Error occurs.
	 */
	private boolean zzRefill() throws java.io.IOException {
		return zzCurrentPos >= s.offset + s.count;
	}

	/**
	 * Resets the scanner to read from a new input stream. Does not close the old
	 * reader. All internal variables are reset, the old input stream
	 * <b>cannot</b> be reused (internal buffer is discarded and lost). Lexical
	 * state is set to {@code YY_INITIAL}.
	 *
	 * @param reader the new input stream
	 */
	public final void yyreset(final java.io.Reader reader)
		throws java.io.IOException
	{
		// 's' has been updated.
		zzBuffer = s.array;
		/*
		 * We replaced the line below with the two below it because zzRefill
		 * no longer "refills" the buffer (since the way we do it, it's always
		 * "full" the first time through, since it points to the segment's
		 * array).  So, we assign zzEndRead here.
		 */
		// zzStartRead = zzEndRead = s.offset;
		zzStartRead = s.offset;
		zzEndRead = zzStartRead + s.count - 1;
		zzCurrentPos = zzMarkedPos = s.offset;
		zzLexicalState = YYINITIAL;
		zzReader = reader;
		zzAtBOL = true;
		zzAtEOF = false;
	}

	/**
	 * Creates a new scanner There is also a java.io.InputStream version of this
	 * constructor.
	 *
	 * @param in the java.io.Reader to read input from.
	 */
	public MatlabTokenMaker(final java.io.Reader in) {
		this.zzReader = in;
	}

	/**
	 * Creates a new scanner. There is also java.io.Reader version of this
	 * constructor.
	 *
	 * @param in the java.io.Inputstream to read input from.
	 */
	public MatlabTokenMaker(final java.io.InputStream in) {
		this(new java.io.InputStreamReader(in));
	}

	/**
	 * Unpacks the compressed character translation table.
	 *
	 * @param packed the packed character translation table
	 * @return the unpacked character translation table
	 */
	private static char[] zzUnpackCMap(final String packed) {
		final char[] map = new char[0x10000];
		int i = 0; /* index in packed string  */
		int j = 0; /* index in unpacked array */
		while (i < 156) {
			int count = packed.charAt(i++);
			final char value = packed.charAt(i++);
			do
				map[j++] = value;
			while (--count > 0);
		}
		return map;
	}

	/**
	 * Refills the input buffer.
	 *
	 * @return {@code false}, iff there was new input.
	 * @exception java.io.IOException if any I/O-Error occurs
	 */

	/**
	 * Closes the input stream.
	 */
	public final void yyclose() throws java.io.IOException {
		zzAtEOF = true; /* indicate end of file */
		zzEndRead = zzStartRead; /* invalidate buffer    */

		if (zzReader != null) zzReader.close();
	}

	/**
	 * Returns the current lexical state.
	 */
	public final int yystate() {
		return zzLexicalState;
	}

	/**
	 * Enters a new lexical state
	 *
	 * @param newState the new lexical state
	 */
	@Override
	public final void yybegin(final int newState) {
		zzLexicalState = newState;
	}

	/**
	 * @return the text matched by the current regular expression.
	 */
	public final String yytext() {
		return new String(zzBuffer, zzStartRead, zzMarkedPos - zzStartRead);
	}

	/**
	 * Returns the character at position {@code pos} from the matched text. It is
	 * equivalent to {@code yytext().charAt(pos)}, but faster.
	 *
	 * @param pos the position of the character to fetch. A value from 0 to
	 *          yylength()-1.
	 * @return the character at position pos
	 */
	public final char yycharat(final int pos) {
		return zzBuffer[zzStartRead + pos];
	}

	/**
	 * @return the length of the matched text region
	 */
	public final int yylength() {
		return zzMarkedPos - zzStartRead;
	}

	/**
	 * Reports an error that occured while scanning. In a wellformed scanner (no
	 * or only correct usage of yypushback(int) and a match-all fallback rule)
	 * this method will only be called with things that "Can't Possibly Happen".
	 * If this method is called, something is seriously wrong (e.g. a JFlex bug
	 * producing a faulty scanner etc.). Usual syntax/scanner level error handling
	 * should be done in error fallback rules.
	 *
	 * @param errorCode the code of the errormessage to display
	 */
	private void zzScanError(final int errorCode) {
		String message;
		try {
			message = ZZ_ERROR_MSG[errorCode];
		}
		catch (final ArrayIndexOutOfBoundsException e) {
			message = ZZ_ERROR_MSG[ZZ_UNKNOWN_ERROR];
		}

		throw new Error(message);
	}

	/**
	 * Pushes the specified amount of characters back into the input stream. They
	 * will be read again by then next call of the scanning method
	 *
	 * @param number the number of characters to be read again. This number must
	 *          not be greater than yylength()!
	 */
	public void yypushback(final int number) {
		if (number > yylength()) zzScanError(ZZ_PUSHBACK_2BIG);

		zzMarkedPos -= number;
	}

	/**
	 * Resumes scanning until the next regular expression is matched, the end of
	 * input is encountered or an I/O-Error occurs.
	 *
	 * @return the next token
	 * @exception java.io.IOException if any I/O-Error occurs
	 */
	public org.fife.ui.rsyntaxtextarea.Token yylex() throws java.io.IOException {
		int zzInput;
		int zzAction;

		// cached fields:
		int zzCurrentPosL;
		int zzMarkedPosL;
		int zzEndReadL = zzEndRead;
		char[] zzBufferL = zzBuffer;
		final char[] zzCMapL = ZZ_CMAP;

		final int[] zzTransL = ZZ_TRANS;
		final int[] zzRowMapL = ZZ_ROWMAP;
		final int[] zzAttrL = ZZ_ATTRIBUTE;

		while (true) {
			zzMarkedPosL = zzMarkedPos;

			zzAction = -1;

			zzCurrentPosL = zzCurrentPos = zzStartRead = zzMarkedPosL;

			zzState = ZZ_LEXSTATE[zzLexicalState];

			zzForAction:
			{
				while (true) {

					if (zzCurrentPosL < zzEndReadL) zzInput = zzBufferL[zzCurrentPosL++];
					else if (zzAtEOF) {
						zzInput = YYEOF;
						break zzForAction;
					}
					else {
						// store back cached positions
						zzCurrentPos = zzCurrentPosL;
						zzMarkedPos = zzMarkedPosL;
						final boolean eof = zzRefill();
						// get translated positions and possibly new buffer
						zzCurrentPosL = zzCurrentPos;
						zzMarkedPosL = zzMarkedPos;
						zzBufferL = zzBuffer;
						zzEndReadL = zzEndRead;
						if (eof) {
							zzInput = YYEOF;
							break zzForAction;
						}
						zzInput = zzBufferL[zzCurrentPosL++];
					}
					final int zzNext = zzTransL[zzRowMapL[zzState] + zzCMapL[zzInput]];
					if (zzNext == -1) break zzForAction;
					zzState = zzNext;

					final int zzAttributes = zzAttrL[zzState];
					if ((zzAttributes & 1) == 1) {
						zzAction = zzState;
						zzMarkedPosL = zzCurrentPosL;
						if ((zzAttributes & 8) == 8) break zzForAction;
					}

				}
			}

			// store back cached position
			zzMarkedPos = zzMarkedPosL;

			switch (zzAction < 0 ? zzAction : ZZ_ACTION[zzAction]) {
				case 7:
					addNullToken();
					return firstToken;
				case 22:
					break;
				case 20:
					yybegin(YYINITIAL);
					addToken(start, zzStartRead + 1, TokenTypes.COMMENT_MULTILINE);
					break;
				case 23:
					break;
				case 15:
					start = zzMarkedPos - 2;
					yybegin(MLC);
					break;
				case 24:
					break;
				case 9:
					addToken(TokenTypes.WHITESPACE);
					break;
				case 25:
					break;
				case 18:
					addToken(TokenTypes.LITERAL_NUMBER_HEXADECIMAL);
					break;
				case 26:
					break;
				case 17:
					addToken(TokenTypes.LITERAL_NUMBER_FLOAT);
					break;
				case 27:
					break;
				case 19:
					addToken(TokenTypes.RESERVED_WORD);
					break;
				case 28:
					break;
				case 3:
					addToken(TokenTypes.SEPARATOR);
					break;
				case 29:
					break;
				case 5:
					addToken(TokenTypes.IDENTIFIER);
					break;
				case 30:
					break;
				case 14:
					addToken(start, zzStartRead - 1, TokenTypes.COMMENT_EOL);
					addNullToken();
					return firstToken;
				case 31:
					break;
				case 1:
					addToken(TokenTypes.ERROR_IDENTIFIER);
					break;
				case 32:
					break;
				case 21:
					addToken(TokenTypes.LITERAL_BOOLEAN);
					break;
				case 33:
					break;
				case 12:
					addToken(start, zzStartRead - 1,
						TokenTypes.LITERAL_STRING_DOUBLE_QUOTE);
					return firstToken;
				case 34:
					break;
				case 16:
					addToken(TokenTypes.ERROR_NUMBER_FORMAT);
					break;
				case 35:
					break;
				case 6:
					start = zzMarkedPos - 1;
					yybegin(STRING);
					break;
				case 36:
					break;
				case 2:
					start = zzMarkedPos - 1;
					yybegin(EOL_COMMENT);
					break;
				case 37:
					break;
				case 4:
					addToken(TokenTypes.LITERAL_NUMBER_DECIMAL_INT);
					break;
				case 38:
					break;
				case 8:
					addToken(TokenTypes.OPERATOR);
					break;
				case 39:
					break;
				case 11:
					yybegin(YYINITIAL);
					addToken(start, zzStartRead, TokenTypes.LITERAL_STRING_DOUBLE_QUOTE);
					break;
				case 40:
					break;
				case 10:
					break;
				case 41:
					break;
				case 13:
					addToken(start, zzStartRead - 1, TokenTypes.COMMENT_MULTILINE);
					return firstToken;
				case 42:
					break;
				default:
					if (zzInput == YYEOF && zzStartRead == zzCurrentPos) {
						zzAtEOF = true;
						switch (zzLexicalState) {
							case EOL_COMMENT: {
								addToken(start, zzStartRead - 1, TokenTypes.COMMENT_EOL);
								addNullToken();
								return firstToken;
							}
							case 110:
								break;
							case STRING: {
								addToken(start, zzStartRead - 1,
									TokenTypes.LITERAL_STRING_DOUBLE_QUOTE);
								return firstToken;
							}
							case 111:
								break;
							case YYINITIAL: {
								addNullToken();
								return firstToken;
							}
							case 112:
								break;
							case MLC: {
								addToken(start, zzStartRead - 1, TokenTypes.COMMENT_MULTILINE);
								return firstToken;
							}
							case 113:
								break;
							default:
								return null;
						}
					}
					else {
						zzScanError(ZZ_NO_MATCH);
					}
			}
		}
	}

}
