/*
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

package org.scijava.ui.swing.script;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.WeakHashMap;

import javax.swing.JTextArea;

import org.scijava.log.LogService;

/**
 * TODO
 *
 * @author Johannes Schindelin
 */
public class ExceptionHandler {

	private static ExceptionHandler instance;
	private final LogService log;
	private final Map<ThreadGroup, TextEditor> threadMap;

	// prevent instantiation from somewhere else
	private ExceptionHandler(final LogService logService) {
		this.log = logService;
		threadMap = new WeakHashMap<>();
	}

	public static ExceptionHandler getInstance(final LogService logService) {
		if (instance == null) {
			instance = new ExceptionHandler(logService);
		}
		else if (instance.log != logService) {
			throw new RuntimeException(
				"Cannot have an ExceptionHandler with two different LogServices");
		}
		return instance;
	}

	public static void addThread(final Thread thread, final TextEditor editor) {
		addThreadGroup(thread.getThreadGroup(), editor);
	}

	public static void addThreadGroup(final ThreadGroup group,
		final TextEditor editor)
	{
		final ExceptionHandler handler = getInstance(editor.log());
		handler.threadMap.put(group, editor);
	}

	public void handle(final Throwable t) {
		ThreadGroup group = Thread.currentThread().getThreadGroup();
		while (group != null) {
			final TextEditor editor = threadMap.get(group);
			if (editor != null) {
				handle(t, editor);
				return;
			}
			group = group.getParent();
		}
		log.error(t);
	}

	public static void handle(Throwable t, final TextEditor editor) {
		final JTextArea screen = editor.getErrorScreen();
		editor.getTab().showErrors();

		if (t instanceof InvocationTargetException) {
			t = ((InvocationTargetException) t).getTargetException();
		}
		final StackTraceElement[] trace = t.getStackTrace();

		screen.insert(t.getClass().getName() + ": " + t.getMessage() + "\n", screen
			.getDocument().getLength());
		final ErrorHandler handler = new ErrorHandler(screen);
		for (int i = 0; i < trace.length; i++) {
			final String fileName = trace[i].getFileName();
			final int line = trace[i].getLineNumber();
			final String text =
				"\t at " + trace[i].getClassName() + "." + trace[i].getMethodName() +
					"(" + fileName + ":" + line + ")\n";
			final File file = editor.getFileForBasename(fileName);
			handler
				.addError(file == null ? null : file.getAbsolutePath(), line, text);
		}

		editor.setErrorHandler(handler);
	}
}
