package fiji.scripting;
//import ij.*;
import java.io.File;

public class MainClassForDebugging {

	static int foo;

	public static void main(String args[]) {
		/*if(IJ.getInstance()!=null) {
			new ImageJ();
		}*/

		foo++;
		foo--;
		foo+=2;
		foo+=3;
		foo--;
		String s="The value of foo is "+foo;
		System.err.write(s.getBytes(),0,s.getBytes().length);
		//IJ.error("the main function");
		/*String className=findClassName(args[0]);
		Object obj=IJ.runPlugIn(className,"");*/
	}

	/*public static String findClassName(String path) {
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
	}*/

}

