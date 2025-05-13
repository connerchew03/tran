package Tran;
import java.util.List;
import java.util.Optional;

public class TokenManager {
    private List<Token> tokens;

    public TokenManager(List<Token> tokens) {
        this.tokens = tokens;
    }

    public boolean done() {
	    return tokens.isEmpty();
    }

    public Optional<Token> matchAndRemove(Token.TokenTypes t) {
        if (!done() && tokens.get(0).getType() == t)
            return Optional.of(tokens.remove(0));
        return Optional.empty();
    }

    public Optional<Token> peek(int i) {
        if (i < tokens.size())
            return Optional.of(tokens.get(i));
        return Optional.empty();
    }

    public boolean nextTwoTokensMatch(Token.TokenTypes first, Token.TokenTypes second) {
        if (peek(0).isPresent() && peek(1).isPresent()) {
            Token firstToken = peek(0).get();
            Token secondToken = peek(1).get();
            return firstToken.getType() == first && secondToken.getType() == second;
        }
        return false;
    }

    public boolean nextIsEither(Token.TokenTypes first, Token.TokenTypes second) {
        if (peek(0).isPresent()) {
            Token token = peek(0).get();
            return token.getType() == first || token.getType() == second;
        }
	    return false;
    }

    public int getCurrentLine() {
        if (peek(0).isPresent()) {
            Token token = peek(0).get();
            return token.getLineNumber();
        }
        return -1;
    }

    public int getCurrentColumnNumber() {
        if (peek(0).isPresent()) {
            Token token = peek(0).get();
            return token.getColumnNumber();
        }
        return -1;
    }
}
