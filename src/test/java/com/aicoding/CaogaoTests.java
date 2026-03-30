package com.aicoding;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class CaogaoTests {

    public class Generic<T> {
        private T key;

        public Generic(T key) {
            this.key = key;
        }

        public T getKey() {
            return key;
        }
    }

    @Test
    public void generator() {
        Generic<Integer> genericInteger = new Generic<>(123);
        System.out.println(genericInteger.getKey());
    }

    @Test
    public void sumCalc() {
        String filename = "D:\\IdeaProjects\\ai-coding-platform\\src\\main\\resources\\data.txt";
        double res = 0.0;

        try(Scanner scanner = new Scanner(new File(filename))) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] tmp = line.strip().split(" ");
                if (tmp.length >= 2) {
                    if (tmp[tmp.length - 1].isEmpty()) continue;
                    res += Double.parseDouble(tmp[tmp.length - 1]);
                }
            }
            System.out.println("Sum: " + res);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
