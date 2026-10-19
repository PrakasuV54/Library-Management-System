package paymentStrategy;
public interface PaymentStrategy {
    int pay(double amount);
    void refund(int gatewayReference);
}
