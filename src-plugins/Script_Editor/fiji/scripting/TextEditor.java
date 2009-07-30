package fiji.scripting;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.JTextArea;
import javax.swing.JPanel;
import javax.swing.JFileChooser;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JMenuBar;
import javax.swing.BorderFactory;
import javax.swing.KeyStroke;
import javax.swing.ToolTipManager;
import javax.swing.JOptionPane;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.BorderLayout;
import java.awt.event.WindowEvent;
import java.awt.event.KeyEvent;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.InputMethodEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseListener;
import java.awt.event.InputMethodListener;
import java.awt.event.WindowListener;
import javax.swing.event.ChangeListener;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ChangeEvent;
import javax.swing.event.CaretEvent;
import javax.swing.text.Document;
import java.io.File;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.FileWriter;
import java.io.IOException;
import java.awt.image.BufferedImage;
import ij.io.OpenDialog;
import ij.io.SaveDialog;
import ij.IJ;
import ij.Prefs;
import javax.imageio.ImageIO;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.fife.ui.rtextarea.ToolTipSupplier;
import org.fife.ui.rtextarea.IconGroup;
import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.CompletionProvider;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import common.RefreshScripts;
import com.sun.jdi.connect.VMStartException;

import fiji.scripting.completion.ClassCompletionProvider;
import fiji.scripting.completion.DefaultProvider;


class TextEditor extends JFrame implements ActionListener , ItemListener , ChangeListener ,MouseMotionListener,MouseListener ,CaretListener,InputMethodListener,DocumentListener,WindowListener	{

	JFileChooser fcc;                                                   //using filechooser
	boolean fileChanged=false;
	boolean isFileUnnamed=true;
   	String title="";
	String language=new String();
	InputMethodListener l;
   	File file,f;
   	CompletionProvider provider1;
   	RSyntaxTextArea textArea;
	JTextArea screen=new JTextArea();
   	Document doc;
	JMenuItem new1,open,save,saveas,compileAndRun,debug,quit,undo,redo,cut,copy,paste,find,replace,selectAll,autocomplete,jfcdialog,ijdialog,resume,terminate;
	JRadioButtonMenuItem[] lang=new JRadioButtonMenuItem[7];
	FileInputStream fin;
    FindAndReplaceDialog replaceDialog;
	AutoCompletion autocomp;
	LanguageScriptMap scriptmap=new LanguageScriptMap();
	ClassCompletionProvider provider;
	StartDebugging debugging;
	Gutter gutter;
	IconGroup iconGroup;

	public TextEditor(String path1) {
		fcc = new JFileChooser();                                        //For the file opening saving things
		JPanel cp = new JPanel(new BorderLayout());
      		title="Text Editor for Fiji";
		textArea = new RSyntaxTextArea();
      		textArea.addInputMethodListener(l);
		textArea.addCaretListener(this);
      		textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
		if(provider1==null) {
			provider1 = createCompletionProvider();
		}
      	autocomp=new AutoCompletion(provider1);
	  	autocomp.setListCellRenderer(new CCellRenderer());
		autocomp.setShowDescWindow(true);
		autocomp.setParameterAssistanceEnabled(true);
      	autocomp.install(textArea);
	  	textArea.setToolTipSupplier((ToolTipSupplier)provider);
		ToolTipManager.sharedInstance().registerComponent(textArea);
      	doc=textArea.getDocument();
      	doc.addDocumentListener(this);
		RTextScrollPane sp = new RTextScrollPane(textArea);
		sp.setPreferredSize(new Dimension(600,350));
		//	cp.add(sp);
		System.out.println(sp.isIconRowHeaderEnabled());
		sp.setIconRowHeaderEnabled(true);
		gutter=sp.getGutter();
		System.out.println(gutter.isBookmarkingEnabled());
		iconGroup=new IconGroup("bullets","images/",null,"png",null);
		gutter.setBookmarkIcon(iconGroup.getIcon("var"));
		gutter.setBookmarkingEnabled(true);
		if(gutter.getBookmarkIcon()==null) {
			System.out.println("It is disabled");
		}
		screen.setEditable(false);
		screen.setLineWrap(true);
		Font font = new Font("Courier", Font.PLAIN, 12);
		screen.setFont(font);
		JScrollPane scroll = new JScrollPane(screen);
		scroll.setPreferredSize(new Dimension(600,80));


      	JSplitPane panel = new JSplitPane(JSplitPane.VERTICAL_SPLIT, sp, scroll);
		panel.setBorder(BorderFactory.createEmptyBorder(4,4,4,4));
		panel.setResizeWeight(350.0/430.0);
		setContentPane(panel);
      	setTitle(title);
      	addWindowListener(this);
      	setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

	/*********** Creating the menu options in the text editor ****************/

		JMenuBar mbar = new JMenuBar();
        setJMenuBar(mbar);

      /*******  creating the menu for the File option **********/
		JMenu file = new JMenu("File");
        file.setMnemonic(KeyEvent.VK_F);
        addToMenu(file,new1,"New",0,KeyEvent.VK_N, ActionEvent.CTRL_MASK);
        addToMenu(file,open,"Open...",0,KeyEvent.VK_O, ActionEvent.CTRL_MASK);
        addToMenu(file,save,"Save",0,KeyEvent.VK_S, ActionEvent.CTRL_MASK);
		addToMenu(file,save,"Save as...",1,0,0);
        file.addSeparator();
		JMenu run = new JMenu("Run");
		addToMenu(run,compileAndRun,"Compile and Run",1,0,0);
		run.addSeparator();
		addToMenu(run,debug,"Start Debugging",1,0,0);
		file.add(run);
		file.addSeparator();
		addToMenu(file,quit,"Quit",0,KeyEvent.VK_X, ActionEvent.ALT_MASK);
        
        mbar.add(file);

        /********The file menu part ended here  ***************/

        /*********The Edit menu part starts here ***************/

		JMenu edit = new JMenu("Edit");
        addToMenu(edit,undo,"Undo",0,KeyEvent.VK_Z, ActionEvent.CTRL_MASK);
        addToMenu(edit,redo,"Redo",0,KeyEvent.VK_Y, ActionEvent.CTRL_MASK);
		edit.addSeparator();
        addToMenu(edit,cut,"Cut",0,KeyEvent.VK_X, ActionEvent.CTRL_MASK);
        addToMenu(edit,copy,"Copy",0,KeyEvent.VK_C, ActionEvent.CTRL_MASK);
        addToMenu(edit,paste,"Paste",0,KeyEvent.VK_V, ActionEvent.CTRL_MASK);
		edit.addSeparator();
        addToMenu(edit,find,"Find...",0,KeyEvent.VK_F, ActionEvent.CTRL_MASK);
        addToMenu(edit,replace,"Find and Replace...",0,KeyEvent.VK_H, ActionEvent.CTRL_MASK);
		edit.addSeparator();
        addToMenu(edit,selectAll,"Select All",0,KeyEvent.VK_A, ActionEvent.CTRL_MASK);
       	mbar.add(edit);
        
        /******** The Edit menu part ends here *****************/

		/********The options menu part starts here**************/
		JMenu options = new JMenu("Options");
		addToMenu(options,autocomplete,"Autocomplete",0,KeyEvent.VK_SPACE, ActionEvent.CTRL_MASK);
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

		lang[0] = new JRadioButtonMenuItem("Java");
		lang[0].setMnemonic(KeyEvent.VK_J);
		lang[1] = new JRadioButtonMenuItem("Javascript");
		lang[1].setMnemonic(KeyEvent.VK_J);
		lang[2] = new JRadioButtonMenuItem("Python");
		lang[2].setMnemonic(KeyEvent.VK_P);
		lang[3] = new JRadioButtonMenuItem("Ruby");
		lang[3].setMnemonic(KeyEvent.VK_R);
		lang[4] = new JRadioButtonMenuItem("Clojure");
		lang[4].setMnemonic(KeyEvent.VK_C);
		lang[5] = new JRadioButtonMenuItem("Matlab");
		lang[5].setMnemonic(KeyEvent.VK_M);
		lang[6] = new JRadioButtonMenuItem("None");
		lang[6].setMnemonic(KeyEvent.VK_N);
		lang[6].setSelected(true);

		ButtonGroup group = new ButtonGroup();
		for(int i=0;i<7;i++) {

			group.add(lang[i]);
			language.add(lang[i]);
			lang[i].addActionListener(this);

		}
		mbar.add(language);

		JMenu breakpoints=new JMenu("Breakpoints");
		resume=new JMenuItem("Resume");
		resume.addActionListener(this);
		breakpoints.add(resume);
		terminate = new JMenuItem("Terminate");
		terminate.addActionListener(this);
		breakpoints.add(terminate);
		mbar.add(breakpoints);




      /*********** The menu part ended here    ********************/

      	pack();
	  	getToolkit().setDynamicLayout(true);            //added to accomodate the autocomplete part
		setLocationRelativeTo(null);
		setVisible(true);
		if(!(path1.equals("")||path1==null)) {
			open(path1);
		}
   	}

	public void addToMenu(JMenu menu,JMenuItem menuitem,String menuEntry,int keyEvent,int keyevent,int actionevent) {
		menuitem=new JMenuItem(menuEntry);
		menu.add(menuitem);
		if(keyEvent==0)
			menuitem.setAccelerator(KeyStroke.getKeyStroke(keyevent,actionevent));
		menuitem.addActionListener(this);
	}

	public void createNewDocument() {
		doc.removeDocumentListener(this);
		textArea.setText("");
		setTitle("TextEditor for Fiji");
		isFileUnnamed=true;
		fileChanged=false;
		doc.addDocumentListener(this);
	}

	public int handleUnsavedChanges() {
		if(fileChanged==true) {
			int val= JOptionPane.showConfirmDialog(this, "Do you want to save changes??");
			if(val==JOptionPane.CANCEL_OPTION) {
				return 0;
			}
			else if(val==JOptionPane.YES_OPTION){
				if(save()!=JFileChooser.APPROVE_OPTION)
					return 0;
			}
			else if(val!=JOptionPane.NO_OPTION) {
				return 0;
			}
		}
		return 1;
	}

	public void actionPerformed(ActionEvent ae) {
		String command=ae.getActionCommand();
		if(command.equals("New")){
			if(handleUnsavedChanges()==0)
				return;
			else
				createNewDocument();
		}

		if (command.equals("Open...")) {
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


				/*condition to check whether there is a change and the 
			 	* user has really opted to open a new file
			 	*/
			if(returnVal==fcc.APPROVE_OPTION) {
				if(handleUnsavedChanges()==0)
					return;
				else
					open(path);
			}
        
		}
		if(command.equals("Save")) {
			int temp=save();
		}
		if(command.equals("Save as..."))  {
			int temp= saveasaction();                   //temp for the int return type of the function nothing else
		}
		if(command.equals("Compile and Run")) {
			runScript();
		}
		if(command.equals("Start Debugging")) {
			BreakpointManager manager=new BreakpointManager(gutter,textArea,iconGroup);
			debugging=new StartDebugging(file.getPath(),manager.findBreakpointsLineNumber());

			try {
				System.out.println(debugging.startDebugging().exitValue());
			} 
			catch(Exception e){}
		}
		if(command.equals("Quit")) {
			processWindowEvent( new WindowEvent(this, WindowEvent.WINDOW_CLOSING) );
		}
         		     	   
    	if(command.equals("Cut")) {
			textArea.cut();
		}
		if(command.equals("Copy")) {
			textArea.copy();
		}
		if(command.equals("Paste")) {
			textArea.paste();
		}
		if(command.equals("Undo")) {
			textArea.undoLastAction();
		}
		if(command.equals("Redo")) {
			textArea.redoLastAction();
		}
		if(command.equals("Find...")) {

			setFindAndReplace(false);
		}
		if(command.equals("Replace..")) {						//here should the code to close all other dialog boxes
			try{
				setFindAndReplace(true);

			} catch(Exception e){ e.printStackTrace(); }
		}


		if(command.equals("Select All")) {
			textArea.setCaretPosition(0);
			textArea.moveCaretPosition(textArea.getDocument().getLength());
		}

		if(command.equals("Autocomplete")) {
			try{
				autocomp.doCompletion();
			} catch(Exception e){}
		}
		if(command.equals("JFileChooser")) {
			//Prefs.set(Prefs.OPTIONS,32);
			Prefs.useJFileChooser=true;
			Prefs.savePreferences();
		}
		if(command.equals("IJ")) {
			Prefs.useJFileChooser=false;
			Prefs.savePreferences();
		}



		if(command.equals("Java")) {
			textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
			provider.setProviderLanguage("Java");
		}
		if(command.equals("Javascript")) {
			textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
			provider.setProviderLanguage("Javascript");
		}
		if(command.equals("Python")) {
			textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PYTHON);
			provider.setProviderLanguage("Python");
		}
		if(command.equals("Ruby")) {
			textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_RUBY);
			provider.setProviderLanguage("Ruby");
		}
		if(command.equals("Clojure")) {
			((RSyntaxDocument)textArea.getDocument()).setSyntaxStyle(new ClojureTokenMaker());
			provider.setProviderLanguage("Clojure");
		}
		if(command.equals("Matlab")) {
			((RSyntaxDocument)textArea.getDocument()).setSyntaxStyle(new MatlabTokenMaker());
			provider.setProviderLanguage("Matlab");
		}
		if(command.equals("None")) {
			textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
			provider.setProviderLanguage("None");
		}
		if(command.equals("Resume")) {
			debugging.resumeVM();
		}
		if(command.equals("Terminate")) {
		}

	}

	/*
	 * this function performs the file opening operation
	 */

	public void setFindAndReplace(boolean ifReplace) {
		if(replaceDialog!=null){						//here should the code to close all other dialog boxes 
			if(replaceDialog.isReplace()!=ifReplace) {
				replaceDialog.dispose();
				replaceDialog=null;
			}
		}
		if(replaceDialog==null){
			replaceDialog=new FindAndReplaceDialog(this,textArea,ifReplace);
			replaceDialog.setResizable(true);
			replaceDialog.pack();
			replaceDialog.setLocationRelativeTo(this);
		}
		replaceDialog.show();
		replaceDialog.toFront();
	}

	public void open(String path) {

		try {
				file = new File(path);
		}
		catch(Exception e){System.out.println("problem in opening");}
		doc.removeDocumentListener(this);
		try {
			if(file!=null) {
				fileChanged=false;
				setLanguage(file);
				isFileUnnamed=false;
				/*changing the title part ends*/
				fin = new FileInputStream(file);
				BufferedReader din = new BufferedReader(new InputStreamReader(fin)); 
				String s = "";

				textArea.setText("");
				while(true) {
					s = din.readLine();
					if(s==null) {
						break;
					}
					textArea.append(s+"\n");
				}
			}
			else {
				JOptionPane.showMessageDialog(this,"The file name "+file.getName()+" not found.");
			}

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
			doc.addDocumentListener(this);
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
		SaveDialog sd = new SaveDialog("Save as ","new",".java");
		String name = sd.getFileName();
		if(name!=null) {
			String path=sd.getDirectory()+name;
			return(saveAs(path,checkForReplace(sd.getDirectory(),name)));

		}
		else {
			return -1;
		}
	}

	public boolean checkForReplace(String directory,String name) {
		boolean ifReplaceFile=false;
		if(!Prefs.useJFileChooser) {
			f = new File(directory);
		}
		else {
   			f = fcc.getCurrentDirectory();
		}
		String[] filenames;
		filenames = f.list();
		for(int i=0; i< filenames.length; i++) {
			if(filenames[i].equals(name)) {
				ifReplaceFile=true;
				break;
			}
		}
		return ifReplaceFile;
	}

	public void saveAs(String path) {
		saveAs(path,true);
	}

	public int saveAs(String path,boolean ifReplaceFile) {
		if(!Prefs.useJFileChooser) {
			file=new File(path);
		}
		else {
			file=fcc.getSelectedFile();
		}
		try{
			if(ifReplaceFile) {
				int val= JOptionPane.showConfirmDialog(this, "Do you want to replace "+file.getName()+"??","Do you want to replace "+file.getName()+"??",JOptionPane.YES_NO_OPTION); 
				if(val==JOptionPane.YES_OPTION) {
					title=(String)file.getName()+" - Text Editor Demo for fiji";
					setTitle(title);
					writeToFile(file);
				}
				else 
					return -1;
			}
			else {
				setLanguage(file);
				writeToFile(file);
			}          
        
		}catch(Exception e){
				e.printStackTrace();
		}
		return JFileChooser.APPROVE_OPTION;
	}
        /*It performs save in either new or the same file depending on whether there was a 
	 * file initially
	 */
	public int save(){

		if(isFileUnnamed) {
			return(saveasaction());         //temp has no use just because saveasaction has a int return type  
		}
		else {
			if(fileChanged) 
				setTitle(getTitle().substring(1));
			writeToFile(file);
			return JFileChooser.APPROVE_OPTION;
		}
	}

	public void writeToFile(File file) {
		try {
			fileChanged=false;
			BufferedWriter outFile = new BufferedWriter( new FileWriter( file ) );
			outFile.write( textArea.getText( ) ); //put in textfile
			outFile.flush( ); // redundant, done by close()
			outFile.close( );
		}
		catch(Exception e){}
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

	public void caretUpdate(CaretEvent ce) {}

	//next function is for the InputMethodEvent changes
	public void inputMethodTextChanged(InputMethodEvent event) {
		if(!fileChanged)
			setTitle("*"+getTitle());
		fileChanged=true;
	}
	public void caretPositionChanged(InputMethodEvent event) {
		if(!fileChanged)
			setTitle("*"+getTitle());
		fileChanged=true;
	}

	//Its the real meat the Document Listener functions raise the flag when the text is modifiedd
	public void insertUpdate(DocumentEvent e) {
		if(!fileChanged)
			setTitle("*"+getTitle());
		fileChanged=true;
	}
	public void removeUpdate(DocumentEvent e) {
		if(!fileChanged)
			setTitle("*"+getTitle());
		fileChanged=true;
	}
	public void changedUpdate(DocumentEvent e) {}
	public void windowActivated(WindowEvent e) {}

	public void setLanguage(File file) {

		title=(String)file.getName()+" - Text Editor Demo for fiji";
		setTitle(title);
		if(file.getName().endsWith(".java")){ 
			textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA); 
			lang[0].setSelected(true);
			provider.setProviderLanguage("Java");
		}
		if(file.getName().endsWith(".js")) {
			textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT); 
			lang[1].setSelected(true);
			provider.setProviderLanguage("Javascript");
		}
		if(file.getName().endsWith(".m")) {
			((RSyntaxDocument)textArea.getDocument()).setSyntaxStyle(new MatlabTokenMaker());
			lang[5].setSelected(true);
			provider.setProviderLanguage("Matlab");
		}
		if(file.getName().endsWith(".py")) {
			textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PYTHON);
			lang[2].setSelected(true);
			provider.setProviderLanguage("Python");
		}
		if(file.getName().endsWith(".rb")) {
			textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_RUBY);
			lang[3].setSelected(true);
			provider.setProviderLanguage("Ruby");
		}
		if(file.getName().endsWith(".clj")) {
			((RSyntaxDocument)textArea.getDocument()).setSyntaxStyle(new ClojureTokenMaker());
			lang[4].setSelected(true);
			provider.setProviderLanguage("Clojure");
		}

	}

	public void runScript() {
		if(fileChanged||isFileUnnamed) {
			int val= JOptionPane.showConfirmDialog(this, "You must save the changes before running.Do you want to save changes??","Select an Option",JOptionPane.YES_NO_OPTION);
			if(val!=JOptionPane.YES_OPTION)
				return;
			else {
					int temp=save();       //temp saves here whether the option was Approved :)
					if (temp != JFileChooser.APPROVE_OPTION) 
						return;
			}
		}
		runSavedScript();
	}

	public void runSavedScript() {

		String extension="";
		String fileName=(String)file.getName();
		int i= fileName.lastIndexOf(".");
		if(i>0)
			extension=fileName.substring(i);
		RefreshScripts refreshClass =scriptmap.get(fileName.substring(i));
		if (refreshClass == null) IJ.error("Booh!"); 
		else refreshClass.runScript(file.getPath());


	}

	public void windowClosing(WindowEvent e) {

		if(fileChanged) {
			int val= JOptionPane.showConfirmDialog(this, "Do you want to save changes??"); 
			if(val==JOptionPane.CANCEL_OPTION) {
				setVisible(true);
				return;
			}
			if(val==JOptionPane.YES_OPTION){
				if (save() != JFileChooser.APPROVE_OPTION) {
					return;
				}
			}
		}
		this.dispose();

	}
	public void windowClosed(WindowEvent e) {}
	public void windowDeactivated(WindowEvent e) {}
	public void windowDeiconified(WindowEvent e) {}
	public void windowIconified(WindowEvent e) {}
	public void windowOpened(WindowEvent e) {}

/*autocomplete addition starts here*/


	private CompletionProvider createCommentCompletionProvider() {
		DefaultCompletionProvider cp = new DefaultCompletionProvider();
		cp.addCompletion(new BasicCompletion(cp, "TODO:", "A to-do reminder"));
		cp.addCompletion(new BasicCompletion(cp, "FIXME:", "A bug that needs to be fixed"));
		return cp;
	}



	private CompletionProvider createCompletionProvider() {


		// The provider used when typing a string.
		CompletionProvider stringCP = createStringCompletionProvider();

		// The provider used when typing a comment.
		CompletionProvider commentCP = createCommentCompletionProvider();

		// Create the "parent" completion provider.
		System.out.println("The language at the completion provider definition is "+language);
		provider = new ClassCompletionProvider(new DefaultProvider(),textArea,language);
		provider.setStringCompletionProvider(stringCP);
		provider.setCommentCompletionProvider(commentCP);

		return provider;

	}




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




