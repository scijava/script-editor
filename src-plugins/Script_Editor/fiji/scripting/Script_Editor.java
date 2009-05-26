package fiji.scripting;

import javax.swing.*;
import ij.plugin.PlugIn;

public class Script_Editor implements PlugIn {

	public static void main(String[] arg){
		//TextEditor editor=new TextEditor();
		SwingUtilities.invokeLater(new Runnable() {
         		public void run() {
            			new TextEditor();
         		}
      		});
	}

	public void run(String arg) {
		SwingUtilities.invokeLater(new Runnable() {
         		public void run() {
            			new TextEditor();
         		}
      		});
	}

}

