package stub;
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
		//System.err.write(s.getBytes(),0,s.getBytes().length);
		className=findClassName(args[0]);
		//System.err.write(className.getBytes(),0,className.getBytes().length);
		try {
			IJ.runPlugIn(className,"");
		} catch(Exception e) { e.printStackTrace(); }
		//System.exit(0);
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
		if(c.indexOf('/')>=0)
			return(c.replace('/','.'));
		else
			return c;
	}

}

