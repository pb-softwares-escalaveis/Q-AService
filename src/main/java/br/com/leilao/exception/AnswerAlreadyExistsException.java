package br.com.leilao.exception;

public class AnswerAlreadyExistsException extends RuntimeException {
    public AnswerAlreadyExistsException(String message) {
        super(message);
    }
}