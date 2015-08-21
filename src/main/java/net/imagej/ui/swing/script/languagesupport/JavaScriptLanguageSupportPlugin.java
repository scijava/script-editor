
package net.imagej.ui.swing.script.languagesupport;

import org.fife.rsta.ac.js.JavaScriptLanguageSupport;
import org.scijava.plugin.Plugin;

import net.imagej.ui.swing.script.LanguageSupportPlugin;
import net.imagej.ui.swing.script.LanguageSupportService;

/**
 * {@link LanguageSupportPlugin} for the javascript language.
 * 
 * @author Jonathan Hale
 * @see JavaScriptLanguageSupport
 * @see LanguageSupportService
 */
@Plugin(type = LanguageSupportPlugin.class)
public class JavaScriptLanguageSupportPlugin extends JavaScriptLanguageSupport
	implements LanguageSupportPlugin
{

	public JavaScriptLanguageSupportPlugin() {
		super();
	}

	@Override
	public String getLanguageName() {
		return "javascript";
	}
}
