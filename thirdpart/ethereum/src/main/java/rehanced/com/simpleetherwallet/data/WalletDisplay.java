package rehanced.com.simpleetherwallet.data;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;

import java.math.BigDecimal;
import java.math.BigInteger;

import rehanced.com.simpleetherwallet.utils.ExchangeCalculator;

@Deprecated
public class WalletDisplay implements Comparable, Parcelable {

    public static final byte NORMAL = 0;
    public static final byte WATCH_ONLY = 1;
    public static final byte CONTACT = 2;

    private String name;
    private String publicKey;
    private BigInteger balance;
    private byte type;

    public WalletDisplay(String name, String publicKey, BigInteger balance, byte type) {
        this.name = name;
        this.publicKey = publicKey;
        this.balance = balance;
        this.type = type;
    }

    public WalletDisplay(String name, String publicKey) {
        this.name = name;
        this.publicKey = publicKey;
        this.balance = null;
        this.type = CONTACT;
    }

    protected WalletDisplay(Parcel in) {
        name = in.readString();
        publicKey = in.readString();
        type = in.readByte();
        this.balance = (BigInteger) in.readSerializable();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeString(publicKey);
        dest.writeByte(type);
        dest.writeSerializable(balance);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<WalletDisplay> CREATOR = new Creator<WalletDisplay>() {
        @Override
        public WalletDisplay createFromParcel(Parcel in) {
            return new WalletDisplay(in);
        }

        @Override
        public WalletDisplay[] newArray(int size) {
            return new WalletDisplay[size];
        }
    };

    public byte getType() {
        return type;
    }

    public void setType(byte type) {
        this.type = type;
    }

    public BigInteger getBalanceNative() {
        return balance;
    }

    public void setBalance(BigInteger balance) {
        this.balance = balance;
    }

    public double getBalance() {
        return new BigDecimal(balance).divide(ExchangeCalculator.ONE_ETHER, 8, BigDecimal.ROUND_UP).doubleValue();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPublicKey() {
        return publicKey.toLowerCase();
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    @Override
    public int compareTo(@NonNull Object o) {
        return name.compareTo(((WalletDisplay) o).getName());
    }
}
