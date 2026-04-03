package dev.engine.bindings.slang;

/**
 * Exception thrown when a Slang API call fails.
 */
public class SlangException extends RuntimeException {

    private final int resultCode;

    public SlangException(String message) {
        super(message);
        this.resultCode = -1;
    }

    public SlangException(String message, int resultCode) {
        super(message + " (SlangResult=0x" + Integer.toHexString(resultCode) + ")");
        this.resultCode = resultCode;
    }

    public SlangException(String message, Throwable cause) {
        super(message, cause);
        this.resultCode = -1;
    }

    public int resultCode() {
        return resultCode;
    }
}
