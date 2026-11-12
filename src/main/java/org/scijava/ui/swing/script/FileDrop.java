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
import java.awt.Container;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.BasicStroke;
import java.awt.geom.RoundRectangle2D;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Makes it easy to drag and drop files from the operating system to a Java
 * program. Any {@link java.awt.Component} displayed in a Swing window can be
 * dropped onto. While dragging, a frame is painted over the target without
 * affecting its border or layout.
 * <p>
 * To use this class, construct a new {@code FileDrop} by passing it the target
 * component and a {@link Listener} to receive notification when file(s) have
 * been dropped. Dropped files are delivered on the Event Dispatch Thread, after
 * the drop operation has been completed, so listeners are free to display
 * dialogs or start long running tasks. Example:
 * </p>
 *
 * <pre>
 * new FileDrop(myPanel, files -&gt; {
 *     // handle file drop
 * });
 * </pre>
 * <p>
 * Released into the Public Domain by the original author, Robert Harder
 * (rob@iharder.net).
 * </p>
 * <p>
 * Additional support:
 * </p>
 * <ul>
 * <li>September 2007, Nathan Blomquist: Linux (KDE/Gnome) support</li>
 * <li>December 2010, Joshua Gerth</li>
 * <li>June 2019, TF: Adjusted default border color, code cleanup</li>
 * <li>2026, TF: Modernization (single drop handler, headless awareness,
 * uri-list parsing, asynchronous listener dispatch, uninstall support, layout
 * neutral drag highlight)</li>
 * </ul>
 *
 * @author Robert Harder
 * @author rharder@users.sf.net
 */
public class FileDrop {

	private static final Logger LOGGER = Logger.getLogger(FileDrop.class
			.getName());
	private static final String URI_LIST_MIME = "text/uri-list";

	private final Component target;
	private final Listener listener;
	private final DropTargetListener dropListener = new Handler();
	private final List<Installed> installed = new ArrayList<>();

	// Frame painted over the target while a drag is active
	private Highlight highlight;

	/**
	 * Constructs a {@link FileDrop} that recursively sets all the components
	 * contained in <var>c</var> as drop targets, though only <var>c</var> is
	 * highlighted while dragging.
	 *
	 * @param c the component on which files will be dropped
	 * @param listener notified when files are dropped
	 */
	public FileDrop(final Component c, final Listener listener) {
		this(c, true, listener);
	}

	/**
	 * Constructs a {@link FileDrop}. In headless mode this constructor is a
	 * no-op.
	 *
	 * @param c the component on which files will be dropped
	 * @param recursive if {@code true}, and <var>c</var> is a
	 *          {@link java.awt.Container}, its children will also be set as drop
	 *          targets, though only <var>c</var> is highlighted while dragging
	 * @param listener notified when files are dropped
	 */
	public FileDrop(final Component c, final boolean recursive,
					final Listener listener)
	{
		this.target = Objects.requireNonNull(c);
		this.listener = Objects.requireNonNull(listener);
		if (GraphicsEnvironment.isHeadless()) {
			LOGGER.fine("Headless mode, drag and drop is disabled");
			return;
		}
		install(c, recursive);
	}

	/**
	 * Removes the drag-and-drop hooks installed by this instance, restoring any
	 * drop target the components had before. Call this if you add and remove
	 * components after you've set up the drag-and-drop.
	 */
	public void uninstall() {
		for (final Installed i : installed) {
			if (i.component.getDropTarget() == i.ours) i.component.setDropTarget(
					i.previous);
		}
		installed.clear();
		hideHighlight();
	}

	private void install(final Component c, final boolean recursive) {
		final DropTarget previous = c.getDropTarget();
		// The DropTarget is attached to the component by its constructor
		final DropTarget ours = new DropTarget(c, DnDConstants.ACTION_COPY,
				dropListener, true);
		installed.add(new Installed(c, previous, ours));
		if (recursive && c instanceof Container) {
			for (final Component child : ((Container) c).getComponents())
				install(child, true);
		}
	}

	/**
	 * Paints a dashed box with a short message at the center of a component, to
	 * advertise that it accepts dropped files. Meant to be called from
	 * {@code paintComponent()} while the component is empty. Nothing is painted
	 * if the component is too small to fit the hint.
	 *
	 * @param component the component being painted
	 * @param g the graphics context of the component. Left untouched (a copy is
	 *          used)
	 * @param text the message, e.g., "Drag folders here"
	 */
	public static void paintHint(final Component component, final Graphics g,
								 final String text)
	{
		final Graphics2D g2 = (Graphics2D) g.create();
		try {
			final FontMetrics fm = g2.getFontMetrics();
			final int gap = fm.stringWidth("W");
			final int w = fm.stringWidth(text) + 4 * gap;
			final int h = fm.getHeight() * 3;
			if (component.getWidth() < w + 2 * gap || component
					.getHeight() < h + 2 * gap) return;
			Color c = UIManager.getColor("Label.disabledForeground");
			if (c == null) c = Color.GRAY;
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(c);
			g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT,
					BasicStroke.JOIN_MITER, gap / 2.5f, new float[] { gap / 2.5f }, 0f));
			final double x = (component.getWidth() - w) / 2d;
			final double y = (component.getHeight() - h) / 2d;
			g2.draw(new RoundRectangle2D.Double(x, y, w, h, gap * 2, gap * 2));
			g2.drawString(text, (int) (x + 2 * gap), (int) (y + (h - fm
					.getHeight()) / 2d + fm.getAscent()));
		} finally {
			g2.dispose();
		}
	}

	private void showHighlight() {
		if (highlight != null) return;
		final JRootPane root = SwingUtilities.getRootPane(target);
		if (root == null || !target.isShowing()) return;
		// The layered pane has no layout manager: adding the highlight to it
		// leaves the layout and the border of the target untouched
		final JLayeredPane layers = root.getLayeredPane();
		final Point p = SwingUtilities.convertPoint(target, 0, 0, layers);
		highlight = new Highlight(highlightColor());
		highlight.setBounds(p.x, p.y, target.getWidth(), target.getHeight());
		layers.add(highlight, JLayeredPane.DRAG_LAYER);
	}

	private void hideHighlight() {
		if (highlight == null) return;
		final Container parent = highlight.getParent();
		if (parent != null) {
			parent.remove(highlight);
			parent.repaint(highlight.getX(), highlight.getY(), highlight
					.getWidth(), highlight.getHeight());
		}
		highlight = null;
	}

	private static Color highlightColor() {
		final String[] keys = { "Component.focusColor", "List.selectionBackground",
				"Tree.selectionBackground" };
		for (final String key : keys) {
			final Color c = UIManager.getColor(key);
			if (c != null) return c;
		}
		return new Color(75, 110, 175);
	}

	private void notifyListener(final File[] files) {
		try {
			listener.filesDropped(files);
		} catch (final RuntimeException ex) {
			LOGGER.log(Level.WARNING, "Listener failed", ex);
		}
	}

	private static boolean isUriList(final DataFlavor flavor) {
		return flavor.isRepresentationClassReader() && flavor.isMimeTypeEqual(
				URI_LIST_MIME);
	}

	private static boolean isDragOk(final DataFlavor[] flavors) {
		for (final DataFlavor f : flavors) {
			if (DataFlavor.javaFileListFlavor.equals(f) || isUriList(f)) return true;
		}
		return false;
	}

	private static DataFlavor findUriListFlavor(final Transferable tr) {
		for (final DataFlavor f : tr.getTransferDataFlavors()) {
			if (isUriList(f)) return f;
		}
		return null;
	}

	private static File[] readFileList(final Transferable tr)
			throws IOException, UnsupportedFlavorException
	{
		final Object data = tr.getTransferData(DataFlavor.javaFileListFlavor);
		if (!(data instanceof List)) return new File[0];
		final List<File> files = new ArrayList<>();
		for (final Object o : (List<?>) data) {
			if (o instanceof File) files.add((File) o);
		}
		return files.toArray(new File[0]);
	}

	private static File[] readUriList(final DataFlavor flavor,
									  final Transferable tr) throws IOException, UnsupportedFlavorException
	{
		final List<String> lines = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(flavor.getReaderForText(
				tr)))
		{
			String line;
			while ((line = br.readLine()) != null)
				lines.add(line);
		}
		return parseUriList(lines);
	}

	private static File[] parseUriList(final List<String> lines) {
		final List<File> files = new ArrayList<>();
		for (final String raw : lines) {
			// KDE may append NUL chars, and uri-lists (RFC 2483) may hold comments
			final String line = raw.replace("\0", "").trim();
			if (line.isEmpty() || line.startsWith("#")) continue;
			try {
				final URI uri = new URI(line);
				if ("file".equalsIgnoreCase(uri.getScheme())) files.add(new File(
						uri));
				else LOGGER.fine("Ignoring non-file URI: " + line);
			} catch (final URISyntaxException | RuntimeException ex) {
				LOGGER.fine("Could not parse " + line + ": " + ex.getMessage());
			}
		}
		return files.toArray(new File[0]);
	}

	@SuppressWarnings("serial")
	private static final class Highlight extends JComponent {

		private static final int THICKNESS = 2;
		private final Color color;

		Highlight(final Color color) {
			this.color = color;
			setOpaque(false);
			setFocusable(false);
		}

		// Let mouse events (and drop target lookups) go through
		@Override
		public boolean contains(final int x, final int y) {
			return false;
		}

		@Override
		protected void paintComponent(final Graphics g) {
			final int w = getWidth();
			final int h = getHeight();
			g.setColor(color);
			g.fillRect(0, 0, w, THICKNESS);
			g.fillRect(0, h - THICKNESS, w, THICKNESS);
			g.fillRect(0, THICKNESS, THICKNESS, h - 2 * THICKNESS);
			g.fillRect(w - THICKNESS, THICKNESS, THICKNESS, h - 2 * THICKNESS);
		}
	}

	private static final class Installed {

		final Component component;
		final DropTarget previous;
		final DropTarget ours;

		Installed(final Component component, final DropTarget previous,
				  final DropTarget ours)
		{
			this.component = component;
			this.previous = previous;
			this.ours = ours;
		}
	}

	private final class Handler extends DropTargetAdapter {

		@Override
		public void dragEnter(final DropTargetDragEvent evt) {
			if (target.isEnabled() && isDragOk(evt.getCurrentDataFlavors())) {
				showHighlight();
				evt.acceptDrag(DnDConstants.ACTION_COPY);
			}
			else {
				evt.rejectDrag();
			}
		}

		@Override
		public void dropActionChanged(final DropTargetDragEvent evt) {
			if (target.isEnabled() && isDragOk(evt.getCurrentDataFlavors())) evt
					.acceptDrag(DnDConstants.ACTION_COPY);
			else evt.rejectDrag();
		}

		@Override
		public void dragExit(final DropTargetEvent evt) {
			hideHighlight();
		}

		@Override
		public void drop(final DropTargetDropEvent evt) {
			boolean accepted = false;
			try {
				final Transferable tr = evt.getTransferable();
				final boolean fileList = tr.isDataFlavorSupported(
						DataFlavor.javaFileListFlavor);
				final DataFlavor uriFlavor = fileList ? null : findUriListFlavor(tr);
				if (!fileList && uriFlavor == null) {
					LOGGER.fine("Not a file list or uri list, aborting");
					evt.rejectDrop();
					return;
				}
				evt.acceptDrop(DnDConstants.ACTION_COPY);
				accepted = true;
				final File[] files = fileList ? readFileList(tr) : readUriList(
						uriFlavor, tr);
				evt.dropComplete(files.length > 0);
				// Dispatch after the drop completes so that the drag source (e.g.,
				// the OS file browser) is not blocked by dialogs or long tasks
				if (files.length > 0) SwingUtilities.invokeLater(() -> notifyListener(
						files));
			} catch (final IOException | UnsupportedFlavorException
						   | RuntimeException ex)
			{
				LOGGER.log(Level.WARNING, "Drop aborted", ex);
				if (accepted) evt.dropComplete(false);
				else evt.rejectDrop();
			} finally {
				hideHighlight();
			}
		}
	}

	/**
	 * Implement this interface to listen for when files are dropped.
	 */
	@FunctionalInterface
	public interface Listener {

		/**
		 * Called on the Event Dispatch Thread when files have been successfully
		 * dropped.
		 *
		 * @param files the (non-empty) array of files that were dropped
		 */
		void filesDropped(File[] files);

	}

}