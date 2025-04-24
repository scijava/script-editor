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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

import org.scijava.util.AppUtils;
import org.scijava.util.FileUtils;
import org.scijava.util.LineOutputStream;
import org.scijava.util.Manifest;
import org.scijava.util.POM;
import org.scijava.util.ProcessUtils;
import org.scijava.util.Types;

/**
 * TODO
 *
 * @author Johannes Schindelin
 */
public class FileFunctions {

	protected TextEditor parent;

	public FileFunctions(final TextEditor parent) {
		this.parent = parent;
	}

	public List<String> extractSourceJar(final String path, final File workspace)
		throws IOException
	{
		String baseName = new File(path).getName();
		if (baseName.endsWith(".jar") || baseName.endsWith(".zip")) baseName =
			baseName.substring(0, baseName.length() - 4);
		final File baseDirectory = new File(workspace, baseName);

		final List<String> result = new ArrayList<>();
		try (JarFile jar = new JarFile(path)) {
			for (final JarEntry entry : Collections.list(jar.entries())) {
				final String name = entry.getName();
				if (name.endsWith(".class") || name.endsWith("/")) continue;
				final String destination = baseDirectory + name;
				copyTo(jar.getInputStream(entry), destination);
				result.add(destination);
			}
		}
		return result;
	}

	protected void copyTo(final InputStream in, final String destination)
		throws IOException
	{
		final File file = new File(destination);
		makeParentDirectories(file);
		copyTo(in, new FileOutputStream(file));
	}

	protected void copyTo(final InputStream in, final OutputStream out)
		throws IOException
	{
		final byte[] buffer = new byte[16384];
		for (;;) {
			final int count = in.read(buffer);
			if (count < 0) break;
			out.write(buffer, 0, count);
		}
		in.close();
		out.close();
	}

	protected void makeParentDirectories(final File file) {
		final File parent = file.getParentFile();
		if (!parent.exists()) {
			makeParentDirectories(parent);
			parent.mkdir();
		}
	}

	/*
	 * This just checks for a NUL in the first 1024 bytes.
	 * Not the best test, but a pragmatic one.
	 */
	public boolean isBinaryFile(final String path) {
		try {
			final InputStream in = new FileInputStream(path);
			final byte[] buffer = new byte[1024];
			int offset = 0;
			while (offset < buffer.length) {
				final int count = in.read(buffer, offset, buffer.length - offset);
				if (count < 0) break;
				offset += count;
			}
			in.close();
			while (offset > 0)
				if (buffer[--offset] == 0) return true;
		}
		catch (final IOException e) {}
		return false;
	}

	/**
	 * Gets the source path.
	 *
	 * @param className the class name
	 * @return the source path
	 * @throws ClassNotFoundException
	 * @deprecated Use {@link #getSourceURL(String)} instead.
	 */
	@Deprecated
	public String getSourcePath(
		@SuppressWarnings("unused") final String className)
		throws ClassNotFoundException
	{
		// move updater's stuff into ij-core and re-use here
		throw new RuntimeException("TODO");
	}

	public String getSourceURL(final String className) {
		final Class<?> c = Types.load(className, false);
		final POM pom = POM.getPOM(c);
		final String scmPath = pom.getSCMURL();
		if (scmPath == null || scmPath.isEmpty()) return null;
		final String branch;
		final String scmTag = pom.getSCMTag();
		if (scmTag == null || scmTag.isEmpty() || Objects.equals(scmTag, "HEAD")) {
			final Manifest m = Manifest.getManifest(c);
			final String commit = m == null ? null : m.getImplementationBuild();
			branch = commit == null || commit.isEmpty() ? "master" : commit;
		}
		else branch = scmTag;

		final String prefix = scmPath.endsWith("/") ? scmPath : scmPath + "/";
		return prefix + "blob/" + branch + "/src/main/java/" + //
			className.replace('.', '/') + ".java";
	}

	protected static Map<String, List<String>> class2source;

	public String findSourcePath(final String className, final File workspace) {
		if (class2source == null) {
			if (JOptionPane.showConfirmDialog(parent, "The class " + className +
				" was not found " + "in the CLASSPATH. Do you want me to search " +
				"for the source?", "Question", JOptionPane.YES_OPTION) != JOptionPane.YES_OPTION) return null;
			class2source = new HashMap<>();
			findJavaPaths(workspace, "");
		}
		final int dot = className.lastIndexOf('.');
		final String baseName = className.substring(dot + 1);
		List<String> paths = class2source.get(baseName);
		if (paths == null || paths.size() == 0) {
			JOptionPane.showMessageDialog(parent, "No source for class '" +
				className + "' was not found!");
			return null;
		}
		if (dot >= 0) {
			final String suffix = "/" + className.replace('.', '/') + ".java";
			paths = new ArrayList<>(paths);
			final Iterator<String> iter = paths.iterator();
			while (iter.hasNext())
				if (!iter.next().endsWith(suffix)) iter.remove();
			if (paths.size() == 0) {
				JOptionPane.showMessageDialog(parent, "No source for class '" +
					className + "' was not found!");
				return null;
			}
		}
		if (paths.size() == 1) return new File(workspace, paths.get(0))
			.getAbsolutePath();
		//final String[] names = paths.toArray(new String[paths.size()]);
		final JFileChooser chooser = new JFileChooser(workspace);
		chooser.setDialogTitle("Choose path");
		if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return null;
		return chooser.getSelectedFile().getPath();
	}

	protected void findJavaPaths(final File directory, final String prefix) {
		final String[] files = directory.list();
		if (files == null) return;
		Arrays.sort(files);
		for (int i = 0; i < files.length; i++)
			if (files[i].endsWith(".java")) {
				final String baseName = files[i].substring(0, files[i].length() - 5);
				List<String> list = class2source.get(baseName);
				if (list == null) {
					list = new ArrayList<>();
					class2source.put(baseName, list);
				}
				list.add(prefix + "/" + files[i]);
			}
			else if ("".equals(prefix) &&
				(files[i].equals("full-nightly-build") || files[i].equals("livecd") ||
					files[i].equals("java") || files[i].equals("nightly-build") ||
					files[i].equals("other") || files[i].equals("work") || files[i]
						.startsWith("chroot-")))
			// skip known non-source directories
			continue;
			else {
				final File file = new File(directory, files[i]);
				if (file.isDirectory()) findJavaPaths(file, prefix + "/" + files[i]);
			}
	}

	protected String readStream(final InputStream in) throws IOException {
		final StringBuffer buf = new StringBuffer();
		final byte[] buffer = new byte[65536];
		for (;;) {
			final int count = in.read(buffer);
			if (count < 0) break;
			buf.append(new String(buffer, 0, count));
		}
		in.close();
		return buf.toString();
	}

	/**
	 * Get a list of files from a directory (recursively).
	 *
	 * @param directory the directory to be parsed
	 * @param prefix a prefix to prepend to filenames
	 * @param result List holding filenames
	 */
	public void listFilesRecursively(final File directory, final String prefix,
		final List<String> result)
	{
		if (!directory.exists()) return;
		for (final File file : directory.listFiles())
			if (file.isDirectory()) listFilesRecursively(file, prefix +
				file.getName() + "/", result);
			else if (file.isFile()) result.add(prefix + file.getName());
	}

	/**
	 * Get a list of files from a directory or within a .jar file The returned items
	 * will only have the base path, to get at the full URL you have to prefix the
	 * url passed to the function.
	 *
	 * @param url the string specifying the resource
	 * @return the list of files
	 */
	public List<String> getResourceList(String url) {
		final List<String> result = new ArrayList<>();

		if (url.startsWith("jar:")) {
			final int bang = url.indexOf("!/");
			String jarURL = url.substring(4, bang);
			if (jarURL.startsWith("file:")) jarURL = jarURL.substring(5);
			final String prefix = url.substring(bang + 2);
			final int prefixLength = prefix.length();

			try (JarFile jar = new JarFile(jarURL)) {
				final Enumeration<JarEntry> e = jar.entries();
				while (e.hasMoreElements()) {
					final JarEntry entry = e.nextElement();
					if (entry.getName().startsWith(prefix)) result.add(entry.getName()
						.substring(prefixLength));
				}
			}
			catch (final IOException e) {
				parent.handleException(e);
			}
		}
		else {
			final String prefix = "file:";
			if (url.startsWith(prefix)) {
				int skip = prefix.length();
				if (url.startsWith(prefix + "//")) skip++;
				url = url.substring(skip);
			}
			listFilesRecursively(new File(url), "", result);
		}
		return result;
	}

	public File getGitDirectory(File file) {
		if (file == null) return null;
		for (;;) {
			file = file.getParentFile();
			if (file == null) return null;
			final File git = new File(file, ".git");
			if (git.isDirectory()) return git;
		}
	}

	public File getPluginRootDirectory(File file) {
		if (file == null) return null;
		if (!file.isDirectory()) file = file.getParentFile();
		if (file == null) return null;

		File git = new File(file, ".git");
		if (git.isDirectory()) return file;

		File backup = file;
		for (;;) {
			final File parent = file.getParentFile();
			if (parent == null) return null;
			git = new File(parent, ".git");
			if (git.isDirectory()) return file.getName().equals("src-plugins")
				? backup : file;
			backup = file;
			file = parent;
		}
	}

	public String firstNLines(final String text, int maxLineCount) {
		int offset = -1;
		while (maxLineCount-- > 0) {
			offset = text.indexOf('\n', offset + 1);
			if (offset < 0) return text;
		}
		int count = 0, next = offset;
		while ((next = text.indexOf('\n', next + 1)) > 0)
			count++;
		return count == 0 ? text : text.substring(0, offset + 1) + "(" + count +
			" more line" + (count > 1 ? "s" : "") + ")...\n";
	}

	public class LengthWarner implements DocumentListener {

		protected int width;
		protected JTextComponent component;
		protected Color normal, warn;

		public LengthWarner(final int width, final JTextComponent component) {
			this.width = width;
			this.component = component;
			normal = component.getForeground();
			warn = Color.red;
		}

		@Override
		public void changedUpdate(final DocumentEvent e) {}

		@Override
		public void insertUpdate(final DocumentEvent e) {
			updateColor();
		}

		@Override
		public void removeUpdate(final DocumentEvent e) {
			updateColor();
		}

		public void updateColor() {
			component.setForeground(component.getDocument().getLength() <= width
				? normal : warn);
		}
	}

	public class TextWrapper implements DocumentListener {

		protected int width;

		public TextWrapper(final int width) {
			this.width = width;
		}

		@Override
		public void changedUpdate(final DocumentEvent e) {}

		@Override
		public void insertUpdate(final DocumentEvent e) {
			final Document document = e.getDocument();
			final int offset = e.getOffset() + e.getLength();
			if (offset <= width) return;
			try {
				final String text = document.getText(0, offset);
				int newLine = text.lastIndexOf('\n');
				if (offset - newLine <= width) return;
				while (offset - newLine > width) {
					int remove = 0;
					final int space = text.lastIndexOf(' ', newLine + width);
					if (space < newLine) break;
					if (space > 0) {
						int first = space;
						while (first > newLine + 1 && text.charAt(first - 1) == ' ')
							first--;
						remove = space + 1 - first;
						newLine = first;
					}
					else newLine += width;

					final int removeCount = remove, at = newLine;
					SwingUtilities.invokeLater(() -> {
						try {
							if (removeCount > 0) document.remove(at, removeCount);
							document.insertString(at, "\n", null);
						}
						catch (final BadLocationException e2) { /* ignore */}
					});
				}
			}
			catch (final BadLocationException e2) { /* ignore */}
		}

		@Override
		public void removeUpdate(final DocumentEvent e) {}
	}

	public class ScreenOutputStream extends LineOutputStream {

		@Override
		public void println(final String line) {
			final TextEditorTab tab = parent.getTab();
			tab.screen.insert(line + "\n", tab.screen.getDocument().getLength());
		}
	}

	public static class GrepLineHandler extends LineOutputStream {

		protected static Pattern pattern = Pattern.compile(
			"([A-Za-z]:[^:]*|[^:]+):([1-9][0-9]*):.*", Pattern.DOTALL);

		public ErrorHandler errorHandler;
		protected String directory;

		public GrepLineHandler(final JTextArea textArea, String directory) {
			errorHandler = new ErrorHandler(textArea);
			if (!directory.endsWith("/")) directory += "/";
			this.directory = directory;
		}

		@Override
		public void println(final String line) {
			final Matcher matcher = pattern.matcher(line);
			if (matcher.matches()) errorHandler.addError(
				directory + matcher.group(1), Integer.parseInt(matcher.group(2)), line);
			else errorHandler.addError(null, -1, line);
		}
	}

	public void gitGrep(final String searchTerm, final File directory) {
		final GrepLineHandler handler =
			new GrepLineHandler(parent.getErrorScreen(), directory.getAbsolutePath());
		final PrintStream out = new PrintStream(handler);
		parent.getTab().showErrors();
		try {
			ProcessUtils.exec(directory, out, out, "git", "grep", "-n", searchTerm);
			parent.setErrorHandler(handler.errorHandler);
		}
		catch (final RuntimeException e) {
			parent.handleException(e);
		}
	}

	public void openInGitweb(final File file, final File gitDirectory,
		final int line)
	{
		if (file == null || gitDirectory == null) {
			parent.error("No file or git directory.");
			return;
		}
		final String url = getGitwebURL(file, gitDirectory, line);
		if (url == null) parent.error("Could not get gitweb URL for " + file + ".");
		else try {
			parent.getPlatformService().open(new URL(url));
		}
		catch (final MalformedURLException e) {
			parent.handleException(e);
		}
		catch (final IOException e) {
			parent.handleException(e);
		}
	}

	public String git(final File gitDirectory, final File workingDirectory,
		String... args)
	{
		try {
			args =
				append(gitDirectory == null ? new String[] { "git" } : new String[] {
					"git", "--git-dir=" + gitDirectory.getAbsolutePath() }, args);
			final PrintStream out = new PrintStream(new ScreenOutputStream());
			return ProcessUtils.exec(workingDirectory, out, out, args);
		}
		catch (final RuntimeException e) {
			parent.write(e.getMessage());
		}
		return null;
	}

	public String git(final File gitDirectory, final String... args) {
		return git(gitDirectory, (File) null, args);
	}

	public String gitConfig(final File gitDirectory, final String key) {
		return git(gitDirectory, "config", key);
	}

	public String getGitwebURL(final File file, final File gitDirectory,
		final int line)
	{
		String url = gitConfig(gitDirectory, "remote.origin.url");
		if (url == null) {
			final String remote = gitConfig(gitDirectory, "branch.master.remote");
			if (remote != null) url =
				gitConfig(gitDirectory, "remote." + remote + ".url");
			if (url == null) return null;
		}
		if (url.startsWith("repo.or.cz:") || url.startsWith("ssh://repo.or.cz/")) {
			final int index = url.indexOf("/srv/git/") + "/srv/git/".length();
			url = "http://repo.or.cz/w/" + url.substring(index);
		}
		else if (url.startsWith("git://repo.or.cz/")) url =
			"http://repo.or.cz/w/" + url.substring("git://repo.or.cz/".length());
		else {
			url = stripSuffix(url, "/");
			int slash = url.lastIndexOf('/');
			if (url.endsWith("/.git")) slash = url.lastIndexOf('/', slash - 1);
			String project = url.substring(slash + 1);
			if (!project.endsWith(".git")) project += "/.git";
			if (project.equals("imageja.git")) project = "ImageJA.git";
			url = "http://fiji.sc/cgi-bin/gitweb.cgi?p=" + project;
		}
		final String head =
			git(gitDirectory, "rev-parse", "--symbolic-full-name", "HEAD");
		final String path =
			git(null /* ls-files does not work with --git-dir */, file
				.getParentFile(), "ls-files", "--full-name", file.getName());
		if (url == null || head == null || path == null) return null;
		return url + ";a=blob;f=" + path + ";hb=" + head +
			(line < 0 ? "" : "#l" + line);
	}

	protected String[] append(final String[] array, final String item) {
		final String[] result = new String[array.length + 1];
		System.arraycopy(array, 0, result, 0, array.length);
		result[array.length] = item;
		return result;
	}

	protected String[] append(final String[] array, final String[] append) {
		final String[] result = new String[array.length + append.length];
		System.arraycopy(array, 0, result, 0, array.length);
		System.arraycopy(append, 0, result, array.length, append.length);
		return result;
	}

	protected String stripSuffix(final String string, final String suffix) {
		if (string.endsWith(suffix)) return string.substring(0, string.length() -
			suffix.length());
		return string;
	}

	/**
	 * @deprecated Use {@link FileUtils#findResources(String, String, File)}
	 *             instead.
	 */
	@Deprecated
	public static Map<String, URL> findResources(final String regex,
		final String pathPrefix)
	{
		final File baseDirectory =
			AppUtils.getBaseDirectory("imagej.dir", FileFunctions.class, null);
		return FileUtils.findResources(regex, pathPrefix, baseDirectory);
	}

	/**
	 * @deprecated Use {@link FileUtils#findResources(String, Iterable)} instead.
	 */
	@Deprecated
	public static Map<String, URL> findResources(final String regex,
		final Iterable<URL> urls)
	{
		return FileUtils.findResources(regex, urls);
	}

}
