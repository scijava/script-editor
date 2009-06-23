package fiji.scripting;

import java.util.HashMap;
import java.util.Map;
import common.*;


public class LanguageScriptMap extends HashMap<String,RefreshScripts>{

	public LanguageScriptMap() {
		this.put(".clj", new Clojure.Refresh_Clojure_Scripts());
		this.put(".py", new Jython.Refresh_Jython_Scripts());
		this.put(".js", new Javascript.Refresh_Javascript_Scripts());
		this.put(".rb", new JRuby.Refresh_JRuby_Scripts());
		this.put(".java", new Refresh_Javas.Refresh_Javas());
	}


}
