import ij.plugin.PlugIn;
import ij.*;
import java.awt.Frame;

public class SamplePlugin implements PlugIn {
	static int foo=0;
	public void run(String path) {
		int test=0;
		test++;
		boolean ass=false;
		ass=true;
		System.out.println("It is running");
		foo++; 
		foo+=2;
		print();
		foo+=5;
		foo--;
		foo-=2;
	}
	public void print() {
		int test2=67;
		test2+=3;
		test2++;
		test2-=2;
		System.out.println("This is a test");
	}

}

