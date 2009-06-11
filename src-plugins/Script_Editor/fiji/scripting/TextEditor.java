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
import ij.io.*;
import ij.IJ;
import ij.Prefs;
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
   	File file,f;
   	CompletionProvider provider;
   	RSyntaxTextArea textArea;
   	Document doc;
	JMenuItem new1,open,save,saveas,quit,undo,redo,cut,copy,paste,find,replace,selectAll,autocomplete,jfcdialog,ijdialog;
	JRadioButtonMenuItem langjava,langjavascript,langclojure,langpython,langruby;
	//JMenu io;
	FileInputStream fin;
      	FindDialog findDialog;
   	ReplaceDialog replaceDialog;
	AutoCompletion autocomp;

	public TextEditor() {
		fcc = new JFileChooser();                                        //For the file opening saving things
		JPanel cp = new JPanel(new BorderLayout());
      		title="Text Editor for Fiji";
		textArea = new RSyntaxTextArea(25,80);
      		textArea.addInputMethodListener(l);
      		textArea.addCaretListener(this);
      		textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
	  	String words[]={"public","private","protected","println","static","System","Swing","void","main","catch","class"};
      		DefaultCompletionProvider provider1 =new DefaultCompletionProvider(words);
		if(provider==null) {
			provider = createCompletionProvider();
		}
      		autocomp=new AutoCompletion(provider);
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
			edit.addSeparator();
        	cut = new JMenuItem("Cut");
        	addToMenu(edit,cut,KeyEvent.VK_X, ActionEvent.CTRL_MASK);
		copy = new JMenuItem("Copy");
        	addToMenu(edit,copy,KeyEvent.VK_C, ActionEvent.CTRL_MASK);
		paste = new JMenuItem("Paste");
        	addToMenu(edit,paste,KeyEvent.VK_V, ActionEvent.CTRL_MASK);
			edit.addSeparator();
		find = new JMenuItem("Find...");
        	addToMenu(edit,find,KeyEvent.VK_F, ActionEvent.CTRL_MASK);
		replace = new JMenuItem("Find and Replace...");
        	addToMenu(edit,replace,KeyEvent.VK_H, ActionEvent.CTRL_MASK);
			edit.addSeparator();
		selectAll = new JMenuItem("Select All");
        	addToMenu(edit,selectAll,KeyEvent.VK_A, ActionEvent.CTRL_MASK);
       		mbar.add(edit);
        
        /******** The Edit menu part ends here *****************/

		/********The options menu part starts here**************/
		JMenu options = new JMenu("Options");
		autocomplete = new JMenuItem("Autocomplete");
		addToMenu(options,autocomplete,KeyEvent.VK_SPACE, ActionEvent.CTRL_MASK);
		options.addSeparator();
		JMenu io = new JMenu("Input/Output");
		JMenu dialog= new JMenu("Open/Save Dialog");
		JMenuItem jfcdialog=new JMenuItem("JFileChooser");
		dialog.add(jfcdialog);
		jfcdialog.addActionListener(this);
		dialog.addSeparator();
		JMenuItem ijdialog=new JMenuItem("IJ");
		dialog.add(ijdialog);
		ijdialog.addActionListener(this);
		io.add(dialog);

		options.add(io);

       		mbar.add(options);

		/*********The Language parts starts here********************/
		JMenu language = new JMenu("Language");

		langjava = new JRadioButtonMenuItem("Java");
		langjava.setMnemonic(KeyEvent.VK_J);
		langjava.setActionCommand("Java");
		langjava.setSelected(true);

		langjavascript = new JRadioButtonMenuItem("Javascript");
		langjavascript.setMnemonic(KeyEvent.VK_J);
		langjavascript.setActionCommand("Javascript");

		langpython = new JRadioButtonMenuItem("Python");
		langpython.setMnemonic(KeyEvent.VK_P);
		langpython.setActionCommand("Python");

		langruby = new JRadioButtonMenuItem("Ruby");
		langruby.setMnemonic(KeyEvent.VK_R);
		langruby.setActionCommand("Ruby");

		langclojure = new JRadioButtonMenuItem("Clojure");
		langclojure.setMnemonic(KeyEvent.VK_C);
		langclojure.setActionCommand("Clojure");

		//Group the radio buttons.
		ButtonGroup group = new ButtonGroup();
		group.add(langclojure);
		group.add(langjava);
		group.add(langjavascript);
		group.add(langpython);
		group.add(langruby);

		language.add(langclojure);
		language.add(langjava);
		language.add(langjavascript);
		language.add(langpython);
		language.add(langruby);

		//Register a listener for the radio buttons.
		langclojure.addActionListener(this);
		langjava.addActionListener(this);
		langjavascript.addActionListener(this);
		langpython.addActionListener(this);
		langruby.addActionListener(this);

		//language.add(group);
		mbar.add(language);


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
		//menu.addSeparator();
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
			int returnVal=-1;

				OpenDialog dialog = new OpenDialog("Open..","");
				String directory = dialog.getDirectory();
				String name = dialog.getFileName();
				String path="";
				if (name!=null) {
					returnVal=fcc.APPROVE_OPTION;
					path = directory+name;
					boolean fullPath = path.startsWith("/") || path.startsWith("\\") || path.indexOf(":\\")==1 || path.startsWith("http://");
					if (!fullPath) {
						String workingDir = OpenDialog.getDefaultDirectory();
						if (workingDir!=null)
							path = workingDir + path;
					}
				}
				try
				{
					file = new File(path);
				}
				catch(Exception e){System.out.println("problem in opening");}
			//}
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
			try{
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
			} catch(Exception e){ e.printStackTrace(); }
		}


		if(ae.getSource()==selectAll) {
			textArea.setCaretPosition(0);
			textArea.moveCaretPosition(textArea.getDocument().getLength());
		}

		if(ae.getSource()==autocomplete) {
			try{
				autocomp.doCompletion();
			} catch(Exception e){}
		}
		if(ae.getSource()==jfcdialog) {
			//Prefs.set(Prefs.OPTIONS,32);
			Prefs.useJFileChooser=true;
			Prefs.savePreferences();
		}
		if(ae.getSource()==ijdialog) {
			Prefs.useJFileChooser=false;
			Prefs.savePreferences();
		}

		if(ae.getSource()==langclojure) {
			textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
		}
		if(ae.getSource()==langjava) {
			textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
		}
		if(ae.getSource()==langjavascript) {
			textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
		}
		if(ae.getSource()==langpython) {
			textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PYTHON);
		}
		if(ae.getSource()==langruby) {
			textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_RUBY);
		}

	}

	/*
	 * this function performs the file opening operation
	 */


	public void openaction(int returnVal) {

		try {
			if(file!=null) {
				title=(String)file.getName()+" - Text Editor for Fiji";
				if(file.getName().endsWith(".java")) textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
				if(file.getName().endsWith(".js")) textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
				if(file.getName().endsWith(".py")) textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PYTHON);
				if(file.getName().endsWith(".rb")) textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_RUBY);
				if(file.getName().endsWith(".clj")) {}
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
					//System.out.println(s);
					textArea.append(s+"\n");
					//ba.textArea.setText(s);
				}
			}
			else if(returnVal != fcc.APPROVE_OPTION) {
					System.out.println("Saving Canceled");
			}

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
			String path="";
			String directory="";

			try{

					SaveDialog sd = new SaveDialog("Save as ","new",".java");
					String name = sd.getFileName();
					if(name!=null) {
						directory = sd.getDirectory();
						path = directory+name;
						returnVal=JFileChooser.APPROVE_OPTION;
					}

         			   if (returnVal == JFileChooser.APPROVE_OPTION) {

							if(!Prefs.useJFileChooser) {
								file = new File(path);
								f = new File(directory);
							}
							else {
								file = fcc.getSelectedFile();
	         					f = fcc.getCurrentDirectory();
							}
							String[] filenames;
          					filenames = f.list();
          					for(int i=0; i< filenames.length; i++) {
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
								if(file.getName().endsWith(".java")) textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
								if(file.getName().endsWith(".js")) textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
								if(file.getName().endsWith(".py")) textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PYTHON);
								if(file.getName().endsWith(".rb")) textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_RUBY);
								if(file.getName().endsWith(".clj")) {}
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
		//System.out.println("here window");

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
	 * Creates the completion provider for the editor.  This provider can be
	 * shared among multiple editors.
	 *
	 * @return The provider.
	 */
	private CompletionProvider createCompletionProvider() {


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




