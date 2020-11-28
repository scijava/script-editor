package org.scijava.ui.swing.script.languagesupport;

import org.fife.rsta.ac.AbstractLanguageSupport;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.CompletionProvider;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.scijava.plugin.Plugin;
import org.scijava.ui.swing.script.LanguageSupportPlugin;
import org.scijava.ui.swing.script.LanguageSupportService;
import org.scijava.ui.swing.script.autocompletion.JythonAutocompletionProvider;
import org.scijava.ui.swing.script.autocompletion.JythonImportFormat;
import org.scijava.ui.swing.script.autocompletion.JythonAutoCompletion;

/**
 * {@link LanguageSupportPlugin} for the jython language.
 *
 * @author Albert Cardona
 * 
 * @see LanguageSupportService
 */
@Plugin(type = LanguageSupportPlugin.class)
public class JythonLanguageSupportPlugin extends  AbstractLanguageSupport implements LanguageSupportPlugin
{
	
	private AutoCompletion ac;
	private RSyntaxTextArea text_area;
	
	public JythonLanguageSupportPlugin() {
		setAutoCompleteEnabled(true);
		setShowDescWindow(true);
	}

	@Override
	public String getLanguageName() {
		return "python";
	}

	@Override
	public void install(final RSyntaxTextArea textArea) {
		this.text_area = textArea;
		this.ac = this.createAutoCompletion(null);
		this.ac.install(textArea);
		// store upstream
		super.installImpl(textArea, this.ac);
	}

	@Override
	public void uninstall(final RSyntaxTextArea textArea) {
		if (textArea == this.text_area) {
			super.uninstallImpl(textArea); // will call this.acp.uninstall();
		}
	}
	
	/**
	 * Ignores the argument.
	 */
	@Override
	protected AutoCompletion createAutoCompletion(CompletionProvider p) {
		 return new JythonAutoCompletion(new JythonAutocompletionProvider(text_area, new JythonImportFormat()));
	}

}
