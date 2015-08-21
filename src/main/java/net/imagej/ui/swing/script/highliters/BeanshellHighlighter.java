
package net.imagej.ui.swing.script.highliters;

import net.imagej.ui.swing.script.SyntaxHighlighter;

import org.fife.ui.rsyntaxtextarea.modes.JavaTokenMaker;
import org.scijava.plugin.Plugin;

/**
 * SyntaxHighliter for "beanshell".
 * 
 * @author Johannes Schindelin
 * @author Jonathan Hale
 */
@Plugin(type = SyntaxHighlighter.class, name = "beanshell")
public class BeanshellHighlighter extends JavaTokenMaker implements
	SyntaxHighlighter
{
	// Everything implemented in JavaTokenMaker
}
