package fiji.scripting;

import java.lang.Comparable;

public class ClassName implements Item ,Comparable {

	String key;
	String completeName;

	public ClassName() {
	}

	public ClassName(String paramkey,String name) {
		key=paramkey;
		completeName=name;
	}

	public String getName() {
		return this.key;
	}

	public String getCompleteName() {
		return this.completeName.replace('/','.');
	}

	public int compareTo(Object o) {
		Item tree=(Item)o;
		return(this.getName().compareTo(tree.getName()));
	}

}

