/*
 * #%L
 * Script Editor and Interpreter for SciJava script languages.
 * %%
 * Copyright (C) 2009 - 2025 SciJava developers.
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
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
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
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipException;

import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.DefaultHighlighter.DefaultHighlightPainter;
import javax.swing.text.Position;
import javax.swing.tree.TreePath;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory;
import org.fife.ui.rtextarea.ClipboardHistory;
import org.fife.ui.rtextarea.Macro;
import org.fife.ui.rtextarea.RTextArea;
import org.fife.ui.rtextarea.RTextAreaEditorKit;
import org.fife.ui.rtextarea.RTextAreaEditorKit.SetReadOnlyAction;
import org.fife.ui.rtextarea.RTextAreaEditorKit.SetWritableAction;
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
import org.scijava.options.OptionsService;
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
import org.scijava.thread.ThreadService;
import org.scijava.ui.CloseConfirmable;
import org.scijava.ui.UIService;
import org.scijava.ui.swing.script.autocompletion.ClassUtil;
import org.scijava.ui.swing.script.commands.ChooseFontSize;
import org.scijava.ui.swing.script.commands.ChooseTabSize;
import org.scijava.ui.swing.script.commands.GitGrep;
import org.scijava.ui.swing.script.commands.KillScript;
import org.scijava.util.FileUtils;
import org.scijava.util.MiscUtils;
import org.scijava.util.POM;
import org.scijava.util.PlatformUtils;
import org.scijava.util.Types;
import org.scijava.widget.FileWidget;

import com.formdev.flatlaf.FlatLaf;

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
 * @author Albert Cardona
 * @author Tiago Ferreira
 */
public class TextEditor extends JFrame implements ActionListener,
	ChangeListener, CloseConfirmable, DocumentListener
{

	private static final Set<String> TEMPLATE_PATHS = new HashSet<>();
//	private static final int BORDER_SIZE = 4;
	public static final String AUTO_IMPORT_PREFS = "script.editor.AutoImport";
	public static final String WINDOW_HEIGHT = "script.editor.height";
	public static final String WINDOW_WIDTH = "script.editor.width";
	public static final int DEFAULT_WINDOW_WIDTH = 800;
	public static final int DEFAULT_WINDOW_HEIGHT = 600;
	public static final String MAIN_DIV_LOCATION = "script.editor.main.divLocation";
	public static final String TAB_DIV_LOCATION = "script.editor.tab.divLocation";
	public static final String TAB_DIV_ORIENTATION = "script.editor.tab.divOrientation";
	public static final String REPL_DIV_LOCATION = "script.editor.repl.divLocation";
	public static final String LAST_LANGUAGE = "script.editor.lastLanguage";

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
			close, undo, redo, cut, copy, paste, find, selectAll, kill,
			gotoLine, makeJar, makeJarWithSource, removeUnusedImports, sortImports,
			removeTrailingWhitespace, findNext, findPrevious, openHelp, addImport,
			nextError, previousError, openHelpWithoutFrames, nextTab,
			previousTab, runSelection, extractSourceJar, askChatGPTtoGenerateCode,
			openSourceForClass,
			//openSourceForMenuItem, // this never had an actionListener!??
			openMacroFunctions, decreaseFontSize, increaseFontSize, chooseFontSize,
			chooseTabSize, gitGrep, replaceTabsWithSpaces,
			replaceSpacesWithTabs, zapGremlins,openClassOrPackageHelp;
	private RecentFilesMenuItem openRecent;
	private JMenu editMenu, gitMenu, tabsMenu, fontSizeMenu, tabSizeMenu, toolsMenu,
			runMenu;
	private int tabsMenuTabsStart;
	private Set<JMenuItem> tabsMenuItems;
	private FindAndReplaceDialog findDialog;
	private JCheckBoxMenuItem autoSave, wrapLines, tabsEmulated, autoImport,
			autocompletion, fallbackAutocompletion, keylessAutocompletion,
			markOccurences, paintTabs, whiteSpace, marginLine, lockPane;
	private ButtonGroup themeRadioGroup;
	private JTextArea errorScreen = new JTextArea();

	private final FileSystemTree tree;
	private final JSplitPane body;

	private int compileStartOffset;
	private Position compileStartPosition;
	private ErrorHandler errorHandler;

	private boolean respectAutoImports;
	private String activeTheme;
	private int[] panePositions;


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
	private ThreadService threadService;
	@Parameter
	private AppService appService;
	@Parameter
	private BatchService batchService;
	@Parameter(required = false)
	private OptionsService optionsService;

	private Map<ScriptLanguage, JRadioButtonMenuItem> languageMenuItems;
	private JRadioButtonMenuItem noneLanguageItem;
	
	private EditableScriptInfo scriptInfo;
	private ScriptModule module;
	private boolean incremental = false;
	private DragSource dragSource;
	private boolean layoutLoading = true;
	private OutlineTreePanel sourceTreePanel;
	protected final CommandPalette cmdPalette;

	public static final ArrayList<TextEditor> instances = new ArrayList<>();
	public static final ArrayList<Context> contexts = new ArrayList<>();

	public TextEditor(final Context context) {

		super("Script Editor");
		instances.add(this);
		contexts.add(context);
		context.inject(this);
		initializeTokenMakers();

		// NB: All panes must be initialized before menus are assembled!
		tabbed = GuiUtils.getJTabbedPane();
		tree = new FileSystemTree(log);
		final JTabbedPane sideTabs = GuiUtils.getJTabbedPane();
		sideTabs.addTab("File Explorer", new FileSystemTreePanel(tree, context));
		sideTabs.addTab("Outline", sourceTreePanel = new OutlineTreePanel());
		body = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sideTabs, tabbed);

		// These items are dynamic and need to be initialized before EditorPane creation
		initializeDynamicMenuComponents();

		// -- BEGIN MENUS --

		// Initialize menu
		final int ctrl = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
		final int shift = ActionEvent.SHIFT_MASK;
		final JMenuBar mbar = new JMenuBar();
		setJMenuBar(mbar);

		// -- File menu --

		final JMenu file = new JMenu("File");
		file.setMnemonic(KeyEvent.VK_F);
		newFile = addToMenu(file, "New", KeyEvent.VK_N, ctrl);
		newFile.setMnemonic(KeyEvent.VK_N);
		open = addToMenu(file, "Open...", KeyEvent.VK_O, ctrl);
		open.setMnemonic(KeyEvent.VK_O);
		openRecent = new RecentFilesMenuItem(prefService, this);
		openRecent.setMnemonic(KeyEvent.VK_R);
		file.add(openRecent);
		file.addSeparator();
		save = addToMenu(file, "Save", KeyEvent.VK_S, ctrl);
		save.setMnemonic(KeyEvent.VK_S);
		saveas = addToMenu(file, "Save As...", KeyEvent.VK_S, ctrl + shift);
		saveas.setMnemonic(KeyEvent.VK_A);
		file.addSeparator();
		makeJar = addToMenu(file, "Export as JAR...", 0, 0);
		makeJar.setMnemonic(KeyEvent.VK_E);
		makeJarWithSource = addToMenu(file, "Export as JAR (With Source)...", 0, 0);
		makeJarWithSource.setMnemonic(KeyEvent.VK_X);
		file.addSeparator();
		lockPane  = new JCheckBoxMenuItem("Lock (Make Read Only)");
		lockPane.setToolTipText("Protects file from accidental editing");
		file.add(lockPane);
		lockPane.addActionListener(e -> {
			if (lockPane.isSelected()) {
				new SetReadOnlyAction().actionPerformedImpl(e, getEditorPane());
			} else {
				new SetWritableAction().actionPerformedImpl(e, getEditorPane());
			}
		});
		JMenuItem jmi = new JMenuItem("Revert...");
		jmi.addActionListener(e -> {
			if (getEditorPane().isLocked()) {
				error("File is currently locked.");
				return;
			}
			final File f = getEditorPane().getFile();
			if (f == null || !f.exists()) {
				error(getEditorPane().getFileName() + "\nhas not been saved or its file is not available.");
			} else {
				reloadRevert("Revert to Saved File? Any unsaved changes will be lost.", "Revert");
			}
		});
		file.add(jmi);
		file.addSeparator();
		jmi = new JMenuItem("Show in System Explorer");
		jmi.addActionListener(e -> {
			final File f = getEditorPane().getFile();
			if (f == null || !f.exists()) {
				error(getEditorPane().getFileName() + "\nhas not been saved or its file is not available.");
			} else {
				try {
					Desktop.getDesktop().open(f.getParentFile());
				} catch (final Exception | Error ignored) {
					error(getEditorPane().getFileName() + "\ndoes not seem to be accessible.");
				}
			}
		});
		file.add(jmi);
		file.addSeparator();
		close = addToMenu(file, "Close", KeyEvent.VK_W, ctrl);

		mbar.add(file);

		// -- Edit menu --
		editMenu = new JMenu("Edit"); // cannot be populated here. see #assembleEditMenu()
		editMenu.setMnemonic(KeyEvent.VK_E);
		mbar.add(editMenu);

		// -- Language menu --

		languageMenuItems = new LinkedHashMap<>();
		final Set<Integer> usedShortcuts = new HashSet<>();
		final JMenu languages = new JMenu("Language");
		languages.setMnemonic(KeyEvent.VK_L);
		final ButtonGroup group = new ButtonGroup();

		// get list of languages, and sort them by name
		final ArrayList<ScriptLanguage> list =
			new ArrayList<>(scriptService.getLanguages());
		Collections.sort(list, (l1, l2) -> {
			final String name1 = l1.getLanguageName();
			final String name2 = l2.getLanguageName();
			return MiscUtils.compare(name1, name2);
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
			item.addActionListener(e -> setLanguage(language, true));

			group.add(item);
			languages.add(item);
		}
		noneLanguageItem.setSelected(true);
		mbar.add(languages);

		// -- Templates menu --

		final JMenu templates = new JMenu("Templates");
		templates.setMnemonic(KeyEvent.VK_T);
		addTemplates(templates);
		mbar.add(templates);

		// -- Run menu --

		runMenu = new JMenu("Run");
		runMenu.setMnemonic(KeyEvent.VK_R);

		compileAndRun = addToMenu(runMenu, "Compile and Run", KeyEvent.VK_R, ctrl);
		compileAndRun.setMnemonic(KeyEvent.VK_R);

		runSelection =
			addToMenu(runMenu, "Run Selected Code", KeyEvent.VK_R, ctrl | shift);
		runSelection.setMnemonic(KeyEvent.VK_S);

		compile = addToMenu(runMenu, "Compile", KeyEvent.VK_C, ctrl | shift);
		compile.setMnemonic(KeyEvent.VK_C);
		autoSave = new JCheckBoxMenuItem("Auto-save Before Compiling");
		runMenu.add(autoSave);

		runMenu.addSeparator();
		nextError = addToMenu(runMenu, "Next Error", KeyEvent.VK_F4, 0);
		nextError.setMnemonic(KeyEvent.VK_N);
		previousError = addToMenu(runMenu, "Previous Error", KeyEvent.VK_F4, shift);
		previousError.setMnemonic(KeyEvent.VK_P);
		final JMenuItem clearHighlights = new JMenuItem("Clear Marked Errors");
		clearHighlights.addActionListener(e -> getEditorPane().getErrorHighlighter().reset());
		runMenu.add(clearHighlights);
		runMenu.addSeparator();

		kill = addToMenu(runMenu, "Kill Running Script...", 0, 0);
		kill.setMnemonic(KeyEvent.VK_K);
		kill.setEnabled(false);

		mbar.add(runMenu);

		// -- Tools menu --

		toolsMenu = new JMenu("Tools");
		toolsMenu.setMnemonic(KeyEvent.VK_O);
		cmdPalette = new CommandPalette(this);
		cmdPalette.install(toolsMenu);
	
		GuiUtils.addMenubarSeparator(toolsMenu, "Imports:");
		addImport = addToMenu(toolsMenu, "Add Import...", 0, 0);
		addImport.setMnemonic(KeyEvent.VK_I);
		respectAutoImports = prefService.getBoolean(getClass(), AUTO_IMPORT_PREFS, false);
		autoImport =
			new JCheckBoxMenuItem("Auto-import (Deprecated)", respectAutoImports);
		autoImport.setToolTipText("Automatically imports common classes before running code");
		autoImport.addItemListener(e -> {
			respectAutoImports = e.getStateChange() == ItemEvent.SELECTED;
			prefService.put(getClass(), AUTO_IMPORT_PREFS, respectAutoImports);
			if (respectAutoImports)
				write("Auto-imports on. Lines associated with execution errors cannot be marked");
			else
				write("Auto-imports off. Lines associated with execution errors can be marked");
		});
		toolsMenu.add(autoImport);
		removeUnusedImports = addToMenu(toolsMenu, "Remove Unused Imports", 0, 0);
		removeUnusedImports.setMnemonic(KeyEvent.VK_U);
		sortImports = addToMenu(toolsMenu, "Sort Imports", 0, 0);
		sortImports.setMnemonic(KeyEvent.VK_S);

		GuiUtils.addMenubarSeparator(toolsMenu, "Source & APIs:");
		extractSourceJar = addToMenu(toolsMenu, "Extract Source Jar...", 0, 0);
		extractSourceJar.setMnemonic(KeyEvent.VK_E);
		openSourceForClass = addToMenu(toolsMenu, "Open Java File for Class...", 0, 0);
		openSourceForClass.setMnemonic(KeyEvent.VK_J);
		//openSourceForMenuItem = addToMenu(toolsMenu, "Open Java File for Menu Item...", 0, 0);
		//openSourceForMenuItem.setMnemonic(KeyEvent.VK_M);

		GuiUtils.addMenubarSeparator(toolsMenu, "chatGPT");
		askChatGPTtoGenerateCode = addToMenu(toolsMenu, "Ask chatGPT...", 0, 0);


		addScritpEditorMacroCommands(toolsMenu);
		mbar.add(toolsMenu);

		// -- Git menu --

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
		mbar.add(gitMenu);

		// -- Window Menu (previously labeled as Tabs menu --
		tabsMenu = new JMenu("Window");
		tabsMenu.setMnemonic(KeyEvent.VK_W);
		GuiUtils.addMenubarSeparator(tabsMenu, "Panes:");
		// Assume initial status from prefs or panel visibility
		final JCheckBoxMenuItem jcmi1 = new JCheckBoxMenuItem("Side Pane",
				prefService.getInt(getClass(), MAIN_DIV_LOCATION, body.getDividerLocation()) > 0
						|| isLeftPaneExpanded(body));
		jcmi1.addItemListener(e -> collapseSplitPane(0, !jcmi1.isSelected()));
		tabsMenu.add(jcmi1);
		// Console not initialized. Assume it is displayed if no prefs read
		final JCheckBoxMenuItem jcmi2 = new JCheckBoxMenuItem("Console",
				prefService.getInt(getClass(), TAB_DIV_LOCATION, 1) > 0);
		jcmi2.addItemListener(e -> collapseSplitPane(1, !jcmi2.isSelected()));
		tabsMenu.add(jcmi2);
		final JMenuItem mi = new JMenuItem("Reset Layout...");
		mi.addActionListener(e -> {
			if (confirm("Reset Location of Console and File Explorer?", "Reset Layout?", "Reset")) {
				resetLayout();
				jcmi1.setSelected(true);
				jcmi2.setSelected(true);
			}
		});
		tabsMenu.add(mi);
		GuiUtils.addMenubarSeparator(tabsMenu, "Tabs:");
		nextTab = addToMenu(tabsMenu, "Next Tab", KeyEvent.VK_PAGE_DOWN, ctrl);
		nextTab.setMnemonic(KeyEvent.VK_N);
		previousTab =
			addToMenu(tabsMenu, "Previous Tab", KeyEvent.VK_PAGE_UP, ctrl);
		previousTab.setMnemonic(KeyEvent.VK_P);
		tabsMenu.addSeparator();
		tabsMenuTabsStart = tabsMenu.getItemCount();
		tabsMenuItems = new HashSet<>();
		mbar.add(tabsMenu);

		// -- Options menu --

		final JMenu options = new JMenu("Options");
		options.setMnemonic(KeyEvent.VK_O);

		// Font adjustments
		GuiUtils.addMenubarSeparator(options, "Font:");
		decreaseFontSize =
			addToMenu(options, "Decrease Font Size", KeyEvent.VK_MINUS, ctrl);
		decreaseFontSize.setMnemonic(KeyEvent.VK_D);
		increaseFontSize =
			addToMenu(options, "Increase Font Size", KeyEvent.VK_PLUS, ctrl);
		increaseFontSize.setMnemonic(KeyEvent.VK_C);

		fontSizeMenu = new JMenu("Font Size");
		fontSizeMenu.setMnemonic(KeyEvent.VK_Z);
		final boolean[] fontSizeShortcutUsed = new boolean[10];
		final ButtonGroup buttonGroup = new ButtonGroup();
		for (final int size : new int[] { 8, 10, 12, 16, 20, 28, 42 }) {
			final JRadioButtonMenuItem item =
				new JRadioButtonMenuItem("" + size + " pt");
			item.addActionListener(event -> setFontSize(size));
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
		options.add(fontSizeMenu);

		GuiUtils.addMenubarSeparator(options, "Indentation:");
		tabsEmulated = new JCheckBoxMenuItem("Indent Using Spaces");
		tabsEmulated.setMnemonic(KeyEvent.VK_S);
		tabsEmulated.addItemListener(e -> setTabsEmulated(tabsEmulated.getState()));
		options.add(tabsEmulated);
		tabSizeMenu = new JMenu("Tab Width");
		tabSizeMenu.setMnemonic(KeyEvent.VK_T);
		final ButtonGroup bg = new ButtonGroup();
		for (final int size : new int[] { 2, 4, 8 }) {
			final JRadioButtonMenuItem item = new JRadioButtonMenuItem("" + size);
			item.addActionListener(event -> {
				getEditorPane().setTabSize(size);
				updateTabAndFontSize(false);
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
		options.add(tabSizeMenu);
		replaceSpacesWithTabs = addToMenu(options, "Replace Spaces With Tabs", 0, 0);
		replaceTabsWithSpaces = addToMenu(options, "Replace Tabs With Spaces", 0, 0);

		GuiUtils.addMenubarSeparator(options, "View:");
		options.add(markOccurences);
		options.add(paintTabs);
		options.add(marginLine);
		options.add(whiteSpace);
		options.add(wrapLines);
		options.add(applyThemeMenu());

		GuiUtils.addMenubarSeparator(options, "Code Completions:");
		options.add(autocompletion);
		options.add(keylessAutocompletion);
		options.add(fallbackAutocompletion);

		options.addSeparator();
		appendPreferences(options);
		mbar.add(options);
		mbar.add(helpMenu());

		// -- END MENUS --

		// add tab-related listeners
		tabbed.addChangeListener(this);
		sideTabs.addChangeListener(e -> {
			if (sideTabs.getSelectedIndex() == 1)
				sourceTreePanel.rebuildSourceTree(getTab(tabbed.getSelectedIndex()).editorPane);
		});

		// Add the editor and output area
		new FileDrop(tabbed, files -> {
			final ArrayList<File> filteredFiles = new ArrayList<>();
			assembleFlatFileCollection(filteredFiles, files);
			if (filteredFiles.isEmpty()) {
				warn("None of the dropped file(s) seems parseable.");
				return;
			}
			if (filteredFiles.size() < 10
					|| confirm("Confirm loading of " + filteredFiles.size() + " items?", "Confirm?", "Load")) {
				filteredFiles.forEach(f -> open(f));
			}
		});
		open(null); // make sure the editor pane is added
		getContentPane().setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));

		// Tweaks for JSplitPane
		body.setOneTouchExpandable(true);
		body.addPropertyChangeListener(evt -> {
			if ("dividerLocation".equals(evt.getPropertyName())) saveWindowSizeToPrefs();
		});

		// Tweaks for FileSystemTree
		tree.addTopLevelFoldersFrom(getEditorPane().loadFolders()); // Restore top-level directories
		dragSource = new DragSource();
		dragSource.createDefaultDragGestureRecognizer(tree, DnDConstants.ACTION_COPY, new DragAndDrop());
		tree.ignoreExtension("class");
		tree.setMinimumSize(new Dimension(200, 600));
		tree.addLeafListener(f -> {
			final String name = f.getName();
			final int idot = name.lastIndexOf('.');
			if (idot > -1) {
				final String ext = name.substring(idot + 1);
				final ScriptLanguage lang = scriptService.getLanguageByExtension(ext);
				if (null != lang) {
					open(f);
					return;
				}
			}
			if (isBinary(f)) {
				log.debug("isBinary: " + true);
				try {
					final Object o = ioService.open(f.getAbsolutePath());
					// Open in whatever way possible
					if (null != o) uiService.show(o);
					else error("Could not open the file at\n" + f.getAbsolutePath());
					return;
				}
				catch (final Exception e) {
					log.error(e);
					error("Could not open file at\n" + f);
				}
			}
			// Ask:
			if (confirm("Really try to open file " + name + " in a tab?", "Confirm", "Open")) {
				open(f);
			}
		});
		getContentPane().add(body);

		// for Eclipse and MS Visual Studio lovers
		addAccelerator(compileAndRun, KeyEvent.VK_F11, 0, true);
		addAccelerator(compileAndRun, KeyEvent.VK_F5, 0, true);
		compileAndRun.setToolTipText("Also triggered by F5 or F11");
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
				cmdPalette.dispose();
				dispose();
			}
		});

		addWindowFocusListener(new WindowAdapter() {

			@Override
			public void windowGainedFocus(final WindowEvent e) {
				checkForOutsideChanges();
			}
		});

		// Tweaks for Console
		errorScreen.setFont(getEditorPane().getFont());
		errorScreen.setEditable(false);
		errorScreen.setLineWrap(false);
		applyConsolePopupMenu(errorScreen);

		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

		try {
			threadService.invoke(() -> {
				pack();
				body.setDividerLocation(0.2); // Important!: will be read as prefs. default
				getTab().setREPLVisible(false);
				loadWindowSizePreferences();
				pack();
			});
		}
		catch (final Exception ie) {
			/* ignore */
			log.debug(ie);
		}
		findDialog = new FindAndReplaceDialog(this);

		// Save the layout when window is resized.
		addComponentListener(new ComponentAdapter() {

			@Override
			public void componentResized(final ComponentEvent e) {
				saveWindowSizeToPrefs();
			}
		});

		setLocationRelativeTo(null); // center on screen

		// HACK: Avoid weird macOS bug where window becomes tiny
		// in bottom left corner if centered while maximized.
		int y = getLocation().y - 11;
		if (y < 0) y = 0;
		setLocation(getLocation().x, y);

		open(null);

		final EditorPane editorPane = getEditorPane();
		// If dark L&F and using the default theme, assume 'dark' theme
		applyTheme((GuiUtils.isDarkLaF() && "default".equals(editorPane.themeName())) ? "dark" : editorPane.themeName(),
				true);

		// Ensure font sizes are consistent across all panels
		setFontSize(getEditorPane().getFontSize());
		// Ensure menu commands are up-to-date
		updateUI(true);
		// Store locations of splitpanes
		panePositions = new int[]{body.getDividerLocation(), getTab().getDividerLocation()};
		editorPane.requestFocus();
	}

	private void resetLayout() {
		body.setDividerLocation(.2d);
		getTab().setOrientation(JSplitPane.VERTICAL_SPLIT);
		getTab().setDividerLocation((incremental) ? .7d : .75d);
		if (incremental)
			getTab().getScreenAndPromptSplit().setDividerLocation(.5d);
		getTab().setREPLVisible(incremental);
		pack();
	}

	private void assembleEditMenu() {
		// requires an existing instance of an EditorPane
		final int ctrl = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
		final int shift = ActionEvent.SHIFT_MASK;
		undo = addToMenu(editMenu, "Undo", KeyEvent.VK_Z, ctrl);
		redo = addToMenu(editMenu, "Redo", KeyEvent.VK_Y, ctrl);
		editMenu.addSeparator();
		selectAll = addToMenu(editMenu, "Select All", KeyEvent.VK_A, ctrl);
		cut = addToMenu(editMenu, "Cut", KeyEvent.VK_X, ctrl);
		copy = addToMenu(editMenu, "Copy", KeyEvent.VK_C, ctrl);
		addMappedActionToMenu(editMenu, "Copy as Styled Text", EditorPaneActions.rstaCopyAsStyledTextAction, false);
		paste = addToMenu(editMenu, "Paste", KeyEvent.VK_V, ctrl);
		addMappedActionToMenu(editMenu, "Paste History...", EditorPaneActions.clipboardHistoryAction, true);
		GuiUtils.addMenubarSeparator(editMenu, "Find:");
		find = addToMenu(editMenu, "Find/Replace...", KeyEvent.VK_F, ctrl);
		find.setMnemonic(KeyEvent.VK_F);
		findNext = addToMenu(editMenu, "Find Next", KeyEvent.VK_F3, 0);
		findNext.setMnemonic(KeyEvent.VK_N);
		findPrevious = addToMenu(editMenu, "Find Previous", KeyEvent.VK_F3, shift);
		findPrevious.setMnemonic(KeyEvent.VK_P);

		GuiUtils.addMenubarSeparator(editMenu, "Go To:");
		gotoLine = addToMenu(editMenu, "Go to Line...", KeyEvent.VK_G, ctrl);
		gotoLine.setMnemonic(KeyEvent.VK_G);
		addMappedActionToMenu(editMenu, "Go to Matching Bracket", EditorPaneActions.rstaGoToMatchingBracketAction, false);

		final JMenuItem gotoType = new JMenuItem("Go to Type...");
		// we could retrieve the accelerator from paneactions but this may not work, if e.g., an
		// unsupported syntax (such IJM) has been opened at startup, so we'll just specify it manually
		//gotoType.setAccelerator(getEditorPane().getPaneActions().getAccelerator("GoToType"));
		gotoType.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, ctrl + shift));
		gotoType.addActionListener(e -> {
			try {
				getTextArea().getActionMap().get("GoToType").actionPerformed(e);
			} catch (final Exception | Error ignored) {
				error("\"Goto Type\" not availabe for current scripting language.");
			}
		});
		editMenu.add(gotoType);

		GuiUtils.addMenubarSeparator(editMenu, "Bookmarks:");
		addMappedActionToMenu(editMenu, "Next Bookmark", EditorPaneActions.rtaNextBookmarkAction, false);
		addMappedActionToMenu(editMenu, "Previous Bookmark", EditorPaneActions.rtaPrevBookmarkAction, false);
		final JMenuItem toggB = addMappedActionToMenu(editMenu, "Toggle Bookmark",
				EditorPaneActions.rtaToggleBookmarkAction, false);
		toggB.setToolTipText("Alternatively, click on left bookmark gutter near the line number");
		final JMenuItem listBookmarks = addToMenu(editMenu, "List Bookmarks...", 0, 0);
		listBookmarks.setMnemonic(KeyEvent.VK_L);
		listBookmarks.addActionListener(e -> listBookmarks());
		final JMenuItem clearBookmarks = addToMenu(editMenu, "Clear Bookmarks...", 0, 0);
		clearBookmarks.addActionListener(e -> clearAllBookmarks());

		GuiUtils.addMenubarSeparator(editMenu, "Utilities:");
		final JMenuItem commentJMI = addMappedActionToMenu(editMenu, "Toggle Comment",
				EditorPaneActions.rstaToggleCommentAction, true);
		commentJMI.setToolTipText("Alternative shortcut: "
				+ getEditorPane().getPaneActions().getAcceleratorLabel(EditorPaneActions.epaToggleCommentAltAction));
		addMappedActionToMenu(editMenu, "Insert Time Stamp", EditorPaneActions.rtaTimeDateAction, true);
		removeTrailingWhitespace = addToMenu(editMenu, "Remove Trailing Whitespace", 0, 0);
		zapGremlins = addToMenu(editMenu, "Zap Gremlins", 0, 0);
		zapGremlins.setToolTipText("Removes invalid (non-printable) ASCII characters");
	}

	private void addScritpEditorMacroCommands(final JMenu menu) {
		GuiUtils.addMenubarSeparator(menu, "Script Editor Macros:");
		final JMenuItem startMacro = new JMenuItem("Start/Resume Macro Recording");
		startMacro.addActionListener(e -> {
			if (getEditorPane().isLocked()) {
				error("File is currently locked.");
				return;
			}
			final String state = (RTextArea.getCurrentMacro() == null) ? "on" : "resumed";
			write("Script Editor: Macro recording " + state);
			RTextArea.beginRecordingMacro();
		});
		menu.add(startMacro);
		final JMenuItem pauseMacro = new JMenuItem("Pause Macro Recording...");
		pauseMacro.addActionListener(e -> {
			if (!RTextArea.isRecordingMacro() || RTextArea.getCurrentMacro() == null) {
				warn("No Script Editor Macro recording exists.");
			} else {
				RTextArea.endRecordingMacro();
				final int nSteps = RTextArea.getCurrentMacro().getMacroRecords().size();
				write("Script Editor: Macro recording off: " + nSteps + " event(s)/action(s) recorded.");
				if (nSteps == 0) {
					RTextArea.loadMacro(null);
				}
			}
		});
		menu.add(pauseMacro);
		final JMenuItem endMacro = new JMenuItem("Stop/Save Recording...");
		endMacro.addActionListener(e -> {
			pauseMacro.doClick();
			if (RTextArea.getCurrentMacro() != null) {
				final File fileToSave = getMacroFile(false);
				if (fileToSave != null) {
					try {
						RTextArea.getCurrentMacro().saveToFile(fileToSave);
					} catch (final IOException e1) {
						error(e1.getMessage());
						e1.printStackTrace();
					}
				}
			}
		});
		menu.add(endMacro);
		final JMenuItem clearMacro = new JMenuItem("Clear Recorded Macro...");
		clearMacro.addActionListener(e -> {
			if (RTextArea.getCurrentMacro() == null) {
				warn("Nothing to clear: No macro has been recorded.");
				return;
			}
			if (confirm("Clear Recorded Macro(s)?", "Clear Recording(s)?", "Clear")) {
				RTextArea.loadMacro(null);
				write("Script Editor: Recorded macro(s) cleared.");
			}
		});
		menu.add(clearMacro);
		final JMenuItem playMacro = new JMenuItem("Run Recorded Macro");
		playMacro.setToolTipText("Runs current recordings. Prompts for\nrecordings file if no recordings exist");
		playMacro.addActionListener(e -> {
			if (getEditorPane().isLocked()) {
				error("File is currently locked.");
				return;
			}
			if (null == RTextArea.getCurrentMacro()) {
				final File fileToOpen = getMacroFile(true);
				if (fileToOpen != null) {
					try {
						RTextArea.loadMacro(new Macro(fileToOpen));
					} catch (final IOException e1) {
						error(e1.getMessage());
						e1.printStackTrace();
					}
				}
			}
			if (RTextArea.isRecordingMacro()) {
				if (confirm("Recording must be paused before execution. Pause recording now?", "Pause and Run?",
						"Pause and Run")) {
					RTextArea.endRecordingMacro();
					write("Script Editor: Recording paused");
				} else {
					return;
				}
			}
			if (RTextArea.getCurrentMacro() != null) {
				final int actions = RTextArea.getCurrentMacro().getMacroRecords().size();
				write("Script Editor: Running recorded macro [" + actions + " event(s)/action(s)]");
				try {
					getTextArea().playbackLastMacro();
				} catch (final Exception | Error ex) {
					error("An Exception occured while running macro. See Console for details");
					ex.printStackTrace();
				}
			}
		});
		menu.add(playMacro);
	}

	private File getMacroFile(final boolean openOtherwiseSave) {
		final String msg = (openOtherwiseSave) ? "No macros have been recorded. Load macro from file?"
				: "Recording Stopped. Save recorded macro to local file?";
		final String title = (openOtherwiseSave) ? "Load from File?" : "Save to File?";
		final String yesLabel = (openOtherwiseSave) ? "Load" : "Save";
		if (confirm(msg, title, yesLabel)) {
			File dir = appService.getApp().getBaseDirectory();
			final String filename = "RecordedScriptEditorMacro.xml";
			if (getEditorPane().getFile() != null) {
				dir = getEditorPane().getFile().getParentFile();
			}
			return uiService.chooseFile(new File(dir, filename),
					(openOtherwiseSave) ? FileWidget.OPEN_STYLE : FileWidget.SAVE_STYLE);
		}
		return null;
	}

	private void displayRecordableMap() {
		displayMap(cmdPalette.getRecordableActions(), "Script Editor Recordable Actions/Events");
	}

	private void displayKeyMap() {
		displayMap(cmdPalette.getShortcuts(), "Script Editor Shortcuts");
	}

	private void displayMap(final Map<String, String> map, final String windowTitle) {
		final ArrayList<String> lines = new ArrayList<>();
		map.forEach( (cmd, key) -> {
			lines.add("<tr><td>" + cmd
			+ "</td><td>" + key + "</td></tr>");
		});
		final String prefix = "<HTML><center><table>" //
				+ "<tbody>" //
				+ "<tr>" //
				+ "<td style=\"width: 60%; text-align: center;\"><b>Action</b></td>" //
				+ "<td style=\"width: 40%; text-align: center;\"><b>Shortcut</b></td>" //
				+ "</tr>"; //
		final String suffix = "</tbody></table></center>";
		showHTMLDialog(windowTitle, prefix + String.join("", lines) + suffix);
	}

	private class DragAndDrop implements DragSourceListener, DragGestureListener {
		@Override
		public void dragDropEnd(final DragSourceDropEvent dsde) {}

		@Override
		public void dragEnter(final DragSourceDragEvent dsde) {
			dsde.getDragSourceContext().setCursor(DragSource.DefaultMoveNoDrop);
		}

		@Override
		public void dragGestureRecognized(final DragGestureEvent dge) {
			final TreePath path = tree.getSelectionPath();
			if (path == null) // nothing is currently selected
				return;
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
				public Object getTransferData(final DataFlavor flavor) throws UnsupportedFlavorException, IOException {
					if (isDataFlavorSupported(flavor))
						return Arrays.asList(new String[]{filepath});
					return null;
				}
			}, this);
		}

		@Override
		public void dragExit(final DragSourceEvent dse) {
			dse.getDragSourceContext().setCursor(DragSource.DefaultMoveNoDrop);
		}

		@Override
		public void dragOver(final DragSourceDragEvent dsde) {
			if (tree == dsde.getSource()) {
				dsde.getDragSourceContext().setCursor(DragSource.DefaultCopyNoDrop);
			} else if (dsde.getDropAction() == DnDConstants.ACTION_COPY) {
				dsde.getDragSourceContext().setCursor(DragSource.DefaultCopyDrop);
			} else {
				dsde.getDragSourceContext().setCursor(DragSource.DefaultCopyNoDrop);
			}
		}

		@Override
		public void dropActionChanged(final DragSourceDragEvent dsde) {}
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
				tokenMakerFactory.putMapping("text/" + info.getName().toLowerCase().replace(' ', '-'), info
					.getClassName());
			}
			catch (final Throwable t) {
				log.warn("Could not register " + info.getName(), t);
			}
	}

	private void initializeDynamicMenuComponents() {

		// Options menu. These will be updated once EditorPane is created
		wrapLines = new JCheckBoxMenuItem("Wrap Lines", false);
		wrapLines.setMnemonic(KeyEvent.VK_W);
		marginLine = new JCheckBoxMenuItem("Show Margin Line", false);
		marginLine.setToolTipText("Displays right margin at column 80");
		marginLine.addItemListener(e -> setMarginLineEnabled(marginLine.getState()));
		wrapLines.addItemListener(e -> setWrapLines(wrapLines.getState()));
		markOccurences = new JCheckBoxMenuItem("Mark Occurences", false);
		markOccurences.setToolTipText("Allows for all occurrences of a double-clicked string to be"
				+ " highlighted.\nLines with hits are marked on the Editor's notification strip");
		markOccurences.addItemListener(e -> setMarkOccurrences(markOccurences.getState()));
		whiteSpace = new JCheckBoxMenuItem("Show Whitespace", false);
		whiteSpace.addItemListener(e -> setWhiteSpaceVisible(whiteSpace.isSelected()));
		paintTabs = new JCheckBoxMenuItem("Show Indent Guides");
		paintTabs.setToolTipText("Displays 'tab lines' for leading whitespace");
		paintTabs.addItemListener(e -> setPaintTabLines(paintTabs.getState()));
		autocompletion = new JCheckBoxMenuItem("Enable Autocompletion", true);
		autocompletion.setToolTipText("Whether code completion should be used.\nNB: Not all languages support this feature");
		autocompletion.addItemListener(e -> setAutoCompletionEnabled(autocompletion.getState()));
		keylessAutocompletion = new JCheckBoxMenuItem("Show Completions Without Ctrl+Space", false);
		keylessAutocompletion.setToolTipText("If selected, the completion pop-up automatically appears while typing");
		keylessAutocompletion.addItemListener(e -> setKeylessAutoCompletion(keylessAutocompletion.getState()));
		fallbackAutocompletion = new JCheckBoxMenuItem("Use Java Completions as Fallback", false);
		fallbackAutocompletion.setToolTipText("<HTML>If selected, Java completions will be used when scripting<br>"
				+ "a language for which auto-completions are not available");
		fallbackAutocompletion.addItemListener(e -> setFallbackAutoCompletion(fallbackAutocompletion.getState()));
		themeRadioGroup = new ButtonGroup();

		// Help menu. These are 'dynamic' items
		openMacroFunctions = new JMenuItem("Open Help on Macro Function(s)...");
		openMacroFunctions.setMnemonic(KeyEvent.VK_H);
		openMacroFunctions.addActionListener(e -> {
			try {
				new MacroFunctions(this).openHelp(getTextArea().getSelectedText());
			} catch (final IOException ex) {
				handleException(ex);
			}
		});
		openHelp = new JMenuItem("Open Help for Class (With Frames)...");
		openHelp.setMnemonic(KeyEvent.VK_H);
		openHelp.addActionListener( e-> openHelp(null));
		openHelpWithoutFrames = new JMenuItem("Open Help for Class...");
		openHelpWithoutFrames.addActionListener(e -> openHelp(null, false));
	}

	/**
	 * Check whether the file was edited outside of this {@link EditorPane} and
	 * ask the user whether to reload.
	 */
	public void checkForOutsideChanges() {
		final EditorPane editorPane = getEditorPane();
		if (editorPane.wasChangedOutside()) {
			reload(editorPane.getFile().getName() +
				"\nwas changed outside of the editor.");
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
	private void onEvent(
		@SuppressWarnings("unused") final ContextDisposingEvent e)
	{
		if (isDisplayable()) dispose();
	}

	/**
	 * Loads the Script Editor layout from persisted storage.
	 * @see #saveWindowSizeToPrefs()
	 */
	public void loadWindowSizePreferences() {
		layoutLoading = true;

		final Dimension dim = getSize();

		// If a dimension is 0 then use the default dimension size
		if (0 == dim.width) dim.width = DEFAULT_WINDOW_WIDTH;
		if (0 == dim.height) dim.height = DEFAULT_WINDOW_HEIGHT;

		final int windowWidth = prefService.getInt(getClass(), WINDOW_WIDTH, dim.width);
		final int windowHeight = prefService.getInt(getClass(), WINDOW_HEIGHT, dim.height);
		// Avoid creating a window larger than the desktop
		final Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		if (windowWidth > screen.getWidth() || windowHeight > screen.getHeight())
			setPreferredSize(new Dimension(DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT));
		else
			setPreferredSize(new Dimension(windowWidth, windowHeight));

		final int mainDivLocation = prefService.getInt(getClass(), MAIN_DIV_LOCATION, body.getDividerLocation());
		body.setDividerLocation(mainDivLocation);

		final TextEditorTab tab = getTab();
		final int tabDivLocation = prefService.getInt(getClass(), TAB_DIV_LOCATION, tab.getDividerLocation());
		final int tabDivOrientation = prefService.getInt(getClass(), TAB_DIV_ORIENTATION, tab.getOrientation());
		final int replDividerLocation = prefService.getInt(getClass(), REPL_DIV_LOCATION, tab.getScreenAndPromptSplit().getDividerLocation());
		tab.setDividerLocation(tabDivLocation);
		tab.setOrientation(tabDivOrientation);
		tab.getScreenAndPromptSplit().setDividerLocation(replDividerLocation);
		layoutLoading = false;
	}

	/**
	 * Saves the Script Editor layout to persisted storage.
	 * <p>
	 * Separated from savePreferences because we always want to save the window
	 * size when it's resized, however, we don't want to automatically save the
	 * font, tab size, etc. without the user pressing "Save Preferences"
	 * </p>
	 * @see #loadWindowSizePreferences()
	 */
	public void saveWindowSizeToPrefs() {
		if (layoutLoading) return;

		final Dimension dim = getSize();
		prefService.put(getClass(), WINDOW_HEIGHT, dim.height);
		prefService.put(getClass(), WINDOW_WIDTH, dim.width);

		prefService.put(getClass(), MAIN_DIV_LOCATION, body.getDividerLocation());

		final TextEditorTab tab = getTab();
		prefService.put(getClass(), TAB_DIV_LOCATION, tab.getDividerLocation());
		prefService.put(getClass(), TAB_DIV_ORIENTATION, tab.getOrientation());
		prefService.put(getClass(), REPL_DIV_LOCATION, tab.getScreenAndPromptSplit().getDividerLocation());
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

	private JMenuItem addMappedActionToMenu(final JMenu menu, String label, String actionID, final boolean editingAction) {
		final JMenuItem jmi = new JMenuItem(label);
		jmi.addActionListener(e -> {
			try {
				if (editingAction && getEditorPane().isLocked()) {
					warn("File is currently locked.");
					return;
				}
				if (RTextAreaEditorKit.clipboardHistoryAction.equals(actionID)
						&& ClipboardHistory.get().getHistory().isEmpty()) {
					warn("The internal clipboard manager is empty.");
					return;
				}
				getTextArea().requestFocusInWindow();
				getTextArea().getActionMap().get(actionID).actionPerformed(e);
			} catch (final Exception | Error ignored) {
				error("\"" + label + "\" not availabe for current scripting language.");
			}
		});
		jmi.setAccelerator(getEditorPane().getPaneActions().getAccelerator(actionID));
		menu.add(jmi);
		return jmi;
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

				final ActionListener menuListener = e -> loadTemplate(entry.getValue());

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
			log.error(e);
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
		if (GuiUtils.confirm(this, "Do you want to save changes?", "Save Changes?", "Save")) {
			return save();
		} else {
			// Compiled languages should not progress if their source is unsaved
			return !beforeCompiling;
		}
	}

	private boolean isJava(final ScriptLanguage language) {
		return language != null && language.getLanguageName().equals("Java");
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
			if (file != null) new Thread(() -> open(file)).start();
			return;
		}
		else if (source == save) save();
		else if (source == saveas) saveAs();
		else if (source == makeJar) makeJar(false);
		else if (source == makeJarWithSource) makeJar(true);
		else if (source == compileAndRun) runText();
		else if (source == compile) compile();
		else if (source == runSelection) runText(true);
		else if (source == nextError) {
			if (isJava(getEditorPane().getCurrentLanguage()))
				new Thread(() -> nextError(true)).start();
			else
				getEditorPane().getErrorHighlighter().gotoNextError();
		}
		else if (source == previousError) {
			if (isJava(getEditorPane().getCurrentLanguage()))
				new Thread(() -> nextError(false)).start();
			else
				getEditorPane().getErrorHighlighter().gotoPreviousError();
		}
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
		else if (source == copy) getTextArea().copy();
		else if (source == find) findOrReplace(true);
		else if (source == findNext) {
			findDialog.setRestrictToConsole(false);
			findDialog.searchOrReplace(false);
		}
		else if (source == findPrevious) {
			findDialog.setRestrictToConsole(false);
			findDialog.searchOrReplace(false, false);
		}
		else if (source == gotoLine) gotoLine();
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
		else if (source == openClassOrPackageHelp) openClassOrPackageHelp(null);
		else if (source == extractSourceJar) extractSourceJar();
		else if (source == askChatGPTtoGenerateCode) askChatGPTtoGenerateCode();
		else if (source == openSourceForClass) {
			final String className = getSelectedClassNameOrAsk("Class (fully qualified name):", "Which Class?");
			if (className != null) {
				try {
					final String url = new FileFunctions(this).getSourceURL(className);
					platformService.open(new URL(url));
				}
				catch (final Throwable e) {
					handleException(e);
				}
			}
		}
		/* TODO
		else if (source == showDiff) {
			new Thread(() -> {
				EditorPane pane = getEditorPane();
				new FileFunctions(TextEditor.this).showDiff(pane.file, pane.getGitDirectory());
			}).start();
		}
		else if (source == commit) {
			new Thread(() -> {
				EditorPane pane = getEditorPane();
				new FileFunctions(TextEditor.this).commit(pane.file, pane.getGitDirectory());
			}).start();
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
		else if (source == increaseFontSize || source == decreaseFontSize) {
			getEditorPane().increaseFontSize(
				(float) (source == increaseFontSize ? 1.2 : 1 / 1.2));
			setFontSize(getEditorPane().getFontSize());
		}
		else if (source == nextTab) switchTabRelative(1);
		else if (source == previousTab) switchTabRelative(-1);
		else if (handleTabsMenu(source)) return;
		else {
			// commands that should not run when files are locked!
			if (getEditorPane().isLocked()) {
				error("File is currently locked.");
				return;
			}
			if (source == cut)
				getTextArea().cut();
			else if (source == paste)
				getTextArea().paste();
			else if (source == undo)
				getTextArea().undoLastAction();
			else if (source == redo)
				getTextArea().redoLastAction();
			else if (source == addImport) {
				addImport(getSelectedClassNameOrAsk("Add import (complete qualified name of class/package)",
						"Which Class to Import?"));
			} else if (source == removeUnusedImports)
				new TokenFunctions(getTextArea()).removeUnusedImports();
			else if (source == sortImports)
				new TokenFunctions(getTextArea()).sortImports();
			else if (source == removeTrailingWhitespace)
				new TokenFunctions(getTextArea()).removeTrailingWhitespace();
			else if (source == replaceTabsWithSpaces)
				getTextArea().convertTabsToSpaces();
			else if (source == replaceSpacesWithTabs)
				getTextArea().convertSpacesToTabs();
			else if (source == zapGremlins)
				zapGremlins();
		}
	}

	private void setAutoCompletionEnabled(final boolean enabled) {
		for (int i = 0; i < tabbed.getTabCount(); i++)
			getEditorPane(i).setAutoCompletion(enabled);
		keylessAutocompletion.setEnabled(enabled);
		fallbackAutocompletion.setEnabled(enabled);
	}

	private void setTabsEmulated(final boolean emulated) {
		for (int i = 0; i < tabbed.getTabCount(); i++)
			getEditorPane(i).setTabsEmulated(emulated);
		getEditorPane().requestFocusInWindow();
	}

	private void setPaintTabLines(final boolean paint) {
		for (int i = 0; i < tabbed.getTabCount(); i++)
			getEditorPane(i).setPaintTabLines(paint);
		getEditorPane().requestFocusInWindow();
	}

	private void setKeylessAutoCompletion(final boolean noKeyRequired) {
		for (int i = 0; i < tabbed.getTabCount(); i++)
			getEditorPane(i).setKeylessAutoCompletion(noKeyRequired);
		getEditorPane().requestFocusInWindow();
	}

	private void setFallbackAutoCompletion(final boolean fallback) {
		for (int i = 0; i < tabbed.getTabCount(); i++)
			getEditorPane(i).setFallbackAutoCompletion(fallback);
		getEditorPane().requestFocusInWindow();
	}

	private void setMarkOccurrences(final boolean markOccurrences) {
		for (int i = 0; i < tabbed.getTabCount(); i++)
			getEditorPane(i).setMarkOccurrences(markOccurrences);
		getEditorPane().requestFocusInWindow();
	}

	private void setWhiteSpaceVisible(final boolean visible) {
		for (int i = 0; i < tabbed.getTabCount(); i++)
			getEditorPane(i).setWhitespaceVisible(visible);
		getEditorPane().requestFocusInWindow();
	}

	private void setWrapLines(final boolean wrap) {
		for (int i = 0; i < tabbed.getTabCount(); i++)
			getEditorPane(i).setLineWrap(wrap);
		getEditorPane().requestFocusInWindow();
	}

	private void setMarginLineEnabled(final boolean enabled) {
		for (int i = 0; i < tabbed.getTabCount(); i++)
			getEditorPane(i).setMarginLineEnabled(enabled);
		getEditorPane().requestFocusInWindow();
	}

	private JMenu applyThemeMenu() {
		final LinkedHashMap<String, String> map = new LinkedHashMap<>();
		map.put("Default", "default");
		map.put("-", "-");
		map.put("Dark", "dark");
		map.put("Druid", "druid");
		map.put("Monokai", "monokai");
		map.put("Eclipse (Light)", "eclipse");
		map.put("IntelliJ (Light)", "idea");
		map.put("Visual Studio (Light)", "vs");
		themeRadioGroup = new ButtonGroup();
		final JMenu menu = new JMenu("Theme");
		map.forEach((k, v) -> {
			if ("-".equals(k)) {
				menu.addSeparator();
				return;
			}
			final JRadioButtonMenuItem item = new JRadioButtonMenuItem(k);
			item.setActionCommand(v); // needed for #updateThemeControls()
			themeRadioGroup.add(item);
			item.addActionListener(e -> {
				try {
					applyTheme(v, false);
					// make the choice available for the next tab
					prefService.put(EditorPane.class, EditorPane.THEME_PREFS, v);
				} catch (final IllegalArgumentException ex) {
					error("Theme could not be loaded. See Console for details.");
					ex.printStackTrace();
				}
			});
			menu.add(item);
		});
		return menu;
	}

	/**
	 * Applies a theme to all the panes of this editor.
	 *
	 * @param theme either "default", "dark", "druid", "eclipse", "idea", "monokai",
	 *              "vs"
	 * @throws IllegalArgumentException If {@code theme} is not a valid option, or
	 *                                  the resource could not be loaded
	 */
	public void applyTheme(final String theme) throws IllegalArgumentException {
		applyTheme(theme, true);
	}

	private void applyTheme(final String theme, final boolean updateMenus) throws IllegalArgumentException {
		try {
			final Theme th = getTheme(theme);
			if (th == null) {
				writeError("Unrecognized theme ignored: '" + theme + "'");
				return;
			}
			for (int i = 0; i < tabbed.getTabCount(); i++) {
				getEditorPane(i).applyTheme(theme);
			}
		} catch (final Exception ex) {
			activeTheme = "default";
			updateThemeControls("default");
			writeError("Could not load theme. See Console for details.");
			updateThemeControls(activeTheme);
			throw new IllegalArgumentException(ex);
		}
		activeTheme = theme;
		if (updateMenus) updateThemeControls(theme);
	}

	static Theme getTheme(final String theme) throws IllegalArgumentException {
		try {
			return Theme
					.load(TextEditor.class.getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/" + theme + ".xml"));
		} catch (final Exception ex) {
			throw new IllegalArgumentException(ex);
		}
	}

	private void updateThemeControls(final String theme) {
		if (themeRadioGroup != null) {
			final Enumeration<AbstractButton> choices = themeRadioGroup.getElements();
			while (choices.hasMoreElements()) {
				final AbstractButton choice = choices.nextElement();
				if (theme.equals(choice.getActionCommand())) {
					choice.setSelected(true);
					break;
				}
			}
		}
	}

	private void collapseSplitPane(final int pane, final boolean collapse) {
		final JSplitPane jsp = (pane == 0)  ? body : getTab();
		if (collapse) {
			panePositions[pane] = jsp.getDividerLocation();
			if (pane == 0) { // collapse to left
				jsp.setDividerLocation(0.0d);
			} else { // collapse to bottom
				jsp.setDividerLocation(1.0d);
			}
		} else {
			jsp.setDividerLocation(panePositions[pane]);
			// Check if user collapsed pane manually (stashed panePosition is invalid)
			final boolean expanded = (pane == 0) ? isLeftPaneExpanded(jsp) : isRightOrBottomPaneExpanded(jsp);
			if (!expanded
				//	&& JOptionPane.OK_OPTION == JOptionPane.showConfirmDialog(TextEditor.this, //
				//	"Expand to default position?", "Expand to Defaults?", JOptionPane.OK_CANCEL_OPTION)
				) {
				jsp.setDividerLocation((pane == 0) ? .2d : .75d);
				panePositions[pane] = jsp.getDividerLocation();
			}
		}
	}

	private boolean isLeftPaneExpanded(final JSplitPane pane) {
		return pane.isVisible() && pane.getLeftComponent().getWidth() > 0;
	}

	private boolean isRightOrBottomPaneExpanded(final JSplitPane pane) {
		final int dim = (pane.getOrientation() == JSplitPane.VERTICAL_SPLIT) ? pane.getRightComponent().getHeight()
				: pane.getRightComponent().getWidth();
		return pane.isVisible() && dim > 0;
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
		lockPane.setSelected(editorPane.isLocked());
		editorPane.requestFocus();
		checkForOutsideChanges();

		//whiteSpace.setSelected(editorPane.isWhitespaceVisible());

		editorPane.setLanguageByFileName(editorPane.getFileName());
		updateLanguageMenu(editorPane.getCurrentLanguage());

		setTitle();
	}

	public EditorPane getEditorPane(final int index) {
		return getTab(index).editorPane;
	}

	public void findOrReplace(final boolean doReplace) {
		findDialog.setLocationRelativeTo(this);
		findDialog.setRestrictToConsole(false);

		// override search pattern only if
		// there is sth. selected
		final String selection = getEditorPane().getSelectedText();
		if (selection != null) findDialog.setSearchPattern(selection);

		findDialog.show(doReplace && !getEditorPane().isLocked());
	}

	public void gotoLine() {
		final String line = GuiUtils.getString(this, "Enter line number:", "Goto Line");
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

	private Vector<Bookmark> getAllBookmarks() {
		final Vector<Bookmark> bookmarks = new Vector<>();
		for (int i = 0; i < tabbed.getTabCount(); i++) {
			final TextEditorTab tab = (TextEditorTab) tabbed.getComponentAt(i);
			tab.editorPane.getBookmarks(tab, bookmarks);
		}
		if (bookmarks.isEmpty()) {
			info("No Bookmarks currently exist.\nYou can bookmark lines by clicking next to their line number.",
					"No Bookmarks");
		}
		return bookmarks;
	}

	public void listBookmarks() {
		final Vector<Bookmark> bookmarks = getAllBookmarks();
		if (!bookmarks.isEmpty()) {
			new BookmarkDialog(this, bookmarks).setVisible(true);
		}
	}

	void clearAllBookmarks() {
		final Vector<Bookmark> bookmarks = getAllBookmarks();
		if (bookmarks.isEmpty())
			return;
		;
		if (confirm("Delete all bookmarks?", "Confirm Deletion?", "Delete")) {
			bookmarks.forEach(bk -> bk.tab.editorPane.toggleBookmark(bk.getLineNumber()));
		}
	}

	public boolean reload() {
		return reload("Reload the file?");
	}

	public boolean reload(final String message) {
		return reloadRevert(message, "Reload");
	}

	private boolean reloadRevert(final String message, final String title) {
		final EditorPane editorPane = getEditorPane();

		final File file = editorPane.getFile();
		if (file == null || !file.exists()) return true;

		final boolean modified = editorPane.fileChanged();
		final String[] options = { title, "Do Not " + title };
		if (modified) options[0] = title + " (Discard Changes)";
		switch (JOptionPane.showOptionDialog(this, message, title + "?",
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
	 * Opens a new tab with some content.
	 * 
	 * @param content           the script content
	 * @param languageExtension the file extension associated with the content's
	 *                          language (e.g., ".java", ".py", etc.
	 * @return the text editor tab
	 */
	public TextEditorTab newTab(final String content, final String languageExtension) {
		String lang = languageExtension;
		final TextEditorTab tab = open(null);
		if (null != lang && lang.length() > 0) {
			lang = lang.trim().toLowerCase();

			if ('.' != lang.charAt(0)) {
				lang = "." + languageExtension;
			}

			tab.editorPane.setLanguage(scriptService.getLanguageByName(languageExtension));
		} else {
			final String lastLanguageName = prefService.get(getClass(), LAST_LANGUAGE);
			if (null != lastLanguageName && "none" != lastLanguageName)
				setLanguage(scriptService.getLanguageByName(lastLanguageName));
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
			} catch (final IOException e) {
				log.error(e);
			}
			return null;
		}

		try {
			TextEditorTab tab = (tabbed.getTabCount() == 0) ? null : getTab();
			final TextEditorTab prior = tab;
			final boolean wasNew = tab != null && tab.editorPane.isNew();
			float font_size = 0; // to set the new editor's font like the last active one, if any
			if (!wasNew) {
				if (tabbed.getTabCount() > 0) font_size = getTab().getEditorPane().getFont().getSize2D();
				tab = new TextEditorTab(this);
				context.inject(tab.editorPane);
				tab.editorPane.loadPreferences();
				addDefaultAccelerators(tab.editorPane);
			} else {
				// the Edit menu can only be populated after an editor pane exists, as it reads
				// actions from its input map. We will built it here, if it has not been assembled
				// yet.
				if (undo == null) assembleEditMenu();
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
			else {
				final String lastLanguageName = prefService.get(getClass(), LAST_LANGUAGE);
				if ("none" != lastLanguageName)
					setLanguage(scriptService.getLanguageByName(lastLanguageName));
			}

			updateLanguageMenu(tab.editorPane.getCurrentLanguage());
			tab.editorPane.getDocument().addDocumentListener(this);
			tab.editorPane.requestFocusInWindow();
			return tab;
		}
		catch (final FileNotFoundException e) {
			log.error(e);
			error("The file\n'" + file + "' was not found.");
		}
		catch (final Exception e) {
			log.error(e);
			error("There was an error while opening\n'" + file + "': " + e);
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
		if (file.exists() && askBeforeReplacing
				&& confirm("Do you want to replace " + path + "?", "Replace " + path + "?", "Replace"))
			return false;
		if (!write(file))
			return false;
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
			log.error(e);
			error("Could not save " + file.getName());
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
		if (selectedFile.exists()
				&& confirm("Do you want to replace " + selectedFile + "?", "Replace " + selectedFile + "?", "Replace"))
			return false;
		try {
			makeJar(selectedFile, includeSources);
			return true;
		}
		catch (final IOException e) {
			log.error(e);
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
			new Thread(() -> {
				java.makeJar(getEditorPane().getFile(), includeSources, file, errors);
				errorScreen.insert("Compilation finished.\n", //
					errorScreen.getDocument().getLength());
				markCompileEnd();
			}).start();
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
			throw new IOException(e);
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
		if (null != this.getCurrentLanguage() && (null == language || this.getCurrentLanguage().getLanguageName() != language.getLanguageName())) {
			this.scriptInfo = null;
		}
		getEditorPane().setLanguage(language, addHeader);
		prefService.put(getClass(), LAST_LANGUAGE, null == language? "none" : language.getLanguageName());

		setTitle();
		updateLanguageMenu(language);
		updateUI(true);
	}

	private String lastSupportStatus = null;

	void updateLanguageMenu(final ScriptLanguage language) {
		JMenuItem item = languageMenuItems.get(language);
		if (item == null) {
			// is none
			item = noneLanguageItem;
			setIncremental(false);
		}
		if (!item.isSelected()) {
			item.setSelected(true);
		}
		// print autocompletion status to console
		String supportStatus = getEditorPane().getSupportStatus();
		if (supportStatus != null && !Objects.equals(supportStatus, lastSupportStatus)) {
			write(supportStatus);
			lastSupportStatus = supportStatus;
		}

		final boolean isRunnable = item != noneLanguageItem;
		final boolean isCompileable =
			language != null && language.isCompiledLanguage();

		runMenu.setEnabled(isRunnable);
		compileAndRun.setText(isCompileable ? "Compile and Run" : "Run");
		compileAndRun.setEnabled(isRunnable);
		runSelection.setEnabled(isRunnable && !isCompileable);
		compile.setEnabled(isCompileable);
		autoSave.setEnabled(isCompileable);
			makeJar.setEnabled(isCompileable);
			makeJarWithSource.setEnabled(isCompileable);
	
			final boolean isJava =
				language != null && language.getLanguageName().equals("Java");
			addImport.setEnabled(isJava);
			removeUnusedImports.setEnabled(isJava);
			sortImports.setEnabled(isJava);
			//openSourceForMenuItem.setEnabled(isJava);
	
			final boolean isMacro =
				language != null && language.getLanguageName().equals("ImageJ Macro");
			openMacroFunctions.setEnabled(isMacro);
			openSourceForClass.setEnabled(!isMacro);
	
		openHelp.setEnabled(!isMacro && isRunnable);
		openHelpWithoutFrames.setEnabled(!isMacro && isRunnable);
			nextError.setEnabled(!isMacro && isRunnable);
			previousError.setEnabled(!isMacro && isRunnable);

		final boolean isInGit = getEditorPane().getGitDirectory() != null;
		gitMenu.setVisible(isInGit);

		updateUI(false);
	}

	/**
	 * Use {@link #updateUI(boolean)} instead
	 */
	@Deprecated
	public void updateTabAndFontSize(final boolean setByLanguage) {
		updateUI(setByLanguage);
	}

	public void updateUI(final boolean setByLanguage) {
		final EditorPane pane = getEditorPane();
		//if (pane.getCurrentLanguage() == null) return;

		if (setByLanguage && pane.getCurrentLanguage() != null) {
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
		getTab().prompt.setTabSize(getEditorPane().getTabSize());
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
		markOccurences.setState(pane.getMarkOccurrences());
		wrapLines.setState(pane.getLineWrap());
		marginLine.setState(pane.isMarginLineEnabled());
		tabsEmulated.setState(pane.getTabsEmulated());
		paintTabs.setState(pane.getPaintTabLines());
		whiteSpace.setState(pane.isWhitespaceVisible());
		autocompletion.setState(pane.isAutoCompletionEnabled());
		fallbackAutocompletion.setState(pane.isAutoCompletionFallbackEnabled());
		keylessAutocompletion.setState(pane.isAutoCompletionKeyless());
		sourceTreePanel.rebuildSourceTree(pane);
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
		SwingUtilities.invokeLater(() -> {
			setTitle(title); // to the main window
			// Update all tabs: could have changed
			for (int i = 0; i < tabbed.getTabCount(); i++)
				tabbed.setTitleAt(i, //
					((TextEditorTab) tabbed.getComponentAt(i)).getTitle());
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
				log.error(e);
			}
			for (final Thread thread : getAllThreads()) {
				try {
					thread.interrupt();
					Thread.yield(); // give it a chance
					thread.stop();
				}
				catch (final Throwable t) {
					log.error(t);
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
			log.error(t);
		}
	}

	/**
	 * Run current script with the batch processor
	 */
	public void runBatch() {
		if (null == getCurrentLanguage()) {
			error("Select a language first! Also, please note that this option\n"
				+ "requires at least one @File parameter to be declared in the script.");
			return;
		}
		// get script from current tab
		final String script = getTab().getEditorPane().getText();
		if (script.trim().isEmpty()) {
			error("This option requires at least one @File parameter to be declared.");
			return;
		}
		final ScriptInfo info = new ScriptInfo(context, //
			"dummy." + getCurrentLanguage().getExtensions().get(0), //
			new StringReader(script));
		batchService.run(info);
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
			getEditorPane().getErrorHighlighter().setSelectedCodeExecution(true);
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
			log.error(t);
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
			final boolean exists = file.exists();
			if (!exists) {
				// Ensure parent directories exist
				file.getParentFile().mkdirs();
				file.createNewFile(); // atomic
			}
			Files.write(Paths.get(path), Arrays.asList(new String[]{text, "#"}), Charset.forName("UTF-8"),
					StandardOpenOption.APPEND, StandardOpenOption.DSYNC);
		} catch (final IOException e) {
			log.error("Failed to write executed prompt command to file " + path, e);
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
		final ArrayList<String> lines = new ArrayList<>();
		if (!file.exists()) return lines;
		RandomAccessFile ra = null;
		List<String> commands = new ArrayList<>();
		try {
			ra = new RandomAccessFile(path, "r");
			final byte[] bytes = new byte[(int)ra.length()];
			ra.readFully(bytes);
			final String sep = System.getProperty("line.separator"); // used fy Files.write above
			commands.addAll(Arrays.asList(new String(bytes, Charset.forName("UTF-8")).split(sep + "#" + sep)));
			if (0 == commands.get(commands.size()-1).length()) commands.remove(commands.size() -1); // last entry is empty
		} catch (final IOException e) {
			log.error("Failed to read history of prompt commands from file " + path, e);
			return lines;
		} finally {
			try { if (null != ra) ra.close(); } catch (final IOException e) { log.error(e); }
		}
		if (commands.size() > 1000) {
			commands = commands.subList(commands.size() - 1000, commands.size());
			// Crop the log: otherwise would grow unbounded
			final ArrayList<String> croppedLog = new ArrayList<>();
			for (final String c : commands) {
				croppedLog.add(c);
				croppedLog.add("#");
			}
			try {
				Files.write(Paths.get(path + "-tmp"), croppedLog, Charset.forName("UTF-8"),
						StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.DSYNC);
				if (!new File(path + "-tmp").renameTo(new File(path))) {
					log.error("Could not rename command log file " + path + "-tmp to " + path);
				}
			} catch (final Exception e) {
				log.error("Failed to crop history of prompt commands file " + path, e);
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
				try (final Reader reader = evalScript(file.getPath(), new FileReader(file), output, errors)) {
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
			new Thread(() -> {
				java.compile(getEditorPane().getFile(), errors);
				errorScreen.insert("Compilation finished.\n", //
					errorScreen.getDocument().getLength());
				markCompileEnd();
			}).start();
		}
	}

	private String getSelectedTextOrAsk(final String msg, final String title) {
		String selection = getTextArea().getSelectedText();
		if (selection == null || selection.indexOf('\n') >= 0) {
			return GuiUtils.getString(this, msg + "\nAlternatively, select a class declaration and re-run.", title);
		}
		return selection;
	}

	private String getSelectedClassNameOrAsk(final String msg, final String title) {
		String className = getSelectedTextOrAsk(msg, title);
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

	public void markCompileEnd() { // this naming is not-intuitive at all!
		if (errorHandler == null) {
			errorHandler =
				new ErrorHandler(getCurrentLanguage(), errorScreen,
					compileStartPosition.getOffset());
			if (errorHandler.getErrorCount() > 0) getTab().showErrors();
		}
		if (getEditorPane().getErrorHighlighter().isLogDetailed() &&
				compileStartOffset != errorScreen.getDocument().getLength()) {
			getTab().showErrors();
		}
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
		SwingUtilities.invokeLater(() -> {
			try {
				gotoLine(lineNumber);
			}
			catch (final BadLocationException e) {
				// ignore
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
		try {
			tabbed.remove(index);
			tabsMenuItems.remove(tabsMenu.getItem(menuItemIndex));
			tabsMenu.remove(menuItemIndex);
		} catch (final IndexOutOfBoundsException e) {
			// this should never happen!?
			log.debug(e);
		}
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
	public void openHelp(String className, final boolean withFrames) {
		if (className == null) className = getSelectedClassNameOrAsk("Class (fully qualified name):", "Online Javadocs...");
		if (className == null) return;
		final Class<?> c = Types.load(className, false);

		final String path = (withFrames ? "index.html?" : "") + //
				className.replace('.', '/') + ".html";

		final String url;

		if (className.startsWith("java.") || className.startsWith("javax.")) {
			// Core Java class -- use javadoc.scijava.org/Java<#> link.
			final String javaVersion = System.getProperty("java.version");
			final String majorVersion;
			if (javaVersion.startsWith("1.")) {
				majorVersion = javaVersion.substring(2, javaVersion.indexOf('.', 2));
			}
			else majorVersion = javaVersion.substring(0, javaVersion.indexOf('.'));
			url = "https://javadoc.scijava.org/Java" + majorVersion + "/" + path;
		}
		else {
			// Third party library -- look for a Maven POM identifying it.
			final POM pom = POM.getPOM(c);
			if (pom == null) {
				throw new IllegalArgumentException(//
					"Unknown origin for class " + className);
			}
			final String releaseProfiles = pom.cdata("//properties/releaseProfiles");
			final boolean scijavaRepo = "deploy-to-scijava".equals(releaseProfiles);
			if (scijavaRepo) {
				// Use javadoc.scijava.org -- try to figure out which project.
				// Maybe some day, we can bake this information into the POM.
				final String project;
				final String g = pom.getGroupId();
				if ("net.imagej".equals(g)) {
					project = "ij".equals(pom.getArtifactId()) ? "ImageJ1" : "ImageJ";
				}
				else if ("io.scif".equals(g)) project = "SCIFIO";
				else if ("net.imglib2".equals(g)) project = "ImgLib2";
				else if ("org.bonej".equals(g)) project = "BoneJ";
				else if ("org.scijava".equals(g)) project = "SciJava";
				else if ("sc.fiji".equals(g)) project = "Fiji";
				else project = "Java";
				url = "https://javadoc.scijava.org/" + project + "/" + path;
			}
			else {
				// Assume Maven Central -- use javadoc.io.
				url = "https://javadoc.io/static/" + pom.getGroupId() + "/" + //
					pom.getArtifactId() + "/" + pom.getVersion() + "/" + path;
			}
		}

		try {
			platformService.open(new URL(url));
		}
		catch (final Throwable e) {
			handleException(e);
		}
	}

	/**
	 * @param text Either a classname, or a partial class name, or package name or
	 *             any part of the fully qualified class name.
	 */
	public void openClassOrPackageHelp(String text) {
		if (text == null)
			text = getSelectedClassNameOrAsk("Class or package (complete or partial name, e.g., 'ij'):", "Lookup Which Class/Package?");
		if (null == text) return;
		new Thread(new FindClassSourceAndJavadoc(text)).start(); // fork away from event dispatch thread
	}

	public class FindClassSourceAndJavadoc implements Runnable {
		private final String text;
		public FindClassSourceAndJavadoc(final String text) {
			this.text = text;
		}
		@Override
		public void run() {
			setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
			final HashMap<String, ArrayList<String>> matches;
			try {
				 matches = ClassUtil.findDocumentationForClass(text);
			} finally {
				setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
			}
			if (matches.isEmpty()) {
				if (confirm("No info found for: '" + text + "'.\nSearch for it on the web?", "Search the Web?",
						"Search")) {
					GuiUtils.runSearchQueryInBrowser(TextEditor.this, getPlatformService(), text.trim());
				}
				return;
			}
			final JPanel panel = new JPanel();
			final GridBagLayout gridbag = new GridBagLayout();
			final GridBagConstraints c = new GridBagConstraints();
			panel.setLayout(gridbag);
			//panel.setBorder(BorderFactory.createEmptyBorder(BORDER_SIZE, BORDER_SIZE, BORDER_SIZE, BORDER_SIZE));
			final List<String> keys = new ArrayList<String>(matches.keySet());
			Collections.sort(keys);
			c.gridy = 0;
			for (final String classname: keys) {
				c.gridx = 0;
				c.anchor = GridBagConstraints.EAST;
				final JLabel class_label = new JLabel(classname);
				gridbag.setConstraints(class_label, c);
				panel.add(class_label);
				ArrayList<String> urls = matches.get(classname);
				if (urls.isEmpty()) {
					urls = new ArrayList<String>();
					urls.add("https://duckduckgo.com/?q=" + classname);
				}
				for (final String url: urls) {
					c.gridx += 1;
					c.anchor = GridBagConstraints.WEST;
					String title = "JavaDoc";
					if (url.endsWith(".java")) title = "Source";
					else if (url.contains("duckduckgo")) title = "Search...";
					final JButton link = new JButton(title);
					gridbag.setConstraints(link, c);
					panel.add(link);
					link.addActionListener(event -> {
						GuiUtils.openURL(TextEditor.this, platformService, url);
					});
				}
				c.gridy += 1;
			}
			final JScrollPane jsp = new JScrollPane(panel);
			//jsp.setPreferredSize(new Dimension(800, 500));
			SwingUtilities.invokeLater(() -> {
				final JFrame frame = new JFrame("Resources for '" + text +"'");
				frame.getContentPane().add(jsp);
				frame.setLocationRelativeTo(TextEditor.this);
				frame.pack();
				frame.setVisible(true);
			});
		}
	}

	public void extractSourceJar() {
		final File file = openWithDialog(null);
		if (file != null) extractSourceJar(file);
	}

	public void askChatGPTtoGenerateCode() {
		SwingUtilities.invokeLater(() -> {

			setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

			// setup default prompt
			String prompt =
					promptPrefix()
							.replace("{programming_language}",  getCurrentLanguage().getLanguageName() )
							.replace("{custom_prompt}", getTextArea().getSelectedText());

			String answer = askChatGPT(prompt);

			if (answer.contains("```")) {
				// clean answer by removing blabla outside the code block
				answer = answer.replace("```java", "```");
				answer = answer.replace("```javascript", "```");
				answer = answer.replace("```python", "```");
				answer = answer.replace("```jython", "```");
				answer = answer.replace("```macro", "```");
				answer = answer.replace("```groovy", "```");

				String[] temp = answer.split("```");
				answer = temp[1];
			}

			//getTextArea().insert(answer, getTextArea().getCaretPosition());
			getTextArea().replaceSelection(answer + "\n");
			setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
		});
	}

	private String askChatGPT(String text) {
		// Modified from: https://github.com/TheoKanning/openai-java/blob/main/example/src/main/java/example/OpenAiApiFunctionsExample.java
		String token = apiKey();

		OpenAiService service = new OpenAiService(token);

		List<ChatMessage> messages = new ArrayList<>();
		ChatMessage userMessage = new ChatMessage(ChatMessageRole.USER.value(), text);
		messages.add(userMessage);

		ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest
				.builder()
				.model(modelName())
				.messages(messages).build();

		ChatMessage responseMessage = service.createChatCompletion(chatCompletionRequest).getChoices().get(0).getMessage();
		messages.add(responseMessage);

		String result = responseMessage.getContent();
		System.out.println(result);
		return result;
	}

	private String apiKey() {
		if (optionsService != null) {
			final OpenAIOptions openAIOptions =
				optionsService.getOptions(OpenAIOptions.class);
			if (openAIOptions != null) {
				final String key = openAIOptions.getOpenAIKey();
				if (key != null && !key.isEmpty()) return key;
			}
		}
		return System.getenv("OPENAI_API_KEY");
	}

	private String modelName() {
		if (optionsService != null) {
			final OpenAIOptions openAIOptions =
					optionsService.getOptions(OpenAIOptions.class);
			if (openAIOptions != null) {
				final String key = openAIOptions.getModelName();
				if (key != null && !key.isEmpty()) return key;
			}
		}
		return null;
	}

	private String promptPrefix() {
		if (optionsService != null) {
			final OpenAIOptions openAIOptions =
					optionsService.getOptions(OpenAIOptions.class);
			if (openAIOptions != null) {
				final String promptPrefix = openAIOptions.getPromptPrefix();
				if (promptPrefix != null && !promptPrefix.isEmpty()) return promptPrefix;
			}
		}
		return "";
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

	void error(final String message) {
		GuiUtils.error(this, message);
	}

	void warn(final String message) {
		GuiUtils.warn(this, message);
	}

	void info(final String message, final String title) {
		GuiUtils.info(this, message, title);
	}

	boolean confirm(final String message, final String title, final String yesButtonLabel) {
		return GuiUtils.confirm(this, message, title, yesButtonLabel);
	}

	void showHTMLDialog(final String title, final String htmlContents) {
		GuiUtils.showHTMLDialog(this, title, htmlContents);
	}

	public void handleException(final Throwable e) {
		handleException(e, errorScreen);
		getEditorPane().getErrorHighlighter().parse(e);
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
		info(msg, "Zap Gremlins");
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
		final Writer output, final JTextAreaWriter errors) throws ModuleException
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
			} catch (final IOException e) {
				log.error(e);
			}
		}
		
		// map stdout and stderr to the UI
		this.module.setOutputWriter(output);
		this.module.setErrorWriter(errors);

		// prepare the highlighter
		getEditorPane().getErrorHighlighter().setEnabled(!respectAutoImports);
		getEditorPane().getErrorHighlighter().reset();
		getEditorPane().getErrorHighlighter().setWriter(errors);

		// execute the script
		try {
			moduleService.run(module, true).get();
		}
		catch (final InterruptedException e) {
			error("Interrupted");
		}
		catch (final ExecutionException e) {
			log.error(e);
		} finally {
			getEditorPane().getErrorHighlighter().parse();
		}
		return reader;
	}
	
	public void setIncremental(final boolean incremental) {
		
		if (incremental && null == getCurrentLanguage()) {
			error("Select a language first!");
			return;
		}
		
		this.incremental = incremental;
		
		final JTextArea prompt = getTab().getPrompt();
		if (incremental) {
			getTab().setREPLVisible(true);
			prompt.addKeyListener(new KeyAdapter() {
				private final ArrayList<String> commands = loadPromptLog(getCurrentLanguage());
				private int index = commands.size();
				{
					commands.add(""); // the current prompt text
				}
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
						} catch (final Throwable t) {
							log.error(t);
							prompt.requestFocusInWindow();
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
			getTab().setREPLVisible(false);
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

	private void appendPreferences(final JMenu menu) {
		JMenuItem item = new JMenuItem("Save Preferences");
		menu.add(item);
		item.addActionListener(e -> {
			getEditorPane().savePreferences(tree.getTopLevelFoldersString(), activeTheme);
			saveWindowSizeToPrefs();
			write("Script Editor: Preferences Saved...\n");
		});
		item = new JMenuItem("Reset...");
		menu.add(item);
		item.addActionListener(e -> {
			if (confirm("Reset preferences to defaults? (a restart may be required)", "Reset?", "Reset")) {
				resetLayout();
				prefService.clear(EditorPane.class);
				prefService.clear(TextEditor.class);
				write("Script Editor: Preferences Reset. Restart is recommended\n");
			}
		});
	}

	private JMenu helpMenu() {
		final JMenu menu = new JMenu("Help");
		GuiUtils.addMenubarSeparator(menu, "Offline Help:");
		JMenuItem item = new JMenuItem("List Shortcuts...");
		item.addActionListener(e -> displayKeyMap());
		menu.add(item);
		item = new JMenuItem("List Recordable Actions...");
		item.addActionListener(e -> displayRecordableMap());
		menu.add(item);
		item = new JMenuItem("Task Tags How-To...");
		item.addActionListener(e -> {
			showHTMLDialog("Task Tags Help", //
				"<p>"
				+ "When inserted in source code comments, the following keywords will automatically " //
				+ "register task definitions on the rightmost side of the Editor: <code>TODO</code>, " //
				+ "<code>README</code>, and <code>HACK</code>.</p>" //
				+ "<ul>" //
				+ "<li>To add a task, simply type one of the keywords in a commented line, e.g., "//
				+ "<code>TODO</code></li>"//
				+ "<li>To remove a task, delete the keyword from the comment</li>" //
				+ "<li>Mouse over the annotation mark to access a summary of the task</li>" //
				+ "<li>Click on the mark to go to the annotated line</li>"
				+ "</ul>");
		});
		menu.add(item);
		GuiUtils.addMenubarSeparator(menu, "Contextual Help:");
		menu.add(openHelpWithoutFrames);
		openHelpWithoutFrames.setMnemonic(KeyEvent.VK_O);
		menu.add(openHelp);
		openClassOrPackageHelp = addToMenu(menu, "Lookup Class or Package...", 0, 0);
		openClassOrPackageHelp.setMnemonic(KeyEvent.VK_S);
		menu.add(openMacroFunctions);
		GuiUtils.addMenubarSeparator(menu, "Online Resources:");
		menu.add(helpMenuItem("Image.sc Forum ", "https://forum.image.sc/"));
		menu.add(helpMenuItem("ImageJ Search Portal", "https://search.imagej.net/"));
		//menu.addSeparator();
		menu.add(helpMenuItem("SciJava Javadoc Portal", "https://javadoc.scijava.org/"));
		menu.add(helpMenuItem("SciJava Maven Repository", "https://maven.scijava.org/"));
		menu.addSeparator();
		menu.add(helpMenuItem("Fiji on GitHub", "https://github.com/fiji"));
		menu.add(helpMenuItem("SciJava on GitHub", "https://github.com/scijava/"));
		menu.addSeparator();
		menu.add(helpMenuItem("ImageJ Macro Functions", "https://imagej.nih.gov/ij/developer/macro/functions.html"));
		menu.add(helpMenuItem("ImageJ Docs: Development", "https://imagej.net/develop/"));
		menu.add(helpMenuItem("ImageJ Docs: Scripting", "https://imagej.net/scripting/"));
		menu.addSeparator();
		menu.add(helpMenuItem("ImageJ Notebook Tutorials", "https://github.com/imagej/tutorials#readme"));
		return menu;
	}

	private JMenuItem helpMenuItem(final String label, final String url) {
		final JMenuItem item = new JMenuItem(label);
		item.addActionListener(e -> GuiUtils.openURL(TextEditor.this, platformService, url));
		return item;
	}

	protected void applyConsolePopupMenu(final JTextArea textArea) {
		final JPopupMenu popup = new JPopupMenu();
		textArea.setComponentPopupMenu(popup);
		final String scope = ((textArea == errorScreen) ? "Errors..." : "Outputs...");
		JMenuItem jmi = new JMenuItem("Search " + scope);
		popup.add(jmi);
		jmi.addActionListener(e -> {
			findDialog.setLocationRelativeTo(this);
			findDialog.setRestrictToConsole(true);
			final String text = textArea.getSelectedText();
			if (text != null) findDialog.setSearchPattern(text);
			// Ensure the right pane is visible in case search is being
			// triggered by CmdPalette
			if (textArea == errorScreen && !getTab().showingErrors)
				getTab().showErrors();
			else if (getTab().showingErrors)
				getTab().showOutput();
			findDialog.show(false);
		});
		cmdPalette.register(jmi, scope);
		jmi = new JMenuItem("Search Script for Selected Text...");
		popup.add(jmi);
		jmi.addActionListener(e -> {
			final String text = textArea.getSelectedText();
			if (text == null) {
				UIManager.getLookAndFeel().provideErrorFeedback(textArea);
			} else {
				findDialog.setLocationRelativeTo(this);
				findDialog.setRestrictToConsole(false);
				if (text != null) findDialog.setSearchPattern(text);
				findDialog.show(false);
			}
		});
		popup.addSeparator();

		jmi = new JMenuItem("Clear Selected Text");
		popup.add(jmi);
		jmi.addActionListener(e -> {
			if (textArea.getSelectedText() == null)
				UIManager.getLookAndFeel().provideErrorFeedback(textArea);
			else 
				textArea.replaceSelection("");
		});
		final DefaultHighlighter highlighter =  (DefaultHighlighter)textArea.getHighlighter();
		highlighter.setDrawsLayeredHighlights(false);
		jmi = new JMenuItem("Highlight Selected Text");
		popup.add(jmi);
		jmi.addActionListener(e -> {
			try {
				final Color taint = (textArea == errorScreen) ? Color.RED : textArea.getSelectionColor();
				final Color color = ErrorParser.averageColors(textArea.getBackground(), taint);
				final DefaultHighlightPainter painter = new DefaultHighlighter.DefaultHighlightPainter(color);
				textArea.getHighlighter().addHighlight(textArea.getSelectionStart(), textArea.getSelectionEnd(), painter);
				textArea.setCaretPosition(textArea.getSelectionEnd());
				textArea.getHighlighter();
			} catch (final BadLocationException ignored) {
				UIManager.getLookAndFeel().provideErrorFeedback(textArea);
			}
		});
		jmi = new JMenuItem("Clear Highlights");
		popup.add(jmi);
		jmi.addActionListener(e -> textArea.getHighlighter().removeAllHighlights());
		cmdPalette.register(jmi, scope);
		popup.addSeparator();
		final JCheckBoxMenuItem jmc = new JCheckBoxMenuItem("Wrap Lines");
		popup.add(jmc);
		jmc.addActionListener( e -> textArea.setLineWrap(jmc.isSelected()));
		cmdPalette.register(jmc, scope);
	}

	private static Collection<File> assembleFlatFileCollection(final Collection<File> collection, final File[] files) {
		if (files == null) return collection; // can happen while pressing 'Esc'!?
		for (final File file : files) {
			if (file == null || isBinary(file))
				continue;
			else if (file.isDirectory())
				assembleFlatFileCollection(collection, file.listFiles());
			else //if (!file.isHidden())
				collection.add(file);
		}
		return collection;
	}

	protected static class GuiUtils {

		private GuiUtils() {
		}

		static void error(final Component parent, final String message) {
			JOptionPane.showMessageDialog(parent, message, "Error", JOptionPane.ERROR_MESSAGE);
		}

		static void warn(final Component parent, final String message) {
			JOptionPane.showMessageDialog(parent, message, "Warning", JOptionPane.WARNING_MESSAGE);
		}

		static void info(final Component parent, final String message, final String title) {
			JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE);
		}

		static boolean confirm(final Component parent, final String message, final String title,
				final String yesButtonLabel) {
			return JOptionPane.showConfirmDialog(parent, message, title, JOptionPane.YES_NO_OPTION) == 
					JOptionPane.YES_OPTION;
		}

		static void showHTMLDialog(final Component parent, final String title, final String htmlContents) {
			final JTextPane f = new JTextPane();
			f.setContentType("text/html");
			f.setEditable(false);
			f.setBackground(null);
			f.setBorder(null);
			f.setText(htmlContents);
			f.setCaretPosition(0);
			final JScrollPane sp = new JScrollPane(f);
			final JOptionPane pane = new JOptionPane(sp);
			final JDialog dialog = pane.createDialog(parent, title);
			dialog.setResizable(true);
			dialog.pack();
			dialog.setPreferredSize(
				new Dimension(
					(int) Math.min(parent.getWidth()  * .5, pane.getPreferredSize().getWidth() + (3 * sp.getVerticalScrollBar().getWidth())),
					(int) Math.min(parent.getHeight() * .8, pane.getPreferredSize().getHeight())));
			dialog.pack();
			pane.addPropertyChangeListener(JOptionPane.VALUE_PROPERTY, ignored -> {
				dialog.dispose();
			});
			dialog.setModal(false);
			dialog.setLocationRelativeTo(parent);
			dialog.setVisible(true);
		}

		static String getString(final Component parent, final String message, final String title) {
			return (String) JOptionPane.showInputDialog(parent, message, title, JOptionPane.QUESTION_MESSAGE);
		}

		static void runSearchQueryInBrowser(final Component parentComponent, final PlatformService platformService,
				final String query) {
			String url;
			try {
				url = "https://forum.image.sc/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8.toString());
			} catch (final Exception ignored) {
				url = query.trim().replace(" ", "%20");
			}
			openURL(parentComponent, platformService, url);
		}

		static void openURL(final Component parentComponent, final PlatformService platformService, final String url) {
			try {
				platformService.open(new URL(url));
			} catch (final Exception ignored) {
				// Error message with selectable text
				final JTextPane f = new JTextPane();
				f.setContentType("text/html");
				f.setText("<HTML>Web page could not be open. Please visit<br>" + url + "<br>using your web browser.");
				f.setEditable(false);
				f.setBackground(null);
				f.setBorder(null);
				JOptionPane.showMessageDialog(parentComponent, f, "Error", JOptionPane.ERROR_MESSAGE);
			}
		}

		static void openTerminal(final File pwd) throws IOException, InterruptedException {
			final String[] wrappedCommand;
			final File dir = (pwd.isDirectory()) ? pwd : pwd.getParentFile();
			if (PlatformUtils.isWindows()) {
				// this should probably be replaced with powershell!?
				wrappedCommand = new String[] { "cmd", "/c", "start", "/wait", "cmd.exe", "/K" };
			} else if (PlatformUtils.isLinux()) {
				// this will likely only work on debian-based distros
				wrappedCommand = new String[] { "/usr/bin/x-terminal-emulator" };
			} else if (PlatformUtils.isMac()) {
				// On MacOS ProcessBuilder#directory() fails!? so we'll use applescript :)
				wrappedCommand = new String[] { "osascript", "-e", //
						"tell application \"Terminal\" to (do script \"cd '" + dir.getAbsolutePath() + "';clear\")" };
			} else {
				throw new IllegalArgumentException("Unsupported OS");
			}
			final ProcessBuilder pb = new ProcessBuilder(wrappedCommand);
			pb.directory(dir); // does nothing on macOS !?
			pb.start();
		}

		static void addMenubarSeparator(final JMenu menu, final String header) {
			if (menu.getMenuComponentCount() > 0) {
				menu.addSeparator();
			}
			try {
				final JLabel label = new JLabel(" "+ header);
				label.setEnabled(false);
				label.setForeground(getDisabledComponentColor());
				menu.add(label);
			} catch (final Exception ignored) {
				// do nothing
			}
		}

		static void addPopupMenuSeparator(final JPopupMenu menu, final String header) {
			if (menu.getComponentCount() > 1) {
				menu.addSeparator();
			}
			final JLabel label = new JLabel(header);
			// label.setHorizontalAlignment(SwingConstants.LEFT);
			label.setEnabled(false);
			label.setForeground(getDisabledComponentColor());
			menu.add(label);

		}

		static Color getDisabledComponentColor() {
			try {
				return UIManager.getColor("MenuItem.disabledForeground");
			} catch (final Exception ignored) {
				return Color.GRAY;
			}
		}

		static boolean isDarkLaF() {
			return FlatLaf.isLafDark() || isDark(new JLabel().getBackground());
		}
	
		static boolean isDark(final Color c) {
			// see https://stackoverflow.com/a/3943023
			return (c.getRed() * 0.299 + c.getGreen() * 0.587 + c.getBlue() * 0.114) < 186;
		}

		static void collapseAllTreeNodes(final JTree tree) {
			final int row1 = (tree.isRootVisible()) ? 1 : 0;
			for (int i = row1; i < tree.getRowCount(); i++)
				tree.collapseRow(i);
		}

		static void expandAllTreeNodes(final JTree tree) {
			for (int i = 0; i < tree.getRowCount(); i++)
				tree.expandRow(i);
		}

		/* Tweaks for tabbed pane */
		static JTabbedPane getJTabbedPane() {
			final JTabbedPane tabbed = new JTabbedPane();
			final JPopupMenu popup = new JPopupMenu();
			tabbed.setComponentPopupMenu(popup);
			final ButtonGroup bGroup = new ButtonGroup();
			for (final String pos : new String[] { "Top", "Left", "Bottom", "Right" }) {
				final JMenuItem jcbmi = new JCheckBoxMenuItem("Place on " + pos, "Top".equals(pos));
				jcbmi.addItemListener(e -> {
					switch (pos) {
					case "Top":
						tabbed.setTabPlacement(JTabbedPane.TOP);
						break;
					case "Bottom":
						tabbed.setTabPlacement(JTabbedPane.BOTTOM);
						break;
					case "Left":
						tabbed.setTabPlacement(JTabbedPane.LEFT);
						break;
					case "Right":
						tabbed.setTabPlacement(JTabbedPane.RIGHT);
						break;
					}
				});
				bGroup.add(jcbmi);
				popup.add(jcbmi);
			}
			tabbed.addMouseWheelListener(e -> {
				// https://stackoverflow.com/a/38463104
				final JTabbedPane pane = (JTabbedPane) e.getSource();
				final int units = e.getWheelRotation();
				final int oldIndex = pane.getSelectedIndex();
				final int newIndex = oldIndex + units;
				if (newIndex < 0)
					pane.setSelectedIndex(0);
				else if (newIndex >= pane.getTabCount())
					pane.setSelectedIndex(pane.getTabCount() - 1);
				else
					pane.setSelectedIndex(newIndex);
			});
			return tabbed;
		}
	}

	static class TextFieldWithPlaceholder extends JTextField {

		private static final long serialVersionUID = 1L;
		private String placeholder;

		void setPlaceholder(final String placeholder) {
			this.placeholder = placeholder;
			update(getGraphics());
		}

		Font getPlaceholderFont() {
			return getFont().deriveFont(Font.ITALIC);
		}

		String getPlaceholder() {
			return placeholder;
		}

		@Override
		protected void paintComponent(final java.awt.Graphics g) {
			super.paintComponent(g);
			if (getText().isEmpty()) {
				final Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				g2.setColor(getDisabledTextColor());
				g2.setFont(getPlaceholderFont());
				g2.drawString(getPlaceholder(), getInsets().left,
						g2.getFontMetrics().getHeight() + getInsets().top - getInsets().bottom);
				g2.dispose();
			}
		}

	}

}
