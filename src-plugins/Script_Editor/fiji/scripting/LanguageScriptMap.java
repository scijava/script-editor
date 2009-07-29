package fiji.scripting;

import java.util.HashMap;
import common.RefreshScripts;
import Clojure.Refresh_Clojure_Scripts;
import Jython.Refresh_Jython_Scripts;
import JRuby.Refresh_JRuby_Scripts;
import Javascript.Refresh_Javascript_Scripts;
import fiji.scripting.java.Refresh_Javas;

public class LanguageScriptMap extends HashMap<String,RefreshScripts>{

	public LanguageScriptMap() {
		put(".clj", new Refresh_Clojure_Scripts());
		put(".py", new Refresh_Jython_Scripts());
		put(".js", new Refresh_Javascript_Scripts());
		put(".rb", new Refresh_JRuby_Scripts());
		put(".java", new Refresh_Javas());
	}
}
