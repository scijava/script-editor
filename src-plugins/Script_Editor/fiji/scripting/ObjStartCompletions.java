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
	ConstructorParser parser1;
	String language=new String();

	public ObjStartCompletions(ClassNames name,String lang,ArrayList pNames) {
		names=name;
		language=lang;
		this.packageNames=pNames;
		parser1=new ConstructorParser(name,lang);
	}

	public void setObjects(RSyntaxTextArea textArea,String text,DefaultProvider defaultProvider) {

		//System.out.println("The first package element "+packageNames.get(0));
		parser1.setPackageNames(packageNames);
		parser1.findConstructorObjects(textArea);
		objectSet=parser1.getObjectSet();
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
