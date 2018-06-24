package org.scijava.ui.swing.script.languagesupport;

import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.rtextarea.RTextArea;
import org.fife.ui.rtextarea.ToolTipSupplier;

import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

/**
 * The MacroAutoCompletionProvider creates the list of auto completion suggestions from html
 * file in the resources directory
 *
 * Author: @haesleinhuepf
 * 06 2018
 */
class MacroAutoCompletionProvider extends DefaultCompletionProvider implements ToolTipSupplier {
    private static MacroAutoCompletionProvider instance = null;

    private MacroAutoCompletionProvider() {
        parseFunctionsHtmlDoc("functions.html");
        parseFunctionsHtmlDoc("functions_extd.html");
    }

    public static synchronized MacroAutoCompletionProvider getInstance() {
        if (instance == null) {
            instance = new MacroAutoCompletionProvider();
        }
        return instance;
    }

    private void parseFunctionsHtmlDoc(String filename) {
        ClassLoader classLoader = getClass().getClassLoader();
        InputStream resourceAsStream = classLoader.getResourceAsStream(filename);

        try {
            BufferedReader br
                    = new BufferedReader(new InputStreamReader(resourceAsStream));

            String name = "";
            String headline = "";
            String description = "";
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.contains("<a name=\"")) {
                    if (checkName(name)) {
                        addCompletion(makeListEntry(this, headline, name, description));
                    }
                    name = htmlToText(line.
                            split("<a name=\"")[1].
                            split("\"></a>")[0]);
                    description = "";
                    headline = "";
                } else {
                    if (headline.length() == 0) {
                        headline = htmlToText(line);
                    } else {
                        description = description + line + "\n";
                    }
                }

            }
            if (checkName(name)) {
                addCompletion(makeListEntry(this, headline, name, description));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean checkName(String name) {
        return (name.length() > 1) &&
                (!name.trim().startsWith("<")) &&
                (!name.trim().startsWith("-")) &&
                (name.compareTo("Top") != 0) &&
                (name.compareTo("IJ") != 0) &&
                (name.compareTo("Stack") != 0) &&
                (name.compareTo("Array") != 0) &&
                (name.compareTo("file") != 0) &&
                (name.compareTo("Fit") != 0) &&
                (name.compareTo("List") != 0) &&
                (name.compareTo("Overlay") != 0) &&
                (name.compareTo("Plot") != 0) &&
                (name.compareTo("Roi") != 0) &&
                (name.compareTo("String") != 0) &&
                (name.compareTo("Table") != 0) &&
                (name.compareTo("Ext") != 0) &&
                (name.compareTo("ext") != 0)&&
                (name.compareTo("alphabar") != 0)&&
                (name.compareTo("ext") != 0)
                ;
    }

    private String htmlToText(String text) {
        return text.
                replace("&quot;", "\"").
                replace("<b>", "").
                replace("</b>", "").
                replace("<i>", "").
                replace("</i>", "").
                replace("<br>", "");
    }

    private BasicCompletion makeListEntry(MacroAutoCompletionProvider provider, String headline, String name, String description) {
        String link = "https://imagej.nih.gov/ij/developer/macro/functions.html#" + name;

        description = "<a href=\"" + link + "\">" + headline + "</a><br>" + description;

        return new BasicCompletion(provider, headline, "", description);
    }

    /**
     * Returns the tool tip to display for a mouse event.<p>
     *
     * For this method to be called, the <tt>RSyntaxTextArea</tt> must be
     * registered with the <tt>javax.swing.ToolTipManager</tt> like so:
     *
     * <pre>
     * ToolTipManager.sharedInstance().registerComponent(textArea);
     * </pre>
     *
     * @param textArea The text area.
     * @param e The mouse event.
     * @return The tool tip text, or <code>null</code> if none.
     */
    @Override
    public String getToolTipText(RTextArea textArea, MouseEvent e) {

        String tip = null;

        List<Completion> completions = getCompletionsAt(textArea, e.getPoint());
        if (completions!=null && completions.size()>0) {
            // Only ever 1 match for us in C...
            Completion c = completions.get(0);
            tip = c.getToolTipText();
        }

        return tip;

    }


}
