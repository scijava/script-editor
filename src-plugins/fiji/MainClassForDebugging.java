package fiji;

import ij.*;
import java.io.File;

public class MainClassForDebugging {

	static int foo;
	static String className;

	public static void main(String args[]) {
		if(IJ.getInstance()==null) {
			new ImageJ();
		}

		String s=System.getProperty("java.class.path");
		className=findClassName(args[0]);
		try {
			IJ.runPlugIn(className,"");
		} catch(Exception e) { e.printStackTrace(); }
		IJ.getInstance().dispose();

	}

	public static String findClassName(String path) {
		String c = path;
        if (c.endsWith(".java")) {
			 c = c.substring(0, c.length() - 5);
		}
		String pluginsPath = Menus.getPlugInsPath();
		if (!pluginsPath.endsWith(File.separator))
			pluginsPath += File.separator;
		if (c.startsWith(pluginsPath)) {
			c = c.substring(pluginsPath.length());
			while (c.startsWith(File.separator))
				c = c.substring(1);
		}

			return(c.replace('/','.'));
	}

}

