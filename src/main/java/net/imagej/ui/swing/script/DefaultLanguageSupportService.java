
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
 * Default {@link LanguageSupportService} implementation.
 * 
 * @author Jonathan Hale
 */
@Plugin(type = Service.class)
public class DefaultLanguageSupportService extends
	AbstractSingletonService<LanguageSupportPlugin> implements LanguageSupportService
{

	Map<String, LanguageSupport> languageSupports = null;

	// -- LanguageSupportService methods --
	
	/* (non-Javadoc)
	 * @see net.imagej.ui.swing.script.LanguageSupportService#getLanguageSupport(org.scijava.script.ScriptLanguage)
	 */
	@Override
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
