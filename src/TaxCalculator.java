import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Simplified Indian income-tax + GST calculator.
 * Amounts are in INR. Deliberately imperfect: some branches are
 * exercised by the seed tests only via a happy path.
 */
public class TaxCalculator {

    // Old-regime slabs (simplified, INR).
    public BigDecimal computeIncomeTax(BigDecimal income) {
        if (income == null || income.signum() < 0) {
            throw new IllegalArgumentException("income must be >= 0");
        }
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal slab1 = new BigDecimal("250000");
        BigDecimal slab2 = new BigDecimal("500000");
        BigDecimal slab3 = new BigDecimal("1000000");
        if (income.compareTo(slab1) <= 0) {
            return tax;
        }
        if (income.compareTo(slab2) <= 0) {
            return income.subtract(slab1).multiply(new BigDecimal("0.05"));
        }
        if (income.compareTo(slab3) <= 0) {
            BigDecimal upToSlab2 = slab2.subtract(slab1).multiply(new BigDecimal("0.05"));
            return upToSlab2.add(income.subtract(slab2).multiply(new BigDecimal("0.20")));
        }
        BigDecimal upToSlab2 = slab2.subtract(slab1).multiply(new BigDecimal("0.05"));
        BigDecimal upToSlab3 = slab3.subtract(slab2).multiply(new BigDecimal("0.20"));
        return upToSlab2.add(upToSlab3)
                .add(income.subtract(slab3).multiply(new BigDecimal("0.30")));
    }

    // GST: 0/5/12/18/28. Only 18 is tested by the seed.
    public BigDecimal computeVAT(BigDecimal amount, int gstRatePercent) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be >= 0");
        }
        if (gstRatePercent != 0 && gstRatePercent != 5 && gstRatePercent != 12
                && gstRatePercent != 18 && gstRatePercent != 28) {
            throw new IllegalArgumentException("unsupported GST rate: " + gstRatePercent);
        }
        return amount.multiply(BigDecimal.valueOf(gstRatePercent))
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    // 80C-style flat exemption cap. Seed tests hit only the "under cap" branch.
    public BigDecimal applyExemption(BigDecimal taxableIncome, BigDecimal exemption) {
        if (taxableIncome == null || exemption == null) {
            throw new IllegalArgumentException("nulls not allowed");
        }
        BigDecimal cap = new BigDecimal("150000");
        BigDecimal effective = exemption.compareTo(cap) > 0 ? cap : exemption;
        BigDecimal result = taxableIncome.subtract(effective);
        return result.signum() < 0 ? BigDecimal.ZERO : result;
    }

    // Round to nearest paise (2 decimals).
    public BigDecimal roundToPaise(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("amount required");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    // Not exercised at all by the seed tests -- students must add coverage.
    public boolean isEligibleForReturn(BigDecimal grossIncome, int ageYears) {
        if (grossIncome == null || ageYears < 0) {
            return false;
        }
        BigDecimal threshold;
        if (ageYears >= 80) {
            threshold = new BigDecimal("500000");
        } else if (ageYears >= 60) {
            threshold = new BigDecimal("300000");
        } else {
            threshold = new BigDecimal("250000");
        }
        return grossIncome.compareTo(threshold) > 0;
    }
}
