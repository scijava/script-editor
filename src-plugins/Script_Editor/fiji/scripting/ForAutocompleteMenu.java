package fiji.scripting;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.CompletionProvider;

public class ForAutocompleteMenu extends AutoCompletion implements ActionListener {

	public ForAutocompleteMenu(CompletionProvider provider) {
		super(provider);
	}


	public void actionPerformed(ActionEvent ae) {
		String action = ae.getActionCommand();
		if (action.equals("Autocomplete")) {
			System.out.println("yes menu running");
			int caret = refreshPopupWindow();
		}
	}
}
