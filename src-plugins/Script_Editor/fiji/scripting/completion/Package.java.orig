package fiji.scripting.completion;

import java.util.TreeSet;
import java.lang.Comparable;

public class Package extends TreeSet<Item> implements Item,Comparable {
	String key;

	public Package() {
	}

	public Package(String key) {
		this.key = key;
	}

	public String getName() {
		return this.key;
	}

	public int compareTo(Object o) {
		Item tree=(Item)o;
		return(this.getName().compareTo(tree.getName()));
	}



}
