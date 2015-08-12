
package net.imagej.ui.swing.script.languagesupport;

import net.imagej.ui.swing.script.LanguageSupportPlugin;
import net.imagej.ui.swing.script.LanguageSupportService;

import org.fife.rsta.ac.js.JavaScriptLanguageSupport;
import org.scijava.plugin.Plugin;

/**
 * {@link LanguageSupportPlugin} for the javascript language.
 * 
 * @author Jonathan Hale
 * @see JavaScriptLanguageSupport
 * @see LanguageSupportService
 */
@Plugin(type = LanguageSupportPlugin.class, label = "javascript")
public class JavaScriptLanguageSupportPlugin extends JavaScriptLanguageSupport
	implements LanguageSupportPlugin
{

	public JavaScriptLanguageSupportPlugin() {
		super();
	}
}
