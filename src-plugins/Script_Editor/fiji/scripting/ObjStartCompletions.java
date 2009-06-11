package fiji.scripting;

import org.fife.ui.rsyntaxtextarea.*;
import javax.swing.text.*;
import java.util.*;
import org.fife.ui.autocomplete.*;
import java.lang.reflect.*;

public class ObjStartCompletions {
	ArrayList<String> packageNames=new ArrayList<String>();
	TreeSet<ImportedClassObjects> objectSet=new TreeSet<ImportedClassObjects>();
	ClassNames names;

	public ObjStartCompletions(ClassNames name) {
		names=name;
	}
	public void printTokens(RSyntaxTextArea textArea) {
		RSyntaxDocument doc = (RSyntaxDocument)textArea.getDocument();
		Element map=doc.getDefaultRootElement();
		int startLine = map.getElementIndex(0);
		int endLine = map.getElementIndex(doc.getLength());
		for(int i= startLine;i<=endLine;i++) {
			Token token=doc.getTokenListForLine(i);
			while(token!=null) {
				System.out.println(token.toString());
				token=token.getNextToken();
			}
		}
	}

	public void objCompletionPackages(RSyntaxTextArea textArea) {
		RSyntaxDocument doc = (RSyntaxDocument)textArea.getDocument();
		Element map=doc.getDefaultRootElement();
		int startLine = map.getElementIndex(0);
		int endLine = map.getElementIndex(doc.getLength());
		for(int i= startLine;i<=endLine;i++) {

			Token token=doc.getTokenListForLine(i);
			Token prev=null;
			Token prevToPrev=null;
			String packageName="";
			boolean isAfterImport=false;
			for(;token!=null;token=token.getNextToken()) {
				//System.out.println("second");

				if(token.type==16||token.type==3||token.type==2||token.type==1) {      //for white space and comments

					continue;

				}
				if((token.type==4&&token.getLexeme().equals("package"))) 
					break;
				if(token.type==4&&token.getLexeme().equals("import")) {

					isAfterImport=true;

					continue;

				}
				if(isAfterImport&&token.getLexeme().equals(";")) {
					isAfterImport=false;
					System.out.println(packageName);
					packageNames.add(packageName);
					packageName="";
					continue;
				}
				if(isAfterImport) {
					//System.out.println("here once "+token.getLexeme()+packageName);
					packageName+=token.getLexeme();
				}
				if(token.type==4&&token.getLexeme().equals("new")) {

					System.out.println(prev.getLexeme());
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
				//System.out.println(token.toString());
				if(!token.isWhitespace()) {
					prevToPrev=prev;
					prev=token;
				}
			}
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

	//public Token getPreviousNonWhitespaceToken(Token t) {

	//public void createObjectList(RSyntaxTextArea textArea) {
		public String isClassPresent(String name,Package root) {
			for(String s : packageNames) {
				String[] parts=s.split("\\.");
				Item current=findPackage(parts,root);                                    //to create this function
				if(current instanceof Package) {
					try {
						System.out.println(current.getName());
						current=names.findTailSet((Package)current,name).first();
					} catch(Exception e) { e.printStackTrace(); }
				}
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

		public void setObjects(RSyntaxTextArea textArea,String text,DefaultProvider defaultProvider) {
			objCompletionPackages(textArea);

			for(ImportedClassObjects ob: objectSet) {
				System.out.println(ob.name);
				System.out.println(ob.fullClassName);
			}
			boolean dotAtLast=false;
			if(text.charAt(text.length()-1)=='.') {
				dotAtLast=true;
			}
			if(text.lastIndexOf(".")==text.indexOf(".")&&text.indexOf(".")>0) {
				String objname=text.substring(0,text.indexOf("."));
				TreeSet<ImportedClassObjects> set=(TreeSet<ImportedClassObjects>)objectSet.tailSet(new ImportedClassObjects(objname,""));
				TreeSet<ClassMethod> methodSet = new TreeSet<ClassMethod>();
				for(ImportedClassObjects object : set) {
					if (object.name.equals(objname)) {
						String fullname = object.getCompleteClassName();
						try {
							try {
								Class clazz=getClass().getClassLoader().loadClass(fullname);
								Method[] methods = clazz.getMethods();
								for(Method m : methods) {
									String fullMethodName = m.toString();
									methodSet.add(new ClassMethod(fullMethodName));
								}
							} catch(java.lang.Error e) { e.printStackTrace(); }
						} catch(Exception e) { e.printStackTrace(); }
						ArrayList listOfCompletions=new ArrayList();
						if(!dotAtLast) {
							methodSet=(TreeSet<ClassMethod>)methodSet.tailSet(new ClassMethod(text.substring(text.indexOf(".")+1),true));
						}
						for(ClassMethod method : methodSet) {
							if((!dotAtLast)&&(!method.onlyName.startsWith(text.substring(text.indexOf(".")+1))))
								break;
							if((!method.isStatic) && method.isPublic) {
								listOfCompletions.add(new FunctionCompletion(defaultProvider,method.onlyName,method.returnType));      //currently basiccompletion can be changed to functioncompletion
							}
						}
						defaultProvider.addCompletions(listOfCompletions);
					}
					else 
						break;
				}
			}
		}

}
