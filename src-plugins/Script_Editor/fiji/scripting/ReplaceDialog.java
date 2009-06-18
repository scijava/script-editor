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
import javax.swing.BorderFactory;
import javax.imageio.*;
import java.util.Arrays;
import java.util.List;
import javax.swing.text.*;
import org.fife.ui.rtextarea.*;
import org.fife.ui.rsyntaxtextarea.*;
import org.fife.ui.autocomplete.*;
import java.lang.Object;


public class ReplaceDialog extends JDialog implements ActionListener {
	JCheckBox regexCB;
	JTextField searchField;
	JCheckBox matchCaseCB;
	JCheckBox markAllCheckBox;
	JCheckBox wholeWordCheckBox;
	JTextField replaceField;
	//JTextField searchField;
	RSyntaxTextArea textArea;

	public ReplaceDialog(TextEditor editor,RSyntaxTextArea textArea){

			this.textArea=textArea;
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
			cancelButton.setActionCommand("Cancel");
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
			setContentPane(temp);
			getRootPane().setDefaultButton(findNextButton);
			setIconImage(editor.getIconImage());
			setTitle("ReplaceDialogTitle");
			setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			applyComponentOrientation(orientation);
	}

	public void actionPerformed(ActionEvent e){
		String action=e.getActionCommand();
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
        		boolean found = SearchEngine.find(textArea, text, forward,matchCase, wholeWord, regex);
         		if (!found) {
            			JOptionPane.showMessageDialog(this, "Fiji has finished searching the document");
						textArea.setCaretPosition(0);
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
			boolean replace =SearchEngine.replace(textArea, text,replaceField.getText(), forward,matchCase, wholeWord, regex);
			if (!replace) {
				JOptionPane.showMessageDialog(this, "Fiji has finished searching the document");
				textArea.setCaretPosition(0);
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
			int replace =SearchEngine.replaceAll(textArea, text,replaceText,matchCase, wholeWord, regex);
			JOptionPane.showMessageDialog(this, replace+" replacements made!");

		}
		if(action=="Cancel"){
			this.dispose();
		}

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





