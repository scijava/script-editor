package fiji.scripting;

import java.util.HashMap;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import javax.swing.JRadioButtonMenuItem;

public class LanguageInformationMap extends HashMap<String,LanguageInformation>{

	public LanguageInformationMap(JRadioButtonMenuItem[] language) {
		put(".java", new LanguageInformation(SyntaxConstants.SYNTAX_STYLE_JAVA,language[0],"Java"));
		put(".js", new LanguageInformation(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT,language[1],"Javascript"));
		put(".py", new LanguageInformation(SyntaxConstants.SYNTAX_STYLE_PYTHON,language[2],"Python"));
		put(".rb", new LanguageInformation(SyntaxConstants.SYNTAX_STYLE_RUBY,language[3],"Ruby"));
		put(".clj", new LanguageInformation(null,language[4],"Clojure"));
		put(".m", new LanguageInformation(null,language[5],"Matlab"));
		put(".n", new LanguageInformation(SyntaxConstants.SYNTAX_STYLE_NONE,language[6],"None"));
	}

}
