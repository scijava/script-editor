/*
 * #%L
 * Script Editor and Interpreter for SciJava script languages.
 * %%
 * Copyright (C) 2009 - 2016 Board of Regents of the University of
 * Wisconsin-Madison, Max Planck Institute of Molecular Cell Biology and Genetics,
 * and others.
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

import org.fife.rsta.ac.LanguageSupport;
import org.scijava.plugin.SingletonService;
import org.scijava.script.ScriptLanguage;
import org.scijava.service.SciJavaService;

/**
 * Service which manages {@link LanguageSupportPlugin}s.
 * {@link LanguageSupportPlugin}s provide features like code completion for
 * example.
 *
 * @author Jonathan Hale
 */
public interface LanguageSupportService extends
	SingletonService<LanguageSupportPlugin>, SciJavaService
{

	/**
	 * Get a {@link LanguageSupport} for the given language.
	 *
	 * @param language Language to get support for.
	 * @return a {@link LanguageSupport} matching the given language or the
	 *         <code>null</code> if there was none or language was
	 *         <code>null</code>.
	 */
	public abstract LanguageSupport getLanguageSupport(ScriptLanguage language);

}
