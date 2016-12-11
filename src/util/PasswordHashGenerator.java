package util;

import java.util.Scanner;

/**
 *
 * @author fbeneditovm
 */
public class PasswordHashGenerator {
    public static void main(String[] args) throws PasswordStorage.CannotPerformOperationException {
        Scanner in = new Scanner(System.in);
        System.out.println("Type you password:");
        String password = in.nextLine();
        System.out.println("The hash is:");
        System.out.println(PasswordStorage.createHash(password));
    }
}
