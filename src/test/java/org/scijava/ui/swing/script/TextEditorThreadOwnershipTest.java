/*-
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
package org.scijava.ui.swing.script;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.Reader;
import java.lang.reflect.Modifier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngineFactory;
import javax.script.SimpleBindings;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.scijava.Context;
import org.scijava.thread.ThreadService;

public class TextEditorThreadOwnershipTest {

	private Context context;
	private ThreadService threadService;

	@Before
	public void setUp() {
		context = new Context(ThreadService.class);
		threadService = context.getService(ThreadService.class);
	}

	@After
	public void tearDown() {
		context.dispose();
	}

	@Test
	public void testTitleSetterDoesNotSynchronizeOnEditor() throws Exception {
		assertFalse(Modifier.isSynchronized(TextEditor.class.getMethod("setTitle",
			String.class).getModifiers()));
	}

	@Test
	public void testTrackedScriptEngineBindingsAreClearedOnRequest() {
		final TextEditor.ScriptEngineTracker tracker =
			new TextEditor.ScriptEngineTracker();
		final TestScriptEngine engine = new TestScriptEngine();
		final Object payload = new Object();
		engine.put("payload", payload);
		tracker.add(engine);

		tracker.clearBindings(null);

		assertNull(engine.get("payload"));
	}

	@Test
	public void testReleasingScriptEngineTrackerPreservesBindings() {
		final TextEditor.ScriptEngineTracker tracker =
			new TextEditor.ScriptEngineTracker();
		final TestScriptEngine engine = new TestScriptEngine();
		final Object payload = new Object();
		engine.put("payload", payload);
		tracker.add(engine);

		tracker.release();

		assertSame(payload, engine.get("payload"));
	}

	@Test
	public void testOrdinaryChildThreadBelongsToRun() {
		final ThreadGroup runGroup = new ThreadGroup("script-run");
		final Thread child = new Thread(runGroup, () -> {});
		child.setDaemon(true);

		assertTrue(TextEditor.isScriptOwnedThread(child, runGroup, threadService));
	}

	@Test
	public void testIdleThreadServiceWorkerDoesNotBelongToRun() throws Exception {
		final ThreadGroup runGroup = new ThreadGroup("script-run");
		final AtomicReference<Thread> worker = new AtomicReference<>();
		final AtomicReference<Future<?>> task = new AtomicReference<>();
		final Thread creator = new Thread(runGroup, () -> task.set(threadService.run(
			() -> worker.set(Thread.currentThread()))));
		creator.start();
		creator.join(5000);
		task.get().get(5, TimeUnit.SECONDS);
		awaitIdlePoolWorker(worker.get());

		assertFalse("worker factory thread should finish", creator.isAlive());
		assertNotNull(worker.get());
		assertSame(runGroup, worker.get().getThreadGroup());
		assertNull(threadService.getParent(worker.get()));
		assertFalse(TextEditor.isScriptOwnedThread(worker.get(), runGroup,
			threadService));
	}

	@Test
	public void testActiveThreadServiceWorkBelongsToRun() throws Exception {
		final ThreadGroup runGroup = new ThreadGroup("script-run");
		final CountDownLatch taskStarted = new CountDownLatch(1);
		final CountDownLatch releaseTask = new CountDownLatch(1);
		final CountDownLatch releaseScriptThread = new CountDownLatch(1);
		final AtomicReference<Thread> worker = new AtomicReference<>();
		final AtomicReference<Future<?>> task = new AtomicReference<>();
		final Thread scriptThread = new Thread(runGroup, () -> {
			task.set(threadService.run(() -> {
				worker.set(Thread.currentThread());
				taskStarted.countDown();
				await(releaseTask);
			}));
			await(releaseScriptThread);
		});

		scriptThread.start();
		try {
			assertTrue("service task should start", taskStarted.await(5,
				TimeUnit.SECONDS));
			assertNotNull(worker.get());
			assertTrue(TextEditor.isScriptOwnedThread(worker.get(), runGroup,
				threadService));

			releaseTask.countDown();
			task.get().get(5, TimeUnit.SECONDS);
			awaitIdlePoolWorker(worker.get());
			assertFalse(TextEditor.isScriptOwnedThread(worker.get(), runGroup,
				threadService));
		}
		finally {
			releaseTask.countDown();
			releaseScriptThread.countDown();
			scriptThread.join(5000);
		}
		assertFalse("script parent should finish", scriptThread.isAlive());
	}

	@Test
	public void testActiveThreadServiceFactoryThreadBelongsToRun() throws Exception {
		final ThreadGroup runGroup = new ThreadGroup("script-run");
		final CountDownLatch taskStarted = new CountDownLatch(1);
		final CountDownLatch releaseTask = new CountDownLatch(1);
		final CountDownLatch releaseScriptThread = new CountDownLatch(1);
		final AtomicReference<Thread> worker = new AtomicReference<>();
		final Thread scriptThread = new Thread(runGroup, () -> {
			final Thread serviceThread = threadService.newThread(() -> {
				worker.set(Thread.currentThread());
				taskStarted.countDown();
				await(releaseTask);
			});
			serviceThread.start();
			await(releaseScriptThread);
		});

		scriptThread.start();
		try {
			assertTrue("service thread should start", taskStarted.await(5,
				TimeUnit.SECONDS));
			assertNotNull(worker.get());
			assertNull(threadService.getParent(worker.get()));
			assertTrue(TextEditor.isScriptOwnedThread(worker.get(), runGroup,
				threadService));
		}
		finally {
			releaseTask.countDown();
			releaseScriptThread.countDown();
			scriptThread.join(5000);
			if (worker.get() != null) worker.get().join(5000);
		}
		assertFalse("script parent should finish", scriptThread.isAlive());
		assertFalse("service thread should finish", worker.get().isAlive());
	}

	private static void await(final CountDownLatch latch) {
		try {
			latch.await();
		}
		catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static final class TestScriptEngine extends AbstractScriptEngine {

		@Override
		public Object eval(final String script, final ScriptContext context) {
			return null;
		}

		@Override
		public Object eval(final Reader reader, final ScriptContext context) {
			return null;
		}

		@Override
		public Bindings createBindings() {
			return new SimpleBindings();
		}

		@Override
		public ScriptEngineFactory getFactory() {
			return null;
		}
	}

	private static void awaitIdlePoolWorker(final Thread worker) throws Exception {
		final long deadline = System.currentTimeMillis() + 5000;
		while (!isIdlePoolWorker(worker) && System.currentTimeMillis() < deadline) {
			Thread.sleep(10);
		}
		assertTrue("worker should be idle in ThreadPoolExecutor.getTask",
			isIdlePoolWorker(worker));
	}

	private static boolean isIdlePoolWorker(final Thread thread) {
		if (thread == null || thread.getState() != Thread.State.TIMED_WAITING) return false;
		final StackTraceElement[] stack = thread.getStackTrace();
		for (int i = 0; i + 1 < stack.length; i++) {
			if (stack[i].getClassName().equals(
				"java.util.concurrent.ThreadPoolExecutor") && stack[i].getMethodName()
					.equals("getTask") && stack[i + 1].getClassName().equals(
						"java.util.concurrent.ThreadPoolExecutor") && stack[i + 1]
							.getMethodName().equals("runWorker")) return true;
		}
		return false;
	}
}
