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

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import org.fife.ui.autocomplete.CompletionProvider;
import org.fife.ui.autocomplete.FunctionCompletion;

/**
 * An RSTA {@link FunctionCompletion} for callable completions (methods,
 * functions, constructors), so parameter assistance and parameter choices
 * engage. It also carries optional additional edits (e.g. the auto-import
 * needed by a constructor completion), applied by
 * {@link SciJavaAutoCompletion}.
 *
 * @author Curtis Rueden
 */
public class SciJavaFunctionCompletion extends FunctionCompletion implements
	AdditionalEdits
{

	private final List<Edit> additionalEdits;
	private Supplier<String> description;

	public SciJavaFunctionCompletion(final CompletionProvider provider,
		final String name, final String returnType,
		final List<Edit> additionalEdits)
	{
		super(provider, name, returnType);
		this.additionalEdits = additionalEdits == null ? Collections.emptyList()
			: additionalEdits;
	}

	@Override
	public List<Edit> getAdditionalEdits() {
		return additionalEdits;
	}

	/**
	 * Sets a supplier of the description (e.g. documentation) shown beside the
	 * completion list, asked for only when the completion is selected.
	 */
	public void setDescription(final Supplier<String> description) {
		this.description = description;
	}

	/**
	 * Adds the description, if any, below RSTA's short description. (RSTA's
	 * {@link FunctionCompletion#getSummary} would not show it otherwise.)
	 */
	@Override
	protected boolean possiblyAddDescription(final StringBuilder sb) {
		final boolean added = super.possiblyAddDescription(sb);
		final String desc = description == null ? null : description.get();
		if (desc == null || desc.isEmpty()) return added;
		if (!added) sb.append("<hr><br>");
		sb.append(desc).append("<br><br><br>");
		return true;
	}

	/**
	 * As {@link FunctionCompletion#addParameters}, which fills in the side
	 * description window, but without the parameter descriptions: those list
	 * the other overloads, for the parameter tooltip only (see
	 * {@link SciJavaCompletionProvider#otherOverloads}).
	 */
	@Override
	protected void addParameters(final StringBuilder sb) {
		final int count = getParamCount();
		if (count > 0) {
			sb.append("<b>Parameters:</b><br>");
			sb.append("<center><table width='90%'><tr><td>");
			for (int i = 0; i < count; i++) {
				final Parameter param = getParam(i);
				sb.append("<b>");
				sb.append(param.getName() != null ? param.getName() : param
					.getType());
				sb.append("</b><br>");
			}
			sb.append("</td></tr></table></center><br><br>");
		}
		final String returnDesc = getReturnValueDescription();
		if (returnDesc != null) {
			sb.append("<b>Returns:</b><br>");
			sb.append("<center><table width='90%'><tr><td>");
			sb.append(returnDesc);
			sb.append("</td></tr></table></center><br><br>");
		}
	}
}
