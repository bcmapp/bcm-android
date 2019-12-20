package com.bcm.messenger.utility.deque;

import java.util.concurrent.LinkedBlockingDeque;

/**
 * Created by wjh on 2019-09-06
 */
public class LinkedBlockingLifoQueue extends LinkedBlockingDeque {

    @Override
    public void put(Object o) throws InterruptedException {
        super.putFirst(o);
    }

    @Override
    public boolean add(Object o) {
        super.addFirst(o);
        return true;
    }

    @Override
    public boolean offer(Object o) {
        super.addFirst(o);
        return true;
    }

}
