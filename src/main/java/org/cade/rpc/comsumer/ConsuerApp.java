package org.cade.rpc.comsumer;

public class ConsuerApp {
    public static void main(String[] args) throws Exception {
        Consumer consumer = new Consumer();
        System.out.println(consumer.add(1,2));
    }
}
