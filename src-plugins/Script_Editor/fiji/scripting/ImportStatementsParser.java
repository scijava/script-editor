package fiji.scripting;

import org.fife.ui.rsyntaxtextarea.*;
import java.util.ArrayList;
import javax.swing.text.Element;

public class ImportStatementsParser {

	ArrayList<String> packageNames=new ArrayList<String>();


	public ArrayList<String> getPackageNames() {
		return packageNames;
	}

	public void objCompletionPackages(RSyntaxTextArea textArea,String language) {

		RSyntaxDocument doc = (RSyntaxDocument)textArea.getDocument();
		Element map=doc.getDefaultRootElement();
		int startLine = map.getElementIndex(0);
		int endLine = map.getElementIndex(doc.getLength());
		for(int i= startLine;i<=endLine;i++) {
			boolean isAfterImportLine=true;
			Token token=doc.getTokenListForLine(i);
			String packageName="";
			boolean isAfterImport=false;
			boolean isAfterPythonImport=false;
			for(;token!=null&&token.type!=0;token=token.getNextToken()) {

				if(token.type==16||token.type==3||token.type==2||token.type==1) {      //for white space 
					continue;
				}
				if(token.type==3||token.type==2||token.type==1) {						//for comments
					isAfterImportLine=false;
					continue;
				}
				if((token.type==4&&token.getLexeme().equals("package"))) {
					isAfterImportLine=false;
					break;
				}
				if(language.equals("Java")) {
					if(token.type==4&&token.getLexeme().equals("import")) {
						isAfterImportLine=false;
						isAfterImport=true;
						continue;
					}
				}

				//For javascript languaguge import statements
				if(language.equals("Javascript")) {
					if(token.type==15&&(token.getLexeme().equals("importPackage")||token.getLexeme().equals("importClass"))) {
						isAfterImport=true;
						isAfterImportLine=false;
						continue;
					}
					if(token.getLexeme().equals("(")||token.getLexeme().equals(")"))         //for javascript import statements
						continue;
				}
				//For Ruby language import statements
				if(language.equals("Ruby")) {
					if(token.type==5&&(token.getLexeme().equals("require"))) {
						isAfterImport=true;
						isAfterImportLine=false;
						continue;
					}

					if(token.getLexeme().equals("\""))                                        //for ruby import staments
					continue;
				}

				if(language.equals("Python")) {
					if(token.type==4&&(token.getLexeme().equals("from"))) {
						isAfterImport=true;
						isAfterImportLine=false;
						continue;
					}
					if(token.type==4&&(token.getLexeme().equals("import"))) {
						isAfterPythonImport=true;
						continue;
					}
					if(isAfterPythonImport) {
						if(token.getLexeme().equals(",")) {
							continue;
						}
						else {
							packageNames.add(packageName+"."+token.getLexeme());
							continue;
						}
					}
				}
				if(isAfterImport&&token.getLexeme().equals(";")) {             //for java and javascript
					isAfterImport=false;
					packageNames.add(packageName);
					packageName="";
					continue;
				}

				if(isAfterImport) {
					packageName+=token.getLexeme();
				}


				if(token.getNextToken().type==Token.NULL||token.getNextToken()==null) {            //for languages in which statements dont end with ";"

					packageNames.add(packageName);
					continue;
				}


			}
			if(isAfterImportLine)
				break;
		}
	}

	public Token getNextNonWhitespaceToken(Token t) {
		Token toReturn = t.getNextToken();
		while(toReturn!=null) {
			if(toReturn.getNextToken().isWhitespace()) {
				toReturn=toReturn.getNextToken();
			}
			else {
				return(toReturn.getNextToken());
			}
		}
		return toReturn;
	}
}
