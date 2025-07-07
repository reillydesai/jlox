package com.craftinginterpreters.lox;

import java.util.List;

public class ScannerTest {
    public static void main(String[] args) {
        // Test input string
        String source = """ 
            @
            var x = 42;
            print x + 3.14;
            if (x > 0) {
                print "Positive!";
            }
            """;

        // Run the scanner
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();

        // Print the tokens
        for (Token token : tokens) {
            System.out.println(token);
        }
    }
}
