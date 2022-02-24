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

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import javax.swing.FocusManager;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

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
	private boolean regex;
	private boolean caseSensitive;

	@Parameter
	private AppService appService;

	FileSystemTreePanel(final FileSystemTree tree, final Context context) {
		this.tree = tree;
		context.inject(this);
		searchField = initializedField();
		setLayout(new GridBagLayout());
		final GridBagConstraints bc = new GridBagConstraints();
		bc.gridx = 0;
		bc.gridy = 0;
		bc.weightx = 0;
		bc.weighty = 0;
		bc.anchor = GridBagConstraints.NORTHWEST;
		bc.fill = GridBagConstraints.NONE;
		add(addDirectoryButton(), bc);
		bc.gridx = 1;
		add(removeDirectoryButton(), bc);
		bc.gridx = 2;
		bc.fill = GridBagConstraints.BOTH;
		bc.weightx = 1;
		add(searchField, bc);
		bc.fill = GridBagConstraints.NONE;
		bc.weightx = 0;
		bc.gridx = 3;
		add(searchOptionsButton(), bc);
		bc.gridx = 0;
		bc.gridwidth = 4;
		bc.gridy = 1;
		bc.weightx = 1.0;
		bc.weighty = 1.0;
		bc.fill = GridBagConstraints.BOTH;
		final JScrollPane treePane = new JScrollPane(tree);
		add(treePane, bc);
		new FileDrop(treePane, files -> {
			final List<File> dirs = Arrays.asList(files).stream().filter(f -> f.isDirectory())
					.collect(Collectors.toList());
			if (dirs.isEmpty()) {
				JOptionPane.showMessageDialog(this, "Only folders can be dropped into the file tree.",
						"Invalid Drop", JOptionPane.WARNING_MESSAGE);
				return;
			}
			final boolean confirm = dirs.size() < 4 || (JOptionPane.showConfirmDialog(this,
					"Confirm loading of " + dirs.size() + " folders?", "Confirm?",
					JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION);
			if (confirm) {
				dirs.forEach(dir -> tree.addRootDirectory(dir.getAbsolutePath(), true));
			}
		});
		addContextualMenuToTree();
	}

	private SearchField initializedField() {
		final SearchField field = new SearchField();
		field.addFocusListener(new FocusAdapter() {
			@Override
			public void focusLost(final FocusEvent e) {
				if (0 == field.getText().length()) {
					tree.setFileFilter(((f) -> true)); // any // no need to press enter
				}
			}
		});
		field.addKeyListener(new KeyAdapter() {
			Pattern pattern = null;

			@Override
			public void keyPressed(final KeyEvent ke) {
				if (ke.getKeyCode() == KeyEvent.VK_ENTER) {
					final String text = field.getText();
					if (0 == text.length()) {
						tree.setFileFilter(((f) -> true)); // any
						return;
					}

					if (isRegexEnabled()) { // if ('/' == text.charAt(0)) {
						// Interpret as a regular expression
						// Attempt to compile the pattern
						try {
							String regex = text; // text.substring(1);
							if ('^' != regex.charAt(1))
								regex = "^.*" + regex;
							if ('$' != regex.charAt(regex.length() - 1))
								regex += ".*$";
							pattern = Pattern.compile(regex);
							field.setForeground(tree.getForeground());
						} catch (final PatternSyntaxException | StringIndexOutOfBoundsException pse) {
							// regex is too short to be parseable or is invalid
							tree.log.warn(pse.getLocalizedMessage());
							field.setForeground(Color.RED);
							pattern = null;
							return;
						}
						if (null != pattern) {
							tree.setFileFilter((f) -> pattern.matcher(f.getName()).matches());
						}
					} else {
						// Interpret as a literal match
						if (isCaseSensitive())
							tree.setFileFilter((f) -> -1 != f.getName().indexOf(text));
						else
							tree.setFileFilter((f) -> -1 != f.getName().toLowerCase().indexOf(text.toLowerCase()));
					}
				} else {
					// Upon re-typing something
					if (field.getForeground() == Color.RED) {
						field.setForeground(tree.getForeground());
					}
				}
			}
		});
		return field;
	}

	private JButton thinButton(final String label) {
		final JButton b = new JButton(label);
		final double FACTOR = .25;
		final Insets insets =b.getMargin();
		b.setMargin(new Insets(insets.top, (int) (insets.left *
			FACTOR), insets.bottom, (int) (insets.right * FACTOR)));
		//b.setBorder(null);
		// set height to that of searchField. Do not allow vertical resizing
		b.setPreferredSize(new Dimension(b.getPreferredSize().width, (int) searchField.getPreferredSize().getHeight()));
		b.setMaximumSize(new Dimension(b.getMaximumSize().width, (int) searchField.getPreferredSize().getHeight()));
		return b;
	}

	private JButton addDirectoryButton() {
		final JButton add_directory = thinButton("<HTML>&#43;");
		add_directory.setToolTipText("Add a directory");
		add_directory.addActionListener(e -> {
			final String folders = tree.getTopLevelFoldersString();
			final String lastFolder = folders.substring(folders.lastIndexOf(":") + 1);
			final JFileChooser c = new JFileChooser();
			c.setDialogTitle("Choose Top-Level Folder");
			c.setCurrentDirectory(new File(lastFolder));
			c.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			c.setFileHidingEnabled(true); // hide hidden files
			c.setAcceptAllFileFilterUsed(false); // disable "All files" as it has no meaning here
			c.setApproveButtonText("Choose Folder");
			c.setMultiSelectionEnabled(false);
			c.setDragEnabled(true);
			new FileDrop(c, files -> {
				if (files.length == 0)
					return;
				final File firstFile = files[0];
				c.setCurrentDirectory((firstFile.isDirectory()) ? firstFile : firstFile.getParentFile());
				c.rescanCurrentDirectory();
			});
			if (JFileChooser.APPROVE_OPTION == c.showOpenDialog(this)) {
				final File f = c.getSelectedFile();
				if (f.isDirectory())
					tree.addRootDirectory(f.getAbsolutePath(), false);
			}
			FileDrop.remove(c);
		});
		return add_directory;
	}

	private JButton removeDirectoryButton() {
		final JButton remove_directory = thinButton("<HTML>&#8722;");
		remove_directory.setToolTipText("Remove a top-level directory");
		remove_directory.addActionListener(e -> {
			final TreePath p = tree.getSelectionPath();
			if (null == p) {
				JOptionPane.showMessageDialog(this, "Select a top-level folder first.", "Invalid Folder",
						JOptionPane.ERROR_MESSAGE);
				return;
			}
			if (2 == p.getPathCount()) {
				// Is a child of the root, so it's a top-level folder
				tree.getModel().removeNodeFromParent(//
						(FileSystemTree.Node) p.getLastPathComponent());
			} else {
				JOptionPane.showMessageDialog(this, "Can only remove top-level folders.", "Invalid Folder",
						JOptionPane.ERROR_MESSAGE);
			}
		});
		return remove_directory;
	}

	private JButton searchOptionsButton() {
		final JButton options = thinButton("<HTML>&#8942;");
		options.setToolTipText("Filtering options");
		final JPopupMenu popup = new JPopupMenu();
		final JCheckBoxMenuItem jcbmi1 = new JCheckBoxMenuItem("Case Sensitive", isCaseSensitive());
		jcbmi1.addItemListener(e -> {
			setCaseSensitive(jcbmi1.isSelected());
		});
		popup.add(jcbmi1);
		final JCheckBoxMenuItem jcbmi2 = new JCheckBoxMenuItem("Enable Regex", isCaseSensitive());
		jcbmi2.addItemListener(e -> {
			setRegexEnabled(jcbmi2.isSelected());
		});
		popup.add(jcbmi2);
		popup.addSeparator();
		JMenuItem jmi = new JMenuItem("Reset Filter");
		jmi.addActionListener(e -> {
			searchField.setText("");
			tree.setFileFilter(((f) -> true));
		});
		popup.add(jmi);
		popup.addSeparator();
		jmi = new JMenuItem("About File Explorer ...");
		jmi.addActionListener(e -> showHelpMsg());
		popup.add(jmi);
		options.addActionListener(e -> popup.show(options, options.getWidth() / 2, options.getHeight() / 2));
		return options;
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
		JMenuItem jmi = new JMenuItem("Collapse All");
		jmi.addActionListener(e -> collapseAllNodes());
		popup.add(jmi);
		jmi = new JMenuItem("Expand Folders");
		jmi.addActionListener(e -> expandImmediateNodes());
		popup.add(jmi);
		popup.addSeparator();
		jmi = new JMenuItem("Show in System Explorer");
		jmi.addActionListener(e -> {
			final TreePath path = tree.getSelectionPath();
			if (path == null) {
				JOptionPane.showMessageDialog(this, "No items are currently selected.", "Invalid Selection",
						JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			try {
				final String filepath = (String) ((FileSystemTree.Node) path.getLastPathComponent()).getUserObject();
				final File f = new File(filepath);
				Desktop.getDesktop().open((f.isDirectory()) ? f : f.getParentFile());
			} catch (final Exception | Error ignored) {
				JOptionPane.showMessageDialog(this, "Folder of selected item does not seem to be accessible.", "Error",
						JOptionPane.ERROR_MESSAGE);
			}
		});
		popup.add(jmi);
		popup.addSeparator();
		jmi = new JMenuItem("Reset to Home Folder");
		jmi.addActionListener(e -> changeRootPath(System.getProperty("user.home")));
		popup.add(jmi);
		jmi = new JMenuItem("Reset to Fiji.app/");
		jmi.addActionListener(e -> changeRootPath(appService.getApp().getBaseDirectory().getAbsolutePath()));
		popup.add(jmi);
		tree.setComponentPopupMenu(popup);
	}

	void changeRootPath(final String path) {
		((DefaultMutableTreeNode) tree.getModel().getRoot()).removeAllChildren();
		tree.addTopLevelFoldersFrom(path);
		tree.getModel().reload(); // this will collapse all nodes
		expandImmediateNodes();
	}

	private void collapseAllNodes() {
		for (int i = tree.getRowCount() - 1; i >= 0; i--)
			tree.collapseRow(i);
	}

	private void expandImmediateNodes() {
		for (int i = tree.getRowCount() - 1; i >= 0; i--)
			tree.expandRow(i);
	}

	private void showHelpMsg() {
		final String msg = "<HTML><div WIDTH=650>" //
				+ "<p><b>Overview</b></p>" //
				+ "<p>The File Explorer pane provides a direct view of selected folders. Changes in " //
				+ "the native file system are synchronized in real time.</p>" //
				+ "<br><p><b>Add/Remove Folders</b></p>" //
				+ "<p>To add a folder, use the [+] button, or drag &amp; drop folders from the native " //
				+ "System Explorer. To remove a folder: select it, then use the [-] button. To reset "
				+ "or reveal items: use the commands in the contextual popup menu.</p>" //
				+ "<br><p><b>Accessing Files &amp; Paths</b></p>" //
				+ "<p>Double-click on a file to open it. Drag &amp; drop items into the editor pane "
				+ "to paste their paths into the active script.</p>" //
				+ "<br><p><b>Filtering Files</b></p>" //
				+ "<p>Filters affect filenames (not folders) and are applied by typing a filtering "//
				+ "string + [Enter]. Filters act only on files being listed, and ignore collapsed " //
				+ "folders. Examples of regex usage:</p>" //
				+ "<br><table align='center'>" //
				+ "  <tr>" //
				+ "   <th>Pattern</th>" //
				+ "   <th>Result</th>" //
				+ "  </tr>" //
				+ "  <tr>" //
				+ "   <td>py$</td>" //
				+ "   <td>Display filenames ending with <i>py</i></td>" //
				+ "  </tr>" //
				+ "  <tr>" //
				+ "   <td>^Demo</td>" //
				+ "   <td>Display filenames starting with <i>Demo</i></td>" //
				+ "  </tr>" //
				+ "</table>";
		JOptionPane.showMessageDialog(this, msg, "File Explorer Pane", JOptionPane.PLAIN_MESSAGE);
	}

	private boolean isCaseSensitive() {
		return caseSensitive;
	}

	private boolean isRegexEnabled() {
		return regex;
	}

	private void setCaseSensitive(final boolean b) {
		caseSensitive = b;
		searchField.update();
	}

	private void setRegexEnabled(final boolean b) {
		regex = b;
		searchField.update();
	}

	private class SearchField extends JTextField {

		private static final long serialVersionUID = 7004232238240585434L;
		private static final String REGEX_HOLDER = "[?*]";
		private static final String CASE_HOLDER = "[Aa]";
		private static final String DEF_HOLDER = "File filter... ";

		SearchField() {
			super();
			try {
				// make sure pane is large enough to display placeholders
				final FontMetrics fm = getFontMetrics(getFont());
				final FontRenderContext frc = fm.getFontRenderContext();
				final String buf = CASE_HOLDER + REGEX_HOLDER + DEF_HOLDER;
				final Rectangle2D rect = getFont().getStringBounds(buf, frc);
				final int prefWidth = (int) rect.getWidth();
				setColumns(prefWidth / super.getColumnWidth());
			} catch (final Exception ignored) {
				// do nothing
			}
		}

		void update() {
			update(getGraphics());
		}

		@Override
		protected void paintComponent(final java.awt.Graphics g) {
			super.paintComponent(g);
			if (super.getText().isEmpty() && !(FocusManager.getCurrentKeyboardFocusManager().getFocusOwner() == this)) {
				final Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				g2.setColor(Color.GRAY);
				g2.setFont(getFont().deriveFont(Font.ITALIC));
				final StringBuilder sb = new StringBuilder(DEF_HOLDER);
				if (isCaseSensitive())
					sb.append(CASE_HOLDER);
				if (isRegexEnabled())
					sb.append(REGEX_HOLDER);
				g2.drawString(sb.toString(), 4, g2.getFontMetrics().getHeight());
				g2.dispose();
			}
		}
	}
}
