package com.gtc.opportunity.trader.domain.stat.general;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

/**
 * Created by Valentyn Berezin on 26.02.18.
 */
@Entity
@DiscriminatorValue("DAILY")
@DynamicInsert
@DynamicUpdate
public class XoTradeStatDaily extends BaseXoTradeStat {
}
