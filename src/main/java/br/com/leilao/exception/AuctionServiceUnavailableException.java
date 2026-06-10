package br.com.leilao.exception;

public class AuctionServiceUnavailableException extends RuntimeException {
    public AuctionServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}