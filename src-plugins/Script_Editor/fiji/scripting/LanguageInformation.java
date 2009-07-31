package fiji.scripting;

import javax.swing.JRadioButtonMenuItem;

/*The class keeps information 
	about particular language as a object in the Map*/
public class LanguageInformation {

	String syntaxStyle;
	JRadioButtonMenuItem menuItem;
	String menuEntry;

	public LanguageInformation(String style,JRadioButtonMenuItem item,String entry) {
		syntaxStyle =style;
		menuItem=item;
		menuEntry=entry;
	}

	public String getSyntaxStyle() {
		return syntaxStyle;
	}

	public JRadioButtonMenuItem getMenuItem() {
		return menuItem;
	}

	public String getMenuEntry() {
		return menuEntry;
	}
}
