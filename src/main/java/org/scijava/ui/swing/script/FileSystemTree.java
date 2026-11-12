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

import com.formdev.flatlaf.icons.FlatAbstractIcon;
import com.formdev.flatlaf.icons.FlatOptionPaneErrorIcon;
import org.scijava.log.Logger;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A tree listing the contents of one or more top-level folders. Folders are
 * listed when expanded, and kept in sync with the file system while expanded.
 * Folder and file icons are the ones defined by the look and feel (
 * {@code Tree.openIcon}, {@code Tree.closedIcon}, {@code Tree.leafIcon}).
 *
 * @author Albert Cardona
 */
public class FileSystemTree extends JTree {

	private static final int MAX_SCANNED = 200_000;
	/** Searches stop at this many matches: more cannot be digested by the tree (or the user) */
	private static final int MAX_HITS = 1000;
	/** Max. no. of folders expanded automatically to reveal hits (each expansion lists a folder on the EDT) */
	private static final int MAX_AUTO_EXPAND = 100;
	private int expand_budget;
	/** Max. no. of entries listed for a single folder (the rest is reported, not listed) */
	private static final int MAX_LISTED = 1000;

	/**
	 * Folders (matched by the end of their name, case-insensitively) that hold huge numbers of
	 * files, e.g., chunked image volumes. Searches match them by name but never look inside.
	 * Editable through {@link #setOpaqueFolders(Collection)}
	 */
	private final Set<String> opaque_folders = new CopyOnWriteArraySet<>(List.of(".zarr", ".n5", "node_modules"));
	private Consumer<String> notice_listener;
	private int last_skipped; // opaque folders left out of the last search
	private boolean expanded_only; // search expanded folders only, regardless of search_collapsed
	private boolean last_hit_cap; // last search stopped at MAX_HITS
	private boolean last_capped; // last search revealed more folders than were expanded

	/**
	 * Whether a file or folder is hidden. Besides names starting with a dot, folders
	 * can be flagged as hidden by the OS (e.g., ~/Library on macOS, AppData on Windows).
	 * Top-level folders are never subject to this check: hidden folders can still be
	 * listed if chosen/dragged manually.
	 */
	private static boolean isHidden(final File f) {
		if (f.isHidden()) return true;
		if (!f.isDirectory()) return false;
		try {
			return Files.isHidden(f.toPath());
		} catch (final IOException | RuntimeException ignored) {
			return false;
		}
	}

	private static String withSlash(final String path) {
		final String p = path.replace('\\', '/'); // compatible with Windows
		return p.endsWith("/") ? p : p + "/";
	}

	public class Node extends DefaultMutableTreeNode {

		private final String path;
		private final boolean directory;
		private boolean failed;
		// True if this node lists everything (it is in, or below, a folder matching the filter)
		private boolean showAll;
		private int omitted; // entries not listed because the folder is too large

		public Node(final String path) {
			this.path = withSlash(path);
			this.directory = new File(this.path).isDirectory();
		}

		public boolean isDirectory() {
			return directory;
		}

		/**
		 * Lists the visible children. Unless pruning (see
		 * {@link FileSystemTree#setFileFilter(FileFilter, boolean)}), directories are
		 * not subject to filtering
		 */
		private File[] listChildren() {
			final Prune p = prune;
			final boolean all = p != null && listsAll(p);
			File[] files = new File(path).listFiles(f -> {
				if (isHidden(f) || f.getName().endsWith("~") || isIgnored(f.getName())) return false;
				if (p == null) return f.isDirectory() || file_filter.accept(f);
				if (all) return true;
				return f.isDirectory() ? p.keep.contains(withSlash(f.getAbsolutePath())) : file_filter.accept(f);
			});
			if (files != null) Arrays.sort(files);
			omitted = (files != null && files.length > MAX_LISTED) ? files.length - MAX_LISTED : 0;
			if (omitted > 0) files = Arrays.copyOf(files, MAX_LISTED);
			return files;
		}

		private boolean listsAll(final Prune p) {
			return showAll || p.full.contains(path);
		}

		/** If this is a directory, adds a Node for each of its visible files */
		private void populate() {
			if (!directory) return;
			clear(); // never list twice
			final File[] files = listChildren();
			failed = files == null;
			if (failed) {
				// e.g., no permission: flagged with an error icon, nothing else to report
				log.debug("Could not list folder " + path);
				return;
			}
			if (omitted > 0 && notice_listener != null) notice_listener
					.accept("Showing the first " + MAX_LISTED + " of " + (MAX_LISTED + omitted) + " items in "
							+ new File(path).getName());
			final int first = getChildCount();
			final int[] indices = new int[files.length];
			final boolean all = prune != null && listsAll(prune);
			for (int i = 0; i < files.length; i++) {
				final Node child = new Node(files[i].getAbsolutePath());
				child.showAll = all;
				add(child);
				indices[i] = first + i;
			}
			getModel().nodesWereInserted(this, indices);
		}

		private void clear() {
			final int n = getChildCount();
			if (n == 0) return;
			final int[] indices = new int[n];
			final Object[] removed = new Object[n];
			for (int i = 0; i < n; i++) {
				indices[i] = i;
				removed[i] = getChildAt(i);
			}
			removeAllChildren();
			getModel().nodesWereRemoved(this, indices, removed);
		}

		@Override
		public String toString() {
			return path;
		}

		@Override
		public Node getChildAt(final int index) {
			return (Node) super.getChildAt(index);
		}

		@Override
		public Object getUserObject() {
			return path;
		}

		@Override
		public boolean isLeaf() {
			return !isRoot() && !directory;
		}
	}

	/** Paths found by a scan of the top-level folders for files/folders accepted by a filter */
	private static final class Prune {

		final Set<String> keep = new HashSet<>(); // folders to display
		final Set<String> full = new HashSet<>(); // folders whose name matches: display their whole content
		int hits;
		int skipped; // opaque folders not searched
		boolean hitCap; // stopped at MAX_HITS
		boolean truncated;

		Prune copy() {
			final Prune c = new Prune();
			c.keep.addAll(keep);
			c.full.addAll(full);
			c.hits = hits;
		c.skipped = skipped;
		c.hitCap = hitCap;
			c.truncated = truncated;
			return c;
		}
	}

	/** Notified when a filter has been applied */
	public interface FilterListener {

		/**
		 * @param hits the number of matches so far, or -1 if folders are not being hidden
		 * @param truncated whether the search stopped before covering all folders
		 * @param done false while the search is still running (results are streamed)
		 */
		void filterApplied(int hits, boolean truncated, boolean done);
	}

	public interface LeafListener {

		void leafDoubleClicked(final File file);
	}

	final Logger log;
	private final List<LeafListener> leaf_listeners = new CopyOnWriteArrayList<>();
	private final DirectoryWatcher dir_watcher;
	private final Set<String> ignored_extensions = new HashSet<>(); // lower case
	private FileFilter file_filter = f -> true;
	// Filtering that hides folders without hits: state of the last completed scan
	private boolean prune_active;
	private Prune prune;
	private Set<String> saved_expansion; // expanded folders before pruning started
	private final TreeMap<Integer, Node> hidden_tops = new TreeMap<>(); // top-level folders without hits
	private SwingWorker<Prune, Prune> scanner;
	private boolean scanning;
	private boolean search_collapsed = true;
	private boolean live_updates = true;
	private volatile long publish_interval_nanos = 300_000_000L;
	private FilterListener filter_listener;
	// Set by FileSystemTreePanel, released in destroy()
	FileDrop fileDrop;

	public FileSystemTree(final Logger log) {
		this.log = log;
		dir_watcher = new DirectoryWatcher();
		setModel(new DefaultTreeModel(new Node("#root#")));
		setRootVisible(false);
		setShowsRootHandles(true);
		getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		ToolTipManager.sharedInstance().registerComponent(this);
		final FlatAbstractIcon errorIcon = new FlatOptionPaneErrorIcon();
		errorIcon.setScale(.35f);
		// FlatLaf does not assign node icons by default, but defines them
		setCellRenderer(new DefaultTreeCellRenderer() {

			@Override
			public Component getTreeCellRendererComponent(final JTree tree, final Object value, final boolean selected,
					final boolean expanded, final boolean leaf, final int row, final boolean hasFocus) {
				super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
				final String key = leaf ? "Tree.leafIcon" : (expanded ? "Tree.openIcon" : "Tree.closedIcon");
				setIcon(((Node) value).failed ? errorIcon : UIManager.getIcon(key));
				return this;
			}
		});
		addTreeWillExpandListener(new TreeWillExpandListener() {

			@Override
			public void treeWillExpand(final TreeExpansionEvent event) {
				final Node node = (Node) event.getPath().getLastPathComponent();
				node.populate();
				if (live_updates && !prune_active) dir_watcher.register(node); // no watchers for transient, filtered listings
			}

			@Override
			public void treeWillCollapse(final TreeExpansionEvent event) {
				final Node node = (Node) event.getPath().getLastPathComponent();
				for (final Enumeration<?> e = node.depthFirstEnumeration(); e.hasMoreElements();)
					dir_watcher.unregister((Node) e.nextElement());
				node.clear();
			}
		});
		addMouseListener(new MouseAdapter() {

			@Override
			public void mousePressed(final MouseEvent me) {
				if (me.getClickCount() != 2) return;
				final TreePath path = getPathForLocation(me.getX(), me.getY());
				if (path == null) return;
				final Node node = (Node) path.getLastPathComponent();
				if (!node.isDirectory()) {
					final File file = new File(node.path);
					leaf_listeners.forEach(l -> l.leafDoubleClicked(file));
				}
			}
		});
	}

	/**
	 * Folders are emptied when collapsed (and listed again when expanded). The
	 * default implementation would remember that a collapsed folder was expanded
	 * before, and since it has no children, would not paint its expand handle.
	 */
	@Override
	public boolean hasBeenExpanded(final TreePath path) {
		return isExpanded(path);
	}

	@Override
	public DefaultTreeModel getModel() {
		return (DefaultTreeModel) super.getModel();
	}

	private Node root() {
		return (Node) getModel().getRoot();
	}

	@Override
	public String convertValueToText(final Object value, final boolean selected, final boolean expanded,
			final boolean leaf, final int row, final boolean hasFocus) {
		// NB: the JTree constructor calls this with its default model
		if (!(value instanceof Node)) return super.convertValueToText(value, selected, expanded, leaf, row, hasFocus);
		return new File(((Node) value).path).getName();
	}

	@Override
	public String getToolTipText(final MouseEvent e) {
		final TreePath path = getPathForLocation(e.getX(), e.getY());
		return (path == null) ? null : ((Node) path.getLastPathComponent()).path;
	}

	@Override
	protected void paintComponent(final Graphics g) {
		super.paintComponent(g);
		// Plain hints while the tree is empty (no content, or all folders hidden by the filter)
		if (root().getChildCount() > 0) return;
		final String[] lines = hidden_tops.isEmpty() ? new String[] { "Drag folders here", "or right-click for options" }
				: new String[] { scanning ? "Searching..." : "No matches" };
		final java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
		try {
			g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
					java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2.setColor(TextEditor.GuiUtils.secondaryColor());
			final java.awt.FontMetrics fm = g2.getFontMetrics();
			int y = (getHeight() - fm.getHeight() * lines.length) / 2 + fm.getAscent();
			for (final String line : lines) {
				g2.drawString(line, (getWidth() - fm.stringWidth(line)) / 2, y);
				y += fm.getHeight();
			}
		} finally {
			g2.dispose();
		}
	}

	/**
	 * Add a file name extension to ignore, without the dot, for example "class".
	 * Files whose name have this extension will not be listed.
	 *
	 * @param extension The file name extension to ignore, e.g. "class".
	 * @return true if the extension wasn't there already.
	 */
	public boolean ignoreExtension(final String extension) {
		return ignored_extensions.add(extension.toLowerCase());
	}

	/**
	 * Opposite of {@link #ignoreExtension(String)}.
	 *
	 * @param extension The file name extension to stop ignoring, e.g. "class".
	 * @return true if the extension was being ignored.
	 */
	public boolean showExtension(final String extension) {
		return ignored_extensions.remove(extension.toLowerCase());
	}

	private boolean isIgnored(final String name) {
		final int dot = name.lastIndexOf('.');
		return dot >= 0 && ignored_extensions.contains(name.substring(dot + 1).toLowerCase());
	}

	/**
	 * Sets the filter applied to files (not folders). Expanded folders are
	 * re-listed
	 *
	 * @param file_filter the filter
	 */
	public void setFileFilter(final FileFilter file_filter) {
		setFileFilter(file_filter, false);
	}

	/**
	 * Sets the filter applied to files. If {@code pruneFolders} is true, folders
	 * are filtered too: all folders are searched in the background and only
	 * those leading to files accepted by the filter are listed (and expanded).
	 * Folders accepted by the filter are listed with all of their content.
	 * Top-level folders without hits are hidden, and restored once the filter is
	 * lifted.
	 *
	 * @param file_filter the filter
	 * @param pruneFolders whether folders without hits should be hidden
	 * @see #setFilterListener(FilterListener)
	 */
	public void setFileFilter(final FileFilter file_filter, final boolean pruneFolders) {
		cancelFilterScan();
		this.file_filter = file_filter;
		if (!pruneFolders) {
			if (prune_active) {
				prune_active = false;
				rebuild(null);
			} else {
				final Node root = root();
				for (int i = 0; i < root.getChildCount(); i++) {
					final Node top = root.getChildAt(i);
					if (isExpanded(new TreePath(top.getPath()))) refresh(top);
				}
			}
			if (filter_listener != null) filter_listener.filterApplied(-1, false, true);
			return;
		}
		if (!prune_active) saved_expansion = expandedFolders();
		prune_active = true;
		scanning = true;
		final List<String> tops = new ArrayList<>();
		for (final Node top : allTops())
			tops.add(top.path);
		final Set<String> onlyIn = (search_collapsed && !expanded_only) ? null : new HashSet<>(saved_expansion);
		if (onlyIn != null) onlyIn.addAll(tops); // top-level folders are always searched, expanded or not
		if (filter_listener != null) filter_listener.filterApplied(0, false, false);
		scanner = new SwingWorker<Prune, Prune>() {

			@Override
			protected Prune doInBackground() {
				final Prune p = new Prune();
				final long[] state = { 0, System.nanoTime() };
				for (final String top : tops) {
					if (isCancelled() || p.truncated || p.hitCap) break;
					walk(Paths.get(top), file_filter, p, onlyIn, state, this::isCancelled, this::publish);
				}
				return p;
			}

			@Override
			protected void process(final List<Prune> chunks) {
				if (!isCancelled() && scanner == this) rebuild(chunks.get(chunks.size() - 1), false);
			}

			@Override
			protected void done() {
				if (isCancelled() || scanner != this) return;
				try {
					scanning = false;
					rebuild(get(), true);
				} catch (final Exception e) {
					log.error(e);
				}
			}
		};
		scanner.execute();
	}

	/**
	 * Whether the filter also searches folders that are not expanded (the default).
	 * If false, only folders that are currently expanded are searched, which is
	 * much faster for very large top-level folders.
	 */
	public void setSearchCollapsedFolders(final boolean search) {
		search_collapsed = search;
	}

	public boolean isSearchCollapsedFolders() {
		return search_collapsed;
	}

	/** Stops a search triggered by {@link #setFileFilter(FileFilter, boolean)} that is still running */
	public void cancelFilterScan() {
		if (scanner != null) scanner.cancel(true);
		scanner = null;
		scanning = false;
	}

	/** The folders that searches do not look into (see {@link #setOpaqueFolders(Collection)}) */
	public List<String> getOpaqueFolders() {
		return new ArrayList<>(opaque_folders);
	}

	/** Sets the folders that searches do not look into: endings of folder names, e.g., ".zarr" */
	public void setOpaqueFolders(final Collection<String> endings) {
		opaque_folders.clear();
		for (final String e : endings)
			if (e != null && !e.trim().isEmpty()) opaque_folders.add(e.trim());
	}

	private boolean isOpaque(final Path dir) {
		final Path name = dir.getFileName();
		if (name == null) return false;
		final String n = name.toString().toLowerCase();
		for (final String e : opaque_folders)
			if (n.endsWith(e.toLowerCase())) return true;
		return false;
	}

	/** Whether the last search stopped at the maximum number of matches */
	boolean isHitCapped() {
		return last_hit_cap;
	}

	/**
	 * Restricts the next searches to expanded folders only (e.g., for very short queries),
	 * regardless of {@link #setSearchCollapsedFolders(boolean)}
	 */
	void setExpandedOnly(final boolean expandedOnly) {
		expanded_only = expandedOnly;
	}

	/** Number of folders left out of the last search because they are opaque */
	int getSkippedFolderCount() {
		return last_skipped;
	}

	/** Whether the last search found more folders to expand than the limit for automatic expansion */
	boolean isExpansionCapped() {
		return last_capped;
	}

	/** Sets the listener for messages such as folders listed partially (called on the EDT) */
	void setNoticeListener(final Consumer<String> listener) {
		notice_listener = listener;
	}

	public void setFilterListener(final FilterListener listener) {
		filter_listener = listener;
	}

	/** A folder being walked by {@link #walk} */
	private static final class Frame {

		final String path;
		boolean found;

		Frame(final String path) {
			this.path = path;
		}
	}

	/**
	 * Searches {@code top} recursively for files/folders accepted by the filter,
	 * recording the folders to display. Uses the attributes provided by the walk
	 * (no additional file system calls per entry). Periodically hands a snapshot of
	 * the findings to {@code publisher}, so that they can be displayed while the
	 * search is running.
	 *
	 * @param onlyIn if not null, only these folders are descended into
	 */
	private void walk(final Path top, final FileFilter filter, final Prune p, final Set<String> onlyIn, final long[] state,
			final BooleanSupplier cancelled, final Consumer<Prune> publisher) {
		final boolean windows = File.separatorChar == '\\';
		final List<Frame> stack = new ArrayList<>();
		try {
			Files.walkFileTree(top, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE,
                    new SimpleFileVisitor<>() {

                        private boolean first = true;

                        private boolean skipped(final Path path, final boolean dir) {
                            final Path name = path.getFileName();
                            if (name == null) return false;
                            final String n = name.toString();
                            if (n.endsWith("~") || isIgnored(n) || n.startsWith(".")) return true;
                            if (!dir && !windows) return false;
                            try {
                                return Files.isHidden(path);
                            } catch (final IOException ignored) {
                                return false;
                            }
                        }

                        private FileVisitResult tick() {
                            if (cancelled.getAsBoolean() || p.hitCap) return FileVisitResult.TERMINATE;
                            if (++state[0] > MAX_SCANNED) {
                                p.truncated = true;
                                return FileVisitResult.TERMINATE;
                            }
                            final long now = System.nanoTime();
                            if (now - state[1] > publish_interval_nanos) {
                                state[1] = now;
                                publisher.accept(p.copy());
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        private void hit() {
                            if (++p.hits >= MAX_HITS) p.hitCap = true;
                            for (int i = stack.size() - 1; i >= 0 && !stack.get(i).found; i--) {
                                stack.get(i).found = true;
                                p.keep.add(stack.get(i).path);
                            }
                        }

                        @Override
                        public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) {
                            final FileVisitResult result = tick();
                            if (result != FileVisitResult.CONTINUE) return result;
                            final boolean isTop = first;
                            first = false;
                            final String path = withSlash(dir.toAbsolutePath().toString());
                            if (!isTop) {
                                if (skipped(dir, true)) return FileVisitResult.SKIP_SUBTREE;
                                if (filter.accept(dir.toFile())) { // list it, with all its content
                                    p.full.add(path);
                                    p.keep.add(path);
                                    hit();
                                    return FileVisitResult.SKIP_SUBTREE;
                                }
                            }
                            if (!isTop && isOpaque(dir)) { // never search inside chunk stores and similar
                                p.skipped++;
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            if (onlyIn != null && !onlyIn.contains(path)) return FileVisitResult.SKIP_SUBTREE;
                            stack.add(new Frame(path));
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) {
                            if (!stack.isEmpty()) stack.remove(stack.size() - 1);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                            final FileVisitResult result = tick();
                            if (result != FileVisitResult.CONTINUE) return result;
                            if (!skipped(file, false) && filter.accept(file.toFile())) hit();
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(final Path file, final IOException exc) {
                            return FileVisitResult.CONTINUE; // e.g., no permission
                        }
                    });
		} catch (final IOException | RuntimeException e) {
			// A folder that cannot be searched: ignore it
		}
	}

	/** Lists the top-level folders, including those hidden by the filter, in their original order */
	private List<Node> allTops() {
		final Node root = root();
		final List<Node> tops = new ArrayList<>();
		for (int i = 0; i < root.getChildCount(); i++)
			tops.add(root.getChildAt(i));
		hidden_tops.forEach((index, node) -> tops.add(Math.min(index, tops.size()), node));
		return tops;
	}

	private void restoreHiddenTops() {
		hidden_tops.forEach((index, node) -> getModel().insertNodeInto(node, root(), Math.min(index, root().getChildCount())));
		hidden_tops.clear();
	}

	private Set<String> expandedFolders() {
		final Set<String> expanded = new HashSet<>();
		final Node root = root();
		for (int i = 0; i < root.getChildCount(); i++) {
			final Enumeration<TreePath> e = getExpandedDescendants(new TreePath(root.getChildAt(i).getPath()));
			while (e != null && e.hasMoreElements())
				expanded.add(((Node) e.nextElement().getLastPathComponent()).path);
		}
		return expanded;
	}

	/**
	 * Re-lists all folders according to the outcome of a scan, expanding the paths to
	 * the hits. A null scan lifts the filtering, restoring the previous expansion
	 */
	private void rebuild(final Prune p) {
		rebuild(p, true);
	}

	/** @param done false if the scan is still running: the tree is updated with partial results */
	private void rebuild(final Prune p, final boolean done) {
		final long start = System.nanoTime();
		restoreHiddenTops();
		prune = p;
		final Node root = root();
		for (int i = 0; i < root.getChildCount(); i++) {
			final Node top = root.getChildAt(i);
			collapsePath(new TreePath(top.getPath())); // releases the content
			top.showAll = false;
		}
		if (p == null) {
			final Set<String> saved = saved_expansion;
			saved_expansion = null;
			for (int i = 0; saved != null && i < root.getChildCount(); i++) {
				final Node top = root.getChildAt(i);
				if (saved.contains(top.path)) {
					expandPath(new TreePath(top.getPath()));
					restoreExpansion(top, saved);
				}
			}
			return;
		}
		for (int i = root.getChildCount() - 1; i >= 0; i--) {
			final Node top = root.getChildAt(i);
			if (!p.keep.contains(top.path)) {
				hidden_tops.put(i, top);
				getModel().removeNodeFromParent(top);
			}
		}
		expand_budget = MAX_AUTO_EXPAND;
		for (int i = 0; i < root.getChildCount(); i++) {
			final Node top = root.getChildAt(i);
			expandPath(new TreePath(top.getPath()));
			expandHits(top, p);
		}
		if (done) scrollRowToVisible(0);
		last_skipped = p.skipped;
		last_hit_cap = p.hitCap;
		last_capped = expand_budget < 0;
		// Do not let updating the tree dominate the search
		publish_interval_nanos = Math.max(300_000_000L, 5 * (System.nanoTime() - start));
		if (filter_listener != null) filter_listener.filterApplied(p.hits, p.truncated, done);
	}

	/** Expands the (listed) folders leading to hits. Folders that match are not expanded further */
	private void expandHits(final Node node, final Prune p) {
		if (node.listsAll(p)) return;
		for (int i = 0; i < node.getChildCount(); i++) {
			final Node child = node.getChildAt(i);
			if (child.isDirectory() && p.keep.contains(child.path)) {
				if (expand_budget-- <= 0) return; // too many: remaining folders can be expanded manually
				expandPath(new TreePath(child.getPath()));
				expandHits(child, p);
			}
		}
	}

	/**
	 * Whether listed folders are kept in sync with the file system (the default).
	 * Turn it off for network or cloud folders, where file system events are
	 * unreliable or expensive. Use {@link #refresh()} to update the listing on demand.
	 */
	public void setLiveUpdates(final boolean live) {
		if (live == live_updates) return;
		live_updates = live;
		final Node root = root();
		if (live) {
			for (final Enumeration<?> e = root.depthFirstEnumeration(); e.hasMoreElements();) {
				final Node n = (Node) e.nextElement();
				if (n.isDirectory() && isExpanded(new TreePath(n.getPath()))) dir_watcher.register(n);
			}
			refresh(); // catch up with changes made meanwhile
		} else {
			for (final Enumeration<?> e = root.depthFirstEnumeration(); e.hasMoreElements();)
				dir_watcher.unregister((Node) e.nextElement());
		}
	}

	public boolean isLiveUpdates() {
		return live_updates;
	}

	/** Re-lists all expanded folders, preserving their expansion */
	public void refresh() {
		if (prune_active) {
			setFileFilter(file_filter, true); // search again
			return;
		}
		final Node root = root();
		for (int i = 0; i < root.getChildCount(); i++) {
			final Node top = root.getChildAt(i);
			if (isExpanded(new TreePath(top.getPath()))) refresh(top);
		}
	}

	/** Removes all top-level folders */
	public void clearTopLevelFolders() {
		cancelFilterScan();
		final Node root = root();
		for (final Enumeration<?> e = root.depthFirstEnumeration(); e.hasMoreElements();)
			dir_watcher.unregister((Node) e.nextElement());
		hidden_tops.clear();
		root.clear();
	}

	/** Removes a top-level folder */
	public void removeTopLevelFolder(final Node top) {
		for (final Enumeration<?> e = top.depthFirstEnumeration(); e.hasMoreElements();)
			dir_watcher.unregister((Node) e.nextElement());
		getModel().removeNodeFromParent(top);
	}

	/** Re-lists the children of a node, preserving the expansion of its subfolders */
	private void refresh(final Node node) {
		if (node.getRoot() != root()) { // removed from the tree
			dir_watcher.unregister(node);
			return;
		}
		// A folder collapsed after the file system event was queued must remain empty
		if (!isExpanded(new TreePath(node.getPath()))) return;
		final Set<String> expanded = new HashSet<>();
		final Enumeration<TreePath> e = getExpandedDescendants(new TreePath(node.getPath()));
		while (e != null && e.hasMoreElements())
			expanded.add(((Node) e.nextElement().getLastPathComponent()).path);
		node.clear();
		node.populate();
		restoreExpansion(node, expanded);
	}

	private void restoreExpansion(final Node node, final Set<String> expanded) {
		for (int i = 0; i < node.getChildCount(); i++) {
			final Node child = node.getChildAt(i);
			if (expanded.contains(child.path)) {
				expandPath(new TreePath(child.getPath())); // lists the child (see treeWillExpand)
				restoreExpansion(child, expanded);
			}
		}
	}

	public void addLeafListener(final LeafListener l) {
		leaf_listeners.add(l);
	}

	public void removeLeafListener(final LeafListener l) {
		leaf_listeners.remove(l);
	}

	public ArrayList<LeafListener> getLeafListeners() {
		return new ArrayList<>(leaf_listeners);
	}

	/**
	 * Add a directory as a top-level root.
	 *
	 * @param dir The directory to add as a top-level root
	 * @param checkIfChild If true, and the {@code dir} is a child of an existing root,
	 *                     then merely expand it and don't add it as a root.
	 */
	public void addRootDirectory(final String dir, final boolean checkIfChild) {
		restoreHiddenTops();
		addRoot(dir, checkIfChild);
		if (prune_active) setFileFilter(file_filter, true); // search again
	}

	private void addRoot(final String dir, final boolean checkIfChild) {
		final File file = new File(dir);
		if (!file.isDirectory()) return;
		final String dirPath = withSlash(file.getAbsolutePath());
		final Node root = root();
		if (checkIfChild) {
			for (int i = 0; i < root.getChildCount(); i++) {
				final TreePath path = pathTo(root.getChildAt(i), dirPath);
				if (path != null) {
					expandPath(path);
					setSelectionPath(path);
					scrollPathToVisible(path);
					return;
				}
			}
		}
		getModel().insertNodeInto(new Node(dirPath), root, root.getChildCount());
	}

	/** Expands nodes downwards until reaching dirPath. Returns null if not found */
	private TreePath pathTo(Node node, final String dirPath) {
		while (dirPath.startsWith(node.path)) {
			if (dirPath.equals(node.path)) return new TreePath(node.getPath());
			expandPath(new TreePath(node.getPath())); // lists the node (see treeWillExpand)
			Node next = null;
			for (int i = 0; i < node.getChildCount() && next == null; i++) {
				if (dirPath.startsWith(node.getChildAt(i).path)) next = node.getChildAt(i);
			}
			if (next == null) return null;
			node = next;
		}
		return null;
	}

	/**
	 * For persistence.
	 *
	 * @return A String with the absolute file paths of all top-level folders
	 *         concatenated with {@link File#pathSeparator}.
	 */
	public String getTopLevelFoldersString() {
		final List<String> paths = new ArrayList<>();
		for (final Node top : allTops())
			paths.add(top.path);
		return String.join(File.pathSeparator, paths);
	}

	/**
	 * For restoring state. Only those paths that are directories and exist will be added.
	 *
	 * @param folders A String generated from {@code FileSystemTree#getTopLevelFoldersString()}.
	 */
	public void addTopLevelFoldersFrom(final String folders) {
		for (final String path : folders.split(File.pathSeparator)) {
			addRootDirectory(path, false);
		}
	}

	public void destroy() {
		cancelFilterScan();
		dir_watcher.interrupt();
		if (fileDrop != null) fileDrop.uninstall();
	}

	/** Watches expanded folders, re-listing them when their content changes */
	private class DirectoryWatcher extends Thread {

		private final Map<WatchKey, Node> keys = new ConcurrentHashMap<>();
		private final Map<Node, WatchKey> byNode = new ConcurrentHashMap<>();
		private WatchService watcher;

		DirectoryWatcher() {
			super("FileSystemTree watcher");
			setDaemon(true);
			try {
				watcher = FileSystems.getDefault().newWatchService();
				start();
			} catch (final IOException e) {
				log.error("Failed to start filesystem watching.", e);
			}
		}

		void register(final Node node) {
			if (watcher == null || node.failed) return; // unreadable folders cannot be watched
			try {
				final WatchKey key = new File(node.path).toPath().register(watcher, StandardWatchEventKinds.ENTRY_CREATE,
						StandardWatchEventKinds.ENTRY_DELETE);
				final Node previous = keys.put(key, node); // same key if the folder was already registered
				if (previous != null && previous != node) byNode.remove(previous);
				byNode.put(node, key);
			} catch (final IOException e) {
				log.debug("Could not watch folder " + node.path + ": " + e.getMessage());
			}
		}

		void unregister(final Node node) {
			final WatchKey key = byNode.remove(node);
			// The key may have been taken over by another node listing the same folder
			if (key != null && keys.get(key) == node) {
				key.cancel();
				keys.remove(key);
			}
		}

		@Override
		public void run() {
			try {
				while (!isInterrupted()) {
					final WatchKey key = watcher.take();
					final Node node = keys.get(key);
					if (node != null && !key.pollEvents().isEmpty()) SwingUtilities.invokeLater(() -> refresh(node));
					if (!key.reset()) {
						final Node gone = keys.remove(key);
						if (gone != null) byNode.remove(gone);
					}
				}
			} catch (final InterruptedException | ClosedWatchServiceException ignored) {
				// we are done
			} finally {
				try {
					watcher.close();
				} catch (final IOException ignored) {
					// nothing to do
				}
			}
		}
	}
}
