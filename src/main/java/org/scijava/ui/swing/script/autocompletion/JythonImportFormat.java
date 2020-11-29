package org.scijava.ui.swing.script.autocompletion;

public class JythonImportFormat implements ImportFormat
{
	@Override
	public final String singleToImportStatement(final String className) {
		final int idot = className.lastIndexOf('.');
		if (-1 == idot)
			return "import " + className;
		return dualToImportStatement(className.substring(0, idot), className.substring(idot + 1));
	}

	@Override
	public String dualToImportStatement(final String packageName, final String simpleClassName) {
		return "from " + packageName + " import " + simpleClassName;
	}
}
