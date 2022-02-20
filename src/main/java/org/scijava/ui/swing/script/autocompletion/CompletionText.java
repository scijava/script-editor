/*-
 * #%L
 * Script Editor and Interpreter for SciJava script languages.
 * %%
 * Copyright (C) 2009 - 2022 SciJava developers.
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.fife.ui.autocomplete.AbstractCompletion;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.CompletionProvider;

public class CompletionText {

	private String replacementText;
	private String description;
	private String summary;
	private List<Parameter> method_args = Collections.emptyList();
	private String method_returnType = null;

	public CompletionText(final String replacementText) {
		this(replacementText, (String)null, (String)null);
	}

	public CompletionText(final String replacementText, final String summary, final String description) {
		this.replacementText = replacementText;
		this.summary = summary;
		this.description = description;
	}

	public CompletionText(final String replacementText, final Class<?> c, final Field f) {
		this(replacementText, ClassUtil.getSummaryCompletion(f, c), null);
	}

	public CompletionText(final String replacementText, final Class<?> c, final Method m) {
		this(replacementText, ClassUtil.getSummaryCompletion(m, c), null);
		this.method_args = Arrays.asList(m.getParameters());
		this.method_returnType = m.getReturnType().getCanonicalName();
	}

	public String getReplacementText() {
		return replacementText;
	}

	public String getDescription() {
		return description;
	}

	public String getSummary() {
		return summary;
	}
	
	public List<Parameter> getMethodArgs() {
		return method_args;
	}
	
	public String getReturnType() {
		return method_returnType;
	}

	public AbstractCompletion getCompletion(final CompletionProvider provider, final String replacementText) {
		return new BasicCompletion(provider, replacementText, description, summary);
	}
	
	public AbstractCompletion getCompletion(final CompletionProvider provider, final String replacementText, final int relevance) {
		final BasicCompletion bc = new BasicCompletion(provider, replacementText, description, summary);
		bc.setRelevance(relevance);
		return bc;
	}

	public void setReplacementText(final String replacementText) {
		this.replacementText = replacementText;
	}

	public void setDescription(final String description) {
		this.description = description;
	}

	public void setSummary(final String summary) {
		this.summary = summary;
	}

	@Override
	public String toString() {
		return replacementText + " | " + description + " | " + summary;
	}

}
