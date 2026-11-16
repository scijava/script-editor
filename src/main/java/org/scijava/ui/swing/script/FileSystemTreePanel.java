/*
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

import java.awt.*;
import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.icons.*;
import com.formdev.flatlaf.ui.FlatRoundBorder;
import com.formdev.flatlaf.util.UIScale;
import org.scijava.Context;
import org.scijava.app.AppService;
import org.scijava.plugin.Parameter;

/**
 * Convenience class for displaying a {@link FileSystemTree} with some bells and
 * whistles, including a filter toolbar.
 * 
 * @author Albert Cardona
 * @author Tiago Ferreira
 */
class FileSystemTreePanel extends JPanel {

	private static final long serialVersionUID = -710040159139542578L;
	private final FileSystemTree tree;
	private final SearchField searchField;
	private static final int SEARCH_DELAY_MS = 250;
	private static final int MAX_HISTORY = 10;
	private final List<String> history = new ArrayList<>(MAX_HISTORY + 1);
	private boolean regex;
	private boolean caseSensitive;

	private java.util.function.Supplier<File> activeScript = () -> null;
	private java.util.function.Consumer<String> statusListener = t -> {};
	private String status = "";
	private boolean userChoice; // user explicitly changed fast mode options: never auto-enable
	private boolean autoFast; // fast mode was auto-enabled already (once per session)
	private boolean autoNotice; // report the auto-enabling once the repeated search completes
	private final Timer searchingTimer = new Timer(400, e -> setStatus("Searching..."));
	private static final long SLOW_SCAN_NANOS = 1_000_000_000L;
	private static final int MIN_CHARS_COLLAPSED = 2; // shorter queries only search expanded folders
	private long scanStart; // when the last filter was applied (0 if not being timed)

	@Parameter
	private AppService appService;

	FileSystemTreePanel(final FileSystemTree tree, final Context context) {
		this.tree = tree;
		context.inject(this);
		searchField = initializedField();
		tree.setFilterListener(this::filterStatus);
		tree.setNoticeListener(this::setStatus);

		// assign contextual menu early on
		addContextualMenuToTree();

		setLayout(new GridBagLayout());
		final GridBagConstraints bc = new GridBagConstraints();
		bc.gridx = 0;
		bc.gridy = 0;
		bc.fill = GridBagConstraints.BOTH;
		bc.weightx = 1;
		bc.weighty = 0;
		add(GuiUtils.getHeader("Files", this::showHelpMsg), bc);
		bc.gridy = 1;
		add(searchField, bc);
		bc.gridy = 2;
		bc.weightx = 1.0;
		bc.weighty = 1.0;
		bc.fill = GridBagConstraints.BOTH;
		final JScrollPane treePane = new JScrollPane(tree);
		GuiUtils.flattenScrollBars(treePane);
		add(treePane, bc);
		bc.gridy = 3;
		bc.weightx = 0;
		bc.weighty = 0;
		add(statusToolbar(), bc);
		tree.fileDrop = new FileDrop(treePane, files -> {
			try {
				final List<File> dirs = Arrays.stream(files).filter(File::isDirectory).collect(Collectors.toList());
				if (dirs.isEmpty()) { // Only files dropped without directories
					addRootFromFile(files[0]);
				} else if (dirs.size() == 1) { // A single folder dropped
					addRootFromFile(dirs.get(0));
				} else if (GuiUtils.confirm(this,
						"Replace contents with the " + dirs.size() + " dropped folders?",
						"Replace Contents?", "Confirm")) {
					tree.clearTopLevelFolders(); // Blank out the entire tree
					dirs.forEach(dir -> tree.addRootDirectory(dir.getAbsolutePath(), true));
				}

			} catch (final Throwable ignored) {
				GuiUtils.warn(this, "Could not retrieve location of dropped file(s).");
			}
		});
	}

	/** Border with the color of scroll pane outlines, drawn on the left, right and bottom edges only */
	private static final class SideBorder extends AbstractBorder {

		private static final long serialVersionUID = 1L;

		private static int thickness() {
			return Math.max(1, Math.round(UIScale.scale(1f)));
		}

		@Override
		public void paintBorder(final Component c, final Graphics g, final int x, final int y, final int w,
				final int h) {
			final Color color = UIManager.getColor("Component.borderColor"); // resolved on paint: follows the LAF
			if (color == null)
				return;
			final int t = thickness();
			g.setColor(color);
			g.fillRect(x, y, t, h);
			g.fillRect(x + w - t, y, t, h);
			g.fillRect(x, y + h - t, w, t);
		}

		@Override
		public Insets getBorderInsets(final Component c) {
			final int t = thickness();
			return new Insets(0, t, t, t);
		}
	}

	private JToolBar statusToolbar() {
		final JToolBar toolbar = new GuiUtils.ToolBar();
		toolbar.setFloatable(false);
		toolbar.setBackground(tree.getBackground());
		// Continue the outline of the tree's scroll pane (the latter provides the top edge)
		toolbar.setBorder(BorderFactory.createCompoundBorder(new SideBorder(), toolbar.getBorder()));
		final JToggleButton statusFast = GuiUtils.smallToggleButton("Fast Mode");
		statusFast.setToolTipText("Fast mode for large folders: turns off live updates and the search of collapsed folders");
		statusFast.addActionListener(e -> setFastMode(statusFast.isSelected()));
		final JButton statusOptions = new JButton("Options");
		final FlatFileChooserDetailsViewIcon optionsIcon = new FlatFileChooserDetailsViewIcon();
		optionsIcon.setScale(.8f); // matches the font size set by the "small" style
		statusOptions.putClientProperty(FlatClientProperties.STYLE_CLASS, "small");
		statusOptions.setForeground(GuiUtils.secondaryColor());
		statusOptions.addActionListener(e -> tree.getComponentPopupMenu().show(statusOptions, 0, 0));
		statusOptions.setIcon(optionsIcon);
		toolbar.add(statusFast);
		toolbar.addSeparator();
		toolbar.add(statusOptions);
		return toolbar;
	}

	private void addRootFromFile(final File file) {
		if (file.isDirectory())
			tree.addRootDirectory(file.getAbsolutePath(), true);
		else
			tree.addRootDirectory(file.getParentFile().getAbsolutePath(), true);
	}

	private SearchField initializedField() {
		final SearchField field = new SearchField();
		// Live search: apply the filter once typing pauses
		final Timer liveSearch = new Timer(SEARCH_DELAY_MS, e -> applyFilter());
		liveSearch.setRepeats(false);
		field.getDocument().addDocumentListener(new DocumentListener() {

			@Override
			public void insertUpdate(final DocumentEvent e) {
				liveSearch.restart();
			}

			@Override
			public void removeUpdate(final DocumentEvent e) {
				liveSearch.restart();
			}

			@Override
			public void changedUpdate(final DocumentEvent e) {
				liveSearch.restart();
			}
		});
		// Enter: apply immediately and remember the term
		field.addActionListener(e -> {
			liveSearch.stop();
			if (applyFilter()) addToHistory(field.getText());
		});
		return field;
	}

	/** Sets the listener notified of status messages (empty string clears the message) */
	void setStatusListener(final java.util.function.Consumer<String> l) {
		statusListener = (l == null) ? t -> {} : l;
		statusListener.accept(status);
	}

	/** Fast mode: no live updates and no search of collapsed folders */
	boolean isFastMode() {
		return !tree.isLiveUpdates() && !tree.isSearchCollapsedFolders();
	}

	/** Sets fast mode on behalf of the user (disables any automatic switching) */
	void setFastMode(final boolean fast) {
		userChoice = true;
		applyFastMode(fast);
	}

	private void applyFastMode(final boolean fast) {
		tree.setLiveUpdates(!fast);
		tree.setSearchCollapsedFolders(!fast);
		searchField.setCollapsedSelected(!fast);
		if (!searchField.getText().isEmpty()) applyFilter();
	}

	private void setStatus(final String text) {
		status = (text == null) ? "" : text;
		statusListener.accept(status);
	}

	/** Called by the tree as results of the filter are found (on the EDT) */
	private void filterStatus(final int hits, final boolean truncated, final boolean done) {
		if (!done) { // Only mention the search if it takes a while
			searchingTimer.setRepeats(false);
			if (!searchingTimer.isRunning() && status.isEmpty()) searchingTimer.restart();
			// A long search of a huge tree: switch to fast mode and search again, restricted to expanded folders
			if (scanStart != 0 && System.nanoTime() - scanStart > SLOW_SCAN_NANOS && !isFastMode() && !userChoice
					&& !autoFast) {
				autoFast = true;
				autoNotice = true;
				applyFastMode(true);
			}
			return;
		}
		searchingTimer.stop();
		searchField.setInvalid(hits == 0);
		setStatus(resultSummary(hits, truncated));
		if (autoNotice) {
			autoNotice = false;
			setStatus("Large folders: Fast mode enabled. " + status);
		}
		final long elapsed = (scanStart == 0) ? 0 : System.nanoTime() - scanStart;
		scanStart = 0;
		final boolean slow = hits >= 0 && (truncated || elapsed > SLOW_SCAN_NANOS);
		if (slow && !isFastMode() && !userChoice && !autoFast) {
			autoFast = true;
			autoNotice = true;
			applyFastMode(true); // repeats the search, now faster
		} else if (slow && !isFastMode()) {
			setStatus("Searching too many files..: Enable Fast mode");
		}
	}

	/** One-line outcome of a finished search: the status stays until the next search */
	private String resultSummary(final int hits, final boolean truncated) {
		if (hits < 0) return ""; // filter lifted
		final StringBuilder sb = new StringBuilder();
		sb.append(hits == 0 ? "No matches" : hits + (tree.isHitCapped() ? "+ matches" : (hits == 1 ? " match" : " matches")));
		final List<String> notes = new ArrayList<>();
		if (tree.isHitCapped()) notes.add("refine your search");
		else if (truncated) notes.add("search limited: results incomplete");
		if (searchField.getText().length() < MIN_CHARS_COLLAPSED && tree.isSearchCollapsedFolders())
			notes.add("type " + MIN_CHARS_COLLAPSED + "+ characters to search collapsed folders");
		if (tree.isExpansionCapped()) notes.add("not all folders expanded");
		if (tree.getSkippedFolderCount() > 0) notes.add(tree.getSkippedFolderCount() + " chunk stores not searched");
		if (!notes.isEmpty()) sb.append(" (").append(String.join("; ", notes)).append(")");
		return sb.toString();
	}

	/** Applies the filter defined by the search field. Returns false if it is not valid */
	private boolean applyFilter() {
		final String text = searchField.getText();
		try {
			final FileFilter filter = filterFor(text);
			searchField.setInvalid(false);
			tree.setExpandedOnly(text.length() < MIN_CHARS_COLLAPSED);
			setStatus(""); // drop the outcome of the previous search; "Searching..." follows if this one is slow
			scanStart = text.isEmpty() ? 0 : System.nanoTime();
			tree.setFileFilter(filter, !text.isEmpty()); // hides folders without hits
			return !text.isEmpty();
		} catch (final PatternSyntaxException ignored) {
			tree.cancelFilterScan();
			searchField.setInvalid(true);
			return false;
		}
	}

	private FileFilter filterFor(final String text) throws PatternSyntaxException {
		if (text.isEmpty()) return f -> true;
		if (isRegexEnabled()) {
			// find() so that alternations (py|txt) are not split by added anchors
			final Pattern pattern = Pattern.compile(text, isCaseSensitive() ? 0 : Pattern.CASE_INSENSITIVE);
			return f -> pattern.matcher(f.getName()).find();
		}
		if (isCaseSensitive()) return f -> f.getName().contains(text);
		final String lowerCase = text.toLowerCase();
		return f -> f.getName().toLowerCase().contains(lowerCase);
	}

	private void addToHistory(final String term) {
		history.remove(term);
		history.add(term);
		if (history.size() > MAX_HISTORY) history.remove(0);
	}

	private JMenuItem addDirectoryMenuItem() {
		final JMenuItem add_directory = new JMenuItem("Other...", new FlatFileChooserNewFolderIcon());
		add_directory.setToolTipText("List a different location");
		add_directory.addActionListener(e -> {
			final String folders = tree.getTopLevelFoldersString();
			final String lastFolder = folders.substring(folders.lastIndexOf(File.pathSeparatorChar) + 1);
			final File f = GuiUtils.chooseDirectory(this, new File(lastFolder), "Choose Top-Level Folder",
					"Choose Folder");
			if (f != null && f.isDirectory()) tree.addRootDirectory(f.getAbsolutePath(), false);
		});
		return add_directory;
	}

	private JMenuItem removeDirectoryMenuItem() {
		final JMenuItem remove_directory = new JMenuItem("Remove Last");
		remove_directory.setToolTipText("Remove last top-level directory");
		remove_directory.addActionListener(e -> {
			try {
				final DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
				if (root != null && root.getChildCount() > 0) {
					DefaultMutableTreeNode lastLevel1Node = (DefaultMutableTreeNode) root.getLastChild();
					if (lastLevel1Node != null)
						tree.removeTopLevelFolder((FileSystemTree.Node) lastLevel1Node);
				}

			} catch (Exception ex) {
				GuiUtils.error(this, "Could not remove top-level folders.");
            }
		});
		return remove_directory;
	}

	private JPopupMenu searchOptionsMenu() {
		final JPopupMenu popup = new JPopupMenu();
		final JMenu historyMenu = new JMenu("Search History");
		historyMenu.setToolTipText("Press Enter on a typed term to store it in this menu");
		popup.add(historyMenu);
		popup.addSeparator();
		JMenuItem jmi = new JMenuItem("Reset Filter(s)");
		jmi.addActionListener(e -> {
			searchField.resetOptions();
			searchField.setText("");
			applyFilter();
		});
		popup.add(jmi);
		popup.addPopupMenuListener(new PopupMenuListener() {

			@Override
			public void popupMenuWillBecomeVisible(final PopupMenuEvent e) {
				historyMenu.removeAll();
				for (int i = history.size() - 1; i >= 0; i--) {
					final String term = history.get(i);
					final JMenuItem item = new JMenuItem(term);
					item.addActionListener(ev -> {
						searchField.setText(term);
						applyFilter();
					});
					historyMenu.add(item);
				}
				historyMenu.setEnabled(!history.isEmpty());
				if (!history.isEmpty()) {
					historyMenu.addSeparator();
					final JMenuItem clear = new JMenuItem("Clear History");
					clear.addActionListener(ev -> history.clear());
					historyMenu.add(clear);
				}
			}

			@Override
			public void popupMenuWillBecomeInvisible(final PopupMenuEvent e) {}

			@Override
			public void popupMenuCanceled(final PopupMenuEvent e) {}
		});
		return popup;
	}

	@SuppressWarnings("unused")
	private boolean allTreeNodesCollapsed() {
		for (int i = 0; i < tree.getRowCount(); i++)
			if (!tree.isCollapsed(i))
				return false;
		return true;
	}

	private void addContextualMenuToTree() {
		final JPopupMenu popup = new JPopupMenu();

		GuiUtils.addPopupMenuSeparator(popup, "List Folder:");
		final JMenuItem selected = new JMenuItem("Selected Folder", new FlatFileViewDirectoryIcon());
		selected.setToolTipText("Makes the selected folder (or the folder of the selected file) the only top-level folder");
		selected.addActionListener(e -> {
			final File dir = selectedDirectory();
			if (dir != null) changeRootPath(dir.getAbsolutePath());
		});
		popup.add(selected);
		final JMenuItem parent = new JMenuItem("Parent Folder", new FlatFileChooserUpFolderIcon());
		parent.setToolTipText("Lists the parent of the current top-level folder");
		parent.addActionListener(e -> {
			final File dir = parentDirectory();
			if (dir != null) changeRootPath(dir.getAbsolutePath());
		});
		popup.add(parent);
		final JMenuItem active = new JMenuItem("Folder of Active Script", new FlatFileViewFileIcon());
		active.addActionListener(e -> {
			final File dir = activeScriptDirectory();
			if (dir != null) changeRootPath(dir.getAbsolutePath());
		});
		popup.add(active);
		JMenuItem jmi = new JMenuItem(appService.getApp().getTitle() + " Folder", new FlatFileChooserHomeFolderIcon());
		jmi.addActionListener(e -> changeRootPath(appService.getApp().getBaseDirectory().getAbsolutePath()));
		popup.add(jmi);
		popup.add(addDirectoryMenuItem());

		GuiUtils.addPopupMenuSeparator(popup, "Open In:");
		final JMenuItem fileSystem = new JMenuItem("File System", new FlatFileViewComputerIcon());
		jmi = fileSystem;
		jmi.addActionListener(e -> {
			final TreePath path = tree.getSelectionPath();
			if (path == null) {
				GuiUtils.info(this, "No items are currently selected.", "Invalid Selection");
				return;
			}
			try {
				final String filepath = (String) ((FileSystemTree.Node) path.getLastPathComponent()).getUserObject();
				final File f = new File(filepath);
				Desktop.getDesktop().open((f.isDirectory()) ? f : f.getParentFile());
			} catch (final Throwable ignored) {
				GuiUtils.error(this, "Folder of selected item does not seem to be accessible.");
			}
		});
		popup.add(jmi);
		final JMenuItem terminal = new JMenuItem("Terminal", new FlatMenuArrowIcon());
		jmi = terminal;
		jmi.addActionListener(e -> {
			final TreePath path = tree.getSelectionPath();
			if (path == null) {
				GuiUtils.info(this,  "No items are currently selected.", "Invalid Selection");
				return;
			}
			try {
				final String filepath = (String) ((FileSystemTree.Node) path.getLastPathComponent()).getUserObject();
				GuiUtils.openTerminal(new File(filepath));
			} catch (final Throwable ignored) {
				GuiUtils.error(this, "Could not open path in Terminal.");
			}
		});
		popup.add(jmi);

		GuiUtils.addPopupMenuSeparator(popup, "Contents:");
		final JMenuItem collapseAll = new JMenuItem("Collapse All");
		jmi = collapseAll;
		jmi.addActionListener(e -> GuiUtils.collapseAllTreeNodes(tree));
		popup.add(jmi);
		final JMenuItem expandLevel = new JMenuItem("Expand One Level");
		jmi = expandLevel;
		jmi.setToolTipText("Expands the listed folders (repeat to go deeper)");
		jmi.addActionListener(e -> expandImmediateNodes());
		popup.add(jmi);
		final JMenuItem refresh = new JMenuItem("Refresh");
		refresh.setToolTipText("Re-lists the expanded folders");
		refresh.addActionListener(e -> tree.refresh());
		popup.add(refresh);
		final javax.swing.JCheckBoxMenuItem live = new javax.swing.JCheckBoxMenuItem("Live Updates", tree.isLiveUpdates());
		live.setToolTipText("Keeps listed folders in sync with the file system. Turn off for network or cloud folders");
		live.addActionListener(e -> {
			userChoice = true;
			tree.setLiveUpdates(live.isSelected());
		});
		popup.add(live);

		GuiUtils.addPopupMenuSeparator(popup, "Reset:");
		final JMenuItem removeLast = removeDirectoryMenuItem();
		popup.add(removeLast);
		final JMenuItem wipe = new JMenuItem("Clear All");
		wipe.addActionListener(e -> tree.clearTopLevelFolders());
		popup.add(wipe);
		popup.addPopupMenuListener(new PopupMenuListener() {

			@Override
			public void popupMenuWillBecomeVisible(final PopupMenuEvent e) {
				selected.setEnabled(selectedDirectory() != null);
				final File dir = activeScriptDirectory();
				active.setEnabled(dir != null);
				active.setToolTipText(dir == null ? "The active script has not been saved" : dir.getAbsolutePath());
				parent.setEnabled(parentDirectory() != null);
				final boolean hasSelection = tree.getSelectionPath() != null;
				fileSystem.setEnabled(hasSelection);
				terminal.setEnabled(hasSelection);
				final boolean hasRows = tree.getRowCount() > 0;
				collapseAll.setEnabled(hasRows);
				expandLevel.setEnabled(hasRows);
				refresh.setEnabled(hasRows);
				live.setSelected(tree.isLiveUpdates());
				final boolean hasFolders = !tree.getTopLevelFoldersString().isEmpty();
				removeLast.setEnabled(hasFolders);
				wipe.setEnabled(hasFolders);
			}

			@Override
			public void popupMenuWillBecomeInvisible(final PopupMenuEvent e) {}

			@Override
			public void popupMenuCanceled(final PopupMenuEvent e) {}
		});
		// Right-click selects the item under the cursor
		tree.addMouseListener(new java.awt.event.MouseAdapter() {

			@Override
			public void mousePressed(final java.awt.event.MouseEvent e) {
				select(e);
			}

			@Override
			public void mouseReleased(final java.awt.event.MouseEvent e) {
				select(e);
			}

			private void select(final java.awt.event.MouseEvent e) {
				if (!e.isPopupTrigger()) return;
				final int row = tree.getRowForLocation(e.getX(), e.getY());
				if (row >= 0) tree.setSelectionRow(row);
				else tree.clearSelection();
			}
		});
		tree.setComponentPopupMenu(popup);
	}

	/** The selected folder, or the parent of the selected file. Null if nothing is selected */
	private File selectedDirectory() {
		final TreePath path = tree.getSelectionPath();
		if (path == null) return null;
		final File f = new File((String) ((FileSystemTree.Node) path.getLastPathComponent()).getUserObject());
		final File dir = f.isDirectory() ? f : f.getParentFile();
		return (dir != null && dir.isDirectory()) ? dir : null;
	}

	/** The parent of the top-level folder holding the selection (or of the first one). Null if there is none */
	private File parentDirectory() {
		final String top = tree.getTopLevelFoldersString();
		if (top.isEmpty()) return null;
		final String[] tops = top.split(File.pathSeparator);
		String chosen = tops[0];
		final TreePath path = tree.getSelectionPath();
		if (path != null) {
			final String sel = ((FileSystemTree.Node) path.getLastPathComponent()).getUserObject().toString();
			for (final String t : tops) {
				if (sel.startsWith(t)) chosen = t;
			}
		}
		final File parent = new File(chosen).getAbsoluteFile().getParentFile();
		return (parent != null && parent.isDirectory()) ? parent : null;
	}

	/** The folder of the active script. Null if the script has not been saved */
	private File activeScriptDirectory() {
		try {
			final File f = activeScript.get();
			final File dir = (f == null) ? null : f.getAbsoluteFile().getParentFile();
			return (dir != null && dir.isDirectory()) ? dir : null;
		} catch (final RuntimeException ignored) {
			return null;
		}
	}

	/** Sets how to retrieve the file of the active script (null if not saved) */
	void setActiveScriptSupplier(final java.util.function.Supplier<File> supplier) {
		activeScript = (supplier == null) ? () -> null : supplier;
	}

	void changeRootPath(final String path) {
		tree.clearTopLevelFolders();
		tree.addRootDirectory(path, false); // NB: not addTopLevelFoldersFrom(), as paths may contain separators
		expandImmediateNodes();
	}

	private void expandImmediateNodes() {
		for (int i = tree.getRowCount() - 1; i >= 0; i--)
			tree.expandRow(i);
	}

	private void showHelpMsg() {
		final String msg = "<HTML><div width=500>" //
				+ "The Files pane provides a direct view of selected folders. Changes in " //
				+ "the native file system are synchronized in real time (turn <i>Live Updates</i> off in the " //
				+ "contextual menu for network or cloud folders, and use <i>Refresh</i> to update on demand). " //
				+ "Hidden files and folders are not listed." //
				+ "<h3>Add/Remove Folders</h3>" //
				+ "To add a folder, drag &amp; drop it from the native System Explorer, or use " //
				+ "<i>Other...</i> in the contextual menu (right-click). The same menu " //
				+ "lists the selected folder, its parent, or the folder of the active script, and " //
				+ "removes the last folder or all folders." //
				+ "<h3>Accessing Files &amp; Paths</h3>" //
				+ "Double-click on a file to open it. Drag &amp; drop items into the editor pane " //
				+ "to paste their paths into the active script." //
				+ "<h3>Filtering</h3>" //
				+ "Filtering is applied as you type. Only files and folders with matching names are " //
				+ "listed: folders without matches are hidden, and the folders leading to matches are " //
				+ "expanded. A folder whose name matches is listed with all of its content. " //
				+ "Press [Enter] to store a term in the search history (magnifier icon). Options:" //
				+ "<dl style='margin-left: 15px;'>" //
				+ "<dt><b><⌄</b><</dt>" //
				+ "<dd style='margin-left: 20px;'>Also search collapsed folders (default). If deselected, only " //
				+ "expanded folders are searched, which is faster in very large folders</dd>" //
				+ "<dt><b>Cc</b></dt>" //
				+ "<dd style='margin-left: 20px;'>Match case</dd>" //
				+ "<dt><b>.*</b><</dt>" //
				+ "<dd style='margin-left: 20px;'>Regular expressions. Examples: <i>py$</i> (names ending with <i>py</i>), " //
				+ "<i>^Demo</i> (names starting with <i>Demo</i>)</dd>" //
				+ "</dl></div></HTML>";

		GuiUtils.showHTMLDialog(this.getRootPane(), "Files Pane", msg);
	}

	private boolean isCaseSensitive() {
		return caseSensitive;
	}

	private boolean isRegexEnabled() {
		return regex;
	}

	private void setCaseSensitive(final boolean b) {
		caseSensitive = b;
		applyFilter();
	}

	private void setRegexEnabled(final boolean b) {
		regex = b;
		applyFilter();
	}

	private void setCollapsedEnabled(final boolean b) {
		userChoice = true;
		tree.setSearchCollapsedFolders(b);
		applyFilter();
	}

	private class SearchField extends TextFieldWithPlaceholder {

		private static final long serialVersionUID = 7004232238240585434L;
		private static final String DEF_HOLDER = "File filter...";
		private final JToggleButton caseToggle;
		private final JToggleButton regexToggle;
		private final JToggleButton collapsedToggle;

		SearchField() {
			setPlaceholder(DEF_HOLDER);
			// Filtering options (and history) are accessed from the search icon
			final JButton options = new JButton(new FlatSearchWithHistoryIcon(true));
			options.setToolTipText("Filtering options");
			options.setFocusable(false);
			options.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
			final JPopupMenu menu = searchOptionsMenu();
			options.addActionListener(e -> menu.show(options, 0, options.getHeight()));
			putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_COMPONENT, options);
			// Case and regex toggles live inside the field (FlatLaf)
			caseToggle = optionToggle("Cc", "Match case", FileSystemTreePanel.this::setCaseSensitive);
			regexToggle = optionToggle(".*", "Use regular expressions", FileSystemTreePanel.this::setRegexEnabled);
			regexToggle.setFont(regexToggle.getFont().deriveFont(Font.BOLD));
			collapsedToggle = optionToggle(null, "Include collapsed folders in searches (slower)", FileSystemTreePanel.this::setCollapsedEnabled);
			collapsedToggle.setSelected(tree.isSearchCollapsedFolders()); // reflect the default
			// Tree chevrons (collapsed/expanded) instead of text
			collapsedToggle.setIcon(Icons.chevron(false));
			collapsedToggle.setSelectedIcon(Icons.chevron(true));

			final JToolBar tb = new GuiUtils.ToolBar();
			tb.setFloatable(false);
			tb.setMargin(new Insets(0, 0, 0, 2));
			tb.add(collapsedToggle);
			tb.add(caseToggle);
			tb.add(regexToggle);
			putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, tb);
			enlarge(1.1f);
		}

		/** Slightly larger text, with padding proportional to the font */
		private void enlarge(final float enlargeFactor) {
			// Normalize by the OS scale so the enlargement is consistent across platforms
			final float factor = 1f + (enlargeFactor - 1f) / osScale();
			setFont(getFont().deriveFont(getFont().getSize() * factor));
			for (final JToggleButton b : Arrays.asList(collapsedToggle, caseToggle, regexToggle))
				b.setFont(b.getFont().deriveFont(b.getFont().getSize() * factor));
			equalizeToggles();
			final int padding = getFontMetrics(getFont()).getDescent();
			setBorder(BorderFactory.createCompoundBorder(new FlatRoundBorder(),
					BorderFactory.createEmptyBorder(padding, padding, padding, padding)));
		}

		/**
		 * Gives all toggles the size of the largest one. Must be called once their
		 * fonts are final (the toolbar layout ignores minimum sizes)
		 */
		private void equalizeToggles() {
			final List<JToggleButton> toggles = Arrays.asList(collapsedToggle, caseToggle, regexToggle);
			int w = 0;
			int h = 0;
			for (final JToggleButton b : toggles) {
				b.setPreferredSize(null); // measure the natural size
				final Dimension d = b.getPreferredSize();
				w = Math.max(w, d.width);
				h = Math.max(h, d.height);
			}
			final Dimension size = new Dimension(w, h);
			for (final JToggleButton b : toggles) {
				b.setPreferredSize(size);
				b.setMinimumSize(size);
				b.setMaximumSize(size);
			}
		}

		/** Ratio between FlatLaf's UI scale and the display's scale */
		private float osScale() {
			try {
				final GraphicsConfiguration gc = getGraphicsConfiguration();
				final double device = (gc == null)
						? GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
								.getDefaultConfiguration().getDefaultTransform().getScaleX()
						: gc.getDefaultTransform().getScaleX();
				return (float) (Math.max(1.0, UIScale.getUserScaleFactor()) / Math.max(1.0, device));
			} catch (final Exception ignored) {
				return 1f;
			}
		}

		private JToggleButton optionToggle(final String label, final String tooltip, final java.util.function.Consumer<Boolean> setter) {
			// Same tint as the search icon (it follows the look and feel)
			final JToggleButton b = new JToggleButton(label) {

				private static final long serialVersionUID = 1L;

				@Override
				public Color getForeground() {
					final Color c = GuiUtils.secondaryColor();
					return (c != null) ? c : super.getForeground();
				}
			};
			b.setToolTipText(tooltip);
			b.setFocusable(false);
			b.setFont(b.getFont().deriveFont(b.getFont().getSize() * .85f));
			b.addActionListener(e -> setter.accept(b.isSelected()));
			return b;
		}

		void setInvalid(final boolean invalid) {
			putClientProperty(FlatClientProperties.OUTLINE, invalid ? FlatClientProperties.OUTLINE_ERROR : null);
		}

		void setCollapsedSelected(final boolean selected) {
			collapsedToggle.setSelected(selected);
		}

		void resetOptions() {
			caseToggle.setSelected(false);
			regexToggle.setSelected(false);
			collapsedToggle.setSelected(true); // default: also search collapsed folders
			tree.setSearchCollapsedFolders(true);
			caseSensitive = false;
			regex = false;
		}
	}
}
