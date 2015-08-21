
package net.imagej.ui.swing.script;

import org.fife.rsta.ac.LanguageSupport;
import org.fife.ui.autocomplete.AutoCompletion;
import org.scijava.plugin.SingletonPlugin;

/**
 * Interface for {@link AutoCompletion} plugins.
 * 
 * @author Jonathan Hale
 */
public interface LanguageSupportPlugin extends SingletonPlugin,
	LanguageSupport
{

	/**
	 * @return the name of the language this plugin adds support for.
	 */
	public String getLanguageName();
}
