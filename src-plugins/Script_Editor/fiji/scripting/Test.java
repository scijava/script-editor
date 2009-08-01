package fiji.scripting;
import java.util.Random;

public class Test {

	static int foo;
	static String name;

	public static void main(String[] args) {
		foo = 0;
		System.out.println("The value of set name is " + name);
		System.out.println("The value of first arg is " + args[0]);
		for (int i = 0; i < 10; i++) {
			foo += i;
		}
		foo--;
		foo += 10;

	}

	public static void setName(String s) {
		name = s;
	}
}
