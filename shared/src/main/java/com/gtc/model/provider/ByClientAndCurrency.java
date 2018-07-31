package com.gtc.model.provider;

import com.gtc.meta.CurrencyPair;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Created by Valentyn Berezin on 13.06.18.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ByClientAndCurrency {

    protected String client;

    protected CurrencyPair pair;

    protected long timestamp = System.currentTimeMillis();

    public ByClientAndCurrency(String client, CurrencyPair pair) {
        this.client = client;
        this.pair = pair;
    }
}
