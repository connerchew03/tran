package Tran;
import java.util.LinkedList;
import java.util.List;
import java.util.HashMap;

public class Lexer {
    private final TextManager textManager;
    private HashMap<String, Token.TokenTypes> keywords;
    private HashMap<String, Token.TokenTypes> punctuation;
    private LinkedList<String> canBeModified;
    private int previousIndentation;
    private int lineNumber;
    private int characterPosition;

    public Lexer(String input) {
        textManager = new TextManager(input);
        addKeywords();
        addPunctuation();
        addModified();
        previousIndentation = 0;
        lineNumber = 1;
        characterPosition = 0;
    }

    public List<Token> Lex() throws Exception {
        LinkedList<Token> listOfTokens = new LinkedList<Token>();
        char skip;
        while (!textManager.isAtEnd()) {
            if (Character.isLetter(textManager.peekCharacter()))
                listOfTokens.add(readWord());
            else if (Character.isDigit(textManager.peekCharacter()))
                listOfTokens.add(readNumber());
            else if (textManager.peekCharacter() == '.') {
                if (Character.isDigit(textManager.peekCharacter(1)))
                    listOfTokens.add(readNumber());
                else
                    listOfTokens.add(readPunctuation());
            }
            else if (textManager.peekCharacter() == '!' || punctuation.containsKey(Character.toString(textManager.peekCharacter())))
                listOfTokens.add(readPunctuation());
            else if (textManager.peekCharacter() == ' ') {
                skip = textManager.getCharacter();
                characterPosition++;
            }
            else if (textManager.peekCharacter() == '\t') {
                skip = textManager.getCharacter();
                characterPosition += 4;
            }
            else if (textManager.peekCharacter() == '\n' || textManager.peekCharacter() == '\r') {
                lineNumber++;
                characterPosition = 0;
                listOfTokens.add(new Token(Token.TokenTypes.NEWLINE, lineNumber, characterPosition));
                if (textManager.peekCharacter() == '\r')
                    skip = textManager.getCharacter();
                skip = textManager.getCharacter();
                int i = 0;
                while (!textManager.isAtEnd() && (textManager.peekCharacter(i) == ' ' || textManager.peekCharacter(i) == '\t'))
                    i++;
                if (textManager.isAtEnd() || textManager.peekCharacter(i) == '\n')
                    continue;
                readIndentation(listOfTokens);
            }
            else if (textManager.peekCharacter() == '\"') {
                skip = textManager.getCharacter();
                characterPosition++;
                listOfTokens.add(readQuotedString());
            }
            else if (textManager.peekCharacter() == '\'') {
                skip = textManager.getCharacter();
                characterPosition++;
                listOfTokens.add(readQuotedCharacter());
            }
            else if (textManager.peekCharacter() == '{') {
                readComment();
            }
            else
                throw new SyntaxErrorException("Invalid character", lineNumber, characterPosition);
        }
        for (int i = 0; i < previousIndentation; i++)
            listOfTokens.add(new Token(Token.TokenTypes.DEDENT, lineNumber, characterPosition));
	    return listOfTokens;
    }

    public Token readWord() {
        String buffer = "";
        int initialPosition = characterPosition;
        while (!textManager.isAtEnd() && Character.isLetterOrDigit(textManager.peekCharacter())) {
            buffer += textManager.getCharacter();
            characterPosition++;
        }
        if (keywords.containsKey(buffer))
            return new Token(keywords.get(buffer), lineNumber, initialPosition);
        else
            return new Token(Token.TokenTypes.WORD, lineNumber, initialPosition, buffer);
    }

    public Token readNumber() throws SyntaxErrorException {
        String buffer = "";
        int initialPosition = characterPosition;
        boolean hasDecimal = false;
        while (!textManager.isAtEnd() && (Character.isDigit(textManager.peekCharacter()) || textManager.peekCharacter() == '.')) {
            if (textManager.peekCharacter() == '.' && hasDecimal)
                throw new SyntaxErrorException("Numbers may only have at most one decimal point", lineNumber, characterPosition);
            else {
                if (textManager.peekCharacter() == '.')
                    hasDecimal = true;
                buffer += textManager.getCharacter();
                characterPosition++;
            }
        }
        if (!textManager.isAtEnd() && Character.isLetter(textManager.peekCharacter()))
            throw new SyntaxErrorException("Numbers may not contain alphabetic characters", lineNumber, characterPosition);
        return new Token(Token.TokenTypes.NUMBER, lineNumber, initialPosition, buffer);
    }

    public Token readPunctuation() throws SyntaxErrorException {
        String buffer = "";
        int initialPosition = characterPosition;
        buffer += textManager.getCharacter();
        characterPosition++;
        if (canBeModified.contains(buffer) && textManager.peekCharacter() == '=') {
            buffer += textManager.getCharacter();
            characterPosition++;
        }
        if (punctuation.containsKey(buffer))
            return new Token(punctuation.get(buffer), lineNumber, initialPosition);
        else
            throw new SyntaxErrorException("Invalid punctuation symbol", lineNumber, initialPosition);
    }

    public void readIndentation(LinkedList<Token> listOfTokens) throws SyntaxErrorException {
        char skip;
        int currentIndentation = 0, numberOfSpaces = 0;
        while (textManager.peekCharacter() == ' ' || textManager.peekCharacter() == '\t') {
            if (textManager.peekCharacter() == '\t') {
                currentIndentation++;
                characterPosition += 4;
            }
            else if (textManager.peekCharacter() == ' ') {
                numberOfSpaces++;
                characterPosition++;
                if (numberOfSpaces % 4 == 0) {
                    currentIndentation++;
                    numberOfSpaces = 0;
                }
            }
            skip = textManager.getCharacter();
        }
        if (numberOfSpaces != 0)
            throw new SyntaxErrorException("Invalid indentation level", lineNumber, 0);
        if (currentIndentation > previousIndentation) {
            int difference = currentIndentation - previousIndentation;
            for (int i = 0; i < difference; i++)
                listOfTokens.add(new Token(Token.TokenTypes.INDENT, lineNumber, characterPosition));
        }
        else if (currentIndentation < previousIndentation) {
            int difference = previousIndentation - currentIndentation;
            for (int i = 0; i < difference; i++)
                listOfTokens.add(new Token(Token.TokenTypes.DEDENT, lineNumber, characterPosition));
        }
        previousIndentation = currentIndentation;
    }

    public Token readQuotedString() throws SyntaxErrorException {
        String buffer = "";
        int initialPosition = characterPosition - 1;
        while (!textManager.isAtEnd() && textManager.peekCharacter() != '\"') {
            buffer += textManager.getCharacter();
            characterPosition++;
        }
        if (textManager.isAtEnd())
            throw new SyntaxErrorException("Unclosed double quote", lineNumber, characterPosition);
        char skip = textManager.getCharacter();
        characterPosition++;
        return new Token(Token.TokenTypes.QUOTEDSTRING, lineNumber, initialPosition, buffer);
    }

    public Token readQuotedCharacter() throws SyntaxErrorException {
        String buffer = "";
        int initialPosition = characterPosition - 1;
        if (!textManager.isAtEnd()) {
            buffer += textManager.getCharacter();
            characterPosition++;
        }
        else
            throw new SyntaxErrorException("Stray single quote", lineNumber, characterPosition);
        if (textManager.isAtEnd() || textManager.peekCharacter() != '\'')
            throw new SyntaxErrorException("Single quotes may only contain one character", lineNumber, characterPosition);
        char skip = textManager.getCharacter();
        characterPosition++;
        return new Token(Token.TokenTypes.QUOTEDCHARACTER, lineNumber, initialPosition, buffer);
    }

    public void readComment() throws SyntaxErrorException {
        char skip;
        while (!textManager.isAtEnd() && textManager.peekCharacter() != '}') {
            if (textManager.peekCharacter() == '\n') {
                lineNumber++;
                characterPosition = 0;
            }
            else if (textManager.peekCharacter() == '\t')
                characterPosition += 4;
            else
                characterPosition++;
            skip = textManager.getCharacter();
        }
        if (textManager.isAtEnd())
            throw new SyntaxErrorException("Unclosed comment", lineNumber, characterPosition);
        if (textManager.peekCharacter() == '}') {
            skip = textManager.getCharacter();
            characterPosition++;
        }
    }

    private void addKeywords() {
        keywords = new HashMap<String, Token.TokenTypes>();
        keywords.put("implements", Token.TokenTypes.IMPLEMENTS);
        keywords.put("class", Token.TokenTypes.CLASS);
        keywords.put("interface", Token.TokenTypes.INTERFACE);
        keywords.put("loop", Token.TokenTypes.LOOP);
        keywords.put("if", Token.TokenTypes.IF);
        keywords.put("else", Token.TokenTypes.ELSE);
        keywords.put("new", Token.TokenTypes.NEW);
        keywords.put("private", Token.TokenTypes.PRIVATE);
        keywords.put("shared", Token.TokenTypes.SHARED);
        keywords.put("construct", Token.TokenTypes.CONSTRUCT);
    }

    private void addPunctuation() {
        punctuation = new HashMap<String, Token.TokenTypes>();
        punctuation.put("=", Token.TokenTypes.ASSIGN);
        punctuation.put("(", Token.TokenTypes.LPAREN);
        punctuation.put(")", Token.TokenTypes.RPAREN);
        punctuation.put(":", Token.TokenTypes.COLON);
        punctuation.put(".", Token.TokenTypes.DOT);
        punctuation.put("+", Token.TokenTypes.PLUS);
        punctuation.put("-", Token.TokenTypes.MINUS);
        punctuation.put("*", Token.TokenTypes.TIMES);
        punctuation.put("/", Token.TokenTypes.DIVIDE);
        punctuation.put("%", Token.TokenTypes.MODULO);
        punctuation.put(",", Token.TokenTypes.COMMA);
        punctuation.put("==", Token.TokenTypes.EQUAL);
        punctuation.put("!=", Token.TokenTypes.NOTEQUAL);
        punctuation.put("<", Token.TokenTypes.LESSTHAN);
        punctuation.put("<=", Token.TokenTypes.LESSTHANEQUAL);
        punctuation.put(">", Token.TokenTypes.GREATERTHAN);
        punctuation.put(">=", Token.TokenTypes.GREATERTHANEQUAL);
    }

    private void addModified() {
        canBeModified = new LinkedList<String>();
        canBeModified.add("=");
        canBeModified.add("!");
        canBeModified.add("<");
        canBeModified.add(">");
    }
}