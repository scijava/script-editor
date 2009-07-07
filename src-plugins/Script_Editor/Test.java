import java.util.Random;

public class Test {

  int foo;

  public static void main(String[] args) {
    Random random = new Random();
    Test test = new Test();
    for (int i = 0; i < 10; i++) {
      test.foo = random.nextInt();
      System.out.println(test.foo);
    }
  }

}
