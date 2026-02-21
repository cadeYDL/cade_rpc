package org.cade.rpc.provider;

import org.cade.rpc.api.Add;
import org.cade.rpc.api.User;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public class AddImpl implements Add {
    @Override
    public int add(int a, int b) {
        return a+b;
    }

    @Override
    public int mul(int a, int b) {
        return a-b;
    }

    @Override
    public User addUser(User user1, User user2) {
        User user = new User();
        user.setName("cade"+"::"+user1.getName()+"::"+user2.getName());
        user.setAge(user1.getAge()+user2.getAge());
        return user;
    }
}
