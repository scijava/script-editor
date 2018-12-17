/*
 * #%L
 * Script Editor and Interpreter for SciJava script languages.
 * %%
 * Copyright (C) 2009 - 2018 Board of Regents of the University of
 * Wisconsin-Madison, Max Planck Institute of Molecular Cell Biology and
 * Genetics, and others.
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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
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
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
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
import javax.swing.JFileChooser;
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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Position;
import javax.swing.tree.TreePath;

import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory;
import org.scijava.Context;
import org.scijava.app.AppService;
import org.scijava.batch.BatchService;
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
import org.scijava.ui.swing.script.commands.ChooseFontSize;
import org.scijava.ui.swing.script.commands.ChooseTabSize;
import org.scijava.ui.swing.script.commands.GitGrep;
import org.scijava.ui.swing.script.commands.KillScript;
import org.scijava.util.FileUtils;
import org.scijava.util.MiscUtils;
import org.scijava.widget.FileWidget;

/**
 * A versatile script editor for SciJava applications.
 * <p>
 * Based on the powerful SciJava scripting framework and the
 * <a href="http://fifesoft.com/rsyntaxtextarea/">RSyntaxTextArea</a> library,
 * this text editor lets users script their way to success. Thanks to the
 * <a href="https://github.com/scijava/scripting-java">Java backend for SciJava
 * scripting</a>, it is even possible to develop Java plugins in the editor.
 * </p>
 *
 * @author Johannes Schindelin
 * @author Jonathan Hale
 */
@SuppressWarnings("serial")
public class TextEditor extends JFrame implements ActionListener,
	ChangeListener, CloseConfirmable, DocumentListener
{

	private static final Set<String> TEMPLATE_PATHS = new HashSet<>();
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

	private JTabbedPane tabbed;
	private JMenuItem newFile, open, save, saveas, compileAndRun, compile,
			close, undo, redo, cut, copy, paste, find, replace, selectAll, kill,
			gotoLine, makeJar, makeJarWithSource, removeUnusedImports, sortImports,
			removeTrailingWhitespace, findNext, findPrevious, openHelp, addImport,
			clearScreen, nextError, previousError, openHelpWithoutFrames, nextTab,
			previousTab, runSelection, extractSourceJar, toggleBookmark,
			listBookmarks, openSourceForClass, openSourceForMenuItem,
			openMacroFunctions, decreaseFontSize, increaseFontSize, chooseFontSize,
			chooseTabSize, gitGrep, openInGitweb, replaceTabsWithSpaces,
			replaceSpacesWithTabs, toggleWhiteSpaceLabeling, zapGremlins,
			savePreferences, toggleAutoCompletionMenu;
	private RecentFilesMenuItem openRecent;
	private JMenu gitMenu, tabsMenu, fontSizeMenu, tabSizeMenu, toolsMenu,
			runMenu, whiteSpaceMenu;
	private int tabsMenuTabsStart;
	private Set<JMenuItem> tabsMenuItems;
	private FindAndReplaceDialog findDialog;
	private JCheckBoxMenuItem autoSave, wrapLines, tabsEmulated, autoImport;
	private JTextArea errorScreen = new JTextArea();
	
	private final FileSystemTree tree;

	private int compileStartOffset;
	private Position compileStartPosition;
	private ErrorHandler errorHandler;

	private boolean respectAutoImports;

	@Parameter
	private Context context;
	@Parameter
	private LogService log;
	@Parameter
	private ModuleService moduleService;
	@Parameter
	private PlatformService platformService;
	@Parameter
	private IOService ioService;
	@Parameter
	private CommandService commandService;
	@Parameter
	private ScriptService scriptService;
	@Parameter
	private PluginService pluginService;
	@Parameter
	private ScriptHeaderService scriptHeaderService;
	@Parameter
	private UIService uiService;
	@Parameter
	private PrefService prefService;
	@Parameter
	private AppService appService;
	@Parameter
	private BatchService batchService;

	private Map<ScriptLanguage, JRadioButtonMenuItem> languageMenuItems;
	private JRadioButtonMenuItem noneLanguageItem;
	
	private EditableScriptInfo scriptInfo;
	private ScriptModule module;
	private boolean incremental = false;
	private DragSource dragSource;
	
	static public final ArrayList<TextEditor> instances = new ArrayList<TextEditor>();
	static public final ArrayList<Context> contexts = new ArrayList<Context>();

	public TextEditor(final Context context) {
		super("Script Editor");
		instances.add(this);
		contexts.add(context);
		context.inject(this);
		initializeTokenMakers();
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
					setFontSize(size);
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

		toggleAutoCompletionMenu = new JCheckBoxMenuItem("Auto completion");
		toggleAutoCompletionMenu.setSelected(prefService.getBoolean(TextEditor.class, "autoComplete", true));
		toggleAutoCompletionMenu.addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(final ChangeEvent e) {
				toggleAutoCompletion();
			}
		});
		edit.add(toggleAutoCompletionMenu);

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
			new LinkedHashMap<>();
		final Set<Integer> usedShortcuts = new HashSet<>();
		final JMenu languages = new JMenu("Language");
		languages.setMnemonic(KeyEvent.VK_L);
		final ButtonGroup group = new ButtonGroup();

		// get list of languages, and sort them by name
		final ArrayList<ScriptLanguage> list =
			new ArrayList<>(scriptService.getLanguages());
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
			new HashMap<>();
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
		addTemplates(templates);
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
		tabsMenuItems = new HashSet<>();
		mbar.add(tabsMenu);

		// Add the editor and output area
		tabbed = new JTabbedPane();
		tabbed.addChangeListener(this);
		open(null); // make sure the editor pane is added

		tabbed.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		getContentPane().setLayout(
			new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));
		
		final JPanel tree_panel = new JPanel();
		final JButton add_directory = new JButton("[+]");
		add_directory.setToolTipText("Add a directory");
		final JButton remove_directory = new JButton("[-]");
		remove_directory.setToolTipText("Remove a top-level directory");
		tree = new FileSystemTree();
		dragSource = new DragSource();
		dragSource.createDefaultDragGestureRecognizer(tree, DnDConstants.ACTION_COPY, new DragAndDrop());
		tree.setMinimumSize(new Dimension(200, 600));
		tree.addLeafListener(new FileSystemTree.LeafListener() {
			@Override
			public void leafDoubleClicked(final File file) {
				final String name = file.getName();
				final int idot = name.lastIndexOf('.');
				if (idot > -1) {
					final String ext = name.substring(idot + 1);
					final ScriptLanguage lang = scriptService.getLanguageByExtension(ext);
					if (null != lang) {
						open(file);
						return;
					}
				}
				if (isBinary(file)) {
					System.out.println("isBinary: " + true);
					try {
						final Object o = ioService.open(file.getAbsolutePath());
						// Open in whatever way possible
						if ( null != o ) uiService.show(o);
						else JOptionPane.showMessageDialog(TextEditor.this, "Could not open the file at: " + file.getAbsolutePath());
						return;
					} catch (Exception e) {
						error("Could not open image at " + file);
						e.printStackTrace();
					}
				}
				// Ask:
				final int choice = JOptionPane.showConfirmDialog(TextEditor.this,
						"Really try to open file " + name + " in a tab?", "Confirm", JOptionPane.OK_CANCEL_OPTION);
				if (JOptionPane.OK_OPTION == choice) {
					open(file);
				}
			}
		});
		add_directory.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				final JFileChooser c = new JFileChooser("Choose a directory");
				c.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				c.setFileHidingEnabled(true); // hide hidden files
				if (JFileChooser.APPROVE_OPTION == c.showOpenDialog(getContentPane())) {
					final File file = c.getSelectedFile();
					if (file.isDirectory()) tree.addRootDirectory(file.getAbsolutePath(), false);
				}
			}
		});
		remove_directory.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				final TreePath p = tree.getSelectionPath();
				if (null == p) {
					JOptionPane.showMessageDialog(TextEditor.this, "Select a top-level folder first.");
					return;
				}
				if (2 == p.getPathCount()) {
					// Is a child of the root, so it's a top-level folder
					tree.getModel().removeNodeFromParent((FileSystemTree.Node)p.getLastPathComponent());
				} else {
					JOptionPane.showMessageDialog(TextEditor.this, "Can only remove top-level folders.");
				}
			}
		});
		
		// Restore top-level directories
		tree.addTopLevelFoldersFrom(getEditorPane().loadFolders());

		final GridBagLayout g = new GridBagLayout();
		tree_panel.setLayout(g);
		final GridBagConstraints bc = new GridBagConstraints();
		bc.gridx = 0;
		bc.gridy = 0;
		bc.weightx = 0;
		bc.weighty = 0;
		bc.anchor = GridBagConstraints.NORTHWEST;
		bc.fill = GridBagConstraints.NONE;
		tree_panel.add(add_directory, bc);
		bc.gridx = 1;
		tree_panel.add(remove_directory, bc);
		bc.gridx = 0;
		bc.gridwidth = 2;
		bc.gridy = 1;
		bc.weightx = 1.0;
		bc.weighty = 1.0;
		bc.fill = GridBagConstraints.BOTH;
		tree_panel.add(tree, bc);
		final JScrollPane scrolltree = new JScrollPane(tree_panel);
		scrolltree.setBackground(Color.white);
		scrolltree.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(0,5,0,5)));
		scrolltree.setPreferredSize(new Dimension(200, 600));
		final JSplitPane body = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrolltree, tabbed);
		body.setOneTouchExpandable(true);
		getContentPane().add(body);

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
				tree.destroy();
				// Necessary to prevent memory leaks
				for (final DragSourceListener l : dragSource.getDragSourceListeners()) {
					dragSource.removeDragSourceListener(l);
				}
				dragSource = null;
				getTab().destroy();
				dispose();
			}
		});

		addWindowFocusListener(new WindowAdapter() {

			@Override
			public void windowGainedFocus(final WindowEvent e) {
				checkForOutsideChanges();
			}
		});

		final Font font = new Font("Courier", Font.PLAIN, 12);
		errorScreen.setFont(font);
		errorScreen.setEditable(false);
		errorScreen.setLineWrap(true);

		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

		try {
			final Runnable ui_task = new Runnable() {
				@Override
				public void run() {
					pack();
					body.setDividerLocation(0.2);
					getTab().getScreenAndPromptSplit().setDividerLocation(1.0);
				}
			};
			if (SwingUtilities.isEventDispatchThread()) {
				ui_task.run();
			}
			else {
				SwingUtilities.invokeAndWait(ui_task);
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
	
	private class DragAndDrop implements DragSourceListener, DragGestureListener {
		@Override
		public void dragDropEnd(DragSourceDropEvent dsde) {}

		@Override
		public void dragEnter(DragSourceDragEvent dsde) {
			dsde.getDragSourceContext().setCursor(DragSource.DefaultMoveNoDrop);
		}

		@Override
		public void dragGestureRecognized(DragGestureEvent dge) {
			TreePath path = tree.getSelectionPath();
			final String filepath = (String)((FileSystemTree.Node) path.getLastPathComponent()).getUserObject();
			dragSource.startDrag(dge, DragSource.DefaultCopyDrop, new Transferable() {
				@Override
				public boolean isDataFlavorSupported(final DataFlavor flavor) {
					return DataFlavor.javaFileListFlavor == flavor;
				}
				
				@Override
				public DataFlavor[] getTransferDataFlavors() {
					return new DataFlavor[]{ DataFlavor.javaFileListFlavor };
				}
				
				@Override
				public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
					if (isDataFlavorSupported(flavor))
						return Arrays.asList(new String[]{filepath});
					return null;
				}
			}, this);
		}

		@Override
		public void dragExit(DragSourceEvent dse) {
			dse.getDragSourceContext().setCursor(DragSource.DefaultMoveNoDrop);
		}

		@Override
		public void dragOver(DragSourceDragEvent dsde) {
			if (tree == dsde.getSource()) {
				dsde.getDragSourceContext().setCursor(DragSource.DefaultCopyNoDrop);
			} else if (dsde.getDropAction() == DnDConstants.ACTION_COPY) {
				dsde.getDragSourceContext().setCursor(DragSource.DefaultCopyDrop);
			} else {
				dsde.getDragSourceContext().setCursor(DragSource.DefaultCopyNoDrop);
			}
		}

		@Override
		public void dropActionChanged(DragSourceDragEvent dsde) {}
	}

	public LogService log() { return log; }
	public PlatformService getPlatformService() { return platformService; }
	public JTextArea getErrorScreen() { return errorScreen; }
	public void setErrorScreen(final JTextArea errorScreen) {
		this.errorScreen = errorScreen;
	}
	public ErrorHandler getErrorHandler() { return errorHandler; }
	public void setErrorHandler(final ErrorHandler errorHandler) {
		this.errorHandler = errorHandler;
	}

	private synchronized void initializeTokenMakers() {
		if (tokenMakerFactory != null) return;
		tokenMakerFactory =
			(AbstractTokenMakerFactory) TokenMakerFactory.getDefaultInstance();
		for (final PluginInfo<SyntaxHighlighter> info : pluginService
			.getPluginsOfType(SyntaxHighlighter.class))
			try {
				tokenMakerFactory.putMapping("text/" + info.getName(), info
					.getClassName());
			}
			catch (final Throwable t) {
				log.warn("Could not register " + info.getName(), t);
			}
	}

	/**
	 * Check whether the file was edited outside of this {@link EditorPane} and
	 * ask the user whether to reload.
	 */
	public void checkForOutsideChanges() {
		final EditorPane editorPane = getEditorPane();
		if (editorPane.wasChangedOutside()) {
			reload("The file " + editorPane.getFile().getName() +
				" was changed outside of the editor");
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
	public TextEditorTab getTab() {
		int index = tabbed.getSelectedIndex();
		if (index < 0) {
			// should not happen, but safety first.
			if (tabbed.getTabCount() == 0) {
				// should not happen either, but, again, safety first.
				createNewDocument();
			}

			// Ensure the new document is returned - otherwise we would pass
			// the negative index to the getComponentAt call below.
			tabbed.setSelectedIndex(0);
			index = 0;
		}
		return (TextEditorTab) tabbed.getComponentAt(index);
	}

	/**
	 * Get tab at provided index.
	 *
	 * @param index the index of the tab.
	 * @return the {@link TextEditorTab} at given index or <code>null</code>.
	 */
	public TextEditorTab getTab(final int index) {
		return (TextEditorTab) tabbed.getComponentAt(index);
	}

	/**
	 * Return the {@link EditorPane} of the currently selected
	 * {@link TextEditorTab}.
	 *
	 * @return the current {@link EditorPane}. Never <code>null</code>.
	 */
	public EditorPane getEditorPane() {
		return getTab().editorPane;
	}

	/**
	 * @return {@link ScriptLanguage} used in the current {@link EditorPane}.
	 */
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
		new ArrayList<>();

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

	private JMenu getMenu(final JMenu root, final String menuItemPath,
		final boolean createIfNecessary)
	{
		final int slash = menuItemPath.indexOf('/');
		if (slash < 0) return root;

		final String menuLabel = menuItemPath.substring(0, slash);
		final String rest = menuItemPath.substring(slash + 1);
		for (int i = 0; i < root.getItemCount(); i++) {
			final JMenuItem item = root.getItem(i);
			if (item instanceof JMenu && menuLabel.equals(item.getText())) {
				return getMenu((JMenu) item, rest, createIfNecessary);
			}
		}
		if (!createIfNecessary) return null;
		final JMenu subMenu = new JMenu(menuLabel);
		root.add(subMenu);
		return getMenu(subMenu, rest, createIfNecessary);
	}

	/**
	 * Initializes the Templates menu.
	 * <p>
	 * Other components can add templates simply by providing scripts in their
	 * resources, identified by a path of the form
	 * {@code /script_templates/<menu path>/<menu label>}.
	 * </p>
	 *
	 * @param templatesMenu the top-level menu to populate
	 */
	private void addTemplates(final JMenu templatesMenu) {
		final File baseDir = appService.getApp().getBaseDirectory();

		for (final String templatePath : TEMPLATE_PATHS) {
			for (final Map.Entry<String, URL> entry : new TreeMap<>(
				FileUtils.findResources(null, templatePath, baseDir)).entrySet())
			{
				final String key = entry.getKey();
				final String ext = FileUtils.getExtension(key);

				// try to determine the scripting language
				final ScriptLanguage lang = ext.isEmpty() ? null :
					scriptService.getLanguageByExtension(ext);
				final String langName = lang == null ? null : lang.getLanguageName();
				final String langSuffix = lang == null ? null : " (" + langName + ")";

				final String path = adjustPath(key, langName);

				// create a human-readable label
				final int labelIndex = path.lastIndexOf('/') + 1;
				final String label = ext.isEmpty() ? path.substring(labelIndex) :
					path.substring(labelIndex, path.length() - ext.length() - 1);

				final ActionListener menuListener = new ActionListener() {
					@Override
					public void actionPerformed(final ActionEvent e) {
						loadTemplate(entry.getValue());
					}
				};

				// add script to the secondary language-sorted menu structure
				if (langName != null) {
					final String langPath = "[by language]/" + langName + "/" + path;
					final JMenu langMenu = getMenu(templatesMenu, langPath, true);
					final JMenuItem langItem = new JMenuItem(label);
					langMenu.add(langItem);
					langItem.addActionListener(menuListener);
				}

				// add script to the primary Templates menu structure
				final JMenu menu = getMenu(templatesMenu, path, true);
				final JMenuItem item = new JMenuItem(label + langSuffix);
				menu.add(item);
				item.addActionListener(menuListener);
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
		final String ext = FileUtils.getExtension(path);
		final ScriptLanguage language =
			ext.isEmpty() ? null : scriptService.getLanguageByExtension(ext);
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
			setEditorPaneFileName(path.substring(path.lastIndexOf('/') + 1));
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
		setEditorPaneFileName(title);

		editorPane.setLanguageByFileName(title);
		updateLanguageMenu(editorPane.getCurrentLanguage());
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
			updateLanguageMenu(editorPane.getCurrentLanguage());
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
				// Compiled languages should not progress if their source is unsaved
				return !beforeCompiling;
			case JOptionPane.YES_OPTION:
				if (save()) return true;
		}

		return false;
	}

	@Override
	public void actionPerformed(final ActionEvent ae) {
		final Object source = ae.getSource();
		if (source == newFile) createNewDocument();
		else if (source == open) {
			final EditorPane editorPane = getEditorPane();
			final File defaultDir =
				editorPane.getFile() != null ? editorPane.getFile().getParentFile()
					: appService.getApp().getBaseDirectory();
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
		else if (source == addImport) {
			addImport(getSelectedClassNameOrAsk());
		}
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
		else if (source == toggleAutoCompletionMenu) {
			toggleAutoCompletion();
		}
		else if (source == savePreferences) {
			getEditorPane().savePreferences(tree.getTopLevelFoldersString());
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
			setFontSize(getEditorPane().getFontSize());
		}
		else if (source == nextTab) switchTabRelative(1);
		else if (source == previousTab) switchTabRelative(-1);
		else if (handleTabsMenu(source)) return;
	}

	private void toggleAutoCompletion() {
		for (int i = 0; i < tabbed.getTabCount(); i++) {
			final EditorPane editorPane = getEditorPane(i);
			editorPane.setAutoCompletionEnabled(toggleAutoCompletionMenu.isSelected());
		}
		prefService.put(TextEditor.class, "autoComplete", toggleAutoCompletionMenu.isSelected());
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
		checkForOutsideChanges();

		toggleWhiteSpaceLabeling.setSelected(editorPane.isWhitespaceVisible());

		editorPane.setLanguageByFileName(editorPane.getFileName());
		updateLanguageMenu(editorPane.getCurrentLanguage());

		setTitle();
	}

	public EditorPane getEditorPane(final int index) {
		return getTab(index).editorPane;
	}

	public void findOrReplace(final boolean doReplace) {
		findDialog.setLocationRelativeTo(this);

		// override search pattern only if
		// there is sth. selected
		final String selection = getTextArea().getSelectedText();
		if (selection != null) findDialog.setSearchPattern(selection);

		findDialog.show(doReplace);
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
		final Vector<Bookmark> bookmarks = new Vector<>();

		for (int i = 0; i < tabbed.getTabCount(); i++) {
			final TextEditorTab tab = (TextEditorTab) tabbed.getComponentAt(i);
			tab.editorPane.getBookmarks(tab, bookmarks);
		}

		final BookmarkDialog dialog = new BookmarkDialog(this, bookmarks);
		dialog.setVisible(true);
	}

	public boolean reload() {
		return reload("Reload the file?");
	}

	public boolean reload(final String message) {
		final EditorPane editorPane = getEditorPane();

		final File file = editorPane.getFile();
		if (file == null || !file.exists()) return true;

		final boolean modified = editorPane.fileChanged();
		final String[] options = { "Reload", "Do not reload" };
		if (modified) options[0] = "Reload (discarding changes)";
		switch (JOptionPane.showOptionDialog(this, message, "Reload",
			JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options,
			options[0])) {
			case 0:
				try {
					editorPane.open(file);
					return true;
				}
				catch (final IOException e) {
					error("Could not reload " + file.getPath());
				}

				updateLanguageMenu(editorPane.getCurrentLanguage());
				break;
		}
		return false;
	}

	public static boolean isBinary(final File file) {
		if (file == null) return false;
		// heuristic: read the first up to 8000 bytes, and say that it is binary if
		// it contains a NUL
		try (final FileInputStream in = new FileInputStream(file)) {
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
	public TextEditorTab newTab(final String content, final String language) {
		String lang = language;
		final TextEditorTab tab = open(null);
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

	public TextEditorTab open(final File file) {
		if (isBinary(file)) {
			try {
				uiService.show(ioService.open(file.getAbsolutePath()));
			} catch (IOException e) {
				e.printStackTrace();
			}
			return null;
		}

		try {
			TextEditorTab tab = (tabbed.getTabCount() == 0) ? null : getTab();
			TextEditorTab prior = tab;
			final boolean wasNew = tab != null && tab.editorPane.isNew();
			float font_size = 0; // to set the new editor's font like the last active one, if any
			if (!wasNew) {
				if (tabbed.getTabCount() > 0) font_size = getTab().getEditorPane().getFont().getSize2D();
				tab = new TextEditorTab(this);
				context.inject(tab.editorPane);
				tab.editorPane.loadPreferences();
				tab.editorPane.getDocument().addDocumentListener(this);
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
				setEditorPaneFileName(tab.editorPane.getFile());
				try {
					updateTabAndFontSize(true);
					if (font_size > 0) setFontSize(font_size);
					if (null != prior) {
						tab.setOrientation(prior.getOrientation());
						tab.setDividerLocation(prior.getDividerLocation());
					}
				}
				catch (final NullPointerException e) {
					/* ignore */
				}
			}
			if (file != null) openRecent.add(file.getAbsolutePath());

			updateLanguageMenu(tab.editorPane.getCurrentLanguage());

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
			final File ijDir = appService.getApp().getBaseDirectory();
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
		setEditorPaneFileName(file);
		openRecent.add(path);
		return true;
	}

	public boolean save() {
		final File file = getEditorPane().getFile();
		if (file == null) {
			return saveAs();
		}
		if (!write(file)) {
			return false;
		}

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

	/**
	 * @throws IOException
	 */
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
		try (final InputStream in = new FileInputStream(file)) {
			final byte[] buffer = new byte[(int) file.length()];
			in.read(buffer);
			return buffer;
		}
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
		if (null != this.getCurrentLanguage() && this.getCurrentLanguage().getLanguageName() != language.getLanguageName()) {
			this.scriptInfo = null;
		}
		getEditorPane().setLanguage(language, addHeader);

		setTitle();
		updateLanguageMenu(language);
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

	public void setEditorPaneFileName(final String baseName) {
		getEditorPane().setFileName(baseName);
	}

	public void setEditorPaneFileName(final File file) {
		final EditorPane editorPane = getEditorPane();
		editorPane.setFileName(file);

		// update language menu
		updateLanguageMenu(editorPane.getCurrentLanguage());
		updateGitDirectory();
	}

	void setTitle() {
		final EditorPane editorPane = getEditorPane();

		final boolean fileChanged = editorPane.fileChanged();
		final String fileName = editorPane.getFileName();
		final String title =
			(fileChanged ? "*" : "") + fileName +
				(executingTasks.isEmpty() ? "" : " (Running)");
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				setTitle(title); // to the main window
				// Update all tabs: could have changed
				for (int i = 0; i < tabbed.getTabCount(); i++)
					tabbed.setTitleAt(i, ((TextEditorTab) tabbed.getComponentAt(i))
						.getTitle());
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

	private final ArrayList<Executer> executingTasks = new ArrayList<>();

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
			final ArrayList<Thread> threads = new ArrayList<>();
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
			final TextEditorTab tab = (TextEditorTab) tabbed.getComponentAt(i);
			if (executer == tab.getExecuter()) {
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
			else write("Compiled languages must be saved before they can be run.");
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
			final TextEditorTab tab = getTab();
			tab.showOutput();
			execute(selectionOnly);
		}
		catch (final Throwable t) {
			t.printStackTrace();
		}
	}

	/**
	 * Run current script with the batch processor
	 */
	public void runBatch() {
		// get script from current tab
		String script = getTab().getEditorPane().getText();
		ScriptInfo scriptInfo = new ScriptInfo(context, "dummy."
				+ getCurrentLanguage().getExtensions().get(0),
				new StringReader(script));
		batchService.run(scriptInfo);
	}

	/** Invoke in the context of the event dispatch thread. */
	private void execute(final boolean selectionOnly) throws IOException {
		final String text;
		final TextEditorTab tab = getTab();
		if (selectionOnly) {
			final String selected = tab.getEditorPane().getSelectedText();
			if (selected == null) {
				error("Selection required!");
				text = null;
			}
			else text = selected + "\n"; // Ensure code blocks are terminated
		}
		else {
			text = tab.getEditorPane().getText();
		}
		
		execute(tab, text, false);
	}
	
	/**
	 * Invoke in the context of the event dispatch thread.
	 * 
	 * @param tab The {@link TextEditorTab} that is the source of the program to run.
	 * @param text The text expressing the program to run.
	 * @param writeCommandLog Whether to append the {@code text} to a log file for the appropriate language.
	 * @throws IOException Whether there was an issue with piping the command to the executer.
	 */
	private void execute(final TextEditorTab tab, final String text, final boolean writeCommandLog) throws IOException {

		tab.prepare();

		final JTextAreaWriter output = new JTextAreaWriter(tab.screen, log);
		final JTextAreaWriter errors = new JTextAreaWriter(errorScreen, log);
		final File file = getEditorPane().getFile();
		// Pipe current text into the runScript:
		final PipedInputStream pi = new PipedInputStream();
		final PipedOutputStream po = new PipedOutputStream(pi);
		// The Executer creates a Thread that
		// does the reading from PipedInputStream
		tab.setExecutor(new Executer(output, errors) {

			@Override
			public void execute() {
				try {
					evalScript(file == null ? getEditorPane().getFileName() : file
						.getAbsolutePath(), new InputStreamReader(pi), output, errors);
					output.flush();
					errors.flush();
					markCompileEnd();
					// For executions from the prompt
					if (writeCommandLog && null != text && text.trim().length() > 0) {
						writePromptLog(getEditorPane().getCurrentLanguage(), text);
					}
				}
				catch (final Throwable t) {
					output.flush();
					errors.flush();
					if (t instanceof ScriptException && t.getCause() != null &&
						t.getCause().getClass().getName().endsWith("CompileError"))
					{
						errorScreen.append("Compilation failed");
						tab.showErrors();
					}
					else {
						handleException(t);
					}
				}
				finally {
					tab.restore();
				}
			}
		});
		// Write into PipedOutputStream
		// from another Thread
		try {
			new Thread() {

				{
					setPriority(Thread.NORM_PRIORITY);
				}

				@Override
				public void run() {
					try (final PrintWriter pw = new PrintWriter(po)) {
						pw.write(text);
						pw.flush(); // will lock and wait in some cases
					}
				}
			}.start();
		}
		catch (final Throwable t) {
			t.printStackTrace();
		}
		finally {
			// Re-enable when all text to send has been sent
			tab.getEditorPane().setEditable(true);
		}
	}
	
	private String getPromptCommandsFilename(final ScriptLanguage language) {
		final String name = language.getLanguageName().replace('/', '_');
		return System.getProperty("user.home").replace('\\', '/') + "/.scijava/" + name + ".command.log";
	}
	
	/**
	 * Append the executed prompt command to the end of the language-specific log file,
	 * and then append a separator.
	 * 
	 * @param language The language used, to choose the right log file.
	 * @param text The command to append at the end of the log.
	 */
	private void writePromptLog(final ScriptLanguage language, final String text) {
		final String path = getPromptCommandsFilename(language);
		final File file = new File(path);
		try {
			boolean exists = file.exists();
			if (!exists) {
				// Ensure parent directories exist
				file.getParentFile().mkdirs();
				file.createNewFile(); // atomic
			}
			Files.write(Paths.get(path), Arrays.asList(new String[]{text, "#"}), Charset.forName("UTF-8"),
					StandardOpenOption.APPEND, StandardOpenOption.DSYNC);
		} catch (IOException e) {
			System.out.println("Failed to write executed prompt command to file " + path);
			e.printStackTrace();
		}
	}
	
	/**
	 * Parse the prompt command log for the given language and return the last 1000 lines.
	 * If the log is longer than 1000 lines, crop it.
	 * 
	 * @param language The language used, to choose the right log file.
	 * @return
	 */
	private ArrayList<String> loadPromptLog(final ScriptLanguage language) {
		final String path = getPromptCommandsFilename(language);
		final File file = new File(path);
		final ArrayList<String> lines = new ArrayList<String>();
		if (!file.exists()) return lines;
		RandomAccessFile ra = null;
		List<String> commands = new ArrayList<String>();
		try {
			ra = new RandomAccessFile(path, "r");
			final byte[] bytes = new byte[(int)ra.length()];
			ra.readFully(bytes);
			final String sep = System.getProperty("line.separator"); // used fy Files.write above
			commands.addAll(Arrays.asList(new String(bytes, Charset.forName("UTF-8")).split(sep + "#" + sep)));
			if (0 == commands.get(commands.size()-1).length()) commands.remove(commands.size() -1); // last entry is empty
		} catch (IOException e) {
			System.out.println("Failed to read history of prompt commands from file " + path);
			e.printStackTrace();
			return lines;
		} finally {
			try { if (null != ra) ra.close(); } catch (IOException e) { e.printStackTrace(); }
		}
		if (commands.size() > 1000) {
			commands = commands.subList(commands.size() - 1000, commands.size());
			// Crop the log: otherwise would grow unbounded
			final ArrayList<String> croppedLog = new ArrayList<String>();
			for (final String c : commands) {
				croppedLog.add(c);
				croppedLog.add("#");
			}
			try {
				Files.write(Paths.get(path + "-tmp"), croppedLog, Charset.forName("UTF-8"),
						StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.DSYNC);
				if (!new File(path + "-tmp").renameTo(new File(path))) {
					System.out.println("Could not rename command log file " + path + "-tmp to " + path);
				}
			} catch (Exception e) {
				System.out.println("Failed to crop history of prompt commands file " + path);
				e.printStackTrace();
			}
		}
		lines.addAll(commands);

		return lines;
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
				try (final Reader reader = evalScript(getEditorPane().getFile()
					.getPath(), new FileReader(file), output, errors))
				{
					output.flush();
					errors.flush();
					markCompileEnd();
				}
				catch (final Throwable e) {
					handleException(e);
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

	private static void append(final JTextArea textArea, final String text) {
		final int length = textArea.getDocument().getLength();
		textArea.insert(text, length);
		textArea.setCaretPosition(length);
	}
	
	public void markCompileStart() {
		markCompileStart(true);
	}

	public void markCompileStart(final boolean with_timestamp) {
		errorHandler = null;

		if (with_timestamp) {
			final String started =
					"Started " + getEditorPane().getFileName() + " at " + new Date() + "\n";
			append(errorScreen, started);
			append(getTab().screen, started);
		}
		final int offset = errorScreen.getDocument().getLength();
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

	private void switchTabRelative(final int delta) {
		final int count = tabbed.getTabCount();
		int index = ((tabbed.getSelectedIndex() + delta) % count);
		if (index < 0) {
			index += count;
		}

		switchTo(index);
	}

	private void removeTab(final int index) {
		final int menuItemIndex = index + tabsMenuTabsStart;

		tabbed.remove(index);
		tabsMenuItems.remove(tabsMenu.getItem(menuItemIndex));
		tabsMenu.remove(menuItemIndex);
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

	/** Updates the git directory to the git directory of the current file. */
	private void updateGitDirectory() {
		final EditorPane editorPane = getEditorPane();
		editorPane.setGitDirectory(new FileFunctions(this)
			.getGitDirectory(editorPane.getFile()));
	}

	public void addImport(final String className) {
		if (className != null) {
			new TokenFunctions(getTextArea()).addImport(className.trim());
		}
	}

	public void openHelp(final String className) {
		openHelp(className, true);
	}

	/**
	 * @param className
	 * @param withFrames
	 */
	public void openHelp(final String className, final boolean withFrames) {
		if (className == null) {
			// FIXME: This cannot be right.
			getSelectedClassNameOrAsk();
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
	private File openWithDialog(final File defaultDir) {
		return uiService.chooseFile(defaultDir, FileWidget.OPEN_STYLE);
	}

	/**
	 * Write a message to the output screen
	 *
	 * @param message The text to write
	 */
	public void write(String message) {
		final TextEditorTab tab = getTab();
		if (!message.endsWith("\n")) message += "\n";
		tab.screen.insert(message, tab.screen.getDocument().getLength());
	}

	public void writeError(String message) {
		getTab().showErrors();
		if (!message.endsWith("\n")) message += "\n";
		errorScreen.insert(message, errorScreen.getDocument().getLength());
	}

	private void error(final String message) {
		JOptionPane.showMessageDialog(this, message);
	}

	public void handleException(final Throwable e) {
		handleException(e, errorScreen);
		getTab().showErrors();
	}

	public static void
		handleException(final Throwable e, final JTextArea textArea)
	{
		final CharArrayWriter writer = new CharArrayWriter();
		try (final PrintWriter out = new PrintWriter(writer)) {
			e.printStackTrace(out);
			for (Throwable cause = e.getCause(); cause != null; cause =
					cause.getCause())
			{
				out.write("Caused by: ");
				cause.printStackTrace(out);
			}
		}
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
	
	private final class EditableScriptInfo extends ScriptInfo
	{	
		private String script;
		
		public EditableScriptInfo(final Context context, final String path, final Reader reader) {
			super(context, path, reader);
		}
		
		public void setScript(final Reader reader) throws IOException {
			final char[] buffer = new char[8192];
			final StringBuilder builder = new StringBuilder();

			int read;
			while ((read = reader.read(buffer)) != -1) {
				builder.append(buffer, 0, read);
			}

			this.script = builder.toString();
		}
		
		@Override
		public String getProcessedScript() {
			return null == this.script ? super.getProcessedScript() : this.script;
		}
	}

	private Reader evalScript(final String filename, Reader reader,
		final Writer output, final Writer errors) throws ModuleException
	{
		final ScriptLanguage language = getCurrentLanguage();
		
		// If there's no engine or the language has changed or the language is compiled from a file
		// then create the engine and module anew
		if (!this.incremental
			|| (this.incremental && null == this.scriptInfo)
		    || language.isCompiledLanguage()
		    || (null != this.scriptInfo
		        && null != this.scriptInfo.getLanguage()
		        && this.scriptInfo.getLanguage().getLanguageName()
		           != getCurrentLanguage().getLanguageName()))
		{
			if (respectAutoImports) {
				reader =
						DefaultAutoImporters.prefixAutoImports(context, language, reader,
								errors);
			}
			// create script module for execution
			this.scriptInfo = new EditableScriptInfo(context, filename, reader);

			// use the currently selected language to execute the script
			this.scriptInfo.setLanguage(language);
			
			this.module = this.scriptInfo.createModule();
			context.inject(this.module);
		} else {
			try {
				// Same engine, with persistent state
				this.scriptInfo.setScript( reader );
			} catch (IOException e) {
				log.error(e);
			}
		}
		
		// map stdout and stderr to the UI
		this.module.setOutputWriter(output);
		this.module.setErrorWriter(errors);

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
	
	public void setIncremental(final boolean incremental) {
		
		if (null == getCurrentLanguage()) {
			error("Select a language first!");
			return;
		}
		
		this.incremental = incremental;
		
		final JTextArea prompt = this.getTab().getPrompt();
		if (incremental) {
			getTab().getScreenAndPromptSplit().setDividerLocation(0.5);
			prompt.addKeyListener(new KeyAdapter() {
				private final ArrayList<String> commands = loadPromptLog(getCurrentLanguage());
				private int index = commands.size();
				{
					commands.add(""); // the current prompt text
				}
				private String unexecuted_command = "";
				@Override
				public void keyPressed(final KeyEvent ke) {
					int keyCode = ke.getKeyCode();
					if (KeyEvent.VK_ENTER == keyCode) {
						if (ke.isShiftDown() || ke.isAltDown() || ke.isAltGraphDown() || ke.isMetaDown() || ke.isControlDown()) {
							prompt.insert("\n", prompt.getCaretPosition());
							ke.consume();
							return;
						}
						final String text = prompt.getText();
						if (null == text || 0 == text.trim().length()) {
							ke.consume(); // avoid writing the line break
							return;
						}
						try {
							final JTextArea screen = getTab().screen;
							getTab().showOutput();
							screen.append("> " + text + "\n");
							// Update the last command in the history
							commands.set(commands.size() -1, prompt.getText());
							// Set the next command
							commands.add("");
							index = commands.size() - 1;
							// Execute
							markCompileStart(false); // weird method name, execute will call markCompileEnd
							execute(getTab(), text, true);
							prompt.setText("");
							screen.scrollRectToVisible(screen.modelToView(screen.getDocument().getLength()));
						} catch (Throwable t) {
							log.error(t);
						}
						ke.consume(); // avoid writing the line break
						return;
					}
					
					// If using arrows for navigating history
					if (getTab().updownarrows.isSelected()) {
						// Only if no modifiers are down
						if (!(ke.isShiftDown() || ke.isControlDown() || ke.isAltDown() || ke.isMetaDown())) {
							switch(keyCode) {
							case KeyEvent.VK_UP:
								keyCode = KeyEvent.VK_PAGE_UP;
								break;
							case KeyEvent.VK_DOWN:
								keyCode = KeyEvent.VK_PAGE_DOWN;
								break;
							}
						}
					}
					
					// control+p and control+n for navigating history
					if (ke.isControlDown()) {
						switch(keyCode) {
						case KeyEvent.VK_P:
							keyCode = KeyEvent.VK_PAGE_UP;
							break;
						case KeyEvent.VK_N:
							keyCode = KeyEvent.VK_PAGE_DOWN;
							break;
						}
					}
					
					if (KeyEvent.VK_PAGE_UP == keyCode) {
						// If last, update the stored command
						if (commands.size() -1 == index) {
							commands.set(commands.size() -1, prompt.getText());
						}
						if (index > 0) {
							prompt.setText(commands.get(--index));
						}
						ke.consume();
						return;
					} else if (KeyEvent.VK_PAGE_DOWN == keyCode) {
						if (index < commands.size() -1) {
							prompt.setText(commands.get(++index));
						}
						ke.consume();
						return;
					}
					
					// Update index and current command when editing an earlier command
					if (commands.size() -1 != index) {
						index = commands.size() -1;
						commands.set(commands.size() -1, prompt.getText());
					}
				}
			});
		} else {
			prompt.setText(""); // clear
			prompt.setEnabled(false);
			for (final KeyListener kl : prompt.getKeyListeners()) {
				prompt.removeKeyListener(kl);
			}
			getTab().getScreenAndPromptSplit().setDividerLocation(1.0);
		}
	}

	private String adjustPath(final String path, final String langName) {
		String result = path.replace('_', ' ');

		// HACK: For templates nested beneath their language name,
		// place them in a folder called "Uncategorized" instead. This avoids
		// menu redundancy when existing script templates are populated
		// under the new Templates menu structure.
		//
		// For example, a script at script_templates/BeanShell/Greeting.bsh
		// was previously placed in:
		//
		//    Templates > BeanShell > Greeting
		//
		// but under the current approach will be placed at:
		//
		//    Templates > [by language] > BeanShell > BeanShell > Greeting
		//    Templates > BeanShell > Greeting (BeanShell)
		//
		// both of which are redundant and annoying.
		//
		// This hack instead places that script at:
		//
		//    Templates > Uncategorized > Greeting (BeanShell)
		//    Templates > [by language] > BeanShell > Uncategorized > Greeting
		if (langName != null &&
			path.toLowerCase().startsWith(langName.toLowerCase() + "/"))
		{
			result = "Uncategorized" + result.substring(langName.length());
		}

		return result;
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

	@Override
	public void insertUpdate(final DocumentEvent e) {
		setTitle();
		checkForOutsideChanges();
	}

	@Override
	public void removeUpdate(final DocumentEvent e) {
		setTitle();
		checkForOutsideChanges();
	}

	@Override
	public void changedUpdate(final DocumentEvent e) {
		setTitle();
	}

	public void setFontSize(final float size) {
		if (getEditorPane().getFontSize() != size)
			getEditorPane().setFontSize(size);
		changeFontSize(errorScreen, size);
		changeFontSize(getTab().screen, size);
		changeFontSize(getTab().prompt, size);
		updateTabAndFontSize(false);
		tree.setFont(tree.getFont().deriveFont(size));
	}

	private void changeFontSize(final JTextArea a, final float size) {
		a.setFont(a.getFont().deriveFont(size));
	}
}
