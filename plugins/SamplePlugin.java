import ij.plugin.PlugIn;
import ij.*;
import java.awt.Frame;

public class SamplePlugin implements PlugIn {
	public static void main(String args[]){
		System.out.println("This is a plugin");
	}
	public void run(String path) {
		System.out.println("It is running");
		static int foo=0;
		foo++;
		foo+=2;
		foo--;
		foo+=5;
		foo--;
		foo-=2;
	}
}

