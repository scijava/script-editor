//package fiji.scripting;
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
import javax.swing.BorderFactory;
/*for the cell renderer part*/
import java.net.MalformedURLException;
import java.net.URL;
/*cell renderer part ends here*/
import javax.imageio.*;
import java.awt.geom.AffineTransform;
import java.util.Arrays;
import java.util.List;
import javax.swing.text.*;
import org.fife.ui.rtextarea.*;
import org.fife.ui.rsyntaxtextarea.*;
import org.fife.ui.autocomplete.*;

class TextEditor extends JFrame implements ActionListener , ItemListener , ChangeListener ,MouseMotionListener,MouseListener ,CaretListener,InputMethodListener,DocumentListener,WindowListener
{

   private static final long serialVersionUID = 1L;
   JFileChooser fcc;                                                   //using filechooser
   String action="";
   boolean fileChange=false;
   String title="";
   InputMethodListener l;
   File file;
   CompletionProvider provider;
   RSyntaxTextArea textArea;
   Document doc;
   private JEditorPane ep;
   JCheckBox regexCB;
   JTextField searchField;
   JCheckBox matchCaseCB;
	JCheckBox markAllCheckBox;
	JCheckBox wholeWordCheckBox;
	JTextField replaceField;
	JDialog findDialog;
	JDialog replaceDialog;

    public static void main(String[] args) {
      // Start all Swing applications on the EDT.
      SwingUtilities.invokeLater(new Runnable() {
         public void run() {
            new TextEditor();
         }
      });
   }

   //public void run(String arg0){
	   

   public TextEditor() {
	fcc = new JFileChooser();                                        //For the file opening saving things

      JPanel cp = new JPanel(new BorderLayout());
      title="Text Editor Demo for Fiji";
		ep= new JEditorPane("text/html", null);
		updateEditorPane();
      textArea = new RSyntaxTextArea();
      textArea.addInputMethodListener(l);
      textArea.addCaretListener(this);
      textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_C);
	  String words[]={"public","private","protected","println","static","System","Swing","void","main","catch","class"};
      DefaultCompletionProvider provider1 =new DefaultCompletionProvider(words);
	  //CCompletionProvider provider =new CCompletionProvider(provider1);
	  //CompletionProvider prov=provider.getProviderFor(textArea);
		if(provider==null)
			provider = createCompletionProvider();

      AutoCompletion autocomp=new AutoCompletion(provider);
	 
	  autocomp.setListCellRenderer(new CCellRenderer());
		autocomp.setShowDescWindow(true);
		autocomp.setParameterAssistanceEnabled(true);
      autocomp.install(textArea);
	  textArea.setToolTipSupplier((ToolTipSupplier)provider);
		ToolTipManager.sharedInstance().registerComponent(textArea);
      doc=textArea.getDocument();
      doc.addDocumentListener(this);
     // super.fireCaretUpdate(CaretEvent ce);
      RTextScrollPane sp = new RTextScrollPane(textArea);
      cp.add(sp);

      	/********* This part is used to change the 
         	   the icon of the 
           	   window of the editor **********/

	BufferedImage image = null;
        try {
           // image = ImageIO.read(cp.getClass().getResource("/images/microscope.gif"));
	   image = ImageIO.read(new java.net.URL("file:images/icon.png"));
        } catch (IOException e) {
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
        JMenuItem quit = new JMenuItem("Quit...");
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
	JMenuItem redo = new JMenuItem("Redo...");
        edit.add(redo);
        redo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, ActionEvent.CTRL_MASK));
        redo.addActionListener(this);
        edit.addSeparator();
        JMenuItem cut = new JMenuItem("Cut...");
        edit.add(cut);
        cut.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, ActionEvent.CTRL_MASK));
        cut.addActionListener(this);
        edit.addSeparator();
	JMenuItem copy = new JMenuItem("Copy...");
        edit.add(copy);
        copy.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.CTRL_MASK));
        copy.addActionListener(this);
        edit.addSeparator();
	JMenuItem paste = new JMenuItem("Paste...");
        edit.add(paste);
        paste.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, ActionEvent.CTRL_MASK));
        paste.addActionListener(this);
	edit.addSeparator();
	JMenuItem find = new JMenuItem("Find...");
        edit.add(find);
        find.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, ActionEvent.CTRL_MASK));
        find.addActionListener(this);
	edit.addSeparator();
	JMenuItem replace = new JMenuItem("Find and Replace...");
        edit.add(replace);
        replace.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_H, ActionEvent.CTRL_MASK));
        replace.addActionListener(this);
	edit.addSeparator();
	JMenuItem selectAll = new JMenuItem("Select All...");
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



   		//test if the textArea is changed
	//on opening change textArea to new Rsyntaxtextarea to clean up the window and check for the flag
	//open an alert to check if the user wants to save in case the flag is true
	//creating the buttoned alert and depending on yes , no and cancel
	//now on closing the window checking if the user wants to save the file (only in case it is changed)
   
     /******* the function which accounts for the actions in the menu ************/
	public void actionPerformed(ActionEvent ae){

		action=ae.getActionCommand();
		 if (action=="Open...") {
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
			 if(fileChange==true&&returnVal==fcc.APPROVE_OPTION){
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

			else{
				doc.removeDocumentListener(this);
		       		openaction(returnVal);
				doc.addDocumentListener(this);
			}
        
		}
		if(action=="Save...") {

			fileChange=false;
			saveaction();
		}
		if(action=="Save as..."){

			fileChange=false;
			int temp= saveasaction();                   //temp for the int return type of the function nothing else
		}

		if(action=="Quit..."){
			processWindowEvent( new WindowEvent(this, WindowEvent.WINDOW_CLOSING) );
		}
         		     	   
    	if(action=="Cut..."){
			textArea.cut();
		}
		if(action=="Copy..."){
			textArea.copy();
		}
		if(action=="Paste..."){
			textArea.paste();
		}
		if(action=="Undo..."){
			textArea.undoLastAction();
		}
		if(action=="Redo..."){
			textArea.redoLastAction();
		}
		if(action=="Find and Replace..."){

			ComponentOrientation orientation = ComponentOrientation.getOrientation(getLocale());

			// Make a panel containing the "Find" edit box.
			regexCB = new JCheckBox("Regex");
			matchCaseCB = new JCheckBox("Match Case");
			markAllCheckBox = new JCheckBox("Mark All");
			wholeWordCheckBox=new JCheckBox("Whole Word");
			JPanel enterTextPane = new JPanel(new SpringLayout());
			//enterTextPane.setBorder(BorderFactory.createEmptyBorder(0,5,5,5));
			searchField=new JTextField();
			replaceField=new JTextField();
			//JTextComponent textField = getTextComponent(findTextComboBox);
			searchField.addFocusListener(new FindFocusAdapter());
			searchField.addKeyListener(new FindKeyListener());
			searchField.getDocument().addDocumentListener(new FindDocumentListener());
			JPanel temp = new JPanel(new BorderLayout());
			JLabel findFieldLabel=new JLabel("Find What");
			temp.add(searchField);
			replaceField.addFocusListener(new FindFocusAdapter());
			replaceField.addKeyListener(new FindKeyListener());
			replaceField.getDocument().addDocumentListener(new FindDocumentListener());
			JPanel temp2 = new JPanel(new BorderLayout());
			JLabel replaceFieldLabel=new JLabel("Replace with");
			temp2.add(replaceField);
			//temp.setBorder(new ContentAssistAvailableBorder(temp, searchField));
			temp.setBorder(BorderFactory.createLineBorder(Color.black));
			temp2.setBorder(BorderFactory.createLineBorder(Color.black));
			if (orientation.isLeftToRight()) {
				enterTextPane.add(findFieldLabel);
				enterTextPane.add(temp);
				enterTextPane.add(replaceFieldLabel);
				enterTextPane.add(temp2);
			}
			else {
				enterTextPane.add(temp);
				enterTextPane.add(findFieldLabel);
				enterTextPane.add(temp2);
				enterTextPane.add(replaceFieldLabel);
			}

			makeSpringCompactGrid(enterTextPane, 2, 2,	//rows, cols
												0,0,		//initX, initY
												6, 6);	//xPad, yPad

			// Make a panel containing the inherited search direction radio
			// buttons and the inherited search options.
			JPanel bottomPanel = new JPanel(new BorderLayout());
			temp = new JPanel(new BorderLayout());
			bottomPanel.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
			JPanel searchConditionsPanel=new JPanel();
			searchConditionsPanel.setLayout(new BorderLayout());
			JPanel temp1 = new JPanel();
			temp1.setLayout(new BoxLayout(temp1, BoxLayout.PAGE_AXIS));
			temp1.add(matchCaseCB);
			temp1.add(wholeWordCheckBox);
			searchConditionsPanel.add(temp1, BorderLayout.LINE_START);
			temp1 = new JPanel();
			temp1.setLayout(new BoxLayout(temp1, BoxLayout.PAGE_AXIS));
			temp1.add(regexCB);
			temp1.add(markAllCheckBox);
			searchConditionsPanel.add(temp1, BorderLayout.LINE_END);
			temp.add(searchConditionsPanel, BorderLayout.LINE_START);
			//temp.add(dirPanel);
			bottomPanel.add(temp, BorderLayout.LINE_START);

			// Now, make a panel containing all the above stuff.
			JPanel leftPanel = new JPanel();
			leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
			leftPanel.add(enterTextPane);
			leftPanel.add(bottomPanel);

			// Make a panel containing the action buttons.
			JPanel buttonPanel = new JPanel();
			buttonPanel.setLayout(new GridLayout(4,1, 5,5));
			JButton findNextButton = new JButton("Find Next");
			findNextButton.setActionCommand("Find Next");
			findNextButton.addActionListener(this);
			buttonPanel.add(findNextButton);
			JButton replaceButton = new JButton("Replace");
			replaceButton.setActionCommand("Replace");
			replaceButton.addActionListener(this);
			buttonPanel.add(replaceButton);
			JButton replaceAllButton = new JButton("Replace All");
			replaceAllButton.setActionCommand("Replace All");
			replaceAllButton.addActionListener(this);
			buttonPanel.add(replaceAllButton);
			JButton cancelButton = new JButton("Cancel");
			cancelButton.setActionCommand("replaceCancel");
			cancelButton.addActionListener(this);
			buttonPanel.add(cancelButton);
			JPanel rightPanel = new JPanel();
			rightPanel.setLayout(new BorderLayout());
			rightPanel.add(buttonPanel, BorderLayout.NORTH);

			// Put everything into a neat little package.
			JPanel contentPane = new JPanel(new BorderLayout());
			if (orientation.isLeftToRight()) {
				contentPane.setBorder(BorderFactory.createEmptyBorder(5,0,0,5));
			}
			else {
				contentPane.setBorder(BorderFactory.createEmptyBorder(5,5,0,0));
			}
			contentPane.add(leftPanel);
			contentPane.add(rightPanel, BorderLayout.LINE_END);
			//temp = new ResizableFrameContentPane(new BorderLayout());
			temp = new JPanel(new BorderLayout());
			temp.add(contentPane, BorderLayout.NORTH);
			replaceDialog=new JDialog(this);
			replaceDialog.setContentPane(temp);
			replaceDialog.getRootPane().setDefaultButton(findNextButton);
			replaceDialog.setTitle("ReplaceDialogTitle");
			replaceDialog.setResizable(true);
			replaceDialog.pack();
			replaceDialog.setLocationRelativeTo(this);

			replaceDialog.applyComponentOrientation(orientation);
			replaceDialog.show();

				/*JPanel panel = new JPanel(new BoxLayout());
					panel.setPreferredSize(new Dimension(300, 250));
					JPanel panel2 = new JPanel();
				panel2.setLayout(null);
				searchField=new JTextField();
				regexCB = new JCheckBox("Regex");
				matchCaseCB = new JCheckBox("Match Case");

				panel2.add(searchField);
				panel2.add(regexCB);
					panel2.add(matchCaseCB);
				JButton b = new JButton("Find Next");
					b.setActionCommand("Find Next");
					b.addActionListener(this);
					panel2.add(b);

				//init(panel);
					panel.add(panel2, "p2");
					JOptionPane.showMessageDialog(this, panel);*/


		}

		if(action=="Find..."){
		ComponentOrientation orientation = ComponentOrientation.
									getOrientation(getLocale());
		regexCB = new JCheckBox("Regex");
		matchCaseCB = new JCheckBox("Match Case");
		markAllCheckBox = new JCheckBox("Mark All");
		wholeWordCheckBox=new JCheckBox("Whole Word");
		JPanel enterTextPane = new JPanel(new SpringLayout());
		enterTextPane.setBorder(BorderFactory.createEmptyBorder(0,5,5,5));
		searchField=new JTextField();
		//JTextComponent textField = getTextComponent(findTextComboBox);
		searchField.addFocusListener(new FindFocusAdapter());
		searchField.addKeyListener(new FindKeyListener());
		searchField.getDocument().addDocumentListener(new FindDocumentListener());
		JPanel temp = new JPanel(new BorderLayout());
		JLabel findFieldLabel=new JLabel("Find What");
		temp.add(searchField);
		//temp.setBorder(new ContentAssistAvailableBorder(temp, searchField));
		temp.setBorder(BorderFactory.createLineBorder(Color.black));
		if (orientation.isLeftToRight()) {
			enterTextPane.add(findFieldLabel);
			enterTextPane.add(temp);
		}
		else {
			enterTextPane.add(temp);
			enterTextPane.add(findFieldLabel);
		}

		makeSpringCompactGrid(enterTextPane, 1, 2,	//rows, cols
											0,0,		//initX, initY
											6, 6);	//xPad, yPad

		// Make a panel containing the inherited search direction radio
		// buttons and the inherited search options.
		JPanel bottomPanel = new JPanel(new BorderLayout());
		temp = new JPanel(new BorderLayout());
		bottomPanel.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
		JPanel searchConditionsPanel=new JPanel();
		searchConditionsPanel.setLayout(new BorderLayout());
		JPanel temp1 = new JPanel();
		temp1.setLayout(new BoxLayout(temp1, BoxLayout.PAGE_AXIS));
		temp1.add(matchCaseCB);
		temp1.add(wholeWordCheckBox);
		searchConditionsPanel.add(temp1, BorderLayout.LINE_START);
		temp1 = new JPanel();
		temp1.setLayout(new BoxLayout(temp1, BoxLayout.PAGE_AXIS));
		temp1.add(regexCB);
		temp1.add(markAllCheckBox);
		searchConditionsPanel.add(temp1, BorderLayout.LINE_END);
		temp.add(searchConditionsPanel, BorderLayout.LINE_START);
		//temp.add(dirPanel);
		bottomPanel.add(temp, BorderLayout.LINE_START);

		// Now, make a panel containing all the above stuff.
		JPanel leftPanel = new JPanel();
		leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
		leftPanel.add(enterTextPane);
		leftPanel.add(bottomPanel);

		// Make a panel containing the action buttons.
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new GridLayout(2,1, 5,5));
		JButton findNextButton = new JButton("Find Next");
      	findNextButton.setActionCommand("Find Next");
     	findNextButton.addActionListener(this);
		buttonPanel.add(findNextButton);
		JButton cancelButton = new JButton("Cancel");
   		cancelButton.setActionCommand("findCancel");
   		cancelButton.addActionListener(this);
		buttonPanel.add(cancelButton);
		JPanel rightPanel = new JPanel();
		rightPanel.setLayout(new BorderLayout());
		rightPanel.add(buttonPanel, BorderLayout.NORTH);

		// Put everything into a neat little package.
		JPanel contentPane = new JPanel(new BorderLayout());
		if (orientation.isLeftToRight()) {
			contentPane.setBorder(BorderFactory.createEmptyBorder(5,0,0,5));
		}
		else {
			contentPane.setBorder(BorderFactory.createEmptyBorder(5,5,0,0));
		}
		contentPane.add(leftPanel);
		contentPane.add(rightPanel, BorderLayout.LINE_END);
		//temp = new ResizableFrameContentPane(new BorderLayout());
		temp = new JPanel(new BorderLayout());
		temp.add(contentPane, BorderLayout.NORTH);
		findDialog=new JDialog(this);
		findDialog.setContentPane(temp);
		findDialog.getRootPane().setDefaultButton(findNextButton);
		findDialog.setTitle("FindDialogTitle");
		findDialog.setResizable(true);
		findDialog.pack();
		findDialog.setLocationRelativeTo(this);

		findDialog.applyComponentOrientation(orientation);
		findDialog.show();

		}
		if(action=="Select All..."){
				textArea.setCaretPosition(0);
				textArea.moveCaretPosition(textArea.getDocument().getLength());
		}
		if(action=="Find Next"){
			String text = searchField.getText();
         		if (text.length() == 0) {
            			return;
         		}
         		boolean forward = true;
         		boolean matchCase = matchCaseCB.isSelected();
         		boolean wholeWord = wholeWordCheckBox.isSelected();
				boolean markAll=markAllCheckBox.isSelected();
         		boolean regex = regexCB.isSelected();
        		 boolean found = SearchEngine.find(textArea, text, forward,
               			matchCase, wholeWord, regex);
         		if (!found) {
            			JOptionPane.showMessageDialog(this, "Text not found");
         		}
		}
		if(action=="Replace"){
			String text = searchField.getText();
			if (text.length() == 0) {
				return;
			}
			boolean forward = true;
			boolean matchCase = matchCaseCB.isSelected();
			boolean wholeWord = wholeWordCheckBox.isSelected();
			boolean markAll=markAllCheckBox.isSelected();
			boolean regex = regexCB.isSelected();
			 boolean replace =SearchEngine.replace(textArea, text,replaceField.getText(), forward,
				matchCase, wholeWord, regex);
			if (!replace) {
				JOptionPane.showMessageDialog(this, "Text not found");
			}
		}

		if(action=="Replace All"){
			String text = searchField.getText();
			String replaceText=replaceField.getText();
			if (text.length() == 0) {
				return;
			}
			boolean forward = true;
			boolean matchCase = matchCaseCB.isSelected();
			boolean wholeWord = wholeWordCheckBox.isSelected();
			boolean markAll=markAllCheckBox.isSelected();
			boolean regex = regexCB.isSelected();
			int replace =SearchEngine.replaceAll(textArea, text,replaceText, 
				matchCase, wholeWord, regex);

				JOptionPane.showMessageDialog(this, replace+" replacements made!");

		}
		if(action=="replaceCancel"){
			replaceDialog.hide();
		}
		if(action=="findCancel"){
			findDialog.hide();
		}




	}

	/*
	 * this function performs the file opening operation
	 */

	public void openaction(int returnVal){
		try{
			title=(String)file.getName()+" - Text Editor Demo for fiji";
				this.setTitle(title);

				/*changing the title part ends*/

				FileInputStream fin = new FileInputStream(file);
				//DataInputStream din = new DataInputStream(fin);
				BufferedReader din = new BufferedReader(new InputStreamReader(fin)); 
				String s = "";
				if (returnVal == fcc.APPROVE_OPTION)
				{
					textArea.setText("");
					while(true)
					{

						s = din.readLine();
						if(s==null)
							break;
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
	/*This function performs the saveasoperation i.e. saving in the
	 * new file
	 * Basically the motive behind creating different
	 * functions is because they are used more than one time
	 * int return type is given just because in one instance of 
	 * its use the value of the button clicked in the save dialog 
	 * box was required.
	 */
	public int saveasaction(){
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
          					for(int i=0; i< filenames.length; i++){
							System.out.println(filenames[i]+" "+file.getName());
							if(filenames[i].equals(file.getName())){
								ifReplaceFile=true;
								break;
							}
						}
						if(ifReplaceFile){
								int val= JOptionPane.showConfirmDialog(this, "Do you want to replace "+file.getName()+"??","Do you want to replace "+file.getName()+"??",JOptionPane.YES_NO_OPTION); 
								if(val==JOptionPane.YES_OPTION){

									//changing the title again
					                        	title=(String)file.getName()+" - Text Editor Demo for fiji";
									this.setTitle(title);
									BufferedWriter outFile = new BufferedWriter( new FileWriter( file ) );
									outFile.write( textArea.getText( ) ); //put in textfile
									outFile.flush( ); // redundant, done by close()
									outFile.close( );
								}
						}
						else{
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
			catch(Exception e){}
			return returnVal;

	}
        /*It performs save in either new or the same file depending on whether there was a 
	 * file initially
	 */
	public void saveaction(){

		 if(title=="Text Editor Demo for Fiji"){
					  int temp= saveasaction();         //temp has no damn use just because saveasaction has a int return type  
				   }
				   else {
					   try{
						BufferedWriter outFile = new BufferedWriter( new FileWriter( file ) );
						outFile.write( textArea.getText( ) ); //put in textfile
						outFile.flush( ); // redundant, done by close()
						outFile.close( );
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

	//this function is for the caret position change not of much use so far
	public void caretUpdate(CaretEvent ce){
	 
//	fileChange=true;
	}

	//next function is for the InputMethodEvent changes
	public void inputMethodTextChanged(InputMethodEvent event){
	fileChange=true;
	}
	public void caretPositionChanged(InputMethodEvent event){
	fileChange=true;
	}

	//Its the real meat the Document Listener functions raise the flag when the text is modifiedd
	public void insertUpdate(DocumentEvent e){
		fileChange=true;
	}
	public void removeUpdate(DocumentEvent e){
		fileChange=true;
	}
	public void changedUpdate(DocumentEvent e){
	}

	public void windowActivated(WindowEvent e){
	}
	public void windowClosing(WindowEvent e){

		if(fileChange){
			int val= JOptionPane.showConfirmDialog(this, "Do you want to save changes??"); 
				if(val==JOptionPane.YES_OPTION){

					/*
					 * This all mess is just to ensure that 
					 * the window doesnot close on doing foll things stepwise
					 * step 1)trying to close window with a unsaved file
					 * step 2)cancelling the save dialog box
					 */
					if(title=="Text Editor Demo for Fiji"){
						int temp=saveasaction();       //temp saves here whether the option was Approved :)
						if (temp == JFileChooser.APPROVE_OPTION) {
							fileChange=false;
							this.dispose();
						}
					}
					else{
						fileChange=false;
						saveaction();
						this.dispose();
					}


				}
				if(val==JOptionPane.NO_OPTION){
					fileChange=false;
					this.dispose();

				}
				if(val==JOptionPane.CANCEL_OPTION){
					setVisible(true);
				}
			 }
		else {
			this.dispose();
		}

	}
	public void windowClosed(WindowEvent e){
	}
	public void windowDeactivated(WindowEvent e){
	}
	public void windowDeiconified(WindowEvent e){
	}
	public void windowIconified(WindowEvent e){
	}
	public void windowOpened(WindowEvent e){
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
		} catch (IOException ioe) {
			ioe.printStackTrace();
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
		CompletionProvider codeCP = createCodeCompletionProvider();

		// The provider used when typing a string.
		CompletionProvider stringCP = createStringCompletionProvider();

		// The provider used when typing a comment.
		CompletionProvider commentCP = createCommentCompletionProvider();

		// Create the "parent" completion provider.
		CCompletionProvider provider = new CCompletionProvider(codeCP);
		provider.setStringCompletionProvider(stringCP);
		provider.setCommentCompletionProvider(commentCP);

		return provider;

	}


	/**
	 * Returns the menu bar for the demo application.
	 *
	 * @return The menu bar.
	 */
	/*private JMenuBar createMenuBar() {

		JMenuBar mb = new JMenuBar();

		JMenu menu = new JMenu("File");
		Action newAction = new TextAction("New") {
			public void actionPerformed(ActionEvent e) {
				AutoCompleteDemoApp app2 = new AutoCompleteDemoApp(
												ac.getCompletionProvider());
				app2.setVisible(true);
			}
		};
		JMenuItem item = new JMenuItem(newAction);
		menu.add(item);
		mb.add(menu);

		menu = new JMenu("View");
		Action renderAction = new FancyCellRenderingAction();
		cellRenderingItem = new JCheckBoxMenuItem(renderAction);
		cellRenderingItem.setSelected(true);
		menu.add(cellRenderingItem);
		Action descWindowAction = new ShowDescWindowAction();
		showDescWindowItem = new JCheckBoxMenuItem(descWindowAction);
		showDescWindowItem.setSelected(true);
		menu.add(showDescWindowItem);
		Action paramAssistanceAction = new ParameterAssistanceAction();
		paramAssistanceItem = new JCheckBoxMenuItem(paramAssistanceAction);
		paramAssistanceItem.setSelected(true);
		menu.add(paramAssistanceItem);
		mb.add(menu);

		ButtonGroup bg = new ButtonGroup();
		menu = new JMenu("LookAndFeel");
		Action lafAction = new LafAction("System", UIManager.getSystemLookAndFeelClassName());
		JRadioButtonMenuItem rbmi = new JRadioButtonMenuItem(lafAction);
		rbmi.setSelected(true);
		menu.add(rbmi);
		bg.add(rbmi);
		lafAction = new LafAction("Motif", "com.sun.java.swing.plaf.motif.MotifLookAndFeel");
		rbmi = new JRadioButtonMenuItem(lafAction);
		menu.add(rbmi);
		bg.add(rbmi);
		lafAction = new LafAction("Ocean", "javax.swing.plaf.metal.MetalLookAndFeel");
		rbmi = new JRadioButtonMenuItem(lafAction);
		menu.add(rbmi);
		bg.add(rbmi);
		lafAction = new LafAction("Nimbus", "com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
		rbmi = new JRadioButtonMenuItem(lafAction);
		menu.add(rbmi);
		bg.add(rbmi);
		mb.add(menu);

		return mb;

	} */


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


	/**
	 * Updates the font used in the HTML, as well as the background color, of
	 * the "label" editor pane.  The font would always have to be done (since
	 * HTMLEditorKit doesn't use the editor pane's font by default), but the
	 * background only has to be modified because Nimbus doesn't clean up the
	 * colors it installs after itself.
	 */
	 
	 
	private void updateEditorPane() {
		Font f = UIManager.getFont("Label.font");
		String fontTag = "<body style=\"font-family: " + f.getFamily() +
					"; font-size: " + f.getSize() + "pt; \">";
		String text = "<html>" + fontTag + "" +
			"The text area below provides simple code completion for the C " +
			"programming language as you type. Simply type <b>Ctrl+Space</b> " +
			"at any time to see a list of completion choices (function names, "+
			"for example). If there is only one possible completion, it will " +
			"be automatically inserted.<p>" +
			"Also, completions are context-sensitive.  If you type Ctrl+Space" +
			"in a comment or in the middle of a string, you will get " +
			"different completion choices than if you are in code.";
		ep.setText(text);
		ep.setBorder(BorderFactory.createEmptyBorder(5,5,10,5));
		ep.setEditable(false);
		ep.setBackground(UIManager.getColor("Panel.background"));
	}

	public final void makeSpringCompactGrid(Container parent, int rows,
								int cols, int initialX, int initialY,
								int xPad, int yPad) {

		SpringLayout layout;
		try {
			layout = (SpringLayout)parent.getLayout();
		} catch (ClassCastException cce) {
			System.err.println("The first argument to makeCompactGrid " +
							"must use SpringLayout.");
			return;
		}

		//Align all cells in each column and make them the same width.
		Spring x = Spring.constant(initialX);
		for (int c = 0; c < cols; c++) {
			Spring width = Spring.constant(0);
			for (int r = 0; r < rows; r++) {
				width = Spring.max(width,
						getConstraintsForCell(
									r, c, parent, cols).getWidth());
			}
			for (int r = 0; r < rows; r++) {
				SpringLayout.Constraints constraints =
							getConstraintsForCell(r, c, parent, cols);
				constraints.setX(x);
				constraints.setWidth(width);
			}
			x = Spring.sum(x, Spring.sum(width, Spring.constant(xPad)));
		}

		//Align all cells in each row and make them the same height.
		Spring y = Spring.constant(initialY);
		for (int r = 0; r < rows; r++) {
			Spring height = Spring.constant(0);
			for (int c = 0; c < cols; c++) {
				height = Spring.max(height,
					getConstraintsForCell(r, c, parent, cols).getHeight());
			}
			for (int c = 0; c < cols; c++) {
				SpringLayout.Constraints constraints =
							getConstraintsForCell(r, c, parent, cols);
				constraints.setY(y);
				constraints.setHeight(height);
			}
			y = Spring.sum(y, Spring.sum(height, Spring.constant(yPad)));
		}

		//Set the parent's size.
		SpringLayout.Constraints pCons = layout.getConstraints(parent);
		pCons.setConstraint(SpringLayout.SOUTH, y);
		pCons.setConstraint(SpringLayout.EAST, x);

	}

	private final SpringLayout.Constraints getConstraintsForCell(
										int row, int col,
										Container parent, int cols) {
		SpringLayout layout = (SpringLayout) parent.getLayout();
		Component c = parent.getComponent(row * cols + col);
		return layout.getConstraints(c);
	}



	private class FindDocumentListener implements DocumentListener {

		public void insertUpdate(DocumentEvent e) {
			//handleToggleButtons();
		}

		public void removeUpdate(DocumentEvent e) {
			/*JTextComponent comp = getTextComponent(findTextComboBox);
			if (comp.getDocument().getLength()==0) {
				findNextButton.setEnabled(false);
			}
			else {
				handleToggleButtons();
			}*/
		}

		public void changedUpdate(DocumentEvent e) {
		}

	}


	/**
	 * Listens for the text field gaining focus.  All it does is select all
	 * text in the combo box's text area.
	 */
	private class FindFocusAdapter extends FocusAdapter {

		public void focusGained(FocusEvent e) {
			/*getTextComponent(findTextComboBox).selectAll();
			// Remember what it originally was, in case they tabbed out.
			lastSearchString = (String)findTextComboBox.getSelectedItem();*/
		}

	}


	/**
	 * Listens for key presses in the find dialog.
	 */
	private class FindKeyListener implements KeyListener {

		// Listens for the user pressing a key down.
		public void keyPressed(KeyEvent e) {
		}

		// Listens for a user releasing a key.
		public void keyReleased(KeyEvent e) {

			// This is an ugly hack to get around JComboBox's
			// insistance on eating the first Enter keypress
			// it receives when it has focus and its selected item
			// has changed since the last time it lost focus.
			/*if (e.getKeyCode()==KeyEvent.VK_ENTER && isPreJava6JRE()) {
				String searchString = (String)findTextComboBox.getSelectedItem();
				if (!searchString.equals(lastSearchString)) {
					findNextButton.doClick(0);
					lastSearchString = searchString;
					getTextComponent(findTextComboBox).selectAll();
				}
			}*/

		}

		// Listens for a key being typed.
		public void keyTyped(KeyEvent e) {
		}

	}



}


class CCellRenderer extends CompletionCellRenderer {

	private Icon variableIcon;
	private Icon functionIcon;
	private Icon emptyIcon;


	/**
	 * Constructor.
	 */
	public CCellRenderer() {
		variableIcon = getIcon("images/var.png");
		functionIcon = getIcon("images/function.png");
		emptyIcon = new EmptyIcon(16);
	}


	/**
	 * Returns an icon.
	 *
	 * @param resource The icon to retrieve.  This should either be a file,
	 *        or a resource loadable by the current ClassLoader.
	 * @return The icon.
	 */
	private Icon getIcon(String resource) {
		ClassLoader cl = getClass().getClassLoader();
		URL url = cl.getResource(resource);
		if (url==null) {
			File file = new File(resource);
			try {
				url = file.toURI().toURL();
			} catch (MalformedURLException mue) {
				mue.printStackTrace(); // Never happens
			}
		}
		return url!=null ? new ImageIcon(url) : null;
	}


	/**
	 * {@inheritDoc}
	 */
	protected void prepareForOtherCompletion(JList list,
			Completion c, int index, boolean selected, boolean hasFocus) {
		super.prepareForOtherCompletion(list, c, index, selected, hasFocus);
		setIcon(emptyIcon);
	}


	/**
	 * {@inheritDoc}
	 */
	protected void prepareForVariableCompletion(JList list,
			VariableCompletion vc, int index, boolean selected,
			boolean hasFocus) {
		super.prepareForVariableCompletion(list, vc, index, selected,
										hasFocus);
		setIcon(variableIcon);
	}


	/**
	 * {@inheritDoc}
	 */
	protected void prepareForFunctionCompletion(JList list,
			FunctionCompletion fc, int index, boolean selected,
			boolean hasFocus) {
		super.prepareForFunctionCompletion(list, fc, index, selected,
										hasFocus);
		setIcon(functionIcon);
	}


	/**
	 * An standard icon that doesn't paint anything.  This can be used to take
	 * up an icon's space when no icon is specified.
	 *
	 * @author Robert Futrell
	 * @version 1.0
	 */
	private static class EmptyIcon implements Icon, Serializable {

		private int size;

		public EmptyIcon(int size) {
			this.size = size;
		}

		public int getIconHeight() {
			return size;
		}

		public int getIconWidth() {
			return size;
		}

		public void paintIcon(Component c, Graphics g, int x, int y) {
		}

	}


}



