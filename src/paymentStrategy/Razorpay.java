package paymentStrategy;

public class Razorpay implements PaymentStrategy {
    private static int nextReference = 1;
    @Override
    public int pay(double amount) {
        if (amount <= 0)
            throw new IllegalArgumentException("Payment amount must be positive");
        System.out.println("Payment Processing: " + amount);
        int reference = nextReference++;
        System.out.println("Mock payment succeeded. Reference: " + reference);
        return reference;
    }

    @Override
    public void refund(int gatewayReference) {

        System.out.println("Mock refund requested for gateway reference " + gatewayReference);
    }
}
