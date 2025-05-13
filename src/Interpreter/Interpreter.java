package Interpreter;

import AST.*;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class Interpreter {
    private TranNode top;
    private List<MemberNode> memberNodes = new LinkedList<>();
    private HashMap<String, InterpreterDataType> members = new HashMap<>();
    // private HashMap<String, InterpreterDataType> methodParameters = new HashMap<>();
    private List<MethodDeclarationNode> methods = new LinkedList<>();
    private List<MethodDeclarationNode> sharedMethods = new LinkedList<>();

    /** Constructor - get the interpreter ready to run. Set members from parameters and "prepare" the class.
     *
     * Store the tran node.
     * Add any built-in methods to the AST
     * @param top - the head of the AST
     */
    public Interpreter(TranNode top) {
        this.top = top;
        for (ClassNode classNode : top.Classes) {
            for (MemberNode member : classNode.members) {
                memberNodes.add(member);
                members.put(member.declaration.name, instantiate(member.declaration.type));
            }
            for (MethodDeclarationNode method : classNode.methods)
                methods.add(method);
        }
        members.put("true", new BooleanIDT(true));
        members.put("false", new BooleanIDT(false));
        BuiltInMethodDeclarationNode write = new ConsoleWrite();
        write.name = "write";
        write.isShared = true;
        write.isPrivate = false;
        write.isVariadic = true;
        ClassNode console = new ClassNode();
        console.name = "console";
        console.methods.add(write);
        this.top.Classes.add(console);
        members.put(console.name, new ObjectIDT(console));
        for (MethodDeclarationNode method : console.methods) {
            if (method.isShared)
                sharedMethods.add(method);
        }
    }

    /**
     * This is the public interface to the interpreter. After parsing, we will create an interpreter and call start to
     * start interpreting the code.
     *
     * Search the classes in Tran for a method that is "isShared", named "start", that is not private and has no parameters
     * Call "InterpretMethodCall" on that method, then return.
     * Throw an exception if no such method exists.
     */
    public void start() {
        // Find the "start" method
        for (ClassNode classNode : top.Classes) {
            for (MethodDeclarationNode method : classNode.methods) {
                if (method.name.equals("start") && method.isShared && !method.isPrivate && method.parameters.isEmpty()) {
                    interpretMethodCall(Optional.empty(), method, List.of());
                    return;
                }
            }
        }
        throw new RuntimeException("No 'start' method found");
    }

    //              Running Methods

    /**
     * Find the method (local to this class, shared (like Java's system.out.print), or a method on another class)
     * Evaluate the parameters to have a list of values
     * Use interpretMethodCall() to actually run the method.
     *
     * Call GetParameters() to get the parameter value list
     * Find the method. This is tricky - there are several cases:
     * someLocalMethod() - has NO object name. Look in "object"
     * console.write() - the objectName is a CLASS and the method is shared
     * bestStudent.getGPA() - the objectName is a local or a member
     *
     * Once you find the method, call InterpretMethodCall() on it. Return the list that it returns.
     * Throw an exception if we can't find a match.
     * @param object - the object we are inside right now (might be empty)
     * @param locals - the current local variables
     * @param mc - the method call
     * @return - the return values
     */
    private List<InterpreterDataType> findMethodForMethodCallAndRunIt(Optional<ObjectIDT> object, HashMap<String, InterpreterDataType> locals, MethodCallStatementNode mc) {
        List<InterpreterDataType> parameters = getMethodParameters(object, locals, mc);
        Optional<MethodDeclarationNode> methodOptional = Optional.empty();
        if (object.isPresent()) {
            for (MethodDeclarationNode method : object.get().astNode.methods) {
                if (mc.methodName.equals(method.name)) {
                    methodOptional = Optional.of(method);
                    break;
                }
            }
            if (methodOptional.isEmpty()) {
                for (MethodDeclarationNode method : sharedMethods) {
                    if (mc.methodName.equals(method.name)) {
                        methodOptional = Optional.of(method);
                        break;
                    }
                }
                if (methodOptional.isEmpty()) {
                    throw new RuntimeException("Unable to resolve method " + mc.methodName);
                }
            }
        }
        else {
            for (java.util.Map.Entry<String, InterpreterDataType> local : locals.entrySet()) {
                if (local.getValue() instanceof ReferenceIDT ref && ref.refersTo.isPresent()) {
                    for (MethodDeclarationNode method : ref.refersTo.get().astNode.methods) {
                        if (mc.methodName.equals(method.name) && mc.objectName.isPresent() && mc.objectName.get().equals(local.getKey())) {
                            methodOptional = Optional.of(method);
                            members = ref.refersTo.get().members;
                            break;
                        }
                    }
                    if (methodOptional.isPresent())
                        break;
                }
            }
            if (methodOptional.isEmpty()) {
                for (MethodDeclarationNode method : methods) {
                    if (mc.methodName.equals(method.name)) {
                        methodOptional = Optional.of(method);
                        break;
                    }
                }
                if (methodOptional.isEmpty()) {
                    if (mc.objectName.isPresent() && mc.objectName.get().equals("console") && mc.methodName.equals("write"))
                        methodOptional = Optional.of(new ConsoleWrite());
                    else
                        throw new RuntimeException("Unable to resolve method " + mc.methodName);
                }
            }
        }
        List<InterpreterDataType> result = interpretMethodCall(object, methodOptional.get(), parameters);
        return result;
    }

    /**
     * Run a "prepared" method (found, parameters evaluated)
     * This is split from findMethodForMethodCallAndRunIt() because there are a few cases where we don't need to do the finding:
     * in start() and dealing with loops with iterator objects, for example.
     *
     * Check to see if "m" is a built-in. If so, call Execute() on it and return
     * Make local variables, per "m"
     * If the number of passed in values doesn't match m's "expectations", throw
     * Add the parameters by name to locals.
     * Call InterpretStatementBlock
     * Build the return list - find the names from "m", then get the values for those names and add them to the list.
     * @param object - The object this method is being called on (might be empty for shared)
     * @param m - Which method is being called
     * @param values - The values to be passed in
     * @return the returned values from the method
     */
    private List<InterpreterDataType> interpretMethodCall(Optional<ObjectIDT> object, MethodDeclarationNode m, List<InterpreterDataType> values) {
        if (m instanceof BuiltInMethodDeclarationNode bm)
            return bm.Execute(values);
        if (m.parameters.size() != values.size())
            throw new RuntimeException("Incorrect number of parameters for given method");
        HashMap<String, InterpreterDataType> locals = new HashMap<>(members);
        if (object.isPresent())
            locals.putAll(object.get().members);
        for (int i = 0; i < m.locals.size(); i++)
            locals.put(m.locals.get(i).name, instantiate(m.locals.get(i).type));
        for (int i = 0; i < m.parameters.size(); i++)
            locals.put(m.parameters.get(i).name, values.get(i));
        for (int i = 0; i < m.returns.size(); i++)
            locals.put(m.returns.get(i).name, instantiate(m.returns.get(i).type));
        interpretStatementBlock(object, m.statements, locals);
        var retVal = new LinkedList<InterpreterDataType>();
        for (int i = 0; i < m.returns.size(); i++)
            retVal.add(locals.get(m.returns.get(i).name));
        return retVal;
    }

    //              Running Constructors

    /**
     * This is a special case of the code for methods. Just different enough to make it worthwhile to split it out.
     *
     * Call GetParameters() to populate a list of IDTs
     * Call GetClassByName() to find the class for the constructor
     * If we didn't find the class, throw an exception
     * Find a constructor that is a good match - use DoesConstructorMatch()
     * Call InterpretConstructorCall() on the good match
     * @param callerObj - the object that we are inside when we called the constructor
     * @param locals - the current local variables (used to fill parameters)
     * @param n  - the constructor call for this construction
     * @param newOne - the object that we just created that we are calling the constructor for
     */
    private void findConstructorAndRunIt(Optional<ObjectIDT> callerObj, HashMap<String, InterpreterDataType> locals, NewNode n, ObjectIDT newOne) {
        List<InterpreterDataType> parameters = getConstructorParameters(callerObj, locals, n);
        Optional<ClassNode> optionalClass = getClassByName(newOne.astNode.name);
        if (optionalClass.isEmpty())
            throw new RuntimeException("Unable to resolve class " + newOne.astNode.name);
        for (ConstructorNode constructor : optionalClass.get().constructors) {
            if (doesConstructorMatch(constructor, n, parameters)) {
                interpretConstructorCall(newOne, constructor, parameters);
                return;
            }
        }
        throw new RuntimeException("Could not find constructor with given parameters");
    }

    /**
     * Similar to interpretMethodCall, but "just different enough" - for example, constructors don't return anything.
     *
     * Creates local variables (as defined by the ConstructorNode), calls Instantiate() to do the creation
     * Checks to ensure that the right number of parameters were passed in, if not throw.
     * Adds the parameters (with the names from the ConstructorNode) to the locals.
     * Calls InterpretStatementBlock
     * @param object - the object that we allocated
     * @param c - which constructor is being called
     * @param values - the parameter values being passed to the constructor
     */
    private void interpretConstructorCall(ObjectIDT object, ConstructorNode c, List<InterpreterDataType> values) {
        HashMap<String, InterpreterDataType> locals = new HashMap<>();
        for (VariableDeclarationNode variable : c.locals) {
            InterpreterDataType idt = instantiate(variable.type);
            locals.put(variable.name, idt);
        }
        for (int i = 0; i < values.size(); i++) {
            object.members.put(memberNodes.get(i).declaration.name, instantiate(memberNodes.get(i).declaration.type));
            locals.put(c.parameters.get(i).name, values.get(i));
        }
        interpretStatementBlock(Optional.of(object), c.statements, locals);
    }

    //              Running Instructions

    /**
     * Given a block (which could be from a method or an "if" or "loop" block), run each statement.
     * Blocks, by definition, do ever statement, so iterating over the statements makes sense.
     *
     * For each statement in statements:
     * check the type:
     *      For AssignmentNode, FindVariable() to get the target. Evaluate() the expression. Call Assign() on the target with the result of Evaluate()
     *      For MethodCallStatementNode, call doMethodCall(). Loop over the returned values and copy the into our local variables
     *      For LoopNode - there are 2 kinds.
     *          Setup:
     *          If this is a Loop over an iterator (an Object node whose class has "iterator" as an interface)
     *              Find the "getNext()" method; throw an exception if there isn't one
     *          Loop:
     *          While we are not done:
     *              if this is a boolean loop, Evaluate() to get true or false.
     *              if this is an iterator, call "getNext()" - it has 2 return values. The first is a boolean (was there another?), the second is a value
     *              If the loop has an assignment variable, populate it: for boolean loops, the true/false. For iterators, the "second value"
     *              If our answer from above is "true", InterpretStatementBlock() on the body of the loop.
     *       For If - Evaluate() the condition. If true, InterpretStatementBlock() on the if's statements. If not AND there is an else, InterpretStatementBlock on the else body.
     * @param object - the object that this statement block belongs to (used to get member variables and any members without an object)
     * @param statements - the statements to run
     * @param locals - the local variables
     */
    private void interpretStatementBlock(Optional<ObjectIDT> object, List<StatementNode> statements, HashMap<String, InterpreterDataType> locals) {
        for (StatementNode statement : statements) {
            if (statement instanceof AssignmentNode assignment) {
                InterpreterDataType target = findVariable(assignment.target.name, locals, object);
                target.Assign(evaluate(locals, object, assignment.expression)); // WORK IN PROGRESS
                continue;
            }
            if (statement instanceof MethodCallStatementNode methodCall) {
                if (methodCall.objectName.isPresent() && !methodCall.objectName.get().equals("console")) {
                    if (locals.get(methodCall.objectName.get()) instanceof ReferenceIDT ref) {
                        Optional<ObjectIDT> createdObject = (ref.refersTo);
                        if (methodCall.objectName.isPresent() && createdObject.isPresent()) {
                            findMethodForMethodCallAndRunIt(createdObject, locals, methodCall);
                            continue;
                        }
                    }
                    else {
                        Optional<ObjectIDT> otherObject = Optional.ofNullable((ObjectIDT)locals.get(methodCall.objectName.get()));
                        if (otherObject.isPresent()) {
                            findMethodForMethodCallAndRunIt(otherObject, locals, methodCall);
                            continue;
                        }
                    }
                }
                findMethodForMethodCallAndRunIt(object, locals, methodCall);
                continue;
            }
            if (statement instanceof LoopNode loop) {
                if (loop.expression instanceof MethodCallExpressionNode mc && mc.objectName.isPresent() && mc.methodName.equals("times")) {
                    if (locals.get(mc.objectName.get()) instanceof NumberIDT) {
                        if (loop.assignment.isPresent()) {
                            locals.put(loop.assignment.get().name, new NumberIDT(0));
                            while (((NumberIDT)locals.get(loop.assignment.get().name)).Value < ((NumberIDT)locals.get(mc.objectName.get())).Value) {
                                interpretStatementBlock(object, loop.statements, locals);
                                ((NumberIDT)locals.get(loop.assignment.get().name)).Value++;
                            }
                        }
                        else {
                            int i = 0;
                            while (i < ((NumberIDT)locals.get(mc.objectName.get())).Value) {
                                interpretStatementBlock(object, loop.statements, locals);
                                i++;
                            }
                        }
                    }
                    else
                        throw new RuntimeException("Attempted to call iterator on non-numeric value");
                }
                else {
                    if (evaluate(locals, object, loop.expression) instanceof BooleanIDT result) {
                        if (loop.assignment.isPresent()) {
                            locals.put(loop.assignment.get().name, result);
                            while (((BooleanIDT)locals.get(loop.assignment.get().name)).Value) {
                                interpretStatementBlock(object, loop.statements, locals);
                                locals.put(loop.assignment.get().name, evaluate(locals, object, loop.expression));
                            }
                        }
                        else {
                            while (((BooleanIDT)evaluate(locals, object, loop.expression)).Value)
                                interpretStatementBlock(object, loop.statements, locals);
                        }
                    }
                    else
                        throw new RuntimeException("Illegal loop condition");
                }
                continue;
            }
            if (statement instanceof IfNode ifNode) {
                InterpreterDataType condition = evaluate(locals, object, ifNode.condition);
                if (condition instanceof BooleanIDT result) {
                    if (result.Value)
                        interpretStatementBlock(object, ifNode.statements, locals);
                    else if (ifNode.elseStatement.isPresent())
                        interpretStatementBlock(object, ifNode.elseStatement.get().statements, locals);
                }
            }
        }
    }

    /**
     *  evaluate() processes everything that is an expression - math, variables, boolean expressions.
     *  There is a good bit of recursion in here, since math and comparisons have left and right sides that need to be evaluated.
     *
     * See the How To Write an Interpreter document for examples
     * For each possible ExpressionNode, do the work to resolve it:
     * BooleanLiteralNode - create a new BooleanLiteralNode with the same value
     *      - Same for all of the basic data types
     * BooleanOpNode - Evaluate() left and right, then perform either and/or on the results.
     * CompareNode - Evaluate() both sides. Do good comparison for each data type
     * MathOpNode - Evaluate() both sides. If they are both numbers, do the math using the built-in operators. Also handle String + String as concatenation (like Java)
     * MethodCallExpression - call doMethodCall() and return the first value
     * VariableReferenceNode - call findVariable()
     * @param locals the local variables
     * @param object - the current object we are running
     * @param expression - some expression to evaluate
     * @return a value
     */
    private InterpreterDataType evaluate(HashMap<String, InterpreterDataType> locals, Optional<ObjectIDT> object, ExpressionNode expression) {
        if (expression instanceof BooleanLiteralNode bool)
            return new BooleanIDT(bool.value);
        if (expression instanceof NumericLiteralNode number)
            return new NumberIDT(number.value);
        if (expression instanceof StringLiteralNode string)
            return new StringIDT(string.value);
        if (expression instanceof CharLiteralNode character)
            return new CharIDT(character.value);
        if (expression instanceof BooleanOpNode boolOp) {
            InterpreterDataType left = evaluate(locals, object, boolOp.left);
            InterpreterDataType right = evaluate(locals, object, boolOp.right);
            if (left instanceof BooleanIDT leftBool && right instanceof BooleanIDT rightBool) {
                if (boolOp.op == BooleanOpNode.BooleanOperations.and)
                    return new BooleanIDT(leftBool.Value && rightBool.Value);
                if (boolOp.op == BooleanOpNode.BooleanOperations.or)
                    return new BooleanIDT(leftBool.Value || rightBool.Value);
            }
            throw new RuntimeException("Attempted boolean operation between incompatible types");
        }
        if (expression instanceof CompareNode compare) {
            InterpreterDataType left = evaluate(locals, object, compare.left);
            InterpreterDataType right = evaluate(locals, object, compare.right);
            if (left instanceof NumberIDT leftNumber && right instanceof NumberIDT rightNumber) {
                if (compare.op == CompareNode.CompareOperations.lt)
                    return new BooleanIDT(leftNumber.Value < rightNumber.Value);
                if (compare.op == CompareNode.CompareOperations.le)
                    return new BooleanIDT(leftNumber.Value <= rightNumber.Value);
                if (compare.op == CompareNode.CompareOperations.gt)
                    return new BooleanIDT(leftNumber.Value > rightNumber.Value);
                if (compare.op == CompareNode.CompareOperations.ge)
                    return new BooleanIDT(leftNumber.Value >= rightNumber.Value);
                if (compare.op == CompareNode.CompareOperations.eq)
                    return new BooleanIDT(leftNumber.Value == rightNumber.Value);
                if (compare.op == CompareNode.CompareOperations.ne)
                    return new BooleanIDT(leftNumber.Value != rightNumber.Value);
            }
            if (left instanceof StringIDT leftString && right instanceof StringIDT rightString) {
                if (compare.op == CompareNode.CompareOperations.lt)
                    return new BooleanIDT(leftString.Value.compareTo(rightString.Value) < 0);
                if (compare.op == CompareNode.CompareOperations.le)
                    return new BooleanIDT(leftString.Value.compareTo(rightString.Value) <= 0);
                if (compare.op == CompareNode.CompareOperations.gt)
                    return new BooleanIDT(leftString.Value.compareTo(rightString.Value) > 0);
                if (compare.op == CompareNode.CompareOperations.ge)
                    return new BooleanIDT(leftString.Value.compareTo(rightString.Value) >= 0);
                if (compare.op == CompareNode.CompareOperations.eq)
                    return new BooleanIDT(leftString.Value.compareTo(rightString.Value) == 0);
                if (compare.op == CompareNode.CompareOperations.ne)
                    return new BooleanIDT(leftString.Value.compareTo(rightString.Value) != 0);
            }
            if (left instanceof CharIDT leftChar && right instanceof CharIDT rightChar) {
                if (compare.op == CompareNode.CompareOperations.lt)
                    return new BooleanIDT(leftChar.Value < rightChar.Value);
                if (compare.op == CompareNode.CompareOperations.le)
                    return new BooleanIDT(leftChar.Value <= rightChar.Value);
                if (compare.op == CompareNode.CompareOperations.gt)
                    return new BooleanIDT(leftChar.Value > rightChar.Value);
                if (compare.op == CompareNode.CompareOperations.ge)
                    return new BooleanIDT(leftChar.Value >= rightChar.Value);
                if (compare.op == CompareNode.CompareOperations.eq)
                    return new BooleanIDT(leftChar.Value == rightChar.Value);
                if (compare.op == CompareNode.CompareOperations.ne)
                    return new BooleanIDT(leftChar.Value != rightChar.Value);
            }
            throw new RuntimeException("Attempted comparison operation between incompatible types");
        }
        if (expression instanceof MathOpNode math) {
            InterpreterDataType left = evaluate(locals, object, math.left);
            InterpreterDataType right = evaluate(locals, object, math.right);
            if (left instanceof NumberIDT leftNumber && right instanceof NumberIDT rightNumber) {
                if (math.op == MathOpNode.MathOperations.add)
                    return new NumberIDT(leftNumber.Value + rightNumber.Value);
                if (math.op == MathOpNode.MathOperations.subtract)
                    return new NumberIDT(leftNumber.Value - rightNumber.Value);
                if (math.op == MathOpNode.MathOperations.multiply)
                    return new NumberIDT(leftNumber.Value * rightNumber.Value);
                if (math.op == MathOpNode.MathOperations.divide) {
                    if (rightNumber.Value != 0)
                        return new NumberIDT(leftNumber.Value / rightNumber.Value);
                    throw new RuntimeException("Attempted division by zero");
                }
                if (math.op == MathOpNode.MathOperations.modulo) {
                    if (rightNumber.Value != 0)
                        return new NumberIDT(leftNumber.Value % rightNumber.Value);
                    throw new RuntimeException("Attempted modulo by zero");
                }
            }
            if (left instanceof StringIDT leftString && right instanceof StringIDT rightString) {
                if (math.op == MathOpNode.MathOperations.add)
                    return new StringIDT(leftString.Value + rightString.Value);
                throw new RuntimeException("Attempted illegal operation on string literals");
            }
            throw new RuntimeException("Attempted mathematical operation between incompatible types");
        }
        if (expression instanceof MethodCallExpressionNode methodCall)
            return findMethodForMethodCallAndRunIt(object, locals, new MethodCallStatementNode(methodCall)).get(0);
        if (expression instanceof NewNode newExp) {
            Optional<ClassNode> optionalClassNode = getClassByName(newExp.className);
            if (optionalClassNode.isEmpty())
                throw new RuntimeException("Class " + newExp.className + " not found");
            ObjectIDT newObject = new ObjectIDT(optionalClassNode.get());
            findConstructorAndRunIt(object, locals, newExp, newObject);
            return newObject;
        }
        if (expression instanceof VariableReferenceNode boolValue && (boolValue.name.equals("true") || boolValue.name.equals("false")))
            return new BooleanIDT(Boolean.parseBoolean(boolValue.name));
        if (expression instanceof VariableReferenceNode varRef)
            return findVariable(varRef.name, locals, object);
        throw new IllegalArgumentException();
    }

    //              Utility Methods

    /**
     * Used when trying to find a match to a method call. Given a method declaration, does it match this method call?
     * We double-check with the parameters, too, although in theory JUST checking the declaration to the call should be enough.
     *
     * Match names, parameter counts (both declared count vs method call and declared count vs value list), return counts.
     * If all of those match, consider the types (use TypeMatchToIDT).
     * If everything is OK, return true, else return false.
     * Note - if m is a built-in and isVariadic is true, skip all of the parameter validation.
     * @param m - the method declaration we are considering
     * @param mc - the method call we are trying to match
     * @param parameters - the parameter values for this method call
     * @return does this method match the method call?
     */
    private boolean doesMatch(MethodDeclarationNode m, MethodCallStatementNode mc, List<InterpreterDataType> parameters) {
        if (!mc.methodName.equals(m.name))
            return false;
        if (!(m instanceof BuiltInMethodDeclarationNode bm && bm.isVariadic)) {
            if (parameters.size() != m.parameters.size())
                return false;
            for (int i = 0; i < m.parameters.size(); i++) {
                if (!typeMatchToIDT(m.parameters.get(i).type, parameters.get(i)))
                    return false;
            }
        }
        if (mc.returnValues.size() != m.returns.size())
            return false; // SHOULD I CHECK THE RETURN TYPES? IF SO, HOW?
        return true;
    }

    /**
     * Very similar to DoesMatch() except simpler - there are no return values, the name will always match.
     * @param c - a particular constructor
     * @param n - the constructor call
     * @param parameters - the parameter values
     * @return does this constructor match the method call?
     */
    private boolean doesConstructorMatch(ConstructorNode c, NewNode n, List<InterpreterDataType> parameters) {
        if (parameters.size() != c.parameters.size())
            return false;
        for (int i = 0; i < c.parameters.size(); i++) {
            if (!typeMatchToIDT(c.parameters.get(i).type, parameters.get(i)))
                return false;
        }
        return true;
    }

    /**
     * Used when we call a method to get the list of values for the parameters.
     *
     * for each parameter in the method call, call Evaluate() on the parameter to get an IDT and add it to a list
     * @param object - the current object
     * @param locals - the local variables
     * @param mc - a method call
     * @return the list of method values
     */
    private List<InterpreterDataType> getMethodParameters(Optional<ObjectIDT> object, HashMap<String,InterpreterDataType> locals, MethodCallStatementNode mc) {
        List<InterpreterDataType> parameters = new LinkedList<>();
        for (ExpressionNode parameter : mc.parameters)
            parameters.add(evaluate(locals, object, parameter));
        return parameters;
    }

    /**
     * Used when we call a constructor to get the list of values for the parameters.
     *
     * for each parameter in the constructor call, call Evaluate() on the parameter to get an IDT and add it to a list
     * @param object - the current object
     * @param locals - the local variables
     * @param n - a constructor call
     * @return the list of constructor values
     */
    private List<InterpreterDataType> getConstructorParameters(Optional<ObjectIDT> object, HashMap<String,InterpreterDataType> locals, NewNode n) {
        List<InterpreterDataType> parameters = new LinkedList<>();
        for (ExpressionNode parameter : n.parameters)
            parameters.add(evaluate(locals, object, parameter));
        return parameters;
    }

    /**
     * Used when we have an IDT and we want to see if it matches a type definition
     * Commonly, when someone is making a function call - do the parameter values match the method declaration?
     *
     * If the IDT is a simple type (boolean, number, etc.) - does the string type match the name of that IDT ("boolean", etc.)
     * If the IDT is an object, check to see if the name matches OR the class has an interface that matches
     * If the IDT is a reference, check the inner (referred to) type
     * @param type the name of a data type (parameter to a method)
     * @param idt the IDT someone is trying to pass to this method
     * @return is this OK?
     */
    private boolean typeMatchToIDT(String type, InterpreterDataType idt) {
        if (idt instanceof NumberIDT)
            return type.equals("number");
        if (idt instanceof StringIDT)
            return type.equals("string");
        if (idt instanceof BooleanIDT)
            return type.equals("boolean");
        if (idt instanceof CharIDT)
            return type.equals("character");
        if (idt instanceof ObjectIDT object) {
            for (ClassNode classNode : top.Classes) {
                if (type.equals(classNode.name))
                    return true;
                for (String interfaceName : classNode.interfaces) {
                    if (type.equals(interfaceName))
                        return true;
                }
            }
            throw new RuntimeException("Unable to resolve type " + type);
        }
        if (idt instanceof ReferenceIDT reference) {
            return typeMatchToIDT(type, reference);
        }
        throw new RuntimeException("Unable to resolve type " + type);
    }

    /**
     * Find a method in an object that is the right match for a method call (same name, parameters match, etc. Uses doesMatch() to do most of the work)
     *
     * Given a method call, we want to loop over the methods for that class, looking for a method that matches (use DoesMatch) or throw
     * @param object - an object that we want to find a method on
     * @param mc - the method call
     * @param parameters - the parameter value list
     * @return a method or throws an exception
     */
    private MethodDeclarationNode getMethodFromObject(ObjectIDT object, MethodCallStatementNode mc, List<InterpreterDataType> parameters) {
        for (MethodDeclarationNode md : object.astNode.methods) {
            if (doesMatch(md, mc, parameters))
                return md;
        }
        throw new RuntimeException("Unable to resolve method call " + mc);
    }

    /**
     * Find a class, given the name. Just loops over the TranNode's classes member, matching by name.
     *
     * Loop over each class in the top node, comparing names to find a match.
     * @param name Name of the class to find
     * @return either a class node or empty if that class doesn't exist
     */
    private Optional<ClassNode> getClassByName(String name) {
        for (ClassNode classNode : top.Classes) {
            if (name.equals(classNode.name))
                return Optional.of(classNode);
        }
        return Optional.empty();
    }

    /**
     * Given an execution environment (the current object, the current local variables), find a variable by name.
     *
     * @param name  - the variable that we are looking for
     * @param locals - the current method's local variables
     * @param object - the current object (so we can find members)
     * @return the IDT that we are looking for or throw an exception
     */
    private InterpreterDataType findVariable(String name, HashMap<String,InterpreterDataType> locals, Optional<ObjectIDT> object) {
        if (object.isPresent()) {
            for (java.util.Map.Entry<String, InterpreterDataType> member : object.get().members.entrySet()) {
                if (name.equals(member.getKey()))
                    return member.getValue();
            }
        }
        for (java.util.Map.Entry<String, InterpreterDataType> local : locals.entrySet()) {
            if (name.equals(local.getKey()))
                return local.getValue();
        }
        for (java.util.Map.Entry<String, InterpreterDataType> member : members.entrySet()) {
            if (name.equals(member.getKey()))
                return member.getValue();
        }
        throw new RuntimeException("Unable to find variable " + name);
    }

    /**
     * Given a string (the type name), make an IDT for it.
     *
     * @param type The name of the type (string, number, boolean, character). Defaults to ReferenceIDT if not one of those.
     * @return an IDT with default values (0 for number, "" for string, false for boolean, ' ' for character)
     */
    private InterpreterDataType instantiate(String type) {
        switch (type) {
            case "number":
                return new NumberIDT(0);
            case "string":
                return new StringIDT("");
            case "boolean":
                return new BooleanIDT(false);
            case "character":
                return new CharIDT(' ');
            default:
                return new ReferenceIDT();
        }
    }
}
