
package net.imagej.ui.swing.script;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.fife.rsta.ac.LanguageSupport;
import org.scijava.plugin.AbstractSingletonService;
import org.scijava.plugin.Plugin;
import org.scijava.script.ScriptLanguage;
import org.scijava.service.Service;

/**
 * Service which manages {@link LanguageSupportPlugin}s.
 * {@link LanguageSupportPlugin}s provide features like code completion for
 * example.
 * 
 * @author Jonathan Hale
 */
@Plugin(type = Service.class)
public class LanguageSupportService extends
	AbstractSingletonService<LanguageSupportPlugin>
{

	Map<String, LanguageSupport> languageSupports = null;

	// -- LanguageSupportService methods --
	
	/**
	 * Get a {@link LanguageSupport} for the given language.
	 * 
	 * @param language Language to get support for.
	 * @return a {@link LanguageSupport} matching the given language or the
	 *         <code>null</code> if there was none or language was
	 *         <code>null</code>.
	 */
	public LanguageSupport getLanguageSupport(ScriptLanguage language) {
		if (language == null) {
			return null;
		}
		final String name = language.getLanguageName().toLowerCase();
		return languageSupports().get(name);
	}
	
	// -- SingletonService methods --

	@Override
	public Class<LanguageSupportPlugin> getPluginType() {
		return LanguageSupportPlugin.class;
	}

	// -- Helper methods - lazy initialization --

	/** Gets {@link #languageSupports}, initializing if necessary. */
	private Map<String, LanguageSupport> languageSupports() {
		if (languageSupports == null) initLanguageSupportPlugins();
		return languageSupports;
	}

	/** Initializes {@link #languageSupports}. */
	private synchronized void initLanguageSupportPlugins() {
		if (languageSupports != null) return;
		final HashMap<String, LanguageSupport> map =
			new HashMap<String, LanguageSupport>();

		for (LanguageSupportPlugin instance : getInstances()) {
			map.put(instance.getLanguageName(), instance);
		}

		languageSupports = Collections.unmodifiableMap(map);
	}

}
