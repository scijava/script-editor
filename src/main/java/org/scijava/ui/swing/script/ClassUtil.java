package org.scijava.ui.swing.script;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ClassUtil {
	
	static private final String scijava_javadoc_URL = "https://javadoc.scijava.org/"; // with ending slash
	
	/** Cache of class names vs list of URLs found in the pom.xml files of their contaning jar files, if any. */
	static private final Map<String, JarProperties> class_urls = new HashMap<>();
	
	static private final Map<String, JarProperties> package_urls = new HashMap<>();
	
	static private boolean ready = false;
	
	/** Cache of subURL javadoc at https://javadoc.scijava.org */
	static private final HashMap<String, String> scijava_javadoc_URLs = new HashMap<>();
	
	static public final void ensureCache() {
		synchronized (class_urls) {
			if (class_urls.isEmpty()) {
				final ArrayList<String> dirs = new ArrayList<>();
				dirs.add(System.getProperty("java.home"));
				dirs.add(System.getProperty("ij.dir"));
				class_urls.putAll(findAllClasses(dirs));
				// Soft attempt at getting all packages (will get them wrong if multiple jars have the same packages)
				for (final Map.Entry<String, JarProperties> entry: class_urls.entrySet()) {
					final int idot = entry.getKey().lastIndexOf('.');
					if (-1 == idot) continue; // no package
					final String package_name = entry.getKey().substring(0, idot);
					if (package_urls.containsKey(package_name)) continue;
					package_urls.put(package_name, entry.getValue());
				}
				ready = true;
			}
		}
	}
	
	static public final boolean isCacheReady() {
		return ready;
	}
	
	static public final void ensureSciJavaSubURLCache() {
		synchronized (scijava_javadoc_URLs) {
			if (!scijava_javadoc_URLs.isEmpty()) return;
			Scanner scanner = null;
			try {
				final Pattern pattern = Pattern.compile("<div class=\"jdbox\"><div><a href=\"(.*?)\">");
				final URLConnection connection =  new URL(scijava_javadoc_URL).openConnection();
				scanner = new Scanner(connection.getInputStream());
				while (scanner.hasNext()) {
					final Matcher matcher = pattern.matcher(scanner.nextLine());
					if (matcher.find()) {
						String name = matcher.group(1).toLowerCase();
						if (name.endsWith("/")) name = name.substring(0, name.length() -1);
						scijava_javadoc_URLs.put(name, scijava_javadoc_URL + matcher.group(1));
					}
				}
				scanner.close();
			} catch ( Exception e ) {
				e.printStackTrace();
			} finally {
				if (null != scanner) scanner.close();
			}
		}
	}
	
	static public HashMap<String, JarProperties> findClassDocumentationURLs(final String s) {
		ensureCache();
		final HashMap<String, JarProperties> matches = new HashMap<>();
		for (final Map.Entry<String, JarProperties> entry: class_urls.entrySet()) {
			if (entry.getKey().contains(s)) {
				final JarProperties props = entry.getValue();
				matches.put(entry.getKey(), new JarProperties(props.name, new ArrayList<String>(props.urls)));
			}
		}
		return matches;
	}
	
	static public HashMap<String, ArrayList<String>> findDocumentationForClass(final String s) {
		final HashMap<String, JarProperties> matches = findClassDocumentationURLs(s);
		ensureSciJavaSubURLCache();
		
		final Pattern javaPackages = Pattern.compile("^(java|javax|org\\.omg|org\\.w3c|org\\.xml|org\\.ietf\\.jgss)\\..*$");
		final String version = System.getProperty("java.version");
		final String majorVersion = version.startsWith("1.") ?
				  version.substring(2, version.indexOf('.', 2))
				: version.substring(0, version.indexOf('.'));
		final String javaDoc = "java" + majorVersion;
	
		final HashMap<String, ArrayList<String>> class_urls = new HashMap<>();
		
		for (final Map.Entry<String, JarProperties> entry: matches.entrySet()) {
			final String classname = entry.getKey();
			final ArrayList<String> urls = new ArrayList<>();
			class_urls.put(classname, urls);
			if (javaPackages.matcher(classname).matches()) {
				urls.add(scijava_javadoc_URLs.get(javaDoc) + classname.replace('.', '/') + ".html");
			} else {
				final JarProperties props = entry.getValue();
				// Find the first URL with git in it
				for (final String url : props.urls) {
					final boolean github = url.contains("/github.com"),
								  gitlab = url.contains("/gitlab.com");
					if (github || gitlab) {
						// Find the 5th slash, e.g. https://github.com/imglib/imglib2/
						int count = 0;
						int last = 0;
						while (count < 5) {
							last = url.indexOf('/', last + 1);
							if (-1 == last) break; // less than 5 found
							++count;
						}
						String urlbase = url;
						if (5 == count) urlbase = url.substring(0, last); // without the ending slash
						// Assume maven, since these URLs were found in a pom.xml: src/main/java/
						urls.add(urlbase + (gitlab ? "/-" : "") + "/blob/master/src/main/java/" + classname.replace('.', '/') + ".java");
						break;
					}
				}
				// Try to find a javadoc in the scijava website
				if (null != props.name) {
					String scijava_javadoc_url = scijava_javadoc_URLs.get(props.name.toLowerCase());
					if (null == scijava_javadoc_url) {
						// Try cropping name at the first whitespace if any (e.g. "ImgLib2 Core Library" to "ImgLib2")
						for (final String word: props.name.split(" ")) {
							scijava_javadoc_url = scijava_javadoc_URLs.get(word.toLowerCase());
							if (null != scijava_javadoc_url) break; // found a valid one
						}
					}
					if (null != scijava_javadoc_url) {
						urls.add(scijava_javadoc_url + classname.replace('.', '/') + ".html");
					} else {
						// Try Fiji: could be a plugin
						Scanner scanner = null;
						try {
							final String url = scijava_javadoc_URL + "Fiji/" + classname.replace('.', '/') + ".html";
							final URLConnection c = new URL(url).openConnection();
							scanner = new Scanner(c.getInputStream());
							while (scanner.hasNext()) {
								final String line = scanner.nextLine();
								if (line.contains("<title>")) {
									if (!line.contains("<title>404")) {
										urls.add(url);
									}
									break;
								}
							}
						} catch (Exception e) {
							// Ignore: 404 that wasn't redirected to an error page
						} finally {
							if (null != scanner) scanner.close();
						}
					}
				}
			}
		}

		return class_urls;
	}
	
	static public final class JarProperties {
		public final ArrayList<String> urls;
		public String name = null;
		public JarProperties(final String name, final ArrayList<String> urls) {
			this.name = name;
			this.urls = urls;
		}
	}
	
	static public final HashMap<String, JarProperties> findAllClasses(final List<String> jar_folders) {
		// Find all jar files
		final ArrayList<String> jarFilePaths = new ArrayList<String>();
		final LinkedList<String> dirs = new LinkedList<>(jar_folders);
		final HashSet<String> seenDirs = new HashSet<>();
		while (!dirs.isEmpty()) {
			final String filepath = dirs.removeFirst();
			if (null == filepath) continue;
			final File file = new File(filepath);
			seenDirs.add(file.getAbsolutePath());
			if (file.exists() && file.isDirectory()) {
				for (final File child : file.listFiles()) {
					final String childfilepath = child.getAbsolutePath();
					if (seenDirs.contains(childfilepath)) continue;
					if (child.isDirectory()) dirs.add(childfilepath);
					else if (childfilepath.endsWith(".jar")) jarFilePaths.add(childfilepath);
				}
			}
		}
		// Find all classes from all jar files
		final HashMap<String, JarProperties> class_urls = new HashMap<>();
		final Pattern urlpattern = Pattern.compile(">(http.*?)<");
		final Pattern namepattern = Pattern.compile("<name>(.*?)<");
		for (final String jarpath : jarFilePaths) {
			JarFile jar = null;
			try {
				jar = new JarFile(jarpath);
				final Enumeration<JarEntry> entries = jar.entries();
				final ArrayList<String> urls = new ArrayList<>();
				final JarProperties props = new JarProperties(null, urls);
				// For every filepath in the jar zip archive
				while (entries.hasMoreElements()) {
					final JarEntry entry = entries.nextElement();
					if (entry.isDirectory()) continue;
					if (entry.getName().endsWith(".class")) {
						String classname = entry.getName().replace('/', '.');
						final int idollar = classname.indexOf('$');
						if (-1 != idollar) {
							classname = classname.substring(0, idollar); // truncate at the first dollar sign
						} else {
							classname = classname.substring(0, classname.length() - 6); // without .class
						}
						class_urls.put(classname, props);
					} else if (entry.getName().endsWith("/pom.xml")) {
						final Scanner scanner = new Scanner(jar.getInputStream(entry));
						while (scanner.hasNext()) {
							final String line = scanner.nextLine();
							final Matcher matcher1 = urlpattern.matcher(line);
							if (matcher1.find()) {
								urls.add(matcher1.group(1));
							}
							if (null == props.name) {
								final Matcher matcher2 = namepattern.matcher(line);
								if (matcher2.find()) {
									props.name = matcher2.group(1);
								}
							}
						}
						scanner.close();
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (null != jar) try {
					jar.close();
				} catch (IOException e) { e.printStackTrace(); }
			}
		}
		return class_urls;
	}
	
	static public final Stream<String> findPackageNamesStartingWith(final String text) {
		ensureCache();
		return package_urls.keySet().stream().filter(s -> s.startsWith(text));
	}
	
	static public final Stream<String> findClassNamesForPackage(final String packageName) {
		ensureCache();
		if (packageName.length() == 0)
			return class_urls.keySet().stream();
		return class_urls.keySet().stream().filter(s -> s.startsWith(packageName) && -1 == s.indexOf('.', packageName.length() + 2));
	}
	
	/**
	 * 
	 * @param text A left-justified substring of a fully qualified class name, with the package.
	 * @return
	 */
	static public final Stream<String> findClassNamesStartingWith(final String text) {
		ensureCache();
		if (text.length() == 0)
			return class_urls.keySet().stream();
		return class_urls.keySet().stream().filter(s -> s.startsWith(text));
	}
	
	/**
	 * 
	 * @param text A substring of a class fully qualified name.
	 * @return
	 */
	static public final Stream<String> findClassNamesContaining(final String text) {
		ensureCache();
		return class_urls.keySet().stream().filter(s -> s.contains(text));
	}
	
	static public final ArrayList<String> findSimpleClassNamesStartingWith(final String text) {
		ensureCache();
		final ArrayList<String> matches = new ArrayList<>();
		if (0 == text.length())
			return matches;
		for (final String classname: class_urls.keySet()) {
			final int idot = classname.lastIndexOf('.');
			final String simplename = -1 == idot ? classname : classname.substring(idot + 1);
			if (simplename.startsWith(text)) matches.add(simplename);
		}
		return matches;
	}
}
