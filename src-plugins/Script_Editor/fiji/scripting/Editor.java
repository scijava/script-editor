package fiji.scripting;
import javax.swing.*;
//import ij.plugin.PlugIn;
public class Editor{
	public static void main(String[] arg){
		//TextEditor editor=new TextEditor();
		SwingUtilities.invokeLater(new Runnable() {
         		public void run() {
            			new TextEditor();
         		}
      		});
	}
}

