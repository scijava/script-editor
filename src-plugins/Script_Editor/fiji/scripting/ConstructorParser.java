package fiji.scripting;

import org.fife.ui.rsyntaxtextarea.*;
import java.util.TreeSet;
import java.util.ArrayList;
import javax.swing.text.Element;

public class ConstructorParser {

	TreeSet<ImportedClassObjects> objectSet=new TreeSet<ImportedClassObjects>();
	ClassNames names;
	ArrayList<String> packageNames=new ArrayList<String>();

	public ConstructorParser(ClassNames name) {
		names=name;
	}

	public TreeSet<ImportedClassObjects> getObjectSet() {
		return objectSet;
	}

	public void findConstructorObjects(RSyntaxTextArea textArea) {

		RSyntaxDocument doc = (RSyntaxDocument)textArea.getDocument();
		Element map=doc.getDefaultRootElement();
		int startLine = map.getElementIndex(0);
		int endLine = map.getElementIndex(doc.getLength());
		for(int i= startLine;i<=endLine;i++) {

			Token token=doc.getTokenListForLine(i);
			Token prev=null;
			Token prevToPrev=null;

			for(;token!=null&&token.type!=0;token=token.getNextToken()) {
				if(token.type==16||token.type==3||token.type==2||token.type==1) {      //for white space and comments
					continue;
				}
				if(token.getLexeme().equals("new")) {

					if(prev.getLexeme().equals("=")) {
						if(prevToPrev.type==15) {
							String temp=getNextNonWhitespaceToken(token).getLexeme();
							String temp2=isClassPresent(temp,names.root);
							if(!temp2.equals("")) {
								ImportedClassObjects obj=new ImportedClassObjects(prevToPrev.getLexeme(),temp,temp2,true);
								objectSet.add(obj);
							}
							System.out.println(prevToPrev.getLexeme()+" "+temp+" "+temp2);

						}
					}
				}

				if(!token.isWhitespace()) {
					prevToPrev=prev;
					prev=token;
				}



			}
		}
	}

	public void setPackageNames(ArrayList<String> names) {
		packageNames=names;
	}

	public String isClassPresent(String name,Package root) {
		System.out.println(packageNames.get(0));
		for(String s : packageNames) {
			String[] parts=s.split("\\.");
			Item current=findPackage(parts,root);                                    //to create this function
			if(current instanceof Package) {
				try {
					current=names.findTailSet((Package)current,name).first();
				} catch(Exception e) { e.printStackTrace(); }
			}
			System.out.println("The name of the class name is "+current.getName());
			if(current.getName().equals(name)) {
				return ((ClassName)current).getCompleteName();
			}
		}
		return "";
	}

	public Item findPackage(String[] splitPart,Package p) {
		for(int i=0;i<splitPart.length-1;i++) {
			p=(Package)names.findTailSet(p,splitPart[i]).first();
		}
		if(splitPart[splitPart.length-1].equals("*")) {
			return (Item)p;
		}
		else {
			return names.findTailSet(p,splitPart[splitPart.length-1]).first();
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
