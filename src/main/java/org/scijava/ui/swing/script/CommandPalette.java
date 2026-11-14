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

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.Box;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.MenuElement;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import org.fife.ui.rtextarea.RecordableTextAction;
import org.scijava.util.PlatformUtils;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.icons.FlatSearchWithHistoryIcon;
import com.formdev.flatlaf.util.Animator;
import com.formdev.flatlaf.util.CubicBezierEasing;
import com.formdev.flatlaf.util.UIScale;

class CommandPalette {

	private static final String NAME = "Command Palette...";

	/** Settings. Ought to become adjustable some day */
	private static final KeyStroke ACCELERATOR = KeyStroke.getKeyStroke(KeyEvent.VK_P,
			Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | KeyEvent.SHIFT_DOWN_MASK);
	private static final int TABLE_ROWS = 6; // no. of commands to be displayed
	private static final int MAX_PATH = 2; // no. of menu levels in the path column
	private static final Pattern QUERY_TOKEN_SPLIT = Pattern.compile("\\s+");
	private static final Pattern ID_TOKEN_SPLIT = Pattern.compile("\\W+");
	private static final Font REF_FONT = refFont();
	private static final Color WARNING_COLOR = new Color(254, 210, 132);

	private final TextEditor textEditor;
	private final CmdAction noHitsCmd;
	private final CmdScrapper cmdScrapper;

	private Palette frame;
	private SearchField searchField;
	private CmdTable table;
	private boolean alwaysOnTop; // Always on top of other windows?
	private boolean autoHide = true; // Hide after command is executed?
	private boolean caseSensitive; // Case-sensitive matching?
	private boolean ignoreWhiteSpace = true; // Ignore white spaces while matching?
	private Point lastLocation; // last on-screen position, restored on re-open
	// Bumped whenever caseSensitive or ignoreWhiteSpace changes: CmdAction caches
	// pre-normalized haystacks keyed to this generation
	private int normGeneration;
	private Timer msgTimer;
	// Guards setText() calls in displayTempMsg() so that they do not trigger a search
	private boolean suppressDocListener;

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

	/** Menu bar button that toggles the palette, for the right end of the menu bar */
	JButton getMenuButton() {
		final JButton button = new JButton(new AbstractAction(null, new MagnifierIcon()) {

			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(final ActionEvent e) {
				toggleVisibility();
			}

		});
		button.setToolTipText("Command Palette (" + getAcceleratorString() + ")");
		button.setFocusable(false);
		button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
		return button;
	}

	String getAcceleratorString() {
		return noHitsCmd.prettifiedKey(ACCELERATOR).trim();
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
		cmdScrapper.registerOther(button, List.of(description));
	}

	void dispose() {
		if (msgTimer != null)
			msgTimer.stop();
		if (frame != null)
			frame.dispose();
		frame = null;
	}

	private void hideWindow() {
		if (frame != null) {
			if (frame.isVisible())
				lastLocation = frame.getLocation(); // remember for next open
			frame.setVisible(false);
		}
	}

	private void autoHide() {
		if (autoHide)
			hideWindow();
	}

	private void assemblePalette() {
		frame = new Palette();
		initSearchField();
		final JToolBar titleBar = new TitleBar().getToolBar();
		frame.add(titleBar, BorderLayout.NORTH);
		frame.add(searchField, BorderLayout.CENTER);
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
		frame.add(table.getScrollPane(), BorderLayout.SOUTH);
		frame.pack();
		enableDrag(frame.getRootPane());
		enableDrag(titleBar);
	}

	private static void enableDrag(final JComponent component) {
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

	private void initSearchField() {
		searchField = new SearchField(" Search for commands and actions (e.g., Theme)");
		final JMenuItem rebuildIndex = new JMenuItem("Rebuild Index...");
		rebuildIndex.addActionListener(e -> rebuildIndex());
		searchField.optionsMenu.add(rebuildIndex);
		searchField.caseButton.setSelected(caseSensitive);
		searchField.caseButton.addItemListener(e -> {
			caseSensitive = searchField.caseButton.isSelected();
			normGeneration++; // invalidate cached haystacks
			populateList(currentQuery()); // avoid setText(): it fires the document listener twice
		});
		searchField.whitespaceButton.setSelected(!ignoreWhiteSpace);
		searchField.whitespaceButton.addItemListener(e -> {
			ignoreWhiteSpace = !searchField.whitespaceButton.isSelected();
			normGeneration++;
			populateList(currentQuery());
		});
	}

	// Case-adjusted but space-preserved: matches() does its own normalization so
	// that fuzzyMatches() always sees the original tokens
	private String currentQuery() {
		final String text = searchField.getText();
		if (text == null)
			return "";
		return (caseSensitive) ? text : text.toLowerCase();
	}

	private void rebuildIndex() {
		cmdScrapper.scrape();
		populateList(currentQuery());
		displayTempMsg(String.format("Index rebuilt: %d commands/actions available...",
				cmdScrapper.getCmdMap().size()), false);
	}

	private void displayTempMsg(final String msg, final boolean warning) {
		if (msgTimer != null)
			msgTimer.stop(); // restore whatever a still-pending message left behind, first
		final String existingPlaceholder = searchField.getPlaceholder();
		final String existingText = searchField.getText();
		searchField.setPlaceholder(msg);
		suppressDocListener = true;
		searchField.setText(null);
		suppressDocListener = false;
		if (warning)
			setWarningModeEnabled(true);
		msgTimer = new Timer((warning) ? 3000 : 6000, null) {
			private static final long serialVersionUID = 1L;

			@Override
			public void stop() {
				super.stop();
				searchField.setPlaceholder(existingPlaceholder);
				suppressDocListener = true;
				searchField.setText(existingText);
				suppressDocListener = false;
				if (warning)
					setWarningModeEnabled(false);
			}
		};
		msgTimer.addActionListener(e -> msgTimer.stop()); // same restore path on timeout
		SwingUtilities.invokeLater(() -> {
			if (frame != null)
				frame.setVisible(true); // dispose() may have cleared it in the meantime
			msgTimer.start();
		});
	}

	private void populateList(final String query) {
		if (!cmdScrapper.scrapeSuccessful())
			cmdScrapper.scrape();
		final List<CmdAction> list = getSortedListByQuery(query);
		final boolean noHits = list.isEmpty();
		if (noHits)
			list.add(noHitsCmd);
		table.getInternalModel().setData(list);
		if (searchField != null) {
			setWarningModeEnabled(noHits);
			searchField.requestFocus();
		}
	}

	/** Pairing of a hit with its rank tier, computed once per hit */
	private record Scored(CmdAction cmd, int score) {
	}

	private List<CmdAction> getSortedListByQuery(final String query) {
		// Query normalization and tokenizing depend only on the settings and query,
		// so they are computed once per keystroke rather than once per command
		final String normalizedQuery = normalize(query);
		final String[] queryTokens = (query.trim().length() < 2) ? new String[0]
				: QUERY_TOKEN_SPLIT.split(query.trim().toLowerCase());
		final List<Scored> hits = new ArrayList<>();
		for (final CmdAction cmd : cmdScrapper.getCmdMap().values()) {
			if (cmd.matches(normalizedQuery, queryTokens))
				hits.add(new Scored(cmd, getMatchScore(cmd, normalizedQuery)));
		}
		hits.sort((a, b) -> {
			if (a.score() != b.score())
				return Integer.compare(a.score(), b.score());
			final int alphaCompare = String.CASE_INSENSITIVE_ORDER.compare(a.cmd().id, b.cmd().id);
			return (alphaCompare != 0) ? alphaCompare : a.cmd().id.compareTo(b.cmd().id);
		});
		final List<CmdAction> sorted = new ArrayList<>(hits.size());
		for (final Scored s : hits)
			sorted.add(s.cmd());
		return sorted;
	}

	private int getMatchScore(final CmdAction cmd, final String normalizedQuery) {
		// cmd.matches() has already triggered ensureNormalized()
		if (normalizedQuery.isEmpty())
			return 1; // no query: alphabetical order takes over
		if (cmd.normId.startsWith(normalizedQuery))
			return 1; // id starts with query
		if (cmd.normId.contains(normalizedQuery))
			return 2; // id contains query
		if (cmd.normTooltip.contains(normalizedQuery) || cmd.normPath.contains(normalizedQuery))
			return 3; // matched through metadata only
		return 4; // fuzzy-only hit
	}

	// Normalizes a haystack/query string per the current case/whitespace settings
	private String normalize(final String s) {
		if (s == null || s.isEmpty())
			return "";
		final String r = caseSensitive ? s : s.toLowerCase();
		return ignoreWhiteSpace ? r.replace(" ", "") : r;
	}

	private void setWarningModeEnabled(final boolean enable) {
		searchField.setBackground((enable) ? WARNING_COLOR : defaultFieldBackground());
		searchField.setForeground((enable) ? Color.BLACK : defaultFieldForeground());
	}

	private static Color defaultFieldBackground() {
		final Color c = UIManager.getColor("TextField.background");
		return (c != null) ? c : Color.WHITE;
	}

	private static Color defaultFieldForeground() {
		final Color c = UIManager.getColor("TextField.foreground");
		return (c != null) ? c : Color.BLACK;
	}

	private void runCmd(final CmdAction cmd) {
		if (cmd == null)
			return;
		SwingUtilities.invokeLater(() -> {
			if (cmd.isDisabled()) {
				displayTempMsg("Command is currently disabled: unmet requirements or unsupported by language", true);
				return;
			}
			autoHide(); // hide before running, in case command opens a dialog
			if (cmd.button != null) {
				cmd.button.doClick();
			} else if (cmd.action != null) {
				cmd.action.actionPerformed(
						new ActionEvent(textEditor.getTextArea(), ActionEvent.ACTION_PERFORMED, cmd.id));
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
			if (searchField.getText().isBlank())
				table.clearSelection();
			else
				searchField.selectAll();
			restoreOrCenterLocation();
			frame.setVisible(true);
			searchField.requestFocus();
		}
	}

	private void restoreOrCenterLocation() {
		// Restore the last user position, if the screen is still reachable
		if (lastLocation != null && isOnScreen(lastLocation)) {
			frame.setLocation(lastLocation);
			return;
		}
		frame.setLocationRelativeTo(textEditor);
		// Keep the palette clear of docks and taskbars
		final GraphicsConfiguration gc = textEditor.getGraphicsConfiguration();
		if (gc == null)
			return;
		final Rectangle b = gc.getBounds();
		final Insets in = Toolkit.getDefaultToolkit().getScreenInsets(gc);
		final int minX = b.x + in.left;
		final int minY = b.y + in.top;
		final int maxX = Math.max(minX, b.x + b.width - in.right - frame.getWidth());
		final int maxY = Math.max(minY, b.y + b.height - in.bottom - frame.getHeight());
		frame.setLocation(Math.clamp(frame.getX(), minX, maxX), Math.clamp(frame.getY(), minY, maxY));
	}

	private static boolean isOnScreen(final Point p) {
		for (final GraphicsDevice gd : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
			if (gd.getDefaultConfiguration().getBounds().contains(p))
				return true;
		}
		return false;
	}

	/* Theme helpers. Computed on demand so that they follow the Look and Feel */

	private static Font refFont() {
		try {
			return UIManager.getFont("TextField.font");
		} catch (final Exception ignored) {
			return new JTextField().getFont();
		}
	}

	private static Color background() {
		Color c = UIManager.getColor("Popup.background");
		if (c == null)
			c = UIManager.getColor("Panel.background");
		return (c != null) ? c : Color.LIGHT_GRAY;
	}

	private static Color iconColor() {
		final Color c = TextEditor.GuiUtils.secondaryColor();
		return (c != null) ? c : Color.GRAY;
	}

	/**
	 * Toggle painted with the LAF's secondary color. The color is returned as a plain
	 * Color because FlatLaf ignores foregrounds that are UIResources, and resolved on
	 * every paint so that it follows Look and Feel changes
	 */
	private static JToggleButton secondaryToggle(final String text, final String tooltip) {
		final JToggleButton b = new JToggleButton(text) {
			private static final long serialVersionUID = 1L;

			@Override
			public Color getForeground() {
				final Color c = TextEditor.GuiUtils.secondaryColor();
				return (c != null) ? new Color(c.getRGB()) : super.getForeground();
			}
		};
		b.setToolTipText(tooltip);
		b.setFocusable(false);
		return b;
	}

	private static Color selectionColor() {
		Color c = UIManager.getColor("Table.selectionBackground");
		if (c == null)
			c = UIManager.getColor("List.selectionBackground");
		return (c != null) ? c : new Color(75, 110, 175);
	}

	private static Color alpha(final Color c, final int percent) {
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(percent / 100f * 255));
	}

	/** Returns a color that is weight of a plus (1 - weight) of b */
	private static Color mix(final Color a, final Color b, final double weight) {
		final double w = Math.clamp(weight, 0d, 1d);
		return new Color((int) Math.round(a.getRed() * w + b.getRed() * (1 - w)),
				(int) Math.round(a.getGreen() * w + b.getGreen() * (1 - w)),
				(int) Math.round(a.getBlue() * w + b.getBlue() * (1 - w)));
	}

	private static void recolorTracks(final JScrollPane scrollPane, final Color color) {
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

	/** The search field, with options menu, and case and whitespace toggles */
	private static class SearchField extends TextEditor.TextFieldWithPlaceholder {
		private static final long serialVersionUID = 1L;
		private static final float ENLARGE_FACTOR = 1.1f;
		final JPopupMenu optionsMenu = new JPopupMenu();
		final JToggleButton caseButton;
		final JToggleButton whitespaceButton;

		SearchField(final String placeholder) {
			setPlaceholder(placeholder);
			final JButton options = new JButton(new FlatSearchWithHistoryIcon(true));
			options.setToolTipText("Options");
			options.setFocusable(false);
			options.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
			options.addActionListener(e -> optionsMenu.show(options, 0, options.getHeight()));
			putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_COMPONENT, options);
			caseButton = secondaryToggle("Cc", "Match case");
			whitespaceButton = secondaryToggle("\u2423", "Match whitespace");
			final JToolBar tb = new JToolBar();
			tb.setFloatable(false);
			tb.setMargin(new Insets(0, 0, 0, 2));
			tb.add(caseButton);
			tb.add(whitespaceButton);
			putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, tb);
			enlarge(options);
			styleBorder();
		}

		private void enlarge(final JButton options) {
			// Normalize by the OS scale so that the enlargement is consistent across platforms
			final float factor = 1f + (ENLARGE_FACTOR - 1f) / osScale();
			setFont(getFont().deriveFont(getFont().getSize2D() * factor));
			options.setFont(options.getFont().deriveFont(options.getFont().getSize2D() * factor));
			int w = 0;
			int h = 0;
			for (final JToggleButton b : List.of(caseButton, whitespaceButton)) {
				b.setFont(b.getFont().deriveFont(b.getFont().getSize2D() * factor * .85f));
				b.setPreferredSize(null);
				final Dimension d = b.getPreferredSize();
				w = Math.max(w, d.width);
				h = Math.max(h, d.height);
			}
			final Dimension size = new Dimension(w, h);
			for (final JToggleButton b : List.of(caseButton, whitespaceButton)) {
				b.setPreferredSize(size);
				b.setMinimumSize(size);
				b.setMaximumSize(size);
			}
		}

		private void styleBorder() {
			final int padding = (int) (getFont().getSize2D() * .8);
			setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createMatteBorder(1, 0, 1, 0, alpha(iconColor(), 40)),
					BorderFactory.createEmptyBorder(padding, padding, padding, padding)));
		}

		@Override
		public void updateUI() {
			super.updateUI(); // resets the border to the Look and Feel default
			if (caseButton != null)
				styleBorder();
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
	}

	/** Magnifier icon, painted with the foreground of its component */
	private static final class MagnifierIcon implements Icon {

		@Override
		public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
			final Graphics2D g2 = (Graphics2D) g.create();
			try {
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
				g2.setColor(c.getForeground());
				g2.setStroke(new BasicStroke(UIScale.scale(1.6f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				final float s = getIconWidth();
				g2.draw(new Ellipse2D.Float(x + .1f * s, y + .1f * s, .6f * s, .6f * s));
				g2.draw(new Line2D.Float(x + .62f * s, y + .62f * s, x + .88f * s, y + .88f * s));
			} finally {
				g2.dispose();
			}
		}

		@Override
		public int getIconWidth() {
			return UIScale.scale(16);
		}

		@Override
		public int getIconHeight() {
			return UIScale.scale(16);
		}
	}

	/** Light bulb icon (Font Awesome Free 7.3.1). Painted with the foreground of its component */
	private static final class BulbIcon implements Icon {
		private static final String PATH = "M424.5 355.1C449 329.2 464 294.4 464 256C464 176.5 399.5 112 320 112"
				+ "C240.5 112 176 176.5 176 256C176 294.4 191 329.2 215.5 355.1C236.8 377.5 260.4 409.1 268.8 448"
				+ "L371.2 448C379.6 409 403.2 377.5 424.5 355.1z"
				+ "M459.3 388.1C435.7 413 416 443.4 416 477.7L416 496C416 540.2 380.2 576 336 576L304 576"
				+ "C259.8 576 224 540.2 224 496L224 477.7C224 443.4 204.3 413 180.7 388.1C148 353.7 128 307.2 128 256"
				+ "C128 150 214 64 320 64C426 64 512 150 512 256C512 307.2 492 353.7 459.3 388.1z"
				+ "M272 248C272 261.3 261.3 272 248 272C234.7 272 224 261.3 224 248C224 199.4 263.4 160 312 160"
				+ "C325.3 160 336 170.7 336 184C336 197.3 325.3 208 312 208C289.9 208 272 225.9 272 248z";
		// Glyph bounds within the 640x640 view box
		private static final double X0 = 128;
		private static final double Y0 = 64;
		private static final double GLYPH_W = 384;
		private static final double GLYPH_H = 512;
		private static final Path2D SHAPE = parse(PATH);
		private final int height;

		BulbIcon(final int height) {
			this.height = height;
		}

		/** Parses absolute M, L, C and z commands, the only ones in PATH */
		private static Path2D parse(final String d) {
			final Path2D.Double p = new Path2D.Double(Path2D.WIND_NON_ZERO);
			final Matcher m = Pattern.compile("([MLCz])([^MLCz]*)").matcher(d);
			while (m.find()) {
				final String args = m.group(2).trim();
				final String[] n = args.isEmpty() ? new String[0] : args.split("[ ,]+");
				switch (m.group(1).charAt(0)) {
				case 'M' -> p.moveTo(Double.parseDouble(n[0]), Double.parseDouble(n[1]));
				case 'L' -> p.lineTo(Double.parseDouble(n[0]), Double.parseDouble(n[1]));
				case 'C' -> p.curveTo(Double.parseDouble(n[0]), Double.parseDouble(n[1]), Double.parseDouble(n[2]),
						Double.parseDouble(n[3]), Double.parseDouble(n[4]), Double.parseDouble(n[5]));
				default -> p.closePath();
				}
			}
			return p;
		}

		@Override
		public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
			final Graphics2D g2 = (Graphics2D) g.create();
			try {
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
				g2.setColor(c.getForeground());
				final double s = height / GLYPH_H;
				g2.translate(x, y);
				g2.scale(s, s);
				g2.translate(-X0, -Y0);
				g2.fill(SHAPE);
			} finally {
				g2.dispose();
			}
		}

		@Override
		public int getIconWidth() {
			return (int) Math.round(height * GLYPH_W / GLYPH_H);
		}

		@Override
		public int getIconHeight() {
			return height;
		}
	}

	/** A {@link JLabel} that fades and slides in/out through a FlatLaf {@link Animator} */
	private static class FadableHintLabel extends JLabel {
		private static final long serialVersionUID = 1L;
		private static final int DURATION_MS = 300;
		private float alpha = 1f; // 0f (fully hidden) to 1f (fully shown)
		private float offset; // horizontal paint offset in px: 0f is the resting position
		private Animator animator;

		FadableHintLabel(final String text) {
			super(text);
		}

		void animateVisibility(final boolean show) {
			if (animator != null && animator.isRunning())
				animator.stop();
			final float startAlpha = alpha;
			final float endAlpha = show ? 1f : 0f;
			// Reveals move left-to-right, hides exit past the right edge
			final float travel = Math.max(getWidth(), getPreferredSize().width);
			final float startOffset = offset;
			final float endOffset = show ? 0f : travel;
			if (show)
				setVisible(true); // must be visible for the transition to be seen
			if (startAlpha == endAlpha && startOffset == endOffset) {
				if (!show)
					setVisible(false);
				return;
			}
			animator = new Animator(DURATION_MS, new Animator.TimingTarget() {
				@Override
				public void timingEvent(final float fraction) {
					alpha = startAlpha + (endAlpha - startAlpha) * fraction;
					offset = startOffset + (endOffset - startOffset) * fraction;
					repaint();
				}

				@Override
				public void end() {
					alpha = endAlpha;
					offset = endOffset;
					if (!show)
						setVisible(false); // hide only once fully faded out
					repaint();
				}
			});
			animator.setInterpolator(CubicBezierEasing.EASE_IN_OUT);
			animator.start();
		}

		@Override
		protected void paintComponent(final Graphics g) {
			if (alpha >= 1f && offset == 0f) {
				super.paintComponent(g);
				return;
			}
			final Graphics2D g2d = (Graphics2D) g.create();
			// Clip to own bounds so that the slide never paints over neighboring buttons
			g2d.clipRect(0, 0, getWidth(), getHeight());
			g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.clamp(alpha, 0f, 1f)));
			g2d.translate(offset, 0);
			super.paintComponent(g2d);
			g2d.dispose();
		}
	}

	private class TitleBar {

		private static final float FONT_SCALE = .9f;

		JToolBar getToolBar() {
			final JToolBar toolbar = new JToolBar();
			toolbar.setOpaque(false);
			toolbar.setFocusable(false);
			toolbar.setFloatable(false);
			toolbar.add(toggle("Auto-Hide", "Dismiss palette after running a command", autoHide,
					selected -> autoHide = selected));
			toolbar.add(toggle("Pin", "Display palette above all other windows", alwaysOnTop,
					selected -> frame.setAlwaysOnTop(alwaysOnTop = selected)));
			toolbar.add(Box.createHorizontalGlue());
			addHint(toolbar);
			return toolbar;
		}

		JToggleButton toggle(final String text, final String tooltip, final boolean initialState,
				final Consumer<Boolean> setter) {
			final JToggleButton button = secondaryToggle(text, tooltip);
			button.setSelected(initialState);
			button.setFont(button.getFont().deriveFont(button.getFont().getSize2D() * FONT_SCALE));
			button.addItemListener(e -> setter.accept(button.isSelected()));
			return button;
		}

		void addHint(final JToolBar parent) {
			final FadableHintLabel label = new FadableHintLabel(
					"HINT: Up/Down to navigate, Enter to run, Esc to close ");
			label.setFont(label.getFont().deriveFont(label.getFont().getSize2D() * FONT_SCALE));
			label.setForeground(iconColor());
			label.setFocusable(false);
			enableDrag(label);
			parent.add(label);
			final JButton button = new JButton(new BulbIcon((int) Math.round(label.getFont().getSize2D() * 1.2)));
			button.setToolTipText("Search hints");
			button.setForeground(iconColor());
			button.setFocusable(false);
			parent.add(button);
			// Tracked separately from label.isVisible(), which lags behind while fading
			final boolean[] shown = { true };
			button.addActionListener(e -> {
				shown[0] = !shown[0];
				label.animateVisibility(shown[0]);
			});
			final Timer timer = new Timer(5000, e -> {
				shown[0] = false;
				label.animateVisibility(false);
				((Timer) e.getSource()).stop();
			});
			timer.setRepeats(false);
			timer.start();
		}
	}

	private class PromptDocumentListener implements DocumentListener {
		public void insertUpdate(final DocumentEvent e) {
			if (!suppressDocListener)
				populateList(currentQuery());
		}

		public void removeUpdate(final DocumentEvent e) {
			if (!suppressDocListener)
				populateList(currentQuery());
		}

		public void changedUpdate(final DocumentEvent e) {
			if (!suppressDocListener)
				populateList(currentQuery());
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
				/* Enter in the text field runs the selected row, or the first one */
				if (key == KeyEvent.VK_ENTER) {
					runCmd(table.getInternalModel().getCommand(Math.max(table.getSelectedRow(), 0)));
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

	/** Content pane that follows the Look and Feel */
	private static class PalettePanel extends JPanel {
		private static final long serialVersionUID = 1L;

		PalettePanel() {
			super(new BorderLayout());
		}

		@Override
		public void updateUI() {
			super.updateUI();
			setBackground(background());
		}
	}

	private class Palette extends JDialog {
		private static final long serialVersionUID = 1L;

		Palette() {
			super(textEditor, "Command Palette", false);
			setResizable(false);
			setAlwaysOnTop(alwaysOnTop);
			setUndecorated(true);
			setContentPane(new PalettePanel());
			addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(final WindowEvent e) {
					hideWindow();
				}

				@Override
				public void windowIconified(final WindowEvent e) {
					autoHide();
				}

				@Override
				public void windowDeactivated(final WindowEvent e) {
					autoHide();
				}
			});
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
			final int col0Width = renderer.maxWidth(0);
			final int col1Width = renderer.maxWidth(1);
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

		@Override
		public void updateUI() {
			super.updateUI();
			setBackground(background());
			if (getParent() instanceof JViewport viewport)
				viewport.setBackground(background());
		}

		private JScrollPane getScrollPane() {
			final JScrollPane scrollPane = new JScrollPane(this, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
					JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED) {
				private static final long serialVersionUID = 1L;

				@Override
				public void updateUI() {
					recolorTracks(this, background());
					super.updateUI();
				}
			};
			scrollPane.setWheelScrollingEnabled(true);
			scrollPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 0, 0));
			return scrollPane;
		}

		CmdTableModel getInternalModel() {
			return (CmdTableModel) getModel();
		}

	}

	private class CmdTableRenderer extends DefaultTableCellRenderer {

		private static final long serialVersionUID = 1L;
		final Font col0Font = REF_FONT.deriveFont(REF_FONT.getSize2D() * 1.1f);
		final Font col1Font = REF_FONT;

		@Override
		public Component getTableCellRendererComponent(final JTable table, final Object value,
				final boolean isSelected, final boolean hasFocus, final int row, final int column) {
			final CmdAction cmd = (CmdAction) value; // see CmdTableModel#getValueAt
			final String text = (cmd == null) ? "" : (column == 0) ? cmd.id : cmd.description();
			super.getTableCellRendererComponent(table, text, false, false, row, column);
			final Color bg = table.getBackground();
			final Color fg = table.getForeground();
			setBackground((isSelected) ? mix(selectionColor(), bg, .35) : bg);
			if (cmd != null && cmd.isDisabled())
				setForeground(mix(fg, bg, .45));
			else
				setForeground((column == 0) ? fg : mix(fg, bg, .6));
			setHorizontalAlignment((column == 0) ? JLabel.LEFT : JLabel.RIGHT);
			setFont((column == 0) ? col0Font : col1Font);
			setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
			return this;
		}

		int rowHeight() {
			return (int) (col0Font.getSize2D() * 1.85f);
		}

		int maxWidth(final int columnIndex) {
			if (columnIndex == 1)
				return SwingUtilities.computeStringWidth(getFontMetrics(col1Font),
						"A really long menu › With long submenus   ");
			return SwingUtilities.computeStringWidth(getFontMetrics(col0Font),
					"A large filename from the Recents menu.groovy");
		}

	}

	private class CmdTableModel extends AbstractTableModel {
		private static final long serialVersionUID = 1L;
		private static final int COLUMNS = 2;
		private List<CmdAction> list = new ArrayList<>();

		void setData(final List<CmdAction> list) {
			this.list = list;
			fireTableDataChanged();
		}

		CmdAction getCommand(final int row) {
			return (row < 0 || row >= list.size()) ? null : list.get(row);
		}

		@Override
		public int getColumnCount() {
			return COLUMNS;
		}

		@Override
		public Object getValueAt(final int row, final int column) {
			// Both columns are rendered from the command itself
			return getCommand(row);
		}

		@Override
		public int getRowCount() {
			return list.size();
		}

	}

	private class CmdAction {

		final String id;
		List<String> path;
		String hotkey;
		AbstractButton button;
		Action action;

		private String pathDescription;
		private String tooltipCache;
		private String[] idTokensCache;
		// Pre-normalized haystacks, valid for normGen == normGeneration
		private String normId, normPath, normTooltip;
		private int normGen = -1;

		CmdAction(final String cmdName) {
			this.id = capitalize(cmdName);
			this.path = new ArrayList<>();
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

		/** True if backed by a button, menu item or action that is currently disabled */
		boolean isDisabled() {
			if (button != null) {
				return !button.isEnabled() || (button instanceof JMenuItem && button.getParent() != null
						&& !button.getParent().isEnabled());
			}
			return action != null && !action.isEnabled();
		}

		String pathDescription() {
			if (pathDescription == null) {
				final List<String> tail = path.subList(Math.max(path.size() - MAX_PATH, 0), path.size());
				pathDescription = String.join(" \u203A ", tail);
			}
			return pathDescription;
		}

		String description() {
			final String rec = (recordable()) ? "   \u29BF" : "";
			if (!hotkey.isEmpty())
				return hotkey + rec;
			if (!path.isEmpty())
				return pathDescription() + rec;
			return rec;
		}

		String tooltip() {
			if (tooltipCache == null) {
				String t = null;
				if (button != null)
					t = button.getToolTipText();
				else if (action != null && action.getValue(Action.SHORT_DESCRIPTION) instanceof String s)
					t = s;
				tooltipCache = (t == null) ? "" : t.replaceAll("<[^>]*>", ""); // strip HTML once
			}
			return tooltipCache;
		}

		// normalizedQuery and queryTokens are computed once per keystroke by the
		// caller (getSortedListByQuery), not per command
		boolean matches(final String normalizedQuery, final String[] queryTokens) {
			ensureNormalized();
			return normId.contains(normalizedQuery) || normTooltip.contains(normalizedQuery)
					|| normPath.contains(normalizedQuery) || fuzzyMatches(queryTokens);
		}

		// Fuzzy fallback: each query token must fuzzy-match at least one id token.
		// The threshold is computed per word (1 error per 3 chars), so that a typo
		// in a single word of a multi-word query is tolerated. Matching is always
		// case-insensitive and tokens shorter than 2 chars are skipped
		private boolean fuzzyMatches(final String[] queryTokens) {
			if (queryTokens.length == 0)
				return false;
			final String[] idTokens = idTokens();
			for (final String qt : queryTokens) {
				if (qt.length() < 2)
					continue;
				final int threshold = Math.max(1, qt.length() / 3);
				boolean matched = false;
				for (final String it : idTokens) {
					if (it.length() < qt.length() - threshold)
						continue; // too short to match
					if (it.contains(qt) || withinEditDistance(qt, it, threshold)) {
						matched = true;
						break;
					}
				}
				if (!matched)
					return false; // all query tokens must match
			}
			return true;
		}

		private String[] idTokens() {
			if (idTokensCache == null)
				idTokensCache = ID_TOKEN_SPLIT.split(id.toLowerCase());
			return idTokensCache;
		}

		private void ensureNormalized() {
			if (normGen == normGeneration)
				return;
			normId = normalize(id);
			normPath = normalize(pathDescription());
			normTooltip = normalize(tooltip());
			normGen = normGeneration;
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
            return switch (keyCode) {
                case KeyEvent.VK_COMMA -> ",";
                case KeyEvent.VK_PERIOD -> ".";
                case KeyEvent.VK_SLASH -> "/";
                case KeyEvent.VK_SEMICOLON -> ";";
                case KeyEvent.VK_EQUALS -> "=";
                case KeyEvent.VK_OPEN_BRACKET -> "[";
                case KeyEvent.VK_BACK_SLASH -> "\\";
                case KeyEvent.VK_CLOSE_BRACKET -> "]";
                case KeyEvent.VK_ENTER -> "↵";
                case KeyEvent.VK_BACK_SPACE -> "⌫";
                case KeyEvent.VK_TAB -> "↹";
                case KeyEvent.VK_CANCEL -> "Cancel";
                case KeyEvent.VK_CLEAR -> "Clear";
                case KeyEvent.VK_PAUSE -> "Pause";
                case KeyEvent.VK_CAPS_LOCK -> "Caps Lock";
                case KeyEvent.VK_ESCAPE -> "Esc";
                case KeyEvent.VK_SPACE -> "Space";
                case KeyEvent.VK_PAGE_UP -> "⇞";
                case KeyEvent.VK_PAGE_DOWN -> "⇟";
                case KeyEvent.VK_END -> "END";
                case KeyEvent.VK_HOME -> "Home"; // "⌂";
                case KeyEvent.VK_LEFT -> "←";
                case KeyEvent.VK_UP -> "↑";
                case KeyEvent.VK_RIGHT -> "→";
                case KeyEvent.VK_DOWN -> "↓";
                case KeyEvent.VK_MULTIPLY -> "[Num ×]";
                case KeyEvent.VK_ADD -> "[Num +]";
                case KeyEvent.VK_SUBTRACT -> "[Num −]";
                case KeyEvent.VK_DIVIDE -> "[Num /]";
                case KeyEvent.VK_DELETE -> "⌦";
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
	}

	/** Levenshtein test that bails out once the distance is known to exceed max */
	private static boolean withinEditDistance(final String a, final String b, final int max) {
		final int n = a.length();
		final int m = b.length();
		if (Math.abs(n - m) > max)
			return false;
		int[] prev = new int[m + 1];
		int[] curr = new int[m + 1];
		for (int j = 0; j <= m; j++)
			prev[j] = j;
		for (int i = 1; i <= n; i++) {
			curr[0] = i;
			int rowMin = curr[0];
			for (int j = 1; j <= m; j++) {
				final int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
				curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
				rowMin = Math.min(rowMin, curr[j]);
			}
			if (rowMin > max)
				return false;
			final int[] tmp = prev;
			prev = curr;
			curr = tmp;
		}
		return prev[m] <= max;
	}

	private class CmdScrapper {
		final TextEditor textEditor;
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
			parseActionAndInputMaps();
			final JMenuBar menuBar = textEditor.getJMenuBar();
			final int topLevelMenus = menuBar.getMenuCount();
			for (int i = 0; i < topLevelMenus; ++i) {
				final JMenu topLevelMenu = menuBar.getMenu(i);
				if (topLevelMenu != null && topLevelMenu.getText() != null) {
					parseMenu(List.of(topLevelMenu.getText()), topLevelMenu);
				}
			}
			final JPopupMenu popup = textEditor.getEditorPane().getPopupMenu();
			if (popup != null) {
				getMenuItems(popup).forEach(mi -> registerMenuItem(mi, List.of("Popup Menu")));
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

		private void parseMenu(final List<String> path, final JMenu menu) {
			final int n = menu.getItemCount();
			for (int i = 0; i < n; ++i) {
				registerMenuItem(menu.getItem(i), path);
			}
		}

		private void registerMenuItem(final JMenuItem m, final List<String> path) {
			if (m == null)
				return;
			if (m instanceof JMenu subMenu) {
				final List<String> subPath = new ArrayList<>(path);
				if (subMenu.getText() != null)
					subPath.add(subMenu.getText());
				parseMenu(subPath, subMenu);
			} else {
				registerMain(m, path);
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

		void registerMain(final AbstractButton button, final List<String> path) {
			register(cmdMap, button, path);
		}

		void registerOther(final AbstractButton button, final List<String> path) {
			if (otherMap == null)
				otherMap = new TreeMap<>();
			register(otherMap, button, path);
		}

		private void register(final TreeMap<String, CmdAction> map, final AbstractButton button,
				final List<String> path) {
			String label = button.getActionCommand();
			if (NAME.equals(label))
				return; // do not register command palette
			if (label == null && button.getText() != null)
				label = button.getText().trim();
			if (irrelevantCommand(label))
				return;
			if (label.endsWith("..."))
				label = label.substring(0, label.length() - 3);
			// If a command has already been registered, we'll include its accelerator
			final boolean isMenuItem = button instanceof JMenuItem;
			final CmdAction registeredAction = map.get(label);
			final KeyStroke accelerator = (isMenuItem) ? ((JMenuItem) button).getAccelerator() : null;
			if (registeredAction != null && accelerator != null) {
				registeredAction.setkeyString(accelerator);
			} else {
				final CmdAction ca = new CmdAction(label, button);
				ca.path = new ArrayList<>(path);
				if (accelerator != null)
					ca.setkeyString(accelerator);
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
			return "Unmatched action";
		}
	}
}
