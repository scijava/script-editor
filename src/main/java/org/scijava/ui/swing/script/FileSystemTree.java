/*-
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

import java.awt.Component;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.scijava.log.Logger;

/**
 * 
 * @author Albert Cardona
 *
 */
public class FileSystemTree extends JTree
{	
	static private String withSlash(String path) {
		path = path.replace('\\', '/'); // compatible with Windows
		return '/' == path.charAt(path.length()-1) ? path : path + "/";
	}

	static private final Icon makeErrorIcon() {
		final int[] pixels = new int[]{16777215, 16777215, 16777215, 16777215, 16777215, 16776958, 16772075, 16761281, 16751001, 16744833, 16744833,
			16751001, 16761281, 16772075, 16776958, 16777215, 16777215, 16777215, 16777215, 16777215, 16777215, 16777215, 16777215, 16777215,
			16772075, 16749973, 16728128, 16726586, 16734553, 16742520, 16742520, 16734553, 16726586, 16728128, 16749973, 16772075, 16777215,
			16777215, 16777215, 16777215, 16777215, 16777215, 16776958, 16764365, 16731212, 16728385, 16754599, 16770790, 16774902, 16774131,
			16774131, 16774131, 16770790, 16754599, 16728385, 16731212, 16764365, 16776958, 16777215, 16777215, 16777215, 16777215, 16764365,
			16726072, 16739436, 16770276, 16777215, 16777215, 16757169, 16727871, 16727614, 16747660, 16776958, 16777215, 16770276, 16739436,
			16726072, 16764365, 16777215, 16777215, 16777215, 16772075, 16731212, 16739436, 16773874, 16777215, 16777215, 16777215, 16750744,
			16711937, 16711680, 16738151, 16776958, 16777215, 16777215, 16773874, 16739436, 16731212, 16772075, 16777215, 16776958, 16749973,
			16728385, 16770276, 16777215, 16777215, 16777215, 16777215, 16750744, 16711937, 16711680, 16738151, 16776958, 16777215, 16777215,
			16777215, 16770276, 16728385, 16749973, 16776958, 16772075, 16728128, 16754599, 16777215, 16777215, 16777215, 16777215, 16777215,
			16750744, 16711937, 16711680, 16738151, 16776958, 16777215, 16777215, 16777215, 16777215, 16754599, 16728128, 16772075, 16761281,
			16726586, 16770790, 16777215, 16777215, 16777215, 16777215, 16777215, 16750744, 16711937, 16711680, 16738151, 16776958, 16777215,
			16777215, 16777215, 16777215, 16770790, 16726586, 16761281, 16751001, 16734553, 16775930, 16777215, 16777215, 16777215, 16777215,
			16777215, 16750744, 16711937, 16711680, 16738151, 16776958, 16777215, 16777215, 16777215, 16777215, 16775930, 16734553, 16751001,
			16744833, 16742520, 16777215, 16777215, 16777215, 16777215, 16777215, 16777215, 16750744, 16711937, 16711680, 16738151, 16776958,
			16777215, 16777215, 16777215, 16777215, 16777215, 16742520, 16744833, 16744833, 16742520, 16777215, 16777215, 16777215, 16777215,
			16777215, 16777215, 16750744, 16711937, 16711680, 16738151, 16776958, 16777215, 16777215, 16777215, 16777215, 16777215, 16742520,
			16744833, 16751001, 16734553, 16775930, 16777215, 16777215, 16777215, 16777215, 16777215, 16750744, 16711937, 16711680, 16738151,
			16776958, 16777215, 16777215, 16777215, 16777215, 16775930, 16734553, 16751001, 16761281, 16726586, 16770790, 16777215, 16777215,
			16777215, 16777215, 16777215, 16751772, 16715021, 16714507, 16739950, 16776958, 16777215, 16777215, 16777215, 16777215, 16770790,
			16726586, 16761281, 16772075, 16728128, 16754599, 16777215, 16777215, 16777215, 16777215, 16777215, 16770790, 16760767, 16760510,
			16767706, 16777215, 16777215, 16777215, 16777215, 16777215, 16754599, 16728128, 16772075, 16776958, 16749973, 16728385, 16770276,
			16777215, 16777215, 16777215, 16777215, 16773360, 16745604, 16738665, 16767192, 16777215, 16777215, 16777215, 16777215, 16770276,
			16728385, 16749973, 16776958, 16777215, 16772075, 16731212, 16739436, 16773874, 16777215, 16777215, 16777215, 16759996, 16714764,
			16711937, 16743291, 16776958, 16777215, 16777215, 16773874, 16739436, 16731212, 16772075, 16777215, 16777215, 16777215, 16764365,
			16726072, 16739436, 16770276, 16777215, 16777215, 16766164, 16723502, 16717848, 16753571, 16777215, 16777215, 16770276, 16739436,
			16726072, 16764365, 16777215, 16777215, 16777215, 16777215, 16776958, 16764365, 16731212, 16728385, 16754599, 16770790, 16775416,
			16767963, 16764879, 16774131, 16770790, 16754599, 16728385, 16731212, 16764365, 16776958, 16777215, 16777215, 16777215, 16777215,
			16777215, 16777215, 16772075, 16749973, 16728128, 16726586, 16734553, 16742520, 16742520, 16734553, 16726586, 16728128, 16749973,
			16772075, 16777215, 16777215, 16777215, 16777215, 16777215, 16777215, 16777215, 16777215, 16777215, 16776958, 16772075, 16761281,
			16751001, 16744833, 16744833, 16751001, 16761281, 16772075, 16776958, 16777215, 16777215, 16777215, 16777215, 16777215};
		final BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, 20, 20, pixels, 0, 20);
		return new ImageIcon(image);
	}

	static public final Icon ICON_ERROR = makeErrorIcon();

	public class Node extends DefaultMutableTreeNode
	{
		final private String path;
		private Icon icon = null;

		public Node(final String path) {
			this.path = withSlash(path);
		}

		public boolean isDirectory() {
			return new File(this.path).isDirectory();
		}

		/**
		 * 
		 * @param sort
		 * @param file_filter Applies to leafs, not to directories.
		 * @return
		 */
		public File[] updatedChildrenFiles(final boolean sort, final FileFilter file_filter) {
			final File file = new File(this.path);
			if (!file.isDirectory()) {
				return new File[0];
			}
			final File[] files = file.listFiles(new FileFilter() {
				@Override
				public boolean accept(final File f) {
					return !f.isHidden() && !f.getName().endsWith("~")
							&& !re_ignored_extensions.matcher(f.getName()).matches()
							&& (f.isDirectory() || file_filter.accept(f));
				}
			});
			if (sort) Arrays.sort(files);
			return files;
		}

		/**
		 * If it's a directory, add a Node for each of its visible files.
		 */
		public synchronized void populateChildren(final DefaultTreeModel model, final FileFilter file_filter) {
			try {
				if (isLeaf()) return;
				int index = 0;
				for (final File file : updatedChildrenFiles(true, file_filter)) {
					// Can't use add: would try to insert at getChildCount(), which is the wrong value here
					model.insertNodeInto(new Node(file.getAbsolutePath()), this, index++);
				}
				icon = null;
			} catch (Throwable t) {
				icon = ICON_ERROR;
				log.error("Failed to populate folder " + this.path, t);
			}
		}

		/**
		 * Recursive expansion until opening the final element of the path
		 * @param path The absolute file path.
		 * @param full A single-slot array containing the full path, if any.
		 */
		public void expandTo(final String path, final TreePath[] full) {
			if (path.startsWith(this.path)) {
				final LinkedList<Node> stack = new LinkedList<>();
				stack.add(this);
				while (true) {
					final Node node = stack.removeFirst();
					if (path.startsWith(node.path)) {
						for (int i=0, count=node.getChildCount(); i<count; ++i) {
							final Node child = node.getChildAt(i);
							if (path.startsWith(child.path)) {
								stack.addLast(child);
								break;
							}
						}
					}
					if (stack.isEmpty()) {
						if (path.equals(node.path)) full[0] = new TreePath(node.getPath());
						break;
					}
				}
			}
		}

		@Override
		public String toString() {
			return this.path;
		}

		@Override
		public synchronized int getChildCount() {
			return super.getChildCount();
		}

		@Override
		public synchronized Node getChildAt(final int index) {
			if (0 == getChildCount()) return null;
			try {
				return (Node) super.getChildAt(index);
			} catch (ArrayIndexOutOfBoundsException ae) {
				log.error("FileSystemTree: no child at index " + index + " for file at " + this.path, ae);
				return null;
			}
		}

		public Icon getIcon() {
			return this.icon;
		}

		@Override
		public Object getUserObject() {
			return this.path;
		}

		@Override
		public boolean isLeaf() {
			return !isRoot() && !this.isDirectory();
		}

		public synchronized void removeAllChildren(final DefaultTreeModel model) {
			for (int i=super.getChildCount() -1; i>-1; --i) {
				// Can't use DefaultMutableTreeNode.removeAllChildren or .remove(int): the model is not notified
				model.removeNodeFromParent((Node)super.getChildAt(i));
			}
		}

		public synchronized void updateChildrenList(final DefaultTreeModel model, final FileFilter file_filter) {
			removeAllChildren(model);
			populateChildren(model, file_filter);
		}
	}

	public interface LeafListener {
		public void leafDoubleClicked(final File file);
	}

	final Logger log;

	private ArrayList<LeafListener> leaf_listeners = new ArrayList<>();

	private final DirectoryWatcher dir_watcher = new DirectoryWatcher();

	private final HashSet<String> ignored_extensions = new HashSet<>();
	private Pattern re_ignored_extensions = Pattern.compile("^.*$", Pattern.CASE_INSENSITIVE); // match all
	private FileFilter file_filter = ((f) -> true);

	public FileSystemTree(final Logger log)
	{
		this.log = log;
		setModel(new DefaultTreeModel(new Node("#root#")));
		setRootVisible(false);
		getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		setAutoscrolls(true);
		setScrollsOnExpand(true);
		setExpandsSelectedPaths(true);
		new FileDrop(this, files -> {
			final List<File> dirs = Arrays.asList(files).stream().filter(f -> f.isDirectory())
					.collect(Collectors.toList());
			if (dirs.isEmpty()) {
				JOptionPane.showMessageDialog(FileSystemTree.this, "Only folders can be dropped into the file tree.",
						"Invalid Drop", JOptionPane.WARNING_MESSAGE);
				return;
			}
			final boolean confirm = dirs.size() < 4 || (JOptionPane.showConfirmDialog(FileSystemTree.this,
					"Confirm loading of " + dirs.size() + " folders?", "Confirm?",
					JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION);
			if (confirm) {
				dirs.forEach(dir -> addRootDirectory(dir.getAbsolutePath(), true));
			}
		});
		addTreeWillExpandListener(new TreeWillExpandListener() {
			@Override
			public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
				final Node node = ((Node)event.getPath().getLastPathComponent());
				node.populateChildren(getModel(), file_filter);
				dir_watcher.register(node);
			}

			@Override
			public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {
				final Node node = ((Node)event.getPath().getLastPathComponent());
				node.removeAllChildren(getModel());
				dir_watcher.unregister(node);
			}
		});
		addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(final MouseEvent me) {
				final TreePath path = getPathForLocation(me.getX(), me.getY());
				if (null == path) return;
				final Node node = (Node) path.getLastPathComponent();
				if (2 == me.getClickCount()) {
					if (node.isLeaf() && !node.isDirectory()) { // do not accept empty directories as leafs
						for (final LeafListener l : leaf_listeners) l.leafDoubleClicked(new File(node.path));
					}
				}
			}
		});
		addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(final KeyEvent ke) {
				if (KeyEvent.VK_DELETE == ke.getKeyCode()) {
					removeSelectionPaths(getSelectionPaths());
				}
			}
		});
		setCellRenderer(new DefaultTreeCellRenderer() {
			@Override
			public Component getTreeCellRendererComponent(final JTree tree, final Object value, final boolean selected,
				final boolean expanded, final boolean leaf, final int row, final boolean hasFocus) {
				super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
				final Node node = (Node) value;
				setText(new File(node.path).getName());
				if (node.isDirectory()) {
					setToolTipText(node.path);
					if (null != node.icon) setIcon(node.icon);
					else if (expanded) setIcon(openIcon);
					else setIcon(closedIcon);
				} else {
					setIcon(leafIcon);
				}
				return this;
			}
		});
		setVisible(true);
	}

	/**
	 * Add a file name extension to ignore, without the dot, for example "class".
	 * Files whose name have this extension will not be listed as leaves in the tree.
	 * 
	 * @param extension The file name extension to ignore, e.g. "class".
	 * @return true if the extension wasn't there already.
	 */
	public boolean ignoreExtension(final String extension) {
		if (this.ignored_extensions.add(extension)) {
			updateIgnoreExtensionPattern();
			return true;
		}
		return false;
	}

	/** Opposite of {@link #ignoreExtension(String)}.
	 * 
	 * @param extension The file name extension to stop ignoring, e.g. "class".
	 * @return true if the extension wasn't there already.
	 */
	public boolean showExtension(final String extension) {
		if (this.ignored_extensions.remove(extension)) {
			updateIgnoreExtensionPattern();
			return true;
		}
		return false;
	}

	private void updateIgnoreExtensionPattern() {
		if (this.ignored_extensions.isEmpty()) {
			this.re_ignored_extensions = Pattern.compile("^.*$", Pattern.CASE_INSENSITIVE);
		} else {
			StringBuilder s = new StringBuilder("^.*\\.(");
			s.append(String.join("|", this.ignored_extensions.toArray(new String[this.ignored_extensions.size()])));
			s.append(")$");
			this.re_ignored_extensions = Pattern.compile(s.toString(), Pattern.CASE_INSENSITIVE);
		}
	}
	
	public void setFileFilter(final FileFilter file_filter) {
		this.file_filter = file_filter;
		updateRecursively(file_filter);
	}
	
	private void updateRecursively(final FileFilter file_filter) {
		// Find expanded directories
		final ArrayList<Node> stack = new ArrayList<>();
		final Node root = (Node) this.getModel().getRoot();
		for (int i=root.getChildCount() -1; i>-1; --i) {
			stack.add((Node)root.getChildAt(i));
		}
		final ArrayList<Node> stack2 = new ArrayList<>(stack); // copy for second phase
		final HashSet<String> expanded = new HashSet<>();
		while (!stack.isEmpty()) {
			final Node node = stack.remove(0);
			if (this.isExpanded(new TreePath(node.getPath()))) {
				expanded.add(node.path);
				for (int i=node.getChildCount() -1; i>-1; --i) {
					final Node child = node.getChildAt(i);
					if (child.isDirectory()) stack.add(child);
				}
			}
		}
		// Re-list all files, filtering
		while (!stack2.isEmpty()) {
			final Node node = stack2.remove(0);
			if (expanded.contains(node.path)) {
				node.removeAllChildren(this.getModel());
				node.populateChildren(this.getModel(), file_filter);
				final int count = node.getChildCount();
				if (count > 0) expandPath(new TreePath(node.getChildAt(0).getPath())); // awkward way to ensure parent is expanded
				for (int i=0; i<count; ++i) {
					final Node child = node.getChildAt(i);
					if (expanded.contains(child.path)) stack2.add(child);
				}
			}
		}
	}

	synchronized public void addLeafListener(final LeafListener l) {
		this.leaf_listeners.add(l);
	}

	synchronized public void removeLeafListener(final LeafListener l) {
		this.leaf_listeners.remove(l);
	}

	synchronized public ArrayList<LeafListener> getLeafListeners() {
		return new ArrayList<>(this.leaf_listeners);
	}

	/**
	 * Add a directory as a top-level root.
	 * 
	 * @param dir The directory to add as a top-level root
	 * @param checkIfChild If true, and the {@code dir} is a child of an existing root,
	 *                     then merely expand it and don't add it as a root.
	 */
	public void addRootDirectory(final String dir, final boolean checkIfChild) {
		final File file = new File(dir);
		if (!file.isDirectory()) return;
		final String dirPath = withSlash(file.getAbsolutePath());
		final Node root = (Node) getModel().getRoot();

		if (checkIfChild) {
			// If dir is a subdirectory of an existing root, expand recursively to show it
			for (int i=0; i<root.getChildCount(); ++i) {
				final Node node = root.getChildAt(i);
				if (dirPath.startsWith(node.path)) {
					final TreePath[] p = new TreePath[1];
					node.expandTo(dirPath, p);
					if (null != p[0]) {
						//getModel().reload(); // this will collapse all nodes
						expandPath(p[0]);
						setSelectionPath(p[0]);
						scrollPathToVisible(p[0]); //spurious!?
						return;
					}
				}
			}
		}
		// Else, append it as a new root
		getModel().insertNodeInto(new Node(dirPath), root, root.getChildCount());
		//getModel().reload(); // this will collapse all nodes
		getModel().nodesWereInserted(root, new int[] { root.getChildCount() - 1 });
	}

	@Override
	public DefaultTreeModel getModel() {
		return (DefaultTreeModel) super.getModel();
	}

	public void updateUILater() {
		SwingUtilities.invokeLater(() -> FileSystemTree.this.updateUI());
	}

	/**
	 * For persistence.
	 * @return A String with the absolute file paths of all top-level folders
	 *         concatenated with a ':' character as separator.
	 */
	public String getTopLevelFoldersString() {
		final Node root = (Node) getModel().getRoot();
		if (0 == root.getChildCount()) return "";
		final StringBuilder sb = new StringBuilder(root.getChildAt(0).path);
		for (int i=1, l=root.getChildCount(); i<l; ++i) {
			sb.append(':').append(root.getChildAt(i).path);
		}
		return sb.toString();
	}

	/**
	 * For restoring state. Only those paths that are directories and exist will be added.
	 * @param folders A String generated from {@code FileSystemTree#getTopLevelFoldersString()}.
	 */
	public void addTopLevelFoldersFrom(final String folders) {
		for (final String path : folders.split(":")) {
			final File file = new File(path);
			if (file.exists() && file.isDirectory()) {
				addRootDirectory(file.getAbsolutePath(), false);
			}
		}
	}

	public void destroy() {
		dir_watcher.interrupt();
		FileDrop.remove(this);
	}

	private class DirectoryWatcher extends Thread {

		private WatchService watcher;
		private final HashMap<WatchKey, Path> keys = new HashMap<>();
		private final HashMap<Path, Node> map = new HashMap<>();

		DirectoryWatcher() {
			try {
				this.watcher = FileSystems.getDefault().newWatchService();
				this.start();
			} catch (IOException e) {
				log.error("Failed to start filesystem watching.", e);
			}
		}

		void register(final Node node) {
			if (null == watcher) {
				log.error("Filesystem watching is not running.");
				return;
			}
			synchronized (keys) {
				try {
					final Path path = new File(node.path).toPath();
					final WatchKey key = path.register(watcher,
						StandardWatchEventKinds.ENTRY_CREATE,
						StandardWatchEventKinds.ENTRY_MODIFY,
						StandardWatchEventKinds.ENTRY_DELETE);
					keys.put(key, path);
					map.put(path, node);
				} catch (IOException e) {
					log.error(e);
				}
			}
		}

		void unregister(final Node node) {
			synchronized (keys) {
				final Iterator<Map.Entry<Path, Node>> it = map.entrySet().iterator();
				Path path = null;
				while (it.hasNext()) {
					final Map.Entry<Path, Node> e = it.next();
					if ( e.getValue() == node ) {
						path = e.getKey();
						it.remove();
						break;
					}
				}
				if (null == path) return;
				final Iterator<Path> itp = keys.values().iterator();
				while (itp.hasNext()) {
					if (itp.next().equals(path)) {
						itp.remove();
						return;
					}
				}
			}
		}

		@Override
		public void run() {
			while (true) {
				if (isInterrupted()) return;
				WatchKey key;
				try {
					key = watcher.take();
				} catch (InterruptedException x) {
					return;
				}

				final Path dir = keys.get(key);
				if (null == dir) {
					log.error("Unrecognized WatchKey: " + key);
					continue;
				}

				final HashSet<Node> nodes = new HashSet<>();

				for (final WatchEvent<?> event: key.pollEvents()) {
					final WatchEvent.Kind<?> kind = event.kind();
					if (StandardWatchEventKinds.OVERFLOW == kind
					 || StandardWatchEventKinds.ENTRY_MODIFY == kind) { // ignore e.g. files getting larger or smaller
						continue;
					}

					@SuppressWarnings("unchecked")
					final Path child = dir.resolve(((WatchEvent<Path>) event).context());
					final Node node = map.get(child.getParent());
					if (null != node) nodes.add(node);
				}

				SwingUtilities.invokeLater(() -> {
					for (final Node node : nodes) {
						node.updateChildrenList(FileSystemTree.this.getModel(), file_filter);
						FileSystemTree.this.expandPath(new TreePath(node.getPath()));
					}
				});

				boolean valid = key.reset();
				if (!valid) {
					final Path path = keys.remove(key);
					if (null != path) map.remove(path);
				}
			}
		}
	}
}
