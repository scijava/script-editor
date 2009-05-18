package Script_Editor;
import javax.swing.*;
public class Script_Editor {
	public static void main(String[] args){
		//TextEditor editor=new TextEditor();
		SwingUtilities.invokeLater(new Runnable() {
         		public void run() {
            			new TextEditor();
         		}
      		});
	}
}
		
