package br.com.leilao.exception;

public class AdServiceUnavailableException extends RuntimeException {
    public AdServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}