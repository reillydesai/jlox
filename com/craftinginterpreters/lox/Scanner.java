package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.craftinginterpreters.lox.TokenType.*;  

class Scanner {

    private static final Map<String, TokenType> keywords;

    static {
        keywords = new HashMap<>();
        keywords.put("and",    AND);
        keywords.put("class",  CLASS);
        keywords.put("else",   ELSE);
        keywords.put("false",  FALSE);
        keywords.put("for",    FOR);
        keywords.put("fun",    FUN);
        keywords.put("if",     IF);
        keywords.put("nil",    NIL);
        keywords.put("or",     OR);
        keywords.put("print",  PRINT);
        keywords.put("return", RETURN);
        keywords.put("super",  SUPER);
        keywords.put("this",   THIS);
        keywords.put("true",   TRUE);
        keywords.put("var",    VAR);
        keywords.put("while",  WHILE);
    }

    private final String source;
    private final List<Token> tokens = new ArrayList<>();

    private int start = 0;
    private int current = 0;
    private int line = 1;

    Scanner(String source) {
        this.source = source;
    }


    // Works through the source code character by character, identifying tokens.
    List<Token> scanTokens() {
        while (!isAtEnd()) {
        // We are at the beginning of the next lexeme.
            start = current;
            scanToken();
        }

        tokens.add(new Token(EOF, "", null, line)); // not necessary, but cleaner to add EOF token at the end
        return tokens;
    }

    // Scanners are glorified state machines
    private void scanToken() {
        char c = advance();
        switch (c) {
            // 1-char tokens
            case '(': addToken(LEFT_PAREN); break;
            case ')': addToken(RIGHT_PAREN); break;
            case '{': addToken(LEFT_BRACE); break;
            case '}': addToken(RIGHT_BRACE); break;
            case ',': addToken(COMMA); break;
            case '.': addToken(DOT); break;
            case '-': addToken(MINUS); break;
            case '+': addToken(PLUS); break;
            case ';': addToken(SEMICOLON); break;
            case '*': addToken(STAR); break; 

            // 1- or 2-char tokens
            case '!':
                addToken(match('=') ? BANG_EQUAL : BANG);
                break;
            case '=':
                addToken(match('=') ? EQUAL_EQUAL : EQUAL);
                break;
            case '<':
                addToken(match('=') ? LESS_EQUAL : LESS);
                break;
            case '>':
                addToken(match('=') ? GREATER_EQUAL : GREATER);
                break;
            
            // division or comments
            case '/':
                if (match('/')) {
                    // A comment goes until the end of the line.
                    while (peek() != '\n' && !isAtEnd()) advance();
                    // we don't add a token for comments; not a useful lexeme
                }
                if (match('*')) {
                    // A block comment starts with /* and ends with */.
                    while (peek() != '*' || peekNext() != '/') {
                        if (peek() == '\n') line++;
                        if (isAtEnd()) {
                            Lox.error(line, "Unterminated block comment.");
                            return; // stop scanning
                        }
                        advance();
                    }
                    // Consume the closing */.
                    advance(); // *
                    advance(); // /
                } else {
                    addToken(SLASH);
                }
                break;

            // skip meaningless whitespace and new lines
            case ' ':
                break;
            case '\r':
                break;
            case '\t':
                break;
            case '\n':
                line++;
                break;

            // literals
            case '"': 
                string(); 
                break;
            

            // Invalid characters
            default:
                if (isDigit(c)) { // putting it in default allows us to use this function
                    number();
                } else if (isAlpha(c)) {
                    identifier();
                } else {
                    Lox.error(line, "Unexpected character ("+ c +").");
                    // still continues scanning after an error to catch as many errors as possible in one go
                }
                break;
        } 
    }
    

    private boolean isAtEnd() {
        return current >= source.length();
    }

    // Adds a token of the specified type to the list of tokens.
    private void addToken(TokenType type) {
        addToken(type, null);
    }

    // for literal values like numbers or strings
    private void addToken(TokenType type, Object literal) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line));
    }



    /// LOOKAHEAD (CONSUME OR NOT COSUME)

    // Advances the current position in the source string and returns the character at that position.
    private char advance() {
        return source.charAt(current++);
    }

    // Checks if the next character matches the expected character and advances the current position if it does.
    // combo of advance() and peek() methods
    private boolean match(char expected) {
        if (isAtEnd()) return false;
        if (source.charAt(current) != expected) return false;

        current++;
        return true;
    }

    // advance() without consuming the character (lookahead)
    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    // makes clear to reader we only want to look ahead at most two characters
    private char peekNext() {
        if (current + 1 >= source.length()) return '\0';
        return source.charAt(current + 1);
    } 


    /// STRINGS & RESERVED WORDS

    // Handles string literals by scanning until the closing quote is found.
    private void string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line++; // Lox allows multi-line strings, so this handles that
            advance();
        }

        if (isAtEnd()) {
            Lox.error(line, "Unterminated string.");
            return;
        }

        // The closing ".
        advance();

        // Trim the surrounding quotes.
        String value = source.substring(start + 1, current - 1);
        addToken(STRING, value);
    }

    private void identifier() {
        while (isAlphaNumeric(peek())) advance();
        String text = source.substring(start, current);
        TokenType type = keywords.get(text);
        if (type == null) {
            type = IDENTIFIER;
        }
        addToken(type);
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') ||
            (c >= 'A' && c <= 'Z') ||
                c == '_';
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }



    /// NUMBERS

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    } 

    // Allows numbers that do not start or end with ".", but may contain it to make this (and expansion) simple
    private void number() {
        while (isDigit(peek())) advance();

        // Look for a fractional part.
        if (peek() == '.' && isDigit(peekNext())) {
            // Consume the "."
            advance();

            while (isDigit(peek())) advance();
        }

        // Using Java's built-in parsing to convert the string to a double.
        addToken(NUMBER,
            Double.parseDouble(source.substring(start, current)));
    }

}