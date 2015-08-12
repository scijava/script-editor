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

import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.CharArrayWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipException;

import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Position;

import net.imagej.ui.swing.script.commands.ChooseFontSize;
import net.imagej.ui.swing.script.commands.ChooseTabSize;
import net.imagej.ui.swing.script.commands.GitGrep;
import net.imagej.ui.swing.script.commands.KillScript;

import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory;
import org.scijava.Context;
import org.scijava.command.CommandService;
import org.scijava.event.ContextDisposingEvent;
import org.scijava.event.EventHandler;
import org.scijava.io.IOService;
import org.scijava.log.LogService;
import org.scijava.module.ModuleException;
import org.scijava.module.ModuleService;
import org.scijava.platform.PlatformService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.PluginInfo;
import org.scijava.plugin.PluginService;
import org.scijava.plugins.scripting.java.JavaEngine;
import org.scijava.prefs.PrefService;
import org.scijava.script.ScriptHeaderService;
import org.scijava.script.ScriptInfo;
import org.scijava.script.ScriptLanguage;
import org.scijava.script.ScriptModule;
import org.scijava.script.ScriptService;
import org.scijava.ui.CloseConfirmable;
import org.scijava.ui.UIService;
import org.scijava.util.AppUtils;
import org.scijava.util.FileUtils;
import org.scijava.util.MiscUtils;
import org.scijava.widget.FileWidget;

/**
 * A versatile script editor for ImageJ.
 * <p>
 * Based on the powerful SciJava scripting framework and the <a
 * href="http://fifesoft.com/rsyntaxtextarea/">RSyntaxTextArea</a> library, this
 * text editor offers users to script their way through image processing. Thanks
 * to the <a href="https://github.com/scijava/scripting-java">Java backend for
 * SciJava scripting</a>, it is even possible to develop Java plugins in the
 * editor.
 * </p>
 *
 * @author Johannes Schindelin
 * @author Jonathan Hale
 */
@SuppressWarnings("serial")
public class TextEditor extends JFrame implements ActionListener,
	ChangeListener, CloseConfirmable
{

	private static final Set<String> TEMPLATE_PATHS = new HashSet<String>();
	public static final String AUTO_IMPORT_PREFS = "script.editor.AutoImport";
	public static final String WINDOW_HEIGHT = "script.editor.height";
	public static final String WINDOW_WIDTH = "script.editor.width";
	public static final int DEFAULT_WINDOW_WIDTH = 800;
	public static final int DEFAULT_WINDOW_HEIGHT = 600;

	static {
		// Add known script template paths.
		addTemplatePath("script_templates");
		// This path interferes with javadoc generation but is preserved for
		// backwards compatibility
		addTemplatePath("script-templates");
	}

	private static AbstractTokenMakerFactory tokenMakerFactory = null;

	protected JTabbedPane tabbed;
	protected JMenuItem newFile, open, save, saveas, compileAndRun, compile,
			close, undo, redo, cut, copy, paste, find, replace, selectAll, kill,
			gotoLine, makeJar, makeJarWithSource, removeUnusedImports, sortImports,
			removeTrailingWhitespace, findNext, findPrevious, openHelp, addImport,
			clearScreen, nextError, previousError, openHelpWithoutFrames, nextTab,
			previousTab, runSelection, extractSourceJar, toggleBookmark,
			listBookmarks, openSourceForClass, openSourceForMenuItem,
			openMacroFunctions, decreaseFontSize, increaseFontSize, chooseFontSize,
			chooseTabSize, gitGrep, openInGitweb, replaceTabsWithSpaces,
			replaceSpacesWithTabs, toggleWhiteSpaceLabeling, zapGremlins,
			savePreferences;
	protected RecentFilesMenuItem openRecent;
	protected JMenu gitMenu, tabsMenu, fontSizeMenu, tabSizeMenu, toolsMenu,
			runMenu, whiteSpaceMenu;
	protected int tabsMenuTabsStart;
	protected Set<JMenuItem> tabsMenuItems;
	protected FindAndReplaceDialog findDialog;
	protected JCheckBoxMenuItem autoSave, wrapLines, tabsEmulated, autoImport;
	protected JTextArea errorScreen = new JTextArea();

	protected final String templateFolder = "templates/";

	protected int compileStartOffset;
	protected Position compileStartPosition;
	protected ErrorHandler errorHandler;

	protected boolean respectAutoImports;

	@Parameter
	protected Context context;
	@Parameter
	protected LogService log;
	@Parameter
	protected ModuleService moduleService;
	@Parameter
	protected PlatformService platformService;
	@Parameter
	protected IOService ioService;
	@Parameter
	protected CommandService commandService;
	@Parameter
	protected ScriptService scriptService;
	@Parameter
	protected PluginService pluginService;
	@Parameter
	protected ScriptHeaderService scriptHeaderService;
	@Parameter
	private UIService uiService;
	@Parameter
	protected PrefService prefService;

	protected Map<ScriptLanguage, JRadioButtonMenuItem> languageMenuItems;
	protected JRadioButtonMenuItem noneLanguageItem;

	public TextEditor(final Context context) {
		super("Script Editor");
		context.inject(this);
		initializeTokenMakers(pluginService, log);
		loadPreferences();

		// Initialize menu
		final int ctrl = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
		final int shift = ActionEvent.SHIFT_MASK;
		final JMenuBar mbar = new JMenuBar();
		setJMenuBar(mbar);

		final JMenu file = new JMenu("File");
		file.setMnemonic(KeyEvent.VK_F);
		newFile = addToMenu(file, "New", KeyEvent.VK_N, ctrl);
		newFile.setMnemonic(KeyEvent.VK_N);
		open = addToMenu(file, "Open...", KeyEvent.VK_O, ctrl);
		open.setMnemonic(KeyEvent.VK_O);
		openRecent = new RecentFilesMenuItem(prefService, this);
		openRecent.setMnemonic(KeyEvent.VK_R);
		file.add(openRecent);
		save = addToMenu(file, "Save", KeyEvent.VK_S, ctrl);
		save.setMnemonic(KeyEvent.VK_S);
		saveas = addToMenu(file, "Save as...", 0, 0);
		saveas.setMnemonic(KeyEvent.VK_A);
		file.addSeparator();
		makeJar = addToMenu(file, "Export as .jar", 0, 0);
		makeJar.setMnemonic(KeyEvent.VK_E);
		makeJarWithSource = addToMenu(file, "Export as .jar (with source)", 0, 0);
		makeJarWithSource.setMnemonic(KeyEvent.VK_X);
		file.addSeparator();
		close = addToMenu(file, "Close", KeyEvent.VK_W, ctrl);

		mbar.add(file);

		final JMenu edit = new JMenu("Edit");
		edit.setMnemonic(KeyEvent.VK_E);
		undo = addToMenu(edit, "Undo", KeyEvent.VK_Z, ctrl);
		redo = addToMenu(edit, "Redo", KeyEvent.VK_Y, ctrl);
		edit.addSeparator();
		selectAll = addToMenu(edit, "Select All", KeyEvent.VK_A, ctrl);
		cut = addToMenu(edit, "Cut", KeyEvent.VK_X, ctrl);
		copy = addToMenu(edit, "Copy", KeyEvent.VK_C, ctrl);
		paste = addToMenu(edit, "Paste", KeyEvent.VK_V, ctrl);
		edit.addSeparator();
		find = addToMenu(edit, "Find...", KeyEvent.VK_F, ctrl);
		find.setMnemonic(KeyEvent.VK_F);
		findNext = addToMenu(edit, "Find Next", KeyEvent.VK_F3, 0);
		findNext.setMnemonic(KeyEvent.VK_N);
		findPrevious = addToMenu(edit, "Find Previous", KeyEvent.VK_F3, shift);
		findPrevious.setMnemonic(KeyEvent.VK_P);
		replace = addToMenu(edit, "Find and Replace...", KeyEvent.VK_H, ctrl);
		gotoLine = addToMenu(edit, "Goto line...", KeyEvent.VK_G, ctrl);
		gotoLine.setMnemonic(KeyEvent.VK_G);
		toggleBookmark = addToMenu(edit, "Toggle Bookmark", KeyEvent.VK_B, ctrl);
		toggleBookmark.setMnemonic(KeyEvent.VK_B);
		listBookmarks = addToMenu(edit, "List Bookmarks", 0, 0);
		listBookmarks.setMnemonic(KeyEvent.VK_O);
		edit.addSeparator();

		// Font adjustments
		decreaseFontSize =
			addToMenu(edit, "Decrease font size", KeyEvent.VK_MINUS, ctrl);
		decreaseFontSize.setMnemonic(KeyEvent.VK_D);
		increaseFontSize =
			addToMenu(edit, "Increase font size", KeyEvent.VK_PLUS, ctrl);
		increaseFontSize.setMnemonic(KeyEvent.VK_C);

		fontSizeMenu = new JMenu("Font sizes");
		fontSizeMenu.setMnemonic(KeyEvent.VK_Z);
		final boolean[] fontSizeShortcutUsed = new boolean[10];
		final ButtonGroup buttonGroup = new ButtonGroup();
		for (final int size : new int[] { 8, 10, 12, 16, 20, 28, 42 }) {
			final JRadioButtonMenuItem item =
				new JRadioButtonMenuItem("" + size + " pt");
			item.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(final ActionEvent event) {
					getEditorPane().setFontSize(size);
					updateTabAndFontSize(false);
				}
			});
			for (final char c : ("" + size).toCharArray()) {
				final int digit = c - '0';
				if (!fontSizeShortcutUsed[digit]) {
					item.setMnemonic(KeyEvent.VK_0 + digit);
					fontSizeShortcutUsed[digit] = true;
					break;
				}
			}
			buttonGroup.add(item);
			fontSizeMenu.add(item);
		}
		chooseFontSize = new JRadioButtonMenuItem("Other...", false);
		chooseFontSize.setMnemonic(KeyEvent.VK_O);
		chooseFontSize.addActionListener(this);
		buttonGroup.add(chooseFontSize);
		fontSizeMenu.add(chooseFontSize);
		edit.add(fontSizeMenu);

		// Add tab size adjusting menu
		tabSizeMenu = new JMenu("Tab sizes");
		tabSizeMenu.setMnemonic(KeyEvent.VK_T);
		final ButtonGroup bg = new ButtonGroup();
		for (final int size : new int[] { 2, 4, 8 }) {
			final JRadioButtonMenuItem item = new JRadioButtonMenuItem("" + size);
			item.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(final ActionEvent event) {
					getEditorPane().setTabSize(size);
					updateTabAndFontSize(false);
				}
			});
			item.setMnemonic(KeyEvent.VK_0 + (size % 10));
			bg.add(item);
			tabSizeMenu.add(item);
		}
		chooseTabSize = new JRadioButtonMenuItem("Other...", false);
		chooseTabSize.setMnemonic(KeyEvent.VK_O);
		chooseTabSize.addActionListener(this);
		bg.add(chooseTabSize);
		tabSizeMenu.add(chooseTabSize);
		edit.add(tabSizeMenu);

		wrapLines = new JCheckBoxMenuItem("Wrap lines");
		wrapLines.addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(final ChangeEvent e) {
				getEditorPane().setLineWrap(wrapLines.getState());
			}
		});
		edit.add(wrapLines);

		// Add Tab inserts as spaces
		tabsEmulated = new JCheckBoxMenuItem("Tab key inserts spaces");
		tabsEmulated.addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(final ChangeEvent e) {
				getEditorPane().setTabsEmulated(tabsEmulated.getState());
			}
		});
		edit.add(tabsEmulated);

		savePreferences = addToMenu(edit, "Save Preferences", 0, 0);

		edit.addSeparator();

		clearScreen = addToMenu(edit, "Clear output panel", 0, 0);
		clearScreen.setMnemonic(KeyEvent.VK_L);

		zapGremlins = addToMenu(edit, "Zap Gremlins", 0, 0);

		edit.addSeparator();
		addImport = addToMenu(edit, "Add import...", 0, 0);
		addImport.setMnemonic(KeyEvent.VK_I);
		removeUnusedImports = addToMenu(edit, "Remove unused imports", 0, 0);
		removeUnusedImports.setMnemonic(KeyEvent.VK_U);
		sortImports = addToMenu(edit, "Sort imports", 0, 0);
		sortImports.setMnemonic(KeyEvent.VK_S);
		respectAutoImports = prefService.getBoolean(AUTO_IMPORT_PREFS, false);
		autoImport =
			new JCheckBoxMenuItem("Auto-import (deprecated)", respectAutoImports);
		autoImport.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(final ItemEvent e) {
				respectAutoImports = e.getStateChange() == ItemEvent.SELECTED;
				prefService.put(AUTO_IMPORT_PREFS, respectAutoImports);
			}
		});
		edit.add(autoImport);
		mbar.add(edit);

		whiteSpaceMenu = new JMenu("Whitespace");
		whiteSpaceMenu.setMnemonic(KeyEvent.VK_W);
		removeTrailingWhitespace =
			addToMenu(whiteSpaceMenu, "Remove trailing whitespace", 0, 0);
		removeTrailingWhitespace.setMnemonic(KeyEvent.VK_W);
		replaceTabsWithSpaces =
			addToMenu(whiteSpaceMenu, "Replace tabs with spaces", 0, 0);
		replaceTabsWithSpaces.setMnemonic(KeyEvent.VK_S);
		replaceSpacesWithTabs =
			addToMenu(whiteSpaceMenu, "Replace spaces with tabs", 0, 0);
		replaceSpacesWithTabs.setMnemonic(KeyEvent.VK_T);
		toggleWhiteSpaceLabeling = new JRadioButtonMenuItem("Label whitespace");
		toggleWhiteSpaceLabeling.setMnemonic(KeyEvent.VK_L);
		toggleWhiteSpaceLabeling.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				getTextArea().setWhitespaceVisible(
					toggleWhiteSpaceLabeling.isSelected());
			}
		});
		whiteSpaceMenu.add(toggleWhiteSpaceLabeling);

		edit.add(whiteSpaceMenu);

		languageMenuItems =
			new LinkedHashMap<ScriptLanguage, JRadioButtonMenuItem>();
		final Set<Integer> usedShortcuts = new HashSet<Integer>();
		final JMenu languages = new JMenu("Language");
		languages.setMnemonic(KeyEvent.VK_L);
		final ButtonGroup group = new ButtonGroup();

		// get list of languages, and sort them by name
		final ArrayList<ScriptLanguage> list =
			new ArrayList<ScriptLanguage>(scriptService.getLanguages());
		Collections.sort(list, new Comparator<ScriptLanguage>() {

			@Override
			public int compare(final ScriptLanguage l1, final ScriptLanguage l2) {
				final String name1 = l1.getLanguageName();
				final String name2 = l2.getLanguageName();
				return MiscUtils.compare(name1, name2);
			}
		});
		list.add(null);

		final Map<String, ScriptLanguage> languageMap =
			new HashMap<String, ScriptLanguage>();
		for (final ScriptLanguage language : list) {
			final String name =
				language == null ? "None" : language.getLanguageName();
			languageMap.put(name, language);

			final JRadioButtonMenuItem item = new JRadioButtonMenuItem(name);
			if (language == null) {
				noneLanguageItem = item;
			}
			else {
				languageMenuItems.put(language, item);
			}

			int shortcut = -1;
			for (final char ch : name.toCharArray()) {
				final int keyCode = KeyStroke.getKeyStroke(ch, 0).getKeyCode();
				if (usedShortcuts.contains(keyCode)) continue;
				shortcut = keyCode;
				usedShortcuts.add(shortcut);
				break;
			}
			if (shortcut > 0) item.setMnemonic(shortcut);
			item.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(final ActionEvent e) {
					setLanguage(language, true);
				}
			});

			group.add(item);
			languages.add(item);
		}
		noneLanguageItem.setSelected(true);
		mbar.add(languages);

		final JMenu templates = new JMenu("Templates");
		templates.setMnemonic(KeyEvent.VK_T);
		addTemplates(templates, languageMap);
		mbar.add(templates);

		runMenu = new JMenu("Run");
		runMenu.setMnemonic(KeyEvent.VK_R);

		compileAndRun = addToMenu(runMenu, "Compile and Run", KeyEvent.VK_R, ctrl);
		compileAndRun.setMnemonic(KeyEvent.VK_R);

		runSelection =
			addToMenu(runMenu, "Run selected code", KeyEvent.VK_R, ctrl | shift);
		runSelection.setMnemonic(KeyEvent.VK_S);

		compile = addToMenu(runMenu, "Compile", KeyEvent.VK_C, ctrl | shift);
		compile.setMnemonic(KeyEvent.VK_C);
		autoSave = new JCheckBoxMenuItem("Auto-save before compiling");
		runMenu.add(autoSave);

		runMenu.addSeparator();
		nextError = addToMenu(runMenu, "Next Error", KeyEvent.VK_F4, 0);
		nextError.setMnemonic(KeyEvent.VK_N);
		previousError = addToMenu(runMenu, "Previous Error", KeyEvent.VK_F4, shift);
		previousError.setMnemonic(KeyEvent.VK_P);

		runMenu.addSeparator();

		kill = addToMenu(runMenu, "Kill running script...", 0, 0);
		kill.setMnemonic(KeyEvent.VK_K);
		kill.setEnabled(false);

		mbar.add(runMenu);

		toolsMenu = new JMenu("Tools");
		toolsMenu.setMnemonic(KeyEvent.VK_O);
		openHelpWithoutFrames =
			addToMenu(toolsMenu, "Open Help for Class...", 0, 0);
		openHelpWithoutFrames.setMnemonic(KeyEvent.VK_O);
		openHelp =
			addToMenu(toolsMenu, "Open Help for Class (with frames)...", 0, 0);
		openHelp.setMnemonic(KeyEvent.VK_P);
		openMacroFunctions =
			addToMenu(toolsMenu, "Open Help on Macro Functions...", 0, 0);
		openMacroFunctions.setMnemonic(KeyEvent.VK_H);
		extractSourceJar = addToMenu(toolsMenu, "Extract source .jar...", 0, 0);
		extractSourceJar.setMnemonic(KeyEvent.VK_E);
		openSourceForClass =
			addToMenu(toolsMenu, "Open .java file for class...", 0, 0);
		openSourceForClass.setMnemonic(KeyEvent.VK_J);
		openSourceForMenuItem =
			addToMenu(toolsMenu, "Open .java file for menu item...", 0, 0);
		openSourceForMenuItem.setMnemonic(KeyEvent.VK_M);
		mbar.add(toolsMenu);

		gitMenu = new JMenu("Git");
		gitMenu.setMnemonic(KeyEvent.VK_G);
		/*
		showDiff = addToMenu(gitMenu,
			"Show diff...", 0, 0);
		showDiff.setMnemonic(KeyEvent.VK_D);
		commit = addToMenu(gitMenu,
			"Commit...", 0, 0);
		commit.setMnemonic(KeyEvent.VK_C);
		*/
		gitGrep = addToMenu(gitMenu, "Grep...", 0, 0);
		gitGrep.setMnemonic(KeyEvent.VK_G);
		openInGitweb = addToMenu(gitMenu, "Open in gitweb", 0, 0);
		openInGitweb.setMnemonic(KeyEvent.VK_W);
		mbar.add(gitMenu);

		tabsMenu = new JMenu("Tabs");
		tabsMenu.setMnemonic(KeyEvent.VK_A);
		nextTab = addToMenu(tabsMenu, "Next Tab", KeyEvent.VK_PAGE_DOWN, ctrl);
		nextTab.setMnemonic(KeyEvent.VK_N);
		previousTab =
			addToMenu(tabsMenu, "Previous Tab", KeyEvent.VK_PAGE_UP, ctrl);
		previousTab.setMnemonic(KeyEvent.VK_P);
		tabsMenu.addSeparator();
		tabsMenuTabsStart = tabsMenu.getItemCount();
		tabsMenuItems = new HashSet<JMenuItem>();
		mbar.add(tabsMenu);

		// Add the editor and output area
		tabbed = new JTabbedPane();
		tabbed.addChangeListener(this);
		open(null); // make sure the editor pane is added

		tabbed.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		getContentPane().setLayout(
			new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));
		getContentPane().add(tabbed);

		// for Eclipse and MS Visual Studio lovers
		addAccelerator(compileAndRun, KeyEvent.VK_F11, 0, true);
		addAccelerator(compileAndRun, KeyEvent.VK_F5, 0, true);
		addAccelerator(nextTab, KeyEvent.VK_PAGE_DOWN, ctrl, true);
		addAccelerator(previousTab, KeyEvent.VK_PAGE_UP, ctrl, true);

		addAccelerator(increaseFontSize, KeyEvent.VK_EQUALS, ctrl | shift, true);

		// make sure that the window is not closed by accident
		addWindowListener(new WindowAdapter() {

			@Override
			public void windowClosing(final WindowEvent e) {
				if (!confirmClose()) return;
				dispose();
			}
		});

		addWindowFocusListener(new WindowAdapter() {

			@Override
			public void windowGainedFocus(final WindowEvent e) {
				final EditorPane editorPane = getEditorPane();
				editorPane.checkForOutsideChanges();
			}
		});

		final Font font = new Font("Courier", Font.PLAIN, 12);
		errorScreen.setFont(font);
		errorScreen.setEditable(false);
		errorScreen.setLineWrap(true);

		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

		try {
			if (SwingUtilities.isEventDispatchThread()) {
				pack();
			}
			else {
				SwingUtilities.invokeAndWait(new Runnable() {

					@Override
					public void run() {
						pack();
					}
				});
			}
		}
		catch (final Exception ie) {
			/* ignore */
		}
		findDialog = new FindAndReplaceDialog(this);

		// Save the size of the window in the preferences
		addComponentListener(new ComponentAdapter() {

			@Override
			public void componentResized(final ComponentEvent e) {
				saveWindowSizeToPrefs();
			}
		});

		setLocationRelativeTo(null); // center on screen

		open(null);

		final EditorPane editorPane = getEditorPane();
		editorPane.requestFocus();
	}

	private synchronized static void initializeTokenMakers(
		final PluginService pluginService, final LogService log)
	{
		if (tokenMakerFactory != null) return;
		tokenMakerFactory =
			(AbstractTokenMakerFactory) TokenMakerFactory.getDefaultInstance();
		for (final PluginInfo<SyntaxHighlighter> info : pluginService
			.getPluginsOfType(SyntaxHighlighter.class))
			try {
				tokenMakerFactory.putMapping("text/" + info.getLabel(), info
					.getClassName());
			}
			catch (final Throwable t) {
				log.warn("Could not register " + info.getLabel(), t);
			}
	}

	/**
	 * Adds a script template path that will be scanned by future TextEditor
	 * instances.
	 *
	 * @param path Resource path to scan for scripts.
	 */
	public static void addTemplatePath(final String path) {
		TEMPLATE_PATHS.add(path);
	}

	@SuppressWarnings("unused")
	@EventHandler
	private void onEvent(final ContextDisposingEvent e) {
		if (isDisplayable()) dispose();
	}

	/**
	 * Loads the preferences for the JFrame from file
	 */
	public void loadPreferences() {
		final Dimension dim = getSize();

		// If a dimension is 0 then use the default dimension size
		if (0 == dim.width) {
			dim.width = DEFAULT_WINDOW_WIDTH;
		}
		if (0 == dim.height) {
			dim.height = DEFAULT_WINDOW_HEIGHT;
		}

		setPreferredSize(new Dimension(prefService.getInt(WINDOW_WIDTH, dim.width),
			prefService.getInt(WINDOW_HEIGHT, dim.height)));
	}

	/**
	 * Saves the window size to preferences.
	 * <p>
	 * Separated from savePreferences because we always want to save the window
	 * size when it's resized, however, we don't want to automatically save the
	 * font, tab size, etc. without the user pressing "Save Preferences"
	 * </p>
	 */
	public void saveWindowSizeToPrefs() {
		final Dimension dim = getSize();
		prefService.put(WINDOW_HEIGHT, dim.height);
		prefService.put(WINDOW_WIDTH, dim.width);
	}

	final public RSyntaxTextArea getTextArea() {
		return getEditorPane();
	}

	/**
	 * Get the currently selected tab.
	 *
	 * @return The currently selected tab. Never null.
	 */
	public Tab getTab() {
		final int index = tabbed.getSelectedIndex();
		if (index < 0) {
			// should not happen, but safety first.
			if (tabbed.getTabCount() == 0) {
				// should not happen either, but, again, safety first.
				createNewDocument();
			}

			tabbed.setSelectedIndex(0);
		}
		return (Tab) tabbed.getComponentAt(index);
	}

	/**
	 * Get tab at provided index.
	 *
	 * @param index the index of the tab.
	 * @return the {@link Tab} at given index or <code>null</code>.
	 */
	public Tab getTab(final int index) {
		return (Tab) tabbed.getComponentAt(index);
	}

	/**
	 * Return the {@link EditorPane} of the currently selected {@link Tab}.
	 *
	 * @return the current {@link EditorPane}. Never <code>null</code>.
	 */
	public EditorPane getEditorPane() {
		return getTab().editorPane;
	}

	public ScriptLanguage getCurrentLanguage() {
		return getEditorPane().getCurrentLanguage();
	}

	public JMenuItem addToMenu(final JMenu menu, final String menuEntry,
		final int key, final int modifiers)
	{
		final JMenuItem item = new JMenuItem(menuEntry);
		menu.add(item);
		if (key != 0) item.setAccelerator(KeyStroke.getKeyStroke(key, modifiers));
		item.addActionListener(this);
		return item;
	}

	protected static class AcceleratorTriplet {

		JMenuItem component;
		int key, modifiers;
	}

	protected List<AcceleratorTriplet> defaultAccelerators =
		new ArrayList<AcceleratorTriplet>();

	public void addAccelerator(final JMenuItem component, final int key,
		final int modifiers)
	{
		addAccelerator(component, key, modifiers, false);
	}

	public void addAccelerator(final JMenuItem component, final int key,
		final int modifiers, final boolean record)
	{
		if (record) {
			final AcceleratorTriplet triplet = new AcceleratorTriplet();
			triplet.component = component;
			triplet.key = key;
			triplet.modifiers = modifiers;
			defaultAccelerators.add(triplet);
		}

		final RSyntaxTextArea textArea = getTextArea();
		if (textArea != null) addAccelerator(textArea, component, key, modifiers);
	}

	public void addAccelerator(final RSyntaxTextArea textArea,
		final JMenuItem component, final int key, final int modifiers)
	{
		textArea.getInputMap().put(KeyStroke.getKeyStroke(key, modifiers),
			component);
		textArea.getActionMap().put(component, new AbstractAction() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				if (!component.isEnabled()) return;
				final ActionEvent event = new ActionEvent(component, 0, "Accelerator");
				TextEditor.this.actionPerformed(event);
			}
		});
	}

	public void addDefaultAccelerators(final RSyntaxTextArea textArea) {
		for (final AcceleratorTriplet triplet : defaultAccelerators)
			addAccelerator(textArea, triplet.component, triplet.key,
				triplet.modifiers);
	}

	protected JMenu getMenu(final JMenu root, final String menuItemPath,
		final boolean createIfNecessary)
	{
		final int gt = menuItemPath.indexOf('>');
		if (gt < 0) return root;

		final String menuLabel = menuItemPath.substring(0, gt);
		final String rest = menuItemPath.substring(gt + 1);
		for (int i = 0; i < root.getItemCount(); i++) {
			final JMenuItem item = root.getItem(i);
			if ((item instanceof JMenu) && menuLabel.equals(item.getText())) return getMenu(
				(JMenu) item, rest, createIfNecessary);
		}
		if (!createIfNecessary) return null;
		final JMenu subMenu = new JMenu(menuLabel);
		root.add(subMenu);
		return getMenu(subMenu, rest, createIfNecessary);
	}

	/**
	 * Initializes the template menu.
	 * <p>
	 * Third-party components can add templates simply by providing
	 * language-specific files in their resources, identified by a path of the
	 * form {@code /script-templates/<language>/menu label}.
	 * </p>
	 * <p>
	 * The sub menus of the template menu correspond to language names; Entries
	 * for languages unknown to the script service will be discarded quietly.
	 * </p>
	 *
	 * @param templatesMenu the top-level menu to populate
	 * @param languageMap the known languages
	 */
	protected void addTemplates(final JMenu templatesMenu,
		final Map<String, ScriptLanguage> languageMap)
	{
		for (final String templatePath : TEMPLATE_PATHS) {
			for (final Map.Entry<String, URL> entry : new TreeMap<String, URL>(
				FileFunctions.findResources(null, templatePath)).entrySet())
			{
				final String path = entry.getKey().replace('/', '>').replace('_', ' ');
				final int gt = path.indexOf('>');
				if (gt < 1) {
					log.warn("Ignoring invalid editor template: " + entry.getValue());
					continue;
				}
				final String language = path.substring(0, gt);
				if (!languageMap.containsKey(language)) {
					log.debug("Ignoring editor template for language " + language + ": " +
						entry.getValue());
					continue;
				}
				final ScriptLanguage engine = languageMap.get(language);
				final JMenu menu = getMenu(templatesMenu, path, true);

				String label = path.substring(path.lastIndexOf('>') + 1);
				final int dot = label.lastIndexOf('.');
				if (dot > 0) label = label.substring(0, dot);
				final JMenuItem item = new JMenuItem(label);
				menu.add(item);
				final URL url = entry.getValue();
				item.addActionListener(new ActionListener() {

					@Override
					public void actionPerformed(final ActionEvent e) {
						loadTemplate(url, engine);
					}
				});
			}
		}
	}

	/**
	 * Loads a template file from the given resource
	 *
	 * @param url The resource to load.
	 */
	public void loadTemplate(final String url) {
		try {
			loadTemplate(new URL(url));
		}
		catch (final Exception e) {
			log.error(e);
			error("The template '" + url + "' was not found.");
		}
	}

	public void loadTemplate(final URL url) {
		final String path = url.getPath();
		final int dot = path.lastIndexOf('.');
		ScriptLanguage language = null;
		if (dot > 0) {
			language = scriptService.getLanguageByExtension(path.substring(dot + 1));
		}
		loadTemplate(url, language);
	}

	public void loadTemplate(final URL url, final ScriptLanguage language) {
		createNewDocument();

		try {
			// Load the template
			final InputStream in = url.openStream();
			getTextArea().read(new BufferedReader(new InputStreamReader(in)), null);

			if (language != null) {
				setLanguage(language);
			}
			final String path = url.getPath();
			setFileName(path.substring(path.lastIndexOf('/') + 1));
		}
		catch (final Exception e) {
			e.printStackTrace();
			error("The template '" + url + "' was not found.");
		}
	}

	public void createNewDocument() {
		open(null);
	}

	public void createNewDocument(final String title, final String text) {
		open(null);
		final EditorPane editorPane = getEditorPane();
		editorPane.setText(text);
		editorPane.setLanguageByFileName(title);
		setFileName(title);
		setTitle();
	}

	/**
	 * Open a new editor to edit the given file, with a templateFile if the file
	 * does not exist yet
	 */
	public void createNewFromTemplate(final File file, final File templateFile) {
		open(file.exists() ? file : templateFile);
		if (!file.exists()) {
			final EditorPane editorPane = getEditorPane();
			try {
				editorPane.open(file);
			}
			catch (final IOException e) {
				handleException(e);
			}
			editorPane.setLanguageByFileName(file.getName());
			setTitle();
		}
	}

	public boolean fileChanged() {
		return getEditorPane().fileChanged();
	}

	public boolean handleUnsavedChanges() {
		return handleUnsavedChanges(false);
	}

	public boolean handleUnsavedChanges(final boolean beforeCompiling) {
		if (!fileChanged()) return true;

		if (beforeCompiling && autoSave.getState()) {
			save();
			return true;
		}

		switch (JOptionPane.showConfirmDialog(this, "Do you want to save changes?")) {
			case JOptionPane.NO_OPTION:
				return true;
			case JOptionPane.YES_OPTION:
				if (save()) return true;
		}

		return false;
	}

	protected void grabFocus() {
		toFront();
	}

	protected void grabFocus(final int laterCount) {
		if (laterCount == 0) {
			grabFocus();
			return;
		}

		SwingUtilities.invokeLater(new Thread() {

			@Override
			public void run() {
				grabFocus(laterCount - 1);
			}
		});
	}

	@Override
	public void actionPerformed(final ActionEvent ae) {
		final Object source = ae.getSource();
		if (source == newFile) createNewDocument();
		else if (source == open) {
			final EditorPane editorPane = getEditorPane();
			final File defaultDir =
				editorPane.getFile() != null ? editorPane.getFile().getParentFile()
					: AppUtils.getBaseDirectory("imagej.dir", TextEditor.class, null);
			final File file = openWithDialog(defaultDir);
			if (file != null) new Thread() {

				@Override
				public void run() {
					open(file);
				}
			}.start();
			return;
		}
		else if (source == save) save();
		else if (source == saveas) saveAs();
		else if (source == makeJar) makeJar(false);
		else if (source == makeJarWithSource) makeJar(true);
		else if (source == compileAndRun) runText();
		else if (source == compile) compile();
		else if (source == runSelection) runText(true);
		else if (source == nextError) new Thread() {

			@Override
			public void run() {
				nextError(true);
			}
		}.start();
		else if (source == previousError) new Thread() {

			@Override
			public void run() {
				nextError(false);
			}
		}.start();
		else if (source == kill) chooseTaskToKill();
		else if (source == close) if (tabbed.getTabCount() < 2) processWindowEvent(new WindowEvent(
			this, WindowEvent.WINDOW_CLOSING));
		else {
			if (!handleUnsavedChanges()) return;
			int index = tabbed.getSelectedIndex();
			removeTab(index);
			if (index > 0) index--;
			switchTo(index);
		}
		else if (source == cut) getTextArea().cut();
		else if (source == copy) getTextArea().copy();
		else if (source == paste) getTextArea().paste();
		else if (source == undo) getTextArea().undoLastAction();
		else if (source == redo) getTextArea().redoLastAction();
		else if (source == find) findOrReplace(false);
		else if (source == findNext) findDialog.searchOrReplace(false);
		else if (source == findPrevious) findDialog.searchOrReplace(false, false);
		else if (source == replace) findOrReplace(true);
		else if (source == gotoLine) gotoLine();
		else if (source == toggleBookmark) toggleBookmark();
		else if (source == listBookmarks) listBookmarks();
		else if (source == selectAll) {
			getTextArea().setCaretPosition(0);
			getTextArea().moveCaretPosition(getTextArea().getDocument().getLength());
		}
		else if (source == chooseFontSize) {
			commandService.run(ChooseFontSize.class, true, "editor", this);
		}
		else if (source == chooseTabSize) {
			commandService.run(ChooseTabSize.class, true, "editor", this);
		}
		else if (source == addImport) addImport(null);
		else if (source == removeUnusedImports) new TokenFunctions(getTextArea())
			.removeUnusedImports();
		else if (source == sortImports) new TokenFunctions(getTextArea())
			.sortImports();
		else if (source == removeTrailingWhitespace) new TokenFunctions(
			getTextArea()).removeTrailingWhitespace();
		else if (source == replaceTabsWithSpaces) getTextArea()
			.convertTabsToSpaces();
		else if (source == replaceSpacesWithTabs) getTextArea()
			.convertSpacesToTabs();
		else if (source == clearScreen) {
			getTab().getScreen().setText("");
		}
		else if (source == zapGremlins) zapGremlins();
		else if (source == savePreferences) {
			getEditorPane().savePreferences();
		}
		else if (source == openHelp) openHelp(null);
		else if (source == openHelpWithoutFrames) openHelp(null, false);
		else if (source == openMacroFunctions) try {
			new MacroFunctions(this).openHelp(getTextArea().getSelectedText());
		}
		catch (final IOException e) {
			handleException(e);
		}
		else if (source == extractSourceJar) extractSourceJar();
		else if (source == openSourceForClass) {
			final String className = getSelectedClassNameOrAsk();
			if (className != null) try {
				final String path = new FileFunctions(this).getSourcePath(className);
				if (path != null) open(new File(path));
				else {
					final String url = new FileFunctions(this).getSourceURL(className);
					try {
						platformService.open(new URL(url));
					}
					catch (final Throwable e) {
						handleException(e);
					}
				}
			}
			catch (final ClassNotFoundException e) {
				error("Could not open source for class " + className);
			}
		}
		/* TODO
		else if (source == showDiff) {
			new Thread() {
				public void run() {
					EditorPane pane = getEditorPane();
					new FileFunctions(TextEditor.this).showDiff(pane.file, pane.getGitDirectory());
				}
			}.start();
		}
		else if (source == commit) {
			new Thread() {
				public void run() {
					EditorPane pane = getEditorPane();
					new FileFunctions(TextEditor.this).commit(pane.file, pane.getGitDirectory());
				}
			}.start();
		}
		*/
		else if (source == gitGrep) {
			final String searchTerm = getTextArea().getSelectedText();
			File searchRoot = getEditorPane().getFile();
			if (searchRoot == null) {
				error("File was not yet saved; no location known!");
				return;
			}
			searchRoot = searchRoot.getParentFile();

			commandService.run(GitGrep.class, true, "editor", this, "searchTerm",
				searchTerm, "searchRoot", searchRoot);
		}
		else if (source == openInGitweb) {
			final EditorPane editorPane = getEditorPane();
			new FileFunctions(this).openInGitweb(editorPane.getFile(), editorPane
				.getGitDirectory(), editorPane.getCaretLineNumber() + 1);
		}
		else if (source == increaseFontSize || source == decreaseFontSize) {
			getEditorPane().increaseFontSize(
				(float) (source == increaseFontSize ? 1.2 : 1 / 1.2));
			updateTabAndFontSize(false);
		}
		else if (source == nextTab) switchTabRelative(1);
		else if (source == previousTab) switchTabRelative(-1);
		else if (handleTabsMenu(source)) return;
	}

	protected boolean handleTabsMenu(final Object source) {
		if (!(source instanceof JMenuItem)) return false;
		final JMenuItem item = (JMenuItem) source;
		if (!tabsMenuItems.contains(item)) return false;
		for (int i = tabsMenuTabsStart; i < tabsMenu.getItemCount(); i++)
			if (tabsMenu.getItem(i) == item) {
				switchTo(i - tabsMenuTabsStart);
				return true;
			}
		return false;
	}

	@Override
	public void stateChanged(final ChangeEvent e) {
		final int index = tabbed.getSelectedIndex();
		if (index < 0) {
			setTitle("");
			return;
		}
		final EditorPane editorPane = getEditorPane(index);
		editorPane.requestFocus();
		setTitle();
		editorPane.checkForOutsideChanges();
		
		editorPane.setLanguageByFileName(editorPane.getFileName());
		toggleWhiteSpaceLabeling.setSelected(editorPane.isWhitespaceVisible());
	}

	public EditorPane getEditorPane(final int index) {
		return getTab(index).editorPane;
	}

	public void findOrReplace(final boolean replace) {
		findDialog.setLocationRelativeTo(this);

		// override search pattern only if
		// there is sth. selected
		final String selection = getTextArea().getSelectedText();
		if (selection != null) findDialog.setSearchPattern(selection);

		findDialog.show(replace);
	}

	public void gotoLine() {
		final String line =
			JOptionPane.showInputDialog(this, "Line:", "Goto line...",
				JOptionPane.QUESTION_MESSAGE);
		if (line == null) return;
		try {
			gotoLine(Integer.parseInt(line));
		}
		catch (final BadLocationException e) {
			error("Line number out of range: " + line);
		}
		catch (final NumberFormatException e) {
			error("Invalid line number: " + line);
		}
	}

	public void gotoLine(final int line) throws BadLocationException {
		getTextArea().setCaretPosition(getTextArea().getLineStartOffset(line - 1));
	}

	public void toggleBookmark() {
		getEditorPane().toggleBookmark();
	}

	public void listBookmarks() {
		final Vector<EditorPane.Bookmark> bookmarks =
			new Vector<EditorPane.Bookmark>();
		for (int i = 0; i < tabbed.getTabCount(); i++)
			getEditorPane(i).getBookmarks(i, bookmarks);
		final BookmarkDialog dialog = new BookmarkDialog(this, bookmarks);
		dialog.setVisible(true);
	}

	public boolean reload() {
		return reload("Reload the file?");
	}

	public boolean reload(final String message) {
		final File file = getEditorPane().getFile();
		if (file == null || !file.exists()) return true;

		final boolean modified = getEditorPane().fileChanged();
		final String[] options = { "Reload", "Do not reload" };
		if (modified) options[0] = "Reload (discarding changes)";
		switch (JOptionPane.showOptionDialog(this, message, "Reload",
			JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options,
			options[0])) {
			case 0:
				try {
					getEditorPane().open(file);
					return true;
				}
				catch (final IOException e) {
					error("Could not reload " + file.getPath());
				}
				break;
		}
		return false;
	}

	public class Tab extends JSplitPane {

		protected final EditorPane editorPane = new EditorPane(TextEditor.this);
		protected final JTextArea screen = new JTextArea();
		protected final JScrollPane scroll;
		protected boolean showingErrors;
		private Executer executer;
		private final JButton runit, killit, toggleErrors;

		public Tab() {
			super(JSplitPane.VERTICAL_SPLIT);
			super.setResizeWeight(350.0 / 430.0);

			screen.setEditable(false);
			screen.setLineWrap(true);
			screen.setFont(new Font("Courier", Font.PLAIN, 12));

			final JPanel bottom = new JPanel();
			bottom.setLayout(new GridBagLayout());
			final GridBagConstraints bc = new GridBagConstraints();

			bc.gridx = 0;
			bc.gridy = 0;
			bc.weightx = 0;
			bc.weighty = 0;
			bc.anchor = GridBagConstraints.NORTHWEST;
			bc.fill = GridBagConstraints.NONE;
			runit = new JButton("Run");
			runit.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(final ActionEvent ae) {
					runText();
				}
			});
			bottom.add(runit, bc);

			bc.gridx = 1;
			killit = new JButton("Kill");
			killit.setEnabled(false);
			killit.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(final ActionEvent ae) {
					kill();
				}
			});
			bottom.add(killit, bc);

			bc.gridx = 2;
			bc.fill = GridBagConstraints.HORIZONTAL;
			bc.weightx = 1;
			bottom.add(new JPanel(), bc);

			bc.gridx = 3;
			bc.fill = GridBagConstraints.NONE;
			bc.weightx = 0;
			bc.anchor = GridBagConstraints.NORTHEAST;
			toggleErrors = new JButton("Show Errors");
			toggleErrors.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(final ActionEvent e) {
					toggleErrors();
				}
			});
			bottom.add(toggleErrors, bc);

			bc.gridx = 4;
			bc.fill = GridBagConstraints.NONE;
			bc.weightx = 0;
			bc.anchor = GridBagConstraints.NORTHEAST;
			final JButton clear = new JButton("Clear");
			clear.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(final ActionEvent ae) {
					if (showingErrors) errorScreen.setText("");
					else screen.setText("");
				}
			});
			bottom.add(clear, bc);

			bc.gridx = 0;
			bc.gridy = 1;
			bc.anchor = GridBagConstraints.NORTHWEST;
			bc.fill = GridBagConstraints.BOTH;
			bc.weightx = 1;
			bc.weighty = 1;
			bc.gridwidth = 5;
			screen.setEditable(false);
			screen.setLineWrap(true);
			final Font font = new Font("Courier", Font.PLAIN, 12);
			screen.setFont(font);
			scroll = new JScrollPane(screen);
			scroll.setPreferredSize(new Dimension(600, 80));
			bottom.add(scroll, bc);

			super.setTopComponent(editorPane.wrappedInScrollbars());
			super.setBottomComponent(bottom);
		}

		/** Invoke in the context of the event dispatch thread. */
		private void prepare() {
			editorPane.setEditable(false);
			runit.setEnabled(false);
			killit.setEnabled(true);
		}

		private void restore() {
			SwingUtilities.invokeLater(new Runnable() {

				@Override
				public void run() {
					editorPane.setEditable(true);
					runit.setEnabled(true);
					killit.setEnabled(false);
					executer = null;
				}
			});
		}

		public void toggleErrors() {
			showingErrors = !showingErrors;
			if (showingErrors) {
				toggleErrors.setText("Show Output");
				scroll.setViewportView(errorScreen);
			}
			else {
				toggleErrors.setText("Show Errors");
				scroll.setViewportView(screen);
			}
		}

		public void showErrors() {
			if (!showingErrors) toggleErrors();
			else if (scroll.getViewport().getView() == null) scroll
				.setViewportView(errorScreen);
		}

		public void showOutput() {
			if (showingErrors) toggleErrors();
		}

		public JTextArea getScreen() {
			return showingErrors ? errorScreen : screen;
		}

		boolean isExecuting() {
			return null != executer;
		}

		String getTitle() {
			return (editorPane.fileChanged() ? "*" : "") + editorPane.getFileName() +
				(isExecuting() ? " (Running)" : "");
		}

		/** Invoke in the context of the event dispatch thread. */
		private void execute(final boolean selectionOnly) throws IOException {
			prepare();
			final JTextAreaWriter output =
				new JTextAreaWriter(this.screen, TextEditor.this.log);
			final JTextAreaWriter errors = new JTextAreaWriter(errorScreen, log);
			final File file = getEditorPane().getFile();
			// Pipe current text into the runScript:
			final PipedInputStream pi = new PipedInputStream();
			final PipedOutputStream po = new PipedOutputStream(pi);
			// The Executer creates a Thread that
			// does the reading from PipedInputStream
			this.executer = new TextEditor.Executer(output, errors) {

				@Override
				public void execute() {
					try {
						evalScript(file == null ? getEditorPane().getFileName() : file
							.getAbsolutePath(), new InputStreamReader(pi), output, errors);
						output.flush();
						errors.flush();
						markCompileEnd();
					}
					catch (final Throwable t) {
						output.flush();
						errors.flush();
						if (t instanceof ScriptException && t.getCause() != null &&
							t.getCause().getClass().getName().endsWith("CompileError"))
						{
							errorScreen.append("Compilation failed");
							showErrors();
						}
						else {
							handleException(t);
						}
					}
					finally {
						restore();
					}
				}
			};
			// Write into PipedOutputStream
			// from another Thread
			try {
				final String text;
				if (selectionOnly) {
					final String selected = editorPane.getSelectedText();
					if (selected == null) {
						error("Selection required!");
						text = null;
					}
					else text = selected + "\n"; // Ensure code blocks are terminated
				}
				else {
					text = editorPane.getText();
				}
				new Thread() {

					{
						setPriority(Thread.NORM_PRIORITY);
					}

					@Override
					public void run() {
						final PrintWriter pw = new PrintWriter(po);
						pw.write(text);
						pw.flush(); // will lock and wait in some cases
						try {
							po.close();
						}
						catch (final Throwable tt) {
							tt.printStackTrace();
						}
						pw.close();
					}
				}.start();
			}
			catch (final Throwable t) {
				t.printStackTrace();
			}
			finally {
				// Re-enable when all text to send has been sent
				editorPane.setEditable(true);
			}
		}

		protected void kill() {
			if (null == executer) return;
			// Graceful attempt:
			executer.interrupt();
			// Give it 3 seconds. Then, stop it.
			final long now = System.currentTimeMillis();
			new Thread() {

				{
					setPriority(Thread.NORM_PRIORITY);
				}

				@Override
				public void run() {
					while (System.currentTimeMillis() - now < 3000)
						try {
							Thread.sleep(100);
						}
						catch (final InterruptedException e) {
							/* ignore */
						}
					if (null != executer) executer.obliterate();
					restore();

				}
			}.start();
		}
	}

	public static boolean isBinary(final File file) {
		if (file == null) return false;
		// heuristic: read the first up to 8000 bytes, and say that it is binary if
		// it contains a NUL
		try {
			final FileInputStream in = new FileInputStream(file);
			int left = 8000;
			final byte[] buffer = new byte[left];
			while (left > 0) {
				final int count = in.read(buffer, 0, left);
				if (count < 0) break;
				for (int i = 0; i < count; i++)
					if (buffer[i] == 0) {
						in.close();
						return true;
					}
				left -= count;
			}
			in.close();
			return false;
		}
		catch (final IOException e) {
			return false;
		}
	}

	/**
	 * Open a new tab with some content; the languageExtension is like ".java",
	 * ".py", etc.
	 */
	public Tab newTab(final String content, final String language) {
		String lang = language;
		final Tab tab = open(null);
		if (null != lang && lang.length() > 0) {
			lang = lang.trim().toLowerCase();

			if ('.' != lang.charAt(0)) {
				lang = "." + language;
			}

			tab.editorPane.setLanguage(scriptService.getLanguageByName(language));
		}

		if (null != content) {
			tab.editorPane.setText(content);
		}

		return tab;
	}

	public Tab open(final File file) {
		if (isBinary(file)) {
			// TODO!
			throw new RuntimeException("TODO: open image using IJ2");
			// return null;
		}

		try {
			Tab tab = (tabbed.getTabCount() == 0) ? null : getTab();
			final boolean wasNew = tab != null && tab.editorPane.isNew();
			if (!wasNew) {
				tab = new Tab();
				context.inject(tab.editorPane);
				tab.editorPane.loadPreferences();
				addDefaultAccelerators(tab.editorPane);
			}
			synchronized (tab.editorPane) { // tab is never null at this location.
				tab.editorPane.open(file);
				if (wasNew) {
					final int index = tabbed.getSelectedIndex() + tabsMenuTabsStart;
					tabsMenu.getItem(index).setText(tab.editorPane.getFileName());
				}
				else {
					tabbed.addTab("", tab);
					switchTo(tabbed.getTabCount() - 1);
					tabsMenuItems.add(addToMenu(tabsMenu, tab.editorPane.getFileName(),
						0, 0));
				}
				setFileName(tab.editorPane.getFile());
				try {
					updateTabAndFontSize(true);
				}
				catch (final NullPointerException e) {
					/* ignore */
				}
			}
			if (file != null) openRecent.add(file.getAbsolutePath());

			return tab;
		}
		catch (final FileNotFoundException e) {
			e.printStackTrace();
			error("The file '" + file + "' was not found.");
		}
		catch (final Exception e) {
			e.printStackTrace();
			error("There was an error while opening '" + file + "': " + e);
		}
		return null;
	}

	public boolean saveAs() {
		final EditorPane editorPane = getEditorPane();
		File file = editorPane.getFile();
		if (file == null) {
			final File ijDir =
				AppUtils.getBaseDirectory("imagej.dir", TextEditor.class, null);
			file = new File(ijDir, editorPane.getFileName());
		}
		final File fileToSave = uiService.chooseFile(file, FileWidget.SAVE_STYLE);
		if (fileToSave == null) return false;
		return saveAs(fileToSave.getAbsolutePath(), true);
	}

	public void saveAs(final String path) {
		saveAs(path, true);
	}

	public boolean saveAs(final String path, final boolean askBeforeReplacing) {
		final File file = new File(path);
		if (file.exists() &&
			askBeforeReplacing &&
			JOptionPane.showConfirmDialog(this, "Do you want to replace " + path +
				"?", "Replace " + path + "?", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return false;
		if (!write(file)) return false;
		setFileName(file);
		openRecent.add(path);
		return true;
	}

	public boolean save() {
		final File file = getEditorPane().getFile();
		if (file == null) return saveAs();
		if (!write(file)) return false;
		setTitle();
		return true;
	}

	public boolean write(final File file) {
		try {
			getEditorPane().write(file);
			return true;
		}
		catch (final IOException e) {
			error("Could not save " + file.getName());
			e.printStackTrace();
			return false;
		}
	}

	public boolean makeJar(final boolean includeSources) {
		final File file = getEditorPane().getFile();
		if ((file == null || isCompiled()) && !handleUnsavedChanges(true)) {
			return false;
		}

		String name = getEditorPane().getFileName();
		final String ext = FileUtils.getExtension(name);
		if (!"".equals(ext)) name = name.substring(0, name.length() - ext.length());
		if (name.indexOf('_') < 0) name += "_";
		name += ".jar";

		final File selectedFile = uiService.chooseFile(file, FileWidget.SAVE_STYLE);
		if (selectedFile == null) return false;
		if (selectedFile.exists() &&
			JOptionPane.showConfirmDialog(this, "Do you want to replace " +
				selectedFile + "?", "Replace " + selectedFile + "?",
				JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return false;
		try {
			makeJar(selectedFile, includeSources);
			return true;
		}
		catch (final IOException e) {
			e.printStackTrace();
			error("Could not write " + selectedFile + ": " + e.getMessage());
			return false;
		}
	}

	public void makeJar(final File file, final boolean includeSources)
		throws IOException
	{
		if (!handleUnsavedChanges(true)) return;

		final ScriptEngine interpreter = getCurrentLanguage().getScriptEngine();
		if (interpreter instanceof JavaEngine) {
			final JavaEngine java = (JavaEngine) interpreter;
			final JTextAreaWriter errors = new JTextAreaWriter(errorScreen, log);
			markCompileStart();
			getTab().showErrors();
			new Thread() {

				@Override
				public void run() {
					java.makeJar(getEditorPane().getFile(), includeSources, file, errors);
					errorScreen.insert("Compilation finished.\n", errorScreen
						.getDocument().getLength());
					markCompileEnd();
				}
			}.start();
		}
	}

	static void getClasses(final File directory, final List<String> paths,
		final List<String> names)
	{
		getClasses(directory, paths, names, "");
	}

	static void getClasses(final File directory, final List<String> paths,
		final List<String> names, final String inPrefix)
	{
		String prefix = inPrefix;

		if (!prefix.equals("")) prefix += "/";
		for (final File file : directory.listFiles())
			if (file.isDirectory()) getClasses(file, paths, names, prefix +
				file.getName());
			else {
				paths.add(file.getAbsolutePath());
				names.add(prefix + file.getName());
			}
	}

	static void writeJarEntry(final JarOutputStream out, final String name,
		final byte[] buf) throws IOException
	{
		try {
			final JarEntry entry = new JarEntry(name);
			out.putNextEntry(entry);
			out.write(buf, 0, buf.length);
			out.closeEntry();
		}
		catch (final ZipException e) {
			e.printStackTrace();
			throw new IOException(e.getMessage());
		}
	}

	static byte[] readFile(final String fileName) throws IOException {
		final File file = new File(fileName);
		final InputStream in = new FileInputStream(file);
		final byte[] buffer = new byte[(int) file.length()];
		in.read(buffer);
		in.close();
		return buffer;
	}

	static void deleteRecursively(final File directory) {
		for (final File file : directory.listFiles())
			if (file.isDirectory()) deleteRecursively(file);
			else file.delete();
		directory.delete();
	}

	void setLanguage(final ScriptLanguage language) {
		setLanguage(language, false);
	}

	void setLanguage(final ScriptLanguage language, final boolean addHeader) {
		getEditorPane().setLanguage(language, addHeader);
		updateTabAndFontSize(true);
	}

	void updateLanguageMenu(final ScriptLanguage language) {
		JMenuItem item = languageMenuItems.get(language);
		if (item == null) item = noneLanguageItem;
		if (!item.isSelected()) {
			item.setSelected(true);
		}

		final boolean isRunnable = item != noneLanguageItem;
		final boolean isCompileable =
			language != null && language.isCompiledLanguage();

		runMenu.setVisible(isRunnable);
		compileAndRun.setText(isCompileable ? "Compile and Run" : "Run");
		compileAndRun.setEnabled(isRunnable);
		runSelection.setVisible(isRunnable && !isCompileable);
		compile.setVisible(isCompileable);
		autoSave.setVisible(isCompileable);
		makeJar.setVisible(isCompileable);
		makeJarWithSource.setVisible(isCompileable);

		final boolean isJava =
			language != null && language.getLanguageName().equals("Java");
		addImport.setVisible(isJava);
		removeUnusedImports.setVisible(isJava);
		sortImports.setVisible(isJava);
		openSourceForMenuItem.setVisible(isJava);

		final boolean isMacro =
			language != null && language.getLanguageName().equals("ImageJ Macro");
		openMacroFunctions.setVisible(isMacro);
		openSourceForClass.setVisible(!isMacro);

		openHelp.setVisible(!isMacro && isRunnable);
		openHelpWithoutFrames.setVisible(!isMacro && isRunnable);
		nextError.setVisible(!isMacro && isRunnable);
		previousError.setVisible(!isMacro && isRunnable);

		final boolean isInGit = getEditorPane().getGitDirectory() != null;
		gitMenu.setVisible(isInGit);

		updateTabAndFontSize(false);
	}

	public void updateTabAndFontSize(final boolean setByLanguage) {
		final EditorPane pane = getEditorPane();
		if (pane.getCurrentLanguage() == null) return;

		if (setByLanguage) {
			if (pane.getCurrentLanguage().getLanguageName().equals("Python")) {
				pane.setTabSize(4);
			}
			else {
				// set tab size to current preferences.
				pane.resetTabSize();
			}
		}

		final int tabSize = pane.getTabSize();
		boolean defaultSize = false;
		for (int i = 0; i < tabSizeMenu.getItemCount(); i++) {
			final JMenuItem item = tabSizeMenu.getItem(i);
			if (item == chooseTabSize) {
				item.setSelected(!defaultSize);
				item.setText("Other" + (defaultSize ? "" : " (" + tabSize + ")") +
					"...");
			}
			else if (tabSize == Integer.parseInt(item.getText())) {
				item.setSelected(true);
				defaultSize = true;
			}
		}
		final int fontSize = (int) pane.getFontSize();
		defaultSize = false;
		for (int i = 0; i < fontSizeMenu.getItemCount(); i++) {
			final JMenuItem item = fontSizeMenu.getItem(i);
			if (item == chooseFontSize) {
				item.setSelected(!defaultSize);
				item.setText("Other" + (defaultSize ? "" : " (" + fontSize + ")") +
					"...");
				continue;
			}
			String label = item.getText();
			if (label.endsWith(" pt")) label = label.substring(0, label.length() - 3);
			if (fontSize == Integer.parseInt(label)) {
				item.setSelected(true);
				defaultSize = true;
			}
		}
		wrapLines.setState(pane.getLineWrap());
		tabsEmulated.setState(pane.getTabsEmulated());
	}

	public void setFileName(final String baseName) {
		getEditorPane().setFileName(baseName);
	}

	public void setFileName(final File file) {
		getEditorPane().setFileName(file);
	}

	synchronized void setTitle() {
		final Tab tab = getTab();

		final boolean fileChanged = tab.editorPane.fileChanged();
		final String fileName = tab.editorPane.getFileName();
		final String title =
			(fileChanged ? "*" : "") + fileName +
				(executingTasks.isEmpty() ? "" : " (Running)");
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				setTitle(title); // to the main window
				// Update all tabs: could have changed
				for (int i = 0; i < tabbed.getTabCount(); i++)
					tabbed.setTitleAt(i, ((Tab) tabbed.getComponentAt(i)).getTitle());
			}
		});
	}

	@Override
	public synchronized void setTitle(final String title) {
		super.setTitle(title);
		final int index = tabsMenuTabsStart + tabbed.getSelectedIndex();
		if (index < tabsMenu.getItemCount()) {
			final JMenuItem item = tabsMenu.getItem(index);
			if (item != null) item.setText(title);
		}
	}

	private final ArrayList<Executer> executingTasks = new ArrayList<Executer>();

	/**
	 * Generic Thread that keeps a starting time stamp, sets the priority to
	 * normal and starts itself.
	 */
	public abstract class Executer extends ThreadGroup {

		JTextAreaWriter output, errors;

		Executer(final JTextAreaWriter output, final JTextAreaWriter errors) {
			super("Script Editor Run :: " + new Date().toString());
			this.output = output;
			this.errors = errors;
			// Store itself for later
			executingTasks.add(this);
			setTitle();
			// Enable kill menu
			kill.setEnabled(true);
			// Fork a task, as a part of this ThreadGroup
			new Thread(this, getName()) {

				{
					setPriority(Thread.NORM_PRIORITY);
					start();
				}

				@Override
				public void run() {
					try {
						execute();
						// Wait until any children threads die:
						int activeCount = getThreadGroup().activeCount();
						while (activeCount > 1) {
							if (isInterrupted()) break;
							try {
								Thread.sleep(500);
								final List<Thread> ts = getAllThreads();
								activeCount = ts.size();
								if (activeCount <= 1) break;
								log.debug("Waiting for " + ts.size() + " threads to die");
								int count_zSelector = 0;
								for (final Thread t : ts) {
									if (t.getName().equals("zSelector")) {
										count_zSelector++;
									}
									log.debug("THREAD: " + t.getName());
								}
								if (activeCount == count_zSelector + 1) {
									// Do not wait on the stack slice selector thread.
									break;
								}
							}
							catch (final InterruptedException ie) {
								/* ignore */
							}
						}
					}
					catch (final Throwable t) {
						handleException(t);
					}
					finally {
						executingTasks.remove(Executer.this);
						try {
							if (null != output) output.shutdown();
							if (null != errors) errors.shutdown();
						}
						catch (final Exception e) {
							handleException(e);
						}
						// Leave kill menu item enabled if other tasks are running
						kill.setEnabled(executingTasks.size() > 0);
						setTitle();
					}
				}
			};
		}

		/** The method to extend, that will do the actual work. */
		abstract void execute();

		/** Fetch a list of all threads from all thread subgroups, recursively. */
		List<Thread> getAllThreads() {
			final ArrayList<Thread> threads = new ArrayList<Thread>();
			// From all subgroups:
			final ThreadGroup[] tgs = new ThreadGroup[activeGroupCount() * 2 + 100];
			this.enumerate(tgs, true);
			for (final ThreadGroup tg : tgs) {
				if (null == tg) continue;
				final Thread[] ts = new Thread[tg.activeCount() * 2 + 100];
				tg.enumerate(ts);
				for (final Thread t : ts) {
					if (null == t) continue;
					threads.add(t);
				}
			}
			// And from this group:
			final Thread[] ts = new Thread[activeCount() * 2 + 100];
			this.enumerate(ts);
			for (final Thread t : ts) {
				if (null == t) continue;
				threads.add(t);
			}
			return threads;
		}

		/**
		 * Totally destroy/stop all threads in this and all recursive thread
		 * subgroups. Will remove itself from the executingTasks list.
		 */
		@SuppressWarnings("deprecation")
		void obliterate() {
			try {
				// Stop printing to the screen
				if (null != output) output.shutdownNow();
				if (null != errors) errors.shutdownNow();
			}
			catch (final Exception e) {
				e.printStackTrace();
			}
			for (final Thread thread : getAllThreads()) {
				try {
					thread.interrupt();
					Thread.yield(); // give it a chance
					thread.stop();
				}
				catch (final Throwable t) {
					t.printStackTrace();
				}
			}
			executingTasks.remove(this);
			setTitle();
		}

		@Override
		public String toString() {
			return getName();
		}
	}

	/** Returns a list of currently executing tasks */
	public List<Executer> getExecutingTasks() {
		return executingTasks;
	}

	public void kill(final Executer executer) {
		for (int i = 0; i < tabbed.getTabCount(); i++) {
			final Tab tab = (Tab) tabbed.getComponentAt(i);
			if (executer == tab.executer) {
				tab.kill();
				break;
			}
		}
	}

	/**
	 * Query the list of running scripts and provide a dialog to choose one and
	 * kill it.
	 */
	public void chooseTaskToKill() {
		if (executingTasks.size() == 0) {
			error("\nNo running scripts\n");
			return;
		}
		commandService.run(KillScript.class, true, "editor", this);
	}

	/** Run the text in the textArea without compiling it, only if it's not java. */
	public void runText() {
		runText(false);
	}

	public void runText(final boolean selectionOnly) {
		if (isCompiled()) {
			if (selectionOnly) {
				error("Cannot run selection of compiled language!");
				return;
			}
			if (handleUnsavedChanges(true)) runScript();
			return;
		}
		final ScriptLanguage currentLanguage = getCurrentLanguage();
		if (currentLanguage == null) {
			error("Select a language first!");
			// TODO guess the language, if possible.
			return;
		}
		markCompileStart();

		try {
			final Tab tab = getTab();
			tab.showOutput();
			tab.execute(selectionOnly);
		}
		catch (final Throwable t) {
			t.printStackTrace();
		}
	}

	public void runScript() {
		if (isCompiled()) getTab().showErrors();
		else getTab().showOutput();

		markCompileStart();
		final JTextAreaWriter output = new JTextAreaWriter(getTab().screen, log);
		final JTextAreaWriter errors = new JTextAreaWriter(errorScreen, log);

		final File file = getEditorPane().getFile();
		new TextEditor.Executer(output, errors) {

			@Override
			public void execute() {
				Reader reader = null;
				try {
					reader =
						evalScript(getEditorPane().getFile().getPath(),
							new FileReader(file), output, errors);

					output.flush();
					errors.flush();
					markCompileEnd();
				}
				catch (final Throwable e) {
					handleException(e);
				}
				finally {
					if (reader != null) {
						try {
							reader.close();
						}
						catch (final IOException exc) {
							handleException(exc);
						}
					}
				}
			}
		};
	}

	public void compile() {
		if (!handleUnsavedChanges(true)) return;

		final ScriptEngine interpreter = getCurrentLanguage().getScriptEngine();
		if (interpreter instanceof JavaEngine) {
			final JavaEngine java = (JavaEngine) interpreter;
			final JTextAreaWriter errors = new JTextAreaWriter(errorScreen, log);
			markCompileStart();
			getTab().showErrors();
			new Thread() {

				@Override
				public void run() {
					java.compile(getEditorPane().getFile(), errors);
					errorScreen.insert("Compilation finished.\n", errorScreen
						.getDocument().getLength());
					markCompileEnd();
				}
			}.start();
		}
	}

	public String getSelectedTextOrAsk(final String label) {
		String selection = getTextArea().getSelectedText();
		if (selection == null || selection.indexOf('\n') >= 0) {
			selection =
				JOptionPane.showInputDialog(this, label + ":", label + "...",
					JOptionPane.QUESTION_MESSAGE);
			if (selection == null) return null;
		}
		return selection;
	}

	public String getSelectedClassNameOrAsk() {
		String className = getSelectedTextOrAsk("Class name");
		if (className != null) className = className.trim();
		return className;
	}

	protected static void append(final JTextArea textArea, final String text) {
		final int length = textArea.getDocument().getLength();
		textArea.insert(text, length);
		textArea.setCaretPosition(length);
	}

	public void markCompileStart() {
		errorHandler = null;

		final String started =
			"Started " + getEditorPane().getFileName() + " at " + new Date() + "\n";
		final int offset = errorScreen.getDocument().getLength();
		append(errorScreen, started);
		append(getTab().screen, started);
		compileStartOffset = errorScreen.getDocument().getLength();
		try {
			compileStartPosition = errorScreen.getDocument().createPosition(offset);
		}
		catch (final BadLocationException e) {
			handleException(e);
		}
		ExceptionHandler.addThread(Thread.currentThread(), this);
	}

	public void markCompileEnd() {
		if (errorHandler == null) {
			errorHandler =
				new ErrorHandler(getCurrentLanguage(), errorScreen,
					compileStartPosition.getOffset());
			if (errorHandler.getErrorCount() > 0) getTab().showErrors();
		}
		if (compileStartOffset != errorScreen.getDocument().getLength()) getTab()
			.showErrors();
		if (getTab().showingErrors) {
			errorHandler.scrollToVisible(compileStartOffset);
		}
	}

	public boolean nextError(final boolean forward) {
		if (errorHandler != null && errorHandler.nextError(forward)) try {
			File file = new File(errorHandler.getPath());
			if (!file.isAbsolute()) file = getFileForBasename(file.getName());
			errorHandler.markLine();
			switchTo(file, errorHandler.getLine());
			getTab().showErrors();
			errorScreen.invalidate();
			return true;
		}
		catch (final Exception e) {
			handleException(e);
		}
		return false;
	}

	public void switchTo(final String path, final int lineNumber)
		throws IOException
	{
		switchTo(new File(path).getCanonicalFile(), lineNumber);
	}

	public void switchTo(final File file, final int lineNumber) {
		if (!editorPaneContainsFile(getEditorPane(), file)) switchTo(file);
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				try {
					gotoLine(lineNumber);
				}
				catch (final BadLocationException e) {
					// ignore
				}
			}
		});
	}

	public void switchTo(final File file) {
		for (int i = 0; i < tabbed.getTabCount(); i++)
			if (editorPaneContainsFile(getEditorPane(i), file)) {
				switchTo(i);
				return;
			}
		open(file);
	}

	public void switchTo(final int index) {
		if (index == tabbed.getSelectedIndex()) return;
		tabbed.setSelectedIndex(index);
	}

	protected void switchTabRelative(final int delta) {
		int index = tabbed.getSelectedIndex();
		final int count = tabbed.getTabCount();
		index = ((index + delta) % count);
		if (index < 0) index += count;
		switchTo(index);
	}

	protected void removeTab(int index) {
		tabbed.remove(index);
		index += tabsMenuTabsStart;
		tabsMenuItems.remove(tabsMenu.getItem(index));
		tabsMenu.remove(index);
	}

	boolean editorPaneContainsFile(final EditorPane editorPane, final File file) {
		try {
			return file != null && editorPane != null &&
				editorPane.getFile() != null &&
				file.getCanonicalFile().equals(editorPane.getFile().getCanonicalFile());
		}
		catch (final IOException e) {
			return false;
		}
	}

	public File getFile() {
		return getEditorPane().getFile();
	}

	public File getFileForBasename(final String baseName) {
		File file = getFile();
		if (file != null && file.getName().equals(baseName)) return file;
		for (int i = 0; i < tabbed.getTabCount(); i++) {
			file = getEditorPane(i).getFile();
			if (file != null && file.getName().equals(baseName)) return file;
		}
		return null;
	}

	public void addImport(String className) {
		if (className == null) {
			className = getSelectedClassNameOrAsk();
		}
		if (className != null) {
			new TokenFunctions(getTextArea()).addImport(className.trim());
		}
	}

	public void openHelp(final String className) {
		openHelp(className, true);
	}

	public void openHelp(String className, final boolean withFrames) {
		if (className == null) {
			className = getSelectedClassNameOrAsk();
		}
	}

	public void extractSourceJar() {
		final File file = openWithDialog(null);
		if (file != null) extractSourceJar(file);
	}

	public void extractSourceJar(final File file) {
		try {
			final FileFunctions functions = new FileFunctions(this);
			final File workspace =
				uiService.chooseFile(new File(System.getProperty("user.home")),
					FileWidget.DIRECTORY_STYLE);
			if (workspace == null) return;
			final List<String> paths =
				functions.extractSourceJar(file.getAbsolutePath(), workspace);
			for (final String path : paths)
				if (!functions.isBinaryFile(path)) {
					open(new File(path));
					final EditorPane pane = getEditorPane();
					new TokenFunctions(pane).removeTrailingWhitespace();
					if (pane.fileChanged()) save();
				}
		}
		catch (final IOException e) {
			error("There was a problem opening " + file + ": " + e.getMessage());
		}
	}

	/* extensionMustMatch == false means extension must not match */
	protected File openWithDialog(final File defaultDir) {
		return uiService.chooseFile(defaultDir, FileWidget.OPEN_STYLE);
	}

	/**
	 * Write a message to the output screen
	 *
	 * @param message The text to write
	 */
	public void write(String message) {
		final Tab tab = getTab();
		if (!message.endsWith("\n")) message += "\n";
		tab.screen.insert(message, tab.screen.getDocument().getLength());
	}

	public void writeError(String message) {
		final Tab tab = getTab();
		tab.showErrors();
		if (!message.endsWith("\n")) message += "\n";
		errorScreen.insert(message, errorScreen.getDocument().getLength());
	}

	protected void error(final String message) {
		JOptionPane.showMessageDialog(this, message);
	}

	protected void handleException(final Throwable e) {
		handleException(e, errorScreen);
		getTab().showErrors();
	}

	public static void
		handleException(final Throwable e, final JTextArea textArea)
	{
		final CharArrayWriter writer = new CharArrayWriter();
		final PrintWriter out = new PrintWriter(writer);
		e.printStackTrace(out);
		for (Throwable cause = e.getCause(); cause != null; cause =
			cause.getCause())
		{
			out.write("Caused by: ");
			cause.printStackTrace(out);
		}
		out.close();
		textArea.append(writer.toString());
	}

	/**
	 * Removes invalid characters, shows a dialog.
	 *
	 * @return The amount of invalid characters found.
	 */
	public int zapGremlins() {
		final int count = getEditorPane().zapGremlins();
		final String msg =
			count > 0 ? "Zap Gremlins converted " + count +
				" invalid characters to spaces" : "No invalid characters found!";
		JOptionPane.showMessageDialog(this, msg);
		return count;
	}

	// -- Helper methods --

	private boolean isCompiled() {
		final ScriptLanguage language = getCurrentLanguage();
		if (language == null) return false;
		return language.isCompiledLanguage();
	}

	private Reader evalScript(final String filename, Reader reader,
		final Writer output, final Writer errors) throws FileNotFoundException,
		ModuleException
	{
		final ScriptLanguage language = getCurrentLanguage();
		if (respectAutoImports) {
			reader =
				DefaultAutoImporters.prefixAutoImports(context, language, reader,
					errors);
		}
		// create script module for execution
		final ScriptInfo info = new ScriptInfo(context, filename, reader);
		final ScriptModule module = info.createModule();
		context.inject(module);

		// use the currently selected language to execute the script
		module.setLanguage(language);

		// map stdout and stderr to the UI
		module.setOutputWriter(output);
		module.setErrorWriter(errors);

		// execute the script
		try {
			moduleService.run(module, true).get();
		}
		catch (final InterruptedException e) {
			error("Interrupted");
		}
		catch (final ExecutionException e) {
			log.error(e);
		}
		return reader;
	}

	@Override
	public boolean confirmClose() {
		while (tabbed.getTabCount() > 0) {
			if (!handleUnsavedChanges()) return false;
			final int index = tabbed.getSelectedIndex();
			removeTab(index);
		}
		return true;
	}

}
