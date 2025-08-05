package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;

import static com.craftinginterpreters.lox.TokenType.*;

class Parser {
    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    // Begin parsing!
    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }

        return statements; 
    }

    // Parse REPL input, which can be a single statement or an expression
    List<Stmt> parseREPL() {
        List<Stmt> statements = new ArrayList<>();

        TokenType first = peek().type;

        // Decide based on first token
        if (first == VAR || first == FUN || first == CLASS) {
            // Declaration or statement
            statements.add(declaration());
        } else if (first == PRINT || first == LEFT_BRACE || first == IF || first == WHILE || first == FOR || first == RETURN) {
            // Statement types
            statements.add(statement());
        } else {
            // Otherwise, parse as expression statement (no semicolon needed)
            Expr expr = expression();
            statements.add(new Stmt.Expression(expr));
        }

        return statements;
    }






    private Expr expression() {
        return assignment();
    }

    private Stmt declaration() {
        try {
            if (match(VAR)) return varDeclaration();

            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt statement() {
        if (match(PRINT)) return printStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());

        return expressionStatement();
    }

    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after expression.");
        return new Stmt.Expression(expr);
    }

    // add statements to the list/block until end of block `}` or EoF
    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }

        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }


    private Expr assignment() {
        Expr expr = equality();

        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            }

            error(equals, "Invalid assignment target."); 
        }

        return expr;
    }

    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expect variable name.");

        // Variables are null unless we initialize them
        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        }

        consume(SEMICOLON, "Expect ';' after variable declaration.");
        return new Stmt.Var(name, initializer);
    }


    private Expr equality() {
        Expr expr = comparison();

        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr comparison() {
        Expr expr = term();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr term() {
        Expr expr = factor();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr factor() {
        Expr expr = unary();

        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return primary();
    }

    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        // Give error if we don't find a closing parenthesis
        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        // We're on a token that can't start an expression, even though we are expecting an expression.
        throw error(peek(), "Expect expression.");
    }

    // Parsing Infrastructure 

    // if the current token has any of the given types, consume it and return true. otherwise, do not consume and return false
    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }

        return false;
    }

    // consume the current token if it is of the given type, otherwise throw an error with the given message
    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();

        throw error(peek(), message);
    }

    // return true if token is of given type
    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    // consume the current token and return it
    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    // return if at the end of the tokens list
    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    // return the current token without consuming it
    private Token peek() {
        return tokens.get(current);
    }

    // return the most recently consumed token
    // makes it easier to match() then use that matched token
    private Token previous() {
        return tokens.get(current - 1);
    }

    // return an error with the given token and message
    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }


    // Synchronization: skip tokens until we reach a point where we can continue parsing
    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            // good faith guess that we are at the end of a statement/line
            if (previous().type == SEMICOLON) return;

            // good faith guess that we are at the beginning of a statement/line
            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                return;
            }

            advance();
        }
    }
    

}