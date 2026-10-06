import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

/**
 * Three seed tests. Coverage is intentionally light:
 *  - computeIncomeTax: only the middle slab branch is hit
 *  - computeVAT: only the 18% rate is hit
 *  - applyExemption: only the "under cap" branch is hit
 *  - roundToPaise: not exercised
 *  - isEligibleForReturn: not exercised at all
 */
public class TaxCalculatorTest {

    @Test
    public void incomeTax_middleSlab() {
        TaxCalculator c = new TaxCalculator();
        BigDecimal tax = c.computeIncomeTax(new BigDecimal("700000"));
        // 12500 (5% on 250k-500k) + 40000 (20% on 500k-700k) = 52500
        assertEquals(0, tax.compareTo(new BigDecimal("52500.00")));
    }

    @Test
    public void vat_eighteenPercent() {
        TaxCalculator c = new TaxCalculator();
        BigDecimal gst = c.computeVAT(new BigDecimal("1000"), 18);
        assertEquals(0, gst.compareTo(new BigDecimal("180.00")));
    }

    @Test
    public void exemption_underCap() {
        TaxCalculator c = new TaxCalculator();
        BigDecimal net = c.applyExemption(
                new BigDecimal("600000"), new BigDecimal("120000"));
        assertEquals(0, net.compareTo(new BigDecimal("480000")));
    }

    @Test
    public void isEligibleForReturn_nullIncome_returnsFalse() {
        // Arrange
        TaxCalculator c = new TaxCalculator();
        // Act
        boolean eligible = c.isEligibleForReturn(null, 30);
        // Assert
        assertFalse(eligible);
    }

    @Test
    public void isEligibleForReturn_negativeAge_returnsFalse() {
        // Arrange
        TaxCalculator c = new TaxCalculator();
        // Act
        boolean eligible = c.isEligibleForReturn(new BigDecimal("300000"), -1);
        // Assert
        assertFalse(eligible);
    }

    @Test
    public void isEligibleForReturn_seniorAtExactThreshold_returnsFalse() {
        // Arrange
        TaxCalculator c = new TaxCalculator();
        // Act: 60-year-old exactly at the 300000 threshold is not strictly above it
        boolean eligible = c.isEligibleForReturn(new BigDecimal("300000"), 60);
        // Assert
        assertFalse(eligible);
    }

    @Test
    public void isEligibleForReturn_superSeniorAboveThreshold_returnsTrue() {
        // Arrange
        TaxCalculator c = new TaxCalculator();
        // Act
        boolean eligible = c.isEligibleForReturn(new BigDecimal("600000"), 85);
        // Assert
        assertTrue(eligible);
    }

    @Test
    public void isEligibleForReturn_adultBelowThreshold_returnsFalse() {
        // Arrange
        TaxCalculator c = new TaxCalculator();
        // Act
        boolean eligible = c.isEligibleForReturn(new BigDecimal("200000"), 30);
        // Assert
        assertFalse(eligible);
    }

    @Test
    public void isEligibleForReturn_adultAboveThreshold_returnsTrue() {
        // Arrange
        TaxCalculator c = new TaxCalculator();
        // Act
        boolean eligible = c.isEligibleForReturn(new BigDecimal("300000").add(BigDecimal.ONE), 30);
        // Assert
        assertTrue(eligible);
    }

    @Test
    public void isEligibleForReturn_exactAge80_usesSuperSeniorThreshold() {
        // Arrange
        TaxCalculator c = new TaxCalculator();
        // Act: age exactly 80 must use the 500000 threshold, so 400000 is not eligible
        boolean eligible = c.isEligibleForReturn(new BigDecimal("400000"), 80);
        // Assert
        assertFalse(eligible);
    }
}
