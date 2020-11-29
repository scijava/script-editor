package org.scijava.ui.swing.script.autocompletion;

public interface ImportFormat
{	
	/** Given a fully-qualified class name, return a String with the class formatted as an import statement. */
	public String singleToImportStatement(String className);
	
	public String dualToImportStatement(String packageName, String simpleClassName);
}