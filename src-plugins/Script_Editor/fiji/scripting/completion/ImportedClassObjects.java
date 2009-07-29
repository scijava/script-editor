package fiji.scripting;

import java.lang.Comparable;

public class ImportedClassObjects implements Comparable{

	String name;
	String className;
	String fullClassName;
	boolean isImported;

	public ImportedClassObjects(String itsname,String classname,String fullclassname,boolean imported) {
		name=itsname;
		className=classname;
		fullClassName=fullclassname;
		isImported=imported;
	}

	public ImportedClassObjects(String itsname,String classname) {
		name=itsname;
		className=classname;
	}

	public ImportedClassObjects(String itsname) {
		name=itsname;
	}

	public void setClassName(String classname) {
		this.className=classname;
	}

	public void setFullClassName(String fullclassname) {
		this.fullClassName=fullclassname;
	}

	public void setIsImported(boolean set) {
		this.isImported = set;
	}

	public String getCompleteClassName() {
		return this.fullClassName;
	}
	public int compareTo(Object o) {
		int i= this.name.compareTo(((ImportedClassObjects)o).name);
		if(i!=0)
			return(i);
		else 
			return(this.className.compareTo(((ImportedClassObjects)o).className));
	}
}
