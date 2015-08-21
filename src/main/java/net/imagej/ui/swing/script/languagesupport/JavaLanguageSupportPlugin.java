
package net.imagej.ui.swing.script.languagesupport;

import java.io.IOException;

import org.fife.rsta.ac.java.JavaLanguageSupport;
import org.scijava.plugin.Plugin;

import net.imagej.ui.swing.script.LanguageSupportPlugin;
import net.imagej.ui.swing.script.LanguageSupportService;

/**
 * {@link LanguageSupportPlugin} for the java language.
 * 
 * @author Jonathan Hale
 * @see JavaLanguageSupport
 * @see LanguageSupportService
 */
// This plugin is temporarily disabled pending a resolution for:
//   https://github.com/bobbylight/RSTALanguageSupport/issues/26
//@Plugin(type = LanguageSupportPlugin.class)
public class JavaLanguageSupportPlugin extends JavaLanguageSupport implements
	LanguageSupportPlugin
{

	public JavaLanguageSupportPlugin() throws IOException {
		super();

		getJarManager().addCurrentJreClassFileSource();
	}

	@Override
	public String getLanguageName() {
		return "java";
	}

}
