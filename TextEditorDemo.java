
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.event.*;
import java.io.*;
import javax.swing.filechooser.*;
import javax.swing.colorchooser.*;
import java.awt.image.*;
import javax.imageio.*;
import java.awt.geom.AffineTransform;
import java.util.Arrays;
import org.fife.ui.rtextarea.*;
import org.fife.ui.rsyntaxtextarea.*;

public class TextEditorDemo extends JFrame implements ActionListener , ItemListener , ChangeListener ,MouseMotionListener,MouseListener
{

   private static final long serialVersionUID = 1L;
   JFileChooser fcc;                                                   //using filechooser
   String action="";
   
   RSyntaxTextArea textArea;

   public TextEditorDemo() {
	fcc = new JFileChooser();                                        //For the file opening saving things
	
      JPanel cp = new JPanel(new BorderLayout());

      textArea = new RSyntaxTextArea();
      textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
      RTextScrollPane sp = new RTextScrollPane(textArea);
      cp.add(sp);

      	/********* This part is used to change the 
         	   the icon of the 
           	   window of the editor ***********/

	BufferedImage image = null;
        try {
            image = ImageIO.read(cp.getClass().getResource("/images/microscope.gif"));
        } catch (IOException e) {
            e.printStackTrace();
        }
      setIconImage(image);



            /********setting the icon part ends ********/


      setContentPane(cp);
      setTitle("Text Editor Demo for Fiji");
      setDefaultCloseOperation(EXIT_ON_CLOSE);

	/*********** Creating the menu options in the text editor ****************/

	JMenuBar mbar = new JMenuBar();
        setJMenuBar(mbar);

      /*******  creating the menu for the File option **********/
	JMenu file = new JMenu("File");
        file.setMnemonic(KeyEvent.VK_F);
        JMenuItem open = new JMenuItem("Open...");
        file.add(open);
        open.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, ActionEvent.ALT_MASK));
        open.addActionListener(this);
        file.addSeparator();
        JMenuItem save = new JMenuItem("Save...");
        file.add(save);
        save.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, ActionEvent.CTRL_MASK));
        save.addActionListener(this);
        file.addSeparator();
       JMenuItem saveas = new JMenuItem("Save as...");
        file.add(saveas);
        saveas.addActionListener(this);
        file.addSeparator();
        JMenuItem quit = new JMenuItem("Quit");
        file.add(quit);
        quit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, ActionEvent.ALT_MASK));
        quit.addActionListener(this);
        mbar.add(file);

        /********The file menu part ended here  ***************/

        /*********The Edit menu part starts here ***************/

	JMenu edit = new JMenu("Edit");
        JMenuItem undo = new JMenuItem("Undo...");
        edit.add(undo);
        undo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, ActionEvent.CTRL_MASK));
        undo.addActionListener(this);
        edit.addSeparator();
        JMenuItem cut = new JMenuItem("Cut...");
        edit.add(cut);
        cut.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, ActionEvent.CTRL_MASK));
        cut.addActionListener(this);
        mbar.add(edit);
        
        /******** The Edit menu part ends here *****************/

       


      /*********** The menu part ended here    ********************/

      pack();
      setLocationRelativeTo(null);
	setVisible(true);
   }

     /******* the function which accounts for the actions in the menu ************/
	public void actionPerformed(ActionEvent ae){
		action=ae.getActionCommand();
		 if (action=="Open...") {
        		fcc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
			int returnVal = fcc.showOpenDialog(TextEditorDemo.this);
			 try
			{
				File file = fcc.getSelectedFile();
				FileInputStream fin = new FileInputStream(file);
				//DataInputStream din = new DataInputStream(fin);
				BufferedReader din = new BufferedReader(new InputStreamReader(fin)); 
				String s = "";
				if (returnVal == fcc.APPROVE_OPTION)
				{
					while(s != null)
					{
						
						s = din.readLine();
						System.out.println(s);
						textArea.append(s+"\n");
						//ba.textArea.setText(s);
					}
				}
				else if(returnVal != fcc.APPROVE_OPTION)
				{
					System.out.println("Saving Canceled");
				}
				System.out.println("returnVal = "+returnVal+" and fcc.APPROVE_OPTION = "+fcc.APPROVE_OPTION);
				fin.close();
			}
			catch (Exception ex)
			{
			}
		}
		if(action=="Save...") {
			try{
         		     	   fcc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
         			   int returnVal = fcc.showDialog(TextEditorDemo.this,"Save");
         			   if (returnVal == JFileChooser.APPROVE_OPTION) {
          	   				File fileName = fcc.getSelectedFile();
						BufferedWriter outFile = new BufferedWriter( new FileWriter( fileName ) );
						outFile.write( textArea.getText( ) ); //put in textfile
						outFile.flush( ); // redundant, done by close()
						outFile.close( );
             
              				//This is where a real application would save the file.
            		  }
			}
			catch(Exception e){}
    		}

	
	}
	
	public void itemStateChanged(ItemEvent ie){}

    public void stateChanged(ChangeEvent e) {}

	public void mouseMoved(MouseEvent me){}
	public void mouseClicked(MouseEvent me){}
	public void mouseEntered(MouseEvent me){}
	public void mouseExited(MouseEvent me){}
	public void mouseDragged(MouseEvent me){}
	public void mouseReleased(MouseEvent me){}
	public void mousePressed(MouseEvent me){}



   public static void main(String[] args) {
      // Start all Swing applications on the EDT.
      SwingUtilities.invokeLater(new Runnable() {
         public void run() {
            new TextEditorDemo();
         }
      });
   }

}
