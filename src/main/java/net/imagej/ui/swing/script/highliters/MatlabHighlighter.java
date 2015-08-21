
package net.imagej.ui.swing.script.highliters;

import net.imagej.ui.swing.script.SyntaxHighlighter;

import org.scijava.plugin.Plugin;

/**
 * SyntaxHighliter for matlab scripts.
 * 
 * @author Johannes Schindelin
 * @author Jonathan Hale
 */
@Plugin(type = SyntaxHighlighter.class, name = "matlab")
public class MatlabHighlighter extends MatlabTokenMaker implements
	SyntaxHighlighter
{
	// Everything implemented in MatlabTokenMaker
}
