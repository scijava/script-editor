package org.scijava.ui.swing.script.autocompletion;

public interface ImportCompletion
{
	/**
	 * 
	 * @return The formatted import statement, e.g. "from this import that".
	 */
	public String getImportStatement();
	
	/**
	 * 
	 * @return The fully qualified class name.
	 */
	public String getClassName();
}
