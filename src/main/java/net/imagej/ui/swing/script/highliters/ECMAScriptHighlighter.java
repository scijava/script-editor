
package net.imagej.ui.swing.script.highliters;

import net.imagej.ui.swing.script.SyntaxHighlighter;

import org.fife.ui.rsyntaxtextarea.modes.JavaScriptTokenMaker;
import org.scijava.plugin.Plugin;

/**
 * SyntaxHighliter for "ecmascript".
 * 
 * @author Johannes Schindelin
 * @author Jonathan Hale
 */
@Plugin(type = SyntaxHighlighter.class, name = "ecmascript")
public class ECMAScriptHighlighter extends JavaScriptTokenMaker implements
	SyntaxHighlighter
{
	// Everything implemented in JavaScriptTokenMaker
}
