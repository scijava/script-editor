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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.RenderingHints;
import java.awt.Insets;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.FontMetrics;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import javax.swing.*;

import org.scijava.platform.PlatformService;
import org.scijava.util.PlatformUtils;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.icons.FlatHelpButtonIcon;

/** Static helpers for dialogs, menus and widgets shared by the script editor */
final class GuiUtils {

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
		final Object[] options = { yesButtonLabel, "Cancel" };
		return JOptionPane.showOptionDialog(parent, message, title, JOptionPane.DEFAULT_OPTION,
				JOptionPane.QUESTION_MESSAGE, null, options, options[0]) == 0;
	}

	/**
	 * Save dialog proposing a file name (JFileChooser selects it, unlike a plain
	 * directory-based prompt). Returns null if cancelled
	 */
	static File chooseSaveFile(final Component parent, final File suggestion, final String title) {
		return showChooser(parent, newChooser(suggestion, title, true), true);
	}

	/**
	 * Open dialog. The start location may be a directory or a file. Returns null
	 * if cancelled
	 */
	static File chooseOpenFile(final Component parent, final File start, final String title) {
		return showChooser(parent, newChooser(start, title, false), false);
	}

	/** Folder dialog. Returns null if cancelled */
	static File chooseDirectory(final Component parent, final File start, final String title,
			final String approveText) {
		final JFileChooser chooser = newChooser(start, title, false);
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setFileHidingEnabled(true);
		chooser.setAcceptAllFileFilterUsed(false); // has no meaning for folders
		chooser.setMultiSelectionEnabled(false);
		if (approveText != null) chooser.setApproveButtonText(approveText);
		return showChooser(parent, chooser, false);
	}

	private static JFileChooser newChooser(final File start, final String title, final boolean save) {
		final JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle(title);
		chooser.setDragEnabled(true);
		if (start != null) {
			if (start.isDirectory()) {
				chooser.setCurrentDirectory(start);
			} else {
				if (start.getParentFile() != null) chooser.setCurrentDirectory(start.getParentFile());
				if (save || start.exists()) chooser.setSelectedFile(start);
			}
		}
		return chooser;
	}

	/** Every file dialog goes through here so that all of them accept dropped files */
	private static File showChooser(final Component parent, final JFileChooser chooser,
			final boolean save) {
		final FileDrop drop = new FileDrop(chooser, files -> {
			if (files == null || files.length == 0 || files[0] == null) return;
			final File ref = files[0].getAbsoluteFile();
			if (ref.isDirectory()) {
				chooser.setCurrentDirectory(ref);
			} else if (ref.getParentFile() != null) {
				chooser.setCurrentDirectory(ref.getParentFile());
				if (!save && chooser.getFileSelectionMode() != JFileChooser.DIRECTORIES_ONLY)
					chooser.setSelectedFile(ref);
			}
		});
		try {
			final int result = save ? chooser.showSaveDialog(parent) : chooser.showOpenDialog(parent);
			return (result == JFileChooser.APPROVE_OPTION) ? chooser.getSelectedFile() : null;
		} finally {
			drop.uninstall();
		}
	}

	/** Outcome of the prompt of {@link #confirmUnsaved} */
	enum UnsavedChoice { SAVE, DISCARD, CANCEL }

	/** Asks what to do with unsaved changes. Closing the dialog counts as cancel */
	static UnsavedChoice confirmUnsaved(final Component parent, final String message,
			final String title) {
		final Object[] options = { "Save", "Discard", "Cancel" };
		final int choice = JOptionPane.showOptionDialog(parent, message, title,
				JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
		return switch (choice) {
			case 0 -> UnsavedChoice.SAVE;
			case 1 -> UnsavedChoice.DISCARD;
			default -> UnsavedChoice.CANCEL;
		};
	}

	/**
	 * A non-floatable toolbar without the drag listeners of its L&F. They throw
	 * an IllegalComponentStateException if the toolbar is hidden during a drag
	 * (e.g., when a window auto-hides)
	 */
	static class ToolBar extends javax.swing.JToolBar {

		@Override
		public void updateUI() {
			super.updateUI();
			setFloatable(false);
			for (final java.awt.event.MouseMotionListener l : getMouseMotionListeners()) {
				if (l.getClass().getName().startsWith("javax.swing.plaf.basic.BasicToolBarUI"))
					removeMouseMotionListener(l);
			}
		}
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

	/** Readable form of a key stroke, e.g., "Ctrl Shift P", using glyphs on macOS */
	static String prettifiedKey(final KeyStroke key) {
		if (key == null)
			return "";
		final StringBuilder s = new StringBuilder();
		final int m = key.getModifiers();
		if ((m & InputEvent.CTRL_DOWN_MASK) != 0) {
			s.append((PlatformUtils.isMac()) ? "\u2303 " : "Ctrl ");
		}
		if ((m & InputEvent.META_DOWN_MASK) != 0) {
			s.append((PlatformUtils.isMac()) ? "\u2318 " : "Ctrl ");
		}
		if ((m & InputEvent.ALT_DOWN_MASK) != 0) {
			s.append((PlatformUtils.isMac()) ? "\u2387 " : "Alt ");
		}
		if ((m & InputEvent.SHIFT_DOWN_MASK) != 0) {
			s.append("\u21e7 ");
		}
		if ((m & InputEvent.BUTTON1_DOWN_MASK) != 0) {
			s.append("L-click ");
		}
		if ((m & InputEvent.BUTTON2_DOWN_MASK) != 0) {
			s.append("R-click ");
		}
		if ((m & InputEvent.BUTTON3_DOWN_MASK) != 0) {
			s.append("M-click ");
		}
		switch (key.getKeyEventType()) {
		case KeyEvent.KEY_TYPED:
			s.append(key.getKeyChar()).append(" ");
			break;
		case KeyEvent.KEY_PRESSED:
		case KeyEvent.KEY_RELEASED:
			s.append(getKeyText(key.getKeyCode())).append(" ");
			break;
		default:
			break;
		}
		return s.toString();
	}

	private static String getKeyText(final int keyCode) {
		if (keyCode >= KeyEvent.VK_0 && keyCode <= KeyEvent.VK_9
				|| keyCode >= KeyEvent.VK_A && keyCode <= KeyEvent.VK_Z) {
			return String.valueOf((char) keyCode);
		}
            return switch (keyCode) {
                case KeyEvent.VK_COMMA -> ",";
                case KeyEvent.VK_PERIOD -> ".";
                case KeyEvent.VK_SLASH -> "/";
                case KeyEvent.VK_SEMICOLON -> ";";
                case KeyEvent.VK_EQUALS -> "=";
                case KeyEvent.VK_OPEN_BRACKET -> "[";
                case KeyEvent.VK_BACK_SLASH -> "\\";
                case KeyEvent.VK_CLOSE_BRACKET -> "]";
                case KeyEvent.VK_ENTER -> "\u21b5";
                case KeyEvent.VK_BACK_SPACE -> "\u232b";
                case KeyEvent.VK_TAB -> "\u21b9";
                case KeyEvent.VK_CANCEL -> "Cancel";
                case KeyEvent.VK_CLEAR -> "Clear";
                case KeyEvent.VK_PAUSE -> "Pause";
                case KeyEvent.VK_CAPS_LOCK -> "Caps Lock";
                case KeyEvent.VK_ESCAPE -> "Esc";
                case KeyEvent.VK_SPACE -> "Space";
                case KeyEvent.VK_PAGE_UP -> "\u21de";
                case KeyEvent.VK_PAGE_DOWN -> "\u21df";
                case KeyEvent.VK_END -> "END";
                case KeyEvent.VK_HOME -> "Home";
                case KeyEvent.VK_LEFT -> "\u2190";
                case KeyEvent.VK_UP -> "\u2191";
                case KeyEvent.VK_RIGHT -> "\u2192";
                case KeyEvent.VK_DOWN -> "\u2193";
                case KeyEvent.VK_MULTIPLY -> "[Num \u00d7]";
                case KeyEvent.VK_ADD -> "[Num +]";
                case KeyEvent.VK_SUBTRACT -> "[Num \u2212]";
                case KeyEvent.VK_DIVIDE -> "[Num /]";
                case KeyEvent.VK_DELETE -> "\u2326";
                case KeyEvent.VK_INSERT -> "Ins";
                case KeyEvent.VK_BACK_QUOTE -> "`";
                case KeyEvent.VK_QUOTE -> "'";
                case KeyEvent.VK_AMPERSAND -> "&";
                case KeyEvent.VK_ASTERISK -> "*";
                case KeyEvent.VK_QUOTEDBL -> "\"";
                case KeyEvent.VK_LESS -> "<";
                case KeyEvent.VK_GREATER -> ">";
                case KeyEvent.VK_BRACELEFT -> "{";
                case KeyEvent.VK_BRACERIGHT -> "}";
                case KeyEvent.VK_COLON -> ":";
                case KeyEvent.VK_CIRCUMFLEX -> "^";
                case KeyEvent.VK_DEAD_TILDE -> "~";
                case KeyEvent.VK_DOLLAR -> "$";
                case KeyEvent.VK_EXCLAMATION_MARK -> "!";
                case KeyEvent.VK_LEFT_PARENTHESIS -> "(";
                case KeyEvent.VK_MINUS -> "-";
                case KeyEvent.VK_PLUS -> "+";
                case KeyEvent.VK_RIGHT_PARENTHESIS -> ")";
                case KeyEvent.VK_UNDERSCORE -> "_";
                default -> KeyEvent.getKeyText(keyCode);
            };
	}

	/**
	 * The LAF's secondary color, as a plain Color: FlatLaf ignores or resets
	 * foregrounds that are UIResources
	 */
	static Color secondaryColor() {
		try {
			final Color c = UIManager.getColor("SearchField.searchIconColor");
			return (c != null) ? new Color(c.getRGB(), true) : getDisabledComponentColor();
		} catch (final Exception ignored) {
			return getDisabledComponentColor();
		}
	}

	static Color accentColor() {
		try {
			Color c = UIManager.getColor("Component.accentColor");
			if (c == null) c = UIManager.getColor("Component.focusColor");
			return (c == null) ? new Color(0x3E73B9) : new Color(c.getRGB()); // plain color: UIResource ones are reset
		} catch (final Exception ignored) {
			// do nothing
		}
		return new Color(0x3E73B9);
	}

	static boolean isDarkLaF() {
		return FlatLaf.isLafDark() || isDark(new JLabel().getBackground());
	}

	static boolean isDark(final Color c) {
		// see https://stackoverflow.com/a/3943023
		return (c.getRed() * 0.299 + c.getGreen() * 0.587 + c.getBlue() * 0.114) < 186;
	}

	/** Small "+" button for the trailing area of a tabbed pane */
	static JButton newTabButton(final Runnable action) {
		final JButton b = new JButton("+");
		b.setToolTipText("New file");
		b.setFocusable(false);
		b.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
		b.addActionListener(e -> action.run());
		return b;
	}

	static JButton ellipsisButton(final Runnable action) {
		final JButton b = new JButton(Icons.ellipsisVertical());
		b.setFocusable(false);
		b.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
		if (action != null) b.addActionListener(e -> action.run());
		b.setForeground(secondaryColor()); // the icon follows the foreground. Set last: style updates reset UI foregrounds
		return b;
	}


	/** Small round "?" button, in the style of the editor's other help buttons */
	static JButton helpButton(final Runnable action) {
		return helpButton(action, .75f, true);
	}

	private static JButton helpButton(final Runnable action, final float scale, final boolean borderless) {
		final FlatHelpButtonIcon icon = new FlatHelpButtonIcon();
		icon.setScale(scale);
		icon.applyStyleProperty("questionMarkColor", secondaryColor());
		icon.applyStyleProperty("background", UIManager.getColor("Panel.background"));
		final JButton b = new JButton(icon);
		b.setFocusable(false);
		b.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
		if (borderless) {
			b.putClientProperty("JButton.buttonType", "borderless");
			b.setBorder(null);
		}
		if (action != null) b.addActionListener(e -> action.run());
		return b;
	}

	/**
	 * Rounded scrollbar thumbs without arrow buttons (FlatLaf only), on tracks that blend into the
	 * view: the tracks and corner follow the background of the view, e.g., after a theme change
	 */
	static void flattenScrollBars(final JScrollPane scrollPane) {
		final Component view = scrollPane.getViewport().getView();
		final Runnable apply = () -> {
			String style = "showButtons: false; trackArc: 999; thumbArc: 999; "
					+ "trackInsets: 2,2,2,2; thumbInsets: 2,2,2,2";
			final Color bg = (view == null) ? null : view.getBackground();
			if (bg != null) {
				final String hex = String.format("#%06x", bg.getRGB() & 0xFFFFFF);
				style += "; track: " + hex + "; hoverTrackColor: " + hex;
				Component corner = scrollPane.getCorner(JScrollPane.LOWER_TRAILING_CORNER);
				if (corner == null) {
					corner = new JPanel();
					scrollPane.setCorner(JScrollPane.LOWER_TRAILING_CORNER, corner);
				}
				corner.setBackground(bg);
			}
			scrollPane.getVerticalScrollBar().putClientProperty(FlatClientProperties.STYLE, style);
			scrollPane.getHorizontalScrollBar().putClientProperty(FlatClientProperties.STYLE, style);
			if (bg != null) {
				// the scroll bar background shows as a 1px fringe around the rounded track
				scrollPane.getVerticalScrollBar().setBackground(bg);
				scrollPane.getHorizontalScrollBar().setBackground(bg);
			}
		};
		apply.run();
		if (view != null) view.addPropertyChangeListener("background", e -> apply.run());
	}

	/**
	 * Removes the divider of a vertical (top/bottom) split pane and lets {@code handle} act as
	 * the divider instead. {@code handle} must sit at the top edge of the lower component, e.g.,
	 * a header label or a toolbar. Dragging any part of it that does not handle mouse events
	 * itself (labels, glue, gaps) moves the split. If the split pane is later switched to a
	 * horizontal (left/right) orientation, its regular divider is restored.
	 */
	static void installSplitHandle(final JComponent handle, final JSplitPane split) {
		split.setContinuousLayout(true);
		split.setOneTouchExpandable(false);
		split.putClientProperty(FlatClientProperties.STYLE, "style: plain;");
		if (handle == null) return;
		final int regularSize = split.getDividerSize();
		final Runnable update = () -> {
			final boolean active = split.getOrientation() == JSplitPane.VERTICAL_SPLIT;
			split.setDividerSize(active ? 0 : regularSize);
			handle.setCursor(active ? java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.N_RESIZE_CURSOR) : null);
		};
		// children must not inherit the resize cursor
		final java.util.function.Consumer<java.awt.Container>[] resetCursors = new java.util.function.Consumer[1];
		resetCursors[0] = parent -> {
			for (final java.awt.Component child : parent.getComponents()) {
				if (!child.isCursorSet()) child.setCursor(java.awt.Cursor.getDefaultCursor());
				if (child instanceof java.awt.Container) resetCursors[0].accept((java.awt.Container) child);
			}
		};
		resetCursors[0].accept(handle);
		update.run();
		split.addPropertyChangeListener(JSplitPane.ORIENTATION_PROPERTY, evt -> update.run());
		final int[] grab = new int[1];
		handle.addMouseListener(new java.awt.event.MouseAdapter() {

			@Override
			public void mousePressed(final java.awt.event.MouseEvent e) {
				grab[0] = e.getY();
			}
		});
		handle.addMouseMotionListener(new java.awt.event.MouseAdapter() {

			@Override
			public void mouseDragged(final java.awt.event.MouseEvent e) {
				if (split.getOrientation() != JSplitPane.VERTICAL_SPLIT) return;
				final int y = SwingUtilities.convertPoint(handle, e.getPoint(), split).y;
				split.setDividerLocation(Math.max(0, y - grab[0]));
			}
		});
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
		return getJTabbedPane(JTabbedPane.TOP);
	}

	/** As {@link #getJTabbedPane()}, with the tabs initially at the given placement (JTabbedPane.TOP, LEFT, ...) */
	static JTabbedPane getJTabbedPane(final int initialPlacement) {
		final JTabbedPane tabbed = new JTabbedPane();
		tabbed.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
		tabbed.putClientProperty(FlatClientProperties.TABBED_PANE_TABS_POPUP_POLICY, "asNeeded"); // overflow dropdown
		tabbed.putClientProperty(FlatClientProperties.TABBED_PANE_SCROLL_BUTTONS_POLICY, "never"); // do not show arrows
		tabbed.putClientProperty(FlatClientProperties.TABBED_PANE_TAB_ROTATION,
				FlatClientProperties.TABBED_PANE_TAB_ROTATION_AUTO);
		final JPopupMenu popup = new JPopupMenu();
		tabbed.setComponentPopupMenu(popup);
		GuiUtils.addPopupMenuSeparator(popup, "Tab Style:");
		ButtonGroup bGroup = new ButtonGroup();
		for (final String option : new String[]{"Card", "Underlined"}) {
			final JCheckBoxMenuItem jcbmi = new JCheckBoxMenuItem(option, "Underlined".equals(option));
			jcbmi.addItemListener(e -> tabbed.putClientProperty(FlatClientProperties.TABBED_PANE_TAB_TYPE,
					option.toLowerCase()));
			bGroup.add(jcbmi);
			popup.add(jcbmi);
		}
		GuiUtils.addPopupMenuSeparator(popup, "Tab Placement:");
		bGroup = new ButtonGroup();
		for (final String pos : new String[] { "Top", "Left", "Bottom", "Right" }) {
			final JMenuItem jcbmi = new JCheckBoxMenuItem(pos, pos.equalsIgnoreCase(placementName(initialPlacement)));
			jcbmi.addItemListener(e -> {
				if (e.getStateChange() != java.awt.event.ItemEvent.SELECTED) return; // ignore the deselected item
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
		final JButton triggerButton = ellipsisButton(null);
		triggerButton.addActionListener( e -> popup.show(triggerButton,
				triggerButton.getWidth() /2, triggerButton.getHeight() /2));
		// With leading alignment the trailing component is stretched to the free
		// space: a wrapper keeps the button itself flushed to the right
		final JPanel trailing = new JPanel(new java.awt.BorderLayout());
		trailing.setOpaque(false);
		trailing.add(triggerButton, java.awt.BorderLayout.EAST);
		// With tabs on the left/right the free space is vertical: flush the button to the bottom instead
		tabbed.addPropertyChangeListener("tabPlacement", evt -> {
			final boolean vertical = tabbed.getTabPlacement() == JTabbedPane.LEFT
					|| tabbed.getTabPlacement() == JTabbedPane.RIGHT;
			// dots run across the tab strip: vertical for tabs on top/bottom, horizontal for left/right
			triggerButton.setIcon(vertical ? Icons.ellipsis() : Icons.ellipsisVertical());
			trailing.add(triggerButton, vertical ? java.awt.BorderLayout.SOUTH : java.awt.BorderLayout.EAST);
			trailing.revalidate();
			trailing.repaint();
		});
		tabbed.putClientProperty(FlatClientProperties.TABBED_PANE_TRAILING_COMPONENT, trailing);
		if (initialPlacement != JTabbedPane.TOP) tabbed.setTabPlacement(initialPlacement); // after the listener: updates the button
		return tabbed;
	}

	private static String placementName(final int placement) {
		return switch (placement) {
			case JTabbedPane.LEFT -> "Left";
			case JTabbedPane.BOTTOM -> "Bottom";
			case JTabbedPane.RIGHT -> "Right";
			default -> "Top";
		};
	}

	/** Returns the color with the given opacity (0-100) */
	static Color alpha(final Color c, final int percent) {
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(percent / 100f * 255));
	}

	/** Returns a color that is weight of a plus (1 - weight) of b */
	static Color mix(final Color a, final Color b, final double weight) {
		final double w = Math.clamp(weight, 0d, 1d);
		return new Color((int) Math.round(a.getRed() * w + b.getRed() * (1 - w)),
				(int) Math.round(a.getGreen() * w + b.getGreen() * (1 - w)),
				(int) Math.round(a.getBlue() * w + b.getBlue() * (1 - w)));
	}

	/** Paints the scroll bar tracks and corner of the scroll pane with the given color */
	static void recolorTracks(final JScrollPane scrollPane, final Color color) {
		Component corner = scrollPane.getCorner(JScrollPane.LOWER_TRAILING_CORNER);
		if (corner == null) {
			corner = new JPanel();
			scrollPane.setCorner(JScrollPane.LOWER_TRAILING_CORNER, corner);
		}
		corner.setBackground(color);
		final Map<String, Object> style = Map.of("track", color, "hoverTrackColor", color);
		scrollPane.getHorizontalScrollBar().putClientProperty(FlatClientProperties.STYLE, style);
		scrollPane.getVerticalScrollBar().putClientProperty(FlatClientProperties.STYLE, style);
	}

	/**
	 * Paints centered, word-wrapped hint text over an empty component (e.g., a list or a text area), in the
	 * secondary color. To be called from paintComponent(), after the superclass has painted. Each argument is
	 * a paragraph.
	 */
	static void paintEmptyHint(final Graphics g, final JComponent c, final String... paragraphs) {
		final Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2.setFont(c.getFont());
			g2.setColor(secondaryColor());
			final FontMetrics fm = g2.getFontMetrics();
			final Insets in = c.getInsets();
			final int maxWidth = Math.max(20, c.getWidth() - in.left - in.right - 2 * fm.getHeight());
			final List<String> lines = new ArrayList<>();
			final List<Boolean> paragraphStart = new ArrayList<>();
			for (final String paragraph : paragraphs) {
				final StringBuilder line = new StringBuilder();
				boolean first = true;
				for (final String word : paragraph.split(" ")) {
					if (line.length() > 0 && fm.stringWidth(line + " " + word) > maxWidth) {
						lines.add(line.toString());
						paragraphStart.add(first);
						first = false;
						line.setLength(0);
					}
					line.append(line.length() > 0 ? " " : "").append(word);
				}
				lines.add(line.toString());
				paragraphStart.add(first);
			}
			final int gap = fm.getHeight() / 3;
			final int height = fm.getHeight() * lines.size() + gap * (paragraphs.length - 1);
			int y = (c.getHeight() - height) / 2 + fm.getAscent();
			for (int i = 0; i < lines.size(); i++) {
				if (i > 0 && paragraphStart.get(i)) y += gap;
				g2.drawString(lines.get(i), (c.getWidth() - fm.stringWidth(lines.get(i))) / 2, y);
				y += fm.getHeight();
			}
		} finally {
			g2.dispose();
		}
	}

	/** Makes the window containing the component draggable by it */
	static void enableDrag(final JComponent component) {
		final Point[] dragStart = { null };
		component.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(final MouseEvent e) {
				dragStart[0] = e.getPoint();
			}

			@Override
			public void mouseReleased(final MouseEvent e) {
				dragStart[0] = null;
			}
		});
		component.addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseDragged(final MouseEvent e) {
				if (dragStart[0] != null) {
					final java.awt.Window w = SwingUtilities.getWindowAncestor(component);
					if (w == null)
						return;
					final Point loc = w.getLocation();
					w.setLocation(loc.x + e.getX() - dragStart[0].x, loc.y + e.getY() - dragStart[0].y);
				}
			}
		});
	}
}
