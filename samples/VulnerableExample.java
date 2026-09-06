import java.io.InputStream;
import java.io.ObjectInputStream;
public final class VulnerableExample {
    private static final String password = "123456";
    public Object execute(String userInput, InputStream stream) throws Exception {
        Runtime.getRuntime().exec(userInput);
        ObjectInputStream input = new ObjectInputStream(stream);
        return input.readObject();
    }
}
