package ru.typizer.errors;

/** 402 {@code insufficient_balance}: not enough pages on the balance. */
public class InsufficientBalanceException extends ApiException {
    private static final long serialVersionUID = 1L;

    public InsufficientBalanceException(Details details) {
        super(details);
    }
}
