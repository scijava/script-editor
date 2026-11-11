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

package org.scijava.ui.swing.script.autocompletion;

import java.util.List;

/**
 * Implemented by RSTA completions that carry additional edits (e.g.
 * auto-imports, a completion's {@code additionalTextEdits}) to apply when
 * accepted. Both the
 * {@code BasicCompletion}-based and {@code FunctionCompletion}-based SciJava
 * completions implement this, so {@link SciJavaAutoCompletion} can apply the
 * edits regardless of the underlying RSTA completion type.
 *
 * @author Curtis Rueden
 */
public interface AdditionalEdits {

	/** Extra edits to apply when the completion is accepted; never null. */
	List<Edit> getAdditionalEdits();

	/** Replaces the text between two offsets. */
	final class Edit {

		private final int start;
		private final int end;
		private final String newText;

		public Edit(final int start, final int end, final String newText) {
			this.start = start;
			this.end = end;
			this.newText = newText;
		}

		public int start() {
			return start;
		}

		public int end() {
			return end;
		}

		public String newText() {
			return newText;
		}
	}
}
