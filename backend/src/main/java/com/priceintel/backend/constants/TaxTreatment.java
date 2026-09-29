package com.priceintel.backend.constants;

/**
 * How sales tax relates to the listed price.
 *
 * <p>This is not a presentation detail. A UK price of £120 including 20% VAT and
 * a US price of $120 with tax added at checkout are different amounts of
 * revenue, and treating them the same overstates the UK margin by the whole VAT
 * rate. The platform compares prices across countries, so it has to know which
 * kind of number each one is.</p>
 */
public enum TaxTreatment {

    /**
     * Tax is added at checkout and collected on the seller's behalf — US sales
     * tax. The listed price is what the seller receives, and tax never touches
     * profit. This is the platform's historical behaviour and remains the
     * default.
     */
    PASS_THROUGH,

    /**
     * The listed price already contains the tax — UK/EU VAT, GST. The seller
     * receives price ÷ (1 + rate); the rest is remitted. Margin computed on the
     * listed price would be wrong by the tax.
     */
    INCLUSIVE,

    /** No sales tax applies. Distinct from a zero rate that someone forgot to fill in. */
    NONE
}
