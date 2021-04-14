package server;

public class CustomFileException extends Exception {

    public CustomFileException(String message) {
        super(message);
    }

    public static CustomFileException nFields(int line, int n) {
        return new CustomFileException("only " + n + " fields on line " + line);
    }

    public static CustomFileException invalidField(int line, String fieldName) {
        return new CustomFileException("invalid " + fieldName + " on line " + line);
    }

    public static CustomFileException notFound() {
        return new CustomFileException("file not found");
    }

    public static CustomFileException readProblem() {
        return new CustomFileException("problem reading file");
    }
    public static CustomFileException envVarNotSet() {
        return new CustomFileException("the FILE env variable that should point to a save file is not set");
    }
}