package com.bcm.messenger.common.core;


import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.provider.AmeModuleCenter;
import com.bcm.messenger.common.utils.GroupUtil;
import com.bcm.messenger.utility.DelimiterUtil;
import com.bcm.messenger.utility.StringAppearanceUtil;
import com.bcm.messenger.utility.proguard.NotGuard;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Address implements Parcelable, Comparable<Address>, NotGuard {

    public static final Parcelable.Creator<Address> CREATOR = new Parcelable.Creator<Address>() {
        @Override
        public Address createFromParcel(Parcel in) {
            return new Address(in);
        }

        @Override
        public Address[] newArray(int size) {
            return new Address[size];
        }
    };

    private static final int PHONE_LIMIT = 20;
    private static final String UNKNOWN_STRING = "Unknown";
    private static final Pattern PUBLIC_PATTERN = Pattern.compile("^[a-zA-Z0-9]+");
    private static final Pattern emailPattern = android.util.Patterns.EMAIL_ADDRESS;

    public static final Address UNKNOWN = new Address(UNKNOWN_STRING, UNKNOWN_STRING);

    private static final String TAG = "Address";

    @NonNull
    private final String context;
    @NonNull
    private final String address;

    private Boolean isGroup = null;
    private Boolean isPublicKey = null;
    private Boolean isEmail = null;

    private Address(@NonNull String context, @NonNull String address) {
        this.context = context;
        this.address = address;
    }

    public Address(Parcel in) {
        this(in.readString(), in.readString());
    }

//    public static @NonNull
//    Address fromSerialized(@NonNull String serialized) {
//        return new Address(serialized);
//    }

    public static Address from(@NonNull String serialized) {
        return new Address(serialized, serialized);
    }

    public static Address from(@NonNull AccountContext context, @NonNull String serialized) {
        return new Address(context.getUid(), serialized);
    }

    public static @NonNull
    List<Address> fromSerializedList(@NonNull String serialized, char delimiter) {
        String[] escapedAddresses = DelimiterUtil.split(serialized, delimiter);
        List<Address> addresses = new LinkedList<>();

        for (String escapedAddress : escapedAddresses) {
            addresses.add(Address.from(DelimiterUtil.unescape(escapedAddress, delimiter)));
        }

        return addresses;
    }

    public static @NonNull
    String toSerializedList(@NonNull List<Address> addresses, char delimiter) {
        Collections.sort(addresses);

        List<String> escapedAddresses = new LinkedList<>();

        for (Address address : addresses) {
            escapedAddresses.add(DelimiterUtil.escape(address.serialize(), delimiter));
        }

        return StringAppearanceUtil.INSTANCE.join(escapedAddresses, delimiter + "");
    }

    /**
     * address
     * @return
     */
    public boolean isCurrentLogin() {
        return AmeModuleCenter.INSTANCE.login().isAccountLogin(address);
    }

    /**
     * 
     * @return
     */
    public boolean isGroup() {
        if (isGroup == null) {
            isGroup = GroupUtil.isTTGroup(address) || GroupUtil.isEncodedGroup(address);
        }
        return isGroup;
    }

    /**
     * bcm
     * @return
     */
    public boolean isNewGroup() {
        return GroupUtil.isTTGroup(address);
    }

    public boolean isMmsGroup() {
        return GroupUtil.isMmsGroup(address);
    }

    public boolean isEmail() {
        if (isEmail == null) {
            Matcher matcher = emailPattern.matcher(address);
            isEmail = matcher.matches();
        }
        return isEmail;
    }

    /**
     * 
     * @return
     */
    public boolean isIndividual() {
        return !isGroup() && !isEmail();
    }

    /**
     * UID
     * @return
     */
    public boolean isPublicKey() {
        if (isPublicKey == null) {
            isPublicKey = address.length() > PHONE_LIMIT && !isGroup() && !isEmail() && PUBLIC_PATTERN.matcher(address).find();
        }
        return isPublicKey;
    }

    public @NonNull
    String toGroupString() {
        if (!isGroup()) {
            return address;
        }
        return address;
    }

    @Override
    public String toString() {
        return context + "_" + address;
    }

    public String serialize() {
        return address;
    }

    public String context() {
        return context;
    }

    public String format() {
        if (isPublicKey()) {
            return address.substring(0, 9);
        }
        return address;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || !(other instanceof Address)) {
            return false;
        }

        Address oa = (Address)other;
        return context.equals(oa.context) && address.equals(oa.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(context, address);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(context);
        dest.writeString(address);
    }

    @Override
    public int compareTo(@NonNull Address other) {
        int result = context.compareTo(other.context);
        if (result == 0) {
            return address.compareTo(other.address);
        }
        return result;
    }


}
