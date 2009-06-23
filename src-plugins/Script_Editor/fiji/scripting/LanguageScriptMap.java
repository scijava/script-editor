package fiji.scripting;

import java.util.HashMap;
import java.util.Map;
import common.*;
import Clojure.Refresh_Clojure_Scripts;
import Jython.Refresh_Jython_Scripts;
import JRuby.Refresh_JRuby_Scripts;
import Javascript.Refresh_Javascript_Scripts;
import refreshJavas.Refresh_Javas;


public class LanguageScriptMap extends HashMap<String,RefreshScripts>{

	public LanguageScriptMap() {
		this.put(".clj", new Refresh_Clojure_Scripts());
		this.put(".py", new Refresh_Jython_Scripts());
		this.put(".js", new Refresh_Javascript_Scripts());
		this.put(".rb", new Refresh_JRuby_Scripts());
		this.put(".java", new Refresh_Javas());
	}
}
