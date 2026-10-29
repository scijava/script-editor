package org.scijava.ui.swing.script;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.scijava.Context;

public class EditorPaneFileNameTest {

	private Context context;

	@Before
	public void setUp() {
		context = new Context();
	}

	@After
	public void tearDown() {
		context.dispose();
	}

	@Test
	public void testSuppliedLanguageExtensionIsNotDuplicated() {
		final EditorPane editorPane = new EditorPane();
		context.inject(editorPane);

		editorPane.setLanguageByFileName("Macro.java");
		editorPane.setFileName("Macro.java");

		assertEquals("Macro.java", editorPane.getFileName());
	}
}