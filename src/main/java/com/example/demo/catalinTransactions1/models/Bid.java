package com.example.demo.catalinTransactions1.models;

import java.math.BigDecimal;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;

@Entity
public class Bid {

	@Id
    @GeneratedValue
    private Long id;

    @NotNull
    private BigDecimal amount;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Item item;

    public Bid() {
    }

    public Bid(BigDecimal amount, Item item) {
        this.amount = amount;
        this.item = item;
    }

    public Bid(BigDecimal amount, Item item, Bid lastBid) throws InvalidBidException {
        if (lastBid != null && amount.compareTo(lastBid.getAmount()) < 1) {
            throw new InvalidBidException(
                    "Bid amount '" + amount + " too low, last bid was: " + lastBid.getAmount()
            );
        }
        this.amount = amount;
        this.item = item;
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Item getItem() {
        return item;
    }
}
