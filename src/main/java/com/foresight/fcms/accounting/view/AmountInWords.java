package com.foresight.fcms.accounting.view;

import java.math.BigDecimal;
import java.util.*;

/** Exact decimal amount wording adapted from Microfinance VoucherPdfService. */
public final class AmountInWords {
    private AmountInWords() {}

    public static String amountInWords(BigDecimal amount, Locale locale) {
        boolean sw = "sw".equals(locale.getLanguage());
        var value = amount.abs().setScale(2, java.math.RoundingMode.UNNECESSARY);
        // Convert the integral part separately: cents never pass through floating point.
        var whole = value.toBigInteger();
        int cents = value.remainder(BigDecimal.ONE).movePointRight(2).intValueExact();
        String words = numberWords(whole, sw);
        return (amount.signum() < 0 ? (sw ? "Hasi " : "Minus ") : "")
                + (sw
                        ? "Shilingi za Tanzania "
                                + words
                                + " na senti "
                                + underThousand(cents, true)
                        : words
                                + " Tanzanian "
                                + (whole.equals(java.math.BigInteger.ONE)
                                        ? "shilling"
                                        : "shillings")
                                + " and "
                                + underThousand(cents, false)
                                + (cents == 1 ? " cent" : " cents"));
    }

    private static String numberWords(java.math.BigInteger value, boolean sw) {
        if (value.signum() == 0) return sw ? "sifuri" : "zero";
        String[] scales =
                sw
                        ? new String[] {
                            "",
                            "elfu",
                            "milioni",
                            "bilioni",
                            "trilioni",
                            "kwadrilioni",
                            "kwintilioni"
                        }
                        : new String[] {
                            "",
                            "thousand",
                            "million",
                            "billion",
                            "trillion",
                            "quadrillion",
                            "quintillion"
                        };
        var parts = new ArrayList<String>();
        int group = 0;
        while (value.signum() > 0) {
            var split = value.divideAndRemainder(java.math.BigInteger.valueOf(1000));
            int n = split[1].intValueExact();
            if (n != 0)
                parts.add(
                        sw && group > 0
                                ? scales[group] + " " + underThousand(n, true)
                                : underThousand(n, sw) + (group == 0 ? "" : " " + scales[group]));
            value = split[0];
            group++;
        }
        Collections.reverse(parts);
        return String.join(sw ? " na " : " ", parts);
    }

    private static String underThousand(int n, boolean sw) {
        String[] small =
                sw
                        ? new String[] {
                            "sifuri",
                            "moja",
                            "mbili",
                            "tatu",
                            "nne",
                            "tano",
                            "sita",
                            "saba",
                            "nane",
                            "tisa",
                            "kumi",
                            "kumi na moja",
                            "kumi na mbili",
                            "kumi na tatu",
                            "kumi na nne",
                            "kumi na tano",
                            "kumi na sita",
                            "kumi na saba",
                            "kumi na nane",
                            "kumi na tisa"
                        }
                        : new String[] {
                            "zero",
                            "one",
                            "two",
                            "three",
                            "four",
                            "five",
                            "six",
                            "seven",
                            "eight",
                            "nine",
                            "ten",
                            "eleven",
                            "twelve",
                            "thirteen",
                            "fourteen",
                            "fifteen",
                            "sixteen",
                            "seventeen",
                            "eighteen",
                            "nineteen"
                        };
        String[] tens =
                sw
                        ? new String[] {
                            "",
                            "",
                            "ishirini",
                            "thelathini",
                            "arobaini",
                            "hamsini",
                            "sitini",
                            "sabini",
                            "themanini",
                            "tisini"
                        }
                        : new String[] {
                            "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy",
                            "eighty", "ninety"
                        };
        if (n < 20) return small[n];
        if (n < 100) return tens[n / 10] + (n % 10 == 0 ? "" : (sw ? " na " : "-") + small[n % 10]);
        return (sw ? "mia " + small[n / 100] : small[n / 100] + " hundred")
                + (n % 100 == 0 ? "" : (sw ? " na " : " and ") + underThousand(n % 100, sw));
    }
}
