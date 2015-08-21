
package net.imagej.ui.swing.script;

import net.imagej.ImageJService;

import org.fife.rsta.ac.LanguageSupport;
import org.scijava.plugin.SingletonService;
import org.scijava.script.ScriptLanguage;

/**
 * Service which manages {@link LanguageSupportPlugin}s.
 * {@link LanguageSupportPlugin}s provide features like code completion for
 * example.
 * 
 * @author Jonathan Hale
 */
public interface LanguageSupportService extends
	SingletonService<LanguageSupportPlugin>, ImageJService
{

	/**
	 * Get a {@link LanguageSupport} for the given language.
	 * 
	 * @param language Language to get support for.
	 * @return a {@link LanguageSupport} matching the given language or the
	 *         <code>null</code> if there was none or language was
	 *         <code>null</code>.
	 */
	public abstract LanguageSupport getLanguageSupport(ScriptLanguage language);

}
