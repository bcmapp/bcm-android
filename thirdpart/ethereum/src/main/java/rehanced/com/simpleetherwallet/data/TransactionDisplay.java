package rehanced.com.simpleetherwallet.data;

import android.os.Parcel;
import android.os.Parcelable;

import java.math.BigDecimal;
import java.math.BigInteger;

@Deprecated
public class TransactionDisplay implements Comparable, Parcelable {

    public static final byte NORMAL = 0;
    public static final byte CONTRACT = 1;

    private String fromAddress;
    private String toAddress;
    private BigInteger amount;
    private int confirmationStatus;
    private long date;
    private String walletName;
    private byte type;
    private String txHash;
    private String nounce;
    private long block;
    private int gasUsed;
    private long gasprice;
    private boolean error;
    private String memo;

    public TransactionDisplay(String fromAddress, String toAddress, BigInteger amount, int confirmationStatus, long date, String walletName,
                              byte type, String txHash, String nounce, long block, int gasUsed, long gasprice, boolean error,
                              String memo) {
        this.fromAddress = fromAddress;
        this.toAddress = toAddress;
        this.amount = amount;
        this.confirmationStatus = confirmationStatus;
        this.date = date;
        this.walletName = walletName;
        this.type = type;
        this.txHash = txHash;
        this.nounce = nounce;
        this.block = block;
        this.gasUsed = gasUsed;
        this.gasprice = gasprice;
        this.error = error;
        this.memo = memo;
    }

    protected TransactionDisplay(Parcel in) {
        fromAddress = in.readString();
        toAddress = in.readString();
        amount = (BigInteger) in.readSerializable();
        confirmationStatus = in.readInt();
        date = in.readLong();
        walletName = in.readString();
        type = in.readByte();
        txHash = in.readString();
        nounce = in.readString();
        block = in.readLong();
        gasUsed = in.readInt();
        gasprice = in.readLong();
        error = in.readByte() != 0;
        this.memo = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(fromAddress);
        dest.writeString(toAddress);
        dest.writeSerializable(amount);
        dest.writeInt(confirmationStatus);
        dest.writeLong(date);
        dest.writeString(walletName);
        dest.writeByte(type);
        dest.writeString(txHash);
        dest.writeString(nounce);
        dest.writeLong(block);
        dest.writeInt(gasUsed);
        dest.writeLong(gasprice);
        dest.writeByte((byte) (error ? 1 : 0));
        dest.writeString(memo);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<TransactionDisplay> CREATOR = new Creator<TransactionDisplay>() {
        @Override
        public TransactionDisplay createFromParcel(Parcel in) {
            return new TransactionDisplay(in);
        }

        @Override
        public TransactionDisplay[] newArray(int size) {
            return new TransactionDisplay[size];
        }
    };

    public boolean isError() {
        return error;
    }

    public void setError(boolean error) {
        this.error = error;
    }

    public long getBlock() {
        return block;
    }

    public void setBlock(long block) {
        this.block = block;
    }

    public int getGasUsed() {
        return gasUsed;
    }

    public void setGasUsed(int gasUsed) {
        this.gasUsed = gasUsed;
    }

    public long getGasprice() {
        return gasprice;
    }

    public void setGasprice(long gasprice) {
        this.gasprice = gasprice;
    }

    public String getTxHash() {
        return txHash;
    }

    public void setTxHash(String txHash) {
        this.txHash = txHash;
    }

    public byte getType() {
        return type;
    }

    public void setType(byte type) {
        this.type = type;
    }

    public String getWalletName() {
        return walletName;
    }

    public void setWalletName(String walletName) {
        this.walletName = walletName;
    }

    public String getFromAddress() {
        return fromAddress.toLowerCase();
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getToAddress() {
        return toAddress.toLowerCase();
    }

    public void setToAddress(String toAddress) {
        this.toAddress = toAddress;
    }

    public BigInteger getAmountNative() {
        return amount;
    }

    public double getAmount() {
        return new BigDecimal(amount).divide(new BigDecimal(1000000000000000000d), 8, BigDecimal.ROUND_UP).doubleValue();
    }

    public void setAmount(BigInteger amount) {
        this.amount = amount;
    }

    public int getConfirmationStatus() {
        return confirmationStatus;
    }

    public void setConfirmationStatus(int confirmationStatus) {
        this.confirmationStatus = confirmationStatus;
    }


    public long getDate() {
        return date;
    }

    public void setDate(long date) {
        this.date = date;
    }

    public String getNounce() {
        return nounce;
    }

    public void setNounce(String nounce) {
        this.nounce = nounce;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    @Override
    public String toString() {
        return "TransactionDisplay{" +
                "fromAddress='" + fromAddress + '\'' +
                ", toAddress='" + toAddress + '\'' +
                ", amount=" + amount +
                ", confirmationStatus=" + confirmationStatus +
                ", date='" + date + '\'' +
                ", walletName='" + walletName + '\'' +
                '}';
    }

    @Override
    public int compareTo(Object o) {
        if (this.getDate() < ((TransactionDisplay) o).getDate()) {
            return 1;
        }
        if (this.getDate() == ((TransactionDisplay) o).getDate()) {
            return 0;
        }
        return -1;
    }
}
