package org.scijava.ui.swing.script;

import java.awt.Component;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;

import javax.swing.Icon;
import javax.swing.ImageIcon;
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

/**
 * 
 * @author Albert Cardona
 *
 */
@SuppressWarnings("serial")
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
	
	static public class Node extends DefaultMutableTreeNode
	{
		final private String path;
		private Icon icon = null;
		
		public Node(final String path) {
			this.path = withSlash(path);
		}
		
		public boolean isDirectory() {
			return new File(this.path).isDirectory();
		}
		
		public File[] updatedChildrenFiles(final boolean sort) {
			final File file = new File(this.path);
			if (!file.isDirectory()) {
				return new File[0];
			}
			final File[] files = file.listFiles(new FileFilter() {
				@Override
				public boolean accept(final File file) {
					return !file.isHidden() && !file.getName().endsWith("~");
				}
			});
			if (sort) Arrays.sort(files);
			return files;
		}
		
		/**
		 * If it's a directory, add a Node for each of its visible files.
		 */
		public void populateChildren(final DefaultTreeModel model) {
			try {
				if (isLeaf()) return;
				int index = 0;
				for (final File file : updatedChildrenFiles(true)) {
					// Can't use add: would try to insert at getChildCount(), which is the wrong value here
					model.insertNodeInto(new Node(file.getAbsolutePath()), this, index++);
				}
				icon = null;
			} catch (Throwable t) {
				icon = ICON_ERROR;
				System.out.println("Failed to populate folder " + this.path);
				t.printStackTrace();
			}
		}
		
		/**
		 * Recursive expansion until opening the final element of the path
		 * @param path The absolute file path.
		 * @param full A single-slot array containing the full path, if any.
		 */
		public void expandTo(final String path, final TreePath[] full) {
			if (path.startsWith(this.path)) {
				final LinkedList<Node> stack = new LinkedList<Node>();
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
		public int getChildCount() {
			if (isRoot()) return super.getChildCount();
			return new File(this.path).isDirectory() ? updatedChildrenFiles(false).length : 0;
		}
		
		@Override
		public Node getChildAt(final int index) {
			if (0 == getChildCount()) return null;
			return (Node) super.getChildAt(index);
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
		
		public void removeAllChildren(final DefaultTreeModel model) {
			for (int i=super.getChildCount() -1; i>-1; --i) {
				// Can't use DefaultMutableTreeNode.removeAllChildren or .remove(int): the model is not notified
				model.removeNodeFromParent((Node)super.getChildAt(i));
			}
		}
	}
	
	public interface LeafListener {
		public void leafDoubleClicked(final File file);
	}
	
	private ArrayList<LeafListener> leaf_listeners = new ArrayList<>();

	static public ArrayList<FileSystemTree> trees = new ArrayList<>();
	
	public FileSystemTree()
	{
		trees.add(this);
		setModel(new DefaultTreeModel(new Node("#root#")));
		setRootVisible(true);
		getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		setAutoscrolls(true);
		setScrollsOnExpand(true);
		addTreeWillExpandListener(new TreeWillExpandListener() {			
			@Override
			public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
				final Node node = ((Node)event.getPath().getLastPathComponent());
				node.populateChildren(getModel());
			}
			
			@Override
			public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {
				((Node)event.getPath().getLastPathComponent()).removeAllChildren(getModel());
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
	
	synchronized public void addLeafListener(final LeafListener l) {
		this.leaf_listeners.add(l);
	}
	
	synchronized public void removeLeafListener(final LeafListener l) {
		this.leaf_listeners.remove(l);
	}
	
	synchronized public ArrayList<LeafListener> getLeafListeners() {
		return new ArrayList<LeafListener>(this.leaf_listeners);
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
				final Node node = (Node) root.getChildAt(i);
				if (dirPath.startsWith(node.path)) {
					final TreePath[] p = new TreePath[1];
					node.expandTo(dirPath, p);
					if (null != p[0]) {
						getModel().reload();
						expandPath(p[0]);
						scrollPathToVisible(p[0]);
						return;
					}
				}
			}
		}
		// Else, append it as a new root
		getModel().insertNodeInto(new Node(dirPath), root, root.getChildCount());
		getModel().reload();
	}
	
	@Override
	public DefaultTreeModel getModel() {
		return (DefaultTreeModel) super.getModel();
	}
	
	public void updateUILater() {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				FileSystemTree.this.updateUI();
			}
		});
	}
	
	/**
	 * For persistence.
	 * @return A String with the absolute file paths of all top-level folders
	 *         concatenated with a ':' character as separator.
	 */
	public String getTopLevelFoldersString() {
		final Node root = (Node) getModel().getRoot();
		if (0 == root.getChildCount()) return "";
		final StringBuilder sb = new StringBuilder(((Node)root.getChildAt(0)).path);
		for (int i=1, l=root.getChildCount(); i<l; ++i) {
			sb.append(':').append(((Node)root.getChildAt(i)).path);
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
}
