package fiji.scripting;

import java.lang.Comparable;

public class ClassName implements Item ,Comparable {

	String key;

	public ClassName() {
	}

	public ClassName(String paramkey) {
		key=paramkey;
	}

	public String getName() {
		return this.key;
	}

	public int compareTo(Object o) {
		Item tree=(Item)o;
		return(this.getName().compareTo(tree.getName()));
	}

}

