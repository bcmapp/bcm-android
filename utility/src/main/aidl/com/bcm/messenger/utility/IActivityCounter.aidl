// IActivityCounter.aidl
package com.bcm.messenger.utility;

// Declare any non-default types here with import statements

interface IActivityCounter {
         int increase();
         int decrease();
         int pid();
}
