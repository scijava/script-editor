package fiji.scripting;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.event.*;
import javax.swing.JOptionPane;
import java.io.*;
import javax.swing.filechooser.*;
import javax.swing.colorchooser.*;
import java.awt.image.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
/*for the cell renderer part*/
import java.net.MalformedURLException;
import java.net.URL;
/*cell renderer part ends here*/
import javax.imageio.*;
import java.util.Arrays;
import java.util.List;
import javax.swing.text.*;
import org.fife.ui.rtextarea.*;
import org.fife.ui.rsyntaxtextarea.*;
import org.fife.ui.autocomplete.*;

class TextEditor extends JFrame implements ActionListener , ItemListener , ChangeListener ,MouseMotionListener,MouseListener ,CaretListener,InputMethodListener,DocumentListener,WindowListener	{

	JFileChooser fcc;                                                   //using filechooser
	//String action="";
	boolean fileChange=false;
   	String title="";
	InputMethodListener l;
   	File file;
   	CompletionProvider provider;
   	RSyntaxTextArea textArea;
   	Document doc;
	JMenuItem new1,open,save,saveas,quit,undo,redo,cut,copy,paste,find,replace,selectAll;
	FileInputStream fin;
      	FindDialog findDialog;
   	ReplaceDialog replaceDialog;     

	public TextEditor() {
		fcc = new JFileChooser();                                        //For the file opening saving things
		JPanel cp = new JPanel(new BorderLayout());
      		title="Text Editor for Fiji";
		textArea = new RSyntaxTextArea(25,80);
      		textArea.addInputMethodListener(l);
      		textArea.addCaretListener(this);
      		textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_C);
	  	String words[]={"public","private","protected","println","static","System","Swing","void","main","catch","class"};
      		DefaultCompletionProvider provider1 =new DefaultCompletionProvider(words);
		if(provider==null) {
			provider = createCompletionProvider();
		}
      		AutoCompletion autocomp=new AutoCompletion(provider);
	  	autocomp.setListCellRenderer(new CCellRenderer());
		autocomp.setShowDescWindow(true);
		autocomp.setParameterAssistanceEnabled(true);
      		autocomp.install(textArea);
	  	textArea.setToolTipSupplier((ToolTipSupplier)provider);
		ToolTipManager.sharedInstance().registerComponent(textArea);
      		doc=textArea.getDocument();
      		doc.addDocumentListener(this);
		RTextScrollPane sp = new RTextScrollPane(textArea);
      		cp.add(sp);

      	/********* This part is used to change the 
         	   the icon of the 
           	   window of the editor **********/

		BufferedImage image = null;
        	try {
	   		image = ImageIO.read(new java.net.URL("file:images/icon.png"));
        	}
	       	catch (IOException e) {
            		e.printStackTrace();
        	}
      		setIconImage(image);

      

            /********setting the icon part ends ********/


      		setContentPane(cp);
      		setTitle(title);
      		addWindowListener(this);
      		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

	/*********** Creating the menu options in the text editor ****************/

		JMenuBar mbar = new JMenuBar();
        	setJMenuBar(mbar);

      /*******  creating the menu for the File option **********/
		JMenu file = new JMenu("File");
        	file.setMnemonic(KeyEvent.VK_F);
		new1= new JMenuItem("New");
        	addToMenu(file,new1,KeyEvent.VK_N, ActionEvent.CTRL_MASK);
        	open = new JMenuItem("Open...");
        	addToMenu(file,open,KeyEvent.VK_O, ActionEvent.CTRL_MASK);
        	save = new JMenuItem("Save");
        	addToMenu(file,save,KeyEvent.VK_S, ActionEvent.CTRL_MASK);
       		saveas = new JMenuItem("Save as...");
        	file.add(saveas);
        	saveas.addActionListener(this);
        	file.addSeparator();
		quit = new JMenuItem("Quit");
        	file.add(quit);
        	quit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, ActionEvent.ALT_MASK));
        	quit.addActionListener(this);
        	mbar.add(file);

        /********The file menu part ended here  ***************/

        /*********The Edit menu part starts here ***************/

		JMenu edit = new JMenu("Edit");
        	undo = new JMenuItem("Undo");
        	addToMenu(edit,undo,KeyEvent.VK_Z, ActionEvent.CTRL_MASK);
		redo = new JMenuItem("Redo");
        	addToMenu(edit,redo,KeyEvent.VK_Y, ActionEvent.CTRL_MASK);
        	cut = new JMenuItem("Cut");
        	addToMenu(edit,cut,KeyEvent.VK_X, ActionEvent.CTRL_MASK);
		copy = new JMenuItem("Copy");
        	addToMenu(edit,copy,KeyEvent.VK_C, ActionEvent.CTRL_MASK);
		paste = new JMenuItem("Paste");
        	addToMenu(edit,paste,KeyEvent.VK_V, ActionEvent.CTRL_MASK);
		find = new JMenuItem("Find...");
        	addToMenu(edit,find,KeyEvent.VK_F, ActionEvent.CTRL_MASK);
		replace = new JMenuItem("Find and Replace...");
        	addToMenu(edit,replace,KeyEvent.VK_H, ActionEvent.CTRL_MASK);
		selectAll = new JMenuItem("Select All");
        	edit.add(selectAll);
        	selectAll.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, ActionEvent.CTRL_MASK));
        	selectAll.addActionListener(this);
       		mbar.add(edit);
        
        /******** The Edit menu part ends here *****************/

       


      /*********** The menu part ended here    ********************/

      		pack();
	  	getToolkit().setDynamicLayout(true);            //added to accomodate the autocomplete part

      		setLocationRelativeTo(null);
		setVisible(true);
   	}

	public void addToMenu(JMenu menu,JMenuItem menuitem,int keyevent,int actionevent) {
		menu.add(menuitem);
		menuitem.setAccelerator(KeyStroke.getKeyStroke(keyevent,actionevent));
		menuitem.addActionListener(this);
		menu.addSeparator();
	}



   		//test if the textArea is changed
	//on opening change textArea to new Rsyntaxtextarea to clean up the window and check for the flag
	//open an alert to check if the user wants to save in case the flag is true
	//creating the buttoned alert and depending on yes , no and cancel
	//now on closing the window checking if the user wants to save the file (only in case it is changed)
   
     /******* the function which accounts for the actions in the menu ************/
	public void actionPerformed(ActionEvent ae){
		//action=ae.getActionCommand();
		if(ae.getSource()==new1){
			if(fileChange==true) {
				int val= JOptionPane.showConfirmDialog(this, "Do you want to save changes??"); 
				if(val==JOptionPane.YES_OPTION){
					fileChange=false;
					saveaction();
					doc.removeDocumentListener(this);
					textArea.setText("");
				       	this.setTitle("Text Editor for Fiji");
					doc.addDocumentListener(this);

				}
				if(val==JOptionPane.NO_OPTION) {
					fileChange=false;
					doc.removeDocumentListener(this);
					textArea.setText("");
					this.setTitle("TextEditor for Fiji");
					doc.addDocumentListener(this);
				}
				if(val==JOptionPane.CANCEL_OPTION) {
				}
			 }

			else{
				doc.removeDocumentListener(this);
		       		textArea.setText("");
				this.setTitle("TextEditor for Fiji");
				doc.addDocumentListener(this);
			}
		}

		if (ae.getSource()==open) {
			fcc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
			int returnVal = fcc.showOpenDialog(TextEditor.this);
			try
			{
				file = fcc.getSelectedFile();
			}
			catch(Exception e){}
				/*condition to check whether there is a change and the 
			 	* user has really opted to open a new file
			 	*/
			if(fileChange==true&&returnVal==fcc.APPROVE_OPTION) {
				//this.setTitle("Not saved - Text Editor Demo for Fiji");
				int val= JOptionPane.showConfirmDialog(this, "Do you want to save changes??"); 
				if(val==JOptionPane.YES_OPTION){
					fileChange=false;
					saveaction();

					/*Document listener is removed before opening the file 
					 * and then added thanx to gitte for telling 
					 * about DocumentListener
					 */

					doc.removeDocumentListener(this);
					openaction(returnVal);
					doc.addDocumentListener(this);

				}
				if(val==JOptionPane.NO_OPTION){
					fileChange=false;
					doc.removeDocumentListener(this);
					openaction(returnVal);
					doc.addDocumentListener(this);
				}
				if(val==JOptionPane.CANCEL_OPTION){
				}
			 }

			if(fileChange==false&&returnVal==fcc.APPROVE_OPTION) {
				doc.removeDocumentListener(this);
		       		openaction(returnVal);
				doc.addDocumentListener(this);
			}
        
		}
		if(ae.getSource()==save) {

			fileChange=false;
			saveaction();
		}
		if(ae.getSource()==saveas)  {

			fileChange=false;
			int temp= saveasaction();                   //temp for the int return type of the function nothing else
		}
		if(ae.getSource()==quit) {
			processWindowEvent( new WindowEvent(this, WindowEvent.WINDOW_CLOSING) );
		}
         		     	   
    		if(ae.getSource()==cut) {
			textArea.cut();
		}
		if(ae.getSource()==copy) {
			textArea.copy();
		}
		if(ae.getSource()==paste) {
			textArea.paste();
		}
		if(ae.getSource()==undo) {
			textArea.undoLastAction();
		}
		if(ae.getSource()==redo) {
			textArea.redoLastAction();
		}
		if(ae.getSource()==find) {
			if(replaceDialog!=null){						//here should the code to close all other dialog boxes 
				replaceDialog.dispose();
			}
			if(findDialog==null){							//checking if there is no other find dialogue boxes open
				findDialog=new FindDialog(this,textArea);
				findDialog.setResizable(true);
				findDialog.pack();
				findDialog.setLocationRelativeTo(this);
			}
			findDialog.show();
			findDialog.toFront();
		}
		if(ae.getSource()==replace) {						//here should the code to close all other dialog boxes
			if(findDialog!=null){
				findDialog.dispose();
			}
			if(replaceDialog==null){
				replaceDialog=new ReplaceDialog(this,textArea);
				replaceDialog.setResizable(true);
				replaceDialog.pack();
				replaceDialog.setLocationRelativeTo(this);
			}
			replaceDialog.show();
			replaceDialog.toFront();
		}


		if(ae.getSource()==selectAll) {
			textArea.setCaretPosition(0);
			textArea.moveCaretPosition(textArea.getDocument().getLength());
		}



	}

	/*
	 * this function performs the file opening operation
	 */


	public void openaction(int returnVal) {

		try {
			if(file!=null) {
				title=(String)file.getName()+" - Text Editor for Fiji";
				this.setTitle(title);
			}
				/*changing the title part ends*/
			fin = new FileInputStream(file);
				//DataInputStream din = new DataInputStream(fin);
			BufferedReader din = new BufferedReader(new InputStreamReader(fin)); 
			String s = "";
			if (returnVal == fcc.APPROVE_OPTION) {
				textArea.setText("");
				while(true) {
					s = din.readLine();
					if(s==null) {
						break;
					}
					System.out.println(s);
					textArea.append(s+"\n");
					//ba.textArea.setText(s);
				}
			}
			else if(returnVal != fcc.APPROVE_OPTION) {
					System.out.println("Saving Canceled");
			}
			System.out.println("returnVal = "+returnVal+" and fcc.APPROVE_OPTION = "+fcc.APPROVE_OPTION);
			//fin.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
    			if (null != fin) {
        			try {
            				fin.close();
        			} catch (Exception e2) {
            				e2.printStackTrace();
        			}
			}
    		}

	}
	/*This function performs the saveasoperation i.e. saving in the
	 * new file
	 * Basically the motive behind creating different
	 * functions is because they are used more than one time
	 * int return type is given just because in one instance of 
	 * its use the value of the button clicked in the save dialog 
	 * box was required.
	 */
	public int saveasaction() {
			int returnVal=0;		//just to make sure it does not give a not initialized error
			boolean ifReplaceFile=false;
			try{
				   
         		     	   fcc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
         			   returnVal = fcc.showDialog(TextEditor.this,"Save");
         			   if (returnVal == JFileChooser.APPROVE_OPTION) {
          	   				file = fcc.getSelectedFile();
						String[] filenames;
         					File f = fcc.getCurrentDirectory();
          					filenames = f.list();
          					for(int i=0; i< filenames.length; i++) {
							System.out.println(filenames[i]+" "+file.getName());
							if(filenames[i].equals(file.getName())) {
								ifReplaceFile=true;
								break;
							}
						}
						if(ifReplaceFile) {
								int val= JOptionPane.showConfirmDialog(this, "Do you want to replace "+file.getName()+"??","D										o you want to replace "+file.getName()+"??",JOptionPane.YES_NO_OPTION); 
								if(val==JOptionPane.YES_OPTION) {

									//changing the title again
					                        	title=(String)file.getName()+" - Text Editor Demo for fiji";
									this.setTitle(title);
									BufferedWriter outFile = new BufferedWriter( new FileWriter( file ) );
									outFile.write( textArea.getText( ) ); //put in textfile
									outFile.flush( ); // redundant, done by close()
									outFile.close( );
								}
						}
						else {
							title=(String)file.getName()+" - Text Editor Demo for fiji";
							this.setTitle(title);
							BufferedWriter outFile = new BufferedWriter( new FileWriter( file ) );
							outFile.write( textArea.getText( ) ); //put in textfile
							outFile.flush( ); // redundant, done by close()
							outFile.close( );
						}

             
              				//This is where a real application would save the file.
            		  }
			}
			catch(Exception e){
				e.printStackTrace();
			}
			return returnVal;

	}
        /*It performs save in either new or the same file depending on whether there was a 
	 * file initially
	 */
	public void saveaction(){

		if(title=="Text Editor for Fiji") {
			int temp= saveasaction();         //temp has no use just because saveasaction has a int return type  
		}
		else {
			try {
				BufferedWriter outFile = new BufferedWriter( new FileWriter( file ) );
				outFile.write( textArea.getText( ) ); //put in textfile
				outFile.flush( ); // redundant, done by close()
				outFile.close( );
			}
			catch(Exception e){}	   
		}
	}

	public void itemStateChanged(ItemEvent ie) {}
	public void stateChanged(ChangeEvent e) {}
	public void mouseMoved(MouseEvent me) {}
	public void mouseClicked(MouseEvent me) {}
	public void mouseEntered(MouseEvent me) {}
	public void mouseExited(MouseEvent me) {}
	public void mouseDragged(MouseEvent me) {}
	public void mouseReleased(MouseEvent me) {}
	public void mousePressed(MouseEvent me) {}

	//this function is for the caret position change not of much use so far
	public void caretUpdate(CaretEvent ce) {
	 
	//	fileChange=true;
	}

	//next function is for the InputMethodEvent changes
	public void inputMethodTextChanged(InputMethodEvent event) {

		fileChange=true;

	}
	public void caretPositionChanged(InputMethodEvent event) {

		fileChange=true;

	}

	//Its the real meat the Document Listener functions raise the flag when the text is modifiedd
	public void insertUpdate(DocumentEvent e) {

		fileChange=true;

	}
	public void removeUpdate(DocumentEvent e) {

		fileChange=true;

	}
	public void changedUpdate(DocumentEvent e) {
	}

	public void windowActivated(WindowEvent e) {
	}
	public void windowClosing(WindowEvent e) {

		if(fileChange) {
			int val= JOptionPane.showConfirmDialog(this, "Do you want to save changes??"); 
			if(val==JOptionPane.YES_OPTION){

					/*
					 * This all mess is just to ensure that 
					 * the window doesnot close on doing foll things stepwise
					 * step 1)trying to close window with a unsaved file
					 * step 2)cancelling the save dialog box
					 */
				if(title=="Text Editor Demo for Fiji") {
					int temp=saveasaction();       //temp saves here whether the option was Approved :)
					if (temp == JFileChooser.APPROVE_OPTION) {
						fileChange=false;
						this.dispose();
					}
				}
				else {
					fileChange=false;
					saveaction();
					this.dispose();
				}


				}
				if(val==JOptionPane.NO_OPTION) {
					fileChange=false;
					this.dispose();

				}
				if(val==JOptionPane.CANCEL_OPTION) {
					setVisible(true);
				}
			 }
		else {
			this.dispose();
		}

	}
	public void windowClosed(WindowEvent e) {
	}
	public void windowDeactivated(WindowEvent e) {
	}
	public void windowDeiconified(WindowEvent e) {
	}
	public void windowIconified(WindowEvent e) {
	}
	public void windowOpened(WindowEvent e) {
	}
/*autocomplete addition starts here*/
	private CompletionProvider createCodeCompletionProvider() {

		// Add completions for the C standard library.
		DefaultCompletionProvider cp = new DefaultCompletionProvider();

		// First try loading resource (running from demo jar), then try
		// accessing file (debugging in Eclipse).
		ClassLoader cl = getClass().getClassLoader();
		InputStream in = cl.getResourceAsStream("c.xml");
		try {
			if (in!=null) {
				cp.loadFromXML(in);
				in.close();
			}
			else {
				cp.loadFromXML(new File("c.xml"));
			}
		} 
		catch (IOException ioe) {
			ioe.printStackTrace();
		}
		finally {
    			if (null != in) {
        			try {
            				in.close();
        			} catch (Exception e2) {
            				e2.printStackTrace();
        			}
    			}
		}



		// Add some handy shorthand completions.
		cp.addCompletion(new ShorthandCompletion(cp, "main",
							"int main(int argc, char **argv)"));

		return cp;

	}


	/**
	 * Returns the provider to use when in a comment.
	 *
	 * @return The provider.
	 * @see #createCodeCompletionProvider()
	 * @see #createStringCompletionProvider()
	 */
	private CompletionProvider createCommentCompletionProvider() {
		DefaultCompletionProvider cp = new DefaultCompletionProvider();
		cp.addCompletion(new BasicCompletion(cp, "TODO:", "A to-do reminder"));
		cp.addCompletion(new BasicCompletion(cp, "FIXME:", "A bug that needs to be fixed"));
		return cp;
	}


	/**
	 * Creates the completion provider for a C editor.  This provider can be
	 * shared among multiple editors.
	 *
	 * @return The provider.
	 */
	private CompletionProvider createCompletionProvider() {

		// Create the provider used when typing code.
		//CompletionProvider codeCP = createCodeCompletionProvider();

		// The provider used when typing a string.
		CompletionProvider stringCP = createStringCompletionProvider();

		// The provider used when typing a comment.
		CompletionProvider commentCP = createCommentCompletionProvider();

		// Create the "parent" completion provider.
		ClassCompletionProvider provider = new ClassCompletionProvider(new DefaultProvider(),textArea);
		provider.setStringCompletionProvider(stringCP);
		provider.setCommentCompletionProvider(commentCP);

		return provider;

	}




	/**
	 * Returns the completion provider to use when the caret is in a string.
	 *
	 * @return The provider.
	 * @see #createCodeCompletionProvider()
	 * @see #createCommentCompletionProvider()
	 */
	private CompletionProvider createStringCompletionProvider() {

		DefaultCompletionProvider cp = new DefaultCompletionProvider();
		cp.addCompletion(new BasicCompletion(cp, "%c", "char", "Prints a character"));
		cp.addCompletion(new BasicCompletion(cp, "%i", "signed int", "Prints a signed integer"));
		cp.addCompletion(new BasicCompletion(cp, "%f", "float", "Prints a float"));
		cp.addCompletion(new BasicCompletion(cp, "%s", "string", "Prints a string"));
		cp.addCompletion(new BasicCompletion(cp, "%u", "unsigned int", "Prints an unsigned integer"));
		cp.addCompletion(new BasicCompletion(cp, "\\n", "Newline", "Prints a newline"));
		return cp;

	}


	/**
	 * Focuses the text area.
	 */
	public void focusEditor() {
		textArea.requestFocusInWindow();
	}
}



