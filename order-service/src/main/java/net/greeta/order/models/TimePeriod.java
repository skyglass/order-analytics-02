package net.greeta.order.models;

public class TimePeriod {
    private long orders;
    private double totalPrice;

    public TimePeriod(long orders, double totalPrice) {
        this.orders = orders;
        this.totalPrice = totalPrice;
    }

    public TimePeriod() {
        this.orders = 0;
        this.totalPrice = 0;
    }

    public void addOrder(Order order) {
        orders += 1;
        totalPrice += order.price;
    }

    public long getOrders() {
        return orders;
    }

    public double getTotalPrice() {
        return totalPrice;
    }
}
