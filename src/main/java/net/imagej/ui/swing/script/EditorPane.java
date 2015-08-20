/*
 * #%L
 * ImageJ software for multidimensional image processing and analysis.
 * %%
 * Copyright (C) 2009 - 2015 Board of Regents of the University of
 * Wisconsin-Madison, Broad Institute of MIT and Harvard, and Max Planck
 * Institute of Molecular Cell Biology and Genetics.
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

package net.imagej.ui.swing.script;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Collection;
import java.util.List;

import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.ToolTipManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;

import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Style;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.GutterIconInfo;
import org.fife.ui.rtextarea.IconGroup;
import org.fife.ui.rtextarea.RTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.fife.ui.rtextarea.RecordableTextAction;
import org.scijava.plugin.Parameter;
import org.scijava.prefs.PrefService;
import org.scijava.script.ScriptHeaderService;
import org.scijava.script.ScriptLanguage;
import org.scijava.script.ScriptService;
import org.scijava.util.FileUtils;

/**
 * Main text editing component of the script editor, based on
 * {@link RSyntaxTextArea}.
 *
 * @author Johannes Schindelin
 * @author Jonathan Hale
 */
public class EditorPane extends RSyntaxTextArea implements DocumentListener {

	private String fallBackBaseName;
	private File curFile;
	private File gitDirectory;
	private long fileLastModified;
	private ScriptLanguage currentLanguage;
	private Gutter gutter;
	private IconGroup iconGroup;
	private int modifyCount;

	private boolean undoInProgress;
	private boolean redoInProgress;

	@Parameter
	private ScriptService scriptService;
	@Parameter
	private ScriptHeaderService scriptHeaderService;
	@Parameter
	private PrefService prefService;

	/**
	 * Constructor.
	 */
	public EditorPane() {
		setLineWrap(false);
		setTabSize(8);

		getActionMap()
			.put(DefaultEditorKit.nextWordAction, wordMovement(+1, false));
		getActionMap().put(DefaultEditorKit.selectionNextWordAction,
			wordMovement(+1, true));
		getActionMap().put(DefaultEditorKit.previousWordAction,
			wordMovement(-1, false));
		getActionMap().put(DefaultEditorKit.selectionPreviousWordAction,
			wordMovement(-1, true));
		ToolTipManager.sharedInstance().registerComponent(this);
		getDocument().addDocumentListener(this);
	}

	@Override
	public void setTabSize(final int width) {
		if (getTabSize() != width) super.setTabSize(width);
	}

	/**
	 * Add this {@link EditorPane} with scrollbars to a container.
	 *
	 * @param container the container to add this editor pane to.
	 */
	public void embedWithScrollbars(final Container container) {
		container.add(wrappedInScrollbars());
	}

	/**
	 * @return this EditorPane wrapped in a {@link RTextScrollPane}.
	 */
	public RTextScrollPane wrappedInScrollbars() {
		final RTextScrollPane sp = new RTextScrollPane(this);
		sp.setPreferredSize(new Dimension(600, 350));
		sp.setIconRowHeaderEnabled(true);

		gutter = sp.getGutter();
		iconGroup = new IconGroup("bullets", "images/", null, "png", null);
		gutter.setBookmarkIcon(iconGroup.getIcon("var"));
		gutter.setBookmarkingEnabled(true);

		return sp;
	}

	/**
	 * TODO
	 *
	 * @param direction
	 * @param select
	 * @return
	 */
	RecordableTextAction wordMovement(final int direction, final boolean select) {
		final String id = "WORD_MOVEMENT_" + select + direction;
		return new RecordableTextAction(id) {

			@Override
			public void actionPerformedImpl(final ActionEvent e,
				final RTextArea textArea)
			{
				int pos = textArea.getCaretPosition();
				final int end = direction < 0 ? 0 : textArea.getDocument().getLength();
				while (pos != end && !isWordChar(textArea, pos))
					pos += direction;
				while (pos != end && isWordChar(textArea, pos))
					pos += direction;
				if (select) textArea.moveCaretPosition(pos);
				else textArea.setCaretPosition(pos);
			}

			@Override
			public String getMacroID() {
				return id;
			}

			boolean isWordChar(final RTextArea textArea, final int pos) {
				try {
					final char c =
						textArea.getText(pos + (direction < 0 ? -1 : 0), 1).charAt(0);
					return c > 0x7f || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') ||
						(c >= '0' && c <= '9') || c == '_';
				}
				catch (final BadLocationException e) {
					return false;
				}
			}
		};
	}

	@Override
	public void undoLastAction() {
		undoInProgress = true;
		super.undoLastAction();
		undoInProgress = false;
	}

	@Override
	public void redoLastAction() {
		redoInProgress = true;
		super.redoLastAction();
		redoInProgress = false;
	}

	/**
	 * @return <code>true</code> if the file in this {@link EditorPane} was
	 *         changes since it was last saved.
	 */
	public boolean fileChanged() {
		return modifyCount != 0;
	}

	@Override
	public void insertUpdate(final DocumentEvent e) {
		modified();
	}

	@Override
	public void removeUpdate(final DocumentEvent e) {
		modified();
	}

	// triggered only by syntax highlighting
	@Override
	public void changedUpdate(final DocumentEvent e) {}

	/**
	 * Set the title according to whether the file was modified or not.
	 */
	protected void modified() {
		if (undoInProgress) {
			modifyCount--;
		}
		else if (redoInProgress || modifyCount >= 0) {
			modifyCount++;
		}
		else {
			// not possible to get back to clean state
			modifyCount = Integer.MIN_VALUE;
		}
	}

	/**
	 * @return <code>true</code> if the file in this {@link EditorPane} is an
	 *         unsaved new file which has not been edited yet.
	 */
	public boolean isNew() {
		return !fileChanged() && curFile == null && fallBackBaseName == null &&
			getDocument().getLength() == 0;
	}

	/**
	 * @return true if the file in this {@link EditorPane} was changed ouside of
	 *         this {@link EditorPane} since it was openend.
	 */
	public boolean wasChangedOutside() {
		return curFile != null && curFile.exists() &&
			curFile.lastModified() != fileLastModified;
	}

	/**
	 * Write the contents of this {@link EditorPane} to given file.
	 *
	 * @param file File to write the contents of this editor to.
	 * @throws IOException
	 */
	public void write(final File file) throws IOException {
		final BufferedWriter outFile =
			new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file),
				"UTF-8"));
		outFile.write(getText());
		outFile.close();
		modifyCount = 0;
		fileLastModified = file.lastModified();
	}

	/**
	 * Load editor contents from given file.
	 * 
	 * @param file file to load.
	 * @throws IOException
	 */
	public void open(final File file) throws IOException {
		final File oldFile = curFile;
		curFile = null;
		if (file == null) setText("");
		else {
			int line = 0;
			try {
				if (file.getCanonicalPath().equals(oldFile.getCanonicalPath())) line =
					getCaretLineNumber();
			}
			catch (final Exception e) { /* ignore */}
			if (!file.exists()) {
				modifyCount = Integer.MIN_VALUE;
				setFileName(file);
				return;
			}
			final StringBuffer string = new StringBuffer();
			final BufferedReader reader =
				new BufferedReader(new InputStreamReader(new FileInputStream(file),
					"UTF-8"));
			final char[] buffer = new char[16384];
			for (;;) {
				final int count = reader.read(buffer);
				if (count < 0) break;
				string.append(buffer, 0, count);
			}
			reader.close();
			setText(string.toString());
			curFile = file;
			if (line > getLineCount()) line = getLineCount() - 1;
			try {
				setCaretPosition(getLineStartOffset(line));
			}
			catch (final BadLocationException e) { /* ignore */}
		}
		discardAllEdits();
		modifyCount = 0;
		fileLastModified = file == null || !file.exists() ? 0 : file.lastModified();
	}

	/**
	 * Set the name to use for new files. The file extension for the current
	 * script language is added automatically.
	 * 
	 * @param baseName the fallback base name.
	 */
	public void setFileName(final String baseName) {
		fallBackBaseName = baseName;
		if (currentLanguage == null) {
			return;
		}
		for (String extension : currentLanguage.getExtensions()) {
			extension = "." + extension;
			if (baseName.endsWith(extension)) {
				fallBackBaseName =
					fallBackBaseName.substring(0, fallBackBaseName.length() -
						extension.length());
				break;
			}
		}

		if (currentLanguage.getLanguageName().equals("Java")) {
			new TokenFunctions(this).setClassName(baseName);
		}
	}

	/**
	 * TODO
	 * 
	 * @param file
	 */
	public void setFileName(final File file) {
		curFile = file;

		if (file != null) {
			setLanguageByFileName(file.getName());
			fallBackBaseName = null;
		}
		fileLastModified = file == null || !file.exists() ? 0 : file.lastModified();
	}

	/**
	 * Get the directory of the git repository for the currently open file.
	 * 
	 * @return the git repository directoy, or <code>null</code> is there is no
	 *         such thing.
	 */
	public File getGitDirectory() {
		return gitDirectory;
	}

	/**
	 * Set this {@link EditorPane}s git directory.
	 * 
	 * @param dir directory to set the git directory to.
	 */
	public void setGitDirectory(File dir) {
		gitDirectory = dir;
	}

	/**
	 * @return name of the currently open file.
	 */
	protected String getFileName() {
		if (curFile != null) return curFile.getName();
		String extension = "";
		if (currentLanguage != null) {
			final List<String> extensions = currentLanguage.getExtensions();
			if (extensions.size() > 0) {
				extension = "." + extensions.get(0);
			}
			if (currentLanguage.getLanguageName().equals("Java")) {
				final String name = new TokenFunctions(this).getClassName();
				if (name != null) {
					return name + extension;
				}
			}
		}
		return (fallBackBaseName == null ? "New_" : fallBackBaseName) + extension;
	}

	/**
	 * Get the language by filename extension.
	 * 
	 * @param name the filename.
	 * @see #setLanguage(ScriptLanguage)
	 * @see #setLanguage(ScriptLanguage, boolean)
	 */
	protected void setLanguageByFileName(final String name) {
		setLanguage(scriptService.getLanguageByExtension(FileUtils
			.getExtension(name)));
	}

	/**
	 * Set the language of this {@link EditorPane}.
	 * 
	 * @param language {@link ScriptLanguage} to set the editors language to.
	 * @see #setLanguageByFileName(String)
	 * @see #setLanguage(ScriptLanguage, boolean)
	 */
	protected void setLanguage(final ScriptLanguage language) {
		setLanguage(language, false);
	}

	/**
	 * Set the language of this {@link EditorPane}, optionally adding a header.
	 * TODO: What is this header?
	 * 
	 * @param language {@link ScriptLanguage} to set the editors language to.
	 * @param addHeader set to <code>true</code> to add a header.
	 * @see #setLanguageByFileName(String)
	 * @see #setLanguage(ScriptLanguage)
	 */
	protected void setLanguage(final ScriptLanguage language,
		final boolean addHeader)
	{
		String languageName;
		String defaultExtension;
		if (language == null) {
			languageName = "None";
			defaultExtension = ".txt";
		}
		else {
			languageName = language.getLanguageName();
			final List<String> extensions = language.getExtensions();
			defaultExtension =
				extensions.size() == 0 ? "" : ("." + extensions.get(0));
		}
		if (fallBackBaseName != null && fallBackBaseName.endsWith(".txt")) fallBackBaseName =
			fallBackBaseName.substring(0, fallBackBaseName.length() - 4);
		if (curFile != null) {
			String name = curFile.getName();
			final String ext = "." + FileUtils.getExtension(name);
			if (!defaultExtension.equals(ext)) {
				name = name.substring(0, name.length() - ext.length());
				curFile = new File(curFile.getParentFile(), name + defaultExtension);
				modifyCount = Integer.MIN_VALUE;
			}
		}
		String header = null;

		if (addHeader && currentLanguage == null) {
			header = scriptHeaderService.getHeader(language);
		}
		currentLanguage = language;

		final String styleName =
			"text/" + languageName.toLowerCase().replace(' ', '-');
		setSyntaxEditingStyle(styleName);

		// Add header text
		if (header != null) {
			setText(header += getText());
		}
	}

	/**
	 * Get file currently open in this {@link EditorPane}.
	 *
	 * @return the file.
	 */
	public File getFile() {
		return curFile;
	}

	/**
	 * Get {@link ScriptLanguage} used for this {@link EditorPane}.
	 *
	 * @return current {@link ScriptLanguage}.
	 */
	public ScriptLanguage getCurrentLanguage() {
		return currentLanguage;
	}

	/**
	 * @return font size of this editor.
	 */
	public float getFontSize() {
		return getFont().getSize2D();
	}

	/**
	 * Set the font size for this editor.
	 * 
	 * @param size the new font size.
	 */
	public void setFontSize(final float size) {
		increaseFontSize(size / getFontSize());
	}

	/**
	 * Increase font size of this editor by a given factor.
	 * 
	 * @param factor Factor to increase font size.
	 */
	public void increaseFontSize(final float factor) {
		if (factor == 1) return;
		final SyntaxScheme scheme = getSyntaxScheme();
		for (int i = 0; i < scheme.getStyleCount(); i++) {
			final Style style = scheme.getStyle(i);
			if (style == null || style.font == null) continue;
			final float size = Math.max(5, style.font.getSize2D() * factor);
			style.font = style.font.deriveFont(size);
		}
		final Font font = getFont();
		final float size = Math.max(5, font.getSize2D() * factor);
		setFont(font.deriveFont(size));
		setSyntaxScheme(scheme);
		Component parent = getParent();
		if (parent instanceof JViewport) {
			parent = parent.getParent();
			if (parent instanceof JScrollPane) {
				parent.repaint();
			}
		}
		parent.repaint();
	}

	/**
	 * @return the underlying {@link RSyntaxDocument}.
	 */
	protected RSyntaxDocument getRSyntaxDocument() {
		return (RSyntaxDocument) getDocument();
	}

	/**
	 * Add/remove bookmark for line containing the cursor/caret.
	 */
	public void toggleBookmark() {
		toggleBookmark(getCaretLineNumber());
	}

	/**
	 * Add/remove bookmark for a specific line.
	 * 
	 * @param line line to toggle the bookmark on.
	 */
	public void toggleBookmark(final int line) {
		if (gutter != null) {
			try {
				gutter.toggleBookmark(line);
			}
			catch (final BadLocationException e) {
				/* ignore */
				System.out.println("Cannot toggle bookmark at this location.");
			}
		}
	}

	/**
	 * Add this editors bookmarks to the specified collection.
	 *
	 * @param tab Tab index to set for added bookmarks.
	 * @param result Collection to add the bookmarks to.
	 */
	public void getBookmarks(final TextEditorTab tab,
		final Collection<Bookmark> result)
	{
		if (gutter == null) return;

		for (final GutterIconInfo info : gutter.getBookmarks())
			result.add(new Bookmark(tab, info));
	}

	/**
	 * Adapted from ij.plugin.frame.Editor. Replaces unquoted invalid ascii
	 * characters with spaces. Characters are considered invalid if outside the
	 * range of [32, 127] (except newlines and vertical tabs).
	 *
	 * @return number of characters replaced.
	 */
	public int zapGremlins() {
		final char[] chars = getText().toCharArray();
		int count = 0; // number of "gremlins" zapped
		boolean inQuotes = false;
		char quoteChar = 0;

		for (int i = 0; i < chars.length; ++i) {
			final char c = chars[i];

			if (!inQuotes) {
				if (c == '"' || c == '\'') {
					inQuotes = true;
					quoteChar = c;
				}
				else if (c != '\n' && c != '\t' && (c < 32 || c > 127)) {
					count++;
					chars[i] = ' ';
				}
			}
			else if (c == quoteChar || c == '\n') {
				inQuotes = false;
			}
		}
		if (count > 0) {
			beginAtomicEdit();
			try {
				setText(new String(chars));
			}
			catch (final Throwable t) {
				t.printStackTrace();
			}
			finally {
				endAtomicEdit();
			}
		}
		return count;
	}

	@Override
	public void convertTabsToSpaces() {
		beginAtomicEdit();
		try {
			super.convertTabsToSpaces();
		}
		catch (final Throwable t) {
			t.printStackTrace();
		}
		finally {
			endAtomicEdit();
		}
	}

	@Override
	public void convertSpacesToTabs() {
		beginAtomicEdit();
		try {
			super.convertSpacesToTabs();
		}
		catch (final Throwable t) {
			t.printStackTrace();
		}
		finally {
			endAtomicEdit();
		}
	}

	// --- Preferences ---
	public static final String FONT_SIZE_PREFS = "script.editor.FontSize";
	public static final String LINE_WRAP_PREFS = "script.editor.WrapLines";
	public static final String TAB_SIZE_PREFS = "script.editor.TabSize";
	public static final String TABS_EMULATED_PREFS = "script.editor.TabsEmulated";

	public static final int DEFAULT_TAB_SIZE = 4;

	/**
	 * Loads the preferences for the Tab and apply them.
	 */
	public void loadPreferences() {
		resetTabSize();
		setFontSize(prefService.getFloat(FONT_SIZE_PREFS, getFontSize()));
		setLineWrap(prefService.getBoolean(LINE_WRAP_PREFS, getLineWrap()));
		setTabsEmulated(prefService.getBoolean(TABS_EMULATED_PREFS,
			getTabsEmulated()));
	}

	/**
	 * Retrieves and saves the preferences to the persistent store
	 */
	public void savePreferences() {
		prefService.put(TAB_SIZE_PREFS, getTabSize());
		prefService.put(FONT_SIZE_PREFS, getFontSize());
		prefService.put(LINE_WRAP_PREFS, getLineWrap());
		prefService.put(TABS_EMULATED_PREFS, getTabsEmulated());
	}

	/**
	 * Reset tab size to current preferences.
	 */
	public void resetTabSize() {
		setTabSize(prefService.getInt(TAB_SIZE_PREFS, DEFAULT_TAB_SIZE));
	}

}
