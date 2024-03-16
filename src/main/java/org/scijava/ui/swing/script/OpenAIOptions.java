/*
 * #%L
 * Script Editor and Interpreter for SciJava script languages.
 * %%
 * Copyright (C) 2009 - 2024 SciJava developers.
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

import org.scijava.options.OptionsPlugin;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * Runs the Edit::Options::OpenAI dialog.
 * 
 * @author Curtis Rueden
 */
@Plugin(type = OptionsPlugin.class, menuPath = "Edit>Options>OpenAI...")
public class OpenAIOptions extends OptionsPlugin {

	@Parameter(label = "OpenAI key", required = false)
	private String openAIKey;

	@Parameter(label = "OpenAI Model name")
	private String modelName = "gpt-3.5-turbo-0613";

	@Parameter(label = "Prompt prefix (added before custom prompt)", style = "text area")
	private String promptPrefix = "Write code in {programming_language}.\n" +
			"Write concise and high quality code for ImageJ/Fiji.\n" +
			"Put minimal comments explaining what the code does.\n" +
			"The code should do the following:\n" +
			"{custom_prompt}";

	public String getOpenAIKey() {
		return openAIKey;
	}

	public void setOpenAIKey(final String openAIKey) {
		this.openAIKey = openAIKey;
	}

	public String getPromptPrefix() {
		return promptPrefix;
	}

	public void setPromptPrefix(final String promptPrefix) {
		this.promptPrefix = promptPrefix;
	}

	public String getModelName() {
		return modelName;
	}

	public void setModelName(final String modelName) {
		this.modelName = modelName;
	}


}
