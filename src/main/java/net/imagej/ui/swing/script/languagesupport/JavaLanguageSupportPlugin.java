
package net.imagej.ui.swing.script.languagesupport;

import java.io.IOException;

import net.imagej.ui.swing.script.LanguageSupportPlugin;
import net.imagej.ui.swing.script.LanguageSupportService;

import org.fife.rsta.ac.java.JavaLanguageSupport;
import org.scijava.plugin.Plugin;

/**
 * {@link LanguageSupportPlugin} for the java language.
 * 
 * @author Jonathan Hale
 * @see JavaLanguageSupport
 * @see LanguageSupportService
 */
@Plugin(type = LanguageSupportPlugin.class, name = "java")
public class JavaLanguageSupportPlugin extends JavaLanguageSupport
	implements LanguageSupportPlugin
{

	public JavaLanguageSupportPlugin() throws IOException {
		super();
		
		getJarManager().addCurrentJreClassFileSource();
	}
}
