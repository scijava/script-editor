package fiji.scripting;

import java.lang.Comparable;
import java.lang.Object;

public class Tree implements Comparable{
	String key;
	sortedSet childList;
	Tree parent;

	public Tree() {
	}


	public Tree(String key){
		this.key=key;
		this.childList=new sortedSet();
	}

	public int compareTo(Object o) {
		Tree tree=(Tree)o;
		return(this.key.compareTo(tree.key));
	}

	//this constructor is used for all other tree objects other than leaves which have non null childList and whose parent is not known or does not exist like the root
	public Tree(String key,sortedSet childList){
		this.key=key;
		this.childList=childList;
	}

	public Tree(String key,Tree parent){
		this.key=key;
		this.parent=parent;
	}

	public Tree(String key,sortedSet childList,Tree parent){
		this.key=key;
		this.childList=childList;
		this.parent=parent;
	}

	public String getKey() {
		return this.key;
	}
}
