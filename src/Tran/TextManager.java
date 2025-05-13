package Tran;
public class TextManager {
    public final String text;
    public int position;

    public TextManager(String input) {
        text = input;
        position = 0;
    }

    public boolean isAtEnd() {
	    return position == text.length();
    }

    public char peekCharacter() {
        return text.charAt(position);
    }

    public char peekCharacter(int dist) {
        return text.charAt(position + dist);
    }

    public char getCharacter() {
        return text.charAt(position++);
    }
}
