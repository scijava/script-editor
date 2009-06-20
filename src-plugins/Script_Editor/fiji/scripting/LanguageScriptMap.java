package fiji.scripting;

import java.util.HashMap;
import java.util.Map;
import common.*;
import Clojure.*;
import JRuby.*;
import Javascript.*;
import Jython.*;
//import Refresh_Javas;

public class LanguageScriptMap {

	public Map<String,RefreshScripts> createScriptMap() {
		HashMap<String,RefreshScripts> map = new HashMap<String,RefreshScripts>();


		map.put(".clj", new Refresh_Clojure_Scripts());
		map.put(".py", new Refresh_Jython_Scripts());
		map.put(".js", new Refresh_Javascript_Scripts());
		map.put(".rb", new Refresh_JRuby_Scripts());
		//map.put(".java", new Refresh_Javas());

		return map;

	}
}
