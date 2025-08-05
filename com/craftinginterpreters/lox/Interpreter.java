package com.craftinginterpreters.lox;

import java.util.List;

class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {

    private Environment environment = new Environment();


    // Run the interpreter!
    void interpret(List<Stmt> statements) {
        try {
            // Handle single expression in REPL by evaluating and printing it
            if (statements.size() == 1 && statements.get(0) instanceof Stmt.Expression) {
                Expr expression = ((Stmt.Expression) statements.get(0)).expression;
                Object value = evaluate(expression);
                System.out.println(stringify(value));
            } else {
                for (Stmt statement : statements) {
                    execute(statement);
                }
            }
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
        }
    }



    // Convert literal expressions to a runtime value by directly grabbing it
    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    // Recursively evalute the expression inside the grouping
    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }


    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);

        // Directly apply the operator
        switch (expr.operator.type) {
            case BANG:
                return !isTruthy(right);
            case MINUS:
                checkNumberOperand(expr.operator, right);
                // Cast it at a number at runtime, this helps support Lox's dynamic typing
                return -(double)right;
        }

        // Unreachable.
        return null;
    }

    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double) return;
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    // Variable expressions look up the variable in the environment
    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        return environment.get(expr.name);
    }



    // 'false' and 'nil' are falsey, everything else is truthy in Lox
    private boolean isTruthy(Object object) {
        if (object == null) return false;
        // instanceof helps us support Lox's dynamic typing in Java's static type system
        if (object instanceof Boolean) return (boolean)object;
        return true;
    }

    // equivalent if both null or follows Java's standards for equality
    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null) return false;

        // can't call this on a null value
        return a.equals(b);
    }

    // Converts the result of an expression to a string for display
    private String stringify(Object object) {
        if (object == null) return "nil";

        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }

        return object.toString();
    }
    
    // Triggers the evaluation of an expression
    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    private void execute(Stmt stmt) {
        stmt.accept(this);
    }

    // execute statements in a particular environment then restores the previous environment
    void executeBlock(List<Stmt> statements, Environment environment) {
        Environment previous = this.environment;
        try {
            this.environment = environment;

            for (Stmt statement : statements) {
                execute(statement);
            }
        } finally {
            this.environment = previous;
        }
    }

    // Create new environment for block scope
    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    // for print statements, evaluate the expression and print the result
    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        // Variables are null unless we initialize them
        Object value = null;
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }

        environment.define(stmt.name.lexeme, value);
        return null;
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);
        environment.assign(expr.name, value);
        return value;
    }


    // Binary Operators
    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right); 

        switch (expr.operator.type) {
            // Comparison Operators
            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (double)left > (double)right;
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left >= (double)right;
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left < (double)right;
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left <= (double)right;
                    
            // Equality Operators
            case BANG_EQUAL: return !isEqual(left, right);
            case EQUAL_EQUAL: return isEqual(left, right);

            // Arithmetic Operators
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left - (double)right;
            case PLUS:
                // Check the type to see whether we are adding or concatenating
                if (left instanceof Double && right instanceof Double) {
                    return (double)left + (double)right;
                }
                if (left instanceof String || right instanceof String) {
                    return stringify(left) + stringify(right);
                }
                throw new RuntimeError(expr.operator, "Operands must be two numbers, two strings, or one number and one string. \n\t Left Operand: " + stringify(left) + "\tRight Operand: " + stringify(right));
            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                if ((double)right == 0) {
                    throw new RuntimeError(expr.operator, "Division by zero, undefined quotient.");
                }
                return (double)left / (double)right;
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double)left * (double)right;
        }

        // Unreachable.
        return null;
    }


    private void checkNumberOperands(Token operator, Object left, Object right) {
        if (left instanceof Double && right instanceof Double) return;
        
        throw new RuntimeError(operator, "Operands must be numbers.");
    }



}