
package net.imagej.ui.swing.script.highliters;

import net.imagej.ui.swing.script.SyntaxHighlighter;

import org.scijava.plugin.Plugin;

/**
 * SyntaxHighliter for ij1-macros.
 * 
 * @author Johannes Schindelin
 * @author Jonathan Hale
 */
@Plugin(type = SyntaxHighlighter.class, name = "ij1-macro")
public class IJ1MacroHighlighter extends ImageJMacroTokenMaker implements
	SyntaxHighlighter
{
	// Everything implemented in ImageJMacroTokenMaker
}
