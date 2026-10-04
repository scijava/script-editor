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

package org.scijava.ui.swing.script.autocompletion;

import java.util.Collections;
import java.util.List;

import org.fife.ui.autocomplete.CompletionProvider;
import org.fife.ui.autocomplete.FunctionCompletion;
import org.scijava.script.complete.Completion.TextEdit;

/**
 * An RSTA {@link FunctionCompletion} for callable completions (methods,
 * functions, constructors), so parameter assistance and parameter choices
 * engage. It also carries optional
 * {@link org.scijava.script.complete.Completion#additionalEdits() additional
 * edits} (e.g. the auto-import needed by a constructor completion), applied by
 * {@link SciJavaAutoCompletion}.
 *
 * @author Curtis Rueden
 */
public class SciJavaFunctionCompletion extends FunctionCompletion implements
	AdditionalEdits
{

	private final List<TextEdit> additionalEdits;

	public SciJavaFunctionCompletion(final CompletionProvider provider,
		final String name, final String returnType,
		final List<TextEdit> additionalEdits)
	{
		super(provider, name, returnType);
		this.additionalEdits = additionalEdits == null ? Collections.emptyList()
			: additionalEdits;
	}

	@Override
	public List<TextEdit> getAdditionalEdits() {
		return additionalEdits;
	}
}
