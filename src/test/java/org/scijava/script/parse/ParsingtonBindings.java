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

package org.scijava.script.parse;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import javax.script.Bindings;

import org.scijava.parsington.eval.AbstractEvaluator;
import org.scijava.parsington.eval.Evaluator;

/**
 * Script bindings for the {@link ParsingtonScriptEngine}.
 * <p>
 * Some operations are not yet implemented!
 * </p>
 * 
 * @author Curtis Rueden
 */
public class ParsingtonBindings implements Bindings {

	private final Evaluator e;

	public ParsingtonBindings(final Evaluator e) {
		this.e = e;
	}

	// -- Map methods --

	@Override
	public int size() {
		return vars().size();
	}

	@Override
	public boolean isEmpty() {
		return vars().isEmpty();
	}

	@Override
	public boolean containsValue(final Object value) {
		return vars().containsValue(value);
	}

	@Override
	public void clear() {
		vars().clear();
	}

	@Override
	public Set<String> keySet() {
		return vars().keySet();
	}

	@Override
	public Collection<Object> values() {
		return vars().values();
	}

	@Override
	public Set<java.util.Map.Entry<String, Object>> entrySet() {
		return vars().entrySet();
	}

	@Override
	public Object put(final String name, final Object value) {
		return vars().put(name, value);
	}

	@Override
	public void putAll(final Map<? extends String, ? extends Object> toMerge) {
		vars().putAll(toMerge);
	}

	@Override
	public boolean containsKey(final Object key) {
		return vars().containsKey(key);
	}

	@Override
	public Object get(final Object key) {
		return vars().get(key);
	}

	@Override
	public Object remove(final Object key) {
		return vars().remove(key);
	}

	// -- Helper methods --

	/** HACK: Extracts the internal vars map from the evaluator, if present. */
	private Map<String, Object> vars() {
		// TODO: Use public Evaluator API for this, once it exists.
		if (!(e instanceof AbstractEvaluator)) {
			throw new UnsupportedOperationException("Unimplemented");
		}
		try {
			final Field varsField = AbstractEvaluator.class.getDeclaredField("vars");
			varsField.setAccessible(true);
			@SuppressWarnings("unchecked")
			final Map<String, Object> vars = (Map<String, Object>) varsField.get(e);
			return vars;
		}
		catch (final NoSuchFieldException exc) {
			throw new IllegalStateException(exc);
		}
		catch (final IllegalAccessException exc) {
			throw new IllegalStateException(exc);
		}
	}

}
