/*-
 * #%L
 * Script Editor and Interpreter for SciJava script languages.
 * %%
 * Copyright (C) 2009 - 2023 SciJava developers.
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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.MenuElement;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import org.fife.ui.rtextarea.RecordableTextAction;
import org.scijava.util.PlatformUtils;

class CommandPalette {

	private static final String NAME = "Command Palette...";

	/** Settings. Ought to become adjustable some day */
	private static final KeyStroke ACCELERATOR = KeyStroke.getKeyStroke(KeyEvent.VK_P,
			java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask() | KeyEvent.SHIFT_DOWN_MASK);
	private static final int TABLE_ROWS = 6; // no. of commands to be displayed
	private static final float OPACITY = 1f; // 0-1 range
	private static final boolean IGNORE_WHITESPACE = true; // Ignore white spaces while matching?
	private static Palette frame;

	private SearchField searchField;
	private CmdTable table;
	private final TextEditor textEditor;
	private final CmdAction noHitsCmd;
	private final CmdScrapper cmdScrapper;

	public CommandPalette(final TextEditor textEditor) {
		this.textEditor = textEditor;
		noHitsCmd = new SearchWebCmd();
		cmdScrapper = new CmdScrapper(textEditor);
	}

	void install(final JMenu toolsMenu) {
		final Action action = new AbstractAction(NAME) {

			private static final long serialVersionUID = -7030359886427866104L;

			@Override
			public void actionPerformed(final ActionEvent e) {
				toggleVisibility();
			}

		};
		action.putValue(Action.ACCELERATOR_KEY, ACCELERATOR);
		toolsMenu.add(new JMenuItem(action));
	}

	Map<String, String> getShortcuts() {
		if (!cmdScrapper.scrapeSuccessful())
			cmdScrapper.scrape();
		final TreeMap<String, String> result = new TreeMap<>();
		cmdScrapper.getCmdMap().forEach((id, cmdAction) -> {
			if (cmdAction.hotkey != null && !cmdAction.hotkey.isEmpty())
				result.put(id, cmdAction.hotkey);

		});
		return result;
	}

	Map<String, String> getRecordableActions() {
		if (!cmdScrapper.scrapeSuccessful())
			cmdScrapper.scrape();
		final TreeMap<String, String> result = new TreeMap<>();
		cmdScrapper.getCmdMap().forEach((id, cmdAction) -> {
			if (cmdAction.recordable())
				result.put(id, cmdAction.hotkey);

		});
		return result;
	}

	void register(final AbstractButton button, final String description) {
		cmdScrapper.registerOther(button, description);
	}

	void dispose() {
		if (frame != null) frame.dispose();
		frame = null;
	}

	private void hideWindow() {
		if (frame != null) frame.setVisible(false);
	}

	private void assemblePalette() {
		if (frame != null)
			return;
		frame = new Palette();
		frame.setLayout(new BorderLayout());
		searchField = new SearchField();
		frame.add(searchField, BorderLayout.NORTH);
		searchField.getDocument().addDocumentListener(new PromptDocumentListener());
		final InternalKeyListener keyListener = new InternalKeyListener();
		searchField.addKeyListener(keyListener);
		table = new CmdTable();
		table.addKeyListener(keyListener);
		table.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(final MouseEvent e) {
				if (e.getClickCount() == 2 && table.getSelectedRow() > -1) {
					runCmd(table.getInternalModel().getCommand(table.getSelectedRow()));
				}
			}
		});
		populateList("");
		frame.add(table.getScrollPane());
		frame.pack();
	}

	private String[] makeRow(final CmdAction ca) {
		return new String[] {ca.id, ca.description()};
	}

	private void populateList(final String matchingSubstring) {
		final ArrayList<String[]> list = new ArrayList<>();
		if (!cmdScrapper.scrapeSuccessful())
			cmdScrapper.scrape();
		cmdScrapper.getCmdMap().forEach((id, cmd) -> {
			if (cmd.matches(matchingSubstring)) {
				list.add(makeRow(cmd));
			}
		});
		if (list.isEmpty()) {
			list.add(makeRow(noHitsCmd));
		}
		table.getInternalModel().setData(list);
		if (searchField != null)
			searchField.requestFocus();
	}

	private void runCmd(final String command) {
		SwingUtilities.invokeLater(() -> {
			if (CmdScrapper.REBUILD_ID.equals(command)) {
				cmdScrapper.scrape();
				table.clearSelection();
				searchField.setText("");
				searchField.requestFocus();
				frame.setVisible(true);
				return;
			}
			CmdAction cmd;
			if (noHitsCmd != null && command.equals(noHitsCmd.id)) {
				cmd = noHitsCmd;
			} else {
				cmd = cmdScrapper.getCmdMap().get(command);
			}
			if (cmd != null) {
				final boolean hasButton = cmd.button != null;
				if (hasButton && !cmd.button.isEnabled()) {
					textEditor.error("Command is currently disabled. Either execution requirements "
							+ "are unmet or it is not supported by current language.");
					frame.setVisible(true);
					return;
				}
				hideWindow(); // hide before running, in case command opens a dialog
				if (hasButton) {
					cmd.button.doClick();
				} else if (cmd.action != null) {
					cmd.action.actionPerformed(
							new ActionEvent(textEditor.getTextArea(), ActionEvent.ACTION_PERFORMED, cmd.id));
				}
			}
		});
	}

	private void toggleVisibility() {
		if (frame == null) {
			assemblePalette();
		}
		if (frame.isVisible()) {
			hideWindow();
		} else {
			frame.center(textEditor);
			table.clearSelection();
			frame.setVisible(true);
			searchField.requestFocus();
		}
	}

	private static class SearchField extends TextEditor.TextFieldWithPlaceholder {
		private static final long serialVersionUID = 1L;
		private static final int PADDING = 4;
		static final Font REF_FONT = refFont();

		SearchField() {
			setPlaceholder(" Search for commands and actions (e.g., Theme)");
			setMargin(new Insets(PADDING, PADDING, 0, 0));
			setFont(REF_FONT.deriveFont(REF_FONT.getSize() * 1.5f));
		}

		@Override
		Font getPlaceholderFont() {
			return REF_FONT.deriveFont(Font.ITALIC);
		}

		static Font refFont() {
			try {
				return UIManager.getFont("TextField.font");
			} catch (final Exception ignored) {
				return new JTextField().getFont();
			}
		}
	}

	private class PromptDocumentListener implements DocumentListener {
		public void insertUpdate(final DocumentEvent e) {
			populateList(getQueryFromSearchField());
		}

		public void removeUpdate(final DocumentEvent e) {
			populateList(getQueryFromSearchField());
		}

		public void changedUpdate(final DocumentEvent e) {
			populateList(getQueryFromSearchField());
		}

		String getQueryFromSearchField() {
			final String text = searchField.getText();
			if (text == null)
				return "";
			final String query = text.toLowerCase();
			return (IGNORE_WHITESPACE) ? query.replaceAll("\\s+", "") : query;
		}
	}

	private class InternalKeyListener extends KeyAdapter {

		@Override
		public void keyPressed(final KeyEvent ke) {
			final int key = ke.getKeyCode();
			final int flags = ke.getModifiersEx();
			final int items = table.getInternalModel().getRowCount();
			final Object source = ke.getSource();
			final boolean meta = ((flags & KeyEvent.META_DOWN_MASK) != 0) || ((flags & KeyEvent.CTRL_DOWN_MASK) != 0);
			if (key == KeyEvent.VK_ESCAPE || (key == KeyEvent.VK_W && meta) || (key == KeyEvent.VK_P && meta)) {
				hideWindow();
			} else if (source == searchField) {
				/*
				 * If you hit enter in the text field, and there's only one command that
				 * matches, run that:
				 */
				if (key == KeyEvent.VK_ENTER) {
					if (1 == items)
						runCmd(table.getInternalModel().getCommand(0));
				}
				/*
				 * If you hit the up or down arrows in the text field, move the focus to the
				 * table and select the row at the bottom or top.
				 */
				int index = -1;
				if (key == KeyEvent.VK_UP) {
					index = table.getSelectedRow() - 1;
					if (index < 0)
						index = items - 1;
				} else if (key == KeyEvent.VK_DOWN) {
					index = table.getSelectedRow() + 1;
					if (index >= items)
						index = Math.min(items - 1, 0);
				}
				if (index >= 0) {
					table.requestFocus();
					// completions.ensureIndexIsVisible(index);
					table.setRowSelectionInterval(index, index);
				}
			} else if (key == KeyEvent.VK_BACK_SPACE || key == KeyEvent.VK_DELETE) {
				/*
				 * If someone presses backspace or delete they probably want to remove the last
				 * letter from the search string, so switch the focus back to the prompt:
				 */
				searchField.requestFocus();
			} else if (source == table) {
				/* If you hit enter with the focus in the table, run the selected command */
				if (key == KeyEvent.VK_ENTER) {
					ke.consume();
					final int row = table.getSelectedRow();
					if (row >= 0)
						runCmd(table.getInternalModel().getCommand(row));
					/* Loop through the list using the arrow keys */
				} else if (key == KeyEvent.VK_UP) {
					if (table.getSelectedRow() == 0)
						table.setRowSelectionInterval(table.getRowCount() - 1, table.getRowCount() - 1);
				} else if (key == KeyEvent.VK_DOWN) {
					if (table.getSelectedRow() == table.getRowCount() - 1)
						table.setRowSelectionInterval(0, 0);
				}
			}
		}
	}

	private class Palette extends JFrame {
		private static final long serialVersionUID = 1L;

		Palette() {
			super("Command Palette");
			setUndecorated(true);
			setAlwaysOnTop(true);
			setOpacity(OPACITY);
			getRootPane().setWindowDecorationStyle(JRootPane.NONE);
			// it should NOT be possible to minimize this frame, but just to
			// be safe, we'll ensure the frame is never in an awkward state
			addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(final WindowEvent e) {
					hideWindow();
				}

				@Override
				public void windowIconified(final WindowEvent e) {
					hideWindow();
				}

				@Override
				public void windowDeactivated(final WindowEvent e) {
					hideWindow();
				}
			});
		}

		void center(final Container component) {
			final Rectangle bounds = component.getBounds();
			final Dimension w = getSize();
			int x = bounds.x + (bounds.width - w.width) / 2;
			int y = bounds.y + (bounds.height - w.height) / 2;
			if (x < 0)
				x = 0;
			if (y < 0)
				y = 0;
			setLocation(x, y);
		}
	}

	private class CmdTable extends JTable {
		private static final long serialVersionUID = 1L;

		CmdTable() {
			super(new CmdTableModel());
			setAutoCreateRowSorter(false);
			setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			setShowGrid(false);
			setRowSelectionAllowed(true);
			setColumnSelectionAllowed(false);
			setTableHeader(null);
			setAutoResizeMode(AUTO_RESIZE_LAST_COLUMN);
			final CmdTableRenderer renderer = new CmdTableRenderer();
			final int col0Width = renderer.maxWidh(0);
			final int col1Width = renderer.maxWidh(1);
			setDefaultRenderer(Object.class, renderer);
			getColumnModel().getColumn(0).setMaxWidth(col0Width);
			getColumnModel().getColumn(1).setMaxWidth(col1Width);
			setRowHeight(renderer.rowHeight());
			int height = TABLE_ROWS * getRowHeight();
			if (getRowMargin() > 0)
				height *= getRowMargin();
			setPreferredScrollableViewportSize(new Dimension(col0Width + col1Width, height));
			setFillsViewportHeight(true);
		}

		private JScrollPane getScrollPane() {
			final JScrollPane scrollPane = new JScrollPane(this, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
					JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
			scrollPane.setWheelScrollingEnabled(true);
			return scrollPane;
		}

		CmdTableModel getInternalModel() {
			return (CmdTableModel) getModel();
		}

	}

	private class CmdTableRenderer extends DefaultTableCellRenderer {

		private static final long serialVersionUID = 1L;
		final Font col0Font = SearchField.REF_FONT.deriveFont(SearchField.REF_FONT.getSize() * 1.2f);
		final Font col1Font = SearchField.REF_FONT.deriveFont(SearchField.REF_FONT.getSize() * 1.2f);

		public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected,
				final boolean hasFocus, final int row, final int column) {
			final Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			if (column == 1) {
				setHorizontalAlignment(JLabel.RIGHT);
				setEnabled(false);
				setFont(col1Font);
			} else {
				setHorizontalAlignment(JLabel.LEFT);
				setEnabled(true);
				setFont(col0Font);
			}
			return c;
		}

		int rowHeight() {
			return (int) (col0Font.getSize() * 1.75f);
		}

		int maxWidh(final int columnIndex) {
			if (columnIndex == 1)
				return SwingUtilities.computeStringWidth(getFontMetrics(col1Font), "Really+Huge+Key+Combo");
			return SwingUtilities.computeStringWidth(getFontMetrics(col0Font),
					"A large filename from the Recents menu.groovy");
		}

	}

	private class CmdTableModel extends AbstractTableModel {
		private static final long serialVersionUID = 1L;
		private final static int COLUMNS = 2;
		List<String[]> list;

		void setData(final ArrayList<String[]> list) {
			this.list = list;
			fireTableDataChanged();
		}

		String getCommand(final int row) {
			if (list.size() == 1)
				return (String) getValueAt(row, 0);
			else if (row < 0 || row >= list.size())
				return "";
			else
				return (String) getValueAt(row, 0);
		}

		@Override
		public int getColumnCount() {
			return COLUMNS;
		}

		@Override
		public Object getValueAt(final int row, final int column) {
			if (row >= list.size() || column >= COLUMNS)
				return null;
			final String[] strings = (String[]) list.get(row);
			return strings[column];
		}

		@Override
		public int getRowCount() {
			return list.size();
		}

	}

	private class CmdAction {

		final String id;
		String menuLocation;
		String hotkey;
		AbstractButton button;
		Action action;

		CmdAction(final String cmdName) {
			this.id = capitalize(cmdName);
			this.menuLocation = "";
			this.hotkey = "";
		}

		CmdAction(final String cmdName, final AbstractButton button) {
			this(cmdName);
			if (button.getAction() != null && button.getAction() instanceof AbstractAction)
				action = (AbstractAction) button.getAction();
			else
				this.button = button;
		}

		CmdAction(final String cmdName, final Action action) {
			this(cmdName);
			this.action = action;
		}

		boolean recordable() {
			return action != null && action instanceof RecordableTextAction;
		}
	
		String description() {
			final String rec = (recordable()) ?"   \u29BF" :"";
			if (!hotkey.isEmpty())
				return hotkey + rec;
			if (!menuLocation.isEmpty())
				return "|" + menuLocation + "|" + rec;
			return rec;
		}

		boolean matches(final String lowercaseQuery) {
			if (IGNORE_WHITESPACE) {
				return id.toLowerCase().replaceAll("\\s+", "").contains(lowercaseQuery)
						|| menuLocation.toLowerCase().contains(lowercaseQuery);
			}
			return id.toLowerCase().contains(lowercaseQuery) || menuLocation.toLowerCase().contains(lowercaseQuery);
		}

		void setkeyString(final KeyStroke key) {
			if (hotkey.isEmpty()) {
				hotkey = prettifiedKey(key);
			} else {
				final String oldHotkey = hotkey;
				final String newHotKey = prettifiedKey(key);
				if (!oldHotkey.contains(newHotKey)) {
					hotkey = oldHotkey + " or " + newHotKey;
				}
			}
		}

		private String capitalize(final String string) {
			return string.substring(0, 1).toUpperCase() + string.substring(1);
		}

		private String prettifiedKey(final KeyStroke key) {
			if (key == null)
				return "";
			final StringBuilder s = new StringBuilder();
			final int m = key.getModifiers();
			if ((m & InputEvent.CTRL_DOWN_MASK) != 0) {
				s.append((PlatformUtils.isMac()) ? "⌃ " : "Ctrl ");
			}
			if ((m & InputEvent.META_DOWN_MASK) != 0) {
				s.append((PlatformUtils.isMac()) ? "⌘ " : "Ctrl ");
			}
			if ((m & InputEvent.ALT_DOWN_MASK) != 0) {
				s.append((PlatformUtils.isMac()) ? "⎇ " : "Alt ");
			}
			if ((m & InputEvent.SHIFT_DOWN_MASK) != 0) {
				s.append("⇧ ");
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
				s.append(key.getKeyChar() + " ");
				break;
			case KeyEvent.KEY_PRESSED:
			case KeyEvent.KEY_RELEASED:
				s.append(getKeyText(key.getKeyCode()) + " ");
				break;
			default:
				break;
			}
			return s.toString();
		}

		String getKeyText(final int keyCode) {
			if (keyCode >= KeyEvent.VK_0 && keyCode <= KeyEvent.VK_9
					|| keyCode >= KeyEvent.VK_A && keyCode <= KeyEvent.VK_Z) {
				return String.valueOf((char) keyCode);
			}
			switch (keyCode) {
			case KeyEvent.VK_COMMA:
				return ",";
			case KeyEvent.VK_PERIOD:
				return ".";
			case KeyEvent.VK_SLASH:
				return "/";
			case KeyEvent.VK_SEMICOLON:
				return ";";
			case KeyEvent.VK_EQUALS:
				return "=";
			case KeyEvent.VK_OPEN_BRACKET:
				return "[";
			case KeyEvent.VK_BACK_SLASH:
				return "\\";
			case KeyEvent.VK_CLOSE_BRACKET:
				return "]";
			case KeyEvent.VK_ENTER:
				return "↵";
			case KeyEvent.VK_BACK_SPACE:
				return "⌫";
			case KeyEvent.VK_TAB:
				return "↹";
			case KeyEvent.VK_CANCEL:
				return "Cancel";
			case KeyEvent.VK_CLEAR:
				return "Clear";
			case KeyEvent.VK_PAUSE:
				return "Pause";
			case KeyEvent.VK_CAPS_LOCK:
				return "Caps Lock";
			case KeyEvent.VK_ESCAPE:
				return "Esc";
			case KeyEvent.VK_SPACE:
				return "Space";
			case KeyEvent.VK_PAGE_UP:
				return "⇞";
			case KeyEvent.VK_PAGE_DOWN:
				return "⇟";
			case KeyEvent.VK_END:
				return "END";
			case KeyEvent.VK_HOME:
				return "Home"; // "⌂";
			case KeyEvent.VK_LEFT:
				return "←";
			case KeyEvent.VK_UP:
				return "↑";
			case KeyEvent.VK_RIGHT:
				return "→";
			case KeyEvent.VK_DOWN:
				return "↓";
			case KeyEvent.VK_MULTIPLY:
				return "[Num ×]";
			case KeyEvent.VK_ADD:
				return "[Num +]";
			case KeyEvent.VK_SUBTRACT:
				return "[Num −]";
			case KeyEvent.VK_DIVIDE:
				return "[Num /]";
			case KeyEvent.VK_DELETE:
				return "⌦";
			case KeyEvent.VK_INSERT:
				return "Ins";
			case KeyEvent.VK_BACK_QUOTE:
				return "`";
			case KeyEvent.VK_QUOTE:
				return "'";
			case KeyEvent.VK_AMPERSAND:
				return "&";
			case KeyEvent.VK_ASTERISK:
				return "*";
			case KeyEvent.VK_QUOTEDBL:
				return "\"";
			case KeyEvent.VK_LESS:
				return "<";
			case KeyEvent.VK_GREATER:
				return ">";
			case KeyEvent.VK_BRACELEFT:
				return "{";
			case KeyEvent.VK_BRACERIGHT:
				return "}";
			case KeyEvent.VK_COLON:
				return ",";
			case KeyEvent.VK_CIRCUMFLEX:
				return "^";
			case KeyEvent.VK_DEAD_TILDE:
				return "~";
			case KeyEvent.VK_DOLLAR:
				return "$";
			case KeyEvent.VK_EXCLAMATION_MARK:
				return "!";
			case KeyEvent.VK_LEFT_PARENTHESIS:
				return "(";
			case KeyEvent.VK_MINUS:
				return "-";
			case KeyEvent.VK_PLUS:
				return "+";
			case KeyEvent.VK_RIGHT_PARENTHESIS:
				return ")";
			case KeyEvent.VK_UNDERSCORE:
				return "_";
			default:
				return KeyEvent.getKeyText(keyCode);
			}
		}
	}

	private class CmdScrapper {
		final TextEditor textEditor;
		static final String REBUILD_ID = "Rebuild Actions Index";
		private final TreeMap<String, CmdAction> cmdMap;
		private TreeMap<String, CmdAction> otherMap;

		CmdScrapper(final TextEditor textEditor) {
			this.textEditor = textEditor;
			// It seems that the ScriptEditor has duplicated actions(!?) registered
			// in input/action maps. Some Duplicates seem to be defined in lower
			// case, so we'll assemble a case-insensitive map to mitigate this
			cmdMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		}

		TreeMap<String, CmdAction> getCmdMap() {
			if (otherMap != null)
				cmdMap.putAll(otherMap);
			return cmdMap;
		}

		boolean scrapeSuccessful() {
			return !cmdMap.isEmpty();
		}

		void scrape() {
			cmdMap.clear();
			cmdMap.put(REBUILD_ID, new CmdAction(REBUILD_ID));
			parseActionAndInputMaps();
			final JMenuBar menuBar = textEditor.getJMenuBar();
			final int topLevelMenus = menuBar.getMenuCount();
			for (int i = 0; i < topLevelMenus; ++i) {
				final JMenu topLevelMenu = menuBar.getMenu(i);
				if (topLevelMenu != null && topLevelMenu.getText() != null) {
					parseMenu(topLevelMenu.getText(), topLevelMenu);
				}
			}
			final JPopupMenu popup = textEditor.getEditorPane().getPopupMenu();
			if (popup != null) {
				getMenuItems(popup).forEach(mi -> {
					registerMenuItem(mi, "Popup Menu");
				});
			}
		}

		private void parseActionAndInputMaps() {
			final InputMap inputMap = textEditor.getTextArea().getInputMap(JComponent.WHEN_FOCUSED);
			final KeyStroke[] keys = inputMap.allKeys();
			if (keys != null) {
				for (final KeyStroke key : keys) {
					if (key.getModifiers() == 0) {
						// ignore 'typed' keystrokes and related single-key actions
						continue;
					}
					final Object obj = inputMap.get(key);
					CmdAction cmdAction;
					String cmdName;
					if (obj instanceof Action) {
						cmdName = (String) ((Action) obj).getValue(Action.NAME);
						cmdAction = new CmdAction(cleanseActionDescription(cmdName), (AbstractAction) obj);
					} else if (obj instanceof AbstractButton) {
						cmdName = ((AbstractButton) obj).getText();
						cmdAction = new CmdAction(cmdName, (AbstractButton) obj);
					} else {
						continue;
					}
					cmdAction.setkeyString(key);
					cmdMap.put(cmdAction.id, cmdAction);
				}
			}
			final ActionMap actionMap = textEditor.getTextArea().getActionMap();
			for (final Object obj : actionMap.keys()) {
				if (obj instanceof String && cmdMap.get((String) obj) == null) {
					final Action action = actionMap.get((String) obj);
					final CmdAction cmdAction = new CmdAction(cleanseActionDescription((String) obj), action);
					cmdMap.put(cmdAction.id, cmdAction);
				}
			}
		}

		private void parseMenu(final String componentHostingMenu, final JMenu menu) {
			final int n = menu.getItemCount();
			for (int i = 0; i < n; ++i) {
				registerMenuItem(menu.getItem(i), componentHostingMenu);
			}
		}

		private void registerMenuItem(final JMenuItem m, final String hostingComponent) {
			if (m != null) {
				String label = m.getActionCommand();
				if (label == null)
					label = m.getText();
				if (m instanceof JMenu) {
					final JMenu subMenu = (JMenu) m;
					String hostDesc = subMenu.getText();
					if (hostDesc == null)
						hostDesc = hostingComponent;
					parseMenu(hostDesc, subMenu);
				} else {
					registerMain(m, hostingComponent);
				}
			}
		}

		private List<JMenuItem> getMenuItems(final JPopupMenu popupMenu) {
			final List<JMenuItem> list = new ArrayList<>();
			for (final MenuElement me : popupMenu.getSubElements()) {
				if (me == null) {
					continue;
				} else if (me instanceof JMenuItem) {
					list.add((JMenuItem) me);
				} else if (me instanceof JMenu) {
					getMenuItems((JMenu) me, list);
				}
			}
			return list;
		}

		private void getMenuItems(final JMenu menu, final List<JMenuItem> holdingList) {
			for (int j = 0; j < menu.getItemCount(); j++) {
				final JMenuItem jmi = menu.getItem(j);
				if (jmi == null)
					continue;
				if (jmi instanceof JMenu) {
					getMenuItems((JMenu) jmi, holdingList);
				} else {
					holdingList.add(jmi);
				}
			}
		}

		private boolean irrelevantCommand(final String label) {
			// commands that would only add clutter to the palette
			return label == null || label.endsWith(" pt") || label.length() < 2;
		}

		void registerMain(final AbstractButton button, final String description) {
			register(cmdMap, button, description);
		}

		void registerOther(final AbstractButton button, final String description) {
			if (otherMap == null)
				otherMap = new TreeMap<>();
			register(otherMap, button, description);
		}

		private void register(final TreeMap<String, CmdAction> map, final AbstractButton button,
				final String descriptionOfComponentHostingButton) {
			String label = button.getActionCommand();
			if (NAME.equals(label))
				return; // do not register command palette
			if (label == null)
				label = button.getText().trim();
			if (irrelevantCommand(label))
				return;
			if (label.endsWith("..."))
				label = label.substring(0, label.length() - 3);
			// If a command has already been registered, we'll include its accelerator
			final boolean isMenuItem = button instanceof JMenuItem;
			final CmdAction registeredAction = (CmdAction) map.get(label);
			final KeyStroke accelerator = (isMenuItem) ? ((JMenuItem) button).getAccelerator() : null;
			if (registeredAction != null && accelerator != null) {
				registeredAction.setkeyString(accelerator);
			} else {
				final CmdAction ca = new CmdAction(label, button);
				ca.menuLocation = descriptionOfComponentHostingButton;
				if (accelerator != null) ca.setkeyString(accelerator);
				map.put(ca.id, ca);
			}
		}

		private String cleanseActionDescription(String actionId) {
			if (actionId.startsWith("RTA."))
				actionId = actionId.substring(4);
			else if (actionId.startsWith("RSTA."))
				actionId = actionId.substring(5);
			if (actionId.endsWith("Action"))
				actionId = actionId.substring(0, actionId.length() - 6);
			actionId = actionId.replace("-", " ");
			return actionId.replaceAll("([A-Z])", " $1").trim(); // CamelCase to Camel Case
		}

	}

	private class SearchWebCmd extends CmdAction {
		SearchWebCmd() {
			super("Search the Web");
			button = new JMenuItem(new AbstractAction(id) {
				private static final long serialVersionUID = 1L;

				@Override
				public void actionPerformed(final ActionEvent e) {
					TextEditor.GuiUtils.runSearchQueryInBrowser(textEditor, textEditor.getPlatformService(),
							searchField.getText());
				}
			});
		}

		@Override
		String description() {
			return "|Unmatched action|";
		}
	}
}
