package fiji.scripting;

import javax.swing.*;
import ij.plugin.PlugIn;
import ij.Macro;

public class Script_Editor implements PlugIn {

	public static void main(String[] arg){
		new TextEditor("");
    }

	public void run(String path) {
		System.out.println(path);
		String a = Macro.getOptions();
		System.out.println(Thread.currentThread().getName());
		try {
			new TextEditor(Macro.getValue(a,"path",null));
		} catch(Exception e) { new TextEditor(""); }
   	}

}

