package Tran;
import AST.*;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class Parser {
    private TokenManager tokenManager;
    private TranNode top;

    public Parser(TranNode top, List<Token> tokens) {
        tokenManager = new TokenManager(tokens);
        this.top = top;
    }

    // Tran = ( Class | Interface )*
    public void Tran() throws SyntaxErrorException {
        while (tokenManager.matchAndRemove(Token.TokenTypes.NEWLINE).isPresent()) {  }
        while (!tokenManager.done()) {
            if (tokenManager.nextIsEither(Token.TokenTypes.CLASS, Token.TokenTypes.INTERFACE)) {
                if (tokenManager.peek(0).isPresent() && tokenManager.peek(0).get().getType() == Token.TokenTypes.CLASS)
                    top.Classes.addLast(Class().get());
                else
                    top.Interfaces.addLast(Interface().get());
            }
            else if (tokenManager.matchAndRemove(Token.TokenTypes.NEWLINE).isPresent()) {  }
            else
                throw new SyntaxErrorException("Program may only contain classes and interfaces", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }
    }

    // Interface = "interface" IDENTIFIER NEWLINE INDENT MethodHeader* DEDENT
    private Optional<InterfaceNode> Interface() throws SyntaxErrorException {
        if (tokenManager.matchAndRemove(Token.TokenTypes.INTERFACE).isEmpty())
            return Optional.empty();
        Optional<Token> nameToken = tokenManager.matchAndRemove(Token.TokenTypes.WORD);
        if (nameToken.isEmpty())
            throw new SyntaxErrorException("Interface definition missing name", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        InterfaceNode interfaceNode = new InterfaceNode();
        interfaceNode.name = nameToken.get().getValue();
        requireNewLine();
        if (tokenManager.matchAndRemove(Token.TokenTypes.INDENT).isEmpty())
            throw new SyntaxErrorException("Interface body must be indented", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        Optional<MethodHeaderNode> method = MethodHeader();
        while (method.isPresent()) {
            interfaceNode.methods.addLast(method.get());
            method = MethodHeader();
        }
        if (tokenManager.matchAndRemove(Token.TokenTypes.DEDENT).isEmpty())
            throw new SyntaxErrorException("Dedent expected after interface body", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        return Optional.of(interfaceNode);
    }

    // MethodHeader = IDENTIFIER "(" ParameterVariableDeclarations ")" (":" ParameterVariableDeclarations)? NEWLINE
    private Optional<MethodHeaderNode> MethodHeader() throws SyntaxErrorException {
        Optional<Token> nameToken = tokenManager.matchAndRemove(Token.TokenTypes.WORD);
        if (nameToken.isEmpty())
            return Optional.empty();
        MethodHeaderNode methodHeader = new MethodHeaderNode();
        methodHeader.name = nameToken.get().getValue();
        if (tokenManager.matchAndRemove(Token.TokenTypes.LPAREN).isEmpty())
            throw new SyntaxErrorException("Method header missing left parenthesis", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        methodHeader.parameters = ParameterVariableDeclarations();
        if (tokenManager.matchAndRemove(Token.TokenTypes.RPAREN).isEmpty())
            throw new SyntaxErrorException("Method header missing right parenthesis", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        if (tokenManager.matchAndRemove(Token.TokenTypes.COLON).isPresent())
            methodHeader.returns = ParameterVariableDeclarations();
        requireNewLine();
        return Optional.of(methodHeader);
    }

    // ParameterVariableDeclarations = ParameterVariableDeclaration ("," ParameterVariableDeclaration)*
    private List<VariableDeclarationNode> ParameterVariableDeclarations() throws SyntaxErrorException {
        List<VariableDeclarationNode> parameterVariableDeclarations = new ArrayList<>();
        Optional<VariableDeclarationNode> firstDeclaration = ParameterVariableDeclaration();
        if (firstDeclaration.isEmpty())
            return parameterVariableDeclarations;
        parameterVariableDeclarations.addLast(firstDeclaration.get());
        while (tokenManager.matchAndRemove(Token.TokenTypes.COMMA).isPresent())
            parameterVariableDeclarations.addLast(ParameterVariableDeclaration().orElseThrow(() -> new SyntaxErrorException("Variable declaration expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber())));
        return parameterVariableDeclarations;
    }

    // ParameterVariableDeclaration = IDENTIFIER IDENTIFIER
    private Optional<VariableDeclarationNode> ParameterVariableDeclaration() throws SyntaxErrorException {
        Optional<Token> typeToken = tokenManager.matchAndRemove(Token.TokenTypes.WORD);
        if (typeToken.isEmpty())
            return Optional.empty();
        VariableDeclarationNode parameterVariableDeclaration = new VariableDeclarationNode();
        parameterVariableDeclaration.type = typeToken.get().getValue();
        Optional<Token> nameToken = tokenManager.matchAndRemove(Token.TokenTypes.WORD);
        if (nameToken.isEmpty())
            throw new SyntaxErrorException("Variable declaration missing name", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        parameterVariableDeclaration.name = nameToken.get().getValue();
        return Optional.of(parameterVariableDeclaration);
    }

    // Class =  "class" IDENTIFIER ( "implements" IDENTIFIER ( "," IDENTIFIER )* )? NEWLINE INDENT ( Constructor | MethodDeclaration | Member )* DEDENT
    private Optional<ClassNode> Class() throws SyntaxErrorException {
        if (tokenManager.matchAndRemove(Token.TokenTypes.CLASS).isEmpty())
            return Optional.empty();
        Optional<Token> name = tokenManager.matchAndRemove(Token.TokenTypes.WORD);
        if (name.isEmpty())
            throw new SyntaxErrorException("Class definition missing name", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        ClassNode classNode = new ClassNode();
        classNode.name = name.get().getValue();
        if (tokenManager.matchAndRemove(Token.TokenTypes.IMPLEMENTS).isPresent()) {
            Optional<Token> firstInterface = tokenManager.matchAndRemove(Token.TokenTypes.WORD);
            if (firstInterface.isEmpty())
                throw new SyntaxErrorException("Interface implementation expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
            classNode.interfaces.addLast(firstInterface.get().getValue());
            while (tokenManager.matchAndRemove(Token.TokenTypes.COMMA).isPresent()) {
                Optional<Token> interfaceToken = tokenManager.matchAndRemove(Token.TokenTypes.WORD);
                if (interfaceToken.isEmpty())
                    throw new SyntaxErrorException("Interface implementation expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
                classNode.interfaces.addLast(interfaceToken.get().getValue());
            }
        }
        requireNewLine();
        if (tokenManager.matchAndRemove(Token.TokenTypes.INDENT).isEmpty())
            throw new SyntaxErrorException("Class body must be indented", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        while (tokenManager.peek(0).isPresent() && tokenManager.peek(0).get().getType() != Token.TokenTypes.DEDENT) {
            Optional<ConstructorNode> constructor = Constructor();
            if (constructor.isPresent())
                classNode.constructors.addLast(constructor.get());
            else {
                if (tokenManager.nextIsEither(Token.TokenTypes.PRIVATE, Token.TokenTypes.SHARED) || tokenManager.nextTwoTokensMatch(Token.TokenTypes.WORD, Token.TokenTypes.LPAREN))
                    classNode.methods.addLast(MethodDeclaration().get());
                else if (tokenManager.nextTwoTokensMatch(Token.TokenTypes.WORD, Token.TokenTypes.WORD))
                    classNode.members.addAll(Member());
                else
                    throw new SyntaxErrorException("Class may only contain constructors, method declarations, and members", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
            }
        }
        if (tokenManager.matchAndRemove(Token.TokenTypes.DEDENT).isEmpty())
            throw new SyntaxErrorException("Dedent expected after class body", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        return Optional.of(classNode);
    }

    // Constructor = "construct" "(" ParameterVariableDeclarations ")" NEWLINE MethodBody
    private Optional<ConstructorNode> Constructor() throws SyntaxErrorException {
        if (tokenManager.matchAndRemove(Token.TokenTypes.CONSTRUCT).isEmpty())
            return Optional.empty();
        if (tokenManager.matchAndRemove(Token.TokenTypes.LPAREN).isEmpty())
            throw new SyntaxErrorException("Constructor missing left parenthesis", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        List<VariableDeclarationNode> parameterVariableDeclarations = ParameterVariableDeclarations();
        if (tokenManager.matchAndRemove(Token.TokenTypes.RPAREN).isEmpty())
            throw new SyntaxErrorException("Constructor missing right parenthesis", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        requireNewLine();
        Optional<MethodDeclarationNode> methodBody = MethodBody();
        if (methodBody.isEmpty())
            throw new SyntaxErrorException("Constructor missing definition", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        ConstructorNode constructor = new ConstructorNode();
        constructor.parameters = parameterVariableDeclarations;
        constructor.locals = methodBody.get().locals;
        constructor.statements = methodBody.get().statements;
        return Optional.of(constructor);
    }

    // Member = VariableDeclarations
    private List<MemberNode> Member() throws SyntaxErrorException {
        List<VariableDeclarationNode> variableDeclarations = VariableDeclarations();
        List<MemberNode> members = new ArrayList<>(variableDeclarations.size());
        for (VariableDeclarationNode variableDeclaration : variableDeclarations) {
            members.addLast(new MemberNode());
            members.getLast().declaration = variableDeclaration;
        }
        return members;
    }

    // VariableDeclarations =  IDENTIFIER VariableNameValue ("," VariableNameValue)* NEWLINE
    private List<VariableDeclarationNode> VariableDeclarations() throws SyntaxErrorException {
        List<VariableDeclarationNode> variableDeclarations = new ArrayList<>();
        if (!tokenManager.nextTwoTokensMatch(Token.TokenTypes.WORD, Token.TokenTypes.WORD))
            return variableDeclarations;
        Optional<Token> type = tokenManager.matchAndRemove(Token.TokenTypes.WORD);
        if (type.isEmpty())
            return variableDeclarations;
        Optional<VariableDeclarationNode> firstDeclaration = VariableNameValue();
        if (firstDeclaration.isEmpty())
            throw new SyntaxErrorException("Variable declaration missing name", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        firstDeclaration.get().type = type.get().getValue();
        variableDeclarations.addLast(firstDeclaration.get());
        while (tokenManager.matchAndRemove(Token.TokenTypes.COMMA).isPresent()) {
            variableDeclarations.addLast(VariableNameValue().orElseThrow(() -> new SyntaxErrorException("Variable declaration expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber())));
            variableDeclarations.getLast().type = type.get().getValue();
        }
        requireNewLine();
        return variableDeclarations;
    }

    // VariableNameValue = IDENTIFIER ( "=" Expression)?
    private Optional<VariableDeclarationNode> VariableNameValue() throws SyntaxErrorException {
        Optional<Token> name = tokenManager.matchAndRemove(Token.TokenTypes.WORD);
        if (name.isEmpty())
            return Optional.empty();
        VariableDeclarationNode variableNameValue = new VariableDeclarationNode();
        variableNameValue.name = name.get().getValue();
        if (tokenManager.matchAndRemove(Token.TokenTypes.ASSIGN).isPresent()) {
            Optional<ExpressionNode> value = Expression();
            if (value.isEmpty())
                throw new SyntaxErrorException("Variable instantiation missing value", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
            variableNameValue.initializer = value;
        }
        return Optional.of(variableNameValue);
    }

    // MethodDeclaration = "private"? "shared"? MethodHeader NEWLINE MethodBody
    private Optional<MethodDeclarationNode> MethodDeclaration() throws SyntaxErrorException {
        if ((tokenManager.peek(0).isPresent() && tokenManager.peek(0).get().getType() != Token.TokenTypes.WORD) && !tokenManager.nextIsEither(Token.TokenTypes.PRIVATE, Token.TokenTypes.SHARED))
            return Optional.empty();
        boolean isPrivate = false, isShared = false;
        if (tokenManager.matchAndRemove(Token.TokenTypes.PRIVATE).isPresent())
            isPrivate = true;
        if (tokenManager.matchAndRemove(Token.TokenTypes.SHARED).isPresent())
            isShared = true;
        Optional<MethodHeaderNode> methodHeader = MethodHeader();
        if (methodHeader.isEmpty())
            throw new SyntaxErrorException("Method header expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        Optional<MethodDeclarationNode> methodDeclaration = MethodBody();
        if (methodDeclaration.isEmpty())
            throw new SyntaxErrorException("Method body expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        methodDeclaration.get().isPrivate = isPrivate;
        methodDeclaration.get().isShared = isShared;
        methodDeclaration.get().name = methodHeader.get().name;
        methodDeclaration.get().parameters = methodHeader.get().parameters;
        methodDeclaration.get().returns = methodHeader.get().returns;
        return methodDeclaration;
    }

    // MethodBody = INDENT ( VariableDeclarations )*  Statement* DEDENT
    private Optional<MethodDeclarationNode> MethodBody() throws SyntaxErrorException {
        if (tokenManager.matchAndRemove(Token.TokenTypes.INDENT).isEmpty())
            return Optional.empty();
        MethodDeclarationNode methodBody = new MethodDeclarationNode();
        List<VariableDeclarationNode> variableDeclarations = VariableDeclarations();
        while (!variableDeclarations.isEmpty()) {
            methodBody.locals.addAll(variableDeclarations);
            variableDeclarations = VariableDeclarations();
        }
        methodBody.statements = Statements();
        if (tokenManager.matchAndRemove(Token.TokenTypes.DEDENT).isEmpty())
            throw new SyntaxErrorException("Dedent expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        return Optional.of(methodBody);
    }

    // Statements = INDENT Statement*  DEDENT
    private List<StatementNode> Statements() throws SyntaxErrorException {
        List<StatementNode> statements = new ArrayList<>();
        Optional<StatementNode> statement = Statement();
        while (statement.isPresent() && (tokenManager.peek(0).isPresent() && tokenManager.peek(0).get().getType() != Token.TokenTypes.DEDENT)) {
            statements.addLast(statement.get());
            statement = Statement();
        }
        if (statement.isPresent())
            statements.addLast(statement.get());
        return statements;
    }

    // Statement = If | Loop | MethodCall | Assignment
    private Optional<StatementNode> Statement() throws SyntaxErrorException {
        if (tokenManager.peek(0).isPresent() && tokenManager.peek(0).get().getType() == Token.TokenTypes.IF)
            return Optional.of(If().get());
        if (tokenManager.peek(0).isPresent() && tokenManager.peek(0).get().getType() == Token.TokenTypes.LOOP)
            return Optional.of(Loop().get());
        return disambiguate();
    }

    // If = "if" BoolExpTerm NEWLINE Statements ("else" NEWLINE (Statement | Statements))?
    private Optional<IfNode> If() throws SyntaxErrorException {
        if (tokenManager.matchAndRemove(Token.TokenTypes.IF).isEmpty())
            return Optional.empty();
        IfNode ifNode = new IfNode();
        Optional<ExpressionNode> boolExpTerm = BoolExpTerm();
        if (boolExpTerm.isEmpty())
            throw new SyntaxErrorException("Boolean expression expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        ifNode.condition = boolExpTerm.get();
        if (tokenManager.matchAndRemove(Token.TokenTypes.INDENT).isEmpty())
            throw new SyntaxErrorException("Indent expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        ifNode.statements = Statements();
        if (tokenManager.matchAndRemove(Token.TokenTypes.DEDENT).isEmpty())
            throw new SyntaxErrorException("Dedent expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        if (tokenManager.matchAndRemove(Token.TokenTypes.ELSE).isPresent()) {
            requireNewLine();
            ElseNode elseNode = new ElseNode();
            if (tokenManager.matchAndRemove(Token.TokenTypes.INDENT).isEmpty())
                throw new SyntaxErrorException("Indent expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
            elseNode.statements = Statements();
            if (tokenManager.matchAndRemove(Token.TokenTypes.DEDENT).isEmpty())
                throw new SyntaxErrorException("Dedent expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
            ifNode.elseStatement = Optional.of(elseNode);
        }
        else
            ifNode.elseStatement = Optional.empty();
        return Optional.of(ifNode);
    }

    // BoolExpTerm = MethodCallExpression | (Expression ( "==" | "!=" | "<=" | ">=" | ">" | "<" ) Expression) | VariableReference
    private Optional<ExpressionNode> BoolExpTerm() throws SyntaxErrorException {
        Optional<MethodCallExpressionNode> methodCallExpression = MethodCallExpression();
        if (methodCallExpression.isPresent()) {
            requireNewLine();
            return Optional.of(methodCallExpression.get());
        }
        if (tokenManager.peek(1).isPresent() && tokenManager.peek(1).get().getType() == Token.TokenTypes.NEWLINE) {
            Optional<VariableReferenceNode> variableReference = VariableReference();
            if (variableReference.isEmpty())
                throw new SyntaxErrorException("Unexpected token", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
            requireNewLine();
            return Optional.of(variableReference.get());
        }
        Optional<ExpressionNode> left = Expression();
        if (left.isEmpty())
            throw new SyntaxErrorException("Expression expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        CompareNode boolExp = new CompareNode();
        if (tokenManager.matchAndRemove(Token.TokenTypes.LESSTHAN).isPresent())
            boolExp.op = CompareNode.CompareOperations.lt;
        else if (tokenManager.matchAndRemove(Token.TokenTypes.LESSTHANEQUAL).isPresent())
            boolExp.op = CompareNode.CompareOperations.le;
        else if (tokenManager.matchAndRemove(Token.TokenTypes.GREATERTHAN).isPresent())
            boolExp.op = CompareNode.CompareOperations.gt;
        else if (tokenManager.matchAndRemove(Token.TokenTypes.GREATERTHANEQUAL).isPresent())
            boolExp.op = CompareNode.CompareOperations.ge;
        else if (tokenManager.matchAndRemove(Token.TokenTypes.EQUAL).isPresent())
            boolExp.op = CompareNode.CompareOperations.eq;
        else if (tokenManager.matchAndRemove(Token.TokenTypes.NOTEQUAL).isPresent())
            boolExp.op = CompareNode.CompareOperations.ne;
        else
            throw new SyntaxErrorException("Unexpected token", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        Optional<ExpressionNode> right = Expression();
        if (right.isEmpty())
            throw new SyntaxErrorException("Expression expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        boolExp.left = left.get();
        boolExp.right = right.get();
        requireNewLine();
        return Optional.of(boolExp);
    }

    // Loop = "loop" (VariableReference "=" )?  ( BoolExpTerm ) NEWLINE Statements
    private Optional<LoopNode> Loop() throws SyntaxErrorException {
        if (tokenManager.matchAndRemove(Token.TokenTypes.LOOP).isEmpty())
            return Optional.empty();
        LoopNode loop = new LoopNode();
        if (tokenManager.nextTwoTokensMatch(Token.TokenTypes.WORD, Token.TokenTypes.ASSIGN)) {
            loop.assignment = VariableReference();
            tokenManager.matchAndRemove(Token.TokenTypes.ASSIGN);
        }
        else
            loop.assignment = Optional.empty();
        Optional<ExpressionNode> boolExpTerm = BoolExpTerm();
        if (boolExpTerm.isEmpty())
            throw new SyntaxErrorException("Boolean expression expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        loop.expression = boolExpTerm.get();
        if (tokenManager.matchAndRemove(Token.TokenTypes.INDENT).isEmpty())
            throw new SyntaxErrorException("Indent expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        loop.statements = Statements();
        if (tokenManager.matchAndRemove(Token.TokenTypes.DEDENT).isEmpty())
            throw new SyntaxErrorException("Dedent expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        return Optional.of(loop);
    }

    // Assignment = VariableReference "=" Expression NEWLINE
    private Optional<AssignmentNode> Assignment() throws SyntaxErrorException {
        if (tokenManager.matchAndRemove(Token.TokenTypes.ASSIGN).isEmpty())
            return Optional.empty();
        Optional<ExpressionNode> expression = Expression();
        if (expression.isEmpty())
            throw new SyntaxErrorException("Expression expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        AssignmentNode assignment = new AssignmentNode();
        assignment.expression = expression.get();
        return Optional.of(assignment);
    }

    // MethodCall = (VariableReference ( "," VariableReference )* "=")? MethodCallExpression NEWLINE
    private Optional<MethodCallStatementNode> MethodCall() throws SyntaxErrorException {
        List<VariableReferenceNode> returnValues = new LinkedList<>();
        while (tokenManager.matchAndRemove(Token.TokenTypes.COMMA).isPresent()) {
            Optional<VariableReferenceNode> returnValue = VariableReference();
            if (returnValue.isEmpty())
                throw new SyntaxErrorException("Variable reference expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
            returnValues.addLast(returnValue.get());
            if (tokenManager.matchAndRemove(Token.TokenTypes.ASSIGN).isPresent())
                break;
            else if (tokenManager.peek(0).isPresent() && tokenManager.peek(0).get().getType() != Token.TokenTypes.COMMA)
                throw new SyntaxErrorException("Comma or assignment operator expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }
        Optional<MethodCallExpressionNode> methodCallExpression = MethodCallExpression();
        if (methodCallExpression.isEmpty())
            throw new SyntaxErrorException("Method call expression expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        MethodCallStatementNode methodCall = new MethodCallStatementNode(methodCallExpression.get());
        methodCall.returnValues = returnValues;
        return Optional.of(methodCall);
    }

    // MethodCallExpression = (IDENTIFIER ".")? IDENTIFIER "(" (Expression ("," Expression )* )? ")"
    private Optional<MethodCallExpressionNode> MethodCallExpression() throws SyntaxErrorException {
        if (tokenManager.peek(1).isPresent() && tokenManager.peek(1).get().getType() != Token.TokenTypes.DOT && tokenManager.peek(1).get().getType() != Token.TokenTypes.LPAREN)
            return Optional.empty();
        MethodCallExpressionNode methodCallExpression = new MethodCallExpressionNode();
        if (tokenManager.peek(1).get().getType() == Token.TokenTypes.DOT) {
            Optional<Token> object = tokenManager.matchAndRemove(Token.TokenTypes.WORD);
            if (object.isEmpty())
                throw new SyntaxErrorException("Object name expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
            methodCallExpression.objectName = Optional.of(object.get().getValue());
            tokenManager.matchAndRemove(Token.TokenTypes.DOT);
        }
        else
            methodCallExpression.objectName = Optional.empty();
        Optional<Token> method = tokenManager.matchAndRemove(Token.TokenTypes.WORD);
        if (method.isEmpty())
            throw new SyntaxErrorException("Method name expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        methodCallExpression.methodName = method.get().getValue();
        if (tokenManager.matchAndRemove(Token.TokenTypes.LPAREN).isEmpty())
            throw new SyntaxErrorException("Left parenthesis expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        if (tokenManager.peek(0).isPresent() && tokenManager.peek(0).get().getType() != Token.TokenTypes.RPAREN) {
            Optional<ExpressionNode> firstExpression = Expression();
            if (firstExpression.isEmpty())
                throw new SyntaxErrorException("Unexpected token inside parentheses", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
            methodCallExpression.parameters.addLast(firstExpression.get());
            while (tokenManager.matchAndRemove(Token.TokenTypes.COMMA).isPresent()) {
                Optional<ExpressionNode> expression = Expression();
                if (expression.isEmpty())
                    throw new SyntaxErrorException("Unexpected token inside parentheses", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
                methodCallExpression.parameters.addLast(expression.get());
            }
        }
        if (tokenManager.matchAndRemove(Token.TokenTypes.RPAREN).isEmpty())
            throw new SyntaxErrorException("Right parenthesis expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        return Optional.of(methodCallExpression);
    }

    // Expression = Term ( ("+"|"-") Term )*
    private Optional<ExpressionNode> Expression() throws SyntaxErrorException {
        Optional<ExpressionNode> term = Term();
        if (term.isEmpty())
            return Optional.empty();
        if (tokenManager.peek(0).isPresent() && (tokenManager.nextIsEither(Token.TokenTypes.PLUS, Token.TokenTypes.MINUS))) {
            MathOpNode mathOp = new MathOpNode();
            mathOp.left = term.get();
            if (tokenManager.matchAndRemove(Token.TokenTypes.PLUS).isPresent())
                mathOp.op = MathOpNode.MathOperations.add;
            else if (tokenManager.matchAndRemove(Token.TokenTypes.MINUS).isPresent())
                mathOp.op = MathOpNode.MathOperations.subtract;
            Optional<ExpressionNode> term2 = Term();
            if (term2.isEmpty())
                throw new SyntaxErrorException("Term expression expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
            mathOp.right = term2.get();
            while (tokenManager.nextIsEither(Token.TokenTypes.PLUS, Token.TokenTypes.MINUS)) {
                MathOpNode temp = mathOp;
                mathOp = new MathOpNode();
                mathOp.left = temp;
                if (tokenManager.matchAndRemove(Token.TokenTypes.PLUS).isPresent())
                    mathOp.op = MathOpNode.MathOperations.add;
                else if (tokenManager.matchAndRemove(Token.TokenTypes.MINUS).isPresent())
                    mathOp.op = MathOpNode.MathOperations.subtract;
                Optional<ExpressionNode> termX = Term();
                if (termX.isEmpty())
                    throw new SyntaxErrorException("Term expression expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
                mathOp.right = termX.get();
            }
            return Optional.of(mathOp);
        }
        return term;
    }

    // Term = Factor ( ("*"|"/"|"%") Factor )*
    private Optional<ExpressionNode> Term() throws SyntaxErrorException {
        Optional<ExpressionNode> factor = Factor();
        if (factor.isEmpty())
            return Optional.empty();
        if (tokenManager.peek(0).isPresent() && (tokenManager.peek(0).get().getType() == Token.TokenTypes.TIMES || tokenManager.nextIsEither(Token.TokenTypes.DIVIDE, Token.TokenTypes.MODULO))) {
            MathOpNode mathOp = new MathOpNode();
            mathOp.left = factor.get();
            if (tokenManager.matchAndRemove(Token.TokenTypes.TIMES).isPresent())
                mathOp.op = MathOpNode.MathOperations.multiply;
            else if (tokenManager.matchAndRemove(Token.TokenTypes.DIVIDE).isPresent())
                mathOp.op = MathOpNode.MathOperations.divide;
            else if (tokenManager.matchAndRemove(Token.TokenTypes.DIVIDE).isPresent())
                mathOp.op = MathOpNode.MathOperations.modulo;
            Optional<ExpressionNode> factor2 = Factor();
            if (factor2.isEmpty())
                throw new SyntaxErrorException("Factor expression expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
            mathOp.right = factor2.get();
            while (tokenManager.peek(0).get().getType() == Token.TokenTypes.TIMES || tokenManager.nextIsEither(Token.TokenTypes.DIVIDE, Token.TokenTypes.MODULO)) {
                MathOpNode temp = mathOp;
                mathOp = new MathOpNode();
                mathOp.left = temp;
                if (tokenManager.matchAndRemove(Token.TokenTypes.TIMES).isPresent())
                    mathOp.op = MathOpNode.MathOperations.multiply;
                else if (tokenManager.matchAndRemove(Token.TokenTypes.DIVIDE).isPresent())
                    mathOp.op = MathOpNode.MathOperations.divide;
                else if (tokenManager.matchAndRemove(Token.TokenTypes.MODULO).isPresent())
                    mathOp.op = MathOpNode.MathOperations.modulo;
                Optional<ExpressionNode> factorX = Factor();
                if (factorX.isEmpty())
                    throw new SyntaxErrorException("Factor expression expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
                mathOp.right = factorX.get();
            }
            return Optional.of(mathOp);
        }
        return factor;
    }

    // Factor = NUMBER | VariableReference |  STRINGLITERAL | CHARACTERLITERAL | MethodCallExpression | "(" Expression ")" | "new" IDENTIFIER "(" (Expression ("," Expression )*)? ")"
    private Optional<ExpressionNode> Factor() throws SyntaxErrorException {
        if (tokenManager.peek(0).isPresent() && tokenManager.peek(0).get().getType() == Token.TokenTypes.NUMBER) {
            Optional<Token> number = tokenManager.matchAndRemove(Token.TokenTypes.NUMBER);
            NumericLiteralNode numericLiteral = new NumericLiteralNode();
            numericLiteral.value = Float.parseFloat(number.get().getValue());
            return Optional.of(numericLiteral);
        }
        if (tokenManager.peek(0).get().getType() == Token.TokenTypes.QUOTEDSTRING) {
            Optional<Token> quotedString = tokenManager.matchAndRemove(Token.TokenTypes.QUOTEDSTRING);
            StringLiteralNode stringLiteral = new StringLiteralNode();
            stringLiteral.value = quotedString.get().getValue();
            return Optional.of(stringLiteral);
        }
        if (tokenManager.peek(0).get().getType() == Token.TokenTypes.QUOTEDCHARACTER) {
            Optional<Token> quotedCharacter = tokenManager.matchAndRemove(Token.TokenTypes.QUOTEDCHARACTER);
            CharLiteralNode charLiteral = new CharLiteralNode();
            charLiteral.value = quotedCharacter.get().getValue().charAt(0);
            return Optional.of(charLiteral);
        }
        if (tokenManager.matchAndRemove(Token.TokenTypes.LPAREN).isPresent()) {
            Optional<ExpressionNode> expression = Expression();
            if (expression.isEmpty())
                throw new SyntaxErrorException("Expression expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
            if (tokenManager.matchAndRemove(Token.TokenTypes.RPAREN).isEmpty())
                throw new SyntaxErrorException("Right parenthesis expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
            return expression;
        }
        if (tokenManager.matchAndRemove(Token.TokenTypes.NEW).isPresent()) {
            Optional<Token> classToken = tokenManager.matchAndRemove(Token.TokenTypes.WORD);
            if (classToken.isEmpty())
                throw new SyntaxErrorException("Class name expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
            NewNode newNode = new NewNode();
            newNode.className = classToken.get().getValue();
            if (tokenManager.matchAndRemove(Token.TokenTypes.LPAREN).isEmpty())
                throw new SyntaxErrorException("Left parenthesis expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
            Optional<ExpressionNode> firstExpression = Expression();
            if (firstExpression.isPresent()) {
                newNode.parameters.addLast(firstExpression.get());
                while (tokenManager.matchAndRemove(Token.TokenTypes.COMMA).isPresent()) {
                    Optional<ExpressionNode> expression = Expression();
                    if (expression.isEmpty())
                        throw new SyntaxErrorException("Expression expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
                    newNode.parameters.addLast(expression.get());
                }
            }
            if (tokenManager.matchAndRemove(Token.TokenTypes.RPAREN).isEmpty())
                throw new SyntaxErrorException("Right parenthesis expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
            return Optional.of(newNode);
        }
        Optional<MethodCallExpressionNode> methodCallExpression = MethodCallExpression();
        if (methodCallExpression.isPresent())
            return Optional.of(methodCallExpression.get());
        Optional<VariableReferenceNode> variableReference = VariableReference();
        if (variableReference.isPresent())
            return Optional.of(variableReference.get());
        return Optional.empty();
    }

    // VariableReference = IDENTIFIER
    private Optional<VariableReferenceNode> VariableReference() {
        Optional<Token> name = tokenManager.matchAndRemove(Token.TokenTypes.WORD);
        if (name.isEmpty())
            return Optional.empty();
        VariableReferenceNode variableReference = new VariableReferenceNode();
        variableReference.name = name.get().getValue();
        return Optional.of(variableReference);
    }

    private void requireNewLine() throws SyntaxErrorException {
        if (tokenManager.matchAndRemove(Token.TokenTypes.NEWLINE).isPresent() || (tokenManager.peek(0).isPresent() && tokenManager.peek(0).get().getType() == Token.TokenTypes.DEDENT))
            while (tokenManager.matchAndRemove(Token.TokenTypes.NEWLINE).isPresent()) {  }
        else
            throw new SyntaxErrorException("Newline expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
    }

    private Optional<StatementNode> disambiguate() throws SyntaxErrorException {
        Optional<MethodCallExpressionNode> methodCallExpression = MethodCallExpression();
        if (methodCallExpression.isPresent()) {
            requireNewLine();
            return Optional.of(new MethodCallStatementNode(methodCallExpression.get()));
        }
        Optional<VariableReferenceNode> variableReference = VariableReference();
        if (variableReference.isEmpty())
            return Optional.empty();
        if (tokenManager.peek(0).isPresent() && tokenManager.peek(0).get().getType() == Token.TokenTypes.ASSIGN) {
            AssignmentNode assignment = Assignment().get();
            assignment.target = variableReference.get();
            requireNewLine();
            if (assignment.expression instanceof MethodCallExpressionNode) {
                MethodCallStatementNode methodAssignment = new MethodCallStatementNode((MethodCallExpressionNode) assignment.expression);
                methodAssignment.returnValues.addLast(assignment.target);
                return Optional.of(methodAssignment);
            }
            return Optional.of(assignment);
        }
        if (tokenManager.peek(0).get().getType() == Token.TokenTypes.COMMA) {
            MethodCallStatementNode methodCall = MethodCall().get();
            methodCall.returnValues.addFirst(variableReference.get());
            requireNewLine();
            return Optional.of(methodCall);
        }
        throw new SyntaxErrorException("Assignment or method call expected", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
    }
}